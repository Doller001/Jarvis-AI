package com.jarvis.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadEngineTest {

    @Test
    fun testActivationState() {
        val vad = VadEngine()
        assertFalse(vad.isActive)
        vad.activate()
        assertTrue(vad.isActive)
        vad.deactivate()
        assertFalse(vad.isActive)
    }

    @Test
    fun testRmsCalculationOnSilenceAndSound() {
        val vad = VadEngine()

        val silence = ShortArray(1600) { 0 }
        val rmsSilence = vad.calculateRms(silence)
        assertEquals(0.0, rmsSilence, 0.0001)

        val silenceDb = vad.rmsToDb(rmsSilence)
        assertEquals(-100f, silenceDb, 0.1f)

        // Loud signal: constant near full scale
        val loud = ShortArray(1600) { 30000 }
        val rmsLoud = vad.calculateRms(loud)
        assertTrue(rmsLoud > 0.8)

        val loudDb = vad.rmsToDb(rmsLoud)
        assertTrue(loudDb > -5f)
    }

    @Test
    fun testVoiceActivityDetection() {
        val vad = VadEngine(thresholdDb = -40f, hysteresisDb = 3f)
        vad.activate()

        // Silence frame (all 0) -> dB is -100dB -> inactive
        val silence = ShortArray(1600) { 0 }
        assertFalse(vad.isVoiceActive(silence))

        // Loud frame (amplitude 15000 -> normalized ~0.45 -> ~-7dB) -> active
        val speech = ShortArray(1600) { 15000 }
        assertTrue(vad.isVoiceActive(speech))

        // Deactivated VAD should allow all audio
        vad.deactivate()
        assertTrue(vad.isVoiceActive(silence))
    }

    @Test
    fun testCalibration() {
        val vad = VadEngine(thresholdDb = -45f)
        vad.activate()

        val quietFrames = List(20) {
            ShortArray(1600) { ((it % 50) - 25).toShort() } // Very quiet noise
        }
        vad.calibrate(quietFrames)

        // Threshold should be adjusted based on quiet noise floor
        assertTrue(vad.getThresholdDb() < -30f)
    }
}
