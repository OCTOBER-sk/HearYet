package com.hearyet.app.sync

import com.hearyet.app.feature.player.sync.AudioChunk
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-guest bounded outbound queue of [AudioChunk]s waiting to be sent
 * over the STREAM channel (BE §6 backpressure policy).
 *
 * One instance per connected guest, owned by the Host.  The SharedAudioRenderer
 * tap is the single producer; each guest's sender thread is the consumer.
 *
 * When the queue reaches [MAX_QUEUED_CHUNKS], the **oldest** chunks are
 * silently dropped — the same "drop, don't delay" principle as the scheduler's
 * drop-late-not-play-late rule.  A queue that stays near capacity is a signal
 * to downgrade that guest's [com.hearyet.app.core.model.SyncHealth].
 *
 * Thread-safe: all public methods are safe to call from any thread.
 */
class GuestOutboundQueue(
    private val endpointId: String,
) {
    /** Maximum chunks before backpressure drops oldest. 200 × 20ms = 4s. */
    companion object {
        const val MAX_QUEUED_CHUNKS: Int = 200
    }

    private val deque = ConcurrentLinkedDeque<AudioChunk>()
    private val droppedCount = AtomicInteger(0)

    /**
     * FIX 2a (R1) — one-shot "re-seed already signaled" flag per full episode.
     * The host sends the guest a re-seed signal (and clears the backlog) at most
     * once while the queue stays chronically full, instead of spamming a signal
     * on every chunk for the whole episode. Reset when the queue drains.
     */
    private val reseedSignaled = AtomicBoolean(false)

    // ── Producer (SharedAudioRenderer tap) ──────────────────────────

    /**
     * Enqueue a chunk for delivery.  If the queue is at capacity the oldest
     * chunk is dropped and [droppedCount] is incremented.
     */
    fun enqueue(chunk: AudioChunk) {
        deque.addLast(chunk)
        while (deque.size > MAX_QUEUED_CHUNKS) {
            deque.pollFirst() // drop oldest
            droppedCount.incrementAndGet()
        }
    }

    // ── Consumer (sender thread) ────────────────────────────────────

    /** Dequeue the oldest chunk, or null if the queue is empty. */
    fun poll(): AudioChunk? = deque.pollFirst()

    /** Number of chunks currently queued. */
    val size: Int get() = deque.size

    /** Total number of chunks dropped since this queue was created. */
    val totalDropped: Int get() = droppedCount.get()

    /** Reset the dropped-chunk counter (e.g., after a health downgrade is recorded). */
    fun resetDropCount() {
        droppedCount.set(0)
    }

    /**
     * FIX 2a (R1) — drop the entire queued backlog. Called when the host signals a
     * re-seed: a backlog of stale chunks must not keep being delivered after the
     * guest flushed, or the drop-cascade simply restarts.
     */
    fun flush() {
        while (deque.pollFirst() != null) { /* discard every queued chunk */ }
        droppedCount.set(0)
    }

    /**
     * FIX 2a (R1) — claim the one-shot re-seed signal for this full episode.
     * Returns true exactly once per episode; the caller re-arms via
     * [resetReseedSignal] once the queue drains below the chronic threshold.
     */
    fun tryClaimReseedSignal(): Boolean = reseedSignaled.compareAndSet(false, true)

    /** FIX 2a (R1) — re-arm the one-shot re-seed signal (queue drained). */
    fun resetReseedSignal() {
        reseedSignaled.set(false)
    }

    /**
     * True if the queue is chronically full (>80% of max), indicating a
     * degraded link that should show [com.hearyet.app.core.model.SyncHealth.POOR].
     */
    val isChronicallyFull: Boolean get() = deque.size > (MAX_QUEUED_CHUNKS * 0.8)
}
