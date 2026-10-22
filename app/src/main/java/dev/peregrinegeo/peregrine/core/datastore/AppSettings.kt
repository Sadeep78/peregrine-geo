package dev.peregrinegeo.peregrine.core.datastore

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.PlayMode

enum class AppOpeningBehavior {
    LastView,
    DeviceCentre,
    SavedFavorite
}

enum class RouteRecoveryPolicy {
    Off,
    Ask,
    Auto
}

data class PersistedRouteCheckpoint(
    val routeId: String,
    val routeName: String,
    val waypoints: List<LatLng>,
    val playMode: PlayMode,
    val speedMps: Double,
    val speedMultiplier: Double,
    val progressFraction: Double,
    val distanceTraveledMeters: Double,
    val timestampMs: Long = System.currentTimeMillis()
)

interface SettingsRepository {
    var appOpeningBehavior: AppOpeningBehavior
    var defaultFavoriteId: String?
    var routeRecoveryPolicy: RouteRecoveryPolicy
    var lastKnownMapCenter: LatLng?
    var activeRouteCheckpoint: PersistedRouteCheckpoint?

    fun shouldPromptForRecovery(): Boolean {
        return routeRecoveryPolicy == RouteRecoveryPolicy.Ask && activeRouteCheckpoint != null
    }

    fun shouldAutoResume(): Boolean {
        return routeRecoveryPolicy == RouteRecoveryPolicy.Auto && activeRouteCheckpoint != null
    }

    fun clearRouteCheckpoint() {
        activeRouteCheckpoint = null
    }
}

class InMemorySettingsRepository : SettingsRepository {
    override var appOpeningBehavior: AppOpeningBehavior = AppOpeningBehavior.LastView
    override var defaultFavoriteId: String? = null
    override var routeRecoveryPolicy: RouteRecoveryPolicy = RouteRecoveryPolicy.Ask
    override var lastKnownMapCenter: LatLng? = null
    override var activeRouteCheckpoint: PersistedRouteCheckpoint? = null
}
