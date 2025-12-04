# Android App - Critical Improvements Needed

## Priority 1: Connection & Reliability

### 1.1 ESP32 Auto-Reconnection Logic

**Problem**: If BLE connection drops mid-game, user must manually reconnect.

**Current State**: `onConnectionStateChange()` logs disconnection but doesn't retry.

**Location**: Add after `esp32Gatt?.close()` in disconnection handler

**Fix**:
```kotlin
private var esp32ReconnectAttempts = 0
private val MAX_RECONNECT_ATTEMPTS = 3
private var reconnectJob: Job? = null

// In onConnectionStateChange() for ESP32
BluetoothProfile.STATE_DISCONNECTED -> {
    esp32Connected = false
    esp32Gatt?.close()
    esp32Gatt = null
    updateESP32ButtonState(false)
    
    addToTestLog("❌ ESP32 Disconnected")
    
    // Auto-reconnect if game is active
    if (gameActive && esp32ReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
        esp32ReconnectAttempts++
        addToTestLog("🔄 Attempting reconnect (${esp32ReconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...")
        
        reconnectJob?.cancel()
        reconnectJob = mainScope.launch {
            delay(2000)  // Wait 2 seconds before retry
            connectToESP32()
        }
    } else if (esp32ReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
        addToTestLog("⚠️ Max reconnect attempts reached. Please press Connect ESP32.")
        showReconnectDialog()
    }
}

// Reset attempt counter on successful connection
BluetoothProfile.STATE_CONNECTED -> {
    esp32Connected = true
    esp32ReconnectAttempts = 0  // Reset counter
    // ... existing code
}
```

**Add dialog helper**:
```kotlin
private fun showReconnectDialog() {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("ESP32 Connection Lost")
            .setMessage("Unable to reconnect to physical board. Please:\n\n" +
                "1. Check ESP32 power\n" +
                "2. Press 'Connect ESP32' button\n" +
                "3. Or continue in Test Mode 2 (Android only)")
            .setPositiveButton("Retry Now") { _, _ ->
                esp32ReconnectAttempts = 0
                connectToESP32()
            }
            .setNegativeButton("Switch to Test Mode 2") { _, _ ->
                enableTestMode2()
            }
            .setCancelable(false)
            .show()
    }
}
```

---

### 1.2 MAC Address Whitelist Validation

**Problem**: `TRUSTED_ESP32_ADDRESSES` is empty - app accepts any LASTDROP-ESP32 device.

**Current State**: Lines 51-56 have empty whitelist set.

**Fix**:
```kotlin
// Line 51 - Add your ESP32's MAC address
private val TRUSTED_ESP32_ADDRESSES = setOf(
    "24:0A:C4:XX:XX:XX"  // YOUR ESP32'S MAC - find in Serial Monitor
    // Add more trusted devices as needed
)

// In connectToESP32() before connection attempt (around line 550)
private fun connectToESP32() {
    if (esp32Connected) {
        addToTestLog("ℹ️ Already connected to ESP32")
        return
    }
    
    addToTestLog("🔍 Scanning for LASTDROP-ESP32...")
    
    val scanner = bluetoothAdapter?.bluetoothLeScanner
    val filter = ScanFilter.Builder()
        .setDeviceName(ESP32_DEVICE_NAME)
        .build()
    
    val settings = ScanSettings.Builder()
        .setScanMode(SCAN_MODE_LOW_LATENCY)
        .build()
    
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address
            
            // ✅ VALIDATE MAC ADDRESS
            if (TRUSTED_ESP32_ADDRESSES.isNotEmpty() && !TRUSTED_ESP32_ADDRESSES.contains(address)) {
                addToTestLog("⚠️ Rejected untrusted ESP32: $address")
                return
            }
            
            scanner?.stopScan(this)
            addToTestLog("✅ Found trusted ESP32: $address")
            
            // Proceed with connection
            esp32Gatt = device.connectGatt(this@MainActivity, false, esp32GattCallback)
        }
        
        override fun onScanFailed(errorCode: Int) {
            addToTestLog("❌ ESP32 scan failed: $errorCode")
        }
    }
    
    scanner?.startScan(listOf(filter), settings, callback)
}
```

**User Guide Addition**:
```kotlin
// Add helper function to show MAC address in UI
private fun showESP32SetupGuide() {
    AlertDialog.Builder(this)
        .setTitle("First-Time ESP32 Setup")
        .setMessage("To connect securely:\n\n" +
            "1. Upload firmware to ESP32\n" +
            "2. Open Arduino Serial Monitor\n" +
            "3. Find line: 'ESP32 MAC Address: XX:XX:XX:XX:XX:XX'\n" +
            "4. Add this MAC to MainActivity.kt line 52\n" +
            "5. Rebuild app\n\n" +
            "This prevents unauthorized devices from connecting.")
        .setPositiveButton("OK", null)
        .show()
}
```

---

### 1.3 Connection Timeout for ESP32

**Problem**: If ESP32 is off, app scans indefinitely.

**Fix**:
```kotlin
private var esp32ScanJob: Job? = null

// Modify connectToESP32()
private fun connectToESP32() {
    // ... existing code
    
    scanner?.startScan(listOf(filter), settings, callback)
    
    // Add timeout after 10 seconds
    esp32ScanJob?.cancel()
    esp32ScanJob = mainScope.launch {
        delay(10000)  // 10 second timeout
        scanner?.stopScan(callback)
        
        if (!esp32Connected) {
            addToTestLog("⏱️ ESP32 not found (timeout)")
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "ESP32 not found. Check power and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
```

---

## Priority 2: User Experience

### 2.1 Coin Placement Timeout Display

**Problem**: Users don't know how long they have to place coin.

**Current State**: ESP32 sends `coin_timeout` event, but Android just logs it.

**Fix**:
```kotlin
private var coinTimeoutJob: Job? = null

// When expecting coin placement (after roll command sent)
private fun startCoinTimeoutTimer(timeoutSeconds: Int = 60) {
    coinTimeoutJob?.cancel()
    
    var remaining = timeoutSeconds
    tvLastEvent.text = "⏱️ Place coin... ${remaining}s"
    
    coinTimeoutJob = mainScope.launch {
        repeat(timeoutSeconds) {
            delay(1000)
            remaining--
            
            val color = when {
                remaining > 30 -> "#00FF00"  // Green
                remaining > 10 -> "#FFA500"  // Orange
                else -> "#FF0000"            // Red
            }
            
            runOnUiThread {
                tvLastEvent.text = Html.fromHtml(
                    "⏱️ Place coin... <font color='$color'>${remaining}s</font>",
                    Html.FROM_HTML_MODE_LEGACY
                )
            }
        }
    }
}

// Call when sending roll command to ESP32
private fun sendRollToESP32(playerId: Int, diceValue: Int) {
    // ... existing code
    sendESP32Command(json)
    startCoinTimeoutTimer(60)  // Start 60s countdown
}

// Cancel timer when coin placed
private fun handleCoinPlaced(json: org.json.JSONObject) {
    coinTimeoutJob?.cancel()
    // ... rest of existing code
}
```

---

### 2.2 Battery Warning for GoDice

**Problem**: Dice battery level is displayed but no low battery warning.

**Fix**:
```kotlin
private val LOW_BATTERY_THRESHOLD = 20  // 20%

override fun onDiceChargeLevel(diceId: String, chargeLevel: Int) {
    val dice = dices[diceId] ?: return
    dice.battery = chargeLevel
    
    runOnUiThread {
        tvBattery.text = "🔋 Battery: ${chargeLevel}%"
        
        // Warning for low battery
        if (chargeLevel <= LOW_BATTERY_THRESHOLD) {
            tvBattery.setTextColor(0xFFFF0000.toInt())  // Red text
            Toast.makeText(
                this,
                "⚠️ Dice battery low (${chargeLevel}%). Please charge soon.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            tvBattery.setTextColor(0xFF00FF00.toInt())  // Green text
        }
    }
}
```

---

### 2.3 Add Game Progress Indicator

**Problem**: Players don't know whose turn it is or game progress.

**Fix**: Add to `activity_main.xml`:
```xml
<TextView
    android:id="@+id/tvCurrentTurn"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="Current Turn: Player 1"
    android:textAlignment="center"
    android:textSize="20sp"
    android:textColor="#FFD700"
    android:background="#1E1E1E"
    android:padding="12dp"
    android:fontFamily="monospace"/>
```

**Update in code**:
```kotlin
private lateinit var tvCurrentTurn: TextView

// In initViews()
tvCurrentTurn = findViewById(R.id.tvCurrentTurn)

// Update after each roll
private fun updateCurrentTurnDisplay() {
    val player = players[currentPlayerIndex]
    tvCurrentTurn.text = "🎲 Current Turn: ${player.name} (${player.color})"
    tvCurrentTurn.setBackgroundColor(parseColor(player.color))
}
```

---

## Priority 3: Error Handling

### 3.1 Handle Coin Timeout Gracefully

**Current State**: `handleCoinTimeout()` just logs error (line 3012).

**Improvement**:
```kotlin
private fun handleCoinTimeout(json: org.json.JSONObject) {
    coinTimeoutJob?.cancel()
    waitingForCoinPlacement = false
    
    val playerId = json.optInt("playerId", -1)
    val tile = json.optInt("tile", -1)
    
    runOnUiThread {
        tvLastEvent.text = "⏱️ Coin placement timeout!"
        
        AlertDialog.Builder(this)
            .setTitle("Coin Placement Timeout")
            .setMessage("Player ${playerId + 1} did not place coin on Tile $tile within 60 seconds.\n\n" +
                "Options:\n" +
                "• Retry - Send same command again\n" +
                "• Skip - Move to next player\n" +
                "• Undo - Reverse the roll")
            .setPositiveButton("Retry") { _, _ ->
                // Resend same roll command
                val player = players[playerId]
                sendRollToESP32(playerId, lastDiceRoll)
            }
            .setNeutralButton("Skip") { _, _ ->
                // Move to next player
                advanceToNextPlayer()
            }
            .setNegativeButton("Undo") { _, _ ->
                // Trigger undo
                handleUndoClick()
            }
            .setCancelable(false)
            .show()
    }
}
```

---

### 3.2 Add ESP32 Heartbeat Monitoring

**Problem**: No way to detect if ESP32 is frozen (still connected but not responding).

**Fix**:
```kotlin
private var lastHeartbeatTime = 0L
private val HEARTBEAT_TIMEOUT = 15000L  // 15 seconds

private fun checkESP32Heartbeat() {
    if (!esp32Connected) return
    
    val elapsed = System.currentTimeMillis() - lastHeartbeatTime
    if (elapsed > HEARTBEAT_TIMEOUT) {
        addToTestLog("⚠️ ESP32 not responding for ${elapsed/1000}s")
        
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("ESP32 Not Responding")
                .setMessage("Physical board connected but not sending data.\n\n" +
                    "Try:\n" +
                    "• Reset ESP32 (press physical reset button)\n" +
                    "• Disconnect and reconnect\n" +
                    "• Continue in Test Mode 2")
                .setPositiveButton("Reconnect") { _, _ ->
                    esp32Gatt?.disconnect()
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectToESP32()
                    }, 1000)
                }
                .setNegativeButton("Test Mode 2") { _, _ ->
                    enableTestMode2()
                }
                .show()
        }
    }
}

// Update heartbeat timestamp when receiving ESP32 events
private fun handleESP32Response(data: ByteArray) {
    lastHeartbeatTime = System.currentTimeMillis()  // ✅ Update timestamp
    // ... rest of existing parsing logic
}

// Check heartbeat every 5 seconds
private fun startHeartbeatMonitor() {
    mainScope.launch {
        while (true) {
            delay(5000)
            if (esp32Connected) {
                checkESP32Heartbeat()
            }
        }
    }
}

// Call in onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    // ... existing code
    startHeartbeatMonitor()
}
```

---

## Priority 4: Data Integrity

### 4.1 Validate ESP32 Responses

**Problem**: No validation of JSON structure from ESP32.

**Fix**:
```kotlin
private fun parseESP32Response(data: ByteArray): org.json.JSONObject? {
    return try {
        val json = org.json.JSONObject(String(data))
        
        // Validate required fields
        if (!json.has("event")) {
            addToTestLog("⚠️ ESP32 response missing 'event' field")
            return null
        }
        
        val event = json.getString("event")
        when (event) {
            "roll_processed" -> {
                if (!json.has("playerId") || !json.has("movement")) {
                    addToTestLog("⚠️ Invalid roll_processed format")
                    return null
                }
            }
            "coin_placed" -> {
                if (!json.has("tile") || !json.has("verified")) {
                    addToTestLog("⚠️ Invalid coin_placed format")
                    return null
                }
            }
            "error" -> {
                val message = json.optString("message", "Unknown error")
                addToTestLog("❌ ESP32 Error: $message")
                showESP32ErrorDialog(message)
                return null
            }
        }
        
        json
    } catch (e: Exception) {
        addToTestLog("❌ JSON parse error: ${e.message}")
        null
    }
}
```

---

### 4.2 Add State Sync Verification

**Problem**: Android and ESP32 states might desync (e.g., after crash/restart).

**Fix**:
```kotlin
private fun requestESP32StateSync() {
    val command = org.json.JSONObject()
    command.put("command", "status")
    sendESP32Command(command)
}

// Handle status response from ESP32
private fun handleESP32Status(json: org.json.JSONObject) {
    val playersArray = json.optJSONArray("players") ?: return
    
    // Compare with Android state
    var mismatchFound = false
    for (i in 0 until playersArray.length()) {
        val espPlayer = playersArray.getJSONObject(i)
        val androidPlayer = players.getOrNull(i) ?: continue
        
        val espTile = espPlayer.getInt("tile")
        val espScore = espPlayer.getInt("score")
        
        if (espTile != androidPlayer.position || espScore != androidPlayer.score) {
            mismatchFound = true
            addToTestLog("⚠️ State mismatch Player $i: ESP32($espTile,$espScore) vs Android(${androidPlayer.position},${androidPlayer.score})")
        }
    }
    
    if (mismatchFound) {
        showStateSyncDialog()
    } else {
        addToTestLog("✅ State sync verified")
    }
}

private fun showStateSyncDialog() {
    AlertDialog.Builder(this)
        .setTitle("State Mismatch Detected")
        .setMessage("Android app and ESP32 board have different game states.\n\n" +
            "Choose which to trust:")
        .setPositiveButton("Trust ESP32") { _, _ ->
            // Pull state from ESP32
            requestESP32StateSync()
        }
        .setNegativeButton("Trust Android") { _, _ ->
            // Push state to ESP32
            pushStateToESP32()
        }
        .setNeutralButton("Reset Both") { _, _ ->
            handleResetClick()
        }
        .show()
}
```

---

## Summary Table

| Priority | Issue | Impact | Difficulty | Estimated Lines |
|----------|-------|--------|------------|-----------------|
| 🔴 P1 | Auto-reconnect ESP32 | Game interrupted | Medium | ~40 |
| 🔴 P1 | MAC whitelist validation | Security | Easy | ~15 |
| 🔴 P1 | Connection timeout | UX freeze | Easy | ~20 |
| 🟡 P2 | Coin timeout display | UX confusion | Easy | ~25 |
| 🟡 P2 | Battery warning | Unexpected dice death | Easy | ~10 |
| 🟡 P2 | Turn indicator | Gameplay clarity | Easy | ~15 |
| 🟢 P3 | Timeout dialog options | Error recovery | Medium | ~30 |
| 🟢 P3 | Heartbeat monitoring | Frozen ESP32 detection | Medium | ~40 |
| 🟢 P4 | Response validation | Data integrity | Medium | ~30 |
| 🟢 P4 | State sync | Crash recovery | Hard | ~50 |

**Total Estimated Changes**: ~275 lines

**Recommended Implementation Order**:
1. ✅ MAC whitelist validation (security first!)
2. ✅ Connection timeout (prevents UI hang)
3. ✅ Auto-reconnect logic (usability critical)
4. ✅ Coin timeout display (immediate UX benefit)
5. ⏭️ Heartbeat monitoring (robustness)
6. ⏭️ Remaining features as needed
