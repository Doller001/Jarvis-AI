package com.jarvis.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("reminder_text") ?: "Reminder"
        Log.i("ReminderReceiver", "Firing reminder: $text")
        // Speak via the foreground service if running, else show a notification.
        if (JarvisForegroundService.isRunning) {
            JarvisForegroundService.speak?.invoke("Reminder, Sir: $text")
        } else {
            com.jarvis.assistant.device.NotificationControllerCompat
                .showReminderNotification(context, text)
        }
    }
}
