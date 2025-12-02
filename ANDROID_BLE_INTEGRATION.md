# ESP32 BLE Integration Guide

## Overview

This document explains the **Bluetooth BLE** implementation for ESP32 ↔ Android communication, replacing the previous WiFi-based approach.

## Why BLE Instead of WiFi?

### Problem with WiFi Approach
- Phone connects to ESP32 WiFi → **loses internet connection**
- Cannot access `lastdrop.earth` server for live.html display
- Must choose: ESP32 communication **OR** internet, not both

### BLE Solution
✅ **Phone WiFi stays free** for internet connection  
✅ **No network switching** required  
✅ **Lower power consumption** on ESP32  
✅ **No conflict with GoDice** (different BLE services)  
✅ **Simpler user experience** (just pair once)

---

## Architecture

```
┌──────────────┐   Bluetooth BLE    ┌─────────────┐
│   Android    │ ←───────────────→  │    ESP32    │
│  (MainActivity) │                    │ (sketch_ble) │
└──────────────┘                    └─────────────┘
      │                                    │
      │ WiFi/Cellular                     │ Physical
      ↓                                    ↓
┌──────────────┐                    ┌─────────────┐
│ lastdrop.earth│                    │  LED Strip  │
│   (Server)    │                    │ Hall Sensors│
└──────────────┘                    └─────────────┘

GoDice (BLE) → Android (separate connection, no conflict)
```

---

## BLE Service Specification

### Service UUID
```
6e400001-b5a3-f393-e0a9-e50e24dcca9e
```
(Nordic UART Service compatible)

### Characteristics

| UUID | Direction | Type | Purpose |
|------|-----------|------|---------|
| `6e400002-...` | Android → ESP32 | Write | Send commands (roll, undo, reset) |
| `6e400003-...` | ESP32 → Android | Notify | Receive events (coin_placed, misplacement) |

### Device Name
```
LASTDROP-ESP32
```

---

## Communication Protocol

### 1. Connection Flow

```
1. Android app starts
2. Scans for BLE device named "LASTDROP-ESP32"
3. Connects to ESP32
4. Discovers services
5. Enables notifications on TX characteristic
6. Ready for communication
```

### 2. Commands (Android → ESP32)

All commands sent as JSON strings:

#### Roll Command
```json
{
  "command": "roll",
  "playerId": 0,
  "playerName": "Player 1",
  "diceValue": 4,
  "currentTile": 5,
  "expectedTile": 9,
  "color": "red"
}
```

**ESP32 Response (via notification):**
```json
{
  "status": "ok",
  "blinking": true,
  "message": "Waiting for coin placement"
}
```

#### Undo Command
```json
{
  "command": "undo",
  "playerId": 0,
  "fromTile": 9,
  "toTile": 5
}
```

#### Reset Command
```json
{
  "command": "reset"
}
```

#### Status Query
```json
{
  "command": "status"
}
```

**Response:**
```json
{
  "status": "ok",
  "connected": true,
  "waitingForCoin": false,
  "currentPlayer": -1,
  "expectedTile": -1,
  "players": [
    {
      "id": 0,
      "tile": 5,
      "water": 8,
      "alive": true,
      "coinPlaced": true
    }
  ]
}
```

### 3. Events (ESP32 → Android)

Sent as notifications when events occur:

#### Coin Placed
```json
{
  "event": "coin_placed",
  "playerId": 0,
  "tile": 9,
  "verified": true
}
```

**Android Action:**
- Cancel timeout timer
- Trigger live.html animation
- Update UI

#### Coin Timeout
```json
{
  "event": "coin_timeout",
  "playerId": 0,
  "tile": 9
}
```

**Android Action:**
- Show timeout message
- Continue game anyway

#### Misplacement Detection
```json
{
  "event": "misplacement",
  "errors": [
    {
      "tile": 7,
      "playerId": 0,
      "issue": "missing_coin"
    },
    {
      "tile": 12,
      "issue": "unexpected_coin"
    }
  ]
}
```

**Android Action:**
- Show alert dialog
- List all errors
- Wait for user to fix

---

## Android Implementation

### Key Classes and Methods

#### BLE Connection Management

```kotlin
// BLE Configuration
private const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"
private val ESP32_SERVICE_UUID = UUID.fromString("6e400001-...")
private val ESP32_CHAR_RX_UUID = UUID.fromString("6e400002-...")
private val ESP32_CHAR_TX_UUID = UUID.fromString("6e400003-...")

// BLE State
private var esp32Connected: Boolean = false
private var esp32Gatt: BluetoothGatt? = null
private var esp32TxCharacteristic: BluetoothGattCharacteristic? = null
private var esp32RxCharacteristic: BluetoothGattCharacteristic? = null
```

#### Connection Flow

```kotlin
fun connectToESP32() {
    // 1. Scan for device
    val scanner = adapter.bluetoothLeScanner
    scanner.startScan(filters, settings, scanCallback)
    
    // 2. When found, connect
    device.connectGatt(context, false, esp32GattCallback)
    
    // 3. Discover services
    gatt.discoverServices()
    
    // 4. Enable notifications
    gatt.setCharacteristicNotification(esp32TxCharacteristic, true)
}
```

#### Sending Commands

```kotlin
fun sendBLECommand(json: JSONObject) {
    val data = json.toString().toByteArray(Charsets.UTF_8)
    esp32RxCharacteristic?.value = data
    esp32Gatt?.writeCharacteristic(esp32RxCharacteristic)
}
```

#### Receiving Events

```kotlin
override fun onCharacteristicChanged(
    gatt: BluetoothGatt?,
    characteristic: BluetoothGattCharacteristic?
) {
    val response = String(characteristic?.value ?: return, Charsets.UTF_8)
    val json = JSONObject(response)
    
    when (json.optString("event")) {
        "coin_placed" -> handleCoinPlaced(json)
        "coin_timeout" -> handleCoinTimeout(json)
        "misplacement" -> handleMisplacement(json)
    }
}
```

---

## ESP32 Implementation

### Required Libraries

```cpp
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>
#include <Preferences.h>
#include <ArduinoJson.h>
```

**Installation (Arduino IDE):**
1. BLE libraries: Built-in with ESP32 board package
2. Adafruit_NeoPixel: Library Manager → "Adafruit NeoPixel"
3. ArduinoJson: Library Manager → "ArduinoJson" (v6+)

### BLE Server Setup

```cpp
void initBLE() {
    BLEDevice::init(DEVICE_NAME);
    
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    // TX Characteristic (ESP32 → Android)
    pTxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_TX,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pTxCharacteristic->addDescriptor(new BLE2902());
    
    // RX Characteristic (Android → ESP32)
    pRxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_RX,
        BLECharacteristic::PROPERTY_WRITE
    );
    pRxCharacteristic->setCallbacks(new MyCallbacks());
    
    pService->start();
    BLEDevice::startAdvertising();
}
```

### Command Handler

```cpp
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        std::string rxValue = pCharacteristic->getValue();
        
        StaticJsonDocument<512> doc;
        deserializeJson(doc, rxValue.c_str());
        
        const char* command = doc["command"];
        
        if (strcmp(command, "roll") == 0) {
            handleRoll(doc);
        } else if (strcmp(command, "undo") == 0) {
            handleUndo(doc);
        } else if (strcmp(command, "reset") == 0) {
            handleReset();
        }
    }
};
```

### Sending Notifications

```cpp
void sendBLEResponse(const char* json) {
    if (deviceConnected) {
        pTxCharacteristic->setValue((uint8_t*)json, strlen(json));
        pTxCharacteristic->notify();
    }
}

// Example: Send coin placed event
void notifyCoinPlaced(int playerId, int tile) {
    StaticJsonDocument<256> doc;
    doc["event"] = "coin_placed";
    doc["playerId"] = playerId;
    doc["tile"] = tile;
    doc["verified"] = true;
    
    String response;
    serializeJson(doc, response);
    sendBLEResponse(response.c_str());
}
```

---

## Game Flow Example

### Complete Dice Roll Sequence

```
1. User rolls GoDice
   └─→ Android detects roll via GoDice SDK

2. Android processes roll
   ├─→ Calculate avg (in 2-die mode)
   ├─→ Determine target tile
   └─→ Send to ESP32 via BLE
       {
         "command": "roll",
         "diceValue": 4,
         "expectedTile": 9
       }

3. ESP32 receives command
   ├─→ Animate LED from tile 5 → 9
   ├─→ Blink LED at tile 9
   └─→ Monitor Hall sensor

4. Player places coin
   └─→ Hall sensor detects magnet

5. ESP32 notifies Android
   └─→ {
         "event": "coin_placed",
         "tile": 9
       }

6. Android receives notification
   ├─→ Cancel timeout
   ├─→ Update local state
   └─→ Send to server
       POST /api/live_push.php
       {
         "lastEvent": {
           "coinPlaced": true
         }
       }

7. live.html receives update
   └─→ Starts token walk animation
```

---

## Comparison: WiFi vs BLE

| Feature | WiFi (Old) | BLE (New) |
|---------|-----------|-----------|
| Phone Internet | ❌ Lost | ✅ Available |
| Power Consumption | High (~100mA) | Low (~15mA) |
| Range | 30m | 10m |
| Setup Complexity | Medium | Low |
| Latency | 50-100ms | 100-300ms |
| Data Throughput | High (Mbps) | Low (100 Kbps) |
| **Best for** | Heavy data | Simple commands |

**Verdict:** BLE is better for this use case (simple commands + need internet)

---

## Testing Checklist

### BLE Connection
- [ ] ESP32 appears in Bluetooth scan
- [ ] Android connects successfully
- [ ] Services discovered correctly
- [ ] Notifications enabled
- [ ] Reconnect after disconnect works

### Command Transmission
- [ ] Roll command triggers LED animation
- [ ] Undo command reverses animation
- [ ] Reset command clears board
- [ ] Status query returns correct data

### Event Reception
- [ ] Coin placement notification received
- [ ] Timeout notification received
- [ ] Misplacement notification received
- [ ] All JSON parsed correctly

### Integration
- [ ] GoDice still connects (no conflict)
- [ ] Phone WiFi works during gameplay
- [ ] live.html updates correctly
- [ ] Undo synchronizes properly
- [ ] Reset works on all systems

---

## Troubleshooting

### Issue: ESP32 not found in scan

**Solutions:**
1. Check ESP32 is powered on
2. Verify BLE code uploaded correctly
3. Open Serial Monitor - should see "BLE Service started"
4. Try manual Bluetooth scan in phone settings
5. Check Android location permission (required for BLE scan)

### Issue: Connected but commands not working

**Solutions:**
1. Check Serial Monitor for received data
2. Verify JSON format matches exactly
3. Ensure characteristics UUIDs match
4. Test with nRF Connect app to verify BLE service

### Issue: Notifications not received

**Solutions:**
1. Verify descriptor enabled: `ENABLE_NOTIFICATION_VALUE`
2. Check `onCharacteristicChanged()` is called
3. Test with nRF Connect - can you receive notifications?
4. Ensure ESP32 calls `notify()` after `setValue()`

### Issue: GoDice connection fails after ESP32

**Solutions:**
1. Disconnect ESP32 first, then connect GoDice
2. Check BLE connection limit (usually 7 devices)
3. Clear Bluetooth cache in Android settings
4. Restart app and try again

---

## Performance Optimization

### Reduce Latency
```cpp
// Use faster connection parameters
BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
pAdvertising->setMinPreferred(0x06);  // 7.5ms
pAdvertising->setMinPreferred(0x12);  // 22.5ms
```

### Reduce Power Consumption
```cpp
// Lower LED brightness
strip.setBrightness(50);  // vs 100

// Sleep when idle
esp_sleep_enable_timer_wakeup(5 * 1000000);  // 5 seconds
esp_light_sleep_start();
```

### Improve Reliability
```cpp
// Add retry logic
int retries = 3;
while (retries-- > 0) {
    if (sendBLECommand(json)) break;
    delay(500);
}
```

---

## Security Considerations

### Current Implementation
- ⚠️ **No pairing required** - any device can connect
- ⚠️ **No encryption** - data sent in plain text
- ⚠️ **No authentication** - accepts all commands

### Production Recommendations

1. **Enable BLE pairing:**
```cpp
BLESecurity *pSecurity = new BLESecurity();
pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
```

2. **Add PIN code:**
```cpp
pSecurity->setStaticPIN(123456);
```

3. **Encrypt sensitive data:**
```kotlin
// Use Android KeyStore
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
val encryptedData = cipher.doFinal(jsonData.toByteArray())
```

4. **Validate commands:**
```cpp
// Check player ID range
if (playerId < 0 || playerId >= NUM_PLAYERS) {
    sendError("Invalid player ID");
    return;
}
```

---

## Future Enhancements

### Multi-Board Support
- Daisy-chain multiple ESP32s via I2C
- Scale to 8+ players (40+ tiles)
- Sync LED animations across boards

### Advanced Features
- **Battery monitoring:** Send battery level to Android
- **OLED display:** Show current player, tile, water level
- **Buzzer feedback:** Audio confirmation of coin placement
- **RGB status LED:** Show connection status (red/green/blue)

### Improved UX
- **Auto-reconnect:** Detect disconnection and retry
- **OTA updates:** Upload new firmware over BLE
- **Configuration UI:** Change LED brightness, colors, timing
- **Debug mode:** Toggle verbose logging on/off

---

## Conclusion

The BLE implementation solves the critical WiFi conflict issue while maintaining all functionality:

✅ **Phone keeps internet** for live.html display  
✅ **No conflicts** with GoDice BLE connections  
✅ **Lower power** usage on ESP32  
✅ **Simpler setup** for users  
✅ **All features** preserved (coin detection, undo, misplacement)  

**Next Steps:**
1. Upload `sketch_ble.ino` to ESP32
2. Build `MainActivity_COMPLETE.kt` to Android
3. Test connection and basic commands
4. Verify full game flow end-to-end
5. Optimize based on real-world performance
