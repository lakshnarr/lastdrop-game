package earth.lastdrop.app

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIHomeActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager
    private lateinit var playerContainer: LinearLayout
    private lateinit var dialogue: TextView
    private lateinit var btnStart: Button
    private lateinit var btnSkip: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_home)

        profileManager = ProfileManager(this)
        playerContainer = findViewById(R.id.playerListContainer)
        dialogue = findViewById(R.id.aiDialogue)
        btnStart = findViewById(R.id.btnStartGameFromAI)
        btnSkip = findViewById(R.id.btnSkipAI)

        val selectedProfiles = intent.getStringArrayListExtra("selected_profiles") ?: arrayListOf()
        val assignedColors = intent.getStringArrayListExtra("assigned_colors") ?: arrayListOf()

        if (selectedProfiles.isEmpty()) {
            Toast.makeText(this, "No players provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPlayers(selectedProfiles, assignedColors)
        playIntro()

        val finishAction: (Int) -> Unit = { code ->
            setResult(code, intent)
            finish()
        }

        btnStart.setOnClickListener { finishAction(RESULT_OK) }
        btnSkip.setOnClickListener { finishAction(RESULT_OK) }
    }

    private fun loadPlayers(ids: List<String>, colors: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val profiles = ids.mapNotNull { profileManager.getProfile(it) }
            withContext(Dispatchers.Main) {
                playerContainer.removeAllViews()
                profiles.forEachIndexed { index, p ->
                    val colorHex = colors.getOrNull(index) ?: p.avatarColor
                    playerContainer.addView(playerChip(p, colorHex))
                }
            }
        }
    }

    private fun playerChip(profile: PlayerProfile, colorHex: String): View {
        return TextView(this).apply {
            text = "${profile.nickname.ifBlank { profile.name }} • ${profile.playerCode}"
            setPadding(20)
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(Color.parseColor("#${colorHex.ifBlank { "304050" }}"))
            setOnClickListener {
                Toast.makeText(context, "${profile.nickname.ifBlank { profile.name }} ready!", Toast.LENGTH_SHORT).show()
            }
            val anim = ObjectAnimator.ofFloat(this, "translationX", -20f, 20f).apply {
                duration = 1200
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
            post { anim.start() }
        }
    }

    private fun playIntro() {
        val lines = listOf(
            "☁️ Cloudie here! Ready to host.",
            "Let's warm up the board and the LEDs.",
            "I'll cue each player on their turn—roll when I call you!"
        )
        lifecycleScope.launch {
            dialogue.text = lines.first()
            lines.drop(1).forEachIndexed { _, line ->
                withContext(Dispatchers.Main) {
                    dialogue.postDelayed({ dialogue.text = line }, 1200L)
                }
            }
        }
    }
}
