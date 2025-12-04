# ESP32 Firmware Files - Comprehensive Comparison

## Overview

You have **3 main ESP32 firmware files** with different purposes and feature sets:

| File | Lines | Purpose | Communication | Game Logic |
|------|-------|---------|---------------|------------|
| **sketch_ble.ino** | 727 | **Production firmware** | BLE only | Android controls |
| **sketch_ble_testmode.ino** | 1655 | Test Mode 1 (standalone) | BLE | ESP32 has full logic |
| **sketch_enhanced.ino** | 696 | WiFi version (obsolete) | WiFi AP + Web | Android controls |

---

## 🎯 RECOMMENDED FILE: **sketch_ble.ino**

**This is your main production firmware** with all recent improvements:

### ✅ Implemented Features
1. **BLE Communication** (Nordic UART Service)
2. **Security**: BLE pairing enabled (PIN 123456)
3. **Timeout**: 60-second coin placement window
4. **UX Enhancements**:
   - Accelerating timeout warning (500ms → 200ms → 100ms RED)
   - Heartbeat reporting (every 5s with time remaining)
5. **Robustness**: Watchdog timer (30s auto-reboot)
6. **Hall Sensors**: Coin detection on all 20 tiles
7. **LED Animations**: Basic move animations
8. **State Persistence**: Preferences storage
9. **Undo Support**: Full state restoration
10. **Compilation**: ✅ **Verified working** (ESP32 v3.3.4)

### Architecture
- **Android is the master** - handles all game logic
- **ESP32 is the display/sensor** - shows LED state, detects coins
- **No tile types or chance cards** on ESP32
- Receives commands: `roll`, `undo`, `reset`, `config`
- Sends events: `coin_placed`, `misplacement`, `heartbeat`

---

## 📊 Feature Comparison Matrix

| Feature | sketch_ble.ino | sketch_ble_testmode.ino | sketch_enhanced.ino |
|---------|----------------|-------------------------|---------------------|
| **Communication** |
| BLE Support | ✅ | ✅ | ❌ |
| WiFi Support | ❌ | ❌ | ✅ (AP mode) |
| BLE Pairing Security | ✅ PIN 123456 | ✅ PIN 654321 | ❌ |
| Heartbeat Reporting | ✅ Every 5s | ✅ Every 5s | ❌ |
| **Hardware** |
| Hall Sensors (20 tiles) | ✅ | ✅ | ✅ |
| LED Strip (80 LEDs) | ✅ | ✅ | ✅ |
| Coin Timeout | ✅ 60s | ✅ 60s | ⚠️ 30s |
| Timeout Warning Flash | ✅ Accelerating + RED | ✅ Accelerating + RED | ❌ Constant |
| Watchdog Timer | ✅ 30s | ✅ 30s | ❌ |
| **Game Logic** |
| Tile Types | ❌ (Android) | ✅ 8 types | ❌ (Android) |
| Chance Cards | ❌ (Android) | ✅ 20 cards | ❌ (Android) |
| Score Calculation | ❌ (Android) | ✅ Full logic | ❌ (Android) |
| Winner Detection | ❌ (Android) | ✅ Auto-detect | ❌ (Android) |
| **Animations** |
| Basic Move Animation | ✅ | ✅ | ✅ |
| Elimination Animation | ❌ | ✅ 3× red blink | ❌ |
| Winner Celebration | ❌ | ✅ 20-25s (4 phases) | ❌ |
| Startup Rainbow | ✅ | ✅ | ❌ |
| **State Management** |
| Undo Support | ✅ | ✅ (with history) | ✅ |
| Reset Support | ✅ | ✅ | ✅ |
| Persistence (NVS) | ✅ | ✅ | ✅ |
| State Validation | ⚠️ Basic | ✅ Comprehensive | ❌ |
| **Advanced Features** |
| Command Queue | ❌ | ✅ std::queue | ❌ |
| Misplacement Detection | ✅ | ✅ Enhanced | ⚠️ Basic |
| Test Log Reporting | ❌ | ✅ Detailed JSON | ❌ |
| Board Unique ID | ❌ | ✅ Configurable | ❌ |
| Idle Dimming | ❌ | ✅ 5-min timeout | ❌ |
| **Compilation Status** |
| Tested & Working | ✅ ESP32 v3.3.4 | ⚠️ Not tested | ❌ Obsolete |

---

## 📂 File Details

### 1. sketch_ble.ino (727 lines) ⭐ **PRODUCTION**

**Purpose**: Main production firmware for physical game with Android app

**Key Functions** (24 total):
```cpp
// BLE Communication
void initBLE()
void handleBLECommand(const char* jsonStr)
void sendBLEResponse(const char* json)
void sendHeartbeat()  // NEW: Reports time remaining

// Command Handlers
void handleRoll(JsonDocument& doc)      // Receive roll from Android
void handleUndo(JsonDocument& doc)      // Undo last move
void handleReset()                      // Reset all players
void handleConfig(JsonDocument& doc)    // Set player count/colors

// Hardware
bool isCoinPresent(int tile)
void checkCoinPlacement()               // Detect coin on expected tile
void checkCoinTimeout()                 // 60s timeout with warnings
void scanAllTiles()                     // Misplacement detection

// LED Display
void setTileColor(int tile, uint32_t color)
void renderBackground()
void renderPlayers()
void animateMove(int fromTile, int toTile, uint32_t color)
void startupAnimation()                 // Rainbow sweep

// State Management
void saveGameState()
void loadGameState()
void sendStatus()
```

**Strengths**:
- ✅ Clean architecture (Android = brain, ESP32 = display)
- ✅ All recent improvements applied
- ✅ Compiled and tested
- ✅ Production-ready security
- ✅ Best UX (timeout warnings, heartbeat)

**Limitations**:
- ❌ No winner animation (happens on Android/live.html)
- ❌ No elimination animation
- ❌ No standalone test mode (requires Android)

---

### 2. sketch_ble_testmode.ino (1655 lines) 🧪 **TEST MODE**

**Purpose**: Test Mode 1 - Full game logic on ESP32 for distributed testing

**Key Functions** (60+ total, including):
```cpp
// All functions from sketch_ble.ino PLUS:

// Game Logic (unique to testmode)
struct TileDefinition BOARD[20]         // Complete board with tile types
struct ChanceCard CHANCE_CARDS[20]      // All 20 chance cards
void handleRoll(JsonDocument& doc)      // ENHANCED: Calculates scores
int calculateScoreChange(TileType type) // Tile effect logic
void applyChanceCard(int playerId)      // Random chance card selection

// Animations (unique to testmode)
void animatePlayerElimination(int playerId)  // 3× red blink across board
void animateWinner(int winnerId)        // 20-25s celebration (4 phases):
                                        //   - Flash (3×)
                                        //   - Disco strobe (20×)
                                        //   - Chase pattern (3 sweeps)
                                        //   - Pulsing fade (5×)

// Enhanced Reporting (unique to testmode)
void sendRollResponse(...)              // Detailed JSON with tile info
void sendTestLog(...)                   // Test Mode 1 log display
void sendUndoResponse(...)              // Undo confirmation with state
void sendCoinPlacedResponse(...)        // Enhanced coin placed event

// Advanced Features (unique to testmode)
void processCommandQueue()              // Command queuing system
void validateGameState()                // State consistency checks
void updateConnectionStatusLEDs()       // Board ID indicator LEDs
void resetIdleTimer()                   // 5-min idle dimming
bool isTrustedDevice(BLEAddress)        // MAC whitelist validation
```

**Unique Data Structures**:
```cpp
// Complete board definition
const TileDefinition BOARD[NUM_TILES] = {
  {1,  "Start Point",          TYPE_START},
  {2,  "Sunny Patch",          TYPE_PENALTY},
  {3,  "Rain Dock",            TYPE_WATER_DOCK},
  // ... all 20 tiles with types
};

// All 20 chance cards
const ChanceCard CHANCE_CARDS[20] = {
  {1,  "You fixed a tap leak",                    +2},
  {2,  "Rainwater harvested",                     +2},
  // ... all 20 cards with effects
};
```

**Strengths**:
- ✅ Complete standalone game (no Android needed for testing)
- ✅ Winner/elimination animations (SPECTACULAR!)
- ✅ Detailed test logging for debugging
- ✅ Command queue prevents race conditions
- ✅ Board unique ID for multi-board setups

**Limitations**:
- ❌ **Not tested with ESP32 v3.3.4** (may need compilation fixes)
- ❌ Larger code size (may not fit on some ESP32 variants)
- ❌ Duplicates game logic (maintenance burden)

**When to Use**:
- Test Mode 1: ESP32 hardware team testing board without Android
- Debugging LED animations and Hall sensors
- Demonstrating game without phone
- **NOT for production** (Android should control game logic)

---

### 3. sketch_enhanced.ino (696 lines) ⚠️ **OBSOLETE**

**Purpose**: Original WiFi AP version (before BLE implementation)

**Communication**: 
- Creates WiFi access point: `LASTDROP-ESP32` / `lastdrop123`
- Web server on port 80
- HTTP endpoints: `/roll`, `/undo`, `/reset`, `/config`, `/status`

**Key Functions**:
```cpp
// WiFi Communication
void handleRoot()        // Web UI
void handleRoll()        // HTTP POST /roll
void handleUndo()        // HTTP POST /undo
void handleReset()       // HTTP POST /reset
void handleConfig()      // HTTP POST /config
void handleStatus()      // HTTP GET /status

// Same hardware functions as sketch_ble.ino
// (scanAllTiles, checkCoinPlaced, animateMove, etc.)
```

**Why It's Obsolete**:
- ❌ WiFi conflicts with GoDice BLE connections
- ❌ Phone loses internet when connected to ESP32 AP
- ❌ No security (open WiFi)
- ❌ No modern improvements (watchdog, heartbeat, etc.)
- ❌ 30-second timeout (not 60s)
- ❌ Basic blinking (no acceleration warnings)

**Historical Value**:
- Shows evolution from WiFi → BLE architecture
- Some LED rendering code may be reusable

---

## 🔧 Recommended Function Additions

### Functions to ADD from `sketch_ble_testmode.ino` → `sketch_ble.ino`

#### 1. ⭐ **Winner Animation** (CRITICAL for production)
```cpp
void animateWinner(int winnerId) {
  // Phase 1: Flash (3 cycles, 400ms on / 200ms off)
  // Phase 2: Disco strobe (20 cycles, random colors)
  // Phase 3: Chase pattern (3 sweeps across 80 LEDs)
  // Phase 4: Pulsing fade (5 cycles, breathing effect)
  // Final: 3-second solid color hold
}
```
**Why**: Winner animation is the game's climax moment! Currently only happens on live.html (web), but ESP32 board should also celebrate.

**Lines**: ~105 lines (testmode lines 1335-1440)

**Trigger**: Add to `handleRoll()` when Android sends `"gameOver": true, "winnerId": X`

---

#### 2. ⭐ **Elimination Animation** (HIGH PRIORITY)
```cpp
void animatePlayerElimination(int playerId) {
  // 3× Blink pattern:
  //   - Turn ON player's LED across all 20 tiles
  //   - Hold 300ms
  //   - Turn OFF all player LEDs
  //   - Hold 300ms
  //   - Repeat 3 times
  // Total: ~1.8 seconds
}
```
**Why**: Dramatic visual feedback when player is eliminated. Makes elimination events memorable.

**Lines**: ~35 lines (testmode lines 1300-1335)

**Trigger**: Add to `handleRoll()` when Android sends `"playerEliminated": true, "playerId": X`

---

#### 3. 🔧 **Command Queue** (OPTIONAL - robustness)
```cpp
#include <queue>

std::queue<String> commandQueue;

void processCommandQueue() {
  if (!commandQueue.empty() && !waitingForCoin) {
    String cmd = commandQueue.front();
    commandQueue.pop();
    handleBLECommand(cmd.c_str());
  }
}

// In MyCallbacks::onWrite():
if (waitingForCoin && command != "undo" && command != "reset") {
  commandQueue.push(rxValue);  // Queue instead of process
} else {
  handleBLECommand(rxValue.c_str());
}
```
**Why**: Prevents race conditions if Android sends rapid commands. Testmode has this.

**Lines**: ~30 lines

**Benefit**: Better handling of multiple quick commands

---

#### 4. 🎨 **Enhanced Misplacement Reporting** (OPTIONAL)
```cpp
void scanAllTiles() {
  // Current: Simple scan
  // Enhanced: Include player positions, expected positions, tile names
  // Send detailed JSON with misplacement context
}
```
**Why**: Testmode's misplacement detection is more informative for debugging

**Lines**: ~20 lines modification

---

#### 5. 📊 **State Validation** (OPTIONAL - debugging)
```cpp
void validateGameState() {
  // Check for impossible states:
  // - Players on same tile
  // - Multiple coins detected
  // - Player tile out of range
  // Send warning to Android if issues found
}
```
**Why**: Helps catch hardware issues (faulty Hall sensors, wiring problems)

**Lines**: ~40 lines (testmode lines 1195-1235)

---

## 🚀 Implementation Recommendation

### Immediate Actions (Production Deployment)

#### Option A: Keep it Simple ✅ **RECOMMENDED**
**Use**: `sketch_ble.ino` as-is

**Add Only**:
1. ✅ Winner animation (~105 lines)
2. ✅ Elimination animation (~35 lines)

**Total Addition**: ~140 lines → ~870 lines total

**Rationale**:
- Animations are visual features players will notice
- Small code addition (fits easily in flash)
- Low risk (animations don't affect game logic)
- ESP32 still focuses on display, Android controls logic

---

#### Option B: Maximum Robustness
**Use**: `sketch_ble.ino` + enhancements

**Add**:
1. Winner animation (~105 lines)
2. Elimination animation (~35 lines)
3. Command queue (~30 lines)
4. State validation (~40 lines)

**Total Addition**: ~210 lines → ~940 lines total

**Rationale**:
- Full feature parity with testmode
- Better error handling
- Command queue prevents edge cases
- State validation catches hardware issues early

---

#### Option C: Hybrid Approach (NOT RECOMMENDED)
**Use**: `sketch_ble_testmode.ino` as production firmware

**Risks**:
- ❌ Duplicated game logic (maintenance nightmare)
- ❌ Android and ESP32 can get out of sync
- ❌ Larger code size
- ❌ More complex debugging
- ❌ Not tested with ESP32 v3.3.4

**Only Consider If**:
- You want true offline gameplay (no Android)
- You're building arcade-style standalone boards
- You can maintain two codebases

---

## 📝 Summary Table

| Scenario | Recommended File | Add From Testmode |
|----------|------------------|-------------------|
| **Production game with Android** | sketch_ble.ino | Winner + Elimination animations |
| **Test Mode 1 (ESP32-only testing)** | sketch_ble_testmode.ino | Use as-is |
| **Debugging hardware issues** | sketch_ble.ino | State validation |
| **Arcade/standalone mode** | sketch_ble_testmode.ino | Full game logic already there |
| **WiFi version** | ❌ None (obsolete) | Migrate to BLE |

---

## 🎯 Final Recommendation

### **USE: `sketch_ble.ino`** ✅

### **ADD: Winner + Elimination Animations**

**Why This is Best**:
1. ✅ Already compiled and tested with ESP32 v3.3.4
2. ✅ Has all recent security/UX improvements
3. ✅ Clean architecture (Android = brain, ESP32 = display)
4. ✅ Small code size leaves room for future features
5. ✅ Easy to maintain (no duplicated logic)

**What You're Missing Without Animations**:
- Winner celebration happens ONLY on live.html (web spectators see it)
- Physical players at the board don't see dramatic LED effects
- Elimination feels anticlimactic (LED just turns off)

**With Animations Added**:
- 🎉 Winner sees 20-25 second celebration on physical board
- 💀 Eliminated player gets dramatic 3× red flash goodbye
- 👥 All players get visual confirmation of game events
- 🎨 Board becomes more engaging and entertaining

---

## 📂 File Organization

Current structure:
```
ESP32 Program/
├── sketch_ble.ino              ← PRODUCTION (use this)
├── sketch_ble_testmode.ino     ← TEST MODE 1 (reference for animations)
├── sketch_enhanced.ino         ← OBSOLETE (can delete)
└── sketch_ble/
    └── sketch_ble.ino          ← COMPILED VERSION (auto-generated)
```

Recommended cleanup:
```
ESP32 Program/
├── sketch_ble_production.ino   ← Rename from sketch_ble.ino
├── sketch_ble_testmode.ino     ← Keep for Test Mode 1
└── sketch_ble/                 ← Arduino compile folder
    └── sketch_ble.ino
```

---

## 🔄 Next Steps

1. **Decide**: Do you want winner/elimination animations on physical board?
   - If YES → I'll add them to sketch_ble.ino
   - If NO → Use sketch_ble.ino as-is

2. **Test Mode**: Keep sketch_ble_testmode.ino for ESP32 hardware team testing

3. **Cleanup**: Delete or archive sketch_enhanced.ino (WiFi version obsolete)

4. **Deploy**: Upload sketch_ble.ino (with or without animations) to production boards

Let me know which option you prefer!
