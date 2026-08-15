Yes. The best way to build an app similar to **NetMonster / Network Cell Info / Force LTE / Opensignal** is to build it as a **5G/4G network diagnostics and optimization assistant**, rather than claiming to electrically “boost” the SIM signal.

A normal Play Store app can read signal/network information, monitor LTE/NR cells, test throughput/latency, compare 4G versus 5G, and send the user to Android's network settings. It **cannot directly force LTE/NR using the official API** unless it has the privileged `MODIFY_PHONE_STATE` permission or carrier privileges. Android explicitly protects `setAllowedNetworkTypesForReason()` and manual network selection this way. ([Android Developers][1])

Below is a practical Jetpack Compose implementation path.

## 1. Decide what your first version will do

Build these features first:

1. Detect active SIMs.
2. Show the current operator.
3. Detect LTE / 5G NR / 5G NSA.
4. Display signal strength in dBm.
5. Display LTE RSRP, RSRQ and RSSNR.
6. Display NR SS-RSRP, SS-RSRQ and SS-SINR.
7. Display serving and neighboring cells.
8. Measure download speed and application-level latency.
9. Estimate whether the problem looks more like weak coverage or network congestion.
10. Provide an **Open Network Settings** button so the user can try LTE versus 5G manually.

Android exposes LTE RSRP/RSRQ/RSSNR and NR SS-RSRP/SS-RSRQ/SS-SINR through its telephony APIs. ([Android Developers][2])

A reasonable screen would look conceptually like:

```text
┌─────────────────────────────────┐
│ Network Optimizer               │
│ Telkomsel - SIM 1               │
│                                 │
│            5G NSA               │
│            -88 dBm              │
│                                 │
│ RSRP       -88 dBm              │
│ RSRQ       -12 dB               │
│ SINR        14 dB               │
│                                 │
│ ↓ 18.4 Mbps   Latency 71 ms     │
│                                 │
│ ⚠ Possible network congestion   │
│ Signal is good, throughput low  │
│                                 │
│ [ Run Network Test ]            │
│ [ Open Mobile Network Settings ]│
└─────────────────────────────────┘
```

That is much closer to what useful “signal booster” applications actually do.

---

# 2. Create the Android Studio project

As of August 14, 2026, Android Studio **Quail 3 | 2026.1.3 Patch 1** is on the stable channel. Using the latest stable Android Studio is the easiest starting point. ([Android Developers][3])

Create:

```text
New Project
→ Empty Activity
→ Kotlin
→ Jetpack Compose
```

For this tutorial I recommend:

```text
Name: NetworkOptimizer
Package: com.example.networkoptimizer

Minimum SDK: 31
```

I suggest API 31 for the first prototype because `TelephonyCallback` and its modern signal/cell listeners are available from API 31. You can add Android 10/11 compatibility later with older telephony APIs. `CellInfoNr` itself exists from API 29. ([Android Developers][4])

For Compose versions, use the **Compose BOM** rather than manually choosing versions for every Compose dependency; Android recommends the BOM specifically so compatible Compose libraries are managed together. ([Android Developers][5])

Your module dependencies will broadly need:

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
```

If the Empty Activity template generated these already, keep the generated versions.

---

# 3. Add Android permissions

Open:

```text
app/src/main/AndroidManifest.xml
```

Add:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application
        ... >

        ...

    </application>

</manifest>
```

`SubscriptionManager.getActiveSubscriptionInfoList()` requires `READ_PHONE_STATE`, while detailed `CellInfo` access requires precise location; the modern `CellInfoListener` requires both `READ_PHONE_STATE` and `ACCESS_FINE_LOCATION`. ([Android Developers][6])

On Android 12+, if you request precise location, Android recommends requesting coarse and fine location together because the user can choose approximate instead. ([Android Developers][7])

Do **not** request:

```xml
<uses-permission android:name="android.permission.MODIFY_PHONE_STATE" />
```

for a normal Play Store application and expect it to work. It is a privileged permission and is exactly why a normal application cannot simply call the LTE/NR forcing APIs. ([Android Developers][1])

---

# 4. Request runtime permissions in Compose

Create:

```text
ui/PermissionScreen.kt
```

For example:

```kotlin
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val phoneGranted =
            permissions[Manifest.permission.READ_PHONE_STATE] == true

        val locationGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (phoneGranted && locationGranted) {
            onPermissionsGranted()
        }
    }

    Button(
        onClick = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    ) {
        Text("Allow Network Diagnostics")
    }
}
```

Explain in your UI why location is required:

```text
Android protects cellular tower information as
location-sensitive information.

Your location is processed on the device and does
not need to be uploaded.
```

And only make that second sentence if that is actually how you design the application.

---

# 5. Create your data model

Create:

```text
data/RadioSnapshot.kt
```

Start with:

```kotlin
data class SignalMetrics(
    val technology: String = "Unknown",

    val dbm: Int? = null,

    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null
)

data class CellSnapshot(
    val technology: String,
    val registered: Boolean,

    val pci: Int?,
    val tac: Int?,
    val channel: Int?,

    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?
)

data class RadioSnapshot(
    val operatorName: String = "",
    val networkType: String = "Unknown",

    val signal: SignalMetrics = SignalMetrics(),

    val cells: List<CellSnapshot> = emptyList(),

    val downloadMbps: Double? = null,
    val latencyMs: Long? = null
)
```

Keep Android framework objects such as `CellInfoNr` out of the UI state when possible. Convert them into your own data classes inside the repository.

That makes your Compose UI easier to test.

---

# 6. Detect the user's SIM cards

Android provides `SubscriptionManager` for active subscription information, and `TelephonyManager.createForSubscriptionId()` lets you create a TelephonyManager associated with a specific SIM/subscription. ([Android Developers][6])

Create:

```text
data/SimRepository.kt
```

```kotlin
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String
)
```

Then:

```kotlin
class SimRepository(
    private val context: Context
) {

    private val subscriptionManager =
        context.getSystemService(SubscriptionManager::class.java)

    @SuppressLint("MissingPermission")
    fun getActiveSims(): List<SimInfo> {

        return subscriptionManager
            .activeSubscriptionInfoList
            .map { info ->

                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    carrierName = info.carrierName.toString()
                )
            }
    }
}
```

Now you can make a Compose selector:

```text
SIM 1 - Telkomsel
SIM 2 - XL
```

and monitor them separately.

---

# 7. Create a TelephonyManager for the selected SIM

Once the user selects a SIM:

```kotlin
val baseTelephonyManager =
    context.getSystemService(TelephonyManager::class.java)

val telephonyManager =
    baseTelephonyManager.createForSubscriptionId(subscriptionId)
```

This part is important for dual-SIM devices. Android documents `createForSubscriptionId()` specifically for making TelephonyManager calls against a particular subscription. ([Android Developers][8])

---

# 8. Listen to signal changes

On API 31+, use `TelephonyCallback`.

Create:

```text
data/NetworkTelephonyCallback.kt
```

```kotlin
class NetworkTelephonyCallback(
    private val onSignalChanged: (SignalStrength) -> Unit,
    private val onCellsChanged: (List<CellInfo>) -> Unit,
    private val onDisplayChanged: (TelephonyDisplayInfo) -> Unit
) : TelephonyCallback(),
    TelephonyCallback.SignalStrengthsListener,
    TelephonyCallback.CellInfoListener,
    TelephonyCallback.DisplayInfoListener {

    override fun onSignalStrengthsChanged(
        signalStrength: SignalStrength
    ) {
        onSignalChanged(signalStrength)
    }

    override fun onCellInfoChanged(
        cellInfo: List<CellInfo>
    ) {
        onCellsChanged(cellInfo)
    }

    override fun onDisplayInfoChanged(
        telephonyDisplayInfo: TelephonyDisplayInfo
    ) {
        onDisplayChanged(telephonyDisplayInfo)
    }
}
```

Android's `SignalStrengthsListener` receives live signal-strength changes, while `CellInfoListener` reports visible cell changes. ([Android Developers][4])

Register it:

```kotlin
@SuppressLint("MissingPermission")
fun register(
    telephonyManager: TelephonyManager,
    callback: TelephonyCallback,
    context: Context
) {

    telephonyManager.registerTelephonyCallback(
        context.mainExecutor,
        callback
    )
}
```

And always unregister:

```kotlin
telephonyManager.unregisterTelephonyCallback(callback)
```

---

# 9. Extract LTE signal measurements

Android's LTE signal object exposes RSRP, RSRQ and RSSNR directly. ([Android Developers][9])

Use:

```kotlin
fun extractLte(
    signalStrength: SignalStrength
): SignalMetrics? {

    val lte =
        signalStrength
            .cellSignalStrengths
            .filterIsInstance<CellSignalStrengthLte>()
            .firstOrNull()
            ?: return null

    return SignalMetrics(
        technology = "4G LTE",

        dbm = valueOrNull(lte.dbm),

        rsrp = valueOrNull(lte.rsrp),
        rsrq = valueOrNull(lte.rsrq),
        sinr = valueOrNull(lte.rssnr)
    )
}
```

Create this helper because Android uses an unavailable sentinel:

```kotlin
fun valueOrNull(value: Int): Int? {
    return if (value == CellInfo.UNAVAILABLE) {
        null
    } else {
        value
    }
}
```

---

# 10. Extract 5G NR measurements

Do the same thing for NR:

```kotlin
fun extractNr(
    signalStrength: SignalStrength
): SignalMetrics? {

    val nr =
        signalStrength
            .cellSignalStrengths
            .filterIsInstance<CellSignalStrengthNr>()
            .firstOrNull()
            ?: return null

    return SignalMetrics(
        technology = "5G NR",

        dbm = valueOrNull(nr.dbm),

        rsrp = valueOrNull(nr.ssRsrp),
        rsrq = valueOrNull(nr.ssRsrq),
        sinr = valueOrNull(nr.ssSinr)
    )
}
```

Android documents `ssRsrp`, `ssRsrq` and `ssSinr` for NR, including an unavailable value when the modem does not report a measurement. ([Android Developers][2])

Now the app can display:

```text
5G NR

SS-RSRP
-91 dBm

SS-RSRQ
-11 dB

SS-SINR
18 dB
```

---

# 11. Detect LTE versus 5G

This is slightly more complicated than:

```kotlin
telephonyManager.dataNetworkType
```

because 5G NSA can use LTE as the underlying data network while Android displays a 5G indicator.

`getDataNetworkType()` reports radio technology currently being used for data and can return LTE or NR. Android also exposes `TelephonyDisplayInfo` values such as `OVERRIDE_NETWORK_TYPE_NR_NSA` and `OVERRIDE_NETWORK_TYPE_NR_ADVANCED`. ([Android Developers][1])

A practical helper is:

```kotlin
fun networkLabel(
    telephonyManager: TelephonyManager,
    displayInfo: TelephonyDisplayInfo?
): String {

    return when {

        displayInfo?.overrideNetworkType ==
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> {
            "5G NSA"
        }

        displayInfo?.overrideNetworkType ==
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
            "5G+"
        }

        telephonyManager.dataNetworkType ==
                TelephonyManager.NETWORK_TYPE_NR -> {
            "5G NR"
        }

        telephonyManager.dataNetworkType ==
                TelephonyManager.NETWORK_TYPE_LTE -> {
            "4G LTE"
        }

        else -> {
            "Other"
        }
    }
}
```

This is more useful than simply checking for `NETWORK_TYPE_NR`.

Also remember that Android describes `TelephonyDisplayInfo` as information used for carrier-policy display, so don't treat the icon label alone as a throughput guarantee. ([Android Developers][10])

---

# 12. Read serving and neighboring cells

For detailed cell analysis use:

```kotlin
CellInfoLte
CellInfoNr
```

Android's `getAllCellInfo()` can return registered/serving and neighboring cellular information. Apps targeting modern Android receive cached results from `getAllCellInfo()`; for an explicit refresh Android provides `requestCellInfoUpdate()`, which is rate-limited and requires `ACCESS_FINE_LOCATION`. ([Android Developers][1])

Convert cells like this:

```kotlin
fun parseCells(
    cells: List<CellInfo>
): List<CellSnapshot> {

    return cells.mapNotNull { cell ->

        when (cell) {

            is CellInfoLte -> {

                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength

                CellSnapshot(
                    technology = "LTE",

                    registered = cell.isRegistered,

                    pci = valueOrNull(id.pci),
                    tac = valueOrNull(id.tac),
                    channel = valueOrNull(id.earfcn),

                    rsrp = valueOrNull(ss.rsrp),
                    rsrq = valueOrNull(ss.rsrq),
                    sinr = valueOrNull(ss.rssnr)
                )
            }

            is CellInfoNr -> {

                val id = cell.cellIdentity as CellIdentityNr
                val ss = cell.cellSignalStrength as CellSignalStrengthNr

                CellSnapshot(
                    technology = "NR",

                    registered = cell.isRegistered,

                    pci = valueOrNull(id.pci),
                    tac = valueOrNull(id.tac),
                    channel = valueOrNull(id.nrarfcn),

                    rsrp = valueOrNull(ss.ssRsrp),
                    rsrq = valueOrNull(ss.ssRsrq),
                    sinr = valueOrNull(ss.ssSinr)
                )
            }

            else -> null
        }
    }
}
```

Android exposes PCI/TAC and NR-ARFCN through `CellIdentityNr`, while LTE exposes its corresponding PCI/TAC and radio identity information. ([Android Developers][11])

You can then show:

```text
Nearby Cells

● LTE   PCI 231   EARFCN 1650   -86 dBm
○ LTE   PCI 122   EARFCN 1850   -101 dBm
○ NR    PCI 341   NRARFCN 642000 -92 dBm
```

The `●` means:

```kotlin
cell.isRegistered
```

---

# 13. Monitor actual internet connectivity

Use `ConnectivityManager` separately from `TelephonyManager`.

`ConnectivityManager` tells you whether the default network is cellular/Wi-Fi and whether Android considers the network validated for internet access. Android recommends callbacks rather than repeatedly polling connectivity state. ([Android Developers][12])

Example:

```kotlin
class ConnectivityMonitor(
    context: Context
) {

    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    val callback =
        object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {

                val cellular =
                    capabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_CELLULAR
                    )

                val internet =
                    capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )

                Log.d(
                    "Network",
                    "Cellular=$cellular internet=$internet"
                )
            }
        }

    fun start() {
        connectivityManager
            .registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        connectivityManager
            .unregisterNetworkCallback(callback)
    }
}
```

This helps distinguish:

```text
Cellular connection exists
```

from:

```text
Android has validated public Internet access
```

which are not necessarily the same thing. ([Android Developers][12])

---

# 14. Add your own speed test

This is one of the most valuable features for crowded events.

Don't infer speed from:

```text
5G icon
```

or:

```text
4 bars
```

Measure it.

Create:

```text
data/SpeedTestRepository.kt
```

Use a file hosted on **your own HTTPS test server/CDN**, for example:

```text
https://speed.example.com/10mb.bin
```

A simplified download test:

```kotlin
class SpeedTestRepository {

    suspend fun testDownload(
        url: URL
    ): Double = withContext(Dispatchers.IO) {

        val connection =
            url.openConnection() as HttpsURLConnection

        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000

        connection.setRequestProperty(
            "Accept-Encoding",
            "identity"
        )

        val started =
            SystemClock.elapsedRealtimeNanos()

        var totalBytes = 0L

        connection.inputStream.use { input ->

            val buffer = ByteArray(64 * 1024)

            while (true) {

                val count = input.read(buffer)

                if (count < 0) break

                totalBytes += count
            }
        }

        val finished =
            SystemClock.elapsedRealtimeNanos()

        connection.disconnect()

        val seconds =
            (finished - started) / 1_000_000_000.0

        val bits =
            totalBytes * 8.0

        bits / seconds / 1_000_000.0
    }
}
```

Result:

```text
21.72 Mbps
```

For production, run several transfers, discard startup anomalies, use multiple file sizes, prevent CDN/browser caching, and make it clear that speed tests consume the user's mobile data.

---

# 15. Measure latency

For the first version, measure application-level HTTPS round-trip latency to your server.

For example:

```kotlin
suspend fun latency(
    url: URL
): Long = withContext(Dispatchers.IO) {

    val start =
        SystemClock.elapsedRealtime()

    val connection =
        url.openConnection() as HttpsURLConnection

    connection.requestMethod = "HEAD"
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000

    connection.responseCode

    connection.disconnect()

    SystemClock.elapsedRealtime() - start
}
```

Run it five times:

```text
72 ms
68 ms
81 ms
70 ms
69 ms
```

Then use the median rather than one measurement.

Call this **HTTP latency**, not ICMP ping.

---

# 16. Build the “congestion detector”

This is where your app becomes more interesting than a basic NetMonster clone.

Android does not give an ordinary app a simple:

```text
TowerLoad = 94%
```

value.

Instead, create an inference engine.

For example:

```kotlin
enum class NetworkCondition {
    GOOD,
    WEAK_SIGNAL,
    POSSIBLE_CONGESTION,
    NO_INTERNET,
    UNKNOWN
}
```

Then:

```kotlin
fun analyzeNetwork(
    validated: Boolean,
    rsrp: Int?,
    downloadMbps: Double?,
    latencyMs: Long?
): NetworkCondition {

    if (!validated) {
        return NetworkCondition.NO_INTERNET
    }

    if (rsrp == null ||
        downloadMbps == null ||
        latencyMs == null
    ) {
        return NetworkCondition.UNKNOWN
    }

    if (rsrp < -110) {
        return NetworkCondition.WEAK_SIGNAL
    }

    if (
        rsrp > -100 &&
        downloadMbps < 2.0 &&
        latencyMs > 100
    ) {
        return NetworkCondition.POSSIBLE_CONGESTION
    }

    return NetworkCondition.GOOD
}
```

Treat those threshold values as **your product heuristic**, not as an Android or carrier-standard congestion measurement.

A result like:

```text
RSRP:       -84 dBm
Download:   0.6 Mbps
Latency:    286 ms
Internet:   Validated
```

could reasonably produce:

```text
Possible congestion

Your radio signal is relatively strong,
but current Internet performance is poor.

Try switching between 5G and LTE and retest.
```

That is exactly the kind of recommendation that is useful at a concert or festival.

---

# 17. Create the ViewModel

Google's current architecture guidance recommends putting business state in a `ViewModel`, exposing UI state, and using unidirectional data flow. ([Android Developers][13])

For example:

```kotlin
class NetworkViewModel(
    private val repository: NetworkRepository,
    private val speedTestRepository: SpeedTestRepository
) : ViewModel() {

    private val _state =
        MutableStateFlow(RadioSnapshot())

    val state: StateFlow<RadioSnapshot> =
        _state.asStateFlow()

    fun startMonitoring(
        subscriptionId: Int
    ) {

        viewModelScope.launch {

            repository
                .observe(subscriptionId)
                .collect { snapshot ->

                    _state.value = snapshot
                }
        }
    }

    fun runSpeedTest() {

        viewModelScope.launch {

            val speed =
                speedTestRepository.testDownload(
                    URL(
                        "https://YOUR_SERVER/test.bin"
                    )
                )

            _state.update {
                it.copy(
                    downloadMbps = speed
                )
            }
        }
    }
}
```

Your real repository would expose a `Flow<RadioSnapshot>` created from the telephony callbacks.

---

# 18. Observe ViewModel state in Compose

Google currently recommends `collectAsStateWithLifecycle()` for lifecycle-aware Flow collection in Compose. ([Android Developers][14])

```kotlin
@Composable
fun NetworkRoute(
    viewModel: NetworkViewModel
) {

    val state by
        viewModel.state
            .collectAsStateWithLifecycle()

    NetworkScreen(
        state = state,

        onRunTest = {
            viewModel.runSpeedTest()
        }
    )
}
```

---

# 19. Build the Compose dashboard

A simple version:

```kotlin
@Composable
fun NetworkScreen(
    state: RadioSnapshot,
    onRunTest: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Network Optimizer")
                }
            )
        }
    ) { padding ->

        LazyColumn(
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                NetworkCard(
                    operator = state.operatorName,
                    network = state.networkType
                )
            }

            item {

                SignalCard(
                    metrics = state.signal
                )
            }

            item {

                SpeedCard(
                    downloadMbps =
                        state.downloadMbps,

                    latencyMs =
                        state.latencyMs,

                    onRunTest =
                        onRunTest
                )
            }

            item {

                CellList(
                    cells = state.cells
                )
            }
        }
    }
}
```

Example signal card:

```kotlin
@Composable
fun SignalCard(
    metrics: SignalMetrics
) {

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                metrics.technology,
                style =
                    MaterialTheme.typography.titleLarge
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                "${metrics.dbm ?: "--"} dBm",
                style =
                    MaterialTheme.typography
                        .displaySmall
            )

            Text(
                "RSRP: ${metrics.rsrp ?: "--"} dBm"
            )

            Text(
                "RSRQ: ${metrics.rsrq ?: "--"} dB"
            )

            Text(
                "SINR: ${metrics.sinr ?: "--"} dB"
            )
        }
    }
}
```

---

# 20. Give users an official network-settings shortcut

This is the safe replacement for pretending your normal app can directly force LTE/5G.

Android defines `Settings.ACTION_NETWORK_OPERATOR_SETTINGS`, and you can optionally give it `Settings.EXTRA_SUB_ID` so Settings can open for a particular subscription. Android warns that a matching Settings activity might not exist on every device, so provide a fallback. ([Android Developers][15])

```kotlin
fun openNetworkSettings(
    context: Context,
    subscriptionId: Int
) {

    val intent =
        Intent(
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS
        ).apply {

            putExtra(
                Settings.EXTRA_SUB_ID,
                subscriptionId
            )
        }

    try {

        context.startActivity(intent)

    } catch (
        e: ActivityNotFoundException
    ) {

        context.startActivity(
            Intent(
                Settings.ACTION_WIRELESS_SETTINGS
            )
        )
    }
}
```

Button:

```kotlin
Button(
    onClick = {
        openNetworkSettings(
            context,
            selectedSubscriptionId
        )
    }
) {
    Text("Open Mobile Network Settings")
}
```

Then tell users:

```text
Try LTE
→ run test

Try 5G
→ run test

Keep whichever currently performs better.
```

---

# 21. Do not try this in a normal Play app

You may encounter code online such as:

```kotlin
telephonyManager.setAllowedNetworkTypesForReason(...)
```

The API exists.

But Android documents it as requiring:

```text
MODIFY_PHONE_STATE
```

or carrier privileges. ([Android Developers][1])

Likewise:

```kotlin
setNetworkSelectionModeManual(...)
```

requires privileged access or carrier privileges. ([Android Developers][1])

So this architecture:

```text
[ LTE only ]
[ NR only ]
[ LTE + NR ]
```

with your application directly changing the modem is **not generally available to an ordinary Play Store app through supported public APIs**.

Instead:

```text
Your app
   │
   ├── analyze network
   │
   ├── recommend LTE or 5G
   │
   └── open Android Settings
                │
                ▼
          user changes mode
```

is the robust approach.

---

# 22. Suggested project architecture

I would organize it like this:

```text
com.example.networkoptimizer
│
├── MainActivity.kt
│
├── data
│   ├── NetworkRepository.kt
│   ├── SimRepository.kt
│   ├── SpeedTestRepository.kt
│   ├── NetworkTelephonyCallback.kt
│   ├── RadioSnapshot.kt
│   └── CellSnapshot.kt
│
├── domain
│   ├── AnalyzeNetworkUseCase.kt
│   └── NetworkCondition.kt
│
└── ui
    ├── NetworkViewModel.kt
    ├── NetworkScreen.kt
    ├── PermissionScreen.kt
    ├── SignalCard.kt
    ├── SpeedCard.kt
    ├── CellCard.kt
    └── SimSelector.kt
```

That will scale much better than placing all telephony logic inside `MainActivity`.

---

# 23. Your eventual app flow

The finished application could behave like this:

```text
Launch
   ↓
Request phone + location permission
   ↓
Detect SIM 1 / SIM 2
   ↓
Select active SIM
   ↓
Register TelephonyCallback
   ↓
Read:

Network
LTE / NR / NSA

Signal
dBm
RSRP
RSRQ
SINR

Cells
PCI
TAC
EARFCN / NRARFCN

Internet
Validated / not validated

   ↓
Run speed test
   ↓
Measure:
download
latency
   ↓
Network analysis engine
   ↓
Recommendation
```

For example:

```text
NETWORK CONDITION

5G NSA
Signal: Good
RSRP: -86 dBm

Download: 0.8 Mbps
Latency: 241 ms

⚠ Possible network congestion

Recommended action:

Try LTE-only in Android network
settings and run another test.
```

Then after the user changes the mode:

```text
4G LTE
Signal: -91 dBm

Download: 13.4 Mbps
Latency: 54 ms

✓ LTE currently performs better

Recommended:
Stay on LTE during this event.
```

That's a genuinely useful application for crowded concerts, festivals and holiday gatherings.

## Most important technical distinction

The architecture should therefore be:

```text
         TelephonyManager
              │
       ┌──────┴──────┐
       ▼             ▼
 Signal metrics    Cell info
       │             │
       └──────┬──────┘
              ▼
       NetworkRepository
              │
              ▼
         StateFlow
              │
              ▼
         ViewModel
              │
              ▼
      Jetpack Compose UI
              │
       ┌──────┴───────┐
       ▼              ▼
  Speed test       Analyzer
       │              │
       └──────┬───────┘
              ▼
       Recommendation
              │
              ▼
   Android Network Settings
```

This gives you most of the useful behavior of the **10 apps we discussed**, while staying within Android's supported telephony/security model. Android's current Compose guidance also aligns well with this ViewModel → StateFlow → lifecycle-aware Compose UI architecture. ([Android Developers][16])

If you want to turn this into an actual project next, the natural next step is to build a **complete runnable Android Studio starter project** containing `MainActivity`, permission handling, `TelephonyCallback`, LTE/5G measurements, dual-SIM selection, a Compose dashboard, congestion analysis, and the network-settings button.

[1]: https://developer.android.com/reference/android/telephony/TelephonyManager "TelephonyManager  |  API reference  |  Android Developers"
[2]: https://developer.android.com/reference/android/telephony/CellSignalStrengthNr "CellSignalStrengthNr  |  API reference  |  Android Developers"
[3]: https://developer.android.com/studio/releases?utm_source=chatgpt.com "Android Studio Quail 3"
[4]: https://developer.android.com/reference/android/telephony/TelephonyCallback.SignalStrengthsListener?utm_source=chatgpt.com "TelephonyCallback.SignalStrengthsListener | API reference"
[5]: https://developer.android.com/develop/ui/compose/bom?utm_source=chatgpt.com "Use a Bill of Materials | Jetpack Compose"
[6]: https://developer.android.com/reference/android/telephony/SubscriptionManager "SubscriptionManager  |  API reference  |  Android Developers"
[7]: https://developer.android.com/develop/sensors-and-location/location/permissions/runtime?utm_source=chatgpt.com "Request location access at runtime | Sensors and location"
[8]: https://developer.android.com/reference/android/telephony/TelephonyManager?utm_source=chatgpt.com "TelephonyManager | API reference"
[9]: https://developer.android.com/reference/android/telephony/CellSignalStrengthLte "CellSignalStrengthLte  |  API reference  |  Android Developers"
[10]: https://developer.android.com/reference/android/telephony/TelephonyCallback.DisplayInfoListener?utm_source=chatgpt.com "TelephonyCallback.DisplayInfoListener | API reference"
[11]: https://developer.android.com/reference/android/telephony/CellIdentityLte?utm_source=chatgpt.com "CellIdentityLte | API reference"
[12]: https://developer.android.com/develop/connectivity/network-ops/reading-network-state?utm_source=chatgpt.com "Read network state | Connectivity"
[13]: https://developer.android.com/topic/architecture/ui-layer/stateholders?utm_source=chatgpt.com "State holders and UI state | App architecture"
[14]: https://developer.android.com/develop/ui/compose/state?utm_source=chatgpt.com "State and Jetpack Compose"
[15]: https://developer.android.com/reference/android/provider/Settings "Settings  |  API reference  |  Android Developers"
[16]: https://developer.android.com/topic/architecture/ui-layer?utm_source=chatgpt.com "UI layer | App architecture"
