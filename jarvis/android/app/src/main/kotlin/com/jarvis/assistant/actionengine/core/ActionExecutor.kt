package com.jarvis.assistant.actionengine.core

import android.content.Context
import android.util.Log
import com.jarvis.assistant.actionengine.model.*
import com.jarvis.assistant.accessibility.AccessibilityController
import com.jarvis.assistant.device.*
import kotlinx.coroutines.delay

class ActionExecutor(private val context: Context? = null) {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    private val appController = AppController(context)
    private val systemController = SystemController(context)
    private val mediaController = MediaController(context)
    private val smsController = SmsController(context)
    private val callController = CallController(context)
    private val accessibilityController = AccessibilityController()
    private val youTubeAdapter = com.jarvis.assistant.actionengine.adapter.YouTubeAdapter(context)
    private val whatsAppAdapter = com.jarvis.assistant.actionengine.adapter.WhatsAppAdapter(context)
    private val chromeAdapter = com.jarvis.assistant.actionengine.adapter.ChromeAdapter(context)
    private val phoneAdapter = com.jarvis.assistant.actionengine.adapter.PhoneAdapter(context)

    suspend fun executePlan(
        plan: TaskPlan,
        onStepUpdate: ((ActionStep, ActionResult) -> Unit)? = null
    ): List<ActionResult> {
        val results = mutableListOf<ActionResult>()
        val completedStepIds = mutableSetOf<String>()
        plan.currentState = TaskState.EXECUTING
        Log.i(TAG, "Starting execution of task plan: ${plan.taskId} with ${plan.steps.size} steps")

        for (step in plan.steps) {
            if (!step.isReady(completedStepIds)) {
                Log.w(TAG, "Step ${step.actionId} prerequisites not met: ${step.prerequisites}")
                val failure = Failure(
                    code = FailureCode.UNKNOWN_ERROR,
                    message = "Prerequisites not met for step ${step.actionId}",
                    stepId = step.actionId
                )
                val result = ActionResult(
                    actionId = step.actionId,
                    executionSuccess = false,
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
            val startTime = System.currentTimeMillis()

            for (attempt in 0..currentStep.maxRetries) {
                try {
                    val result = executeSingleAction(currentStep)
                    executionSuccess = result.first
                    outputData = result.second
                    if (executionSuccess) break
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
                    delay(delayMs)
                    currentStep = currentStep.incrementRetry()
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            val actionResult = ActionResult(
                actionId = currentStep.actionId,
                executionSuccess = executionSuccess,
                verificationPassed = executionSuccess,
                taskState = if (executionSuccess) TaskState.NEXT_STEP else TaskState.FAILED,
                output = outputData,
                failure = if (!executionSuccess) lastFailure else null,
                durationMs = durationMs
            )
            results.add(actionResult)
            onStepUpdate?.invoke(currentStep, actionResult)

            if (executionSuccess) {
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
                val duration = (step.parameters["durationMs"] as? Number)?.toLong() ?: 1000L
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
                val ok = mediaController.playMedia()
                Pair(ok, null)
            }
            ActionType.PAUSE_MEDIA -> {
                val ok = mediaController.pauseMedia()
                Pair(ok, null)
            }
            ActionType.RESOLVE_CONTACT -> {
                val contact = step.parameters["contact"] as? String ?: ""
                Pair(true, mapOf("contact" to contact))
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
            ActionType.READ_CALL_LOG -> {
                val log = phoneAdapter.getRecentCalls()
                Pair(true, mapOf("log" to log))
            }
            ActionType.MAKE_CALL -> {
                val number = step.parameters["number"] as? String ?: ""
                val ok = phoneAdapter.makeCall(number)
                Pair(ok, mapOf("calling" to number))
            }
            else -> Pair(true, null)
        }
    }
}
