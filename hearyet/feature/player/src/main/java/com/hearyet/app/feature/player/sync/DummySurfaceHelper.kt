package com.hearyet.app.feature.player.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.Player

/**
 * Keeps the video decoder (and therefore the PCM audio tap) alive when the
 * host's screen turns off during an active HearYet session.
 *
 * On some devices, turning the screen off causes the system to release the
 * video [Surface], which can stall the shared decode pipeline that the audio
 * tap also depends on.  This helper creates a tiny invisible surface and
 * swaps it onto the player when the screen goes off, preventing the pipeline
 * from tearing down.
 *
 * BE §6 — "Host screen-off behavior"
 */
class DummySurfaceHelper(
    private val context: Context,
    private val player: Player,
) {
    private var dummySurface: Surface? = null
    private var isScreenOn: Boolean = true
    private var sessionActive: Boolean = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /** Call from [PlayerService.onCreate] to start listening. */
    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenReceiver, filter)
    }

    /** Call from [PlayerService.onDestroy] to clean up. */
    fun stop() {
        context.unregisterReceiver(screenReceiver)
        releaseDummySurface()
    }

    /** Call when a session becomes active (Host role, audio streaming). */
    fun onSessionActive() {
        sessionActive = true
        if (!isScreenOn) swapToDummySurface()
    }

    /** Call when the session ends. */
    fun onSessionEnded() {
        sessionActive = false
        releaseDummySurface()
    }

    // ── Internal ────────────────────────────────────────────────────

    private fun onScreenOff() {
        isScreenOn = false
        if (sessionActive) swapToDummySurface()
    }

    private fun onScreenOn() {
        isScreenOn = true
        releaseDummySurface()
    }

    private fun swapToDummySurface() {
        if (dummySurface != null) return
        // Create a 1×1 SurfaceTexture on a temporary thread — just enough
        // to keep the codec from tearing down without consuming resources.
        val texture = SurfaceTexture(/* texName = */ 0, /* singleBufferMode = */ true)
        texture.setDefaultBufferSize(1, 1)
        dummySurface = Surface(texture)
        player.setVideoSurface(dummySurface)
        android.util.Log.d("HearYet", "Swapped to dummy surface for screen-off")
    }

    private fun releaseDummySurface() {
        dummySurface?.release()
        dummySurface = null
        // Don't clear the video surface here — let the PlayerView re-attach
        // its real surface when the activity resumes.
    }

    /** Whether a dummy surface is currently active. */
    val isDummySurfaceActive: Boolean get() = dummySurface != null
}
