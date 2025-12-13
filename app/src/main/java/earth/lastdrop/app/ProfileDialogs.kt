package earth.lastdrop.app

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.TextToSpeechVoiceService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Profile creation and editing dialogs
 */
object ProfileDialogs {
    
    /**
     * Show create profile dialog with name, nickname, and voice persona selection (with sample play).
     * @return CreateProfileResult or null if cancelled
     */
    suspend fun showCreateProfileDialog(context: Context): CreateProfileResult? {
        return suspendCancellableCoroutine { continuation ->
            val linear = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
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

            // Persona selector
            val personaLabel = TextView(context).apply {
                text = "Pick your voice style"
                textSize = 14f
                setPadding(0, 24, 0, 8)
            }
            val personas = listOf(
                "cloudie" to "Cloudie (friendly)",
                "coach" to "Coach (sporty)",
                "jester" to "Jester (silly)",
                "wizard" to "Wizard (magic)",
                "bot" to "Bot (robot)"
            )
            val personaNames = personas.map { it.second }
            val personaKeys = personas.map { it.first }
            val personaSpinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, personaNames)
                setSelection(0)
            }

            // Sample button
            var ttsService: TextToSpeechVoiceService? = null
            val sampleButton = Button(context).apply {
                text = "▶️ Play Sample"
                setOnClickListener {
                    val personaKey = personaKeys[personaSpinner.selectedItemPosition]
                    val sampleLine = sampleLines[personaKey] ?: sampleLines["cloudie"]!!
                    if (ttsService == null) {
                        ttsService = runCatching {
                            TextToSpeechVoiceService(context)
                        }.getOrElse { null }
                    }
                    (ttsService ?: NoOpVoiceService(context)).speak(sampleLine)
                }
            }
            
            linear.addView(nameLabel)
            linear.addView(nameInput)
            linear.addView(nicknameLabel)
            linear.addView(nicknameInput)
            linear.addView(personaLabel)
            linear.addView(personaSpinner)
            linear.addView(sampleButton)

            val scroll = ScrollView(context).apply {
                isFillViewport = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                addView(linear)
            }

            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(40, 24, 40, 24)
                gravity = android.view.Gravity.END
            }

            val cancelButton = Button(context).apply {
                text = "Cancel"
            }

            val createButton = Button(context).apply {
                text = "Create"
            }

            actionRow.addView(cancelButton)
            actionRow.addView(createButton)

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(scroll)
                addView(actionRow)
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle("Create New Profile")
                .setMessage("You'll get a unique player code to track your stats even if you change your name later.")
                .setView(root)
                .setCancelable(false)
                .create()

            fun resume(result: CreateProfileResult?) {
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            cancelButton.setOnClickListener {
                resume(null)
                dialog.dismiss()
            }

            createButton.setOnClickListener {
                val name = nameInput.text.toString().trim()
                val nickname = nicknameInput.text.toString().trim().ifEmpty { name }
                val persona = personaKeys[personaSpinner.selectedItemPosition]
                resume(CreateProfileResult(name, nickname, persona))
                dialog.dismiss()
            }

            dialog.setOnShowListener {
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                nameInput.requestFocus()
            }

            dialog.setOnDismissListener {
                ttsService?.shutdown()
            }

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
     * Show persona selection with sample play.
     * @return persona key or null if cancelled
     */
    suspend fun showPersonaSelectionDialog(context: Context, currentPersona: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }

            val personas = listOf(
                "cloudie" to "Cloudie (friendly)",
                "coach" to "Coach (sporty)",
                "jester" to "Jester (silly)",
                "wizard" to "Wizard (magic)",
                "bot" to "Bot (robot)"
            )
            val personaNames = personas.map { it.second }
            val personaKeys = personas.map { it.first }
            val currentIndex = personaKeys.indexOf(currentPersona).takeIf { it >= 0 } ?: 0

            val spinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, personaNames)
                setSelection(currentIndex)
            }

            var ttsService: TextToSpeechVoiceService? = null
            val sampleButton = Button(context).apply {
                text = "▶️ Play Sample"
                setOnClickListener {
                    val personaKey = personaKeys[spinner.selectedItemPosition]
                    val sampleLine = sampleLines[personaKey] ?: sampleLines["cloudie"]!!
                    if (ttsService == null) {
                        ttsService = runCatching { TextToSpeechVoiceService(context) }.getOrElse { null }
                    }
                    (ttsService ?: NoOpVoiceService(context)).speak(sampleLine)
                }
            }

            layout.addView(TextView(context).apply {
                text = "Choose a voice style"
                textSize = 14f
                setPadding(0, 0, 0, 8)
            })
            layout.addView(spinner)
            layout.addView(sampleButton)

            val dialog = AlertDialog.Builder(context)
                .setTitle("Voice Persona")
                .setView(layout)
                .setPositiveButton("Save") { _, _ ->
                    val persona = personaKeys[spinner.selectedItemPosition]
                    if (continuation.isActive) {
                        continuation.resume(persona)
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (continuation.isActive) continuation.resume(null)
                }
                .setCancelable(true)
                .create()

            dialog.setOnDismissListener { ttsService?.shutdown() }
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
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

data class CreateProfileResult(
    val name: String,
    val nickname: String,
    val persona: String
)

private val sampleLines = mapOf(
    "cloudie" to "Hi there! I'm Cloudie, ready to cheer you on!",
    "coach" to "Coach mode: keep your head in the game!",
    "jester" to "Heehee! Let's make this turn super silly!",
    "wizard" to "By spark and spell, adventure begins!",
    "bot" to "Beep boop. Fun mode activated."
)
