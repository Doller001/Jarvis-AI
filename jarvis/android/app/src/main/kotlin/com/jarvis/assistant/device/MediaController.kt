package com.jarvis.assistant.device

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent

class MediaController(private val context: Context? = null) {
    fun playMedia(): Boolean {
        Log.i("MediaController", "Play media")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun pauseMedia(): Boolean {
        Log.i("MediaController", "Pause media")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun togglePlayPause(): Boolean {
        Log.i("MediaController", "Toggle play/pause media")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    private fun dispatchKeyEvent(keyCode: Int): Boolean {
        val ctx = context ?: return false
        return try {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (e: Exception) {
            Log.e("MediaController", "Error dispatching media key $keyCode", e)
            false
        }
    }
}

class CameraController(private val context: Context? = null) {
    fun openCamera(): Boolean {
        Log.i("CameraController", "Opening camera")
        val ctx = context ?: return false
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("CameraController", "Failed to open camera", e)
            false
        }
    }

    fun takePhoto(): Boolean {
        Log.i("CameraController", "Taking photo via camera intent")
        return openCamera()
    }
}

class NotificationController(private val context: Context? = null) {
    fun readNotifications(): List<String> {
        Log.i("NotificationController", "Reading active notifications")
        val ctx = context ?: return listOf("Jarvis Assistant is running in foreground.")
        return com.jarvis.assistant.services.JarvisNotificationListenerService.getActiveNotificationsList(ctx)
    }
}

class ContactsController(private val context: Context? = null) {
    fun lookupContact(name: String): String {
        val ctx = context ?: return "Contact: $name"
        try {
            val resolver: ContentResolver = ctx.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    return "$displayName ($number)"
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsController", "Failed to query contacts for $name", e)
        }
        return "Contact: $name"
    }
}

class CallController(private val context: Context? = null) {
    fun makeCall(recipient: String): Boolean {
        Log.i("CallController", "Calling $recipient")
        val ctx = context ?: return false
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${recipient.replace(" ", "")}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to DIAL intent if permission or tel uri requires user confirmation
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${recipient.replace(" ", "")}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(dialIntent)
                true
            } catch (ex: Exception) {
                Log.e("CallController", "Failed to initiate call to $recipient", ex)
                false
            }
        }
    }
}

class SmsController(private val context: Context? = null) {
    fun sendSms(recipient: String, message: String): Boolean {
        Log.i("SmsController", "Sending SMS to $recipient: '$message'")
        val ctx = context ?: return false
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(recipient, null, message, null, null)
            true
        } catch (e: Exception) {
            // Fallback to SMS Intent
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${Uri.encode(recipient)}")
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
                true
            } catch (ex: Exception) {
                Log.e("SmsController", "Failed to send SMS to $recipient", ex)
                false
            }
        }
    }

    fun sendWhatsApp(contactNameOrPhone: String, message: String): Boolean {
        Log.i("SmsController", "Sending WhatsApp to $contactNameOrPhone: '$message'")
        val ctx = context ?: return false
        return try {
            val cleanNumber = contactNameOrPhone.replace("[^0-9]".toRegex(), "")
            val uri = if (cleanNumber.length >= 7) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("SmsController", "Failed to send WhatsApp message", e)
            false
        }
    }
}
