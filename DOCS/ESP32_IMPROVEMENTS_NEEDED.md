# ESP32 Firmware - Critical Improvements Needed

## Priority 1: Security & Authentication

### 1.1 Add BLE Pairing with PIN (SECURITY.md documents this but code doesn't implement it)

**Location**: `sketch_ble_testmode.ino` after `BLEDevice::init(DEVICE_NAME);` (line ~266)

**Add**:
```cpp
// ==================== BLE SECURITY CONFIGURATION ====================
#define BLE_PAIRING_ENABLED true    // Set false to disable pairing
#define BLE_PAIRING_PIN 123456       // Change to your own PIN

void initBLE() {
  BLEDevice::init(DEVICE_NAME);
  
  Serial.print("ESP32 MAC Address: ");
  Serial.println(BLEDevice::getAddress().toString().c_str());
  
  #if BLE_PAIRING_ENABLED
    BLESecurity *pSecurity = new BLESecurity();
    pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
    pSecurity->setCapability(ESP_IO_CAP_OUT); // Display only (shows PIN in Serial)
    pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    
    uint32_t passkey = BLE_PAIRING_PIN;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));
    
    Serial.printf("✓ BLE Security Enabled - PIN: %d\n", BLE_PAIRING_PIN);
    Serial.println("  Android will prompt for PIN on first connection\n");
  #else
    Serial.println("⚠️  BLE Security DISABLED - Any device can connect!\n");
  #endif
  
  // ... rest of existing BLE setup
}
```

**Why**: Prevents unauthorized devices from connecting to your physical board.

---

### 1.2 Add MAC Address Whitelist (Optional but Recommended)

**Location**: Add near top of file after includes

**Add**:
```cpp
// ==================== ANDROID DEVICE WHITELIST ====================
#define MAC_FILTERING_ENABLED false  // Set true to enable
const String TRUSTED_ANDROID_MACS[] = {
  "AA:BB:CC:DD:EE:FF",  // Your phone's BLE MAC address
  "11:22:33:44:55:66"   // Second trusted device
};
const int NUM_TRUSTED_DEVICES = 2;

bool isTrustedDevice(BLEAddress address) {
  if (!MAC_FILTERING_ENABLED) return true;
  
  String addrStr = address.toString().c_str();
  for (int i = 0; i < NUM_TRUSTED_DEVICES; i++) {
    if (addrStr.equalsIgnoreCase(TRUSTED_ANDROID_MACS[i])) {
      return true;
    }
  }
  return false;
}
```

**Modify `MyServerCallbacks::onConnect()`**:
```cpp
void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t* param) {
  BLEAddress clientAddr = BLEAddress(param->connect.remote_bda);
  
  if (!isTrustedDevice(clientAddr)) {
    Serial.printf("❌ REJECTED untrusted device: %s\n", clientAddr.toString().c_str());
    pServer->disconnect(param->connect.conn_id);
    return;
  }
  
  deviceConnected = true;
  Serial.printf("✓ BLE Client Connected: %s\n", clientAddr.toString().c_str());
}
```

---

## Priority 2: Timeout & Timing Issues

### 2.1 Increase COIN_TIMEOUT for Winner Animation

**Problem**: Winner animation = 20-25s, current timeout = 30s. Barely enough time!

**Location**: Line 161

**Change**:
```cpp
const unsigned long COIN_TIMEOUT = 60000; // 60 seconds (was 30)
```

**Rationale**:
- Elimination animation: ~1.8s
- Winner animation: ~20-25s
- Total animation time: ~22-27s
- Player needs ~10-15s to physically place coin
- 60s provides comfortable buffer

---

### 2.2 Add Configurable Timeout Command

**Add to command handler** (after line 327):
```cpp
} else if (strcmp(command, "set_timeout") == 0) {
  handleSetTimeout(doc);
}
```

**Add function**:
```cpp
void handleSetTimeout(JsonDocument& doc) {
  int timeout = doc["timeout"] | 30;  // Default 30s
  COIN_TIMEOUT = timeout * 1000;  // Convert to milliseconds
  
  Serial.printf("⏱️  Coin timeout set to %d seconds\n", timeout);
  
  StaticJsonDocument<128> response;
  response["event"] = "timeout_updated";
  response["timeout"] = timeout;
  
  String json;
  serializeJson(response, json);
  sendBLEResponse(json.c_str());
}
```

**Android sends**: `{"command": "set_timeout", "timeout": 60}`

---

## Priority 3: User Experience Improvements

### 3.1 Add Visual Feedback for Timeout Warning

**Problem**: Players don't know how much time remains for coin placement.

**Add to `loop()` function** (before line 1226):
```cpp
// Visual timeout warning at 10 seconds remaining
if (waitingForCoin && expectedTile >= 1) {
  unsigned long elapsed = millis() - coinWaitStartTime;
  unsigned long remaining = COIN_TIMEOUT - elapsed;
  
  // Last 10 seconds: flash faster
  if (remaining < 10000 && remaining > 5000) {
    if (millis() - lastBlinkTime > 250) {  // 250ms blink (was 500ms)
      blinkState = !blinkState;
      lastBlinkTime = millis();
      setTileColor(expectedTile, blinkState ? PLAYER_COLORS[currentPlayer] : 0x000000);
      strip.show();
    }
  }
  // Last 5 seconds: very fast flash
  else if (remaining < 5000) {
    if (millis() - lastBlinkTime > 100) {  // 100ms blink
      blinkState = !blinkState;
      lastBlinkTime = millis();
      setTileColor(expectedTile, blinkState ? 0xFF0000 : 0x000000);  // RED warning
      strip.show();
    }
  }
}
```

**Why**: Players see accelerating blinks as deadline approaches (calm → fast → urgent red).

---

### 3.2 Add Heartbeat Status Report

**Problem**: Android doesn't know if ESP32 is still responsive during long waits.

**Add to `loop()`** (after line 1226):
```cpp
// Send heartbeat every 5 seconds when waiting
static unsigned long lastHeartbeat = 0;
if (waitingForCoin && millis() - lastHeartbeat > 5000) {
  sendHeartbeat();
  lastHeartbeat = millis();
}
```

**Add function**:
```cpp
void sendHeartbeat() {
  StaticJsonDocument<256> doc;
  doc["event"] = "heartbeat";
  doc["waiting"]["forCoin"] = waitingForCoin;
  doc["waiting"]["playerId"] = currentPlayer;
  doc["waiting"]["tile"] = expectedTile;
  doc["waiting"]["elapsed"] = (millis() - coinWaitStartTime) / 1000;
  doc["waiting"]["remaining"] = (COIN_TIMEOUT - (millis() - coinWaitStartTime)) / 1000;
  
  String json;
  serializeJson(doc, json);
  sendBLEResponse(json.c_str());
}
```

**Android can display**: "Waiting for coin... 45s remaining"

---

### 3.3 Add Audio/Buzzer Support (Optional Hardware)

**Add after LED pin definitions** (line 24):
```cpp
#define BUZZER_PIN 4        // Piezo buzzer (optional)
#define BUZZER_ENABLED false  // Set true if buzzer connected
```

**Add to `setup()`**:
```cpp
#if BUZZER_ENABLED
  pinMode(BUZZER_PIN, OUTPUT);
#endif
```

**Add beep functions**:
```cpp
void beep(int freq, int duration) {
  #if BUZZER_ENABLED
    tone(BUZZER_PIN, freq, duration);
    delay(duration);
    noTone(BUZZER_PIN);
  #endif
}

void beepCoinPlaced() {
  beep(1000, 100);  // Short high beep
}

void beepTimeout() {
  beep(500, 200);   // Low warning beep
  delay(100);
  beep(500, 200);
}

void beepElimination() {
  beep(400, 500);   // Long sad beep
}

void beepWinner() {
  beep(1000, 100);
  delay(50);
  beep(1200, 100);
  delay(50);
  beep(1500, 200);  // Victory fanfare
}
```

**Call in appropriate functions**:
- `checkCoinPlacement()` → `beepCoinPlaced()`
- `checkCoinTimeout()` → `beepTimeout()`
- `animatePlayerElimination()` → `beepElimination()`
- `animateWinner()` → `beepWinner()`

---

## Priority 4: Robustness & Error Recovery

### 4.1 Add Watchdog Timer

**Problem**: ESP32 might freeze during edge cases (corrupt BLE data, infinite loop).

**Add to `setup()`** (after Serial.begin):
```cpp
#include <esp_task_wdt.h>

// Watchdog timer - resets ESP32 if frozen for 30s
esp_task_wdt_init(30, true);  // 30 second timeout, panic on timeout
esp_task_wdt_add(NULL);       // Add current task
Serial.println("✓ Watchdog enabled (30s timeout)");
```

**Add to `loop()`** (at start):
```cpp
esp_task_wdt_reset();  // Reset watchdog every loop iteration
```

**Why**: Automatically recovers from firmware crashes without physical reset.

---

### 4.2 Add State Validation

**Add function**:
```cpp
void validateGameState() {
  for (int i = 0; i < activePlayerCount; i++) {
    // Validate tile bounds
    if (players[i].currentTile < 1 || players[i].currentTile > NUM_TILES) {
      Serial.printf("⚠️  Player %d invalid tile %d - resetting to 1\n", i, players[i].currentTile);
      players[i].currentTile = 1;
    }
    
    // Validate score bounds
    if (players[i].score < 0) players[i].score = 0;
    if (players[i].score > 99) {
      Serial.printf("⚠️  Player %d excessive score %d - capping at 99\n", i, players[i].score);
      players[i].score = 99;
    }
  }
}
```

**Call after** loading from preferences and after each move.

---

### 4.3 Add Command Queue (Prevent Race Conditions)

**Problem**: If Android sends commands rapidly, ESP32 might process them out of order.

**Add**:
```cpp
#include <queue>

std::queue<String> commandQueue;
bool processingCommand = false;

void handleBLECommand(const char* jsonStr) {
  commandQueue.push(String(jsonStr));
}

void processCommandQueue() {
  if (processingCommand || commandQueue.empty()) return;
  
  processingCommand = true;
  String cmd = commandQueue.front();
  commandQueue.pop();
  
  // Parse and execute command (existing logic)
  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, cmd);
  if (!error) {
    const char* command = doc["command"];
    if (strcmp(command, "roll") == 0) handleRoll(doc);
    else if (strcmp(command, "undo") == 0) handleUndo(doc);
    // ... etc
  }
  
  processingCommand = false;
}
```

**Call from `loop()`**:
```cpp
processCommandQueue();
```

---

## Priority 5: Power Management

### 5.1 Add Low Power Mode

**Problem**: LED strip consumes power even when idle.

**Add**:
```cpp
unsigned long lastActivityTime = 0;
const unsigned long IDLE_TIMEOUT = 300000;  // 5 minutes

void checkIdleTimeout() {
  if (millis() - lastActivityTime > IDLE_TIMEOUT) {
    // Dim LEDs to 20% brightness when idle
    strip.setBrightness(20);
    strip.show();
  }
}

void resetIdleTimer() {
  lastActivityTime = millis();
  strip.setBrightness(100);  // Full brightness
}
```

**Call `resetIdleTimer()`** whenever command received.

---

## Summary Table

| Priority | Issue | Impact | Difficulty | Status |
|----------|-------|--------|------------|--------|
| 🔴 P1 | BLE Pairing/Security | Security breach | Easy | NOT IMPLEMENTED |
| 🔴 P1 | Increase COIN_TIMEOUT | Winner timeout errors | Trivial | NEEDS FIX |
| 🟡 P2 | Timeout warning flash | UX confusion | Easy | NOT IMPLEMENTED |
| 🟡 P2 | Heartbeat status | Android sync issues | Medium | NOT IMPLEMENTED |
| 🟢 P3 | Watchdog timer | Crash recovery | Easy | NOT IMPLEMENTED |
| 🟢 P3 | Command queue | Race conditions | Medium | NOT IMPLEMENTED |
| 🟢 P3 | Audio feedback | Accessibility | Easy | OPTIONAL |
| 🟢 P4 | Low power mode | Battery drain | Easy | NOT IMPLEMENTED |

**Recommended Implementation Order**:
1. ✅ Increase `COIN_TIMEOUT` to 60s (1 line change)
2. ✅ Add BLE pairing (15 lines, major security improvement)
3. ✅ Add timeout warning flash (20 lines, immediate UX benefit)
4. ✅ Add heartbeat (15 lines, helps debugging)
5. ⏭️ Add watchdog timer (robustness)
6. ⏭️ Remaining features as needed
