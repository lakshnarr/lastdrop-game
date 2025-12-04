# ESP32 Architecture - Complete Game Brain

## Overview

The ESP32 firmware (`sketch_ble.ino`) is a **complete game brain** supporting two operational modes:

1. **Android Mode** (Current Production): Android app controls, ESP32 displays and detects
2. **Standalone Mode** (Future Variant): ESP32 runs entire game with LED display and random dice

## Dual-Mode Architecture

### Mode 1: Android Controls (Current)
```
GoDice → Android App → ESP32
         (Calculates)   (Displays + Detects)
```

**Flow**:
1. Android receives dice roll from GoDice
2. Android calculates: new tile, score changes, chance cards
3. Android sends `roll` command with final target tile
4. ESP32 animates LED movement and waits for coin
5. ESP32 confirms coin placement via Hall sensor
6. Android updates database and web display

**Advantages**:
- Clean separation of concerns
- Android has database for history
- Android can push to web API

**Commands Android Sends**:
```json
{"command": "roll", "playerId": 1, "diceValue": 4, "currentTile": 3, "targetTile": 7}
{"command": "undo", "playerId": 2}
{"command": "reset"}
```

### Mode 2: ESP32 Controls (Future Standalone)
```
Random Dice → ESP32 → LED Display
              (Complete Game)
```

**Flow**:
1. ESP32 generates random dice (1-6) from button press
2. ESP32 calculates: tile effects, score changes, chance cards
3. ESP32 animates LED movement and waits for coin
4. ESP32 confirms coin placement
5. ESP32 updates scores and displays on LED matrix
6. ESP32 auto-triggers winner/elimination animations

**Advantages**:
- No smartphone needed
- Complete portable game
- Lower cost (just ESP32 + LEDs)

**Commands Internal ESP32**:
```cpp
handleRoll(playerId, randomDice, currentTile);
// ESP32 calculates everything internally
```

## Complete Game Logic on ESP32

### 1. 20-Tile Board Definition
```cpp
const TileDefinition BOARD[NUM_TILES] = {
  {1,  "Start Point",          TYPE_START},
  {2,  "Sunny Patch",          TYPE_PENALTY},    // -1 drop
  {3,  "Rain Dock",            TYPE_WATER_DOCK}, // +2 drops
  {4,  "Busy Junction",        TYPE_NORMAL},
  {5,  "Riverbank Road",       TYPE_NORMAL},
  {6,  "Chance Card",          TYPE_CHANCE},     // Random card
  {7,  "Desert Path",          TYPE_DISASTER},   // -3 drops
  {8,  "Village Square",       TYPE_BONUS},      // +1 drop
  {9,  "Forest Trail",         TYPE_NORMAL},
  {10, "Mountain Pass",        TYPE_PENALTY},    // -1 drop
  {11, "Lake Shore",           TYPE_WATER_DOCK}, // +2 drops
  {12, "Chance Card",          TYPE_CHANCE},     // Random card
  {13, "City Center",          TYPE_NORMAL},
  {14, "Industrial Zone",      TYPE_PENALTY},    // -1 drop
  {15, "Oasis",                TYPE_SUPER_DOCK}, // +5 drops
  {16, "Wasteland",            TYPE_DISASTER},   // -3 drops
  {17, "Chance Card",          TYPE_CHANCE},     // Random card
  {18, "Harbor",               TYPE_WATER_DOCK}, // +2 drops
  {19, "Marketplace",          TYPE_BONUS},      // +1 drop
  {20, "Drought Zone",         TYPE_PENALTY}     // -1 drop
};
```

### 2. 20 Chance Cards
```cpp
const ChanceCard CHANCE_CARDS[20] = {
  {1,  "You fixed a tap leak! Take 2 drops",               +2},
  {2,  "Rainwater harvested! Take 2 drops",                +2},
  {3,  "Community well cleaned! Take 2 drops",             +2},
  {4,  "Water filter installed! Take 1 drop",              +1},
  {5,  "Early monsoon! Take 1 drop",                       +1},
  {6,  "Groundwater recharged! Take 1 drop",               +1},
  {7,  "Safe journey! No change",                           0},
  {8,  "Water delivery arrived! Take 3 drops",             +3},
  {9,  "Spring discovered! Take 2 drops",                  +2},
  {10, "Irrigation system repaired! Take 1 drop",          +1},
  {11, "Water conservation award! Take 2 drops",           +2},
  {12, "Reservoir filled! Take 3 drops",                   +3},
  {13, "Drought warning! Lose 1 drop",                     -1},
  {14, "Water contaminated! Lose 2 drops",                 -2},
  {15, "You left tap running! Lose 1 drop",                -1},
  {16, "Evaporation loss! Lose 1 drop",                    -1},
  {17, "Pipe burst nearby! Lose 3 drops",                  -3},
  {18, "Heatwave! Lose 2 drops",                           -2},
  {19, "Well dried up! Lose 2 drops",                      -2},
  {20, "Water theft! Lose 1 drop",                         -1}
};
```

### 3. Tile Processing Logic
```cpp
void handleRoll(JsonDocument& doc) {
  // 1. Extract player, dice, current tile
  int playerId = doc["playerId"];
  int diceValue = doc["diceValue"];
  int fromTile = playerTiles[playerId];
  
  // 2. Calculate new tile (with lap wraparound)
  int toTile = fromTile + diceValue;
  int lapBonus = 0;
  if (toTile > NUM_TILES) {
    toTile = toTile - NUM_TILES;
    lapBonus = 5;  // Lap completion bonus
  }
  
  // 3. Get tile definition
  TileDefinition tile = BOARD[toTile - 1];
  
  // 4. Apply tile effects
  int scoreChange = lapBonus;
  ChanceCard* chanceCard = nullptr;
  
  switch(tile.type) {
    case TYPE_START:
      // No effect
      break;
    case TYPE_NORMAL:
      // Safe tile
      break;
    case TYPE_BONUS:
      scoreChange += 1;
      break;
    case TYPE_PENALTY:
      scoreChange -= 1;
      break;
    case TYPE_DISASTER:
      scoreChange -= 3;
      break;
    case TYPE_WATER_DOCK:
      scoreChange += 2;
      break;
    case TYPE_SUPER_DOCK:
      scoreChange += 5;
      break;
    case TYPE_CHANCE:
      int cardIndex = random(0, 20);
      chanceCard = &CHANCE_CARDS[cardIndex];
      scoreChange += chanceCard->dropChange;
      break;
  }
  
  // 5. Update score (bounds: 0-99)
  int oldScore = playerScores[playerId];
  int newScore = oldScore + scoreChange;
  if (newScore < 0) newScore = 0;
  if (newScore > 99) newScore = 99;
  playerScores[playerId] = newScore;
  
  // 6. Check elimination
  bool alive = (newScore > 0);
  playerAlive[playerId] = alive;
  
  // 7. Auto-trigger elimination animation
  if (!alive) {
    eliminationAnimation(playerId);
  }
  
  // 8. Check winner (only 1 alive)
  int aliveCount = 0;
  int winnerId = -1;
  for (int i = 0; i < activePlayerCount; i++) {
    if (playerAlive[i]) {
      aliveCount++;
      winnerId = i;
    }
  }
  if (aliveCount == 1) {
    winnerAnimation(winnerId);
    gameOver = true;
  }
  
  // 9. Animate LED movement
  animateMovement(playerId, fromTile, toTile);
  
  // 10. Wait for coin placement
  waitForCoinPlacement(playerId, toTile);
  
  // 11. Send detailed response
  sendRollResponse(playerId, fromTile, toTile, tile, 
                   scoreChange, oldScore, newScore, 
                   chanceCard, alive);
}
```

### 4. Response Format
ESP32 sends comprehensive JSON responses:

```json
{
  "event": "roll_processed",
  "playerId": 2,
  "movement": {
    "from": 5,
    "to": 8,
    "animated": true
  },
  "tile": {
    "index": 8,
    "name": "Village Square",
    "type": "BONUS"
  },
  "score": {
    "old": 7,
    "new": 8,
    "change": 1
  },
  "chanceCard": null,
  "player": {
    "alive": true,
    "eliminated": false
  }
}
```

**Chance Card Example**:
```json
{
  "event": "roll_processed",
  "playerId": 1,
  "tile": {
    "index": 6,
    "name": "Chance Card",
    "type": "CHANCE"
  },
  "chanceCard": {
    "id": 17,
    "description": "Pipe burst nearby! Lose 3 drops",
    "dropChange": -3
  },
  "score": {
    "old": 10,
    "new": 7,
    "change": -3
  }
}
```

## Advanced Features

### 1. Command Queue
Prevents race conditions during long animations:
```cpp
std::queue<String> commandQueue;
bool processingCommand = false;

void onBLEReceive(String command) {
  commandQueue.push(command);
}

void processCommandQueue() {
  if (!processingCommand && !commandQueue.empty()) {
    processingCommand = true;
    String cmd = commandQueue.front();
    commandQueue.pop();
    
    // Process command (may trigger 17s animation)
    
    processingCommand = false;
  }
}
```

### 2. State Validation
Detects sensor glitches:
```cpp
void validateGameState() {
  // Check for duplicate positions
  for (int i = 0; i < activePlayerCount; i++) {
    for (int j = i+1; j < activePlayerCount; j++) {
      if (playerTiles[i] == playerTiles[j]) {
        sendError("DUPLICATE_POSITION", "Multiple coins on same tile");
      }
    }
  }
  
  // Check for impossible states
  if (aliveCount == 0 && !gameOver) {
    sendError("INVALID_STATE", "No players alive but game not over");
  }
}
```

### 3. Security Features
**Optional Password Protection**:
```cpp
bool isPaired = false;

void handlePair(JsonDocument& doc) {
  String password = doc["password"];
  if (password == BOARD_PASSWORD) {
    isPaired = true;
    sendPairResponse(true);
  } else {
    sendPairResponse(false);
  }
}

void handleRoll(JsonDocument& doc) {
  if (PAIRING_REQUIRED && !isPaired) {
    sendError("NOT_PAIRED", "Board requires pairing first");
    return;
  }
  // Process roll...
}
```

**MAC Address Whitelist**:
```cpp
bool isTrustedDevice(BLEAddress address) {
  if (!MAC_FILTERING_ENABLED) return true;
  
  String mac = address.toString();
  for (int i = 0; i < NUM_TRUSTED_DEVICES; i++) {
    if (mac == TRUSTED_ANDROID_MACS[i]) return true;
  }
  return false;
}
```

### 4. Power Management
```cpp
const unsigned long IDLE_TIMEOUT = 300000; // 5 minutes
unsigned long lastActivityTime = 0;

void checkIdleTimeout() {
  if (millis() - lastActivityTime > IDLE_TIMEOUT) {
    strip.setBrightness(20);  // Dim to 20%
    strip.show();
  }
}

void resetIdleTimer() {
  lastActivityTime = millis();
  strip.setBrightness(100);
}
```

## Future Standalone Variant Hardware

### Required Components
1. **ESP32 Dev Board** - Game brain
2. **LED Strip** (80 LEDs, WS2812B) - Board display
3. **Hall Effect Sensors** (4x, A3144) - Coin detection
4. **LED Matrix Display** (16x16, MAX7219) - Score display
5. **Push Button** - Random dice generator trigger
6. **Power Supply** (5V, 3A) - LED strip power
7. **Magnetic Coins** (4x, different colors) - Player tokens

### Hardware Connections
```
ESP32 GPIO Assignments:
- GPIO 13: LED Strip Data (DIN)
- GPIO 32: Hall Sensor Player 1
- GPIO 33: Hall Sensor Player 2
- GPIO 25: Hall Sensor Player 3
- GPIO 26: Hall Sensor Player 4
- GPIO 27: Random Dice Button
- GPIO 22: SPI CLK (LED Matrix)
- GPIO 23: SPI MOSI (LED Matrix)
- GPIO 21: SPI CS (LED Matrix)
```

### LED Matrix Display Layout
```
┌─────────────────┐
│ P1:  7 | P2:  5 │  Top row: Player scores
│ P3: 12 | P4:  0 │  (0 = eliminated)
├─────────────────┤
│   DICE: 🎲 4   │  Middle: Last dice roll
├─────────────────┤
│ TILE: BONUS +1 │  Bottom: Current tile effect
└─────────────────┘
```

### Standalone Mode Code Changes
```cpp
// Add random dice generator
int generateRandomDice() {
  return random(1, 7);
}

// Button press handler
void onButtonPress() {
  if (gameOver) {
    resetGame();
    return;
  }
  
  int diceValue = generateRandomDice();
  
  // Create internal command (no Android)
  JsonDocument doc;
  doc["playerId"] = currentPlayer;
  doc["diceValue"] = diceValue;
  doc["currentTile"] = playerTiles[currentPlayer];
  
  handleRoll(doc);  // ESP32 processes everything
  
  updateLEDMatrix();  // Show scores on display
  
  // Advance to next player
  do {
    currentPlayer = (currentPlayer + 1) % activePlayerCount;
  } while (!playerAlive[currentPlayer]);
}
```

## Compilation Statistics

**Current Build** (Full Game Logic):
```
Sketch Size:      1,122,771 bytes (85% of 1,310,720 bytes)
Global Variables:    40,720 bytes (12% of 327,680 bytes)
Free RAM:           286,960 bytes
Platform:           ESP32 Arduino v3.3.4
```

**Features Included**:
- ✅ 20-tile board with 8 tile types
- ✅ 20 chance cards with random selection
- ✅ Complete score calculation (+5 lap bonus)
- ✅ Elimination detection (score <= 0)
- ✅ Winner detection (last player standing)
- ✅ Auto-triggered animations (winner, elimination)
- ✅ Command queue (race condition prevention)
- ✅ State validation (sensor error detection)
- ✅ Security pairing (optional password)
- ✅ Idle timeout (power saving)
- ✅ Heartbeat reporting (every 5s)
- ✅ Watchdog timer (30s auto-reboot)
- ✅ 60s coin timeout (supports long animations)

## Android Integration Modes

### Mode A: Android as Controller (Current)
Android calculates everything, ESP32 just displays:
```kotlin
// Android side
val targetTile = GameEngine.calculateMove(currentTile, diceValue)
val scoreChange = GameEngine.getTileEffect(targetTile)

esp32.sendCommand("""
  {"command": "roll", "playerId": $playerId, 
   "diceValue": $diceValue, "targetTile": $targetTile}
""")
```

ESP32 ignores internal logic, just animates to `targetTile`.

### Mode B: ESP32 as Controller (Future-Compatible)
Android just sends dice value, ESP32 does everything:
```kotlin
// Android side (simplified)
esp32.sendCommand("""
  {"command": "roll", "playerId": $playerId, "diceValue": $diceValue}
""")

// ESP32 response
val response = esp32.waitForResponse()
// {"tile": {"name": "Village Square", "type": "BONUS"}, 
//  "score": {"change": +1, "new": 8}}

// Android updates UI from ESP32 response
playerScore.text = response.score.new.toString()
```

**Advantage**: Android code works identically whether ESP32 or GameEngine.kt calculates. Just different data source.

## Migration Path

### Phase 1: Current Production (Android Controller)
- Android uses GameEngine.kt for all calculations
- ESP32 receives final target tiles
- ESP32 validates coin placement
- Database and web display handled by Android

### Phase 2: Dual Mode Support
- Android sends full dice commands (not pre-calculated)
- ESP32 calculates and sends detailed responses
- Android displays ESP32 responses
- Validates ESP32 vs GameEngine.kt match

### Phase 3: Standalone Variant
- Remove Android dependency
- ESP32 runs entire game
- LED matrix shows all game state
- Button triggers random dice
- Self-contained game

## Testing Checklist

### ESP32 Game Logic Tests
- [ ] Roll lands on NORMAL tile (score unchanged)
- [ ] Roll lands on BONUS tile (score +1)
- [ ] Roll lands on PENALTY tile (score -1)
- [ ] Roll lands on DISASTER tile (score -3)
- [ ] Roll lands on WATER_DOCK tile (score +2)
- [ ] Roll lands on SUPER_DOCK tile (score +5)
- [ ] Roll lands on CHANCE tile (random card applied)
- [ ] Roll completes lap (score +5 bonus)
- [ ] Player eliminated (score reaches 0)
- [ ] Winner detected (last player alive)
- [ ] Elimination animation triggers automatically
- [ ] Winner animation triggers automatically
- [ ] Command queue prevents race conditions
- [ ] State validation catches duplicate positions
- [ ] Idle timeout dims LEDs after 5 minutes

### Security Tests
- [ ] Password pairing works (when enabled)
- [ ] Wrong password rejected
- [ ] Commands blocked before pairing
- [ ] MAC filtering blocks unknown devices
- [ ] Trusted devices connect successfully

### Power Management Tests
- [ ] Idle timeout dims after 5 minutes
- [ ] Activity resets brightness to 100%
- [ ] Watchdog reboots after 30s freeze
- [ ] Heartbeat sends every 5 seconds

## Configuration Guide

### Production Settings (Current)
```cpp
#define PAIRING_REQUIRED false         // No password needed
#define BLE_PAIRING_ENABLED false      // No PIN pairing
#define MAC_FILTERING_ENABLED false    // Accept all devices
#define COIN_TIMEOUT 60000             // 60 seconds
```

### High-Security Settings
```cpp
#define PAIRING_REQUIRED true          // Require board password
#define BOARD_PASSWORD "654321"        // Unique per board
#define BLE_PAIRING_ENABLED true       // Require PIN pairing
#define BLE_PAIRING_PIN 123456         // 6-digit PIN
#define MAC_FILTERING_ENABLED true     // Whitelist only
```

### Multi-Board Setup
```cpp
#define BOARD_UNIQUE_ID "LASTDROP-0001"  // Board 1
// #define BOARD_UNIQUE_ID "LASTDROP-0002"  // Board 2
// #define BOARD_UNIQUE_ID "LASTDROP-0003"  // Board 3

// Each board gets unique password
#define BOARD_PASSWORD "111111"  // Board 0001
// #define BOARD_PASSWORD "222222"  // Board 0002
// #define BOARD_PASSWORD "333333"  // Board 0003
```

## Conclusion

The ESP32 firmware is now a **complete game brain** capable of:
1. Operating with Android app (current production)
2. Operating standalone (future variant)
3. Supporting full game logic (20 tiles, 20 chance cards)
4. Auto-detecting winners and eliminations
5. Preventing race conditions with command queue
6. Validating game state for sensor errors
7. Optional security (password + MAC filtering)
8. Power management (idle dimming + watchdog)

**Flash Usage**: 85% (180KB free) - room for future enhancements
**RAM Usage**: 12% (280KB free) - plenty for runtime operations

The architecture is **future-proof** and ready for standalone variant development.
