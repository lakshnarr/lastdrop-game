# Hall Sensor Dual-Mode Implementation

## Overview
Modified `sketch_ble.ino` to support **two operational modes**:
1. **Hall Sensor Mode** (ENABLED): ESP waits for physical coin detection via Hall sensors
2. **Timer Delay Mode** (DISABLED): ESP auto-confirms coin placement after configurable delay

## Configuration Flags

### In sketch_ble.ino (Lines ~115-120)
```cpp
// ==================== HALL SENSOR OPERATIONAL FLAGS ====================
bool HALL_SENSOR_OPERATIONAL = true;    // true = Hall sensor mode, false = timer delay mode
const unsigned long TURN_DELAY_MS = 30000;  // 30 seconds default (30000ms)
unsigned long currentTurnDelayMs = TURN_DELAY_MS;  // Runtime configurable
```

## BLE Configuration Command

Android app can now send additional parameters in the `config` command:

```json
{
  "command": "config",
  "playerCount": 2,
  "colors": ["FF0000", "0000FF"],
  "hallSensorMode": true,          // NEW: true/false to enable/disable Hall sensors
  "turnDelaySeconds": 30           // NEW: 1-300 seconds delay when Hall sensors disabled
}
```

**Response includes current mode:**
```json
{
  "event": "config_complete",
  "playerCount": 2,
  "hallSensorMode": true,
  "turnDelaySeconds": 30
}
```

## Workflow Comparison

### Mode 1: Hall Sensor Mode (hallSensorMode=true)
```
Android: Sends dice roll → 
ESP: Moves LED animation → 
ESP: Waits for Hall sensor detection →
ESP: Sends "coin_placed" event when magnet detected →
Android: Receives confirmation
```

**Console Output Example:**
```
⏳ HALL SENSOR MODE - Waiting for coin placement...
  Player 0 must place coin on Tile 5
  Hall sensor will detect magnet automatically
✓ Roll processed, waiting for Hall sensor confirmation

[Hall] Tile 5 → MCP PA3: LOW (magnet)
[Hall] Tile 5 result: 5/5 readings LOW → COIN DETECTED

🧲 COIN DETECTED VIA HALL SENSOR!
  Player 0 at Tile 5
  Detection time: 2340 ms
```

### Mode 2: Timer Delay Mode (hallSensorMode=false)
```
Android: Sends dice roll → 
ESP: Moves LED animation → 
ESP: Waits for turn delay timer →
ESP: Sends "coin_placed" event after delay expires →
Android: Receives confirmation
```

**Console Output Example:**
```
⏱️  TIMER DELAY MODE - No Hall sensor wait
  Turn delay: 30 seconds
  ESP will auto-confirm coin after 30000 ms
✓ Roll processed, timer started

[30 seconds pass...]

⏱️  TURN DELAY COMPLETE - AUTO-CONFIRMING COIN!
  Player 0 at Tile 5
  Delay duration: 30000 ms (30 seconds)
```

## Extensive Console Logging

### Startup Initialization
```
======================================
Initializing I2C and MCP23017...
  I2C SDA: GPIO13
  I2C SCL: GPIO14
  MCP Address: 0x27
======================================
Scanning I2C bus...
  Found device at 0x27
I2C scan complete: 1 device(s) found

✓ MCP23017 detected at 0x27

Configuring Port B (8 Hall sensors):
  PB0 → Tile 1 (INPUT_PULLUP)
  PB1 → Tile 20 (INPUT_PULLUP)
  ...
  Port B: All 8 pins configured as INPUT_PULLUP

Configuring Port A (8 Hall sensors):
  PA0 → Tile 2 (INPUT_PULLUP)
  PA1 → Tile 3 (INPUT_PULLUP)
  ...
  Port A: All 8 pins configured as INPUT_PULLUP

✓ MCP23017 configuration complete
  A3144 Hall sensors: Active-LOW (output pulls LOW when magnet present)
  MCP internal pull-ups: ENABLED (pulls pins HIGH when no magnet)

======================================
Initializing Direct GPIO Hall Sensors
======================================
  GPIO17 → Tile 9 (INPUT_PULLUP) | Initial: HIGH
  GPIO18 → Tile 10 (INPUT_PULLUP) | Initial: HIGH
  GPIO8 → Tile 11 (INPUT_PULLUP) | Initial: HIGH
  GPIO9 → Tile 12 (INPUT_PULLUP) | Initial: HIGH

✓ All Hall sensors initialized

Hall Sensor Mode: ENABLED (waiting for coin)
```

### Hall Sensor Detection Logging
Every Hall sensor check prints:
```
[Hall] Tile 18 → MCP PB3: LOW (magnet)
[Hall] Tile 18 result: 5/5 readings LOW → COIN DETECTED
```

Or when no magnet:
```
[Hall] Tile 5 → MCP PA3: HIGH (no magnet)
[Hall] Tile 5 result: 0/5 readings LOW → NO COIN
```

## Coin Placement Response

### Hall Sensor Mode Response
```json
{
  "event": "coin_placed",
  "playerId": 0,
  "tile": 5,
  "verified": true,
  "hallSensor": true,
  "message": "Hall sensor detected magnet",
  "player": {
    "score": 12,
    "alive": true
  }
}
```

### Timer Delay Mode Response
```json
{
  "event": "coin_placed",
  "playerId": 0,
  "tile": 5,
  "verified": false,
  "hallSensor": false,
  "message": "Timer delay complete - coin auto-confirmed",
  "player": {
    "score": 12,
    "alive": true
  }
}
```

## Android App Integration

### 1. Add UI Settings (Settings Screen)
```kotlin
// In SettingsActivity or MainActivity settings

Switch hallSensorSwitch = findViewById(R.id.hallSensorSwitch)
SeekBar turnDelaySeekBar = findViewById(R.id.turnDelaySeekBar)
TextView turnDelayValue = findViewById(R.id.turnDelayValue)

hallSensorSwitch.setOnCheckedChangeListener { _, isChecked ->
    // Show/hide turn delay controls
    turnDelaySeekBar.isEnabled = !isChecked
}

turnDelaySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        // Range: 5-300 seconds
        val seconds = 5 + progress
        turnDelayValue.text = "$seconds seconds"
    }
})
```

### 2. Update Config Command
```kotlin
fun sendConfigCommand(playerCount: Int, colors: List<String>) {
    val config = JSONObject().apply {
        put("command", "config")
        put("playerCount", playerCount)
        put("colors", JSONArray(colors))
        put("hallSensorMode", hallSensorSwitch.isChecked)  // NEW
        put("turnDelaySeconds", turnDelaySeekBar.progress + 5)  // NEW: 5-300s
    }
    
    esp32ConnectionManager.sendCommand(config.toString())
}
```

### 3. Update Coin Wait UI
```kotlin
// In MainActivity or GameActivity
override fun onConfigComplete(response: JSONObject) {
    val hallSensorMode = response.getBoolean("hallSensorMode")
    val turnDelaySeconds = response.getInt("turnDelaySeconds")
    
    if (hallSensorMode) {
        showStatus("Waiting for coin placement (Hall sensor detection)")
    } else {
        showStatus("Waiting $turnDelaySeconds seconds (timer mode)")
        startCountdownTimer(turnDelaySeconds * 1000L)
    }
}
```

## Testing Procedures

### Test 1: Hall Sensor Mode (Physical Board)
1. Upload sketch with `HALL_SENSOR_OPERATIONAL = true`
2. Connect Android app
3. Send config with `"hallSensorMode": true`
4. Roll dice
5. **Place magnetic coin on LED tile**
6. ESP should detect magnet and send confirmation within 2-3 seconds

**Expected Serial Output:**
```
[Hall] Tile 5 → MCP PA3: LOW (magnet)
🧲 COIN DETECTED VIA HALL SENSOR!
```

### Test 2: Timer Delay Mode (Development)
1. Upload sketch with `HALL_SENSOR_OPERATIONAL = false` OR send config with `"hallSensorMode": false`
2. Set `"turnDelaySeconds": 10` (10 seconds for faster testing)
3. Roll dice
4. **Wait 10 seconds (no coin needed)**
5. ESP should auto-confirm after timer expires

**Expected Serial Output:**
```
⏱️  TIMER DELAY MODE - No Hall sensor wait
  Turn delay: 10 seconds
[10 seconds pass]
⏱️  TURN DELAY COMPLETE - AUTO-CONFIRMING COIN!
```

### Test 3: Dynamic Mode Switching
1. Start game in Hall Sensor mode
2. Send new config with `"hallSensorMode": false`
3. Next roll should use timer delay mode
4. Send config with `"hallSensorMode": true`
5. Next roll should wait for Hall sensor

## Troubleshooting

### Hall Sensor Always Shows "NO COIN"
```
[Hall] Tile 18 → MCP PB3: HIGH (no magnet)
[Hall] Tile 18 result: 0/5 readings LOW → NO COIN
```
**Causes:**
1. External 10kΩ pull-up resistor not installed
2. Hall sensor VCC not connected to 3.3V
3. Magnet too weak or too far from sensor
4. Hall sensor damaged or incorrect pinout

**Solution:** Check voltages with multimeter as done in testing session

### Timer Delay Not Working
**Symptom:** Coin auto-confirms immediately even with 30s delay
**Cause:** `HALL_SENSOR_OPERATIONAL` still set to `true` in firmware
**Solution:** 
- Set flag to `false` in firmware, OR
- Send config with `"hallSensorMode": false`

### MCP23017 Not Detected
```
✗ ERROR: MCP23017 not found at 0x27!
```
**Causes:**
1. I2C wiring incorrect (SDA/SCL swapped)
2. MCP not powered (VCC not connected)
3. A0/A1/A2 address pins not set correctly
4. I2C pull-up resistors missing (2.2kΩ on SDA/SCL)

**Solution:** Run I2C scanner, check address pins

## Files Modified
- `sketch_ble/sketch_ble.ino` (Lines ~115-120, 520-560, 790-820, 1060-1100, 1420-1520)

## Compilation Status
✓ **Successfully compiled**
- Sketch size: 700,603 bytes (53% of 1,310,720)
- Global variables: 34,736 bytes (10% of 327,680)
- ESP32-S3-N16R8 compatible

## Next Steps for Android Integration
1. Add UI toggle for Hall Sensor Mode in Settings
2. Add SeekBar for Turn Delay (5-300 seconds)
3. Update `sendConfigCommand()` to include new parameters
4. Update UI to show countdown timer in Timer Delay mode
5. Handle `"hallSensor": true/false` in coin_placed response
