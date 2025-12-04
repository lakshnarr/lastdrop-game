# Player Configuration Integration - Testing Checklist

## ✅ Pre-Testing Setup

### Android App
- [ ] Copy `local.properties.template` to `local.properties`
- [ ] Add your API key to `local.properties`
- [ ] Build app: `.\gradlew assembleDebug`
- [ ] Install app: `.\gradlew installDebug`

### ESP32 Firmware
- [ ] Open `sketch_ble_testmode.ino` in Arduino IDE
- [ ] Install libraries: `Adafruit NeoPixel`, `ArduinoJson`, ESP32 BLE
- [ ] Select board: "ESP32 Dev Module"
- [ ] Upload firmware (921600 baud)
- [ ] Note MAC address from Serial Monitor

---

## ✅ Test Scenario 1: 2 Players (Minimum)

### Configuration
1. [ ] Open Android app
2. [ ] Tap "How many players?" → Select **2 players**
3. [ ] Player 1 Setup:
   - [ ] Name: "Alice"
   - [ ] Color: **Red 🔴**
4. [ ] Player 2 Setup:
   - [ ] Name: "Bob"
   - [ ] Color: **Green 🟢**

### Expected Results
- [ ] Toast shows: "Players: Alice, Bob"
- [ ] Test log (if Test Mode 1 enabled) shows:
  ```
  📤 Sent CONFIG command: 2 players
     Player 1: #FF0000
     Player 2: #00FF00
  ⚙️ Configuration Applied
     Active players: 2
  ```
- [ ] ESP32 Serial Monitor shows:
  ```
  ⚙️ Processing CONFIG command...
    Active Players: 2
    Player 0 color: #FF0000 (0xFF0000)
    Player 1 color: #00FF00 (0x00FF00)
  ✓ Config applied
  ```

### LED Verification
- [ ] Only **2 player LEDs** active on ESP32
- [ ] Player 0 position: **Red LED** at tile 1
- [ ] Player 1 position: **Green LED** at tile 1
- [ ] Players 2-3: **No LEDs** (inactive)

---

## ✅ Test Scenario 2: 4 Players (Maximum)

### Configuration
1. [ ] Reset app or start new game
2. [ ] Select **4 players**
3. [ ] Player 1: "Alice" → **Red 🔴**
4. [ ] Player 2: "Bob" → **Green 🟢**
5. [ ] Player 3: "Charlie" → **Blue 🔵**
6. [ ] Player 4: "Diana" → **Yellow 🟡**

### Expected Results
- [ ] Config command sent with 4 colors
- [ ] ESP32 confirms 4 active players
- [ ] All 4 player LEDs active on ESP32
- [ ] Each LED matches selected color

### Color Verification
- [ ] Roll dice for Player 1 → **Red LED** animates
- [ ] Roll dice for Player 2 → **Green LED** animates
- [ ] Roll dice for Player 3 → **Blue LED** animates
- [ ] Roll dice for Player 4 → **Yellow LED** animates

---

## ✅ Test Scenario 3: 3 Players (Mixed Colors)

### Configuration
1. [ ] Select **3 players**
2. [ ] Player 1: "Eve" → **Yellow 🟡**
3. [ ] Player 2: "Frank" → **Blue 🔵**
4. [ ] Player 3: "Grace" → **Red 🔴**

### Expected Results
- [ ] Config sent: `["FFFF00", "0000FF", "FF0000"]`
- [ ] Player 0: **Yellow LED**
- [ ] Player 1: **Blue LED**
- [ ] Player 2: **Red LED**
- [ ] Player 3: **OFF** (inactive)

### Color Dropdown Verification
- [ ] Player 1 sees: Red, Green, Blue, Yellow
- [ ] Player 2 sees: Red, Green, Blue (Yellow removed)
- [ ] Player 3 sees: Green (Yellow, Blue removed)
- [ ] Each player gets unique color

---

## ✅ Test Scenario 4: Test Mode 1 Integration

### Setup
1. [ ] Upload `sketch_ble_testmode.ino` to ESP32
2. [ ] Connect ESP32 via BLE in Android app
3. [ ] Enable **Test Mode 1**
4. [ ] Configure 3 players (any colors)

### Config Command Test
- [ ] Test log shows config command sent
- [ ] Test log shows all 3 player colors
- [ ] Test log shows "Configuration Applied"

### Dice Roll Test
- [ ] Tap "Simulate Dice Roll"
- [ ] Test log shows roll processed for Player 0
- [ ] Test log shows tile movement
- [ ] Test log shows score change
- [ ] ESP32 Serial Monitor confirms roll

### LED Behavior
- [ ] Only configured players' LEDs animate
- [ ] Inactive player LEDs remain off
- [ ] Coin placement wait starts

---

## ✅ Test Scenario 5: Reset Game

### Before Reset
1. [ ] Configure 2 players
2. [ ] Roll dice several times
3. [ ] Note current positions and scores

### Reset Action
- [ ] Tap "Reset Score" button
- [ ] Confirm reset

### Expected Results
- [ ] ESP32 receives reset command
- [ ] All **active players** reset to tile 1, score 10
- [ ] **Inactive players** remain off
- [ ] Player colors **preserved** (not reset to defaults)
- [ ] Test log shows "Game Reset Complete"

---

## ✅ Test Scenario 6: Live HTML Integration

### Setup
1. [ ] Configure 2 players in Android
2. [ ] Start game
3. [ ] Open `live.html` in browser

### Verification
- [ ] live.html shows **2 tokens** (not 4)
- [ ] Token 1 color matches Player 1 selection
- [ ] Token 2 color matches Player 2 selection
- [ ] Roll dice in Android
- [ ] Token animates on live.html
- [ ] Token color remains consistent

---

## ✅ Test Scenario 7: BLE Disconnection/Reconnection

### Disconnect Test
1. [ ] Configure 3 players
2. [ ] Disconnect ESP32 (turn off or move away)
3. [ ] Roll dice in Android

### Expected Behavior
- [ ] Android shows "ESP32 not connected"
- [ ] Config command not sent (BLE unavailable)
- [ ] Game continues in Test Mode 2 (software only)

### Reconnect Test
1. [ ] Reconnect ESP32 (turn on or move closer)
2. [ ] BLE connection re-established
3. [ ] **Manually send config** (restart game or reset)

### Expected Behavior
- [ ] ESP32 receives config on reconnection
- [ ] Player colors restored
- [ ] LED count matches active players

---

## ✅ Test Scenario 8: Edge Cases

### Same Color Selection (should not happen)
- [ ] Verify dropdown removes selected colors
- [ ] Each player forced to choose unique color
- [ ] No duplicate color assignments possible

### Invalid Player Count
- [ ] Cannot select 1 player (minimum is 2)
- [ ] Cannot select 5+ players (maximum is 4)

### Missing Color Data
- [ ] If colors array missing, ESP32 uses defaults
- [ ] No crash or error

### Color Conversion
- [ ] "red" → `0xFF0000` (correct)
- [ ] "green" → `0x00FF00` (correct)
- [ ] "blue" → `0x0000FF` (correct)
- [ ] "yellow" → `0xFFFF00` (correct)

---

## ✅ Performance Tests

### Response Time
- [ ] Config sent within **1 second** of player setup
- [ ] ESP32 confirms within **500ms** of receiving config
- [ ] No noticeable delay in game start

### LED Update Speed
- [ ] LEDs turn off immediately for inactive players
- [ ] Active player LEDs turn on immediately
- [ ] No flickering or glitches

### BLE Data Size
- [ ] Config JSON < 512 bytes (fits in BLE packet)
- [ ] No truncation or corruption

---

## ✅ Error Handling Tests

### ESP32 Not Connected
- [ ] Android detects no BLE connection
- [ ] Config command skipped gracefully
- [ ] Warning logged in test mode
- [ ] Game continues without ESP32

### Invalid JSON
- [ ] ESP32 receives malformed config
- [ ] ESP32 sends error response
- [ ] Android handles error gracefully

### Player Count Out of Range
- [ ] Config with 0 players → ESP32 rejects
- [ ] Config with 5 players → ESP32 rejects
- [ ] Error response sent to Android

---

## ✅ Documentation Verification

### Code Comments
- [ ] `sendConfigToESP32()` has clear comments
- [ ] `handleConfig()` explains color conversion
- [ ] Color mapping documented in both files

### Markdown Files
- [ ] `PLAYER_CONFIG_INTEGRATION.md` complete
- [ ] `PLAYER_CONFIG_SUMMARY.md` created
- [ ] `.github/copilot-instructions.md` updated
- [ ] All examples accurate

---

## 🎯 Final Validation

### Android
- [ ] No compilation errors
- [ ] No lint warnings
- [ ] Builds successfully
- [ ] Installs without issues

### ESP32
- [ ] Code compiles in Arduino IDE
- [ ] Uploads to ESP32 successfully
- [ ] Serial Monitor shows boot sequence
- [ ] BLE advertises correctly

### Integration
- [ ] Android connects to ESP32 via BLE
- [ ] Config command sends successfully
- [ ] ESP32 confirms configuration
- [ ] LEDs match player selections
- [ ] live.html displays correct tokens
- [ ] Game plays normally with configured players

---

## 📝 Sign-Off

**Tester Name**: ___________________________  
**Test Date**: ___________________________  
**Android App Version**: ___________________________  
**ESP32 Firmware Version**: ___________________________  
**Result**: ☐ PASS  ☐ FAIL  

**Notes**:
_____________________________________________
_____________________________________________
_____________________________________________

---

## 🐛 Known Issues

(List any issues found during testing)

1. ___________________________________________
2. ___________________________________________
3. ___________________________________________

---

## 📊 Test Summary

| Scenario | Status | Notes |
|----------|--------|-------|
| 2 Players | ☐ Pass ☐ Fail | |
| 4 Players | ☐ Pass ☐ Fail | |
| 3 Players Mixed | ☐ Pass ☐ Fail | |
| Test Mode 1 | ☐ Pass ☐ Fail | |
| Reset Game | ☐ Pass ☐ Fail | |
| Live HTML | ☐ Pass ☐ Fail | |
| BLE Reconnect | ☐ Pass ☐ Fail | |
| Edge Cases | ☐ Pass ☐ Fail | |
| Performance | ☐ Pass ☐ Fail | |
| Error Handling | ☐ Pass ☐ Fail | |

**Overall Status**: ☐ READY FOR PRODUCTION  ☐ NEEDS FIXES
