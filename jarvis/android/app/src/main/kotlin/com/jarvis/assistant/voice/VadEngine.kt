package com.jarvis.assistant.voice

import android.util.Log
import java.util.ArrayDeque

/**
 * Energy-based Voice Activity Detection (VAD) engine.
 * Calculates Root Mean Square (RMS) energy in decibels (dBFS)
 * to distinguish speech from ambient silence or low background noise.
 */
class VadEngine(
    private var thresholdDb: Float = -45f,
    private var hysteresisDb: Float = 3f,
    private val historySize: Int = 100
) {
    companion object {
        private const val TAG = "VadEngine"
        private const val DEFAULT_THRESHOLD_DB = -45f
    }

    var isActive = false
        private set

    private var speechDetected = false
    private val frameHistory = ArrayDeque<Float>()
    private var noiseFloorDb: Float = -55f

    fun activate() {
        isActive = true
        speechDetected = false
        Log.d(TAG, "Voice Activity Detection activated.")
    }

    fun deactivate() {
        isActive = false
        speechDetected = false
        frameHistory.clear()
        Log.d(TAG, "Voice Activity Detection deactivated.")
    }

    /**
     * Calculates the RMS (Root Mean Square) energy of 16-bit PCM audio samples.
     * Normalized between 0.0 and 1.0.
     */
    fun calculateRms(pcmSamples: ShortArray): Double {
        if (pcmSamples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in pcmSamples) {
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
        }
        return Math.sqrt(sumSquares / pcmSamples.size)
    }

    /**
     * Converts normalized RMS amplitude (0.0 .. 1.0) to decibels relative to full scale (dBFS).
     */
    fun rmsToDb(rms: Double): Float {
        return if (rms > 1e-9) {
            (20.0 * Math.log10(rms)).toFloat().coerceIn(-100f, 0f)
        } else {
            -100f
        }
    }

    /**
     * Evaluates whether speech is present in the provided PCM audio frame.
     * Uses a dual-threshold hysteresis scheme to prevent rapid toggling.
     */
    fun isVoiceActive(pcmSamples: ShortArray): Boolean {
        if (!isActive) return true // If VAD is disabled/inactive, don't filter out audio
        val rms = calculateRms(pcmSamples)
        val rmsDb = rmsToDb(rms)
        return isVoiceActive(rmsDb)
    }

    /**
     * Evaluates whether speech is active given a pre-computed dBFS level.
     */
    fun isVoiceActive(rmsDb: Float): Boolean {
        if (!isActive) return true

        synchronized(frameHistory) {
            frameHistory.addLast(rmsDb)
            if (frameHistory.size > historySize) {
                frameHistory.removeFirst()
            }
        }

        // Hysteresis logic
        speechDetected = if (speechDetected) {
            // Lower threshold to stay in speech state
            rmsDb > (thresholdDb - hysteresisDb)
        } else {
            // Higher threshold to enter speech state
            rmsDb > thresholdDb
        }

        return speechDetected
    }

    /**
     * Calibrates the noise floor using background ambient noise frames.
     */
    fun calibrate(ambientFrames: List<ShortArray>) {
        if (ambientFrames.isEmpty()) return
        val dbValues = ambientFrames.map { rmsToDb(calculateRms(it)) }
        val sorted = dbValues.sorted()
        // Take 30th percentile as the ambient noise floor
        val index = (sorted.size * 0.3).toInt().coerceIn(0, sorted.size - 1)
        noiseFloorDb = sorted[index]
        thresholdDb = (noiseFloorDb + 12f).coerceIn(-60f, -20f)
        Log.i(TAG, "Calibrated VAD: noise floor = $noiseFloorDb dB, threshold = $thresholdDb dB")
    }

    /**
     * Dynamically updates the threshold based on recent quiet frame history.
     */
    fun updateThresholdFromHistory() {
        synchronized(frameHistory) {
            if (frameHistory.size < 20) return
            val sorted = frameHistory.sorted()
            val noiseIndex = (sorted.size * 0.25).toInt().coerceIn(0, sorted.size - 1)
            val measuredFloor = sorted[noiseIndex]
            val targetThreshold = (measuredFloor + 10f).coerceIn(-60f, -20f)
            // Smooth adaptation
            thresholdDb = (thresholdDb * 0.8f) + (targetThreshold * 0.2f)
        }
    }

    fun getThresholdDb(): Float = thresholdDb

    fun setThresholdDb(db: Float) {
        thresholdDb = db.coerceIn(-80f, 0f)
    }
}
