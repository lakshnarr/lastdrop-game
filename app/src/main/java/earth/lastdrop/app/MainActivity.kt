package earth.lastdrop.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import earth.lastdrop.app.GameEngine
import earth.lastdrop.app.TileType
import earth.lastdrop.app.ai.cloudie.CloudieBrain
import earth.lastdrop.app.ai.cloudie.CloudieEventType
import earth.lastdrop.app.ai.cloudie.CloudieRequest
import earth.lastdrop.app.DiceConnectionController
import earth.lastdrop.app.PlayerConfigUiHelper
import earth.lastdrop.app.game.session.GameSessionManager
import earth.lastdrop.app.game.session.PlayerInfo
import earth.lastdrop.app.game.session.TileType as SessionTileType
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.TextToSpeechVoiceService
import earth.lastdrop.app.voice.VoiceService
import earth.lastdrop.app.ui.intro.IntroAiActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.sample.godicesdklib.GoDiceSDK
import java.net.HttpURLConnection
import earth.lastdrop.app.BoardConnectionController
import earth.lastdrop.app.LiveServerUiHelper
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.collections.HashMap
import kotlin.math.roundToInt
import kotlin.random.Random

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), GoDiceSDK.Listener {

    // ---------- API config ----------
    companion object {
        private const val API_BASE_URL = "https://lastdrop.earth/api"
        private const val API_KEY = BuildConfig.API_KEY
        private const val TAG = "LastDrop"
        private const val REQUEST_CODE_PROFILES = 1001
        private const val REQUEST_CAMERA_PERMISSION = 1002
        private const val REQUEST_CODE_INTRO_AI = 1003
        
        // Generate unique session ID for multi-device support
        private val SESSION_ID = java.util.UUID.randomUUID().toString()
        
        // ESP32 BLE Configuration
        const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"  // Legacy - deprecated
        const val ESP32_BOARD_PREFIX = "LASTDROP-"       // Multi-board support
        val ESP32_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCDUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private const val ESP32_PAIR_PIN = "654321"       // Default board PIN
        
        // MAC Address Whitelist - Add your ESP32's MAC address for security
        // Leave empty to accept any LASTDROP-ESP32 device
        // Find MAC in Arduino Serial Monitor when ESP32 starts
        val TRUSTED_ESP32_ADDRESSES = setOf<String>(
            // "24:0A:C4:XX:XX:XX"  // Example - Replace with your ESP32's MAC
            // Add more trusted devices as needed
        )
    }

    private fun startGameWithProfiles(profileIds: List<String>, assignedColors: List<String>) {
        mainScope.launch {
            // Load profile data
            val profiles = profileIds.mapNotNull { id -> profileManager.getProfile(id) }

            if (profiles.isNotEmpty()) {
                playerCount = profiles.size
                val displayNames = profiles.mapIndexed { index, profile ->
                    formatProfileDisplayName(profile.name, profile.nickname, "Player ${index + 1}")
                }

                displayNames.forEachIndexed { index, displayName ->
                    playerNames[index] = displayName
                    playerColors[index] = assignedColors.getOrNull(index) ?: ProfileManager.GAME_COLORS.getOrElse(index) { ProfileManager.GAME_COLORS.first() }
                }

                // Store profile IDs and full profiles for game end recording and AI detection
                currentGameProfileIds.clear()
                currentGameProfileIds.addAll(profileIds)
                currentGameProfiles.clear()
                currentGameProfiles.addAll(profiles)

                Log.d(TAG, "Game starting with profiles: $displayNames")

                // Initialize game
                resetLocalGame()
                updateTurnIndicatorDisplay()
                aiPresenter.onGameStart(displayNames)
                aiPresenter.onTurnStart(displayNames.getOrNull(currentPlayer) ?: "Player")
                syncSessionState()
                emitCloudieLine(CloudieEventType.GAME_START, null)
                emitCloudieLine(CloudieEventType.TURN_PROMPT, null)

                Toast.makeText(
                    this@MainActivity,
                    "Welcome ${displayNames.joinToString()}!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load player profiles. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------- UI ----------
    private lateinit var tvTitle: TextView
    private lateinit var tvDiceStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvLastEvent: TextView
    private lateinit var tvUndoStatus: TextView
    private lateinit var tvCoinTimeout: TextView
    private lateinit var tvCurrentTurn: TextView
    private lateinit var tvCloudieBanner: TextView
    private lateinit var switchCloudieVoice: Switch
    private var lastCloudieLine: String? = null
    private var lastCloudieTimestampMs: Long = 0L

    private data class ScoreboardRow(
        val container: TableRow,
        val rank: TextView,
        val color: TextView,
        val name: TextView,
        val tileNumber: TextView,
        val tileName: TextView,
        val drops: TextView
    )

    private lateinit var scoreboardRows: List<ScoreboardRow>

    private lateinit var btnConnectDice: Button
    private lateinit var btnConnectBoard: Button
    private lateinit var btnConnectServer: Button
    private lateinit var btnUndo: Button
    private lateinit var btnEndGame: Button
    private lateinit var btnResetScore: Button
    private lateinit var btnRefreshScoreboard: Button
    private lateinit var btnTestMode: Button
    private lateinit var btnShowQR: Button
    private lateinit var undoProgressBar: ProgressBar
    private lateinit var layoutDiceButtons: LinearLayout
    private lateinit var btnDice1: Button
    private lateinit var btnDice2: Button
    private lateinit var btnDice3: Button
    private lateinit var btnDice4: Button
    private lateinit var btnDice5: Button
    private lateinit var btnDice6: Button
    private lateinit var tvTestLog: TextView
    private lateinit var tvTestLogTitle: TextView
    private lateinit var scrollTestLog: ScrollView
    private lateinit var btnClearLog: Button

    // ---------- Game / players ----------
    private var lastRoll: Int? = null
    private var previousRoll: Int? = null
    private val currentGameProfileIds = mutableListOf<String>() // Track profiles in current game
    private val currentGameProfiles = mutableListOf<PlayerProfile>() // Full profile objects for AI detection

    // Last event details for API
    private var lastDice1: Int? = null
    private var lastDice2: Int? = null   // null in 1-die mode
    private var lastAvg: Int? = null     // in 1-die mode, avg = dice1
    private var lastModeTwoDice: Boolean = false

    // Last event details for live board
    private var lastTile: Tile? = null
    private var lastChanceCard: ChanceCard? = null

    private var playerCount: Int = 2
    private val playerNames = mutableListOf("Player 1", "Player 2", "Player 3", "Player 4")
    private val playerColors = mutableListOf("red", "green", "blue", "yellow") // Player-selected colors (4 options)
    private val selectedColors = mutableSetOf<String>() // Track already selected colors
    private var currentPlayer: Int = 0 // index in playerNames[0..playerCount-1]

    private val playerPositions = mutableMapOf<String, Int>()
    private val playerScores = mutableMapOf<String, Int>()
    private val playerStreaks = mutableMapOf<String, Int>()

    private var undoTimer: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialLaunch = true // Track if this is the first profile selection

    // ---------- BLE / BLDice ----------
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var diceConnectionController: DiceConnectionController

    private val diceColorMap = HashMap<Int, String>() // diceId -> color name

    // ---------- ESP32 Auto-Reconnection ----------
    private var esp32ReconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3
    private var reconnectJob: Job? = null
    private var gameActive = false  // Track if game is in progress
    private var watchdogReconnectInProgress = false  // True when heartbeat watchdog is forcing a reconnect
    
    // ---------- ESP32 Connection Timeout ----------
    private var esp32ScanJob: Job? = null
    private val ESP32_SCAN_TIMEOUT_MS = 10000L  // 10 seconds

    private var diceConnected: Boolean = false

    // dice mode
    private var playWithTwoDice: Boolean = false
    private val diceResults: MutableMap<Int, Int> = HashMap()
    
    // ---------- Helper Classes ----------
    private lateinit var esp32Manager: ESP32ConnectionManager
    private lateinit var uiUpdateManager: UIUpdateManager
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var esp32ErrorHandler: ESP32ErrorHandler
    private lateinit var esp32StateValidator: ESP32StateValidator
    private lateinit var stateSyncManager: StateSyncManager
    private lateinit var boardConnectionController: BoardConnectionController
    private lateinit var cloudieBrain: CloudieBrain
    private lateinit var sessionManager: GameSessionManager
    private var voiceService: VoiceService? = null
    private var voiceEnabled: Boolean = true
    private val cloudiePrefs by lazy { getSharedPreferences("cloudie_prefs", MODE_PRIVATE) }
    
    // ---------- Pairing Support ----------
    private var pairingDevice: BluetoothDevice? = null
    private val pairingReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
                    
                    Log.d(TAG, "Pairing request: variant=$pairingVariant, device=${device?.address}")
                    
                    if (device?.address == pairingDevice?.address) {
                        when (pairingVariant) {
                            BluetoothDevice.PAIRING_VARIANT_PIN -> {
                                // User needs to enter PIN
                                Log.d(TAG, "PIN pairing variant")
                                device?.let { dev ->
                                    BoardPairingDialogs.showPinEntryDialog(
                                        activity = this@MainActivity,
                                        device = dev,
                                        onClearPairing = { pairingDevice = null }
                                    )
                                }
                                abortBroadcast()
                            }
                            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> {
                                // ESP32 displays passkey, user confirms
                                val passkey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
                                Log.d(TAG, "Passkey confirmation variant: $passkey")
                                device?.let { dev ->
                                    BoardPairingDialogs.showPasskeyConfirmationDialog(
                                        activity = this@MainActivity,
                                        device = dev,
                                        passkey = passkey,
                                        onClearPairing = { pairingDevice = null }
                                    )
                                }
                                abortBroadcast()
                            }
                            0 -> {
                                // PAIRING_VARIANT_CONSENT (API 19+) - Just consent required (no passkey)
                                Log.d(TAG, "Consent pairing variant")
                                device?.setPairingConfirmation(true)
                                abortBroadcast()
                            }
                            else -> {
                                Log.w(TAG, "Unknown pairing variant: $pairingVariant")
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    
                    Log.d(TAG, "Bond state changed: ${device?.address}, prev=$prevBondState, new=$bondState")
                    
                    if (device?.address == pairingDevice?.address) {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                Log.d(TAG, "Device paired successfully")
                                appendTestLog("✅ Pairing successful!")
                                device?.let { dev ->
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Board paired successfully!", Toast.LENGTH_SHORT).show()
                                        // Now connect
                                        proceedWithESP32Connection(dev)
                                    }
                                }
                                pairingDevice = null
                            }
                            BluetoothDevice.BOND_NONE -> {
                                Log.d(TAG, "Pairing failed or cancelled (prev state: $prevBondState)")
                                appendTestLog("❌ Pairing failed")
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Pairing failed. Check ESP32 PIN.", Toast.LENGTH_LONG).show()
                                    btnConnectBoard.text = "🎮  Connect Board"
                                    btnConnectBoard.isEnabled = true
                                }
                                pairingDevice = null
                            }
                            BluetoothDevice.BOND_BONDING -> {
                                Log.d(TAG, "Pairing in progress...")
                                appendTestLog("🔐 Pairing in progress...")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Show PIN entry dialog for ESP32 pairing
    @SuppressLint("MissingPermission")
    private fun showPinEntryDialog(device: BluetoothDevice) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 6-digit PIN"
            setText("654321")  // Pre-fill with default PIN
            selectAll()  // Select all text for easy override
        }
        
        AlertDialog.Builder(this)
            .setTitle("Pair with ${device.name ?: device.address}")
            .setMessage("Enter the PIN code for this board\n(Default: 654321)")
            .setView(input)
            .setPositiveButton("Pair") { _, _ ->
                val pin = input.text.toString()
                if (pin.length == 6 && pin.all { it.isDigit() }) {
                    try {
                        val success = device.setPin(pin.toByteArray())
                        Log.d(TAG, "PIN set result: $success")
                        if (success) {
                            runOnUiThread {
                                Toast.makeText(this, "Pairing...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "Failed to set PIN", Toast.LENGTH_SHORT).show()
                            }
                            pairingDevice = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting PIN", e)
                        runOnUiThread {
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        pairingDevice = null
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
                    }
                    pairingDevice = null
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                pairingDevice = null
                runOnUiThread {
                    Toast.makeText(this, "Pairing cancelled", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)  // Force user to choose
            .show()
    }
    
    // Show passkey confirmation dialog for ESP32 pairing
    @SuppressLint("MissingPermission")
    private fun showPasskeyConfirmationDialog(device: BluetoothDevice, passkey: Int) {
        val passkeyStr = String.format("%06d", passkey)
        
        AlertDialog.Builder(this)
            .setTitle("Pair with ${device.name ?: device.address}")
            .setMessage("Confirm that the following passkey matches\nthe one shown on the ESP32 Serial Monitor:\n\n$passkeyStr")
            .setPositiveButton("Match") { _, _ ->
                try {
                    val success = device.setPairingConfirmation(true)
                    Log.d(TAG, "Pairing confirmation result: $success")
                    if (success) {
                        runOnUiThread {
                            Toast.makeText(this, "Pairing...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to confirm pairing", Toast.LENGTH_SHORT).show()
                        }
                        pairingDevice = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error confirming pairing", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    pairingDevice = null
                }
            }
            .setNegativeButton("Don't Match") { _, _ ->
                try {
                    device.setPairingConfirmation(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error rejecting pairing", e)
                }
                pairingDevice = null
                runOnUiThread {
                    Toast.makeText(this, "Pairing rejected", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }
    
    private lateinit var apiManager: ApiManager
    private lateinit var animationEventHandler: AnimationEventHandler
    private lateinit var aiPresenter: LocalAIPresenter
    private lateinit var profileManager: ProfileManager
    private lateinit var achievementEngine: AchievementEngine
    private lateinit var rivalryManager: RivalryManager
    private lateinit var liveStatePusher: LiveStatePusher
    
    // ---------- ESP32 BLE (Legacy - will be phased out) ----------
    private var esp32Connected: Boolean = false
    private var waitingForCoinPlacement: Boolean = false
    
    // Board management (multi-board support)
    private lateinit var boardScanManager: BoardScanManager
    private lateinit var boardPreferencesManager: BoardPreferencesManager
    
    // Test mode (bypasses ESP32)
    private var testModeEnabled: Boolean = false
    private var testModeType: Int = 0 // 0=Production, 1=Test Mode 1 (ESP32), 2=Test Mode 2 (No ESP32)

    // battery per diceId
    private val diceBatteryLevels: MutableMap<Int, Int> = HashMap()

    // Rolling status tracking - per die in 2-dice mode
    private var isDiceRolling: Boolean = false
    private val diceRollingStatus: MutableMap<Int, Boolean> = HashMap() // diceId -> rolling status

    // Database
    private lateinit var db: LastDropDatabase
    private lateinit var savedGameDao: SavedGameDao
    private lateinit var dao: LastDropDao
    private var currentGameId: Long = 0L

    // Game Engine
    private lateinit var gameEngine: GameEngine

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter?.isEnabled == true) {
                tvDiceStatus.text = "Bluetooth ON, tap Connect again"
            } else {
                tvDiceStatus.text = "Bluetooth still OFF"
            }
        }

    // ------------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initBluetooth()

        db = LastDropDatabase.getInstance(this)
        dao = db.dao()

        gameEngine = GameEngine()
        cloudieBrain = CloudieBrain()
        sessionManager = GameSessionManager(emptyList())
        savedGameDao = db.savedGameDao()
        profileManager = ProfileManager(this)
        achievementEngine = AchievementEngine(this)
        rivalryManager = RivalryManager(this)
        voiceEnabled = cloudiePrefs.getBoolean("cloudie_voice_enabled", true)
        voiceService = runCatching {
            TextToSpeechVoiceService(
                context = this,
                onReady = { appendTestLog("🔊 Cloudie voice ready") },
                onError = {
                    appendTestLog("⚠️ TTS error: $it")
                    runOnUiThread {
                        Toast.makeText(this, "Cloudie voice unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }.getOrElse {
            appendTestLog("⚠️ Falling back to silent Cloudie (TTS unavailable)")
            runOnUiThread {
                Toast.makeText(this, "Cloudie voice unavailable", Toast.LENGTH_SHORT).show()
            }
            NoOpVoiceService(this)
        }
        switchCloudieVoice.isChecked = voiceEnabled
        switchCloudieVoice.setOnCheckedChangeListener { _, isChecked ->
            voiceEnabled = isChecked
            appendTestLog(if (isChecked) "🔊 Cloudie voice ON" else "🤫 Cloudie voice OFF")
            cloudiePrefs.edit().putBoolean("cloudie_voice_enabled", isChecked).apply()
        }
        
        // Initialize board preferences manager
        boardPreferencesManager = BoardPreferencesManager(this)
        
        // Initialize board scanner (multi-board support)
        boardScanManager = BoardScanManager(
            context = this,
            bluetoothAdapter = bluetoothAdapter,
            scope = mainScope,
            onBoardFound = { boards -> showBoardSelectionDialog(boards) },
            onBoardSelected = { device -> connectToESP32Device(device) },
            onLogMessage = { msg -> appendTestLog(msg) }
        )
        
        // Configure MAC whitelist if needed
        BoardScanManager.TRUSTED_ADDRESSES = TRUSTED_ESP32_ADDRESSES

        // Initialize dice connection controller
        diceConnectionController = DiceConnectionController(
            context = this,
            bluetoothAdapter = bluetoothAdapter,
            playWithTwoDice = { playWithTwoDice },
            onStatus = { status -> runOnUiThread { tvDiceStatus.text = status } },
            onLog = { msg -> appendTestLog(msg) },
            onDiceConnected = { _, dieName ->
                runOnUiThread {
                    diceConnected = true
                    btnConnectDice.text = "Disconnect Dice"
                    tvDiceStatus.text = "Connected to ${dieName ?: "BLDice"}"
                }
            },
            onDiceDisconnected = {
                runOnUiThread {
                    diceConnected = false
                    btnConnectDice.text = "Connect Dice"
                    tvDiceStatus.text = "Dice disconnected"
                }
            }
        )

        boardConnectionController = BoardConnectionController(
            context = this,
            scope = mainScope,
            serviceUuid = ESP32_SERVICE_UUID,
            txUuid = ESP32_CHAR_TX_UUID,
            rxUuid = ESP32_CHAR_RX_UUID,
            cccdUuid = CCCDUUID,
            onLog = { message -> appendTestLog(message) },
            onConnected = { device ->
                esp32Connected = true
                esp32ReconnectAttempts = 0
                esp32ErrorHandler.startHeartbeatMonitoring()
                stateSyncManager.startSyncMonitoring()
                gameActive = true

                // Save as last connected board
                device.name?.let { boardId ->
                    boardPreferencesManager.saveLastBoard(boardId, device.address)
                    boardPreferencesManager.updateBoardConnection(boardId)
                }

                runOnUiThread {
                    btnConnectBoard.text = "🎮  Disconnect Board"
                    btnConnectBoard.isEnabled = true
                }
            },
            onDisconnected = { _ ->
                esp32Connected = false
                esp32ErrorHandler.stopHeartbeatMonitoring()
                stateSyncManager.stopSyncMonitoring()
                gameActive = false

                if (!watchdogReconnectInProgress) {
                    runOnUiThread {
                        Toast.makeText(this, "ESP32 disconnected", Toast.LENGTH_SHORT).show()
                        btnConnectBoard.text = "🎮  Connect Board"
                        btnConnectBoard.isEnabled = true
                    }
                }

                if (!watchdogReconnectInProgress && gameActive && esp32ReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    esp32ReconnectAttempts++
                    appendTestLog("🔄 Attempting reconnect (${esp32ReconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...")
                    reconnectJob?.cancel()
                    reconnectJob = mainScope.launch {
                        delay(2000)
                        connectESP32()
                    }
                } else if (!watchdogReconnectInProgress && esp32ReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    appendTestLog("⚠️ Max reconnect attempts reached")
                    showReconnectDialog()
                }
            },
            onMessage = { message -> handleESP32Event(message) },
            onServicesReady = {
                runOnUiThread {
                    Toast.makeText(this, "ESP32 Connected!", Toast.LENGTH_SHORT).show()
                }
                sendPairCommandToESP32()
                sendConfigToESP32()
            }
        )
        
        // Initialize helper classes
        uiUpdateManager = UIUpdateManager()
        batteryMonitor = BatteryMonitor(this)
        esp32ErrorHandler = ESP32ErrorHandler(
            context = this,
            onLogMessage = { message -> appendTestLog(message) },
            onCoinTimeoutExpired = { 
                // Handle coin timeout - could skip turn or continue
                runOnUiThread {
                    uiUpdateManager.cancelCountdown()
                    tvCoinTimeout.visibility = View.GONE
                }
            },
            onHeartbeatLost = {
                handleHeartbeatLoss()
            }
        )
        esp32StateValidator = ESP32StateValidator(
            onLogMessage = { message -> appendTestLog(message) }
        )
        stateSyncManager = StateSyncManager(
            onLogMessage = { message -> appendTestLog(message) },
            onSyncFailure = { message ->
                runOnUiThread {
                    showSyncFailureDialog(message)
                }
            }
        )
        apiManager = ApiManager(
            apiBaseUrl = API_BASE_URL,
            apiKey = API_KEY,
            sessionId = SESSION_ID
        )
        liveStatePusher = LiveStatePusher(apiManager)

        aiPresenter = LocalAIPresenter { line ->
            runOnUiThread {
                tvLastEvent.text = line
                appendTestLog("🤖 $line")
            }
        }
        
        // Initialize animation event handler
        animationEventHandler = AnimationEventHandler(this)

        registerBackPressHandler()

        mainScope.launch(Dispatchers.IO) {
            // start a new game session
            val game = GameEntity(
                startedAt = System.currentTimeMillis(),
                modeTwoDice = playWithTwoDice // will be updated once player chooses mode
            )
            currentGameId = dao.insertGame(game)
        }

        GoDiceSDK.listener = this

        val savedGameIdFromIntent = intent.getStringExtra("savedGameId")

        setupUiListeners()
        
        // Register pairing broadcast receiver
        val pairingFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(pairingReceiver, pairingFilter)
        
        // Connect to ESP32 board
        if (!testModeEnabled) {
            mainScope.launch {
                delay(2000)
                connectESP32()
            }
        }

        // Show profile selection screen unless resuming a saved game
        if (!savedGameIdFromIntent.isNullOrBlank()) {
            consumeSavedGameIntent(savedGameIdFromIntent)
        } else {
            showProfileSelection()
        }

        // ping global server on startup
        pingServer()

        fetchScoreboard()
        
        // Try auto-reconnect to last board if exists
        tryAutoReconnect()
    }
    
    /**
     * Handle activity results (Profile Selection and QR code scan)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Handle profile selection result
        if (requestCode == REQUEST_CODE_PROFILES) {
            if (resultCode == RESULT_OK && data != null) {
                val profileIds = data.getStringArrayListExtra("selected_profiles") ?: emptyList()
                val assignedColors = data.getStringArrayListExtra("assigned_colors") ?: emptyList()

                if (profileIds.isNotEmpty()) {
                    // Route to intro AI screen before the main board
                    val introIntent = Intent(this, IntroAiActivity::class.java).apply {
                        putStringArrayListExtra("selected_profiles", ArrayList(profileIds))
                        putStringArrayListExtra("assigned_colors", ArrayList(assignedColors))
                    }
                    startActivityForResult(introIntent, REQUEST_CODE_INTRO_AI)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No players selected. Please select profiles to start.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        if (requestCode == REQUEST_CODE_INTRO_AI) {
            if (resultCode == RESULT_OK && data != null) {
                val profileIds = data.getStringArrayListExtra("selected_profiles") ?: emptyList()
                val assignedColors = data.getStringArrayListExtra("assigned_colors") ?: emptyList()
                if (profileIds.isNotEmpty()) {
                    startGameWithProfiles(profileIds, assignedColors)
                }
            }
            return
        }
        
        val handled = LiveServerUiHelper.handleQrActivityResult(
            activity = this,
            requestCode = requestCode,
            resultCode = resultCode,
            data = data,
            onSessionId = { sessionId -> connectToLiveServer(sessionId) },
            onBoardQr = { qrData ->
                appendTestLog("📷 QR Scanned: ${qrData.boardId}")
                boardPreferencesManager.saveBoard(
                    boardId = qrData.boardId,
                    macAddress = qrData.macAddress,
                    nickname = qrData.nickname,
                    password = qrData.password
                )
                Toast.makeText(this, "Board saved: ${qrData.nickname ?: qrData.boardId}", Toast.LENGTH_SHORT).show()
                connectESP32()
            },
            onInvalid = {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
                appendTestLog("❌ Invalid QR code")
            },
            onCancel = { appendTestLog("📷 QR scan cancelled") }
        )

        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
    
    /**
     * Try to auto-reconnect to last connected board
     */
    private fun tryAutoReconnect() {
        val lastBoard = boardPreferencesManager.getLastBoard()
        if (lastBoard != null) {
            val (boardId, macAddress) = lastBoard
            appendTestLog("🔄 Auto-reconnect available: $boardId")
            
            // Show toast with option to reconnect
            Toast.makeText(
                this,
                "Last board: $boardId\nTap Connect to reconnect",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        undoTimer?.cancel()
        mainScope.cancel()
        uiUpdateManager.cleanup()  // Cleanup helper classes
        esp32ErrorHandler.cleanup()  // Cleanup error handler
        stateSyncManager.cleanup()  // Cleanup sync manager
        apiManager.cleanup()  // Cleanup API manager
        animationEventHandler.cleanup()  // Cleanup animation handler
        (voiceService as? TextToSpeechVoiceService)?.shutdown()
        diceConnectionController.stopScan()
        disconnectESP32()
        stopESP32Scan()
        disconnectAllDice()
        
        // Unregister pairing broadcast receiver
        try {
            unregisterReceiver(pairingReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering pairing receiver", e)
        }
    }

    // ------------------------------------------------------------------------
    //  UI setup
    // ------------------------------------------------------------------------

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvDiceStatus = findViewById(R.id.tvDiceStatus)
        tvBattery = findViewById(R.id.tvBattery)
        tvLastEvent = findViewById(R.id.tvLastEvent)
        tvUndoStatus = findViewById(R.id.tvUndoStatus)
        tvCoinTimeout = findViewById(R.id.tvCoinTimeout)
        tvCurrentTurn = findViewById(R.id.tvCurrentTurn)
        tvCloudieBanner = findViewById(R.id.tvCloudieBanner)
        switchCloudieVoice = findViewById(R.id.switchCloudieVoice)

        scoreboardRows = listOf(
            ScoreboardRow(
                findViewById(R.id.rowPlayer1),
                findViewById(R.id.tvRank1),
                findViewById(R.id.tvColor1),
                findViewById(R.id.tvName1),
                findViewById(R.id.tvTileNumber1),
                findViewById(R.id.tvTileName1),
                findViewById(R.id.tvDrops1)
            ),
            ScoreboardRow(
                findViewById(R.id.rowPlayer2),
                findViewById(R.id.tvRank2),
                findViewById(R.id.tvColor2),
                findViewById(R.id.tvName2),
                findViewById(R.id.tvTileNumber2),
                findViewById(R.id.tvTileName2),
                findViewById(R.id.tvDrops2)
            ),
            ScoreboardRow(
                findViewById(R.id.rowPlayer3),
                findViewById(R.id.tvRank3),
                findViewById(R.id.tvColor3),
                findViewById(R.id.tvName3),
                findViewById(R.id.tvTileNumber3),
                findViewById(R.id.tvTileName3),
                findViewById(R.id.tvDrops3)
            ),
            ScoreboardRow(
                findViewById(R.id.rowPlayer4),
                findViewById(R.id.tvRank4),
                findViewById(R.id.tvColor4),
                findViewById(R.id.tvName4),
                findViewById(R.id.tvTileNumber4),
                findViewById(R.id.tvTileName4),
                findViewById(R.id.tvDrops4)
            )
        )

        btnConnectDice = findViewById(R.id.btnConnectDice)
        btnConnectBoard = findViewById(R.id.btnConnectBoard)
        btnConnectServer = findViewById(R.id.btnConnectServer)
        btnUndo = findViewById(R.id.btnUndo)
        undoProgressBar = findViewById(R.id.undoProgressBar)
        btnResetScore = findViewById(R.id.btnResetScore)
        btnEndGame = findViewById(R.id.btnEndGame)
        btnRefreshScoreboard = findViewById(R.id.btnRefreshScoreboard)
        btnTestMode = findViewById(R.id.btnTestMode)
        btnShowQR = findViewById(R.id.btnShowQR)
        layoutDiceButtons = findViewById(R.id.layoutDiceButtons)
        btnDice1 = findViewById(R.id.btnDice1)
        btnDice2 = findViewById(R.id.btnDice2)
        btnDice3 = findViewById(R.id.btnDice3)
        btnDice4 = findViewById(R.id.btnDice4)
        btnDice5 = findViewById(R.id.btnDice5)
        btnDice6 = findViewById(R.id.btnDice6)
        tvTestLog = findViewById(R.id.tvTestLog)
        tvTestLogTitle = findViewById(R.id.tvTestLogTitle)
        scrollTestLog = findViewById(R.id.scrollTestLog)
        btnClearLog = findViewById(R.id.btnClearLog)

        tvTitle.text = "Last Drop Earth: AI Board Game Controller"
        tvDiceStatus.text = "Not connected"
        tvBattery.text = "Battery: --"
        tvLastEvent.text = "No events yet."
        tvUndoStatus.text = ""
        tvCloudieBanner.text = "Cloudie is warming up…"

        btnConnectDice.text = "Connect Dice"
        // Initialize test mode UI
        btnTestMode.text = "Production"
        layoutDiceButtons.visibility = View.GONE
        tvTestLogTitle.visibility = View.GONE
        scrollTestLog.visibility = View.GONE
        btnClearLog.visibility = View.GONE
    }

    private fun setupUiListeners() {
        btnConnectDice.setOnClickListener { onConnectButtonClicked() }
        btnConnectBoard.setOnClickListener { onConnectBoardClicked() }
        btnConnectServer.setOnClickListener { onConnectServerClicked() }
        
        // Menu buttons
        findViewById<Button>(R.id.btnGameHistory)?.setOnClickListener {
            val preferredProfile = when {
                playerCount > 0 -> currentGameProfiles.getOrNull(currentPlayer.coerceIn(0, playerCount - 1))
                currentGameProfiles.size == 1 -> currentGameProfiles.firstOrNull()
                else -> null
            }
            openHistoryForProfile(
                profileId = preferredProfile?.playerId,
                profileName = preferredProfile?.nickname?.ifBlank { preferredProfile.name }
            )
        }
        findViewById<Button>(R.id.btnLeaderboard)?.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        findViewById<Button>(R.id.btnChangePlayers)?.setOnClickListener {
            showProfileSelection()
        }
        
        btnUndo.setOnClickListener {
            if (btnUndo.text.toString().startsWith("Confirm")) {
                confirmUndo()
            } else {
                startUndoWindow()
            }
        }
        btnResetScore.setOnClickListener { resetLocalGame() }
        btnEndGame.setOnClickListener { showEndGameDialog() }
        btnRefreshScoreboard.setOnClickListener { fetchScoreboard() }
        
        btnTestMode.setOnClickListener { toggleTestMode() }
        btnShowQR.setOnClickListener { showSpectatorQR() }
        btnDice1.setOnClickListener { simulateDiceRoll(1) }
        btnDice2.setOnClickListener { simulateDiceRoll(2) }
        btnDice3.setOnClickListener { simulateDiceRoll(3) }
        btnDice4.setOnClickListener { simulateDiceRoll(4) }
        btnDice5.setOnClickListener { simulateDiceRoll(5) }
        btnDice6.setOnClickListener { simulateDiceRoll(6) }
        btnClearLog.setOnClickListener { 
            tvTestLog.text = "Console cleared..."
            Toast.makeText(this, "Test log cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onConnectButtonClicked() {
        if (!diceConnected) {
            // Not connected → start connect flow
            showDiceModeDialog()
        } else {
            // Already connected → disconnect all dice
            disconnectAllDice()
        }
    }

    private fun onConnectBoardClicked() {
        if (!esp32Connected) {
            // Not connected → start scanning for boards
            btnConnectBoard.text = "🎮  Scanning..."
            btnConnectBoard.isEnabled = false
            
            boardScanManager.startScan()
            
            // Re-enable button after scan timeout
            mainScope.launch {
                delay(60000)  // 60 second scan timeout
                if (!esp32Connected) {
                    runOnUiThread {
                        btnConnectBoard.text = "🎮  Connect Board"
                        btnConnectBoard.isEnabled = true
                    }
                }
            }
        } else {
            // Already connected → disconnect
            disconnectESP32()
            runOnUiThread {
                btnConnectBoard.text = "🎮  Connect Board"
                Toast.makeText(this, "Board disconnected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onConnectServerClicked() {
        Log.d(TAG, "onConnectServerClicked called")
        
        // Send immediate heartbeat when button is clicked
        apiManager.sendImmediateHeartbeat()
        
        // Show instructions dialog
        LiveServerUiHelper.showConnectDialog(this) {
            Log.d(TAG, "Scan QR Code button clicked in dialog")
            LiveServerUiHelper.checkCameraPermissionAndScan(
                activity = this,
                requestCode = REQUEST_CAMERA_PERMISSION
            )
        }
    }

    private fun connectToLiveServer(sessionId: String) {
        btnConnectServer.isEnabled = false
        btnConnectServer.text = "🌐  Connecting..."
        
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
                    btnConnectServer.text = "🌐  Connected ✓"
                    btnConnectServer.setBackgroundColor(0xFF4CAF50.toInt())
                    Toast.makeText(
                        this@MainActivity,
                        "Connected to session: ${sessionId.take(8)}...",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Send another heartbeat after successful connection
                    apiManager.sendImmediateHeartbeat()
                    
                    // Re-enable after 2 seconds
                    delay(2000)
                    btnConnectServer.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnConnectServer.text = "🌐  Connect to Live Server"
                    btnConnectServer.isEnabled = true
                    Toast.makeText(
                        this@MainActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Player configuration (2–4 players + names)
    // ------------------------------------------------------------------------

    /**
     * Show profile selection screen
     */
    private fun showProfileSelection() {
        val intent = Intent(this, ProfileSelectionActivity::class.java)
        // Only mark as "from main activity" if this is NOT the initial launch
        if (!isInitialLaunch) {
            intent.putExtra("FROM_MAIN_ACTIVITY", true)
        }
        startActivityForResult(intent, REQUEST_CODE_PROFILES)
    }

    /**
     * Fallback: Old player configuration flow (kept for backward compatibility)
     */
    private fun configurePlayers() {
        PlayerConfigUiHelper.showPlayerCountDialog(
            activity = this,
            defaultIndex = 0
        ) { count ->
            playerCount = count
            selectedColors.clear()
            askPlayerName(0)
        }
    }

    private fun askPlayerName(index: Int) {
        if (index >= playerCount) {
            Toast.makeText(
                this,
                "Players: " + (0 until playerCount).joinToString { playerNames[it] },
                Toast.LENGTH_LONG
            ).show()
            resetLocalGame()
            
            // Show turn indicator for first player
            updateTurnIndicatorDisplay()
            return
        }

        val allColors = listOf("red", "green", "blue", "yellow")
        val allColorNames = listOf("Red 🔴", "Green 🟢", "Blue 🔵", "Yellow 🟡")

        val availableColors = mutableListOf<String>()
        val availableColorNames = mutableListOf<String>()
        allColors.forEachIndexed { idx, color ->
            if (!selectedColors.contains(color)) {
                availableColors.add(color)
                availableColorNames.add(allColorNames[idx])
            }
        }

        if (availableColors.isEmpty()) {
            availableColors.addAll(allColors)
            availableColorNames.addAll(allColorNames)
        }

        PlayerConfigUiHelper.showPlayerSetupDialog(
            activity = this,
            playerIndex = index,
            availableColorNames = availableColorNames,
            availableColors = availableColors
        ) { name, color ->
            playerNames[index] = if (name.isNotEmpty()) name else "Player ${index + 1}"
            playerColors[index] = color
            selectedColors.add(color)
            Log.d(TAG, "Player ${index + 1}: ${playerNames[index]}, Color: ${playerColors[index]}")
            askPlayerName(index + 1)
        }
    }

    // ------------------------------------------------------------------------
    //  Bluetooth + scan
    // ------------------------------------------------------------------------

    private fun initBluetooth() {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
    }

    private fun showDiceModeDialog() {
        val options = arrayOf("Play with 1 die", "Play with 2 dice")

        AlertDialog.Builder(this)
            .setTitle("Choose dice mode")
            .setItems(options) { dialog, which ->
                playWithTwoDice = (which == 1)
                dialog.dismiss()
                connectDice()
            }
            .show()
    }

    private fun connectDice() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBt.launch(intent)
            return
        }
        if (!ensureBlePermissions()) return

        diceConnectionController.startScan()
    }

    private fun disconnectAllDice() {
        diceConnectionController.disconnectAll()
        diceResults.clear()
        diceBatteryLevels.clear()
        diceColorMap.clear()
        diceRollingStatus.clear()

        diceConnected = false

        runOnUiThread {
            btnConnectDice.text = "Connect Dice"
            tvDiceStatus.text = "Dice disconnected"
            tvBattery.text = "Battery: --"
        }
    }

    // ------------------------------------------------------------------------
    //  GoDiceSDK.Listener implementation
    // ------------------------------------------------------------------------

    override fun onDiceColor(diceId: Int, color: Int) {
        val colorName = when (color) {
            GoDiceSDK.DICE_BLACK -> "black"
            GoDiceSDK.DICE_RED -> "red"
            GoDiceSDK.DICE_GREEN -> "green"
            GoDiceSDK.DICE_BLUE -> "blue"
            GoDiceSDK.DICE_YELLOW -> "yellow"
            GoDiceSDK.DICE_ORANGE -> "orange"
            else -> "red"
        }

        diceColorMap[diceId] = colorName

        // Assign color to current player if it's one of the standard colors
        if (colorName in listOf("red", "green", "blue", "yellow")) {
            playerColors[currentPlayer] = colorName
        }

        Log.d(TAG, "Dice $diceId color: $colorName")
    }

    override fun onDiceStable(diceId: Int, number: Int) {
        // Mark this specific die as stable (not rolling)
        diceRollingStatus[diceId] = false

        // Only set global rolling to false when ALL dice are stable
        val anyDiceRolling = diceRollingStatus.values.any { it }
        if (!anyDiceRolling) {
            isDiceRolling = false
        }

        runOnUiThread {
            if (!playWithTwoDice) {
                // simple 1-die mode
                lastModeTwoDice = false
                lastDice1 = number
                lastDice2 = null
                lastAvg = number   // avg is just the same as the single die

                handleNewRoll(number)
                sendLastRollToServer()
                pushLiveStateToBoard(rolling = false)
                return@runOnUiThread
            }

            // 2-dice mode: collect results from both dice
            diceResults[diceId] = number

// Sort dice IDs so we always treat die1/die2 consistently
            val sortedIds = diceResults.keys.sorted()
            val d1Id = sortedIds[0]
            val d2Id = sortedIds.getOrNull(1)
            val v1 = diceResults[d1Id] ?: return@runOnUiThread
            val v2 = d2Id?.let { diceResults[it] } // may be null if only one die stopped

// Update last* so rolling payload / board can expose partial results
            lastModeTwoDice = true
            lastDice1 = v1
            lastDice2 = v2
            lastAvg = if (v2 != null) ((v1 + v2) / 2.0).roundToInt() else null

            if (diceResults.size < 2) {
                // FIRST DIE STOPPED, SECOND STILL ROLLING
                tvDiceStatus.text = "Die 1 stable, waiting for second die…"

                // If at least one die is still rolling, push a rolling update
                if (diceRollingStatus.values.any { it }) {
                    pushRollingStatusToApi()
                }
                // Do NOT advance game state or move tokens yet
                return@runOnUiThread
            }

// At this point we have both dice results
            val v1Final = lastDice1 ?: return@runOnUiThread
            val v2Final = lastDice2 ?: return@runOnUiThread
            val avg = ((v1Final + v2Final) / 2.0).roundToInt()
            lastAvg = avg

// Advance game state with the average
            handleNewRoll(avg)

// Player who just rolled:
            val playerIndex = (currentPlayer - 1 + playerCount) % playerCount
            val playerName = playerNames[playerIndex]

// Show detailed roll info
            tvLastEvent.text = "D1: $v1Final, D2: $v2Final, Avg: $avg (Player: $playerName)"
            tvDiceStatus.text = "Two dice stable"

            diceResults.clear()
            sendLastRollToServer()
            pushLiveStateToBoard(rolling = false)

        }
    }

    override fun onDiceRoll(diceId: Int, number: Int) {
        // Mark this specific die as rolling
        diceRollingStatus[diceId] = true

        runOnUiThread {
            if (playWithTwoDice) {
                val rollingCount = diceRollingStatus.values.count { it }
                tvDiceStatus.text = "Dice rolling… ($rollingCount/2)"
            } else {
                tvDiceStatus.text = "Dice rolling…"
            }
        }

        // Send rolling status to API
        if (!isDiceRolling) {
            isDiceRolling = true
            pushRollingStatusToApi()
        }
    }

    override fun onDiceChargingStateChanged(diceId: Int, charging: Boolean) {
        runOnUiThread {
            val state = if (charging) "Charging" else "Not charging"
            Log.d(TAG, "Dice $diceId $state")
        }
    }

    override fun onDiceChargeLevel(diceId: Int, level: Int) {
        runOnUiThread {
            diceBatteryLevels[diceId] = level
            
            // Use BatteryMonitor for warnings
            batteryMonitor.updateBatteryLevel(diceId, level) { message ->
                appendTestLog(message)
            }
            
            updateBatteryUi()
        }
    }

    // Update battery text for 1 or 2 dice with color-coded warnings
    private fun updateBatteryUi() {
        if (!playWithTwoDice) {
            val level = diceBatteryLevels.values.firstOrNull()
            if (level != null) {
                tvBattery.text = "Battery: $level%"
                // Color-code based on battery level
                tvBattery.setTextColor(when {
                    level <= 10 -> android.graphics.Color.parseColor("#F44336") // Red - critical
                    level <= 20 -> android.graphics.Color.parseColor("#FF9800") // Orange - low
                    else -> android.graphics.Color.parseColor("#4CAF50") // Green - good
                })
            } else {
                tvBattery.text = "Battery: --"
                tvBattery.setTextColor(android.graphics.Color.WHITE)
            }
        } else {
            val sorted = diceBatteryLevels.keys.sorted()
            when {
                sorted.size >= 2 -> {
                    val l1 = diceBatteryLevels[sorted[0]] ?: 0
                    val l2 = diceBatteryLevels[sorted[1]] ?: 0
                    tvBattery.text = "D1: ${l1}%   D2: ${l2}%"
                    
                    // Show red if any dice is critical
                    tvBattery.setTextColor(when {
                        l1 <= 10 || l2 <= 10 -> android.graphics.Color.parseColor("#F44336")
                        l1 <= 20 || l2 <= 20 -> android.graphics.Color.parseColor("#FF9800")
                        else -> android.graphics.Color.parseColor("#4CAF50")
                    })
                }
                sorted.size == 1 -> {
                    val l1 = diceBatteryLevels[sorted[0]] ?: 0
                    tvBattery.text = "D1: ${l1}%   D2: --"
                    tvBattery.setTextColor(when {
                        l1 <= 10 -> android.graphics.Color.parseColor("#F44336")
                        l1 <= 20 -> android.graphics.Color.parseColor("#FF9800")
                        else -> android.graphics.Color.parseColor("#4CAF50")
                    })
                }
                else -> {
                    tvBattery.text = "D1: --   D2: --"
                    tvBattery.setTextColor(android.graphics.Color.WHITE)
                }
            }
        }
    }
    
    // Update turn indicator with current player
    private fun updateTurnIndicatorDisplay() {
        runOnUiThread {
            if (currentPlayer < playerCount) {
                val playerName = playerNames[currentPlayer]
                val playerColor = playerColors[currentPlayer]
                uiUpdateManager.updateTurnIndicator(tvCurrentTurn, playerName, playerColor)
                aiPresenter.onTurnStart(playerName)
                tvCurrentTurn.visibility = View.VISIBLE
                emitCloudieLine(CloudieEventType.TURN_PROMPT, null)
            } else {
                tvCurrentTurn.visibility = View.GONE
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Game logic hooks
    // ------------------------------------------------------------------------

    // Store state before each roll for undo
    private var previousPlayerIndex: Int = 0
    private var previousPosition: Int = 0
    private var previousScore: Int = 0
    private var previousDice1: Int? = null
    private var previousDice2: Int? = null
    private var previousAvg: Int? = null

    /**
     * Check if current player is AI (Cloudie) and trigger automatic dice roll
     */
    private fun checkAndTriggerAITurn() {
        // Only trigger if we have profiles loaded
        if (currentGameProfiles.isEmpty()) return
        
        val safeIndex = currentPlayer.coerceIn(0, playerCount - 1)
        if (safeIndex >= currentGameProfiles.size) return
        
        val currentProfile = currentGameProfiles[safeIndex]
        
        // Check if current player is AI
        if (currentProfile.isAI) {
            // Delay slightly to simulate "thinking"
            mainScope.launch {
                delay(1500) // 1.5 second delay
                
                runOnUiThread {
                    // Generate random dice roll for AI
                    val aiRoll = if (playWithTwoDice) {
                        // Two dice mode: generate two random values
                        val die1 = Random.nextInt(1, 7)
                        val die2 = Random.nextInt(1, 7)
                        val avg = ((die1 + die2) / 2.0).roundToInt()
                        
                        tvDiceStatus.text = "☁️ Cloudie rolls: $die1 + $die2 = $avg"
                        appendTestLog("🤖 AI Roll: Die 1 = $die1, Die 2 = $die2, Avg = $avg")
                        
                        // Set last dice values for server
                        lastModeTwoDice = true
                        lastDice1 = die1
                        lastDice2 = die2
                        lastAvg = avg
                        
                        avg
                    } else {
                        // Single die mode
                        val roll = Random.nextInt(1, 7)
                        
                        tvDiceStatus.text = "☁️ Cloudie rolls: $roll"
                        appendTestLog("🤖 AI Roll: $roll")
                        
                        // Set last dice values for server
                        lastModeTwoDice = false
                        lastDice1 = roll
                        lastDice2 = null
                        lastAvg = roll
                        
                        roll
                    }
                    
                    // Process the AI roll
                    handleNewRoll(aiRoll)
                    sendLastRollToServer()
                    pushLiveStateToBoard(rolling = false)
                }
            }
        }
    }
    
    private fun handleNewRoll(value: Int) {
        previousRoll = lastRoll
        lastRoll = value

        val safeIndex = currentPlayer.coerceIn(0, playerCount - 1)
        val playerName = playerNames[safeIndex]

        // Save state before making changes (for undo)
        previousPlayerIndex = safeIndex
        previousPosition = playerPositions[playerName] ?: 1
        previousScore = playerScores[playerName] ?: 10
        previousDice1 = lastDice1
        previousDice2 = lastDice2
        previousAvg = lastAvg

        val currentPos = playerPositions[playerName] ?: 1
        val turnResult = gameEngine.processTurn(currentPos, value)

        playerPositions[playerName] = turnResult.newPosition
        playerScores[playerName] = (playerScores[playerName] ?: 0) + turnResult.scoreChange
        playerStreaks[playerName] = if (turnResult.scoreChange > 0) {
            (playerStreaks[playerName] ?: 0) + 1
        } else {
            0
        }

        // track for live board
        lastTile = turnResult.tile
        lastChanceCard = turnResult.chanceCard

        var eventText =
            "$playerName rolled a $value, moved to Tile ${turnResult.newPosition} (${turnResult.tile.name})"
        if (turnResult.chanceCard != null) {
            eventText += "\nDrew card: ${turnResult.chanceCard.description}"
        }
        tvLastEvent.text = eventText
        aiPresenter.onTurnResult(playerName, turnResult.tile.name, turnResult.scoreChange)

        Log.d(TAG, "Stable roll $value by $playerName")
        
        // Send to ESP32 for physical board update
        sendRollToESP32(safeIndex, value, currentPos, turnResult.newPosition)

        // advance turn 0..playerCount-1
        val sessionTileType = mapTileTypeForSession(turnResult.tile.type, turnResult.tile.index >= gameEngine.tiles.size)
        val moveCtx = buildMoveContextForIndex(
            playerIndex = safeIndex,
            fromPosition = previousPosition,
            toPosition = turnResult.newPosition,
            dice = value,
            tileType = sessionTileType,
            engineTileType = turnResult.tile.type,
            tileName = turnResult.tile.name,
            scoreDelta = turnResult.scoreChange,
            chanceCardDescription = turnResult.chanceCard?.description,
            chanceCardEffect = turnResult.chanceCard?.effect
        )

        val nextPlayerIndex = (currentPlayer + 1) % playerCount
        currentPlayer = nextPlayerIndex
        updateSessionAfterMove(safeIndex, turnResult.newPosition, nextPlayerIndex)
        emitCloudieLine(CloudieEventType.DICE_ROLL, moveCtx)
        emitCloudieLine(CloudieEventType.LANDED_TILE, moveCtx)
        
        // Update turn indicator
        updateTurnIndicatorDisplay()

        updateScoreboard()
        
        // Check if next player is AI and trigger auto-roll
        checkAndTriggerAITurn()
    }

    // ------------------------------------------------------------------------
    //  Server communication (LastDrop API)
    // ------------------------------------------------------------------------

    private fun pingServer() {
        apiManager.pingServer()
    }

    private fun resetLocalGame() {
        mainScope.launch(Dispatchers.IO) {
            playerPositions.clear()
            playerScores.clear()
            playerStreaks.clear()
            (0 until playerCount).forEach {
                playerScores[playerNames[it]] = 10
                playerPositions[playerNames[it]] = 1  // Start position is tile 1
            }

            // Reset to player 1 (index 0)
            currentPlayer = 0
            
            // Check if first player is AI
            withContext(Dispatchers.Main) {
                checkAndTriggerAITurn()
            }
            
            // Update turn indicator
            runOnUiThread {
                updateTurnIndicatorDisplay()
            }

            val game = GameEntity(
                startedAt = System.currentTimeMillis(),
                modeTwoDice = playWithTwoDice // will be updated once player chooses mode
            )
            currentGameId = dao.insertGame(game)
            syncSessionState()
            
            // Send reset command to ESP32
            withContext(Dispatchers.Main) {
                sendResetToESP32()
            }

            // Push reset state to server
            pushResetStateToServer()
            
            // Start heartbeat to keep session alive
            apiManager.startHeartbeat()

            withContext(Dispatchers.Main) {
                updateScoreboard()
                tvLastEvent.text = "Game reset - ${playerNames[0]}'s turn"
                showCloudieLine("Game reset — ${playerNames[0]}'s turn. Let's roll!")
            }
        }
    }

    private fun pushResetStateToServer() {
        liveStatePusher.pushResetState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerCount = playerCount
        )
    }

    /**
     * Record game results to player profiles
     */
    private fun recordGameResults(winnerIndex: Int) {
        if (currentGameProfileIds.isEmpty()) {
            Log.d(TAG, "No profiles to record (using old player flow)")
            return
        }
        
        mainScope.launch(Dispatchers.IO) {
            try {
                // Record rivalry results (head-to-head)
                if (currentGameProfileIds.size >= 2) {
                    val scores = (0 until playerCount).map { i ->
                        Pair(currentGameProfileIds.getOrNull(i), playerScores[playerNames[i]] ?: 0)
                    }.filter { it.first != null }
                    
                    // Record each rivalry pairing
                    for (i in scores.indices) {
                        for (j in (i + 1) until scores.size) {
                            val (p1Id, p1Score) = scores[i]
                            val (p2Id, p2Score) = scores[j]
                            
                            if (p1Score > p2Score) {
                                rivalryManager.recordGameResult(p1Id!!, p2Id!!, p1Score, p2Score)
                            } else if (p2Score > p1Score) {
                                rivalryManager.recordGameResult(p2Id!!, p1Id!!, p2Score, p1Score)
                            }
                        }
                    }
                }
                
                for (i in 0 until playerCount) {
                    val profileId = currentGameProfileIds.getOrNull(i) ?: continue
                    val won = (i == winnerIndex)
                    val score = playerScores[playerNames[i]] ?: 0
                    val position = playerPositions[playerNames[i]] ?: 1
                    val color = playerColors.getOrNull(i) ?: "FF0000"
                    
                    // Calculate placement (1st, 2nd, 3rd, 4th)
                    val allScores = (0 until playerCount).mapNotNull { idx ->
                        playerScores[playerNames[idx]]
                    }.sortedDescending()
                    val placement = allScores.indexOf(score) + 1
                    
                    // Create game result
                    val gameResult = GameResult(
                        name = playerNames[i],
                        color = color,
                        score = score,
                        finalTile = position,
                        placement = placement,
                        dropsEarned = score,
                        gameTimeMinutes = 0, // TODO: Track actual game time
                        chanceCardsDrawn = 0, // TODO: Track from game
                        droughtTileHits = 0,
                        bonusTileHits = 0,
                        waterDockHits = 0,
                        maxComebackPoints = 0,
                        wasEliminated = false,
                        eliminatedOpponents = emptyList(),
                        hadPerfectStart = false,
                        usedUndo = false
                    )
                    
                    // Record to profile
                    profileManager.recordGameResult(
                        playerId = profileId,
                        won = won,
                        score = score,
                        dropsEarned = score,
                        playTimeMinutes = 0 // TODO: Track actual game time
                    )
                    
                    // Get updated profile for achievement checking
                    val updatedProfile = profileManager.getProfile(profileId)
                    
                    if (updatedProfile != null) {
                        // Check for unlocked achievements
                        val newAchievements = achievementEngine.checkGameEndAchievements(
                            playerId = profileId,
                            won = won,
                            score = score,
                            placement = placement,
                            totalPlayers = playerCount,
                            gameResult = gameResult,
                            profile = updatedProfile
                        )
                        
                        // Show achievement notifications
                        if (newAchievements.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                showAchievementUnlocked(newAchievements)
                            }
                        }
                    }
                    
                    Log.d(TAG, "Recorded game result for ${playerNames[i]}: ${if (won) "WIN" else "LOSS"}")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Game stats saved to profiles!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording game results: ${e.message}")
            }
        }
    }

    /**
     * Show achievement unlock notification
     */
    private fun showAchievementUnlocked(achievements: List<Achievement>) {
        achievements.forEach { achievement ->
            val definition = AchievementDefinitions.getById(achievement.type)
            if (definition != null) {
                val message = "${definition.icon} ${definition.name}\n${definition.description}"
                
                // Create toast with custom duration
                val toast = Toast.makeText(this, message, Toast.LENGTH_LONG)
                toast.show()
                
                // Pulse animation on tvLastEvent to draw attention
                tvLastEvent.text = "🏆 ${definition.name} unlocked!"
                val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse_celebration)
                tvLastEvent.startAnimation(pulseAnim)
                
                Log.d(TAG, "🏆 Achievement Unlocked: ${definition.name}")
                
                // Mark as shown
                mainScope.launch {
                    achievementEngine.markAsShown(achievement.achievementId)
                }
            }
        }
    }

    private fun sendLastRollToServer() {
        val avg = lastAvg ?: run {
            Toast.makeText(this, "No roll yet", Toast.LENGTH_SHORT).show()
            return
        }

        val playerIndex = (currentPlayer - 1 + playerCount) % playerCount
        val playerName = playerNames[playerIndex]
        val d1 = lastDice1
        val d2 = lastDice2
        val modeTwo = lastModeTwoDice

        // 1) Save to local DB
        mainScope.launch(Dispatchers.IO) {
            val event = RollEventEntity(
                gameId = currentGameId,
                playerName = playerName,
                timestamp = System.currentTimeMillis(),
                modeTwoDice = modeTwo,
                dice1 = d1,
                dice2 = d2,
                avg = avg,
                sentToServer = false
            )
            val eventId = dao.insertRoll(event)

            // 2) Try to send to server (best effort)
            val success = sendToCloud(playerName, modeTwo, d1, d2, avg)

            if (success) {
                dao.markEventSynced(eventId)
            }
        }

        runOnUiThread {
            Toast.makeText(
                this,
                if (modeTwo) "Saved & sent: D1=$d1 D2=$d2 Avg=$avg ($playerName)"
                else "Saved & sent: D1=$d1 Avg=$avg ($playerName)",
                Toast.LENGTH_SHORT
            ).show()

            fetchScoreboard() // remote scoreboard
        }
    }

    private fun sendToCloud(
        playerName: String,
        modeTwoDice: Boolean,
        d1: Int?,
        d2: Int?,
        avg: Int
    ): Boolean {
        return apiManager.sendRollToCloud(playerName, modeTwoDice, d1, d2, avg)
    }

    /**
     * Send rolling status while dice is tumbling
     */
    private fun pushRollingStatusToApi() {
        liveStatePusher.pushRollingStatus(buildRollingSnapshot())
    }

    private fun pushLiveStateToBoard(
        rolling: Boolean = false,
        eventType: String? = null,
        eventMessage: String? = null
    ) {
        liveStatePusher.pushLiveState(
            buildLiveStateSnapshot(
                rolling = rolling,
                eventType = eventType,
                eventMessage = eventMessage
            )
        )
    }

    private fun buildRollingSnapshot(): RollingStateSnapshot {
        return RollingStateSnapshot(
            playerNames = playerNames.toList(),
            playerColors = playerColors.toList(),
            playerPositions = playerPositions.toMap(),
            playerScores = playerScores.toMap(),
            playerCount = playerCount,
            currentPlayer = currentPlayer,
            playWithTwoDice = playWithTwoDice,
            diceColorMap = diceColorMap.toMap(),
            diceRollingStatus = diceRollingStatus.toMap(),
            lastDice1 = lastDice1,
            lastDice2 = lastDice2,
            lastAvg = lastAvg
        )
    }

    private fun buildLiveStateSnapshot(
        rolling: Boolean = false,
        eventType: String? = null,
        eventMessage: String? = null
    ): LiveStateSnapshot {
        return LiveStateSnapshot(
            playerNames = playerNames.toList(),
            playerColors = playerColors.toList(),
            playerPositions = playerPositions.toMap(),
            playerScores = playerScores.toMap(),
            playerCount = playerCount,
            currentPlayer = currentPlayer,
            playWithTwoDice = playWithTwoDice,
            diceColorMap = diceColorMap.toMap(),
            lastDice1 = lastDice1,
            lastDice2 = lastDice2,
            lastAvg = lastAvg,
            lastTileName = lastTile?.name,
            lastTileType = lastTile?.type?.name,
            lastChanceCardNumber = lastChanceCard?.number,
            lastChanceCardText = lastChanceCard?.description,
            rolling = rolling,
            eventType = eventType,
            eventMessage = eventMessage
        )
    }

    private fun fetchScoreboard() {
        mainScope.launch(Dispatchers.IO) {
            try {
                val totals = dao.getTotalsForGame(currentGameId)

                if (totals.isEmpty()) {
                    runOnUiThread {
                        updateScoreboard()
                    }
                } else {
                    // Manually map player names from your list to the scores
                    val p1Name = playerNames.getOrNull(0) ?: "Player 1"
                    val p2Name = playerNames.getOrNull(1) ?: "Player 2"
                    val p3Name = playerNames.getOrNull(2) ?: "Player 3"
                    val p4Name = playerNames.getOrNull(3) ?: "Player 4"

                    val s1 = totals.find { it.playerName == p1Name }?.totalDrops ?: 0
                    val s2 = totals.find { it.playerName == p2Name }?.totalDrops ?: 0
                    val s3 = totals.find { it.playerName == p3Name }?.totalDrops ?: 0
                    val s4 = totals.find { it.playerName == p4Name }?.totalDrops ?: 0

                    // here you could use these scores if you want,
                    // but currently you also track scores in playerScores
                    // we'll just refresh UI from playerScores
                    runOnUiThread {
                        updateScoreboard()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching scoreboard", e)
                runOnUiThread {
                    scoreboardRows.forEach { row ->
                        row.rank.text = "--"
                        row.color.text = ""
                        row.name.text = "Error"
                        row.tileNumber.text = ""
                        row.tileName.text = ""
                        row.drops.text = ""
                        row.container.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateScoreboard() {
        data class RowData(
            val originalIndex: Int,
            val displayKey: String,
            val name: String,
            val nickname: String?,
            val color: String,
            val drops: Int,
            val position: Int,
            val tileName: String
        )

        fun crownForRank(rank: Int): String {
            return when (rank) {
                1 -> "👑"
                2 -> "🥈"
                3 -> "🥉"
                else -> "⚠️"
            }
        }

        // Determine how many rows to render using whichever source is largest (profiles, names, or count)
        val inferredCount = maxOf(
            playerCount,
            currentGameProfiles.size,
            playerNames.size,
            playerScores.size
        ).coerceAtMost(scoreboardRows.size)

        val rows = (0 until inferredCount).map { idx ->
            val displayKey = playerNames.getOrNull(idx) ?: "Player ${idx + 1}"
            val profile = currentGameProfiles.getOrNull(idx)
            val baseName = profile?.name ?: displayKey
            val nickname = profile?.nickname
            val drops = playerScores[displayKey] ?: 0
            val position = playerPositions[displayKey] ?: 1
            val tileName = gameEngine.tiles
                .getOrNull((position - 1).coerceAtLeast(0))
                ?.name
                ?.toString()
                ?: ""
            val color = playerColors.getOrNull(idx) ?: "red"

            RowData(
                originalIndex = idx,
                displayKey = displayKey,
                name = baseName,
                nickname = nickname,
                color = color,
                drops = drops,
                position = position,
                tileName = tileName
            )
        }
            .sortedWith(compareByDescending<RowData> { it.drops }.thenBy { it.originalIndex })

        // Dense ranking: ties share the same rank, next distinct drops get +1
        var nextRank = 1
        var lastDrops: Int? = null

        scoreboardRows.forEach { row ->
            row.container.visibility = View.GONE
        }

        rows.forEachIndexed { idx, row ->
            if (idx >= scoreboardRows.size) return@forEachIndexed
            val viewRow = scoreboardRows[idx]

            val rankToShow = if (lastDrops != null && row.drops == lastDrops) {
                nextRank - 1
            } else {
                nextRank
            }
            if (lastDrops == null || row.drops != lastDrops) {
                nextRank += 1
            }
            lastDrops = row.drops

            val nameText = formatNameMultiline(row.name, row.nickname)

            viewRow.rank.text = crownForRank(rankToShow)
            viewRow.color.text = getColorEmoji(row.color)
            viewRow.name.text = nameText
            viewRow.tileNumber.text = "Tile ${row.position}"
            viewRow.tileName.text = row.tileName.ifBlank { "-" }
            viewRow.drops.text = row.drops.toString()
            viewRow.container.visibility = View.VISIBLE

            // Tap a row to open history for that player (if profile is known)
            val profile = currentGameProfiles.getOrNull(row.originalIndex)
            viewRow.container.setOnClickListener {
                if (profile != null) {
                    openHistoryForProfile(
                        profileId = profile.playerId,
                        profileName = profile.nickname.ifBlank { profile.name }
                    )
                } else {
                    Toast.makeText(this, "No profile linked for this player", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Saved game helpers
    // ------------------------------------------------------------------------

    private fun buildSavedGameSnapshot(label: String): SavedGame? {
        // Allow saving even if gameActive flag dropped, as long as we have players/scores
        val hasPlayers = playerCount > 0 && playerNames.isNotEmpty()
        val hasScores = playerScores.isNotEmpty()
        if (!hasPlayers && !hasScores) return null

        val inferredCount = listOf(
            playerCount,
            currentGameProfileIds.size,
            currentGameProfiles.size,
            playerScores.size,
            playerPositions.size
        ).maxOrNull()?.coerceIn(1, playerNames.size) ?: playerNames.size

        return runCatching {
            val namesJson = JSONArray().apply {
                playerNames.take(inferredCount).forEach { put(it) }
            }
            val colorsJson = JSONArray().apply {
                playerColors.take(inferredCount).forEach { put(it) }
            }
            val profileIdsJson = JSONArray().apply {
                currentGameProfileIds.take(inferredCount).forEach { put(it) }
            }
            val positionsJson = JSONObject().apply {
                playerPositions.forEach { (name, pos) -> put(name, pos) }
            }
            val scoresJson = JSONObject().apply {
                playerScores.forEach { (name, score) -> put(name, score) }
            }

            SavedGame(
                gameId = currentGameId.toString(),
                playerCount = inferredCount,
                currentPlayer = currentPlayer,
                playWithTwoDice = playWithTwoDice,
                playerNames = namesJson.toString(),
                playerColors = colorsJson.toString(),
                currentGameProfileIds = profileIdsJson.toString(),
                playerPositions = positionsJson.toString(),
                playerScores = scoresJson.toString(),
                lastDice1 = lastDice1,
                lastDice2 = lastDice2,
                lastAvg = lastAvg,
                lastTileName = lastTile?.name,
                lastTileType = lastTile?.type?.name,
                lastChanceCardNumber = lastChanceCard?.number,
                lastChanceCardText = lastChanceCard?.description,
                waitingForCoin = waitingForCoinPlacement,
                testModeEnabled = testModeEnabled,
                testModeType = testModeType,
                label = label
            )
        }.getOrElse {
            Log.e(TAG, "Failed to build saved game snapshot", it)
            null
        }
    }

    private fun saveCurrentGameSnapshot(reason: String = "manual", labelOverride: String? = null, onDone: (() -> Unit)? = null) {
        val label = (labelOverride ?: defaultSaveLabel()).ifBlank { defaultSaveLabel() }
        val snapshot = buildSavedGameSnapshot(label) ?: return
        mainScope.launch(Dispatchers.IO) {
            runCatching {
                savedGameDao.upsert(snapshot)
            }.onSuccess {
                Log.d(TAG, "Saved game snapshot (${snapshot.savedGameId}) reason=$reason")
                withContext(Dispatchers.Main) { onDone?.invoke() }
            }.onFailure {
                Log.e(TAG, "Failed to save game snapshot", it)
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Save failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun loadLatestSavedGame(onLoaded: (SavedGame?) -> Unit) {
        mainScope.launch(Dispatchers.IO) {
            val saved = runCatching { savedGameDao.getLatest() }.getOrNull()
            withContext(Dispatchers.Main) {
                onLoaded(saved)
            }
        }
    }

    private fun consumeSavedGameIntent(savedGameId: String) {
        mainScope.launch(Dispatchers.IO) {
            val saved = runCatching { savedGameDao.getById(savedGameId) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (saved != null) {
                    saved.gameId?.toLongOrNull()?.let { currentGameId = it }
                    applySavedGame(saved)
                    Toast.makeText(this@MainActivity, "Saved game loaded", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Saved game not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun defaultSaveLabel(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val players = playerNames.take(playerCount).joinToString(", ").ifBlank { "Players" }
        return "$players • $timestamp"
    }

    private fun applySavedGame(saved: SavedGame) {
        runCatching {
            val namesArray = JSONArray(saved.playerNames)
            val colorsArray = JSONArray(saved.playerColors)
            val profileIdsArray = JSONArray(saved.currentGameProfileIds)
            val positionsJson = JSONObject(saved.playerPositions)
            val scoresJson = JSONObject(saved.playerScores)

            playerNames.clear()
            playerStreaks.clear()
            repeat(namesArray.length()) { idx ->
                playerNames.add(namesArray.optString(idx, "Player ${idx + 1}"))
            }

            playerColors.clear()
            repeat(colorsArray.length()) { idx ->
                playerColors.add(colorsArray.optString(idx, "red"))
            }

            currentGameProfileIds.clear()
            repeat(profileIdsArray.length()) { idx ->
                val id = profileIdsArray.optString(idx, "")
                currentGameProfileIds.add(id)
            }

            playerPositions.clear()
            positionsJson.keys().forEach { key ->
                playerPositions[key] = positionsJson.optInt(key, 1)
            }

            playerScores.clear()
            scoresJson.keys().forEach { key ->
                playerScores[key] = scoresJson.optInt(key, 0)
            }

            playerCount = playerNames.size
            currentPlayer = saved.currentPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))
            playWithTwoDice = saved.playWithTwoDice
            lastDice1 = saved.lastDice1
            lastDice2 = saved.lastDice2
            lastAvg = saved.lastAvg
            lastTile = gameEngine.tiles.firstOrNull { it.name == saved.lastTileName }
            lastChanceCard = saved.lastChanceCardNumber?.let { num ->
                ChanceCard(number = num, description = saved.lastChanceCardText ?: "", effect = 0)
            }
            waitingForCoinPlacement = saved.waitingForCoin
            testModeEnabled = saved.testModeEnabled
            testModeType = saved.testModeType

            gameActive = true
            tvLastEvent.text = "Resumed saved game"
            updateScoreboard()
            syncSessionState()
        }.onFailure {
            Log.e(TAG, "Failed to apply saved game", it)
            Toast.makeText(this, "Could not restore saved game", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------------
    //  Session + Cloudie helpers (non-Android modules)
    // ------------------------------------------------------------------------

    private fun mapTileTypeForSession(engineType: earth.lastdrop.app.TileType, isFinish: Boolean): SessionTileType {
        if (isFinish) return SessionTileType.FINISH
        return when (engineType) {
            TileType.START -> SessionTileType.START
            TileType.NORMAL -> SessionTileType.SAFE
            TileType.CHANCE -> SessionTileType.SAFE
            TileType.BONUS -> SessionTileType.BONUS
            TileType.PENALTY -> SessionTileType.DANGER
            TileType.DISASTER -> SessionTileType.DANGER
            TileType.WATER_DOCK -> SessionTileType.WATER
            TileType.SUPER_DOCK -> SessionTileType.WATER
            else -> SessionTileType.SAFE
        }
    }

    private fun buildSessionTiles(): List<SessionTileType> {
        val tiles = gameEngine.tiles
        val lastIndex = tiles.lastIndex
        return tiles.mapIndexed { idx, tile -> mapTileTypeForSession(tile.type, idx == lastIndex) }
    }

    private fun playerIdForIndex(index: Int): String {
        return currentGameProfileIds.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: playerNames.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: "player_$index"
    }

    private fun playerIdForName(name: String): String {
        val idx = playerNames.indexOfFirst { it == name }
        return if (idx >= 0) playerIdForIndex(idx) else name
    }

    private fun buildSessionPlayers(): List<PlayerInfo> {
        val count = playerCount.coerceAtLeast(playerNames.size)
        return (0 until count).map { idx ->
            val profile = currentGameProfiles.getOrNull(idx)
            PlayerInfo(
                id = profile?.playerId ?: playerIdForIndex(idx),
                name = profile?.name ?: playerNames.getOrNull(idx) ?: "Player ${idx + 1}",
                nickname = profile?.nickname,
                colorHex = playerColors.getOrNull(idx) ?: profile?.avatarColor ?: "00D4FF",
                isAI = profile?.isAI == true,
                isGuest = profile?.isGuest == true
            )
        }
    }

    private fun syncSessionState() {
        val tiles = buildSessionTiles()
        sessionManager = GameSessionManager(tiles)
        val players = buildSessionPlayers()
        sessionManager.setPlayers(players)
        players.forEachIndexed { idx, p ->
            val pos1Based = playerPositions[playerNames.getOrNull(idx) ?: p.name] ?: 1
            sessionManager.setPosition(p.id, (pos1Based - 1).coerceAtLeast(0))
        }
        sessionManager.setCurrentPlayerIndex(currentPlayer.coerceIn(0, (players.size - 1).coerceAtLeast(0)))
    }

    private fun updateSessionAfterMove(playerIndex: Int, newPosition: Int, nextPlayerIndex: Int) {
        if (!::sessionManager.isInitialized) return
        val playerId = playerIdForIndex(playerIndex)
        sessionManager.setPosition(playerId, (newPosition - 1).coerceAtLeast(0))
        sessionManager.setCurrentPlayerIndex(nextPlayerIndex)
    }

    private fun updateSessionAfterUndo(playerIndex: Int, position: Int) {
        if (!::sessionManager.isInitialized) return
        val playerId = playerIdForIndex(playerIndex)
        sessionManager.setPosition(playerId, (position - 1).coerceAtLeast(0))
        sessionManager.setCurrentPlayerIndex(playerIndex)
    }

    private fun buildMoveContextForIndex(
        playerIndex: Int,
        fromPosition: Int,
        toPosition: Int,
        dice: Int,
        tileType: SessionTileType,
        engineTileType: TileType? = null,
        tileName: String? = null,
        scoreDelta: Int? = null,
        chanceCardDescription: String? = null,
        chanceCardEffect: Int? = null
    ): earth.lastdrop.app.game.session.MoveContext? {
        if (!::sessionManager.isInitialized) return null
        val state = sessionManager.currentState()
        val player = state.players.getOrNull(playerIndex) ?: return null
        return earth.lastdrop.app.game.session.MoveContext(
            player = player,
            fromTile = (fromPosition - 1).coerceAtLeast(0),
            toTile = (toPosition - 1).coerceAtLeast(0),
            diceValue = dice,
            tileType = tileType,
            engineTileType = engineTileType,
            tileName = tileName,
            scoreDelta = scoreDelta,
            chanceCardDescription = chanceCardDescription,
            chanceCardEffect = chanceCardEffect
        )
    }

    private fun emitCloudieLine(eventType: CloudieEventType, moveContext: earth.lastdrop.app.game.session.MoveContext?) {
        if (!::cloudieBrain.isInitialized || !::sessionManager.isInitialized) return
        val state = buildCloudieState() ?: return
        val line = cloudieBrain.generate(CloudieRequest(eventType, state, moveContext)).line
        showCloudieLine(line)
    }

    private fun buildCloudieState(): earth.lastdrop.app.game.session.GameState? {
        if (!::sessionManager.isInitialized) return null
        val base = sessionManager.currentState()
        return base.copy(
            scoreLeaderIds = computeScoreLeaderIds(),
            hotStreakPlayerIds = computeHotStreakIds(),
            trailingIds = computeTrailingIds(),
            comebackPlayerIds = computeComebackIds(),
            scoreGaps = computeScoreGaps()
        )
    }

    private fun computeScoreLeaderIds(): List<String> {
        if (playerScores.isEmpty()) return emptyList()
        val maxScore = playerScores.values.maxOrNull() ?: return emptyList()
        return playerScores.filter { it.value == maxScore }
            .keys
            .map { name -> playerIdForName(name) }
    }

    private fun computeHotStreakIds(): List<String> {
        val hot = playerStreaks.filterValues { it >= 2 }.keys
        return hot.map { name -> playerIdForName(name) }
    }

    private fun computeTrailingIds(): List<String> {
        if (playerScores.isEmpty()) return emptyList()
        val minScore = playerScores.values.minOrNull() ?: return emptyList()
        return playerScores.filter { it.value == minScore }
            .keys
            .map { name -> playerIdForName(name) }
    }

    private fun computeScoreGaps(): Map<String, Int> {
        if (playerScores.isEmpty()) return emptyMap()
        val maxScore = playerScores.values.maxOrNull() ?: return emptyMap()
        return playerScores.mapValues { (_, score) -> maxScore - score }
            .mapKeys { (name, _) -> playerIdForName(name) }
    }

    private fun computeComebackIds(): List<String> {
        val gaps = computeScoreGaps()
        if (gaps.isEmpty()) return emptyList()
        val recentGainers = playerStreaks.filterValues { it >= 1 }.keys.map { playerIdForName(it) }
        return recentGainers.filter { gaps[it] != null && gaps[it]!! in 1..5 }
    }

    private fun showCloudieLine(line: String) {
        val now = System.currentTimeMillis()
        val isDuplicate = line == lastCloudieLine && (now - lastCloudieTimestampMs) < 4000
        if (isDuplicate) return

        lastCloudieLine = line
        lastCloudieTimestampMs = now

        runOnUiThread {
            tvCloudieBanner.text = line
            tvCloudieBanner.visibility = View.VISIBLE
            runCatching {
                val fade = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.cloudie_fade_in)
                val pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.cloudie_pulse)
                tvCloudieBanner.startAnimation(fade)
                tvCloudieBanner.startAnimation(pulse)
            }
        }
        if (voiceEnabled) {
            voiceService?.speak(line)
        }
        appendTestLog("☁️ $line")
    }



    // ------------------------------------------------------------------------
    //  Helper functions
    // ------------------------------------------------------------------------

    private fun formatProfileDisplayName(name: String?, nickname: String?, fallback: String): String {
        val base = name?.takeIf { it.isNotBlank() } ?: fallback
        val nick = nickname?.takeIf { it.isNotBlank() }
        return if (nick != null && nick != base) "$base ($nick)" else base
    }

    private fun formatNameMultiline(name: String?, nickname: String?): String {
        val base = name?.takeIf { it.isNotBlank() } ?: "Player"
        val nick = nickname?.takeIf { it.isNotBlank() }
        return if (nick != null && nick != base) "$base\n($nick)" else base
    }

    private fun getColorEmoji(color: String): String {
        return when (color.uppercase()) {
            "FF0000", "RED" -> "🔴"
            "00FF00", "GREEN" -> "🟢"
            "0000FF", "BLUE" -> "🔵"
            "FFFF00", "YELLOW" -> "🟡"
            else -> when (color.lowercase()) {
                "red" -> "🔴"
                "green" -> "🟢"
                "blue" -> "🔵"
                "yellow" -> "🟡"
                else -> "⚪"
            }
        }
    }

    private fun openHistoryForProfile(profileId: String?, profileName: String?) {
        val intent = Intent(this, GameHistoryActivity::class.java)
        profileId?.let { intent.putExtra("profileId", it) }
        profileName?.let { intent.putExtra("profileName", it) }
        startActivity(intent)
    }

    // ------------------------------------------------------------------------
    //  Undo logic
    // ------------------------------------------------------------------------

    private fun startUndoWindow() {
        if (previousRoll == null) {
            Toast.makeText(this, "Nothing to undo yet", Toast.LENGTH_SHORT).show()
            return
        }

        undoTimer?.cancel()
        btnUndo.text = "Confirm Undo"
        btnUndo.setBackgroundColor(0xFFFF6B6B.toInt()) // Red highlight
        undoProgressBar.visibility = View.VISIBLE
        undoProgressBar.max = 5
        undoProgressBar.progress = 5

        undoTimer = mainScope.launch {
            var seconds = 5
            while (seconds > 0) {
                tvUndoStatus.text = "Tap CONFIRM to undo ($seconds s)"
                btnUndo.text = "Confirm ($seconds s)"
                undoProgressBar.progress = seconds
                delay(1000)
                seconds--
            }
            tvUndoStatus.text = "Undo expired"
            btnUndo.text = "Undo Last Move"
            btnUndo.setBackgroundColor(0xFF6200EE.toInt()) // Reset color
            undoProgressBar.visibility = View.GONE
        }
    }

    private fun confirmUndo() {
        val prev = previousRoll ?: run {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }

        // Restore the player who made the last roll
        val playerName = playerNames[previousPlayerIndex]

        // Cancel any pending coin placement operations (undo before coin placed)
        if (waitingForCoinPlacement) {
            waitingForCoinPlacement = false
            uiUpdateManager.cancelCountdown()
            esp32ErrorHandler.cancelCoinPlacementTimeout()
            appendTestLog("🔄 Undo: Cancelled pending coin placement")
            
            runOnUiThread {
                tvCoinTimeout.visibility = View.GONE
                tvCoinTimeout.text = ""
            }
        }

        // Revert position and score
        playerPositions[playerName] = previousPosition
        playerScores[playerName] = previousScore
        playerStreaks[playerName] = 0

        // Set current player back to the one who just rolled (so they play again)
        currentPlayer = previousPlayerIndex
        updateSessionAfterUndo(previousPlayerIndex, previousPosition)
        
        // Update turn indicator
        updateTurnIndicatorDisplay()
        
        // Send undo command to ESP32 (works in Production and Test Mode 1)
        sendUndoToESP32(previousPlayerIndex, playerPositions[playerName] ?: 1, previousPosition)

        // Clear ALL roll data (critical: prevents server from showing stale data)
        lastRoll = null
        lastDice1 = null
        lastDice2 = null
        lastAvg = null
        lastTile = null
        lastChanceCard = null
        
        // Clear previous roll to prevent double undo
        previousRoll = null

        val undoMessage = if (testModeEnabled) {
            "🔄 Undo: $playerName returns to position $previousPosition"
        } else {
            "Undo applied - $playerName returns to position $previousPosition"
        }
        
        tvLastEvent.text = undoMessage
        tvUndoStatus.text = "Undo applied"
        btnUndo.text = "Undo Last Move"
        btnUndo.setBackgroundColor(0xFF6200EE.toInt()) // Reset color
        undoProgressBar.visibility = View.GONE
        undoTimer?.cancel()

        Log.d(TAG, "Undo confirmed: $playerName -> pos=$previousPosition, score=$previousScore")
        
        if (testModeEnabled) {
            appendTestLog("✅ Undo applied: $playerName → Tile $previousPosition, Score $previousScore")
        }

        // Update UI
        updateScoreboard()

        // Push undo state to server (with cleared roll data)
        pushUndoStateToServer()

        val undoCtx = buildMoveContextForIndex(
            playerIndex = previousPlayerIndex,
            fromPosition = previousPosition,
            toPosition = previousPosition,
            dice = prev,
            tileType = mapTileTypeForSession(
                gameEngine.tiles.getOrNull((previousPosition - 1).coerceAtLeast(0))?.type
                    ?: TileType.NORMAL,
                previousPosition > gameEngine.tiles.size
            ),
            engineTileType = gameEngine.tiles.getOrNull((previousPosition - 1).coerceAtLeast(0))?.type,
            tileName = gameEngine.tiles.getOrNull((previousPosition - 1).coerceAtLeast(0))?.name,
            scoreDelta = null
        )
        emitCloudieLine(CloudieEventType.UNDO, undoCtx)
    }

    private fun pushUndoStateToServer() {
        apiManager.pushUndoState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerPositions = playerPositions,
            playerScores = playerScores,
            playerCount = playerCount
        )
    }

    // ------------------------------------------------------------------------
    //  Permission helper
    // ------------------------------------------------------------------------

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

    // Permission callback
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
                connectDice()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required to connect to BLDice",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (
            LiveServerUiHelper.handleCameraPermissionResult(
                activity = this,
                requestCode = requestCode,
                expectedRequestCode = REQUEST_CAMERA_PERMISSION,
                grantResults = grantResults,
                onGranted = { LiveServerUiHelper.launchQrScanner(this) }
            )
        ) {
            // handled in helper
        }
    }

    // ------------------------------------------------------------------------
    //  Test Mode Functions
    // ------------------------------------------------------------------------

    private fun toggleTestMode() {
        Log.d(TAG, "toggleTestMode called, current state: $testModeEnabled")
        
        // Show test mode selection dialog
        val options = arrayOf(
            "Production",
            "Test Mode 1",
            "Test Mode 2"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Select Test Mode")
            .setSingleChoiceItems(options, testModeType) { dialog, which ->
                when (which) {
                    0 -> {
                        // Production Mode
                        testModeEnabled = false
                        testModeType = 0
                        btnTestMode.text = "Production"
                        btnTestMode.setBackgroundColor(0xFF6200EE.toInt())
                        layoutDiceButtons.visibility = View.GONE
                        
                        // Hide test console
                        tvTestLogTitle.visibility = View.GONE
                        scrollTestLog.visibility = View.GONE
                        btnClearLog.visibility = View.GONE
                        
                        // Clear log
                        tvTestLog.text = ""
                        
                        appendTestLog("🔴 Production Mode - Using BLDice + ESP32")
                        Toast.makeText(this, "Production Mode: BLDice + ESP32 + live.html", Toast.LENGTH_SHORT).show()
                        
                        // Try to connect ESP32
                        if (!esp32Connected) {
                            connectESP32()
                        }
                    }
                    1 -> {
                        // Test Mode 1: Virtual Dice + ESP32
                        testModeEnabled = true
                        testModeType = 1
                        Log.d(TAG, "Test Mode 1 activated: testModeType=$testModeType, testModeEnabled=$testModeEnabled")
                        btnTestMode.text = "Test Mode 1"
                        btnTestMode.setBackgroundColor(0xFFFF9800.toInt()) // Orange
                        layoutDiceButtons.visibility = View.VISIBLE
                        
                        // Show test console
                        tvTestLogTitle.visibility = View.VISIBLE
                        scrollTestLog.visibility = View.VISIBLE
                        btnClearLog.visibility = View.VISIBLE
                        
                        // Clear log
                        tvTestLog.text = ""
                        
                        appendTestLog("🟠 Test Mode 1 - Virtual Dice + ESP32 Board")
                        Toast.makeText(this, "Test Mode 1: Simulated dice with ESP32 board", Toast.LENGTH_LONG).show()
                        
                        // Try to connect ESP32
                        if (!esp32Connected) {
                            connectESP32()
                        }
                    }
                    2 -> {
                        // Test Mode 2: Android + live.html only (no ESP32)
                        testModeEnabled = true
                        testModeType = 2
                        Log.d(TAG, "Test Mode 2 activated: testModeType=$testModeType, testModeEnabled=$testModeEnabled")
                        btnTestMode.text = "Test Mode 2"
                        btnTestMode.setBackgroundColor(0xFF4CAF50.toInt()) // Green
                        layoutDiceButtons.visibility = View.VISIBLE
                        
                        // Show test console
                        tvTestLogTitle.visibility = View.VISIBLE
                        scrollTestLog.visibility = View.VISIBLE
                        btnClearLog.visibility = View.VISIBLE
                        
                        // Clear log
                        tvTestLog.text = ""
                        
                        appendTestLog("🟢 Test Mode 2 - Virtual Dice + live.html (No ESP32)")
                        Toast.makeText(this, "Test Mode 2: Simulated dice, live.html only", Toast.LENGTH_LONG).show()
                        
                        // Disconnect ESP32 if connected
                        if (esp32Connected) {
                            disconnectESP32()
                            appendTestLog("ESP32 disconnected for Test Mode 2")
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        Log.d(TAG, "toggleTestMode dialog shown")
    }

    /**
     * Show QR code for spectators to scan and view live.html
     * Displays session ID and board ID (if connected to ESP32)
     */
    private fun showSpectatorQR() {
        val boardId = boardConnectionController.connectedDevice?.name
        
        LiveQRGenerator.showQRCodeDialog(
            context = this,
            sessionId = SESSION_ID,
            boardId = boardId
        )
        
        Log.d(TAG, "Spectator QR code shown - Session: ${SESSION_ID.substring(0, 8)}..., Board: $boardId")
        
        if (testModeEnabled) {
            appendTestLog("📱 QR code displayed for session ${SESSION_ID.substring(0, 8)}...")
        }
    }

    private fun simulateDiceRoll(diceValue: Int) {
        Log.d(TAG, "simulateDiceRoll called with value: $diceValue, testModeEnabled: $testModeEnabled, testModeType: $testModeType")
        
        if (!testModeEnabled) {
            Toast.makeText(this, "Enable Test Mode first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (diceValue !in 1..6) {
            Toast.makeText(this, "Invalid dice value: $diceValue", Toast.LENGTH_SHORT).show()
            return
        }

        when (testModeType) {
            1 -> {
                // Test Mode 1: Manual dice value for ESP32 testing
                // Send to ESP32 and wait for ESP32 response (don't process locally)
                appendTestLog("🎲 Manual roll: $diceValue (sending to ESP32)")
                Log.d(TAG, "Test Mode 1 - Sending dice value $diceValue to ESP32")
                
                if (!esp32Connected) {
                    Toast.makeText(this, "ESP32 not connected!", Toast.LENGTH_SHORT).show()
                    appendTestLog("❌ ESP32 not connected - cannot process roll")
                    return
                }
                
                // Save dice value for server reporting
                lastDice1 = diceValue
                lastDice2 = null
                lastAvg = diceValue
                lastModeTwoDice = false
                
                // Send roll to ESP32 - it will respond with roll_processed or coin_placed
                val safeIndex = currentPlayer.coerceIn(0, playerCount - 1)
                val playerName = playerNames[safeIndex]
                val currentPos = playerPositions[playerName] ?: 1
                
                // Save state BEFORE sending to ESP32 (for undo)
                previousPlayerIndex = safeIndex
                previousPosition = currentPos
                previousScore = playerScores[playerName] ?: 10
                previousRoll = diceValue
                previousDice1 = diceValue
                previousDice2 = null
                previousAvg = diceValue
                
                sendRollToESP32(safeIndex, diceValue, currentPos, currentPos + diceValue)
                
                appendTestLog("📤 Sent to ESP32, waiting for response...")
            }
            2 -> {
                // Test Mode 2: Manual dice value for Android/web testing (no ESP32)
                appendTestLog("🎲 Manual roll: $diceValue")
                Log.d(TAG, "Test Mode 2 - Simulating dice value: $diceValue")
                
                // Simulate dice stable callback
                onDiceStable(0, diceValue)
            }
            else -> {
                Toast.makeText(this, "Unknown test mode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun appendTestLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val currentLog = tvTestLog.text.toString()
            tvTestLog.text = "[$timestamp] $message\n$currentLog"
        }
    }

    // ------------------------------------------------------------------------
    //  ESP32 BLE Integration
    // ------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun connectESP32() {
        if (esp32Connected) {
            Log.d(TAG, "ESP32 already connected")
            return
        }

        if (!ensureBlePermissions()) {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Starting board scan...")
        appendTestLog("🔍 Scanning for boards...")
        
        // Use new BoardScanManager for multi-board support
        boardScanManager.startScan()
    }

    @SuppressLint("MissingPermission")
    private fun stopESP32Scan() {
        // Use BoardScanManager
        boardScanManager.stopScan()
    }

    /**
     * Watchdog handler for heartbeat loss. Cleans up GATT, suppresses duplicate UI noise,
     * and kicks off a fresh scan after a short delay.
     */
    private fun handleHeartbeatLoss() {
        appendTestLog("💔 Board heartbeat lost - reconnecting")

        watchdogReconnectInProgress = true
        esp32ReconnectAttempts = 0

        runOnUiThread {
            Toast.makeText(this, "Board unresponsive. Reconnecting...", Toast.LENGTH_LONG).show()
            btnConnectBoard.text = "🎮  Reconnecting..."
            btnConnectBoard.isEnabled = false
        }

        disconnectESP32()

        // Give BLE stack a moment before resuming discovery
        mainScope.launch {
            delay(1000)
            connectESP32()
            watchdogReconnectInProgress = false
        }
    }
    
    /**
     * Show board selection dialog when multiple boards are found
     */
    private fun showBoardSelectionDialog(boards: List<BluetoothDevice>) {
        runOnUiThread {
            BoardSelectionDialog.show(
                context = this,
                boards = boards,
                preferencesManager = boardPreferencesManager,
                onBoardSelected = { device ->
                    appendTestLog("✅ Selected: ${device.name}")
                    connectToESP32Device(device)
                },
                onRescan = {
                    appendTestLog("🔄 Rescanning...")
                    boardScanManager.rescan()
                },
                onManageBoards = {
                    showSavedBoardsDialog()
                }
            )
        }
    }
    
    /**
     * Show saved boards management dialog
     */
    private fun showSavedBoardsDialog() {
        BoardSelectionDialog.showSavedBoardsDialog(
            context = this,
            preferencesManager = boardPreferencesManager,
            onBoardSelected = { boardId, macAddress ->
                appendTestLog("📱 Reconnecting to $boardId...")
                // TODO: Reconnect to saved board by MAC
                Toast.makeText(this, "Reconnecting to $boardId", Toast.LENGTH_SHORT).show()
            },
            onForgetBoard = { boardId ->
                boardPreferencesManager.forgetBoard(boardId)
                Toast.makeText(this, "Forgot $boardId", Toast.LENGTH_SHORT).show()
                appendTestLog("🗑️ Forgot board: $boardId")
            },
            onEditNickname = { boardId ->
                val currentNickname = boardPreferencesManager.getSavedBoard(boardId)?.nickname
                BoardSelectionDialog.showNicknameDialog(
                    context = this,
                    currentNickname = currentNickname,
                    boardId = boardId,
                    onNicknameSaved = { nickname ->
                        boardPreferencesManager.updateBoardNickname(boardId, nickname)
                        Toast.makeText(this, "Saved nickname: $nickname", Toast.LENGTH_SHORT).show()
                        appendTestLog("✏️ Updated nickname for $boardId: $nickname")
                    }
                )
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectToESP32Device(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ESP32: ${device.name} (${device.address})")
        appendTestLog("📡 Connecting to ${device.name}...")
        
        // Check bonding state before connecting
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                // Already paired, proceed with connection
                Log.d(TAG, "Device already bonded, connecting...")
                appendTestLog("✓ Already paired, connecting...")
                proceedWithESP32Connection(device)
            }
            BluetoothDevice.BOND_NONE -> {
                // Not paired, initiate pairing
                Log.d(TAG, "Device not bonded, initiating pairing...")
                appendTestLog("🔐 Initiating pairing...")
                pairingDevice = device
                runOnUiThread {
                    Toast.makeText(this, "Pairing with ${device.name}...", Toast.LENGTH_SHORT).show()
                }
                val bondResult = device.createBond()
                Log.d(TAG, "createBond() result: $bondResult")
                if (!bondResult) {
                    appendTestLog("❌ Failed to start pairing")
                    runOnUiThread {
                        Toast.makeText(this, "Failed to start pairing", Toast.LENGTH_SHORT).show()
                    }
                    pairingDevice = null
                }
            }
            BluetoothDevice.BOND_BONDING -> {
                // Pairing in progress, wait
                Log.d(TAG, "Pairing already in progress...")
                appendTestLog("⏳ Pairing in progress...")
                pairingDevice = device
                runOnUiThread {
                    Toast.makeText(this, "Pairing in progress...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun proceedWithESP32Connection(device: BluetoothDevice) {
        Log.d(TAG, "Proceeding with ESP32 connection: ${device.address}")
        boardConnectionController.connect(device)
    }

    @SuppressLint("MissingPermission")
    private fun sendToESP32(jsonString: String) {
        if (!esp32Connected) {
            Log.w(TAG, "ESP32 not connected, cannot send: $jsonString")
            return
        }

        Log.d(TAG, "ESP32 → $jsonString")
        boardConnectionController.send(jsonString)
    }

    private fun sendPairCommandToESP32() {
        if (testModeEnabled) return // Skip when board is bypassed
        if (!esp32Connected) {
            Log.w(TAG, "ESP32 not ready for pairing command")
            return
        }

        val pairPayload = JSONObject().apply {
            put("command", "pair")
            put("password", ESP32_PAIR_PIN)
        }

        appendTestLog("🔐 Sending pair command to board")
        sendToESP32(pairPayload.toString())
    }

    private fun sendConfigToESP32() {
        if (testModeEnabled) return // Skip ESP32 in test mode
        
        val colors = playerColors.take(playerCount).map { color ->
            when (color) {
                "red" -> "FF0000"
                "green" -> "00FF00"
                "blue" -> "0000FF"
                "yellow" -> "FFFF00"
                else -> "FFFFFF"
            }
        }

        val config = org.json.JSONObject().apply {
            put("command", "config")
            put("playerCount", playerCount)
            put("colors", org.json.JSONArray(colors))
        }

        sendToESP32(config.toString())
    }
    
    /**
     * Show board settings dialog to change ESP32 password and nickname
     */
    private fun showBoardSettings() {
        if (!esp32Connected) {
            Toast.makeText(this, "Please connect to a board first", Toast.LENGTH_SHORT).show()
            return
        }
        
        boardConnectionController.connectedDevice?.let { device ->
            val boardId = device.name ?: "Unknown Board"
            val savedBoard = boardPreferencesManager.getSavedBoard(boardId)
            val currentNickname = savedBoard?.nickname ?: boardId
            val currentPassword = if (savedBoard?.passwordHash != null) "saved" else null
            
            BoardSettingsDialog.show(
                context = this,
                currentNickname = currentNickname,
                currentPassword = currentPassword,
                onSettingsUpdate = { nickname, password ->
                    sendBoardSettingsUpdate(nickname, password)
                }
            )
        }
    }
    
    /**
     * Send update_settings command to ESP32
     */
    private fun sendBoardSettingsUpdate(nickname: String?, password: String?) {
        if (testModeEnabled) {
            Toast.makeText(this, "Board settings update not available in test mode", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!esp32Connected) {
            Toast.makeText(this, "Board not connected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val command = BoardSettingsDialog.generateUpdateCommand(nickname, password)
        sendToESP32(command)
        
        val passwordMask = if (password != null) "***" else "null"
        Log.d(TAG, "Sent board settings update: nickname=$nickname, password=$passwordMask")
        Toast.makeText(this, "Updating board settings...", Toast.LENGTH_SHORT).show()
    }

    private fun sendRollToESP32(playerId: Int, diceValue: Int, currentTile: Int, expectedTile: Int) {
        if (testModeEnabled) {
            // In test mode, skip ESP32 and immediately process the turn
            Log.d(TAG, "Test mode: Skipping ESP32, processing turn immediately")
            return
        }
        
        if (!esp32Connected) {
            Log.w(TAG, "ESP32 not connected, skipping roll command")
            return
        }

        val playerName = if (playerId < playerNames.size) playerNames[playerId] else "Player ${playerId + 1}"
        val color = if (playerId < playerColors.size) playerColors[playerId] else "red"

        val rollCmd = org.json.JSONObject().apply {
            put("command", "roll")
            put("playerId", playerId)
            put("playerName", playerName)
            put("diceValue", diceValue)
            put("currentTile", currentTile)
            put("expectedTile", expectedTile)
            put("color", color)
        }

        sendToESP32(rollCmd.toString())
        waitingForCoinPlacement = true
        
        runOnUiThread {
            tvDiceStatus.text = "Waiting for coin placement on tile $expectedTile..."
            tvCoinTimeout.visibility = View.VISIBLE
            tvCurrentTurn.visibility = View.VISIBLE
            
            // Show turn indicator
            val currentPlayerName = playerNames[currentPlayer]
            val currentPlayerColor = playerColors[currentPlayer]
            uiUpdateManager.updateTurnIndicator(tvCurrentTurn, currentPlayerName, currentPlayerColor)
            
            // Start 30-second countdown for coin placement
            uiUpdateManager.startCountdown(
                textView = tvCoinTimeout,
                totalSeconds = 30,
                prefix = "⏱ Coin placement timeout: ",
                onComplete = {
                    // Countdown complete - now show graceful dialog
                    esp32ErrorHandler.startCoinPlacementTimeout(expectedTile)
                }
            )
        }
    }

    private fun sendUndoToESP32(playerId: Int, fromTile: Int, toTile: Int) {
        // Allow undo in Test Mode 1 (ESP32 testing) and Production
        if (testModeType == 2) {
            // Test Mode 2: No ESP32, undo is local only
            return
        }
        
        if (!esp32Connected) {
            Log.w(TAG, "ESP32 not connected, undo will be local only")
            return
        }

        val undoCmd = org.json.JSONObject().apply {
            put("command", "undo")
            put("playerId", playerId)
            put("fromTile", fromTile)
            put("toTile", toTile)
        }

        sendToESP32(undoCmd.toString())
        
        appendTestLog("📤 Sent undo command to ESP32")
        Log.d(TAG, "Undo command sent to ESP32: Player $playerId, $fromTile → $toTile")
    }

    private fun sendResetToESP32() {
        if (testModeEnabled) return
        if (!esp32Connected) return

        val resetCmd = org.json.JSONObject().apply {
            put("command", "reset")
        }

        sendToESP32(resetCmd.toString())
    }

    private fun handleESP32Event(jsonString: String) {
        try {
            // P4.1: Validate JSON structure
            val structureValidation = esp32StateValidator.validateResponseStructure(jsonString)
            if (!structureValidation.success) {
                Log.e(TAG, "Invalid ESP32 response: ${structureValidation.message}")
                return
            }
            
            val json = org.json.JSONObject(jsonString)
            val event = json.optString("event", "")

            when (event) {
                "roll_processed" -> {
                    // Test Mode 1: ESP32 processed the roll and returns complete game state
                    esp32ErrorHandler.updateHeartbeat()
                    
                    val playerId = json.getInt("playerId")
                    val playerName = playerNames.getOrNull(playerId) ?: "Player ${playerId + 1}"
                    
                    // Extract movement data
                    val movement = json.getJSONObject("movement")
                    val fromTile = movement.getInt("from")
                    val toTile = movement.getInt("to")
                    
                    // Extract tile data
                    val tileObj = json.getJSONObject("tile")
                    val tileName = tileObj.getString("name")
                    val tileType = tileObj.getString("type")
                    
                    // Extract score data
                    val scoreObj = json.getJSONObject("score")
                    val newScore = scoreObj.getInt("new")
                    val scoreChange = scoreObj.getInt("change")
                    
                    // Extract chance card if present
                    var chanceCardText = ""
                    if (json.has("chanceCard")) {
                        val cardObj = json.getJSONObject("chanceCard")
                        val cardNum = cardObj.getInt("number")
                        val cardDesc = cardObj.getString("description")
                        val cardEffect = cardObj.getInt("effect")
                        chanceCardText = "\nChance Card #$cardNum: $cardDesc ($cardEffect)"
                        
                        // Store for live.html
                        lastChanceCard = ChanceCard(cardNum, cardDesc, cardEffect)
                    } else {
                        lastChanceCard = null
                    }
                    
                    // Update local game state
                    playerPositions[playerName] = toTile
                    playerScores[playerName] = newScore
                    
                    // Store for live.html
                    lastTile = gameEngine.tiles.find { it.index == toTile }
                    
                    // Advance turn
                    currentPlayer = (currentPlayer + 1) % playerCount
                    updateTurnIndicatorDisplay()
                    
                    // Update UI
                    runOnUiThread {
                        val eventText = "$playerName: Tile $fromTile → $toTile ($tileName)\n" +
                                       "Score: $newScore ($scoreChange)$chanceCardText"
                        tvLastEvent.text = eventText
                        tvDiceStatus.text = "Waiting for coin placement..."
                        
                        appendTestLog("📥 ESP32 response: $playerName → Tile $toTile, Score: $newScore")
                        
                        updateScoreboard()
                        
                        // Send to server and live.html
                        sendLastRollToServer()
                        pushLiveStateToBoard()
                    }
                    
                    Log.d(TAG, "Roll processed by ESP32: $playerName → Tile $toTile, Score: $newScore")
                }
                
                "coin_placed" -> {
                    val playerId = json.getInt("playerId")
                    val tile = json.getInt("tile")
                    val verified = json.optBoolean("verified", true)
                    
                    // P4.1: Validate coin placement data
                    // Note: We'd need to track expected values; for now just log
                    Log.d(TAG, "Coin placed: Player $playerId at tile $tile, verified=$verified")
                    
                    if (!verified) {
                        Log.w(TAG, "ESP32 reported unverified coin placement")
                        appendTestLog("⚠️ Unverified coin placement at tile $tile")
                    }

                    waitingForCoinPlacement = false
                    
                    // P4.2: Update state sync manager
                    val playerName = playerNames.getOrNull(playerId)
                    if (playerName != null) {
                        val position = playerPositions[playerName] ?: 1
                        val score = playerScores[playerName] ?: 10
                        stateSyncManager.updateLocalState(playerId, position, score)
                    }
                    
                    // Cancel countdown and timeout dialog
                    uiUpdateManager.cancelCountdown()
                    esp32ErrorHandler.cancelCoinPlacementTimeout()
                    
                    // Update heartbeat
                    esp32ErrorHandler.updateHeartbeat()

                    runOnUiThread {
                        tvDiceStatus.text = "Coin placed! ✓"
                        tvCoinTimeout.visibility = View.GONE
                        tvCoinTimeout.text = ""
                        Toast.makeText(this, "Coin detected at tile $tile", Toast.LENGTH_SHORT).show()
                        
                        appendTestLog("✅ Coin placed at tile $tile")

                        // Trigger live.html update
                        pushLiveStateToBoard(
                            eventType = "coin_placed",
                            eventMessage = "Coin placed at tile $tile"
                        )
                    }
                }

                "coin_timeout" -> {
                    val tile = json.getInt("tile")
                    Log.w(TAG, "Coin placement timeout at tile $tile")
                    waitingForCoinPlacement = false
                    
                    // Update heartbeat
                    esp32ErrorHandler.updateHeartbeat()
                    
                    // Cancel countdown
                    uiUpdateManager.cancelCountdown()

                    runOnUiThread {
                        tvCoinTimeout.visibility = View.GONE
                        tvCoinTimeout.text = ""
                        Toast.makeText(this, "Coin placement timeout - continuing anyway", Toast.LENGTH_SHORT).show()
                        pushLiveStateToBoard(
                            eventType = "coin_timeout",
                            eventMessage = "Coin placement timeout at tile $tile"
                        )
                        emitCloudieLine(CloudieEventType.WARNING_TIMEOUT, null)
                    }
                }
                
                "undo_complete" -> {
                    // ESP32 confirmed undo and is waiting for coin at reverted position
                    esp32ErrorHandler.updateHeartbeat()
                    
                    val playerId = json.getInt("playerId")
                    val playerName = playerNames.getOrNull(playerId) ?: "Player ${playerId + 1}"
                    
                    val movement = json.getJSONObject("movement")
                    val fromTile = movement.getInt("from")
                    val toTile = movement.getInt("to")
                    
                    val scoreObj = json.getJSONObject("score")
                    val restoredScore = scoreObj.getInt("restored")
                    
                    Log.d(TAG, "Undo complete: $playerName → Tile $toTile, Score: $restoredScore")
                    
                    // Wait for coin placement at reverted position
                    waitingForCoinPlacement = true
                    
                    runOnUiThread {
                        tvDiceStatus.text = "Undo complete! Place coin at tile $toTile"
                        tvCoinTimeout.visibility = View.VISIBLE
                        
                        appendTestLog("✅ ESP32 undo complete: $playerName → Tile $toTile")
                        
                        Toast.makeText(this, "Undo complete - place coin at tile $toTile", Toast.LENGTH_SHORT).show()
                        
                        // Start countdown for coin placement at reverted position
                        uiUpdateManager.startCountdown(
                            textView = tvCoinTimeout,
                            totalSeconds = 30,
                            prefix = "⏱ Coin placement timeout: ",
                            onComplete = {
                                esp32ErrorHandler.startCoinPlacementTimeout(toTile)
                            }
                        )
                    }
                }

                "misplacement" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    
                    val errors = json.getJSONArray("errors")
                    val errorList = mutableListOf<MisplacementError>()

                    for (i in 0 until errors.length()) {
                        val error = errors.getJSONObject(i)
                        val tile = error.getInt("tile")
                        val issue = error.getString("issue")
                        errorList.add(MisplacementError(tile, issue))
                    }
                    
                    // P4.1: Validate misplacement report
                    val validation = esp32StateValidator.validateMisplacementReport(errorList)

                    runOnUiThread {
                        val message = "Please fix:\n" + errorList.joinToString("\n") { "Tile ${it.tile}: ${it.issue}" }
                        AlertDialog.Builder(this)
                            .setTitle("Coin Misplacement Detected")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                        emitCloudieLine(CloudieEventType.WARNING_MISPLACEMENT, null)
                        pushLiveStateToBoard(
                            eventType = "misplacement",
                            eventMessage = message
                        )
                    }
                }

                "config_complete" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    Log.d(TAG, "ESP32 configuration complete")
                    runOnUiThread {
                        Toast.makeText(this, "ESP32 configured with $playerCount players", Toast.LENGTH_SHORT).show()
                    }
                }

                "settings_updated" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    val newPassword = json.optString("password", "").takeIf { it.isNotEmpty() }
                    val newNickname = json.optString("nickname", "").takeIf { it.isNotEmpty() }
                    val restartRequired = json.optBoolean("restartRequired", false)
                    
                    Log.d(TAG, "ESP32 settings updated - Nickname: $newNickname, Restart: $restartRequired")
                    
                    // Save updated settings to preferences
                    boardConnectionController.connectedDevice?.let { device ->
                        device.name?.let { boardId ->
                            newNickname?.let { nickname ->
                                boardPreferencesManager.updateBoardNickname(boardId, nickname)
                            }
                            if (newPassword != null) {
                                boardPreferencesManager.saveBoard(boardId, device.address, newNickname, newPassword)
                            }
                        }
                    }
                    
                    runOnUiThread {
                        BoardSettingsDialog.showUpdateConfirmation(
                            this,
                            newNickname,
                            newPassword != null,
                            restartRequired
                        )
                    }
                }

                "ready" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    val message = json.optString("message", "ESP32 Ready")
                    Log.d(TAG, "ESP32: $message")
                }
                
                "player_eliminated" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    val playerId = json.getInt("playerId")
                    val playerName = playerNames.getOrNull(playerId) ?: "Player ${playerId + 1}"
                    
                    Log.d(TAG, "Player eliminated: $playerId ($playerName)")
                    
                    runOnUiThread {
                        // Show elimination alert
                        animationEventHandler.showEliminationAlert(
                            playerId = playerId,
                            playerName = playerName,
                            onAnimationComplete = {
                                // Animation complete - update UI status
                                animationEventHandler.clearAnimationStatus(tvDiceStatus)
                                tvDiceStatus.text = "$playerName eliminated!"
                            }
                        )
                        
                        // Update UI status during animation
                        animationEventHandler.updateAnimationStatus(tvDiceStatus, "elimination")
                    }
                }
                
                "winner_declared" -> {
                    esp32ErrorHandler.updateHeartbeat()
                    val winnerId = json.getInt("winnerId")
                    val winnerName = playerNames.getOrNull(winnerId) ?: "Player ${winnerId + 1}"
                    val winnerColor = playerColors.getOrNull(winnerId) ?: "red"
                    
                    Log.d(TAG, "Winner declared: $winnerId ($winnerName)")
                    
                    // Record game results to player profiles
                    recordGameResults(winnerId)
                    updateSessionAfterUndo(winnerId, playerPositions[winnerName] ?: 1)
                    emitCloudieLine(CloudieEventType.WIN_ANIMATION, null)
                    val winCtx = buildMoveContextForIndex(
                        playerIndex = winnerId,
                        fromPosition = playerPositions[winnerName] ?: 1,
                        toPosition = playerPositions[winnerName] ?: 1,
                        dice = lastAvg ?: lastRoll ?: 0,
                        tileType = mapTileTypeForSession(
                            gameEngine.tiles.getOrNull((playerPositions[winnerName] ?: 1) - 1)?.type
                                ?: TileType.NORMAL,
                            (playerPositions[winnerName] ?: 1) > gameEngine.tiles.size
                        )
                    )
                    emitCloudieLine(CloudieEventType.WIN, winCtx)
                    
                    // Disable coin timeout during winner animation
                    esp32ErrorHandler.setWinnerAnimationInProgress(true)
                    
                    runOnUiThread {
                        pushLiveStateToBoard(
                            eventType = "win_animation",
                            eventMessage = "$winnerName celebrating"
                        )
                        // Show winner celebration
                        animationEventHandler.showWinnerCelebration(
                            winnerId = winnerId,
                            winnerName = winnerName,
                            winnerColor = winnerColor,
                            onAnimationComplete = {
                                // Animation complete - re-enable timeouts
                                esp32ErrorHandler.setWinnerAnimationInProgress(false)
                                animationEventHandler.clearAnimationStatus(tvDiceStatus)
                                tvDiceStatus.text = "🏆 $winnerName wins the game!"
                                aiPresenter.onGameEnd(winnerName)
                                
                                // Push final state to live.html
                                pushLiveStateToBoard(
                                    eventType = "winner",
                                    eventMessage = "$winnerName wins"
                                )
                            }
                        )
                        
                        // Update UI status during animation
                        animationEventHandler.updateAnimationStatus(tvDiceStatus, "winner")
                        
                        // Push state to live.html to show winner
                        pushLiveStateToBoard(
                            eventType = "win_animation",
                            eventMessage = "$winnerName celebrating"
                        )
                    }
                }

                else -> {
                    Log.w(TAG, "Unknown ESP32 event: $event")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ESP32 event: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectESP32() {
        reconnectJob?.cancel()  // Cancel any pending reconnect attempts
        uiUpdateManager.cancelCountdown()  // Cancel coin timeout countdown
        esp32ErrorHandler.stopHeartbeatMonitoring()
        stateSyncManager.stopSyncMonitoring()
        boardConnectionController.disconnect()
        esp32Connected = false
        waitingForCoinPlacement = false
        gameActive = false  // Mark game as inactive
        
        runOnUiThread {
            tvCoinTimeout.visibility = View.GONE
            tvCoinTimeout.text = ""
        }
        
        Log.d(TAG, "ESP32 disconnected and cleaned up")
    }

    // Auto-reconnection dialog
    private fun showReconnectDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("ESP32 Connection Lost")
                .setMessage("Unable to reconnect to physical board. Please:\n\n" +
                    "1. Check ESP32 power\n" +
                    "2. Retry connection\n" +
                    "3. Or continue in Test Mode 2 (Android only)")
                .setPositiveButton("Retry Now") { _, _ ->
                    esp32ReconnectAttempts = 0
                    connectESP32()
                }
                .setNegativeButton("Test Mode 2") { _, _ ->
                    enableTestMode2()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun registerBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (gameActive && playerScores.isNotEmpty()) {
                    showEndGameDialog()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showEndGameDialog() {
        // Consider either flag or non-empty scores as an active game to avoid false negatives
        val hasActiveGame = gameActive || playerScores.isNotEmpty()

        if (hasActiveGame) {
            AlertDialog.Builder(this)
                .setTitle("End game")
                .setMessage("Save the current game to resume later, or exit without saving.")
                .setPositiveButton("Save") { _, _ ->
                    promptForSaveLabel(onSaved = {
                        finish()
                    })
                }
                .setNegativeButton("Exit") { _, _ ->
                    gameActive = false
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit app")
                .setMessage("There is no active game to save. Do you want to close the app?")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun promptForSaveLabel(onSaved: () -> Unit) {
        val input = EditText(this).apply {
            setText(defaultSaveLabel())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Name this save")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val label = input.text.toString().trim().ifBlank { defaultSaveLabel() }
                saveCurrentGameSnapshot("end_game", label) { onSaved() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helper to switch to Test Mode 2
    private fun enableTestMode2() {
        testModeEnabled = true
        testModeType = 2
        btnTestMode.text = "Test Mode 2: ON"
        btnTestMode.setBackgroundColor(0xFF4CAF50.toInt()) // Green
        layoutDiceButtons.visibility = View.VISIBLE
        
        tvTestLogTitle.visibility = View.VISIBLE
        scrollTestLog.visibility = View.VISIBLE
        btnClearLog.visibility = View.VISIBLE
        
        appendTestLog("🟢 Switched to Test Mode 2 - Android + live.html (No ESP32)")
        Toast.makeText(this, "Test Mode 2: Continuing without ESP32", Toast.LENGTH_LONG).show()
        
        // Disconnect ESP32 if still attempting
        if (esp32Connected) {
            disconnectESP32()
        }
    }
    
    // Sync failure dialog
    private fun showSyncFailureDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ State Synchronization Issue")
            .setMessage("$message\n\nThe game state between Android and ESP32 may be inconsistent. What would you like to do?")
            .setPositiveButton("Use Android State") { _, _ ->
                val result = stateSyncManager.forceSyncResolution(useLocalState = true)
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                updateScoreboard()
            }
            .setNegativeButton("Use ESP32 State") { _, _ ->
                val result = stateSyncManager.forceSyncResolution(useLocalState = false)
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
                // Would need to request state from ESP32
            }
            .setNeutralButton("Ignore") { _, _ ->
                Toast.makeText(this, "Sync warning dismissed", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    // MAC Address setup guide
    private fun showESP32SetupGuide() {
        AlertDialog.Builder(this)
            .setTitle("ESP32 Security Setup")
            .setMessage("To restrict connections to trusted ESP32 boards:\n\n" +
                "1. Upload firmware to ESP32\n" +
                "2. Open Arduino Serial Monitor (115200 baud)\n" +
                "3. Find line: 'MAC Address: XX:XX:XX:XX:XX:XX'\n" +
                "4. Add this MAC to MainActivity.kt (line 60)\n" +
                "5. Rebuild app\n\n" +
                "This prevents unauthorized devices from connecting to your game.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Show Current Whitelist") { _, _ ->
                val whitelistText = if (TRUSTED_ESP32_ADDRESSES.isEmpty()) {
                    "Whitelist is EMPTY - All ESP32 devices accepted"
                } else {
                    "Trusted MACs:\n" + TRUSTED_ESP32_ADDRESSES.joinToString("\n")
                }
                Toast.makeText(this, whitelistText, Toast.LENGTH_LONG).show()
            }
            .show()
    }
}

// ============================================================================
//  Dice helper class – adapted from BLDice demo app
// ============================================================================

@SuppressLint("MissingPermission")
class Dice(private val id: Int, val device: BluetoothDevice) {
    var gatt: BluetoothGatt? = null
    private var service: BluetoothGattService? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var readChar: BluetoothGattCharacteristic? = null
    private val writes: Queue<ByteArray> = LinkedList()
    private var writeInProgress = true
    private var dieName: String? = device.name

    fun onConnected() {
        gatt?.discoverServices()
    }

    fun getDieName(): String? = dieName

    fun onServicesDiscovered() {
        service = gatt?.services?.firstOrNull { it.uuid == serviceUUID }
        writeChar = service?.characteristics?.firstOrNull { it.uuid == writeCharUUID }
        readChar = service?.characteristics?.firstOrNull { it.uuid == readCharUUID }

        readChar?.let {
            gatt?.setCharacteristicNotification(it, true)
            val descriptor = it.getDescriptor(CCCDUUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }
    }

    fun onEvent() {
        readChar?.value?.let {
            GoDiceSDK.incomingPacket(id, GoDiceSDK.DiceType.D6, it)
        }
    }

    fun nextWrite() {
        synchronized(writes) {
            writeInProgress = false
            writes.poll()?.let { value ->
                writeChar?.let { char ->
                    char.value = value
                    gatt?.writeCharacteristic(char)
                    writeInProgress = true
                }
            }
        }
    }

    fun scheduleWrite(value: ByteArray) {
        synchronized(writes) {
            writes.add(value)
            if (!writeInProgress) {
                nextWrite()
            }
        }
    }

    companion object {
        // BLDice BLE UUIDs (Nordic UART Service)
        val serviceUUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val writeCharUUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val readCharUUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCDUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
