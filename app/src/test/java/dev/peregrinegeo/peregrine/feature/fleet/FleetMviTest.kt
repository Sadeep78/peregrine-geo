package dev.peregrinegeo.peregrine.feature.fleet

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.MockTarget
import dev.peregrinegeo.peregrine.core.network.CommandPayload
import dev.peregrinegeo.peregrine.core.network.ConflictResolution
import dev.peregrinegeo.peregrine.core.network.DeviceCredentials
import dev.peregrinegeo.peregrine.core.network.FleetConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FleetMviTest {

    private lateinit var viewModel: FleetViewModel

    @Before
    fun setUp() {
        viewModel = FleetViewModel()
    }

    @Test
    fun `pairing code input is sanitized to uppercase alphanumerics and max 8 chars`() {
        viewModel.handleIntent(FleetIntent.UpdatePairingCodeInput("abc-123!xy99"))
        // Filtered to ABC123XY (8 chars)
        assertEquals("ABC123XY", viewModel.state.pairingCodeInput)
        assertNull(viewModel.state.pairingError)
    }

    @Test
    fun `submitting short pairing code displays validation error`() {
        viewModel.handleIntent(FleetIntent.UpdatePairingCodeInput("ABC"))
        viewModel.handleIntent(FleetIntent.SubmitPairingCode)
        assertNotNull(viewModel.state.pairingError)
        assertFalse(viewModel.state.isPairingInProgress)
    }

    @Test
    fun `successful pairing transitions state and emits toast effect`() {
        var emittedToast: String? = null
        viewModel.addEffectListener { effect ->
            if (effect is FleetEffect.ShowToast) {
                emittedToast = effect.message
            }
        }

        val credentials = DeviceCredentials(
            deviceId = "dev-pixel-01",
            token = "sec-tok-991823",
            organizationId = "org-default"
        )

        viewModel.handleIntent(FleetIntent.PairingCompleted(credentials))
        assertTrue(viewModel.state.isPaired)
        assertEquals(credentials, viewModel.state.credentials)
        assertEquals("Device paired with fleet successfully", emittedToast)
    }

    @Test
    fun `remote consent toggle controls active banner visibility`() {
        viewModel.handleIntent(FleetIntent.PairingCompleted(DeviceCredentials("d1", "tok")))
        viewModel.handleIntent(FleetIntent.UpdateConnectionState(FleetConnectionState.Connected()))

        // Default remote control consent is false
        assertFalse(viewModel.state.remoteControlEnabled)
        assertFalse(viewModel.state.showRemoteActiveBanner)

        // User opts in to remote control
        viewModel.handleIntent(FleetIntent.ToggleRemoteConsent(true))
        assertTrue(viewModel.state.remoteControlEnabled)
        assertTrue(viewModel.state.showRemoteActiveBanner)

        // User revokes remote control
        viewModel.handleIntent(FleetIntent.ToggleRemoteConsent(false))
        assertFalse(viewModel.state.remoteControlEnabled)
        assertFalse(viewModel.state.showRemoteActiveBanner)
    }

    @Test
    fun `conflict dialog lifecycle in MVI flow`() {
        var requestedConflict: ActiveConflictState? = null
        viewModel.addEffectListener { effect ->
            if (effect is FleetEffect.RequestConflictResolution) {
                requestedConflict = effect.conflict
            }
        }

        val cmd = CommandPayload("c-conf", "start_mock", "idem-conf", params = mapOf("routeName" to "Fleet Route Alpha"))
        val localTarget = MockTarget.SinglePoint(LatLng(37.77, -122.41), "Local Waypoint")

        viewModel.handleIntent(FleetIntent.RaiseConflict(cmd, localTarget))
        assertNotNull(viewModel.state.activeConflict)
        assertEquals("Fleet Route Alpha", viewModel.state.activeConflict?.remoteTargetName)
        assertEquals("Fleet Route Alpha", requestedConflict?.remoteTargetName)

        viewModel.handleIntent(FleetIntent.ResolveConflict(ConflictResolution.KEEP_BOTH))
        assertNull(viewModel.state.activeConflict)
    }

    @Test
    fun `unpairing resets credentials and remote control state`() {
        viewModel.handleIntent(FleetIntent.PairingCompleted(DeviceCredentials("d1", "tok")))
        viewModel.handleIntent(FleetIntent.ToggleRemoteConsent(true))
        assertTrue(viewModel.state.isPaired)

        viewModel.handleIntent(FleetIntent.Unpair)
        assertFalse(viewModel.state.isPaired)
        assertNull(viewModel.state.credentials)
        assertFalse(viewModel.state.remoteControlEnabled)
    }
}
