package dev.peregrinegeo.peregrine.core.network

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.MockConfig
import dev.peregrinegeo.peregrine.core.location.MockState
import dev.peregrinegeo.peregrine.core.location.MockStateMachine
import dev.peregrinegeo.peregrine.core.location.MockTarget

/**
 * Handles incoming remote fleet commands, strictly honoring user remote consent,
 * TTL expiration, and local active mock conflict resolution.
 */
class CommandExecutor(
    val mockStateMachine: MockStateMachine,
    var remoteControlEnabled: Boolean = false
) {
    /**
     * Callback invoked when an incoming remote route/mock command conflicts with
     * an active local mock running on the device.
     */
    var conflictResolver: ((command: CommandPayload, currentTarget: MockTarget) -> ConflictResolution)? = null

    /**
     * Callback to save a displaced active route to the user's Library when resolving with KEEP_BOTH.
     */
    var onSaveRouteToLibrary: ((MockTarget.Route) -> Unit)? = null

    fun execute(command: CommandPayload, deviceId: String): CommandAckPayload {
        val now = System.currentTimeMillis()

        // 1. Enforce Remote Control Consent (US-09: Opt-in, visible, revocable)
        if (!remoteControlEnabled) {
            return CommandAckPayload(
                commandId = command.commandId,
                deviceId = deviceId,
                status = "rejected",
                executedAt = now,
                error = "REMOTE_CONTROL_DISABLED"
            )
        }

        // 2. Enforce Command TTL
        if (command.ttlMs > 0 && (now - command.issuedAt) > command.ttlMs) {
            return CommandAckPayload(
                commandId = command.commandId,
                deviceId = deviceId,
                status = "rejected",
                executedAt = now,
                error = "COMMAND_EXPIRED"
            )
        }

        // 3. Dispatch command
        return try {
            when (command.name) {
                "start_mock", "load_route" -> handleStartOrLoad(command, deviceId, now)
                "pause_mock" -> {
                    mockStateMachine.pause()
                    CommandAckPayload(command.commandId, deviceId, "acked", now)
                }
                "resume_mock" -> {
                    mockStateMachine.resume()
                    CommandAckPayload(command.commandId, deviceId, "acked", now)
                }
                "stop_mock" -> {
                    mockStateMachine.stop()
                    CommandAckPayload(command.commandId, deviceId, "acked", now)
                }
                "set_speed" -> {
                    val multiplier = (command.params["speedMultiplier"] as? Number)?.toDouble() ?: 1.0
                    mockStateMachine.setSpeedMultiplier(multiplier)
                    CommandAckPayload(command.commandId, deviceId, "acked", now)
                }
                "set_location" -> {
                    val lat = (command.params["lat"] as? Number)?.toDouble()
                    val lng = (command.params["lng"] as? Number)?.toDouble()
                    if (lat == null || lng == null) {
                        CommandAckPayload(command.commandId, deviceId, "failed", now, "MISSING_COORDINATES")
                    } else {
                        val target = MockTarget.SinglePoint(LatLng(lat, lng))
                        mockStateMachine.selectPreview(target)
                        mockStateMachine.startMock()
                        CommandAckPayload(command.commandId, deviceId, "acked", now)
                    }
                }
                else -> {
                    CommandAckPayload(
                        commandId = command.commandId,
                        deviceId = deviceId,
                        status = "failed",
                        executedAt = now,
                        error = "UNKNOWN_COMMAND: ${command.name}"
                    )
                }
            }
        } catch (e: Exception) {
            CommandAckPayload(
                commandId = command.commandId,
                deviceId = deviceId,
                status = "failed",
                executedAt = now,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun handleStartOrLoad(command: CommandPayload, deviceId: String, now: Long): CommandAckPayload {
        val currentState = mockStateMachine.state

        // Check Conflict if active mock is currently running
        if (currentState is MockState.Active) {
            val resolver = conflictResolver
            val resolution = if (resolver != null) {
                resolver(command, currentState.target)
            } else {
                ConflictResolution.KEEP_MINE
            }

            when (resolution) {
                ConflictResolution.KEEP_MINE -> {
                    return CommandAckPayload(
                        commandId = command.commandId,
                        deviceId = deviceId,
                        status = "rejected",
                        executedAt = now,
                        error = "REJECTED_BY_DEVICE_USER"
                    )
                }
                ConflictResolution.KEEP_BOTH -> {
                    if (currentState.target is MockTarget.Route) {
                        onSaveRouteToLibrary?.invoke(currentState.target)
                    }
                    // Proceed to apply remote target
                }
                ConflictResolution.KEEP_THEIRS -> {
                    // Proceed to apply remote target
                }
            }
        }

        // Construct target from command params
        val target = parseTargetFromParams(command.params)
        val speedMultiplier = (command.params["speedMultiplier"] as? Number)?.toDouble() ?: 1.0
        val profileName = (command.params["profile"] as? String) ?: "drive"
        val config = MockConfig(profileName = profileName, speedMultiplier = speedMultiplier)

        mockStateMachine.stop()
        mockStateMachine.selectPreview(target, config)
        if (command.name == "start_mock") {
            mockStateMachine.startMock()
        }

        return CommandAckPayload(command.commandId, deviceId, "acked", now)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTargetFromParams(params: Map<String, Any?>): MockTarget {
        val waypointsRaw = params["waypoints"] as? List<Map<String, Any?>>
        if (waypointsRaw != null && waypointsRaw.isNotEmpty()) {
            val waypoints = waypointsRaw.mapNotNull { pt ->
                val lat = (pt["lat"] as? Number)?.toDouble()
                val lng = (pt["lng"] as? Number)?.toDouble()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            }
            if (waypoints.isNotEmpty()) {
                val routeName = (params["routeName"] as? String) ?: "Remote Route"
                val routeId = (params["routeId"] as? String) ?: "route-remote"
                return MockTarget.Route(routeId = routeId, name = routeName, waypoints = waypoints)
            }
        }

        val lat = (params["lat"] as? Number)?.toDouble() ?: 37.7749
        val lng = (params["lng"] as? Number)?.toDouble() ?: -122.4194
        return MockTarget.SinglePoint(LatLng(lat, lng), (params["pointName"] as? String) ?: "Remote Point")
    }
}
