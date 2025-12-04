package com.example.lastdrop

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
        
        // Generate unique session ID for multi-device support
        private val SESSION_ID = java.util.UUID.randomUUID().toString()
        
        // ESP32 BLE Configuration
        const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"
        val ESP32_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_RX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val ESP32_CHAR_TX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCDUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    // ---------- UI ----------
    private lateinit var tvTitle: TextView
    private lateinit var tvDiceStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvLastEvent: TextView
    private lateinit var tvUndoStatus: TextView
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
    private lateinit var btnUndo: Button
    private lateinit var btnResetScore: Button
    private lateinit var btnRefreshScoreboard: Button
    private lateinit var btnTestMode: Button
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

    // ---------- BLE / GoDice ----------
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    private val dices = HashMap<String, Dice>()
    private val diceIds = LinkedList<String>() // index = diceId used for GoDiceSDK callbacks
    private val diceColorMap = HashMap<Int, String>() // diceId -> color name

    private var diceConnected: Boolean = false

    // dice mode
    private var playWithTwoDice: Boolean = false
    private val diceResults: MutableMap<Int, Int> = HashMap()
    
    // ---------- ESP32 BLE ----------
    private var esp32Connected: Boolean = false
    private var esp32Gatt: BluetoothGatt? = null
    private var esp32TxCharacteristic: BluetoothGattCharacteristic? = null
    private var esp32RxCharacteristic: BluetoothGattCharacteristic? = null
    private var waitingForCoinPlacement: Boolean = false
    private var esp32ScanCallback: ScanCallback? = null
    
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

        // Ask user how many players and their names (2–4)
        configurePlayers()

        // ping global server on startup
        pingServer()

        fetchScoreboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        undoTimer?.cancel()
        mainScope.cancel()
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
        btnUndo = findViewById(R.id.btnUndo)
        btnResetScore = findViewById(R.id.btnResetScore)
        btnRefreshScoreboard = findViewById(R.id.btnRefreshScoreboard)
        btnTestMode = findViewById(R.id.btnTestMode)
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

        tvTitle.text = "Last Drop – GoDice Controller"
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

    // ------------------------------------------------------------------------
    //  Player configuration (2–4 players + names)
    // ------------------------------------------------------------------------

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
            "Scanning for 2 GoDice…"
        } else {
            "Scanning for GoDice…"
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
                tvDiceStatus.text = "Scan timeout. No GoDice found."
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
                        tvDiceStatus.text = "Connected to ${dice.getDieName() ?: "GoDice"}"
                        Log.d(TAG, "GoDice connected (id=$diceId)")
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
            updateBatteryUi()
        }
    }

    // Update battery text for 1 or 2 dice
    private fun updateBatteryUi() {
        if (!playWithTwoDice) {
            val level = diceBatteryLevels.values.firstOrNull()
            tvBattery.text = if (level != null) {
                "Battery: $level%"
            } else {
                "Battery: --"
            }
        } else {
            val sorted = diceBatteryLevels.keys.sorted()
            when {
                sorted.size >= 2 -> {
                    val l1 = diceBatteryLevels[sorted[0]]
                    val l2 = diceBatteryLevels[sorted[1]]
                    tvBattery.text =
                        "D1: ${l1 ?: "--"}%   D2: ${l2 ?: "--"}%"
                }
                sorted.size == 1 -> {
                    val l1 = diceBatteryLevels[sorted[0]]
                    tvBattery.text =
                        "D1: ${l1 ?: "--"}%   D2: --"
                }
                else -> {
                    tvBattery.text = "D1: --   D2: --"
                }
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

        updateScoreboard()
    }

    // ------------------------------------------------------------------------
    //  Server communication (LastDrop API)
    // ------------------------------------------------------------------------

    private fun pingServer() {
        Thread {
            try {
                val urlString = "$API_BASE_URL/ping.php?key=$API_KEY"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }

                Log.d(TAG, "Ping response code: $code, body: $body")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pinging server", e)
            }
        }.start()
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
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/live_push.php?key=$API_KEY&session=$SESSION_ID")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                // Build players array with reset values
                val playersJson = org.json.JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val color = playerColors[index]
                        val obj = org.json.JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", 1)  // Start position is tile 1
                            put("score", 10)
                            put("eliminated", false)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                // Clear last event (no dice roll yet)
                val lastEventJson = org.json.JSONObject().apply {
                    put("playerId", "")
                    put("playerName", "")
                    put("dice1", org.json.JSONObject.NULL)
                    put("dice2", org.json.JSONObject.NULL)
                    put("avg", org.json.JSONObject.NULL)
                    put("tileIndex", 1)  // Start position is tile 1
                    put("tileName", "")
                    put("tileType", "")
                    put("chanceCardId", org.json.JSONObject.NULL)
                    put("chanceCardText", "")
                    put("rolling", false)
                    put("reset", true) // Flag indicating this is a reset
                }

                val root = org.json.JSONObject().apply {
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Reset state pushed, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing reset state", e)
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
        return try {
            val encodedPlayer = URLEncoder.encode(playerName, "UTF-8")
            val base = "$API_BASE_URL/register_drop.php"
            val sb = StringBuilder()
            sb.append("$base?key=$API_KEY")
            sb.append("&player=$encodedPlayer")
            sb.append("&mode=" + if (modeTwoDice) "2" else "1")
            sb.append("&avg=$avg")
            if (d1 != null) sb.append("&dice1=$d1")
            if (d2 != null) sb.append("&dice2=$d2")

            val url = URL(sb.toString())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val code = conn.responseCode
            conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending roll to cloud", e)
            false
        }
    }

    /**
     * Send rolling status while dice is tumbling
     */
    private fun pushRollingStatusToApi() {
        val playerIndex = currentPlayer.coerceIn(0, playerCount - 1)
        val playerName = playerNames[playerIndex]
        val playerId = "p${playerIndex + 1}"

        // Get dice colors (for 2-dice mode, get both individual colors)
        val diceColor1: String
        val diceColor2: String?

        if (playWithTwoDice && diceColorMap.size >= 2) {
            val sortedDiceIds = diceColorMap.keys.sorted()
            diceColor1 = diceColorMap[sortedDiceIds[0]] ?: playerColors[playerIndex]
            diceColor2 = diceColorMap[sortedDiceIds.getOrNull(1)] ?: playerColors[playerIndex]
        } else {
            diceColor1 = diceColorMap.values.firstOrNull() ?: playerColors[playerIndex]
            diceColor2 = null
        }

        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/live_push.php?key=$API_KEY&session=$SESSION_ID")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                // Check which specific dice are rolling
                val rollingDice = diceRollingStatus.filter { it.value }.keys.sorted()

                // Build players array with CURRENT positions (before dice lands)
                val playersJson = org.json.JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 1
                        val score = playerScores[name] ?: 10
                        val color = playerColors[index]
                        val obj = org.json.JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                val payload = org.json.JSONObject().apply {
                    put("players", playersJson)
                    put("lastEvent", org.json.JSONObject().apply {
                        put("playerId", playerId)
                        put("playerName", playerName)
                        put("rolling", true)
                        put("diceColor1", diceColor1)
                        if (diceColor2 != null) {
                            put("diceColor2", diceColor2)
                        }
                        // NEW: send current dice values (may be partial in 2-dice mode)
                        if (lastDice1 != null) {
                            put("dice1", lastDice1)
                        } else {
                            put("dice1", org.json.JSONObject.NULL)
                        }
                        if (lastDice2 != null) {
                            put("dice2", lastDice2)
                        } else {
                            put("dice2", org.json.JSONObject.NULL)
                        }
                        if (lastAvg != null) {
                            put("avg", lastAvg)
                        } else {
                            put("avg", org.json.JSONObject.NULL)
                        }
                        // Include which dice are rolling (useful for debugging)
                        put("rollingDiceCount", rollingDice.size)
                        if (playWithTwoDice) {
                            put("dice1Rolling", diceRollingStatus[rollingDice.getOrNull(0)] ?: false)
                            put("dice2Rolling", diceRollingStatus[rollingDice.getOrNull(1)] ?: false)
                        }
                    })
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Rolling status sent, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing rolling status", e)
            }
        }
    }

    private fun pushLiveStateToBoard(rolling: Boolean = false) {
        val lastAvgLocal = lastAvg ?: return  // nothing to send yet

        // Player who just played (currentPlayer has already advanced)
        val playerIndex = (currentPlayer - 1 + playerCount) % playerCount
        val playerName = playerNames[playerIndex]
        val playerId = "p${playerIndex + 1}"

        // Get dice colors (for 2-dice mode, get both individual colors)
        val diceColor1: String
        val diceColor2: String?

        if (playWithTwoDice && diceColorMap.size >= 2) {
            // 2-dice mode: get individual colors for each die
            val sortedDiceIds = diceColorMap.keys.sorted()
            diceColor1 = diceColorMap[sortedDiceIds[0]] ?: playerColors[playerIndex]
            diceColor2 = diceColorMap[sortedDiceIds.getOrNull(1)] ?: playerColors[playerIndex]
        } else {
            // 1-die mode or fallback: use first available color
            diceColor1 = diceColorMap.values.firstOrNull() ?: playerColors[playerIndex]
            diceColor2 = null
        }

        // 1) Build players array
        val playersJson = org.json.JSONArray().apply {
            playerNames.take(playerCount).forEachIndexed { index, name ->
                val pos = playerPositions[name] ?: 0
                val score = playerScores[name] ?: 0
                val color = playerColors[index]
                val obj = org.json.JSONObject().apply {
                    put("id", "p${index + 1}")
                    put("name", name)
                    put("pos", pos)
                    put("score", score)
                    put("eliminated", score <= 0)
                    put("color", color)
                }
                put(obj)
            }
        }

        // 2) Build lastEvent object
        val lastEventJson = org.json.JSONObject().apply {
            put("playerId", playerId)
            put("playerName", playerName)
            put("dice1", lastDice1)
            put("dice2", lastDice2)
            put("avg", lastAvgLocal)
            put("tileIndex", playerPositions[playerName] ?: 0)
            put("tileName", lastTile?.name ?: "")
            put("tileType", lastTile?.type ?: "")
            put("chanceCardId", lastChanceCard?.number ?: org.json.JSONObject.NULL)
            put("chanceCardText", lastChanceCard?.description ?: "")
            put("rolling", rolling)
            put("diceColor1", diceColor1)
            if (diceColor2 != null) {
                put("diceColor2", diceColor2)
            }
        }

        // 3) Wrap into root JSON
        val root = org.json.JSONObject().apply {
            put("players", playersJson)
            put("lastEvent", lastEventJson)
        }

        // 4) POST to https://lastdrop.earth/api/live_push.php?key=ABC123
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/live_push.php?key=$API_KEY&session=$SESSION_ID")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Live push response code: $code")
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing live state", e)
            }
        }
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

        undoTimer = mainScope.launch {
            var seconds = 5
            while (seconds > 0) {
                tvUndoStatus.text = "Tap CONFIRM to undo ($seconds s)"
                btnUndo.text = "Confirm ($seconds s)"
                delay(1000)
                seconds--
            }
            tvUndoStatus.text = "Undo expired"
            btnUndo.text = "Undo Last Move"
            btnUndo.setBackgroundColor(0xFF6200EE.toInt()) // Reset color
        }
    }

    private fun confirmUndo() {
        val prev = previousRoll ?: run {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
        }

        // Restore the player who made the last roll
        val playerName = playerNames[previousPlayerIndex]

        // Revert position and score
        playerPositions[playerName] = previousPosition
        playerScores[playerName] = previousScore

        // Set current player back to the one who just rolled (so they play again)
        currentPlayer = previousPlayerIndex
        
        // Send undo command to ESP32
        sendUndoToESP32(previousPlayerIndex, playerPositions[playerName] ?: 1, previousPosition)

        // Clear ALL roll data (critical: prevents server from showing stale data)
        lastRoll = null
        lastDice1 = null
        lastDice2 = null
        lastAvg = null
        lastTile = null
        lastChanceCard = null

        tvLastEvent.text = "Undo applied - $playerName returns to position $previousPosition"
        tvUndoStatus.text = "Undo applied"
        btnUndo.text = "Undo Last Move"
        btnUndo.setBackgroundColor(0xFF6200EE.toInt()) // Reset color
        undoTimer?.cancel()

        Log.d(TAG, "Undo confirmed: $playerName -> pos=$previousPosition, score=$previousScore")

        // Update UI
        updateScoreboard()

        // Push undo state to server (with cleared roll data)
        pushUndoStateToServer()
    }

    private fun pushUndoStateToServer() {
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$API_BASE_URL/live_push.php?key=$API_KEY&session=$SESSION_ID")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 3000
                    readTimeout = 3000
                }

                // Build players array with current (undone) state
                val playersJson = org.json.JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 0
                        val score = playerScores[name] ?: 10
                        val color = playerColors[index]
                        val obj = org.json.JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                // Clear last event and mark as undo
                val lastEventJson = org.json.JSONObject().apply {
                    put("playerId", "p${previousPlayerIndex + 1}")
                    put("playerName", playerNames[previousPlayerIndex])
                    put("dice1", org.json.JSONObject.NULL)
                    put("dice2", org.json.JSONObject.NULL)
                    put("avg", org.json.JSONObject.NULL)
                    put("tileIndex", previousPosition)
                    put("tileName", "")
                    put("tileType", "")
                    put("chanceCardId", org.json.JSONObject.NULL)
                    put("chanceCardText", "")
                    put("rolling", false)
                    put("undo", true) // Flag indicating this is an undo
                }

                val root = org.json.JSONObject().apply {
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Undo state pushed, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing undo state", e)
            }
        }
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
                    "Bluetooth permissions are required to connect to GoDice",
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
            .setSingleChoiceItems(options, if (testModeEnabled) 2 else 0) { dialog, which ->
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
                        
                        appendTestLog("🔴 Production Mode - Using GoDice + ESP32")
                        Toast.makeText(this, "Production Mode: GoDice + ESP32 + live.html", Toast.LENGTH_SHORT).show()
                        
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
                appendTestLog("🎲 Manual roll: $diceValue")
                Log.d(TAG, "Test Mode 1 - Sending dice value $diceValue to ESP32")
                
                // Simulate dice stable callback with selected value
                onDiceStable(0, diceValue)
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

        Log.d(TAG, "Starting ESP32 scan...")
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Bluetooth scanner not available", Toast.LENGTH_SHORT).show()
            return
        }

        val scanFilter = ScanFilter.Builder()
            .setDeviceName(ESP32_DEVICE_NAME)
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        esp32ScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    Log.d(TAG, "Found ESP32: ${device.name} - ${device.address}")
                    stopESP32Scan()
                    connectToESP32Device(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "ESP32 scan failed: $errorCode")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "ESP32 scan failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        scanner.startScan(listOf(scanFilter), scanSettings, esp32ScanCallback)
        
        // Stop scan after 10 seconds
        handler.postDelayed({
            stopESP32Scan()
        }, 10000)
    }

    @SuppressLint("MissingPermission")
    private fun stopESP32Scan() {
        esp32ScanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            esp32ScanCallback = null
            Log.d(TAG, "ESP32 scan stopped")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToESP32Device(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ESP32: ${device.address}")
        
        esp32Gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "ESP32 connected, discovering services...")
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "ESP32 disconnected")
                        esp32Connected = false
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "ESP32 disconnected", Toast.LENGTH_SHORT).show()
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
        }
    }

    private fun sendUndoToESP32(playerId: Int, fromTile: Int, toTile: Int) {
        if (testModeEnabled) return
        if (!esp32Connected) return

        val undoCmd = org.json.JSONObject().apply {
            put("command", "undo")
            put("playerId", playerId)
            put("fromTile", fromTile)
            put("toTile", toTile)
        }

        sendToESP32(undoCmd.toString())
        waitingForCoinPlacement = true
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
            val json = org.json.JSONObject(jsonString)
            val event = json.optString("event", "")

            when (event) {
                "coin_placed" -> {
                    val playerId = json.getInt("playerId")
                    val tile = json.getInt("tile")
                    val verified = json.optBoolean("verified", true)

                    Log.d(TAG, "Coin placed: Player $playerId at tile $tile")
                    waitingForCoinPlacement = false

                    runOnUiThread {
                        tvDiceStatus.text = "Coin placed! ✓"
                        Toast.makeText(this, "Coin detected at tile $tile", Toast.LENGTH_SHORT).show()
                        // Trigger live.html update
                        pushLiveStateToBoard()
                    }
                }

                "coin_timeout" -> {
                    val tile = json.getInt("tile")
                    Log.w(TAG, "Coin placement timeout at tile $tile")
                    waitingForCoinPlacement = false

                    runOnUiThread {
                        Toast.makeText(this, "Coin placement timeout - continuing anyway", Toast.LENGTH_SHORT).show()
                        pushLiveStateToBoard()
                    }
                }

                "misplacement" -> {
                    val errors = json.getJSONArray("errors")
                    val errorList = mutableListOf<String>()

                    for (i in 0 until errors.length()) {
                        val error = errors.getJSONObject(i)
                        val tile = error.getInt("tile")
                        val issue = error.getString("issue")
                        errorList.add("Tile $tile: $issue")
                    }

                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("Coin Misplacement Detected")
                            .setMessage("Please fix:\n" + errorList.joinToString("\n"))
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                "config_complete" -> {
                    Log.d(TAG, "ESP32 configuration complete")
                    runOnUiThread {
                        Toast.makeText(this, "ESP32 configured with $playerCount players", Toast.LENGTH_SHORT).show()
                    }
                }

                "ready" -> {
                    val message = json.optString("message", "ESP32 Ready")
                    Log.d(TAG, "ESP32: $message")
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
        esp32Gatt?.disconnect()
        esp32Gatt?.close()
        esp32Gatt = null
        esp32Connected = false
        esp32TxCharacteristic = null
        esp32RxCharacteristic = null
        waitingForCoinPlacement = false
        Log.d(TAG, "ESP32 disconnected and cleaned up")
    }
}

// ============================================================================
//  Dice helper class – adapted from GoDice demo app
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
        // GoDice BLE UUIDs (Nordic UART Service)
        val serviceUUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val writeCharUUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val readCharUUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCDUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
