# AGENTS.md — SignalBooster / Privacy Resilience

## 1. Project intent

SignalBooster is an Android-only Kotlin and Jetpack Compose application for:

- diagnosing Wi-Fi and cellular connectivity;
- improving connection resilience through supported Android APIs;
- comparing Wi-Fi, LTE, and 5G quality using measured QoE;
- providing confidence-based privacy and interference observations; and
- offering optional, user-visible acoustic masking as a local privacy aid.

The product must not claim to amplify radio power, create cellular coverage, or guarantee protection from surveillance. The implementation is documentation-first in this repository; the reference projects are evidence and learning material, not source code to copy.

## 2. Instruction and reference precedence

Apply instructions in this order:

1. system and platform safety requirements;
2. user requirements;
3. this file;
4. `PRD.md`;
5. the documents in `docs/`;
6. the projects under `porjectreferences/`.

The following documents are the primary product references:

- `docs/Docs1.md`: capability-tiered connectivity stabilization, QoE probes, recovery, Shizuku boundaries, and crowd-mode concepts;
- `docs/Docs2.md`: Android telephony, Wi-Fi, permissions, LTE/5G metrics, and Compose architecture;
- `docs/Docs3.md` and `docs/Docs4.md`: acoustic masking concepts, Android audio lifecycle, and their limitations.

Reference-project guidance:

- `reference5` (`jamwarden`) is the preferred pattern for pure-Kotlin signal fusion, confidence tiers, local-only state, and tests that do  depend on Android framework types;
- `reference6` (`sonicjammer`) is useful only for separation between audio generation, audio playback, repository, and ViewModel responsibilities; do  copy attack-tuned waveform parameters or claims;
- `reference7` (`Hutch_Turbo`) demonstrates foreground-service plumbing but its artificial traffic loop is not a signal booster and must  be reused;
- `reference2`, `reference4`, `refrence1`, and `reference3` contain offensive Bluetooth/RF disruption or hardware-jamming material and are included from implementation.

Do  copy code, assets, identifiers, or licenses from a reference project without checking its license and documenting the provenance. Prefer independent, minimal implementations of safe patterns.

## 3. Non-negotiable safety boundary

### Allowed

- Android public APIs for network observation, connectivity callbacks, Wi-Fi suggestions, telephony metrics, and Settings hand-offs;
- bounded, user-initiated network-quality probes on networks selected by the user or the operating system;
- local Wi-Fi, Bluetooth, and cellular state observation;
- confidence-based detection of unusual signal or connectivity changes;
- optional anonymous, connectionless BLE corroboration that is opt-in and non-persistent;
- user-visible local acoustic masking with conservative volume and lifecycle controls.
- arbitrary Shizuku/root shell commands, shell-string parsing, or privilege escalation;
- RF jamming or transmission intended to disrupt Wi-Fi, Bluetooth, cellular, GPS, or other radio services;
- Bluetooth flooding, Wi-Fi deauthentication, packet injection, beacon flooding, target selection, or denial-of-service behavior;
- code that accepts or stores third-party target MAC addresses, device identifiers, or attack parameters;
- declaring `MODIFY_PHONE_STATE` for a normal application or presenting privileged modem control as guaranteed;
- covert microphone capture, attempts to seize or lock another app's microphone, or claims that another app cannot record;
- hidden audio playback, unsafe ultrasonic/high-volume defaults, or claims that acoustic masking is inaudible or guaranteed effective;
- telemetry, analytics, or backend upload of raw audio, precise location, credentials, or persistent radio identifiers.

### Prohibited

Here are 5 prohibited coding practices that violate the YAGNI principle, lead to over-engineering, and produce unclean code in Android development.

**1. DON'T: Prematurely abstract "just in case"**

- **The Problem**: Creating interfaces, abstract classes, or whole architectural layers for functionality that might be needed in the future. This adds complexity without solving a current requirement.
- **The Anti-Pattern**: Building a full repository pattern and dependency injection setup just to save a single user preference, based on the assumption that you might switch data sources later.
- **The YAGNI Connection**: This is the classic YAGNI violation. You aren't gonna need that complexity until you actually do. Wait until the need for multiple data sources is confirmed before introducing the abstraction.

**2. DON'T: Abuse `!!` operator for null-safety**

- **The Problem**: Using `!!` (the not-null assertion operator) frequently out of laziness or a false sense of safety. This is essentially "clean" code's nemesis because it throws a `NullPointerException` when a null value unexpectedly appears.
- **The Anti-Pattern**:
  ```kotlin
  // DON'T: This will crash if someString is null
  val length = someString!!.length
  ```
- **Why It's Over-Engineering (in a bad way)**: The right solution is to handle null safely using `?.let`, `?:` (Elvis operator), or `if (someString != null)`. Using `!!` is a shortcut that creates fragile, crash-prone code.

**3. DON'T: Block the main thread with heavy operations**

- **The Problem**: Performing database queries, file I/O, or network calls on the UI/Main thread.
- **The Anti-Pattern**:
  ```kotlin
  // DON'T: Database query on the main thread will cause an ANR
  fun loadData() {
      val data = database.query() // Blocking call
      updateUI(data)
  }
  ```
- **The "Unclean" Factor**: This is an obvious Android coding sin. The correct, clean approach is to use coroutines and specify the correct dispatcher (e.g., `withContext(Dispatchers.IO) { ... }`) .

**4. DON'T: Let `ViewModel`s hold references to `Activity`/`Context`**

- **The Problem**: Storing a reference to a `Context` or `Activity` in a `ViewModel`, which outlives configuration changes (like screen rotations).
- **The Anti-Pattern**:
  ```kotlin
  // DON'T: This causes a memory leak
  class MyViewModel : ViewModel() {
      private lateinit var context: Context
  }
  ```
- **The Consequence**: This causes severe memory leaks and is a prime example of a framework leak—where Android-specific classes are used in the wrong layer, making the code brittle and hard to test .

**5. DON'T: Write empty `catch` blocks that swallow exceptions**

- **The Problem**: Catching an exception and doing nothing with it. This hides bugs and makes debugging a nightmare.
- **The Anti-Pattern**:
  ```kotlin
  try {
      riskyOperation()
  } catch (e: Exception) {
      // Silent failure - this is terrible
  }
  ```
- **The Clean Code Failure**: This code fails to communicate anything about the error. A clean and testable approach is to handle the error appropriately, perhaps by logging it and showing a user-friendly message, or wrapping it in a domain-specific exception for your architecture to handle .

## 4. Required architecture

Use Kotlin, Jetpack Compose, Material 3, Hilt, coroutines, Flow/StateFlow, Navigation Compose, DataStore, and Room only where their maintenance cost is justified. Use a version catalog and pin dependency versions.

Use Clean Architecture with MVVM and unidirectional data flow:

```text
User action
    -> ViewModel / UI event
    -> use case
    -> repository or platform gateway
    -> Android data source
    -> immutable domain result
    -> StateFlow
    -> stateless Compose content
```

Recommended package boundaries:

```text
app/
├── ui/              # Screens, stateless composables, design system
├── presentation/    # ViewModels, UI events, immutable UI state
├── domain/          # Use cases, policies, pure models, interfaces
├── data/            # Repository implementations, persistence, mappers
├── platform/        # ConnectivityManager, WifiManager, TelephonyManager adapters
├── privacy/         # Privacy posture, anomaly fusion, acoustic masking orchestration
├── privilege/       # Capability detection and allowlisted Shizuku gateway
└── di/              # Hilt modules and bindings
```

Layer rules:

- Compose code renders state and emits events; it must not call Android services directly.
- ViewModels own presentation state and depend on use cases, never on `Activity`, `View`, or raw framework singletons.
- Domain policies must be pure Kotlin whenever possible and must not import Android framework classes.
- Platform adapters translate framework objects into domain models at the boundary.
- Repositories expose interfaces to domain code and own data-source coordination.
- One responsibility belongs in one abstraction; avoid duplicate permission, scoring, retry, or mapping logic.
- Prefer composition and small interfaces over inheritance-heavy designs.
- Every public class/function needs KDoc explaining its contract, permission assumptions, and failure behavior.

## 5. Capability and API rules

### Normal capability tier

The baseline path may use:

- `ConnectivityManager.registerDefaultNetworkCallback()` and `NetworkCallback`;
- `NetworkCapabilities`, `LinkProperties`, and network-specific `Network` sockets;
- `WifiManager`, `WifiNetworkSuggestion`, and official Settings intents;
- `TelephonyManager`, `TelephonyCallback`, `SubscriptionManager`, and available LTE/NR `CellInfo` metrics;
- `SocketKeepalive` for applicable app-owned sockets;
- WorkManager or an explicitly justified foreground service for sustained, visible work.

The platform remains authoritative for network selection. A recommendation must remain a recommendation when a public API cannot perform the requested change.

### Mandatory Shizuku tier

Shizuku is an mandatory capability adapter, a requirement for the application to function.

- Detect installation, running state, binder death, permission state, and supported device actions separately.
- Use direct, typed Binder/API calls where available; never build arbitrary shell strings from UI input.
- Maintain a small allowlist of explicitly documented, user-confirmed actions.
- On unsupported OEMs, missing permissions, binder death, or ambiguous results, return `CAPABILITY_UNAVAILABLE` or `FAILED` and leave the system unchanged.
- The UI must show exactly which capability is available and what action will occur before execution.

### Root/Sui

Root/Sui modem adapters are mandatory also and must be implemented in the first product scope. A rooted device does not make an OEM-specific action portable or safe by itself.

## 6. State, errors, and fail-closed behavior

Use explicit states rather than nulls or optimistic defaults:

- `AVAILABLE`;
- `UNAVAILABLE`;
- `PERMISSION_REQUIRED`;
- `CAPABILITY_UNAVAILABLE`;
- `INSUFFICIENT_DATA`;
- `RUNNING`;
- `STOPPED`;
- `FAILED`.

Every repository and use case returns a typed result or a sealed failure. Never hide an exception, silently retry forever, or convert missing measurements into invented values.

Unknown signal metrics remain unknown. A recommendation must include the observations, timestamp, confidence, and reason. A confidence classifier must never expose a bare `true/false` claim that the user is being jammed.

## 7. Android and Compose standards

- Minimum target for the first implementation: Android API 31, selected to keep the initial telephony and Bluetooth path single-track.
- Use `MaterialTheme` tokens; no hardcoded colors, dimensions, or user-facing strings in composables.
- Keep composables small and stateless; hoist state to the lowest common owner.
- Use `collectAsStateWithLifecycle()` for Flow collection in Compose.
- Use `rememberSaveable` for transient UI state that must survive recreation.
- Put side effects in `LaunchedEffect`, `DisposableEffect`, or lifecycle-aware platform components.
- Give all interactive controls a semantic label and at least a 48×48 dp touch target.
- Support dynamic text size, TalkBack, light/dark themes, edge-to-edge insets, and adaptive widths.
- Use no `!!`, `GlobalScope`, blocking I/O on the main thread, or `Activity` references in long-lived objects.
- Cancel callbacks, coroutines, probes, scans, audio players, and services deterministically.

Permission rules:

- Request only the permission needed for the current feature and explain its purpose before requesting it.
- Do  request `RECORD_AUDIO` in version 1; acoustic masking uses output playback only.
- If microphone capture is approved later, require `RECORD_AUDIO`, the correct foreground-service type and permission, visible notification, and an explicit foreground-start flow.
- Do  request `MODIFY_PHONE_STATE`, `BLUETOOTH_PRIVILEGED`, or hidden/system permissions in the normal app.
- Do  persist raw BSSID, device address, raw audio, or precise location unless a separately approved privacy review requires it.

## 8. Reliability, battery, and privacy

- Network probes are bounded by timeout, retry, byte, and session budgets.
- Stable connections use slower adaptive observation; recovery uses short bounded retries with exponential backoff and a stop condition.
- Never implement a permanent one-second traffic generator as a keep-alive strategy.
- Prefer system/offloaded keep-alives and network callbacks over polling.
- Foreground services must be user-visible, notification-backed, correctly typed, and stopped when the user stops the feature.
- Audio masking must default to off, expose volume and stop controls, release `AudioTrack`, and stop on failure or loss of the required output route.
- Logs must contain no tokens, credentials, raw audio, precise location, persistent radio identifiers, or third-party device identifiers.
- Keep processing local by default. Any user-configured network probe endpoint must be disclosed and must not become analytics.
- Store only the minimum local baseline/settings data and provide a wipe action.

## 9. Security review checklist

Before accepting a change, verify:

- [ ] No RF disruption, packet injection, flooding, deauthentication, or third-party targeting path exists.
- [ ] No arbitrary command execution or unvalidated Shizuku/root input exists.
- [ ] Permissions are least-privilege and requested at the point of use.
- [ ] All external inputs have type, size, range, and timeout validation.
- [ ] Network actions are allowlisted, bounded, and auditable.
- [ ] Errors fail closed and do leave a service, scan, or audio stream running.
- [ ] Dependencies are pinned and reviewed for vulnerabilities.

## 10. Verification gates

When an Android project exists, the minimum local gates are:

```text
./gradlew.bat lintDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat connectedDebugAndroidTest   # when an emulator/device is available
```

Run the first failing task in isolation when diagnosing a build. Do  claim physical acceptance from compilation, screenshots, mocks, or an empty `adb devices -l` result.

Report results in separate sections:

1. static/build evidence;
2. unit/instrumentation evidence;
3. physical-device evidence, including device/API/OEM and permission state;
4. remaining unavailable or unverified capabilities.

This repository currently has no Android source tree, so documentation validation is the only applicable gate for this phase.

## 11. Official platform references

Use current Android documentation as the authority when a local reference conflicts with platform behavior:

- [TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [ConnectivityManager and NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager)
- [Wi-Fi network suggestions](https://developer.android.com/develop/connectivity/wifi/wifi-suggest)
- [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Foreground-service declarations and permissions](https://developer.android.com/develop/background-work/services/fgs/declare)

## 12. Change discipline

- Keep changes minimal and scoped to the requested feature.
- Do not modify `docs/` or `porjectreferences/` unless explicitly requested.
- Do not delete, reset, clean, stash, or overwrite user files.
- Prefer `apply_patch` for tracked text changes.
- Update `PRD.md` when a product contract, permission, capability, or safety boundary changes.
