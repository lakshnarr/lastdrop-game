# GoDice ESP32 Library - Complete Port

## Overview
Complete port of ParticulaCode's GoDice library for ESP32, providing full BLE client functionality for connecting to GoDice smart dice.

## Features Implemented

### ✅ Core Protocol (`godiceapi.c/h`)
- [x] Incoming packet parsing
- [x] Roll detection (unstable state)
- [x] Stable detection (settled face value)
- [x] Shell color detection (Black, Red, Green, Blue, Yellow, Orange)
- [x] Battery level monitoring (0-100%)
- [x] Charging state detection
- [x] LED control commands
- [x] Detection settings configuration
- [x] Support for D4, D6, D8, D10, D12, D20, D10X dice types

### ✅ ESP32 BLE Client (`godice_ble_client.cpp/h`)
- [x] BLE Central mode scanning
- [x] Auto-discovery of GoDice devices
- [x] Connection management (up to 2 dice simultaneously)
- [x] Automatic reconnection on disconnect
- [x] Notification handling
- [x] Command queue management
- [x] Connection status monitoring

### ✅ All GoDice Commands
```cpp
// Information Requests
requestColor(slot)          // Get die shell color
requestBattery(slot)        // Get battery percentage

// LED Control
setLEDColors(slot, r1, g1, b1, r2, g2, b2)  // Static colors
blinkLEDs(slot, blinks, on, off, r, g, b)   // Blinking pattern
turnOffLEDs(slot)                            // Turn off LEDs

// Advanced
updateDetectionSettings(slot, ...)           // Tune roll detection
```

### ✅ All Event Callbacks
```cpp
onDiceConnected(slot, address, name)    // Connected to die
onDiceDisconnected(slot)                // Lost connection
onDiceColor(slot, color)                // Shell color received
onDiceRolling(slot)                     // Die is rolling
onDiceStable(slot, value)               // Settled on face (1-6)
onDiceBattery(slot, level)              // Battery level (0-100)
onDiceCharging(slot, charging)          // Charging status
```

## File Structure

```
sketch_ble_standalone/
├── godiceapi.h              # Protocol definitions and API
├── godiceapi.c              # Protocol implementation (pure C)
├── godice_ble_client.h      # ESP32 BLE client interface
├── godice_ble_client.cpp    # ESP32 BLE client implementation
├── godice_test.ino          # Test sketch with all features
└── GODICE_LIBRARY_README.md # This file
```

## Usage Example

### Basic Setup
```cpp
#include "godice_ble_client.h"

GoDiceBLEClient goDiceClient;

class MyHandler : public GoDiceEventHandler {
    void onDiceStable(int slot, uint8_t value) override {
        Serial.printf("Die rolled: %d\n", value);
        // Process turn with this value
    }
    
    void onDiceBattery(int slot, uint8_t level) override {
        if (level < 20) {
            Serial.println("Low battery warning!");
        }
    }
    
    // Implement other callbacks...
};

MyHandler handler;

void setup() {
    goDiceClient.begin("MyGame");
    goDiceClient.setEventHandler(&handler);
    goDiceClient.startScan();
}

void loop() {
    goDiceClient.update();
}
```

### LED Control Example
```cpp
// Celebrate a high roll with rainbow LEDs
void celebrateHighRoll(int slot, uint8_t value) {
    if (value == 6) {
        // Blink rainbow colors
        goDiceClient.blinkLEDs(slot, 5, 30, 30, 255, 0, 0);
        delay(500);
        goDiceClient.blinkLEDs(slot, 5, 30, 30, 0, 255, 0);
        delay(500);
        goDiceClient.blinkLEDs(slot, 5, 30, 30, 0, 0, 255);
    }
}
```

### Battery Monitoring Example
```cpp
void checkDiceBatteries() {
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        if (goDiceClient.isConnected(i)) {
            GoDiceInfo* info = goDiceClient.getDiceInfo(i);
            
            if (info->batteryLevel < 10) {
                // Critical battery - warn player
                displayLowBatteryWarning(i);
            } else if (info->batteryLevel < 30) {
                // Low battery - pulse yellow
                goDiceClient.blinkLEDs(i, 3, 50, 50, 255, 255, 0);
            }
        }
    }
}
```

### Connection Status Example
```cpp
void displayConnectionStatus() {
    int connected = goDiceClient.getConnectedCount();
    
    Serial.printf("Connected Dice: %d\n", connected);
    
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        GoDiceInfo* info = goDiceClient.getDiceInfo(i);
        if (info && info->connected) {
            const char* colors[] = {"Black", "Red", "Green", "Blue", "Yellow", "Orange"};
            
            Serial.printf("  Slot %d:\n", i);
            Serial.printf("    Address: %s\n", info->address.c_str());
            Serial.printf("    Color: %s\n", colors[info->shellColor]);
            Serial.printf("    Battery: %d%%\n", info->batteryLevel);
            Serial.printf("    Last Roll: %d\n", info->lastRoll);
            Serial.printf("    Rolling: %s\n", info->rolling ? "Yes" : "No");
        }
    }
}
```

## Testing the Library

### Upload Test Sketch
1. Open `godice_test.ino` in Arduino IDE
2. Select ESP32 board (ESP32-S3 Dev Module or similar)
3. Upload sketch
4. Open Serial Monitor (115200 baud)
5. Power on GoDice
6. Watch connection and roll events

### Expected Output
```
╔════════════════════════════════════════════════════════╗
║          GoDice ESP32 Integration Test                ║
╚════════════════════════════════════════════════════════╝

📡 Starting scan for GoDice...

✓ DICE CONNECTED - Slot 0
  Address: XX:XX:XX:XX:XX:XX
  Name: GoDice_XXXX

🎨 DICE COLOR DETECTED - Slot 0
   Shell Color: Red

🔋 BATTERY LEVEL - Slot 0
   Level: 85%
   [████████████████░░░░]

🎲 Rolling... (Slot 0)

╔════════════════════════════════════════╗
║  🎯 DICE STABLE - Slot 0              ║
║     Roll Value: 4                     ║
╚════════════════════════════════════════╝
```

### Serial Commands
While test sketch is running, send these commands via Serial Monitor:

- `s` - Start scan for dice
- `b` - Request battery levels
- `c` - Request dice colors
- `l` - Turn on LEDs (red)
- `o` - Turn off LEDs
- `d` - Disconnect all dice
- `h` - Show help

## Integration with Game Logic

### Connect to Existing Board System
```cpp
#include "godice_ble_client.h"
#include "existing_game_logic.h"  // Your game code

GoDiceBLEClient goDiceClient;

class GameDiceHandler : public GoDiceEventHandler {
    void onDiceStable(int slot, uint8_t value) override {
        // Send roll to your existing game logic
        processTurn(currentPlayer, value);
        
        // Light up LED board destination tile
        int newPosition = calculateNewPosition(currentPlayer, value);
        lightUpTile(newPosition);
        
        // Wait for coin placement on Hall sensor
        waitForCoinPlacement(newPosition);
    }
    
    void onDiceRolling(int slot) override {
        // Start LED animation on board
        startRollingAnimation();
    }
};
```

## Advanced Configuration

### Multiple Dice Colors
```cpp
// Track which die belongs to which player
void assignDiceToPlayer(int slot, int playerId) {
    GoDiceInfo* info = goDiceClient.getDiceInfo(slot);
    
    // Set die LEDs to player color
    switch(playerId) {
        case 0:  // Red player
            goDiceClient.setLEDColors(slot, 255, 0, 0, 255, 0, 0);
            break;
        case 1:  // Green player
            goDiceClient.setLEDColors(slot, 0, 255, 0, 0, 255, 0);
            break;
    }
}
```

### Custom Detection Settings
```cpp
// Make dice more/less sensitive to rolls
void configureDiceSensitivity(int slot, bool moreSensitive) {
    if (moreSensitive) {
        goDiceClient.updateDetectionSettings(slot,
            4,   // samples_count
            2,   // movement_count
            1,   // face_count
            5,   // min_flat_deg (lower = more sensitive)
            60,  // max_flat_deg
            15,  // weak_stable
            45,  // movement_deg
            20   // roll_threshold (lower = more sensitive)
        );
    } else {
        // Use defaults for less sensitive
        goDiceClient.updateDetectionSettings(slot);
    }
}
```

## Memory Usage

### Flash (Program Storage)
- `godiceapi.c`: ~8KB
- `godice_ble_client.cpp`: ~12KB
- Total: **~20KB** (plenty of room on ESP32-S3)

### RAM (Runtime)
- Per die connection: ~2KB
- BLE stack overhead: ~80KB
- Total for 2 dice: **~84KB** (fits in 512KB ESP32-S3 RAM)

## Known Limitations

1. **Maximum 2 Dice**: Current implementation supports 2 simultaneous connections (configurable via `MAX_GODICE_CONNECTIONS`)

2. **No Pairing PIN**: Dies not require pairing with PIN (they use Just Works pairing)

3. **D6 Default**: Test code assumes D6 dice. Change `dice_max` parameter in `onNotify()` for other die types

4. **Sequential Scanning**: Scans for one die at a time. Connect both dice sequentially, not simultaneously.

## Troubleshooting

### Dice Not Found
- Ensure dice are powered on and charged
- Check dice are in range (< 10 meters)
- Try power cycling dice (place on charger, remove)
- Check Serial Monitor for BLE errors

### Connection Drops
- Check battery level (< 20% causes instability)
- Move ESP32 closer to dice
- Check for WiFi interference (disable WiFi if not needed)
- Dice auto-sleep after 5 minutes idle - roll to wake

### No Roll Events
- Dice may be in sleep mode - roll vigorously
- Check `onDiceStable` callback is implemented
- Verify initialization packet was sent
- Check Serial Monitor for parsing errors

### LED Commands Don't Work
- Ensure die is connected (`isConnected(slot)`)
- Check initialization completed (`info->initialized`)
- Some LED commands queue - add delays between commands
- Battery < 10% may disable LEDs

## Credits

Original library: [ParticulaCode/GoDiceAndroid_iOS_API](https://github.com/ParticulaCode/GoDiceAndroid_iOS_API)

Ported by: LastDrop Team

License: See LICENSE in ParticulaCode repository

## Next Steps

After verifying this library works with `godice_test.ino`:

1. ✅ **Integrate with LCD display** - Add TFT_eSPI for UI
2. ✅ **Connect to LED board** - Use existing LED control code
3. ✅ **Add Hall sensors** - Use existing MCP23017 code
4. ✅ **Implement game logic** - Use existing GameEngine
5. ✅ **Add WiFi API push** - Optional live.html updates

See `ESP32_STANDALONE_DESIGN.md` for complete system architecture.
