package com.jarvis.assistant.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class NearFieldAudioProcessorTest {

    private val processor = NearFieldAudioProcessor()

    @Test
    fun testSilenceFrameProcessing() {
        val silentFrame = ShortArray(160) { 0 }
        val result = processor.processFrame(silentFrame)

        assertFalse(result.isVoiceActive)
        assertFalse(result.isNearestVoice)
        assertTrue(result.noiseFloorDbfs <= -50f)
    }

    @Test
    fun testButterworthHighPassFilterAttenuatesSubBass() {
        val sampleRate = 16000
        val lowFreq = 40.0 // 40 Hz sub-bass rumble
        val subBassFrame = ShortArray(160) { i ->
            (sin(2.0 * Math.PI * lowFreq * i / sampleRate) * 10000).toInt().toShort()
        }

        val result = processor.processFrame(subBassFrame)
        // Sub-bass should be filtered out by 85Hz high-pass filter
        assertTrue(result.snrDb < 20.0f)
    }

    @Test
    fun testNearFieldDominancePassesDominantVoice() {
        val sampleRate = 16000
        val speechFreq = 450.0 // 450 Hz typical human speech formant
        val loudSpeechFrame = ShortArray(160) { i ->
            (sin(2.0 * Math.PI * speechFreq * i / sampleRate) * 16000).toInt().toShort()
        }

        // Warm up processor noise floor
        repeat(5) {
            processor.processFrame(loudSpeechFrame)
        }

        val result = processor.processFrame(loudSpeechFrame)
        assertTrue(result.isVoiceActive)
        assertTrue(result.snrDb > 5.0f)
    }

    @Test
    fun testAdaptiveEnvironmentProfileSwitching() {
        processor.setEnvironment(EnvironmentProfile.OUTDOOR_NOISY)
        assertEquals(EnvironmentProfile.OUTDOOR_NOISY, processor.currentProfile)

        processor.setEnvironment(EnvironmentProfile.INDOOR_QUIET)
        assertEquals(EnvironmentProfile.INDOOR_QUIET, processor.currentProfile)
    }
}
