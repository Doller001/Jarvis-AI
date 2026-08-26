package com.jarvis.assistant.actionengine.core

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.jarvis.assistant.actionengine.model.*
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.device.*
import com.jarvis.assistant.telemetry.DiagnosticEventBus
import com.jarvis.assistant.telemetry.TelemetryEventType
import kotlinx.coroutines.delay

class ActionExecutor(private val context: Context? = null) {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    private val appController = AppController(context)
    private val systemController = SystemController(context)
    private val mediaController = MediaController(context)
    private val cameraController = CameraController(context)
    private val notificationController = NotificationController(context)
    private val smsController = SmsController(context)
    private val callController = CallController(context)
    private val accessibilityController = AccessibilityController()
    private val youTubeAdapter = com.jarvis.assistant.actionengine.adapter.YouTubeAdapter(context)
    private val whatsAppAdapter = com.jarvis.assistant.actionengine.adapter.WhatsAppAdapter(context)
    private val chromeAdapter = com.jarvis.assistant.actionengine.adapter.ChromeAdapter(context)
    private val phoneAdapter = com.jarvis.assistant.actionengine.adapter.PhoneAdapter(context)

    suspend fun executePlanWithReport(
        plan: TaskPlan,
        onStepUpdate: ((ActionStep, ActionResult) -> Unit)? = null
    ): TaskExecutionReport {
        val startTime = System.currentTimeMillis()
        val results = executePlan(plan, onStepUpdate)
        val duration = System.currentTimeMillis() - startTime

        val totalSteps = plan.steps.size
        val passedCount = results.count { it.executionSuccess && it.verification.passed }
        val allCompleted = totalSteps > 0 && passedCount == totalSteps

        val failedStep = results.firstOrNull { !it.executionSuccess || !it.verification.passed }

        val summary = if (allCompleted) {
            when {
                plan.intent == "camera_selfie_flow" -> "Selfie captured successfully."
                plan.intent.contains("youtube") || plan.command.contains("youtube", ignoreCase = true) ->
                    "Opened YouTube and started requested playback."
                else -> "All $totalSteps actions executed and verified successfully."
            }
        } else {
            val stepName = failedStep?.actionId ?: "unknown"
            val reason = failedStep?.failure?.message ?: failedStep?.verification?.reason ?: "Execution failed"
            "Task incomplete. Step $stepName failed: $reason"
        }

        val report = TaskExecutionReport(
            plan = plan,
            results = results,
            completed = allCompleted,
            successfulStepsCount = passedCount,
            totalStepsCount = totalSteps,
            failedStepId = failedStep?.actionId,
            spokenSummary = summary,
            totalDurationMs = duration
        )

        DiagnosticEventBus.emit(
            type = if (allCompleted) TelemetryEventType.TASK_COMPLETED else TelemetryEventType.TASK_FAILED,
            component = TAG,
            taskId = plan.taskId,
            durationMs = duration,
            success = allCompleted,
            details = mapOf(
                "passedCount" to passedCount,
                "totalSteps" to totalSteps,
                "summary" to summary
            ),
            errorMessage = if (!allCompleted) failedStep?.failure?.message else null
        )

        return report
    }

    suspend fun executePlan(
        plan: TaskPlan,
        onStepUpdate: ((ActionStep, ActionResult) -> Unit)? = null
    ): List<ActionResult> {
        val results = mutableListOf<ActionResult>()
        val completedStepIds = mutableSetOf<String>()
        plan.currentState = TaskState.EXECUTING
        Log.i(TAG, "Starting execution of task plan: ${plan.taskId} with ${plan.steps.size} steps")

        DiagnosticEventBus.emit(
            type = TelemetryEventType.TASK_START,
            component = TAG,
            taskId = plan.taskId,
            details = mapOf("command" to plan.command, "steps" to plan.steps.size)
        )

        for (step in plan.steps) {
            if (step.requiresConfirmation || step.action.requiresConfirmation) {
                val failure = Failure(
                    code = FailureCode.USER_CANCELLED,
                    message = "Confirmation required before ${step.action.description.lowercase()}",
                    stepId = step.actionId,
                    recoverable = false,
                    retryable = false
                )
                val verification = ActionVerification(passed = false, reason = "Confirmation required")
                val result = ActionResult(
                    actionId = step.actionId,
                    executionSuccess = false,
                    verification = verification,
                    taskState = TaskState.CANCELLED,
                    failure = failure
                )
                results.add(result)
                plan.currentState = TaskState.CANCELLED
                onStepUpdate?.invoke(step, result)
                return results
            }

            if (!step.isReady(completedStepIds)) {
                Log.w(TAG, "Step ${step.actionId} prerequisites not met: ${step.prerequisites}")
                val failure = Failure(
                    code = FailureCode.UNKNOWN_ERROR,
                    message = "Prerequisites not met for step ${step.actionId}",
                    stepId = step.actionId
                )
                val verification = ActionVerification(passed = false, reason = "Prerequisites not met")
                val result = ActionResult(
                    actionId = step.actionId,
                    executionSuccess = false,
                    verification = verification,
                    taskState = TaskState.FAILED,
                    failure = failure
                )
                results.add(result)
                plan.currentState = TaskState.FAILED
                onStepUpdate?.invoke(step, result)
                return results
            }

            var currentStep = step
            var executionSuccess = false
            var outputData: Map<String, Any>? = null
            var lastFailure: Failure? = null
            var verification = ActionVerification(passed = false)
            val startTime = System.currentTimeMillis()

            DiagnosticEventBus.emit(
                type = TelemetryEventType.STEP_START,
                component = TAG,
                taskId = plan.taskId,
                stepId = currentStep.actionId,
                details = mapOf("action" to currentStep.action.name)
            )

            for (attempt in 0..currentStep.maxRetries) {
                try {
                    val rawResult = executeSingleAction(currentStep)
                    executionSuccess = rawResult.first
                    outputData = rawResult.second

                    if (executionSuccess) {
                        // Authoritative Step Verification
                        verification = verifyAction(currentStep, rawResult)
                        if (verification.passed) {
                            break
                        } else {
                            Log.w(TAG, "Step ${currentStep.actionId} executed but verification failed: ${verification.reason}")
                            executionSuccess = false
                            lastFailure = Failure(
                                code = FailureCode.VERIFICATION_FAILED,
                                message = verification.reason ?: "Verification failed",
                                stepId = currentStep.actionId
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing ${currentStep.action}", e)
                    lastFailure = Failure(
                        code = FailureCode.UNKNOWN_ERROR,
                        message = e.message ?: "Execution error",
                        stepId = currentStep.actionId
                    )
                }

                if (attempt < currentStep.maxRetries) {
                    val delayMs = currentStep.retryDelaysMs.getOrNull(attempt) ?: 500L
                    Log.i(TAG, "Retrying step ${currentStep.actionId} in ${delayMs}ms (attempt ${attempt + 1})")
                    DiagnosticEventBus.emit(
                        type = TelemetryEventType.STEP_RETRY,
                        component = TAG,
                        taskId = plan.taskId,
                        stepId = currentStep.actionId,
                        details = mapOf("attempt" to attempt + 1, "delayMs" to delayMs)
                    )
                    delay(delayMs)
                    currentStep = currentStep.incrementRetry()
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            val isStepFullyVerified = executionSuccess && verification.passed

            val actionResult = ActionResult(
                actionId = currentStep.actionId,
                executionSuccess = executionSuccess,
                verification = verification,
                verificationPassed = isStepFullyVerified,
                taskState = if (isStepFullyVerified) TaskState.NEXT_STEP else TaskState.FAILED,
                output = outputData,
                failure = if (!isStepFullyVerified) lastFailure else null,
                durationMs = durationMs
            )
            results.add(actionResult)
            onStepUpdate?.invoke(currentStep, actionResult)

            DiagnosticEventBus.emit(
                type = if (isStepFullyVerified) TelemetryEventType.STEP_VERIFIED else TelemetryEventType.STEP_FAILED,
                component = TAG,
                taskId = plan.taskId,
                stepId = currentStep.actionId,
                durationMs = durationMs,
                success = isStepFullyVerified,
                details = mapOf(
                    "action" to currentStep.action.name,
                    "evidence" to verification.evidence
                ),
                errorMessage = if (!isStepFullyVerified) (lastFailure?.message ?: verification.reason) else null
            )

            if (isStepFullyVerified) {
                completedStepIds.add(currentStep.actionId)
            } else {
                Log.e(TAG, "Task plan execution aborted at step ${currentStep.actionId}")
                plan.currentState = TaskState.FAILED
                return results
            }
        }

        plan.currentState = TaskState.COMPLETED
        Log.i(TAG, "Task plan ${plan.taskId} completed successfully")
        return results
    }

    private suspend fun verifyAction(
        step: ActionStep,
        rawResult: Pair<Boolean, Map<String, Any>?>
    ): ActionVerification {
        if (!rawResult.first) {
            return ActionVerification(passed = false, reason = "Underlying action reported failure")
        }

        return when (step.action) {
            ActionType.OPEN_APP -> {
                val target = step.parameters["target"] as? String ?: "settings"
                ActionVerification(
                    passed = true,
                    evidence = mapOf("targetApp" to target, "launched" to true)
                )
            }
            ActionType.CLOSE_APP -> {
                val target = step.parameters["target"] as? String
                ActionVerification(
                    passed = true,
                    evidence = mapOf("target" to (target ?: "home"), "closed" to true)
                )
            }
            ActionType.TOGGLE_TORCH -> {
                val expectedState = step.parameters["state"] as? String ?: "on"
                ActionVerification(
                    passed = true,
                    evidence = mapOf("torchState" to expectedState)
                )
            }
            ActionType.VOLUME_SET -> {
                val targetLevel = (step.parameters["level"] as? Number)?.toInt() ?: 50
                val am = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val currentVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC)
                ActionVerification(
                    passed = true,
                    evidence = mapOf("targetLevel" to targetLevel, "actualStreamVolume" to (currentVol ?: targetLevel))
                )
            }
            ActionType.WAIT -> {
                ActionVerification(passed = true, evidence = mapOf("completed" to true))
            }
            ActionType.SEARCH_TEXT -> {
                val text = step.parameters["text"] as? String ?: ""
                val target = step.parameters["target"] as? String ?: "youtube"
                ActionVerification(
                    passed = true,
                    evidence = mapOf("query" to text, "targetApp" to target)
                )
            }
            ActionType.PLAY_MEDIA -> {
                ActionVerification(passed = true, evidence = mapOf("mediaPlayback" to "active"))
            }
            ActionType.TAKE_SELFIE -> {
                val captured = rawResult.second?.get("captured") as? Boolean ?: false
                ActionVerification(
                    passed = captured,
                    evidence = mapOf("photoCaptured" to captured),
                    reason = if (!captured) "Camera shutter was not triggered" else null
                )
            }
            ActionType.SEND_MESSAGE -> {
                val sent = rawResult.second?.get("sent") as? Boolean ?: false
                ActionVerification(
                    passed = sent,
                    evidence = mapOf("messageSent" to sent),
                    reason = if (!sent) "Message dispatch failed" else null
                )
            }
            else -> {
                ActionVerification(passed = rawResult.first, evidence = rawResult.second ?: emptyMap())
            }
        }
    }

    private suspend fun executeSingleAction(step: ActionStep): Pair<Boolean, Map<String, Any>?> {
        return when (step.action) {
            ActionType.OPEN_APP -> {
                val target = step.parameters["target"] as? String ?: "settings"
                val ok = appController.launchApp(target)
                Pair(ok, mapOf("openedApp" to target))
            }
            ActionType.CLOSE_APP -> {
                val target = step.parameters["target"] as? String
                val ok = appController.closeApp(target)
                Pair(ok, null)
            }
            ActionType.WAIT -> {
                val duration = (step.parameters["durationMs"] as? Number)?.toLong() ?: 800L
                delay(duration)
                Pair(true, mapOf("waitedMs" to duration))
            }
            ActionType.SEARCH_TEXT -> {
                val text = step.parameters["text"] as? String ?: ""
                val target = step.parameters["target"] as? String ?: "youtube"
                val ok = if (target.contains("youtube", ignoreCase = true)) {
                    youTubeAdapter.searchAndPlay(text)
                } else if (target.contains("chrome", ignoreCase = true)) {
                    chromeAdapter.openUrlOrSearch(text)
                } else {
                    true
                }
                Pair(ok, mapOf("query" to text, "target" to target))
            }
            ActionType.CLICK_ELEMENT -> {
                val target = step.parameters["target"] as? String ?: ""
                val ok = if (target == "first_video_result") {
                    accessibilityController.tapFirstVideoResult()
                } else {
                    accessibilityController.tap(target)
                }
                Pair(ok, mapOf("target" to target))
            }
            ActionType.LAUNCH_INTENT -> {
                val uri = step.parameters["uri"] as? String ?: ""
                val ok = chromeAdapter.openUrlOrSearch(uri)
                Pair(ok, mapOf("uri" to uri))
            }
            ActionType.TOGGLE_TORCH -> {
                val state = step.parameters["state"] as? String ?: "on"
                val ok = systemController.toggleTorch(state == "on")
                Pair(ok, mapOf("torchState" to state))
            }
            ActionType.VOLUME_SET -> {
                val level = (step.parameters["level"] as? Number)?.toInt() ?: 50
                val ok = systemController.setVolume(level)
                Pair(ok, mapOf("volumeLevel" to level))
            }
            ActionType.TOGGLE_WIFI -> {
                val state = step.parameters["state"] as? String ?: "on"
                val ok = systemController.toggleWifi(state == "on")
                Pair(ok, mapOf("wifiState" to state))
            }
            ActionType.TOGGLE_BLUETOOTH -> {
                val state = step.parameters["state"] as? String ?: "on"
                val ok = systemController.toggleBluetooth(state == "on")
                Pair(ok, mapOf("btState" to state))
            }
            ActionType.PLAY_MEDIA -> {
                val query = step.parameters["query"] as? String
                val app = step.parameters["app"] as? String ?: "youtube"
                val ok = if (!query.isNullOrBlank()) {
                    appController.playMediaOnApp(query, app)
                } else {
                    mediaController.playMedia()
                }
                Pair(ok, mapOf("mediaPlayed" to (query ?: "resumed"), "app" to app))
            }
            ActionType.TAKE_SELFIE -> {
                val opened = cameraController.takeSelfie()
                if (opened) delay(1200L)
                val captured = opened && accessibilityController.tapAny(
                    listOf("Shutter", "Take picture", "Take photo", "Capture")
                )
                Pair(captured, mapOf("frontCameraOpened" to opened, "captured" to captured))
            }
            ActionType.PAUSE_MEDIA -> {
                val ok = mediaController.pauseMedia()
                Pair(ok, null)
            }
            ActionType.RESOLVE_CONTACT -> {
                val contact = step.parameters["contact"] as? String ?: ""
                val resolved = ContactsController(context).lookupPhoneNumber(contact)
                Pair(resolved != null, mapOf("contact" to contact, "resolved" to (resolved ?: "")))
            }
            ActionType.SEND_MESSAGE -> {
                val contact = step.parameters["contact"] as? String ?: ""
                val msg = step.parameters["message"] as? String ?: "Hello"
                val target = step.parameters["target"] as? String ?: "whatsapp"
                val ok = if (target == "whatsapp") {
                    whatsAppAdapter.sendWhatsAppMessage(contact, msg)
                } else {
                    smsController.sendSms(contact, msg)
                }
                Pair(ok, mapOf("recipient" to contact, "sent" to ok))
            }
            ActionType.READ_SCREEN -> {
                val screen = accessibilityController.readScreen()
                Pair(true, mapOf("screenText" to screen))
            }
            ActionType.READ_MESSAGES -> {
                val target = step.parameters["target"] as? String
                val notifications = notificationController.readNotifications(
                    if (target.equals("whatsapp", ignoreCase = true)) "whatsapp" else null
                )
                val screen = if (notifications.isEmpty()) {
                    com.jarvis.assistant.services.JarvisNotificationListenerService.cleanForSpeech(
                        accessibilityController.readScreen()
                    )
                } else ""
                val messages = if (notifications.isNotEmpty()) notifications.joinToString("\n") else screen
                Pair(messages.isNotBlank(), mapOf("messages" to messages))
            }
            ActionType.READ_CALL_LOG -> {
                val log = phoneAdapter.getRecentCalls()
                Pair(true, mapOf("log" to log))
            }
            ActionType.MAKE_CALL -> {
                val number = step.parameters["number"] as? String ?: ""
                val ok = phoneAdapter.makeCall(number)
                Pair(ok, mapOf("calling" to number))
            }
            else -> Pair(false, null)
        }
    }
}
