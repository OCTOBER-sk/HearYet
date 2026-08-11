package com.hearyet.app.sync

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.hearyet.app.feature.player.sync.AudioChunk
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Guest-side presentation scheduler.  Holds a ring buffer of [AudioChunk]s
 * and feeds them to an [AudioTrack] at precisely the right guest-local time.
 *
 * BE §7 — the math:
 * ```
 * guestPlaybackTimeNanos = hostTimestampNanos + (clockOffsetMs * 1_000_000) + (lookaheadMs * 1_000_000)
 * ```
 *
 * - [lookaheadMs] starts at **250 ms** (tunable per codec class in §9, §16).
 * - Writes use [AudioTrack.WRITE_NON_BLOCKING] — never blocks.
 * - Chunks whose target time passed by >20 ms are silently dropped.
 * - On seek/pause/media-change: flush entirely and re-seed from "now."
 */
class PresentationScheduler(
    val sampleRateHz: Int = 48000,
    val channelCount: Int = 2,
) {
    companion object {
        private const val TAG = "PresentationScheduler"

        /** Starting lookahead in milliseconds (BE §7). */
        const val DEFAULT_LOOKAHEAD_MS: Int = 250

        /** Grace period: chunks up to this many ms late are still played. */
        private const val LATE_GRACE_MS: Long = 20

        /** Frame duration per AudioChunk in ms. */
        private const val FRAME_MS: Long = 20
    }

    // ── Configuration ───────────────────────────────────────────────

    /** Current lookahead in milliseconds. Tunable per codec class. */
    @Volatile
    var lookaheadMs: Int = DEFAULT_LOOKAHEAD_MS

    /** Current clock offset from [ClockSyncManager] (hostNanos → guestNanos). */
    @Volatile
    var clockOffsetNanos: Double = 0.0

    // ── Internal ────────────────────────────────────────────────────

    /** Ring buffer keyed by guestPlaybackTimeNanos. */
    private val buffer = ConcurrentSkipListMap<Long, AudioChunk>()

    private var audioTrack: AudioTrack? = null
    private val running = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    // ── Statistics ──────────────────────────────────────────────────

    @Volatile var chunksReceived: Long = 0
    @Volatile var chunksPlayed: Long = 0
    @Volatile var chunksDropped: Long = 0
    @Volatile var bufferSize: Int = 0
        private set

    /**
     * Set to true while flush() is executing pause→flush→play on the AudioTrack.
     * The playback thread checks this flag and parks for one frame duration when true,
     * preventing a concurrent write() call during the AudioTrack state transition
     * (undefined behavior on some OEM implementations on Android 16).
     */
    @Volatile
    private var isFlushing: Boolean = false

    // ── Callbacks ───────────────────────────────────────────────────

    var onBufferEmpty: (() -> Unit)? = null
    var onBufferDrained: (() -> Unit)? = null

    // ── Seeding ─────────────────────────────────────────────────────

    /**
     * BE §7 — single shared seeding code path, reused for:
     * initial join, latecomer join, rejoin-after-crash, exiting [ClockSyncing].
     *
     * Clears the ring buffer and sets the schedule to start from "now."
     */
    fun seedFromNow() {
        buffer.clear()
        bufferSize = 0
        Log.d(TAG, "Scheduler seeded from now (buffer flushed)")
    }

    // ── AudioTrack lifecycle ────────────────────────────────────────

    /** Create and start the [AudioTrack]. Must call before [start]. */
    fun openAudioTrack(): Boolean {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) return true

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        // Use a larger buffer to absorb scheduling jitter (2× the min).
        val bufferSizeBytes = minBufferSize * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack?.play()
            Log.d(TAG, "AudioTrack opened: $sampleRateHz Hz, buffer=${bufferSizeBytes}B")
            true
        } else {
            Log.e(TAG, "Failed to initialize AudioTrack")
            audioTrack?.release()
            audioTrack = null
            false
        }
    }

    /** Start the playback thread. Call after [openAudioTrack]. */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val track = audioTrack ?: return
        playbackThread = Thread {
            Log.d(TAG, "Playback thread started")
            while (running.get()) {
                try {
                    val nextEntry = buffer.firstEntry()
                    if (nextEntry == null) {
                        onBufferEmpty?.invoke()
                        LockSupport.parkNanos(FRAME_MS * 1_000_000)
                        continue
                    }

                    // Yield during flush — a concurrent pause→flush→play sequence is in
                    // progress; parking avoids undefined behaviour from calling write()
                    // while the AudioTrack state is transitioning (BE §7 flush-and-reseed).
                    if (isFlushing) {
                        LockSupport.parkNanos(FRAME_MS * 1_000_000)
                        continue
                    }

                    val (targetNanos, chunk) = nextEntry
                    val now = System.nanoTime()
                    val delayNanos = targetNanos - now

                    if (delayNanos > 0) {
                        // Chunk is due in the future — wait precisely.
                        LockSupport.parkNanos(delayNanos)
                        continue
                    }

                    // Remove the chunk from the buffer.
                    buffer.remove(targetNanos)

                    if (delayNanos < -LATE_GRACE_MS * 1_000_000) {
                        // Too late — drop silently.
                        chunksDropped++
                        continue
                    }

                    // Write to AudioTrack (non-blocking).
                    val written = track.write(
                        chunk.pcmPayload,
                        0,
                        chunk.pcmPayload.size,
                        AudioTrack.WRITE_NON_BLOCKING,
                    )
                    if (written > 0) {
                        chunksPlayed++
                    } else {
                        // Non-blocking write couldn't accept data — retry next cycle.
                        buffer[targetNanos] = chunk
                        LockSupport.parkNanos(FRAME_MS * 1_000_000 / 2)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Playback thread error", e)
                    LockSupport.parkNanos(FRAME_MS * 1_000_000)
                }
            }
            Log.d(TAG, "Playback thread stopped")
        }.apply {
            name = "HearYet-Scheduler"
            priority = Thread.MAX_PRIORITY - 1 // near-realtime
            isDaemon = true
            start()
        }
    }

    /** Stop the playback thread and release the AudioTrack. */
    fun stop() {
        running.set(false)
        playbackThread?.interrupt()
        playbackThread = null
        audioTrack?.apply {
            pause()
            flush()
            release()
        }
        audioTrack = null
        buffer.clear()
        Log.d(TAG, "Scheduler stopped")
    }

    // ── Incoming chunks ─────────────────────────────────────────────

    /**
     * Called when an [AudioChunk] arrives from the transport layer.
     * Computes the target playback time and inserts it into the ring buffer.
     */
    fun onChunkReceived(chunk: AudioChunk) {
        val targetNanos = guestPlaybackTimeNanos(chunk.hostTimestampNanos)
        buffer[targetNanos] = chunk
        chunksReceived++
        bufferSize = buffer.size
    }

    // ── Core formula (BE §7) ────────────────────────────────────────

    /**
     * Calculate the guest-local playback time for a chunk captured at
     * [hostTimestampNanos] on the host.
     */
    fun guestPlaybackTimeNanos(hostTimestampNanos: Long): Long {
        return hostTimestampNanos +
            (clockOffsetNanos).toLong() +
            (lookaheadMs * 1_000_000L)
    }

    // ── Query ───────────────────────────────────────────────────────

    val isRunning: Boolean get() = running.get()
    val isAudioTrackReady: Boolean get() = audioTrack?.state == AudioTrack.STATE_INITIALIZED

    /** Expose the [AudioTrack] for [DriftCorrectionManager] without making the field public. */
    fun getAudioTrack(): AudioTrack? = audioTrack

    // ── Flush (C5/C12) ───────────────────────────────────────────────

    /**
     * Lightweight flush — clears the ring buffer and resets counters, but does
     * NOT tear down the [AudioTrack].  Used for seek / pause-resume / media-change
     * flush-and-reseed (BE §7) where the scheduler keeps running after the reset.
     *
     * Unlike [stop], this does not release the [AudioTrack] or stop the playback thread.
     */
    fun flush() {
        // AudioTrack.flush() is a no-op while the track is PLAYING — the hardware buffer
        // must be paused first so stale frames are actually discarded (BE §7 flush-and-reseed).
        //
        // isFlushing gates the playback thread's write() calls so the pause→flush→play
        // transition is never concurrent with a write (thread-safety on Android 16 OEMs).
        isFlushing = true
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } finally {
            // Clear BEFORE play() so the playback thread can resume writing as soon as
            // the track is running again.
            isFlushing = false
        }
        audioTrack?.play() // Resume immediately; the scheduler refills from incoming chunks.
        buffer.clear()
        bufferSize = 0
        chunksPlayed = 0
        Log.d(TAG, "Scheduler flushed (paused → flushed → resumed; counters reset)")
    }
}
