package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.GeoMath

/**
 * Pure Kotlin MVI State Machine managing location mocking state transitions.
 * Enforces strict separation between Preview and Active states,
 * and requires a ConfirmationDiff dialog when switching between Active mocks.
 */
class MockStateMachine(initialState: MockState = MockState.Idle) {

    var state: MockState = initialState
        private set

    private val listeners = mutableListOf<(MockState) -> Unit>()

    fun addListener(listener: (MockState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun removeListener(listener: (MockState) -> Unit) {
        listeners.remove(listener)
    }

    private fun setState(newState: MockState) {
        state = newState
        for (listener in listeners) {
            listener(newState)
        }
    }

    /**
     * User selects a target (point or route) to preview.
     * If already Active and target/config is different, presents Confirmation Diff.
     */
    fun selectPreview(target: MockTarget, config: MockConfig = MockConfig()): MockState {
        val currentState = state
        if (currentState is MockState.Active) {
            // Active mock already running: check if switching
            val isSame = currentState.target == target && currentState.config == config
            if (!isSame) {
                val currentSnapshot = createSnapshot(currentState.target, currentState.config, currentState.progress)
                val prospectiveSnapshot = createSnapshot(target, config, null)
                val diff = ConfirmationDiff(current = currentSnapshot, prospective = prospectiveSnapshot)
                val diffState = MockState.AwaitingDiffConfirmation(
                    currentActive = currentState,
                    pendingTarget = target,
                    pendingConfig = config,
                    diff = diff
                )
                setState(diffState)
                return diffState
            }
        }

        val previewState = MockState.Preview(target, config)
        setState(previewState)
        return previewState
    }

    /**
     * User confirms switching active mock via Confirmation Diff Dialog.
     */
    fun confirmDiff(): MockState {
        val currentState = state
        if (currentState is MockState.AwaitingDiffConfirmation) {
            val initialProgress = createInitialProgress(currentState.pendingTarget, currentState.pendingConfig)
            val newActive = MockState.Active(
                target = currentState.pendingTarget,
                config = currentState.pendingConfig,
                isPlaying = true,
                progress = initialProgress
            )
            setState(newActive)
            return newActive
        }
        return state
    }

    /**
     * User dismisses Confirmation Diff Dialog. Returns to previous Active state.
     */
    fun dismissDiff(): MockState {
        val currentState = state
        if (currentState is MockState.AwaitingDiffConfirmation) {
            setState(currentState.currentActive)
            return currentState.currentActive
        }
        return state
    }

    /**
     * Start active mocking from Preview.
     */
    fun startMock(): MockState {
        val currentState = state
        if (currentState is MockState.Preview) {
            val initialProgress = createInitialProgress(currentState.target, currentState.config)
            val activeState = MockState.Active(
                target = currentState.target,
                config = currentState.config,
                isPlaying = true,
                progress = initialProgress
            )
            setState(activeState)
            return activeState
        }
        return state
    }

    /**
     * Pause active mock playback.
     */
    fun pause(): MockState {
        val currentState = state
        if (currentState is MockState.Active && currentState.isPlaying) {
            val paused = currentState.copy(isPlaying = false)
            setState(paused)
            return paused
        }
        return state
    }

    /**
     * Resume active mock playback.
     */
    fun resume(): MockState {
        val currentState = state
        if (currentState is MockState.Active && !currentState.isPlaying) {
            val resumed = currentState.copy(isPlaying = true)
            setState(resumed)
            return resumed
        }
        return state
    }

    /**
     * Stop mocking and return to Idle.
     */
    fun stop(): MockState {
        val idle = MockState.Idle
        setState(idle)
        return idle
    }

    /**
     * Update progress of an active mock.
     */
    fun updateProgress(progress: MockProgress): MockState {
        val currentState = state
        if (currentState is MockState.Active) {
            val updated = currentState.copy(progress = progress)
            setState(updated)
            return updated
        }
        return state
    }

    /**
     * Dynamically change speed multiplier.
     */
    fun setSpeedMultiplier(multiplier: Double): MockState {
        require(multiplier > 0.0) { "Speed multiplier must be > 0" }
        when (val currentState = state) {
            is MockState.Active -> {
                val updatedConfig = currentState.config.copy(speedMultiplier = multiplier)
                val updated = currentState.copy(config = updatedConfig)
                setState(updated)
                return updated
            }
            is MockState.Preview -> {
                val updatedConfig = currentState.config.copy(speedMultiplier = multiplier)
                val updated = currentState.copy(config = updatedConfig)
                setState(updated)
                return updated
            }
            else -> return state
        }
    }

    /**
     * Flag that app is not authorized as Mock Location App in Android Developer Options.
     */
    fun onSecurityException(message: String = "App not set as mock location provider in Developer Options"): MockState {
        val notMockState = MockState.NotMockApp(message)
        setState(notMockState)
        return notMockState
    }

    fun onError(reason: String, canRetry: Boolean = true): MockState {
        val errorState = MockState.Error(reason, canRetry)
        setState(errorState)
        return errorState
    }

    private fun createInitialProgress(target: MockTarget, config: MockConfig): MockProgress {
        return when (target) {
            is MockTarget.SinglePoint -> MockProgress(
                currentPosition = target.point,
                bearing = 0.0,
                currentSpeedMps = 0.0,
                progressFraction = 1.0,
                distanceTraveledMeters = 0.0,
                totalDistanceMeters = 0.0,
                estimatedRemainingSeconds = 0.0
            )
            is MockTarget.Route -> {
                val totalDist = calculateTotalDistance(target.waypoints)
                val effectiveSpeed = config.effectiveSpeedMps
                val durationSec = if (effectiveSpeed > 0) totalDist / effectiveSpeed else 0.0
                MockProgress(
                    currentPosition = target.waypoints.first(),
                    bearing = if (target.waypoints.size >= 2) GeoMath.initialBearing(target.waypoints[0], target.waypoints[1]) else 0.0,
                    currentSpeedMps = effectiveSpeed,
                    progressFraction = 0.0,
                    distanceTraveledMeters = 0.0,
                    totalDistanceMeters = totalDist,
                    estimatedRemainingSeconds = durationSec
                )
            }
        }
    }

    private fun calculateTotalDistance(waypoints: List<dev.peregrinegeo.peregrine.core.geo.LatLng>): Double {
        var sum = 0.0
        for (i in 0 until waypoints.size - 1) {
            sum += GeoMath.distanceMeters(waypoints[i], waypoints[i + 1])
        }
        return sum
    }

    private fun createSnapshot(target: MockTarget, config: MockConfig, progress: MockProgress?): ActiveSnapshot {
        val totalDist = when (target) {
            is MockTarget.SinglePoint -> 0.0
            is MockTarget.Route -> calculateTotalDistance(target.waypoints)
        }
        val duration = if (config.effectiveSpeedMps > 0.0) totalDist / config.effectiveSpeedMps else 0.0
        return ActiveSnapshot(
            targetName = target.displayName,
            profileName = config.profileName,
            speedMps = config.effectiveSpeedMps,
            noisePreset = config.noisePreset,
            estimatedDurationSeconds = duration
        )
    }
}
