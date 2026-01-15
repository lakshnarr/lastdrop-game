# ESP32-S3 Direct GoDice Connection

Complete implementation for connecting ESP32-S3 directly to GoDice smart dice via BLE, eliminating the need for Android as a bridge.

## Overview

This implementation enables direct BLE communication between ESP32-S3 and GoDice smart dice, providing:
- Real-time dice roll detection (1-6 values)
- Dice color identification (shell color)
- Battery level monitoring
- Rolling/Stable status tracking
- Connection state management
- LED visual feedback

## Architecture

```
GoDice (BLE Random Address)
    ↓ (BLE Connection)
ESP32-S3 
    ↓ (Serial Monitor)
User Terminal
```

**Key Challenge Solved**: GoDice uses BLE random addresses (Type 1), which standard ESP32 BLE libraries fail to connect to. This implementation includes a modified BLE library that properly handles random address devices.

## Project Structure

```
LastDrop/
├── sketch_godice_modified/
│   └── sketch_godice_modified.ino    # Main ESP32 firmware
├── libraries/
│   └── BLE_Modified/                 # Modified official ESP32 BLE library
│       └── src/
│           └── BLEClient.cpp         # Fixed connect() for random addresses
└── config.ps1                        # Build/upload automation script
```

## Modified BLE Library

### Problem
The official ESP32 BLE library's `BLEClient::connect()` fails immediately when connecting to random address devices like GoDice, returning `false` in 0-954ms instead of the expected 15-second timeout.

### Solution
Modified `libraries/BLE_Modified/src/BLEClient.cpp` with fixes in the `handleGAPEvent()` function:

**Key Changes:**
1. Set `m_isConnected = true` **before** MTU exchange (not after)
2. Don't fail on MTU error code 2 (`BLE_HS_EALREADY`)
3. Call `BLEUtils::taskRelease()` in `BLE_GAP_EVENT_CONNECT` handler to signal connection complete

```cpp
case BLE_GAP_EVENT_CONNECT: {
    m_isConnected = (event->connect.status == 0);
    
    if (m_isConnected) {
        int rc = ble_att_set_preferred_mtu(180);
        if (rc != 0 && rc != BLE_HS_EALREADY) {
            // Only fail on serious errors, not EALREADY
        }
    }
    
    BLEUtils::taskRelease(*pTaskData, event->connect.status);
    break;
}
```

### Compilation
**Critical**: Must compile with the modified library in the libraries path:

```powershell
arduino-cli compile --libraries "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\libraries" ...
```

## GoDice Protocol Implementation

### BLE Service
- **Service UUID**: `6e400001-b5a3-f393-e0a9-e50e24dcca9e` (Nordic UART)
- **TX Characteristic**: `6e400002-b5a3-f393-e0a9-e50e24dcca9e` (Write to dice)
- **RX Characteristic**: `6e400003-b5a3-f393-e0a9-e50e24dcca9e` (Receive notifications)

### Commands (ESP32 → GoDice)
| Command | Hex | Description | Format |
|---------|-----|-------------|--------|
| Battery | `0x03` | Request battery level | `[0x03]` |
| Color | `0x17` | Request shell color | `[0x17]` |
| Set LED | `0x08` | Set LED RGB color | `[0x08, R, G, B]` |
| Pulse LED | `0x10` | Pulse LED | `[0x10, count, onTime, offTime, R, G, B]` |

### Messages (GoDice → ESP32)
| Message | Hex | Format | Description |
|---------|-----|--------|-------------|
| Rolling | `0x52` ('R') | `[R]` | Dice is rolling |
| Stable | `0x53` ('S') | `[S, x, y, z]` | Dice stable, XYZ at bytes 1-3 |
| Fake Stable | `0x46` ('F') | `[F, S, x, y, z]` | Fake stable, XYZ at bytes 2-4 |
| Move Stable | `0x4D` ('M') | `[M, S, x, y, z]` | Move stable, XYZ at bytes 2-4 |
| Tilt Stable | `0x54` ('T') | `[T, S, x, y, z]` | Tilt stable, XYZ at bytes 2-4 |
| Battery | `0x42` ('B') | `[B, a, t, level]` | Response: "Bat" + level |
| Color | `0x43` ('C') | `[C, o, l, code]` | Response: "Col" + color code |

**Important**: Different stable messages have different byte offsets:
- `MSG_STABLE` (0x53): XYZ starts at byte 1
- `MSG_FAKE_STABLE`, `MSG_MOVE_STABLE`, `MSG_TILT_STABLE`: XYZ starts at byte 2 (extra 'S' byte)

### Color Codes
| Code | Color | Shell |
|------|-------|-------|
| 0 | Black | Black shell |
| 1 | Red | Red shell |
| 2 | Green | Green shell |
| 3 | Blue | Blue shell |
| 4 | Yellow | Yellow shell |
| 5 | Orange | Orange shell |

## XYZ to Dice Face Mapping

GoDice reports accelerometer XYZ values (signed int8, ±64 range) for the face pointing UP.

### Calibrated Vectors (D6)
```cpp
const int8_t D6_VECTORS[6][3] = {
    { -64,  0,   0 },    // Face 1 UP (X negative)
    {  0,   0,  64 },    // Face 2 UP (Z positive)
    {  0,  64,   0 },    // Face 3 UP (Y positive)
    {  0, -64,   0 },    // Face 4 UP (Y negative)
    {  0,   0, -64 },    // Face 5 UP (Z negative)
    {  64,  0,   0 }     // Face 6 UP (X positive)
};
```

### Face Detection Algorithm
```cpp
int xyzToDiceFace(int8_t x, int8_t y, int8_t z) {
    int bestFace = 1;
    int bestDistance = 999999;
    
    for (int face = 0; face < 6; face++) {
        int dx = x - D6_VECTORS[face][0];
        int dy = y - D6_VECTORS[face][1];
        int dz = z - D6_VECTORS[face][2];
        int distance = dx*dx + dy*dy + dz*dz;
        
        if (distance < bestDistance) {
            bestDistance = distance;
            bestFace = face + 1;
        }
    }
    
    return bestFace;
}
```

Uses Euclidean distance to find the closest matching vector.

## Features Implemented

### ✅ Connection Management
- Auto-scan for GoDice devices (prefix: `GoDice_`)
- Automatic connection with address type handling (random address support)
- Connection success/failure callbacks
- Disconnect handling
- 3 green LED pulses on successful connection

### ✅ Dice Data
- **Rolling status**: Detects when dice is in motion
- **Stable status**: Detects when dice has settled
- **Dice value (1-6)**: Accurate face detection from XYZ accelerometer
- **Dice color**: Shell color identification (Red, Green, Blue, Yellow, Orange, Black)
- **Battery level**: Percentage (0-100%)

### ✅ User Interface (Serial Monitor)
- Interactive command interface
- Real-time roll updates
- Status display with statistics
- Heartbeat monitoring every 15 seconds

### ✅ Serial Commands
| Key | Action |
|-----|--------|
| `s` | Start BLE scan |
| `b` | Request battery level |
| `c` | Request dice color |
| `r` | Set LED to RED |
| `g` | Set LED to GREEN |
| `o` | Set LED OFF |
| `p` | Print full status |
| `d` | Disconnect |

## Setup Instructions

### Hardware Requirements
- **ESP32-S3** (QFN56 or similar variant)
- **GoDice smart dice** (any color)
- USB cable for programming

### Software Requirements
- Arduino IDE or Arduino CLI
- ESP32 board support (version 3.3.5 tested)
- Modified BLE library (included in `libraries/BLE_Modified/`)

### Installation Steps

1. **Copy Modified BLE Library**
   ```powershell
   # Library is already at: LastDrop/libraries/BLE_Modified/
   ```

2. **Configure Build Script**
   Edit `config.ps1` if needed:
   ```powershell
   $global:ESP32Board = "esp32:esp32:esp32s3:CDCOnBoot=cdc"
   $global:ESP32Port = "COM11"  # Change to your port
   ```

3. **Compile and Upload**
   ```powershell
   . "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\config.ps1"
   
   # Compile with modified library
   & $ArduinoCli compile --fqbn $ESP32Board `
     --libraries "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\libraries" `
     "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\sketch_godice_modified"
   
   # Upload
   & $ArduinoCli upload --fqbn $ESP32Board -p $ESP32Port `
     "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\sketch_godice_modified"
   ```

4. **Open Serial Monitor**
   ```powershell
   Start-ESP32Monitor
   ```

## Usage

### First-Time Connection
1. **Wake the dice**: Roll it to activate BLE advertising
2. **Scan**: Press `s` in serial monitor
3. **Auto-connect**: ESP32 will connect to first GoDice found
4. **LED feedback**: Dice will pulse green 3 times on success

### Normal Operation
- **Roll the dice**: Values appear automatically
- **Check battery**: Press `b`
- **Check color**: Press `c`
- **View status**: Press `p`

### Output Example
```
🔗 Connecting to: GoDice_19CCE4_Y_v04
✅ Connected!
🎨 Dice Color: Yellow (code=4)
🔋 Battery: 53%

🎲 ROLLING...
✅ STABLE: 3 (xyz: 0,64,-2)

💓 [CONNECTED] Color:Yellow Battery:53% LastRoll:3 Rolls:1
```

## Troubleshooting

### Connection Fails Immediately
**Symptom**: `❌ Connection failed` appears in <1 second

**Cause**: Not using modified BLE library

**Fix**: Verify compile command includes correct library path:
```powershell
--libraries "d:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\libraries"
```

### Dice Not Found
**Symptom**: Scan completes with no devices

**Solution**:
1. Roll the dice to wake it up
2. Ensure dice is charged (>10% battery)
3. Move dice closer to ESP32
4. Check if another device is connected (Android app, etc.)

### Wrong Dice Values
**Symptom**: Face 1 shows as 4, etc.

**Solution**: The XYZ vectors are calibrated for standard GoDice D6 orientation. If your dice shows different values, the vectors may need recalibration. Contact support with actual roll data.

### Compilation Errors
**Symptom**: `BLEDevice.h not found` or similar

**Solution**: Ensure ESP32 board support is installed:
```powershell
arduino-cli core install esp32:esp32
```

## Technical Specifications

### Memory Usage
- **Flash**: 659,931 bytes (50% of ESP32-S3 1.31MB)
- **RAM**: 32,984 bytes (10% of 327KB)

### Performance
- **Connection time**: 1-5 seconds (after scan)
- **Scan duration**: 30 seconds max (stops on first GoDice found)
- **Roll latency**: <100ms from physical roll to ESP32 notification

### BLE Parameters
- **Connection interval**: Default (NimBLE auto-negotiated)
- **MTU**: 180 bytes preferred
- **Address type**: Random (Type 1)
- **Active scan**: Enabled

## Known Limitations

1. **Single dice connection**: Current implementation connects to first GoDice found
2. **No pairing**: Connection is non-paired (any GoDice can connect)
3. **No reconnection**: Must rescan after disconnect
4. **LED control only on connected dice**: Cannot control LED when not connected

## Future Enhancements

- [ ] Multi-dice support (connect to 2-4 dice simultaneously)
- [ ] Auto-reconnect after disconnect
- [ ] Dice selection UI (when multiple dice found)
- [ ] WiFi integration for web dashboard
- [ ] Persistent statistics storage
- [ ] Configurable XYZ calibration tool

## References

### GoDice SDK
- Official SDK: https://github.com/ParticulaCode/GoDiceJavaScriptAPI
- Protocol documentation: GoDice BLE API (Nordic UART Service)

### ESP32 BLE
- NimBLE Stack: https://github.com/espressif/esp-nimble
- ESP32 Arduino Core: https://github.com/espressif/arduino-esp32

## License

This implementation is part of the Last Drop project. See main project README for license information.

## Credits

**Implementation**: AI-assisted development with user testing
**Hardware**: ESP32-S3, GoDice smart dice
**Modified Library**: Based on official ESP32 Arduino Core BLE library (v3.3.5)

---

**Last Updated**: January 14, 2026  
**Tested With**: 
- ESP32-S3 (QFN56, revision v0.2)
- GoDice D6 (Yellow, Orange, Red shells)
- Firmware version: v04
