package com.jarvis.assistant.vision

import android.graphics.Bitmap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import java.io.OutputStream

class VisionCaptureManagerTest {

    @Test
    fun `scaleBitmap returns original bitmap when within bounds`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(800)
        `when`(mockBitmap.height).thenReturn(600)

        val result = VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 1024)
        assertSame(mockBitmap, result)
    }

    @Test
    fun `scaleBitmap returns original bitmap when dimensions exactly equal maxDimension`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(1024)
        `when`(mockBitmap.height).thenReturn(768)

        val result = VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 1024)
        assertSame(mockBitmap, result)
    }

    @Test
    fun `scaleBitmap returns original bitmap for invalid non-positive maxDimension`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(1920)
        `when`(mockBitmap.height).thenReturn(1080)

        assertSame(mockBitmap, VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 0))
        assertSame(mockBitmap, VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = -100))
    }

    @Test
    fun `scaleBitmap scales landscape bitmap proportionally when oversized`() {
        val mockBitmap = mock(Bitmap::class.java)
        val mockScaledBitmap = mock(Bitmap::class.java)

        `when`(mockBitmap.width).thenReturn(1920)
        `when`(mockBitmap.height).thenReturn(1080)

        mockStatic(Bitmap::class.java).use { mockedStatic ->
            mockedStatic.`when`<Bitmap> {
                Bitmap.createScaledBitmap(mockBitmap, 960, 540, true)
            }.thenReturn(mockScaledBitmap)

            val result = VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 960)
            assertSame(mockScaledBitmap, result)
        }
    }

    @Test
    fun `scaleBitmap scales portrait bitmap proportionally when oversized`() {
        val mockBitmap = mock(Bitmap::class.java)
        val mockScaledBitmap = mock(Bitmap::class.java)

        `when`(mockBitmap.width).thenReturn(1080)
        `when`(mockBitmap.height).thenReturn(1920)

        mockStatic(Bitmap::class.java).use { mockedStatic ->
            mockedStatic.`when`<Bitmap> {
                Bitmap.createScaledBitmap(mockBitmap, 540, 960, true)
            }.thenReturn(mockScaledBitmap)

            val result = VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 960)
            assertSame(mockScaledBitmap, result)
        }
    }

    @Test
    fun `scaleBitmap scales square bitmap proportionally when oversized`() {
        val mockBitmap = mock(Bitmap::class.java)
        val mockScaledBitmap = mock(Bitmap::class.java)

        `when`(mockBitmap.width).thenReturn(2048)
        `when`(mockBitmap.height).thenReturn(2048)

        mockStatic(Bitmap::class.java).use { mockedStatic ->
            mockedStatic.`when`<Bitmap> {
                Bitmap.createScaledBitmap(mockBitmap, 1024, 1024, true)
            }.thenReturn(mockScaledBitmap)

            val result = VisionCaptureManager.scaleBitmap(mockBitmap, maxDimension = 1024)
            assertSame(mockScaledBitmap, result)
        }
    }

    @Test
    fun `encodeBitmapToBase64 compresses and encodes to valid Base64 string`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(640)
        `when`(mockBitmap.height).thenReturn(480)

        val testBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        `when`(mockBitmap.compress(eq(Bitmap.CompressFormat.JPEG), eq(85), any())).thenAnswer { invocation ->
            val os = invocation.getArgument<OutputStream>(2)
            os.write(testBytes)
            true
        }

        val base64 = VisionCaptureManager.encodeBitmapToBase64(mockBitmap)

        assertNotNull(base64)
        assertTrue(base64.isNotEmpty())
        val decoded = java.util.Base64.getDecoder().decode(base64)
        assertArrayEquals(testBytes, decoded)
    }

    @Test
    fun `encodeBitmapToBase64 respects custom quality and maxDimension parameters`() {
        val mockBitmap = mock(Bitmap::class.java)
        val mockScaled = mock(Bitmap::class.java)

        `when`(mockBitmap.width).thenReturn(2000)
        `when`(mockBitmap.height).thenReturn(1000)

        val testData = "custom_image_bytes".toByteArray()
        `when`(mockScaled.compress(eq(Bitmap.CompressFormat.JPEG), eq(60), any())).thenAnswer { invocation ->
            val os = invocation.getArgument<OutputStream>(2)
            os.write(testData)
            true
        }

        mockStatic(Bitmap::class.java).use { mockedStatic ->
            mockedStatic.`when`<Bitmap> {
                Bitmap.createScaledBitmap(mockBitmap, 500, 250, true)
            }.thenReturn(mockScaled)

            val base64 = VisionCaptureManager.encodeBitmapToBase64(mockBitmap, maxDimension = 500, quality = 60)

            assertNotNull(base64)
            val decoded = java.util.Base64.getDecoder().decode(base64)
            assertArrayEquals(testData, decoded)
        }
    }
}
