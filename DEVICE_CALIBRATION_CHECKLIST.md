# HearYet — Device Calibration Checklist (§16)

**Purpose:** resolve the C-1 blocker with data. Every numeric constant in §5/§7/§8/§9 of
`HearYet_BACKEND.md` is a *tuned starting point* until this pass is run. The core question:
**can the 5 ms convergence gate ever be met on the reference phones, or do we enable the
15 ms degraded-mode opt-in?** (Audit evidence: 8/8 join attempts failed; best batch stddev
~8.5 ms, typical 9–14 ms, degraded 31–59 ms; `DEGRADED_MODE_ENABLED=false` in
`ClockSyncManager.kt:56-57`.)

**Hardware (minimum):** Realme RMX3853 as **Host** + Infinix X669C as **Guest** + budget
Classic Bluetooth earbuds (SBC) for the Guest. A second Guest on AAC/aptX is ideal but not
blocking for pass 1.

**Prep:**
1. Install the debug APK (CI artifact) on both phones. Same Wi-Fi/BT area, both charged.
2. Prepare a **440 Hz tone loop or metronome/clap track** — transients make offset obvious.
3. Set up log capture on the Host before starting: `adb logcat -s SessionCoordinator -v time > host_cal.log` (the app logs every `Sync failed to converge: stddev=Xms` line).

---

## Phase 1 — Clock-sync convergence (the C-1 decision)

1. Host: Create session → pick the tone track → Start.
2. Guest: Join via QR. Observe result: `SYNC_TIMEOUT` or synced?
3. Record the stddev from each attempt (host log line: `Sync failed to converge: stddev=Xms`).
4. Repeat **10 join attempts** (mix: phones side-by-side, then 2 m apart).
5. **Decision rule (spec §5 degraded-mode clause):**
   - If best/typical stddev stays **< 15 ms** → the sanctioned opt-in applies: set
     `DEGRADED_MODE_ENABLED = true` (allow entry with `SyncHealth.POOR` after the 10 s
     gate fails, per §5) — **do not lower the 5 ms gate itself**.
   - If stddev is **consistently ≥ 15 ms even close together** → clock-sync path has a real
     problem (NTP/BT jitter floor) — fix the estimator before any tuning; do not ship
     degraded mode as a band-aid.
   - Record the full stddev distribution into the results table below.

## Phase 2 — Lookahead phasing test (per codec class)

1. With a synced session (or degraded entry), play the tone/metronome.
2. Stand devices close; Guest audio audible in the room (not in-ear).
3. Listen:
   - **Flanging** (swept comb-filter "whoosh", close but misaligned) → you're close:
     nudge `lookaheadMs` ±10–20 ms per the active codec's §9 table row, re-test.
   - **Echo** (distinct repeated hit) → offset is large: verify clock-sync convergence +
     codec detection are working BEFORE fine-tuning lookahead.
4. Optional: record both outputs on a second mic; compare waveform onsets in an audio
   editor — catches offsets too small for the ear.
5. Repeat per codec class available. `lookaheadMs` is **per-guest/per-codec**, not global.
6. When phasing is inaudible → that's the tuned baseline → **write it back into §9's table**.

## Phase 3 — Drift-threshold tuning (15+ min session)

1. Run a continuous session for 15+ minutes (no route changes, no network hiccups).
2. Watch `DriftReport` / `SyncHealth` over time (host guest-list pill + logs).
3. Decision rules:
   - `SyncHealth` oscillating `GOOD`/`DEGRADED` under normal conditions → `NUDGE_THRESHOLD_MS`
     (15 ms) is **too tight** → loosen.
   - Audible phasing reappears before health leaves `GOOD` → **too loose** → tighten.
   - `SEVERE_DESYNC_THRESHOLD_MS` (150 ms): must hard-resync **before** a listener would
     say "out of sync," not after.

## Phase 4 — Screen-off / Doze survival (5+ min)

1. Active session, Host screen off, untouched **5+ minutes**.
2. Audio must keep streaming to the Guest uninterrupted (tests §6 DummySurface handling +
   §10 foreground service — a known silent failure point).
3. Repeat with Guest screen off (BT route manager path).

## Phase 5 — Record & commit

| Item | Value measured | Decision |
|---|---|---|
| stddev distribution (10 attempts) | | gate keep 5 ms / degraded 15 ms |
| lookaheadMs — SBC | | write into §9 table |
| lookaheadMs — AAC/aptX (if available) | | write into §9 table |
| NUDGE_THRESHOLD_MS (15 ms) | | tighten / keep / loosen |
| SEVERE_DESYNC_THRESHOLD_MS (150 ms) | | tighten / keep / loosen |
| Screen-off verdict (Host/Guest) | | pass / fail |

Commit changes as a labeled commit (e.g. `calib: RMX3853+X669C — degraded 15ms, lookahead SBC 280ms`)
with this file updated — never ship tuned constants without the data behind them.

**Pass criteria for the whole pass:** a Guest can Join and hear the Host in sync (no flanging)
for a 15+ min session including screen-off, with health stable at GOOD or DEGRADED-with-cause.
