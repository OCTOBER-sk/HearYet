# HearYet — Real-Device Verification Plan (for the next agent)

> You are taking over a project that is already **spec-conformant in code** and
> verified by **82 unit tests** (all green). What has never been proven is the
> runtime behavior on the two physical phones. This plan is the complete
> device-verification runbook. Follow it in order; record results in the log
> template at the end. Do NOT skip the pre-flight (Section 2) — a known GMS
> Nearby radio issue depends on it.

---

## 1. Everything you need (locations)

| Item | Path |
|---|---|
| Knowledge file (READ FIRST — 20 min) | `C:\Users\sk638\Downloads\Desktop\HearYet\HEARYET_BACKEND_KNOWLEDGE.md` |
| Governing spec (sole authority) | `C:\Users\sk638\Downloads\Desktop\HearYet OG\HearYet_BACKEND.md` (778 lines) |
| Dev guide (build/test/lint gates) | `C:\Users\sk638\Downloads\Desktop\HearYet\AGENTS.md` |
| Working project | `C:\Users\sk638\Downloads\Desktop\HearYet\HearYet OG\hearyet` |
| Build output (APKs, per-ABI splits) | `hearyet\app\build\outputs\apk\debug\app-<abi>-debug.apk` |
| Stale copy — DO NOT TOUCH | `C:\Users\sk638\Downloads\Desktop\HearYet OG\hearyet` |
| Plan doc (this file) | `C:\Users\sk638\Downloads\Desktop\HearYet\HEARYET_DEVICE_VERIFICATION_PLAN.md` |

Hard rules (from the knowledge file §10):
1. No re-litigating locked decisions; no raising the 5ms gate or 15ms degraded
   ceiling; no enabling degraded mode by default.
2. Any code change must cite a spec line — and then
   `:app:testDebugUnitTest` + `ktlintCheck` + `:app:assembleDebug` must all pass.
3. If a test "fails", first classify it: device-environment issue vs. code bug.
   Most expected results on noisy links are honest `SYNC_TIMEOUT` — that is
   spec-correct, not a failure.

---

## 2. Pre-flight (do this exactly, ~20 min)

### 2.1 Both phones — reboot FIRST
The previous session ended with the guest's Nearby discovery finding nothing
while the host logged "Advertising started" (GMS Nearby radio state broken by an
APK reinstall + `pm clear`). A full reboot of both phones fixes it.

### 2.2 Verify the build is current and install
```powershell
# from the project root (PowerShell):
.\gradlew.bat :app:assembleDebug
adb devices
```
Identify the two devices:
```powershell
adb devices -l                       # note serials; Realme = HOST, Infinix = GUEST
adb -s <SERIAL> shell getprop ro.product.model
adb -s <SERIAL> shell getprop ro.product.cpu.abi   # pick the matching ABI APK
```
Install the correct ABI APK on each (arm64-v8a on most modern phones):
```powershell
adb -s <HOST_SERIAL> install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
adb -s <GUEST_SERIAL> install -r app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

### 2.3 Permissions & settings (both phones)
```powershell
adb -s <SERIAL> shell settings put secure location_mode 3
adb -s <SERIAL> shell pm grant com.hearyet.app android.permission.ACCESS_FINE_LOCATION
adb -s <SERIAL> shell pm grant com.hearyet.app android.permission.BLUETOOTH_CONNECT
adb -s <SERIAL> shell pm grant com.hearyet.app android.permission.BLUETOOTH_SCAN
adb -s <SERIAL> shell pm grant com.hearyet.app android.permission.BLUETOOTH_ADVERTISE
adb -s <SERIAL> shell pm grant com.hearyet.app android.permission.NEARBY_WIFI_DEVICES   # API 33+
```
(Also grant CAMERA on the guest so the QR scanner works, and media/photo perms on
the host so the picker can open a file.)

### 2.4 Prepare log capture (both devices, before each test phase)
```powershell
adb -s <SERIAL> logcat -c
# capture into a file for the whole session:
adb -s <SERIAL> logcat -v threadtime -s SessionCoordinator:V ClockSyncManager:V NearbyTransportMgr:V PresentationScheduler:V DriftCorrection:V BluetoothRouteMgr:V GuestGreetingMgr:V AndroidRuntime:E > <session_dir>\<phase>_<role>.log
```

### 2.5 Sanity check first (no session)
Launch the app on both; verify Home shows the three actions (Watch/Create/Join).
On the HOST only: Create → QR + 6-char code visible. On the GUEST: Join →
scanner opens (camera permission). Close everything cleanly.

---

## 3. Test matrix (run in order; each row = one test case)

Legend for the result column: ✅ PASS / ⚠️ EXPECTED (spec-correct, document
evidence) / ❌ FAIL (needs root-cause + spec-cited fix).

### Phase A — Transport & join (§4) — ~15 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| A1 | Host advertising + guest discovery | Host: Create. Guest: Join → scan QR | Guest logs: `Endpoint found` → `matching host found` → `Connection request sent` | |
| A2 | Session handshake confirm | follow A1 through connection | `Session handshake confirmed` → guest enters `ClockSyncing`; no `handshake mismatch` | |
| A3 | Typed-code join | Guest: Join → "Enter code instead" → type host's 6-char code | Converges on the identical discovery path (same logs as A1) | |
| A4 | QR-payload vs. code identity | Compare: both paths connect to the SAME host | No duplicate guest on the host's list | |
| A5 | Latecomer join mid-playback | Host starts playback first; guest joins AFTER | `latecomer join — sending PlaybackState`; guest goes straight to `Playing` (seeds from now, not session start) | |
| A6 | Rejoin replace-in-place | Guest force-stops app mid-session, reopens, rejoins | Host logs `RejoinRequest`; guest list size unchanged (entry replaced, not appended) | |
| A7 | Wrong-session rejection | Scan a QR from a different/fake host (or tamper payload) | `QR_INVALID` / `PAYLOAD_INVALID` / `handshake mismatch` → `CONNECTION_FAILED`; no crash | |

### Phase B — Clock sync (§5) — ~15 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| B1 | Convergence on clean link | Two phones side by side, quiet radio; watch `ClockSyncManager` | `Sync converged: offset=…ms stddev=…ms` with stddev < 5ms; state → `Connected` | |
| B2 | Batch cadence (Fix A) | Watch timestamps of the 10 probes in logcat | 10 probes ~200ms apart → batch ≈ 2s (NOT collapsed on a fast link) | |
| B3 | Background re-sync | Stay connected 60+ s; watch logs | `Background re-sync starting` roughly every 30–60s, reuses same offset path | |
| B4 | Honest SYNC_TIMEOUT (expected on noisy link) | Hold guest far apart / behind a body / BT+Wi-Fi off; watch deadline | Either converges or logs `Sync failed to converge … > threshold` → `SYNC_TIMEOUT` state at ~10s. **Record the stddev** — this is the §16 calibration input | |
| B5 | Clock uses nanoTime, not wall-clock | Change guest's wall clock mid-session (Settings) | No offset jump, no resync triggered by the clock change | |

### Phase C — Audio pipeline & scheduler (§6, §7) — ~15 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| C1 | Guest hears audio | Host plays a test file; guest has earbuds/ speaker audible | `AudioTrack opened` → `chunksPlayed` advancing monotonically; audio audible on guest | |
| C2 | Chunk flow rate | Watch `chunksPlayed` over 30s | ≈ 50 chunks/s (20ms frames); no long stalls; no `chunksDropped` storm on clean link | |
| C3 | No audible gap on drop | Compare host vs guest audio casually | Single dropped frame inaudible (spec §6/§7) | |
| C4 | Seek | Host seeks during playback | Guest logs `SeekTo — flushed scheduler`; audio resumes at new position, no stale audio | |
| C5 | Pause/resume | Host pauses 5s then resumes | Guest flushes+reseeds on resume; no stale timeline audio; `chunksPlayed` resets then advances | |
| C6 | Media change | Host switches to a different file | Host broadcasts `MediaChanged` then `PlaybackState`; guest flushes on MediaChanged, reseeds on PlaybackState; new audio plays | |
| C7 | Audio track change (if multi-track file available) | Host switches audio track | Guest flushes+reseeds like a seek; no glitch | |
| C8 | Backward seek (documented v1 limit) | Host seeks backward | Guest flushes; no replay of earlier audio (expected — spec §7 L372) | |
| C9 | Lookahead sanity | With earbuds in, judge delay between host's speaker and guest's audio | Delay ≈ lookahead (250ms nominal; tune only via §16, not code) | |

### Phase D — Drift correction (§8) — 15+ min (long run)
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| D1 | 15-min drift soak | Host + guest playing continuously, logcat recording | `drift=…ms health=GOOD` throughout; `SyncHealth` stays GOOD on a clean link; no hard resyncs | |
| D2 | Nudge behavior | Artificially create drift (walk guest far / let buffer underrun) and watch | `DriftReport` values rise; health GOES → DEGRADED/POOR briefly; speed nudges applied; recovers to GOOD | |
| D3 | Hard resync path | Force severe desync (block guest's BT briefly) | `Severe desync (…ms) — hard resync triggered`; audio re-aligns; only on |drift| ≥ 150ms | |
| D4 | Host guest-list health | Host's in-session guest list during D1 | drift + SyncHealth per guest update every ~2s (DriftReport cadence) | |

### Phase E — Bluetooth (§9) — ~10 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| E1 | Route change survival | Guest earbuds: disconnect mid-session, reconnect | No resync event, no audio stop, no state change; `Route change → codec: …` logged; lookahead updated for future chunks only | |
| E2 | Codec fallback | Verify detection logs | `getCodecStatus is not available in the public SDK — assuming SBC` (expected; §9 conservative path) | |

### Phase F — Lifecycle & edge cases (§10) — ~15 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| F1 | Host ends session | Host taps End | Guests see explicit `Session ended` state (never frozen/silent); `SessionEnded` broadcast logged | |
| F2 | Host silent death | Kill host app (swipe away) without ending | Guest reaches `HOST_UNREACHABLE` within ~15s of last heartbeat | |
| F3 | Heartbeat cadence | Watch host logcat | `Heartbeat` broadcast every ~5s, including while paused / waiting for media | |
| F4 | Guest app backgrounded | Guest presses Home while playing, screen off 5+ min | Audio continues (foreground service); no dropouts after Doze | |
| F5 | Host screen off 5+ min | Host screen off during active session | Guest keeps receiving chunks (DummySurface keeps decode alive) — verify `chunksPlayed` keeps advancing | |
| F6 | Process-death restore | Guest force-stops + reopens mid-session | Auto-reconnect via persisted session (`RejoinRequest`), routed into session UI, not Home | |
| F7 | Host process-death restore | Host force-stops + reopens mid-session | Routes Home, persisted session cleared (no silent resume — spec §10.2) | |
| F8 | Leave during sync | Guest leaves while still in `ClockSyncing` | State → Idle; NO late `SYNC_TIMEOUT` error ~10s later (orphaned-batch guard regression) | |

### Phase G — Greeting chime (§14) — ~10 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| G1 | Chime once per session | Guest joins and reaches first `Playing` | Exactly one chime, heard only on guest, at first Playing (not at connect) | |
| G2 | No re-chime on rejoin | Force-stop + rejoin the SAME session | No second chime | |
| G3 | New session → new chime | End session, join a new one | Chime plays again | |
| G4 | Toggle OFF | Settings: "Play a sound when you join a session" → OFF; rejoin | No chime, zero other behavior change | |
| G5 | Chime doesn't disturb sync | Listen during G1 | No audible glitch at session start (chime never touches scheduler/clock/drift) | |

### Phase H — §16 calibration data collection (NOT tuning — measure & record) — ~20 min
| # | Test case | Steps | Expected | Result |
|---|---|---|---|---|
| H1 | Offset stddev sample | Run B4's noisy scenario 3×, record each `stddev` | Records (device link quality); feeds the degraded-mode opt-in decision later, if ever justified | |
| H2 | Phasing/echo listen | 440Hz tone or metronome on host, guest earbuds; listen for flanging vs echo | Qualitative note per codec in use; do NOT change code — record findings for the human | |
| H3 | Drift thresholds observation | From D1 logs: min/max/avg `driftMs`, number of DEGRADED transitions | Records for potential §16 tuning of NUDGE/SEVERE thresholds (human decision) | |
| H4 | Screen-off/Doze 5+ min | Covered by F4/F5 — consolidate evidence | Pass/fail with log evidence | |

---

## 4. Failure handling (read before testing)

1. **Classify**: restart the phase once (radio flakiness). If it persists, check
   whether the failure is reproducible and whether logcat shows a code path
   (tag + line) vs. an environment cause (no host found, timeouts, status codes).
2. **Environment issues** (no discovery, status 8003/8034, radio dead): follow
   the reboot → location_mode 3 → reinstall sequence; do not "fix" in code.
3. **Code bugs**: before touching anything, write the failing behavior + logcat
   evidence into the report. Any fix MUST cite its spec line (knowledge file §10)
   and then pass `:app:testDebugUnitTest` + `ktlintCheck` + `:app:assembleDebug`.
   Re-run the affected unit suite (e.g. `ClockSyncManagerBehaviorTest` for sync
   changes) AND the related device test.
4. **Never** raise the 5ms gate / 15ms degraded ceiling, and never enable
   degraded mode by default, to "make a test pass". If `SYNC_TIMEOUT` is the
   result, it is the spec's designed default (§5 L304) — record the stddev
   numbers as §16 calibration input instead.

---

## 5. Deliverables (what you must produce when done)

1. **`DEVICE_VERIFICATION_RESULTS.md`** in
   `C:\Users\sk638\Downloads\Desktop\HearYet\` containing:
   - Devices used (model, Android version, ABI), APK build timestamp, session date
   - The full test matrix above with ✅/⚠️/❌ and one line of evidence each
   - All collected stddev/drift numbers (H1/H3) as the calibration dataset
   - Any ❌ with: reproduction steps, logcat excerpt, root cause hypothesis,
     and the spec line a fix would implement
2. **Log files**: one logcat capture per phase per device (kept in
   `C:\Users\sk638\Downloads\Desktop\HearYet\logs\`).
3. **Summary verdict**: CONFORMANT-ON-DEVICE / CONFORMANT-WITH-CAVEATS /
   NON-CONFORMANT, with the exact caveats listed.

## 6. Time budget
Pre-flight 20 min · Phase A 15 · B 15 · C 15 · D 15+ · E 10 · F 15 · G 10 ·
H 20 · report 15 → **~2.5–3 hours** of hands-on testing, longer if any ❌
requires a code fix.
