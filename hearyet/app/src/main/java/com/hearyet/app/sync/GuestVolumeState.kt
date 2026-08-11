package com.hearyet.app.sync

import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for the Guest's local playback volume (BE §6 /
 * FE §9.6's GuestVolumeSlider). Controls ONLY the session AudioTrack's own
 * gain — never the device's system STREAM_MUSIC volume, and has zero effect
 * on sync (BE §6:356 is explicit on both points).
 *
 * One instance per [SessionCoordinator] (guest-role lifetime). Read live by:
 *  - GuestSessionScreen's slider (read+write via the nav graph wiring)
 *  - SessionCoordinator's PresentationScheduler AudioTrack (write, applies it)
 *  - GuestAudioFocusManager's duck/regain (reads current value as the
 *    "unducked" baseline to restore to — never a hardcoded 1.0f)
 *  - GuestGreetingManager.maybeGreet's currentGuestVolume param (read)
 */
class GuestVolumeState(initial: Float = 1.0f) {

    private val _volume = MutableStateFlow(initial.coerceIn(0f, 1f))
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /**
     * Explicit user change (slider drag) — updates the shared value and
     * re-applies it to the live AudioTrack if one is present.
     */
    fun set(value: Float, audioTrack: AudioTrack?) {
        val clamped = value.coerceIn(0f, 1f)
        _volume.value = clamped
        audioTrack?.setVolume(clamped)
    }

    /** Current volume as a 0..1 fraction. */
    val current: Float get() = _volume.value
}
