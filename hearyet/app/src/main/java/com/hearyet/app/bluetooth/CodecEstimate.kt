package com.hearyet.app.bluetooth

/**
 * Per-guest estimate of the active Bluetooth A2DP output codec and the
 * corresponding [lookaheadMs] adjustment for the presentation scheduler.
 *
 * BE §9 — each guest independently detects its own output codec; these
 * values are per-guest, NOT session-global.  The starting points below
 * must be recalibrated against real hardware in Section 16.
 */
enum class CodecEstimate(val defaultLookaheadMs: Int) {
    SBC(250),
    AAC(250),
    APTX(200),
    APTX_HD(200),
    APTX_LOW_LATENCY(150),
    LDAC(280),
    /** No A2DP codec detected — assume conservative SBC-level latency. */
    UNKNOWN_ASSUME_SBC(300),
    /** Wired headset or USB-C DAC — minimal added latency. */
    WIRED(180),
}
