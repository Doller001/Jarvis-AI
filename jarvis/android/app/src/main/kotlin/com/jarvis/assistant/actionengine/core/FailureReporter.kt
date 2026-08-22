package com.jarvis.assistant.actionengine.core

import com.jarvis.assistant.actionengine.model.Failure
import com.jarvis.assistant.actionengine.model.FailureCode

object FailureReporter {

    fun formatUserMessage(failure: Failure): String {
        return when (failure.code) {
            FailureCode.APP_NOT_INSTALLED -> "Yeh app aapke phone mein install nahi hai."
            FailureCode.APP_NOT_OPENED -> "App open karne mein dikkat aayi."
            FailureCode.ELEMENT_NOT_FOUND -> "Screen par target element nahi mila."
            FailureCode.ELEMENT_NOT_CLICKABLE -> "Element click karne ke liye available nahi hai."
            FailureCode.PERMISSION_MISSING -> "Is kaam ke liye zaroori permission nahi mili."
            FailureCode.PERMISSION_DENIED -> "Permission allow nahi ki gayi."
            FailureCode.TIMEOUT, FailureCode.ACTION_TIMEOUT -> "Action complete hone mein waqt zyada lag gaya."
            FailureCode.VERIFICATION_FAILED -> "Action ka expected result verify nahi ho saka."
            FailureCode.USER_CANCELLED -> "Task cancel kar diya gaya."
            FailureCode.UNKNOWN_ERROR -> failure.message.ifBlank { "Kuch gadbad hui, dobara koshish karein." }
        }
    }
}
