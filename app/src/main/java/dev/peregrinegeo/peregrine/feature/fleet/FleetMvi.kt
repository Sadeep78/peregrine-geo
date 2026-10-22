package dev.peregrinegeo.peregrine.feature.fleet

import dev.peregrinegeo.peregrine.core.location.MockTarget
import dev.peregrinegeo.peregrine.core.network.CommandPayload
import dev.peregrinegeo.peregrine.core.network.ConflictResolution
import dev.peregrinegeo.peregrine.core.network.DeviceCredentials
import dev.peregrinegeo.peregrine.core.network.FleetConnectionState

/**
 * MVI UI State for the Fleet console pairing and remote control management screen.
 */
data class FleetUiState(
    val isPaired: Boolean = false,
    val credentials: DeviceCredentials? = null,
    val pairingCodeInput: String = "",
    val isPairingInProgress: Boolean = false,
    val pairingError: String? = null,
    val remoteControlEnabled: Boolean = false,
    val connectionState: FleetConnectionState = FleetConnectionState.Disconnected,
    val queuedTelemetryPoints: Int = 0,
    val droppedTelemetryPoints: Long = 0,
    val activeConflict: ActiveConflictState? = null
) {
    /**
     * Whether the high-contrast "REMOTE CONTROL ACTIVE" warning banner should be displayed.
     */
    val showRemoteActiveBanner: Boolean
        get() = isPaired && remoteControlEnabled && connectionState is FleetConnectionState.Connected
}

data class ActiveConflictState(
    val command: CommandPayload,
    val localTarget: MockTarget,
    val remoteTargetName: String
)

/**
 * User intents for the Fleet feature.
 */
sealed class FleetIntent {
    data class UpdatePairingCodeInput(val code: String) : FleetIntent()
    object SubmitPairingCode : FleetIntent()
    data class PairingCompleted(val credentials: DeviceCredentials) : FleetIntent()
    data class PairingFailed(val error: String) : FleetIntent()
    data class ToggleRemoteConsent(val enabled: Boolean) : FleetIntent()
    object Unpair : FleetIntent()
    object Connect : FleetIntent()
    object Disconnect : FleetIntent()
    data class UpdateConnectionState(val state: FleetConnectionState) : FleetIntent()
    data class UpdateQueueMetrics(val queued: Int, val dropped: Long) : FleetIntent()
    data class RaiseConflict(val command: CommandPayload, val localTarget: MockTarget) : FleetIntent()
    data class ResolveConflict(val resolution: ConflictResolution) : FleetIntent()
    object DismissConflictDialog : FleetIntent()
}

/**
 * Side effects triggered by the Fleet ViewModel.
 */
sealed class FleetEffect {
    data class ShowToast(val message: String) : FleetEffect()
    data class RequestConflictResolution(val conflict: ActiveConflictState) : FleetEffect()
    object NavigateToMap : FleetEffect()
}

/**
 * Pure ViewModel managing Fleet pairing and remote control MVI unidirectional flow.
 */
class FleetViewModel(
    initialState: FleetUiState = FleetUiState()
) {
    var state: FleetUiState = initialState
        private set

    private val listeners = mutableListOf<(FleetUiState) -> Unit>()
    private val effectListeners = mutableListOf<(FleetEffect) -> Unit>()

    fun addStateListener(listener: (FleetUiState) -> Unit) {
        listeners.add(listener)
        listener(state)
    }

    fun removeStateListener(listener: (FleetUiState) -> Unit) {
        listeners.remove(listener)
    }

    fun addEffectListener(listener: (FleetEffect) -> Unit) {
        effectListeners.add(listener)
    }

    fun removeEffectListener(listener: (FleetEffect) -> Unit) {
        effectListeners.remove(listener)
    }

    private fun setState(newState: FleetUiState) {
        state = newState
        for (l in listeners) {
            l(newState)
        }
    }

    private fun emitEffect(effect: FleetEffect) {
        for (l in effectListeners) {
            l(effect)
        }
    }

    fun handleIntent(intent: FleetIntent) {
        when (intent) {
            is FleetIntent.UpdatePairingCodeInput -> {
                // Sanitize to uppercase alphanumeric, max 8 chars
                val clean = intent.code.uppercase().filter { it.isLetterOrDigit() }.take(8)
                setState(state.copy(pairingCodeInput = clean, pairingError = null))
            }

            is FleetIntent.SubmitPairingCode -> {
                if (state.pairingCodeInput.length != 8) {
                    setState(state.copy(pairingError = "Pairing code must be exactly 8 characters"))
                    return
                }
                setState(state.copy(isPairingInProgress = true, pairingError = null))
            }

            is FleetIntent.PairingCompleted -> {
                setState(
                    state.copy(
                        isPaired = true,
                        credentials = intent.credentials,
                        isPairingInProgress = false,
                        pairingError = null,
                        connectionState = FleetConnectionState.Connecting
                    )
                )
                emitEffect(FleetEffect.ShowToast("Device paired with fleet successfully"))
            }

            is FleetIntent.PairingFailed -> {
                setState(
                    state.copy(
                        isPairingInProgress = false,
                        pairingError = intent.error
                    )
                )
                emitEffect(FleetEffect.ShowToast("Pairing failed: ${intent.error}"))
            }

            is FleetIntent.ToggleRemoteConsent -> {
                setState(state.copy(remoteControlEnabled = intent.enabled))
                val message = if (intent.enabled) {
                    "Remote fleet control enabled. A banner will be shown on the map."
                } else {
                    "Remote fleet control disabled. Remote commands will be rejected."
                }
                emitEffect(FleetEffect.ShowToast(message))
            }

            is FleetIntent.Unpair -> {
                setState(
                    FleetUiState(
                        isPaired = false,
                        credentials = null,
                        remoteControlEnabled = false,
                        connectionState = FleetConnectionState.Disconnected
                    )
                )
                emitEffect(FleetEffect.ShowToast("Device unpaired from fleet"))
            }

            is FleetIntent.Connect -> {
                if (state.isPaired) {
                    setState(state.copy(connectionState = FleetConnectionState.Connecting))
                }
            }

            is FleetIntent.Disconnect -> {
                setState(state.copy(connectionState = FleetConnectionState.Disconnected))
            }

            is FleetIntent.UpdateConnectionState -> {
                setState(state.copy(connectionState = intent.state))
            }

            is FleetIntent.UpdateQueueMetrics -> {
                setState(
                    state.copy(
                        queuedTelemetryPoints = intent.queued,
                        droppedTelemetryPoints = intent.dropped
                    )
                )
            }

            is FleetIntent.RaiseConflict -> {
                val conflict = ActiveConflictState(
                    command = intent.command,
                    localTarget = intent.localTarget,
                    remoteTargetName = (intent.command.params["routeName"] as? String) ?: "Remote Route"
                )
                setState(state.copy(activeConflict = conflict))
                emitEffect(FleetEffect.RequestConflictResolution(conflict))
            }

            is FleetIntent.ResolveConflict -> {
                setState(state.copy(activeConflict = null))
                val msg = when (intent.resolution) {
                    ConflictResolution.KEEP_MINE -> "Kept local mock. Remote command rejected."
                    ConflictResolution.KEEP_THEIRS -> "Replaced active mock with remote route."
                    ConflictResolution.KEEP_BOTH -> "Saved local route to Library and applied remote route."
                }
                emitEffect(FleetEffect.ShowToast(msg))
            }

            is FleetIntent.DismissConflictDialog -> {
                setState(state.copy(activeConflict = null))
            }
        }
    }
}
