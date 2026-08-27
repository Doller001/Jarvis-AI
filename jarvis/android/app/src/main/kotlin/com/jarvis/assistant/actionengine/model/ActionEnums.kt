package com.jarvis.assistant.actionengine.model

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class ActionType(
    val description: String,
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean = false,
    val requiresPermission: String? = null
) {
    // App Lifecycle
    OPEN_APP("Open application by package name or alias", RiskLevel.LOW),
    CLOSE_APP("Close application (back press or force stop)", RiskLevel.LOW),
    LAUNCH_INTENT("Open URL, deep link, or Intent", RiskLevel.LOW),

    // UI Interaction via Accessibility
    CLICK_ELEMENT("Click on UI element found by text or ID", RiskLevel.LOW),
    LONG_CLICK_ELEMENT("Long press on UI element", RiskLevel.LOW),
    TYPE_TEXT("Type text into focused input field", RiskLevel.LOW),
    SWIPE("Perform swipe gesture (up/down/left/right)", RiskLevel.LOW),
    SCROLL("Scroll up/down/forward/backward", RiskLevel.LOW),
    PRESS_BACK("Press back button", RiskLevel.LOW),
    PRESS_HOME("Press home button", RiskLevel.LOW),
    PRESS_RECENTS("Press recent apps button", RiskLevel.LOW),
    SEARCH_TEXT("Find search field, type text, and press search", RiskLevel.LOW),
    FIND_ELEMENT("Find UI element on screen", RiskLevel.LOW),

    // Verification & Screen
    WAIT("Wait for specified duration", RiskLevel.LOW),
    VERIFY_STATE("Verify current state matches expected state", RiskLevel.LOW),
    READ_SCREEN("Extract visible text/content from screen", RiskLevel.LOW),
    READ_SCREEN_AND_REPLY("Read screen and suggest replies", RiskLevel.LOW),
    READ_NOTIFICATIONS("Read notification list", RiskLevel.LOW),
    READ_SMS_INBOX("Read SMS inbox", RiskLevel.LOW, requiresPermission = "android.permission.READ_SMS"),
    READ_WHATSAPP_UNREAD("Read WhatsApp notifications", RiskLevel.LOW),

    // Media & System
    PLAY_MEDIA("Trigger media playback", RiskLevel.LOW),
    TAKE_SELFIE("Open the front camera and capture a selfie", RiskLevel.LOW),
    PAUSE_MEDIA("Pause media playback", RiskLevel.LOW),
    NEXT_MEDIA("Skip to next track", RiskLevel.LOW),
    PREV_MEDIA("Play previous track", RiskLevel.LOW),
    STOP_MEDIA("Stop media playback", RiskLevel.LOW),
    VOLUME_SET("Set system volume percentage", RiskLevel.LOW),
    VOLUME_UP("Increase volume", RiskLevel.LOW),
    VOLUME_DOWN("Decrease volume", RiskLevel.LOW),
    BRIGHTNESS_SET("Set screen brightness percentage", RiskLevel.LOW),
    BRIGHTNESS_UP("Increase brightness", RiskLevel.LOW),
    BRIGHTNESS_DOWN("Decrease brightness", RiskLevel.LOW),
    TOGGLE_TORCH("Toggle flashlight", RiskLevel.LOW),
    TOGGLE_WIFI("Toggle Wi-Fi", RiskLevel.LOW),
    TOGGLE_BLUETOOTH("Toggle Bluetooth", RiskLevel.LOW),
    TOGGLE_ROTATION("Toggle screen rotation lock", RiskLevel.LOW),
    SET_RINGER("Set ringer mode (silent/vibrate/normal)", RiskLevel.LOW),
    TOGGLE_DND("Toggle Do Not Disturb", RiskLevel.LOW),
    SET_SCREENSHOT("Capture screenshot", RiskLevel.LOW),

    // Communication
    SEND_MESSAGE("Send message via WhatsApp or SMS", RiskLevel.MEDIUM, requiresConfirmation = true),
    SEND_WHATSAPP_VOICENOTE("Send WhatsApp voice note", RiskLevel.MEDIUM, requiresConfirmation = true),
    READ_CALL_LOG("Read call history", RiskLevel.LOW, requiresPermission = "android.permission.READ_CALL_LOG"),
    RESOLVE_CONTACT("Resolve contact phone number", RiskLevel.LOW, requiresPermission = "android.permission.READ_CONTACTS"),
    MAKE_CALL("Initiate phone call", RiskLevel.HIGH, requiresConfirmation = true, requiresPermission = "android.permission.CALL_PHONE"),
    MAKE_SPEAKER_CALL("Initiate phone call on speaker", RiskLevel.HIGH, requiresConfirmation = true, requiresPermission = "android.permission.CALL_PHONE"),

    // Reminders & Location
    SET_ALARM("Set alarm for specified time", RiskLevel.LOW),
    SET_REMINDER("Set a reminder (time or location based)", RiskLevel.LOW),
    SET_TIMER("Set countdown timer", RiskLevel.LOW),
    READ_CALENDAR("Read upcoming calendar events", RiskLevel.LOW, requiresPermission = "android.permission.READ_CALENDAR"),
    GET_LOCATION("Get current location", RiskLevel.LOW, requiresPermission = "android.permission.ACCESS_FINE_LOCATION"),
    GET_WEATHER("Get weather for current location", RiskLevel.LOW),
    NAVIGATE_TO("Open navigation to a place", RiskLevel.LOW),
    SET_GEOFENCE("Set location-based reminder/geofence", RiskLevel.LOW, requiresPermission = "android.permission.ACCESS_FINE_LOCATION"),

    // Translation & AI
    LIVE_TRANSLATE("Translate text to target language", RiskLevel.LOW),
    GET_DAILY_BRIEFING("Get morning briefing (time, weather, news, battery)", RiskLevel.LOW),
    EXPORT_LOGS("Export logs to external storage", RiskLevel.LOW),
    CLEAR_MEMORY("Clear all cached memories", RiskLevel.LOW),

    // System Settings & Control
    CHECK_PERMISSION("Check if permission is granted", RiskLevel.LOW),
    REQUEST_PERMISSION("Request permission from user", RiskLevel.LOW),
    OPEN_SETTINGS("Open specific settings screen", RiskLevel.LOW),
    CONFIRM_ACTION("Prompt user for confirmation", RiskLevel.LOW),
    CANCEL_TASK("Cancel current task execution", RiskLevel.LOW)
}