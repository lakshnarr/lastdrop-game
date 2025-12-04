package com.example.lastdrop

import android.util.Log
import org.json.JSONObject

/**
 * Validates ESP32 responses and maintains state synchronization
 * Ensures data integrity between Android app and ESP32 board
 */
class ESP32StateValidator(
    private val onLogMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "ESP32Validator"
    }
    
    // Expected state tracking
    private var expectedPlayerPosition = mutableMapOf<Int, Int>() // playerId -> tile
    private var expectedCoinPlacement: Int? = null // Expected tile for next coin
    private var lastCommandSent: String? = null
    private var lastCommandTimestamp = 0L
    
    /**
     * Validate ESP32 response matches expected data
     * @return ValidationResult with success status and error message
     */
    fun validateCoinPlacement(
        response: JSONObject,
        expectedPlayerId: Int,
        expectedTile: Int
    ): ValidationResult {
        val playerId = response.optInt("playerId", -1)
        val tile = response.optInt("tile", -1)
        val verified = response.optBoolean("verified", false)
        
        // Validate player ID
        if (playerId != expectedPlayerId) {
            val error = "Player ID mismatch: expected $expectedPlayerId, got $playerId"
            onLogMessage("❌ Validation failed: $error")
            Log.w(TAG, error)
            return ValidationResult(false, error)
        }
        
        // Validate tile position
        if (tile != expectedTile) {
            val error = "Tile mismatch: expected $expectedTile, got $tile"
            onLogMessage("⚠️ Validation warning: $error")
            Log.w(TAG, error)
            return ValidationResult(false, error, isCritical = false)
        }
        
        // Check verification flag
        if (!verified) {
            val error = "ESP32 reported unverified coin placement"
            onLogMessage("⚠️ $error")
            Log.w(TAG, error)
            return ValidationResult(true, error, isCritical = false)
        }
        
        onLogMessage("✅ Coin placement validated: Player $playerId at tile $tile")
        return ValidationResult(true, "Valid")
    }
    
    /**
     * Set expected state for next coin placement
     */
    fun expectCoinPlacement(playerId: Int, tile: Int) {
        expectedCoinPlacement = tile
        expectedPlayerPosition[playerId] = tile
        onLogMessage("📝 Expecting: Player $playerId → Tile $tile")
    }
    
    /**
     * Clear expected state after successful placement
     */
    fun clearExpectedState() {
        expectedCoinPlacement = null
    }
    
    /**
     * Record command sent to ESP32 for debugging
     */
    fun recordCommand(command: String) {
        lastCommandSent = command
        lastCommandTimestamp = System.currentTimeMillis()
    }
    
    /**
     * Validate misplacement report from ESP32
     */
    fun validateMisplacementReport(errors: List<MisplacementError>): ValidationResult {
        if (errors.isEmpty()) {
            return ValidationResult(true, "No errors")
        }
        
        val errorMessages = errors.joinToString("\n") { 
            "Tile ${it.tile}: ${it.issue}" 
        }
        
        onLogMessage("⚠️ Misplacement detected:\n$errorMessages")
        
        return ValidationResult(
            success = false,
            message = "Misplacement: ${errors.size} error(s)",
            isCritical = true
        )
    }
    
    /**
     * Validate JSON response structure
     */
    fun validateResponseStructure(jsonString: String): ValidationResult {
        try {
            val json = JSONObject(jsonString)
            
            if (!json.has("event")) {
                return ValidationResult(false, "Missing 'event' field")
            }
            
            val event = json.getString("event")
            
            // Validate required fields per event type
            val requiredFields = when (event) {
                "coin_placed" -> listOf("playerId", "tile")
                "misplacement" -> listOf("errors")
                "coin_timeout" -> listOf("tile")
                else -> emptyList()
            }
            
            for (field in requiredFields) {
                if (!json.has(field)) {
                    val error = "Event '$event' missing required field: $field"
                    onLogMessage("❌ Invalid response: $error")
                    return ValidationResult(false, error)
                }
            }
            
            return ValidationResult(true, "Valid structure")
            
        } catch (e: Exception) {
            val error = "Invalid JSON: ${e.message}"
            onLogMessage("❌ $error")
            Log.e(TAG, "JSON parse error", e)
            return ValidationResult(false, error)
        }
    }
    
    /**
     * Get current expected state for debugging
     */
    fun getExpectedState(): String {
        return buildString {
            append("Expected Coin: ${expectedCoinPlacement ?: "None"}\n")
            append("Player Positions: ${expectedPlayerPosition}\n")
            append("Last Command: ${lastCommandSent ?: "None"}\n")
            append("Command Age: ${System.currentTimeMillis() - lastCommandTimestamp}ms")
        }
    }
    
    /**
     * Reset all state tracking
     */
    fun reset() {
        expectedPlayerPosition.clear()
        expectedCoinPlacement = null
        lastCommandSent = null
        lastCommandTimestamp = 0L
        onLogMessage("🔄 Validator state reset")
    }
}

/**
 * Result of validation operation
 */
data class ValidationResult(
    val success: Boolean,
    val message: String,
    val isCritical: Boolean = true
)

/**
 * Misplacement error data
 */
data class MisplacementError(
    val tile: Int,
    val issue: String
)
