<div align="center">

# 🎧 HearYet

**Watch together. Hear in sync. No internet needed.**

HearYet turns any Android device into a **host** that streams its audio to nearby **guest** devices over Google Nearby Connections — so everyone's headphones play the same thing, synchronized. A watch-party app for one room, one network-free pocket.

[![CI](https://img.shields.io/github/actions/workflow/status/OCTOBER-sk/HearYet/android_build.yaml?branch=main&label=build%20%26%20tests&logo=github)](https://github.com/OCTOBER-sk/HearYet/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2023-3DDC84?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

</div>

---

## What is HearYet?

HearYet is a fork of [NextPlayer](https://github.com/anilbeesetti/nextplayer) — a Kotlin/Compose video player — rebuilt around a single idea: **local, synchronized group listening**.

- The **Host** plays a video normally on their own screen.
- Each **Guest** receives only the **audio** (raw 16-bit PCM, 48 kHz stereo) over Google Nearby Connections — P2P star topology, **no internet, no shared Wi-Fi, no account**.
- Guests hear it through their own Bluetooth/wired output, synchronized to the Host within a target ~250 ms lookahead.

## How it works

```
Host phone          Nearby Connections (P2P)        Guest phones
┌─────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│ Video plays │────▶│ Clock sync (Cristian │────▶│ AudioTrack plays │
│ PCM tapped  │     │ + batch, <5ms gate) │     │ in sync ± lookahead│
│ QR + 6-char │     │ Drift correction    │     │ (Bluetooth-aware) │
│ code share  │     │ Heartbeat (5s)      │     │ Rejoin after kill │
└─────────────┘     └─────────────────────┘     └──────────────────┘
```

- **Join** by scanning the Host's QR or typing a 6-character code — both converge on the same discovery path.
- **Clock sync** is a Cristian's-algorithm-style batch estimator (8–10 samples, RTT-filtered, median) with a 5 ms convergence gate.
- **Drift** is corrected continuously via tiny playback-speed nudges (±0.5%/±1.5%) — never abrupt jumps.
- **Resilience**: 5 s heartbeats, 15 s host-unreachable detection, session persistence + rejoin after process death, guest greeting chime, screen-off playback protection.
- **Privacy-first**: fully local, zero telemetry, zero accounts.

## Features

- 📡 **Nearby Connections P2P** — works with Bluetooth/Wi-Fi Direct, no router required
- 🔊 **Audio-only fan-out** — video stays on the host's screen
- 🎯 **Synchronized playback** — clock-sync + drift-correction math with a real convergence gate
- 🔒 **Local-first** — no internet, no accounts, no data leaves the room
- 📱 **QR or code join** — 6-char Crockford Base32 code, protocol-versioned payloads
- 🔄 **Crash recovery** — guests rejoin automatically after process death
- 🎵 **Guest greeting chime** — anti-spam identity handling, focus-safe
- 🎨 **Material 3 Compose UI** — modern, familiar NextPlayer base (34-language fork lineage)

## Project status

> **Honest status:** the session layer is fully implemented and **CI-verified** (build + ~141 unit/Robolectric tests + ktlint green). A deep end-to-end audit (2026-08) found 15 real bugs — **all fixed and verified**. One blocker remains and it is *not* code: the 5 ms clock-sync convergence gate has never been met on the reference phones, so the 15 ms degraded-mode opt-in is compiled off until the [device calibration pass](DEVICE_CALIBRATION_CHECKLIST.md) is run. The app is ready to be made to work — the phones decide the last mile.

## Getting started

```bash
# Clone (the Android project lives in hearyet/)
git clone https://github.com/OCTOBER-sk/HearYet.git && cd HearYet/hearyet

# Build the debug APK
./gradlew assembleDebug

# Install on a device
./gradlew :app:installDebug
```

1. Open the app on the **host** device → **Create** → pick a video → **Start playback**.
2. Open the app on each **guest** device → **Join** → scan the QR (or type the code).
3. Put on your headphones. Everyone hears the same thing, in sync.

## Documentation

| Doc | What it covers |
|---|---|
| [HearYet_BACKEND.md](HearYet_BACKEND.md) | The definitive architecture + sync math (spec v2) |
| [HEARYET_DEVICE_VERIFICATION_PLAN.md](HEARYET_DEVICE_VERIFICATION_PLAN.md) | Phased on-device verification plan |
| [DEVICE_CALIBRATION_CHECKLIST.md](DEVICE_CALIBRATION_CHECKLIST.md) | §16 calibration runbook — the C-1 gate decision |
| [HEARYET_BACKEND_KNOWLEDGE.md](HEARYET_BACKEND_KNOWLEDGE.md) | Operational knowledge base |
| [HEARYET_ZEUS_REPORT.md](HEARYET_ZEUS_REPORT.md) | Full audit report (15 bugs found → fixed) |

## Architecture

13 Gradle modules: `:app` (session layer: coordinator, clock-sync, scheduler, drift, transport, QR, chime) + `:core:{common,data,database,datastore,domain,media,model,ui}` + `:feature:{player,settings,videopicker,network}`. Kotlin 2.4.10, Jetpack Compose (Material 3, navigation3), Hilt, Room, DataStore, Media3 1.10.1, play-services-nearby 19.3.0.

## Roadmap

- [ ] **§16 device calibration** — decide the convergence gate with real measurements (blocker)
- [ ] Degraded-mode opt-in (15 ms) once calibration justifies it
- [ ] Guest volume/mute controls polish, multi-guest stress test (8+ devices)

## License

MIT — see [LICENSE](hearyet/LICENSE). Fork of [NextPlayer](https://github.com/anilbeesetti/nextplayer) (MIT, by Anil Beesetti). HearYet-specific code (`:app` session layer, `core/ui` session components) is new work by the OCTOBER-sk project.
