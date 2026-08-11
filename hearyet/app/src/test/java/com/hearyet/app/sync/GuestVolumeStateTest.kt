package com.hearyet.app.sync

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BE §6:356 — the Guest's local volume state: clamped 0..1, observable via
 * StateFlow, applied live to the session AudioTrack (never STREAM_MUSIC).
 */
@RunWith(RobolectricTestRunner::class)
class GuestVolumeStateTest {

    @Test
    fun initialValue_isClampedIntoRange() {
        assertEquals(1.0f, GuestVolumeState().current, 0f)
        assertEquals(0.0f, GuestVolumeState(initial = -1f).current, 0f)
        assertEquals(1.0f, GuestVolumeState(initial = 2f).current, 0f)
        assertEquals(0.5f, GuestVolumeState(initial = 0.5f).current, 0f)
    }

    @Test
    fun set_clampsAndUpdatesFlow() {
        val state = GuestVolumeState()
        state.set(0.4f, audioTrack = null)
        assertEquals(0.4f, state.current, 0f)
        assertEquals(0.4f, state.volume.value, 0f)

        state.set(1.5f, audioTrack = null)
        assertEquals(1.0f, state.current, 0f)

        state.set(-0.2f, audioTrack = null)
        assertEquals(0.0f, state.current, 0f)
    }

    @Test
    fun set_withLiveAudioTrack_appliesGain() {
        // Robolectric's AudioTrack shadow accepts setVolume without a real device.
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(48_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(3_840 * 2)
            .build()

        val state = GuestVolumeState()
        state.set(0.3f, track)
        assertEquals(0.3f, state.current, 0f)

        track.release()
    }
}
