package dev.peregrinegeo.peregrine.core.network

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.MockConfig
import dev.peregrinegeo.peregrine.core.location.MockState
import dev.peregrinegeo.peregrine.core.location.MockStateMachine
import dev.peregrinegeo.peregrine.core.location.MockTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandExecutorTest {

    private lateinit var stateMachine: MockStateMachine
    private lateinit var executor: CommandExecutor

    @Before
    fun setUp() {
        stateMachine = MockStateMachine()
        executor = CommandExecutor(stateMachine, remoteControlEnabled = false)
    }

    @Test
    fun `commands are rejected when remoteControlEnabled is false`() {
        executor.remoteControlEnabled = false
        val cmd = CommandPayload(
            commandId = "cmd-001",
            name = "start_mock",
            idempotencyKey = "idem-1",
            params = mapOf("lat" to 37.7749, "lng" to -122.4194)
        )

        val ack = executor.execute(cmd, "dev-test")
        assertEquals("rejected", ack.status)
        assertEquals("REMOTE_CONTROL_DISABLED", ack.error)
        assertTrue(stateMachine.state is MockState.Idle)
    }

    @Test
    fun `commands are rejected when TTL has expired`() {
        executor.remoteControlEnabled = true
        val cmd = CommandPayload(
            commandId = "cmd-expired",
            name = "pause_mock",
            idempotencyKey = "idem-2",
            ttlMs = 1000L,
            issuedAt = System.currentTimeMillis() - 5000L
        )

        val ack = executor.execute(cmd, "dev-test")
        assertEquals("rejected", ack.status)
        assertEquals("COMMAND_EXPIRED", ack.error)
    }

    @Test
    fun `start_mock command successfully activates state machine`() {
        executor.remoteControlEnabled = true
        val cmd = CommandPayload(
            commandId = "cmd-start",
            name = "start_mock",
            idempotencyKey = "idem-3",
            params = mapOf(
                "lat" to 37.7749,
                "lng" to -122.4194,
                "profile" to "bicycle",
                "speedMultiplier" to 1.5
            )
        )

        val ack = executor.execute(cmd, "dev-test")
        assertEquals("acked", ack.status)
        assertTrue(stateMachine.state is MockState.Active)
        val active = stateMachine.state as MockState.Active
        assertTrue(active.isPlaying)
        assertEquals("bicycle", active.config.profileName)
        assertEquals(1.5, active.config.speedMultiplier, 0.001)
    }

    @Test
    fun `pause resume and set_speed lifecycle`() {
        executor.remoteControlEnabled = true
        // Start first
        executor.execute(
            CommandPayload("c1", "start_mock", "i1", params = mapOf("lat" to 37.77, "lng" to -122.41)),
            "dev-test"
        )
        assertTrue((stateMachine.state as MockState.Active).isPlaying)

        // Pause
        val pauseAck = executor.execute(CommandPayload("c2", "pause_mock", "i2"), "dev-test")
        assertEquals("acked", pauseAck.status)
        assertFalse((stateMachine.state as MockState.Active).isPlaying)

        // Speed change
        val speedAck = executor.execute(
            CommandPayload("c3", "set_speed", "i3", params = mapOf("speedMultiplier" to 2.5)),
            "dev-test"
        )
        assertEquals("acked", speedAck.status)
        assertEquals(2.5, (stateMachine.state as MockState.Active).config.speedMultiplier, 0.001)

        // Resume
        val resumeAck = executor.execute(CommandPayload("c4", "resume_mock", "i4"), "dev-test")
        assertEquals("acked", resumeAck.status)
        assertTrue((stateMachine.state as MockState.Active).isPlaying)

        // Stop
        val stopAck = executor.execute(CommandPayload("c5", "stop_mock", "i5"), "dev-test")
        assertEquals("acked", stopAck.status)
        assertTrue(stateMachine.state is MockState.Idle)
    }

    @Test
    fun `conflict resolution KEEP_MINE rejects remote command and preserves local mock`() {
        executor.remoteControlEnabled = true
        // Local user starts route
        val localRoute = MockTarget.Route("r-local-1", "Local Active Route", listOf(LatLng(37.77, -122.41), LatLng(37.78, -122.42)))
        stateMachine.selectPreview(localRoute)
        stateMachine.startMock()

        // Configure conflict resolver to keep mine
        executor.conflictResolver = { _, _ -> ConflictResolution.KEEP_MINE }

        val remoteCmd = CommandPayload(
            commandId = "c-remote-1",
            name = "start_mock",
            idempotencyKey = "i-rem-1",
            params = mapOf("lat" to 34.05, "lng" to -118.25, "pointName" to "Remote LA")
        )

        val ack = executor.execute(remoteCmd, "dev-test")
        assertEquals("rejected", ack.status)
        assertEquals("REJECTED_BY_DEVICE_USER", ack.error)

        // Local mock is still active
        val current = stateMachine.state as MockState.Active
        assertEquals("Local Active Route", current.target.displayName)
    }

    @Test
    fun `conflict resolution KEEP_THEIRS overrides local mock with remote route`() {
        executor.remoteControlEnabled = true
        val localRoute = MockTarget.Route("r-local-2", "Local Active Route", listOf(LatLng(37.77, -122.41), LatLng(37.78, -122.42)))
        stateMachine.selectPreview(localRoute)
        stateMachine.startMock()

        executor.conflictResolver = { _, _ -> ConflictResolution.KEEP_THEIRS }

        val remoteCmd = CommandPayload(
            commandId = "c-remote-2",
            name = "start_mock",
            idempotencyKey = "i-rem-2",
            params = mapOf("lat" to 34.05, "lng" to -118.25, "pointName" to "Remote LA")
        )

        val ack = executor.execute(remoteCmd, "dev-test")
        assertEquals("acked", ack.status)

        val current = stateMachine.state as MockState.Active
        assertEquals("Remote LA", current.target.displayName)
    }

    @Test
    fun `conflict resolution KEEP_BOTH saves local route to library and executes remote route`() {
        executor.remoteControlEnabled = true
        val localRoute = MockTarget.Route("r-local-3", "Local Active Route", listOf(LatLng(37.77, -122.41), LatLng(37.78, -122.42)))
        stateMachine.selectPreview(localRoute)
        stateMachine.startMock()

        var savedToLibraryRoute: MockTarget.Route? = null
        executor.onSaveRouteToLibrary = { route -> savedToLibraryRoute = route }
        executor.conflictResolver = { _, _ -> ConflictResolution.KEEP_BOTH }

        val remoteCmd = CommandPayload(
            commandId = "c-remote-3",
            name = "start_mock",
            idempotencyKey = "i-rem-3",
            params = mapOf("lat" to 34.05, "lng" to -118.25, "pointName" to "Remote LA")
        )

        val ack = executor.execute(remoteCmd, "dev-test")
        assertEquals("acked", ack.status)

        // Check local route was safely archived to library
        assertEquals("Local Active Route", savedToLibraryRoute?.displayName)
        // Check new remote target is active
        val current = stateMachine.state as MockState.Active
        assertEquals("Remote LA", current.target.displayName)
    }
}
