# HearYet — Backend & Sync Architecture Guide (Definitive, v2)

**This file and its companion `HearYet_FRONTEND.md` are the primary documents that govern this build. `HearYet_Guest_Greeting_Chime_AgentPrompt.md` is an approved, additive-only extension — folded into this file as Section 14 — that adds exactly one new sync-adjacent component (`GuestGreetingManager`) and one new Settings row; it never overrides anything below. If any other file, memory, or prior draft conflicts with these, these win.**

---

## 0. Read this first — agent contract

You are building **HearYet** by forking **NextPlayer** (`github.com/anilbeesetti/nextplayer`), a Kotlin/Jetpack Compose/Media3 Android video player, in a **fresh empty folder**. You are not restoring or patching a previous HearYet attempt — that codebase is discarded. Clone NextPlayer clean and work from there.

**Confirmed locked decisions — do not re-litigate these:**
- **Audio-only fan-out.** The Host device plays video + audio normally, visible only on the Host's own screen. Guest devices never receive video — only perfectly synced audio, through whatever Bluetooth/wired output they already have connected. Do not build video transport to guests. Do not leave hooks for it.
- **Three home-screen actions, not two:** **Watch** (solo local playback — opens NextPlayer's existing player directly on a picked file, zero session/network code involved, functionally identical to stock NextPlayer), **Create** (become Host, start a session), **Join** (become Guest, scan into a session).
- No internet is used or required. No shared Wi-Fi network is required. Devices only need to be physically near each other (Bluetooth/Wi-Fi Direct range).

**Ground-truth honesty notice:** the latency numbers in this doc (e.g. "100–200ms A2DP," "5–20ms AAudio") are well-established, widely-documented *ranges* for current Android/Bluetooth hardware — not measurements from your specific devices. Every numeric constant here (lookahead, drift thresholds, per-codec offsets) is a **tuned starting point**. Section 16 has a mandatory calibration pass on real hardware — run it, don't ship the defaults unverified. Never present these numbers to the person as verified facts about their exact phones.

**Verified upstream facts (checked directly against the live repo, do not re-derive from memory):** root package `dev.anilbeesetti.nextplayer`, Gradle modules include `:core:common`, `:core:ui`, `:core:data`, `:core:domain`, `:core:model`, and `:feature:player` (each feature module depends on all five `core:*` modules). The upstream app currently advertises Material 3 "You" dynamic color, a tree/folder/file media picker, playback speed control, subtitle track selection, and ships with Ko-fi/PayPal/UPI donation links surfaced in-app and in its store listings — all of which the teardown in `HearYet_FRONTEND.md` Section 1 must strip. Minimum SDK is low (Android 5.0-class); treat this as your real floor for "support most Android phones," not a phone released in the last two years.

**Edit discipline — this is the rule that broke the last attempt:** HearYet is a **fork**, not a rewrite. Before touching any upstream file, locate it first:

```bash
# Never guess a filename. Always locate before editing.
find . -path "*/feature/player/*" -iname "*.kt" | xargs grep -l "PlayerScreen\|VideoPlayer" 
find . -path "*/core/ui/*" \( -iname "*theme*.kt" -o -iname "*color*.kt" -o -iname "*type*.kt" -o -iname "*shape*.kt" \)
find . -iname "*NavGraph*.kt" -o -iname "*Navigation*.kt"
```
If a working NextPlayer file already does 80% of what a HearYet screen needs, **edit it in place** (add composables, add parameters, add branches) — do not create a parallel file that duplicates it and abandon the original. Only write a wholly new file when the concept genuinely doesn't exist upstream (e.g. `SessionCoordinator.kt` — NextPlayer has no concept of sessions). Section 2 gives the exact map of what's new vs. what's edited.

---

## 1. Fork setup

```bash
git clone https://github.com/anilbeesetti/nextplayer.git hearyet
cd hearyet

# Package rename: dev.anilbeesetti.nextplayer -> com.hearyet.app
# Use Android Studio's "Rename Package" refactor tool, NOT find/replace —
# find/replace breaks resource references and manifest entries.
```

**`app/build.gradle.kts` — new dependencies on top of what NextPlayer already has:**

```kotlin
dependencies {
    // Transport
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // QR generation (host)
    implementation("com.google.zxing:core:3.5.3")

    // QR scanning (guest)
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Audio codec: NONE needed for transport. HearYet transports raw PCM (Section 6) —
    // no Opus/AAC extension dependency required. Do not add media3-exoplayer-opus;
    // it is not published to Maven and was rejected for that reason (Section 6).

    // Serialization for control messages / QR payload
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Keep every existing NextPlayer dependency (media3-exoplayer, media3-ui, hilt, compose, nextlib) untouched.
}
```
**Build-check before first compile:** `media3-exoplayer-opus` must be pinned to the **exact same version string** as whatever `media3-exoplayer` and `media3-ui` NextPlayer already ships (`grep -r "media3" **/build.gradle.kts` to confirm the existing pin before adding the Opus line) — a version skew between Media3 artifacts is a common source of runtime `NoSuchMethodError`/crash-on-first-playback that won't show up until the Opus path is actually exercised. Don't assume `1.9.3` above is current; treat it as a placeholder to overwrite with whatever NextPlayer's existing pin actually is.

**`AndroidManifest.xml` — permissions to add:**

```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" /> <!-- pre-Android 12 needs this for BT/Wi-Fi discovery -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" /> <!-- already present upstream; confirm -->

<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

**Contextual permission rule (ties to `HearYet_FRONTEND.md`):** never request any of the above at app launch. Request each permission at the exact moment its feature is invoked:
- `CAMERA` → only when the person taps **Join**, immediately before the scanner opens.
- `BLUETOOTH_*` / `NEARBY_WIFI_DEVICES` / `ACCESS_FINE_LOCATION` → only when the person taps **Create** or **Join**, immediately before advertising/discovery starts.
- Storage/media permissions (already in NextPlayer) → only when **Watch** is tapped or the media picker opens, exactly as upstream already does it — do not change this behavior.

If a required permission is missing when a feature is invoked, transition to the matching `SessionState.Error` / permission-required UI state (Section 17) — never fail silently, never crash.

---

## 2. Module / file map — what's new vs. what's edited

| Path | Status | Purpose |
|---|---|---|
| `core/ui/*` (theme, color, typography, shape) | **EDIT existing** | Strip dynamic color, install HearYet tokens — see `HearYet_FRONTEND.md` |
| `feature/player/*` (existing player screen/ViewModel) | **EDIT existing** | Add the in-player "start session" entry point (Section 2.1); this is still the Watch screen, unmodified in its solo path |
| `core/navigation/*` (existing nav graph) | **EDIT existing** | Add three new routes: Create, Join, InSession-Guest — do not replace the graph |
| `sync/` *(new package)* | **NEW** | `SessionCoordinator.kt`, `ClockSyncManager.kt`, `SharedAudioRenderer.kt`, `PresentationScheduler.kt`, `DriftCorrectionManager.kt`, `AudioChunk.kt`, `SessionModels.kt`, `GuestGreetingManager.kt` (Section 14 — Guest-side arrival chime, one integration point inside `SessionCoordinator.kt`, no changes to the other files in this package) |
| `transport/` *(new package)* | **NEW** | `NearbyTransportManager.kt`, `ControlMessage.kt`, `SessionPayload.kt` |
| `bluetooth/` *(new package)* | **NEW** | `BluetoothRouteManager.kt`, `BluetoothCodecDetector.kt` |
| `qr/` *(new package)* | **NEW** | `QrGenerator.kt`, `QrScannerAnalyzer.kt` |
| `feature/home/*` | **NEW** (replaces whatever NextPlayer used as its launcher screen — see `HearYet_FRONTEND.md` for exactly what that means visually) | Watch / Create / Join |
| `feature/host/*`, `feature/join/*`, `feature/sessionended/*` | **NEW** | Session-specific screens with no NextPlayer equivalent |

### 2.1 The one required edit inside `feature/player`

NextPlayer's existing player screen gains exactly one new affordance: a session button in the player controls overlay (visual spec in `HearYet_FRONTEND.md` Section 7.6) that, when tapped, opens the same bottom/side sheet as **Create** — turning whatever is currently playing locally into a live Host session without leaving the player. This is additive: find the existing controls composable (`find . -path "*/feature/player/*" -iname "*Controls*.kt"`), add one icon button and one sheet-trigger callback. Do not restructure the existing controls layout to do this.

---

## 3. Core data models

**Clock rule — applies to every model and calculation in this section and Sections 5–8:** all timestamps in `ControlMessage`, `AudioChunk`, and the clock-sync/scheduler/drift math are `System.nanoTime()` (monotonic, per-device) values, never `System.currentTimeMillis()`. Wall-clock time can jump (NTP correction, timezone/DST change, user editing the clock) and would silently corrupt the offset math in Section 5. `nanoTime()` has no cross-device meaning on its own — that's exactly what `ClockSyncManager`'s offset conversion is for — but it can never jump backward or skip on a single device, which wall-clock time can.

```kotlin
// sync/SessionModels.kt

sealed class SessionRole {
    data object Host : SessionRole()
    data object Guest : SessionRole()
}

sealed class SessionState {
    data object Idle : SessionState()
    data object Advertising : SessionState()          // host: session created, no media chosen yet — guests may already join
    data object WaitingForMedia : SessionState()       // host: media not yet picked; guests connected but nothing to schedule
    data object Discovering : SessionState()           // guest: found host, connecting
    data object ClockSyncing : SessionState()          // guest: clock handshake in progress
    data class Connected(val guestCount: Int) : SessionState()
    data class Playing(val positionMs: Long) : SessionState()
    data class Error(val reason: SessionError) : SessionState()
    data object Ended : SessionState()                 // host ended, guest sees this
}

enum class SessionError {
    PERMISSION_MISSING, CONNECTION_FAILED, QR_INVALID, PAYLOAD_INVALID,
    DISCOVERY_FAILED, DEVICE_INCOMPATIBLE, SYNC_TIMEOUT, HOST_UNREACHABLE
}

enum class SyncHealth { GOOD, DEGRADED, POOR }

data class GuestInfo(
    val endpointId: String,
    val displayName: String,
    val clockOffsetMs: Double,
    val lastRttMs: Long,
    val driftMs: Double,
    val syncHealth: SyncHealth,
    val connectedAtMs: Long
)
```

**`SessionError` trigger map — every entry must be reachable, per Section 17's acceptance criteria. If a trigger can't be wired to real code, remove the enum entry instead of leaving dead UI.**

| `SessionError` | Triggered when | Who observes it |
|---|---|---|
| `PERMISSION_MISSING` | A required runtime permission (Section 1) is denied at the exact moment Create/Join/Watch is invoked | Host & Guest |
| `CONNECTION_FAILED` | `transport.requestConnection` rejects, times out, or `onConnectionResult` reports failure during the initial handshake | Guest |
| `QR_INVALID` | `SessionPayloadCodec.decode` fails to parse the scanned string at all (malformed/foreign QR) | Guest |
| `PAYLOAD_INVALID` | Payload decodes but `protocolVersion` mismatches `CURRENT_PROTOCOL_VERSION`, or a required field is blank | Guest |
| `DISCOVERY_FAILED` | `startDiscovery` never finds an endpoint matching `hostEndpointName` within a 15s window | Guest |
| `DEVICE_INCOMPATIBLE` | Nearby Connections API reports `STATUS_ERROR`/unsupported on this device (e.g. no BLE, Nearby Connections unavailable via Play Services) at the moment Create or Join is invoked | Host & Guest |
| `SYNC_TIMEOUT` | Clock-sync offset stddev doesn't converge below the 5ms threshold (Section 5) within ~10 seconds | Guest |
| `HOST_UNREACHABLE` | No `ControlMessage.Heartbeat` received for 15s (Section 10), or the connection drops without a `SessionEnded` message | Guest |

```kotlin
// transport/SessionPayload.kt — exactly what the QR code encodes. Keep it small.

import kotlinx.serialization.Serializable

@Serializable
data class SessionPayload(
    val sessionId: String,          // fresh UUID per session
    val sessionCode: String,        // 6-character Base32 (Crockford alphabet, no ambiguous chars), same session
                                     // identity as sessionId but human-typable — see "Enter code instead" in
                                     // HearYet_FRONTEND.md Section 9.5
    val hostEndpointName: String,   // Nearby Connections advertised name to match against; encodes sessionId
                                     // (or a stable derivation of it) so two hosts with the same display name
                                     // never collide — see Section 4's discoverability rule
    val hostDisplayName: String,    // shown on the guest's "Connecting to…" screen
    val protocolVersion: Int = 1    // bump if ControlMessage schema changes; reject mismatches with PAYLOAD_INVALID
)

// transport/SessionPayloadCodec.kt — the encode/decode contract QR generation and scanning both depend on.
object SessionPayloadCodec {
    private val json = Json { ignoreUnknownKeys = true } // forward-compatible with future fields

    fun encode(payload: SessionPayload): String =
        Base64.encodeToString(json.encodeToString(payload).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun decode(raw: String): SessionPayload? = try {
        json.decodeFromString<SessionPayload>(String(Base64.decode(raw, Base64.NO_WRAP), Charsets.UTF_8))
    } catch (e: Exception) {
        null // caller maps this to SessionError.QR_INVALID
    }

    /** Generates the 6-char code shown under the QR (Frontend 9.4) and accepted by "Enter code instead" (9.5). */
    fun generateSessionCode(): String {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford Base32, no I/L/O/U
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
```
Manual-code join resolves the typed 6-character code to the same host connection the QR would have: the code is looked up against the currently-advertising `hostEndpointName` the same way a decoded QR payload is (Section 4's discovery filter matches on the code/sessionId, not on re-deriving a full `SessionPayload` from six characters alone) — practically, advertise the session code as (or alongside) the Nearby Connections endpoint name so a typed code and a scanned QR converge on the identical discovery path.

```kotlin
// transport/ControlMessage.kt — everything sent on the BYTES channel. All timestamps are System.nanoTime().

@Serializable
sealed class ControlMessage {
    @Serializable data class ClockSyncRequest(val t0: Long) : ControlMessage()
    @Serializable data class ClockSyncResponse(val t0: Long, val t1: Long, val t2: Long) : ControlMessage()
    @Serializable data class PlaybackState(val isPlaying: Boolean, val positionMs: Long, val sharedClockTimestampNanos: Long) : ControlMessage()
    @Serializable data class GuestJoined(val endpointId: String, val displayName: String) : ControlMessage()
    @Serializable data object SessionEnded : ControlMessage()
    @Serializable data class DriftReport(val driftMs: Double) : ControlMessage() // guest -> host, for the host's guest-list UI, every 2s (Section 8)
    @Serializable data class Heartbeat(val hostTimestampNanos: Long) : ControlMessage() // host -> all guests, every 5s (Section 10)
    @Serializable data class SeekTo(val positionMs: Long, val sharedClockTimestampNanos: Long) : ControlMessage() // host -> guests
    @Serializable data class MediaChanged(val mediaTitle: String, val sharedClockTimestampNanos: Long) : ControlMessage() // host -> guests
    @Serializable data class AudioTrackChanged(val trackId: String, val sharedClockTimestampNanos: Long) : ControlMessage() // host -> guests, see Section 12
    @Serializable data class RejoinRequest(val previousEndpointId: String, val displayName: String) : ControlMessage() // guest -> host, after crash/restart (Section 10)
}
```

---

## 4. Transport layer — Nearby Connections, dual channel

Use `Strategy.P2P_STAR` (one Host, many Guests). Two logical channels over the same connection:

- **BYTES payload** — all `ControlMessage` traffic (clock sync, playback state, drift reports, session end). Low frequency, must arrive reliably, ordering matters less.
- **STREAM payload** — the continuous PCM audio feed, host → each guest. High frequency, latency-sensitive, some loss is tolerable (a dropped chunk should be silently skipped by the scheduler, not retried).

**Discovery rule (skip the generic browse list):** the guest never sees a list of "nearby sessions." The QR payload already names the exact `hostEndpointName` to connect to — discovery filters for that name **and** confirms `sessionId` before connecting, so two hosts that happen to share a display name (e.g. two phones both named "Sandy's Pixel") never cross-connect a guest to the wrong session:

```kotlin
// sync/SessionCoordinator.kt — guest join sequence (abbreviated)

fun onQrScanned(rawPayload: String) {
    val payload = SessionPayloadCodec.decode(rawPayload)
        ?: return updateState(SessionState.Error(SessionError.QR_INVALID))

    if (payload.protocolVersion != CURRENT_PROTOCOL_VERSION)
        return updateState(SessionState.Error(SessionError.PAYLOAD_INVALID))

    updateState(SessionState.Discovering)
    transport.startDiscovery(onEndpointFound = { endpointId, endpointName ->
        // endpointName alone is not a safe match — it must also carry/resolve to payload.sessionId
        // (e.g. hostEndpointName embeds sessionId, or the post-connect handshake confirms it before
        // ClockSyncManager starts) so a name collision between two nearby hosts can't misroute a guest.
        if (endpointName == payload.hostEndpointName) {
            transport.requestConnection(localEndpointName, endpointId)
        }
    })
    // ClockSyncManager.startContinuousSync() fires from onConnectionResult's
    // success path, BEFORE any audio chunk is processed.
}

/** Guest -> Host, sent by the entry link in Frontend Section 9.5 ("Enter code instead") instead of onQrScanned. */
fun onCodeEntered(code: String) {
    // Resolves the same way a decoded QR does — the code maps to the currently-advertising
    // hostEndpointName/sessionId, then proceeds through the identical Discovering -> ClockSyncing path above.
}
```

**Media-pick / QR timing:** the QR is generated and shown **immediately** when the Create sheet opens (`SessionState.Advertising`), before the Host has picked media — guests can scan and connect while the Host is still browsing files. While no media is chosen, the session sits in `SessionState.WaitingForMedia`: guests are connected and clock-synced, but the `PresentationScheduler` has nothing queued. The moment the Host picks media and hits "Start playback," the host broadcasts an initial `PlaybackState`, and every already-connected guest transitions straight from `WaitingForMedia` to scheduled playback — no re-join required.

**Latecomer join:** when a guest connects mid-playback, the host sends `PlaybackState` immediately on connection accept (not on the next natural sync tick), so the new guest's `PresentationScheduler` seeds from "now," not from session start.

**Guest reconnect after crash/restart:** a guest that force-closes or crashes mid-session and reopens the app does not silently re-scan into a brand-new `endpointId` sitting alongside its stale one in the Host's guest list. On restart, if `DataStore` shows an in-progress session (Section 10.5's role persistence), the guest sends `ControlMessage.RejoinRequest(previousEndpointId, displayName)` over a fresh connection to the same host. The Host looks up `previousEndpointId` in its guest list, replaces it with the new `endpointId` in place (preserving position/name, resetting sync stats), and immediately re-sends the current `PlaybackState` — functionally identical to a latecomer join, but it deduplicates the guest list instead of appending to it.

---

## 5. Clock synchronization — the math

This is a **Cristian's-algorithm-style** offset estimation, refined with multiple round trips.

**Single round trip:**
- Guest records `t0` (local time), sends `ClockSyncRequest(t0)`.
- Host receives it, immediately records its own clock as `t1`, and — right before sending the reply — records `t2` (also host clock; `t1 ≈ t2` since host does no meaningful work between receive and reply).
- Guest receives `ClockSyncResponse(t0, t1, t2)` at local time `t3`.

```
round_trip_time (RTT)   = (t3 - t0) - (t2 - t1)
clock_offset             = ((t1 - t0) + (t2 - t3)) / 2
```
`clock_offset` is how far ahead (positive) or behind (negative) the host's clock is relative to the guest's. Every subsequent timestamp the host sends can be converted to "guest local time" via `hostTimestamp + clock_offset`.

**Why one sample isn't enough:** Bluetooth/Wi-Fi Direct RTT jitters. Take **8–10 samples** over ~2 seconds, discard the top 25% by RTT (asymmetric-delay outliers bias the offset), then take the **median** of the remaining offsets as `clockOffsetMs`. Repeat this full batch every 30–60 seconds in the background to correct for crystal drift between phones (cheap Android clocks drift on the order of tens of ppm — over 10 minutes that's real, audible drift if uncorrected).

All of `t0`/`t1`/`t2`/`t3` are `System.nanoTime()` reads (Section 3's clock rule) — never `System.currentTimeMillis()`, since a wall-clock jump mid-batch would poison the offset estimate with no way to detect it.

**Convergence gate:** a guest doesn't leave `SessionState.ClockSyncing` until the standard deviation of its offset samples is below a threshold (start at 5ms) — if it can't converge within ~10 seconds, transition to `SessionState.Error(SYNC_TIMEOUT)` rather than proceeding with a bad estimate.

**Degraded-mode fallback:** hard-failing to `SYNC_TIMEOUT` is the default and stays the default — proceeding with an unconverged offset produces audible phasing, which is worse than a clear error state. If real-world calibration (Section 16) shows too many legitimate devices timing out on noisy Bluetooth/Wi-Fi Direct links, the one sanctioned relaxation is: after 10 seconds without reaching the 5ms gate, if stddev is at least below a looser 15ms ceiling, allow entry with `SyncHealth.POOR` set from the start (rather than only reachable via Section 8's drift correction) instead of erroring out. Do not ship this relaxed path by default — it's an explicit, calibration-justified opt-in, not a silent lowering of the bar.

```kotlin
// sync/ClockSyncManager.kt — core estimate function
fun computeOffset(t0: Long, t1: Long, t2: Long, t3: Long): Pair<Double, Long> {
    val rtt = (t3 - t0) - (t2 - t1)
    val offset = ((t1 - t0) + (t2 - t3)) / 2.0
    return offset to rtt
}

fun estimateFromSamples(samples: List<Pair<Double, Long>>): Double {
    val sorted = samples.sortedBy { it.second } // sort by RTT
    val keep = sorted.take((sorted.size * 0.75).toInt().coerceAtLeast(1))
    // Edge case: with only 8-10 raw samples, a 75% keep can leave fewer than 2 offsets to median.
    // If filtering leaves fewer than 2 samples, skip the median step and fall back to the single
    // lowest-RTT sample's offset directly — it's the least jitter-biased individual reading available.
    if (keep.size < 2) return keep.firstOrNull()?.first ?: sorted.first().first
    val offsets = keep.map { it.first }.sorted()
    return offsets[offsets.size / 2] // median
}
```

---

## 6. Audio pipeline

**Tap point:** intercept PCM after decode, before the host's own `AudioTrack` output, using a Media3 `AudioProcessor` inserted into the renderer's audio sink chain. Concretely: `MediaCodecAudioRenderer` builds its output pipeline through a `DefaultAudioSink`, which accepts a list of `AudioProcessor`s via `DefaultAudioSink.Builder.setAudioProcessors(...)`. Insert `SharedAudioRenderer` (implementing `AudioProcessor`) at the **end** of that chain — after any existing processors (e.g. Opus's own decode-side processors, if applicable to Host-side subtitles/effects) so the tapped samples are the exact final PCM about to reach the Host's `AudioTrack`. Look for wherever NextPlayer's `ExoPlayer.Builder` or `RenderersFactory` constructs `DefaultAudioSink` (`find . -iname "*RenderersFactory*.kt" -o -iname "*AudioSink*.kt"`) and add the processor there — do not fork a second, parallel audio pipeline.

**Codec for transport — CHANGED from original Opus plan, read this note first:** the original plan called for Opus at 48kHz. **Opus is not used.** Media3's Opus decoder (`decoder_opus`) is not published to Maven/Google Maven under any coordinate — it is only distributable by cloning `androidx/media` locally, vendoring libopus's C source, and building it via NDK/CMake/Ninja as part of the app's own build. That is a disproportionate build-system cost for this project and was rejected for that reason.

**HearYet transports raw, uncompressed PCM instead of Opus.** This preserves the one property the whole scheduler design actually depends on — **zero algorithmic encode/decode delay** — which Opus had and AAC (Media3's other realistic option) does not: AAC's frame format bakes in ~2112 samples (~44ms at 48kHz) of encoder priming delay before any real audio is even decodable, which eats directly into the 250ms `lookaheadMs` budget the scheduler (Section 7) is built around. Raw PCM has no such priming delay, is Maven-free (zero new dependency), and at Bluetooth/Wi-Fi Direct close range the bandwidth cost (~1.5Mbps for 48kHz/16-bit stereo) is not a practical constraint — this is not an internet or congested-Wi-Fi transport.

**What this costs, honestly:** Opus's built-in Packet Loss Concealment (PLC) is gone. A single dropped 20ms frame was already designed to be inaudible (Section 7's drop-late-chunks rule) and remains so. What changes is **burst loss** (3–5 consecutive dropped frames): with Opus this would have been PLC-smoothed; with raw PCM it is a harder gap. Mitigate cheaply if calibration (Section 16) shows this is audible in practice — e.g. repeat-last-frame or a short linear fade-to-silence across the gap instead of a hard cut — but do not build this preemptively; confirm it's actually a problem on real hardware first.

**Chunking math:** use **20ms frames** (960 samples per channel at 48kHz — the same frame size originally chosen for Opus-frame alignment; kept because it's still a sound scheduling granularity on its own merits, not because Opus needs it anymore). Each `AudioChunk` carries:
```kotlin
data class AudioChunk(
    val hostTimestampNanos: Long,   // host's monotonic clock (System.nanoTime()) at the moment this frame was tapped
    val sequenceNumber: Long,       // monotonic, for gap detection
    val pcmPayload: ByteArray       // raw 16-bit PCM, 48kHz, interleaved stereo — was opusPayload in the original plan
)
```
`sequenceNumber` lets a guest detect a dropped chunk (STREAM payload can lose packets) and decide to skip ahead in the schedule rather than stall waiting for it. There is no decoder-side concealment step now — a detected gap is simply skipped (optionally with the cheap fade-out mitigation above).

**Backpressure on the STREAM channel:** one guest with a degraded Bluetooth link or slow processing must never back up the Host's send queue for every other guest. Each guest connection gets its own bounded outbound queue (start at **200 chunks**, ≈4 seconds of audio at 20ms/frame) on the Host side. When a guest's queue hits that depth, drop the **oldest** queued chunks first, not the newest — this is the same "drop, don't delay" principle as Section 7's late-chunk rule, applied at the send side instead of the schedule side. A queue that's chronically at max depth is a signal to downgrade that guest's `SyncHealth`, not to slow down the other guests.

**Audio focus (Guest side):** a Guest's stream is presented as a "media" audio-focus request like any other player.
- **Permanent focus loss** (e.g. an incoming phone call) → **pause** the guest's `AudioTrack` outright, don't duck. Ducking keeps the clock advancing against silence, so on focus regain the guest is instantly behind the schedule; pausing lets the guest re-enter cleanly the same way it would after an explicit host pause (Section 7's unpause-as-seek handling) — flush and re-seed from the fresh `PlaybackState`/`Heartbeat` on regain.
- **Transient focus loss** (e.g. a notification sound) → ducking is acceptable; the interruption is short enough that the resulting drift is within `DriftCorrectionManager`'s normal nudge range (Section 8).

**Host screen-off behavior:** turning the screen off can cause Android to release the `Surface` or throttle video decode on some devices, which would stall the same pipeline the audio tap depends on. When the screen turns off during an active session, swap the player's video output to a `DummySurface` (or `setVideoSurface(null)` if the renderer supports audio-only cleanly) rather than letting the system tear down decode entirely — this keeps the PCM tap alive for audio-only background streaming. Verify this explicitly during Section 16's calibration pass with the Host's screen off for 5+ minutes (this is the same silent-failure point flagged in Section 10 for the foreground service).

**Volume normalization is a known v1 limitation, not a bug to chase:** the same PCM stream will sound like a different loudness across different phones/earbuds/output hardware — there is no reliable cross-device loudness-normalization API to lean on here. Document this plainly rather than attempting per-device auto-leveling in v1; the mitigation is Guest-local volume control (Frontend Section 9.6's `GuestVolumeSlider`), which controls the Guest's own `AudioTrack` output volume only and has zero effect on sync.

---

## 7. Presentation scheduler — the math

Every `AudioChunk` gets a **target guest-local playback time**:
```
guestPlaybackTimeNanos = hostTimestampNanos + (clockOffsetMs * 1_000_000) + (lookaheadMs * 1_000_000)
```
`lookaheadMs` starts at **250ms** — enough buffer to absorb typical Classic Bluetooth A2DP output latency (100–200ms) plus jitter margin, without being perceptible as delay relative to the host's own ears (the host hears its own audio at near-zero extra latency, so the guest's total round trip — network + decode + this lookahead — needs to land inside a window a human doesn't register as "late").

The scheduler holds a small ring buffer of upcoming chunks and, on a timer/callback close to actual playback time, feeds each chunk to the guest's `AudioTrack` at exactly `guestPlaybackTimeNanos` using `AudioTrack.write(byte[], int, int, AudioTrack.WRITE_NON_BLOCKING)` with timestamp-aware buffering (never a blocking write — a blocking call can stall the scheduler thread and cascade into missed deadlines for every subsequent chunk; never rely on coarse `Thread.sleep` either). Chunks whose target time has already passed by more than one frame duration (20ms) are dropped, not played late — a dropped 20ms frame is inaudible; a late one causes audible phasing.

**Seeding on entering playback (first time and after `ClockSyncing`):** a guest never seeds its schedule from session start or from a stale cached position. It seeds from whatever `hostTimestampNanos`/`PlaybackState` is current at the exact moment it exits `ClockSyncing` — if the Host is already mid-playback when a guest's clock converges (including a guest who was connected during `WaitingForMedia` and only now has something to schedule), the guest starts scheduling from "now," identically to the latecomer-join path in Section 4. There is exactly one seeding code path, reused for: initial join, latecomer join, rejoin-after-crash, and exiting `ClockSyncing` — not four separate implementations.

**Seek propagation:** the Host broadcasts `ControlMessage.SeekTo(positionMs, sharedClockTimestampNanos)` on every user-initiated seek. On receipt, a guest **flushes its ring buffer entirely** and re-seeds the schedule from the new position using the same seed path above — it does not attempt to reconcile old buffered chunks against the new timeline. **Backward seeks have no replay:** if the Host seeks backward, the audio for that earlier region was already streamed and discarded once; guests cannot replay it, since nothing retains a rewind buffer. This is a hard, documented v1 limitation, not a bug — see Section 18 and `HearYet_FRONTEND.md` Section 9.6, which explicitly does not build a "replay" affordance for guests.

**Pause/resume:** treat unpause identically to a seek. On resume, the Host's `PlaybackState.positionMs` has moved (however slightly) since the pause began, and guest clocks may have kept drifting during the paused interval; broadcasting `PlaybackState` on resume and having guests flush-and-reseed (same path as `SeekTo`) avoids resuming against a stale schedule rather than trying to patch the existing one.

**Media change:** when the Host switches to a different file/URL mid-session, broadcast `ControlMessage.MediaChanged` before the new `PlaybackState`. Guests treat this exactly like a seek — flush the scheduler, discard anything queued for the old media, and re-seed once the new `PlaybackState` arrives. There is no cross-fade or gapless handling in v1; a brief silence during the flush is expected and acceptable.

---

## 8. Drift correction — the math

Clock offset is corrected at sync time, but **playback rate drift** (accumulated scheduling jitter, buffer underrun/overrun) needs continuous, gentle correction — never abrupt jumps, which are audible as clicks or skips.

Track `driftMs` = the running difference between where a guest's `AudioTrack` playback head actually is vs. where the schedule says it should be. Apply correction as a tiny **playback speed nudge**, not a seek:

```
if |driftMs| < 15         -> SyncHealth.GOOD,   no correction
if 15 <= |driftMs| < 50    -> SyncHealth.DEGRADED, nudge playback speed by ±0.5%
                               (PlaybackParams.setSpeed(1.005f) or (0.995f))
if |driftMs| >= 50          -> SyncHealth.POOR
    if |driftMs| < SEVERE_DESYNC_THRESHOLD_MS (start at 150ms): nudge harder, ±1.5%
    else: hard resync — snap the schedule (audible but rare; better than staying audibly out of phase)
```
Nudges are applied via `AudioTrack.playbackParams` speed adjustment on the decoded PCM stream (pitch-preserving is not required here since ±0.5–1.5% is inaudible as pitch shift). Re-evaluate `driftMs` every 1–2 seconds; don't correct on every single chunk — that causes oscillation. These thresholds are starting points; Section 16’s calibration pass tunes them against real audible phasing tests (e.g. a metronome or clap track playing on host + guest simultaneously).

Report `driftMs` back to the host via `ControlMessage.DriftReport` on a **fixed 2-second interval** (not "periodically" left to implementation discretion) so the Host Session screen's guest list can show live per-guest `SyncHealth` without either flooding the BYTES channel or feeling laggy to update.

---

## 9. Bluetooth — supporting most phones and most earbuds

This is the part most likely to break silently across a wide device pool if not handled explicitly.

**Not affected by the Section 6 transport-codec change:** this section is about the Guest's own phone-to-earbud A2DP output leg (SBC/AAC/aptX/LDAC as negotiated by the Guest's Bluetooth stack), which is entirely separate from what codec HearYet uses to send audio from Host to Guest over Nearby Connections (Section 6, now raw PCM). Nothing in this section changes.

**The core problem:** Bluetooth audio output latency varies enormously by codec, and Android doesn't give you a clean universal API to query "what will my actual output latency be." You compensate with codec-aware defaults, not a single global constant.

**Codec-aware latency compensation table (starting values — recalibrate in Section 16):**

| Codec | Typical added output latency | `lookaheadMs` adjustment |
|---|---|---|
| SBC (default, nearly all budget earbuds) | 150–270ms | baseline 250ms, may need up to 300ms on cheap hardware |
| AAC (iPhone-adjacent earbuds, some Android) | 120–200ms | 250ms baseline is usually fine |
| aptX / aptX HD | 80–150ms | can safely reduce toward 180–200ms |
| aptX Low Latency | 32–65ms | reduce toward 150ms |
| LDAC | 150–200ms (higher bitrate modes add more) | 250–280ms |
| LE Audio (Auracast-class, newest devices) | 20–50ms | out of scope for v1 — see Section 20 |
| Wired / USB-C DAC | near-zero | can reduce toward 150–180ms floor |

**Detection approach:** query the active Bluetooth codec via `BluetoothCodecStatus` (`BluetoothA2dp` proxy, requires `BLUETOOTH_CONNECT`) where available. `BluetoothCodecStatus` itself was added in Android 8 (API 26); it does not exist as an API at all below that, and Section 0's SDK floor for HearYet goes lower still. On any device where the class/method is unavailable — whether because the API level is below 26 or because `getCodecStatus` returns `null` at runtime — never attempt a reflection-based workaround; go straight to the SBC-safe default of 250–300ms and treat the codec as permanently `UNKNOWN_ASSUME_SBC` for that session rather than retrying detection on a timer. Do not assume every phone in a session has the same codec — each guest independently detects its own output codec and sets its own `lookaheadMs`; this is a per-guest value, not a session-global constant.

```kotlin
// bluetooth/BluetoothCodecDetector.kt — sketch
fun detectActiveCodec(a2dpProxy: BluetoothA2dp, device: BluetoothDevice): CodecEstimate {
    val status = a2dpProxy.getCodecStatus(device) ?: return CodecEstimate.UNKNOWN_ASSUME_SBC
    return when (status.codecConfig?.codecType) {
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> CodecEstimate.SBC
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> CodecEstimate.AAC
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> CodecEstimate.APTX
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> CodecEstimate.APTX_HD
        BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> CodecEstimate.LDAC
        else -> CodecEstimate.UNKNOWN_ASSUME_SBC
    }
}
```

**Route changes must never touch sync state:**

```kotlin
// bluetooth/BluetoothRouteManager.kt
class BluetoothRouteManager(private val onRouteChanged: (CodecEstimate) -> Unit) {
    // Listens for ACTION_ACL_CONNECTED / ACTION_ACL_DISCONNECTED and
    // AudioDeviceCallback route changes. On change: re-detect codec,
    // update lookaheadMs going forward, and nothing else.
}
```
**Critical rule:** this class only ever calls `onRouteChanged(...)`, which updates `lookaheadMs` for *future* chunks. It must never touch `ClockSyncManager`, `PresentationScheduler`'s existing queue, or `DriftCorrectionManager` state. The shared clock and scheduled-chunk queue keep running through a Bluetooth disconnect/reconnect exactly as-is — that's what makes the transition silent instead of a resync event.

**Older/budget device reality check:** many Android phones in real use are several years old, on SDK levels where `BluetoothCodecStatus` may be unavailable or unreliable, and paired with SBC-only earbuds. Treat "unknown codec" as SBC-safe (the most conservative default), never as "assume best case" — an under-compensated guest is audibly out of sync; an over-compensated one just has a fraction more (inaudible) delay.

---

## 10. Session lifecycle & edge cases

- **Host leaves/ends session:** broadcast `ControlMessage.SessionEnded` before tearing down the connection. Guests transition to `SessionState.Ended` and must show the frontend's explicit Session Ended screen — never a frozen/silent state.
- **Guest disconnects unexpectedly (out of range, app killed):** host removes them from the guest list on `onDisconnected`; no special handling needed beyond updating the UI list.
- **App backgrounded mid-session:** run the audio session in a **foreground service** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, already an upstream pattern for the player) so Doze/App Standby doesn't throttle the Nearby Connections socket or the audio thread. Verify this explicitly on a real device with the screen off for 5+ minutes during Section 16 calibration — this is a common silent failure point.
- **Guest reconnect after transient BT dropout:** handled entirely by Section 9's route manager; no resync event, no UI disruption beyond the sync-health dot briefly changing color.

### 10.1 Heartbeat & host-unreachable detection
A silent Host crash (process killed, device dies) never sends `SessionEnded` — without an independent liveness signal, connected Guests would sit in `Playing`/`Connected` forever with no new audio and no explanation. The Host broadcasts `ControlMessage.Heartbeat(hostTimestampNanos)` to all connected Guests every **5 seconds**, independent of whether audio is actively playing (i.e. it continues during `WaitingForMedia` and while paused, not only during active playback). Each Guest tracks time since its last received Heartbeat (or any other Host message, which resets the same timer); if **15 seconds** pass with nothing received, the Guest transitions to `SessionState.Error(HOST_UNREACHABLE)` rather than continuing to display a stale "in sync" state.

### 10.2 Role & session-state persistence across app restart
If the app process is killed (by the user, or by the system under memory pressure) while a session is active, reopening the app must not strand the user in a default Home screen with no memory of what it was doing. `SessionRole` (Host/Guest) and enough of `SessionState`/session identity (`sessionId`, `previousEndpointId` if Guest) to attempt a rejoin are persisted to DataStore continuously while a session is active, and cleared on `SessionEnded`/explicit leave. On app restart, the navigation graph checks this persisted state before routing to Home: if it shows an in-progress Guest session, attempt `ControlMessage.RejoinRequest` (Section 4) and route into the in-session screen showing a brief "Reconnecting…" state rather than Home; if it shows an in-progress Host session, the Host cannot silently resume broadcasting after a process death (its own playback state is gone too) — route to Home and clear the persisted session, since there is no live session left to rejoin. Since Host and Guest run from the **same APK** with role decided at runtime (Section 0), this persisted role is also what `SessionCoordinator` reads on cold start to know which of the two restore paths applies.

---

## 11. QR generation & scanning

```kotlin
// qr/QrGenerator.kt
object QrGenerator {
    fun generate(payload: String, sizePx: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        return bitmap
    }
}
```
The `payload` string passed in is `SessionPayloadCodec.encode(...)` (Section 3) — the QR always encodes the full payload, including `sessionCode`, so a scan and a manually-typed code resolve to the same connection path. Visual container for this bitmap (the bottom-sheet card, the `sessionCode` text styling, colors, and the "Waiting for guests to join…" label shown while `SessionState.Advertising`/`WaitingForMedia` is active) is defined in `HearYet_FRONTEND.md` — this file only owns the bitmap generation and the payload contents (Section 3).

```kotlin
// qr/QrScannerAnalyzer.kt
class QrScannerAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()
    private var hasDecoded = false
    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (hasDecoded) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let {
                    hasDecoded = true; onDecoded(it)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
```
Smooth-scan feel (no jittery line animation) is a frontend concern — the camera preview, framing guide, and state-ladder copy ("Connecting…" → "Syncing clock…" → "In sync") live in `HearYet_FRONTEND.md`; this analyzer just needs to hand off a decoded string reliably and stop scanning immediately after (`hasDecoded` guard above).

---

## 12. Feature triage — what to keep from NextPlayer, what to cut

Apply this test to every non-core-playback NextPlayer feature: **does it plausibly help someone share or manage a synced listening/watching session, or is it generic media-player scope creep for this product?**

| Feature | Verdict | Why |
|---|---|---|
| Local file/folder media picker | **Keep, unmodified** | Core to Watch and to Create (Host picks what to play) |
| Network stream / URL playback (if present upstream) | **Keep, gate behind Watch/Create only** | A Host can legitimately want to share a stream, not just a local file — but this never applies to Guest, who never opens a URL. Surfaced in the UI as a "Play from URL" secondary action on the Create sheet (`HearYet_FRONTEND.md` Section 9.4), alongside the primary local-file picker, not as a separate hidden entry point |
| Playlist / queue view | **Keep for Watch and Host** | Useful, not session-specific, no conflict with sync model |
| Subtitle track selection, subtitle delay | **Keep, Host-side only, no `ControlMessage` needed** | Subtitles are visual — irrelevant to Guests (audio-only), fine for Watch and for the Host's own screen. Switching subtitle tracks never touches the PCM tap, so it has no sync implication |
| Audio track switching (multi-language tracks in a container) | **Keep for Watch; gated for Host in an active session** | Unlike subtitles, switching the *active audio track* changes the PCM feed the tap point (Section 6) captures — a silent switch would cause an audible glitch/discontinuity for every connected Guest. Broadcast `ControlMessage.AudioTrackChanged` (Section 3) before switching, and have the Guest scheduler flush-and-reseed exactly like a seek (Section 7); the alternative, simpler v1 option is to disable audio-track switching entirely once guests are connected, matching how playback speed is handled below — pick one explicitly rather than leaving it ambiguous. Guests never get their *own* independent track choice: they hear whatever track the Host has selected (Section 18) |
| Playback speed control | **Cut from session context, keep for Watch** | Changing speed mid-session would desync everyone instantly unless it's propagated as a `ControlMessage` and re-synced — out of scope for v1; disable the control while `SessionRole.Host` is `Playing` with guests connected |
| Audio/subtitle track external file loading | **Keep for Watch/Host** | No sync implication |
| Donation/sponsorship UI | **Cut entirely** | Explicit teardown target, see `HearYet_FRONTEND.md` Section 1 |
| Any dynamic-color / Material You branch | **Cut entirely** | Explicit teardown target |

If you find a NextPlayer feature not listed here, apply the same test explicitly before deciding — don't default to "keep everything" or "cut everything."

---

## 13. Build order (do not reorder)

1. **Watch path first** — confirm the forked, renamed app plays local media exactly as NextPlayer did, with zero new code touched. This is your baseline sanity check before any sync code exists.
2. **SharedAudioRenderer + host PCM tap** (Section 6) — verify host's own audio still plays with zero added lip-sync delay before touching anything network-related.
3. **ClockSyncManager** (Section 5) over a live Nearby Connections link between two physical devices — log offset convergence, confirm it stabilizes within ~10 seconds.
4. **PresentationScheduler** (Section 7) with the fixed 250ms lookahead — get one guest hearing scheduled audio, not necessarily perfectly synced yet.
5. **DriftCorrectionManager** (Section 8) — run a 15+ minute session, confirm drift stays bounded instead of accumulating.
6. **BluetoothRouteManager + codec detection** (Section 9) — physically disconnect/reconnect a guest's earbuds mid-session; confirm playback never stops and sync state survives untouched. Test with at least two different codec classes if you have the hardware (e.g. one SBC-only earbud, one AAC/aptX-capable one).
7. **QR join flow** (Section 11) — layer on last; doesn't block anything above.
8. **Session lifecycle edge cases** (Section 10) — background/Doze survival, host-end broadcast, latecomer join, heartbeat/`HOST_UNREACHABLE` timeout, rejoin-after-crash, role/session persistence across app restart.
9. **Mid-session control messages** — `SeekTo`, pause/resume-as-seek, `MediaChanged`, `AudioTrackChanged` (Section 7 & 12) — verify each flushes and re-seeds the guest scheduler cleanly with no lingering old-timeline audio.
10. **Calibration pass** (Section 16) — with real hardware (minimum: one budget Android phone + one Classic Bluetooth earbud, ideally a second phone/earbud pair with a different codec), run the phasing test procedure and tune `lookaheadMs` per codec class, `NUDGE_THRESHOLD_MS`, and `SEVERE_DESYNC_THRESHOLD_MS` — every numeric constant in this document is a starting point, not a guarantee.
11. **Guest Greeting Chime** (Section 14) — layer on last, after 3–8 are functionally stable; it's a self-contained consumer of `SessionState`/`SessionCoordinator` and must never be built before the state machine it hooks into is solid. Fold its checks (Section 14.8) into the same calibration pass as step 10 rather than treating it as a separate milestone.

---

## 14. Guest Greeting Chime (Guest-only ambient audio)

**Source:** folded in from `HearYet_Guest_Greeting_Chime_AgentPrompt.md`, additive to everything above — it does not change `PresentationScheduler`, `ClockSyncManager`, or `DriftCorrectionManager` math in Sections 5–8.

**What it is, in one sentence:** when a Guest device successfully joins and starts receiving synced audio for the first time in a session, it plays one short local confirmation chime — heard only by that Guest, never by the Host, never more than once per guest per session.

### 14.1 Scope boundary

**Touches:** `sync/GuestGreetingManager.kt` (new file), `SessionCoordinator.kt` (one integration point), `feature/settings/*` (one new `SettingsRow`, see `HearYet_FRONTEND.md` Section 9.7), `res/raw/` (five audio clips).

**Never touches:** `PresentationScheduler.kt`, `ClockSyncManager.kt`, `DriftCorrectionManager.kt` internals; any Host-facing UI (the Host's existing guest-count pill and side-sheet list, Section 9.6 of the frontend doc, are sufficient); `GuestVolumeSlider`'s wiring (this feature only reads its current value, never adds wiring to it); any persistence mechanism other than DataStore. If implementing this feature seems to require touching any of the above, stop and report it rather than improvising.

### 14.2 `sync/GuestGreetingManager.kt`

Sits alongside `SessionCoordinator.kt`, `ClockSyncManager.kt`, `SharedAudioRenderer.kt` in the existing `sync/` package (Section 2's file map — a new file inside an existing package, not a new package). Responsibilities, and only these: own a `SoundPool` instance and its lifecycle (create on Guest-role entry, release on session end); preload the five chime clips (Section 14.5); track which guest identities have already been greeted this session (Section 14.4); request audio focus, play one randomly chosen clip, release focus (Section 14.6); read the live `GuestVolumeSlider` value at the moment of playback.

```kotlin
// sync/GuestGreetingManager.kt — package com.hearyet.app.sync

class GuestGreetingManager(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var loadedSoundIds: List<Int> = emptyList()
    private val greetedThisSession = mutableSetOf<String>()

    /** Call once, when the app enters Guest role and starts SessionState.Discovering —
     *  Section 1's "request/allocate only when the feature is actually invoked"
     *  discipline applies to preloading too; never preload at app launch or cold start. */
    fun preload() {
        loadedSoundIds = GREETING_RES_IDS.map { soundPool.load(context, it, 1) }
    }

    /** Call from SessionCoordinator on the guest's first transition into
     *  SessionState.Playing this session. guestIdentity is the persisted rejoin
     *  identity (Section 10.2's previousEndpointId concept), never the raw transient
     *  endpointId, so a rejoin never re-triggers this. */
    fun maybeGreet(guestIdentity: String, currentGuestVolume: Float) {
        if (guestIdentity in greetedThisSession) return
        greetedThisSession += guestIdentity
        playChime(currentGuestVolume)
    }

    /** Call on SessionState.Ended or explicit Leave, matching SessionCoordinator's
     *  existing session-teardown point. */
    fun onSessionEnded() {
        greetedThisSession.clear()
    }

    /** Call wherever the Guest's audio foreground service is torn down entirely. */
    fun release() {
        soundPool.release()
    }

    private fun playChime(currentGuestVolume: Float) {
        if (loadedSoundIds.isEmpty()) return // preload() wasn't called or hasn't finished — fail silent, not crash

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* no-op: transient, we release explicitly below */ }
            .build()

        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return // Section 14.6.5 — silent no-op, never retry, never queue

        val soundId = loadedSoundIds.random()
        val volume = (currentGuestVolume * CHIME_VOLUME_SCALE).coerceIn(0f, 1f)
        soundPool.play(soundId, volume, volume, 1, 0, 1f)

        // Release focus after the clip's real measured duration (Section 14.5) — never a guess.
        Handler(Looper.getMainLooper()).postDelayed(
            { audioManager.abandonAudioFocusRequest(focusRequest) },
            CHIME_DURATION_MS
        )
    }

    companion object {
        private val GREETING_RES_IDS = listOf(
            R.raw.guest_greet_chime_01,
            R.raw.guest_greet_chime_02,
            R.raw.guest_greet_chime_03,
            R.raw.guest_greet_chime_04,
            R.raw.guest_greet_chime_05,
        )
        private const val CHIME_VOLUME_SCALE = 0.6f // starting point — tune during Section 16 calibration
        private const val CHIME_DURATION_MS = 2000L // placeholder — replace with the longest clip's real duration
    }
}
```

### 14.3 Trigger point — edit `SessionCoordinator.kt`

Locate the guest-side code path that transitions `SessionState` into `Playing` (Section 3's sealed class, Section 4's latecomer-join and first-`PlaybackState` handling). Add the greeting call only on the first such transition per session:

```kotlin
// Inside SessionCoordinator, guest-side state update logic — adapt to the coordinator's
// actual existing state-update function.

private var hasReachedPlayingThisSession = false

private fun onPlaybackStateReceived(state: ControlMessage.PlaybackState) {
    // ...existing scheduling/state-update logic, unchanged...

    if (!hasReachedPlayingThisSession) {
        hasReachedPlayingThisSession = true
        guestGreetingManager.maybeGreet(
            guestIdentity = persistedGuestIdentity, // Section 10.2's rejoin identity, not raw endpointId
            currentGuestVolume = guestVolumeState.value // live read, not a snapshot
        )
    }
}
```

**Why `Playing`, not `Connected`:** Section 4 states plainly that a session can sit in `SessionState.WaitingForMedia` for an arbitrary stretch while the Host is still picking media — `Connected` can precede real audio by a long, unpredictable gap. Greeting on `Connected` risks a chime followed by dead air. `Playing` means audio is either already flowing or about to be.

**Why the identity must be the rejoin identity, not the raw `endpointId`:** per Section 4's rejoin flow, a guest that crashes and reconnects gets the Host's guest list entry replaced in place, preserving position/name — but keying off the fresh `endpointId` instead makes a rejoining guest look new and re-greets it, which is exactly the "spam/burst" failure this feature exists to prevent. Use whatever identity `RejoinRequest.previousEndpointId` resolves to.

Reset `hasReachedPlayingThisSession` and call `guestGreetingManager.onSessionEnded()` at the same point `SessionCoordinator` already clears session state on `SessionState.Ended` or explicit Leave (Section 10.2).

**Lifecycle wiring:** call `preload()` when the app enters Guest role and starts `SessionState.Discovering` (not at `Application.onCreate`, matching Section 1's contextual resource-allocation discipline); call `release()` wherever the Guest's audio foreground service is torn down (Section 10).

### 14.4 Frequency & identity rules (the anti-spam contract)

1. **Exactly one chime per guest identity per session** — not per app lifetime, not per day. A six-hour session with the same guest connected throughout plays the chime exactly once, at the first `Playing` transition.
2. **A guest that disconnects and rejoins the same session via `RejoinRequest` is not re-greeted** — this is why Section 14.3's identity must be the persisted rejoin identity, not the raw `endpointId`.
3. **A guest that leaves and joins a new, separate session later gets greeted again.** `greetedThisSession` lives in memory, scoped to `GuestGreetingManager`'s lifetime, cleared on `onSessionEnded()` — deliberately not persisted to DataStore.
4. **No global rate limit, cooldown timer, or daily cap is needed or wanted.** Rules 1–3 already produce a natural "once per guest per session" ceiling; a second frequency mechanism would be unnecessary complexity.

### 14.5 Audio files

Five clips, already provided, placed at:
```
app/src/main/res/raw/guest_greet_chime_01.ogg
app/src/main/res/raw/guest_greet_chime_02.ogg
app/src/main/res/raw/guest_greet_chime_03.ogg
app/src/main/res/raw/guest_greet_chime_04.ogg
app/src/main/res/raw/guest_greet_chime_05.ogg
```
If the source files aren't currently `.ogg` (e.g. `.mp3`/`.wav`), don't re-encode purely to match this spec — keep the actual format and adjust the extension/`R.raw.*` references accordingly. `.ogg`/Vorbis is preferred for `SoundPool` (lower decode overhead for short UI sounds), not a hard requirement. The `_0N` suffix indicates five interchangeable variants for random selection, not a ranked sequence — never treat one as a default.

**Required before shipping:** measure each clip's actual playback duration and replace the `CHIME_DURATION_MS` placeholder in Section 14.2 with the longest of the five, rounded up. An under-measured duration means audio focus gets abandoned mid-playback, producing an audible artifact right as the chime plays.

### 14.6 Audio focus & sync-safety requirements (priority order)

Section 6 draws a hard distinction between **permanent** focus loss (pause the Guest's `AudioTrack` outright) and **transient** focus loss (ducking is fine, drift stays inside `DriftCorrectionManager`'s normal tolerance). This chime is a self-inflicted transient event and must never cross into permanent-loss territory.

1. **Request type:** `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, exactly as in Section 14.2. Never request `AUDIOFOCUS_GAIN` (permanent) for this feature — that would compete with the Guest's own ongoing session audio focus in a way Section 6 never sanctions.
2. **Request timing:** request focus immediately before calling `soundPool.play()`. Never request focus, wait, then play — if a settle delay is wanted, delay the entire trigger (wait, then request-and-play as one atomic step).
3. **Release timing:** release focus once the clip's actual measured duration has elapsed (Section 14.5's `CHIME_DURATION_MS`) — never left implicit. `Handler.postDelayed` (Section 14.2) is the minimum acceptable implementation; a `SoundPool.OnLoadCompleteListener`- or coroutine-driven completion callback is equally acceptable if more idiomatic for the rest of the codebase.
4. **Volume:** read `GuestVolumeSlider`'s current value at the exact moment `playChime()` runs (a live `StateFlow`/`State<Float>` read, never a captured snapshot), scaled by `CHIME_VOLUME_SCALE` (0.6 starting point, tune during Section 16 calibration). Never play at full volume, never independent of the Guest's own volume choice.
5. **On focus denial:** if `requestAudioFocus` doesn't return granted, skip the chime entirely — no retry, no queueing, no fallback UI. This is a nice-to-have ambient confirmation, not a critical path.
6. **Never touch `DriftCorrectionManager`, `PresentationScheduler`, or `ClockSyncManager`** to accommodate this feature. If Section 16's calibration pass shows the chime causing audible drift or scheduling glitches, that's a signal the chime's implementation is wrong, not a reason to loosen sync thresholds.

### 14.7 Settings row (cross-reference — spec lives in `HearYet_FRONTEND.md` Section 9.7)

- **Location:** `feature/settings/*`, whatever section groups other Guest/session-adjacent preferences (or near the top-level list if no such section exists).
- **Component:** existing `SettingsRow` (`HearYet_FRONTEND.md` Section 8) — label + trailing `Switch`, no new row style.
- **Label copy:** "Play a sound when you join a session." No exclamation mark. Optional supporting text only if the existing row pattern always includes one: "A short sound plays once when you join, on your device only."
- **Default:** ON. **Persistence:** DataStore, same mechanism as every other setting.
- **Wiring:** `GuestGreetingManager.maybeGreet(...)` checks this flag first; if OFF, it's a no-op. It is not necessary to still mark the guest as greeted internally when OFF — the simplest correct behavior is: check the flag first, and only if ON, proceed to the `greetedThisSession` check.

### 14.8 Testing — folded into Section 16's calibration pass

Add to the existing real-hardware calibration pass rather than a separate test plan:
- A guest hears exactly one chime across the full `Connected → Playing` transition, including rapid Bluetooth disconnect/reconnect during that window.
- A guest that force-closes and rejoins via `RejoinRequest` does not hear a second chime.
- The chime does not audibly delay, glitch, or offset the first scheduled `PresentationScheduler` audio chunk — use the same metronome/clap-track phasing test Section 16 already specifies, listening specifically for artifacts right at session start.
- Toggling the Settings switch OFF suppresses the chime with zero other behavioral change.
- A guest joining a new session after a previous one ended is greeted again (confirms `onSessionEnded()`'s reset is actually wired to the real session-teardown point).

---

## 16. Calibration & testing procedures

This section is the "Section 18" referenced throughout this document as the mandatory real-hardware calibration pass — every numeric constant in Sections 5, 7, 8, and 9 is a tuned starting point until it's been run through this.

**Minimum hardware:** one budget Android phone as Host, at least one second phone as Guest, plus a Classic Bluetooth earbud/speaker pair for the Guest's output (SBC-only if possible, since that's the conservative default most real users will hit). A second Guest device with a different codec class (AAC/aptX) is ideal but not blocking for a first pass.

**Phasing test procedure (resolves the vague "listen for phasing" instruction):**
1. Play a steady, sharp-transient source on the Host — a 440Hz tone loop or a simple metronome/clap track works better than music, since transients make timing offsets obvious to the ear.
2. Stand the Host and Guest device (with its Bluetooth output audible in the room, not just in-ear) close together.
3. Listen for **flanging/phasing** (a swept, comb-filter "whooshing" quality where the two sources are close but not aligned) or a distinct **echo** (a clearly separate repeated hit, meaning the offset is large). Flanging means you're close — nudge `lookaheadMs` in small increments (10–20ms) per the active codec's row in Section 9's table and re-test. A clear echo means the offset is large enough to check the clock-sync convergence and codec detection are actually working, not just fine-tune lookahead.
4. Optionally, record both outputs on a separate microphone/second recorder and visually compare waveform onsets in any audio editor — this catches offsets too small to reliably judge by ear alone.
5. Repeat per codec class you have hardware for; `lookaheadMs` is per-guest/per-codec (Section 9), not a single global constant, so a value that sounds right on one earbud may need separate tuning on another.
6. Once phasing is inaudible, that's your tuned `lookaheadMs` baseline for that codec class — record it back into Section 9's table for the next build.

**Drift-threshold tuning:** during a 15+ minute continuous session (Section 13's build-order step 5), watch `DriftReport` values over time. If `SyncHealth` is oscillating between `GOOD`/`DEGRADED` under normal conditions (no route changes, no network hiccups), `NUDGE_THRESHOLD_MS` (15ms default) is too tight for your hardware — loosen it. If audible phasing reappears before `SyncHealth` drops out of `GOOD`, it's too loose — tighten it. Same logic applies to `SEVERE_DESYNC_THRESHOLD_MS` (150ms default): it should trigger a hard resync before the drift is bad enough that a listener would describe it as "out of sync," not after.

**Screen-off / Doze survival check:** with a session active, turn the Host's screen off and leave it untouched for 5+ minutes. Confirm audio keeps streaming to the Guest without interruption (Section 6's `DummySurface` handling and Section 10's foreground service both need to hold up here) — this is a common silent failure point that won't show up in a quick manual test.

---

## 17. Acceptance criteria (objective — not "an agent says it looks good")

1. Watch plays local media identically to stock NextPlayer, no session code in that path.
2. A Host and one Guest can complete: Create → QR shown → Join scans → clock syncs within 10s → audio starts within one lookahead window of the host's own playback.
3. Killing the guest's Bluetooth mid-session and reconnecting causes no audible resync click and no dropped session.
4. A metronome/clap test between host and guest shows no perceptible phasing after the calibration pass.
5. A second guest joining after playback has started hears correctly-scheduled audio from the moment it connects, not from session start.
6. Ending the session on the Host immediately shows every Guest an explicit "session ended" state — never a frozen or silent screen.
7. Every `SessionError` case (Section 3) has a real, reachable UI state and a real trigger (Section 3's trigger map) — none of them are unreachable dead code.
8. No permission is requested before the feature that needs it is actually invoked.
9. A Guest whose Host process dies silently (no `SessionEnded`) reaches `HOST_UNREACHABLE` within 15 seconds of the last Heartbeat, never sits indefinitely in a stale "in sync" state.
10. A Host seek, pause/resume, or media change never leaves a Guest playing stale-timeline audio for more than one scheduler cycle — flush-and-reseed is visibly instant, not a slow drift back into alignment.
11. A Guest that force-closes and reopens mid-session successfully rejoins via `RejoinRequest` without appearing twice in the Host's guest list.
12. A person can join a session either by scanning the QR or by typing the 6-character `sessionCode` — both paths converge on the identical `Discovering` → `ClockSyncing` → `Connected` flow.
13. A Guest hears the arrival chime (Section 14) exactly once per session, is never re-greeted on a `RejoinRequest` reconnect, and hearing it is confirmed not to audibly disturb the first scheduled `PresentationScheduler` chunk.

---

## 18. Explicitly out of scope for this build

- Any video transport/mirroring to guest screens. Not a phased-in feature — not built at all in this version.
- **Guest-side independent audio track selection.** Guests receive a decoded PCM stream, not the source container — there is no track to choose from on the Guest side. Guests hear exactly what the Host's selected audio track produces; track selection (Section 12) is Host-controlled only, full stop. Do not build any Guest-facing track picker.
- LE Audio/Auracast fast-path detection. Real and worth revisiting once Section 16's calibration shows a real need for it in your user base, but not part of this build.
- Per-device Bluetooth latency calibration via microphone loopback measurement. Optional future refinement.
- Per-device volume normalization across the fan-out (Section 6). Deferred; Guest-local volume control is the only v1 mitigation.
- Mid-session playback speed changes propagated across guests. Speed control stays disabled in an active multi-device session (Section 12).
- Any server, cloud relay, or account system. HearYet is fully local-first; nothing here should assume internet connectivity.
