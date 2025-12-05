package earth.lastdrop.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.sample.godicesdklib.GoDiceSDK
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.LinkedList
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
        
        // Generate unique session ID for multi-device support
        private val SESSION_ID = java.util.UUID.randomUUID().toString()
        
        // ESP32 BLE Configuration
        const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"  // Legacy - deprecated
        const val ESP32_BOARD_PREFIX = "LASTDROP-"       // Multi-board support
        val ESP32_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCDUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        
        // MAC Address Whitelist - Add your ESP32's MAC address for security
        // Leave empty to accept any LASTDROP-ESP32 device
        // Find MAC in Arduino Serial Monitor when ESP32 starts
        val TRUSTED_ESP32_ADDRESSES = setOf<String>(
            // "24:0A:C4:XX:XX:XX"  // Example - Replace with your ESP32's MAC
            // Add more trusted devices as needed
        )
    }

    // ---------- UI ----------
    private lateinit var tvTitle: TextView
    private lateinit var tvDiceStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvLastEvent: TextView
    private lateinit var tvUndoStatus: TextView
    private lateinit var tvCoinTimeout: TextView
    private lateinit var tvCurrentTurn: TextView
    private lateinit var tvScoreP1: TextView
    private lateinit var tvScoreP2: TextView
    private lateinit var tvScoreP3: TextView
    private lateinit var tvScoreP4: TextView
    private lateinit var tvPosP1: TextView
    private lateinit var tvPosP2: TextView
    private lateinit var tvPosP3: TextView
    private lateinit var tvPosP4: TextView
    private lateinit var tvTileP1: TextView
    private lateinit var tvTileP2: TextView
    private lateinit var tvTileP3: TextView
    private lateinit var tvTileP4: TextView

    private lateinit var btnConnectDice: Button
    private lateinit var btnConnectBoard: Button
    private lateinit var btnUndo: Button
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

    private var undoTimer: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInitialLaunch = true // Track if this is the first profile selection

    // ---------- BLE / BLDice ----------
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    private val dices = HashMap<String, Dice>()
    private val diceIds = LinkedList<String>() // index = diceId used for GoDiceSDK callbacks
    private val diceColorMap = HashMap<Int, String>() // diceId -> color name

    // ---------- ESP32 Auto-Reconnection ----------
    private var esp32ReconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3
    private var reconnectJob: Job? = null
    private var gameActive = false  // Track if game is in progress
    
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
    private lateinit var apiManager: ApiManager
    private lateinit var animationEventHandler: AnimationEventHandler
    private lateinit var profileManager: ProfileManager
    private lateinit var achievementEngine: AchievementEngine
    private lateinit var rivalryManager: RivalryManager
    
    // ---------- ESP32 BLE (Legacy - will be phased out) ----------
    private var esp32Connected: Boolean = false
    private var esp32Gatt: BluetoothGatt? = null
    private var esp32TxCharacteristic: BluetoothGattCharacteristic? = null
    private var esp32RxCharacteristic: BluetoothGattCharacteristic? = null
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
        profileManager = ProfileManager(this)
        achievementEngine = AchievementEngine(this)
        rivalryManager = RivalryManager(this)
        
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
                // Handle heartbeat loss - try reconnect
                runOnUiThread {
                    Toast.makeText(this, "ESP32 connection lost", Toast.LENGTH_LONG).show()
                }
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
        
        // Initialize animation event handler
        animationEventHandler = AnimationEventHandler(this)

        mainScope.launch(Dispatchers.IO) {
            // start a new game session
            val game = GameEntity(
                startedAt = System.currentTimeMillis(),
                modeTwoDice = playWithTwoDice // will be updated once player chooses mode
            )
            currentGameId = dao.insertGame(game)
        }

        GoDiceSDK.listener = this

        setupUiListeners()
        
        // Connect to ESP32 board
        if (!testModeEnabled) {
            handler.postDelayed({
                connectESP32()
            }, 2000) // Wait 2 seconds after startup
        }

        // Show profile selection screen
        showProfileSelection()

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
                isInitialLaunch = false // Mark that initial launch is complete
                val profileIds = data.getStringArrayListExtra("selected_profiles") ?: emptyList()
                val assignedColors = data.getStringArrayListExtra("assigned_colors") ?: emptyList()
                
                if (profileIds.isNotEmpty()) {
                    mainScope.launch {
                        // Load profile data
                        val profiles = profileIds.mapNotNull { id ->
                            profileManager.getProfile(id)
                        }
                        
                        if (profiles.isNotEmpty()) {
                            // Map profiles to game state
                            playerCount = profiles.size
                            profiles.forEachIndexed { index, profile ->
                                playerNames[index] = profile.nickname // Use nickname for AI
                                playerColors[index] = assignedColors.getOrNull(index) ?: ProfileManager.GAME_COLORS[index]
                            }
                            
                            // Store profile IDs and full profiles for game end recording and AI detection
                            currentGameProfileIds.clear()
                            currentGameProfileIds.addAll(profileIds)
                            currentGameProfiles.clear()
                            currentGameProfiles.addAll(profiles)
                            
                            Log.d(TAG, "Game starting with profiles: ${profiles.map { it.nickname }}")
                            
                            // Initialize game
                            resetLocalGame()
                            updateTurnIndicatorDisplay()
                            
                            Toast.makeText(
                                this@MainActivity,
                                "Welcome ${profiles.joinToString { it.nickname }}!",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                    }
                }
            }
            // If no profiles selected or error, use fallback
            configurePlayers()
            return
        }
        
        // Handle QR code scan result
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                // Parse QR code
                val qrData = QRCodeHelper.parseQRResult(result.contents)
                if (qrData != null && QRCodeHelper.isValidBoardQR(qrData)) {
                    appendTestLog("📷 QR Scanned: ${qrData.boardId}")
                    
                    // Save board with nickname and password
                    boardPreferencesManager.saveBoard(
                        boardId = qrData.boardId,
                        macAddress = qrData.macAddress,
                        nickname = qrData.nickname,
                        password = qrData.password
                    )
                    
                    Toast.makeText(this, "Board saved: ${qrData.nickname ?: qrData.boardId}", Toast.LENGTH_SHORT).show()
                    
                    // Auto-connect to scanned board
                    connectESP32()
                } else {
                    Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
                    appendTestLog("❌ Invalid QR code")
                }
            } else {
                // Cancelled
                appendTestLog("📷 QR scan cancelled")
            }
        } else {
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
        stopScan()
        disconnectESP32()
        stopESP32Scan()
        dices.values.forEach { dice ->
            try {
                dice.gatt?.close()
            } catch (_: Exception) {
            }
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
        tvScoreP1 = findViewById(R.id.tvScoreP1)
        tvScoreP2 = findViewById(R.id.tvScoreP2)
        tvScoreP3 = findViewById(R.id.tvScoreP3)
        tvScoreP4 = findViewById(R.id.tvScoreP4)
        tvPosP1 = findViewById(R.id.tvPosP1)
        tvPosP2 = findViewById(R.id.tvPosP2)
        tvPosP3 = findViewById(R.id.tvPosP3)
        tvPosP4 = findViewById(R.id.tvPosP4)
        tvTileP1 = findViewById(R.id.tvTileP1)
        tvTileP2 = findViewById(R.id.tvTileP2)
        tvTileP3 = findViewById(R.id.tvTileP3)
        tvTileP4 = findViewById(R.id.tvTileP4)

        btnConnectDice = findViewById(R.id.btnConnectDice)
        btnConnectBoard = findViewById(R.id.btnConnectBoard)
        btnUndo = findViewById(R.id.btnUndo)
        undoProgressBar = findViewById(R.id.undoProgressBar)
        btnResetScore = findViewById(R.id.btnResetScore)
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

        btnConnectDice.text = "Connect Dice"
        tvScoreP1.text = "P1: 10"
        tvScoreP2.text = "P2: 10"
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
        
        // Menu buttons
        findViewById<Button>(R.id.btnGameHistory)?.setOnClickListener {
            startActivity(Intent(this, GameHistoryActivity::class.java))
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
                delay(10000)  // 10 second scan timeout
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
        val options = arrayOf("2 players", "3 players", "4 players")

        var chosenIndex = 0 // default = 2 players

        AlertDialog.Builder(this)
            .setTitle("How many players?")
            .setSingleChoiceItems(options, chosenIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton("OK") { dialog, _ ->
                playerCount = chosenIndex + 2 // 0->2,1->3,2->4
                selectedColors.clear() // Reset selected colors for new game
                dialog.dismiss()
                askPlayerName(0)
            }
            .setCancelable(false)
            .show()
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

        // Create custom dialog layout
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        // Name input
        val nameLabel = TextView(this).apply {
            text = "Player ${index + 1} Name:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        val editText = EditText(this).apply {
            hint = "Enter name"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(20, 20, 20, 20)
        }

        // Color selection
        val colorLabel = TextView(this).apply {
            text = "Choose Token Color:"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }

        val allColors = listOf("red", "green", "blue", "yellow")
        val allColorNames = listOf("Red 🔴", "Green 🟢", "Blue 🔵", "Yellow 🟡")

        // Filter out already selected colors
        val availableColors = mutableListOf<String>()
        val availableColorNames = mutableListOf<String>()
        allColors.forEachIndexed { idx, color ->
            if (!selectedColors.contains(color)) {
                availableColors.add(color)
                availableColorNames.add(allColorNames[idx])
            }
        }

        // If all colors taken (shouldn't happen with max 4 players), fallback to all colors
        if (availableColors.isEmpty()) {
            availableColors.addAll(allColors)
            availableColorNames.addAll(allColorNames)
        }

        val colorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                availableColorNames
            )
            // Default selection is first available color
            setSelection(0)
        }

        // Add views to container
        container.addView(nameLabel)
        container.addView(editText)
        container.addView(colorLabel)
        container.addView(colorSpinner)

        AlertDialog.Builder(this)
            .setTitle("Player ${index + 1} Setup")
            .setView(container)
            .setPositiveButton("OK") { dialog, _ ->
                val name = editText.text.toString().trim()
                playerNames[index] = if (name.isNotEmpty()) name else "Player ${index + 1}"

                // Get selected color from available colors list
                val selectedColorIndex = colorSpinner.selectedItemPosition
                val selectedColor = availableColors[selectedColorIndex]
                playerColors[index] = selectedColor

                // Mark this color as selected
                selectedColors.add(selectedColor)

                Log.d(TAG, "Player ${index + 1}: ${playerNames[index]}, Color: ${playerColors[index]}")

                dialog.dismiss()
                askPlayerName(index + 1)
            }
            .setCancelable(false)
            .show()
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

        if (isScanning) {
            tvDiceStatus.text = "Already scanning…"
            return
        }

        tvDiceStatus.text = if (playWithTwoDice) {
            "Scanning for 2 BLDice…"
        } else {
            "Scanning for BLDice…"
        }

        startScan()
    }

    private fun startScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filters = LinkedList<ScanFilter>()
        filters.add(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(Dice.serviceUUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(SCAN_MODE_LOW_LATENCY)
            .build()

        val activity = this

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                activity.runOnUiThread {
                    activity.handleScanResult(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                super.onBatchScanResults(results)
                activity.runOnUiThread {
                    results.forEach { activity.handleScanResult(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                runOnUiThread {
                    tvDiceStatus.text = "Scan failed: $errorCode"
                    isScanning = false
                }
            }
        }

        isScanning = true
        scanner.startScan(filters, scanSettings, scanCallback)

        // auto-stop scanning after 15s
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                tvDiceStatus.text = "Scan timeout. No BLDice found."
            }
        }, 15_000)
    }

    private fun stopScan() {
        if (!isScanning) return
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanCallback?.let { scanner.stopScan(it) }
        isScanning = false
    }

    private fun disconnectAllDice() {
        try {
            stopScan()
        } catch (_: Exception) { }

        dices.values.forEach { dice ->
            try {
                dice.gatt?.disconnect()
                dice.gatt?.close()
            } catch (_: Exception) { }
        }

        dices.clear()
        diceIds.clear()
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

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return

        val name = device.name
        Log.d(TAG, "Scan result: addr=${device.address}, name=$name, rssi=${result.rssi}")

        if (name == null) return
        if (!name.contains("GoDice")) return

        if (dices.containsKey(device.address)) return

        // Assign diceId
        var diceId = diceIds.indexOf(device.address)
        if (diceId < 0) {
            diceId = diceIds.size
            diceIds.add(device.address)
        }

        val dice = Dice(diceId, device)
        dices[device.address] = dice

        // LED blink + queries
        dice.scheduleWrite(GoDiceSDK.openLedsPacket(0xff0000, 0x00ff00))
        Timer().schedule(object : TimerTask() {
            override fun run() {
                dice.scheduleWrite(GoDiceSDK.closeToggleLedsPacket())
            }
        }, 3000)

        dice.scheduleWrite(GoDiceSDK.getColorPacket())
        dice.scheduleWrite(GoDiceSDK.getChargeLevelPacket())

        tvDiceStatus.text = "Connecting to ${device.name}…"

        // Stop scanning after we have enough dice:
        val neededDice = if (playWithTwoDice) 2 else 1
        if (dices.size >= neededDice) {
            stopScan()
        }

        dice.gatt = device.connectGatt(this, true, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt?,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread {
                        diceConnected = true
                        btnConnectDice.text = "Disconnect Dice"
                        tvDiceStatus.text = "Connected to ${dice.getDieName() ?: "BLDice"}"
                        Log.d(TAG, "BLDice connected (id=$diceId)")
                    }
                    dice.onConnected()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread {
                        diceConnected = false
                        btnConnectDice.text = "Connect Dice"
                        tvDiceStatus.text = "Dice disconnected"
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                dice.onServicesDiscovered()
            }

            @Deprecated("Use onCharacteristicChanged(gatt, characteristic, value) instead")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                dice.onEvent()
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                dice.nextWrite()
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                dice.nextWrite()
            }
        })
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
                tvCurrentTurn.visibility = View.VISIBLE
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

        // track for live board
        lastTile = turnResult.tile
        lastChanceCard = turnResult.chanceCard

        var eventText =
            "$playerName rolled a $value, moved to Tile ${turnResult.newPosition} (${turnResult.tile.name})"
        if (turnResult.chanceCard != null) {
            eventText += "\nDrew card: ${turnResult.chanceCard.description}"
        }
        tvLastEvent.text = eventText

        Log.d(TAG, "Stable roll $value by $playerName")
        
        // Send to ESP32 for physical board update
        sendRollToESP32(safeIndex, value, currentPos, turnResult.newPosition)

        // advance turn 0..playerCount-1
        currentPlayer = (currentPlayer + 1) % playerCount
        
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
            
            // Send reset command to ESP32
            withContext(Dispatchers.Main) {
                sendResetToESP32()
            }

            // Push reset state to server
            pushResetStateToServer()

            withContext(Dispatchers.Main) {
                updateScoreboard()
                tvLastEvent.text = "Game reset - ${playerNames[0]}'s turn"
            }
        }
    }

    private fun pushResetStateToServer() {
        apiManager.pushResetState(
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
        apiManager.pushRollingStatus(
            playerNames = playerNames,
            playerColors = playerColors,
            playerPositions = playerPositions,
            playerScores = playerScores,
            playerCount = playerCount,
            currentPlayer = currentPlayer,
            playWithTwoDice = playWithTwoDice,
            diceColorMap = diceColorMap,
            diceRollingStatus = diceRollingStatus,
            lastDice1 = lastDice1,
            lastDice2 = lastDice2,
            lastAvg = lastAvg
        )
    }

    private fun pushLiveStateToBoard(rolling: Boolean = false) {
        apiManager.pushLiveState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerPositions = playerPositions,
            playerScores = playerScores,
            playerCount = playerCount,
            currentPlayer = currentPlayer,
            playWithTwoDice = playWithTwoDice,
            diceColorMap = diceColorMap,
            lastDice1 = lastDice1,
            lastDice2 = lastDice2,
            lastAvg = lastAvg,
            lastTileName = lastTile?.name,
            lastTileType = lastTile?.type?.name,
            lastChanceCardNumber = lastChanceCard?.number,
            lastChanceCardText = lastChanceCard?.description,
            rolling = rolling
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
                    tvScoreP1.text = "Error"
                    tvScoreP2.text = ""
                    tvScoreP3.text = ""
                    tvScoreP4.text = ""
                }
            }
        }
    }

    private fun updateScoreboard() {
        val p1Name = playerNames.getOrNull(0) ?: "Player 1"
        val p2Name = playerNames.getOrNull(1) ?: "Player 2"
        val p3Name = playerNames.getOrNull(2) ?: "Player 3"
        val p4Name = playerNames.getOrNull(3) ?: "Player 4"

        val s1 = playerScores[p1Name] ?: 0
        val s2 = playerScores[p2Name] ?: 0
        val s3 = playerScores[p3Name] ?: 0
        val s4 = playerScores[p4Name] ?: 0

        val colorEmoji1 = getColorEmoji(playerColors.getOrNull(0) ?: "red")
        val colorEmoji2 = getColorEmoji(playerColors.getOrNull(1) ?: "green")
        val colorEmoji3 = getColorEmoji(playerColors.getOrNull(2) ?: "blue")
        val colorEmoji4 = getColorEmoji(playerColors.getOrNull(3) ?: "yellow")

        tvScoreP1.text = "$colorEmoji1 $p1Name : $s1"
        tvScoreP2.text = "$colorEmoji2 $p2Name : $s2"

        tvPosP1.text = "Pos: ${playerPositions[p1Name] ?: 0}"
        tvPosP2.text = "Pos: ${playerPositions[p2Name] ?: 0}"

        // 🔹 Safely build tile label as pure String
        val tileP1Text: String = gameEngine.tiles
            .getOrNull((playerPositions[p1Name] ?: 1) - 1)
            ?.name
            ?.toString()
            ?: ""

        val tileP2Text: String = gameEngine.tiles
            .getOrNull((playerPositions[p2Name] ?: 1) - 1)
            ?.name
            ?.toString()
            ?: ""

        tvTileP1.text = tileP1Text
        tvTileP2.text = tileP2Text

        if (playerCount >= 3) {
            tvScoreP3.text = "$colorEmoji3 $p3Name : $s3"
            tvPosP3.text = "Pos: ${playerPositions[p3Name] ?: 0}"

            val tileP3Text: String = gameEngine.tiles
                .getOrNull((playerPositions[p3Name] ?: 1) - 1)
                ?.name
                ?.toString()
                ?: ""

            tvTileP3.text = tileP3Text
        } else {
            tvScoreP3.text = ""
            tvPosP3.text = ""
            tvTileP3.text = ""
        }

        if (playerCount >= 4) {
            tvScoreP4.text = "$colorEmoji4 $p4Name : $s4"
            tvPosP4.text = "Pos: ${playerPositions[p4Name] ?: 0}"

            val tileP4Text: String = gameEngine.tiles
                .getOrNull((playerPositions[p4Name] ?: 1) - 1)
                ?.name
                ?.toString()
                ?: ""

            tvTileP4.text = tileP4Text
        } else {
            tvScoreP4.text = ""
            tvPosP4.text = ""
            tvTileP4.text = ""
        }
    }



    // ------------------------------------------------------------------------
    //  Helper functions
    // ------------------------------------------------------------------------

    private fun getColorEmoji(color: String): String {
        return when (color.lowercase()) {
            "red" -> "🔴"
            "green" -> "🟢"
            "blue" -> "🔵"
            "yellow" -> "🟡"
            else -> "⚪"
        }
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

        // Set current player back to the one who just rolled (so they play again)
        currentPlayer = previousPlayerIndex
        
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
        val boardId = esp32Gatt?.device?.name
        
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
        Log.d(TAG, "Connecting to ESP32: ${device.address}")
        
        // Save as last connected board
        device.name?.let { boardId ->
            boardPreferencesManager.saveLastBoard(boardId, device.address)
            boardPreferencesManager.updateBoardConnection(boardId)
        }
        
        esp32Gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "ESP32 connected, discovering services...")
                        esp32Connected = true
                        esp32ReconnectAttempts = 0  // Reset counter on successful connection
                        esp32ScanJob?.cancel()  // Cancel timeout timer
                        gameActive = true
                        
                        // Start heartbeat monitoring
                        esp32ErrorHandler.startHeartbeatMonitoring()
                        
                        // P4.2: Start state sync monitoring
                        stateSyncManager.startSyncMonitoring()
                        
                        appendTestLog("✅ ESP32 Connected")
                        
                        // Update Connect Board button
                        runOnUiThread {
                            btnConnectBoard.text = "🎮  Disconnect Board"
                            btnConnectBoard.isEnabled = true
                        }
                        
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "ESP32 disconnected")
                        esp32Connected = false
                        esp32Gatt?.close()
                        esp32Gatt = null
                        
                        // Stop heartbeat monitoring
                        esp32ErrorHandler.stopHeartbeatMonitoring()
                        
                        // P4.2: Stop state sync monitoring
                        stateSyncManager.stopSyncMonitoring()
                        
                        appendTestLog("❌ ESP32 Disconnected")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "ESP32 disconnected", Toast.LENGTH_SHORT).show()
                            btnConnectBoard.text = "🎮  Connect Board"
                            btnConnectBoard.isEnabled = true
                        }
                        
                        // Auto-reconnect if game is active
                        if (gameActive && esp32ReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            esp32ReconnectAttempts++
                            appendTestLog("🔄 Attempting reconnect (${esp32ReconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...")
                            
                            reconnectJob?.cancel()
                            reconnectJob = mainScope.launch {
                                delay(2000)  // Wait 2 seconds before retry
                                connectESP32()
                            }
                        } else if (esp32ReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                            appendTestLog("⚠️ Max reconnect attempts reached")
                            showReconnectDialog()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = gatt.getService(ESP32_SERVICE_UUID)
                    esp32TxCharacteristic = service?.getCharacteristic(ESP32_CHAR_TX_UUID)
                    esp32RxCharacteristic = service?.getCharacteristic(ESP32_CHAR_RX_UUID)

                    esp32TxCharacteristic?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(CCCDUUID)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }

                    esp32Connected = true
                    Log.d(TAG, "ESP32 ready for communication")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "ESP32 Connected!", Toast.LENGTH_SHORT).show()
                        sendConfigToESP32()
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic?.value?.let { data ->
                    val message = String(data, Charsets.UTF_8)
                    Log.d(TAG, "ESP32 ← $message")
                    handleESP32Event(message)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun sendToESP32(jsonString: String) {
        if (!esp32Connected || esp32Gatt == null || esp32RxCharacteristic == null) {
            Log.w(TAG, "ESP32 not connected, cannot send: $jsonString")
            return
        }

        Log.d(TAG, "ESP32 → $jsonString")
        esp32RxCharacteristic?.value = jsonString.toByteArray(Charsets.UTF_8)
        esp32Gatt?.writeCharacteristic(esp32RxCharacteristic)
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
        
        esp32Gatt?.device?.let { device ->
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
                        pushLiveStateToBoard()
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
                        pushLiveStateToBoard()
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
                        AlertDialog.Builder(this)
                            .setTitle("Coin Misplacement Detected")
                            .setMessage("Please fix:\n" + errorList.joinToString("\n") { "Tile ${it.tile}: ${it.issue}" })
                            .setPositiveButton("OK", null)
                            .show()
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
                    val newPassword = json.optString("password", null)
                    val newNickname = json.optString("nickname", null)
                    val restartRequired = json.optBoolean("restartRequired", false)
                    
                    Log.d(TAG, "ESP32 settings updated - Nickname: $newNickname, Restart: $restartRequired")
                    
                    // Save updated settings to preferences
                    esp32Gatt?.device?.let { device ->
                        device.name?.let { boardId ->
                            if (newNickname != null) {
                                boardPreferencesManager.updateBoardNickname(boardId, newNickname)
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
                    
                    // Disable coin timeout during winner animation
                    esp32ErrorHandler.setWinnerAnimationInProgress(true)
                    
                    runOnUiThread {
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
                                
                                // Push final state to live.html
                                pushLiveStateToBoard()
                            }
                        )
                        
                        // Update UI status during animation
                        animationEventHandler.updateAnimationStatus(tvDiceStatus, "winner")
                        
                        // Push state to live.html to show winner
                        pushLiveStateToBoard()
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
        esp32Gatt?.disconnect()
        esp32Gatt?.close()
        esp32Gatt = null
        esp32Connected = false
        esp32TxCharacteristic = null
        esp32RxCharacteristic = null
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
