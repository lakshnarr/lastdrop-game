package com.example.lastdrop

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * Handles ESP32 error states, coin placement timeouts, and heartbeat monitoring
 */
class ESP32ErrorHandler(
    private val context: Context,
    private val onLogMessage: (String) -> Unit,
    private val onCoinTimeoutExpired: () -> Unit = {},
    private val onHeartbeatLost: () -> Unit = {}
) {
    companion object {
        private const val COIN_PLACEMENT_TIMEOUT_MS = 30000L // 30 seconds
        private const val HEARTBEAT_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val HEARTBEAT_TIMEOUT_MS = 15000L // 15 seconds without response
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Coin placement timeout
    private var coinTimeoutJob: Job? = null
    private var lastCoinPlacementTime = 0L
    
    // Heartbeat monitoring
    private var heartbeatJob: Job? = null
    private var lastHeartbeatReceived = 0L
    private var isHeartbeatActive = false
    
    // Winner animation state
    private var winnerAnimationInProgress = false
    
    /**
     * Start coin placement timeout monitor
     * Shows dialog when timeout expires
     * Skips timeout if winner animation is in progress
     */
    fun startCoinPlacementTimeout(expectedTile: Int) {
        // Don't start timeout during winner animation
        if (winnerAnimationInProgress) {
            onLogMessage("🏆 Skipping coin timeout - winner animation in progress")
            return
        }
        
        lastCoinPlacementTime = System.currentTimeMillis()
        
        coinTimeoutJob?.cancel()
        coinTimeoutJob = scope.launch {
            delay(COIN_PLACEMENT_TIMEOUT_MS)
            
            // Timeout expired - show graceful dialog
            onLogMessage("⏱️ Coin placement timeout at tile $expectedTile")
            
            showCoinTimeoutDialog(expectedTile)
            onCoinTimeoutExpired()
        }
    }
    
    /**
     * Cancel coin placement timeout (called when coin is placed)
     */
    fun cancelCoinPlacementTimeout() {
        coinTimeoutJob?.cancel()
        coinTimeoutJob = null
    }
    
    /**
     * Show dialog when coin placement times out
     * Offers options to retry or continue without physical coin
     */
    private fun showCoinTimeoutDialog(expectedTile: Int) {
        AlertDialog.Builder(context)
            .setTitle("⏱️ Coin Placement Timeout")
            .setMessage("No coin detected at tile $expectedTile after 30 seconds.\n\nWhat would you like to do?")
            .setPositiveButton("Continue Anyway") { dialog, _ ->
                onLogMessage("✅ User chose to continue without coin placement")
                Toast.makeText(context, "Continuing without physical coin placement", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Wait Longer") { dialog, _ ->
                onLogMessage("⏳ User chose to wait longer for coin placement")
                Toast.makeText(context, "Waiting for coin placement...", Toast.LENGTH_SHORT).show()
                
                // Restart timeout for another 30 seconds
                startCoinPlacementTimeout(expectedTile)
                dialog.dismiss()
            }
            .setNeutralButton("Skip Turn") { dialog, _ ->
                onLogMessage("⏭️ User chose to skip turn")
                Toast.makeText(context, "Turn skipped", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Start heartbeat monitoring for ESP32 connection health
     * Checks if ESP32 is still responding regularly
     */
    fun startHeartbeatMonitoring() {
        if (isHeartbeatActive) return
        
        isHeartbeatActive = true
        lastHeartbeatReceived = System.currentTimeMillis()
        
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isHeartbeatActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                
                val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatReceived
                
                if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    // Heartbeat lost
                    onLogMessage("💔 ESP32 heartbeat lost (${timeSinceLastHeartbeat}ms)")
                    showHeartbeatLostDialog()
                    onHeartbeatLost()
                    isHeartbeatActive = false
                    break
                }
            }
        }
    }
    
    /**
     * Stop heartbeat monitoring
     */
    fun stopHeartbeatMonitoring() {
        isHeartbeatActive = false
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /**
     * Update heartbeat timestamp (call when receiving any ESP32 message)
     */
    fun updateHeartbeat() {
        lastHeartbeatReceived = System.currentTimeMillis()
    }
    
    /**
     * Show dialog when ESP32 heartbeat is lost
     */
    private fun showHeartbeatLostDialog() {
        AlertDialog.Builder(context)
            .setTitle("💔 Connection Issue")
            .setMessage("ESP32 board stopped responding.\n\nThis could mean:\n• Board lost power\n• Bluetooth connection dropped\n• Physical hardware issue")
            .setPositiveButton("Reconnect") { dialog, _ ->
                onLogMessage("🔄 User requested reconnection")
                Toast.makeText(context, "Attempting to reconnect...", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Continue Without Board") { dialog, _ ->
                onLogMessage("📱 User chose to continue without ESP32")
                Toast.makeText(context, "Continuing in phone-only mode", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Get time since last heartbeat in milliseconds
     */
    fun getTimeSinceLastHeartbeat(): Long {
        return System.currentTimeMillis() - lastHeartbeatReceived
    }
    
    /**
     * Check if heartbeat is healthy
     */
    fun isHeartbeatHealthy(): Boolean {
        return getTimeSinceLastHeartbeat() < HEARTBEAT_TIMEOUT_MS
    }
    
    /**
     * Set winner animation state
     * Disables coin placement timeout during winner celebration
     */
    fun setWinnerAnimationInProgress(inProgress: Boolean) {
        winnerAnimationInProgress = inProgress
        if (inProgress) {
            // Cancel any active coin timeout
            cancelCoinPlacementTimeout()
            onLogMessage("🏆 Winner animation started - timeouts disabled")
        } else {
            onLogMessage("✅ Winner animation complete - timeouts re-enabled")
        }
    }
    
    /**
     * Check if winner animation is in progress
     */
    fun isWinnerAnimationInProgress(): Boolean {
        return winnerAnimationInProgress
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelCoinPlacementTimeout()
        stopHeartbeatMonitoring()
        scope.cancel()
    }
}
