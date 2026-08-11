# HearYet Backend — Project Knowledge (for future agents)

> Status snapshot: **SPEC-CONFORMANT** as of 2026-08-08. Backend sync code audited
> line-by-line against the governing spec; **82 unit tests, 0 failures**;
> `ktlintCheck` and `:app:assembleDebug` green. This file is the fast
> re-grounding reference — it summarizes work already done so it never has to
> be re-derived.

---

## 1. Ground truth (use ONLY these)

| Item | Path |
|---|---|
| Working project | `C:\Users\sk638\Downloads\Desktop\HearYet\HearYet OG\hearyet` |
| Governing spec (SOLE authority) | `C:\Users\sk638\Downloads\Desktop\HearYet OG\HearYet_BACKEND.md` (778 lines) |
| Dev guide (build/test/lint commands) | `C:\Users\sk638\Downloads\Desktop\HearYet\AGENTS.md` |
| Ignore / do NOT touch | `C:\Users\sk638\Downloads\Desktop\HearYet OG\hearyet` (stale old-UI copy) |

- If any file conflicts with `HearYet_BACKEND.md`, **the spec wins** (§5, §7, §8
  govern clock/scheduler/drift; §16 calibration governs numeric tuning).
- Gates after ANY code change: `:app:testDebugUnitTest` + `ktlintCheck` +
  `:app:assembleDebug` must all pass (EXIT=0).

### Locked decisions — never re-litigate (§0)
- Audio-only fan-out to guests. No video transport, no hooks for it.
- Three home actions: **Watch** (solo, zero session code), **Create** (Host), **Join** (Guest).
- No internet, no shared Wi-Fi. Devices physically near each other only.

---

## 2. Conformance status

- **CONFORMANT.** Every backend file audited line-by-line against the spec
  (models, transport, §5 clock math, §6 pipeline, §7 scheduler, §8 drift,
  §9 Bluetooth, §10 lifecycle, §11 QR, §14 chime).
- One **documented structural deviation** (not a content change): `SessionState`,
  `SessionError`, `SyncHealth`, `GuestInfo` live in `:core:model`
  (core/model/SessionState.kt, GuestInfo.kt) instead of `sync/SessionModels.kt`
  so `:core:ui` / `:feature:player` can use them without depending on `:app`.
  `sync/SessionModels.kt` holds only `SessionRole`.
- `SessionError` gains an optional `detail` field (additive; feeds §17.7's
  honest-failure UI). `ControlMessage` additionally has
  `SessionHandshake`/`SessionHandshakeAck` — the §4-sanctioned post-connect
  sessionId confirmation.

---

## 3. The math (spec-verbatim; do not "improve")

### §5 Clock sync (L293-294, L308-323)
```kotlin
rtt     = (t3 - t0) - (t2 - t1)
offset  = ((t1 - t0) + (t2 - t3)) / 2.0
```
- Estimate: sort samples by RTT, keep lowest 75% (`coerceAtLeast(1)`), take the
  **median** of the remaining offsets; if <2 remain, use the lowest-RTT sample.
- **Gate**: stddev of ALL batch offset samples < 5ms. Exit `ClockSyncing`
  immediately on convergence; re-batch until the 10s deadline → honest
  `SYNC_TIMEOUT`.
- **Degraded fallback (L304)**: OFF by default (`DEGRADED_MODE_ENABLED=false`),
  15ms ceiling, entry only allowed **after the 10s deadline has actually
  elapsed** (a teardown/interrupt exit must never accept it), enters with
  `SyncHealth.POOR` via `DriftCorrectionManager.markDegradedEntry()`.
- Background re-sync every 30–60s (randomized) for crystal drift.

### §7 Scheduler (L364, L366)
```kotlin
guestPlaybackTimeNanos = hostTimestampNanos + (clockOffsetMs * 1_000_000) + (lookaheadMs * 1_000_000)
```
- `lookaheadMs` starts at 250ms; per-guest, per-codec tunable (§9).
- Writes use `WRITE_NON_BLOCKING`; precise waits via `LockSupport.parkNanos`
  (never coarse `Thread.sleep` in the playback loop).
- Chunks whose target has passed by **more than one frame (20ms)** are dropped,
  not played late. `LATE_GRACE_MS = 20`.
- **Exactly one seeding path** (`seedFromNow()`): initial join, latecomer,
  rejoin-after-crash, exiting ClockSyncing. Seek/pause-resume/AudioTrackChanged
  = flush + reseed; **MediaChanged = flush ONLY** — reseed happens when the new
  `PlaybackState` arrives.

### §8 Drift (L387-393)
```
|drift| < 15          → GOOD,   no correction
15 ≤ |drift| < 50      → DEGRADED, ±0.5% speed (1.005 / 1/1.005)
50 ≤ |drift| < 150     → POOR,   ±1.5% speed (1.015)
|drift| ≥ 150          → POOR,   hard resync (scheduler.flush + speed reset)
```
- Evaluated every 1.5s (midpoint of §8's 1–2s); `DriftReport` to host on a
  **fixed 2s** interval. Ahead → slow down; behind → speed up.
- Baseline (head frames + nanoTime) rebased on any flush or pause→resume so the
  lookahead fill and pause gaps never read as drift.

### §10.1 Heartbeat
- Host broadcasts `Heartbeat` every 5s; ANY host message resets the guest's
  timer; 15s silence → `SessionState.Error(HOST_UNREACHABLE)` (checked every 1s).

---

## 4. Constants (all locked by spec; named, no magic numbers)

| Constant | Value | Lives in |
|---|---|---|
| `SAMPLE_COUNT` | 10 | ClockSyncManager.kt |
| `SAMPLE_INTERVAL_MS` | 200 (10×200 ≈ 2s batch by construction) | ClockSyncManager.kt |
| `CONVERGENCE_THRESHOLD_MS` | 5.0 | ClockSyncManager.kt |
| `DEGRADED_THRESHOLD_MS` | 15.0 | ClockSyncManager.kt |
| `DEGRADED_MODE_ENABLED` | **false** (never ship enabled) | ClockSyncManager.kt |
| `SYNC_TIMEOUT_MS` | 10_000 | ClockSyncManager.kt |
| resync interval | 30_000–60_000 | ClockSyncManager.kt |
| `DEFAULT_LOOKAHEAD_MS` | 250 | PresentationScheduler.kt |
| `LATE_GRACE_MS` / `FRAME_MS` | 20 / 20 | PresentationScheduler.kt |
| `MAX_QUEUED_CHUNKS` | 200 (≈4s; drop OLDEST first) | GuestOutboundQueue.kt |
| GOOD / DEGRADED / SEVERE | 15 / 50 / 150 | DriftCorrectionManager.kt |
| GENTLE / AGGRESSIVE nudge | 1.005 / 1.015 | DriftCorrectionManager.kt |
| `EVALUATION_INTERVAL_MS` / `REPORT_INTERVAL_MS` | 1500 / 2000 | DriftCorrectionManager.kt |
| `HEARTBEAT_INTERVAL_MS` / `HOST_UNREACHABLE_TIMEOUT_MS` | 5_000 / 15_000 | SessionCoordinator.kt |
| `DISCOVERY_TIMEOUT_MS` | 15_000 (→ `DISCOVERY_FAILED`) | SessionCoordinator.kt |
| codec lookaheads | SBC 250, AAC 250, APTX 200, APTX_HD 200, APTX_LL 150, LDAC 280, UNKNOWN/SBC-safe 300, WIRED 180 | CodecEstimate.kt |
| frame size | 20ms = 960 samples/ch @48kHz (3840 B stereo) | SharedAudioRenderer.kt |
| chime | 5 clips, `CHIME_DURATION_MS=4000` (measured longest = 3631ms), `CHIME_VOLUME_SCALE=0.6` | GuestGreetingManager.kt |

---

## 5. Backend file map (all in `app/src/main/java/com/hearyet/app/`)

| Package / file | Role |
|---|---|
| `sync/SessionCoordinator.kt` | Orchestrator (Host+Guest); heartbeat, persistence, teardown, guest pipeline |
| `sync/ClockSyncManager.kt` | §5 batch loop, math in companion, background re-sync |
| `sync/PresentationScheduler.kt` | §7 ring buffer (ConcurrentSkipListMap keyed by target time), AudioTrack writer |
| `sync/DriftCorrectionManager.kt` | §8 eval/report threads, speed nudges, hard resync, `markDegradedEntry()` |
| `sync/GuestOutboundQueue.kt` | §6 per-guest bounded queue (drop-oldest) |
| `sync/GuestGreetingManager.kt` | §14 chime (SoundPool, once per guest identity per session) |
| `sync/GuestAudioFocusManager.kt` | §6 focus (permanent loss → pause; transient → duck) |
| `sync/SessionDataStore.kt` | §10.2 DataStore persistence (role, sessionId, previousEndpointId…) |
| `sync/SessionModels.kt` | `SessionRole` only (rest in `:core:model`) |
| `transport/NearbyTransportManager.kt` | P2P_STAR, BYTES (control) + STREAM (PCM) channels |
| `transport/ControlMessage.kt`, `SessionPayload.kt`, `SessionPayloadCodec.kt` | §3 messages, QR payload (Crockford Base32, 6 chars), codec |
| `bluetooth/BluetoothRouteManager.kt`, `BluetoothCodecDetector.kt`, `CodecEstimate.kt` | §9 route-change → lookahead only; SBC-conservative fallback |
| `qr/QrGenerator.kt`, `QrScannerAnalyzer.kt` | §11 |
| `feature/player/sync/SharedAudioRenderer.kt` | §6 PCM tap (Media3 AudioProcessor, end of sink chain) |
| `feature/player/sync/AudioChunk.kt` | chunk + 16-byte binary header for STREAM |
| `feature/player/sync/HearYetRenderersFactory.kt` | inserts tap via `DefaultAudioSink.Builder.setAudioProcessors` |
| `feature/session/{host,join,ended,guest}/*` | session UI + `GuestSessionService` (foreground) |
| `feature/permission/PermissionRequiredScreen.kt` | `nearbyRuntimePermissions()` (BT scan/connect/advertise, NEARBY_WIFI_DEVICES, FINE_LOCATION) |

Key flows: guest = `onQrScanned`/`onCodeEntered` → same `startGuestDiscovery`
(endpoint-name match + post-connect `SessionHandshake` sessionId confirm) →
`ClockSyncing` → batch convergence → `onGuestSyncReady` (scheduler + AudioTrack
+ drift + BT route + background resync + foreground service) → `Connected` →
`PlaybackState` → `Playing` (+ one chime). Host = `startAsHost` → advertise →
per-guest queue+sender thread → latecomer/rejoin `PlaybackState` reseed.

---

## 6. Test infrastructure (82 tests, 0 failures)

Location: `app/src/test/java/com/hearyet/app/` and
`feature/player/src/test/java/com/hearyet/app/feature/player/sync/`.
Dependencies: JUnit4, Robolectric 4.16.1, kotlinx-coroutines-test, **MockK
1.14.11** (test-only, in `gradle/libs.versions.toml`).

### What pins what
- **ClockSyncManagerTest / SessionSerializationTest / DriftCorrectionManagerTest /
  PresentationSchedulerTest** — pure math (§5 formulas, estimator, drift math,
  scheduler formula, message round-trips).
- **GuestOutboundQueueTest / SessionPayloadTest / SharedAudioRendererTest** —
  drop-oldest order, endpoint-name contract, frame/overflow handling.
- **ClockSyncManagerBehaviorTest** (6, ~45s real) — Fix A (~2s batch even on an
  instant link), re-batch-until-converged, honest ~10s `SYNC_TIMEOUT`, degraded
  opt-in after deadline, **Fix B regression** (interrupt → never degraded),
  **D3 regression** (gate uses ALL samples, not the 75% subset).
- **SessionCoordinatorBehaviorTest** (9) — rejoin replace-in-place, drift→health
  mapping, latecomer reseed, handshake-mismatch rejection, `SessionEnded`,
  15s `HOST_UNREACHABLE`, **D4** (MediaChanged flush-only, reseed on
  PlaybackState, scheduler stays alive), chime exactly once, orphaned-batch
  regression (leave mid-sync → no late `SYNC_TIMEOUT`).
- **TwoPartySyncSimulationTest** (3) — real host `handleSyncRequest` + real guest
  batch over a faked wire: offset recovery, ~2s span at exchange level, honest
  timeout/degraded, 10-parallel-batch Monte-Carlo.
- **PresentationSchedulerBehaviorTest** (5) / **DriftCorrectionManagerBehaviorTest**
  (3) — Robolectric AudioTrack: due-chunk write, drop-late rule, precise future
  wait, flush/seed keep track alive, paused-track never measured, ≥150ms hard
  resync, `markDegradedEntry`.

### Working patterns (reuse, don't reinvent)
- **Fake wire**: `mockk<NearbyTransportManager>(relaxed = true)` + `every {
  sendControlMessage(ep, any()) } answers { … offer into
  manager.pendingSyncResponseQueue }` — the SAME queue the coordinator uses.
  Inject offsets via `t1 = t0 + bias + 1, t2 = t0 + bias + 2` (constant bias →
  stddev ≈ 0; alternating 90/110ms → stddev ≈ 10ms).
- **Sticky var properties**: MockK does NOT store `var` assignments on mocks.
  For coordinator-wired callbacks use
  `every { transport.onX = capture(slot) } just Runs` +
  `every { transport.onX } answers { slot.captured }`, then invoke `slot.captured`.
- **GMS stub**: `mockkObject(GoogleApiAvailability.getInstance())` + `every {
  it.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS`
  (stubbing only `getInstance()` is NOT enough — the instance method still runs
  real code and bails the coordinator to `DEVICE_INCOMPATIBLE`).
- **Permissions**: `shadowOf(RuntimeEnvironment.getApplication() as
  Application).grantPermissions(*nearbyRuntimePermissions())`.
- **Shared-flag hazard**: `ClockSyncManager.DEGRADED_MODE_ENABLED` is a
  companion `@Volatile` — tests that flip it MUST reset it in `finally`.
- **Robolectric realities**: `System.nanoTime()` is NOT virtualized (heartbeat
  tests idle the looper 1s + sleep real time per iteration); ShadowAudioTrack
  has NO head-position setter (its head stays 0 → drift tests exploit the
  resulting hard-resync path); `android.util.Log` requires the Robolectric
  runner in this project.
- **Test-time costs**: slot loop = 2s/batch, deadline tests = ~10s each (constants
  are locked — never fake these).

### Known production nuance (documented, not a bug)
- Interrupting `performSyncBatch` while blocked in `LinkedBlockingQueue.poll`
  throws `InterruptedException` (callbacks are skipped); interrupting during a
  slot `Thread.sleep` exits cleanly through the timeout decision
  (`onResult(-1)`). Fix B's guarantee — "never degraded before the deadline" —
  holds in both cases. The orphaned-batch guard (`syncPipelineJob` cancel +
  `isActive` gate in the batch callback) makes teardown safe.

---

## 7. Bugs found & fixed (all spec-cited, verified by tests)

| Fix | Spec cite | Change |
|---|---|---|
| D1 batch shape | §5 L298+L302 | 10 probes, one per 200ms slot → ~2s batch by construction; gate after each batch; leave ClockSyncing immediately on convergence; 10s deadline → SYNC_TIMEOUT |
| D2 degraded defaults | §5 L304 | `DEGRADED_THRESHOLD_MS=15.0`, `DEGRADED_MODE_ENABLED=false` |
| D3 gate population | §5 L302 | stddev over ALL batch samples (estimate still lowest-RTT 75%) |
| D4 MediaChanged | §7 L376 | guest flushes only; reseed on the new PlaybackState; AudioTrackChanged stays flush+reseed (§12 L520) |
| D5 single seed path | §7 L370 | `onGuestSyncReady` calls `seedFromNow()` — all four transitions share it |
| Fix A (follow-up) | §5 L298 | slot-spaced probes — batch duration no longer RTT-dependent |
| Fix B (follow-up) | §5 L304 | degraded entry requires `System.nanoTime() >= deadline` |
| Sender-thread guard | §10 | host audio-sender thread try/finally on disconnect |
| Stale-endpoint cleanup | §4 | `connectToHost` disconnects prior endpoint (fixes Nearby 8003) |
| Location permissions | §1 | manifest + `PermissionRequiredScreen` (fixes Nearby 8034) |
| Media-pick URI adoption | §4/§11 | HearYetNavGraph adopts the picked URI |
| **getProfileProxy guard** | §9 (new this session) | `BluetoothRouteManager.start` wrapped `getProfileProxy` in try/catch — a SecurityException was killing the guest sync pipeline |
| **bufferSize stat** | §7 (new this session) | `PresentationScheduler.flush()`/`seedFromNow()` now set `bufferSize = 0` |
| **Orphaned sync batch** | §10 (new this session) | `syncPipelineJob` cancelled in teardown + `isActive` gate in the batch callback — leaving mid-sync can no longer resurrect `Error(SYNC_TIMEOUT)` |

Test seams added (additive, defaults preserve behavior): `SessionCoordinator`
takes `transportOverride` + `guestGreetingManagerFactory`; the six manager
fields are `internal`.

---

## 8. Honest limits & expectations (device-only items)

- **No audible latency has been measured.** Design target: guest hears audio
  ~250ms after the host (lookahead 250ms nominal, 150–300ms per §9 codec).
  §16's calibration pass (tone/metronome phasing test, optional mic recording)
  is the sanctioned way to tune — never by raising the 5ms gate or the 15ms
  degraded ceiling.
- **Honest `SYNC_TIMEOUT` is expected** with degraded OFF on noisy links: the
  two devices measured 28–86ms batch stddev — above the 5ms gate. That is the
  spec's designed default (§5 L304), not a regression.
- `BluetoothCodecDetector.getCodecStatus` was removed from the public Android
  SDK — detection always returns `UNKNOWN_ASSUME_SBC` (300ms lookahead). This
  is the spec-sanctioned conservative branch (§9); no reflection workaround.
- Guest audio focus: permanent loss → pause (never release); regain →
  flush + reseed. Transient → duck only.
- Screen-off: `DummySurfaceHelper` keeps the PCM tap alive while the host's
  screen is off — verify for 5+ min during §16.
- Volume normalization is a documented v1 non-goal; guest-local volume slider only.

---

## 9. Device verification runbook (blocked: needs both phones)

1. **Reboot both phones first** (the last session ended with the guest's Nearby
   discovery finding nothing after an APK reinstall + `pm clear` — GMS Nearby
   radio state; reboot fixes it, USB reconnects after boot).
2. `adb shell settings put secure location_mode 3` on both; grant
   nearby/location permissions; reinstall the current debug APK
   (`:app:assembleDebug` → per-ABI splits under `app/build/outputs/apk/debug/`).
3. Realme = **Host** (Create → pick file → Start playback); Infinix = **Guest**
   (Join → 6-char code).
4. Watch logcat tags: `SessionCoordinator`, `ClockSyncManager`,
   `NearbyTransportMgr`, `PresentationScheduler`, `DriftCorrection`.
5. Expected markers: Endpoint found → matching host found → connected → Sync
   converged **or honest SYNC_TIMEOUT** → Guest sync pipeline ready →
   AudioTrack opened → chunk flow (`chunksPlayed` advancing) → drift GOOD.
6. Known spec-correct outcome: with degraded OFF and 28–86ms device stddev, a
   noisy link may legitimately hit `SYNC_TIMEOUT`. The §16 calibration pass is
   the sanctioned path to enable the 15ms opt-in — never silently raise
   thresholds.

---

## 10. Rules for future agents (hard rules)

1. Do NOT re-litigate locked decisions (§0): no internet, audio-only fan-out,
   three home actions.
2. Do NOT raise the 5ms gate or the 15ms degraded ceiling, and do NOT enable
   degraded mode by default — the spec forbids it (L304).
3. Keep the math byte-identical to the spec formulas (L293-294, L308-323,
   L364, L387-393).
4. Any change must cite the spec line it implements. If it can't be cited,
   don't ship it.
5. After any change: `:app:testDebugUnitTest` + `ktlintCheck` +
   `:app:assembleDebug` all EXIT=0. AGENTS.md code rules apply: no `!!`, no
   wildcard imports, named constants, `Formatting` utilities, `Build.VERSION_CODES.*`.
