package com.jarvis.assistant.telemetry

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Structured diagnostic event bus for cross-layer correlation and telemetry.
 * Captures lifecycle, task execution, step verification, audio invariants, and backend health.
 */
enum class TelemetryEventType {
    // Task Lifecycle
    TASK_START,
    TASK_PLANNED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,

    // Step Execution & Verification
    STEP_START,
    STEP_EXECUTED,
    STEP_VERIFIED,
    STEP_FAILED,
    STEP_RETRY,

    // Audio & Volume Invariants
    AUDIO_SESSION_START,
    AUDIO_SESSION_END,
    AUDIO_ROUTE_CHANGED,
    VOLUME_INVARIANT_CHECK,

    // Backend Connectivity
    NETWORK_AVAILABLE,
    NETWORK_LOST,
    HTTP_HEALTH_CHECK,
    WS_CONNECTING,
    WS_CONNECTED,
    WS_DISCONNECTED,
    BACKEND_STATUS_CHANGED,

    // Service Lifecycle
    SERVICE_CREATED,
    FOREGROUND_STARTED,
    RUNTIME_STARTED,
    WAKE_STARTED,
    RUNTIME_STOP_REQUESTED,
    RUNTIME_STOPPED,
    SERVICE_DESTROYED
}

data class DiagnosticEvent(
    val type: TelemetryEventType,
    val component: String,
    val taskId: String? = null,
    val stepId: String? = null,
    val durationMs: Long? = null,
    val success: Boolean? = null,
    val details: Map<String, Any?> = emptyMap(),
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toLogString(): String {
        val parts = mutableListOf<String>()
        parts.add("[$type]")
        parts.add("component=$component")
        if (taskId != null) parts.add("taskId=$taskId")
        if (stepId != null) parts.add("stepId=$stepId")
        if (durationMs != null) parts.add("duration=${durationMs}ms")
        if (success != null) parts.add("success=$success")
        if (errorMessage != null) parts.add("error='$errorMessage'")
        if (details.isNotEmpty()) {
            val detailStr = details.entries.joinToString(", ") { "${it.key}=${it.value}" }
            parts.add("details={$detailStr}")
        }
        return parts.joinToString(" | ")
    }
}

object DiagnosticEventBus {
    private const val TAG = "DiagnosticEventBus"

    private val _events = MutableSharedFlow<DiagnosticEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DiagnosticEvent> = _events.asSharedFlow()

    fun emit(
        type: TelemetryEventType,
        component: String,
        taskId: String? = null,
        stepId: String? = null,
        durationMs: Long? = null,
        success: Boolean? = null,
        details: Map<String, Any?> = emptyMap(),
        errorMessage: String? = null
    ) {
        val event = DiagnosticEvent(
            type = type,
            component = component,
            taskId = taskId,
            stepId = stepId,
            durationMs = durationMs,
            success = success,
            details = details,
            errorMessage = errorMessage
        )
        val logStr = event.toLogString()
        if (success == false || errorMessage != null) {
            Log.w(TAG, logStr)
        } else {
            Log.i(TAG, logStr)
        }
        _events.tryEmit(event)
    }
}
