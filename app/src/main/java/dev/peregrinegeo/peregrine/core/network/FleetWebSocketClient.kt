package dev.peregrinegeo.peregrine.core.network

/**
 * Connection states for the Fleet WebSocket client.
 */
sealed class FleetConnectionState {
    object Disconnected : FleetConnectionState()
    object Connecting : FleetConnectionState()
    data class Connected(val sessionStartTime: Long = System.currentTimeMillis()) : FleetConnectionState()
    data class Reconnecting(val attempt: Int, val nextRetryDelayMs: Long) : FleetConnectionState()
}

/**
 * Abstract WebSocket transport layer allowing clean-room testing and OkHttp production binding.
 */
interface WebSocketTransport {
    fun connect(url: String, listener: TransportListener)
    fun send(text: String): Boolean
    fun close(code: Int = 1000, reason: String = "Normal closure")
}

interface TransportListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onClosed(code: Int, reason: String)
    fun onFailure(throwable: Throwable)
}

/**
 * Fleet WebSocket client managing exponential backoff reconnection,
 * offline queue draining, heartbeat keepalive, and command execution.
 */
class FleetWebSocketClient(
    val credentials: DeviceCredentials,
    val transport: WebSocketTransport,
    val queue: OfflineTelemetryQueue = OfflineTelemetryQueue(),
    val commandExecutor: CommandExecutor,
    val backoff: ExponentialBackoff = ExponentialBackoff(),
    val jsonSerializer: (Any) -> String = { it.toString() }
) {
    var state: FleetConnectionState = FleetConnectionState.Disconnected
        private set

    var onStateChanged: ((FleetConnectionState) -> Unit)? = null
    var onHeartbeatSent: (() -> Unit)? = null

    private var batchSequence: Long = 1

    private val transportListener = object : TransportListener {
        override fun onOpen() {
            backoff.reset()
            updateState(FleetConnectionState.Connected())
            sendHello()
            drainOfflineQueue()
        }

        override fun onMessage(text: String) {
            handleIncomingMessage(text)
        }

        override fun onClosed(code: Int, reason: String) {
            scheduleReconnect()
        }

        override fun onFailure(throwable: Throwable) {
            scheduleReconnect()
        }
    }

    fun connect() {
        if (state is FleetConnectionState.Connected || state is FleetConnectionState.Connecting) {
            return
        }
        updateState(FleetConnectionState.Connecting)
        transport.connect(credentials.serverWsUrl, transportListener)
    }

    fun disconnect() {
        backoff.reset()
        updateState(FleetConnectionState.Disconnected)
        transport.close()
    }

    fun scheduleReconnect(): Long {
        val delay = backoff.nextDelayMs()
        val attempt = backoff.attempt
        updateState(FleetConnectionState.Reconnecting(attempt, delay))
        return delay
    }

    fun sendTelemetryPoint(point: TelemetryPoint) {
        if (state is FleetConnectionState.Connected) {
            // Send batch immediately with any previous queued points up to 50
            val batch = queue.drain(49).toMutableList()
            batch.add(point)
            val dropped = queue.resetDroppedCount()
            val payload = TelemetryBatchPayload(
                deviceId = credentials.deviceId,
                seq = batchSequence++,
                points = batch,
                droppedCount = dropped
            )
            val envelope = mapOf(
                "v" to 1,
                "id" to "batch-${batchSequence}",
                "type" to "telemetry.batch",
                "ts" to System.currentTimeMillis(),
                "payload" to payload
            )
            val success = transport.send(jsonSerializer(envelope))
            if (!success) {
                // If send failed, buffer points into offline queue
                for (pt in batch) {
                    queue.enqueue(pt)
                }
            }
        } else {
            queue.enqueue(point)
        }
    }

    fun sendHeartbeat(batteryPct: Int = 85, charging: Boolean = false, mockActive: Boolean = false) {
        if (state !is FleetConnectionState.Connected) return
        val envelope = mapOf(
            "v" to 1,
            "id" to "hb-${System.currentTimeMillis()}",
            "type" to "heartbeat",
            "ts" to System.currentTimeMillis(),
            "payload" to mapOf(
                "deviceId" to credentials.deviceId,
                "batteryPct" to batteryPct,
                "charging" to charging,
                "mockActive" to mockActive
            )
        )
        transport.send(jsonSerializer(envelope))
        onHeartbeatSent?.invoke()
    }

    fun drainOfflineQueue(): Int {
        var totalDrained = 0
        while (!queue.isEmpty && state is FleetConnectionState.Connected) {
            val batch = queue.drain(50)
            if (batch.isEmpty()) break
            val dropped = queue.resetDroppedCount()
            val payload = TelemetryBatchPayload(
                deviceId = credentials.deviceId,
                seq = batchSequence++,
                points = batch,
                droppedCount = dropped
            )
            val envelope = mapOf(
                "v" to 1,
                "id" to "batch-drain-${batchSequence}",
                "type" to "telemetry.batch",
                "ts" to System.currentTimeMillis(),
                "payload" to payload
            )
            val sent = transport.send(jsonSerializer(envelope))
            if (!sent) {
                // Re-enqueue if send failed
                for (pt in batch) {
                    queue.enqueue(pt)
                }
                break
            }
            totalDrained += batch.size
        }
        return totalDrained
    }

    private fun sendHello() {
        val helloMsg = mapOf(
            "v" to 1,
            "id" to "hello-${credentials.deviceId}",
            "type" to "hello",
            "ts" to System.currentTimeMillis(),
            "payload" to mapOf(
                "deviceId" to credentials.deviceId,
                "model" to "Android Peregrine Client",
                "osVersion" to "Android 15",
                "appVersion" to "0.1.0",
                "capabilities" to listOf("mock_location", "overlay"),
                "remoteStandby" to true
            )
        )
        transport.send(jsonSerializer(helloMsg))
    }

    fun handleIncomingCommand(command: CommandPayload) {
        val ack = commandExecutor.execute(command, credentials.deviceId)
        val ackEnvelope = mapOf(
            "v" to 1,
            "id" to "ack-${command.commandId}",
            "type" to "command.ack",
            "ts" to System.currentTimeMillis(),
            "payload" to ack
        )
        transport.send(jsonSerializer(ackEnvelope))
    }

    private fun handleIncomingMessage(text: String) {
        // In full integration this delegates to JSON parser, mapping "command" to handleIncomingCommand
    }

    private fun updateState(newState: FleetConnectionState) {
        state = newState
        onStateChanged?.invoke(newState)
    }
}
