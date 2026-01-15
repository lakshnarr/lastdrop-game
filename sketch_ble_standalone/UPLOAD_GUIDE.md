# 🚀 ESP32 Upload Scripts - Quick Reference

## Quick Start (30 seconds)

### **Option 1: Full Upload (Recommended for first time)**
```powershell
cd sketch_ble_standalone
.\upload_godice_test.ps1
```
- ✅ Checks everything (Arduino CLI, ESP32 board, COM port)
- ✅ Compiles firmware
- ✅ Shows memory usage
- ✅ Uploads to ESP32
- ✅ Offers to open Serial Monitor

### **Option 2: Quick Upload (For rapid testing)**
```powershell
.\quick_upload.ps1
```
- ⚡ No prompts, just upload and monitor
- ⚡ Perfect for code iterations
- ⚡ Auto-opens Serial Monitor after upload

### **Option 3: Monitor Only (No upload)**
```powershell
.\monitor.ps1
```
- 👁️ View serial output only
- 👁️ Resets ESP32 automatically
- 👁️ No compilation or upload

---

## Prerequisites

### 1. Arduino CLI Installed
The scripts use Arduino CLI from your `config.ps1`:
```powershell
C:\Users\ADMIN\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe
```

### 2. ESP32 Board Support
First time only - install ESP32 core:
```powershell
arduino-cli core update-index
arduino-cli core install esp32:esp32
```
(The upload script does this automatically if needed)

### 3. ESP32 Connected
- Connect ESP32 to USB
- Check COM port in Device Manager
- Update `$global:ESP32Port` in `config.ps1` if needed (default: COM10)

---

## Configuration

Edit `config.ps1` in project root to customize:

```powershell
$global:ESP32Board = "esp32:esp32:esp32s3:CDCOnBoot=cdc"  # Board type
$global:ESP32Port = "COM10"                                 # COM port
$global:ESP32BaudRate = 115200                             # Serial speed
```

---

## Upload Process Details

### Full Upload Script Does:
```
1. ✓ Load config.ps1
2. ✓ Verify sketch exists
3. ✓ Check Arduino CLI
4. ✓ Check ESP32 board support (install if needed)
5. ✓ Detect ESP32 on COM port
6. ⚙️  Compile firmware (1-2 min first time)
7. 📊 Show memory usage
8. 📤 Upload to ESP32 (30-60 sec)
9. ✓ Success confirmation
10. 📡 Optional: Open Serial Monitor
```

### Expected Output:
```
╔════════════════════════════════════════════════════════╗
║        GoDice ESP32 Test Firmware Upload              ║
╚════════════════════════════════════════════════════════╝

✓ Sketch found: godice_test.ino
✓ Arduino CLI ready
📦 Checking ESP32 board support...
✓ ESP32 board support installed
🔌 Checking ESP32 connection...
✓ ESP32 detected on COM10
⚙️  Compiling firmware...
✓ Compilation successful

Memory Usage:
  Sketch uses 385234 bytes (24%) of program storage
  Global variables use 84532 bytes (15%) of dynamic memory

📤 Uploading to ESP32...
✓ Upload successful!

╔════════════════════════════════════════════════════════╗
║              ✓ Firmware Uploaded Successfully          ║
╚════════════════════════════════════════════════════════╝
```

---

## Troubleshooting

### ❌ "Compilation failed"
**Fix:**
```powershell
# Update ESP32 core
arduino-cli core update-index
arduino-cli core upgrade esp32:esp32
```

### ❌ "Upload failed"
**Fix:**
1. Press and hold **BOOT button** on ESP32
2. Run upload script
3. Release BOOT when upload starts

Or:
```powershell
# Check if port is correct
arduino-cli board list

# Update config.ps1 with correct COM port
$global:ESP32Port = "COM3"  # Your actual port
```

### ❌ "ESP32 not detected"
**Fix:**
1. Check USB cable (must be data cable, not charge-only)
2. Install CH340/CP2102 drivers if needed
3. Check Device Manager for COM port
4. Try different USB port

### ❌ "Arduino CLI not found"
**Fix:**
```powershell
# Option 1: Install Arduino IDE (includes CLI)
# Download from: https://www.arduino.cc/en/software

# Option 2: Install CLI standalone
# Download from: https://arduino.github.io/arduino-cli/

# Update config.ps1 with correct path
$global:ArduinoCli = "C:\Path\To\arduino-cli.exe"
```

### ❌ "Permission denied" on COM port
**Fix:**
- Close Arduino IDE
- Close any Serial Monitor programs
- Close other terminals with serial connections
- Restart PowerShell as Administrator

---

## Serial Monitor Controls

### In Monitor:
- **Ctrl+C** - Exit monitor
- Type commands:
  - `s` - Start dice scan
  - `b` - Request battery
  - `c` - Request color
  - `l` - LEDs on (red)
  - `o` - LEDs off
  - `d` - Disconnect dice
  - `h` - Show help

### Alternative Monitors:
If arduino-cli monitor has issues:
```powershell
# Use PuTTY
putty -serial COM10 -sercfg 115200

# Use PowerShell native
.\monitor.ps1  # Includes auto-reset
```

---

## Development Workflow

### Typical Iteration:
```powershell
1. Edit code in godice_test.ino
2. Run: .\quick_upload.ps1
3. Watch Serial Monitor output
4. Test with GoDice
5. Repeat
```

### Testing Multiple Changes:
```powershell
# Edit godiceapi.c
.\quick_upload.ps1

# Watch for errors
# Make fixes

.\quick_upload.ps1
# Repeat until working
```

### Clean Build:
```powershell
# Full rebuild (if compilation issues)
arduino-cli cache clean
.\upload_godice_test.ps1
```

---

## Memory Optimization

If sketch is too large:

### 1. Check Current Usage
```powershell
.\upload_godice_test.ps1
# Look for: "Sketch uses X bytes (XX%)"
```

### 2. Reduce Size
Edit `godice_test.ino`:
```cpp
// Disable logging
// #define GODICE_LOGGING

// Remove unused features
// Remove serialEvent() function
// Simplify onDiceStable callback
```

### 3. Change Partition Scheme
Edit `config.ps1`:
```powershell
# Larger app partition
$global:ESP32Board = "esp32:esp32:esp32s3:PartitionScheme=huge_app,CDCOnBoot=cdc"
```

---

## Advanced Options

### Custom Board Config
```powershell
# ESP32 (non-S3)
$global:ESP32Board = "esp32:esp32:esp32:CDCOnBoot=cdc"

# ESP32-C3
$global:ESP32Board = "esp32:esp32:esp32c3:CDCOnBoot=cdc"

# With specific flash size
$global:ESP32Board = "esp32:esp32:esp32s3:FlashSize=16M,CDCOnBoot=cdc"
```

### Upload Speed
```powershell
# Faster upload (may be unstable)
arduino-cli upload --fqbn $global:ESP32Board --port COM10 --upload-speed 921600

# Slower upload (more reliable)
arduino-cli upload --fqbn $global:ESP32Board --port COM10 --upload-speed 115200
```

### Verbose Output
```powershell
# See detailed compile/upload logs
arduino-cli compile --fqbn $global:ESP32Board --verbose
arduino-cli upload --fqbn $global:ESP32Board --port COM10 --verbose
```

---

## File Summary

| Script | Purpose | Use When |
|--------|---------|----------|
| `upload_godice_test.ps1` | Full upload with checks | First time, or after changes |
| `quick_upload.ps1` | Fast upload + monitor | Rapid development |
| `monitor.ps1` | View output only | Already uploaded |
| `godice_test.ino` | Test firmware | Main sketch file |

---

## Integration with config.ps1

These scripts automatically load settings from `config.ps1`:
- ✅ Arduino CLI path
- ✅ ESP32 board type
- ✅ COM port
- ✅ Baud rate

No need to edit individual scripts - just update `config.ps1` once!

---

## Next Steps

After successful upload:

1. ✅ **Verify boot** - See banner in Serial Monitor
2. ✅ **Test connection** - Power on GoDice
3. ✅ **Test roll detection** - Roll die
4. ✅ **Test LED control** - Type 'l' then 'o'
5. ✅ **Run full tests** - Follow `GODICE_TEST_PLAN.md`

Then proceed to:
- 🖥️ Add LCD display integration
- 🎮 Implement touch UI
- 🎲 Build complete game system

---

**Need help?** Check:
- `GODICE_TEST_PLAN.md` - Comprehensive testing
- `QUICK_TEST_GUIDE.md` - 5-minute quick test
- `GODICE_LIBRARY_README.md` - API documentation
