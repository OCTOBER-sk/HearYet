package com.hearyet.app.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

/**
 * Listens for Bluetooth route changes on the Guest device and re-detects
 * the active A2DP codec so the PresentationScheduler's [lookaheadMs] can
 * be updated for the new output path.
 *
 * BE §9 — critical rule: this class only ever calls [onRouteChanged].
 * It must **never** touch [ClockSyncManager], the [PresentationScheduler]
 * queue, or [DriftCorrectionManager] state directly.  The shared clock and
 * scheduled-chunk queue keep running through a Bluetooth disconnect/reconnect
 * exactly as-is — that's what makes the transition silent instead of a
 * resync event.
 */
class BluetoothRouteManager(
    private val context: Context,
    private val onRouteChanged: (CodecEstimate) -> Unit,
) {
    companion object {
        private const val TAG = "BluetoothRouteMgr"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var a2dpProxy: BluetoothA2dp? = null

    private var started: Boolean = false

    // ── Audio device callback (API 23+) ─────────────────────────────

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            reDetectCodec()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            reDetectCodec()
        }
    }

    // ── ACL connect/disconnect receiver ─────────────────────────────

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                -> reDetectCodec()
            }
        }
    }

    // ── A2DP profile listener ───────────────────────────────────────

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = proxy as? BluetoothA2dp
                Log.d(TAG, "A2DP service connected")
                reDetectCodec()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
                Log.d(TAG, "A2DP service disconnected")
                // Route to wired/none — use conservative SBC default
                onRouteChanged(CodecEstimate.UNKNOWN_ASSUME_SBC)
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    fun start() {
        if (started) return
        started = true
        Log.d(TAG, "BluetoothRouteManager started")

        // Register audio device callback
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        // Register ACL receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(aclReceiver, filter)

        // Connect to A2DP profile — use BluetoothManager to avoid deprecated getDefaultAdapter().
        // getProfileProxy can throw SecurityException when BLUETOOTH_CONNECT is revoked at
        // runtime; the route manager is best-effort (BE §9) and must never crash the
        // guest's sync pipeline, so it is guarded exactly like the adapter lookup.
        val adapter = try {
            context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        } catch (_: Exception) { null }
        if (adapter != null) {
            try {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            } catch (_: Exception) {
                Log.w(TAG, "Failed to connect A2DP profile proxy — codec detection stays conservative")
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        Log.d(TAG, "BluetoothRouteManager stopped")

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        try { context.unregisterReceiver(aclReceiver) } catch (_: Exception) {}

        a2dpProxy?.let {
            val adapter = try {
                context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
            } catch (_: Exception) { null }
            adapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
        }
        a2dpProxy = null
    }

    // ── Detection ───────────────────────────────────────────────────

    private fun reDetectCodec() {
        val activeDevice = getActiveBluetoothDevice()
        val estimate = BluetoothCodecDetector.detectActiveCodec(a2dpProxy, activeDevice)
        Log.d(TAG, "Route change → codec: $estimate (device=$activeDevice)")
        onRouteChanged(estimate)
    }

    private fun getActiveBluetoothDevice(): BluetoothDevice? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?.let { info ->
                // AudioDeviceInfo doesn't expose BluetoothDevice directly on older APIs
                // Try to get it from the A2DP proxy's connected devices list
                a2dpProxy?.connectedDevices?.firstOrNull()
            }
    }
}
