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

        fun getActiveNotificationsList(context: Context, packageFilter: String? = null): List<String> {
            val service = instance
            if (service != null) {
                try {
                    val sbns = service.activeNotifications
                    val list = mutableListOf<String>()
                    for (sbn in sbns) {
                        if (packageFilter != null && sbn.packageName != packageFilter &&
                            !(packageFilter == "whatsapp" && sbn.packageName.startsWith("com.whatsapp"))) continue
                        val extras = sbn.notification.extras
                        val title = cleanForSpeech(extras.getCharSequence("android.title")?.toString().orEmpty())
                        val text = cleanForSpeech(extras.getCharSequence("android.text")?.toString().orEmpty())
                        val app = try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(sbn.packageName, 0)
                            ).toString()
                        } catch (_: Exception) {
                            sbn.packageName.substringAfterLast('.')
                        }
                        val body = listOf(title, text).filter { it.isNotBlank() }.distinct().joinToString(": ")
                        if (body.isNotBlank() && !isNoise(body)) {
                            list.add("$app: $body")
                        }
                    }
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) {
                    Log.e("NotificationService", "Failed to retrieve active notifications", e)
                }
            }
            return emptyList()
        }

        fun cleanForSpeech(value: String): String {
            return value
                .replace(Regex("[^\\p{L}\\p{N}\\s.,!?@:/&'()%-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun isNoise(value: String): Boolean {
            val lower = value.lowercase()
            return lower == "jarvis assistant" || lower.contains("foreground listener active")
        }
    }
}
