# Multi-Board Pairing Guide

## ✅ IMPLEMENTATION COMPLETE

**Status**: Multi-board support fully implemented  
**Date**: December 4, 2025  
**Build**: ✅ Successful (Android APK compiled)  
**Line Count**: ~260 lines added (2 new files), ~95 lines removed from MainActivity.kt  

### New Files Created

1. **BoardScanManager.kt** (~180 lines) - Handles board scanning and discovery
2. **BoardSelectionDialog.kt** (~80 lines) - UI for board selection

### Key Features Implemented

✅ Scans for all `LASTDROP-*` boards (prefix matching)  
✅ Auto-connects to single board (no dialog)  
✅ Shows selection dialog for multiple boards  
✅ Displays board ID + MAC address  
✅ Rescan functionality  
✅ MAC address whitelist validation  
✅ 10-second scan timeout  

---

## Problem: Multiple Boards in Same Location

**Scenario**: Tournament with 5 physical boards and 10 players

- **Board 1**: `LASTDROP-0001` (Password: `111111`)
- **Board 2**: `LASTDROP-0002` (Password: `222222`)
- **Board 3**: `LASTDROP-0003` (Password: `333333`)
- **Board 4**: `LASTDROP-0004` (Password: `444444`)
- **Board 5**: `LASTDROP-0005` (Password: `555555`)

**Challenge**: How does Player A's phone connect to Board 3 specifically, not Board 1 or 2?

---

## Current Implementation ✅

### ESP32 Side (ALREADY IMPLEMENTED)

Each board advertises **unique BLE device name**:

```cpp
// Board 1 firmware
#define BOARD_UNIQUE_ID "LASTDROP-0001"
#define BOARD_PASSWORD "111111"

// Board 2 firmware
#define BOARD_UNIQUE_ID "LASTDROP-0002"  
#define BOARD_PASSWORD "222222"

// Board 3 firmware
#define BOARD_UNIQUE_ID "LASTDROP-0003"
#define BOARD_PASSWORD "333333"
```

**BLE Advertisement**:
```cpp
void initBLE() {
  BLEDevice::init(BOARD_UNIQUE_ID);  // ✅ Each board has unique name
  // Board 1 advertises as "LASTDROP-0001"
  // Board 2 advertises as "LASTDROP-0002"
  // etc.
}
```

### Android Side (NEEDS UPDATE)

**Current Problem**:
```kotlin
const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"  // ❌ Hardcoded generic name

val scanFilter = ScanFilter.Builder()
    .setDeviceName(ESP32_DEVICE_NAME)  // ❌ Will NOT find "LASTDROP-0001"
    .build()
```

Android scans for `"LASTDROP-ESP32"` but boards advertise as `"LASTDROP-0001"`, `"LASTDROP-0002"`, etc.

**Result**: Android finds **zero boards** ❌

---

## Solution: Board Selection UI

### Option 1: Scan for All LASTDROP Boards (RECOMMENDED)

**Android Changes Needed**:

1. **Remove device name filter, use prefix matching**:
```kotlin
// MainActivity.kt companion object
const val ESP32_DEVICE_PREFIX = "LASTDROP-"  // Match any LASTDROP-xxxx

// Scan callback
val scanFilter = ScanFilter.Builder()
    // .setDeviceName(ESP32_DEVICE_NAME)  // ❌ Remove this
    .build()  // ✅ Scan for all BLE devices

esp32ScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        result?.device?.let { device ->
            val deviceName = device.name ?: return
            
            // ✅ Filter by prefix
            if (deviceName.startsWith(ESP32_DEVICE_PREFIX)) {
                // Found a LASTDROP board!
                discoveredBoards.add(device)
            }
        }
    }
}
```

2. **Show board selection dialog**:
```kotlin
// Store discovered boards
private val discoveredBoards = mutableListOf<BluetoothDevice>()

private fun showBoardSelectionDialog() {
    val boardNames = discoveredBoards.map { it.name }.toTypedArray()
    
    AlertDialog.Builder(this)
        .setTitle("Select Board")
        .setItems(boardNames) { _, which ->
            val selectedBoard = discoveredBoards[which]
            connectToESP32Device(selectedBoard)
        }
        .setNegativeButton("Rescan") { _, _ ->
            discoveredBoards.clear()
            connectESP32()  // Scan again
        }
        .show()
}
```

**User Experience**:
1. Player taps "Connect to Board"
2. App scans for 5 seconds
3. Dialog shows:
   ```
   Select Board
   ○ LASTDROP-0001
   ○ LASTDROP-0002
   ○ LASTDROP-0003
   ○ LASTDROP-0004
   ○ LASTDROP-0005
   
   [Rescan]  [Cancel]
   ```
4. Player selects `LASTDROP-0003`
5. App prompts for password: `333333`
6. Connected to correct board ✅

### Option 2: QR Code Pairing (ADVANCED)

**Physical Setup**:
- Print QR code sticker for each board
- Stick on physical board case

**QR Code Contains**:
```json
{
  "boardId": "LASTDROP-0003",
  "password": "333333",
  "macAddress": "AA:BB:CC:DD:EE:03"
}
```

**Android Implementation**:
1. Add QR scanner library (ZXing)
2. Scan QR code → auto-connect to exact board
3. No manual password entry needed

**Advantage**: Fastest pairing (1 second scan vs 20 second manual)

### Option 3: NFC Tap Pairing (PREMIUM)

**Hardware**: Add NFC tag to each board ($0.50 per tag)

**Android**:
1. Player holds phone near board
2. NFC tag contains board ID + password
3. Auto-connect instantly

**User Experience**: Like tapping credit card on payment terminal

---

## Production Deployment Checklist

### Manufacturing Phase

For each board produced:

1. **Flash unique firmware**:
```cpp
// Board 1
#define BOARD_UNIQUE_ID "LASTDROP-0001"
#define BOARD_PASSWORD "111111"

// Board 2
#define BOARD_UNIQUE_ID "LASTDROP-0002"
#define BOARD_PASSWORD "222222"

// etc.
```

2. **Physical labeling**:
   - Print sticker: **"Board #0001"** (large font)
   - Include password: **"Password: 111111"** (small font, bottom)
   - Stick on board case (visible when lid closed)

3. **Documentation**:
   - Create spreadsheet:
   ```
   Board ID       | MAC Address        | Password | Assigned To
   LASTDROP-0001  | AA:BB:CC:DD:EE:01 | 111111   | Game Room A
   LASTDROP-0002  | AA:BB:CC:DD:EE:02 | 222222   | Game Room B
   LASTDROP-0003  | AA:BB:CC:DD:EE:03 | 333333   | Tournament Hall
   ```

4. **Optional: QR codes**:
   - Generate QR for each board (https://www.qr-code-generator.com/)
   - Print and laminate
   - Stick on board case

### Android App Updates

**Priority 1: Board Selection Dialog** (Essential)
- Remove hardcoded device name filter
- Scan for all `LASTDROP-*` devices
- Show selection dialog
- ~50 lines of code

**Priority 2: Saved Boards** (Quality of Life)
- Remember last connected board
- Auto-reconnect to saved board
- "Forget this board" option
- ~100 lines of code

**Priority 3: QR Code Scanner** (Advanced)
- Add ZXing library
- Camera permission
- Parse QR data → auto-connect
- ~150 lines of code

---

## User Workflow Examples

### Scenario 1: Home User (1 board)
**Current Experience**: ✅ Works perfectly
1. Tap "Connect"
2. App finds `LASTDROP-0001` automatically
3. Enter password once
4. Play game

**No changes needed** for single-board users.

### Scenario 2: Tournament (5 boards)
**Current Experience**: ❌ Broken (finds wrong board or none)

**After Fix**:
1. Organizer assigns: "Table 3 uses Board LASTDROP-0003"
2. Player taps "Connect"
3. **Dialog shows all 5 boards**
4. Player selects `LASTDROP-0003`
5. Enters password `333333`
6. Connected ✅

### Scenario 3: Cafe with Multiple Games (10+ boards)
**With QR Codes**:
1. Each table has laminated card: "Scan to Connect"
2. Player opens app → "Scan QR Code"
3. Camera scans QR
4. Auto-connects to correct board (no password entry)
5. Game ready in 3 seconds ✅

---

## Security Considerations

### MAC Address Whitelisting (Optional)

Restrict which phones can connect to a specific board:

**ESP32 Firmware**:
```cpp
#define MAC_FILTERING_ENABLED true

// Only these phones can connect to this board
const String TRUSTED_ANDROID_MACS[] = {
  "AA:11:22:33:44:55",  // Tournament organizer phone
  "BB:66:77:88:99:00"   // Backup referee phone
};
```

**Use Case**: Prevent players from connecting during active tournament game.

### Password Rotation

**Monthly Password Change**:
```cpp
// January
#define BOARD_PASSWORD "654321"

// February
#define BOARD_PASSWORD "789123"
```

Upload new firmware each month → old passwords stop working.

---

## Implementation Priority

### Phase 1: Basic Multi-Board Support (CRITICAL) 🔥
**Time**: 1 hour  
**Lines**: ~50  
**Impact**: Tournament-ready

Changes:
- Remove hardcoded device name filter
- Add prefix matching (`LASTDROP-*`)
- Show board selection dialog
- Test with 2 boards in same room

### Phase 2: Saved Boards (Quality of Life)
**Time**: 2 hours  
**Lines**: ~100  
**Impact**: Better UX for repeat players

Changes:
- Save last connected board ID to SharedPreferences
- Auto-connect to saved board if found
- "Connect to different board" button
- "Forget this board" option

### Phase 3: QR Code Scanner (Advanced)
**Time**: 4 hours  
**Lines**: ~150  
**Impact**: Commercial deployment

Changes:
- Add ZXing dependency
- Camera permission flow
- QR scanner UI
- Parse JSON from QR → auto-connect
- Generate QR codes for all boards

---

## Testing Procedure

### Test 1: Single Board (Baseline)
1. Flash Board 1 with `BOARD_UNIQUE_ID = "LASTDROP-0001"`
2. Android scans and finds `LASTDROP-0001` ✅
3. Connect → enter password → game works ✅

### Test 2: Multiple Boards (Critical)
1. Flash Board 1: `LASTDROP-0001`, password `111111`
2. Flash Board 2: `LASTDROP-0002`, password `222222`
3. Power both boards on (same room)
4. Android scans → dialog shows BOTH boards ✅
5. Select Board 2 → enter `222222` → connects to Board 2 only ✅
6. Verify Board 1 still advertising (not affected) ✅

### Test 3: Interference Test
1. 5 boards powered on simultaneously
2. 10 phones scanning at once
3. Each phone connects to different board
4. Verify no cross-connections ✅
5. All 5 games running independently ✅

---

## Cost Analysis

### Option 1: Manual Selection (FREE)
- **Hardware**: $0 (no changes)
- **Development**: 1 hour
- **User Time**: 15 seconds to select board

### Option 2: QR Codes ($2 per board)
- **Hardware**: $2 (printed QR sticker)
- **Development**: 4 hours
- **User Time**: 3 seconds to scan

### Option 3: NFC Tags ($5 per board)
- **Hardware**: $5 (NFC tag + integration)
- **Development**: 8 hours
- **User Time**: 1 second to tap

**Recommendation**: Start with Option 1 (manual selection), add QR codes later if demand is high.

---

## Conclusion

**Current Status**: 
- ✅ ESP32 firmware ready (unique board IDs)
- ❌ Android app hardcoded for single generic name

**Next Steps**:
1. Update Android to scan for `LASTDROP-*` prefix
2. Add board selection dialog
3. Test with 2 physical boards
4. Deploy to production

**Timeline**: 1 hour for basic multi-board support (Phase 1)

This enables tournaments, cafes, and multi-table deployments. 🎯
