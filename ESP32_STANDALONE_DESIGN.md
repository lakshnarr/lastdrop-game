# ESP32 Standalone Board Game System - Design Document

## Overview
Transform Last Drop into a **fully standalone board game system** using ESP32 S3 with integrated LCD touchscreen, eliminating the need for Android app.

## System Architecture

```
┌──────────────────────────────────────────────────────────┐
│               ESP32-S3 Main Controller                    │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │       LCD Touchscreen (480x320 recommended)      │    │
│  │  ┌────────────────────────────────────────┐     │    │
│  │  │  Player Setup Screen                    │     │    │
│  │  │  ┌────────┬────────┬────────┬────────┐ │     │    │
│  │  │  │Player 1│Player 2│Player 3│Player 4│ │     │    │
│  │  │  │  Red   │ Green  │  Blue  │ Yellow │ │     │    │
│  │  │  │  15pts │  8pts  │  12pts │  6pts  │ │     │    │
│  │  │  └────────┴────────┴────────┴────────┘ │     │    │
│  │  │                                         │     │    │
│  │  │  [Roll Dice] [Undo] [Reset Game]       │     │    │
│  │  └────────────────────────────────────────┘     │    │
│  └─────────────────────────────────────────────────┘    │
│                                                           │
│  BLE Central Mode          WiFi Client (optional)        │
│  ├─ GoDice 1 (6E400001)    ├─ Push to live.html         │
│  └─ GoDice 2 (6E400001)    └─ Leaderboard sync          │
│                                                           │
│  GPIO Control                                             │
│  ├─ WS2812B LED Strip (20 tiles × 4 LEDs)               │
│  ├─ Hall Sensors (20 sensors via MCP23017)              │
│  └─ Connection Status LEDs                               │
└──────────────────────────────────────────────────────────┘
```

## Hardware Components

### Core System
1. **ESP32-S3-DevKitC-1** or **WT32-SC01 Plus**
   - Dual-core 240MHz
   - 8MB PSRAM (required for BLE + display buffering)
   - Built-in BLE 5.0
   - WiFi 802.11b/g/n

2. **LCD Touchscreen** (if not integrated)
   - Recommended: **3.5" ILI9488** (320x480 SPI + XPT2046 touch)
   - Alternative: **2.8" ILI9341** (240x320 - minimum viable)
   - Library: TFT_eSPI (supports touch)

3. **Existing Hardware** (from current design)
   - WS2812B LED strip (136 LEDs)
   - 20× Hall effect sensors (A3144)
   - MCP23017 I/O expander
   - AHCT125 level shifter

### Optional Enhancements
- **SD Card Module** - Save game history locally
- **Speaker Module** - Sound effects (I2S DAC)
- **Battery Pack** - 18650 cells with charging circuit for portability

## Pin Assignment (ESP32-S3)

### SPI Display (TFT_eSPI)
```cpp
#define TFT_MOSI    11  // SPI MOSI
#define TFT_SCLK    12  // SPI Clock
#define TFT_CS      10  // Chip Select
#define TFT_DC       9  // Data/Command
#define TFT_RST     46  // Reset
#define TFT_BL      45  // Backlight PWM

#define TOUCH_CS    38  // Touch chip select (XPT2046)
```

### I2C (Hall Sensors)
```cpp
#define SDA_PIN     13  // MCP23017 data
#define SCL_PIN     14  // MCP23017 clock
```

### LED Control
```cpp
#define LED_PIN     16  // WS2812B data via AHCT125
#define LED_OE_PIN  48  // AHCT125 output enable
```

### Direct GPIO Hall Sensors
```cpp
const uint8_t DIRECT_GPIO_PINS[4] = {17, 18, 8, 9};  // Tiles 9-12
```

## Software Architecture

### Libraries Required
```cpp
#include <TFT_eSPI.h>           // Display driver
#include <BLEDevice.h>          // ESP32 BLE stack
#include <Adafruit_NeoPixel.h>  // LED control
#include <Adafruit_MCP23X17.h>  // I/O expander
#include <ArduinoJson.h>        // JSON parsing
#include <WiFi.h>               // WiFi (optional)
#include <HTTPClient.h>         // API client (optional)
#include "godiceapi.h"          // GoDice protocol (ported from ParticulaCode)
```

### State Machine
```cpp
enum GameState {
  BOOT_SCREEN,           // Show logo, check hardware
  WIFI_SETUP,            // WiFi config screen (if enabled)
  PLAYER_SETUP,          // Select player count, names, colors
  DICE_PAIRING,          // Scan and connect GoDice
  GAME_READY,            // Waiting for first roll
  ROLLING,               // Dice animation on screen
  WAITING_COIN,          // LED lit, waiting for Hall sensor
  COIN_PLACED,           // Processing turn
  GAME_OVER,             // Winner screen
  SETTINGS               // System settings
};

GameState currentState = BOOT_SCREEN;
```

### UI Screens

#### 1. Boot Screen (2 seconds)
```
┌──────────────────────────┐
│                          │
│      LAST DROP          │
│   Smart Board Game      │
│                          │
│   Initializing...       │
│   ✓ LED Strip           │
│   ✓ Hall Sensors        │
│   ⏳ Bluetooth          │
└──────────────────────────┘
```

#### 2. Player Setup Screen
```
┌──────────────────────────┐
│  How many players?       │
│  ┌──┐ ┌──┐ ┌──┐ ┌──┐   │
│  │ 2│ │ 3│ │ 4│ │ 5│   │
│  └──┘ └──┘ └──┘ └──┘   │
│                          │
│  Player Colors:          │
│  P1: [🔴 Red   ▼]        │
│  P2: [🟢 Green ▼]        │
│  P3: [🔵 Blue  ▼]        │
│  P4: [🟡 Yellow▼]        │
│                          │
│  [ Start Game ]          │
└──────────────────────────┘
```

#### 3. Dice Pairing Screen
```
┌──────────────────────────┐
│  Searching for GoDice... │
│                          │
│  Found Dice:             │
│  ✓ GoDice #1 (Red shell) │
│  ⏳ Searching...         │
│                          │
│  Tap to select die for   │
│  each player             │
│                          │
│  [ Skip - Use Virtual ]  │
└──────────────────────────┘
```

#### 4. Main Game Screen
```
┌──────────────────────────┐
│ Turn: Player 1 (Red)     │
│ ┌──┬──┬──┬──┐           │
│ │P1│P2│P3│P4│           │
│ │15│ 8│12│ 6│           │
│ └──┴──┴──┴──┘           │
│                          │
│  🎲 Rolling...           │
│     ⚅ ⚃                 │
│                          │
│  Place coin on tile 7    │
│                          │
│ [Undo] [Menu] [Stats]    │
└──────────────────────────┘
```

#### 5. Winner Screen
```
┌──────────────────────────┐
│                          │
│    🏆 WINNER! 🏆        │
│                          │
│     Player 1 (Red)       │
│      20 Points!          │
│                          │
│  Top 3:                  │
│  🥇 Player 1 - 20pts     │
│  🥈 Player 3 - 15pts     │
│  🥉 Player 2 - 12pts     │
│                          │
│ [New Game] [View Stats]  │
└──────────────────────────┘
```

## GoDice Integration

### Port ParticulaCode Library
The C library from `https://github.com/ParticulaCode/GoDiceAndroid_iOS_API` can be ported directly:

```cpp
// godiceapi.h and godiceapi.c - minimal changes needed
// Already pure C, no platform dependencies

// ESP32 implementation:
#include "godiceapi.h"

// BLE callbacks
class GoDiceCallbacks: public BLEClientCallbacks {
  void onConnect(BLEClient* client) {
    Serial.println("GoDice connected");
    // Request color and battery
    sendGoDiceCommand(client, GoDiceSDK.getColorPacket());
    sendGoDiceCommand(client, GoDiceSDK.getChargeLevelPacket());
  }
};

// Process incoming dice data
void onDiceNotification(BLERemoteCharacteristic* chr, uint8_t* data, size_t len, bool isNotify) {
  godice_incoming_packet(&callbacks, nullptr, activeDiceId, 6, data, len);
}

// Callbacks
godice_callbacks_t callbacks = {
  .on_dice_color = [](void* userdata, int dice_id, godice_color_t color) {
    Serial.printf("Dice %d color: %d\n", dice_id, color);
    tft.print("Die color detected!");
  },
  
  .on_dice_stable = [](void* userdata, int dice_id, uint8_t number) {
    Serial.printf("Dice %d rolled: %d\n", dice_id, number);
    processTurn(currentPlayer, number);  // Existing game logic
  },
  
  .on_dice_roll = [](void* userdata, int dice_id) {
    // Start rolling animation on LCD
    drawRollingAnimation();
  },
  
  .on_charge_level = [](void* userdata, int dice_id, uint8_t level) {
    diceBatteryLevel[dice_id] = level;
    updateBatteryIcon();
  }
};
```

### BLE Client Implementation
```cpp
BLEClient* diceClients[2];  // Support 2 dice max
bool diceConnected[2] = {false, false};

void scanForGoDice() {
  BLEScan* scan = BLEDevice::getScan();
  scan->setActiveScan(true);
  BLEScanResults* results = scan->start(10);  // 10 second scan
  
  for (int i = 0; i < results->getCount(); i++) {
    BLEAdvertisedDevice device = results->getDevice(i);
    
    // Check if device has GoDice service UUID
    if (device.haveServiceUUID() && 
        device.isAdvertisingService(BLEUUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))) {
      
      // Found a GoDice!
      connectToDice(device);
    }
  }
}

void connectToDice(BLEAdvertisedDevice device) {
  int slot = findFreeDiceSlot();
  if (slot < 0) return;  // No slots available
  
  diceClients[slot] = BLEDevice::createClient();
  diceClients[slot]->setClientCallbacks(new GoDiceCallbacks());
  diceClients[slot]->connect(&device);
  
  // Get service and characteristic
  BLERemoteService* service = diceClients[slot]->getService(
    BLEUUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
  );
  
  BLERemoteCharacteristic* txChar = service->getCharacteristic(
    BLEUUID("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")  // RX on dice side
  );
  
  // Register for notifications
  txChar->registerForNotify(onDiceNotification);
  
  diceConnected[slot] = true;
  updateDiceStatusUI();
}
```

## Display UI Implementation

### Touch-Responsive Buttons
```cpp
#include <TFT_eSPI.h>

TFT_eSPI tft = TFT_eSPI();

struct Button {
  int x, y, w, h;
  const char* label;
  uint16_t color;
  void (*callback)();
};

Button startButton = {60, 200, 200, 50, "Start Game", TFT_GREEN, startGame};
Button undoButton = {10, 10, 80, 40, "Undo", TFT_ORANGE, undoLastMove};

void checkButtonTouch() {
  uint16_t touchX, touchY;
  bool pressed = tft.getTouch(&touchX, &touchY);
  
  if (pressed) {
    // Check each button
    if (isTouched(startButton, touchX, touchY)) {
      startButton.callback();
    }
  }
}

bool isTouched(Button btn, int x, int y) {
  return (x >= btn.x && x <= btn.x + btn.w &&
          y >= btn.y && y <= btn.y + btn.h);
}

void drawButton(Button btn) {
  tft.fillRoundRect(btn.x, btn.y, btn.w, btn.h, 8, btn.color);
  tft.setTextColor(TFT_WHITE);
  tft.drawCentreString(btn.label, btn.x + btn.w/2, btn.y + btn.h/2, 4);
}
```

### Animated Dice Display
```cpp
void drawRollingDice() {
  static int frame = 0;
  const int diceX = 120, diceY = 120;
  
  // Clear previous frame
  tft.fillRect(diceX - 30, diceY - 30, 60, 60, TFT_BLACK);
  
  // Draw rotating dice faces
  int face = (frame / 10) % 6 + 1;
  drawDiceFace(diceX, diceY, face, TFT_WHITE);
  
  frame++;
}

void drawDiceFace(int x, int y, int number, uint16_t color) {
  tft.drawRoundRect(x - 25, y - 25, 50, 50, 5, color);
  
  // Draw dots based on number
  switch(number) {
    case 1:
      tft.fillCircle(x, y, 5, color);
      break;
    case 2:
      tft.fillCircle(x - 10, y - 10, 5, color);
      tft.fillCircle(x + 10, y + 10, 5, color);
      break;
    // ... cases 3-6
  }
}
```

### Player Scoreboard Widget
```cpp
void drawScoreboard() {
  int boxWidth = 70;
  int startX = 10;
  int startY = 10;
  
  for (int i = 0; i < playerCount; i++) {
    uint16_t bgColor = playerColors[i];
    uint16_t textColor = (i == currentPlayer) ? TFT_YELLOW : TFT_WHITE;
    
    // Draw player box
    tft.fillRoundRect(startX + i * (boxWidth + 5), startY, boxWidth, 60, 5, bgColor);
    
    // Player name
    tft.setTextColor(textColor);
    tft.drawCentreString(playerNames[i], startX + i * (boxWidth + 5) + boxWidth/2, startY + 10, 2);
    
    // Score
    tft.drawCentreString(String(playerScores[i]), startX + i * (boxWidth + 5) + boxWidth/2, startY + 35, 4);
  }
}
```

## Development Phases

### Phase 1: Hardware Setup (Week 1)
- [ ] Order ESP32-S3 with LCD (WT32-SC01 Plus recommended)
- [ ] Test display with TFT_eSPI library
- [ ] Verify touch input calibration
- [ ] Connect existing LED strip and Hall sensors
- [ ] Test all hardware independently

### Phase 2: Basic UI (Week 2)
- [ ] Implement boot screen
- [ ] Create player setup screen with touch buttons
- [ ] Add scoreboard display
- [ ] Test button responsiveness
- [ ] Design color picker UI

### Phase 3: GoDice Integration (Week 3)
- [ ] Port `godiceapi.c` to ESP32
- [ ] Implement BLE central mode
- [ ] Scan and connect to GoDice
- [ ] Display dice roll animations
- [ ] Handle battery status

### Phase 4: Game Logic Integration (Week 4)
- [ ] Integrate existing LED control code
- [ ] Connect Hall sensor detection
- [ ] Implement turn processing
- [ ] Add undo functionality
- [ ] Test complete game flow

### Phase 5: Polish & Features (Week 5)
- [ ] Add WiFi configuration screen (optional)
- [ ] Implement API push for live.html
- [ ] Create settings menu
- [ ] Add sound effects (if speaker added)
- [ ] Game statistics screen
- [ ] Save/load game state to SD card

## Memory Considerations

### Flash Usage Estimate
```
ESP32-S3 Flash: 16MB available
Current sketch_ble.ino: 1.3MB (85% of 1.5MB partition)

With additions:
- TFT_eSPI library: +150KB
- GoDice BLE client: +200KB
- Fonts and UI assets: +300KB
- Total estimated: 2.0MB (fits comfortably)
```

### RAM Usage
```
ESP32-S3 RAM: 512KB total
PSRAM: 8MB (critical for display buffering)

Allocations:
- Display buffer: 320×480×2 = 307KB (use PSRAM)
- BLE stack: ~100KB
- Game state: ~50KB
- LED buffer: ~1KB
- Total: ~458KB (fits with PSRAM)
```

## Advantages of Standalone Design

### ✅ **Benefits**
1. **Portable** - No phone/tablet required
2. **Faster** - No BLE relay through Android
3. **Simpler** - Single device, no app installation
4. **Reliable** - Fewer Bluetooth connections to manage
5. **Cost-effective** - ESP32-S3 with display < $25 USD
6. **Self-contained** - All game state on device
7. **No permissions** - No Android BLE/location hassles

### ⚠️ **Trade-offs**
1. **Screen size** - 3.5" vs smartphone 6"
2. **Touch precision** - Resistive less accurate than capacitive
3. **Development time** - Custom UI vs Android framework
4. **Debugging** - Serial monitor only, no Android Studio
5. **Updates** - OTA firmware vs Play Store

## Recommended Hardware Package

### Budget Option (~$45)
- ESP32-S3-DevKitC-1 ($8)
- 3.5" ILI9488 TFT with touch ($12)
- Existing components (LEDs, sensors) ($25)

### Premium Option (~$65)
- **WT32-SC01 Plus** integrated display ($25) ⭐
- Better touch (capacitive)
- Cleaner wiring
- Existing components ($40)

### Deluxe Option (~$95)
- WT32-SC01 Plus ($25)
- Enclosure with acrylic window ($15)
- 18650 battery pack with charging ($15)
- I2S speaker module ($10)
- Existing components ($30)

## Next Steps

### Immediate Actions
1. **Order hardware**: WT32-SC01 Plus or similar
2. **Test setup**: Display "Hello World" with TFT_eSPI
3. **Port godiceapi.c**: Copy from ParticulaCode repo, compile test
4. **Create mockup UI**: Design player setup screen layout

### Code Repository Structure
```
LastDrop/
├── sketch_ble_standalone/          # New standalone firmware
│   ├── sketch_ble_standalone.ino   # Main entry point
│   ├── godiceapi.h                 # Ported from ParticulaCode
│   ├── godiceapi.c                 # Dice protocol
│   ├── ui_screens.cpp              # LCD UI implementation
│   ├── ble_client.cpp              # GoDice BLE connection
│   ├── game_logic.cpp              # Existing game engine
│   └── led_control.cpp             # Existing LED code
└── docs/
    └── ESP32_STANDALONE_DESIGN.md  # This document
```

## Testing Strategy

### Unit Tests
1. Display calibration and touch accuracy
2. BLE client connection to GoDice
3. LED strip control with new pin assignments
4. Hall sensor detection on MCP23017
5. WiFi connection and API push

### Integration Tests
1. Full game flow: setup → roll → coin placement → scoring
2. Multi-player game with score tracking
3. Undo/redo functionality with UI feedback
4. Battery monitoring and low-battery warnings
5. Connection loss recovery (dice disconnect)

### User Acceptance
1. Play 10 complete games with 4 players
2. Test all UI touch interactions
3. Verify LED animations match expected behavior
4. Confirm live.html updates (if WiFi enabled)
5. Check performance (no lag, smooth animations)

## Conclusion

This standalone design is **highly feasible** and offers significant advantages:
- **Simpler deployment** - No app installation
- **Better reliability** - Fewer wireless hops
- **Lower cost** - ESP32-S3 + display < $30

The existing ESP32 code (game logic, LEDs, sensors) can be reused with minimal changes. The main additions are:
1. **TFT display UI** (TFT_eSPI library - well documented)
2. **GoDice BLE client** (port existing C library)
3. **Touch input handling** (straightforward button detection)

**Estimated development time: 4-6 weeks** for a complete, polished system.

Would you like me to:
1. Create the initial `sketch_ble_standalone.ino` with display setup?
2. Port the GoDice library with ESP32 BLE client implementation?
3. Design the touch UI layout and button system?
