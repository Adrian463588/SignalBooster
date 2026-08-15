Yang Anda maksud kemungkinan besar **Shizuku**, bukan “Citoku”. Untuk aplikasi seperti ini, desain terbaik adalah membuat **Connection Stabilizer bertingkat**: fitur dasar berjalan tanpa root, fitur lanjutan memakai Shizuku/ADB, dan fitur modem paling agresif hanya aktif jika benar-benar tersedia root/Sui.

Hal penting pertama: **jangan menyamakan Shizuku Wireless Debugging dengan root**. Dalam mode Wireless Debugging, Shizuku menjalankan aplikasi dengan privilege ADB/`shell`; permission-nya lebih tinggi daripada aplikasi Android biasa tetapi tetap terbatas. Dengan Sui/root, barulah kode dapat berjalan dengan identitas root. Shizuku sendiri menyarankan pemanggilan API/Binder secara langsung dibanding membuat aplikasi bergantung pada parsing perintah shell. ([GitHub][1])

## 1. Arsitektur yang saya rekomendasikan

```text
┌────────────────────────────────────────────┐
│               Jetpack Compose              │
│ Dashboard / Signal / Stabilizer / Bands    │
└─────────────────────┬──────────────────────┘
                      │
              Stabilizer Engine
                      │
       ┌──────────────┼──────────────┐
       │              │              │
 Network Monitor   QoE Engine   Recovery Engine
       │              │              │
 Connectivity     RTT/Jitter       Reconnect
 Telephony        Loss/Speed       Failover
 Wi-Fi            Signal           RAT change
       │              │              │
       └──────────────┼──────────────┘
                      │
              Privilege Gateway
       ┌──────────────┼──────────────┐
       │              │              │
    Normal API     Shizuku       Sui / Root
       │           ADB shell       UID 0
       │              │              │
 Public Android    Privileged    OEM/Modem
 APIs              Android API   adapters
```

Saya sarankan aplikasi **tidak langsung dibuat root-only**. Buat capability layer:

```text
NORMAL
   ↓
SHIZUKU_ADB
   ↓
ROOT/SUI
```

Setiap fitur memeriksa capability terlebih dahulu.

---

# 2. Fitur utama aplikasi

### A. Connection Monitor

Monitor:

- Wi-Fi
- Cellular
- VPN
- Ethernet bila tersedia
- internet validated/unvalidated
- captive portal
- metered/unmetered
- perubahan IP
- DNS
- network handover
- kehilangan jaringan

Gunakan `ConnectivityManager.registerDefaultNetworkCallback()` dan `NetworkCallback`, bukan polling `getActiveNetworkInfo()`. Android secara resmi menyediakan callback `onAvailable`, `onLost`, `onCapabilitiesChanged`, dan sebagainya. Android juga memperingatkan agar tidak melakukan synchronous network lookup di dalam callback karena berpotensi race condition. ([Android Developers][2])

Gunakan Kotlin:

```text
ConnectivityManager
NetworkCallback
NetworkCapabilities
LinkProperties
StateFlow<NetworkState>
```

---

# 3. Connection Stabilizer Engine

Jangan mendefinisikan stabilizer sebagai:

```text
while(true)
    ping google.com
```

Itu boros baterai dan tidak benar-benar memperkuat sinyal radio.

Lebih baik:

```text
Network change
       ↓
Connectivity validation
       ↓
Short active probe
       ↓
QoE calculation
       ↓
Stable?
 ┌─────┴─────┐
 YES          NO
 ↓             ↓
slow probes   recovery
15–60 sec     ↓
              alternate network
              reconnect
              retry
              rescore
```

Gunakan adaptive interval misalnya:

```text
Koneksi stabil     : 20–60 detik
Mulai memburuk     : 5–10 detik
Recovery mode      : 1–5 detik
Setelah recovery   : kembali perlahan
```

Dengan exponential backoff:

```text
1s → 2s → 4s → 8s → 15s → 30s
```

---

# 4. Jangan hanya menggunakan ICMP ping

Saya lebih menyarankan tiga probe:

```text
DNS resolution
        +
TCP/TLS connection
        +
small HTTPS request
```

Dengan endpoint kecil milik Anda sendiri, misalnya respons:

```http
HTTP/1.1 204 No Content
```

Kemudian hitung:

```text
RTT
jitter
packet/request loss
DNS latency
TLS latency
HTTP latency
timeout ratio
```

Android bahkan menyediakan `Network.getSocketFactory()` dan `Network.openConnection()` sehingga Anda bisa menjalankan probe melalui **network tertentu**, misalnya khusus Wi-Fi atau khusus mobile data, bukan sekadar default network. ([Android Developers][3])

Ini sangat berguna untuk:

```text
Wi-Fi RTT     = 350 ms
Cellular RTT  = 58 ms

→ cellular lebih sehat
```

tanpa harus langsung memindahkan seluruh koneksi perangkat.

---

# 5. Keep Alive

Ada dua jenis.

### Software Keep Alive

Coroutine melakukan probe kecil:

```text
DNS / TCP / HTTPS
```

secara adaptif.

### Hardware-offloaded Keep Alive

Jika use case/socket mendukungnya, Android menyediakan `SocketKeepalive`. Packet keepalive dapat ditangani sistem/hardware untuk mengurangi penggunaan CPU dan baterai. ([Android Developers][4])

Jadi jangan menggunakan `WakeLock + ping 1 detik` terus menerus.

---

# 6. Wi-Fi Stabilizer

Untuk Wi-Fi, ambil:

```text
SSID
BSSID
RSSI
frequency
channel
link speed
network validation
RTT
jitter
loss
```

Aplikasi modern sebaiknya memakai:

```text
WifiManager
WifiNetworkSuggestion
WifiNetworkSpecifier
ConnectivityManager
```

`WifiNetworkSuggestion` memungkinkan aplikasi memberikan kandidat Wi-Fi kepada Android untuk dipertimbangkan dalam proses auto-connect. Aplikasi normal tidak lagi bebas memaksa koneksi global seperti Android lama. ([Android Developers][5])

Untuk Android 13+, pengelolaan koneksi Wi-Fi juga menggunakan permission `NEARBY_WIFI_DEVICES`; beberapa operasi scan masih memerlukan `ACCESS_FINE_LOCATION`. ([Android Developers][6])

---

# 7. Cellular Signal Analyzer

Di sinilah aplikasi Anda menjadi menarik.

Untuk LTE ambil:

```text
Band
EARFCN
PCI
Cell ID
Bandwidth
RSRP
RSRQ
RSSI
RSSNR
CQI
Timing Advance
```

Android menyediakan `CellIdentityLte.getBands()`, `getEarfcn()` dan `CellSignalStrengthLte` untuk RSRP, RSRQ, RSSNR dan CQI. ([Android Developers][7])

Untuk 5G NR ambil:

```text
NR Band
NR-ARFCN
PCI
NCI
SS-RSRP
SS-RSRQ
SS-SINR
CSI-RSRP
CSI-RSRQ
CSI-SINR
CQI
```

`CellIdentityNr` dapat memberikan band dan NR-ARFCN; `CellSignalStrengthNr` mengekspos sejumlah parameter kualitas radio tersebut. ([Android Developers][8])

`PhysicalChannelConfig` juga dapat memberikan band serta bandwidth uplink/downlink pada perangkat yang menyediakannya. ([Android Developers][9])

---

# 8. Jangan menggunakan RSSI saja

Misalnya:

```text
5G n78
RSRP    -82 dBm
RSRQ    -17 dB
SINR      2 dB
RTT     240 ms
Loss      9%
```

Walaupun RSRP terlihat kuat, kualitas sebenarnya buruk.

Band lain:

```text
LTE B3
RSRP    -94 dBm
RSRQ     -9 dB
SINR     16 dB
RTT      54 ms
Loss    0.2%
```

Untuk aplikasi Anda, **LTE B3 harus mendapat score lebih tinggi**.

Gunakan kombinasi:

```text
Network Score =
    signal quality
  + SINR
  + RSRQ
  + latency
  + jitter
  + packet loss
  + throughput
  + connection stability
```

bukan sekadar jumlah bar sinyal.

---

# 9. “Band paling tidak ramai” perlu sedikit dikoreksi

Ini sangat penting.

Pada cellular network, yang Anda cari bukan:

> “port yang tidak ramai”.

TCP/UDP port seperti:

```text
80
443
5228
```

**tidak menentukan kepadatan jaringan radio 4G/5G.**

Yang relevan adalah:

```text
Operator
   ↓
Tower / sector
   ↓
Cell
   ↓
Band
   ↓
EARFCN / NR-ARFCN
   ↓
Radio resource
```

Android public API **tidak memberikan persentase penggunaan resource tower/PRB secara universal**.

Jadi jangan menampilkan:

```text
Band n78 congestion = 74%
```

kalau datanya sebenarnya tidak tersedia.

Yang bisa dilakukan adalah **congestion inference**:

```text
RSRP tinggi
+
SINR rendah
+
RSRQ buruk
+
RTT meningkat
+
packet loss meningkat
+
throughput turun
        ↓
probable congestion
```

Kemudian beri confidence:

```text
n78
Quality Score      42/100
Congestion Risk    HIGH
Confidence         MEDIUM
```

Ini jauh lebih ilmiah.

---

# 10. Cellular Band Scanner

Buat halaman:

```text
AVAILABLE CELLS

5G n78
PCI       213
NRARFCN   633984
RSRP      -84 dBm
RSRQ      -10 dB
SINR       17 dB
Score      89
Status    Excellent

LTE B3
PCI       101
EARFCN    1650
RSRP      -91 dBm
RSRQ      -12 dB
SINR       12 dB
Score      76

LTE B1
...
```

Tetapi ada batasannya.

`requestNetworkScan()` merupakan API privileged: akses penuh memerlukan `MODIFY_PHONE_STATE` atau carrier privileges, dengan permission/location requirements tertentu. Jadi aplikasi Play Store biasa tidak boleh diasumsikan bisa melakukan full modem scan. ([Android Developers][10])

Karena itu:

```text
Normal Mode
→ passive CellInfo

Shizuku
→ try privileged telephony capabilities

Root
→ OEM/modem-specific scan
```

---

# 11. Band Steering / Band Lock

Untuk cellular istilah yang lebih tepat sebenarnya:

**RAT / Band / Cell Selection**

bukan Wi-Fi band steering.

Android menyediakan API untuk mengubah **allowed network types**, misalnya:

```text
LTE
NR
LTE + NR
```

melalui `setAllowedNetworkTypesForReason()`, tetapi API tersebut membutuhkan `MODIFY_PHONE_STATE` atau carrier privileges. Itu juga **memilih teknologi jaringan**, bukan mengunci LTE B3 atau NR n78 tertentu. ([Android Developers][10])

Jadi buat abstraction:

```kotlin
interface RadioController {

    fun capabilities(): Set<RadioCapability>

    suspend fun prefer5g(): Result<Unit>

    suspend fun preferLte(): Result<Unit>

    suspend fun setAutomatic(): Result<Unit>

    suspend fun restartData(): Result<Unit>

    suspend fun lockBand(
        band: RadioBand
    ): Result<Unit>
}
```

Implementasi:

```text
PublicRadioController
ShizukuRadioController
RootQualcommRadioController
RootExynosRadioController
RootMediaTekRadioController
```

**Jangan membuat satu command band-lock dan menganggapnya akan bekerja di semua Android.**

RIL/modem Android memang memiliki vendor-specific implementation; AOSP sendiri menggunakan Radio Interface Layer/HAL sebagai lapisan antara framework dan modem. ([Android Open Source Project][11])

---

# 12. Shizuku Integration

Stack:

```text
Shizuku API
Shizuku Provider
Binder/AIDL
UserService
Kotlin Coroutines
```

Dependency berasal dari:

```text
dev.rikka.shizuku:api
dev.rikka.shizuku:provider
```

Shizuku API memungkinkan kode Java/JNI berjalan menggunakan identitas shell/ADB atau root tergantung backend. ([GitHub][1])

Saya sangat menyarankan:

```text
UI
 ↓
PrivilegeRepository
 ↓
typed Binder calls
 ↓
Shizuku UserService
 ↓
specific operation
```

bukan:

```text
UI input
 ↓
"rish -c " + userString
```

Ini mencegah command injection.

---

# 13. Capability Detection

Saat aplikasi dibuka:

```text
Checking privileges...

Android API        ✓
Shizuku installed  ✓
Shizuku running    ✓
ADB privilege      ✓
Root               ✓
Band lock          ✓
Radio restart      ✓
Network scan       ✓
```

Jangan menjalankan command lalu berharap berhasil.

Gunakan:

```text
CapabilityProbe
```

dan simpan hasil:

```kotlin
data class DeviceCapabilities(
    val shizuku: Boolean,
    val root: Boolean,
    val cellularScan: Boolean,
    val bandRead: Boolean,
    val bandLock: Boolean,
    val radioRestart: Boolean,
    val wifiControl: Boolean
)
```

Shizuku sendiri memperingatkan bahwa permission ADB berbeda antar versi Android/perangkat dan tetap terbatas. ([GitHub][12])

---

# 14. Recovery Engine

Recovery jangan langsung:

```text
network slow
→ restart modem
```

Gunakan state machine.

```text
HEALTHY
   ↓
DEGRADED
   ↓
VERIFYING
   ↓
RECOVERING
   ↓
VALIDATING
   ↓
HEALTHY
```

Urutan recovery:

```text
1. verify internet
2. refresh DNS/socket
3. select alternate available Network
4. reconnect Wi-Fi
5. request cellular path
6. switch Wi-Fi ↔ cellular
7. RAT re-selection
8. data reconnect
9. modem/radio restart             ← root/privileged, last resort
```

Tambahkan hysteresis supaya tidak terjadi:

```text
5G → LTE → 5G → LTE → 5G
```

setiap beberapa detik.

Misalnya:

```text
minimum dwell time = 30–120 sec
```

dan switch hanya jika kandidat secara konsisten lebih baik.

---

# 15. Fitur konser / kerumunan

Untuk kondisi ini saya akan membuat mode khusus:

## Crowd Mode

```text
┌ Crowd Stabilizer ─────────────┐
│ Current      5G n78           │
│ Quality      47 / 100         │
│ Latency      186 ms           │
│ Jitter       71 ms            │
│ Loss         5.8 %            │
│ SINR         3 dB             │
│                              │
│ Better path detected          │
│ LTE B3       76 / 100         │
│                              │
│ [ AUTO OPTIMIZE ]             │
└───────────────────────────────┘
```

Algorithm:

```text
observe
↓
detect degradation
↓
collect 5–15 sec samples
↓
calculate confidence
↓
find candidate
↓
switch only if improvement significant
↓
validate
↓
rollback if worse
```

Dengan demikian aplikasi tidak terus-terusan mengganti radio.

---

# 16. Fitur yang justru lebih efektif: Wi-Fi + Cellular failover

Ini kemungkinan memberikan hasil lebih nyata daripada hanya mengejar band locking.

Android memungkinkan socket diarahkan ke network tertentu melalui `Network.getSocketFactory()` atau `Network.openConnection()`. ([Android Developers][3])

Jadi aplikasi bisa mempertahankan:

```text
Wi-Fi probe
+
cellular probe
```

secara bersamaan.

Contoh:

```text
Wi-Fi
Score 85
 ↓
traffic via Wi-Fi

Wi-Fi turun menjadi 25

Cellular
Score 77
 ↓
new socket → Cellular
```

Untuk **traffic aplikasi Anda sendiri**, ini cukup menggunakan Android networking APIs.

Untuk membuat efek stabilizer berlaku pada traffic aplikasi lain:

```text
VPNService
       ↓
TUN interface
       ↓
Connection Manager
     ↙       ↘
 Wi-Fi     Cellular
     ↘       ↙
   tunnel server
```

Ini jauh lebih kompleks, tetapi merupakan arah arsitektur yang benar bila Anda ingin membuat produk seperti network accelerator.

---

# 17. Tech stack

| Layer                      | Stack                                                 |
| -------------------------- | ----------------------------------------------------- |
| Language                   | Kotlin                                                |
| UI                         | Jetpack Compose + Material 3                          |
| Architecture               | Clean Architecture + MVVM/MVI                         |
| Async                      | Kotlin Coroutines + Flow/StateFlow                    |
| DI                         | Hilt                                                  |
| Network                    | ConnectivityManager + `Network` sockets               |
| HTTP                       | OkHttp                                                |
| Telephony                  | TelephonyManager + TelephonyCallback                  |
| LTE                        | CellInfoLte / CellIdentityLte / CellSignalStrengthLte |
| 5G                         | CellInfoNr / CellIdentityNr / CellSignalStrengthNr    |
| Wi-Fi                      | WifiManager + NetworkSuggestion                       |
| Privilege                  | Shizuku API                                           |
| Root                       | Sui/Magisk adapter                                    |
| IPC                        | Binder/AIDL                                           |
| Persistence                | Room                                                  |
| Settings                   | DataStore                                             |
| Background                 | Foreground Service                                    |
| Periodic non-critical work | WorkManager                                           |
| Optional global stabilizer | VpnService                                            |
| Logging                    | Timber / structured logger                            |
| Testing                    | JUnit + MockK + Compose UI Test                       |

Untuk continuous stabilizer, jangan memakai `dataSync` FGS sebagai hack untuk berjalan selamanya: Android modern menerapkan timeout terhadap beberapa foreground-service types. Bila fungsi inti aplikasi benar-benar tidak cocok dengan kategori FGS lainnya, Android menyediakan `specialUse`, tetapi use case tersebut harus dideklarasikan dan akan ditinjau bila didistribusikan melalui Google Play. ([Android Developers][13])

---

# 18. Struktur project

Saya akan menggunakan:

```text
app/
│
├── core/
│   ├── network/
│   ├── wifi/
│   ├── telephony/
│   ├── privilege/
│   ├── database/
│   └── common/
│
├── feature/
│   ├── dashboard/
│   ├── stabilizer/
│   ├── cellular/
│   ├── wifi/
│   ├── bands/
│   ├── diagnostics/
│   └── settings/
│
├── stabilizer/
│   ├── monitor/
│   ├── probe/
│   ├── scoring/
│   ├── recovery/
│   └── decision/
│
├── privilege/
│   ├── public/
│   ├── shizuku/
│   └── root/
│
└── modem/
    ├── common/
    ├── qualcomm/
    ├── exynos/
    └── mediatek/
```

Ini penting karena modem Qualcomm, Exynos dan MediaTek tidak boleh dipaksa memakai implementasi yang sama.

---

# 19. Tahapan implementasi

### Phase 1 — MVP tanpa root

Bangun terlebih dahulu:

```text
Network monitoring
Wi-Fi/mobile detection
Cellular metrics
LTE/NR band reader
RTT
jitter
loss
network score
adaptive keepalive
connection history
auto recovery
```

Ini sudah bisa dibuat dengan Android API.

### Phase 2 — Shizuku

Tambahkan:

```text
Shizuku Binder lifecycle
permission request
capability detection
privileged network operations
privileged telephony operations
safe reconnect
```

Non-root Shizuku melalui Wireless Debugging masih bergantung pada ADB privilege dan secara umum perlu diaktifkan kembali setelah reboot; karena itu aplikasi harus menangani binder mati secara eksplisit. ([GitHub][1])

### Phase 3 — Root

Baru tambahkan:

```text
Root detection
Sui integration
radio control
OEM-specific band controller
rollback system
```

### Phase 4 — Crowd Optimizer

Tambahkan:

```text
historical QoE
band quality model
congestion inference
adaptive RAT selection
hysteresis
rollback
```

### Phase 5 — Advanced

Jika ingin koneksi benar-benar sangat resilient:

```text
VpnService
Wi-Fi + cellular path manager
QUIC tunnel
remote relay
seamless failover
```

---

Wajib sampai phase 5 .

## Target akhir yang realistis

Saya akan memposisikan aplikasi ini sebagai **Adaptive Connection Stabilizer**, bukan “signal booster”.

Fungsinya:

```text
✓ menjaga sesi jaringan hidup
✓ mendeteksi internet mati
✓ reconnect otomatis
✓ Wi-Fi ↔ cellular failover
✓ membaca LTE/5G band
✓ membaca RSRP/RSRQ/SINR/CQI
✓ mendeteksi probable congestion
✓ memberikan band/cell recommendation
✓ memilih LTE vs 5G jika privilege memungkinkan
✓ melakukan privileged recovery melalui Shizuku
✓ band locking jika root + modem memang mendukung
✓ rollback otomatis ketika hasil lebih buruk

```

Untuk perangkat yang mendukungnya, Android sendiri memang menyediakan band, ARFCN, bandwidth dan parameter kualitas radio yang cukup kaya untuk membangun **QoE-driven cellular optimizer**. Bagian yang harus diperlakukan sebagai OEM-specific adalah **memaksa modem mengunci band/cell tertentu**. ([Android Developers][8])

Jika targetnya Samsung/Qualcomm/MediaTek tertentu, tahap berikutnya yang paling berguna adalah membuat **PRD + arsitektur lengkap sampai class/module Kotlin**, termasuk algoritma `NetworkScore`, `CrowdMode`, `ShizukuService`, `RadioController`, permission matrix, dan flow Compose-nya.

[1]: https://github.com/RikkaApps/Shizuku-API?utm_source=chatgpt.com "GitHub - RikkaApps/Shizuku-API: The API and the developer guide for Shizuku and Sui. · GitHub"
[2]: https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback?utm_source=chatgpt.com "ConnectivityManager.NetworkCallback  |  API reference  |  Android Developers"
[3]: https://developer.android.com/reference/android/net/Network.html?utm_source=chatgpt.com "Network  |  API reference  |  Android Developers"
[4]: https://developer.android.com/reference/android/net/SocketKeepalive?utm_source=chatgpt.com "SocketKeepalive  |  API reference  |  Android Developers"
[5]: https://developer.android.com/reference/android/net/wifi/WifiNetworkSuggestion.html?utm_source=chatgpt.com "WifiNetworkSuggestion  |  API reference  |  Android Developers"
[6]: https://developer.android.com/develop/connectivity/wifi/wifi-permissions?utm_source=chatgpt.com "Request permission to access nearby Wi-Fi devices  |  Connectivity  |  Android Developers"
[7]: https://developer.android.com/reference/kotlin/android/telephony/CellIdentityLte?utm_source=chatgpt.com "CellIdentityLte | API reference"
[8]: https://developer.android.com/reference/android/telephony/CellIdentityNr?utm_source=chatgpt.com "CellIdentityNr | API reference"
[9]: https://developer.android.com/reference/android/telephony/PhysicalChannelConfig?authuser=19&utm_source=chatgpt.com "PhysicalChannelConfig  |  API reference  |  Android Developers"
[10]: https://developer.android.com/reference/android/telephony/TelephonyManager?utm_source=chatgpt.com "TelephonyManager  |  API reference  |  Android Developers"
[11]: https://source.android.com/docs/core/connect/ril?hl=id&utm_source=chatgpt.com "Pemfaktoran ulang RIL  |  Android Open Source Project"
[12]: https://github.com/rikkaapps/shizuku?pubDate=20260725&utm_source=chatgpt.com "GitHub - RikkaApps/Shizuku: Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process. · GitHub"
[13]: https://developer.android.com/about/versions/15/behavior-changes-15?utm_source=chatgpt.com "Behavior changes: Apps targeting Android 15 or higher  |  Android Developers"
