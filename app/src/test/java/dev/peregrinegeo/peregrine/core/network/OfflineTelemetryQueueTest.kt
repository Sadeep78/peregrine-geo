package dev.peregrinegeo.peregrine.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTelemetryQueueTest {

    @Test
    fun `enqueue and drain preserve exact FIFO chronological order`() {
        val queue = OfflineTelemetryQueue(maxCapacity = 100)
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)

        for (i in 1..10) {
            queue.enqueue(TelemetryPoint(lat = 37.0 + i, lng = -122.0, ts = i.toLong()))
        }

        assertFalse(queue.isEmpty)
        assertEquals(10, queue.size)

        val batch1 = queue.drain(batchSize = 4)
        assertEquals(4, batch1.size)
        assertEquals(38.0, batch1[0].lat, 0.0001)
        assertEquals(39.0, batch1[1].lat, 0.0001)
        assertEquals(40.0, batch1[2].lat, 0.0001)
        assertEquals(41.0, batch1[3].lat, 0.0001)

        val batch2 = queue.drain(batchSize = 10)
        assertEquals(6, batch2.size)
        assertEquals(42.0, batch2[0].lat, 0.0001)
        assertEquals(47.0, batch2[5].lat, 0.0001)

        assertTrue(queue.isEmpty)
    }

    @Test
    fun `strict cap enforcement drops oldest points when exceeding 10000 capacity`() {
        val queue = OfflineTelemetryQueue(maxCapacity = 10_000)

        // Enqueue 10,050 points
        for (i in 0 until 10_050) {
            queue.enqueue(TelemetryPoint(lat = 37.0, lng = -122.0, ts = i.toLong()))
        }

        assertEquals(10_000, queue.size)
        assertEquals(50L, queue.droppedPointsCount)

        // The oldest 50 points (ts 0 to 49) must have been dropped
        // Therefore the next drained point must have ts = 50
        val drained = queue.drain(batchSize = 1)
        assertEquals(1, drained.size)
        assertEquals(50L, drained[0].ts)

        assertEquals(9_999, queue.size)
        assertEquals(50L, queue.resetDroppedCount())
        assertEquals(0L, queue.droppedPointsCount)
    }

    @Test
    fun `drain returns empty list when queue is empty`() {
        val queue = OfflineTelemetryQueue(maxCapacity = 100)
        val result = queue.drain(50)
        assertTrue(result.isEmpty())
    }
}
