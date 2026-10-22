package dev.peregrinegeo.peregrine.core.network

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Calculates exponential backoff with randomized jitter for fleet WebSocket reconnection.
 *
 * Default configuration:
 * - minDelayMs: 1,000 ms (1 s)
 * - maxDelayMs: 60,000 ms (60 s)
 * - factor: 2.0
 * - jitterFraction: +/- 20% (0.20)
 */
class ExponentialBackoff(
    val minDelayMs: Long = 1000L,
    val maxDelayMs: Long = 60000L,
    val factor: Double = 2.0,
    val jitterFraction: Double = 0.20,
    private val randomProvider: () -> Double = { Random.nextDouble() }
) {
    var attempt: Int = 0
        private set

    /**
     * Computes the next backoff delay in milliseconds, advancing the attempt counter.
     */
    fun nextDelayMs(): Long {
        val rawDelay = min(maxDelayMs.toDouble(), minDelayMs * factor.pow(attempt.toDouble()))
        attempt++
        // Jitter between (1.0 - jitterFraction) and (1.0 + jitterFraction)
        val jitterRange = 2.0 * jitterFraction
        val jitterMultiplier = (1.0 - jitterFraction) + (jitterRange * randomProvider())
        val calculated = (rawDelay * jitterMultiplier).toLong()
        return calculated.coerceAtLeast(100L)
    }

    /**
     * Resets attempt count back to 0 on successful connection.
     */
    fun reset() {
        attempt = 0
    }
}
