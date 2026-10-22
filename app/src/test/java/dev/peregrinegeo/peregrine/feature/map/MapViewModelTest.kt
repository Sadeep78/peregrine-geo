package dev.peregrinegeo.peregrine.feature.map

import dev.peregrinegeo.peregrine.core.database.InMemoryFavoritesRepository
import dev.peregrinegeo.peregrine.core.datastore.InMemorySettingsRepository
import dev.peregrinegeo.peregrine.core.datastore.PersistedRouteCheckpoint
import dev.peregrinegeo.peregrine.core.datastore.RouteRecoveryPolicy
import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.MockState
import dev.peregrinegeo.peregrine.core.location.MockStateMachine
import dev.peregrinegeo.peregrine.core.location.MockTarget
import dev.peregrinegeo.peregrine.core.location.PlayMode
import dev.peregrinegeo.peregrine.feature.checklist.FakeOnboardingStatusChecker
import dev.peregrinegeo.peregrine.feature.checklist.OnboardingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewModelTest {

    private fun createViewModel(
        initialCheckpoint: PersistedRouteCheckpoint? = null,
        recoveryPolicy: RouteRecoveryPolicy = RouteRecoveryPolicy.Ask
    ): MapViewModel {
        val sm = MockStateMachine()
        val favRepo = InMemoryFavoritesRepository()
        val settingsRepo = InMemorySettingsRepository().apply {
            routeRecoveryPolicy = recoveryPolicy
            activeRouteCheckpoint = initialCheckpoint
        }
        val onboarding = OnboardingManager(FakeOnboardingStatusChecker())
        return MapViewModel(sm, favRepo, settingsRepo, onboarding)
    }

    @Test
    fun testCoordinateSearchUpdatesPreview() {
        val vm = createViewModel()
        vm.processIntent(MapIntent.SearchQueryChanged("37.7749, -122.4194"))

        val state = vm.uiState
        assertNotNull(state.parsedCoordinate)
        assertTrue(state.mockState is MockState.Preview)

        val effects = vm.pollEffects()
        assertTrue(effects.any { it is MapEffect.AnimateCamera })
    }

    @Test
    fun testMockPlaybackLifecycle() {
        val vm = createViewModel()
        val pt = LatLng(37.7749, -122.4194)
        vm.processIntent(MapIntent.SelectPoint(pt))

        // Start
        vm.processIntent(MapIntent.StartMock)
        assertTrue(vm.uiState.mockState is MockState.Active)
        assertTrue((vm.uiState.mockState as MockState.Active).isPlaying)

        // Pause
        vm.processIntent(MapIntent.PauseMock)
        assertFalse((vm.uiState.mockState as MockState.Active).isPlaying)

        // Resume
        vm.processIntent(MapIntent.ResumeMock)
        assertTrue((vm.uiState.mockState as MockState.Active).isPlaying)

        // Stop
        vm.processIntent(MapIntent.StopMock)
        assertTrue(vm.uiState.mockState is MockState.Idle)
    }

    @Test
    fun testConfirmationDiffFlow() {
        val vm = createViewModel()
        val pt1 = LatLng(37.7749, -122.4194)
        val pt2 = LatLng(37.7849, -122.4094)

        vm.processIntent(MapIntent.SelectPoint(pt1))
        vm.processIntent(MapIntent.StartMock)

        // While active, pick new point
        vm.processIntent(MapIntent.SelectPoint(pt2))
        assertTrue(vm.uiState.isDiffDialogVisible)
        assertNotNull(vm.uiState.diffData)

        // Dismiss diff -> keeps original active
        vm.processIntent(MapIntent.DismissDiff)
        assertFalse(vm.uiState.isDiffDialogVisible)
        assertEquals(pt1, ((vm.uiState.mockState as MockState.Active).target as MockTarget.SinglePoint).point)

        // Trigger diff again and confirm -> switches to pt2
        vm.processIntent(MapIntent.SelectPoint(pt2))
        vm.processIntent(MapIntent.ConfirmDiff)
        assertFalse(vm.uiState.isDiffDialogVisible)
        assertEquals(pt2, ((vm.uiState.mockState as MockState.Active).target as MockTarget.SinglePoint).point)
    }

    @Test
    fun testRecoveryPromptOnLaunch() {
        val checkpoint = PersistedRouteCheckpoint(
            routeId = "r1",
            routeName = "Saved Scenic Route",
            waypoints = listOf(LatLng(10.0, 10.0), LatLng(10.1, 10.1)),
            playMode = PlayMode.Once,
            speedMps = 5.0,
            speedMultiplier = 1.0,
            progressFraction = 0.5,
            distanceTraveledMeters = 500.0
        )
        val vm = createViewModel(initialCheckpoint = checkpoint, recoveryPolicy = RouteRecoveryPolicy.Ask)

        assertTrue(vm.uiState.isRecoveryPromptVisible)

        // Confirm recovery
        vm.processIntent(MapIntent.ConfirmRouteRecovery)
        assertFalse(vm.uiState.isRecoveryPromptVisible)
        assertTrue(vm.uiState.mockState is MockState.Active)
        assertEquals("Saved Scenic Route", (vm.uiState.mockState as MockState.Active).target.displayName)
    }
}
