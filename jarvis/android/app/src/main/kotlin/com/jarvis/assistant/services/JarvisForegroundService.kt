package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.jarvis.assistant.voice.VoiceRuntime
import com.jarvis.assistant.voice.VoiceState

class JarvisForegroundService : Service() {
    private var voiceRuntime = VoiceRuntime(context = null)

    override fun onCreate() {
        super.onCreate()
        voiceRuntime = VoiceRuntime(applicationContext)
        speak = { text ->
            voiceRuntime.speakResponse(text) { onResponseDone?.invoke() }
        }
        toggleWakeListening = {
            val active = voiceRuntime.toggleMonitoring()
            onWakeToggled?.invoke(active)
            active
        }
        Log.i("JarvisService", "Starting JarvisForegroundService runtime...")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification().build()
        startForeground(1001, notification)

        voiceRuntime.setStateListener { state ->
            onStateChanged?.invoke(state)
            updateNotification(state)
        }
        voiceRuntime.startRuntime { userUtterance ->
            Log.i("JarvisService", "Received utterance in foreground service: '$userUtterance'")
            onUtterance?.invoke(userUtterance)
        }

        return START_STICKY
    }

    private fun updateNotification(state: VoiceState) {
        val text = when (state) {
            VoiceState.STOPPED, VoiceState.STARTING -> "Starting…"
            VoiceState.WAKE_LISTENING -> "Listening for 'Hey Jarvis'"
            VoiceState.WAKE_DETECTED, VoiceState.COMMAND_LISTENING -> "Listening…"
            VoiceState.PROCESSING -> "Processing…"
            VoiceState.SPEAKING -> "Speaking…"
            VoiceState.ERROR -> "Recovering…"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(1001, buildForegroundNotification().setContentText(text).build())
    }

    fun speakResponse(text: String) {
        Log.i("JarvisService", "Speaking response: '$text'")
        voiceRuntime.speakResponse(text) {
            onResponseDone?.invoke()
        }
    }

    companion object {
        var onUtterance: ((String) -> Unit)? = null
        var onResponseDone: (() -> Unit)? = null
        var onWakeToggled: ((Boolean) -> Unit)? = null
        var onStateChanged: ((VoiceState) -> Unit)? = null
        var speak: ((String) -> Unit)? = null
        var toggleWakeListening: (() -> Boolean)? = null
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
            .setContentText("Listening for 'Hey Jarvis'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
    }

    override fun onDestroy() {
        voiceRuntime.release()
        onUtterance = null
        onResponseDone = null
        onWakeToggled = null
        onStateChanged = null
        speak = null
        toggleWakeListening = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}