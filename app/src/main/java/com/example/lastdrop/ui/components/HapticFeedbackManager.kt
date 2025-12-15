package com.example.lastdrop.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * HapticFeedbackManager - Centralized haptic feedback controller
 * 
 * Features:
 * - Contextual vibration patterns for game events
 * - Android 12+ VibratorManager support
 * - Fallback for older devices
 * - Haptic enable/disable toggle
 * 
 * Usage:
 * ```
 * hapticManager.vibrateDiceRoll()
 * hapticManager.vibrateElimination()
 * hapticManager.setEnabled(false)
 * ```
 */
class HapticFeedbackManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HapticFeedback"
        
        // Vibration durations (milliseconds)
        private const val LIGHT_TAP = 10L
        private const val MEDIUM_TAP = 20L
        private const val HEAVY_TAP = 50L
        private const val SHORT_BUZZ = 100L
        private const val LONG_BUZZ = 200L
    }
    
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    private var isEnabled = true
    
    /**
     * Check if device supports haptic feedback
     */
    fun hasVibrator(): Boolean {
        return vibrator.hasVibrator()
    }
    
    /**
     * Enable or disable haptic feedback
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Log.d(TAG, "Haptic feedback ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if haptics are enabled
     */
    fun isEnabled(): Boolean = isEnabled
    
    // ==================== UI HAPTICS ====================
    
    /**
     * Light tap - for button presses
     */
    fun vibrateButtonClick() {
        vibrate(LIGHT_TAP)
    }
    
    /**
     * Medium tap - for menu interactions
     */
    fun vibrateMenuInteraction() {
        vibrate(MEDIUM_TAP)
    }
    
    /**
     * Heavy tap - for important actions
     */
    fun vibrateImportantAction() {
        vibrate(HEAVY_TAP)
    }
    
    // ==================== DICE HAPTICS ====================
    
    /**
     * Dice roll pattern - rolling effect
     * Pattern: short burst followed by landing
     */
    fun vibrateDiceRoll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 30, 10, 30, 10, 60) // Roll-roll-LAND
            val amplitudes = intArrayOf(0, 100, 0, 120, 0, 180)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(SHORT_BUZZ)
        }
    }
    
    /**
     * Dice land - final result
     */
    fun vibrateDiceLand() {
        vibrate(HEAVY_TAP)
    }
    
    // ==================== GAME EVENT HAPTICS ====================
    
    /**
     * Score gain - success pattern
     * Pattern: quick double tap
     */
    fun vibrateScoreGain() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 150, 0, 180)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(SHORT_BUZZ)
        }
    }
    
    /**
     * Score loss - warning pattern
     * Pattern: single heavy buzz
     */
    fun vibrateScoreLoss() {
        vibrate(HEAVY_TAP)
    }
    
    /**
     * Tile landing - soft thud
     */
    fun vibrateTileLand() {
        vibrate(MEDIUM_TAP)
    }
    
    /**
     * Chance card - special event
     * Pattern: triple tap
     */
    fun vibrateChanceCard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 30, 40, 30, 40, 30)
            val amplitudes = intArrayOf(0, 120, 0, 150, 0, 180)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(SHORT_BUZZ)
        }
    }
    
    /**
     * Player elimination - dramatic pattern
     * Pattern: long descending buzz
     */
    fun vibrateElimination() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 50, 30, 50, 30, 100)
            val amplitudes = intArrayOf(0, 200, 0, 150, 0, 100)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(LONG_BUZZ)
        }
    }
    
    /**
     * Player revived - triumphant pattern
     * Pattern: ascending burst
     */
    fun vibrateRevival() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 30, 20, 40, 20, 60)
            val amplitudes = intArrayOf(0, 100, 0, 150, 0, 255)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(SHORT_BUZZ)
        }
    }
    
    // ==================== VICTORY/DEFEAT HAPTICS ====================
    
    /**
     * Game win - celebration pattern
     * Pattern: rapid ascending bursts
     */
    fun vibrateGameWin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 40, 30, 50, 30, 60, 30, 80)
            val amplitudes = intArrayOf(0, 150, 0, 180, 0, 210, 0, 255)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(LONG_BUZZ)
        }
    }
    
    /**
     * Confetti burst - party effect
     * Pattern: scattered bursts
     */
    fun vibrateConfetti() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 20, 50, 25, 40, 30, 30, 35, 20, 40)
            val amplitudes = intArrayOf(0, 180, 0, 150, 0, 200, 0, 170, 0, 190)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(SHORT_BUZZ)
        }
    }
    
    // ==================== ERROR/WARNING HAPTICS ====================
    
    /**
     * Error - attention grabber
     * Pattern: double heavy buzz
     */
    fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 80, 60, 80)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vibrate(pattern, amplitudes, -1)
        } else {
            vibrate(LONG_BUZZ)
        }
    }
    
    /**
     * Warning - single alert
     */
    fun vibrateWarning() {
        vibrate(HEAVY_TAP)
    }
    
    // ==================== CORE VIBRATION METHODS ====================
    
    /**
     * Simple vibration (duration only)
     */
    private fun vibrate(duration: Long) {
        if (!isEnabled || !hasVibrator()) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
            Log.d(TAG, "Vibrated for ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error: ${e.message}")
        }
    }
    
    /**
     * Pattern vibration with amplitudes (Android O+)
     * @param pattern Timing pattern [delay, on, off, on, off, ...]
     * @param amplitudes Vibration strength [0-255] for each ON period
     * @param repeat -1 for no repeat, or index to repeat from
     */
    private fun vibrate(pattern: LongArray, amplitudes: IntArray, repeat: Int) {
        if (!isEnabled || !hasVibrator()) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, repeat))
                Log.d(TAG, "Pattern vibration executed")
            } else {
                // Fallback for older devices - use pattern without amplitudes
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, repeat)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pattern vibration error: ${e.message}")
        }
    }
    
    /**
     * Cancel all ongoing vibrations
     */
    fun cancel() {
        try {
            vibrator.cancel()
            Log.d(TAG, "Vibration cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Cancel vibration error: ${e.message}")
        }
    }
}
