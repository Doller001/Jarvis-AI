package com.jarvis.assistant.ui.permissions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every permission the Jarvis app declares in the AndroidManifest, with the data
 * the onboarding UI needs to drive a real grant flow.
 *
 * - [required] = needed before the assistant can go live.
 * - [grant] describes how the permission is obtained:
 *      RUNTIME   -> ActivityResultContracts.RequestMultiplePermissions
 *      SETTINGS  -> an Intent into system Settings (accessibility / battery)
 */
enum class GrantKind { RUNTIME, SETTINGS }

data class JarvisPermission(
    val id: String,
    val androidPermission: String?,   // null for settings-only grants
    val title: String,
    val description: String,
    val icon: ImageVector,
    val required: Boolean,
    val grant: GrantKind,
    val settingsAction: String? = null // Settings action for SETTINGS-kind grants
)

/** Full catalog mirroring the manifest declarations. */
object AllPermissions {

    val list: List<JarvisPermission> = listOf(
        JarvisPermission(
            id = "microphone",
            androidPermission = android.Manifest.permission.RECORD_AUDIO,
            title = "Microphone",
            description = "Capture voice for wake-word detection and speech commands.",
            icon = Icons.Filled.Mic,
            required = true,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "notifications",
            androidPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.POST_NOTIFICATIONS else null,
            title = "Notifications",
            description = "Show the always-ready foreground service status and alerts.",
            icon = Icons.Filled.Notifications,
            required = true,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "call_phone",
            androidPermission = android.Manifest.permission.CALL_PHONE,
            title = "Phone / Calls",
            description = "Lets Jarvis place calls to your contacts by voice.",
            icon = Icons.Filled.Call,
            required = true,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "contacts",
            androidPermission = android.Manifest.permission.READ_CONTACTS,
            title = "Contacts",
            description = "Resolve contact names when you say \"call mom\" or \"message Ali\".",
            icon = Icons.Filled.Contacts,
            required = true,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "sms",
            androidPermission = android.Manifest.permission.SEND_SMS,
            title = "SMS",
            description = "Send text messages hands-free (\"send SMS to Ali\").",
            icon = Icons.Filled.Sms,
            required = true,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "camera",
            androidPermission = android.Manifest.permission.CAMERA,
            title = "Camera",
            description = "Capture photos and visual context on request (optional).",
            icon = Icons.Filled.CameraAlt,
            required = false,
            grant = GrantKind.RUNTIME
        ),
        JarvisPermission(
            id = "accessibility",
            androidPermission = null,
            title = "Accessibility Service",
            description = "Automate taps, scrolls and screen reading for full device control.",
            icon = Icons.Filled.TouchApp,
            required = true,
            grant = GrantKind.SETTINGS,
            settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
        ),
        JarvisPermission(
            id = "battery",
            androidPermission = null,
            title = "Ignore Battery Optimization",
            description = "Keep the always-ready voice runtime running in the background.",
            icon = Icons.Filled.BatteryChargingFull,
            required = true,
            grant = GrantKind.SETTINGS,
            settingsAction = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        )
    )

    val requiredIds = list.filter { it.required }.map { it.id }
}
