package earth.lastdrop.app

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * PIN entry dialog for ESP32 board authentication
 * Provides user-friendly app-level security without BLE pairing complexity
 */
object PinEntryDialog {
    
    /**
     * Show PIN entry dialog for board connection
     * 
     * @param context Android context
     * @param device Selected Bluetooth device
     * @param boardId Board ID (e.g., "LASTDROP-0001")
     * @param preferencesManager Board preferences manager
     * @param onPinEntered Callback with entered PIN and remember choice
     */
    fun show(
        context: Context,
        device: BluetoothDevice,
        boardId: String,
        preferencesManager: BoardPreferencesManager,
        onPinEntered: (pin: String, rememberPin: Boolean) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        // Create layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        
        // Board info text
        val infoText = TextView(context).apply {
            text = "Enter PIN for:\n$boardId\n${device.address}"
            textSize = 14f
            setPadding(0, 0, 0, 30)
        }
        layout.addView(infoText)
        
        // PIN input field
        val pinInput = EditText(context).apply {
            hint = "Enter 6-digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setLines(1)
            
            // Pre-fill with saved PIN if exists
            val savedBoard = preferencesManager.getSavedBoard(boardId)
            if (savedBoard?.passwordHash != null) {
                // Check if we have the PIN saved (for auto-fill)
                val savedPin = preferencesManager.getSavedPin(boardId)
                if (savedPin != null) {
                    setText(savedPin)
                    hint = "Saved PIN loaded"
                }
            }
        }
        layout.addView(pinInput)
        
        // Remember PIN checkbox
        val rememberCheckbox = CheckBox(context).apply {
            text = "Remember PIN for this board"
            isChecked = preferencesManager.getSavedBoard(boardId) != null
            setPadding(0, 20, 0, 0)
        }
        layout.addView(rememberCheckbox)
        
        // Hint text
        val hintText = TextView(context).apply {
            text = "\n💡 Default PIN: 654321\n(configured in ESP32 firmware)"
            textSize = 12f
            alpha = 0.7f
        }
        layout.addView(hintText)
        
        // Show dialog
        AlertDialog.Builder(context)
            .setTitle("🔐 Board Authentication")
            .setView(layout)
            .setPositiveButton("Connect") { dialog, _ ->
                val pin = pinInput.text.toString()
                if (pin.isNotEmpty()) {
                    onPinEntered(pin, rememberCheckbox.isChecked)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Please enter a PIN",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onCancel()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                onCancel()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
        
        // Focus PIN input and show keyboard
        pinInput.requestFocus()
    }
    
    /**
     * Show PIN validation result dialog
     */
    fun showValidationResult(
        context: Context,
        success: Boolean,
        boardId: String,
        onRetry: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        if (success) {
            AlertDialog.Builder(context)
                .setTitle("✅ Connected")
                .setMessage("Successfully connected to $boardId")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(context)
                .setTitle("❌ Invalid PIN")
                .setMessage("The PIN you entered is incorrect.\n\nPlease try again or check your ESP32 board configuration.")
                .setPositiveButton("Retry") { dialog, _ ->
                    dialog.dismiss()
                    onRetry()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    onCancel()
                }
                .show()
        }
    }
    
    /**
     * Show PIN change confirmation
     */
    fun showPinChangeDialog(
        context: Context,
        boardId: String,
        onConfirm: (newPin: String) -> Unit
    ) {
        val pinInput = EditText(context).apply {
            hint = "Enter new 6-digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(60, 40, 60, 40)
        }
        
        AlertDialog.Builder(context)
            .setTitle("Change Board PIN")
            .setMessage("Enter new PIN for $boardId")
            .setView(pinInput)
            .setPositiveButton("Change") { dialog, _ ->
                val newPin = pinInput.text.toString()
                if (newPin.length >= 6) {
                    onConfirm(newPin)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "PIN must be at least 6 digits",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
