# Player Configuration Feature - Quick Reference

## What Was Implemented

✅ **Android sends player count and colors to ESP32** after player setup  
✅ **ESP32 only powers LEDs for active players** (2-4 players)  
✅ **LED colors match player selections** (red, green, blue, yellow)  
✅ **Configuration already sent to live.html** (no changes needed)  

---

## How It Works

### 1. Player Setup in Android
```
User selects: 3 players
  → Player 1: Alice (Red)
  → Player 2: Bob (Green)  
  → Player 3: Charlie (Blue)
```

### 2. Android Sends Config to ESP32
```json
{
  "command": "config",
  "playerCount": 3,
  "colors": ["FF0000", "00FF00", "0000FF"]
}
```

### 3. ESP32 Configures LEDs
```
Player 0: Red LED     (Alice)
Player 1: Green LED   (Bob)
Player 2: Blue LED    (Charlie)
Player 3: OFF         (inactive)
```

### 4. ESP32 Confirms
```json
{
  "event": "config_complete",
  "playerCount": 3
}
```

---

## Test It

### Option 1: Test Mode 1 (ESP32 Board Testing)
1. Upload `sketch_ble_testmode.ino` to ESP32
2. Open Android app, configure 3 players
3. Enable Test Mode 1
4. Check test log shows:
   ```
   📤 Sent CONFIG command: 3 players
      Player 1: #FF0000
      Player 2: #00FF00
      Player 3: #0000FF
   ⚙️ Configuration Applied
   ```
5. Simulate dice rolls - only 3 LEDs should glow

### Option 2: Physical Board
1. Connect ESP32 via BLE
2. Configure players in Android
3. Roll dice for each player
4. Verify LED colors match your selections

---

## Files Changed

**Android**: `MainActivity.kt`
- Added `sendConfigToESP32()` (line ~2407)
- Updated `askPlayerName()` to call config
- Added "config_complete" event handler

**ESP32**: `sketch_ble_testmode.ino`
- Added `activePlayerCount` variable
- Added `handleConfig()` function
- Updated `handleReset()` to use activePlayerCount
- Updated roll validation

**Documentation**:
- `PLAYER_CONFIG_INTEGRATION.md` - Full technical docs
- `PLAYER_CONFIG_SUMMARY.md` - This quick reference

---

## Color Mapping

| Android | Hex Code | ESP32 RGB |
|---------|----------|-----------|
| "red" | FF0000 | 0xFF0000 |
| "green" | 00FF00 | 0x00FF00 |
| "blue" | 0000FF | 0x0000FF |
| "yellow" | FFFF00 | 0xFFFF00 |

---

## Troubleshooting

**Q: ESP32 doesn't receive config?**  
A: Check BLE connection status. Config sent only if `esp32Connected == true`

**Q: All 4 LEDs still glow?**  
A: Ensure you uploaded updated `sketch_ble_testmode.ino` firmware

**Q: Colors don't match?**  
A: Check Serial Monitor for color values. Verify hex conversion.

**Q: Test log doesn't show config?**  
A: Enable Test Mode 1 before configuring players

---

## Production Firmware Note

⚠️ **`sketch_ble.ino` (production) NOT YET UPDATED**

Apply the same changes to production firmware after testing:
1. Add `activePlayerCount` variable
2. Add `handleConfig()` function  
3. Update `handleReset()` and `handleRoll()`

---

**Status**: ✅ Ready for testing  
**Next Step**: Upload firmware and test with physical board
