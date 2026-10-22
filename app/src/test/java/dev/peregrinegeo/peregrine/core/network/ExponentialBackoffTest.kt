package dev.peregrinegeo.peregrine.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffTest {

    @Test
    fun `initial attempt produces delay near 1s with 20 percent jitter bounds`() {
        val backoff = ExponentialBackoff(minDelayMs = 1000L, maxDelayMs = 60000L, jitterFraction = 0.20)
        
        // 100 iterations to verify all randomized samples stay within [800, 1200] ms
        for (i in 0 until 100) {
            val freshBackoff = ExponentialBackoff(minDelayMs = 1000L, maxDelayMs = 60000L, jitterFraction = 0.20)
            val delay = freshBackoff.nextDelayMs()
            assertTrue("Delay $delay should be >= 800ms", delay >= 800L)
            assertTrue("Delay $delay should be <= 1200ms", delay <= 1200L)
        }
    }

    @Test
    fun `deterministic random test verifies exponential doubling`() {
        // Fix random to 0.5 (exact midpoint = zero jitter effect)
        val backoff = ExponentialBackoff(
            minDelayMs = 1000L,
            maxDelayMs = 60000L,
            factor = 2.0,
            jitterFraction = 0.20,
            randomProvider = { 0.5 }
        )

        assertEquals(0, backoff.attempt)
        assertEquals(1000L, backoff.nextDelayMs()) // 1000 * 2^0
        assertEquals(1, backoff.attempt)
        assertEquals(2000L, backoff.nextDelayMs()) // 1000 * 2^1
        assertEquals(2, backoff.attempt)
        assertEquals(4000L, backoff.nextDelayMs()) // 1000 * 2^2
        assertEquals(3, backoff.attempt)
        assertEquals(8000L, backoff.nextDelayMs()) // 1000 * 2^3
        assertEquals(16000L, backoff.nextDelayMs()) // 1000 * 2^4
        assertEquals(32000L, backoff.nextDelayMs()) // 1000 * 2^5
        assertEquals(60000L, backoff.nextDelayMs()) // capped at 60000
        assertEquals(60000L, backoff.nextDelayMs()) // still capped at 60000
    }

    @Test
    fun `reset sets attempt counter back to zero`() {
        val backoff = ExponentialBackoff(randomProvider = { 0.5 })
        backoff.nextDelayMs()
        backoff.nextDelayMs()
        backoff.nextDelayMs()
        assertEquals(3, backoff.attempt)

        backoff.reset()
        assertEquals(0, backoff.attempt)
        assertEquals(1000L, backoff.nextDelayMs())
    }
}
