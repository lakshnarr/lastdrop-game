package com.example.lastdrop

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles UI updates for ESP32 animation events (elimination, winner)
 * 
 * Purpose: Dedicated manager for animation-related UI feedback
 * Modular design: Keeps MainActivity.kt clean by extracting animation UI logic
 * 
 * Features:
 * - Player elimination alerts
 * - Winner celebration dialog
 * - Animation progress indicators
 * - Automatic timeout management during animations
 */
class AnimationEventHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AnimationEventHandler"
        private const val ELIMINATION_ANIMATION_DURATION = 2000L  // 1.8s + buffer
        private const val WINNER_ANIMATION_DURATION = 25000L      // 20-25s
    }
    
    private var currentDialog: AlertDialog? = null
    private var animationJob: Job? = null
    
    /**
     * Show player elimination alert
     * 
     * @param playerId Eliminated player ID
     * @param playerName Eliminated player name
     * @param onAnimationComplete Callback when animation completes
     */
    fun showEliminationAlert(
        playerId: Int,
        playerName: String,
        onAnimationComplete: (() -> Unit)? = null
    ) {
        Log.d(TAG, "Player $playerId ($playerName) eliminated")
        
        dismissCurrentDialog()
        
        // Show elimination alert
        currentDialog = AlertDialog.Builder(context)
            .setTitle("💀 Player Eliminated!")
            .setMessage("$playerName has run out of water drops!\n\nElimination animation playing...")
            .setCancelable(false)
            .create()
        
        currentDialog?.show()
        
        // Auto-dismiss after animation completes
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            delay(ELIMINATION_ANIMATION_DURATION)
            dismissCurrentDialog()
            onAnimationComplete?.invoke()
            Log.d(TAG, "Elimination animation complete")
        }
    }
    
    /**
     * Show winner celebration dialog
     * 
     * @param winnerId Winner player ID
     * @param winnerName Winner player name
     * @param winnerColor Winner color (for display)
     * @param onAnimationComplete Callback when celebration completes
     */
    fun showWinnerCelebration(
        winnerId: Int,
        winnerName: String,
        winnerColor: String,
        onAnimationComplete: (() -> Unit)? = null
    ) {
        Log.d(TAG, "Winner: Player $winnerId ($winnerName)")
        
        dismissCurrentDialog()
        
        // Build celebration message
        val message = buildString {
            append("🎉 GAME OVER! 🎉\n\n")
            append("$winnerName wins!\n\n")
            append("Winner celebration animation playing on board...\n")
            append("(~25 seconds)")
        }
        
        // Show winner dialog
        currentDialog = AlertDialog.Builder(context)
            .setTitle("🏆 VICTORY!")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Celebrate!") { dialog, _ ->
                // User can dismiss early if desired
                dialog.dismiss()
                onAnimationComplete?.invoke()
            }
            .create()
        
        currentDialog?.show()
        
        // Auto-dismiss after animation completes
        animationJob = CoroutineScope(Dispatchers.Main).launch {
            delay(WINNER_ANIMATION_DURATION)
            dismissCurrentDialog()
            onAnimationComplete?.invoke()
            Log.d(TAG, "Winner animation complete")
        }
    }
    
    /**
     * Update UI status message during animation
     * 
     * @param statusTextView TextView to update with animation status
     * @param animationType "elimination" or "winner"
     */
    fun updateAnimationStatus(statusTextView: TextView?, animationType: String) {
        statusTextView?.text = when (animationType) {
            "elimination" -> "⚠️ Elimination animation playing..."
            "winner" -> "🎉 Winner celebration in progress..."
            else -> "Animation playing..."
        }
    }
    
    /**
     * Clear animation status message
     * 
     * @param statusTextView TextView to clear
     */
    fun clearAnimationStatus(statusTextView: TextView?) {
        statusTextView?.text = ""
    }
    
    /**
     * Check if animation is currently in progress
     */
    fun isAnimationPlaying(): Boolean {
        return currentDialog?.isShowing == true
    }
    
    /**
     * Get remaining animation duration
     * Useful for timeout management
     */
    fun getAnimationDuration(animationType: String): Long {
        return when (animationType) {
            "elimination" -> ELIMINATION_ANIMATION_DURATION
            "winner" -> WINNER_ANIMATION_DURATION
            else -> 0L
        }
    }
    
    /**
     * Dismiss current animation dialog
     */
    fun dismissCurrentDialog() {
        animationJob?.cancel()
        currentDialog?.dismiss()
        currentDialog = null
    }
    
    /**
     * Show game over dialog when all players eliminated
     */
    fun showGameOverAllEliminated() {
        dismissCurrentDialog()
        
        currentDialog = AlertDialog.Builder(context)
            .setTitle("Game Over")
            .setMessage("All players have been eliminated!\n\nNo winner this round.")
            .setPositiveButton("New Game") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()
        
        currentDialog?.show()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        dismissCurrentDialog()
        animationJob?.cancel()
    }
}
