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
import com.jarvis.assistant.voice.VoiceRuntime
import com.jarvis.assistant.voice.VoiceState

class JarvisForegroundService : Service() {
    private lateinit var voiceRuntime: VoiceRuntime
    private val brain by lazy { JarvisBrain() }
    private val commandExecutor by lazy { CommandExecutor(applicationContext) }

    companion object {
        var isRunning: Boolean = false
            private set
        var onUtterance: ((String) -> Unit)? = null
        var onResponseDone: (() -> Unit)? = null
        var onStateChanged: ((VoiceState) -> Unit)? = null
        var onEnvironmentChanged: ((com.jarvis.assistant.voice.EnvironmentProfile) -> Unit)? = null
        var onAudioMetrics: ((com.jarvis.assistant.voice.AudioProcessingResult) -> Unit)? = null
        var speak: ((String) -> Unit)? = null
        var startCommandListening: (() -> Unit)? = null
        var setSpeechRate: ((Float) -> Unit)? = null

        const val ACTION_START = "com.jarvis.assistant.START"
        const val ACTION_STOP = "com.jarvis.assistant.STOP"
        const val ACTION_LISTEN_FOR_COMMAND = "com.jarvis.assistant.LISTEN_FOR_COMMAND"
    }

    override fun onCreate() {
        super.onCreate()
        voiceRuntime = VoiceRuntime(applicationContext)
        speak = { text ->
            voiceRuntime.speakResponse(text) { onResponseDone?.invoke() }
        }
        startCommandListening = {
            voiceRuntime.startListeningForCommand()
        }
        setSpeechRate = { rate ->
            voiceRuntime.setSpeechRate(rate)
        }
        Log.i("JarvisService", "Starting JarvisForegroundService runtime...")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        isRunning = true
        val notification = buildForegroundNotification().build()
        startForeground(1001, notification)

        voiceRuntime.setStateListener { state ->
            onStateChanged?.invoke(state)
            updateNotification(state)
        }
        voiceRuntime.onEnvironmentChanged = { env ->
            onEnvironmentChanged?.invoke(env)
        }
        voiceRuntime.onAudioMetrics = { metrics ->
            onAudioMetrics?.invoke(metrics)
        }
        voiceRuntime.startRuntime { userUtterance ->
            Log.i("JarvisService", "Received utterance in foreground service: '$userUtterance'")
            val uiHandler = onUtterance
            if (uiHandler != null) {
                uiHandler(userUtterance)
            } else {
                // The service can outlive the activity.  Keep local commands
                // working in that state, while never executing a risky action
                // without the UI's confirmation flow.
                val plan = brain.processCommand(userUtterance)
                val response = if (plan.requiresConfirmation) {
                    "Please open Jarvis to confirm this action."
                } else {
                    commandExecutor.execute(plan.intent)
                }
                voiceRuntime.speakResponse(response)
            }
        }

        if (intent?.action == ACTION_LISTEN_FOR_COMMAND) {
            voiceRuntime.startListeningForCommand()
        }

        return START_STICKY
    }

    private fun updateNotification(state: VoiceState) {
        val text = when (state) {
            VoiceState.STOPPED, VoiceState.STARTING -> "JARVIS Assistant Active"
            VoiceState.COMMAND_LISTENING -> "Listening for command…"
            VoiceState.PROCESSING -> "Processing…"
            VoiceState.SPEAKING -> "Speaking…"
            VoiceState.ERROR -> "Recovering…"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1001, buildForegroundNotification().setContentText(text).build())
    }

    fun speakResponse(text: String) {
        Log.i("JarvisService", "Speaking response: '$text'")
        if (::voiceRuntime.isInitialized) {
            voiceRuntime.speakResponse(text) {
                onResponseDone?.invoke()
            }
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "jarvis_runtime",
                "Jarvis Runtime Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
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
        if (::voiceRuntime.isInitialized) {
            voiceRuntime.release()
        }
        onUtterance = null
        onResponseDone = null
        onStateChanged = null
        onEnvironmentChanged = null
        onAudioMetrics = null
        speak = null
        startCommandListening = null
        setSpeechRate = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
