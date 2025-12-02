# Last Drop - ESP32 Physical Board Implementation Guide

## Overview

This guide explains how to set up and use the ESP32 physical board with the Last Drop game system.

## System Components

1. **GoDice** - Bluetooth dice that detect rolls
2. **Android App** (MainActivity_COMPLETE.kt) - Game controller
3. **ESP32 Board** (sketch_enhanced.ino) - Physical LED board with Hall sensors
4. **Web Display** (live.html) - Visual display for spectators

## Data Flow

```
GoDice Roll
    ↓
Android App (calculates avg in 2-dice mode)
    ↓
    ├─→ ESP32 (avg value only)
    │   └─→ LED animates to new position
    │       └─→ Blinks, waiting for coin
    │           └─→ Hall sensor detects coin
    │               └─→ Callback to Android
    │                   └─→ Android → live.html
    │                       └─→ Token animation starts
    │
    └─→ Server API (both dice values + avg)
        └─→ Stored in database
```

## Setup Instructions

### 1. ESP32 Hardware Setup

**Components Needed:**
- ESP32 Dev Board
- WS2812B LED Strip (80 LEDs: 4 per tile × 20 tiles)
- 20x Hall Effect Sensors (e.g., A3144)
- 20x Pushbuttons (for Wokwi simulation)
- 4x Game coins/tokens (magnetic)
- Power supply (5V, 3A minimum)
- Breadboard and jumper wires

**Wiring:**
```
ESP32 Pin 18  → LED Strip DIN
ESP32 3V3     → LED Strip VDD
ESP32 GND     → LED Strip VSS

Hall Sensors (one per tile):
ESP32 Pin 34  → Tile 1 Sensor
ESP32 Pin 35  → Tile 2 Sensor
... (see tileSensorPins array)
ESP32 Pin 19  → Tile 20 Sensor

Each sensor:
  - VCC → ESP32 3V3
  - GND → ESP32 GND
  - OUT → GPIO Pin (with internal pullup)
```

### 2. Upload Firmware

1. Open `sketch_enhanced.ino` in Arduino IDE
2. Install libraries:
   - Adafruit NeoPixel
   - ArduinoJson
3. Select Board: "ESP32 Dev Module"
4. Select Port: (your ESP32 port)
5. Upload

**Verify:**
- Serial monitor should show: "WiFi AP started. SSID: LASTDROP-ESP32"
- LED strip does startup animation
- IP address shown: 192.168.4.1

### 3. Android App Configuration

1. Build and install the app on Android phone
2. Make sure phone can connect to WiFi networks
3. The app will automatically:
   - Connect to ESP32 WiFi (LASTDROP-ESP32)
   - Configure phone IP on ESP32
   - Enable bi-directional communication

**Manual WiFi Connection (if needed):**
```
SSID: LASTDROP-ESP32
Password: lastdrop123
```

### 4. Testing the System

**Test 1: LED Animation**
1. Power on ESP32
2. All LEDs should show dim blue background
3. Tiles with water effects: cyan tint
4. Chance tiles: purple tint

**Test 2: Dice Roll**
1. Connect GoDice to Android app
2. Roll the dice
3. ESP32 should:
   - Animate LED from current tile to new tile
   - Blink at destination tile (player color)
   - Wait for coin placement

**Test 3: Coin Placement**
1. Place magnetic coin on correct tile
2. Hall sensor detects coin (LED solid)
3. ESP32 sends callback to Android
4. Android shows "✓ Coin placed!"
5. live.html shows token animation

**Test 4: Misplacement Detection**
1. Place coin on wrong tile
2. Wait 5 seconds (scan interval)
3. ESP32 detects misplacement
4. Red LED blinks at correct tile
5. Android shows alert dialog

**Test 5: Undo**
1. Press "Undo" button
2. Confirm within 5 seconds
3. ESP32 LED reverses animation
4. Blinks at original tile
5. Wait for coin replacement

## Troubleshooting

### ESP32 Not Connecting

**Problem:** Android can't reach ESP32
**Solutions:**
1. Check ESP32 serial monitor for IP address
2. Verify WiFi AP is running: `WiFi.softAPIP()`
3. Phone should be on 192.168.4.x network
4. Ping test: `ping 192.168.4.1` from phone terminal

### Coin Not Detected

**Problem:** Hall sensor doesn't trigger
**Solutions:**
1. Check sensor wiring (VCC, GND, OUT)
2. Verify magnet polarity (try flipping coin)
3. Test sensor manually:
   ```cpp
   Serial.println(digitalRead(tileSensorPins[0]));
   ```
4. Adjust coin distance (closer = stronger signal)
5. Use stronger magnets or neodymium coins

### LEDs Not Lighting

**Problem:** Strip stays dark
**Solutions:**
1. Check power supply (5V, adequate amperage)
2. Verify DIN connection to Pin 18
3. Test strip with simple sketch:
   ```cpp
   strip.setPixelColor(0, 255, 0, 0);
   strip.show();
   ```
4. Check LED_COUNT matches your strip (80 LEDs)

### Animation Lag

**Problem:** LEDs update slowly
**Solutions:**
1. Reduce delay() in renderBoard() loop
2. Increase WiFi signal strength
3. Use faster LED protocol (APA102 instead of WS2812B)
4. Optimize JSON parsing in handleRoll()

### Misplacement False Positives

**Problem:** ESP32 reports errors when coins are correct
**Solutions:**
1. Increase SCAN_INTERVAL (currently 5 seconds)
2. Add debounce logic to sensor reads
3. Implement multi-read verification:
   ```cpp
   bool readSensor(int pin) {
     int count = 0;
     for(int i=0; i<3; i++) {
       if(digitalRead(pin) == LOW) count++;
       delay(10);
     }
     return count >= 2;  // 2 out of 3 reads
   }
   ```

## Advanced Features

### 1. Add OLED Display

**Hardware:** SSD1306 128x64 OLED

**Wiring:**
```
SDA → GPIO 21
SCL → GPIO 22
VCC → 3V3
GND → GND
```

**Code:**
```cpp
#include <Adafruit_SSD1306.h>

Adafruit_SSD1306 display(128, 64, &Wire, -1);

void setup() {
  display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  display.setTextSize(1);
  display.setTextColor(WHITE);
}

void updateDisplay() {
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("LAST DROP");
  display.print("Player: ");
  display.println(players[currentPlayer].name);
  display.print("Water: ");
  display.println(players[currentPlayer].water);
  display.display();
}
```

### 2. Add Buzzer for Audio Feedback

**Hardware:** Passive buzzer

**Wiring:**
```
Buzzer + → GPIO 25
Buzzer - → GND
```

**Code:**
```cpp
const int BUZZER_PIN = 25;

void playTone(int freq, int duration) {
  tone(BUZZER_PIN, freq, duration);
  delay(duration);
  noTone(BUZZER_PIN);
}

// On coin placed:
playTone(1000, 100);  // Short beep

// On misplacement:
playTone(500, 300);   // Long low tone

// On game win:
for(int i=0; i<3; i++) {
  playTone(1500, 150);
  delay(100);
}
```

### 3. Battery Monitoring

**Hardware:** Voltage divider on ADC pin

**Wiring:**
```
Battery + → 10kΩ → GPIO 36 → 10kΩ → GND
```

**Code:**
```cpp
float getBatteryVoltage() {
  int raw = analogRead(36);
  return (raw / 4095.0) * 3.3 * 2;  // *2 for voltage divider
}

void checkBattery() {
  float voltage = getBatteryVoltage();
  if(voltage < 3.5) {
    Serial.println("⚠️ Low battery!");
    // Flash red LED warning
  }
}
```

## Performance Optimization

### 1. Reduce Network Latency

**Current:** 3-5 second round trip
**Target:** <1 second

**Optimizations:**
- Use UDP instead of HTTP for ESP32 callbacks
- Implement WebSocket connection (persistent)
- Batch sensor reads instead of individual checks

### 2. Improve Sensor Reliability

**Current:** 95% accuracy
**Target:** 99.9% accuracy

**Improvements:**
- Add capacitive sensors (backup detection)
- Implement sensor calibration routine
- Use machine learning for coin pattern recognition
- Add weight sensors (load cells) for verification

### 3. Scale to More Players

**Current:** 4 players max
**Target:** 8 players (2 boards)

**Approach:**
- Daisy-chain ESP32 boards via I2C
- Master board communicates with Android
- Slave board controls second set of 20 tiles
- Shared game state via broadcast

## Game Rule Enhancements

### 1. Tournament Mode

**Features:**
- Strict 30-second turn timer
- No undo allowed
- Auto-skip on timeout
- Leaderboard tracking

**Implementation:**
```cpp
unsigned long turnStartTime = 0;
const unsigned long TURN_TIMEOUT = 30000;

void checkTurnTimeout() {
  if(millis() - turnStartTime > TURN_TIMEOUT) {
    Serial.println("Turn timeout - skipping");
    nextPlayer();
    sendTimeoutToPhone();
  }
}
```

### 2. Power-Up Cards

**Concept:** Special tiles give temporary abilities

**Examples:**
- Double Movement (next roll × 2)
- Teleport (move to any tile)
- Shield (immune to one penalty)
- Swap (trade positions with any player)

**Implementation:**
- Add to tileEffects array
- Store active powerups per player
- Display with special LED colors

### 3. Team Mode

**Concept:** 2v2 gameplay

**Rules:**
- Team water pool (shared)
- One player from each team rolls alternately
- Team wins if either teammate reaches finish
- Special team power-ups

## Maintenance

### Daily Checks
- [ ] Verify WiFi connection
- [ ] Test all 20 Hall sensors
- [ ] Check LED strip integrity
- [ ] Inspect coin magnets

### Weekly Tasks
- [ ] Clean sensor surfaces
- [ ] Tighten loose wires
- [ ] Update firmware if available
- [ ] Backup game statistics

### Monthly Maintenance
- [ ] Replace weak magnets
- [ ] Recalibrate sensors
- [ ] Clean circuit board
- [ ] Test backup power supply

## Safety Warnings

⚠️ **Electrical Safety:**
- Never modify while powered on
- Use proper 5V regulated power supply
- Don't exceed LED strip current rating
- Ensure proper ventilation for ESP32

⚠️ **Magnet Safety:**
- Keep strong magnets away from electronics
- Supervise young children with magnetic coins
- Store magnets properly when not in use

## Support

For issues or questions:
1. Check serial monitor logs first
2. Review this guide's troubleshooting section
3. Test components individually
4. Contact: support@lastdrop.earth

## License

This project is part of the Last Drop educational game.
© 2025 Last Drop Team
