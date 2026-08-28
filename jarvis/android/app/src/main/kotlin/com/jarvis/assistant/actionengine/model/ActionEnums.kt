package com.jarvis.assistant.actionengine.model

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class CapabilityTier {
    SAFE,           // Read-only, no side effects
    DEVICE_CONTROL, // Modifies device state
    AUTOMATION,     // Accessibility-based UI automation
    HIGH_RISK       // Irreversible or sensitive actions
}

enum class ActionType(
    val description: String,
    val riskLevel: RiskLevel,
    val capabilityTier: CapabilityTier,
    val requiresConfirmation: Boolean = false,
    val requiresPermission: String? = null
) {
    // App Lifecycle
    OPEN_APP("Open application by package name or alias", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    CLOSE_APP("Close application (back press or force stop)", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    LAUNCH_INTENT("Open URL, deep link, or Intent", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),

    // UI Interaction via Accessibility
    CLICK_ELEMENT("Click on UI element found by text or ID", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    LONG_CLICK_ELEMENT("Long press on UI element", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    TYPE_TEXT("Type text into focused input field", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    SWIPE("Perform swipe gesture (up/down/left/right)", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    SCROLL("Scroll up/down/forward/backward", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    PRESS_BACK("Press back button", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    PRESS_HOME("Press home button", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    PRESS_RECENTS("Press recent apps button", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    SEARCH_TEXT("Find search field, type text, and press search", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    FIND_ELEMENT("Find UI element on screen", RiskLevel.LOW, CapabilityTier.AUTOMATION),

    // Verification & Screen
    WAIT("Wait for specified duration", RiskLevel.LOW, CapabilityTier.SAFE),
    VERIFY_STATE("Verify current state matches expected state", RiskLevel.LOW, CapabilityTier.SAFE),
    READ_SCREEN("Extract visible text/content from screen", RiskLevel.LOW, CapabilityTier.SAFE),
    READ_SCREEN_AND_REPLY("Read screen and suggest replies", RiskLevel.LOW, CapabilityTier.SAFE),
    READ_NOTIFICATIONS("Read notification list", RiskLevel.LOW, CapabilityTier.SAFE),
    READ_MESSAGES("Read messages and notifications", RiskLevel.LOW, CapabilityTier.SAFE),
    READ_SMS_INBOX("Read SMS inbox", RiskLevel.LOW, CapabilityTier.SAFE, requiresPermission = "android.permission.READ_SMS"),
    READ_WHATSAPP_UNREAD("Read WhatsApp notifications", RiskLevel.LOW, CapabilityTier.SAFE),

    // Media & System
    PLAY_MEDIA("Trigger media playback", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    TAKE_SELFIE("Open the front camera and capture a selfie", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    PAUSE_MEDIA("Pause media playback", RiskLevel.LOW, CapabilityTier.AUTOMATION),
    NEXT_MEDIA("Skip to next track", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    PREV_MEDIA("Play previous track", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    STOP_MEDIA("Stop media playback", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    VOLUME_SET("Set system volume percentage", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    VOLUME_UP("Increase volume", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    VOLUME_DOWN("Decrease volume", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    BRIGHTNESS_SET("Set screen brightness percentage", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_BRIGHTNESS("Set screen brightness percentage", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    BRIGHTNESS_UP("Increase brightness", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    BRIGHTNESS_DOWN("Decrease brightness", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    TOGGLE_TORCH("Toggle flashlight", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    TOGGLE_WIFI("Toggle Wi-Fi", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    TOGGLE_BLUETOOTH("Toggle Bluetooth", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    TOGGLE_ROTATION("Toggle screen rotation lock", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_RINGER("Set ringer mode (silent/vibrate/normal)", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    TOGGLE_DND("Toggle Do Not Disturb", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_SCREENSHOT("Capture screenshot", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),

    // Communication
    SEND_MESSAGE("Send message via WhatsApp or SMS", RiskLevel.MEDIUM, CapabilityTier.HIGH_RISK, requiresConfirmation = true),
    SEND_WHATSAPP_VOICENOTE("Send WhatsApp voice note", RiskLevel.MEDIUM, CapabilityTier.HIGH_RISK, requiresConfirmation = true),
    READ_CALL_LOG("Read call history", RiskLevel.LOW, CapabilityTier.SAFE, requiresPermission = "android.permission.READ_CALL_LOG"),
    RESOLVE_CONTACT("Resolve contact phone number", RiskLevel.LOW, CapabilityTier.SAFE, requiresPermission = "android.permission.READ_CONTACTS"),
    MAKE_CALL("Initiate phone call", RiskLevel.HIGH, CapabilityTier.HIGH_RISK, requiresConfirmation = true, requiresPermission = "android.permission.CALL_PHONE"),
    MAKE_SPEAKER_CALL("Initiate phone call on speaker", RiskLevel.HIGH, CapabilityTier.HIGH_RISK, requiresConfirmation = true, requiresPermission = "android.permission.CALL_PHONE"),

    // Reminders & Location
    SET_ALARM("Set alarm for specified time", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_REMINDER("Set a reminder (time or location based)", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_TIMER("Set countdown timer", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    READ_CALENDAR("Read upcoming calendar events", RiskLevel.LOW, CapabilityTier.SAFE, requiresPermission = "android.permission.READ_CALENDAR"),
    GET_LOCATION("Get current location", RiskLevel.LOW, CapabilityTier.SAFE, requiresPermission = "android.permission.ACCESS_FINE_LOCATION"),
    GET_WEATHER("Get weather for current location", RiskLevel.LOW, CapabilityTier.SAFE),
    NAVIGATE_TO("Open navigation to a place", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    SET_GEOFENCE("Set location-based reminder/geofence", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL, requiresPermission = "android.permission.ACCESS_FINE_LOCATION"),

    // Translation & AI
    LIVE_TRANSLATE("Translate text to target language", RiskLevel.LOW, CapabilityTier.SAFE),
    GET_DAILY_BRIEFING("Get morning briefing (time, weather, news, battery)", RiskLevel.LOW, CapabilityTier.SAFE),
    EXPORT_LOGS("Export logs to external storage", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    CLEAR_MEMORY("Clear all cached memories", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),

    // System Settings & Control
    CHECK_PERMISSION("Check if permission is granted", RiskLevel.LOW, CapabilityTier.SAFE),
    REQUEST_PERMISSION("Request permission from user", RiskLevel.LOW, CapabilityTier.SAFE),
    OPEN_SETTINGS("Open specific settings screen", RiskLevel.LOW, CapabilityTier.DEVICE_CONTROL),
    CONFIRM_ACTION("Prompt user for confirmation", RiskLevel.LOW, CapabilityTier.SAFE),
    CANCEL_TASK("Cancel current task execution", RiskLevel.LOW, CapabilityTier.SAFE)
}

/**
 * Capability Policy checker for Android side.
 */
object CapabilityPolicy {
    fun checkCapability(
        action: ActionType,
        accessibilityEnabled: Boolean = true,
        userConsent: Boolean = false
    ): CapabilityDecision {
        return when (action.capabilityTier) {
            CapabilityTier.SAFE -> CapabilityDecision.ALLOW
            CapabilityTier.DEVICE_CONTROL -> {
                if (userConsent) CapabilityDecision.ALLOW
                else CapabilityDecision.ALLOW  // Device control is auto-execute with consent
            }
            CapabilityTier.AUTOMATION -> {
                if (accessibilityEnabled) CapabilityDecision.ALLOW
                else CapabilityDecision.REJECT
            }
            CapabilityTier.HIGH_RISK -> CapabilityDecision.CONFIRM
        }
    }

    fun isAutoExecutable(action: ActionType): Boolean {
        return checkCapability(action) == CapabilityDecision.ALLOW
    }

    fun requiresConfirmation(action: ActionType): Boolean {
        return checkCapability(action) == CapabilityDecision.CONFIRM
    }

    fun isRejected(action: ActionType): Boolean {
        return checkCapability(action) == CapabilityDecision.REJECT
    }
}

enum class CapabilityDecision {
    ALLOW,
    CONFIRM,
    REJECT
}
