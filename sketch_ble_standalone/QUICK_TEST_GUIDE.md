# GoDice ESP32 Library - Quick Test Guide

## 🚀 Quick Start (5 Minutes)

### 1. Hardware Setup
- Connect ESP32 to computer via USB
- Power on GoDice (remove from charger, shake)
- Keep die within 2 meters of ESP32

### 2. Upload Code
```
Arduino IDE:
  File → Open → sketch_ble_standalone/godice_test.ino
  Tools → Board → ESP32S3 Dev Module
  Tools → Port → [Select COM port]
  Click Upload ▶
```

### 3. Open Serial Monitor
```
Tools → Serial Monitor
Set to 115200 baud
```

### 4. Expected Output
```
╔════════════════════════════════════════════════════════╗
║          GoDice ESP32 Integration Test                ║
╚════════════════════════════════════════════════════════╝

📡 Starting scan for GoDice...

✓ DICE CONNECTED - Slot 0
  Address: XX:XX:XX:XX:XX:XX

🎨 DICE COLOR DETECTED - Slot 0
   Shell Color: Red

🔋 BATTERY LEVEL - Slot 0
   Level: 85%
```

### 5. Roll Test
- Pick up die and roll it
- You should see:
```
🎲 Rolling... (Slot 0)

╔════════════════════════════════════════╗
║  🎯 DICE STABLE - Slot 0              ║
║     Roll Value: 4                     ║
╚════════════════════════════════════════╝
```

---

## ✅ Quick Validation Checklist

**Boot** (5 seconds):
- [ ] Banner appears
- [ ] "Starting scan" message

**Connection** (10-30 seconds):
- [ ] "Found GoDice" message
- [ ] "DICE CONNECTED" banner
- [ ] Die LEDs blink green 3 times

**Detection** (5-10 seconds):
- [ ] Color detected
- [ ] Battery level shown
- [ ] Battery bar displays

**Roll Test** (manual):
- [ ] Roll die vigorously
- [ ] "Rolling..." appears while tumbling
- [ ] Yellow LEDs blink on die
- [ ] Stable value appears (1-6)
- [ ] Value matches actual top face

**LED Test** (manual):
- [ ] Type `l` in Serial Monitor
- [ ] Die LEDs turn red
- [ ] Type `o` in Serial Monitor  
- [ ] LEDs turn off

---

## 🎮 Serial Commands

Type these in Serial Monitor to test features:

| Key | Command | Result |
|-----|---------|--------|
| `s` | Scan | Start scanning for dice |
| `b` | Battery | Request battery level |
| `c` | Color | Request shell color |
| `l` | LED On | Turn LEDs red |
| `o` | LED Off | Turn off LEDs |
| `d` | Disconnect | Disconnect all dice |
| `h` | Help | Show command help |

---

## 🔧 Troubleshooting

### ❌ "No dice found"
**Fix**: 
1. Make sure die is ON (shake vigorously)
2. Move die closer to ESP32
3. Check die is charged (>20%)
4. Try resetting ESP32

### ❌ Connection drops
**Fix**:
1. Charge die to >50%
2. Keep die within 2 meters
3. Don't let die auto-sleep (roll occasionally)

### ❌ Wrong roll values
**Fix**:
1. Let die settle completely
2. Roll on flat, hard surface
3. Make sure using D6 die (not D20)

### ❌ LEDs don't work
**Fix**:
1. Charge die fully
2. Reset die (place on charger, remove)
3. Add delays between commands

### ❌ Compilation error
**Fix**:
1. Install ESP32 board support in Arduino IDE
2. Update to version 2.0.11 or later
3. Restart Arduino IDE

---

## 📊 What Success Looks Like

If you see ALL of these, the library is working perfectly:

✅ Boot banner displays  
✅ Scan finds die within 30 seconds  
✅ Connection established successfully  
✅ Color detected and displayed  
✅ Battery level reported  
✅ Roll detection works (rolling + stable)  
✅ Roll values are accurate (1-6)  
✅ LED commands work  
✅ Connection stays stable  

**STATUS: PRODUCTION READY** 🎉

---

## 🎯 Next Steps

Once all tests pass:

1. **Add LCD display** - Follow `ESP32_STANDALONE_DESIGN.md`
2. **Integrate LED board** - Connect existing LED strip code
3. **Add Hall sensors** - Connect existing MCP23017 code
4. **Implement game** - Use existing GameEngine
5. **Build complete system** - Assemble all components

---

## 📝 Test Results Template

Copy and fill out:

```
TEST DATE: ______________
HARDWARE: ESP32-S3 / ESP32
GODICE: D6 Standard, [Color] Shell
BATTERY: ____%

RESULTS:
✓/✗ Boot banner
✓/✗ Scan found die
✓/✗ Connection established
✓/✗ Color detected correctly
✓/✗ Battery reported
✓/✗ Roll detection works
✓/✗ Values accurate
✓/✗ LED control works
✓/✗ Connection stable

ISSUES FOUND:
1. ________________
2. ________________

OVERALL: ✓ PASS / ✗ FAIL

NOTES:
_____________________
_____________________
```

---

## 🆘 Need Help?

**Check these files:**
- `GODICE_TEST_PLAN.md` - Comprehensive test procedures
- `GODICE_LIBRARY_README.md` - Complete API documentation
- `ESP32_STANDALONE_DESIGN.md` - Full system design

**Common Issues:**
- Die won't connect → Charge battery, shake to wake
- Compilation errors → Update ESP32 board support
- LED issues → Add delays, check battery
- Roll detection → Use D6 die, flat surface

---

**ESTIMATED TIME: 5-10 minutes**  
**DIFFICULTY: Easy** ⭐☆☆☆☆

Once this test passes, you're ready to build the complete standalone board game system! 🎲✨
