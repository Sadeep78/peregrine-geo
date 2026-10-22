package dev.peregrinegeo.peregrine.core.network

/**
 * Single telemetry fix sample formatted according to the Peregrine protocol.
 */
data class TelemetryPoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double = 0.0,
    val speed: Double = 0.0,
    val bearing: Double = 0.0,
    val accuracy: Float = 2.0f,
    val ts: Long = System.currentTimeMillis(),
    val verticalAccuracy: Float? = null,
    val speedAccuracy: Float? = null,
    val bearingAccuracy: Float? = null,
    val zoneId: String? = null
)

/**
 * Outgoing telemetry batch payload.
 */
data class TelemetryBatchPayload(
    val deviceId: String,
    val seq: Long,
    val points: List<TelemetryPoint>,
    val droppedCount: Long = 0
)

/**
 * Incoming remote command payload.
 */
data class CommandPayload(
    val commandId: String,
    val name: String,
    val idempotencyKey: String,
    val ttlMs: Long = 60000L,
    val issuedAt: Long = System.currentTimeMillis(),
    val params: Map<String, Any?> = emptyMap()
)

/**
 * Command execution acknowledgment sent back to backend.
 */
data class CommandAckPayload(
    val commandId: String,
    val deviceId: String,
    val status: String, // "acked", "rejected", "failed"
    val executedAt: Long = System.currentTimeMillis(),
    val error: String? = null
)

/**
 * Actions available to resolve conflicts when a remote route command arrives while a local mock is running.
 */
enum class ConflictResolution {
    KEEP_MINE,
    KEEP_THEIRS,
    KEEP_BOTH
}

/**
 * Pairing credentials stored persistently on device.
 */
data class DeviceCredentials(
    val deviceId: String,
    val token: String,
    val organizationId: String = "org-default",
    val serverWsUrl: String = "ws://localhost:4000/ws/device"
)
