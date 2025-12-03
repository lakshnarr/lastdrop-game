# Last Drop - Test Mode Guide

## Overview

The Last Drop Android app includes **two comprehensive test modes** to enable distributed team development:

- **Test Mode 1: ESP32 Board Only** - For ESP32 hardware team
- **Test Mode 2: Android + Web Only** - For Android/GoDice/Web team

## Test Mode 1: ESP32 Board Only

### Purpose
Allows the **ESP32 hardware team** to test the physical board independently without needing:
- GoDice hardware
- Android game logic
- Internet connection for live.html

### What It Does

1. **Dummy Dice Generator**: Android app generates random dice rolls (1-6) on button press
2. **Full Game Logic on ESP32**: ESP32 firmware implements complete game:
   - 20-tile board with tile types (START, NORMAL, CHANCE, BONUS, PENALTY)
   - 10 chance cards with random selection
   - Score tracking and calculation
   - Player elimination detection
   - Undo functionality with state restoration
3. **Comprehensive Reporting**: ESP32 reports back to Android with detailed information:
   - Roll processing (movement, tile landed, score changes)
   - Chance card selection and effects
   - Coin placement confirmation via Hall sensors
   - Misplacement detection
   - Undo confirmations
   - Reset confirmations

### How to Use

1. **Upload Firmware**: Flash `sketch_ble_testmode.ino` to ESP32 board
2. **Enable Test Mode**:
   - Open Android app
   - Tap "Test Mode: OFF" button
   - Select "Test Mode 1: ESP32 Board Only"
3. **Connect ESP32**: App automatically scans and connects to `LASTDROP-ESP32-TESTMODE`
4. **View Test Log**: Scrollable log appears showing all ESP32 responses
5. **Simulate Rolls**: 
   - Tap "Simulate Dice Roll" button
   - Random dice value (1-6) is generated
   - Command sent to ESP32 via BLE
   - ESP32 processes move, calculates score, selects chance card if applicable
6. **Monitor Responses**: Test log displays:
   - Sent commands
   - Received responses with full details
   - Movement animations
   - Score changes
   - Chance card effects
   - Hall sensor confirmations

### Test Log Output Example

```
[14:32:15] ✓ ESP32 Test Mode Enabled
[14:32:16] Waiting for ESP32 connection...
[14:32:18] ✓ ESP32 Ready: ESP32 Test Mode Ready (v2.0-testmode)
[14:32:20] 📤 Sent ROLL command: Dice=4, Player=0
[14:32:21] 🎲 Roll Processed: Player 0
[14:32:21]    Movement: Tile 1 → Tile 5
[14:32:21]    Landed on: Recycling Drive (BONUS)
[14:32:21]    Score: 10 → 12 (+2)
[14:32:21]    Waiting for coin placement...
[14:32:24] ✓ Coin Placed: Player 0 at Tile 5
[14:32:24]    Hall sensor confirmed
```

### ESP32 Test Firmware Features

**File**: `sketch_ble_testmode.ino` (900+ lines)

**Game Logic**:
- 20-tile board matching `GameEngine.kt`
- Tile types: START, NORMAL, CHANCE, BONUS (+2), PENALTY (-2)
- 10 chance cards with effects ranging from -4 to +5
- Random chance card selection on CHANCE tiles
- Score clamping (minimum 0, maximum unlimited)
- Player elimination when score reaches 0

**BLE Commands** (Android → ESP32):
```json
{"command": "roll", "playerId": 0, "diceValue": 4, "currentTile": 1, "expectedTile": 5}
{"command": "undo", "playerId": 0, "fromTile": 5, "toTile": 1}
{"command": "reset"}
{"command": "status"}
```

**BLE Responses** (ESP32 → Android):
```json
// Roll processed
{
  "event": "roll_processed",
  "playerId": 0,
  "movement": {"from": 1, "to": 5, "animated": true},
  "tile": {"index": 5, "name": "Recycling Drive", "type": "BONUS"},
  "score": {"old": 10, "new": 12, "change": 2},
  "player": {"alive": true, "eliminated": false},
  "waiting": {"forCoin": true, "tile": 5, "blinking": true}
}

// Coin placed
{
  "event": "coin_placed",
  "playerId": 0,
  "tile": 5,
  "verified": true,
  "hallSensor": true,
  "message": "Physical coin placement confirmed"
}

// Undo complete
{
  "event": "undo_complete",
  "playerId": 0,
  "movement": {"from": 5, "to": 1, "reversed": true},
  "score": {"restored": 10},
  "player": {"alive": true},
  "waiting": {"forCoin": true, "tile": 1, "message": "Place coin at original position"}
}

// Reset complete
{
  "event": "reset_complete",
  "message": "All players reset to start",
  "players": [
    {"id": 0, "tile": 1, "score": 10, "alive": true},
    {"id": 1, "tile": 1, "score": 10, "alive": true}
  ],
  "board": {"cleared": true},
  "leds": {"background": true}
}

// Misplacement detected
{
  "event": "misplacement_scan",
  "errors": [
    {"tile": 7, "tileName": "Green Belt", "issue": "unexpected_coin"},
    {"tile": 3, "tileName": "Chance – Rainfall", "playerId": 1, "issue": "missing_coin"}
  ]
}

// Timeout
{
  "event": "coin_timeout",
  "playerId": 0,
  "tile": 5,
  "timeout": 30,
  "message": "Coin placement timed out"
}
```

### Hardware Requirements

- ESP32 Dev Board
- WS2812B LED Strip (80 LEDs = 4 per tile × 20 tiles)
- 20 Hall Effect Sensors (A3144)
- Magnetic coins for coin detection

### Testing Workflow

1. **Basic Movement Test**:
   - Simulate roll (dice = 3)
   - Verify LED animation from tile 1 → 4
   - Place coin on tile 4
   - Verify Hall sensor detects coin
   - Check test log for confirmation

2. **Chance Card Test**:
   - Simulate roll to land on CHANCE tile (3, 6, 9, 12, 16, 19)
   - Verify random chance card selection
   - Check score change matches card effect
   - Verify test log shows card description

3. **Bonus/Penalty Test**:
   - Roll to BONUS tile (5, 11, 18) → verify +2 score
   - Roll to PENALTY tile (4, 10, 14) → verify -2 score

4. **Undo Test**:
   - Perform a roll
   - Place coin
   - Tap "Undo" button in Android app
   - Verify reverse animation
   - Verify score restoration
   - Place coin at original position
   - Check test log for undo confirmation

5. **Reset Test**:
   - Tap "Reset Score" button
   - Verify all LEDs clear
   - Verify board background restored
   - Check test log for reset confirmation

6. **Misplacement Detection**:
   - Place coin on wrong tile
   - Wait 5 seconds for scan
   - Verify red flash on misplaced tile
   - Check test log for misplacement report

## Test Mode 2: Android + Web Only

### Purpose
Allows the **Android/GoDice/Web team** to test software components without ESP32 hardware:
- Android UI and game logic
- GoDice integration (optional - can use dummy dice)
- live.html spectator display
- Server API integration

### What It Does

1. **Bypasses ESP32**: No BLE communication with physical board
2. **Simulates Complete Game Flow**:
   - Generates random dice rolls
   - Processes game logic via `GameEngine.kt`
   - Updates UI with scores and positions
   - Pushes state to live.html via API
3. **Instant Feedback**: No waiting for coin placement

### How to Use

1. **Enable Test Mode**:
   - Tap "Test Mode: OFF"
   - Select "Test Mode 2: Android + Web Only"
2. **Simulate Rolls**:
   - Tap "Simulate Dice Roll"
   - Random dice generated
   - Game logic processed
   - UI updated immediately
   - State pushed to live.html
3. **Open Web Display**: Navigate to https://lastdrop.earth/live.html to see animations

### Testing Workflow

1. **UI Test**: Verify scoreboard updates correctly
2. **Game Logic Test**: Test chance cards, bonus/penalty tiles
3. **Web Integration Test**: Verify live.html shows player movements
4. **Elimination Test**: Reduce player score to 0, verify elimination

## Test Mode Comparison

| Feature | Test Mode 1 (ESP32) | Test Mode 2 (Android/Web) |
|---------|---------------------|---------------------------|
| **ESP32 Required** | ✅ Yes | ❌ No |
| **GoDice Required** | ❌ No | ❌ No |
| **Internet Required** | ❌ No | ✅ Yes (for live.html) |
| **Tests Physical Board** | ✅ Yes | ❌ No |
| **Tests Hall Sensors** | ✅ Yes | ❌ No |
| **Tests LED Animations** | ✅ Yes | ❌ No |
| **Tests Android UI** | ⚠️ Partial | ✅ Full |
| **Tests Game Logic** | ⚠️ On ESP32 | ✅ On Android |
| **Tests live.html** | ❌ No | ✅ Yes |
| **Tests Server API** | ❌ No | ✅ Yes |
| **Coin Placement Wait** | ✅ Yes | ❌ No |
| **Real-time Feedback** | ⚠️ Slow (physical) | ✅ Instant |

## Production Mode

To disable test modes and use the full system:

1. Tap "Test Mode: [ACTIVE]"
2. Select "Normal Mode (Production)"
3. Connect real GoDice
4. System uses actual dice rolls + ESP32 + live.html

## Troubleshooting

### Test Mode 1 Issues

**ESP32 won't connect**:
- Check ESP32 is powered on
- Verify correct firmware (`sketch_ble_testmode.ino`) uploaded
- Check Android Bluetooth permissions granted
- Look for "LASTDROP-ESP32-TESTMODE" in Serial Monitor

**No responses in test log**:
- Verify BLE connection successful (check toast message)
- Check ESP32 Serial Monitor for outgoing messages
- Ensure TX characteristic notifications enabled

**Hall sensor not detecting coin**:
- Verify GPIO pin assignments in `hallPins` array
- Check magnet strength (use neodymium magnets)
- Test sensor manually: `digitalRead(hallPins[tile])` should be LOW when magnet near

**Chance cards repeating**:
- Normal behavior - random selection from 10 cards
- Seed initialized with `random()` on ESP32

### Test Mode 2 Issues

**live.html not updating**:
- Check internet connection
- Verify API key in `local.properties`
- Check server logs at lastdrop.earth

**Score calculation wrong**:
- Verify `GameEngine.kt` tile definitions match rulebook
- Check chance card effects

## Switching Between Modes

You can switch modes at any time:

1. **ESP32 → Android/Web**: Safely disconnect ESP32, switch modes
2. **Android/Web → ESP32**: Ensure ESP32 firmware uploaded, switch modes, wait for auto-connect
3. **Test → Production**: Switch to Normal Mode, connect real GoDice

**Note**: Game state resets when switching modes.

## Development Workflow

### ESP32 Team (Remote Location)
```
1. Upload sketch_ble_testmode.ino
2. Enable Test Mode 1
3. Test LED animations, Hall sensors, coin placement
4. Report issues via test log screenshots
5. Iterate on hardware/firmware
```

### Android/Web Team (User Location)
```
1. Enable Test Mode 2
2. Develop UI features, game logic, API integration
3. Test with live.html open
4. No hardware dependencies
5. Fast iteration
```

### Integration Testing (Both Teams Together)
```
1. ESP32 team ships board to Android team
2. Switch to Normal Mode (Production)
3. Connect real GoDice
4. Test full system end-to-end
```

## API Reference

### Test Mode State

```kotlin
// In MainActivity.kt
enum class TestMode {
    NONE,           // Production - full system
    ESP32_ONLY,     // Test Mode 1
    ANDROID_WEB     // Test Mode 2
}

private var testModeType: TestMode = TestMode.NONE
```

### Test Log Functions

```kotlin
private fun addToTestLog(message: String)
private fun clearTestLog()
private fun parseESP32Response(jsonString: String)
```

### BLE Command Sending

```kotlin
private fun sendBLECommand(json: JSONObject)
private fun sendRollToESP32(playerId, playerName, diceAvg, currentTile, expectedTile, color)
private fun sendUndoToESP32(playerId, fromTile, toTile)
private fun sendResetToESP32()
```

## Security Notes

- Test mode uses same MAC address filtering as production
- API key still required for Test Mode 2
- ESP32 test firmware logs all connections to Serial Monitor
- Whitelist ESP32 MAC address in `TRUSTED_ESP32_ADDRESSES` in MainActivity.kt

## Firmware Comparison

| Feature | `sketch_ble.ino` (Production) | `sketch_ble_testmode.ino` (Test) |
|---------|------------------------------|----------------------------------|
| **Game Logic** | ❌ None (Android handles) | ✅ Full 20-tile + chance cards |
| **BLE Device Name** | `LASTDROP-ESP32` | `LASTDROP-ESP32-TESTMODE` |
| **Chance Cards** | ❌ No | ✅ 10 cards |
| **Score Tracking** | ⚠️ Basic | ✅ Full with history |
| **Undo Support** | ⚠️ Position only | ✅ Full state restoration |
| **Response Detail** | ⚠️ Minimal | ✅ Comprehensive JSON |
| **Firmware Size** | ~660 lines | ~900 lines |

## File Reference

### Test Mode 1 Files
- `sketch_ble_testmode.ino` - Enhanced ESP32 firmware (UPLOAD THIS FOR TEST MODE 1)
- `app/src/main/java/com/example/lastdrop/MainActivity.kt` - Test log UI + parsing
- `app/src/main/res/layout/activity_main.xml` - Test log views

### Test Mode 2 Files
- Same MainActivity.kt (different code path)
- `app/src/main/java/com/example/lastdrop/GameEngine.kt` - Game logic
- `live.html` - Web display

### Documentation
- `TEST_MODE_GUIDE.md` - This file
- `.github/copilot-instructions.md` - AI agent reference
- `IMPLEMENTATION_GUIDE.md` - Hardware setup
- `ANDROID_BLE_INTEGRATION.md` - BLE protocol details

## Conclusion

Test modes enable **parallel development** across distributed teams:
- **Hardware team** tests ESP32 independently with full game logic
- **Software team** tests Android + Web without physical board
- **Both teams** iterate faster with immediate feedback
- **Integration** happens only when both components ready

Use Test Mode 1 test log to capture detailed ESP32 behavior for debugging and validation.
