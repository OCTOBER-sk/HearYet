<div align="center"><h1>
  <img src="assets/hearyet-logo.png" alt="HearYet" width="42" height="42" align="middle">
  <span>&nbsp;HearYet</span>
</h1><p><strong>Watch together. Hear in sync. No internet needed.</strong></p><p>
  An offline Android app that lets a host play a video while guests receive its audio on their own headphones — synchronized in real time.
</p><p>
  <a href="https://github.com/OCTOBER-sk/HearYet/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/OCTOBER-sk/HearYet/android_build.yaml?branch=main&label=build%20%26%20tests&logo=github" alt="Build & Tests">
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  </a>
  <a href="https://developer.android.com">
    <img src="https://img.shields.io/badge/Android-API%2023%2B-3DDC84?logo=android&logoColor=white" alt="Android">
  </a>
  <a href="hearyet/LICENSE">
    <img src="https://img.shields.io/badge/license-MIT- blue" alt="MIT License">
  </a>
</p></div>---

Overview

HearYet is a local-first Android media player built for synchronized group listening.

The host plays a video normally on their device. Guests connect directly to the host using Google Nearby Connections and receive only the audio through their own headphones.

There is no internet dependency, shared Wi-Fi requirement, or account system.

Host
  │
  │  Nearby Connections
  │  synchronized audio
  ├──────────────┬──────────────┐
  ▼              ▼              ▼
Guest 1        Guest 2        Guest 3
🎧             🎧             🎧

HearYet is a fork of "NextPlayer" (https://github.com/anilbeesetti/nextplayer).

---

Features

- Offline peer-to-peer audio streaming
- Google Nearby Connections transport
- Host / guest session architecture
- QR code and 6-character session joining
- Audio-only streaming to guests
- Clock synchronization with RTT filtering
- Median-based clock offset estimation
- Synchronization convergence gate
- Continuous playback drift correction
- Timestamp-based guest scheduling
- Host heartbeat and failure detection
- Session persistence
- Automatic guest rejoin
- Protocol versioning
- Guest greeting chime
- Audio focus handling
- Screen-off playback
- Jetpack Compose + Material 3

---

How It Works

Host

1. Creates a HearYet session.
2. Shares the generated QR code or 6-character code.
3. Selects and plays a video.
4. HearYet extracts the audio from the playback pipeline.
5. Audio is streamed to connected guests.

Guest

1. Scans the QR code or enters the session code.
2. Connects to the host through Nearby Connections.
3. Synchronizes its clock with the host.
4. Receives timestamped PCM audio.
5. Schedules playback against its local clock.
6. Continuously corrects small timing differences.

Synchronization

HearYet performs an initial clock synchronization using 8–10 samples with RTT filtering and median offset estimation.

Guest playback is targeted using:

Host timestamp
      +
Clock offset
      +
Lookahead
      =
Guest playback target

Small playback-speed adjustments are then used to correct drift continuously.

---

Architecture

HearYet currently contains 13 Gradle modules.

hearyet/
│
├── app/
│   ├── session coordinator
│   ├── clock synchronization
│   ├── audio scheduler
│   ├── drift correction
│   ├── Nearby transport
│   ├── QR / session management
│   └── session chime
│
├── core/
│   ├── common
│   ├── data
│   ├── database
│   ├── datastore
│   ├── domain
│   ├── media
│   ├── model
│   └── ui
│
└── feature/
    ├── player
    ├── settings
    ├── videopicker
    └── network

Stack

Component| Technology
Language| Kotlin 2.4.10
UI| Jetpack Compose
Design| Material 3
DI| Hilt
Database| Room
Storage| DataStore
Media| Media3 1.10.1
Networking| Nearby Connections 19.3.0
Testing| JVM + Robolectric
Lint| ktlint
CI| GitHub Actions
Minimum Android| API 23

---

Project Status

The session layer is implemented and verified through CI.

Completed

- Session management
- Nearby Connections transport
- QR / code-based joining
- Clock synchronization
- Audio scheduling
- Drift correction
- Heartbeat monitoring
- Session persistence
- Automatic rejoin
- Protocol versioning
- Guest audio handling
- Unit tests
- Robolectric tests
- ktlint
- CI build verification

A session-layer audit previously identified 15 bugs. All identified issues have been fixed and merged.

Current Gap

The remaining work is real-device synchronization calibration.

The current synchronization target is:

Target convergence: ≤ 5 ms

The 5 ms convergence target has not yet been consistently achieved on the two current reference devices.

A degraded-mode target of approximately 15 ms is implemented but currently disabled until hardware calibration is completed.

The application builds, installs, and runs successfully. The remaining work is validating and calibrating synchronization behavior on real hardware.

---

Build

The Android project is located in "hearyet/".

git clone https://github.com/OCTOBER-sk/HearYet.git
cd HearYet/hearyet

Build

./gradlew assembleDebug

Install

./gradlew :app:installDebug

Tests

./gradlew test

Lint

./gradlew ktlintCheck

---

Development Notes

The synchronization path is timing-sensitive.

Changes affecting the following components should be validated on physical Android devices:

- Audio extraction
- Timestamp generation
- Clock synchronization
- Nearby transport
- Guest scheduling
- Audio buffering
- Drift measurement
- Playback correction

Automated JVM and Robolectric tests verify deterministic behavior, but they cannot replace real-device synchronization testing.

---

License

HearYet is licensed under the "MIT License" (hearyet/LICENSE).

HearYet is based on "NextPlayer" (https://github.com/anilbeesetti/nextplayer), which is also licensed under MIT.

The session layer, synchronization pipeline, transport integration, scheduling, and related functionality are developed by the OCTOBER-sk project.

---

<div align="center">HearYet

Watch together. Hear in sync. No internet needed.

</div>