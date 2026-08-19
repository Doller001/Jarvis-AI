package com.jarvis.assistant.device

import android.util.Log

class MediaController {
    fun playMedia() = Log.i("MediaController", "Play media")
    fun pauseMedia() = Log.i("MediaController", "Pause media")
}

class CameraController {
    fun openCamera() = Log.i("CameraController", "Opening camera")
    fun takePhoto() = Log.i("CameraController", "Taking photo")
}

class NotificationController {
    fun readNotifications() = Log.i("NotificationController", "Reading notifications")
}

class ContactsController {
    fun lookupContact(name: String): String = "Contact: $name"
}

class CallController {
    fun makeCall(recipient: String) = Log.i("CallController", "Calling $recipient")
}

class SmsController {
    fun sendSms(recipient: String, message: String) = Log.i("SmsController", "Sending SMS to $recipient: '$message'")
}
