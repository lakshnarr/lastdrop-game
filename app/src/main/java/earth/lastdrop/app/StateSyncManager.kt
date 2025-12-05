package earth.lastdrop.app

import android.util.Log
import kotlinx.coroutines.*

/**
 * Manages state synchronization between Android app and ESP32 board
 * Periodically verifies consistency and handles sync failures
 */
class StateSyncManager(
    private val onLogMessage: (String) -> Unit,
    private val onSyncFailure: (String) -> Unit
) {
    companion object {
        private const val TAG = "StateSyncManager"
        private const val SYNC_CHECK_INTERVAL_MS = 10000L // Check every 10 seconds
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var isSyncActive = false
    
    // Local state mirror
    private val localPlayerPositions = mutableMapOf<Int, Int>() // playerId -> tile
    private val localPlayerScores = mutableMapOf<Int, Int>() // playerId -> score
    
    // Sync status
    private var lastSyncTime = 0L
    private var syncFailureCount = 0
    private val MAX_SYNC_FAILURES = 3
    
    /**
     * Start periodic state synchronization checking
     */
    fun startSyncMonitoring() {
        if (isSyncActive) return
        
        isSyncActive = true
        lastSyncTime = System.currentTimeMillis()
        
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && isSyncActive) {
                delay(SYNC_CHECK_INTERVAL_MS)
                
                checkSyncHealth()
            }
        }
        
        onLogMessage("🔄 State sync monitoring started")
    }
    
    /**
     * Stop sync monitoring
     */
    fun stopSyncMonitoring() {
        isSyncActive = false
        syncJob?.cancel()
        syncJob = null
        onLogMessage("⏹️ State sync monitoring stopped")
    }
    
    /**
     * Update local state mirror
     */
    fun updateLocalState(playerId: Int, position: Int, score: Int) {
        localPlayerPositions[playerId] = position
        localPlayerScores[playerId] = score
        lastSyncTime = System.currentTimeMillis()
    }
    
    /**
     * Verify state consistency
     */
    fun verifySyncState(
        esp32Positions: Map<Int, Int>,
        esp32Scores: Map<Int, Int>
    ): SyncResult {
        val positionMismatches = mutableListOf<String>()
        val scoreMismatches = mutableListOf<String>()
        
        // Check positions
        for ((playerId, localPos) in localPlayerPositions) {
            val esp32Pos = esp32Positions[playerId]
            if (esp32Pos != null && esp32Pos != localPos) {
                positionMismatches.add("Player $playerId: local=$localPos, ESP32=$esp32Pos")
            }
        }
        
        // Check scores
        for ((playerId, localScore) in localPlayerScores) {
            val esp32Score = esp32Scores[playerId]
            if (esp32Score != null && esp32Score != localScore) {
                scoreMismatches.add("Player $playerId: local=$localScore, ESP32=$esp32Score")
            }
        }
        
        return if (positionMismatches.isEmpty() && scoreMismatches.isEmpty()) {
            syncFailureCount = 0
            SyncResult(
                inSync = true,
                message = "✅ State synchronized"
            )
        } else {
            syncFailureCount++
            val errors = (positionMismatches + scoreMismatches).joinToString("\n")
            
            SyncResult(
                inSync = false,
                message = "❌ State desync detected:\n$errors",
                positionMismatches = positionMismatches,
                scoreMismatches = scoreMismatches
            )
        }
    }
    
    /**
     * Check sync health and trigger alerts if needed
     */
    private fun checkSyncHealth() {
        val timeSinceLastSync = System.currentTimeMillis() - lastSyncTime
        
        if (timeSinceLastSync > 30000) { // 30 seconds without sync
            onLogMessage("⚠️ No state updates for ${timeSinceLastSync / 1000}s")
        }
        
        if (syncFailureCount >= MAX_SYNC_FAILURES) {
            val message = "Critical: $syncFailureCount consecutive sync failures"
            onLogMessage("🚨 $message")
            onSyncFailure(message)
            syncFailureCount = 0 // Reset after alerting
        }
    }
    
    /**
     * Force state resync
     */
    fun forceSyncResolution(useLocalState: Boolean): String {
        return if (useLocalState) {
            onLogMessage("📱 Using local (Android) state as source of truth")
            "Synced from local state"
        } else {
            onLogMessage("📡 Using ESP32 state as source of truth")
            localPlayerPositions.clear()
            localPlayerScores.clear()
            "Synced from ESP32 state"
        }
    }
    
    /**
     * Get current local state snapshot
     */
    fun getLocalStateSnapshot(): Map<String, Any> {
        return mapOf(
            "positions" to localPlayerPositions.toMap(),
            "scores" to localPlayerScores.toMap(),
            "lastSyncTime" to lastSyncTime,
            "syncFailureCount" to syncFailureCount
        )
    }
    
    /**
     * Get time since last successful sync
     */
    fun getTimeSinceLastSync(): Long {
        return System.currentTimeMillis() - lastSyncTime
    }
    
    /**
     * Check if state is healthy
     */
    fun isSyncHealthy(): Boolean {
        return syncFailureCount < MAX_SYNC_FAILURES && 
               getTimeSinceLastSync() < 30000
    }
    
    /**
     * Reset sync state
     */
    fun reset() {
        localPlayerPositions.clear()
        localPlayerScores.clear()
        syncFailureCount = 0
        lastSyncTime = System.currentTimeMillis()
        onLogMessage("🔄 Sync state reset")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopSyncMonitoring()
        scope.cancel()
    }
}

/**
 * Result of state synchronization check
 */
data class SyncResult(
    val inSync: Boolean,
    val message: String,
    val positionMismatches: List<String> = emptyList(),
    val scoreMismatches: List<String> = emptyList()
)
