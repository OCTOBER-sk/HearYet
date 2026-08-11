<div align="center">

<h1>
  <img src="assets/hearyet-logo.png" alt="HearYet logo" width="44" style="vertical-align:-6px"/>
  <span style="color:#E57357">HearYet</span>
</h1>

**Watch together. Hear in sync. No internet needed.**

[![CI](https://img.shields.io/github/actions/workflow/status/OCTOBER-sk/HearYet/android_build.yaml?branch=main&label=build%20%26%20tests&logo=github)](https://github.com/OCTOBER-sk/HearYet/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2023-3DDC84?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](hearyet/LICENSE)

</div>

---

## What it is

HearYet is an Android app (a fork of [NextPlayer](https://github.com/anilbeesetti/nextplayer)) that lets people in the same room listen together: the **host** plays a video on their own screen, and each **guest** receives only the audio over Google Nearby Connections and hears it through their own headphones — synchronized to the host's playback.

No internet, no shared Wi-Fi, no accounts.

## How it works

- **Host** creates a session (QR code + 6-character code), plays a video; the audio is tapped from the player's renderer chain.
- **Guests** join by scanning the QR or typing the code, then a clock-sync batch estimates the host/guest clock offset (8–10 samples, RTT-filtered, median).
- Audio streams as raw 16-bit PCM over a Nearby Connections STREAM payload; each guest's scheduler targets a guest-local playback time (host timestamp + offset + lookahead) with `WRITE_NON_BLOCKING` writes.
- Drift is corrected continuously with small playback-speed nudges; a 5-second heartbeat detects a dead host within 15 seconds; sessions persist across process death and guests can rejoin automatically.

## Features

- Nearby Connections P2P transport (Bluetooth / Wi-Fi Direct)
- Audio-only fan-out; video stays on the host's screen
- Clock sync with a convergence gate + continuous drift correction
- QR or 6-character code join, protocol-versioned payloads
- Session persistence + automatic rejoin after process death
- Guest greeting chime, guest audio focus handling, screen-off playback support
- Material 3 Compose UI

## Project status

The session layer is implemented and verified by CI (build + unit/Robolectric tests + ktlint on every push). A code audit found 15 bugs in the session layer; all are fixed and merged.

Remaining known gap: the clock-sync convergence gate (5 ms) has not yet been met on the two reference devices, and the 15 ms degraded-mode opt-in is compiled off until a calibration pass on real hardware is completed. The app installs and runs; the sync path needs on-device calibration to finish.

## Build

```bash
# The Android project lives in hearyet/
git clone https://github.com/OCTOBER-sk/HearYet.git && cd HearYet/hearyet

./gradlew assembleDebug        # debug APK
./gradlew :app:installDebug    # install on a connected device
./gradlew test                 # JVM + Robolectric tests
./gradlew ktlintCheck          # lint
```

## Architecture

13 Gradle modules: `:app` (session coordinator, clock sync, scheduler, drift correction, transport, QR, chime) plus `:core:{common,data,database,datastore,domain,media,model,ui}` and `:feature:{player,settings,videopicker,network}`. Kotlin 2.4.10, Jetpack Compose (Material 3), Hilt, Room, DataStore, Media3 1.10.1, play-services-nearby 19.3.0.

## License

MIT — see [LICENSE](hearyet/LICENSE). HearYet is a fork of [NextPlayer](https://github.com/anilbeesetti/nextplayer) (MIT, Anil Beesetti); the session layer and sync pipeline are new code by the OCTOBER-sk project.
