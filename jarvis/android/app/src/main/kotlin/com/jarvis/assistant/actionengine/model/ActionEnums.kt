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
    SEARCH_TEXT("Find search field, type text, and press search", RiskLevel.LOW),
    FIND_ELEMENT("Find UI element on screen", RiskLevel.LOW),

    // Verification & Screen
    WAIT("Wait for specified duration", RiskLevel.LOW),
    VERIFY_STATE("Verify current state matches expected state", RiskLevel.LOW),
    READ_SCREEN("Extract visible text/content from screen", RiskLevel.LOW),

    // Media & System
    PLAY_MEDIA("Trigger media playback", RiskLevel.LOW),
    TAKE_SELFIE("Open the front camera and capture a selfie", RiskLevel.LOW),
    PAUSE_MEDIA("Pause media playback", RiskLevel.LOW),
    VOLUME_SET("Set system volume percentage", RiskLevel.LOW),
    TOGGLE_TORCH("Toggle flashlight", RiskLevel.LOW),
    TOGGLE_WIFI("Toggle Wi-Fi", RiskLevel.LOW),
    TOGGLE_BLUETOOTH("Toggle Bluetooth", RiskLevel.LOW),

    // Communication
    SEND_MESSAGE("Send message via WhatsApp or SMS", RiskLevel.MEDIUM, requiresConfirmation = true),
    READ_MESSAGES("Read latest notifications / messages", RiskLevel.LOW),
    READ_CALL_LOG("Read call history", RiskLevel.LOW, requiresPermission = "android.permission.READ_CALL_LOG"),
    RESOLVE_CONTACT("Resolve contact phone number", RiskLevel.LOW, requiresPermission = "android.permission.READ_CONTACTS"),
    MAKE_CALL("Initiate phone call", RiskLevel.HIGH, requiresConfirmation = true, requiresPermission = "android.permission.CALL_PHONE"),

    // System Settings & Control
    CHECK_PERMISSION("Check if permission is granted", RiskLevel.LOW),
    REQUEST_PERMISSION("Request permission from user", RiskLevel.LOW),
    OPEN_SETTINGS("Open specific settings screen", RiskLevel.LOW),
    CONFIRM_ACTION("Prompt user for confirmation", RiskLevel.LOW),
    CANCEL_TASK("Cancel current task execution", RiskLevel.LOW)
}

enum class TaskState {
    IDLE,
    PLANNING,
    PREPARING,
    EXECUTING,
    VERIFYING,
    NEXT_STEP,
    COMPLETED,
    FAILED,
    RETRY,
    FALLBACK,
    CANCELLED
}

enum class StateType {
    APP_FOREGROUND,
    APP_PACKAGE,
    ELEMENT_VISIBLE,
    ELEMENT_TEXT,
    TEXT_VISIBLE,
    MESSAGE_SENT,
    CALL_ACTIVE,
    PLAYBACK_ACTIVE,
    SEARCH_RESULTS,
    PERMISSION_GRANTED,
    SCREEN_CONTENT
}

enum class FailureCode {
    APP_NOT_INSTALLED,
    APP_NOT_OPENED,
    ELEMENT_NOT_FOUND,
    ELEMENT_NOT_CLICKABLE,
    PERMISSION_MISSING,
    PERMISSION_DENIED,
    TIMEOUT,
    ACTION_TIMEOUT,
    VERIFICATION_FAILED,
    USER_CANCELLED,
    UNKNOWN_ERROR
}
