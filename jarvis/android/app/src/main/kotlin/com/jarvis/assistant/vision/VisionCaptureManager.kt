package com.jarvis.assistant.vision

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Utility for scaling and encoding visual camera/screen capture frames for multimodal transmission.
 */
object VisionCaptureManager {

    /**
     * Proportionally scales a [Bitmap] down so that neither width nor height exceeds [maxDimension].
     * If the bitmap is already within the specified dimension bounds, returns the original [bitmap].
     */
    fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (maxDimension <= 0 || (width <= maxDimension && height <= maxDimension)) {
            return bitmap
        }

        val targetWidth: Int
        val targetHeight: Int

        if (width >= height) {
            targetWidth = maxDimension
            targetHeight = ((height.toFloat() / width.toFloat()) * maxDimension).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = ((width.toFloat() / height.toFloat()) * maxDimension).toInt().coerceAtLeast(1)
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Compresses and encodes the given [Bitmap] to a standard Base64 JPEG string.
     *
     * @param bitmap The source image bitmap.
     * @param maxDimension The maximum width or height permitted before scaling down (default: 1024).
     * @param quality The JPEG compression quality between 0 and 100 (default: 85).
     * @return Standard Base64 encoded string representing the JPEG image bytes.
     */
    fun encodeBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 85): String {
        val scaled = scaleBitmap(bitmap, maxDimension)
        val outputStream = ByteArrayOutputStream()
        val clampedQuality = quality.coerceIn(0, 100)
        scaled.compress(Bitmap.CompressFormat.JPEG, clampedQuality, outputStream)
        val bytes = outputStream.toByteArray()

        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
                ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }
}
