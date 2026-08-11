package com.hearyet.app.sync

import android.media.AudioTrack
import android.util.Log
import com.hearyet.app.core.model.SyncHealth
import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Continuous drift correction for a Guest's audio playback (BE §8).
 *
 * Clock offset is corrected at sync time, but playback rate drift
 * (accumulated scheduling jitter, buffer underrun/overrun) needs
 * gentle, continuous correction — never abrupt jumps.
 *
 * Correction is applied as a tiny **playback speed nudge**, not a seek:
 * - |drift| < 15 ms → [SyncHealth.GOOD], no correction
 * - 15 ≤ |drift| < 50 ms → [SyncHealth.DEGRADED], ±0.5% speed
 * - |drift| ≥ 50 ms → [SyncHealth.POOR]
 *   - |drift| < 150 ms: ±1.5% speed
 *   - |drift| ≥ 150 ms: hard resync (seek-like snap)
 *
 * Drift is re-evaluated every 1–2 seconds and reported to the host
 * via [ControlMessage.DriftReport] on a fixed 2-second interval.
 */
class DriftCorrectionManager(
    private val transport: NearbyTransportManager,
) {
    companion object {
        private const val TAG = "DriftCorrection"

        // Thresholds (BE §8 — starting points, tunable in §16)
        private const val GOOD_THRESHOLD_MS: Double = 15.0
        private const val DEGRADED_THRESHOLD_MS: Double = 50.0
        private const val SEVERE_DESYNC_THRESHOLD_MS: Double = 150.0

        // Speed correction factors
        private const val GENTLE_NUDGE: Float = 1.005f   // ±0.5%
        private const val AGGRESSIVE_NUDGE: Float = 1.015f // ±1.5%

        // Evaluation interval
        private const val EVALUATION_INTERVAL_MS: Long = 1500 // midpoint of 1-2s
        private const val REPORT_INTERVAL_MS: Long = 2000 // fixed 2s

        /**
         * BE §8 — drift in ms between the AudioTrack's presented frames and the
         * wall-clock expectation since a baseline. playbackHeadPosition counts frames
         * (one frame = one sample per channel), so the frame rate equals sampleRateHz
         * regardless of channel count — never multiply by channelCount.
         */
        fun computeDriftMs(
            headPositionFrames: Long,
            baselineHeadFrames: Long,
            elapsedNanos: Long,
            sampleRateHz: Int,
        ): Double {
            val framesPresented = headPositionFrames - baselineHeadFrames
            val expectedFrames = (elapsedNanos * sampleRateHz) / 1_000_000_000L
            val frameDrift = framesPresented - expectedFrames
            return if (sampleRateHz > 0) (frameDrift * 1000.0) / sampleRateHz else 0.0
        }
    }

    // ── State ───────────────────────────────────────────────────────

    @Volatile var driftMs: Double = 0.0
        private set

    @Volatile var syncHealth: SyncHealth = SyncHealth.GOOD
        private set

    /**
     * BE §5 — degraded-mode entry: mark sync POOR from the start so the Host
     * (and the Guest's own UI) reflect that the clock converged only via the
     * relaxed fallback. Drift correction normalizes it within a few report
     * cycles on a healthy link.
     */
    fun markDegradedEntry() {
        if (syncHealth == SyncHealth.GOOD) syncHealth = SyncHealth.POOR
    }

    @Volatile var currentPlaybackSpeed: Float = 1.0f
        private set

    private var audioTrack: AudioTrack? = null
    private var scheduler: PresentationScheduler? = null
    private var hostEndpointId: String? = null

    // Drift-measurement baseline (BE §8): drift is the delta between the AudioTrack's
    // presented frames and the wall-clock expectation since a baseline, so the hardware
    // buffer depth and the scheduler's lookahead fill never show up as false drift.
    // Rebased whenever the scheduler flushes (chunksPlayed resets to 0 — the AudioTrack
    // head counter does not) or on any pause → resume transition.
    private var baselineHeadFrames: Long = 0
    private var baselineNanos: Long = 0
    private var baselineInitialized: Boolean = false
    private var lastChunksPlayed: Long = 0
    private var lastPlayState: Int = AudioTrack.PLAYSTATE_STOPPED

    private val running = AtomicBoolean(false)
    private var evalThread: Thread? = null
    private var reportThread: Thread? = null

    // ── Lifecycle ───────────────────────────────────────────────────

    /**
     * Start drift monitoring. Requires references to the active [AudioTrack]
     * and [PresentationScheduler] to measure playback position vs. schedule.
     */
    fun start(
        track: AudioTrack,
        scheduler: PresentationScheduler,
        hostEndpointId: String,
    ) {
        if (!running.compareAndSet(false, true)) return
        this.audioTrack = track
        this.scheduler = scheduler
        this.hostEndpointId = hostEndpointId

        // Reset the measurement baseline — the first PLAYING evaluation re-captures it
        // (well past the initial lookahead fill, so the fill never counts as drift).
        baselineInitialized = false
        lastChunksPlayed = scheduler.chunksPlayed
        lastPlayState = AudioTrack.PLAYSTATE_STOPPED

        // Evaluation thread: measure and nudge
        evalThread = Thread {
            Log.d(TAG, "Drift evaluation thread started")
            while (running.get()) {
                try {
                    Thread.sleep(EVALUATION_INTERVAL_MS)
                    if (!running.get()) break
                    evaluate()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            Log.d(TAG, "Drift evaluation thread stopped")
        }.apply {
            name = "HearYet-DriftEval"
            isDaemon = true
            start()
        }

        // Report thread: send DriftReport to host every 2s
        reportThread = Thread {
            Log.d(TAG, "Drift report thread started")
            while (running.get()) {
                try {
                    Thread.sleep(REPORT_INTERVAL_MS)
                    if (!running.get()) break
                    sendDriftReport()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            Log.d(TAG, "Drift report thread stopped")
        }.apply {
            name = "HearYet-DriftReport"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        evalThread?.interrupt()
        reportThread?.interrupt()
        evalThread = null
        reportThread = null
        baselineInitialized = false
        resetPlaybackSpeed()
        Log.d(TAG, "Drift correction stopped")
    }

    // ── Core evaluation (BE §8) ─────────────────────────────────────

    private fun evaluate() {
        val track = audioTrack ?: return
        val sched = scheduler ?: return
        val playState = track.playState

        // While paused/stopped (permanent focus loss, flush in progress) the playback
        // head is frozen — measuring it against the wall clock would report false
        // drift. Skip correction; the resume path rebases the baseline (below).
        if (playState != AudioTrack.PLAYSTATE_PLAYING) {
            lastPlayState = playState
            return
        }

        // Rebase the measurement baseline whenever the scheduler flushed (chunksPlayed
        // resets to 0 on seek / pause-resume / media-change / hard-resync) or on any
        // pause → resume transition, so the lookahead refill and any pause gap are
        // excluded from the drift reading.
        if (!baselineInitialized ||
            sched.chunksPlayed < lastChunksPlayed ||
            lastPlayState != AudioTrack.PLAYSTATE_PLAYING
        ) {
            baselineHeadFrames = track.playbackHeadPosition.toLong()
            baselineNanos = System.nanoTime()
            baselineInitialized = true
        }
        lastChunksPlayed = sched.chunksPlayed
        lastPlayState = playState

        // Drift = frames actually presented since baseline minus the frames the
        // schedule expected in that wall-clock window (BE §8).
        val elapsedNanos = System.nanoTime() - baselineNanos
        driftMs = computeDriftMs(
            headPositionFrames = track.playbackHeadPosition.toLong(),
            baselineHeadFrames = baselineHeadFrames,
            elapsedNanos = elapsedNanos,
            sampleRateHz = sched.sampleRateHz,
        )

        val absDrift = Math.abs(driftMs)

        when {
            absDrift < GOOD_THRESHOLD_MS -> {
                syncHealth = SyncHealth.GOOD
                resetPlaybackSpeed()
            }
            absDrift < DEGRADED_THRESHOLD_MS -> {
                syncHealth = SyncHealth.DEGRADED
                applySpeedNudge(GENTLE_NUDGE)
            }
            absDrift < SEVERE_DESYNC_THRESHOLD_MS -> {
                syncHealth = SyncHealth.POOR
                applySpeedNudge(AGGRESSIVE_NUDGE)
            }
            else -> {
                syncHealth = SyncHealth.POOR
                Log.w(TAG, "Severe desync (${"%.1f".format(driftMs)}ms) — hard resync triggered")
                hardResync()
            }
        }

        Log.v(TAG, "drift=${"%.1f".format(driftMs)}ms health=$syncHealth speed=${"%.3f".format(currentPlaybackSpeed)}x")
    }

    // ── Speed control ───────────────────────────────────────────────

    private fun applySpeedNudge(factor: Float) {
        val track = audioTrack ?: return
        // driftMs > 0 → AudioTrack ahead of schedule → slow down
        // driftMs < 0 → AudioTrack behind schedule → speed up
        val direction = if (driftMs > 0) (1.0f / factor) else factor
        if (Math.abs(currentPlaybackSpeed - direction) < 0.0001f) return

        currentPlaybackSpeed = direction
        try {
            track.playbackParams = track.playbackParams
                .setSpeed(direction)
                .setPitch(1.0f) // pitch-preserving not required but kept for clarity
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set playback speed", e)
        }
    }

    private fun resetPlaybackSpeed() {
        val track = audioTrack ?: return
        if (currentPlaybackSpeed == 1.0f) return
        currentPlaybackSpeed = 1.0f
        try {
            track.playbackParams = track.playbackParams
                .setSpeed(1.0f)
                .setPitch(1.0f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset playback speed", e)
        }
    }

    /** Hard resync: flush the AudioTrack + ring buffer and re-seed (audible, rare).
     *  BE §8 — a real snap: [PresentationScheduler.flush] pauses/flushes the hardware
     *  buffer, so stale audio already handed to AudioTrack is discarded, not just the
     *  in-memory schedule. */
    private fun hardResync() {
        scheduler?.flush()
        resetPlaybackSpeed()
    }

    // ── Drift reporting ─────────────────────────────────────────────

    private fun sendDriftReport() {
        val host = hostEndpointId ?: return
        transport.sendControlMessage(host, ControlMessage.DriftReport(driftMs))
    }
}
