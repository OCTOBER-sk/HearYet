package com.hearyet.app.sync

import android.util.Log
import com.hearyet.app.transport.ControlMessage
import com.hearyet.app.transport.NearbyTransportManager
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Implements Cristian's-algorithm-style clock synchronization between a
 * HearYet Guest and Host over the Nearby Connections control channel.
 *
 * BE §5 — the math is prescribed, not invented:
 * - 8–10 round-trip samples over ~2 seconds per batch
 * - Discard top 25% by RTT (asymmetric-delay outliers)
 * - Median offset of the remainder
 * - Convergence gate: stddev of the batch's offset samples < 5 ms
 * - If not converged within ~10 s: SYNC_TIMEOUT (degraded-mode opt-in, 15 ms ceiling, off by default)
 * - Background re-sync every 30–60 s for crystal drift correction
 *
 * All timestamps are [System.nanoTime] — never [System.currentTimeMillis]
 * (clock rule from BE §3).
 */
class ClockSyncManager(
    private val transport: NearbyTransportManager,
) {
    companion object {
        private const val TAG = "ClockSyncManager"

        /** Number of round trips per sync batch (BE §5: 8–10 samples). */
        const val SAMPLE_COUNT: Int = 10

        /** Interval between individual samples in ms (10 samples × 200 ms ≈ 2 s batch). */
        private const val SAMPLE_INTERVAL_MS: Long = 200

        /** Stddev threshold for convergence (ms) — BE §5 "start at 5ms". */
        private const val CONVERGENCE_THRESHOLD_MS: Double = 5.0

        /**
         * Relaxed stddev ceiling for degraded-mode entry (ms) — BE §5 verbatim:
         * "a looser 15ms ceiling". This path is an opt-in (see
         * [DEGRADED_MODE_ENABLED]) and is NOT shipped by default.
         */
        private const val DEGRADED_THRESHOLD_MS: Double = 15.0

        /**
         * Enable the relaxed degraded-mode fallback (stddev 5–15 ms → enter with
         * SyncHealth.POOR from the start instead of SYNC_TIMEOUT).
         *
         * BE §5 verbatim: "Do not ship this relaxed path by default — it's an
         * explicit, calibration-justified opt-in, not a silent lowering of the
         * bar." Section 16's calibration pass is what may justify flipping this
         * on for noisy Bluetooth/Wi-Fi Direct links.
         */
        @Volatile
        var DEGRADED_MODE_ENABLED: Boolean = false

        /** Total timeout for a sync batch. */
        private const val SYNC_TIMEOUT_MS: Long = 10_000

        /** Minimum interval between background re-sync batches. */
        private const val RESYNC_INTERVAL_MIN_MS: Long = 30_000

        /** Maximum interval between background re-sync batches. */
        private const val RESYNC_INTERVAL_MAX_MS: Long = 60_000

        // ── Core math (BE §5 formulas) — companion so the pure functions are unit-testable. ──

        /**
         * Compute offset and RTT from a single round-trip sample (BE §5).
         * @return Pair(offsetNanos, rttNanos)
         */
        fun computeOffset(t0: Long, t1: Long, t2: Long, t3: Long): Pair<Double, Long> {
            val rtt = (t3 - t0) - (t2 - t1)
            val offset = ((t1 - t0) + (t2 - t3)) / 2.0
            return offset to rtt
        }

        /**
         * Estimate the best clock offset from a list of (offset, rtt) samples (BE §5).
         * Sorts by RTT, keeps top 75%, takes median of the remainder.
         */
        fun estimateFromSamples(samples: List<Pair<Double, Long>>): Double {
            val sorted = samples.sortedBy { it.second } // sort by RTT ascending
            val keepCount = (sorted.size * 0.75).toInt().coerceAtLeast(1)
            val keep = sorted.take(keepCount)
            // Edge case: fewer than 2 after filtering → use lowest-RTT sample directly
            if (keep.size < 2) return keep.firstOrNull()?.first ?: sorted.first().first
            val offsets = keep.map { it.first }.sorted()
            return offsets[offsets.size / 2] // median
        }
    }

    // ── State ───────────────────────────────────────────────────────

    /** The current best-estimate clock offset (hostNanos + offsetNanos ≈ guestNanos). */
    @Volatile
    var clockOffsetNanos: Double = 0.0
        private set

    /** True once initial sync has converged. */
    @Volatile
    var isSynced: Boolean = false
        private set

    /** Standard deviation of the last batch (NaN before first batch). */
    @Volatile
    var lastStddevMs: Double = Double.NaN
        private set

    /** True when the last batch only qualified via degraded mode (POOR from start). */
    @Volatile
    var lastSyncDegraded: Boolean = false
        private set

    private var syncInProgress: Boolean = false
    private var backgroundJob: Thread? = null

    /**
     * During an active sync batch, incoming [ControlMessage.ClockSyncResponse] messages
     * are delivered here by [SessionCoordinator.handleGuestControlMessage] instead of
     * going through a temporary replacement of [NearbyTransportManager.onControlMessage].
     *
     * This avoids the handler-swap race: the coordinator's onControlMessage handler is
     * never replaced, so a concurrent teardown/retry can never overwrite the wrong handler.
     * The queue is non-null only while a batch is in progress.
     */
    @Volatile
    var pendingSyncResponseQueue: LinkedBlockingQueue<ControlMessage.ClockSyncResponse>? = null
        private set

    // ── Callbacks ───────────────────────────────────────────────────

    /** Called when a sync batch completes successfully. */
    var onSyncComplete: ((offsetNanos: Double, stddevMs: Double) -> Unit)? = null

    /** Called when a sync batch times out without convergence. */
    var onSyncTimeout: (() -> Unit)? = null

    /** Called whenever the host responds during a sync batch (ClockSyncResponse).
     *  BE §10.1 — lets the guest reset its HOST_UNREACHABLE timer. */
    var onHostActivity: (() -> Unit)? = null

    /**
     * H-3 — called with the refreshed offset whenever a background re-sync batch
     * (BE §5 crystal-drift correction) converges. The SessionCoordinator wires
     * this to [PresentationScheduler.updateClockOffset] so the live scheduler's
     * target computation picks up the new offset instead of discarding it.
     * Invoked on the background re-sync thread.
     */
    var onBackgroundResync: ((offsetNanos: Double) -> Unit)? = null

    // ── Guest: initiate a sync batch ────────────────────────────────

    /**
     * Guest side — perform one full sync session: repeat §5 batches until the
     * convergence gate is met or the ~10s deadline expires.
     *
     * BE §5 — each batch is 8–10 samples over ~2 seconds; the estimate is the
     * median of the lowest-RTT 75% ([estimateFromSamples]), and the gate is the
     * standard deviation of the batch's offset samples.  The guest leaves
     * `ClockSyncing` as soon as a batch satisfies the gate; if no batch
     * converges within ~10 seconds the session ends in SYNC_TIMEOUT (unless the
     * calibration-justified degraded-mode opt-in is enabled).
     *
     * @param onResult called with (offsetNanos, stddevMs) on convergence,
     *   or (-1, NaN) on timeout.
     */
    fun performSyncBatch(
        hostEndpointId: String,
        onResult: (offsetNanos: Double, stddevMs: Double) -> Unit,
    ) {
        if (syncInProgress) {
            Log.w(TAG, "Sync already in progress, ignoring request")
            return
        }
        syncInProgress = true
        Log.d(TAG, "Starting sync batch to $hostEndpointId")

        val deadline = System.nanoTime() + SYNC_TIMEOUT_MS * 1_000_000

        // Install response queue so the coordinator can deliver ClockSyncResponse messages
        // to this batch without replacing transport.onControlMessage (eliminates the
        // handler-swap race where a concurrent teardown could restore the wrong handler).
        val responseQueue = LinkedBlockingQueue<ControlMessage.ClockSyncResponse>()
        pendingSyncResponseQueue = responseQueue

        // Last measured estimate/stddev — carried past the deadline for the
        // degraded-mode decision.
        var lastOffsetNanos = 0.0
        var lastStddevMs = Double.NaN

        try {
            // BE §5 — repeat batches until the gate is met or the ~10s deadline expires.
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted) {
                val batch = mutableListOf<Pair<Double, Long>>() // (offsetNanos, rttNanos)

                // One batch: SAMPLE_COUNT probes, one per SAMPLE_INTERVAL_MS slot,
                // so the batch spans ~2 seconds (BE §5: "8–10 samples over ~2 seconds").
                // The slot spacing is what samples RTT jitter diversity — a fast link
                // must not collapse the batch window.
                val batchStartNanos = System.nanoTime()
                var slot = 0
                while (slot < SAMPLE_COUNT &&
                    System.nanoTime() < deadline &&
                    !Thread.currentThread().isInterrupted
                ) {
                    // Wait for this slot's boundary so probes stay ~200ms apart.
                    val slotStartNanos = batchStartNanos + slot * SAMPLE_INTERVAL_MS * 1_000_000L
                    val untilSlotNanos = slotStartNanos - System.nanoTime()
                    if (untilSlotNanos > 0) {
                        try {
                            Thread.sleep(untilSlotNanos / 1_000_000L + 1)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                    if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted) break

                    val t0 = System.nanoTime()
                    transport.sendControlMessage(hostEndpointId, ControlMessage.ClockSyncRequest(t0))

                    // Poll until this slot ends (or the global deadline), then move to
                    // the next slot — the cadence is fixed by the slot boundaries.
                    val slotEndNanos = slotStartNanos + SAMPLE_INTERVAL_MS * 1_000_000L
                    val pollBudgetMs = ((slotEndNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                    val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                    val waitMs = pollBudgetMs.coerceAtMost(remainingMs)
                    val response = responseQueue.poll(waitMs, TimeUnit.MILLISECONDS)
                    if (response != null) {
                        val t3 = System.nanoTime()
                        // Late responses remain valid: the message carries the original
                        // t0, so computeOffset attribution is correct regardless of when
                        // the queue is polled (BE §5).
                        val (offset, rtt) = computeOffset(response.t0, response.t1, response.t2, t3)
                        batch.add(offset to rtt)
                        onHostActivity?.invoke()
                        Log.v(TAG, "Sample: offset=${"%.3f".format(offset / 1_000_000.0)}ms rtt=${"%.3f".format(rtt / 1_000_000.0)}ms")
                    }
                    slot++
                }

                if (batch.size < 2) continue // not enough samples — try a fresh batch

                lastOffsetNanos = estimateFromSamples(batch)
                lastStddevMs = stddevMs(batch)

                // BE §5 — convergence gate: stddev of the batch's offset samples < 5ms.
                if (lastStddevMs <= CONVERGENCE_THRESHOLD_MS) {
                    clockOffsetNanos = lastOffsetNanos
                    this.lastStddevMs = lastStddevMs
                    isSynced = true
                    lastSyncDegraded = false
                    Log.d(TAG, "Sync converged: offset=${"%.3f".format(lastOffsetNanos / 1_000_000.0)}ms stddev=${"%.3f".format(lastStddevMs)}ms")
                    onSyncComplete?.invoke(lastOffsetNanos, lastStddevMs)
                    onResult(lastOffsetNanos, lastStddevMs)
                    return
                }
                Log.d(TAG, "Batch stddev=${"%.3f".format(lastStddevMs)}ms — not converged, re-batching until deadline")
            }
        } finally {
            // Always clear the queue reference so the coordinator stops delivering to it,
            // and so a teardown/interrupt leaves no dangling reference.
            pendingSyncResponseQueue = null
            syncInProgress = false
        }

        // BE §5 — deadline reached without meeting the gate. Degraded entry is only
        // sanctioned "after 10 seconds without reaching the 5ms gate", so it must
        // additionally require the deadline to have actually elapsed — an
        // interrupt-driven exit (teardown) must never accept degraded sync.
        clockOffsetNanos = lastOffsetNanos
        this.lastStddevMs = lastStddevMs
        if (System.nanoTime() >= deadline &&
            DEGRADED_MODE_ENABLED &&
            !lastStddevMs.isNaN() &&
            lastStddevMs <= DEGRADED_THRESHOLD_MS
        ) {
            // Degraded-mode fallback: allow entry with POOR sync health
            // (BE §5 — calibration-justified opt-in, off by default).
            isSynced = true
            lastSyncDegraded = true
            Log.w(TAG, "Sync converged (degraded): offset=${"%.3f".format(lastOffsetNanos / 1_000_000.0)}ms stddev=${"%.3f".format(lastStddevMs)}ms")
            onSyncComplete?.invoke(lastOffsetNanos, lastStddevMs)
            onResult(lastOffsetNanos, lastStddevMs)
        } else {
            isSynced = false
            lastSyncDegraded = false
            Log.w(TAG, "Sync failed to converge: stddev=${"%.3f".format(lastStddevMs)}ms > threshold=${CONVERGENCE_THRESHOLD_MS}ms")
            onSyncTimeout?.invoke()
            onResult(-1.0, lastStddevMs)
        }
    }

    // ── Host: respond to sync requests ──────────────────────────────

    /**
     * Host side — handle an incoming [ControlMessage.ClockSyncRequest].
     * Call this from the Host's ControlMessage handler.
     */
    fun handleSyncRequest(
        guestEndpointId: String,
        request: ControlMessage.ClockSyncRequest,
    ) {
        val t1 = System.nanoTime()
        val t2 = System.nanoTime() // t1 ≈ t2 (no meaningful work between)
        transport.sendControlMessage(
            guestEndpointId,
            ControlMessage.ClockSyncResponse(t0 = request.t0, t1 = t1, t2 = t2),
        )
    }

    // ── Background re-sync ──────────────────────────────────────────

    /**
     * Guest side — start periodic background re-sync batches (BE §5: every
     * 30–60 seconds) to correct for crystal drift between devices.
     */
    fun startBackgroundResync(hostEndpointId: String) {
        stopBackgroundResync()
        backgroundJob = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val delayMs = RESYNC_INTERVAL_MIN_MS +
                    (Math.random() * (RESYNC_INTERVAL_MAX_MS - RESYNC_INTERVAL_MIN_MS)).toLong()
                Thread.sleep(delayMs)
                if (Thread.currentThread().isInterrupted) break
                Log.d(TAG, "Background re-sync starting")
                performSyncBatch(hostEndpointId) { offsetNanos, stddevMs ->
                    if (offsetNanos >= 0) {
                        Log.d(TAG, "Background re-sync: offset=${"%.3f".format(offsetNanos / 1_000_000.0)}ms stddev=${"%.3f".format(stddevMs)}ms")
                        // H-3 — deliver the refreshed offset to the live scheduler
                        // instead of discarding it (BE §5 crystal-drift correction).
                        onBackgroundResync?.invoke(offsetNanos)
                    }
                }
            }
        }.apply {
            name = "HearYet-ClockResync"
            isDaemon = true
            start()
        }
    }

    fun stopBackgroundResync() {
        backgroundJob?.interrupt()
        backgroundJob = null
    }

    // ── Core math lives in the companion (BE §5) — computeOffset / estimateFromSamples. ──

    /**
     * Standard deviation of a batch's offset samples in milliseconds (BE §5:
     * the convergence gate is "the standard deviation of its offset samples" —
     * measured over every sample in the batch).
     */
    private fun stddevMs(
        samples: List<Pair<Double, Long>>,
    ): Double {
        if (samples.size < 2) return Double.NaN
        val offsets = samples.map { it.first }
        val mean = offsets.average()
        val variance = offsets.map { (it - mean) * (it - mean) }.sum() / (offsets.size - 1)
        return sqrt(variance) / 1_000_000.0 // convert nanos² to ms
    }

    /** Convert the current clock offset to milliseconds for display/logging. */
    val clockOffsetMs: Double get() = clockOffsetNanos / 1_000_000.0
}
