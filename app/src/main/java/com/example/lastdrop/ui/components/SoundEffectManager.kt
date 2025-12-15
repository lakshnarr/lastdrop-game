package com.example.lastdrop.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * SoundEffectManager - Centralized audio controller for game sound effects
 * 
 * Features:
 * - SoundPool for low-latency sound effects
 * - Volume control with mute support
 * - Preloaded sounds for instant playback
 * - Sound categories (UI, game events, voice)
 * 
 * Usage:
 * ```
 * soundManager.playSound(SoundEffect.DICE_ROLL)
 * soundManager.setVolume(0.7f)
 * soundManager.mute(true)
 * ```
 */
class SoundEffectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SoundEffectManager"
        private const val MAX_STREAMS = 10
        private const val DEFAULT_VOLUME = 0.8f
    }
    
    // SoundPool for efficient sound effect playback
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    
    // Sound ID map (loaded sounds)
    private val soundMap = mutableMapOf<SoundEffect, Int>()
    
    // Volume settings
    private var masterVolume = DEFAULT_VOLUME
    private var isMuted = false
    
    /**
     * Initialize sound pool with all sound effects
     * Call this in onCreate() to preload sounds
     */
    fun initialize() {
        Log.d(TAG, "Initializing SoundEffectManager...")
        
        // Load all sound effects from raw resources
        // Note: Actual sound files need to be added to res/raw/
        
        // UI Sounds
        loadSound(SoundEffect.BUTTON_CLICK, "button_click")
        loadSound(SoundEffect.MENU_OPEN, "menu_open")
        loadSound(SoundEffect.MENU_CLOSE, "menu_close")
        loadSound(SoundEffect.TOGGLE_ON, "toggle_on")
        loadSound(SoundEffect.TOGGLE_OFF, "toggle_off")
        
        // Dice Sounds
        loadSound(SoundEffect.DICE_ROLL, "dice_roll")
        loadSound(SoundEffect.DICE_LAND, "dice_land")
        
        // Game Event Sounds
        loadSound(SoundEffect.SCORE_GAIN, "score_gain")
        loadSound(SoundEffect.SCORE_LOSS, "score_loss")
        loadSound(SoundEffect.TILE_LAND, "tile_land")
        loadSound(SoundEffect.CHANCE_CARD, "chance_card")
        loadSound(SoundEffect.PLAYER_ELIMINATED, "player_eliminated")
        loadSound(SoundEffect.PLAYER_REVIVED, "player_revived")
        
        // Victory Sounds
        loadSound(SoundEffect.GAME_WIN, "game_win")
        loadSound(SoundEffect.CONFETTI, "confetti")
        
        // Error Sounds
        loadSound(SoundEffect.ERROR, "error")
        loadSound(SoundEffect.WARNING, "warning")
        
        Log.d(TAG, "Loaded ${soundMap.size} sound effects")
    }
    
    /**
     * Load a sound effect from res/raw/
     */
    private fun loadSound(effect: SoundEffect, fileName: String) {
        try {
            val resId = context.resources.getIdentifier(
                fileName, 
                "raw", 
                context.packageName
            )
            
            if (resId != 0) {
                val soundId = soundPool.load(context, resId, 1)
                soundMap[effect] = soundId
                Log.d(TAG, "Loaded sound: $fileName -> $soundId")
            } else {
                Log.w(TAG, "Sound file not found: $fileName.mp3")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sound $fileName: ${e.message}")
        }
    }
    
    /**
     * Play a sound effect
     * @param effect The sound effect to play
     * @param loop Number of times to loop (0 = play once, -1 = loop forever)
     * @return Stream ID (can be used to stop sound later)
     */
    fun playSound(effect: SoundEffect, loop: Int = 0): Int {
        if (isMuted) {
            Log.d(TAG, "Skipping sound (muted): $effect")
            return 0
        }
        
        val soundId = soundMap[effect]
        if (soundId == null) {
            Log.w(TAG, "Sound not loaded: $effect")
            return 0
        }
        
        val volume = masterVolume * effect.volumeMultiplier
        val streamId = soundPool.play(soundId, volume, volume, 1, loop, 1.0f)
        
        Log.d(TAG, "Playing sound: $effect (stream: $streamId, volume: $volume)")
        return streamId
    }
    
    /**
     * Play sound with custom volume
     */
    fun playSound(effect: SoundEffect, customVolume: Float, loop: Int = 0): Int {
        if (isMuted) return 0
        
        val soundId = soundMap[effect] ?: return 0
        val volume = customVolume.coerceIn(0f, 1f)
        val streamId = soundPool.play(soundId, volume, volume, 1, loop, 1.0f)
        
        Log.d(TAG, "Playing sound: $effect (custom volume: $volume)")
        return streamId
    }
    
    /**
     * Stop a playing sound stream
     */
    fun stopSound(streamId: Int) {
        if (streamId > 0) {
            soundPool.stop(streamId)
            Log.d(TAG, "Stopped stream: $streamId")
        }
    }
    
    /**
     * Set master volume (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        Log.d(TAG, "Master volume set to: $masterVolume")
    }
    
    /**
     * Get current master volume
     */
    fun getVolume(): Float = masterVolume
    
    /**
     * Mute/unmute all sounds
     */
    fun mute(muted: Boolean) {
        isMuted = muted
        Log.d(TAG, "Muted: $isMuted")
    }
    
    /**
     * Check if sounds are muted
     */
    fun isMuted(): Boolean = isMuted
    
    /**
     * Release resources (call in onDestroy)
     */
    fun release() {
        Log.d(TAG, "Releasing SoundEffectManager resources")
        soundPool.release()
        soundMap.clear()
    }
}

/**
 * Enumeration of all game sound effects
 * @param volumeMultiplier Relative volume adjustment (0.0 to 1.0)
 */
enum class SoundEffect(val volumeMultiplier: Float = 1.0f) {
    // UI Sounds
    BUTTON_CLICK(0.6f),
    MENU_OPEN(0.7f),
    MENU_CLOSE(0.7f),
    TOGGLE_ON(0.5f),
    TOGGLE_OFF(0.5f),
    
    // Dice Sounds
    DICE_ROLL(0.8f),
    DICE_LAND(0.9f),
    
    // Game Event Sounds
    SCORE_GAIN(0.8f),
    SCORE_LOSS(0.7f),
    TILE_LAND(0.6f),
    CHANCE_CARD(0.9f),
    PLAYER_ELIMINATED(1.0f),
    PLAYER_REVIVED(0.9f),
    
    // Victory Sounds
    GAME_WIN(1.0f),
    CONFETTI(0.7f),
    
    // Error Sounds
    ERROR(0.8f),
    WARNING(0.7f)
}
