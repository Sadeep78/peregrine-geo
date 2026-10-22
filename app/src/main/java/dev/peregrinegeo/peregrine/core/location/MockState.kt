package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.LatLng

sealed interface MockTarget {
    val displayName: String

    data class SinglePoint(
        val point: LatLng,
        override val displayName: String = "Point (${String.format("%.4f, %.4f", point.latitude, point.longitude)})"
    ) : MockTarget

    data class Route(
        val routeId: String,
        val name: String,
        val waypoints: List<LatLng>,
        val playMode: PlayMode = PlayMode.Once
    ) : MockTarget {
        override val displayName: String = name
    }
}

data class MockConfig(
    val speedMps: Double = 5.0,
    val speedMultiplier: Double = 1.0,
    val profileName: String = "Drive",
    val noisePreset: String = "Ideal",
    val altitudeMeters: Double = 15.0
) {
    val effectiveSpeedMps: Double get() = speedMps * speedMultiplier
}

data class MockProgress(
    val currentPosition: LatLng,
    val bearing: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val progressFraction: Double = 0.0,
    val distanceTraveledMeters: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val estimatedRemainingSeconds: Double = 0.0
)

data class ActiveSnapshot(
    val targetName: String,
    val profileName: String,
    val speedMps: Double,
    val noisePreset: String,
    val estimatedDurationSeconds: Double
)

data class ConfirmationDiff(
    val current: ActiveSnapshot,
    val prospective: ActiveSnapshot
)

sealed interface MockState {

    object Idle : MockState

    data class Preview(
        val target: MockTarget,
        val config: MockConfig = MockConfig()
    ) : MockState

    data class Active(
        val target: MockTarget,
        val config: MockConfig,
        val isPlaying: Boolean,
        val progress: MockProgress
    ) : MockState

    data class AwaitingDiffConfirmation(
        val currentActive: Active,
        val pendingTarget: MockTarget,
        val pendingConfig: MockConfig,
        val diff: ConfirmationDiff
    ) : MockState

    data class Error(
        val reason: String,
        val canRetry: Boolean = true
    ) : MockState

    data class NotMockApp(
        val message: String = "Peregrine is not selected as mock location app in Developer Options"
    ) : MockState
}
