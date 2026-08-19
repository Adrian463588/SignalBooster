# DESIGN.md — SignalBooster Design System & Anti-Slop Directive

## 1. Design Identity & Product Voice

SignalBooster is a utility application for network diagnostics, resilience, and defensive privacy. Its visual language is **precise, trustworthy, and content-first**, avoiding ambiguous decoration, aggressive gradients, and false certainty.

### Core Visual Principles
- **Science, Not a Vibe**: Every visual element serves an informative or interactive purpose.
- **Signal Over Noise**: Information hierarchy is strictly ordered by user priority. Unnecessary cards, dividers, and decorative icons are excluded.
- **Fail-Closed & Honest Telemetry**: Missing data is presented transparently (`"—"`) without optimistic guessing.
- **Material 3 Expressive**: Dynamic theming, spring-based contextual motion, and adaptive layouts across mobile and wide-screen/tablet form factors.

---

## 2. Information & Interaction Hierarchy

Each screen strictly follows this 5-level visual hierarchy:

```text
Level 1 — Primary Status / Content (QoE Gauge, Connection State, Radio Posture)
Level 2 — Primary Action (Run Quality Probe, Execute Recovery, Toggle Masking)
Level 3 — Key Measurements & Signal Metrics (FlowRow Grid: RTT, Jitter, Loss, Speed)
Level 4 — Secondary Controls & Settings Hand-offs (FilterChips, Preferred RAT, Rescan)
Level 5 — Contextual Metadata & Limitations (Disclosures, Timestamp, Confidence Tier)
```

### Visual Budget Limits per Screen:
- **Maximum 1 Primary Action** with high-emphasis styling per viewport.
- **Maximum 2 Card Nesting Levels** (No "card inside card inside card" patterns).
- **All Interactive Touch Targets $\ge 48\times 48\,\text{dp}$**.

---

## 3. Color Roles & Semantic Tokens

SignalBooster uses Material 3 semantic color tokens supporting dynamic color (Android 12+ / API 31+).

| Role | Semantic Meaning | Token |
| :--- | :--- | :--- |
| **Primary** | Core telemetry, active status, validated connections | `MaterialTheme.colorScheme.primary` |
| **Primary Container** | Active state badges, selected filters | `MaterialTheme.colorScheme.primaryContainer` |
| **Tertiary / Warning** | Unvalidated network, moderate interference, band steering | `MaterialTheme.colorScheme.tertiary` |
| **Error** | Captive portal, high RF congestion, disconnected state | `MaterialTheme.colorScheme.error` |
| **Surface Variant** | Telemetry metric tiles, passive observation cards | `MaterialTheme.colorScheme.surfaceVariant` |
| **Outline / Outline Variant** | Card boundaries, non-distracting dividers | `MaterialTheme.colorScheme.outlineVariant` |

---

## 4. Typography Scale

Follows the Material 3 Type Scale with deliberate sizing:

| Text Style | Usage | Properties |
| :--- | :--- | :--- |
| **Display Large / Medium** | Numeric QoE Score (0–100) | Bold, tabular numbers |
| **Title Large / Medium** | Screen headings, primary card titles | Bold, high contrast |
| **Title Small** | Telemetry section headers, probe categories | Semi-bold |
| **Body Medium / Small** | Measurement values, observation descriptions | Regular/Medium |
| **Label Small** | Confidence pills, category tags, uppercase metadata | Bold, letter spacing 1sp |

---

## 5. Contextual Motion & Microinteractions

Animations provide direct physical feedback without delaying user interaction:

1. **Spring-Based Transitions**:
   - Signal bars and quality gauges use `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)`.
2. **Pulse & Liveness Indicators**:
   - Adaptive guard and active monitoring dots use `rememberInfiniteTransition` with subtle alpha/scale pulsing (`0.8f` to `1.0f`).
3. **Live Waveform Visualizer**:
   - Acoustic masking visualizer animates 16 vertical bars using staggered infinite repeatable tweens representing active synthetic noise output.
4. **Haptic Feedback**:
   - Every interactive button, probe trigger, filter chip, and dialog confirmation triggers `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` or `TextHandleMove`.

---

## 6. Adaptive Multi-Pane Layout Rules

- **Compact (< 600 dp — Phones)**: Single-column vertical scroll with bottom `NavigationBar`.
- **Expanded ($\ge 600$ dp — Tablets / Foldables / Landscape)**:
  - Navigation switches to left-side `NavigationRail`.
  - Screens split into **Adaptive Two-Column Multi-Pane** layouts (Column 1: Status & Telemetry; Column 2: Probes, Steering Advice, & Recovery Controls).
- **Edge-to-Edge**: Native `enableEdgeToEdge()` with system bar insets handled via `innerPadding`.

---

## 7. Anti-Slop Checklist (Delivery Gate)

- [x] **No generic gradients or decorative orb backgrounds**.
- [x] **No card-in-card nesting beyond 2 levels**.
- [x] **No fabricated or simulated sensor data** (100% real platform APIs or explicit `INSUFFICIENT_DATA`).
- [x] **All touch targets $\ge 48\,\text{dp}$**.
- [x] **All interactive components have semantic accessibility descriptions**.
- [x] **Fail-closed behavior on missing permissions**.
