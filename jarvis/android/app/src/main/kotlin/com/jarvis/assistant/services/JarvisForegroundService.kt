package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.jarvis.assistant.brain.JarvisBrain
import com.jarvis.assistant.execution.CommandExecutor
import com.jarvis.assistant.settings.SettingsManager
import com.jarvis.assistant.voice.VoiceRuntime
import com.jarvis.assistant.voice.VoiceState

/**
 * Foreground service that owns VoiceRuntime.
 *
 * Phase 2 fix: Reads SettingsManager.wakeWordEnabled AFTER startRuntime() and
 * calls voiceRuntime.setWakeEnabled() as the SINGLE wake authority.
 * UI must call setWakeEnabled() via the service — never touch WakeEngine directly.
 */
class JarvisForegroundService : Service() {
    private lateinit var voiceRuntime: VoiceRuntime
    private lateinit var settingsManager: SettingsManager
    private val brain by lazy { JarvisBrain() }
    private val commandExecutor by lazy { CommandExecutor(applicationContext) }

    companion object {
        var isRunning: Boolean = false
            private set
        var onUtterance: ((String) -> Unit)? = null
        var onResponseDone: (() -> Unit)? = null
        var onWakeToggled: ((Boolean) -> Unit)? = null
        var onStateChanged: ((VoiceState) -> Unit)? = null
        var onEnvironmentChanged: ((com.jarvis.assistant.voice.EnvironmentProfile) -> Unit)? = null
        var onAudioMetrics: ((com.jarvis.assistant.voice.AudioProcessingResult) -> Unit)? = null
        var speak: ((String) -> Unit)? = null
        /** Phase 2: single authority toggle — delegates to voiceRuntime.setWakeEnabled(). */
        var toggleWakeListening: (() -> Boolean)? = null
        var setWakeSensitivity: ((String) -> Unit)? = null
        var startCommandListening: (() -> Unit)? = null
        var setSpeechRate: ((Float) -> Unit)? = null

        const val ACTION_START = "com.jarvis.assistant.START"
        const val ACTION_STOP  = "com.jarvis.assistant.STOP"
        const val ACTION_LISTEN_FOR_COMMAND = "com.jarvis.assistant.LISTEN_FOR_COMMAND"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
        voiceRuntime = VoiceRuntime(applicationContext)

        speak = { text ->
            voiceRuntime.speakResponse(text) { onResponseDone?.invoke() }
        }
        // Phase 2: toggleWakeListening → voiceRuntime.toggleMonitoring()
        // which internally calls setWakeEnabled(). No direct engine access from UI.
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
            voiceRuntime.startListeningForCommand()
        }
        setSpeechRate = { rate ->
            voiceRuntime.setSpeechRate(rate)
        }
        Log.i("JarvisService", "JarvisForegroundService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
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
        } catch (e: Exception) {
            Log.e("JarvisService", "startForeground with mic type failed", e)
            try { startForeground(1001, notification) } catch (ex: Exception) {
                Log.e("JarvisService", "Fallback startForeground also failed", ex)
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
                Log.i("JarvisService", "Utterance: '$userUtterance'")
                val uiHandler = onUtterance
                if (uiHandler != null) {
                    uiHandler(userUtterance)
                } else {
                    val plan = brain.processCommand(userUtterance)
                    val response = if (plan.requiresConfirmation) {
                        "Please open Jarvis to confirm this action."
                    } else {
                        commandExecutor.execute(plan.intent)
                    }
                    voiceRuntime.speakResponse(response)
                }
            }

            // Phase 2: Apply wake setting AFTER startRuntime() — this is the
            // authoritative moment when the detector is allowed to start.
            val wakeEnabled = settingsManager.wakeWordEnabled
            val sensitivity = settingsManager.wakeSensitivity
            voiceRuntime.setWakeSensitivity(sensitivity)
            voiceRuntime.setWakeEnabled(wakeEnabled)
            Log.i("JarvisService", "Wake word enabled=$wakeEnabled, sensitivity=$sensitivity")
        }

        if (intent?.action == ACTION_LISTEN_FOR_COMMAND) {
            voiceRuntime.startListeningForCommand()
        }

        return START_STICKY
    }

    private fun updateNotification(state: VoiceState) {
        val text = when (state) {
            VoiceState.DISABLED           -> "Wake word OFF"
            VoiceState.WAKE_LISTENING, VoiceState.WAKE -> "Listening for 'Hey Jarvis'"
            VoiceState.ACKNOWLEDGING      -> "Acknowledging…"
            VoiceState.COMMAND_LISTENING, VoiceState.LISTENING -> "Listening for command…"
            VoiceState.PROCESSING         -> "Processing…"
            VoiceState.SPEAKING           -> "Speaking…"
            VoiceState.RECOVERING, VoiceState.ERROR -> "Recovering…"
            VoiceState.IDLE               -> "JARVIS Assistant Active"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1001, buildForegroundNotification().setContentText(text).build())
    }

    fun speakResponse(text: String) {
        Log.i("JarvisService", "Speaking: '$text'")
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
        if (::voiceRuntime.isInitialized) voiceRuntime.release()
        onUtterance          = null
        onResponseDone       = null
        onWakeToggled        = null
        onStateChanged       = null
        onEnvironmentChanged = null
        onAudioMetrics       = null
        speak                = null
        toggleWakeListening  = null
        setWakeSensitivity   = null
        startCommandListening = null
        setSpeechRate        = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
