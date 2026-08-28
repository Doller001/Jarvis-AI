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

    fun nextMedia(): Boolean {
        Log.i("MediaController", "Next media track")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousMedia(): Boolean {
        Log.i("MediaController", "Previous media track")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun stopMedia(): Boolean {
        Log.i("MediaController", "Stop media")
        return dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)
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

class GalleryController(private val context: Context? = null) {
    fun openGallery(): Boolean {
        Log.i("GalleryController", "Opening gallery")
        val ctx = context ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to gallery package intents
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
                true
            } catch (ex: Exception) {
                Log.e("GalleryController", "Failed to open gallery", ex)
                false
            }
        }
    }

    fun getLatestPhotoCount(): Int {
        val ctx = context ?: return 0
        return try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cursor = ctx.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )
            cursor?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e("GalleryController", "Failed to query photo count", e)
            0
        }
    }
}

class CallLogController(private val context: Context? = null) {
    fun getRecentCalls(limit: Int = 5): List<String> {
        val ctx = context ?: return emptyList()
        val calls = mutableListOf<String>()
        return try {
            val cursor = ctx.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.CACHED_NAME,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.DATE
                ),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT $limit"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val number = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER))
                    val name = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.CACHED_NAME)) ?: number
                    val type = when (it.getInt(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))) {
                        android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        android.provider.CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Call"
                    }
                    calls.add("$type: $name ($number)")
                }
            }
            calls
        } catch (e: Exception) {
            Log.e("CallLogController", "Failed to query call log", e)
            emptyList()
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

    fun takeSelfie(): Boolean {
        Log.i("CameraController", "Opening front camera for selfie")
        val ctx = context ?: return false
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("CameraController", "Failed to open front camera", e)
            false
        }
    }
}

class NotificationController(private val context: Context? = null) {
    fun readNotifications(packageFilter: String? = null): List<String> {
        Log.i("NotificationController", "Reading active notifications")
        val ctx = context ?: return emptyList()
        return com.jarvis.assistant.services.JarvisNotificationListenerService
            .getActiveNotificationsList(ctx, packageFilter)
    }
}

class ContactsController(private val context: Context? = null) {
    fun lookupPhoneNumber(name: String): String? {
        val ctx = context ?: return null
        val clean = name.trim().lowercase()
        val searchNames = when (clean) {
            "mom", "maa", "mummy", "mother", "ammi" -> listOf("mom", "maa", "mummy", "mother", "ammi")
            "dad", "papa", "father", "abbu", "daddy" -> listOf("papa", "dad", "father", "abbu", "daddy")
            else -> listOf(clean)
        }

        for (queryName in searchNames) {
            try {
                val resolver: ContentResolver = ctx.contentResolver
                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$queryName%"),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val num = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        if (num.isNotBlank()) return num
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactsController", "Failed to query phone number for $queryName", e)
            }
        }
        return null
    }

    fun lookupContact(name: String): String {
        val ctx = context ?: return "Contact: $name"
        val clean = name.trim().lowercase()
        val searchNames = when (clean) {
            "mom", "maa", "mummy", "mother", "ammi" -> listOf("mom", "maa", "mummy", "mother", "ammi")
            "dad", "papa", "father", "abbu", "daddy" -> listOf("papa", "dad", "father", "abbu", "daddy")
            else -> listOf(clean)
        }

        for (queryName in searchNames) {
            try {
                val resolver: ContentResolver = ctx.contentResolver
                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$queryName%"),
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
                Log.e("ContactsController", "Failed to query contacts for $queryName", e)
            }
        }
        return "Contact: $name"
    }
}

class CallController(private val context: Context? = null) {
    private val contactsController = ContactsController(context)

    fun makeCall(recipient: String): Boolean {
        Log.i("CallController", "Calling $recipient")
        val ctx = context ?: return false
        val phoneNumber = if (recipient.any { it.isLetter() }) {
            contactsController.lookupPhoneNumber(recipient) ?: recipient
        } else {
            recipient
        }
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")
        val target = if (cleanNumber.isNotEmpty()) cleanNumber else recipient
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$target")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to DIAL intent if permission or tel uri requires user confirmation
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$target")
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
    private val contactsController = ContactsController(context)

    fun sendSms(recipient: String, message: String): Boolean {
        Log.i("SmsController", "Sending SMS to $recipient: '$message'")
        val ctx = context ?: return false
        val targetNumber = if (recipient.any { it.isLetter() }) {
            contactsController.lookupPhoneNumber(recipient) ?: recipient
        } else {
            recipient
        }
        val cleanTarget = targetNumber.replace("[^0-9+]".toRegex(), "").ifEmpty { targetNumber }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(cleanTarget, null, message, null, null)
            true
        } catch (e: Exception) {
            // Fallback to SMS Intent
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:${Uri.encode(cleanTarget)}")
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
        val phone = if (contactNameOrPhone.any { it.isLetter() }) {
            contactsController.lookupPhoneNumber(contactNameOrPhone) ?: contactNameOrPhone
        } else {
            contactNameOrPhone
        }
        val cleanNumber = phone.replace("[^0-9]".toRegex(), "")
        val targetPhone = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
        val uri = if (targetPhone.length >= 7) {
            Uri.parse("https://api.whatsapp.com/send?phone=$targetPhone&text=${Uri.encode(message)}")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
        }

        val started = try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                Log.e("SmsController", "Failed to start WhatsApp intent", ex)
                false
            }
        }

        if (started) {
            // Auto-click WhatsApp Send button as the end action
            Thread {
                try {
                    val acc = com.jarvis.assistant.accessibility.AccessibilityController()
                    val sendIds = listOf(
                        "com.whatsapp:id/send",
                        "com.whatsapp.w4b:id/send",
                        "com.whatsapp:id/conversation_send_button"
                    )
                    val sendLabels = listOf("Send", "send", "भेजें", "bhejo")
                    var sent = false
                    for (attempt in 1..10) {
                        Thread.sleep(350)
                        for (id in sendIds) {
                            if (acc.tapById(id)) {
                                sent = true
                                break
                            }
                        }
                        if (sent) break
                        for (label in sendLabels) {
                            if (acc.tap(label)) {
                                sent = true
                                break
                            }
                        }
                        if (sent) break
                    }
                    Log.i("SmsController", "WhatsApp auto-click send result: $sent")
                } catch (e: Exception) {
                    Log.w("SmsController", "Accessibility auto-send error", e)
                }
            }.start()
        }

        return started
    }
}
