package dev.peregrinegeo.peregrine.feature.checklist

enum class OnboardingStepId {
    DEVELOPER_OPTIONS,
    MOCK_LOCATION_APP,
    GOOGLE_LOCATION_ACCURACY,
    NOTIFICATIONS,
    OVERLAY_PERMISSION
}

data class OnboardingStep(
    val id: OnboardingStepId,
    val title: String,
    val description: String,
    val isOptional: Boolean,
    val isCompleted: Boolean,
    val deepLinkAction: String
)

interface OnboardingStatusChecker {
    fun isDeveloperOptionsEnabled(): Boolean
    fun isMockLocationAppSelected(): Boolean
    fun isGoogleLocationAccuracyDisabled(): Boolean
    fun isNotificationPermissionGranted(): Boolean
    fun isOverlayPermissionGranted(): Boolean
}

class OnboardingManager(private val checker: OnboardingStatusChecker) {

    companion object {
        const val ACTION_DEVELOPMENT_SETTINGS = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
        const val ACTION_LOCATION_SETTINGS = "android.settings.LOCATION_SOURCE_SETTINGS"
        const val ACTION_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
        const val ACTION_OVERLAY_SETTINGS = "android.settings.action.MANAGE_OVERLAY_PERMISSION"
    }

    fun getSteps(): List<OnboardingStep> {
        return listOf(
            OnboardingStep(
                id = OnboardingStepId.DEVELOPER_OPTIONS,
                title = "Developer Options Enabled",
                description = "Required to enable system-level mock location capabilities.",
                isOptional = false,
                isCompleted = checker.isDeveloperOptionsEnabled(),
                deepLinkAction = ACTION_DEVELOPMENT_SETTINGS
            ),
            OnboardingStep(
                id = OnboardingStepId.MOCK_LOCATION_APP,
                title = "Peregrine Selected as Mock App",
                description = "Select Peregrine in Developer Options -> Select mock location app.",
                isOptional = false,
                isCompleted = checker.isMockLocationAppSelected(),
                deepLinkAction = ACTION_DEVELOPMENT_SETTINGS
            ),
            OnboardingStep(
                id = OnboardingStepId.GOOGLE_LOCATION_ACCURACY,
                title = "Google Location Accuracy Disabled",
                description = "Turn off Wi-Fi/Cell fusion to prevent Google Play Services from overriding mocks.",
                isOptional = false,
                isCompleted = checker.isGoogleLocationAccuracyDisabled(),
                deepLinkAction = ACTION_LOCATION_SETTINGS
            ),
            OnboardingStep(
                id = OnboardingStepId.NOTIFICATIONS,
                title = "Notification Permission",
                description = "Required to display location mocking foreground service status and controls.",
                isOptional = false,
                isCompleted = checker.isNotificationPermissionGranted(),
                deepLinkAction = ACTION_NOTIFICATION_SETTINGS
            ),
            OnboardingStep(
                id = OnboardingStepId.OVERLAY_PERMISSION,
                title = "Overlay Permission (Optional)",
                description = "Enables floating joystick and on-screen HUD over other apps.",
                isOptional = true,
                isCompleted = checker.isOverlayPermissionGranted(),
                deepLinkAction = ACTION_OVERLAY_SETTINGS
            )
        )
    }

    val isReadyToMock: Boolean
        get() = getSteps().filterNot { it.isOptional }.all { it.isCompleted }
}
