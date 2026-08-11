package com.hearyet.app.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.util.Log

/**
 * Detects the active Bluetooth A2DP codec for a connected audio device so
 * the guest's PresentationScheduler can set a codec-aware [lookaheadMs].
 *
 * BE §9 — detection rules (verbatim):
 * - Query via [BluetoothA2dp.getCodecStatus] where available (API 26+).
 * - On API < 26 OR runtime unavailability: return [CodecEstimate.UNKNOWN_ASSUME_SBC].
 * - **Never** use reflection as a workaround — go straight to SBC-safe default.
 * - Per-guest detection; never a session-global constant.
 *
 * Note: `getCodecStatus` was removed from the public Android SDK (it became a
 * system API), verified against the compile SDK. Per BE §9, detection therefore
 * falls back to [CodecEstimate.UNKNOWN_ASSUME_SBC] on every device — never a
 * reflection workaround. If the public API is ever re-exposed, restore the
 * BE §9 `codecType` mapping (SBC/AAC/APTX/APTX_HD/LDAC) here.
 */
object BluetoothCodecDetector {

    private const val TAG = "BluetoothCodecDetector"

    /**
     * Detect the active codec for [device]. Always returns
     * [CodecEstimate.UNKNOWN_ASSUME_SBC] because the public SDK no longer
     * exposes [BluetoothA2dp.getCodecStatus]; the conservative default is
     * used for that session, never a reflection workaround (BE §9).
     */
    fun detectActiveCodec(
        a2dpProxy: BluetoothA2dp?,
        device: BluetoothDevice?,
    ): CodecEstimate {
        Log.d(TAG, "getCodecStatus is not available in the public SDK — assuming SBC")
        return CodecEstimate.UNKNOWN_ASSUME_SBC
    }
}
