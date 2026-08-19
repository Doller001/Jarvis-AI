package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.jarvis.assistant.voice.VoiceRuntime

class JarvisForegroundService : Service() {
    private var voiceRuntime = VoiceRuntime(context = null)

    override fun onCreate() {
        super.onCreate()
        voiceRuntime = VoiceRuntime(applicationContext)
        Log.i("JarvisService", "Starting JarvisForegroundService runtime...")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(1001, notification)
        
        voiceRuntime.startRuntime { userUtterance ->
            Log.i("JarvisService", "Received utterance in foreground service: '$userUtterance'")
            onUtterance?.invoke(userUtterance)
        }
        
        return START_STICKY
    }

    companion object {
        var onUtterance: ((String) -> Unit)? = null
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

    private fun buildForegroundNotification(): Notification {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, "jarvis_runtime")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("JARVIS")
            .setContentText("Ready — listening for wake word 'Jarvis'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        voiceRuntime.stopRuntime()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
