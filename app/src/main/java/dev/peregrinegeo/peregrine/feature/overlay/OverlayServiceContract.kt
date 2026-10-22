package dev.peregrinegeo.peregrine.feature.overlay

/**
 * Window layout flags and configuration for Android WindowManager overlay integration.
 */
data class OverlayWindowConfig(
    val windowType: Int = 2038, // WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    val flags: Int = 0x00000008 or 0x00000200, // FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS
    val defaultWidthDp: Int = 280,
    val defaultHeightDp: Int = 200,
    val bubbleSizeDp: Int = 56,
    val notificationChannelId: String = "peregrine_overlay_channel",
    val notificationId: Int = 1002
)
