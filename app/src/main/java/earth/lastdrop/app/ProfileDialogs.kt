package earth.lastdrop.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Profile creation and editing dialogs
 */
object ProfileDialogs {
    
    /**
     * Show create profile dialog with name and nickname inputs
     * @return Pair<name, nickname> or null if cancelled
     */
    suspend fun showCreateProfileDialog(context: Context): Pair<String, String>? {
        return suspendCancellableCoroutine { continuation ->
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            // Profile Name input
            val nameLabel = TextView(context).apply {
                text = "Profile Name (for game display)"
                textSize = 14f
                setPadding(0, 0, 0, 8)
            }
            val nameInput = EditText(context).apply {
                hint = "e.g., Player 1, Sarah, Mike"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            
            // Nickname input
            val nicknameLabel = TextView(context).apply {
                text = "How should AI call you?"
                textSize = 14f
                setPadding(0, 24, 0, 8)
            }
            val nicknameInput = EditText(context).apply {
                hint = "e.g., Champion, Boss, Sarah"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            
            layout.addView(nameLabel)
            layout.addView(nameInput)
            layout.addView(nicknameLabel)
            layout.addView(nicknameInput)
            
            val dialog = AlertDialog.Builder(context)
                .setTitle("Create New Profile")
                .setMessage("You'll get a unique player code to track your stats even if you change your name later.")
                .setView(layout)
                .setPositiveButton("Create") { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val nickname = nicknameInput.text.toString().trim().ifEmpty { name }
                    if (continuation.isActive) {
                        continuation.resume(name to nickname)
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .setCancelable(false)
                .create()
            
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
    }
    
    /**
     * Show rename profile dialog
     */
    suspend fun showRenameProfileDialog(context: Context, currentName: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val input = EditText(context).apply {
                setText(currentName)
                hint = "Profile name"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setPadding(50, 40, 50, 10)
                setSelectAllOnFocus(true)
            }
            
            val dialog = AlertDialog.Builder(context)
                .setTitle("Rename Profile")
                .setMessage("Change how this profile appears in the game. Your player code and stats remain unchanged.")
                .setView(input)
                .setPositiveButton("Rename") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(input.text.toString().trim())
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .setCancelable(false)
                .create()
            
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
            input.requestFocus()
        }
    }
    
    /**
     * Show update nickname dialog
     */
    suspend fun showUpdateNicknameDialog(context: Context, currentNickname: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val input = EditText(context).apply {
                setText(currentNickname)
                hint = "How AI calls you"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setPadding(50, 40, 50, 10)
                setSelectAllOnFocus(true)
            }
            
            val dialog = AlertDialog.Builder(context)
                .setTitle("Change AI Nickname")
                .setMessage("How would you like the AI to address you during the game?")
                .setView(input)
                .setPositiveButton("Update") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(input.text.toString().trim())
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .setCancelable(false)
                .create()
            
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
            input.requestFocus()
        }
    }
    
    /**
     * Show profile details with player code
     */
    fun showProfileDetailsDialog(context: Context, profile: PlayerProfile) {
        val message = buildString {
            append("Player Code: ${profile.playerCode}\n")
            append("Profile Name: ${profile.name}\n")
            append("AI Calls You: ${profile.nickname}\n\n")
            append("Games Played: ${profile.totalGames}\n")
            append("Wins: ${profile.wins}\n")
            append("Losses: ${profile.losses}\n")
            if (profile.totalGames > 0) {
                val winRate = (profile.wins.toFloat() / profile.totalGames * 100).toInt()
                append("Win Rate: $winRate%\n")
            }
            append("\nStats:\n")
            append("Best Score: ${profile.personalBestScore}\n")
            append("Average Score: ${String.format("%.1f", profile.averageScore)}\n")
            append("Current Win Streak: ${profile.currentWinStreak}\n")
            append("Best Win Streak: ${profile.bestWinStreak}\n")
            append("Total Drops Earned: ${profile.totalDropsEarned}\n")
            append("Play Time: ${profile.totalPlayTimeMinutes} minutes")
        }
        
        AlertDialog.Builder(context)
            .setTitle("Profile Stats")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
