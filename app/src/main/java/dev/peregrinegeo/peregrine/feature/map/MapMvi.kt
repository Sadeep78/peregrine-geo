package dev.peregrinegeo.peregrine.feature.map

import dev.peregrinegeo.peregrine.core.database.FavoriteLocation
import dev.peregrinegeo.peregrine.core.database.FavoriteSortOrder
import dev.peregrinegeo.peregrine.core.database.FavoritesRepository
import dev.peregrinegeo.peregrine.core.datastore.SettingsRepository
import dev.peregrinegeo.peregrine.core.geo.CoordParser
import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.ConfirmationDiff
import dev.peregrinegeo.peregrine.core.location.MockState
import dev.peregrinegeo.peregrine.core.location.MockStateMachine
import dev.peregrinegeo.peregrine.core.location.MockTarget
import dev.peregrinegeo.peregrine.feature.checklist.OnboardingManager
import dev.peregrinegeo.peregrine.feature.checklist.OnboardingStep

data class MapUiState(
    val mockState: MockState = MockState.Idle,
    val searchQuery: String = "",
    val parsedCoordinate: LatLng? = null,
    val favorites: List<FavoriteLocation> = emptyList(),
    val favoriteSortOrder: FavoriteSortOrder = FavoriteSortOrder.Recent,
    val isDiffDialogVisible: Boolean = false,
    val diffData: ConfirmationDiff? = null,
    val onboardingSteps: List<OnboardingStep> = emptyList(),
    val isRecoveryPromptVisible: Boolean = false,
    val speedMultiplier: Double = 1.0,
    val cameraTarget: LatLng = LatLng(37.7749, -122.4194)
)

sealed interface MapIntent {
    data class SearchQueryChanged(val query: String) : MapIntent
    data class SelectPoint(val point: LatLng) : MapIntent
    data class SelectRoute(val target: MockTarget.Route) : MapIntent
    object StartMock : MapIntent
    object PauseMock : MapIntent
    object ResumeMock : MapIntent
    object StopMock : MapIntent
    object ConfirmDiff : MapIntent
    object DismissDiff : MapIntent
    data class SetSpeedMultiplier(val multiplier: Double) : MapIntent
    data class AddFavorite(val name: String, val point: LatLng) : MapIntent
    data class DeleteFavorite(val id: String) : MapIntent
    data class ChangeFavoriteSort(val sortOrder: FavoriteSortOrder) : MapIntent
    object ConfirmRouteRecovery : MapIntent
    object DismissRouteRecovery : MapIntent
}

sealed interface MapEffect {
    data class ShowToast(val message: String) : MapEffect
    data class OpenDeepLink(val action: String) : MapEffect
    data class AnimateCamera(val target: LatLng) : MapEffect
}

/**
 * MVI Presentation ViewModel handling unidirectional State, Intent, and Effect dispatch.
 */
class MapViewModel(
    val stateMachine: MockStateMachine,
    val favoritesRepo: FavoritesRepository,
    val settingsRepo: SettingsRepository,
    val onboardingManager: OnboardingManager
) {
    var uiState: MapUiState = MapUiState()
        private set

    private val effects = mutableListOf<MapEffect>()

    init {
        // Initial state population
        refreshState()

        // Check route recovery policy
        if (settingsRepo.shouldPromptForRecovery()) {
            uiState = uiState.copy(isRecoveryPromptVisible = true)
        } else if (settingsRepo.shouldAutoResume()) {
            resumeFromCheckpoint()
        }
    }

    fun processIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.SearchQueryChanged -> {
                val parsed = CoordParser.parse(intent.query)
                uiState = uiState.copy(searchQuery = intent.query, parsedCoordinate = parsed)
                if (parsed != null) {
                    processIntent(MapIntent.SelectPoint(parsed))
                }
            }

            is MapIntent.SelectPoint -> {
                val target = MockTarget.SinglePoint(intent.point)
                stateMachine.selectPreview(target)
                refreshState()
                effects.add(MapEffect.AnimateCamera(intent.point))
            }

            is MapIntent.SelectRoute -> {
                stateMachine.selectPreview(intent.target)
                refreshState()
                if (intent.target.waypoints.isNotEmpty()) {
                    effects.add(MapEffect.AnimateCamera(intent.target.waypoints.first()))
                }
            }

            is MapIntent.StartMock -> {
                stateMachine.startMock()
                refreshState()
                effects.add(MapEffect.ShowToast("Mock location active"))
            }

            is MapIntent.PauseMock -> {
                stateMachine.pause()
                refreshState()
            }

            is MapIntent.ResumeMock -> {
                stateMachine.resume()
                refreshState()
            }

            is MapIntent.StopMock -> {
                stateMachine.stop()
                settingsRepo.clearRouteCheckpoint()
                refreshState()
                effects.add(MapEffect.ShowToast("Mock location stopped"))
            }

            is MapIntent.ConfirmDiff -> {
                stateMachine.confirmDiff()
                refreshState()
            }

            is MapIntent.DismissDiff -> {
                stateMachine.dismissDiff()
                refreshState()
            }

            is MapIntent.SetSpeedMultiplier -> {
                stateMachine.setSpeedMultiplier(intent.multiplier)
                refreshState()
            }

            is MapIntent.AddFavorite -> {
                favoritesRepo.add(intent.name, intent.point)
                refreshState()
                effects.add(MapEffect.ShowToast("Saved to favorites"))
            }

            is MapIntent.DeleteFavorite -> {
                favoritesRepo.delete(intent.id)
                refreshState()
            }

            is MapIntent.ChangeFavoriteSort -> {
                uiState = uiState.copy(favoriteSortOrder = intent.sortOrder)
                refreshState()
            }

            is MapIntent.ConfirmRouteRecovery -> {
                uiState = uiState.copy(isRecoveryPromptVisible = false)
                resumeFromCheckpoint()
            }

            is MapIntent.DismissRouteRecovery -> {
                uiState = uiState.copy(isRecoveryPromptVisible = false)
                settingsRepo.clearRouteCheckpoint()
            }
        }
    }

    fun pollEffects(): List<MapEffect> {
        val list = effects.toList()
        effects.clear()
        return list
    }

    private fun resumeFromCheckpoint() {
        val checkpoint = settingsRepo.activeRouteCheckpoint ?: return
        val routeTarget = MockTarget.Route(
            routeId = checkpoint.routeId,
            name = checkpoint.routeName,
            waypoints = checkpoint.waypoints,
            playMode = checkpoint.playMode
        )
        stateMachine.selectPreview(routeTarget)
        stateMachine.setSpeedMultiplier(checkpoint.speedMultiplier)
        stateMachine.startMock()
        refreshState()
        effects.add(MapEffect.ShowToast("Resumed route: ${checkpoint.routeName}"))
    }

    private fun refreshState() {
        val machineState = stateMachine.state
        val isDiff = machineState is MockState.AwaitingDiffConfirmation
        val diff = if (machineState is MockState.AwaitingDiffConfirmation) machineState.diff else null

        uiState = uiState.copy(
            mockState = machineState,
            favorites = favoritesRepo.getAll(uiState.favoriteSortOrder),
            onboardingSteps = onboardingManager.getSteps(),
            isDiffDialogVisible = isDiff,
            diffData = diff,
            speedMultiplier = when (machineState) {
                is MockState.Active -> machineState.config.speedMultiplier
                is MockState.Preview -> machineState.config.speedMultiplier
                else -> uiState.speedMultiplier
            }
        )
    }
}
