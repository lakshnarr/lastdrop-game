# ESP32 Physical Board Integration - Last Drop

## System Architecture

```
GoDice → MainActivity_COMPLETE.kt → {
    ├─→ ESP32 (192.168.4.1) - Physical LED Board
    │   └─→ Hall Sensors detect coin placement
    │       └─→ Confirms back to MainActivity
    │           └─→ Triggers animation on live.html
    │
    └─→ live.html (Server) - Visual Display
        └─→ Shows dice animation + token movement
}
```

## Communication Protocol

### 1. Dice Roll Flow (MainActivity → ESP32 → MainActivity → Server)

**Step 1: Dice Roll Detected**
- GoDice sends value to MainActivity_COMPLETE.kt
- In 2-dice mode: avg = (dice1 + dice2) / 2

**Step 2: Send to ESP32**
```
POST http://192.168.4.1/roll
{
  "playerId": 0,           // 0-3
  "playerName": "Alice",
  "diceValue": 4,          // ALWAYS send avg (even in 2-dice mode)
  "currentTile": 5,        // Before movement
  "expectedTile": 9,       // After movement (calculated by app)
  "color": "red"           // Player color
}
```

**Step 3: ESP32 Response**
```
{
  "status": "ok",
  "ledTile": 9,
  "blinking": true,
  "message": "Waiting for coin placement"
}
```

**Step 4: ESP32 Detects Coin Placement**
```
Callback to http://<phone-ip>:8080/coin-placed
{
  "playerId": 0,
  "tile": 9,
  "verified": true,
  "timestamp": 1234567890
}
```

**Step 5: MainActivity sends to live.html**
```
POST https://lastdrop.earth/api/live_push.php
{
  "players": [...],
  "lastEvent": {
    "dice1": 3,
    "dice2": 5,
    "avg": 4,
    "coinPlaced": true,    // NEW FLAG
    ...
  }
}
```

### 2. Undo Flow

**Step 1: User presses Undo**
```
POST http://192.168.4.1/undo
{
  "playerId": 0,
  "fromTile": 9,
  "toTile": 5
}
```

**Step 2: ESP32 reverses LED**
- Animates LED back to original tile
- Blinks at original position
- Waits for coin replacement

**Step 3: Coin placed, confirms back**

**Step 4: MainActivity updates server with undo flag**

### 3. Coin Misplacement Detection

**Every turn start, ESP32 scans all 20 tiles:**
```
POST http://<phone-ip>:8080/misplacement
{
  "errors": [
    {
      "playerId": 0,
      "expectedTile": 5,
      "actualTile": 7,
      "color": "red"
    },
    {
      "playerId": 2,
      "expectedTile": 3,
      "actualTile": null,  // coin missing
      "color": "blue"
    }
  ]
}
```

**MainActivity shows alert:**
"Please fix coin positions:
- Red coin: Move from Tile 7 to Tile 5
- Blue coin: Place on Tile 3"

**ESP32 blinks correction LEDs** until all coins are correct.

## WiFi Setup

### ESP32 Creates Access Point
- **SSID:** LASTDROP-ESP32
- **Password:** lastdrop123
- **IP:** 192.168.4.1
- **Port:** 80

### Android App Connects
1. Automatically connects to ESP32 AP
2. Phone creates hotspot for HTTP callbacks
3. Phone IP communicated to ESP32 on first connect

## Enhanced Features

### 1. Turn Timeout
- If coin not placed within 30 seconds → auto-skip turn
- MainActivity shows countdown timer
- ESP32 stops blinking, returns to idle

### 2. Multi-Coin Detection
- Hall sensors detect multiple coins on same tile
- ESP32 can differentiate based on timing
- Alert if >4 coins detected (impossible state)

### 3. Battery Monitoring
- ESP32 monitors power level
- Warns at 20% battery
- Auto-saves game state on low battery

### 4. Visual Feedback Modes

**Kid Mode:**
- Slower LED animations
- Rainbow effects on correct placement
- Sound effects (if buzzer added)

**Tournament Mode:**
- Strict timing
- No hints
- Faster gameplay

### 5. Game State Persistence
- ESP32 stores current game state in flash
- Recovery on power loss
- Export/import game state via JSON

## Error Handling

### Connection Lost
1. MainActivity retries 3 times
2. If ESP32 unreachable → fallback to software-only mode
3. Game continues on live.html without physical board

### Sensor Failure
1. ESP32 detects stuck/dead sensors
2. Reports to MainActivity
3. Allows manual override for that tile

### Sync Errors
1. Periodic full state sync (every 5 turns)
2. Compare MainActivity state vs ESP32 state
3. Resolve conflicts (MainActivity is source of truth)

## Physical Board Enhancements

### Recommended Additions

1. **OLED Display (128x64)**
   - Shows current player
   - Shows dice roll value
   - Shows water count
   - Connection status

2. **Buzzer/Speaker**
   - Sound on dice roll
   - Tone on correct coin placement
   - Alert on misplacement

3. **RGB LED Indicator**
   - Green: Connected & ready
   - Yellow: Waiting for coin
   - Red: Misplacement error
   - Blue: Rolling/animating

4. **Reset Button**
   - Long press: Reset game
   - Short press: Skip waiting state

5. **Infrared Sensors (backup)**
   - Detect coin above tile (before placement)
   - Helps with positioning

## Testing Protocol

### Unit Tests
- Each sensor individually
- LED strip sections
- WiFi connection stability
- HTTP endpoint responses

### Integration Tests
- Full game simulation (4 players, 20 turns)
- Rapid dice rolls
- Multiple undo operations
- Intentional misplacements

### Stress Tests
- 100+ consecutive rolls
- All 4 players active
- WiFi interference simulation
- Power fluctuation tests

## Future Enhancements

1. **Bluetooth BLE** (alternative to WiFi)
   - Lower power consumption
   - Simpler pairing
   - No AP mode needed

2. **Mobile App for Board Control**
   - Configure LED brightness
   - Test individual tiles
   - View sensor diagnostics

3. **Multi-Board Support**
   - Connect multiple ESP32 boards
   - Tournament mode (4 boards = 16 players)

4. **Cloud Sync**
   - ESP32 → MQTT → Cloud
   - Remote spectating
   - Leaderboards

5. **AR Integration**
   - Phone camera overlays
   - Virtual effects on physical board
   - Tutorial mode
