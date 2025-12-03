# BLE Communication Protocol

**Last Drop - Android ↔ ESP32 Data Exchange**

This document describes all data variables and JSON structures transmitted between the Android app and ESP32 board via Bluetooth Low Energy (BLE).

---

## BLE Service Configuration

- **Service UUID**: `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART Service)
- **RX Characteristic**: `6e400002-b5a3-f393-e0a9-e50e24dcca9e` (Android → ESP32)
- **TX Characteristic**: `6e400003-b5a3-f393-e0a9-e50e24dcca9e` (ESP32 → Android)
- **Device Name**: `LASTDROP-ESP32` (production) or `LASTDROP-ESP32-TESTMODE` (test firmware)
- **Data Format**: JSON strings
- **Max Payload**: 512 bytes (BLE MTU limitation)

---

## Android → ESP32 (Commands)

The Android app sends JSON commands to control the ESP32 board. All commands use the `command` field to specify the operation type.

### 1. ROLL Command

Sent when a player rolls the dice. ESP32 uses this to animate LEDs and track game state.

**Structure:**
```json
{
  "command": "roll",
  "playerId": 0,
  "playerName": "Player 1",
  "diceValue": 4,
  "currentTile": 5,
  "expectedTile": 9,
  "color": "FF0000"
}
```

**Fields:**

| Field | Type | Range/Values | Description |
|-------|------|--------------|-------------|
| `command` | string | `"roll"` | Command identifier |
| `playerId` | integer | 0-3 | Player index (0-based) |
| `playerName` | string | Any | Player's display name |
| `diceValue` | integer | 1-6 | Dice roll result (averaged if using 2 dice) |
| `currentTile` | integer | 0-19 | Current player position (ESP32 uses 0-based indexing) |
| `expectedTile` | integer | 0-19 | Expected landing tile after movement |
| `color` | string | Hex RGB | Player's coin color: `"FF0000"` (red), `"00FF00"` (green), `"0000FF"` (blue), `"FFFF00"` (yellow) |

**Notes:**
- Android converts 1-based tile positions to 0-based before sending
- In 2-dice mode, `diceValue` is the average of both dice
- ESP32 animates LEDs from `currentTile` to `expectedTile`
- ESP32 waits for Hall sensor confirmation of coin placement

---

### 2. UNDO Command

Sent when a player undoes their last move.

**Structure:**
```json
{
  "command": "undo",
  "playerId": 0,
  "fromTile": 9,
  "toTile": 5
}
```

**Fields:**

| Field | Type | Range/Values | Description |
|-------|------|--------------|-------------|
| `command` | string | `"undo"` | Command identifier |
| `playerId` | integer | 0-3 | Player index |
| `fromTile` | integer | 0-19 | Current position (where player is now) |
| `toTile` | integer | 0-19 | Position to restore (where player was before) |

**Notes:**
- ESP32 reverses the LED animation
- Score restoration is handled by Android
- 5-second confirmation window in Android UI

---

### 3. CONFIG Command

Sent at game start to configure active players and their colors.

**Structure:**
```json
{
  "command": "config",
  "playerCount": 3,
  "colors": ["FF0000", "00FF00", "0000FF"]
}
```

**Fields:**

| Field | Type | Range/Values | Description |
|-------|------|--------------|-------------|
| `command` | string | `"config"` | Command identifier |
| `playerCount` | integer | 2-4 | Number of active players |
| `colors` | array | Hex RGB strings | Player colors in order (Player 1, 2, 3, 4) |

**Color Mapping:**

| Color Name | Hex Value | RGB |
|------------|-----------|-----|
| Red | `"FF0000"` | (255, 0, 0) |
| Green | `"00FF00"` | (0, 255, 0) |
| Blue | `"0000FF"` | (0, 0, 255) |
| Yellow | `"FFFF00"` | (255, 255, 0) |

**Notes:**
- ESP32 only powers LEDs for active players
- Inactive player LEDs remain off
- Colors must match user selection in Android app

---

### 4. RESET Command

Sent to reset the entire game state.

**Structure:**
```json
{
  "command": "reset"
}
```

**Fields:**

| Field | Type | Range/Values | Description |
|-------|------|--------------|-------------|
| `command` | string | `"reset"` | Command identifier |

**Notes:**
- Resets all player positions to tile 0 (Start)
- Resets all scores to 10 drops
- Clears LED animations
- Android confirms with user before sending

---

## ESP32 → Android (Events)

The ESP32 sends JSON event notifications to inform Android of state changes. All events use the `event` field to specify the event type.

### 1. ready Event

Sent when ESP32 initializes and BLE connection is established.

**Structure:**
```json
{
  "event": "ready",
  "message": "ESP32 initialized",
  "firmware": "v1.0 Test Mode"
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"ready"` |
| `message` | string | Startup confirmation message |
| `firmware` | string | Firmware version identifier |

---

### 2. roll_processed Event

Sent after ESP32 processes a roll command. Includes complete game state update.

**Structure (Full Example):**
```json
{
  "event": "roll_processed",
  "playerId": 0,
  "movement": {
    "from": 5,
    "to": 9
  },
  "tile": {
    "name": "Marsh Land",
    "type": "CHANCE",
    "effect": 0
  },
  "score": {
    "old": 8,
    "new": 9,
    "change": 1
  },
  "chanceCard": {
    "number": 7,
    "description": "You cleaned a riverbank",
    "effect": 1
  },
  "player": {
    "score": 9,
    "position": 9,
    "alive": true,
    "eliminated": false
  }
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"roll_processed"` |
| `playerId` | integer | Player index (0-3) |
| **movement** | object | Movement details |
| `movement.from` | integer | Starting tile (0-19) |
| `movement.to` | integer | Landing tile (0-19) |
| **tile** | object | Landed tile information |
| `tile.name` | string | Tile name (e.g., "Rain Dock", "Storm Zone") |
| `tile.type` | string | Tile type: `START`, `NORMAL`, `CHANCE`, `BONUS`, `PENALTY`, `DISASTER`, `WATER_DOCK`, `SUPER_DOCK` |
| `tile.effect` | integer | Score change from tile (-2 to +3) |
| **score** | object | Score tracking |
| `score.old` | integer | Score before this turn (0-10) |
| `score.new` | integer | Score after tile effect (0-10) |
| `score.change` | integer | Score delta from tile (-2 to +3) |
| **chanceCard** | object | Chance card drawn (only if `tile.type == "CHANCE"`) |
| `chanceCard.number` | integer | Card number (1-20) |
| `chanceCard.description` | string | Card text |
| `chanceCard.effect` | integer | Score change from card (-2 to +2) |
| **player** | object | Final player state |
| `player.score` | integer | Final score after all effects (0-10) |
| `player.position` | integer | Final position (0-19) |
| `player.alive` | boolean | Still in game? (false if score ≤ 0) |
| `player.eliminated` | boolean | Eliminated this turn? |

**Tile Types & Effects:**

| Type | Description | Effect |
|------|-------------|--------|
| `START` | Starting position | 0 |
| `NORMAL` | No effect | 0 |
| `CHANCE` | Draw chance card | 0 (card determines effect) |
| `BONUS` | Gain drops | +1 to +3 |
| `PENALTY` | Lose drops | -1 to -2 |
| `DISASTER` | Major loss | -2 |
| `WATER_DOCK` | Gain drops | +1 to +2 |
| `SUPER_DOCK` | Major gain | +3 |

**Notes:**
- `chanceCard` object only present when landing on CHANCE tiles
- `player.eliminated` is true only on the turn elimination occurs
- Android uses this data to update UI and database

---

### 3. coin_placed Event

Sent when Hall sensor detects coin placement.

**Structure:**
```json
{
  "event": "coin_placed",
  "playerId": 0,
  "tile": 9,
  "verified": true
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"coin_placed"` |
| `playerId` | integer | Player index (0-3) |
| `tile` | integer | Tile where coin detected (0-19) |
| `verified` | boolean | Hall sensor confirmation status |

**Notes:**
- Triggers Android to advance to next player's turn
- Android pushes state to live.html for web display
- 30-second timeout if coin not placed

---

### 4. coin_timeout Event

Sent when player doesn't place coin within timeout period.

**Structure:**
```json
{
  "event": "coin_timeout",
  "playerId": 0,
  "tile": 9,
  "timeout": 30
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"coin_timeout"` |
| `playerId` | integer | Player index (0-3) |
| `tile` | integer | Expected tile (0-19) |
| `timeout` | integer | Timeout duration in seconds (default: 30) |

**Notes:**
- Android continues game flow despite timeout
- State still pushed to live.html
- No penalty for timeout (implementation choice)

---

### 5. undo_complete Event

Sent after undo operation completes.

**Structure:**
```json
{
  "event": "undo_complete",
  "playerId": 0,
  "movement": {
    "from": 9,
    "to": 5
  },
  "score": {
    "restored": 8
  }
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"undo_complete"` |
| `playerId` | integer | Player index (0-3) |
| **movement** | object | Reversed movement |
| `movement.from` | integer | Current position (0-19) |
| `movement.to` | integer | Restored position (0-19) |
| **score** | object | Score restoration |
| `score.restored` | integer | Score restored to (0-10) |

**Notes:**
- Android updates UI to reflect undo
- Database logs undo event
- Only last move can be undone

---

### 6. reset_complete Event

Sent after game reset completes.

**Structure:**
```json
{
  "event": "reset_complete",
  "players": [
    { "id": 0, "score": 10, "position": 0 },
    { "id": 1, "score": 10, "position": 0 },
    { "id": 2, "score": 10, "position": 0 }
  ]
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"reset_complete"` |
| `players` | array | Array of all player states |
| `players[].id` | integer | Player index (0-3) |
| `players[].score` | integer | Reset score (always 10) |
| `players[].position` | integer | Reset position (always 0) |

**Notes:**
- All players reset to identical state
- Android clears UI and database
- Game starts fresh

---

### 7. config_complete Event

Sent after configuration applied.

**Structure:**
```json
{
  "event": "config_complete",
  "playerCount": 3,
  "colors": ["FF0000", "00FF00", "0000FF"]
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"config_complete"` |
| `playerCount` | integer | Number of active players (2-4) |
| `colors` | array | Player colors confirmed |

**Notes:**
- Confirms ESP32 ready for game start
- Android can proceed with first roll

---

### 8. misplacement_scan Event

Sent when ESP32 detects coin placement errors during validation scan.

**Structure:**
```json
{
  "event": "misplacement_scan",
  "errors": [
    {
      "tile": 5,
      "tileName": "Storm Zone",
      "issue": "Unexpected coin detected"
    },
    {
      "tile": 12,
      "tileName": "Waste Dump",
      "issue": "Coin missing for active player"
    }
  ]
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"misplacement_scan"` |
| `errors` | array | Array of detected issues |
| `errors[].tile` | integer | Tile with issue (0-19) |
| `errors[].tileName` | string | Human-readable tile name |
| `errors[].issue` | string | Description of problem |

**Common Issues:**
- `"Unexpected coin detected"` - Coin on wrong tile
- `"Coin missing for active player"` - Expected coin not found
- `"Multiple coins on same tile"` - Hardware malfunction

**Notes:**
- Helps debug physical setup issues
- Android logs errors for review
- Game can continue despite misplacement

---

### 9. error Event

Sent when ESP32 encounters an error.

**Structure:**
```json
{
  "event": "error",
  "message": "Invalid command format"
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"error"` |
| `message` | string | Error description |

**Common Errors:**
- `"Invalid command format"` - Malformed JSON
- `"Unknown command"` - Command type not recognized
- `"Player index out of range"` - Invalid playerId
- `"Tile index out of range"` - Invalid tile number
- `"BLE buffer overflow"` - Payload too large

**Notes:**
- Android logs error and notifies user
- Game state not modified on error
- User can retry operation

---

### 10. status_report Event

Sent when Android requests ESP32 status.

**Structure:**
```json
{
  "event": "status_report",
  "connected": true,
  "waitingForCoin": false
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `event` | string | Event identifier: `"status_report"` |
| `connected` | boolean | BLE connection active |
| `waitingForCoin` | boolean | Waiting for coin placement |

**Notes:**
- Used for debugging connection issues
- Android can query status anytime
- Helps diagnose stuck states

---

## Data Flow Examples

### Example 1: Normal Dice Roll Sequence

1. **Android sends:**
   ```json
   {"command": "roll", "playerId": 0, "playerName": "Alice", 
    "diceValue": 4, "currentTile": 5, "expectedTile": 9, "color": "FF0000"}
   ```

2. **ESP32 responds:**
   ```json
   {"event": "roll_processed", "playerId": 0, 
    "movement": {"from": 5, "to": 9},
    "tile": {"name": "Marsh Land", "type": "CHANCE", "effect": 0},
    "score": {"old": 8, "new": 9, "change": 1},
    "chanceCard": {"number": 7, "description": "You cleaned a riverbank", "effect": 1},
    "player": {"score": 9, "position": 9, "alive": true, "eliminated": false}}
   ```

3. **ESP32 waits for coin, then sends:**
   ```json
   {"event": "coin_placed", "playerId": 0, "tile": 9, "verified": true}
   ```

4. **Android updates UI and pushes to live.html**

---

### Example 2: Player Elimination

1. **Android sends roll for player with 2 drops landing on DISASTER tile:**
   ```json
   {"command": "roll", "playerId": 1, "playerName": "Bob", 
    "diceValue": 3, "currentTile": 4, "expectedTile": 7, "color": "00FF00"}
   ```

2. **ESP32 responds with elimination:**
   ```json
   {"event": "roll_processed", "playerId": 1,
    "movement": {"from": 4, "to": 7},
    "tile": {"name": "Oil Spill Bay", "type": "DISASTER", "effect": -2},
    "score": {"old": 2, "new": 0, "change": -2},
    "player": {"score": 0, "position": 7, "alive": false, "eliminated": true}}
   ```

3. **Android shows elimination notification and removes player from turn rotation**

---

### Example 3: Game Configuration

1. **Android sends at game start:**
   ```json
   {"command": "config", "playerCount": 3, 
    "colors": ["FF0000", "00FF00", "0000FF"]}
   ```

2. **ESP32 confirms:**
   ```json
   {"event": "config_complete", "playerCount": 3, 
    "colors": ["FF0000", "00FF00", "0000FF"]}
   ```

3. **Android enables game controls for 3 players**

---

## Test Mode Considerations

### Test Mode 1: ESP32 Board Only
- Android sends virtual dice rolls (manually selected or random)
- ESP32 processes with full game logic
- All events logged to Android console with color coding

### Test Mode 2: Android + Web Only
- No ESP32 communication
- Android processes game logic locally
- Bypasses `sendRollToESP32()` calls
- State pushed directly to live.html

---

## Protocol Version

**Current Version:** 1.0  
**Last Updated:** December 3, 2025  
**Compatible Firmware:** sketch_ble.ino, sketch_ble_testmode.ino  
**Compatible Android:** MainActivity.kt (build 1.0)

---

## Security Notes

- **MAC Address Filtering**: Android can whitelist trusted ESP32 devices via `TRUSTED_ESP32_ADDRESSES` array
- **Pairing**: Optional PIN-based pairing (default disabled, configurable via `BLE_PAIRING_ENABLED`)
- **Encryption**: BLE link layer encryption used for production firmware
- **No Authentication**: Test mode firmware does not require authentication

---

## Debugging

### Enable BLE Logging (Android):
- Test Mode 1 automatically logs all BLE traffic to console
- Color-coded messages with timestamps
- Separators between requests/responses

### Enable Serial Monitor (ESP32):
```cpp
Serial.begin(115200);
// Watch Serial Monitor in Arduino IDE
```

### Common Issues:

| Issue | Cause | Solution |
|-------|-------|----------|
| No `ready` event | BLE connection failed | Check device name, restart ESP32 |
| JSON parse errors | Malformed payload | Validate JSON structure |
| Timeout on coin placement | Hall sensor issue | Check wiring, magnet strength |
| Misplacement events | Coins on wrong tiles | Verify physical setup matches game state |

---

## Future Protocol Extensions

Potential additions for future versions:

- **Lap completion detection** - `lap_complete` event when player completes board circuit
- **Multi-board support** - `boardId` field for multiple physical boards
- **Haptic feedback** - Vibration commands for player notifications
- **Sound effects** - Audio cue triggers from ESP32
- **Advanced analytics** - Detailed timing and sensor data
- **Network sync** - Multi-device game synchronization

---

**End of Protocol Documentation**
