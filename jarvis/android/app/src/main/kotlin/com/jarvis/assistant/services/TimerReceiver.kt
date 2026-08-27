package com.jarvis.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("timer_label") ?: "Timer"
        val seconds = intent.getIntExtra("timer_seconds", 0)
        Log.i("TimerReceiver", "Timer finished: $label ($seconds s)")
        if (JarvisForegroundService.isRunning) {
            JarvisForegroundService.speak?.invoke("Timer complete, Sir: $label")
        } else {
            com.jarvis.assistant.device.NotificationControllerCompat
                .showReminderNotification(context, "Timer complete: $label")
        }
    }
}
