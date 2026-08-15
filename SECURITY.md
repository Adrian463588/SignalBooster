# Security Policy

## Supported Versions

Only the latest release and the current `main` branch receive active security updates.

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

---

## Ethical & Platform Safety Boundaries

SignalBooster is strictly engineered as a **defensive, diagnostic, and privacy resilience tool**. In compliance with legal, ethical, and Android platform guidelines, the application strictly adheres to the following boundaries:

- **Zero RF Jamming / Disruption**: The application does NOT transmit radio frequency interference, jam cellular/Wi-Fi/Bluetooth/GPS frequencies, or bypass FCC/ETSI radio regulations.
- **No Unauthorized Radio Manipulation**: No packet injection, deauthentication attacks, beacon flooding, or denial-of-service capabilities are implemented.
- **Strictly Bounded Privilege Gateway**: Shizuku or elevated capabilities are restricted to an explicit, typed, allowlisted set of diagnostic actions with user confirmation. No arbitrary shell execution is permitted.
- **Local-First Privacy Architecture**: All signal analysis, QoE scoring, and acoustic masking calculations execute strictly on-device. No raw audio, precise GPS coordinates, BSSIDs, cell identifiers, or persistent device identifiers are exfiltrated or uploaded.
- **Safe Acoustic Masking**: Masking is strictly an output playback mechanism with conservative volume caps and explicit foreground notification. The app does not covertly record audio or lock the microphone from other apps.

---

## Reporting a Vulnerability

If you discover a security vulnerability or safety concern within this project, please report it responsibly:

1. **Do not create a public GitHub issue.**
2. Send a detailed report describing the vulnerability, affected components, and proof of concept to the maintainer:
   - **Lead Maintainer**: Adrian Syah Abidin
   - **GitHub**: [@Adrian463588](https://github.com/Adrian463588)
3. You will receive an acknowledgment within 48 hours.
4. Validated security issues will be patched promptly with an accompanying advisory.
