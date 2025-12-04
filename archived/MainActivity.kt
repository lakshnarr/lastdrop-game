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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
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

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), GoDiceSDK.Listener {

    // ---------- API config ----------
    companion object {
        private const val API_BASE_URL = "https://lastdrop.earth"
        private const val API_KEY = "ABC123"
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
    private lateinit var btnUndoConfirm: Button
    private lateinit var btnResetScore: Button
    private lateinit var btnRefreshScoreboard: Button

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
    private val playerColors = mutableListOf("red", "green", "blue", "yellow") // Default colors
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

    // battery per diceId
    private val diceBatteryLevels: MutableMap<Int, Int> = HashMap()

    // Rolling status tracking
    private var isDiceRolling: Boolean = false

    // Database
    private lateinit var db: LastDropDatabase
    private lateinit var dao: LastDropDao
    private var currentGameId: Long = 0L

    // Game Engine
    private lateinit var gameEngine: GameEngine

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth is required for dice connection", Toast.LENGTH_SHORT).show()
            }
        }

    // ------------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize database
        db = LastDropDatabase.getInstance(this)
        dao = db.dao()

        // Initialize game engine
        gameEngine = GameEngine()

        // Initialize all player positions and scores
        for (i in 0 until 4) {
            val playerId = "p${i + 1}"
            playerPositions[playerId] = 1
            playerScores[playerId] = 10
        }

        initViews()
        setupUiListeners()
        initBluetooth()

        // Ask how many players
        configurePlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        if (diceConnected) {
            disconnectAllDice()
        }
        if (isScanning) {
            stopScan()
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
        btnUndoConfirm = findViewById(R.id.btnUndoConfirm)
        btnResetScore = findViewById(R.id.btnResetScore)
        btnRefreshScoreboard = findViewById(R.id.btnRefreshScoreboard)

        tvDiceStatus.text = "Not connected"
        btnUndo.isEnabled = false
        btnUndoConfirm.isEnabled = false
        btnUndoConfirm.visibility = Button.GONE
    }

    private fun setupUiListeners() {
        btnConnectDice.setOnClickListener { onConnectButtonClicked() }
        btnUndo.setOnClickListener { onUndoClicked() }
        btnUndoConfirm.setOnClickListener { onUndoConfirmClicked() }
        btnResetScore.setOnClickListener { onResetScoreClicked() }
        btnRefreshScoreboard.setOnClickListener { refreshScoreboard() }
    }

    private fun onConnectButtonClicked() {
        if (diceConnected) {
            disconnectAllDice()
        } else {
            showDiceModeDialog()
        }
    }

    // ------------------------------------------------------------------------
    //  Player configuration (2–4 players + names)
    // ------------------------------------------------------------------------

    private fun configurePlayers() {
        val options = arrayOf("2 Players", "3 Players", "4 Players")
        AlertDialog.Builder(this)
            .setTitle("How many players?")
            .setItems(options) { _, which ->
                playerCount = which + 2
                // Ask for each player's name
                askPlayerName(0)
            }
            .setCancelable(false)
            .show()
    }

    private fun askPlayerName(index: Int) {
        if (index >= playerCount) {
            // Done collecting names
            refreshScoreboard()
            return
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Player ${index + 1}"

        AlertDialog.Builder(this)
            .setTitle("Enter name for Player ${index + 1}")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    playerNames[index] = name
                } else {
                    playerNames[index] = "Player ${index + 1}"
                }
                askPlayerName(index + 1)
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------------
    //  Bluetooth + scan
    // ------------------------------------------------------------------------

    private fun initBluetooth() {
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private fun showDiceModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dice Mode")
            .setMessage("Play with one dice or two dice?")
            .setPositiveButton("Two Dice") { _, _ ->
                playWithTwoDice = true
                connectDice()
            }
            .setNegativeButton("One Dice") { _, _ ->
                playWithTwoDice = false
                connectDice()
            }
            .show()
    }

    private fun connectDice() {
        val adapter = bluetoothAdapter ?: run {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
            return
        }

        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBt.launch(enableBtIntent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return
            }
        }

        startScan()
    }

    private fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handleScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed: $errorCode")
            }
        }

        scanner.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        tvDiceStatus.text = "Scanning for dice..."

        handler.postDelayed({
            if (isScanning) {
                stopScan()
                if (!diceConnected) {
                    tvDiceStatus.text = "No dice found"
                    Toast.makeText(this, "No GoDice found. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }, 10000)
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    private fun disconnectAllDice() {
        dices.values.forEach { it.close() }
        dices.clear()
        diceIds.clear()
        diceColorMap.clear()
        diceResults.clear()
        diceBatteryLevels.clear()
        diceConnected = false
        tvDiceStatus.text = "Disconnected"
        btnConnectDice.text = "Connect Dice"
        tvBattery.text = "Battery: --"
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val name = device.name ?: return

        if (!name.startsWith("GoDice")) return

        val address = device.address
        if (dices.containsKey(address)) return

        val neededDiceCount = if (playWithTwoDice) 2 else 1
        if (dices.size >= neededDiceCount) {
            stopScan()
            return
        }

        val diceId = dices.size
        val dice = Dice(diceId, device)
        dices[address] = dice
        diceIds.add(address)

        Log.d("BLE", "Connecting to $name ($address) as dice ID $diceId...")

        dice.connect(this, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("BLE", "Connected to $name")
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("BLE", "Disconnected from $name")
                        runOnUiThread {
                            dices.remove(address)
                            diceIds.remove(address)
                            if (dices.isEmpty()) {
                                diceConnected = false
                                tvDiceStatus.text = "Disconnected"
                                btnConnectDice.text = "Connect Dice"
                            }
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    dice.subscribeToNotifications()
                    GoDiceSDK.shared.add(this@MainActivity, diceId)

                    runOnUiThread {
                        if (dices.size >= neededDiceCount) {
                            stopScan()
                            diceConnected = true
                            val statusText = if (playWithTwoDice) "2 dice connected" else "1 die connected"
                            tvDiceStatus.text = statusText
                            btnConnectDice.text = "Disconnect Dice"
                            Toast.makeText(this@MainActivity, "Dice ready!", Toast.LENGTH_SHORT).show()
                        } else {
                            tvDiceStatus.text = "Connected ${dices.size}/${neededDiceCount}"
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic?.value?.let { data ->
                    GoDiceSDK.shared.parseData(diceId, data)
                }
            }
        })
    }

    // ------------------------------------------------------------------------
    //  GoDiceSDK.Listener implementation
    // ------------------------------------------------------------------------

    override fun onDiceColor(diceId: Int, color: Int) {
        val colorName = when (color) {
            1 -> "red"
            2 -> "green"
            3 -> "blue"
            4 -> "orange"
            5 -> "yellow"
            6 -> "black"
            else -> "red"
        }
        diceColorMap[diceId] = colorName
        
        // Assign color to current player if not already assigned
        if (playerColors[currentPlayer] == "red" || playerColors[currentPlayer] == "green" || 
            playerColors[currentPlayer] == "blue" || playerColors[currentPlayer] == "yellow") {
            // Only override if it's a default color
            if (colorName in listOf("red", "green", "blue", "yellow")) {
                playerColors[currentPlayer] = colorName
            }
        }
        
        Log.d("GoDice", "Dice $diceId color detected: $colorName (code: $color)")
    }

    override fun onDiceStable(diceId: Int, number: Int) {
        // Dice has stopped rolling
        isDiceRolling = false
        
        runOnUiThread {
            diceResults[diceId] = number

            if (playWithTwoDice) {
                if (diceResults.size == 2) {
                    val values = diceResults.values.toList()
                    val dice1 = values[0]
                    val dice2 = values[1]
                    val sum = dice1 + dice2

                    processTwodiceTurn(dice1, dice2, sum)
                    diceResults.clear()
                }
            } else {
                processSingleDiceTurn(number)
                diceResults.clear()
            }
        }
    }

    override fun onDiceRoll(diceId: Int, number: Int) {
        // Dice is physically rolling - send rolling status to API
        if (!isDiceRolling) {
            isDiceRolling = true
            pushRollingStatusToApi()
        }
    }

    override fun onDiceChargingStateChanged(diceId: Int, charging: Boolean) {
        val state = if (charging) "charging" else "not charging"
        Log.d("GoDice", "Dice $diceId $state")
    }

    override fun onDiceChargeLevel(diceId: Int, level: Int) {
        diceBatteryLevels[diceId] = level
        runOnUiThread { updateBatteryUi() }
    }

    // Update battery text for 1 or 2 dice
    private fun updateBatteryUi() {
        val text = if (diceBatteryLevels.isEmpty()) {
            "Battery: --"
        } else if (diceBatteryLevels.size == 1) {
            val level = diceBatteryLevels.values.first()
            "Battery: $level%"
        } else {
            val levels = diceBatteryLevels.values.joinToString(" / ") { "$it%" }
            "Battery: $levels"
        }
        tvBattery.text = text
    }

    // ------------------------------------------------------------------------
    //  Game logic hooks
    // ------------------------------------------------------------------------

    private fun processSingleDiceTurn(diceValue: Int) {
        val playerId = "p${currentPlayer + 1}"
        val playerName = playerNames[currentPlayer]
        val currentPos = playerPositions[playerId] ?: 1

        // Use GameEngine to process the turn
        val result = gameEngine.processTurn(currentPos, diceValue)

        // Update player position and score
        playerPositions[playerId] = result.newPosition
        val oldScore = playerScores[playerId] ?: 10
        val newScore = (oldScore + result.scoreChange).coerceAtLeast(0)
        playerScores[playerId] = newScore

        // Store for API
        lastDice1 = diceValue
        lastDice2 = null
        lastAvg = diceValue
        lastModeTwoDice = false
        lastTile = result.tile
        lastChanceCard = result.chanceCard

        // Update UI
        val tileInfo = "${result.tile.name} (${result.tile.type})"
        val cardInfo = result.chanceCard?.let { " | Card: ${it.description}" } ?: ""
        
        tvLastEvent.text = "$playerName rolled $diceValue → Tile ${result.newPosition}: $tileInfo$cardInfo | Score: $oldScore → $newScore"

        refreshScoreboard()
        checkElimination(playerId, playerName, newScore)

        // Save to database
        saveRollEvent(playerName, diceValue, null, diceValue)

        // Enable undo
        enableUndo()

        // Push to API
        pushGameStateToApi(rolling = false)

        // Move to next player
        currentPlayer = (currentPlayer + 1) % playerCount
    }

    private fun processTwodiceTurn(dice1: Int, dice2: Int, sum: Int) {
        val playerId = "p${currentPlayer + 1}"
        val playerName = playerNames[currentPlayer]
        val currentPos = playerPositions[playerId] ?: 1

        // Use GameEngine to process the turn
        val result = gameEngine.processTurn(currentPos, sum)

        // Update player position and score
        playerPositions[playerId] = result.newPosition
        val oldScore = playerScores[playerId] ?: 10
        val newScore = (oldScore + result.scoreChange).coerceAtLeast(0)
        playerScores[playerId] = newScore

        // Store for API
        lastDice1 = dice1
        lastDice2 = dice2
        lastAvg = sum
        lastModeTwoDice = true
        lastTile = result.tile
        lastChanceCard = result.chanceCard

        // Update UI
        val tileInfo = "${result.tile.name} (${result.tile.type})"
        val cardInfo = result.chanceCard?.let { " | Card: ${it.description}" } ?: ""
        
        tvLastEvent.text = "$playerName rolled $dice1 + $dice2 = $sum → Tile ${result.newPosition}: $tileInfo$cardInfo | Score: $oldScore → $newScore"

        refreshScoreboard()
        checkElimination(playerId, playerName, newScore)

        // Save to database
        saveRollEvent(playerName, dice1, dice2, sum)

        // Enable undo
        enableUndo()

        // Push to API
        pushGameStateToApi(rolling = false)

        // Move to next player
        currentPlayer = (currentPlayer + 1) % playerCount
    }

    private fun checkElimination(playerId: String, playerName: String, score: Int) {
        if (score <= 0) {
            Toast.makeText(this, "$playerName is eliminated! (Score reached 0)", Toast.LENGTH_LONG).show()
            // You could add additional elimination logic here
        }
    }

    private fun saveRollEvent(playerName: String, dice1: Int, dice2: Int?, avg: Int) {
        mainScope.launch(Dispatchers.IO) {
            val event = RollEventEntity(
                gameId = currentGameId,
                playerName = playerName,
                timestamp = System.currentTimeMillis(),
                modeTwoDice = playWithTwoDice,
                dice1 = dice1,
                dice2 = dice2,
                avg = avg
            )
            dao.insertRoll(event)
        }
    }

    private fun refreshScoreboard() {
        val scoreViews = listOf(tvScoreP1, tvScoreP2, tvScoreP3, tvScoreP4)
        val posViews = listOf(tvPosP1, tvPosP2, tvPosP3, tvPosP4)
        val tileViews = listOf(tvTileP1, tvTileP2, tvTileP3, tvTileP4)

        for (i in 0 until playerCount) {
            val playerId = "p${i + 1}"
            val name = playerNames[i]
            val score = playerScores[playerId] ?: 10
            val pos = playerPositions[playerId] ?: 1
            val tile = gameEngine.tiles.getOrNull(pos - 1)?.name ?: "Unknown"

            scoreViews[i].text = "$name: $score drops"
            posViews[i].text = "Position: $pos"
            tileViews[i].text = "Tile: $tile"
            scoreViews[i].visibility = TextView.VISIBLE
            posViews[i].visibility = TextView.VISIBLE
            tileViews[i].visibility = TextView.VISIBLE
        }

        // Hide unused player slots
        for (i in playerCount until 4) {
            scoreViews[i].visibility = TextView.GONE
            posViews[i].visibility = TextView.GONE
            tileViews[i].visibility = TextView.GONE
        }
    }

    private fun enableUndo() {
        btnUndo.isEnabled = true
        undoTimer?.cancel()
        undoTimer = mainScope.launch {
            delay(5000)
            btnUndo.isEnabled = false
            btnUndoConfirm.visibility = Button.GONE
            tvUndoStatus.text = ""
        }
    }

    private fun onUndoClicked() {
        btnUndoConfirm.visibility = Button.VISIBLE
        tvUndoStatus.text = "Confirm undo?"
    }

    private fun onUndoConfirmClicked() {
        // Move back to previous player
        currentPlayer = if (currentPlayer == 0) playerCount - 1 else currentPlayer - 1
        val playerId = "p${currentPlayer + 1}"

        // Restore previous values (simplified - in production you'd restore from database)
        playerPositions[playerId] = (playerPositions[playerId] ?: 1) - (lastAvg ?: 0)
        if (playerPositions[playerId]!! < 1) playerPositions[playerId] = 1

        refreshScoreboard()
        btnUndo.isEnabled = false
        btnUndoConfirm.visibility = Button.GONE
        tvUndoStatus.text = "Undone!"
        tvLastEvent.text = "Last roll undone"

        // Push updated state to API
        pushGameStateToApi(rolling = false)
    }

    private fun onResetScoreClicked() {
        AlertDialog.Builder(this)
            .setTitle("Reset Game")
            .setMessage("Reset all scores to 10 and positions to start?")
            .setPositiveButton("Yes") { _, _ ->
                for (i in 0 until playerCount) {
                    val playerId = "p${i + 1}"
                    playerPositions[playerId] = 1
                    playerScores[playerId] = 10
                }
                currentPlayer = 0
                refreshScoreboard()
                tvLastEvent.text = "Game reset"
                
                // Push reset state to API
                pushGameStateToApi(rolling = false)
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ------------------------------------------------------------------------
    //  API Integration
    // ------------------------------------------------------------------------

    /**
     * Send rolling status while dice is tumbling
     */
    private fun pushRollingStatusToApi() {
        val url = URL("$API_BASE_URL/api/live_push.php?key=$API_KEY")
        
        mainScope.launch(Dispatchers.IO) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val currentPlayerName = playerNames[currentPlayer]
                val playerId = "p${currentPlayer + 1}"
                
                // Get dice color if available
                val diceColor = diceColorMap.values.firstOrNull() ?: playerColors[currentPlayer]
                
                // Simple payload with just rolling status
                val payload = """
                    {
                        "players": [],
                        "lastEvent": {
                            "playerId": "$playerId",
                            "playerName": "$currentPlayerName",
                            "rolling": true,
                            "diceColor": "$diceColor"
                        }
                    }
                """.trimIndent()
                
                connection.outputStream.use { it.write(payload.toByteArray()) }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    Log.d("API", "Rolling status sent")
                } else {
                    Log.w("API", "Rolling status failed: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("API", "Failed to push rolling status: ${e.message}")
            }
        }
    }

    /**
     * Send complete game state after dice stops
     */
    private fun pushGameStateToApi(rolling: Boolean = false) {
        val url = URL("$API_BASE_URL/api/live_push.php?key=$API_KEY")
        
        mainScope.launch(Dispatchers.IO) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                // Build players array
                val playersJson = buildString {
                    append("[")
                    for (i in 0 until playerCount) {
                        val playerId = "p${i + 1}"
                        val playerName = playerNames[i]
                        val pos = playerPositions[playerId] ?: 1
                        val score = playerScores[playerId] ?: 10
                        val eliminated = score <= 0
                        val color = playerColors[i]
                        
                        if (i > 0) append(",")
                        append("""
                            {
                                "id": "$playerId",
                                "name": "$playerName",
                                "pos": $pos,
                                "score": $score,
                                "eliminated": $eliminated,
                                "color": "$color"
                            }
                        """.trimIndent())
                    }
                    append("]")
                }
                
                // Build lastEvent with all details
                val currentPlayerName = playerNames[currentPlayer]
                val currentPlayerId = "p${currentPlayer + 1}"
                val currentPos = playerPositions[currentPlayerId] ?: 1
                
                // Get dice color
                val diceColor = diceColorMap.values.firstOrNull() ?: playerColors[currentPlayer]
                
                val eventJson = """
                    {
                        "playerId": "$currentPlayerId",
                        "playerName": "$currentPlayerName",
                        "dice1": ${lastDice1 ?: 0},
                        ${if (lastDice2 != null) "\"dice2\": $lastDice2," else ""}
                        "avg": ${lastAvg ?: 0},
                        "tileIndex": $currentPos,
                        "tileName": "${lastTile?.name?.replace("\"", "\\\"") ?: ""}",
                        "tileType": "${lastTile?.type ?: ""}",
                        "chanceCardId": ${lastChanceCard?.number ?: "null"},
                        "chanceCardText": "${lastChanceCard?.description?.replace("\"", "\\\"") ?: ""}",
                        "rolling": false,
                        "diceColor": "$diceColor"
                    }
                """.trimIndent()
                
                val payload = """
                    {
                        "players": $playersJson,
                        "lastEvent": $eventJson
                    }
                """.trimIndent()
                
                connection.outputStream.use { it.write(payload.toByteArray()) }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    Log.d("API", "Game state pushed successfully")
                } else {
                    Log.w("API", "Game state push failed: $responseCode")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("API", "Failed to push game state: ${e.message}")
            }
        }
    }
}

// ============================================================================
//  Dice helper class – adapted from GoDice demo app
// ============================================================================

@SuppressLint("MissingPermission")
class Dice(private val id: Int, val device: BluetoothDevice) {

    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    fun connect(context: Context, callback: BluetoothGattCallback) {
        gatt = device.connectGatt(context, false, callback)
    }

    fun subscribeToNotifications() {
        val service = gatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
        rxCharacteristic = service?.getCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))
        txCharacteristic = service?.getCharacteristic(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))

        rxCharacteristic?.let { char ->
            gatt?.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }
    }

    fun write(data: ByteArray) {
        txCharacteristic?.value = data
        txCharacteristic?.let { gatt?.writeCharacteristic(it) }
    }

    fun close() {
        gatt?.close()
        gatt = null
    }
}
