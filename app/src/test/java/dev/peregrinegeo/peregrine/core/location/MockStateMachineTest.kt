package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockStateMachineTest {

    private val pointA = LatLng(37.7749, -122.4194)
    private val pointB = LatLng(37.7849, -122.4094)

    @Test
    fun testLifecycleFromIdleToActiveToIdle() {
        val sm = MockStateMachine()
        assertTrue(sm.state is MockState.Idle)

        // 1. Select Preview
        val preview = sm.selectPreview(MockTarget.SinglePoint(pointA))
        assertTrue(preview is MockState.Preview)
        assertEquals(pointA, (preview as MockState.Preview).target.let { (it as MockTarget.SinglePoint).point })

        // 2. Start Active
        val active = sm.startMock()
        assertTrue(active is MockState.Active)
        assertTrue((active as MockState.Active).isPlaying)

        // 3. Pause
        val paused = sm.pause()
        assertTrue(paused is MockState.Active)
        assertFalse((paused as MockState.Active).isPlaying)

        // 4. Resume
        val resumed = sm.resume()
        assertTrue(resumed is MockState.Active)
        assertTrue((resumed as MockState.Active).isPlaying)

        // 5. Stop
        val idle = sm.stop()
        assertTrue(idle is MockState.Idle)
    }

    @Test
    fun testConfirmationDiffWhenSwitchingActiveMocks() {
        val sm = MockStateMachine()
        sm.selectPreview(MockTarget.SinglePoint(pointA))
        sm.startMock()
        assertTrue(sm.state is MockState.Active)

        // Select a different target while Active
        val newTarget = MockTarget.SinglePoint(pointB)
        val diffState = sm.selectPreview(newTarget)
        assertTrue("Must transition to AwaitingDiffConfirmation", diffState is MockState.AwaitingDiffConfirmation)

        val awaiting = diffState as MockState.AwaitingDiffConfirmation
        assertEquals(newTarget, awaiting.pendingTarget)
        assertTrue(awaiting.diff.current.targetName.isNotEmpty())
        assertTrue(awaiting.diff.prospective.targetName.isNotEmpty())

        // If dismissed, returns to original Active
        val dismissed = sm.dismissDiff()
        assertTrue(dismissed is MockState.Active)
        assertEquals(pointA, ((dismissed as MockState.Active).target as MockTarget.SinglePoint).point)

        // Trigger diff again and confirm
        sm.selectPreview(newTarget)
        val confirmed = sm.confirmDiff()
        assertTrue(confirmed is MockState.Active)
        assertEquals(pointB, ((confirmed as MockState.Active).target as MockTarget.SinglePoint).point)
    }

    @Test
    fun testSecurityExceptionTransitionsToNotMockApp() {
        val sm = MockStateMachine()
        sm.selectPreview(MockTarget.SinglePoint(pointA))
        sm.onSecurityException("Mock location permission denied")
        assertTrue(sm.state is MockState.NotMockApp)
        assertEquals("Mock location permission denied", (sm.state as MockState.NotMockApp).message)
    }

    @Test
    fun testSpeedMultiplierUpdatesConfig() {
        val sm = MockStateMachine()
        sm.selectPreview(MockTarget.SinglePoint(pointA))
        sm.setSpeedMultiplier(2.5)
        assertEquals(2.5, (sm.state as MockState.Preview).config.speedMultiplier, 0.001)

        sm.startMock()
        sm.setSpeedMultiplier(3.0)
        assertEquals(3.0, (sm.state as MockState.Active).config.speedMultiplier, 0.001)
    }
}
