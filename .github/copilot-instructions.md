# Last Drop - AI Agent Instructions

## Project Overview

Last Drop is a **physical board game hybrid system** combining:
- **Android app** (Kotlin) - Game controller with GoDice BLE integration
- **ESP32 hardware** (Arduino C++) - Physical LED board with Hall effect sensors
- **Web display** (`live.html`) - Real-time spectator view
- **Server API** (`lastdrop.earth`) - Centralized game state and live updates

**Architecture**: GoDice → Android (via BLE) → {ESP32 (via BLE), Server API} → Web Display

## Test Modes for Distributed Development

The system includes **two comprehensive test modes** enabling distributed team workflows:

### Test Mode 1: ESP32 Board Only (`sketch_ble_testmode.ino`)
**For**: ESP32 hardware team (remote location)
**Tests**: Physical board, LEDs, Hall sensors, coin placement
**Features**:
- Full game logic on ESP32 (20 tiles, 10 chance cards, score tracking)
- Dummy dice generator in Android app (no GoDice needed)
- Comprehensive ESP32 → Android reporting with detailed JSON responses
- Real-time test log UI displaying all ESP32 events
- Validates: LED animations, Hall sensor detection, misplacement scanning, undo/reset

**Usage**: Upload `sketch_ble_testmode.ino`, enable Test Mode 1, tap "Simulate Dice Roll", observe test log

### Test Mode 2: Android + Web Only
**For**: Android/GoDice/Web team (user location)  
**Tests**: Android UI, game logic, live.html display, server API
**Features**:
- Bypasses ESP32 completely
- Uses `GameEngine.kt` for game logic
- Instant feedback (no coin placement wait)
- Tests API integration with live.html

**Usage**: Enable Test Mode 2, tap "Simulate Dice Roll", check live.html for animations

See `TEST_MODE_GUIDE.md` for complete documentation.

## Critical Architecture Decisions

### Multi-Device Communication Pattern
The Android app (`MainActivity.kt`) is the **orchestration hub**:
1. Receives dice rolls from GoDice SDK via BLE callbacks (`onDiceRoll`, `onDiceStable`)
2. Sends movement commands to ESP32 via BLE (Nordic UART Service)
3. Waits for physical coin placement confirmation from ESP32 Hall sensors
4. Only after confirmation, pushes complete state to server API for web display

**Key Insight**: The ESP32 does NOT connect to WiFi - it uses BLE to preserve phone's internet connection for API calls.

### Player Configuration System
At game start, Android collects:
- **Player count** (2-4 players)
- **Player names** and **coin colors** (red, green, blue, yellow)

This configuration is distributed to:
1. **ESP32** - Powers only active player LEDs with matching colors via `config` BLE command
2. **live.html** - Displays correct number of tokens via API push

See `PLAYER_CONFIG_INTEGRATION.md` for complete details.

### Two-Dice Mode & Averaging
When `playWithTwoDice = true`:
- Each die reports separately via `onDiceStable(diceId, number)`
- App calculates `avg = (dice1 + dice2) / 2` rounded
- **Critical**: ESP32 receives ONLY the averaged value, not individual dice
- Server API receives both `dice1`, `dice2`, AND `avg` for display purposes
- Individual dice colors tracked in `diceColorMap` for visual differentiation

See `TWO_DICE_COLOR_INTEGRATION.md` for per-die rolling status implementation.

## Project Structure

```
app/src/main/java/com/example/lastdrop/
  ├── MainActivity.kt          # Main orchestration (1600+ lines)
  ├── GameEngine.kt            # 20-tile board logic, chance cards
  ├── LastDropDatabase.kt      # Room DB (players, games, roll events)
  ├── LastDropDao.kt           # Database queries
  └── *Entity.kt               # Room entities

godicesdklib/                  # Native GoDice SDK (C + JNI)
  ├── src/main/java/.../GoDiceSDK.java
  └── common/godiceapi.{c,h,m}

sketch_ble.ino                 # ESP32 firmware (BLE version, 612 lines)
live.html                      # Web spectator display (3171 lines)
```

## Development Workflows

### Build & Run Android App
```powershell
# Standard Gradle build
.\gradlew assembleDebug

# Install on connected device
.\gradlew installDebug

# Build both app and godicesdklib modules
.\gradlew :app:build :godicesdklib:build
```

**Note**: Uses Gradle 8.13.1, Kotlin 2.0.21, AGP 8.13.1 (see `gradle/libs.versions.toml`)

**Security Setup Required**:
1. Copy `local.properties.template` to `local.properties`
2. Add your API key: `LASTDROP_API_KEY=your_key_here`
3. `local.properties` is gitignored - never commit it
4. API key loaded via `BuildConfig.API_KEY` at compile time

### ESP32 Firmware Upload
1. Open `sketch_ble.ino` in Arduino IDE
2. Install libraries: `Adafruit NeoPixel`, `ArduinoJson`, ESP32 BLE libraries
3. Board: "ESP32 Dev Module", upload speed 921600
4. Device advertises as `LASTDROP-ESP32` after upload
5. **Security**: Note MAC address from Serial Monitor for whitelisting

**Optional Pairing**: Set `BLE_PAIRING_ENABLED true` and `BLE_PAIRING_PIN 123456` for PIN-protected connections.

### Testing Physical Integration
See `IMPLEMENTATION_GUIDE.md` for hardware wiring and end-to-end test procedures.

## Key Conventions & Patterns

### BLE Communication (Android ↔ ESP32)
**Service UUID**: `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART)
- **RX Characteristic** (Android → ESP32): JSON commands `{command: "roll", playerId, diceValue, ...}`
- **TX Characteristic** (ESP32 → Android): JSON events `{event: "coin_placed", tile, ...}`

Commands: `roll`, `undo`, `reset`, `config`  
Events: `coin_placed`, `misplacement`, `undo_complete`, `ready`, `config_complete`

**Config Command**: Sends player count and colors to ESP32
```json
{
  "command": "config",
  "playerCount": 3,
  "colors": ["FF0000", "00FF00", "0000FF"]
}
```

See `ANDROID_BLE_INTEGRATION.md` and `PLAYER_CONFIG_INTEGRATION.md` for complete protocol specs.

### GoDice SDK Integration
MainActivity implements `GoDiceSDK.Listener`:
- `onDiceColor(diceId, color)` - Detects die shell color on connect
- `onDiceRoll(diceId, number)` - Fires WHILE rolling (triggers animation)
- `onDiceStable(diceId, number)` - Fires when settled (processes turn)
- `onDiceChargeLevel(diceId, level)` - Battery percentage

**Critical**: `diceId` is internal SDK identifier, NOT player index. Use `diceResults` map to pair dice.

### Room Database Schema
Three entities:
- `PlayerEntity`: `playerId`, `name`, `color`, `score`, `position`, `alive`
- `GameEntity`: `gameId`, `startTime`, `playerCount`
- `RollEventEntity`: `eventId`, `gameId`, `playerId`, `dice1`, `dice2`, `avg`, `timestamp`

Database name: `lastdrop.db`, version 1, no migrations yet.

### API Communication
Base URL: `https://lastdrop.earth`, API key: `ABC123` (hardcoded in MainActivity companion object)

Main endpoints:
- `POST /api/live_push.php` - Push complete game state for live display
- `POST /api/roll.php` - Log individual roll events
- `GET /api/live_state.php` - Fetch current game state

Payload includes `players` array + `lastEvent` object with `coinPlaced` flag.

## Debugging & Common Issues

### Security & API Keys
**API key not found**: Ensure `local.properties` exists with `LASTDROP_API_KEY=...`. BuildConfig defaults to `"ABC123"` if missing.

**Gitignore check**: Run `git status` - `local.properties` should NOT appear. If it does, it's not properly gitignored.

**MAC address filtering**: Populate `TRUSTED_ESP32_ADDRESSES` in MainActivity.kt (lines 51-56) to restrict BLE connections to specific ESP32 boards.

### Permission Runtime Checks
Android 12+ requires explicit runtime permissions for `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`. MainActivity handles this in `onCreate()` with `requestPermissionLauncher`.

**If dice won't connect**: Check Logcat for "Permission denied" - user must grant Bluetooth permissions.

### ESP32 Not Responding
1. Check BLE connection status in Android (should see "LASTDROP-ESP32")
2. ESP32 Serial Monitor should show "BLE Client Connected" + MAC address
3. If Hall sensors not triggering: verify GPIO pin assignments match `hallPins` array (lines 29-32 in sketch)
4. **If pairing enabled**: Android will prompt for PIN (default 123456)

### Undo Window Timeout
Undo button activates 5-second confirmation window (`undoTimer` coroutine). If player doesn't confirm within 5s, window closes automatically. This prevents accidental undos.

## File-Specific Notes

### MainActivity.kt Structure
- Lines 1-100: Imports, companion object (API config), UI/state variables
- Lines 150-250: Initialization (permissions, GoDice, BLE scanning)
- Lines 550-650: GoDice callbacks (onDiceStable processes turn logic)
- Lines 850-950: ESP32 BLE communication (send commands, parse responses)
- Lines 1200-1400: Game logic (process turn, apply chance cards, update scores)
- Lines 1500-1600: API push functions, undo system

### GameEngine.kt
Contains **hardcoded** 20-tile board definition and 15 chance cards. Tiles are 1-indexed.
`processTurn(currentPos, diceRoll)` returns `TurnResult` with new position, score change, and optional chance card.

**To modify board**: Edit `tiles` list (lines 42-62) and update tile types/names.

### sketch_ble.ino
- Lines 1-60: Hardware pin definitions, BLE UUIDs
- Lines 90-120: BLE callbacks (connection, command parsing)
- Lines 200-350: LED animations (rainbow sweep, coin wait blink)
- Lines 400-500: Hall sensor polling and coin detection
- Lines 550-612: Main loop (BLE reconnection, sensor scanning, LED updates)

**LED Layout**: 80 LEDs = 4 per tile. Tiles indexed 0-19 (not 1-20 like GameEngine).

## Testing Strategy

No automated tests exist yet. Manual testing workflow:
1. Build Android app, install on physical device
2. Upload ESP32 firmware, verify BLE advertising
3. Connect GoDice in app, roll dice physically
4. Verify LED moves to correct tile on ESP32
5. Place magnetic coin on Hall sensor
6. Check `live.html` displays token animation

See `IMPLEMENTATION_GUIDE.md` lines 70-110 for step-by-step test procedures.
## Reference Documentation

When modifying specific features, consult:
- `TEST_MODE_GUIDE.md` - Comprehensive test mode documentation (ESP32-only and Android/Web-only modes)
- `SECURITY.md` - API key management, BLE device filtering, pairing setup
- `ANDROID_BLE_INTEGRATION.md` - Complete BLE protocol specification
- `ESP32_INTEGRATION.md` - Data flow diagrams and callback sequences
- `TWO_DICE_COLOR_INTEGRATION.md` - Per-die tracking implementation
- `IMPLEMENTATION_GUIDE.md` - Hardware setup and testing procedures
- `PLAYER_COLOR_SELECTION.md` - Player color assignment logic
- `PLAYER_CONFIG_INTEGRATION.md` - Player count and color configuration system
- `SYNC_COMPLETE.md` - Game rules synchronization status
- `RULEBOOK.md` - Comprehensive game rules with all tiles and chance cards
- `local.properties.template` - Configuration template (copy to local.properties)

## Reference Documentation

When modifying specific features, consult:
- `ANDROID_BLE_INTEGRATION.md` - Complete BLE protocol specification
- `ESP32_INTEGRATION.md` - Data flow diagrams and callback sequences
- `TWO_DICE_COLOR_INTEGRATION.md` - Per-die tracking implementation
- `IMPLEMENTATION_GUIDE.md` - Hardware setup and testing procedures
- `PLAYER_COLOR_SELECTION.md` - Player color assignment logic
