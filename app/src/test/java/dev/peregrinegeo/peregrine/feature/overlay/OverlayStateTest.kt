package dev.peregrinegeo.peregrine.feature.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStateTest {

    @Test
    fun `edge snap logic accurately attaches bubble to nearest screen boundary`() {
        val screenWidth = 1080
        val bubbleWidth = 120

        // Position on left half of screen (x = 200 < 540)
        val stateLeft = OverlayState(positionX = 200, positionY = 400).snapToEdge(screenWidth, bubbleWidth)
        assertEquals(DockedEdge.LEFT, stateLeft.dockedEdge)
        assertEquals(0, stateLeft.positionX)

        // Position on right half of screen (x = 800 > 540)
        val stateRight = OverlayState(positionX = 800, positionY = 400).snapToEdge(screenWidth, bubbleWidth)
        assertEquals(DockedEdge.RIGHT, stateRight.dockedEdge)
        assertEquals(960, stateRight.positionX) // 1080 - 120 = 960
    }

    @Test
    fun `opacity clamping enforces 20 to 100 percent boundary`() {
        val state = OverlayState()
        assertEquals(0.90f, state.opacity, 0.001f)

        // Clamping below 20%
        val low = state.setClampedOpacity(0.05f)
        assertEquals(0.20f, low.opacity, 0.001f)

        // Clamping above 100%
        val high = state.setClampedOpacity(1.50f)
        assertEquals(1.00f, high.opacity, 0.001f)
    }

    @Test
    fun `permission manager creates matching android settings intent contract`() {
        var isGranted = false
        val manager = OverlayPermissionManager(overlayPermissionChecker = { isGranted })

        assertFalse(manager.hasPermission())
        val intentContract = manager.createSettingsIntent("dev.peregrinegeo.peregrine")
        assertEquals("android.settings.action.MANAGE_OVERLAY_PERMISSION", intentContract.action)
        assertEquals("package:dev.peregrinegeo.peregrine", intentContract.dataUriString)

        isGranted = true
        assertTrue(manager.hasPermission())
    }
}
