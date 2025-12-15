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
import earth.lastdrop.app.MainActivity
import earth.lastdrop.app.GameHistoryActivity
import earth.lastdrop.app.LeaderboardActivity
import earth.lastdrop.app.R
import earth.lastdrop.app.PlayerProfile
import earth.lastdrop.app.GameEngine
import earth.lastdrop.app.ChanceCard
import earth.lastdrop.app.TurnResult
import earth.lastdrop.app.ESP32ConnectionManager
import earth.lastdrop.app.BoardScanManager
import earth.lastdrop.app.VirtualDiceView
import earth.lastdrop.app.DiceConnectionController
import earth.lastdrop.app.LiveServerUiHelper
import org.sample.godicesdklib.GoDiceSDK
import earth.lastdrop.app.voice.HybridVoiceService
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.VoiceService
import earth.lastdrop.app.voice.VoiceSettingsManager
import com.example.lastdrop.ui.components.ScorecardBadge
import com.example.lastdrop.ui.components.EmoteManager
import com.example.lastdrop.ui.components.DialogueGenerator
import com.example.lastdrop.ui.components.SoundEffectManager
import com.example.lastdrop.ui.components.SoundEffect
import com.example.lastdrop.ui.components.HapticFeedbackManager
import com.example.lastdrop.ui.components.ParticleEffectView
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.children
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import earth.lastdrop.app.LastDropDatabase
import earth.lastdrop.app.SavedGame
import earth.lastdrop.app.SavedGameDao
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import android.util.Log

class IntroAiActivity : AppCompatActivity(), GoDiceSDK.Listener {

    companion object {
        private const val REQUEST_CODE_MAIN_ACTIVITY = 2001
    }

    private lateinit var profileManager: ProfileManager
    private lateinit var cloudieAnimation: LottieAnimationView
    private lateinit var dialogue: TextView
    private lateinit var dropsRow: LinearLayout
    private lateinit var voiceService: VoiceService
    private lateinit var virtualDiceView: VirtualDiceView
    private lateinit var emoteManager: EmoteManager
    private lateinit var dialogueGenerator: DialogueGenerator
    private lateinit var soundManager: SoundEffectManager
    private lateinit var hapticManager: HapticFeedbackManager
    private lateinit var particleEffectView: ParticleEffectView
    private val cloudiePrefs by lazy { getSharedPreferences("cloudie_prefs", MODE_PRIVATE) }
    
    // Game Engine & State
    private lateinit var gameEngine: GameEngine
    private val currentGameProfiles = mutableListOf<PlayerProfile>()
    private val selectedProfileIds = mutableListOf<String>() // Store original profile IDs
    private val assignedColors = mutableListOf<String>() // Store original color assignments
    private var currentPlayerIndex = 0
    private val playerPositions = IntArray(4) { 1 } // Track positions (1-based)
    private val playerScores = IntArray(4) { 0 }    // Track scores
    private val playerAlive = BooleanArray(4) { true } // Track elimination
    private var gameStarted = false
    
    // Undo state tracking
    private var lastRoll: Int? = null
    private var previousRoll: Int? = null
    private var previousPlayerIndex = 0
    private var previousPosition = 1
    private var previousScore = 0
    
    // BLE Managers
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var esp32Manager: ESP32ConnectionManager? = null
    private var esp32Connected = false
    private var diceConnectionController: DiceConnectionController? = null
    private var playWithTwoDice = false
    private var diceConnected = false
    private val diceResults = HashMap<Int, Int>()
    private val diceColorMap = HashMap<Int, String>()
    private var useBluetoothDice = true  // Toggle between Bluetooth and Virtual dice (default: Bluetooth)
    private var isSyncingState = false // Flag to prevent callbacks during state sync
    
    // Database
    private lateinit var savedGameDao: SavedGameDao
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Toolbar icon references
    private lateinit var iconConnect: ImageView
    private lateinit var iconBoard: ImageView
    private lateinit var iconSave: ImageView
    private lateinit var iconDiceMode: ImageView
    private lateinit var iconPlayers: ImageView
    private lateinit var iconHistory: ImageView
    private lateinit var iconRanks: ImageView
    private lateinit var iconUndo: ImageView
    private lateinit var iconRefresh: ImageView
    private lateinit var iconReset: ImageView
    private lateinit var iconEndGame: ImageView
    private lateinit var iconClose: ImageView
    private lateinit var iconDebug: ImageView
    
    // Scorecard badges
    private val scorecardBadges = arrayOfNulls<ScorecardBadge>(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_ai)

        profileManager = ProfileManager(this)
        val db = LastDropDatabase.getInstance(this)
        savedGameDao = db.savedGameDao()
        
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
        cloudieAnimation = findViewById(R.id.cloudieAnimation)
        dialogue = findViewById(R.id.cloudieDialogue)
        dropsRow = findViewById(R.id.dropsRow)
        virtualDiceView = findViewById(R.id.virtualDiceView)
        virtualDiceView.setValue(1) // Initialize with value 1
        virtualDiceView.visibility = View.GONE // Hidden by default (Bluetooth mode)
        
        // Initialize EmoteManager for Lottie animations
        emoteManager = EmoteManager(this)
        
        // Initialize SoundEffectManager for game audio
        soundManager = SoundEffectManager(this)
        soundManager.initialize()
        
        // Initialize HapticFeedbackManager for tactile feedback
        hapticManager = HapticFeedbackManager(this)
        
        // Initialize ParticleEffectView for visual effects
        particleEffectView = findViewById(R.id.particleEffectView)
        
        // Initialize DialogueGenerator for contextual speech
        dialogueGenerator = DialogueGenerator()
        
        // Initialize Game Engine
        gameEngine = GameEngine()
        
        // Initialize Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        
        // Initialize GoDice SDK
        GoDiceSDK.listener = this
        
        // Initialize toolbar icons
        setupToolbar()
        
        // Set initial dice mode icon to Bluetooth
        iconDiceMode.setImageResource(R.drawable.ic_dice_bluetooth)
        
        // Setup virtual dice buttons
        setupVirtualDice()
        
        // Setup scorecard badges
        setupScorecards()

        val selectedProfiles = intent.getStringArrayListExtra("selected_profiles") ?: arrayListOf()
        val colors = intent.getStringArrayListExtra("assigned_colors") ?: arrayListOf()

        if (selectedProfiles.isEmpty()) {
            Toast.makeText(this, "No players provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Store profile IDs and colors for later use
        selectedProfileIds.clear()
        selectedProfileIds.addAll(selectedProfiles)
        assignedColors.clear()
        assignedColors.addAll(colors)

        setInitialStates()
        bindPlayers(selectedProfiles, colors)
        playIntroLines()
        playEntranceAnimations()
    }

    override fun onDestroy() {
        voiceService?.shutdown()
        esp32Manager?.disconnect()
        soundManager.release()
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
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle return from MainActivity with game state sync
        if (requestCode == REQUEST_CODE_MAIN_ACTIVITY && resultCode == RESULT_OK && data != null) {
            android.util.Log.d("IntroAiActivity", "Received game state from MainActivity")
            
            // Set sync flag to prevent disconnection callbacks
            isSyncingState = true
            
            // Sync game state
            currentPlayerIndex = data.getIntExtra("current_player_index", currentPlayerIndex)
            gameStarted = data.getBooleanExtra("game_started", gameStarted)
            playWithTwoDice = data.getBooleanExtra("play_with_two_dice", playWithTwoDice)
            
            // Sync connection states
            val syncedUseBluetoothDice = data.getBooleanExtra("use_bluetooth_dice", useBluetoothDice)
            val syncedDiceConnected = data.getBooleanExtra("dice_connected", diceConnected)
            val syncedEsp32Connected = data.getBooleanExtra("esp32_connected", esp32Connected)
            
            // Apply virtual dice mode if changed (silently, no messages)
            if (useBluetoothDice != syncedUseBluetoothDice) {
                useBluetoothDice = syncedUseBluetoothDice
                if (useBluetoothDice) {
                    virtualDiceView?.visibility = View.GONE
                    android.util.Log.d("IntroAiActivity", "Synced to Bluetooth dice mode")
                } else {
                    virtualDiceView?.visibility = View.VISIBLE
                    android.util.Log.d("IntroAiActivity", "Synced to Virtual dice mode")
                }
            }
            
            // Silently sync connection states (no toasts or voice messages)
            diceConnected = syncedDiceConnected
            esp32Connected = syncedEsp32Connected
            
            android.util.Log.d("IntroAiActivity", "Synced connection states: virtualDice=${!useBluetoothDice}, dice=$diceConnected, board=$esp32Connected")
            
            // Sync positions
            data.getIntegerArrayListExtra("player_positions")?.let { positions ->
                positions.forEachIndexed { index, pos ->
                    if (index < playerPositions.size) {
                        playerPositions[index] = pos
                    }
                }
            }
            
            // Sync scores
            data.getIntegerArrayListExtra("player_scores")?.let { scores ->
                scores.forEachIndexed { index, score ->
                    if (index < playerScores.size) {
                        playerScores[index] = score
                    }
                }
            }
            
            // Sync alive status
            data.getBooleanArrayExtra("player_alive")?.let { alive ->
                alive.forEachIndexed { index, isAlive ->
                    if (index < playerAlive.size) {
                        playerAlive[index] = isAlive
                    }
                }
            }
            
            // Clear sync flag
            isSyncingState = false
            
            // Update UI with synced state
            updateAllScorecards()
            
            Toast.makeText(this, "Game state synced from Classic mode", Toast.LENGTH_SHORT).show()
            soundManager.playSound(SoundEffect.SCORE_GAIN)
        }
        
        // Handle profile selection result (existing code)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val profileIds = data.getStringArrayListExtra("selected_profiles") ?: return
            val colors = data.getStringArrayListExtra("assigned_colors") ?: emptyList()
            
            selectedProfileIds.clear()
            selectedProfileIds.addAll(profileIds)
            assignedColors.clear()
            assignedColors.addAll(colors)
            
            bindPlayers(profileIds, colors)
        }
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
        cloudieAnimation.translationY = -80f
        cloudieAnimation.scaleX = 0.8f
        cloudieAnimation.scaleY = 0.8f
        cloudieAnimation.alpha = 0f

        dropsRow.children.forEach { child ->
            child.translationY = -40f
            child.alpha = 0f
        }
    }

    private fun playEntranceAnimations() {
        // Start Cloudie idle animation
        emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_IDLE)
        
        // Cloudie flies in and grows
        cloudieAnimation.animate()
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
        iconBoard = findViewById(R.id.iconBoard)
        android.util.Log.d("IntroAiActivity", "iconBoard initialized: $iconBoard")
        iconSave = findViewById(R.id.iconSave)
        iconDiceMode = findViewById(R.id.iconDiceMode)
        iconPlayers = findViewById(R.id.iconPlayers)
        iconHistory = findViewById(R.id.iconHistory)
        iconRanks = findViewById(R.id.iconRanks)
        iconUndo = findViewById(R.id.iconUndo)
        iconRefresh = findViewById(R.id.iconRefresh)
        iconReset = findViewById(R.id.iconReset)
        iconEndGame = findViewById(R.id.iconEndGame)
        iconClose = findViewById(R.id.iconClose)
        iconDebug = findViewById(R.id.iconDebug)

        // Connect icon with dropdown menu
        iconConnect.setOnClickListener { 
            hapticManager.vibrateButtonClick()
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            showConnectMenu(it) 
        }

        // Classic Mode - go to MainActivity (traditional board gameplay)
        iconBoard.setOnClickListener {
            android.util.Log.d("IntroAiActivity", "Classic Mode icon clicked - Opening MainActivity")
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            Toast.makeText(this, "Switching to Classic Mode", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(this, MainActivity::class.java)
            // Pass profile IDs and colors to MainActivity
            if (selectedProfileIds.isNotEmpty()) {
                intent.putStringArrayListExtra("selected_profiles", ArrayList(selectedProfileIds))
                intent.putStringArrayListExtra("assigned_colors", ArrayList(assignedColors))
                android.util.Log.d("IntroAiActivity", "Passing ${selectedProfileIds.size} profiles with colors: $assignedColors")
            }
            // Pass current game state to MainActivity
            intent.putExtra("current_player_index", currentPlayerIndex)
            intent.putExtra("game_started", gameStarted)
            intent.putIntegerArrayListExtra("player_positions", ArrayList(playerPositions.toList()))
            intent.putIntegerArrayListExtra("player_scores", ArrayList(playerScores.toList()))
            intent.putExtra("player_alive", playerAlive)
            intent.putExtra("play_with_two_dice", playWithTwoDice)
            
            // Pass connection states
            intent.putExtra("use_bluetooth_dice", useBluetoothDice)
            intent.putExtra("dice_connected", diceConnected)
            intent.putExtra("esp32_connected", esp32Connected)
            
            android.util.Log.d("IntroAiActivity", "Connection states: virtualDice=${!useBluetoothDice}, dice=$diceConnected, board=$esp32Connected")
            
            startActivityForResult(intent, REQUEST_CODE_MAIN_ACTIVITY)
        }

        // Save - save game state
        iconSave.setOnClickListener {
            hapticManager.vibrateButtonClick()
            soundManager.playSound(SoundEffect.SCORE_GAIN)
            saveGameState()
        }

        // Dice mode toggle - switch between Bluetooth and Virtual dice
        iconDiceMode.setOnClickListener {
            hapticManager.vibrateButtonClick()
            soundManager.playSound(if (useBluetoothDice) SoundEffect.TOGGLE_OFF else SoundEffect.TOGGLE_ON)
            toggleDiceMode()
        }

        // Players - go back to profile selection
        iconPlayers.setOnClickListener {
            android.util.Log.d("IntroAiActivity", "Players icon clicked - Opening ProfileSelection")
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            Toast.makeText(this, "Players Icon: Opening Profile Selection", Toast.LENGTH_LONG).show()
            val intent = Intent(this, ProfileSelectionActivity::class.java)
            intent.putExtra("FROM_INTRO_AI", true)
            startActivityForResult(intent, 1001)
        }

        // History (placeholder - will be implemented later)
        iconHistory.setOnClickListener {
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            openHistoryForProfile()
        }

        // Ranks (placeholder - will be implemented later)
        iconRanks.setOnClickListener {
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        // Undo (placeholder - will be implemented in Phase 2)
        iconUndo.setOnClickListener {
            if (!gameStarted) {
                soundManager.playSound(SoundEffect.ERROR)
                Toast.makeText(this, "No game in progress", Toast.LENGTH_SHORT).show()
            } else if (previousRoll == null) {
                soundManager.playSound(SoundEffect.WARNING)
                Toast.makeText(this, "Nothing to undo yet", Toast.LENGTH_SHORT).show()
            } else {
                soundManager.playSound(SoundEffect.WARNING)
                showUndoConfirmation()
            }
        }

        // Refresh (placeholder - will be implemented in Phase 2)
        iconRefresh.setOnClickListener {
            if (!gameStarted) {
                soundManager.playSound(SoundEffect.ERROR)
                Toast.makeText(this, "No game to refresh", Toast.LENGTH_SHORT).show()
            } else {
                soundManager.playSound(SoundEffect.BUTTON_CLICK)
                refreshGameState()
            }
        }

        // Reset - restart game with same players
        iconReset.setOnClickListener {
            if (currentGameProfiles.isEmpty()) {
                hapticManager.vibrateError()
                soundManager.playSound(SoundEffect.ERROR)
                Toast.makeText(this, "No game to reset", Toast.LENGTH_SHORT).show()
            } else {
                hapticManager.vibrateWarning()
                soundManager.playSound(SoundEffect.WARNING)
                resetGame()
            }
        }

        // End Game - finish current game
        iconEndGame.setOnClickListener {
            if (!gameStarted) {
                hapticManager.vibrateError()
                soundManager.playSound(SoundEffect.ERROR)
                Toast.makeText(this, "No game in progress", Toast.LENGTH_SHORT).show()
            } else {
                hapticManager.vibrateWarning()
                soundManager.playSound(SoundEffect.WARNING)
                endGame()
            }
        }

        // Close - exit with save/exit dialog (like MainActivity end game)
        iconClose.setOnClickListener {
            hapticManager.vibrateButtonClick()
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            showEndGameDialog()
        }

        // Debug button - navigate to MainActivity (hidden by default)
        iconDebug.setOnClickListener {
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            try {
                val mainActivityClass = Class.forName("earth.lastdrop.app.MainActivity")
                val intent = Intent(this, mainActivityClass)
                startActivity(intent)
            } catch (e: Exception) {
                soundManager.playSound(SoundEffect.ERROR)
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
                    showServerConnectionInfo()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    // ==================== BLE CONNECTION MANAGEMENT ====================

    private fun connectGoDice() {
        if (diceConnected) {
            // Already connected → disconnect
            disconnectAllDice()
            return
        }
        
        // Not connected → show dice mode selection
        showDiceModeDialog()
    }
    
    private fun showDiceModeDialog() {
        val options = arrayOf("Play with 1 die", "Play with 2 dice")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose dice mode")
            .setItems(options) { dialog, which ->
                playWithTwoDice = (which == 1)
                val modeAnnouncement = if (playWithTwoDice) "Two dice mode selected" else "One die mode selected"
                Toast.makeText(this, modeAnnouncement, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                startDiceConnection()
            }
            .show()
    }
    
    private fun startDiceConnection() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Initialize dice connection controller
        if (diceConnectionController == null) {
            diceConnectionController = DiceConnectionController(
                context = this,
                bluetoothAdapter = bluetoothAdapter,
                playWithTwoDice = { playWithTwoDice },
                onStatus = { status ->
                    runOnUiThread {
                        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
                    }
                },
                onLog = { message ->
                    appendDebug("GoDice: $message")
                },
                onDiceConnected = { count, _ ->
                    runOnUiThread {
                        diceConnected = true
                        val status = if (playWithTwoDice) "2 dice connected ✅" else "1 die connected ✅"
                        Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                        soundManager.playSound(SoundEffect.CHANCE_CARD)
                        
                        // Switch to Bluetooth dice mode
                        if (!useBluetoothDice) {
                            toggleDiceMode()
                        }
                    }
                },
                onDiceDisconnected = {
                    runOnUiThread {
                        diceConnected = false
                        // Only show message if not syncing state from MainActivity
                        if (!isSyncingState) {
                            Toast.makeText(this, "Dice disconnected", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        
        diceConnectionController?.startScan()
    }
    
    private fun disconnectAllDice() {
        diceConnectionController?.disconnectAll()
        diceResults.clear()
        diceColorMap.clear()
        diceConnected = false
        
        Toast.makeText(this, "Dice disconnected", Toast.LENGTH_SHORT).show()
        
        // Switch to virtual dice mode
        if (useBluetoothDice) {
            toggleDiceMode()
        }
    }

    private fun connectESP32Board() {
        // Check if ESP32 manager already exists and is connected
        if (esp32Manager != null && esp32Connected) {
            Toast.makeText(this, "ESP32 already connected ✅", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show scanning dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Scanning for ESP32 Board")
            .setMessage("Looking for LASTDROP-* boards...\n\nMake sure your board is powered on.")
            .setCancelable(false)
            .create()
        dialog.show()
        
        lifecycleScope.launch {
            delay(500) // Brief delay for visual feedback
            
            // Initialize ESP32 manager if needed
            if (esp32Manager == null) {
                esp32Manager = ESP32ConnectionManager(
                    context = this@IntroAiActivity,
                    bluetoothAdapter = bluetoothAdapter,
                    onConnectionStateChanged = { connected ->
                        esp32Connected = connected
                        lifecycleScope.launch(Dispatchers.Main) {
                            val status = if (connected) "Connected ✅" else "Disconnected ❌"
                            Toast.makeText(this@IntroAiActivity, "ESP32 $status", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogMessage = { message ->
                        appendDebug(message)
                    },
                    onCharacteristicChanged = { jsonResponse ->
                        handleESP32Response(jsonResponse)
                    }
                )
            }
            
            // Attempt connection
            try {
                esp32Manager?.connect()
                delay(2000) // Give time for connection
                
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (esp32Connected) {
                        androidx.appcompat.app.AlertDialog.Builder(this@IntroAiActivity)
                            .setTitle("ESP32 Connected")
                            .setMessage("Physical board is ready!\n\nNote: Coin placement tracking is available in MainActivity for full game integration.")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Open MainActivity") { _, _ ->
                                val intent = Intent(this@IntroAiActivity, MainActivity::class.java)
                                startActivity(intent)
                            }
                            .show()
                    } else {
                        androidx.appcompat.app.AlertDialog.Builder(this@IntroAiActivity)
                            .setTitle("Connection Failed")
                            .setMessage("Could not find ESP32 board.\n\n• Check board is powered on\n• Board should advertise as LASTDROP-*\n• Try again or use MainActivity for advanced scanning")
                            .setPositiveButton("Retry") { _, _ -> connectESP32Board() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@IntroAiActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleESP32Response(jsonResponse: String) {
        // Parse ESP32 JSON responses (coin_placed, misplacement, etc.)
        appendDebug("ESP32: $jsonResponse")
        // Will be fully implemented with game state sync in Phase 2.4
    }

    private fun showServerConnectionInfo() {
        // Same workflow as MainActivity - show QR scan dialog
        LiveServerUiHelper.showConnectDialog(this) {
            LiveServerUiHelper.checkCameraPermissionAndScan(
                activity = this,
                requestCode = 1002 // Camera permission request code
            )
        }
    }

    private fun toggleDiceMode() {
        useBluetoothDice = !useBluetoothDice
        
        val icon = if (useBluetoothDice) R.drawable.ic_dice_bluetooth else R.drawable.ic_dice_virtual
        iconDiceMode.setImageResource(icon)
        
        // Show/hide virtual dice view
        virtualDiceView.visibility = if (useBluetoothDice) View.GONE else View.VISIBLE
        
        val mode = if (useBluetoothDice) "Bluetooth Dice" else "Virtual Dice"
        Toast.makeText(this, "Switched to $mode", Toast.LENGTH_SHORT).show()
        speakLine("Now using $mode")
    }

    private fun setupVirtualDice() {
        virtualDiceView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                if (!virtualDiceView.isRolling() && !useBluetoothDice && gameStarted) {
                    // Generate random value 1-6
                    val targetValue = (1..6).random()
                    val intensity = 0.7f + kotlin.random.Random.nextFloat() * 0.3f // 0.7-1.0
                    
                    // Roll dice with 3D animation
                    virtualDiceView.rollDice(targetValue, intensity) {
                        // Callback when animation completes
                        handleNewRoll(targetValue)
                    }
                } else if (!gameStarted) {
                    Toast.makeText(this, "Start game first!", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun setupScorecards() {
        scorecardBadges[0] = findViewById(R.id.scorecard1)
        scorecardBadges[1] = findViewById(R.id.scorecard2)
        scorecardBadges[2] = findViewById(R.id.scorecard3)
        scorecardBadges[3] = findViewById(R.id.scorecard4)
        
        // Initially hide all badges
        scorecardBadges.forEach { it?.visibility = View.GONE }
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
                val colorString = profiles[i].avatarColor.let { 
                    if (it.startsWith("#")) it else "#$it" 
                }
                setBorderColor(Color.parseColor(colorString))
            }
        }
        
        // Hide unused badges
        for (i in profiles.size until 4) {
            scorecardBadges[i]?.visibility = View.GONE
        }
        
        updateGameUI()
        
        // Play game start celebration sound and haptic
        soundManager.playSound(SoundEffect.GAME_WIN)
        hapticManager.vibrateGameWin()
        
        // Burst confetti for game start celebration
        particleEffectView.burstConfetti()
        
        // Generate game start dialogue
        val startDialogue = dialogueGenerator.generateGameStart(
            profiles.size,
            profiles[0].nickname
        )
        speakLine(startDialogue)
        
        // Play excited animation for game start
        emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_EXCITED)
    }

    private fun handleNewRoll(diceValue: Int) {
        if (!gameStarted || currentGameProfiles.isEmpty()) {
            soundManager.playSound(SoundEffect.ERROR)
            Toast.makeText(this, "Start game first!", Toast.LENGTH_SHORT).show()
            return
        }

        // Play dice roll sound
        soundManager.playSound(SoundEffect.DICE_ROLL)
        hapticManager.vibrateDiceRoll()
        
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
            
            soundManager.playSound(SoundEffect.PLAYER_ELIMINATED)
            hapticManager.vibrateElimination()
            
            val remainingCount = playerAlive.count { it }
            val eliminationDialogue = dialogueGenerator.generateElimination(
                currentProfile.nickname,
                remainingCount
            )
            speakLine(eliminationDialogue)
            emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_SAD)
            
        } else {
            // Play tile landing sound and haptic
            soundManager.playSound(SoundEffect.TILE_LAND)
            hapticManager.vibrateTileLand()
            
            // Play score change sound/haptic based on result
            when {
                turnResult.scoreChange > 0 -> {
                    soundManager.playSound(SoundEffect.SCORE_GAIN)
                    hapticManager.vibrateScoreGain()
                    // Spawn sparkles at scorecard location
                    scorecardBadges[safeIndex]?.let { badge ->
                        val location = IntArray(2)
                        badge.getLocationOnScreen(location)
                        particleEffectView.spawnSparkles(
                            location[0] + badge.width / 2f,
                            location[1] + badge.height / 2f
                        )
                    }
                }
                turnResult.scoreChange < 0 -> {
                    soundManager.playSound(SoundEffect.SCORE_LOSS)
                    hapticManager.vibrateScoreLoss()
                }
            }
            
            // Generate roll announcement
            val rollDialogue = dialogueGenerator.generateRollAnnouncement(
                currentProfile.nickname,
                diceValue
            )
            
            // Generate tile landing dialogue
            val tileDialogue = dialogueGenerator.generateTileLanding(
                currentProfile.nickname,
                turnResult.tile,
                turnResult.scoreChange,
                playerScores[safeIndex]
            )
            
            // Chance card dialogue if applicable
            val finalDialogue = if (turnResult.chanceCard != null) {
                soundManager.playSound(SoundEffect.CHANCE_CARD)
                hapticManager.vibrateChanceCard()
                val cardDialogue = dialogueGenerator.generateChanceCard(
                    currentProfile.nickname,
                    turnResult.chanceCard
                )
                "$rollDialogue $tileDialogue $cardDialogue"
            } else {
                "$rollDialogue $tileDialogue"
            }
            
            speakLine(finalDialogue)
            
            // Play appropriate animation based on result
            when {
                turnResult.scoreChange > 5 -> emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_CELEBRATE)
                turnResult.scoreChange < -5 -> emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_WARNING)
                turnResult.chanceCard != null -> emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_THINKING)
                else -> emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_SPEAKING)
            }
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
        val isLeading = playerScores[currentPlayerIndex] == playerScores.max()
        
        val transitionDialogue = dialogueGenerator.generateTurnTransition(
            nextProfile.nickname,
            playerScores[currentPlayerIndex],
            isLeading
        )
        speakLine(transitionDialogue)
        
        // Return to idle animation
        emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_IDLE)
    }

    private fun updateGameUI() {
        // Update scorecard badges with animated score changes
        for (i in currentGameProfiles.indices) {
            scorecardBadges[i]?.animateToScore(playerScores[i])
            
            // Shimmer + pulse animation for active player
            if (i == currentPlayerIndex) {
                scorecardBadges[i]?.startPulseAnimation()
                scorecardBadges[i]?.startShimmer()
            } else {
                scorecardBadges[i]?.stopAnimations()
                scorecardBadges[i]?.stopShimmer()
            }
            
            // Dim eliminated players with fade effect
            scorecardBadges[i]?.animate()
                ?.alpha(if (playerAlive[i]) 1.0f else 0.3f)
                ?.setDuration(300)
                ?.start()
        }
        
        appendDebug("Scores: ${playerScores.contentToString()}")
        appendDebug("Positions: ${playerPositions.contentToString()}")
    }
    
    private fun updateAllScorecards() {
        // Update all scorecard badges with current scores
        for (i in 0 until currentGameProfiles.size) {
            scorecardBadges[i]?.animateToScore(playerScores[i])
            
            // Update visual state based on alive status
            scorecardBadges[i]?.animate()
                ?.alpha(if (playerAlive[i]) 1.0f else 0.3f)
                ?.setDuration(300)
                ?.start()
        }
    }

    private fun updateLastEventText(playerName: String, diceValue: Int, result: TurnResult) {
        val lastEventText = findViewById<TextView>(R.id.lastEventText)
        lastEventText.text = "Last: $playerName rolled $diceValue, moved to tile ${result.newPosition} (${result.tile.name})"
    }

    // ==================== END GAME & EXIT WORKFLOW ====================

    private fun openHistoryForProfile() {
        val intent = Intent(this, GameHistoryActivity::class.java)
        // Use first profile if game started, otherwise use first saved profile
        if (currentGameProfiles.isNotEmpty()) {
            val firstProfile = currentGameProfiles.first()
            intent.putExtra("profileId", firstProfile.playerId)
            intent.putExtra("profileName", firstProfile.nickname.ifBlank { firstProfile.name })
        }
        startActivity(intent)
    }

    private fun showUndoConfirmation() {
        if (previousRoll == null) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Undo Last Move")
            .setMessage("Undo the last roll and return to the previous position?")
            .setPositiveButton("Undo") { _, _ ->
                confirmUndo()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUndo() {
        if (previousRoll == null) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }

        // Restore previous state
        if (previousPlayerIndex < currentGameProfiles.size) {
            playerPositions[previousPlayerIndex] = previousPosition
            playerScores[previousPlayerIndex] = previousScore
            currentPlayerIndex = previousPlayerIndex
            
            val playerName = currentGameProfiles[previousPlayerIndex].nickname
            Toast.makeText(this, "$playerName returned to position $previousPosition", Toast.LENGTH_SHORT).show()
            speakLine("Undo applied. $playerName is back at position $previousPosition.")
            
            // Clear undo data
            previousRoll = null
            lastRoll = null
            
            // Update scorecards
            updateGameUI()
        }
    }

    private fun refreshGameState() {
        Toast.makeText(this, "Refreshing game state...", Toast.LENGTH_SHORT).show()
        
        // Refresh scorecards display
        updateGameUI()
        
        // Show current player
        if (currentGameProfiles.isNotEmpty() && currentPlayerIndex < currentGameProfiles.size) {
            val currentProfile = currentGameProfiles[currentPlayerIndex]
            val message = "${currentProfile.nickname}'s turn - Position ${playerPositions[currentPlayerIndex]}, Score ${playerScores[currentPlayerIndex]}"
            speakLine(message)
        }
        
        Toast.makeText(this, "Game state refreshed", Toast.LENGTH_SHORT).show()
    }

    // ==================== END GAME & EXIT WORKFLOW ====================

    private fun showEndGameDialog() {
        // Check if there's an active game
        val hasActiveGame = gameStarted || currentGameProfiles.isNotEmpty()

        if (hasActiveGame) {
            AlertDialog.Builder(this)
                .setTitle("End game")
                .setMessage("Save the current game to resume later, or exit without saving.")
                .setPositiveButton("Save") { _, _ ->
                    val input = EditText(this).apply {
                        setText(defaultSaveLabel())
                        setSelection(text.length)
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Name this save")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val label = input.text.toString().trim().ifBlank { defaultSaveLabel() }
                            saveCurrentGameSnapshot(label, onFinish = true)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .setNegativeButton("Exit") { _, _ ->
                    gameStarted = false
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit AI page")
                .setMessage("There is no active game to save. Do you want to close this page?")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setNegativeButton("Cancel", null)
                .show()
        }
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
        
        // Announce winner
        val winnerMessage = if (winnerIndex >= 0) {
            val winner = currentGameProfiles[winnerIndex]
            "Game Over! ${winner.nickname} wins with $maxScore points! 🎉"
        } else {
            "Game Over! It's a tie!"
        }
        
        // Play celebration
        soundManager.playSound(SoundEffect.GAME_WIN)
        hapticManager.vibrateGameWin()
        particleEffectView.burstConfetti()
        emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_EXCITED)
        
        // Show result dialog
        AlertDialog.Builder(this)
            .setTitle("🏆 Game Over")
            .setMessage(winnerMessage)
            .setPositiveButton("OK") { _, _ ->
                // Navigate back to profile selection
                val intent = Intent(this, ProfileSelectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
        
        // Also speak the result
        speakLine(winnerMessage)
    }

    private fun resetGame() {
        if (currentGameProfiles.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Reset Game")
            .setMessage("Reset the game with the same players? All progress will be lost.")
            .setPositiveButton("Reset") { _, _ ->
                performReset()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performReset() {
        // Reset game state
        currentPlayerIndex = 0
        gameStarted = true
        
        for (i in currentGameProfiles.indices) {
            playerPositions[i] = 1
            playerScores[i] = 0
            playerAlive[i] = true
        }
        
        // Clear undo state
        lastRoll = null
        previousRoll = null
        
        // Update UI
        updateGameUI()
        
        // Play celebration
        soundManager.playSound(SoundEffect.GAME_WIN)
        hapticManager.vibrateGameWin()
        particleEffectView.burstConfetti()
        
        // Announce reset
        val firstPlayer = currentGameProfiles[0].nickname
        speakLine("Game reset! ${currentGameProfiles.size} players ready. $firstPlayer starts!")
        emoteManager.playCloudieEmote(cloudieAnimation, EmoteManager.CLOUDIE_EXCITED)
        
        Toast.makeText(this, "Game reset - $firstPlayer's turn", Toast.LENGTH_SHORT).show()
    }

    // ==================== GODICE SDK LISTENER IMPLEMENTATIONS ====================

    override fun onDiceColor(diceId: Int, color: Int) {
        val colorName = when (color) {
            GoDiceSDK.DICE_BLACK -> "black"
            GoDiceSDK.DICE_RED -> "red"
            GoDiceSDK.DICE_GREEN -> "green"
            GoDiceSDK.DICE_BLUE -> "blue"
            GoDiceSDK.DICE_YELLOW -> "yellow"
            GoDiceSDK.DICE_ORANGE -> "orange"
            else -> "unknown"
        }
        
        diceColorMap[diceId] = colorName
        appendDebug("Die $diceId color: $colorName")
    }

    override fun onDiceRoll(diceId: Int, number: Int) {
        appendDebug("Die $diceId rolling: $number")
        // This fires while rolling - we'll use onDiceStable for final value
    }

    override fun onDiceStable(diceId: Int, number: Int) {
        if (!gameStarted || !useBluetoothDice) return
        
        diceResults[diceId] = number
        appendDebug("Die $diceId stable: $number")
        
        // Check if we have all needed results
        val neededCount = if (playWithTwoDice) 2 else 1
        if (diceResults.size >= neededCount) {
            val rollValue = if (playWithTwoDice) {
                // Average of two dice
                val values = diceResults.values.toList()
                (values[0] + values[1]) / 2
            } else {
                diceResults.values.first()
            }
            
            // Clear results for next roll
            diceResults.clear()
            
            // Process the roll
            runOnUiThread {
                handleNewRoll(rollValue)
            }
        }
    }

    // ==================== SAVE FUNCTIONALITY ====================

    private fun saveGameState() {
        if (!gameStarted || currentGameProfiles.isEmpty()) {
            Toast.makeText(this, "No active game to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show prompt for save label
        promptForSaveLabel()
    }
    
    private fun promptForSaveLabel() {
        val input = EditText(this).apply {
            setText(defaultSaveLabel())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Name this save")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim().ifBlank { defaultSaveLabel() }
                saveCurrentGameSnapshot(label)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun defaultSaveLabel(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val players = currentGameProfiles.take(currentGameProfiles.size).joinToString(", ") { it.name }.ifBlank { "Players" }
        return "$players • $timestamp"
    }
    
    private fun buildSavedGameSnapshot(label: String): SavedGame? {
        if (currentGameProfiles.isEmpty()) return null

        return runCatching {
            val namesJson = JSONArray().apply {
                currentGameProfiles.forEach { put(it.name) }
            }
            val colorsJson = JSONArray().apply {
                currentGameProfiles.forEachIndexed { index, profile ->
                    // Use favoriteColor from profile, or fallback to position-based color
                    val colors = listOf("red", "green", "blue", "yellow")
                    put(if (index < colors.size) colors[index] else profile.favoriteColor)
                }
            }
            val profileIdsJson = JSONArray().apply {
                currentGameProfiles.forEach { put(it.playerId) }
            }
            val positionsJson = JSONObject().apply {
                currentGameProfiles.forEachIndexed { index, profile ->
                    put(profile.name, playerPositions[index])
                }
            }
            val scoresJson = JSONObject().apply {
                currentGameProfiles.forEachIndexed { index, profile ->
                    put(profile.name, playerScores[index])
                }
            }

            SavedGame(
                gameId = System.currentTimeMillis().toString(),
                playerCount = currentGameProfiles.size,
                currentPlayer = currentPlayerIndex,
                playWithTwoDice = playWithTwoDice,
                playerNames = namesJson.toString(),
                playerColors = colorsJson.toString(),
                currentGameProfileIds = profileIdsJson.toString(),
                playerPositions = positionsJson.toString(),
                playerScores = scoresJson.toString(),
                lastDice1 = 0,
                lastDice2 = 0,
                lastAvg = 0,
                lastTileName = null,
                lastTileType = null,
                lastChanceCardNumber = null,
                lastChanceCardText = null,
                waitingForCoin = false,
                testModeEnabled = false,
                testModeType = 0,
                label = label
            )
        }.getOrElse {
            Log.e("IntroAiActivity", "Failed to build saved game snapshot", it)
            null
        }
    }

    private fun saveCurrentGameSnapshot(label: String, onFinish: Boolean = false) {
        val snapshot = buildSavedGameSnapshot(label) ?: run {
            Toast.makeText(this, "Failed to create save snapshot", Toast.LENGTH_SHORT).show()
            return
        }
        
        mainScope.launch(Dispatchers.IO) {
            runCatching {
                savedGameDao.upsert(snapshot)
            }.onSuccess {
                Log.d("IntroAiActivity", "Saved game snapshot (${snapshot.savedGameId})")
                withContext(Dispatchers.Main) {
                    hapticManager.vibrateScoreGain()
                    Toast.makeText(this@IntroAiActivity, "Game saved successfully", Toast.LENGTH_SHORT).show()
                    
                    // Show dialogue feedback only if not finishing
                    if (!onFinish) {
                        val responseMessage = "Game saved! You can resume this game later from the history."
                        speakLine(responseMessage)
                    }
                    
                    // Finish activity if requested
                    if (onFinish) {
                        finish()
                    }
                }
            }.onFailure {
                Log.e("IntroAiActivity", "Failed to save game snapshot", it)
                withContext(Dispatchers.Main) {
                    hapticManager.vibrateError()
                    soundManager.playSound(SoundEffect.ERROR)
                    Toast.makeText(this@IntroAiActivity, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== GODICE SDK LISTENER ====================

    override fun onDiceChargeLevel(diceId: Int, level: Int) {
        appendDebug("Die $diceId battery: $level%")
    }

    override fun onDiceChargingStateChanged(diceId: Int, charging: Boolean) {
        val state = if (charging) "Charging" else "Not charging"
        appendDebug("Die $diceId $state")
    }
}
