package earth.lastdrop.app

import android.widget.Toast
import android.content.Context

/**
 * Monitors battery levels for BLDice and shows warnings
 */
class BatteryMonitor(private val context: Context) {
    companion object {
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10
    }
    
    // Track which dice have shown warnings
    private val lowBatteryWarnings = mutableSetOf<Int>()
    private val criticalBatteryWarnings = mutableSetOf<Int>()
    
    /**
     * Update battery level for a dice
     * @param diceId Internal dice identifier
     * @param level Battery percentage (0-100)
     * @param onLogMessage Callback for logging
     */
    fun updateBatteryLevel(diceId: Int, level: Int, onLogMessage: (String) -> Unit) {
        when {
            level <= CRITICAL_BATTERY_THRESHOLD -> {
                if (!criticalBatteryWarnings.contains(diceId)) {
                    criticalBatteryWarnings.add(diceId)
                    lowBatteryWarnings.add(diceId)
                    
                    val message = "🔴 CRITICAL: Dice $diceId battery at $level%! Charge immediately!"
                    onLogMessage(message)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            level <= LOW_BATTERY_THRESHOLD -> {
                if (!lowBatteryWarnings.contains(diceId)) {
                    lowBatteryWarnings.add(diceId)
                    
                    val message = "⚠️ Dice $diceId battery low ($level%). Please charge soon."
                    onLogMessage(message)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            level > LOW_BATTERY_THRESHOLD -> {
                // Reset warnings when battery is charged
                lowBatteryWarnings.remove(diceId)
                criticalBatteryWarnings.remove(diceId)
            }
        }
    }
    
    /**
     * Check if dice has critical battery
     */
    fun hasCriticalBattery(diceId: Int): Boolean {
        return criticalBatteryWarnings.contains(diceId)
    }
    
    /**
     * Reset all warnings (e.g., on new game)
     */
    fun reset() {
        lowBatteryWarnings.clear()
        criticalBatteryWarnings.clear()
    }
}
