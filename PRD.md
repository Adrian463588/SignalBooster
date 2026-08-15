# Product Requirements Document — SignalBooster

**Status:** Draft

**Platform:** Android only

**Implementation baseline:** Kotlin, Jetpack Compose, Material 3, Clean Architecture, MVVM, Hilt, coroutines, Flow/StateFlow, and API 31 minimum.

## 1. Product summary

SignalBooster is a local-first Android application that helps users understand and stabilize their own connectivity in difficult environments such as festivals, stations, campuses, and crowded venues. It measures Wi-Fi and cellular quality, recommends supported recovery actions, and exposes privacy-oriented observations without pretending to increase radio transmit power.

The privacy feature set is defensive. It provides local radio/privacy posture, confidence-based interference observations, and optional user-visible acoustic masking. It does not transmit a radio jammer, attack nearby devices, block another application’s microphone, or guarantee that a recording device is defeated.

## 2. Problem statement

Users often see a 5G icon or a strong Wi-Fi signal while applications remain slow or disconnected. They need measured evidence to distinguish weak coverage, congestion, validation failure, roaming, captive portals, and handover problems. They also need privacy controls that clearly communicate what the phone can observe and what Android cannot guarantee.

The application must replace vague “signal booster” and “jammer” claims with observable metrics, supported actions, confidence labels, and honest unavailable states.

## 3. Goals

- Monitor active Wi-Fi, cellular, VPN, and validated-internet state.
- Keep user-owned app connectivity resilient through callbacks, bounded probes, system keep-alives where applicable, and controlled recovery.
- Compare Wi-Fi, LTE, and 5G using measured quality of experience rather than RSSI alone.
- Provide crowd-mode recommendations based on measured performance and available band/channel observations.
- Offer mandatory Shizuku assistance only for explicit, allowlisted, device-supported actions.
- Detect unusual local connectivity/interference patterns with confidence tiers and plain-language reasons.
- Provide safe, visible, local acoustic masking as an optional privacy aid.
- Keep processing local by default and make local baselines/settings removable.
- jam or disrupt Wi-Fi, Bluetooth, cellular, GPS, or other RF services;
- flood, deauthenticate, inject packets into, or target third-party devices;
- control an access point’s RF band steering;
- claim unrestricted cellular band scanning or guaranteed LTE/5G forcing;
- require root/Sui in version 1;
- capture microphone input in version 1;
- seize, lock, or deny another application access to the microphone;
- use hidden or unattended audio playback;
- claim acoustic masking is inaudible, universally effective, or safe at arbitrary volume;
- upload raw audio, precise location, credentials, or persistent radio/device identifiers;
- include analytics, advertising, or a backend service.

## 4. Non-goals and DON'T — PROHIBITED CODING RULES

The following rules are **mandatory** for all coding agents.

1. **DO NOT violate YAGNI.**
   - Do not implement features that are not explicitly required.
   - Do not prepare speculative functionality for hypothetical future requirements.
   - Do not add code only because "it might be useful later."

2. **DO NOT over-engineer the solution.**
   - Prefer the simplest correct implementation.
   - Do not introduce unnecessary architectural layers.
   - Do not turn a simple feature into a framework.

3. **DO NOT introduce abstractions prematurely.**
   - Do not create interfaces, abstract classes, factories, adapters, strategies, or repositories unless they solve a real current problem.
   - Do not abstract code solely to make it "more extensible."

4. **DO NOT create unnecessary wrapper classes.**
   - Avoid wrappers that only forward calls without adding meaningful behavior.

5. **DO NOT create unnecessary interfaces.**
   - Do not create an interface when there is only one implementation unless an actual architectural or testing requirement justifies it.

6. **DO NOT create speculative extension points.**
   - No unused hooks.
   - No unused callbacks.
   - No unused plugin systems.
   - No unused configuration switches.

7. **DO NOT create premature generic solutions.**
   - Solve the actual use case first.
   - Do not design for unknown future use cases.

8. **DO NOT create a custom framework when an ordinary function, class, or existing library is sufficient.**

9. **DO NOT introduce microservices, event buses, message brokers, or distributed architecture for problems that can be solved locally.**

10. **DO NOT use Design Patterns merely for the sake of using Design Patterns.**
    - A pattern must solve a concrete problem.
    - Never force Factory, Strategy, Observer, Builder, Adapter, or similar patterns without justification.

11. **DO NOT create deep inheritance hierarchies.**
    - Prefer simple composition when appropriate.
    - Avoid inheritance chains that make behavior difficult to trace.

12. **DO NOT create God Classes or God Objects.**
    - Classes must have focused responsibilities.

13. **DO NOT create massive functions.**
    - Functions should perform a clear, understandable responsibility.

14. **DO NOT create excessive numbers of tiny functions/classes that make simple logic difficult to follow.**
    - Clean code does not mean maximum fragmentation.

15. **DO NOT create excessive folder/package/module structures.**
    - Every layer and directory must have a clear purpose.

16. **DO NOT create unnecessary `Manager`, `Helper`, `Utils`, `Base`, `Common`, or `Core` classes.**
    - Prefer names representing concrete responsibilities.

17. **DO NOT duplicate business logic.**
    - Maintain one authoritative implementation for the same business rule.

18. **DO NOT apply DRY blindly.**
    - Do not merge unrelated code merely because it currently looks similar.
    - Only abstract duplication when the shared concept is real and stable.

19. **DO NOT create unnecessary state.**
    - Avoid duplicated, derived, or synchronized state when the value can be calculated from an existing source of truth.

20. **DO NOT create multiple sources of truth.**
    - One piece of domain state should have one authoritative owner.

21. **DO NOT introduce unnecessary caching.**
    - Add caching only when there is a demonstrated performance requirement.

22. **DO NOT perform premature optimization.**
    - Correctness, readability, and maintainability come first unless profiling identifies a real bottleneck.

23. **DO NOT introduce concurrency, coroutines, threads, queues, or async processing when synchronous execution is sufficient.**

24. **DO NOT add retry mechanisms everywhere.**
    - Retries must address a known transient failure and have bounded retry/backoff behavior.

25. **DO NOT add fallback behavior that hides real failures.**
    - Failures must remain observable and diagnosable.

26. **DO NOT swallow exceptions.**
    - Never use empty `catch` blocks or silently ignore errors.

27. **DO NOT use exceptions for normal control flow.**

28. **DO NOT hide bugs using excessive null checks, default values, or silent fallbacks.**
    - Fix the underlying cause.

29. **DO NOT introduce unnecessary feature flags.**
    - Every flag must have a real lifecycle and removal strategy.

30. **DO NOT add configuration options without an actual requirement.**
    - Avoid unnecessary configurability.

31. **DO NOT hard-code duplicated magic numbers or unexplained strings.**
    - Use meaningful constants where they improve clarity.

32. **DO NOT create boolean-parameter-heavy APIs.**
    - Avoid calls whose intent cannot be understood without reading the implementation.

33. **DO NOT use vague names.**
    - Avoid meaningless names such as:
      - `data`
      - `temp`
      - `stuff`
      - `obj`
      - `manager`
      - `helper`
      - `processData`
      - `handleThing`

34. **DO NOT write deeply nested control flow.**
    - Avoid excessive nested `if`, `when`, loops, callbacks, and try/catch blocks.

35. **DO NOT use clever code when straightforward code is easier to understand.**

36. **DO NOT use reflection, metaprogramming, dynamic dispatch, or code generation unless clearly required.**

37. **DO NOT add dependencies for trivial functionality.**
    - Every new dependency must provide meaningful value.

38. **DO NOT duplicate functionality already available in the language, standard library, platform SDK, or an existing approved project dependency.**

39. **DO NOT rewrite working modules unnecessarily.**
    - Prefer targeted changes over broad rewrites.

40. **DO NOT perform unrelated refactoring while implementing a focused task.**

41. **DO NOT change public APIs without a requirement.**

42. **DO NOT break backward compatibility unnecessarily.**

43. **DO NOT change architecture merely because another architecture looks cleaner theoretically.**

44. **DO NOT rename large numbers of files/classes without functional benefit.**

45. **DO NOT generate unnecessary boilerplate.**

46. **DO NOT generate excessive comments explaining obvious code.**
    - Comments should explain **why**, constraints, assumptions, or non-obvious behavior.

47. **DO NOT leave commented-out code.**
    - Delete dead code; version control already stores history.

48. **DO NOT leave unused imports, variables, functions, classes, resources, dependencies, or configuration.**

49. **DO NOT introduce dead code or unreachable branches.**

50. **DO NOT leave placeholder implementations in production paths.**
    - No dummy results.
    - No hard-coded fake success.
    - No fake sensor values.
    - No fake API responses.
    - No template predictions presented as real output.

51. **DO NOT replace real implementation with mocks outside tests.**

52. **DO NOT add TODO/FIXME comments as substitutes for completing required functionality.**

53. **DO NOT bypass validation merely to make the application run.**

54. **DO NOT disable tests because they fail.**
    - Fix the implementation or correct an invalid test.

55. **DO NOT disable lint/static-analysis rules merely to suppress legitimate errors.**

56. **DO NOT suppress compiler warnings without understanding their root cause.**

57. **DO NOT use broad suppression annotations when a localized correction is possible.**

58. **DO NOT duplicate tests that verify exactly the same behavior without adding coverage.**

59. **DO NOT create excessive mocks.**
    - Test behavior at the appropriate boundary.

60. **DO NOT test implementation details unnecessarily.**
    - Prefer externally observable behavior.

61. **DO NOT add logging everywhere.**
    - Log meaningful operational information only.

62. **DO NOT log secrets, credentials, tokens, passwords, PII, or sensitive raw data.**

63. **DO NOT add security mechanisms that provide no actual security benefit.**

64. **DO NOT implement custom cryptography.**
    - Use established platform/security libraries.

65. **DO NOT store credentials or secrets directly in source code.**

66. **DO NOT introduce unnecessary global mutable state or Singleton objects.**

67. **DO NOT tightly couple UI, domain logic, persistence, networking, and hardware logic when they have clearly different responsibilities.**

68. **DO NOT add architectural layers that contain no meaningful responsibility.**

69. **DO NOT create pass-through layers such as:**

`UI → Controller → Manager → Service → Handler → Processor → Repository`

when the same requirement can be expressed clearly with substantially fewer boundaries.

70. **DO NOT sacrifice readability for theoretical architectural purity.**

71. **DO NOT sacrifice maintainability for fewer lines of code.**

72. **DO NOT sacrifice simplicity for maximum reusability.**

73. **DO NOT optimize for hypothetical scale.**
    - Design for current documented requirements and realistic constraints.

74. **DO NOT anticipate requirements that are absent from the specification.**

75. **DO NOT expand the task scope autonomously.**
    - Implement only what is necessary to satisfy the specification and fix directly related issues.

76. **DO NOT modify stable working code without a concrete reason.**

77. **DO NOT create files merely to make the architecture appear more sophisticated.**

78. **DO NOT generate documentation files, diagrams, reports, migrations, scripts, or configuration unless required by the task.**

79. **DO NOT duplicate an existing component when it can be safely reused.**

80. **DO NOT continue adding complexity once the requirement is correctly solved.**

## Mandatory Decision Rule

Before adding any:

- class
- interface
- abstraction
- layer
- dependency
- configuration
- architectural pattern
- background worker
- cache
- retry mechanism
- feature flag
- extension point

the agent MUST ask internally:

> **Is this required to solve a documented requirement or demonstrated problem right now?**

If the answer is **NO**, **DO NOT ADD IT**.

## Priority

When multiple technically valid solutions exist, prefer in this order:

**Correctness → Simplicity → Readability → Testability → Maintainability → Performance when measured → Extensibility only when required**

Follow:

**YAGNI + KISS + DRY + SOLID**

but never apply these principles mechanically when doing so would increase complexity.

**The best implementation is the smallest, clearest, production-ready solution that fully satisfies the current requirement without creating unnecessary future architecture.**

## 5. Target users and use cases

### Personal connectivity user

The user wants to know whether Wi-Fi or cellular is currently healthier and receive a supported action such as retry, reconnect, or open Android network settings.

### Crowded-venue user

The user wants measured evidence about congestion, latency, loss, and available Wi-Fi/cellular alternatives while accepting that the application cannot control the carrier or access point.

### Privacy-conscious user

The user wants a local view of Bluetooth/Wi-Fi/cellular posture, confidence-based unusual-interference observations, and an explicitly controlled acoustic masking session.

## 6. Product principles

1. **Evidence over claims:** every recommendation includes measurements, timestamp, confidence, and reason.
2. **Fail closed:** missing permission, unavailable hardware, dead Shizuku binder, or unsupported OEM behavior produces an unavailable state.
3. **User remains in control:** privileged actions, network changes, Bluetooth advertising, and audio playback require explicit user intent.
4. **Local first:** telemetry is not uploaded; local baselines can be inspected and erased.
5. **Least privilege:** request only the permission required for the active feature.
6. **Reversible actions:** prefer Settings hand-offs and bounded retries over persistent system changes.

## 7. Functional requirements

### FR-01 — Connection dashboard

The dashboard shall display:

- active transport and network validation state;
- Wi-Fi SSID/frequency/link information when permitted, with identifiers redacted where possible;
- cellular operator, SIM selection, LTE/5G/NSA classification, and available signal metrics;
- metered/captive-portal/VPN state where exposed by Android;
- last update timestamp and data availability state.

The dashboard must show `INSUFFICIENT_DATA` or `PERMISSION_REQUIRED` instead of inventing values.

### FR-02 — Wi-Fi keep-alive and health monitoring

The application shall:

- observe network changes through `NetworkCallback`;
- run bounded, network-specific DNS/TCP/TLS/HTTPS probes only while the user enables monitoring;
- calculate latency, jitter, timeout/loss ratio, and optional throughput;
- use adaptive intervals and a visible battery/data budget;
- use `SocketKeepalive` only for applicable app-owned sockets;
- stop callbacks, probes, and jobs when monitoring stops or the owning lifecycle ends.

Success means the application accurately reports health and maintains its own monitored path. It does not mean the Android Wi-Fi radio is forced to remain connected.

### FR-03 — Smart Wi-Fi reconnection

The application shall:

- detect loss, validation failure, captive portal, and changed link properties;
- offer a bounded reconnect/retry policy;
- use `WifiNetworkSuggestion` or a user-confirmed Android Settings flow where supported;
- expose the platform result and reason to the user;
- return `CAPABILITY_UNAVAILABLE` when direct reconnection is not supported.

The platform remains authoritative for network selection.

### FR-04 — Wi-Fi/cellular/4G/5G comparison

The application shall score available paths using:

- validation state;
- application-level latency and jitter;
- timeout/loss ratio;
- measured throughput when the user starts a test;
- LTE/NR signal metrics such as RSRP, RSRQ, RSSNR/SINR when available;
- current transport and metering constraints.

The result shall be one of `STAY`, `TRY_ALTERNATIVE`, `OPEN_SETTINGS`, or `INSUFFICIENT_DATA`, with a reason. A normal application shall not claim to force a radio technology.

### FR-05 — Band-aware crowd mode

Crowd Mode shall:

- show available Wi-Fi frequency/channel observations when Android exposes them;
- compare candidate networks using measured QoE, not channel number or RSSI alone;
- use serving and neighboring cellular information when permission and hardware provide it;
- mark congestion estimates as heuristic and confidence-based;
- recommend a user action or Settings hand-off rather than controlling the access point or performing unrestricted modem scans.

### FR-06 — Capability and Shizuku screen

The screen shall distinguish:

- normal Android API availability;
- Shizuku installed/running/authorized state;
- action-specific support;
- binder death or action failure;
- deferred root/Sui support.

Every privileged action must be allowlisted, user-confirmed, typed, auditable, and reversible where possible. No arbitrary command text may come from the UI.

### FR-07 — Passive privacy posture

The privacy screen shall expose, where supported:

- Bluetooth enabled/disabled and discoverability/connection posture;
- Wi-Fi security/validation posture and captive-portal state;
- cellular availability changes and multi-signal anomaly observations;
- permission state and links to relevant Android privacy/settings controls;
- optional local BLE corroboration status.

The application shall not identify, connect to, or target unrelated nearby devices. BLE corroboration must be opt-in, connectionless, anonymous, short-lived, and non-persistent.

### FR-08 — Confidence-based interference observation

The classifier shall combine available signals such as abruptness, breadth across SIMs/carriers, neighbor-cell change, cross-radio contrast, local baseline deviation, and optional peer corroboration.

It shall return a confidence tier such as:

- `NORMAL_OR_UNKNOWN`;
- `POSSIBLE_LOCALIZED_INTERFERENCE`;
- `LIKELY_LOCALIZED_INTERFERENCE`.

Each result shall include the contributing observations, confidence, peer count if used, timestamp, and a plain-language limitation. It shall never present detection as certainty.

### FR-09 — Acoustic masking

Version 1 may provide a user-visible local audio-output session that:

- is off by default;
- starts only after explicit user action;
- shows an ongoing notification while active;
- exposes safe volume and stop controls;
- stops and releases audio resources on cancellation, failure, or output-route loss;
- does not capture microphone input;
- does not claim guaranteed protection or interference with another device.

The implementation must not expose attack-tuned waveform parameters or hidden/stealth modes in the UI.

## 8. Domain contracts

The domain layer shall define immutable, framework-independent contracts equivalent to the following:

| Contract                 | Responsibility                                                                        |
| ------------------------ | ------------------------------------------------------------------------------------- |
| `NetworkSnapshot`        | Current transport, validation, metering, identifiers, timestamp, and availability.    |
| `QualityMetrics`         | RTT, jitter, timeout/loss ratio, throughput, probe scope, and measurement confidence. |
| `NetworkRecommendation`  | Suggested action, evidence, confidence, and limitation.                               |
| `RecoveryAction`         | A finite allowlisted action such as retry, suggestion update, or open Settings.       |
| `CapabilityState`        | Normal API, Shizuku, unsupported, unauthorized, dead, or failed capability.           |
| `InterferenceConfidence` | Tier, reason, observations, peer count, and timestamp; never a bare boolean.          |
| `PrivacyMode`            | `OFF`, `PASSIVE_POSTURE`, or `ACOUSTIC_MASKING`.                                      |
| `AcousticMaskState`      | `STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, or `FAILED`.                            |

Required interfaces:

- `NetworkMonitor`;
- `QualityProbe`;
- `RecoveryCoordinator`;
- `PrivilegeGateway`;
- `RadioTelemetrySource`;
- `InterferenceClassifier`;
- `AcousticMaskingController`.

Platform types such as `NetworkCapabilities`, `CellInfo`, `BluetoothDevice`, and `AudioTrack` must not leak into these domain contracts.

## 9. UI and navigation

The first navigation surface shall contain:

1. **Dashboard:** current connection, health, recommendation, and active service state;
2. **Diagnostics:** Wi-Fi/cellular metrics, SIM selector, probe controls, and history;
3. **Crowd Mode:** band-aware observations and measured comparison;
4. **Privacy:** posture, confidence-based interference observations, and acoustic masking;
5. **Settings:** permissions, capability details, budgets, endpoint disclosure, and data wipe.

All screens must support loading, available, unavailable, permission-required, failed, and empty states. Child composables receive immutable state and callbacks, not ViewModel instances.

## 10. Permission and platform matrix

| Capability                | Permission/API                                                         | Product rule                                                                           |
| ------------------------- | ---------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Internet probes           | `INTERNET`, `ACCESS_NETWORK_STATE`                                     | User-visible, bounded, no analytics upload.                                            |
| Wi-Fi state               | `ACCESS_WIFI_STATE`, applicable nearby-Wi-Fi permission                | Request only for the selected diagnostic feature.                                      |
| Cellular/SIM metrics      | `READ_PHONE_STATE`, location permission where Android requires it      | Explain that Android treats detailed cell data as location-sensitive; process locally. |
| BLE corroboration         | `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`                                | Optional, runtime-granted, `neverForLocation` only if true. No pairing or targeting.   |
| Notifications             | `POST_NOTIFICATIONS`                                                   | Required for clear foreground-service status on supported Android versions.            |
| Acoustic output           | Media playback/audio APIs                                              | No microphone permission in version 1; visible controls and notification.              |
| Future microphone capture | `RECORD_AUDIO` plus correct microphone foreground-service declarations | Not in version 1; requires a separate privacy and safety review.                       |
| Direct modem forcing      | Privileged API/carrier privilege                                       | Not assumed or requested by the normal app.                                            |

The manifest must not declare `MODIFY_PHONE_STATE`, `BLUETOOTH_PRIVILEGED`, or other hidden/system permissions for the normal application.

## 11. Privacy and data requirements

- No backend, analytics, advertising, or crash-reporting service in the initial product contract.
- Store settings and optional coarse local baselines only as needed for recommendations.
- Do  persist raw audio, exact BSSID/device addresses, credentials, or precise location.
- Provide a “Delete local data” action and document exactly what it removes.
- Redact identifiers in logs and UI.
- Network probe endpoints must be disclosed, configurable only through a validated allowlist, and subject to timeout/byte budgets.
- Every privacy-sensitive feature must show permission state, active state, last update, and limitation text.

## 12. Quality attributes

- **Correctness:** no fabricated metrics or unsupported capability claims.
- **Maintainability:** SOLID boundaries, DRY policies, KDoc, small composables, and single-purpose adapters.
- **Reliability:** deterministic cancellation, bounded retries, explicit error states, and no silent background work.
- **Performance:** responsive Compose UI, no main-thread I/O, stable recomposition, and measured battery/data budgets.
- **Accessibility:** TalkBack semantics, 48×48 dp targets, dynamic text, contrast, and non-color-only status.

## 13. Test and acceptance plan

### Unit tests

- recommendation scoring with missing and conflicting metrics;
- stable/degraded/recovery probe policy and retry termination;
- permission/capability state mapping;
- cellular/Wi-Fi confidence fusion;
- gradual signal fade versus abrupt multi-signal change;
- Shizuku unavailable, binder-dead, denied, and successful allowlisted action;
- acoustic masking state transitions, cancellation, and failure cleanup;
- local-data wipe policy.

### Integration and UI tests

- `NetworkCallback` registration/unregistration and lifecycle restart;
- network-specific probe timeout and cancellation;
- Wi-Fi suggestion result handling and Settings hand-off;
- Compose states for loading, unavailable, permission required, failure, and recommendations;
- notification and service stop behavior;
- permission rationale and denial flows;
- no microphone permission requested by the version-1 manifest.

### Build gates

When the Android project exists, all of the following must pass:

```text
./gradlew.bat lintDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat connectedDebugAndroidTest   # when a device/emulator is available
```

Physical acceptance must be reported separately and must include Android version, OEM/model, SIM/Wi-Fi setup, granted permissions, Shizuku state if used, and whether the device was reachable through ADB. A passing build is not physical feature acceptance.

## 14. Release gates

The application may not be described as production-ready until:

- Do RF and microphone-control behavior is absent;
- the normal API path works without Shizuku;
- all supported and unavailable states are visible and tested;
- Android lint, unit tests, build, and applicable UI/instrumentation tests pass;
- permission and privacy disclosures match the actual manifest and runtime behavior;
- acoustic masking is visibly user-controlled and safety-reviewed;
- physical-device evidence covers at least one supported Android device and clearly lists unverified OEM-specific behavior.

## 15. Reference sources

Local product and design references:

- `docs/Docs1.md` — connectivity stabilization and capability tiers;
- `docs/Docs2.md` — Android network, telephony, permissions, and Compose guidance;
- `docs/Docs3.md` and `docs/Docs4.md` — acoustic masking concepts and limitations;
- `porjectreferences/reference5` — confidence-based local signal fusion;
- `porjectreferences/reference6` — audio repository/player separation;
- `porjectreferences/reference2`, `reference4`, `refrence1`, and `reference3` — included offensive or hardware-jamming material.

Official platform references:

- [TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [ConnectivityManager and NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager)
- [Wi-Fi network suggestions](https://developer.android.com/develop/connectivity/wifi/wifi-suggest)
- [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Foreground-service declarations and permissions](https://developer.android.com/develop/background-work/services/fgs/declare)
