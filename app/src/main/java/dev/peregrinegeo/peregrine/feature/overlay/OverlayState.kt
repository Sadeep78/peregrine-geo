package dev.peregrinegeo.peregrine.feature.overlay

/**
 * Display modes for the on-screen overlay HUD.
 */
enum class OverlayMode {
    /** Full Mini-HUD with telemetry + floating joystick */
    EXPANDED,

    /** Floating joystick only without telemetry banner */
    JOYSTICK_ONLY,

    /** Minimized to small floating bubble docked to screen edge */
    COLLAPSED
}

enum class DockedEdge {
    LEFT,
    RIGHT
}

/**
 * Immutable state of the on-screen floating overlay.
 */
data class OverlayState(
    val mode: OverlayMode = OverlayMode.EXPANDED,
    val opacity: Float = 0.90f,
    val joystickMode: JoystickMode = JoystickMode.NORTH_RELATIVE,
    val positionX: Int = 100,
    val positionY: Int = 300,
    val dockedEdge: DockedEdge = DockedEdge.RIGHT,
    val isMockActive: Boolean = false,
    val isPlaying: Boolean = false,
    val currentLat: Double = 37.7749,
    val currentLng: Double = -122.4194,
    val currentSpeedMps: Double = 0.0,
    val currentBearingDeg: Double = 0.0,
    val speedMultiplier: Double = 1.0,
    val activeProfileName: String = "Drive"
) {
    init {
        require(opacity in 0.20f..1.0f) { "Opacity must be clamped between 0.20 (20%) and 1.0 (100%)" }
    }

    /**
     * Computes edge snap destination X coordinate for a given screen width.
     */
    fun snapToEdge(screenWidth: Int, bubbleWidth: Int = 120): OverlayState {
        val midX = screenWidth / 2
        val newEdge = if (positionX + bubbleWidth / 2 < midX) DockedEdge.LEFT else DockedEdge.RIGHT
        val newX = if (newEdge == DockedEdge.LEFT) 0 else (screenWidth - bubbleWidth)
        return copy(positionX = newX, dockedEdge = newEdge)
    }

    fun setClampedOpacity(newOpacity: Float): OverlayState {
        return copy(opacity = newOpacity.coerceIn(0.20f, 1.0f))
    }
}
