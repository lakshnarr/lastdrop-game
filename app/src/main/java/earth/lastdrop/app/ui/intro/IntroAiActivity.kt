package earth.lastdrop.app.ui.intro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupMenu
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import earth.lastdrop.app.ProfileManager
import earth.lastdrop.app.ProfileSelectionActivity
import earth.lastdrop.app.R
import earth.lastdrop.app.PlayerProfile
import earth.lastdrop.app.GameEngine
import earth.lastdrop.app.ChanceCard
import earth.lastdrop.app.TurnResult
import earth.lastdrop.app.ESP32ConnectionManager
import earth.lastdrop.app.BoardScanManager
import earth.lastdrop.app.voice.HybridVoiceService
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.VoiceService
import earth.lastdrop.app.voice.VoiceSettingsManager
import com.example.lastdrop.ui.components.ScorecardBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private lateinit var voiceService: VoiceService
    private lateinit var virtualDicePanel: LinearLayout
    private val cloudiePrefs by lazy { getSharedPreferences("cloudie_prefs", MODE_PRIVATE) }
    
    // Game Engine & State
    private lateinit var gameEngine: GameEngine
    private val currentGameProfiles = mutableListOf<PlayerProfile>()
    private var currentPlayerIndex = 0
    private val playerPositions = IntArray(4) { 1 } // Track positions (1-based)
    private val playerScores = IntArray(4) { 0 }    // Track scores
    private val playerAlive = BooleanArray(4) { true } // Track elimination
    private var gameStarted = false
    
    // BLE Managers
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var esp32Manager: ESP32ConnectionManager? = null
    private var esp32Connected = false
    private var useBluetoothDice = false  // Toggle between Bluetooth and Virtual dice
    
    // Toolbar icon references
    private lateinit var iconConnect: ImageView
    private lateinit var iconDiceMode: ImageView
    private lateinit var iconPlayers: ImageView
    private lateinit var iconHistory: ImageView
    private lateinit var iconRanks: ImageView
    private lateinit var iconUndo: ImageView
    private lateinit var iconRefresh: ImageView
    private lateinit var iconReset: ImageView
    private lateinit var iconEndGame: ImageView
    private lateinit var iconDebug: ImageView
    
    // Scorecard badges
    private val scorecardBadges = arrayOfNulls<ScorecardBadge>(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_ai)

        profileManager = ProfileManager(this)
        val voiceSettingsManager = VoiceSettingsManager(this)
        val voiceSettings = voiceSettingsManager.getSettings()
        voiceService = runCatching {
            HybridVoiceService(
                context = this,
                settings = voiceSettings,
                onReady = { appendDebug("🔊 Cloudie voice ready (${if (voiceSettings.useElevenLabs) "ElevenLabs + TTS" else "TTS only"})") },
                onError = {
                    appendDebug("⚠️ Voice error: $it")
                    runOnUiThread {
                        Toast.makeText(this, "Cloudie voice unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }.getOrElse {
            appendDebug("⚠️ Falling back to silent Cloudie (Voice unavailable)")
            runOnUiThread {
                Toast.makeText(this, "Cloudie voice unavailable", Toast.LENGTH_SHORT).show()
            }
            NoOpVoiceService(this)
        }
        cloudieImage = findViewById(R.id.cloudieImage)
        dialogue = findViewById(R.id.cloudieDialogue)
        dropsRow = findViewById(R.id.dropsRow)
        virtualDicePanel = findViewById(R.id.virtualDicePanel)
        
        // Initialize Game Engine
        gameEngine = GameEngine()
        
        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        // Initialize toolbar icons
        setupToolbar()
        
        // Setup virtual dice buttons
        setupVirtualDice()
        
        // Setup scorecard badges
        setupScorecards()

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
    }

    override fun onDestroy() {
        voiceService?.shutdown()
        esp32Manager?.disconnect()
        super.onDestroy()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Go back to ProfileSelectionActivity, clear task stack
        val intent = Intent(this, ProfileSelectionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun speakLine(line: String) {
        dialogue.text = line
        voiceService.speak(line)
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
                
                // Initialize game with these profiles
                if (profiles.isNotEmpty()) {
                    initializeGame(profiles)
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
        val pool = listOf(
            "☁️ Cloudie here! Ready to host.",
            "I tuned into your colors—looking sharp!",
            "Tap start when you want to hit the board.",
            "I brought extra sparkles—let's play!",
            "Who's rolling first? I'm cheering for everyone!",
            "Colors locked in. Adventures ahead!"
        ).shuffled().take(3)

        dialogue.isVisible = true
        lifecycleScope.launch {
            pool.forEach { line ->
                speakLine(line)
                val pauseMs = (line.length * 60L).coerceIn(1200L, 2200L)
                delay(pauseMs)
            }
        }
    }

    // ==================== TOOLBAR FUNCTIONALITY ====================

    private fun setupToolbar() {
        iconConnect = findViewById(R.id.iconConnect)
        iconDiceMode = findViewById(R.id.iconDiceMode)
        iconPlayers = findViewById(R.id.iconPlayers)
        iconHistory = findViewById(R.id.iconHistory)
        iconRanks = findViewById(R.id.iconRanks)
        iconUndo = findViewById(R.id.iconUndo)
        iconRefresh = findViewById(R.id.iconRefresh)
        iconReset = findViewById(R.id.iconReset)
        iconEndGame = findViewById(R.id.iconEndGame)
        iconDebug = findViewById(R.id.iconDebug)

        // Connect icon with dropdown menu
        iconConnect.setOnClickListener { showConnectMenu(it) }

        // Dice mode toggle - switch between Bluetooth and Virtual dice
        iconDiceMode.setOnClickListener {
            toggleDiceMode()
        }

        // Players - go back to profile selection
        iconPlayers.setOnClickListener {
            val intent = Intent(this, ProfileSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        // History (placeholder - will be implemented later)
        iconHistory.setOnClickListener {
            Toast.makeText(this, "Game history coming soon", Toast.LENGTH_SHORT).show()
        }

        // Ranks (placeholder - will be implemented later)
        iconRanks.setOnClickListener {
            Toast.makeText(this, "Leaderboard coming soon", Toast.LENGTH_SHORT).show()
        }

        // Undo (placeholder - will be implemented in Phase 2)
        iconUndo.setOnClickListener {
            Toast.makeText(this, "Undo feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Refresh (placeholder - will be implemented in Phase 2)
        iconRefresh.setOnClickListener {
            Toast.makeText(this, "Refresh coming soon", Toast.LENGTH_SHORT).show()
        }

        // Reset - restart game with same players
        iconReset.setOnClickListener {
            if (currentGameProfiles.isEmpty()) {
                Toast.makeText(this, "No game to reset", Toast.LENGTH_SHORT).show()
            } else {
                resetGame()
            }
        }

        // End Game - finish current game
        iconEndGame.setOnClickListener {
            if (!gameStarted) {
                Toast.makeText(this, "No game in progress", Toast.LENGTH_SHORT).show()
            } else {
                endGame()
            }
        }

        // Debug button - navigate to MainActivity (hidden by default)
        iconDebug.setOnClickListener {
            try {
                val mainActivityClass = Class.forName("earth.lastdrop.app.MainActivity")
                val intent = Intent(this, mainActivityClass)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "MainActivity not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConnectMenu(anchor: View) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(R.menu.connect_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.connect_dice -> {
                    connectGoDice()
                    true
                }
                R.id.connect_board -> {
                    connectESP32Board()
                    true
                }
                R.id.connect_server -> {
                    Toast.makeText(this, "Server connection - Coming in Phase 2.4", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    // ==================== BLE CONNECTION MANAGEMENT ====================

    private fun connectGoDice() {
        // For Phase 2, placeholder - full GoDice integration will come in Phase 2.4
        Toast.makeText(this, "GoDice connection - Full integration coming in Phase 2.4", Toast.LENGTH_SHORT).show()
        appendDebug("GoDice integration planned for Phase 2.4")
    }

    private fun connectESP32Board() {
        // For Phase 2, use simple direct connection
        // Full BoardScanManager integration will come in later phases
        Toast.makeText(this, "ESP32 scanning - Full integration coming in Phase 2.4", Toast.LENGTH_SHORT).show()
        
        // Direct connect to default ESP32
        if (esp32Manager == null) {
            esp32Manager = ESP32ConnectionManager(
                context = this,
                bluetoothAdapter = bluetoothAdapter,
                onConnectionStateChanged = { connected ->
                    esp32Connected = connected
                    val status = if (connected) "connected ✅" else "disconnected ❌"
                    Toast.makeText(this, "ESP32 $status", Toast.LENGTH_SHORT).show()
                },
                onLogMessage = { message ->
                    appendDebug(message)
                },
                onCharacteristicChanged = { jsonResponse ->
                    handleESP32Response(jsonResponse)
                }
            )
        }
        
        esp32Manager?.connect()
    }

    private fun handleESP32Response(jsonResponse: String) {
        // Parse ESP32 JSON responses (coin_placed, misplacement, etc.)
        appendDebug("ESP32: $jsonResponse")
        // Will be fully implemented with game state sync in Phase 2.4
    }

    private fun toggleDiceMode() {
        useBluetoothDice = !useBluetoothDice
        
        val icon = if (useBluetoothDice) R.drawable.ic_dice_bluetooth else R.drawable.ic_dice_virtual
        iconDiceMode.setImageResource(icon)
        
        // Show/hide virtual dice panel
        virtualDicePanel.visibility = if (useBluetoothDice) View.GONE else View.VISIBLE
        
        val mode = if (useBluetoothDice) "Bluetooth Dice" else "Virtual Dice"
        Toast.makeText(this, "Switched to $mode", Toast.LENGTH_SHORT).show()
        speakLine("Now using $mode")
    }

    private fun setupVirtualDice() {
        findViewById<Button>(R.id.btnDice1).setOnClickListener { rollVirtualDice(1) }
        findViewById<Button>(R.id.btnDice2).setOnClickListener { rollVirtualDice(2) }
        findViewById<Button>(R.id.btnDice3).setOnClickListener { rollVirtualDice(3) }
        findViewById<Button>(R.id.btnDice4).setOnClickListener { rollVirtualDice(4) }
        findViewById<Button>(R.id.btnDice5).setOnClickListener { rollVirtualDice(5) }
        findViewById<Button>(R.id.btnDice6).setOnClickListener { rollVirtualDice(6) }
    }
    
    private fun setupScorecards() {
        scorecardBadges[0] = findViewById(R.id.scorecard1)
        scorecardBadges[1] = findViewById(R.id.scorecard2)
        scorecardBadges[2] = findViewById(R.id.scorecard3)
        scorecardBadges[3] = findViewById(R.id.scorecard4)
        
        // Initially hide all badges
        scorecardBadges.forEach { it?.visibility = View.GONE }
    }

    private fun rollVirtualDice(value: Int) {
        if (!gameStarted) {
            Toast.makeText(this, "Start game first!", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (useBluetoothDice) {
            Toast.makeText(this, "Switch to Virtual Dice mode first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Process the roll
        handleNewRoll(value)
    }

    // ==================== GAME LOGIC ====================

    private fun initializeGame(profiles: List<PlayerProfile>) {
        currentGameProfiles.clear()
        currentGameProfiles.addAll(profiles)
        
        // Reset game state
        currentPlayerIndex = 0
        gameStarted = true
        
        for (i in currentGameProfiles.indices) {
            playerPositions[i] = 1
            playerScores[i] = 0
            playerAlive[i] = true
        }
        
        // Setup scorecard badges with player colors
        for (i in profiles.indices) {
            scorecardBadges[i]?.apply {
                visibility = View.VISIBLE
                setScore(0)
                setBorderColor(Color.parseColor(profiles[i].avatarColor))
            }
        }
        
        // Hide unused badges
        for (i in profiles.size until 4) {
            scorecardBadges[i]?.visibility = View.GONE
        }
        
        updateGameUI()
        speakLine("Game initialized! ${profiles[0].nickname} goes first.")
    }

    private fun handleNewRoll(diceValue: Int) {
        if (!gameStarted || currentGameProfiles.isEmpty()) {
            Toast.makeText(this, "Start game first!", Toast.LENGTH_SHORT).show()
            return
        }

        val safeIndex = currentPlayerIndex.coerceIn(0, currentGameProfiles.size - 1)
        val currentProfile = currentGameProfiles[safeIndex]
        val currentPos = playerPositions[safeIndex]

        // Process turn through GameEngine
        val turnResult = gameEngine.processTurn(currentPos, diceValue)
        
        // Update player state
        playerPositions[safeIndex] = turnResult.newPosition
        playerScores[safeIndex] += turnResult.scoreChange
        
        // Check for elimination (score <= 0)
        if (playerScores[safeIndex] <= 0) {
            playerAlive[safeIndex] = false
            speakLine("${currentProfile.nickname} has been eliminated!")
        } else {
            // Generate Cloudie dialogue
            val speech = generateCloudieDialogue(currentProfile, diceValue, turnResult)
            speakLine(speech)
        }
        
        // Update UI
        updateGameUI()
        updateLastEventText(currentProfile.nickname, diceValue, turnResult)
        
        // Move to next player
        advanceToNextPlayer()
    }

    private fun generateCloudieDialogue(
        profile: PlayerProfile,
        diceValue: Int,
        result: TurnResult
    ): String {
        val name = profile.nickname.ifBlank { profile.name }
        val scoreText = when {
            result.scoreChange > 0 -> "gained ${result.scoreChange} points"
            result.scoreChange < 0 -> "lost ${-result.scoreChange} points"
            else -> "no score change"
        }
        
        return when {
            result.chanceCard != null -> 
                "$name drew a chance card: ${result.chanceCard.description}! $scoreText"
            result.scoreChange > 5 -> 
                "Wow! $name landed on ${result.tile.name} and $scoreText!"
            result.scoreChange < -5 ->
                "Oops! $name hit ${result.tile.name} and $scoreText."
            else ->
                "$name rolled $diceValue, landed on ${result.tile.name}. $scoreText."
        }
    }

    private fun advanceToNextPlayer() {
        val startIndex = currentPlayerIndex
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % currentGameProfiles.size
            // Loop until we find alive player or return to start
            if (playerAlive[currentPlayerIndex]) break
            if (currentPlayerIndex == startIndex) {
                // All players eliminated, game over
                endGame()
                return
            }
        } while (!playerAlive[currentPlayerIndex])
        
        val nextProfile = currentGameProfiles[currentPlayerIndex]
        speakLine("${nextProfile.nickname}'s turn!")
    }

    private fun updateGameUI() {
        // Update scorecard badges with animated score changes
        for (i in currentGameProfiles.indices) {
            scorecardBadges[i]?.animateToScore(playerScores[i])
            
            // Pulse animation for active player
            if (i == currentPlayerIndex) {
                scorecardBadges[i]?.startPulseAnimation()
            } else {
                scorecardBadges[i]?.stopAnimations()
            }
            
            // Dim eliminated players
            scorecardBadges[i]?.alpha = if (playerAlive[i]) 1.0f else 0.3f
        }
        
        appendDebug("Scores: ${playerScores.contentToString()}")
        appendDebug("Positions: ${playerPositions.contentToString()}")
    }

    private fun updateLastEventText(playerName: String, diceValue: Int, result: TurnResult) {
        val lastEventText = findViewById<TextView>(R.id.lastEventText)
        lastEventText.text = "Last: $playerName rolled $diceValue, moved to tile ${result.newPosition} (${result.tile.name})"
    }

    private fun endGame() {
        gameStarted = false
        
        // Find winner (highest score among alive players)
        var maxScore = Int.MIN_VALUE
        var winnerIndex = -1
        
        for (i in currentGameProfiles.indices) {
            if (playerAlive[i] && playerScores[i] > maxScore) {
                maxScore = playerScores[i]
                winnerIndex = i
            }
        }
        
        if (winnerIndex >= 0) {
            val winner = currentGameProfiles[winnerIndex]
            speakLine("Game Over! ${winner.nickname} wins with $maxScore points! 🎉")
        } else {
            speakLine("Game Over! It's a tie!")
        }
    }

    private fun resetGame() {
        if (currentGameProfiles.isNotEmpty()) {
            initializeGame(currentGameProfiles)
        }
    }
}
