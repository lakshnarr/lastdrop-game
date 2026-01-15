# GoDice ESP32 Library - Test Plan & Verification

## Test Environment
- **Date**: January 14, 2026
- **Hardware**: ESP32-S3 Dev Board or ESP32 Dev Module
- **Library Version**: 1.0.0
- **Test Sketch**: godice_test.ino

---

## Pre-Test Setup Checklist

### ☐ Hardware Requirements
- [ ] ESP32-S3 or ESP32 Dev Board
- [ ] USB cable for programming
- [ ] At least 1 GoDice (up to 2 for multi-dice testing)
- [ ] GoDice charging cable
- [ ] Computer with Arduino IDE 2.x

### ☐ Software Requirements
- [ ] Arduino IDE 2.0 or later installed
- [ ] ESP32 board support installed (version 2.0.11+)
- [ ] Serial Monitor or terminal emulator

### ☐ Arduino IDE Configuration
```
Tools → Board → ESP32 Arduino → ESP32S3 Dev Module
Tools → USB CDC On Boot → Enabled
Tools → Flash Size → 16MB (if available)
Tools → Partition Scheme → Default
Tools → Upload Speed → 921600
```

### ☐ Library Files Verification
Ensure all files are in `sketch_ble_standalone/` folder:
- [ ] godice_test.ino (280 lines)
- [ ] godiceapi.h (310 lines)
- [ ] godiceapi.c (530 lines)
- [ ] godice_ble_client.h (180 lines)
- [ ] godice_ble_client.cpp (450 lines)

---

## Test Procedures

## TEST 1: Compilation Test
**Objective**: Verify code compiles without errors

### Steps:
1. Open `godice_test.ino` in Arduino IDE
2. Click "Verify" button (checkmark icon)
3. Wait for compilation to complete

### Expected Results:
```
Sketch uses XXXXX bytes (XX%) of program storage space
Global variables use XXXXX bytes (XX%) of dynamic memory
```

### Pass Criteria:
- [ ] No compilation errors
- [ ] No warnings (or only minor ESP32 warnings)
- [ ] Sketch size < 1MB
- [ ] Global variables < 200KB

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 2: Upload & Boot Test
**Objective**: Verify sketch uploads and boots correctly

### Steps:
1. Connect ESP32 to computer via USB
2. Select correct COM port in Arduino IDE
3. Click "Upload" button
4. Open Serial Monitor (115200 baud)
5. Press ESP32 reset button if needed

### Expected Serial Output:
```
╔════════════════════════════════════════════════════════╗
║          GoDice ESP32 Integration Test                ║
║                                                        ║
║  Features Tested:                                     ║
║  • Dice scanning and connection                       ║
║  • Shell color detection                              ║
║  • Roll detection (rolling + stable)                  ║
║  • Battery monitoring                                 ║
║  • Charging state                                     ║
║  • LED control                                        ║
╚════════════════════════════════════════════════════════╝

📡 Starting scan for GoDice...
```

### Pass Criteria:
- [ ] Upload completes successfully
- [ ] Serial output appears immediately after boot
- [ ] Banner displays correctly
- [ ] "Starting scan" message appears
- [ ] No crash or reboot loop

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 3: BLE Scanning Test
**Objective**: Verify ESP32 can scan for BLE devices

### Preparation:
1. Ensure GoDice is charged (>20% battery)
2. Power on GoDice (remove from charger, shake to wake)
3. Keep GoDice within 2 meters of ESP32

### Steps:
1. Watch Serial Monitor after boot
2. Wait up to 10 seconds for scan results

### Expected Serial Output:
```
📡 Starting scan for GoDice...
   (Make sure your dice are powered on)

Found GoDice: GoDice_XXXX (XX:XX:XX:XX:XX:XX)
```

### Pass Criteria:
- [ ] Scan starts within 1 second of boot
- [ ] GoDice detected within 10 seconds
- [ ] Device name contains "GoDice"
- [ ] MAC address displayed correctly
- [ ] No BLE errors in Serial Monitor

**Status**: ☐ PASS ☐ FAIL  
**GoDice MAC**: _______________________________________________  
**Notes**: _______________________________________________

---

## TEST 4: Connection Test
**Objective**: Verify ESP32 can connect to GoDice

### Steps:
1. Wait for scan to detect GoDice
2. Auto-connection should start immediately
3. Observe Serial Monitor for connection events

### Expected Serial Output:
```
GoDice slot 0 connected

========================================
✓ DICE CONNECTED - Slot 0
  Address: XX:XX:XX:XX:XX:XX
  Name: GoDice_XXXX
========================================

Sent init packet to slot 0
```

### Pass Criteria:
- [ ] Connection established within 5 seconds of detection
- [ ] "DICE CONNECTED" banner displays
- [ ] MAC address matches scanned address
- [ ] Init packet sent successfully
- [ ] Connection remains stable (no disconnect for 30s)

**Status**: ☐ PASS ☐ FAIL  
**Connection Time**: _____ seconds  
**Notes**: _______________________________________________

---

## TEST 5: Shell Color Detection
**Objective**: Verify die shell color is detected correctly

### Steps:
1. After connection, wait 2-3 seconds
2. Observe color detection event in Serial Monitor

### Expected Serial Output:
```
----------------------------------------
🎨 DICE COLOR DETECTED - Slot 0
   Shell Color: [Black/Red/Green/Blue/Yellow/Orange]
----------------------------------------
```

### Pass Criteria:
- [ ] Color event received within 5 seconds of connection
- [ ] Detected color matches actual die shell color
- [ ] Die LEDs briefly light up with matching color
- [ ] LEDs turn off after 2 seconds

**Status**: ☐ PASS ☐ FAIL  
**Expected Color**: _______________________________________________  
**Detected Color**: _______________________________________________  
**Notes**: _______________________________________________

---

## TEST 6: Battery Level Detection
**Objective**: Verify battery level is reported correctly

### Steps:
1. After connection, wait 3-5 seconds
2. Observe battery event in Serial Monitor

### Expected Serial Output:
```
----------------------------------------
🔋 BATTERY LEVEL - Slot 0
   Level: XX%
   [████████████████░░░░]
   
   ⚠️  LOW BATTERY WARNING!  (if < 20%)
----------------------------------------
```

### Pass Criteria:
- [ ] Battery event received within 10 seconds of connection
- [ ] Battery percentage is reasonable (0-100%)
- [ ] Battery bar displays correctly
- [ ] Low battery warning if < 20%
- [ ] Can request battery again with 'b' command

**Status**: ☐ PASS ☐ FAIL  
**Battery Level**: _____% at _____ (time)  
**Notes**: _______________________________________________

---

## TEST 7: Roll Detection - Rolling State
**Objective**: Verify die rolling state is detected

### Steps:
1. Pick up connected die
2. Roll die vigorously
3. Observe Serial Monitor during roll

### Expected Serial Output:
```
🎲 Rolling... (Slot 0)
```

### Pass Criteria:
- [ ] "Rolling..." message appears during tumble
- [ ] Message appears within 0.5s of starting roll
- [ ] Die LEDs blink yellow rapidly during roll
- [ ] Multiple rolling messages may appear for one roll

**Status**: ☐ PASS ☐ FAIL  
**Response Time**: _____ ms (estimate)  
**Notes**: _______________________________________________

---

## TEST 8: Roll Detection - Stable State
**Objective**: Verify die face value is detected when stable

### Steps:
1. Continue from TEST 7
2. Let die settle on a face
3. Observe final value in Serial Monitor

### Expected Serial Output:
```
╔════════════════════════════════════════╗
║  🎯 DICE STABLE - Slot 0              ║
║     Roll Value: X                     ║
╚════════════════════════════════════════╝
```

### Pass Criteria:
- [ ] Stable event received within 2s of die settling
- [ ] Roll value matches actual top face (1-6)
- [ ] Yellow blinking stops
- [ ] LEDs briefly show brightness based on value
- [ ] Can roll again and get different values

**Status**: ☐ PASS ☐ FAIL  
**Test Rolls**:
- Roll 1: Expected ____ Detected ____
- Roll 2: Expected ____ Detected ____
- Roll 3: Expected ____ Detected ____
- Roll 4: Expected ____ Detected ____
- Roll 5: Expected ____ Detected ____

**Accuracy**: _____/5 correct  
**Notes**: _______________________________________________

---

## TEST 9: LED Control - Static Colors
**Objective**: Verify LED color commands work

### Steps:
1. Send 'l' command via Serial Monitor
2. Observe die LEDs

### Expected Results:
- Both LEDs turn solid red
- LEDs stay on until turned off

### Steps to Test All Colors:
Modify test sketch to test other colors:
```cpp
// Red
goDiceClient.setLEDColors(0, 255, 0, 0, 255, 0, 0);

// Green
goDiceClient.setLEDColors(0, 0, 255, 0, 0, 255, 0);

// Blue
goDiceClient.setLEDColors(0, 0, 0, 255, 0, 0, 255);

// White
goDiceClient.setLEDColors(0, 255, 255, 255, 255, 255, 255);

// Mixed (Red + Blue)
goDiceClient.setLEDColors(0, 255, 0, 0, 0, 0, 255);
```

### Pass Criteria:
- [ ] Red command works (press 'l')
- [ ] LEDs respond within 0.5s
- [ ] Colors are accurate
- [ ] Both LEDs light up
- [ ] Off command works (press 'o')

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 10: LED Control - Blinking Patterns
**Objective**: Verify LED blinking patterns work

### Steps:
1. Die should blink green 3 times after connection (auto-test)
2. Roll die to trigger yellow blinking during roll
3. Test custom blink patterns

### Observed Behaviors:
- [ ] Connection blink (3× green, auto)
- [ ] Rolling blink (yellow rapid, auto during roll)
- [ ] Custom patterns work (modify sketch to test)

### Pass Criteria:
- [ ] Blink count is correct
- [ ] Blink timing is correct
- [ ] Colors are accurate
- [ ] Pattern stops when commanded

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 11: Charging State Detection
**Objective**: Verify charging state is detected

### Steps:
1. While die connected, place on charging pad
2. Observe Serial Monitor

### Expected Serial Output:
```
----------------------------------------
⚡ CHARGING STATE - Slot 0
   Status: CHARGING
----------------------------------------
```

### Steps to Test:
1. Place die on charger while connected
2. Wait 5-10 seconds for charging event
3. Remove die from charger
4. Wait for not-charging event

### Pass Criteria:
- [ ] Charging event detected within 10s of placing on charger
- [ ] "CHARGING" status correct
- [ ] Green pulsing LEDs appear during charging
- [ ] Not-charging event detected when removed

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 12: Connection Stability Test
**Objective**: Verify connection remains stable over time

### Steps:
1. Keep die connected
2. Monitor status updates for 5 minutes
3. Roll die occasionally
4. Check status display every 10 seconds

### Expected Serial Output (every 10s):
```
┌─────────────────────────────────────┐
│  Connected Dice: 1 / 2              │
│  Slot 0:                            │
│    Battery: XX%                     │
│    Last Roll: X                     │
│    Rolling: No                      │
└─────────────────────────────────────┘
```

### Pass Criteria:
- [ ] No disconnections during 5-minute test
- [ ] Status updates appear every 10 seconds
- [ ] Battery level remains consistent (±2%)
- [ ] Last roll updates correctly after each roll
- [ ] Rolling state accurate

**Status**: ☐ PASS ☐ FAIL  
**Test Duration**: _____ minutes  
**Disconnections**: _____  
**Notes**: _______________________________________________

---

## TEST 13: Reconnection Test
**Objective**: Verify automatic reconnection works

### Steps:
1. With die connected, power off die (hold, place on charger, remove)
2. Observe disconnect event
3. Power on die again
4. Observe reconnection

### Expected Serial Output:
```
========================================
✗ DICE DISCONNECTED - Slot 0
========================================

Attempting reconnect to slot 0

========================================
✓ DICE CONNECTED - Slot 0
========================================
```

### Pass Criteria:
- [ ] Disconnect detected within 10 seconds
- [ ] Reconnect attempt starts automatically
- [ ] Successful reconnection within 15 seconds
- [ ] All features work after reconnection
- [ ] Can reconnect multiple times

**Status**: ☐ PASS ☐ FAIL  
**Reconnection Time**: _____ seconds  
**Attempts**: _____  
**Notes**: _______________________________________________

---

## TEST 14: Multiple Dice Test (Optional)
**Objective**: Verify 2 dice can connect simultaneously

### Prerequisites:
- [ ] Have 2 GoDice available
- [ ] Both dice charged (>20%)

### Steps:
1. Power on both dice
2. Reset ESP32 to start fresh scan
3. Observe both connections

### Expected Serial Output:
```
✓ DICE CONNECTED - Slot 0
  Address: XX:XX:XX:XX:XX:XX

✓ DICE CONNECTED - Slot 1
  Address: YY:YY:YY:YY:YY:YY

┌─────────────────────────────────────┐
│  Connected Dice: 2 / 2              │
│  Slot 0:                            │
│    Battery: XX%                     │
│  Slot 1:                            │
│    Battery: YY%                     │
└─────────────────────────────────────┘
```

### Pass Criteria:
- [ ] Both dice connect successfully
- [ ] Different slot numbers assigned (0 and 1)
- [ ] Roll detection works for both
- [ ] Color detection works for both
- [ ] Battery reported for both
- [ ] LED control works independently

**Status**: ☐ PASS ☐ FAIL ☐ NOT TESTED  
**Notes**: _______________________________________________

---

## TEST 15: Serial Command Interface Test
**Objective**: Verify all serial commands work

### Commands to Test:
Type each command in Serial Monitor and observe results

| Command | Function | Expected Result | Status |
|---------|----------|-----------------|--------|
| `s` | Start scan | "Starting scan..." message | ☐ PASS ☐ FAIL |
| `b` | Request battery | Battery event for all connected dice | ☐ PASS ☐ FAIL |
| `c` | Request color | Color event for all connected dice | ☐ PASS ☐ FAIL |
| `l` | LEDs on (red) | Red LEDs on all dice | ☐ PASS ☐ FAIL |
| `o` | LEDs off | LEDs turn off | ☐ PASS ☐ FAIL |
| `d` | Disconnect all | All dice disconnect | ☐ PASS ☐ FAIL |
| `h` | Show help | Help menu displays | ☐ PASS ☐ FAIL |

### Pass Criteria:
- [ ] All commands execute correctly
- [ ] Commands respond within 1 second
- [ ] Help displays complete menu
- [ ] No errors or crashes

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 16: Memory & Performance Test
**Objective**: Verify memory usage is acceptable

### Steps:
1. Check compilation output for memory usage
2. Monitor for memory leaks during extended test
3. Check for stack overflow or heap issues

### Expected Values:
```
Sketch uses ~350KB (23%) of program storage
Global variables use ~85KB (16%) of dynamic memory
```

### Pass Criteria:
- [ ] Sketch size < 1MB
- [ ] Global variables < 200KB
- [ ] No memory warnings during compilation
- [ ] No crashes during extended testing (30+ minutes)
- [ ] Free heap remains stable

**Status**: ☐ PASS ☐ FAIL  
**Sketch Size**: _____ KB  
**Global Variables**: _____ KB  
**Notes**: _______________________________________________

---

## TEST 17: Error Handling Test
**Objective**: Verify graceful error handling

### Test Scenarios:

#### A. Die Out of Range
- [ ] Move die >10 meters away
- [ ] Connection drops gracefully
- [ ] Reconnection works when back in range

#### B. Low Battery Die
- [ ] Use die with <10% battery
- [ ] Low battery warning appears
- [ ] Die still functions (may be unreliable)

#### C. Die Goes to Sleep
- [ ] Leave die idle for 5 minutes
- [ ] Die auto-sleeps
- [ ] Roll to wake triggers reconnection

#### D. Multiple Rapid Rolls
- [ ] Roll die 10 times rapidly
- [ ] All rolls detected
- [ ] No buffer overflow or missed events

### Pass Criteria:
- [ ] All error conditions handled gracefully
- [ ] No crashes or reboots
- [ ] Appropriate warnings displayed
- [ ] Recovery works automatically

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## TEST 18: Integration Readiness Test
**Objective**: Verify library is ready for game integration

### Code Integration Checks:
- [ ] Event callbacks are easy to implement
- [ ] API is clear and well-documented
- [ ] Can access dice info (battery, color, roll)
- [ ] LED control is responsive
- [ ] Can distinguish between multiple dice

### Sample Integration Code Test:
Add this to test sketch and verify it compiles:
```cpp
void processGameRoll(int diceSlot, uint8_t value) {
    GoDiceInfo* info = goDiceClient.getDiceInfo(diceSlot);
    
    // Check battery before processing
    if (info->batteryLevel < 20) {
        Serial.println("Warning: Low battery!");
    }
    
    // Process the roll
    int newPosition = calculateMove(value);
    
    // Light up die based on result
    if (newPosition >= 20) {
        goDiceClient.blinkLEDs(diceSlot, 5, 30, 30, 0, 255, 0);  // Winner!
    }
}
```

### Pass Criteria:
- [ ] Code compiles with integration example
- [ ] API is intuitive and easy to use
- [ ] Documentation is clear
- [ ] Ready for full game implementation

**Status**: ☐ PASS ☐ FAIL  
**Notes**: _______________________________________________

---

## Overall Test Summary

### Test Results
Total Tests: 18  
Passed: _____  
Failed: _____  
Not Tested: _____  

### Critical Issues Found
1. _______________________________________________
2. _______________________________________________
3. _______________________________________________

### Minor Issues Found
1. _______________________________________________
2. _______________________________________________
3. _______________________________________________

### Performance Notes
- Connection Time: _____ seconds average
- Roll Detection Latency: _____ ms estimate
- Battery Reporting: _____ seconds after connection
- Reconnection Time: _____ seconds

### Library Status
☐ **PRODUCTION READY** - All tests passed, no critical issues  
☐ **NEEDS MINOR FIXES** - All tests passed, minor issues found  
☐ **NEEDS MAJOR FIXES** - Some tests failed, requires debugging  
☐ **NOT READY** - Critical tests failed

### Tester Information
- **Tester Name**: _______________________________________________
- **Test Date**: _______________________________________________
- **Hardware Used**: _______________________________________________
- **GoDice Model**: _______________________________________________
- **GoDice Firmware**: _______________________________________________

### Sign-Off
☐ I confirm all required tests have been completed  
☐ I have documented all issues found  
☐ I recommend proceeding to integration phase

**Signature**: _______________________________________________

---

## Troubleshooting Guide

### Issue: Die Not Found During Scan
**Possible Causes**:
- Die is off or sleeping
- Die battery is dead
- Die is out of range
- BLE interference

**Solutions**:
1. Roll die vigorously to wake from sleep
2. Charge die fully
3. Move ESP32 closer to die (<2 meters)
4. Turn off WiFi on nearby devices
5. Check Serial Monitor for BLE errors

### Issue: Connection Drops Frequently
**Possible Causes**:
- Low battery (<15%)
- Signal interference
- Die going to sleep
- Multiple devices trying to connect

**Solutions**:
1. Charge die to >50%
2. Move closer to ESP32
3. Roll die occasionally to keep awake
4. Disconnect die from other apps (phone, tablet)

### Issue: Wrong Roll Values Detected
**Possible Causes**:
- Die type mismatch (using D20 but code expects D6)
- Detection settings too sensitive/insensitive
- Die not settling properly

**Solutions**:
1. Verify die type in code (change dice_max parameter)
2. Let die settle completely before reading
3. Roll on flat, hard surface
4. Adjust detection settings if needed

### Issue: LEDs Don't Respond
**Possible Causes**:
- Low battery (<10%)
- Command timing issue
- Die firmware issue

**Solutions**:
1. Charge die fully
2. Add delays between LED commands
3. Reset die (place on charger, remove)
4. Check for command errors in Serial Monitor

### Issue: Compilation Errors
**Common Errors**:
- `BLEDevice.h: No such file or directory`
  - **Fix**: Install ESP32 board support in Arduino IDE
  
- `undefined reference to BLEDevice::init`
  - **Fix**: Update ESP32 board support to 2.0.11+
  
- `Multiple libraries found for BLE`
  - **Fix**: Remove duplicate BLE libraries

### Issue: Upload Fails
**Possible Causes**:
- Wrong COM port selected
- ESP32 not in boot mode
- Driver issues

**Solutions**:
1. Press and hold BOOT button, press RST, release BOOT
2. Check correct COM port in Tools menu
3. Install CH340 or CP2102 drivers if needed
4. Try lower upload speed (115200)

---

## Next Steps After Testing

### If All Tests Pass:
1. ✅ Integrate with TFT display code
2. ✅ Connect to existing LED board code
3. ✅ Add Hall sensor detection
4. ✅ Implement full game logic
5. ✅ Build complete standalone system

### If Tests Fail:
1. Document all failures in detail
2. Check troubleshooting guide
3. Review code for issues
4. Test with different hardware if available
5. Report issues with full Serial Monitor logs

### Integration Checklist:
- [ ] Library tested and verified
- [ ] Display code ready
- [ ] LED board code ready
- [ ] Hall sensors tested
- [ ] Game logic implemented
- [ ] Ready for full system integration

---

## Test Data Collection Template

Copy this section for each test session:

```
SESSION ID: TEST-GODICE-[DATE]-[TIME]
HARDWARE: ESP32-S3 / ESP32
GODICE MODEL: D6 Standard
SHELL COLOR: [Red/Green/Blue/Black/Yellow/Orange]
BATTERY: ____%
FIRMWARE: v_____

RESULTS:
TEST 1: ☐ PASS ☐ FAIL - Notes: ________________
TEST 2: ☐ PASS ☐ FAIL - Notes: ________________
[...continue for all tests...]

TOTAL PASSED: _____/18
RECOMMENDATION: ☐ PROCEED ☐ FIX ISSUES ☐ NEEDS REVIEW
```

---

**END OF TEST PLAN**
