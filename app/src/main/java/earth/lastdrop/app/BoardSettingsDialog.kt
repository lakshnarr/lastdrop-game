package earth.lastdrop.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * Dialog for changing ESP32 board settings (password and nickname)
 * 
 * Features:
 * - Change board pairing password (6+ characters)
 * - Change board nickname (1-30 characters)
 * - Validates input before sending to ESP32
 * - Shows current values
 * - Sends update_settings command to ESP32
 */
object BoardSettingsDialog {
    
    /**
     * Show board settings dialog
     * 
     * @param context Android context
     * @param currentNickname Current board nickname (from preferences or BLE name)
     * @param currentPassword Current password (if saved, otherwise null)
     * @param onSettingsUpdate Callback when user confirms changes (nickname, password)
     */
    fun show(
        context: Context,
        currentNickname: String,
        currentPassword: String?,
        onSettingsUpdate: (nickname: String?, password: String?) -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        // Title
        val titleText = TextView(context).apply {
            text = "Board Settings"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleText)
        
        // Current settings info
        val infoText = TextView(context).apply {
            text = "Current Nickname: $currentNickname\n" +
                   "Current Password: ${if (currentPassword != null) "●●●●●●" else "Not saved"}"
            textSize = 14f
            setPadding(0, 0, 0, 30)
            setTextColor(0xFF666666.toInt())
        }
        layout.addView(infoText)
        
        // Nickname input
        val nicknameLabel = TextView(context).apply {
            text = "New Nickname (optional)"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(nicknameLabel)
        
        val nicknameInput = EditText(context).apply {
            hint = "e.g., Game Room A, Table 3"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(currentNickname)
            setPadding(20, 20, 20, 20)
        }
        layout.addView(nicknameInput)
        
        // Password input
        val passwordLabel = TextView(context).apply {
            text = "New Password (optional, min 6 chars)"
            textSize = 14f
            setPadding(0, 20, 0, 8)
        }
        layout.addView(passwordLabel)
        
        val passwordInput = EditText(context).apply {
            hint = "Enter new 6-digit password"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(20, 20, 20, 20)
        }
        layout.addView(passwordInput)
        
        // Warning message
        val warningText = TextView(context).apply {
            text = "\n⚠️ Important:\n" +
                   "• Password changes require re-pairing\n" +
                   "• Nickname changes need board restart\n" +
                   "• Leave fields empty to keep current values"
            textSize = 12f
            setTextColor(0xFFFF6600.toInt())
            setPadding(0, 20, 0, 0)
        }
        layout.addView(warningText)
        
        AlertDialog.Builder(context)
            .setTitle("Update Board Settings")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val newNickname = nicknameInput.text.toString().trim()
                val newPassword = passwordInput.text.toString().trim()
                
                // Validate inputs
                var hasChanges = false
                var nicknameToSend: String? = null
                var passwordToSend: String? = null
                
                // Check nickname
                if (newNickname.isNotEmpty() && newNickname != currentNickname) {
                    if (newNickname.length in 1..30) {
                        nicknameToSend = newNickname
                        hasChanges = true
                    } else {
                        Toast.makeText(context, "Nickname must be 1-30 characters", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }
                
                // Check password
                if (newPassword.isNotEmpty()) {
                    if (newPassword.length >= 6) {
                        passwordToSend = newPassword
                        hasChanges = true
                    } else {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }
                
                if (hasChanges) {
                    onSettingsUpdate(nicknameToSend, passwordToSend)
                } else {
                    Toast.makeText(context, "No changes made", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show confirmation dialog after settings update
     */
    fun showUpdateConfirmation(
        context: Context,
        nickname: String?,
        passwordChanged: Boolean,
        restartRequired: Boolean
    ) {
        val message = buildString {
            append("Settings updated successfully!\n\n")
            
            if (nickname != null) {
                append("✓ Nickname: $nickname\n")
            }
            
            if (passwordChanged) {
                append("✓ Password updated\n")
            }
            
            if (restartRequired) {
                append("\n⚠️ Board restart required for nickname to take effect in BLE advertising")
            }
            
            if (passwordChanged) {
                append("\n\n🔑 Password changed - you may need to re-pair the board")
            }
        }
        
        AlertDialog.Builder(context)
            .setTitle("Update Complete")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Generate update_settings JSON command for ESP32
     */
    fun generateUpdateCommand(nickname: String?, password: String?): String {
        return JSONObject().apply {
            put("command", "update_settings")
            nickname?.let { put("nickname", it) }
            password?.let { put("password", it) }
        }.toString()
    }
}
