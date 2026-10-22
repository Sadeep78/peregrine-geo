package dev.peregrinegeo.peregrine.feature.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOnboardingStatusChecker : OnboardingStatusChecker {
    var devOptions = true
    var mockApp = true
    var googleAccuracyDisabled = true
    var notifications = true
    var overlay = false

    override fun isDeveloperOptionsEnabled(): Boolean = devOptions
    override fun isMockLocationAppSelected(): Boolean = mockApp
    override fun isGoogleLocationAccuracyDisabled(): Boolean = googleAccuracyDisabled
    override fun isNotificationPermissionGranted(): Boolean = notifications
    override fun isOverlayPermissionGranted(): Boolean = overlay
}

class OnboardingManagerTest {

    @Test
    fun testAllMandatoryStepsComplete() {
        val fake = FakeOnboardingStatusChecker()
        val manager = OnboardingManager(fake)

        val steps = manager.getSteps()
        assertEquals(5, steps.size)
        assertTrue(manager.isReadyToMock)
    }

    @Test
    fun testMissingMockAppBlocksReadiness() {
        val fake = FakeOnboardingStatusChecker().apply {
            mockApp = false
        }
        val manager = OnboardingManager(fake)

        assertFalse(manager.isReadyToMock)
        val mockStep = manager.getSteps().first { it.id == OnboardingStepId.MOCK_LOCATION_APP }
        assertFalse(mockStep.isCompleted)
    }

    @Test
    fun testMissingOverlayDoesNotBlockReadiness() {
        val fake = FakeOnboardingStatusChecker().apply {
            overlay = false // optional
        }
        val manager = OnboardingManager(fake)
        assertTrue(manager.isReadyToMock)
    }
}
