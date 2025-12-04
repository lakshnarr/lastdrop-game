package com.example.lastdrop

import android.widget.TextView
import kotlinx.coroutines.*

/**
 * Manages timed UI updates like coin placement countdown and turn indicators
 */
class UIUpdateManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Active countdown job
    private var countdownJob: Job? = null
    
    /**
     * Start a countdown timer display
     * @param textView TextView to update with countdown
     * @param totalSeconds Total countdown duration
     * @param prefix Text prefix before countdown
     * @param onComplete Callback when countdown finishes
     */
    fun startCountdown(
        textView: TextView,
        totalSeconds: Int,
        prefix: String = "Waiting for coin placement: ",
        onComplete: () -> Unit = {}
    ) {
        cancelCountdown()
        
        countdownJob = scope.launch {
            for (remaining in totalSeconds downTo 0) {
                textView.text = "$prefix${remaining}s"
                delay(1000)
            }
            textView.text = ""
            onComplete()
        }
    }
    
    /**
     * Cancel active countdown
     */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }
    
    /**
     * Update turn indicator with player info
     * @param textView TextView to update
     * @param playerName Name of current player
     * @param playerColor Color of current player
     */
    fun updateTurnIndicator(textView: TextView, playerName: String, playerColor: String) {
        textView.text = "Current Turn: $playerName ($playerColor)"
    }
    
    /**
     * Clear turn indicator
     */
    fun clearTurnIndicator(textView: TextView) {
        textView.text = ""
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelCountdown()
        scope.cancel()
    }
}
