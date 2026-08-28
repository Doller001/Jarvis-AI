package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.jarvis.assistant.execution.ExecutionOutcome
import com.jarvis.assistant.execution.TaskExecutionCoordinator
import com.jarvis.assistant.settings.SettingsManager
import com.jarvis.assistant.telemetry.DiagnosticEventBus
import com.jarvis.assistant.telemetry.TelemetryEventType
import com.jarvis.assistant.voice.VoiceRuntime
import com.jarvis.assistant.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns VoiceRuntime and TaskExecutionCoordinator.
 *
 * Manages voice lifecycle, wake word detection, and background command execution.
 * Supports interrupt handling for TTS barge-in.
 */
class JarvisForegroundService : Service() {
    private lateinit var voiceRuntime: VoiceRuntime
    private lateinit var settingsManager: SettingsManager
    private val coordinator by lazy { TaskExecutionCoordinator(applicationContext) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "JarvisForegroundService"

        var isRunning: Boolean = false
            private set
        var onUtterance: ((String) -> Unit)? = null
        var onResponseDone: (() -> Unit)? = null
        var onWakeToggled: ((Boolean) -> Unit)? = null
        var onStateChanged: ((VoiceState) -> Unit)? = null
        var onEnvironmentChanged: ((com.jarvis.assistant.voice.EnvironmentProfile) -> Unit)? = null
        var onAudioMetrics: ((com.jarvis.assistant.voice.AudioProcessingResult) -> Unit)? = null
        var speak: ((String) -> Unit)? = null
        var toggleWakeListening: (() -> Boolean)? = null
        var setWakeSensitivity: ((String) -> Unit)? = null
        var startCommandListening: (() -> Unit)? = null
        var setSpeechRate: ((Float) -> Unit)? = null
        var interruptVoice: (() -> Unit)? = null

        const val ACTION_START = "com.jarvis.assistant.START"
        const val ACTION_STOP = "com.jarvis.assistant.STOP"
        const val ACTION_LISTEN_FOR_COMMAND = "com.jarvis.assistant.LISTEN_FOR_COMMAND"
        const val ACTION_INTERRUPT = "com.jarvis.assistant.INTERRUPT"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
        voiceRuntime = VoiceRuntime(applicationContext)

        speak = { text ->
            voiceRuntime.speakResponse(text) { onResponseDone?.invoke() }
        }
        toggleWakeListening = {
            val active = voiceRuntime.toggleMonitoring()
            settingsManager.wakeWordEnabled = active
            onWakeToggled?.invoke(active)
            active
        }
        setWakeSensitivity = { label ->
            settingsManager.wakeSensitivity = label
            voiceRuntime.setWakeSensitivity(label)
        }
        startCommandListening = {
            voiceRuntime.startManualCommand()
        }
        setSpeechRate = { rate ->
            voiceRuntime.setSpeechRate(rate)
        }
        interruptVoice = {
            voiceRuntime.interrupt()
        }

        DiagnosticEventBus.emit(
            type = TelemetryEventType.SERVICE_CREATED,
            component = TAG
        )
        Log.i(TAG, "JarvisForegroundService created")
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                shutdownRuntime(
                    "ACTION_STOP requested"
                )
                return START_NOT_STICKY
            }

            ACTION_LISTEN_FOR_COMMAND -> {
                if (::voiceRuntime.isInitialized) {
                    Log.i(
                        TAG,
                        "Explicit manual mic action received"
                    )

                    voiceRuntime.startManualCommand()
                }

                return START_STICKY
            }

            ACTION_INTERRUPT -> {
                if (::voiceRuntime.isInitialized) {
                    voiceRuntime.interrupt()
                }

                return START_STICKY
            }
        }

        val wasRunning = isRunning
        isRunning = true

        val notification = buildForegroundNotification().build()
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && hasMicPermission) {
                startForeground(
                    1001, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(1001, notification)
            }
            DiagnosticEventBus.emit(
                type = TelemetryEventType.FOREGROUND_STARTED,
                component = TAG
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground with mic type failed", e)
            try { startForeground(1001, notification) } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground also failed", ex)
            }
        }

        if (!wasRunning) {
            voiceRuntime.setStateListener { state ->
                onStateChanged?.invoke(state)
                updateNotification(state)
            }
            voiceRuntime.onEnvironmentChanged = { env -> onEnvironmentChanged?.invoke(env) }
            voiceRuntime.onAudioMetrics = { metrics -> onAudioMetrics?.invoke(metrics) }

            voiceRuntime.startRuntime { userUtterance ->
                Log.i(TAG, "Utterance received in service: '$userUtterance'")
                val uiHandler = onUtterance
                if (uiHandler != null) {
                    uiHandler(userUtterance)
                } else {
                    // Headless background execution via TaskExecutionCoordinator
                    serviceScope.launch {
                        val outcome = coordinator.coordinate(userUtterance)
                        val responseText = when (outcome) {
                            is ExecutionOutcome.Success -> outcome.spokenResponse
                            is ExecutionOutcome.ConfirmationRequired -> outcome.prompt
                            is ExecutionOutcome.Failure -> outcome.spokenResponse
                            is ExecutionOutcome.RouteToCloud -> "Command received. Connecting to cloud intelligence."
                        }
                        // Fixed: speakResponse now always calls completion callback
                        voiceRuntime.speakResponse(responseText) {
                            Log.d(TAG, "Headless TTS completed for: '${responseText.take(30)}...'")
                        }
                    }
                }
            }

            val wakeEnabled = settingsManager.wakeWordEnabled
            val sensitivity = settingsManager.wakeSensitivity
            voiceRuntime.setWakeSensitivity(sensitivity)
            voiceRuntime.setWakeEnabled(wakeEnabled)
            Log.i(TAG, "Wake word initialized: enabled=$wakeEnabled, sensitivity=$sensitivity")
        }

        return START_STICKY
    }

    private fun shutdownRuntime(reason: String) {
        Log.i(TAG, "Shutting down JarvisForegroundService: reason='$reason'")
        DiagnosticEventBus.emit(
            type = TelemetryEventType.RUNTIME_STOP_REQUESTED,
            component = TAG,
            details = mapOf("reason" to reason)
        )
        if (::voiceRuntime.isInitialized) {
            voiceRuntime.setWakeEnabled(false)
            voiceRuntime.release()
        }
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(state: VoiceState) {
        val text = when (state) {
            VoiceState.DISABLED -> "Wake word OFF"
            VoiceState.WAKE_LISTENING -> "Listening for 'Hey Jarvis'"
            VoiceState.ACKNOWLEDGING -> "Acknowledging..."
            VoiceState.COMMAND_LISTENING -> "Listening for command..."
            VoiceState.PROCESSING -> "Processing..."
            VoiceState.SPEAKING -> "Speaking..."
            VoiceState.INTERRUPTING -> "Interrupting..."
            VoiceState.RECOVERING -> "Recovering..."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1001, buildForegroundNotification().setContentText(text).build())
    }

    fun speakResponse(text: String) {
        Log.i(TAG, "Speaking: '$text'")
        if (::voiceRuntime.isInitialized) {
            voiceRuntime.speakResponse(text) { onResponseDone?.invoke() }
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jarvis_runtime", "Jarvis Runtime Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification.Builder {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, "jarvis_runtime")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("JARVIS")
            .setContentText("JARVIS Assistant Active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
    }

    override fun onDestroy() {
        isRunning = false
        DiagnosticEventBus.emit(
            type = TelemetryEventType.SERVICE_DESTROYED,
            component = TAG
        )
        if (::voiceRuntime.isInitialized) voiceRuntime.release()
        serviceScope.cancel()
        onUtterance = null
        onResponseDone = null
        onWakeToggled = null
        onStateChanged = null
        onEnvironmentChanged = null
        onAudioMetrics = null
        speak = null
        toggleWakeListening = null
        setWakeSensitivity = null
        startCommandListening = null
        setSpeechRate = null
        interruptVoice = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
