package dev.peregrinegeo.peregrine.feature.overlay

/**
 * Contract specifying intent parameters for Android SYSTEM_ALERT_WINDOW settings.
 */
data class OverlayIntentContract(
    val action: String = "android.settings.action.MANAGE_OVERLAY_PERMISSION",
    val dataUriString: String = "package:dev.peregrinegeo.peregrine"
)

/**
 * Pure manager for overlay permission verification and intent construction.
 */
class OverlayPermissionManager(
    private val overlayPermissionChecker: () -> Boolean = { false }
) {
    /**
     * Checks if SYSTEM_ALERT_WINDOW is currently granted.
     */
    fun hasPermission(): Boolean = overlayPermissionChecker()

    /**
     * Creates intent contract to launch the Android system permission screen.
     */
    fun createSettingsIntent(packageName: String = "dev.peregrinegeo.peregrine"): OverlayIntentContract {
        return OverlayIntentContract(
            action = "android.settings.action.MANAGE_OVERLAY_PERMISSION",
            dataUriString = "package:$packageName"
        )
    }
}
