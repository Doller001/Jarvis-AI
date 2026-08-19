package com.jarvis.assistant.services

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i("NotificationService", "Jarvis Notification Listener Service Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
        Log.i("NotificationService", "Jarvis Notification Listener Service Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val title = it.notification?.extras?.getCharSequence("android.title")?.toString() ?: ""
            val text = it.notification?.extras?.getCharSequence("android.text")?.toString() ?: ""
            if (title.isNotBlank() || text.isNotBlank()) {
                Log.d("NotificationService", "Posted: [$title] $text (pkg: ${it.packageName})")
            }
        }
    }

    companion object {
        private var instance: JarvisNotificationListenerService? = null

        fun getActiveNotificationsList(context: Context): List<String> {
            val service = instance
            if (service != null) {
                try {
                    val sbns = service.activeNotifications
                    val list = mutableListOf<String>()
                    for (sbn in sbns) {
                        val extras = sbn.notification.extras
                        val title = extras.getCharSequence("android.title")?.toString() ?: ""
                        val text = extras.getCharSequence("android.text")?.toString() ?: ""
                        val app = sbn.packageName.substringAfterLast(".")
                        if (title.isNotBlank() || text.isNotBlank()) {
                            list.add("[$app] $title: $text")
                        }
                    }
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) {
                    Log.e("NotificationService", "Failed to retrieve active notifications", e)
                }
            }
            return listOf("Jarvis Assistant foreground listener active.")
        }
    }
}
