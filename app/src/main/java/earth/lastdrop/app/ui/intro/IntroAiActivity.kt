package earth.lastdrop.app.ui.intro

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import earth.lastdrop.app.ProfileManager
import earth.lastdrop.app.R
import earth.lastdrop.app.PlayerProfile
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.TextToSpeechVoiceService
import earth.lastdrop.app.voice.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.children

class IntroAiActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager
    private lateinit var cloudieImage: ImageView
    private lateinit var dialogue: TextView
    private lateinit var dropsRow: LinearLayout
    private lateinit var btnStart: Button
    private lateinit var btnSkip: Button
    private lateinit var voiceService: VoiceService
    private var voiceEnabled: Boolean = true
    private val cloudiePrefs by lazy { getSharedPreferences("cloudie_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_ai)

        profileManager = ProfileManager(this)
        voiceEnabled = cloudiePrefs.getBoolean("cloudie_voice_enabled", true)
        voiceService = if (voiceEnabled) {
            runCatching {
                TextToSpeechVoiceService(
                    context = this,
                    onReady = { appendDebug("🔊 Cloudie voice ready") },
                    onError = {
                        appendDebug("⚠️ TTS error: $it")
                        runOnUiThread {
                            android.widget.Toast.makeText(this, "Cloudie voice unavailable", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }.getOrElse {
                appendDebug("⚠️ Falling back to silent Cloudie (TTS unavailable)")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Cloudie voice unavailable", android.widget.Toast.LENGTH_SHORT).show()
                }
                NoOpVoiceService(this)
            }
        } else {
            appendDebug("🤫 Cloudie voice disabled from main screen")
            NoOpVoiceService(this)
        }
        cloudieImage = findViewById(R.id.cloudieImage)
        dialogue = findViewById(R.id.cloudieDialogue)
        dropsRow = findViewById(R.id.dropsRow)
        btnStart = findViewById(R.id.btnIntroStart)
        btnSkip = findViewById(R.id.btnIntroSkip)

        val selectedProfiles = intent.getStringArrayListExtra("selected_profiles") ?: arrayListOf()
        val assignedColors = intent.getStringArrayListExtra("assigned_colors") ?: arrayListOf()

        if (selectedProfiles.isEmpty()) {
            Toast.makeText(this, "No players provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setInitialStates()
        bindPlayers(selectedProfiles, assignedColors)
        playIntroLines()
        playEntranceAnimations()

        val finishOk: () -> Unit = {
            setResult(RESULT_OK, intent)
            finish()
        }
        btnStart.setOnClickListener { finishOk() }
        btnSkip.setOnClickListener { finishOk() }
    }

    override fun onDestroy() {
        (voiceService as? TextToSpeechVoiceService)?.shutdown()
        super.onDestroy()
    }

    private fun speakLine(line: String) {
        dialogue.text = line
        if (voiceEnabled) {
            voiceService.speak(line)
        }
    }

    private fun appendDebug(message: String) {
        // Minimal inline logger for this screen to avoid coupling to MainActivity logs
        android.util.Log.d("IntroAi", message)
    }

    private fun setInitialStates() {
        cloudieImage.translationY = -80f
        cloudieImage.scaleX = 0.8f
        cloudieImage.scaleY = 0.8f
        cloudieImage.alpha = 0f

        dropsRow.children.forEach { child ->
            child.translationY = -40f
            child.alpha = 0f
        }
    }

    private fun playEntranceAnimations() {
        // Cloudie flies in and grows
        cloudieImage.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(650)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        // Staggered drop bounces
        dropsRow.post {
            dropsRow.children.forEachIndexed { index, child ->
                child.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setStartDelay((150L * index))
                    .setDuration(450)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun bindPlayers(ids: List<String>, colors: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val profiles = ids.mapNotNull { profileManager.getProfile(it) }
            withContext(Dispatchers.Main) {
                dropsRow.removeAllViews()
                profiles.forEachIndexed { index, profile ->
                    val colorHex = colors.getOrNull(index).orEmpty().ifBlank { profile.avatarColor }
                    dropsRow.addView(createDrop(profile, colorHex))
                }
                // Reset initial states now that drops are inflated
                dropsRow.children.forEach { child ->
                    child.translationY = -40f
                    child.alpha = 0f
                }
            }
        }
    }

    private fun createDrop(profile: PlayerProfile, colorHex: String): LinearLayout {
        val dropView = LayoutInflater.from(this).inflate(R.layout.view_player_drop, dropsRow, false) as LinearLayout
        val dropIcon = dropView.findViewById<ImageView>(R.id.dropIcon)
        val dropLabel = dropView.findViewById<TextView>(R.id.dropLabel)

        dropIcon.setColorFilter(Color.parseColor("#${colorHex.ifBlank { "4FC3F7" }}"))
        dropLabel.text = profile.nickname.ifBlank { profile.name }

        // subtle pulse animation placeholder
        dropView.alpha = 0f
        dropView.animate().alpha(1f).setDuration(350).start()
        return dropView
    }

    private fun playIntroLines() {
        val lines = listOf(
            "☁️ Cloudie here! Ready to host.",
            "I tuned into your colors—looking sharp!",
            "Tap start when you want to hit the board."
        )
        dialogue.isVisible = true
        lines.forEachIndexed { i, line ->
            dialogue.postDelayed({ speakLine(line) }, i * 900L)
        }
    }
}
