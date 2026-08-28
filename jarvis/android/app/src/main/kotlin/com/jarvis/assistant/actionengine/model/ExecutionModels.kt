package com.jarvis.assistant.actionengine.model

/** State of a planned action-engine task. */
enum class TaskState {
    IDLE,
    EXECUTING,
    NEXT_STEP,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class StateType {
    APP_FOREGROUND,
    NODE_EXISTS,
    SCREEN_CONTAINS,
    MEDIA_PLAYING
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
