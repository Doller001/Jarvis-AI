package com.jarvis.assistant.actionengine.model

data class ExpectedState(
    val type: StateType,
    val description: String,
    val criteria: Map<String, Any> = emptyMap(),
    val timeoutMs: Long = 3000L
)

data class Failure(
    val code: FailureCode,
    val message: String,
    val stepId: String,
    val recoverable: Boolean = true,
    val retryable: Boolean = true,
    val userMessage: String = message,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActionVerification(
    val passed: Boolean,
    val evidence: Map<String, Any?> = emptyMap(),
    val reason: String? = null
)

data class RawExecutionResult(
    val success: Boolean,
    val output: Map<String, Any>? = null,
    val errorMessage: String? = null
)

enum class WaitType {
    APP_FOREGROUND,
    NODE_EXISTS,
    MEDIA_PLAYING,
    SCREEN_CONTAINS,
    DURATION
}

data class WaitCondition(
    val type: WaitType,
    val target: String? = null,
    val timeoutMs: Long = 3000L,
    val pollIntervalMs: Long = 100L
)

data class ActionStep(
    val actionId: String,
    val action: ActionType,
    val parameters: Map<String, Any> = emptyMap(),
    val timeoutMs: Long = 3000L,
    val maxRetries: Int = 2,
    val retryDelaysMs: List<Long> = listOf(500L, 1000L),
    val expectedState: ExpectedState? = null,
    val waitCondition: WaitCondition? = null,
    val successCondition: String? = null,
    val failureCondition: String? = null,
    val fallbackAction: ActionStep? = null,
    val requiresConfirmation: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val prerequisites: List<String> = emptyList(),
    val dependsOnApp: String? = null,
    val verified: Boolean = false,
    val completed: Boolean = false,
    val retryCount: Int = 0,
    val lastError: String? = null
) {
    fun isReady(completedStepIds: Set<String>): Boolean = prerequisites.all { it in completedStepIds }
    fun canRetry(): Boolean = retryCount < maxRetries && lastError != null && !completed
    fun incrementRetry(): ActionStep = copy(retryCount = retryCount + 1)
}

data class ActionResult(
    val actionId: String,
    val executionSuccess: Boolean,
    val verification: ActionVerification = ActionVerification(passed = executionSuccess),
    val verificationPassed: Boolean? = verification.passed,
    val taskState: TaskState = TaskState.COMPLETED,
    val output: Map<String, Any>? = null,
    val failure: Failure? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class TaskPlan(
    val taskId: String,
    val command: String,
    val intent: String,
    val steps: List<ActionStep>,
    var currentState: TaskState = TaskState.IDLE,
    val createdAt: Long = System.currentTimeMillis()
)

data class TaskExecutionReport(
    val plan: TaskPlan,
    val results: List<ActionResult>,
    val completed: Boolean,
    val successfulStepsCount: Int,
    val totalStepsCount: Int,
    val failedStepId: String? = null,
    val spokenSummary: String,
    val totalDurationMs: Long
)
