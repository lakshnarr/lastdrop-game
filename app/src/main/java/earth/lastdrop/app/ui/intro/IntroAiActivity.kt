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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import earth.lastdrop.app.BoardPreferencesManager
import earth.lastdrop.app.BoardSelectionDialog
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
import earth.lastdrop.app.BoardScanManager
import earth.lastdrop.app.VirtualDiceView
import earth.lastdrop.app.DiceConnectionController
import earth.lastdrop.app.LiveServerUiHelper
import earth.lastdrop.app.ApiManager
import earth.lastdrop.app.BuildConfig
import org.sample.godicesdklib.GoDiceSDK
import earth.lastdrop.app.voice.HybridVoiceService
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.VoiceService
import earth.lastdrop.app.voice.VoiceSettingsManager
import earth.lastdrop.app.voice.SpeechCallback
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
import java.util.UUID
import earth.lastdrop.app.BoardConnectionController
import earth.lastdrop.app.DebugFileLogger
import earth.lastdrop.app.PinEntryDialog
import earth.lastdrop.app.ui.components.StatusLedBar
import earth.lastdrop.app.ui.components.PlayerScoreboardView
import earth.lastdrop.app.ui.components.DiceRollAnimationView
import earth.lastdrop.app.ui.components.CloudieExpressionView

class IntroAiActivity : AppCompatActivity(), GoDiceSDK.Listener {

    private lateinit var profileManager: ProfileManager
    private var cloudieAnimation: LottieAnimationView? = null  // Now optional (legacy)
    private lateinit var cloudieExpression: CloudieExpressionView  // New simplified Cloudie
    private lateinit var dialogue: TextView
    private var dropsRow: LinearLayout? = null  // Now optional (legacy)
    private lateinit var voiceService: VoiceService
    private lateinit var virtualDiceView: VirtualDiceView
    private var emoteManager: EmoteManager? = null  // Now optional (legacy Lottie)
    private lateinit var dialogueGenerator: DialogueGenerator
    private lateinit var soundManager: SoundEffectManager
    
    // New UI components
    private lateinit var statusLedBar: StatusLedBar
    private lateinit var playerScoreboard: PlayerScoreboardView
    private lateinit var diceRollAnimation: DiceRollAnimationView
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
    private var boardScanManager: BoardScanManager? = null
    private lateinit var boardPreferencesManager: BoardPreferencesManager
    private lateinit var boardConnectionController: BoardConnectionController
    private var esp32Connected = false
    private var esp32Paired = false
    private var pendingPinDevice: BluetoothDevice? = null
    private var pendingPin: String = ""
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
    
    // API Manager for web server connection
    private lateinit var apiManager: ApiManager
    private val API_BASE_URL = "https://lastdrop.earth/api"
    private val API_KEY = BuildConfig.API_KEY
    private val SESSION_ID = java.util.UUID.randomUUID().toString()
    
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

    private val classicModeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        handleClassicModeResult(data)
    }

    private val profileSelectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        handleProfileSelectionResult(data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_ai_v2)  // Using new simplified layout

        // Initialize file logger (overwrites on each fresh start)
        DebugFileLogger.init(this)
        DebugFileLogger.i("IntroAi", "=== IntroAiActivity onCreate (V2 UI) ===")

        profileManager = ProfileManager(this)
        val db = LastDropDatabase.getInstance(this)
        savedGameDao = db.savedGameDao()
        boardPreferencesManager = BoardPreferencesManager(this)
        
        // Initialize ApiManager for web server connection
        apiManager = ApiManager(
            apiBaseUrl = API_BASE_URL,
            apiKey = API_KEY,
            sessionId = SESSION_ID
        )
        
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
        
        // Set up speech callbacks for mouth animation sync
        voiceService.setSpeechCallback(object : SpeechCallback {
            override fun onSpeechStart(utteranceId: String) {
                runOnUiThread {
                    cloudieExpression.startTalking(voiceService.getSpeechRate())
                }
            }
            
            override fun onSpeechDone(utteranceId: String) {
                runOnUiThread {
                    cloudieExpression.stopTalking()
                }
            }
            
            override fun onSpeechError(utteranceId: String, error: String) {
                runOnUiThread {
                    cloudieExpression.stopTalking()
                }
                appendDebug("Speech error: $error")
            }
        })
        
        // Initialize NEW UI components
        statusLedBar = findViewById(R.id.statusLedBar)
        cloudieExpression = findViewById(R.id.cloudieExpression)
        playerScoreboard = findViewById(R.id.playerScoreboard)
        diceRollAnimation = findViewById(R.id.diceRollAnimation)
        dialogue = findViewById(R.id.cloudieDialogue)
        
        // Legacy views (may be null in v2 layout, kept for compatibility)
        cloudieAnimation = findViewById(R.id.cloudieAnimation)
        dropsRow = findViewById(R.id.dropsRow)
        
        virtualDiceView = findViewById(R.id.virtualDiceView)
        virtualDiceView.setValue(1) // Initialize with value 1
        virtualDiceView.visibility = View.GONE // Hidden by default (Bluetooth mode)
        
        // Initialize EmoteManager for legacy Lottie (only if cloudieAnimation exists)
        emoteManager = cloudieAnimation?.let { EmoteManager(this) }
        
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
        
        // Initialize BoardScanManager (match MainActivity behavior)
        boardScanManager = BoardScanManager(
            context = this,
            bluetoothAdapter = bluetoothAdapter,
            scope = lifecycleScope,
            onBoardFound = { boards -> showBoardSelectionDialog(boards) },
            onBoardSelected = { device -> handleBoardSelected(device) },
            onLogMessage = { message -> appendDebug(message) }
        )
        BoardScanManager.TRUSTED_ADDRESSES = MainActivity.TRUSTED_ESP32_ADDRESSES

        // Initialize BoardConnectionController (reuse Classic flow)
        boardConnectionController = BoardConnectionController(
            context = this,
            scope = lifecycleScope,
            serviceUuid = MainActivity.ESP32_SERVICE_UUID,
            txUuid = MainActivity.ESP32_CHAR_TX_UUID,
            rxUuid = MainActivity.ESP32_CHAR_RX_UUID,
            cccdUuid = MainActivity.CCCDUUID,
            onLog = { message -> appendDebug(message) },
            onConnected = { device ->
                esp32Connected = true
                runOnUiThread {
                    Toast.makeText(this, "Board connected", Toast.LENGTH_SHORT).show()
                    statusLedBar.setBoardState(StatusLedBar.LedState.ONLINE)
                }
            },
            onDisconnected = { _ ->
                esp32Connected = false
                DebugFileLogger.w("ESP32", "Board disconnected callback")
                runOnUiThread {
                    Toast.makeText(this, "Board disconnected", Toast.LENGTH_SHORT).show()
                    statusLedBar.setBoardState(StatusLedBar.LedState.OFFLINE)
                }
            },
            onMessage = { message -> 
                DebugFileLogger.d("ESP32", "onMessage callback: $message")
                handleESP32Response(message) 
            },
            onServicesReady = {
                DebugFileLogger.i("ESP32", "=== onServicesReady callback ===")
                DebugFileLogger.d("ESP32", "  gameStarted=$gameStarted, selectedProfileIds=$selectedProfileIds")
                runOnUiThread {
                    Toast.makeText(this, "ESP32 Connected!", Toast.LENGTH_SHORT).show()
                    statusLedBar.setBoardState(StatusLedBar.LedState.ONLINE)
                }
                // Match Classic: only send pair on services ready, config follows pair_success
                sendPairCommandToESP32()
            }
        )
        
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
        
        DebugFileLogger.i("IntroAi", "Received ${selectedProfiles.size} profiles")
        DebugFileLogger.d("IntroAi", "selectedProfiles=$selectedProfiles")
        DebugFileLogger.d("IntroAi", "colors from intent=$colors")
        DebugFileLogger.d("IntroAi", "assignedColors after init=$assignedColors")

        setInitialStates()
        boardScanManager?.stopScan()
        bindPlayers(selectedProfiles, colors)
        playIntroLines()
        playEntranceAnimations()
    }

    override fun onDestroy() {
        voiceService?.shutdown()
        boardScanManager?.stopScan()
        boardConnectionController.disconnect()
        soundManager.release()
        apiManager.cleanup()  // Cleanup API manager
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
    
    /**
     * Handle activity results (QR code scan for web server connection)
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val handled = LiveServerUiHelper.handleQrActivityResult(
            activity = this,
            requestCode = requestCode,
            resultCode = resultCode,
            data = data,
            onSessionId = { sessionId -> connectToLiveServer(sessionId) },
            onBoardQr = { qrData ->
                appendDebug("📷 QR Scanned: ${qrData.boardId}")
                boardPreferencesManager.saveBoard(
                    boardId = qrData.boardId,
                    macAddress = qrData.macAddress,
                    nickname = qrData.nickname,
                    password = qrData.password
                )
                Toast.makeText(this, "Board saved: ${qrData.nickname ?: qrData.boardId}", Toast.LENGTH_SHORT).show()
                connectESP32Board()
            },
            onInvalid = {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
                appendDebug("❌ Invalid QR code")
                // Notify wizard of failure
                pendingServerCallback?.invoke(false)
                pendingServerCallback = null
            },
            onCancel = { 
                appendDebug("📷 QR scan cancelled")
                // Notify wizard of cancellation (treat as skip)
                pendingServerCallback?.invoke(false)
                pendingServerCallback = null
            }
        )

        if (!handled) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    
    private fun handleClassicModeResult(data: Intent) {
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

    private fun handleProfileSelectionResult(data: Intent) {
        val profileIds = data.getStringArrayListExtra("selected_profiles") ?: return
        val colors = data.getStringArrayListExtra("assigned_colors") ?: emptyList()

        selectedProfileIds.clear()
        selectedProfileIds.addAll(profileIds)
        assignedColors.clear()
        assignedColors.addAll(colors)

        bindPlayers(profileIds, colors)
    }

    private fun speakLine(line: String) {
        DebugFileLogger.i("VOICE", "🔊 Speaking: $line")
        dialogue.text = line
        
        // Voice service now handles speech callbacks for mouth animation sync
        // onSpeechStart -> startTalking(), onSpeechDone -> stopTalking()
        voiceService.speak(line)
    }

    private fun appendDebug(message: String) {
        // Log to both Logcat and file
        android.util.Log.d("IntroAi", message)
        DebugFileLogger.d("IntroAi", message)
    }

    private fun setInitialStates() {
        // New CloudieExpression starts at idle
        cloudieExpression.setExpression(CloudieExpressionView.Expression.IDLE, animate = false)
        
        // Hide dice animation initially
        diceRollAnimation.hide()
        
        // Legacy views (only animate if they exist)
        cloudieAnimation?.apply {
            translationY = -80f
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0f
        }

        dropsRow?.children?.forEach { child ->
            child.translationY = -40f
            child.alpha = 0f
        }
    }

    private fun playEntranceAnimations() {
        // New: Cloudie expression starts at IDLE (animated by view itself)
        cloudieExpression.setExpression(CloudieExpressionView.Expression.EXCITED)
        
        // Legacy Lottie animation (if present)
        cloudieAnimation?.let { anim ->
            emoteManager?.playCloudieEmote(anim, EmoteManager.CLOUDIE_IDLE)
            anim.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(650)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }

        // Legacy staggered drop bounces (if present)
        dropsRow?.let { row ->
            row.post {
                row.children.forEachIndexed { index, child ->
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
    }

    private fun bindPlayers(ids: List<String>, colors: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val profiles = ids.mapNotNull { profileManager.getProfile(it) }
            withContext(Dispatchers.Main) {
                // Update NEW PlayerScoreboardView
                val playerData = profiles.mapIndexed { index, profile ->
                    val colorHex = colors.getOrNull(index).orEmpty().ifBlank { profile.avatarColor }
                    Triple(
                        profile.nickname.ifBlank { profile.name },
                        10,  // Players start with 10 drops per game rules
                        colorHex
                    )
                }
                playerScoreboard.setPlayers(playerData)
                
                // Set first player as active
                if (profiles.isNotEmpty()) {
                    playerScoreboard.setActivePlayer(0)
                }
                
                // Legacy dropsRow (if present)
                dropsRow?.removeAllViews()
                profiles.forEachIndexed { index, profile ->
                    val colorHex = colors.getOrNull(index).orEmpty().ifBlank { profile.avatarColor }
                    dropsRow?.addView(createDrop(profile, colorHex))
                }
                // Reset initial states now that drops are inflated
                dropsRow?.children?.forEach { child ->
                    child.translationY = -40f
                    child.alpha = 0f
                }
                
                // Show setup wizard before initializing game
                if (profiles.isNotEmpty()) {
                    showSetupWizard(profiles)
                }
            }
        }
    }
    
    /**
     * Show the setup wizard to guide user through Board/Dice/Server connection
     */
    private fun showSetupWizard(profiles: List<PlayerProfile>) {
        val playerNames = profiles.map { it.nickname.ifBlank { it.name } }
        
        SetupWizardDialog(
            context = this,
            playerNames = playerNames,
            onSpeak = { text -> speakLine(text) },
            onConnectBoard = { onResult ->
                // Connect board with callback
                connectESP32BoardWithCallback { success ->
                    runOnUiThread { onResult(success) }
                }
            },
            onConnectDice = { diceCount, onResult ->
                // Set two-dice mode based on user selection
                playWithTwoDice = (diceCount == 2)
                android.util.Log.d("IntroAiActivity", "🎲 User selected $diceCount dice mode, playWithTwoDice=$playWithTwoDice")
                
                // Connect dice with callback
                connectDiceWithCallback { success ->
                    runOnUiThread { onResult(success) }
                }
            },
            onConnectServer = { onResult ->
                // Open QR scanner - result comes back via onActivityResult
                pendingServerCallback = onResult
                showServerConnectionInfo()
            },
            onComplete = { boardConnected, diceConnected, serverConnected, diceCount ->
                // Set virtual dice mode if no Bluetooth dice connected
                if (!diceConnected) {
                    useBluetoothDice = false
                    virtualDiceView.visibility = View.VISIBLE
                    iconDiceMode.setImageResource(R.drawable.ic_dice_virtual)
                } else {
                    useBluetoothDice = true
                    playWithTwoDice = (diceCount == 2)
                    android.util.Log.d("IntroAiActivity", "🎲 Game starting with Bluetooth dice, diceCount=$diceCount, playWithTwoDice=$playWithTwoDice")
                }
                
                // Initialize and start the game
                initializeGame(profiles)
            },
            onQuit = {
                // User chose to quit
                finish()
            }
        ).show()
    }
    
    // Callback holder for server connection result
    private var pendingServerCallback: ((Boolean) -> Unit)? = null

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
            
            classicModeLauncher.launch(intent)
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
            profileSelectionLauncher.launch(intent)
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

        // Debug button - tap to show debug log, long-press to share
        iconDebug.setOnClickListener {
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            showDebugLogDialog()
        }
        iconDebug.setOnLongClickListener {
            soundManager.playSound(SoundEffect.BUTTON_CLICK)
            shareDebugLog()
            true
        }
    }
    
    private fun showDebugLogDialog() {
        val logContent = DebugFileLogger.getLogContent()
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Debug Log")
            .setMessage(if (logContent.isNotEmpty()) logContent else "(no logs)")
            .setPositiveButton("Share") { _, _ -> shareDebugLog() }
            .setNeutralButton("Clear") { _, _ -> 
                DebugFileLogger.init(this) // Clears log
                Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
        
        // Make text scrollable
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
            textSize = 10f
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
    }
    
    private fun shareDebugLog() {
        val logFile = DebugFileLogger.getLogFile()
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "No log file available", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                logFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LastDrop Debug Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Debug Log"))
        } catch (e: Exception) {
            DebugFileLogger.e("IntroAi", "Failed to share log", e)
            // Fallback: copy to clipboard
            val logContent = DebugFileLogger.getLogContent()
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("debug_log", logContent))
            Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
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
        // Ensure BLE permissions before attempting connection
        if (!ensureBlePermissions()) {
            Toast.makeText(this, "Bluetooth permissions required to connect to dice", Toast.LENGTH_LONG).show()
            return
        }
        
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
                        statusLedBar.setDiceState(StatusLedBar.LedState.ONLINE)
                        statusLedBar.setTwoDiceMode(playWithTwoDice)
                        
                        // Switch to Bluetooth dice mode
                        if (!useBluetoothDice) {
                            toggleDiceMode()
                        }
                    }
                },
                onDiceDisconnected = {
                    runOnUiThread {
                        diceConnected = false
                        statusLedBar.setDiceState(StatusLedBar.LedState.OFFLINE)
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
        statusLedBar.setDiceState(StatusLedBar.LedState.OFFLINE)
        
        Toast.makeText(this, "Dice disconnected", Toast.LENGTH_SHORT).show()
        
        // Switch to virtual dice mode
        if (useBluetoothDice) {
            toggleDiceMode()
        }
    }

    private fun ensureBlePermissions(): Boolean {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.BLUETOOTH_SCAN

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(this, "Bluetooth permissions granted. Please try connecting again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required to connect to dice and board",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectESP32Board() {
        DebugFileLogger.i("ESP32", "=== connectESP32Board called ===")
        // Ensure BLE permissions before attempting connection
        if (!ensureBlePermissions()) {
            DebugFileLogger.w("ESP32", "BLE permissions denied")
            Toast.makeText(this, "Bluetooth permissions required to connect to board", Toast.LENGTH_LONG).show()
            return
        }
        
        // Already connected → offer disconnect
        if (esp32Connected) {
            DebugFileLogger.d("ESP32", "Already connected, disconnecting...")
            boardConnectionController.disconnect()
            esp32Connected = false
            Toast.makeText(this, "Board disconnected", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Scanning for LASTDROP-* boards...", Toast.LENGTH_SHORT).show()
        DebugFileLogger.d("ESP32", "Starting board scan...")
        
        // Check if scanner can start (debounce check)
        if (boardScanManager?.isScanning() == true) {
            Toast.makeText(this, "Scan already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        val liveScanDialog = BoardSelectionDialog.showLiveScanDialog(
            context = this,
            preferencesManager = boardPreferencesManager,
            onBoardSelected = { device ->
                appendDebug("Selected board: ${device.name}")
                boardScanManager?.stopScan()
                showPinEntryDialog(device)  // Show PIN dialog like Classic page
            },
            onCancel = {
                appendDebug("Board scan cancelled")
                boardScanManager?.stopScan()
                boardScanManager?.resetDebounce()  // Allow immediate rescan after cancel
            }
        )

        boardScanManager?.setDiscoveryCallback(
            onDiscovered = { device -> runOnUiThread { liveScanDialog.addBoard(device) } },
            onComplete = { runOnUiThread {
                if (boardScanManager?.getDiscoveredBoards()?.isEmpty() == true) {
                    liveScanDialog.showNoBoards()
                }
            } }
        )

        boardScanManager?.startScan()
    }

    private fun showBoardSelectionDialog(boards: List<BluetoothDevice>) {
        runOnUiThread {
            BoardSelectionDialog.show(
                context = this,
                boards = boards,
                preferencesManager = boardPreferencesManager,
                onBoardSelected = { device ->
                    appendDebug("Selected board: ${device.name}")
                    boardScanManager?.stopScan()
                    showPinEntryDialog(device)  // Show PIN dialog like Classic page
                },
                onRescan = {
                    appendDebug("Rescanning boards...")
                    boardScanManager?.rescan()
                },
                onManageBoards = {
                    // Optional: could show saved boards dialog; not needed in AI flow
                }
            )
        }
    }
    
    /**
     * Connect to ESP32 board with callback for setup wizard
     */
    private var pendingBoardCallback: ((Boolean) -> Unit)? = null
    
    private fun connectESP32BoardWithCallback(onResult: (Boolean) -> Unit) {
        pendingBoardCallback = onResult
        
        if (!ensureBlePermissions()) {
            onResult(false)
            pendingBoardCallback = null
            return
        }
        
        if (esp32Connected) {
            onResult(true)  // Already connected
            pendingBoardCallback = null
            return
        }
        
        // Start scanning with timeout
        boardScanManager?.setDiscoveryCallback(
            onDiscovered = { device -> 
                runOnUiThread { 
                    // Auto-connect to first found board for simplicity
                    boardScanManager?.stopScan()
                    connectToDeviceWithCallback(device)
                } 
            },
            onComplete = { 
                runOnUiThread {
                    if (!esp32Connected && pendingBoardCallback != null) {
                        pendingBoardCallback?.invoke(false)
                        pendingBoardCallback = null
                    }
                }
            }
        )
        
        boardScanManager?.startScan()
    }
    
    private fun connectToDeviceWithCallback(device: BluetoothDevice) {
        // Track connection state changes
        var connectionSucceeded = false
        var connectionFailed = false
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("IntroAiActivity", "🔌 Attempting to connect to ${device.name}")
                boardConnectionController.connect(device)
                
                // Wait up to 8 seconds for connection, checking state every 500ms
                var waited = 0
                while (waited < 8000 && !connectionSucceeded && !connectionFailed) {
                    delay(500)
                    waited += 500
                    
                    // Check if connected
                    if (esp32Connected) {
                        connectionSucceeded = true
                        android.util.Log.d("IntroAiActivity", "✅ Board connection confirmed after ${waited}ms")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (connectionSucceeded) {
                        android.util.Log.d("IntroAiActivity", "✅ Board connected, invoking callback with true")
                        pendingBoardCallback?.invoke(true)
                    } else {
                        android.util.Log.d("IntroAiActivity", "❌ Board connection timed out after ${waited}ms")
                        pendingBoardCallback?.invoke(false)
                    }
                    pendingBoardCallback = null
                }
            } catch (e: Exception) {
                android.util.Log.e("IntroAiActivity", "❌ Board connection error: ${e.message}")
                withContext(Dispatchers.Main) {
                    pendingBoardCallback?.invoke(false)
                    pendingBoardCallback = null
                }
            }
        }
    }
    
    /**
     * Connect to dice with callback for setup wizard
     */
    private var pendingDiceCallback: ((Boolean) -> Unit)? = null
    
    private fun connectDiceWithCallback(onResult: (Boolean) -> Unit) {
        pendingDiceCallback = onResult
        
        if (!ensureBlePermissions()) {
            onResult(false)
            pendingDiceCallback = null
            return
        }
        
        if (diceConnected) {
            onResult(true)  // Already connected
            pendingDiceCallback = null
            return
        }
        
        // Always create fresh controller for wizard to ensure our callback is used
        diceConnectionController?.stopScan()
        diceConnectionController = DiceConnectionController(
            context = this,
            bluetoothAdapter = bluetoothAdapter,
            playWithTwoDice = { playWithTwoDice },
            onStatus = { status ->
                runOnUiThread {
                    appendDebug("Dice: $status")
                }
            },
            onLog = { message -> appendDebug("GoDice: $message") },
            onDiceConnected = { diceId, _ ->
                // diceId is the ID of the connected dice (0, 1, etc), not a count!
                // Any dice connection means success
                diceConnected = true
                runOnUiThread {
                    appendDebug("Dice connected! diceId=$diceId, invoking callback")
                    statusLedBar.setDiceState(StatusLedBar.LedState.ONLINE)
                    statusLedBar.setTwoDiceMode(playWithTwoDice)
                    // IMPORTANT: Call the pending callback to advance wizard
                    val callback = pendingDiceCallback
                    pendingDiceCallback = null
                    callback?.invoke(true)
                }
            },
            onDiceDisconnected = {
                diceConnected = false
                runOnUiThread {
                    statusLedBar.setDiceState(StatusLedBar.LedState.OFFLINE)
                }
            }
        )
        
        // Start scanning for dice with timeout
        diceConnectionController?.startScan()
        
        // Timeout after 10 seconds
        lifecycleScope.launch {
            delay(10000)
            if (!diceConnected && pendingDiceCallback != null) {
                diceConnectionController?.stopScan()
                val callback = pendingDiceCallback
                pendingDiceCallback = null
                callback?.invoke(false)
            }
        }
    }

    private fun handleBoardSelected(device: BluetoothDevice) {
        showPinEntryDialog(device)
    }
    
    /**
     * Show PIN entry dialog for board authentication (matches Classic page flow)
     */
    private fun showPinEntryDialog(device: BluetoothDevice) {
        val boardId = device.name ?: "Unknown"
        DebugFileLogger.d("ESP32", "showPinEntryDialog for $boardId")
        
        if (esp32Connected) {
            DebugFileLogger.w("ESP32", "Already connected, ignoring PIN dialog request")
            return
        }
        
        pendingPinDevice = device
        
        runOnUiThread {
            PinEntryDialog.show(
                context = this,
                device = device,
                boardId = boardId,
                preferencesManager = boardPreferencesManager,
                onPinEntered = { pin, rememberPin ->
                    DebugFileLogger.d("ESP32", "PIN entered: $pin, remember=$rememberPin")
                    
                    // Save PIN if requested
                    if (rememberPin) {
                        boardPreferencesManager.saveBoardPin(boardId, pin)
                        DebugFileLogger.d("ESP32", "PIN saved for $boardId")
                    }
                    
                    // Store PIN and connect
                    pendingPin = pin
                    connectToDevice(device)
                },
                onCancel = {
                    DebugFileLogger.d("ESP32", "PIN entry cancelled")
                    pendingPinDevice = null
                    pendingPin = ""
                }
            )
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        DebugFileLogger.i("ESP32", "=== connectToDevice: ${device.name} ===")
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Connecting to Board")
            .setMessage("Connecting to ${device.name}...")
            .setCancelable(false)
            .create()
        dialog.show()
        
        lifecycleScope.launch {
            delay(500) // Brief delay for visual feedback
            
            // Stop scanning
            boardScanManager?.stopScan()
            
            // Attempt connection using BoardConnectionController (same as Classic)
            try {
                DebugFileLogger.d("ESP32", "Calling boardConnectionController.connect()")
                boardConnectionController.connect(device)
                delay(2000) // Give time for connection
                DebugFileLogger.d("ESP32", "After connect delay, esp32Connected=$esp32Connected")
                
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (!esp32Connected) {
                        DebugFileLogger.w("ESP32", "Connection failed - esp32Connected still false")
                        androidx.appcompat.app.AlertDialog.Builder(this@IntroAiActivity)
                            .setTitle("Connection Failed")
                            .setMessage("Could not find ESP32 board.\n\n• Check board is powered on\n• Board should advertise as LASTDROP-*\n• Try again or use MainActivity for advanced scanning")
                            .setPositiveButton("Retry") { _, _ -> connectESP32Board() }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                DebugFileLogger.e("ESP32", "Connection exception", e)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@IntroAiActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendPairCommandToESP32() {
        DebugFileLogger.d("ESP32", "sendPairCommandToESP32 called, esp32Connected=$esp32Connected, pendingPin=$pendingPin")
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendPairCommandToESP32: NOT connected, skipping")
            return
        }
        
        // Use the PIN entered by user, fallback to saved PIN or default
        val pin = if (pendingPin.isNotEmpty()) {
            pendingPin
        } else {
            val boardId = boardConnectionController.connectedDevice?.name ?: ""
            boardPreferencesManager.getSavedPin(boardId) ?: "654321"
        }
        DebugFileLogger.d("ESP32", "Using PIN: $pin")
        
        val pairPayload = JSONObject().apply {
            put("command", "pair")
            put("password", pin)
        }
        val pairStr = pairPayload.toString()
        DebugFileLogger.d("ESP32", "sendPairCommandToESP32: Sending → $pairStr")
        sendToESP32(pairStr)
    }

    private fun sendConfigToESP32() {
        DebugFileLogger.d("ESP32", "sendConfigToESP32 called, esp32Connected=$esp32Connected")
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendConfigToESP32: NOT connected, skipping")
            return
        }

        val playerCount = selectedProfileIds.size.coerceAtMost(4)
        DebugFileLogger.d("ESP32", "sendConfigToESP32: selectedProfileIds=$selectedProfileIds (size=${selectedProfileIds.size})")
        DebugFileLogger.d("ESP32", "sendConfigToESP32: assignedColors=$assignedColors")
        DebugFileLogger.d("ESP32", "sendConfigToESP32: playerCount=$playerCount")
        
        val colors = assignedColors.take(playerCount).map { color ->
            // Colors from ProfileManager are already hex codes (FF0000, 00FF00, etc.)
            // Just validate they're proper 6-char hex, or map color names as fallback
            val hexColor = when (color.uppercase()) {
                "RED" -> "FF0000"
                "GREEN" -> "00FF00"
                "BLUE" -> "0000FF"
                "YELLOW" -> "FFFF00"
                else -> {
                    // Already a hex code - validate it
                    val cleaned = color.removePrefix("#").uppercase()
                    if (cleaned.length == 6 && cleaned.all { it.isDigit() || it in 'A'..'F' }) {
                        cleaned
                    } else {
                        DebugFileLogger.w("ESP32", "Invalid color '$color', defaulting to FFFFFF")
                        "FFFFFF"
                    }
                }
            }
            DebugFileLogger.d("ESP32", "  Mapped color '$color' -> $hexColor")
            hexColor
        }

        val config = JSONObject().apply {
            put("command", "config")
            put("playerCount", playerCount)
            put("colors", JSONArray(colors))
        }

        val configStr = config.toString()
        DebugFileLogger.d("ESP32", "sendConfigToESP32: Sending → $configStr")
        sendToESP32(configStr)
    }

    private fun sendResetToESP32() {
        DebugFileLogger.d("ESP32", "sendResetToESP32 called, esp32Connected=$esp32Connected")
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendResetToESP32: NOT connected, skipping")
            return
        }
        val resetCmd = JSONObject().apply {
            put("command", "reset")
        }
        val resetStr = resetCmd.toString()
        DebugFileLogger.d("ESP32", "sendResetToESP32: Sending → $resetStr")
        sendToESP32(resetStr)
    }
    
    /**
     * Send roll command to ESP32 to move LED to the new tile
     */
    private fun sendRollToESP32(playerId: Int, diceValue: Int, currentTile: Int, expectedTile: Int, scoreChange: Int, newScore: Int) {
        DebugFileLogger.d("ESP32", "sendRollToESP32 called: player=$playerId, dice=$diceValue, from=$currentTile, to=$expectedTile, scoreChange=$scoreChange, newScore=$newScore")
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendRollToESP32: NOT connected, skipping")
            return
        }
        
        val playerName = if (playerId < currentGameProfiles.size) {
            currentGameProfiles[playerId].nickname
        } else {
            "Player ${playerId + 1}"
        }
        val color = if (playerId < assignedColors.size) assignedColors[playerId] else "FF0000"
        
        val rollCmd = JSONObject().apply {
            put("command", "roll")
            put("playerId", playerId)
            put("playerName", playerName)
            put("diceValue", diceValue)
            put("currentTile", currentTile)
            put("expectedTile", expectedTile)
            put("color", color)
            put("scoreChange", scoreChange)
            put("newScore", newScore)
        }
        
        val rollStr = rollCmd.toString()
        DebugFileLogger.d("ESP32", "sendRollToESP32: Sending → $rollStr")
        sendToESP32(rollStr)
    }
    
    /**
     * Send victory command to ESP32 for grand winner LED animation
     */
    private fun sendVictoryToESP32(winnerId: Int) {
        DebugFileLogger.i("ESP32", "sendVictoryToESP32: winnerId=$winnerId")
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendVictoryToESP32: NOT connected, skipping")
            return
        }
        
        val winnerColor = if (winnerId < assignedColors.size) assignedColors[winnerId] else "FF0000"
        val winnerName = if (winnerId < currentGameProfiles.size) currentGameProfiles[winnerId].nickname else "Player ${winnerId + 1}"
        
        val victoryCmd = JSONObject().apply {
            put("command", "victory")
            put("winnerId", winnerId)
            put("winnerColor", winnerColor)
            put("winnerName", winnerName)
        }
        
        val victoryStr = victoryCmd.toString()
        DebugFileLogger.d("ESP32", "sendVictoryToESP32: Sending → $victoryStr")
        sendToESP32(victoryStr)
    }

    private fun sendToESP32(jsonString: String) {
        if (!esp32Connected) {
            DebugFileLogger.w("ESP32", "sendToESP32: NOT connected, cannot send: $jsonString")
            return
        }
        DebugFileLogger.d("ESP32", "sendToESP32: → $jsonString")
        boardConnectionController.send(jsonString)
    }

    private fun handleESP32Response(jsonResponse: String) {
        // Parse ESP32 JSON responses (coin_placed, misplacement, etc.)
        DebugFileLogger.d("ESP32", "handleESP32Response: RAW ← $jsonResponse")
        appendDebug("ESP32: $jsonResponse")
        
        try {
            val json = org.json.JSONObject(jsonResponse)
            val event = json.optString("event", "")
            DebugFileLogger.d("ESP32", "handleESP32Response: event='$event'")
            
            when (event) {
                "pair_success" -> {
                    // Match Classic: send config after successful pairing
                    DebugFileLogger.i("ESP32", "★ pair_success received - sending config")
                    sendConfigToESP32()
                }
                "config_complete" -> {
                    // Match Classic: send reset after config to move LEDs to start tile
                    DebugFileLogger.i("ESP32", "★ config_complete received - gameStarted=$gameStarted")
                    if (gameStarted) {
                        DebugFileLogger.d("ESP32", "  → scheduling reset in 200ms")
                        // Small delay to let ESP32 process config fully
                        lifecycleScope.launch {
                            delay(200)
                            sendResetToESP32()
                        }
                    } else {
                        DebugFileLogger.w("ESP32", "  → game NOT started, skipping reset")
                    }
                }
                "reset_complete" -> {
                    DebugFileLogger.i("ESP32", "★ reset_complete received - LEDs should be at start")
                    runOnUiThread {
                        Toast.makeText(this, "Board ready - LEDs at start", Toast.LENGTH_SHORT).show()
                    }
                }
                "player_eliminated" -> {
                    // ESP32 detected elimination via score tracking
                    val playerId = json.optInt("playerId", -1)
                    DebugFileLogger.i("ESP32", "★ player_eliminated received for player $playerId")
                    if (playerId >= 0 && playerId < currentGameProfiles.size) {
                        runOnUiThread {
                            Toast.makeText(this, "${currentGameProfiles[playerId].nickname} eliminated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "winner_declared" -> {
                    // ESP32 detected winner (only 1 player left)
                    val winnerId = json.optInt("winnerId", -1)
                    DebugFileLogger.i("ESP32", "★ winner_declared received for player $winnerId")
                    if (winnerId >= 0 && winnerId < currentGameProfiles.size) {
                        runOnUiThread {
                            val winnerName = currentGameProfiles[winnerId].nickname
                            val winnerScore = playerScores[winnerId]
                            showVictoryDialog(winnerName, winnerScore)
                        }
                    }
                }
                "victory_complete" -> {
                    // ESP32 finished playing victory animation
                    val winnerId = json.optInt("winnerId", -1)
                    val winnerName = json.optString("winnerName", "Player")
                    DebugFileLogger.i("ESP32", "★ victory_complete received for $winnerName (id=$winnerId)")
                }
                else -> {
                    DebugFileLogger.d("ESP32", "handleESP32Response: unhandled event '$event'")
                }
            }
        } catch (e: Exception) {
            DebugFileLogger.e("ESP32", "Error parsing ESP32 response: ${e.message}", e)
        }
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
    
    /**
     * Connect to live server with scanned session ID
     */
    private fun connectToLiveServer(sessionId: String) {
        statusLedBar.setWebState(StatusLedBar.LedState.CONNECTING)
        
        mainScope.launch {
            try {
                // Update ApiManager with scanned session ID
                apiManager.setSessionId(sessionId)
                
                // Send immediate heartbeat to create session entry
                apiManager.sendImmediateHeartbeat()
                
                // Start periodic heartbeat with scanned session ID
                apiManager.startHeartbeat()
                
                // Push current game state to server with session ID
                pushResetStateToServer()
                
                delay(1000) // Small delay for user feedback
                
                withContext(Dispatchers.Main) {
                    statusLedBar.setWebState(StatusLedBar.LedState.ONLINE)
                    Toast.makeText(
                        this@IntroAiActivity,
                        "Connected to session: ${sessionId.take(8)}...",
                        Toast.LENGTH_LONG
                    ).show()
                    runCatching { voiceService?.speak("Connected to live server") }
                    
                    // Send another heartbeat after successful connection
                    apiManager.sendImmediateHeartbeat()
                    
                    // Notify setup wizard of success
                    pendingServerCallback?.invoke(true)
                    pendingServerCallback = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusLedBar.setWebState(StatusLedBar.LedState.OFFLINE)
                    Toast.makeText(
                        this@IntroAiActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    runCatching { voiceService?.speak("Live server disconnected") }
                    
                    // Notify setup wizard of failure
                    pendingServerCallback?.invoke(false)
                    pendingServerCallback = null
                }
            }
        }
    }
    
    /**
     * Push current game state to the server (for initial sync after connecting)
     */
    private fun pushResetStateToServer() {
        if (currentGameProfiles.isEmpty()) {
            appendDebug("Cannot push to server - no players configured")
            return
        }
        
        val playerNames = currentGameProfiles.map { it.nickname }
        val playerColors = currentGameProfiles.mapIndexed { index, profile ->
            assignedColors.getOrElse(index) { "FF0000" }
        }
        
        apiManager.pushResetState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerCount = currentGameProfiles.size
        )
        
        appendDebug("Pushed initial game state to server")
    }
    
    /**
     * Push live game state to server (after each turn)
     */
    private fun pushLiveStateToServer(
        diceValue: Int,
        turnResult: TurnResult
    ) {
        if (currentGameProfiles.isEmpty()) return
        
        val playerNames = currentGameProfiles.map { it.nickname }
        val playerColors = currentGameProfiles.mapIndexed { index, _ ->
            assignedColors.getOrElse(index) { "FF0000" }
        }
        
        // Build position and score maps
        val positionsMap = playerNames.mapIndexed { index, name ->
            name to playerPositions.getOrElse(index) { 1 }
        }.toMap()
        
        val scoresMap = playerNames.mapIndexed { index, name ->
            name to playerScores.getOrElse(index) { 10 }
        }.toMap()
        
        // Only show 2 dice when using Bluetooth dice AND 2-dice mode is enabled
        // Virtual dice = always single die
        // Bluetooth + 1-dice mode = single die
        // Bluetooth + 2-dice mode = 2 dice
        val showTwoDice = useBluetoothDice && playWithTwoDice
        
        apiManager.pushLiveState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerPositions = positionsMap,
            playerScores = scoresMap,
            playerCount = currentGameProfiles.size,
            currentPlayer = (currentPlayerIndex + 1) % currentGameProfiles.size, // Next player
            playWithTwoDice = showTwoDice,  // Only true when Bluetooth + 2-dice mode
            diceColorMap = diceColorMap,
            lastDice1 = diceValue,
            lastDice2 = if (showTwoDice) diceValue else null,  // null for virtual/single dice
            lastAvg = diceValue,
            lastTileName = turnResult.tile?.name,
            lastTileType = turnResult.tile?.type?.name,
            lastChanceCardNumber = turnResult.chanceCard?.number,
            lastChanceCardText = turnResult.chanceCard?.description,
            rolling = false
        )
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
        DebugFileLogger.i("GAME", "╔══════════════════════════════════════════════════════╗")
        DebugFileLogger.i("GAME", "║         🎮 INITIALIZING NEW GAME                     ║")
        DebugFileLogger.i("GAME", "╚══════════════════════════════════════════════════════╝")
        DebugFileLogger.i("GAME", "  Player count: ${profiles.size}")
        
        currentGameProfiles.clear()
        currentGameProfiles.addAll(profiles)
        
        // Reset game state
        currentPlayerIndex = 0
        gameStarted = true
        
        for (i in currentGameProfiles.indices) {
            playerPositions[i] = 1
            playerScores[i] = 10  // Players start with 10 drops per game rules
            playerAlive[i] = true
            val color = if (i < assignedColors.size) assignedColors[i] else "unknown"
            DebugFileLogger.i("GAME", "  Player $i: ${profiles[i].nickname} (color=$color, starting with 10 drops)")
        }

        // Align board LEDs to start tile, matching Classic reset behavior
        DebugFileLogger.i("GAME", "  Sending reset to ESP32...")
        sendResetToESP32()
        
        // Setup scorecard badges with player colors (starting with 10 drops)
        // NOTE: Legacy scorecards disabled - using PlayerScoreboardView instead
        // for (i in profiles.indices) {
        //     scorecardBadges[i]?.apply {
        //         visibility = View.VISIBLE
        //         setScore(10)  // Start with 10 drops
        //         val colorString = profiles[i].avatarColor.let { 
        //             if (it.startsWith("#")) it else "#$it" 
        //         }
        //         setBorderColor(Color.parseColor(colorString))
        //     }
        // }
        
        // Hide all legacy scorecard badges (using PlayerScoreboardView now)
        for (i in 0 until 4) {
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
        
        // Play excited animation for game start (NEW Cloudie expression)
        cloudieExpression.setExpression(CloudieExpressionView.Expression.EXCITED)
        cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_EXCITED) }
    }

    private fun handleNewRoll(diceValue: Int) {
        DebugFileLogger.i("GAME", "════════════════════════════════════════")
        DebugFileLogger.i("GAME", "🎲 NEW ROLL: diceValue=$diceValue")
        
        if (!gameStarted || currentGameProfiles.isEmpty()) {
            DebugFileLogger.w("GAME", "  ❌ Game not started or no profiles")
            soundManager.playSound(SoundEffect.ERROR)
            Toast.makeText(this, "Start game first!", Toast.LENGTH_SHORT).show()
            return
        }

        // Play dice roll sound
        soundManager.playSound(SoundEffect.DICE_ROLL)
        hapticManager.vibrateDiceRoll()
        
        // Show dice roll animation with player color
        val safeIndex = currentPlayerIndex.coerceIn(0, currentGameProfiles.size - 1)
        val currentProfile = currentGameProfiles[safeIndex]
        val currentPos = playerPositions[safeIndex]
        
        // Get player color for dice animation
        val playerColorHex = assignedColors.getOrNull(safeIndex) ?: currentProfile.avatarColor
        val playerColor = try {
            Color.parseColor(if (playerColorHex.startsWith("#")) playerColorHex else "#$playerColorHex")
        } catch (e: Exception) {
            Color.parseColor("#4FC3F7")
        }
        diceRollAnimation.setPlayerColor(playerColor)
        diceRollAnimation.showRoll(diceValue)
        
        // Cloudie shows thinking expression during roll
        cloudieExpression.setExpression(CloudieExpressionView.Expression.THINKING)
        
        DebugFileLogger.i("GAME", "  👤 Player: ${currentProfile.nickname} (index=$safeIndex)")
        DebugFileLogger.i("GAME", "  📍 Current position: tile $currentPos")
        DebugFileLogger.i("GAME", "  💧 Current score: ${playerScores[safeIndex]}")

        // Process turn through GameEngine
        val turnResult = gameEngine.processTurn(currentPos, diceValue)
        
        DebugFileLogger.i("GAME", "  📊 Turn Result:")
        DebugFileLogger.i("GAME", "     → New position: tile ${turnResult.newPosition}")
        DebugFileLogger.i("GAME", "     → Score change: ${if (turnResult.scoreChange >= 0) "+" else ""}${turnResult.scoreChange}")
        DebugFileLogger.i("GAME", "     → Tile: ${turnResult.tile?.name ?: "unknown"}")
        turnResult.chanceCard?.let { card ->
            DebugFileLogger.i("GAME", "     → Chance Card: ${card.description} (effect: ${card.effect})")
        }
        
        // Check for lap completion bonus (+5 drops when passing start tile)
        val lapBonus = if (turnResult.newPosition < currentPos && turnResult.newPosition <= 3) {
            // Player wrapped around past start (tile 1) - completed a lap!
            DebugFileLogger.i("GAME", "  🏁 LAP COMPLETED! +5 bonus drops")
            5
        } else 0
        
        // Calculate new score (tile effect + lap bonus)
        val newScore = playerScores[safeIndex] + turnResult.scoreChange + lapBonus
        DebugFileLogger.i("GAME", "  💧 New score: $newScore (was ${playerScores[safeIndex]}, tile change: ${turnResult.scoreChange}, lap bonus: $lapBonus)")
        
        // Send roll command to ESP32 to move LED (includes score for elimination detection)
        DebugFileLogger.i("GAME", "  📡 Sending roll to ESP32...")
        sendRollToESP32(
            playerId = safeIndex,
            diceValue = diceValue,
            currentTile = currentPos,
            expectedTile = turnResult.newPosition,
            scoreChange = turnResult.scoreChange,
            newScore = newScore
        )
        
        // Update player state
        playerPositions[safeIndex] = turnResult.newPosition
        playerScores[safeIndex] = newScore
        
        // Check for elimination (score <= 0)
        if (playerScores[safeIndex] <= 0) {
            playerAlive[safeIndex] = false
            
            DebugFileLogger.i("GAME", "  ☠️ ELIMINATION: ${currentProfile.nickname} eliminated!")
            
            soundManager.playSound(SoundEffect.PLAYER_ELIMINATED)
            hapticManager.vibrateElimination()
            
            val remainingPlayers = playerAlive.withIndex().filter { it.value }
            val remainingCount = remainingPlayers.size
            DebugFileLogger.i("GAME", "     → Remaining players: $remainingCount")
            
            // Funny elimination message
            val eliminationDialogue = dialogueGenerator.generateElimination(
                currentProfile.nickname,
                remainingCount
            )
            speakLine(eliminationDialogue)
            cloudieExpression.setExpression(CloudieExpressionView.Expression.SAD)
            cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_SAD) }
            
            // Update scoreboard to show elimination
            playerScoreboard.eliminatePlayer(safeIndex)
            
            // Check if there's a winner (only 1 player left alive)
            if (remainingCount == 1) {
                // We have a winner!
                val winnerIndex = remainingPlayers.first().index
                val winnerProfile = currentGameProfiles[winnerIndex]
                
                DebugFileLogger.i("GAME", "  🏆 WINNER: ${winnerProfile.nickname} (index=$winnerIndex)")
                DebugFileLogger.i("GAME", "     → Final score: ${playerScores[winnerIndex]}")
                
                // Small delay for elimination animation to complete, then announce winner
                lifecycleScope.launch {
                    delay(2000) // Let ESP32 elimination animation finish
                    
                    // Send victory command to ESP32 for grand winner animation
                    sendVictoryToESP32(winnerIndex)
                    
                    // Play victory celebration
                    soundManager.playSound(SoundEffect.GAME_WIN)
                    hapticManager.vibrateGameWin()
                    particleEffectView.burstConfetti()
                    cloudieExpression.setExpression(CloudieExpressionView.Expression.EXCITED)
                    cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_EXCITED) }
                    
                    // Grand victory announcement
                    val victoryDialogue = "Incredible! ${winnerProfile.nickname} is the last one standing! " +
                        "With ${playerScores[winnerIndex]} drops remaining, they are the champion! " +
                        "All hail the mighty water warrior! Bow before the hydration hero!"
                    speakLine(victoryDialogue)
                    
                    delay(5000) // Let victory animation play
                    
                    // Show winner dialog
                    runOnUiThread {
                        showVictoryDialog(winnerProfile.nickname, playerScores[winnerIndex])
                    }
                }
                return // Don't continue with normal turn flow
            }
            
        } else {
            // Play tile landing sound and haptic
            soundManager.playSound(SoundEffect.TILE_LAND)
            hapticManager.vibrateTileLand()
            
            // Update Cloudie expression based on score change
            when {
                turnResult.scoreChange > 0 -> {
                    cloudieExpression.setExpression(CloudieExpressionView.Expression.HAPPY)
                    soundManager.playSound(SoundEffect.SCORE_GAIN)
                    hapticManager.vibrateScoreGain()
                    // Spawn sparkles at player scoreboard
                    particleEffectView.burstConfetti()
                }
                turnResult.scoreChange < 0 -> {
                    cloudieExpression.setExpression(CloudieExpressionView.Expression.WORRIED)
                    soundManager.playSound(SoundEffect.SCORE_LOSS)
                    hapticManager.vibrateScoreLoss()
                }
                else -> {
                    cloudieExpression.setExpression(CloudieExpressionView.Expression.IDLE)
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
                cloudieExpression.setExpression(CloudieExpressionView.Expression.THINKING)
                val cardDialogue = dialogueGenerator.generateChanceCard(
                    currentProfile.nickname,
                    turnResult.chanceCard
                )
                "$rollDialogue $tileDialogue $cardDialogue"
            } else {
                "$rollDialogue $tileDialogue"
            }
            
            speakLine(finalDialogue)
            
            // Legacy emoteManager animations (if present)
            cloudieAnimation?.let { anim ->
                when {
                    turnResult.scoreChange > 5 -> emoteManager?.playCloudieEmote(anim, EmoteManager.CLOUDIE_CELEBRATE)
                    turnResult.scoreChange < -5 -> emoteManager?.playCloudieEmote(anim, EmoteManager.CLOUDIE_WARNING)
                    turnResult.chanceCard != null -> emoteManager?.playCloudieEmote(anim, EmoteManager.CLOUDIE_THINKING)
                    else -> emoteManager?.playCloudieEmote(anim, EmoteManager.CLOUDIE_SPEAKING)
                }
            }
        }
        
        // Update NEW PlayerScoreboardView with animated score change
        playerScoreboard.updatePlayerScore(safeIndex, newScore, animate = true)
        
        // Update legacy UI
        updateGameUI()
        updateLastEventText(currentProfile.nickname, diceValue, turnResult)
        
        // Push updated game state to live server (for web display)
        pushLiveStateToServer(diceValue, turnResult)
        
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
        DebugFileLogger.i("GAME", "  🔄 Advancing to next player (current=$startIndex)")
        
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % currentGameProfiles.size
            // Loop until we find alive player or return to start
            if (playerAlive[currentPlayerIndex]) {
                DebugFileLogger.i("GAME", "     → Found alive player at index $currentPlayerIndex")
                // Update scoreboard active player
                playerScoreboard.setActivePlayer(currentPlayerIndex)
                break
            }
            DebugFileLogger.d("GAME", "     → Skipping eliminated player at index $currentPlayerIndex")
            if (currentPlayerIndex == startIndex) {
                // All players eliminated, game over
                DebugFileLogger.w("GAME", "     → All players eliminated! Ending game.")
                endGame()
                return
            }
        } while (!playerAlive[currentPlayerIndex])
        
        val nextProfile = currentGameProfiles[currentPlayerIndex]
        val isLeading = playerScores[currentPlayerIndex] == playerScores.max()
        
        DebugFileLogger.i("GAME", "  👤 Next player: ${nextProfile.nickname} (score=${playerScores[currentPlayerIndex]}, leading=$isLeading)")
        DebugFileLogger.i("GAME", "════════════════════════════════════════")
        
        val transitionDialogue = dialogueGenerator.generateTurnTransition(
            nextProfile.nickname,
            playerScores[currentPlayerIndex],
            isLeading
        )
        speakLine(transitionDialogue)
        
        // Return to idle animation (new + legacy)
        cloudieExpression.setExpression(CloudieExpressionView.Expression.IDLE)
        cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_IDLE) }
    }

    private fun updateGameUI() {
        // Update NEW PlayerScoreboardView
        playerScoreboard.setActivePlayer(currentPlayerIndex)
        for (i in currentGameProfiles.indices) {
            playerScoreboard.updatePlayerScore(i, playerScores[i], animate = false)
        }
        
        // Update legacy scorecard badges with animated score changes
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
        // Update NEW PlayerScoreboardView
        for (i in 0 until currentGameProfiles.size) {
            playerScoreboard.updatePlayerScore(i, playerScores[i], animate = true)
            if (!playerAlive[i]) {
                playerScoreboard.eliminatePlayer(i)
            }
        }
        playerScoreboard.setActivePlayer(currentPlayerIndex)
        
        // Update legacy scorecard badges with current scores
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
        
        // Find winner (highest score among alive players, or last standing)
        val alivePlayers = playerAlive.withIndex().filter { it.value }
        
        if (alivePlayers.size == 1) {
            // Single winner - already handled by elimination detection
            val winnerIndex = alivePlayers.first().index
            showVictoryDialog(currentGameProfiles[winnerIndex].nickname, playerScores[winnerIndex])
            return
        }
        
        // Multiple players alive - find highest score
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
            
            // Send victory to ESP32
            sendVictoryToESP32(winnerIndex)
            
            // Play celebration
            soundManager.playSound(SoundEffect.GAME_WIN)
            hapticManager.vibrateGameWin()
            particleEffectView.burstConfetti()
            cloudieExpression.setExpression(CloudieExpressionView.Expression.EXCITED)
            cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_EXCITED) }
            
            showVictoryDialog(winner.nickname, maxScore)
            speakLine("Congratulations ${winner.nickname}! You are the ultimate water champion with ${maxScore} drops!")
        } else {
            // All players eliminated somehow - shouldn't happen normally
            AlertDialog.Builder(this)
                .setTitle("🎮 Game Over")
                .setMessage("No survivors! Everyone ran out of water!")
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(this, ProfileSelectionActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                .setCancelable(false)
                .show()
            speakLine("Incredible! Everyone ran out of water! It's a total drought!")
        }
    }
    
    private fun showVictoryDialog(winnerName: String, score: Int) {
        gameStarted = false
        
        AlertDialog.Builder(this)
            .setTitle("🏆 VICTORY!")
            .setMessage("$winnerName wins with $score drops remaining!\n\nCongratulations, water champion!")
            .setPositiveButton("Play Again") { _, _ ->
                performReset()
            }
            .setNegativeButton("Exit") { _, _ ->
                val intent = Intent(this, ProfileSelectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
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
            playerScores[i] = 10  // Reset to starting 10 drops per game rules
            playerAlive[i] = true
        }
        
        // Clear undo state
        lastRoll = null
        previousRoll = null
        
        // Update UI
        updateGameUI()

        // Reset board LEDs to the starting tile for all players
        sendResetToESP32()
        
        // Play celebration
        soundManager.playSound(SoundEffect.GAME_WIN)
        hapticManager.vibrateGameWin()
        particleEffectView.burstConfetti()
        
        // Announce reset
        val firstPlayer = currentGameProfiles[0].nickname
        speakLine("Game reset! ${currentGameProfiles.size} players ready. $firstPlayer starts!")
        cloudieExpression.setExpression(CloudieExpressionView.Expression.EXCITED)
        cloudieAnimation?.let { emoteManager?.playCloudieEmote(it, EmoteManager.CLOUDIE_EXCITED) }
        
        // Reset scoreboard
        playerScoreboard.reset()
        // Re-initialize with players (starting with 10 drops)
        val playerData = currentGameProfiles.mapIndexed { index, profile ->
            val colorHex = assignedColors.getOrNull(index).orEmpty().ifBlank { profile.avatarColor }
            Triple(profile.nickname.ifBlank { profile.name }, 10, colorHex)  // Start with 10 drops
        }
        playerScoreboard.setPlayers(playerData)
        playerScoreboard.setActivePlayer(0)
        
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
        DebugFileLogger.i("DICE", "🎲 onDiceStable: diceId=$diceId, number=$number")
        
        if (!gameStarted || !useBluetoothDice) {
            DebugFileLogger.w("DICE", "  → Ignored: gameStarted=$gameStarted, useBluetoothDice=$useBluetoothDice")
            return
        }
        
        diceResults[diceId] = number
        DebugFileLogger.d("DICE", "  → Stored result. diceResults=$diceResults")
        
        // Check if we have all needed results
        val neededCount = if (playWithTwoDice) 2 else 1
        DebugFileLogger.d("DICE", "  → neededCount=$neededCount, have=${diceResults.size}")
        
        if (diceResults.size >= neededCount) {
            val rollValue = if (playWithTwoDice) {
                // Average of two dice
                val values = diceResults.values.toList()
                val avg = (values[0] + values[1]) / 2
                DebugFileLogger.i("DICE", "  → Two dice average: (${values[0]} + ${values[1]}) / 2 = $avg")
                avg
            } else {
                val value = diceResults.values.first()
                DebugFileLogger.i("DICE", "  → Single die value: $value")
                value
            }
            
            // Clear results for next roll
            diceResults.clear()
            
            // Process the roll
            DebugFileLogger.i("DICE", "  → Processing roll with value: $rollValue")
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
