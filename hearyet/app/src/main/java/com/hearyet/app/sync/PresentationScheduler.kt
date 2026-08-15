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
    initialSampleRateHz: Int = 48000,
    initialChannelCount: Int = 2,
) {
    companion object {
        private const val TAG = "PresentationScheduler"

        /** Starting lookahead in milliseconds (BE §7). */
        const val DEFAULT_LOOKAHEAD_MS: Int = 250

        /** Grace period: chunks up to this many ms late are still played. */
        private const val LATE_GRACE_MS: Long = 20

        /** Frame duration per AudioChunk in ms. */
        private const val FRAME_MS: Long = 20

        /**
         * Ring-buffer cap — 250 chunks ≈ 5 s of audio at 20 ms per chunk (BE §7
         * "ring buffer"). When a stalled [AudioTrack] (WRITE_NON_BLOCKING → 0)
         * cannot drain the buffer, the oldest chunk is dropped for each new one
         * so a pause/focus-loss stall can never grow memory without bound
         * (~192 KB/s of incoming PCM must not accumulate forever).
         */
        const val MAX_BUFFERED_CHUNKS: Int = 250

        /**
         * FIX 2a (R1) — consecutive late drops before the scheduler treats the
         * schedule as fallen permanently behind and re-seeds. 10 × 20 ms = 200 ms
         * of sustained lateness: a single jitter drop must never flush the buffer.
         */
        const val CONSECUTIVE_LATE_DROPS_TO_RESEED: Long = 10

        /**
         * FIX 2a (R1) — minimum gap between recovery re-seeds. Bounds thrash on a
         * permanently backlogged link where every delivered chunk is stale: the
         * scheduler re-syncs at most this often instead of flushing constantly.
         */
        private const val RESEED_MIN_INTERVAL_MS: Long = 5_000
    }

    // ── Configuration ───────────────────────────────────────────────

    /**
     * Current AudioTrack sample rate in Hz. FIX 3 — set from the constructor
     * default (48kHz) and updated to the incoming chunk format when it differs,
     * so a 44.1kHz source is played at the correct rate.
     */
    @Volatile
    var sampleRateHz: Int = initialSampleRateHz
        private set

    /** Current AudioTrack channel count (FIX 3 — updated to the chunk format). */
    @Volatile
    var channelCount: Int = initialChannelCount
        private set

    /** Current lookahead in milliseconds. Tunable per codec class. */
    @Volatile
    var lookaheadMs: Int = DEFAULT_LOOKAHEAD_MS

    /** Current clock offset from [ClockSyncManager] (hostNanos → guestNanos). */
    @Volatile
    var clockOffsetNanos: Double = 0.0

    /**
     * H-3 — apply a refreshed clock offset from a background re-sync batch
     * (BE §5 crystal-drift correction). [clockOffsetNanos] is [Volatile], so a
     * write from the background re-sync thread is immediately visible to the
     * playback thread's [guestPlaybackTimeNanos] target computation.
     */
    fun updateClockOffset(newOffsetNanos: Long) {
        clockOffsetNanos = newOffsetNanos.toDouble()
    }

    // ── Internal ────────────────────────────────────────────────────

    /** Ring buffer keyed by guestPlaybackTimeNanos. */
    private val buffer = ConcurrentSkipListMap<Long, AudioChunk>()

    /** @Volatile so the playback thread picks up a FIX 3 format rebuild (new track). */
    @Volatile
    private var audioTrack: AudioTrack? = null
    private val running = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    // ── Statistics ──────────────────────────────────────────────────

    @Volatile var chunksReceived: Long = 0
    @Volatile var chunksPlayed: Long = 0
    @Volatile var chunksDropped: Long = 0

    /** FIX 2a (R1) — count of recovery re-seeds triggered by sustained late drops. */
    @Volatile var reseedCount: Long = 0
        private set
    @Volatile var bufferSize: Int = 0
        private set

    /** Consecutive late drops since the last playable/written chunk (playback thread only). */
    private var consecutiveLateDrops: Long = 0

    /** nanoTime of the last recovery re-seed (playback thread only). */
    private var lastReseedAtNanos: Long = 0L

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

        // FIX 3 — build the track from the current format: a mono source needs a
        // mono channel mask (a stereo track fed mono PCM plays at double speed).
        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRateHz,
            channelMask,
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
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack?.play()
            Log.d(TAG, "AudioTrack opened: ${sampleRateHz}Hz/${channelCount}ch, buffer=${bufferSizeBytes}B")
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
        if (audioTrack == null) return
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
                        consecutiveLateDrops++
                        // FIX 2a (R1) — a stream of sustained late drops means the
                        // schedule has fallen permanently behind (drop-cascade: the
                        // host is delivering stale chunks). Flush the ring buffer and
                        // re-seed from "now" so playback restarts instead of staying
                        // silent forever. Throttled to bound thrash on a permanently
                        // backlogged link.
                        if (consecutiveLateDrops >= CONSECUTIVE_LATE_DROPS_TO_RESEED &&
                            System.nanoTime() - lastReseedAtNanos >= RESEED_MIN_INTERVAL_MS * 1_000_000L
                        ) {
                            consecutiveLateDrops = 0
                            lastReseedAtNanos = System.nanoTime()
                            reseedCount++
                            Log.w(TAG, "Sustained late drops — flushing and re-seeding from now (recoveries=$reseedCount)")
                            flush()
                            seedFromNow()
                        }
                        continue
                    }

                    // FIX 3 — read the track fresh each iteration so a format rebuild
                    // (new AudioTrack) is picked up instead of writing to a released one.
                    val track = audioTrack ?: run {
                        LockSupport.parkNanos(FRAME_MS * 1_000_000)
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
                        consecutiveLateDrops = 0
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
     * FIX 3 — make the AudioTrack match [chunk]'s format. When the incoming chunk's
     * sample rate/channel count differ from the current track (44.1kHz or mono
     * media vs the 48kHz stereo default), rebuild the track so it plays at the
     * correct pitch and rate. The rebuild is gated by [isFlushing] so the playback
     * thread parks instead of writing to a released track.
     */
    private fun ensureTrackFormat(chunk: AudioChunk) {
        if (chunk.sampleRateHz == sampleRateHz && chunk.channelCount == channelCount) return
        Log.w(TAG, "Chunk format differs (${chunk.sampleRateHz}Hz/${chunk.channelCount}ch vs $sampleRateHz/$channelCount) — rebuilding AudioTrack")
        isFlushing = true
        try {
            audioTrack?.apply {
                try {
                    pause()
                    flush()
                    release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error tearing down old AudioTrack", e)
                }
            }
            audioTrack = null
            sampleRateHz = chunk.sampleRateHz
            channelCount = chunk.channelCount
            openAudioTrack()
        } finally {
            isFlushing = false
        }
    }

    /**
     * Called when an [AudioChunk] arrives from the transport layer.
     * Computes the target playback time and inserts it into the ring buffer.
     *
     * Ring-buffer semantics (BE §7): when the buffer is at its cap, the oldest
     * buffered chunk is dropped before the new one is inserted. A stalled track
     * therefore sheds old audio instead of growing memory forever.
     */
    fun onChunkReceived(chunk: AudioChunk) {
        ensureTrackFormat(chunk)
        val targetNanos = guestPlaybackTimeNanos(chunk.hostTimestampNanos)
        if (buffer.size >= MAX_BUFFERED_CHUNKS) {
            buffer.pollFirstEntry()?.let {
                chunksDropped++
                Log.v(TAG, "Ring buffer full — dropped oldest chunk (cap=${MAX_BUFFERED_CHUNKS}, stalled track?)")
            }
        }
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
            // Clear BEFORE play() so the playback thread can resume writing as soon as
            // the track is running again. isFlushing stays held until after the clear:
            // the thread parked at the isFlushing check (see start()) therefore cannot
            // wake between play() and clear() and write a stale chunk (M-2).
            buffer.clear()
            bufferSize = 0
            chunksPlayed = 0
        } finally {
            isFlushing = false
        }
        audioTrack?.play() // Resume immediately; the scheduler refills from incoming chunks.
        Log.d(TAG, "Scheduler flushed (paused → flushed → resumed; counters reset)")
    }
}
