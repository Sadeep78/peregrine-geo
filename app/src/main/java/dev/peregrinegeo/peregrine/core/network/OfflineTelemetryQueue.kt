package dev.peregrinegeo.peregrine.core.network

import java.util.ArrayDeque

/**
 * Thread-safe bounded offline queue for telemetry points.
 *
 * Implements strict capacity capping (default 10,000 points).
 * When capacity is exceeded during an extended network disconnect,
 * the oldest points are dropped in FIFO order to preserve the freshest
 * kinematic fixes, incrementing [droppedPointsCount].
 */
class OfflineTelemetryQueue(
    val maxCapacity: Int = 10_000
) {
    private val lock = Any()
    private val buffer = ArrayDeque<TelemetryPoint>(minOf(maxCapacity, 1024))
    
    var droppedPointsCount: Long = 0
        private set

    /**
     * Enqueues a telemetry point. If the queue is at [maxCapacity],
     * the oldest point is removed and [droppedPointsCount] is incremented.
     */
    fun enqueue(point: TelemetryPoint) {
        synchronized(lock) {
            if (buffer.size >= maxCapacity) {
                buffer.pollFirst()
                droppedPointsCount++
            }
            buffer.addLast(point)
        }
    }

    /**
     * Drains up to [batchSize] points from the front of the queue (FIFO order).
     */
    fun drain(batchSize: Int = 50): List<TelemetryPoint> {
        require(batchSize > 0) { "batchSize must be positive" }
        synchronized(lock) {
            if (buffer.isEmpty()) return emptyList()
            val count = minOf(batchSize, buffer.size)
            val batch = ArrayList<TelemetryPoint>(count)
            for (i in 0 until count) {
                batch.add(buffer.removeFirst())
            }
            return batch
        }
    }

    /**
     * Returns the current number of queued points.
     */
    val size: Int
        get() = synchronized(lock) { buffer.size }

    /**
     * Returns true if the queue is empty.
     */
    val isEmpty: Boolean
        get() = synchronized(lock) { buffer.isEmpty() }

    /**
     * Resets the dropped points count (e.g. after reporting metric in a batch).
     */
    fun resetDroppedCount(): Long {
        synchronized(lock) {
            val count = droppedPointsCount
            droppedPointsCount = 0
            return count
        }
    }

    /**
     * Clears all items in the queue.
     */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            droppedPointsCount = 0
        }
    }
}
