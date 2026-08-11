package com.hearyet.app.sync

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages Android audio focus for the Guest-side playback pipeline (BE §6).
 *
 * - **Permanent focus loss** (e.g., incoming call): calls [onPermanentFocusLost]
 *   so the Guest's AudioTrack can be paused.  On regain, the Guest re-enters
 *   cleanly via flush-and-reseed (same as unpause-as-seek handling in BE §7).
 * - **Transient focus loss** (e.g., notification ping): calls [onTransientFocusDuck]
 *   so the Guest can lower volume briefly without disrupting sync state.
 *
 * Not used on the Host side — the Host's audio pipeline is managed by ExoPlayer.
 */
class GuestAudioFocusManager(context: Context) {

    companion object {
        private const val TAG = "GuestAudioFocus"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Callbacks ───────────────────────────────────────────────────

    /** Permanent focus lost — pause playback entirely. */
    var onPermanentFocusLost: (() -> Unit)? = null

    /** Focus regained after permanent loss — resume via flush-and-reseed. */
    var onFocusRegained: (() -> Unit)? = null

    /** Transient focus lost — duck volume (notification ping). */
    var onTransientFocusDuck: (() -> Unit)? = null

    /** Transient focus lost — pause briefly then resume. */
    var onTransientFocusLost: (() -> Unit)? = null

    // ── Focus request ───────────────────────────────────────────────

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus: Boolean = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                onPermanentFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                onTransientFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onTransientFocusDuck?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onFocusRegained?.invoke()
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /** Request audio focus before starting playback. */
    fun requestFocus(): Boolean {
        if (hasFocus) return true

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            val result = audioManager.requestAudioFocus(focusRequest!!)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusListener,
                android.media.AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            hasFocus
        }
    }

    /** Release audio focus when playback stops. */
    fun abandonFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        focusRequest = null
        hasFocus = false
    }

    /** True if this manager currently holds audio focus. */
    val isFocused: Boolean get() = hasFocus
}
