package dev.peregrinegeo.peregrine.feature.fleet

/**
 * Configuration and lifecycle contract for the Remote Standby foreground service.
 *
 * Keeps the device connected to the fleet WebSocket while the screen is off or app is backgrounded,
 * acquiring a partial WakeLock and dispatching 30s heartbeats.
 */
data class RemoteStandbyConfig(
    val enabled: Boolean = false,
    val heartbeatIntervalMs: Long = 30_000L,
    val notificationTitle: String = "Peregrine Fleet Standby",
    val notificationContent: String = "Listening for remote fleet scenarios and commands",
    val channelId: String = "peregrine_fleet_standby",
    val wakeLockTag: String = "Peregrine::FleetStandbyWakeLock"
)

/**
 * Pure state model representing the active state of the background standby service.
 */
data class RemoteStandbyServiceState(
    val isRunning: Boolean = false,
    val lastHeartbeatTs: Long = 0L,
    val wakeLockAcquired: Boolean = false,
    val batteryPct: Int = 100,
    val isCharging: Boolean = false
)
