package com.hearyet.app.sync

import com.hearyet.app.feature.player.sync.AudioChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BE §6 backpressure — per-guest bounded outbound queue.
 *
 * Verifies the "drop the OLDEST queued chunks first, not the newest" policy
 * and the chronically-full signal that downgrades a guest's SyncHealth.
 */
class GuestOutboundQueueTest {

    private fun chunk(sequenceNumber: Long): AudioChunk = AudioChunk(
        hostTimestampNanos = 1_000_000L + sequenceNumber,
        sequenceNumber = sequenceNumber,
        pcmPayload = ByteArray(16) { it.toByte() },
    )

    @Test
    fun poll_returnsChunksInFifoOrder() {
        val queue = GuestOutboundQueue("endpoint-1")

        queue.enqueue(chunk(1))
        queue.enqueue(chunk(2))
        queue.enqueue(chunk(3))

        assertEquals(1L, queue.poll()?.sequenceNumber)
        assertEquals(2L, queue.poll()?.sequenceNumber)
        assertEquals(3L, queue.poll()?.sequenceNumber)
        assertNull(queue.poll())
    }

    @Test
    fun enqueueBeyondCapacity_dropsOldestFirst_notNewest() {
        val queue = GuestOutboundQueue("endpoint-1")

        for (seq in 1L..(GuestOutboundQueue.MAX_QUEUED_CHUNKS + 5L)) {
            queue.enqueue(chunk(seq))
        }

        // Capacity is unchanged and the five oldest chunks (1..5) were dropped.
        assertEquals(GuestOutboundQueue.MAX_QUEUED_CHUNKS, queue.size)
        assertEquals(5, queue.totalDropped)
        assertEquals(6L, queue.poll()?.sequenceNumber)
    }

    @Test
    fun enqueueMany_dropsOldestContinuously() {
        val queue = GuestOutboundQueue("endpoint-1")

        for (seq in 1L..250L) {
            queue.enqueue(chunk(seq))
        }

        assertEquals(50, queue.totalDropped)
        // The surviving window starts at 51.
        assertEquals(51L, queue.poll()?.sequenceNumber)
        assertEquals(GuestOutboundQueue.MAX_QUEUED_CHUNKS - 1, queue.size)
    }

    @Test
    fun isChronicallyFull_flipsAtEightyPercent() {
        val queue = GuestOutboundQueue("endpoint-1")

        for (seq in 1L..160L) {
            queue.enqueue(chunk(seq))
        }
        assertFalse("at exactly 80% the queue is not chronically full", queue.isChronicallyFull)

        queue.enqueue(chunk(161L))
        assertTrue("above 80% the queue signals a degraded link", queue.isChronicallyFull)
    }

    @Test
    fun resetDropCount_zeroesTheCounter() {
        val queue = GuestOutboundQueue("endpoint-1")

        for (seq in 1L..(GuestOutboundQueue.MAX_QUEUED_CHUNKS + 1L)) {
            queue.enqueue(chunk(seq))
        }
        assertEquals(1, queue.totalDropped)

        queue.resetDropCount()

        assertEquals(0, queue.totalDropped)
    }
}
