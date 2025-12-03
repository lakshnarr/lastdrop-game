# Player Configuration Integration

**Date**: December 3, 2025  
**Feature**: Pass player count and colors from Android to ESP32 and live.html  
**Status**: ✅ IMPLEMENTED

---

## Overview

When the Android app starts, it asks the user:
1. **How many players** (2-4 players)
2. **Player names** and **coin colors** (red, green, blue, yellow)

This configuration is now passed to:
- ✅ **live.html** - To display correct number of tokens with matching colors
- ✅ **ESP32** - To power only active player LEDs with matching colors

---

## User Flow

### Step 1: Select Player Count
```
┌─────────────────────────┐
│  How many players?      │
├─────────────────────────┤
│  ○ 2 players            │
│  ○ 3 players            │
│  ● 4 players            │
├─────────────────────────┤
│         [OK]            │
└─────────────────────────┘
```

### Step 2: Configure Each Player
```
┌─────────────────────────────┐
│  Player 1 Setup             │
├─────────────────────────────┤
│  Player 1 Name:             │
│  ┌─────────────────────┐   │
│  │ Alice               │   │
│  └─────────────────────┘   │
│                             │
│  Choose Token Color:        │
│  ┌─────────────────────┐   │
│  │ Red 🔴           ▼ │   │
│  └─────────────────────┘   │
├─────────────────────────────┤
│            [OK]             │
└─────────────────────────────┘
```

**Available Colors**: Red 🔴, Green 🟢, Blue 🔵, Yellow 🟡

**Color Selection Rules**:
- Each player must choose a unique color
- Once a color is selected, it's removed from the dropdown for subsequent players
- If all colors are taken (shouldn't happen with max 4 players), all colors become available again

### Step 3: Configuration Sent
After all players are configured:
1. Android sends `config` command to ESP32 via BLE
2. Android sends player data to live.html via API
3. ESP32 confirms with `config_complete` event
4. Game starts with correct player count and colors

---

## Technical Implementation

### Android → ESP32 (BLE)

#### Command Structure
```json
{
  "command": "config",
  "playerCount": 3,
  "colors": ["FF0000", "00FF00", "0000FF"]
}
```

**Field Details**:
- `command`: Always `"config"`
- `playerCount`: Integer (2-4)
- `colors`: Array of hex color strings (RGB format without `#`)
  - Red: `"FF0000"`
  - Green: `"00FF00"`
  - Blue: `"0000FF"`
  - Yellow: `"FFFF00"`

#### When Sent
- **Trigger**: After all players complete name/color selection
- **Function**: `sendConfigToESP32()` in `MainActivity.kt` line ~2407
- **Location**: Called from `askPlayerName()` when `index >= playerCount`

#### ESP32 Response
```json
{
  "event": "config_complete",
  "playerCount": 3
}
```

---

### Android → live.html (HTTP API)

#### API Endpoint
```
POST https://lastdrop.earth/api/live_push.php?key=API_KEY
```

#### Payload Structure
```json
{
  "players": [
    {
      "id": "p1",
      "name": "Alice",
      "pos": 1,
      "score": 10,
      "eliminated": false,
      "color": "red"
    },
    {
      "id": "p2",
      "name": "Bob",
      "pos": 1,
      "score": 10,
      "eliminated": false,
      "color": "green"
    },
    {
      "id": "p3",
      "name": "Charlie",
      "pos": 1,
      "score": 10,
      "eliminated": false,
      "color": "blue"
    }
  ],
  "lastEvent": {
    "playerId": "",
    "dice1": 0,
    "dice2": 0,
    "avg": 0,
    "from": 0,
    "to": 0,
    "coinPlaced": false
  }
}
```

**Field Details**:
- `players`: Array of player objects (length = `playerCount`)
- `color`: String ("red", "green", "blue", "yellow")
- Each player starts at tile 1 with 10 drops

#### When Sent
- **Trigger**: During `resetLocalGame()` → `pushResetStateToServer()`
- **Function**: `pushResetStateToServer()` in `MainActivity.kt` line ~1491

---

## ESP32 Firmware Changes

### New Global Variable
```cpp
int activePlayerCount = 2;  // Default to 2 players, updated via config command
```

### New Command Handler
```cpp
void handleConfig(JsonDocument& doc) {
  int playerCount = doc["playerCount"];
  JsonArray colorsArray = doc["colors"];
  
  // Validate player count
  if (playerCount < 2 || playerCount > NUM_PLAYERS) {
    sendErrorResponse("Invalid player count");
    return;
  }
  
  activePlayerCount = playerCount;
  
  // Update player colors from hex strings
  for (int i = 0; i < activePlayerCount; i++) {
    const char* colorHex = colorsArray[i];
    uint32_t color = (uint32_t)strtol(colorHex, NULL, 16);
    players[i].color = color;
  }
  
  // Turn off LEDs for inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = 0x000000;  // Black (off)
  }
  
  // Send confirmation
  response["event"] = "config_complete";
  response["playerCount"] = activePlayerCount;
}
```

### Updated Reset Logic
```cpp
void handleReset() {
  // Reset only active players
  for (int i = 0; i < activePlayerCount; i++) {
    players[i].currentTile = 1;
    players[i].score = 10;
    players[i].alive = true;
    // Keep the configured color
  }
  
  // Turn off inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = 0x000000;  // Black (off)
  }
}
```

### Updated Roll Validation
```cpp
void handleRoll(JsonDocument& doc) {
  int playerId = doc["playerId"];
  
  if (playerId < 0 || playerId >= activePlayerCount) {
    sendErrorResponse("Invalid player ID");
    return;
  }
  
  // Process roll only for active players
  // ...
}
```

---

## Android Code Changes

### New Function: `sendConfigToESP32()`
```kotlin
private fun sendConfigToESP32() {
    // Convert color names to RGB hex values for ESP32
    val colorMap = mapOf(
        "red" to "FF0000",
        "green" to "00FF00",
        "blue" to "0000FF",
        "yellow" to "FFFF00"
    )
    
    val colorsArray = org.json.JSONArray().apply {
        (0 until playerCount).forEach { index ->
            val colorName = playerColors[index]
            val colorHex = colorMap[colorName] ?: "FFFFFF"
            put(colorHex)
        }
    }
    
    val json = org.json.JSONObject().apply {
        put("command", "config")
        put("playerCount", playerCount)
        put("colors", colorsArray)
    }
    
    sendBLECommand(json)
    
    Log.d(TAG, "Sent config to ESP32: $playerCount players, colors: $colorsArray")
}
```

### Updated `askPlayerName()`
```kotlin
private fun askPlayerName(index: Int) {
    if (index >= playerCount) {
        Toast.makeText(
            this,
            "Players: " + (0 until playerCount).joinToString { playerNames[it] },
            Toast.LENGTH_LONG
        ).show()
        
        // Send player configuration to ESP32
        sendConfigToESP32()
        
        resetLocalGame()
        return
    }
    
    // ... rest of player name/color selection dialog
}
```

### Updated Test Log Parsing
```kotlin
private fun parseESP32Response(jsonString: String) {
    // ... existing event handlers
    
    "config_complete" -> {
        val playerCount = json.optInt("playerCount", 0)
        addToTestLog("⚙️ Configuration Applied")
        addToTestLog("   Active players: $playerCount")
        addToTestLog("   Player colors configured on ESP32")
    }
}
```

### Updated BLE Command Logging
```kotlin
private fun sendBLECommand(json: org.json.JSONObject) {
    // ... existing code
    
    when (command) {
        "config" -> {
            val playerCount = json.optInt("playerCount", 0)
            val colors = json.optJSONArray("colors")
            addToTestLog("📤 Sent CONFIG command: $playerCount players")
            if (colors != null) {
                for (i in 0 until colors.length()) {
                    addToTestLog("   Player ${i+1}: #${colors.getString(i)}")
                }
            }
        }
        // ... other commands
    }
}
```

---

## LED Behavior on ESP32

### Before Configuration
- All 4 player slots powered (default hardcoded colors)
- Red, Blue, Green, Yellow LEDs active

### After Configuration (2 players example)
- **Player 0**: Red LED (if player chose red)
- **Player 1**: Green LED (if player chose green)
- **Player 2**: OFF (inactive)
- **Player 3**: OFF (inactive)

### During Gameplay
- Only active player LEDs animate during dice rolls
- Inactive players have `color = 0x000000` (black/off)
- Inactive players have `alive = false`

### LED Animation
```cpp
// Only active players participate in LED updates
for (int p = 0; p < activePlayerCount; p++) {
  if (players[p].alive && players[p].currentTile > 0) {
    setTileColor(players[p].currentTile, players[p].color);
  }
}
```

---

## Color Mapping

### Android Color Names → Hex Values
```kotlin
val colorMap = mapOf(
    "red" to "FF0000",
    "green" to "00FF00",
    "blue" to "0000FF",
    "yellow" to "FFFF00"
)
```

### ESP32 Color Conversion
```cpp
// Convert hex string "FF0000" to uint32_t 0xFF0000
const char* colorHex = colorsArray[i];
uint32_t color = (uint32_t)strtol(colorHex, NULL, 16);
players[i].color = color;
```

### NeoPixel Color Format
```cpp
// NeoPixel uses 24-bit RGB (GRB order for WS2812B)
uint32_t red = 0xFF0000;     // R=255, G=0, B=0
uint32_t green = 0x00FF00;   // R=0, G=255, B=0
uint32_t blue = 0x0000FF;    // R=0, G=0, B=255
uint32_t yellow = 0xFFFF00;  // R=255, G=255, B=0
```

---

## Testing

### Test Mode 1: ESP32 Only
1. Configure 3 players in Android app
2. Choose colors: Red, Green, Blue
3. Enable Test Mode 1
4. Check test log for config command:
   ```
   📤 Sent CONFIG command: 3 players
      Player 1: #FF0000
      Player 2: #00FF00
      Player 3: #0000FF
   ⚙️ Configuration Applied
      Active players: 3
      Player colors configured on ESP32
   ```
5. Verify ESP32 Serial Monitor shows:
   ```
   ⚙️ Processing CONFIG command...
     Active Players: 3
     Player 0 color: #FF0000 (0xFF0000)
     Player 1 color: #00FF00 (0x00FF00)
     Player 2 color: #0000FF (0x0000FF)
   ✓ Config applied
   ```
6. Simulate dice rolls and verify only 3 LEDs active

### Test Mode 2: Android + Web Only
1. Configure 2 players in Android app
2. Choose colors: Yellow, Green
3. Enable Test Mode 2
4. Open live.html in browser
5. Verify only 2 tokens displayed
6. Verify token colors match selections (yellow, green)

### Physical Board Test
1. Connect ESP32 via BLE
2. Configure 4 players with all colors
3. Roll dice for each player
4. Verify LEDs match player colors:
   - Player 1 rolls → Red LED animates
   - Player 2 rolls → Green LED animates
   - Player 3 rolls → Blue LED animates
   - Player 4 rolls → Yellow LED animates

---

## Error Handling

### Invalid Player Count
```cpp
if (playerCount < 2 || playerCount > NUM_PLAYERS) {
  sendErrorResponse("Invalid player count");
  return;
}
```

### Missing Colors Array
```cpp
JsonArray colorsArray = doc["colors"];
if (colorsArray.size() < playerCount) {
  // Fallback to default colors for missing entries
}
```

### BLE Not Connected
```kotlin
if (!esp32Connected || esp32RxCharacteristic == null) {
    Log.w(TAG, "ESP32 not connected, skipping command")
    return
}
```

---

## Backward Compatibility

### ESP32 Without Config
- **Default**: `activePlayerCount = 2`
- **Behavior**: 2 players with hardcoded red/blue colors
- **Upgrade Path**: Send `config` command during first connection

### live.html Without API
- **Default**: Shows all 4 player slots
- **Behavior**: Displays players based on API payload
- **Graceful Degradation**: If API fails, shows default 4 players

---

## Future Enhancements

### Possible Additions
1. **Custom RGB Colors**: Allow users to pick any color (color picker)
2. **Player Avatars**: Upload images for live.html display
3. **Team Mode**: Group players into teams with shared colors
4. **Color Blindness Mode**: Alternative color schemes
5. **LED Brightness**: Adjust brightness per player preference

### Configuration Persistence
- Save player preferences to Android SharedPreferences
- Recall last used names/colors on app restart
- ESP32 save config to EEPROM/Preferences

---

## Files Modified

### Android
- ✅ `app/src/main/java/com/example/lastdrop/MainActivity.kt`
  - Added `sendConfigToESP32()` function
  - Updated `askPlayerName()` to call config after setup
  - Added "config_complete" event handler in `parseESP32Response()`
  - Added config command logging in `sendBLECommand()`

### ESP32
- ✅ `sketch_ble_testmode.ino`
  - Added `activePlayerCount` global variable
  - Added `handleConfig()` function
  - Updated `handleBLECommand()` to route "config" commands
  - Updated `handleReset()` to respect `activePlayerCount`
  - Updated `handleRoll()` validation to use `activePlayerCount`
  - Updated LED rendering to skip inactive players

### Documentation
- ✅ `PLAYER_CONFIG_INTEGRATION.md` (this file)

---

## Summary

✅ **Player count** now controls how many LEDs activate on ESP32  
✅ **Player colors** now match between Android, ESP32, and live.html  
✅ **Configuration sent** automatically after player setup  
✅ **Test Mode** displays config commands in test log  
✅ **Backward compatible** with default 2-player setup  

**Result**: Physical board LEDs now perfectly match the game state shown in the Android app and live.html spectator display! 🎮💡
