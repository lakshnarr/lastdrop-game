# Last Drop - Physical Board Game Hybrid

A unique board game system combining physical GoDice, ESP32 LED hardware, and live web display.

## 📁 Project Structure

```
LastDrop/
├── README.md                    # This file
├── RULEBOOK.md                  # Complete game rules and mechanics
├── live.html                    # Web spectator display
│
├── ESP32 Program/               # ESP32 firmware files
│   ├── sketch_ble.ino          # Production firmware (BLE)
│   ├── sketch_ble_testmode.ino # Test mode firmware (full game logic)
│   └── sketch_enhanced.ino     # Enhanced version
│
├── DOCS/                        # All documentation
│   ├── ANDROID_BLE_INTEGRATION.md
│   ├── IMPLEMENTATION_GUIDE.md
│   ├── PLAYER_CONFIG_INTEGRATION.md
│   ├── SECURITY.md
│   ├── TEST_MODE_GUIDE.md
│   └── ... (17 documentation files total)
│
├── Test/                        # Test files and utilities
│   └── MainActivity_COMPLETE.kt
│
├── app/                         # Android application
│   └── src/main/java/com/example/lastdrop/
│       ├── MainActivity.kt
│       ├── GameEngine.kt
│       └── ... (Room DB, entities)
│
├── godicesdklib/                # GoDice SDK (native C + JNI)
└── gradle/                      # Gradle build configuration
```

**Key Folders**:
- **ESP32 Program/** - All `.ino` firmware files for ESP32 board
- **DOCS/** - Complete technical documentation and guides
- **Test/** - Test files and backup copies

## 🎲 System Components

- **Android App** (Kotlin) - Game controller with GoDice BLE integration
- **ESP32 Hardware** - Physical LED board (80 LEDs) with Hall effect sensors
- **Web Display** (`live.html`) - Real-time spectator view
- **Server API** - Game state synchronization

## 🚀 Quick Start

### 1. Security Setup (Required)

```powershell
# Run the security setup script
.\setup-security.ps1
```

This will:
- Create `local.properties` with your API key
- Optionally configure ESP32 MAC filtering
- Verify gitignore is protecting secrets

**Manual setup**: Copy `local.properties.template` to `local.properties` and add your API key.

### 2. Build Android App

```powershell
# Build the app
.\gradlew assembleDebug

# Install on connected Android device
.\gradlew installDebug
```

**Requirements**:
- Android Studio or Gradle 8.13+
- Android SDK (minSdk 24, targetSdk 34)
- Physical Android device (emulator won't work for BLE)

### 3. Upload ESP32 Firmware

1. Open `ESP32 Program/sketch_ble.ino` in Arduino IDE
2. Install libraries:
   - Adafruit NeoPixel
   - ArduinoJson
   - ESP32 BLE Arduino
3. Select Board: "ESP32 Dev Module"
4. Upload to your ESP32

**Hardware needed**:
- ESP32 Dev Board
- WS2812B LED Strip (80 LEDs)
- 20× Hall Effect Sensors (A3144)
- 4× Magnetic coins/tokens
- Power supply (5V, 3A)

See `DOCS/IMPLEMENTATION_GUIDE.md` for detailed wiring diagrams.

### 4. Get GoDice

Purchase from [particula.tech](https://particula.tech) or [Kickstarter](https://www.kickstarter.com/projects/godice/godice).

## 📖 Documentation

All technical documentation is now in the `DOCS/` folder:

- **[DOCS/SECURITY.md](DOCS/SECURITY.md)** - API keys, BLE filtering, security best practices
- **[DOCS/IMPLEMENTATION_GUIDE.md](DOCS/IMPLEMENTATION_GUIDE.md)** - Hardware setup and testing
- **[DOCS/ANDROID_BLE_INTEGRATION.md](DOCS/ANDROID_BLE_INTEGRATION.md)** - BLE protocol specs
- **[DOCS/ESP32_INTEGRATION.md](DOCS/ESP32_INTEGRATION.md)** - Data flow and callbacks
- **[DOCS/TEST_MODE_GUIDE.md](DOCS/TEST_MODE_GUIDE.md)** - Test mode documentation
- **[DOCS/PLAYER_CONFIG_INTEGRATION.md](DOCS/PLAYER_CONFIG_INTEGRATION.md)** - Player configuration system
- **[DOCS/SYNC_COMPLETE.md](DOCS/SYNC_COMPLETE.md)** - Game rules synchronization
- **[.github/copilot-instructions.md](.github/copilot-instructions.md)** - AI agent guide

See the `DOCS/` folder for all 17 documentation files.

## 🔒 Security Features

✅ **API Key Management** - Loaded from gitignored `local.properties`  
✅ **BLE Device Filtering** - Whitelist specific ESP32/GoDice MAC addresses  
✅ **Optional PIN Pairing** - Secure ESP32 connections with 6-digit PIN  
✅ **HTTPS Only** - All server communication encrypted  
✅ **Minimal Permissions** - Only Bluetooth + Internet, no location tracking  

**Never commit**:
- `local.properties` (contains API keys)
- Production credentials
- Device MAC addresses

## 🎮 How It Works

```
GoDice (BLE) → Android App (orchestrator)
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
    ESP32 Board            Server API
    (BLE + LEDs)         (lastdrop.earth)
        ↓                       ↓
   Hall Sensors           Web Display
   (coin detect)          (live.html)
```

1. Roll physical GoDice
2. Android receives roll via BLE
3. Android sends movement to ESP32 (avg if 2 dice)
4. ESP32 animates LED to new tile
5. Player places magnetic coin
6. Hall sensor detects coin
7. ESP32 confirms to Android
8. Android pushes complete state to server
9. Web display shows animated token movement

**Key insight**: ESP32 uses BLE (not WiFi) so phone keeps internet connection for API calls.

## 🛠️ Tech Stack

**Android**:
- Kotlin 2.0.21
- Gradle 8.13.1
- Room Database (player/game state)
- GoDice SDK (native C + JNI)
- OkHttp (API calls)
- Coroutines (async operations)

**ESP32**:
- Arduino C++
- Adafruit NeoPixel (LED control)
- ArduinoJson (BLE protocol)
- BLE Server (Nordic UART Service)

**Web**:
- Pure HTML/CSS/JS (no frameworks)
- Fetch API (poll server for updates)
- Canvas 2D (dice animations)

## 🧪 Testing

No automated tests yet. Manual workflow:

1. Build + install Android app
2. Upload ESP32 firmware
3. Connect GoDice in app
4. Roll dice physically
5. Verify LED animates on ESP32
6. Place coin on tile
7. Check web display updates

## 🤝 Contributing

1. Fork the repository
2. Run `.\setup-security.ps1` to configure local environment
3. Create feature branch: `git checkout -b feature/my-feature`
4. Make changes and test thoroughly
5. Commit: `git commit -m "Add feature X"`
6. Push: `git push origin feature/my-feature`
7. Open Pull Request

**Before submitting**:
- Ensure `local.properties` is NOT in your commits
- Test with physical hardware if possible
- Update relevant documentation

## 📝 License

[Add your license here]

## 🙏 Credits

- **GoDice SDK** - Particula Tech
- **Game Design** - [Your team]
- **Hardware Integration** - [Contributors]

## 📧 Support

- Issues: [GitHub Issues](https://github.com/lakshnarr/lastdrop-game/issues)
- Documentation: See `/docs` folder
- API: https://lastdrop.earth/docs

---

**Built with ❤️ for physical-digital hybrid gaming**
