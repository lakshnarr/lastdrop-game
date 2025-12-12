# PIN Authentication System - Quick Reference

**Implementation Date**: December 12, 2025  
**Status**: ✅ Implemented, Compiled, Ready for Testing

---

## 🎯 Overview

The Last Drop app now uses a **hybrid security approach** that combines:
- ✅ BLE connection without pairing (simple, reliable)
- ✅ App-level PIN validation (secure, user-friendly)
- ✅ PIN memory system (convenient for repeated connections)

---

## 📱 User Experience

### First-Time Connection:
1. Tap "🎮 Connect ESP32 Board"
2. App scans for 10 seconds
3. **Multiple boards**: Selection dialog appears
4. **Single board**: Auto-selected
5. **PIN entry dialog** appears:
   ```
   🔐 Board Authentication
   
   Enter PIN for:
   LASTDROP-0001
   88:57:21:2d:54:06
   
   [Enter 6-digit PIN: ______]
   
   ☑ Remember PIN for this board
   
   💡 Default PIN: 654321
   (configured in ESP32 firmware)
   
   [Connect] [Cancel]
   ```
6. Enter PIN (default: `654321`)
7. Check "Remember PIN" for auto-fill next time
8. Tap "Connect"
9. **Success**: Green flash on board, "✅ Connected" toast
10. **Failure**: Red flash on board, retry dialog

### Subsequent Connections:
1. Tap "🎮 Connect ESP32 Board"
2. Select same board
3. PIN dialog auto-fills saved PIN
4. Tap "Connect" (or just press Enter)
5. Instant connection ✅

---

## 🔧 Technical Implementation

### Architecture Components:

#### 1. **PinEntryDialog.kt** (New File)
- Material Design dialog with PIN input
- Auto-fill from saved PINs
- "Remember PIN" checkbox
- Validation result dialogs
- Methods:
  - `show()` - Main PIN entry
  - `showValidationResult()` - Success/failure feedback
  - `showPinChangeDialog()` - Change board PIN (future)

#### 2. **BoardPreferencesManager.kt** (Modified)
- Added PIN storage methods:
  - `saveBoardPin(boardId, pin)` - Store PIN for board
  - `getSavedPin(boardId)` - Retrieve saved PIN
  - `clearBoardPin(boardId)` - Remove saved PIN
- PINs stored in SharedPreferences with key: `board_pin_LASTDROP-0001`
- Separate from password hash (convenience vs security)

#### 3. **MainActivity.kt** (Modified)
- **New Variables:**
  - `pendingPinDevice: BluetoothDevice?` - Device awaiting validation
  - `esp32Paired: Boolean` - True when PIN validated

- **New Functions:**
  - `showPinEntryDialog(device)` - Display PIN entry UI
  - `validatePinAndConnect(device, pin, remember)` - Validate PIN with ESP32

- **Modified Functions:**
  - `showBoardSelectionDialog()` - Calls `showPinEntryDialog()` after selection
  - `handleESP32Event()` - Added `pair_success` and `pair_failed` handlers
  - `disconnectESP32()` - Resets `esp32Paired` flag

---

## 🔄 Connection Flow Diagram

```
┌─────────────────────────────────────────┐
│ User taps "Connect ESP32 Board"        │
└────────────┬────────────────────────────┘
             │
             v
┌─────────────────────────────────────────┐
│ BoardScanManager scans 10 seconds      │
│ Finds all LASTDROP-* boards            │
└────────────┬────────────────────────────┘
             │
             v
      ┌──────┴──────┐
      │ 1 board?    │
      └──────┬──────┘
         Yes│  │No
            │  │
            │  v
            │ ┌────────────────────────┐
            │ │ BoardSelectionDialog   │
            │ │ (shows all boards)     │
            │ └────────┬───────────────┘
            │          │
            └──────────┘
                       v
            ┌────────────────────────┐
            │ showPinEntryDialog()   │
            │ - Display board info   │
            │ - Auto-fill saved PIN  │
            │ - Remember checkbox    │
            └────────┬───────────────┘
                     │
                     v
            ┌────────────────────────┐
            │ User enters PIN        │
            │ & taps Connect         │
            └────────┬───────────────┘
                     │
                     v
            ┌────────────────────────┐
            │ connectToESP32Device() │
            │ (BLE GATT connection)  │
            └────────┬───────────────┘
                     │
                     v
            ┌────────────────────────┐
            │ Send pair command:     │
            │ {"command":"pair",     │
            │  "password":"654321"}  │
            └────────┬───────────────┘
                     │
                     v
       ┌─────────────┴──────────────┐
       │ ESP32 validates password   │
       └─────────────┬──────────────┘
                     │
           ┌─────────┴─────────┐
           │ PIN correct?      │
           └─────────┬─────────┘
               Yes   │   No
                     │
        ┌────────────┼────────────┐
        v            v            v
 ┌──────────┐ ┌──────────┐ ┌──────────┐
 │pair_     │ │pair_     │ │Connection│
 │success   │ │failed    │ │timeout   │
 └────┬─────┘ └────┬─────┘ └────┬─────┘
      │            │            │
      v            v            v
┌──────────┐ ┌──────────┐ ┌──────────┐
│Green     │ │Red       │ │Disconnect│
│LED flash │ │LED flash │ │& retry   │
└────┬─────┘ └────┬─────┘ └────┬─────┘
     │            │            │
     v            v            v
┌──────────┐ ┌──────────┐ ┌──────────┐
│Connected │ │Retry     │ │Error     │
│✅        │ │dialog    │ │dialog    │
└──────────┘ └──────────┘ └──────────┘
```

---

## 📡 BLE Protocol

### Android → ESP32 Commands:

**Pair Command:**
```json
{
  "command": "pair",
  "password": "654321"
}
```

### ESP32 → Android Events:

**Success:**
```json
{
  "event": "pair_success",
  "message": "Paired successfully",
  "boardId": "LASTDROP-0001",
  "version": "v2.0",
  "pairingRequired": true
}
```

**Failure:**
```json
{
  "event": "pair_failed",
  "message": "Incorrect password",
  "boardId": "LASTDROP-0001",
  "version": "v2.0",
  "pairingRequired": true
}
```

---

## 🔐 Security Model

### What's Protected:
- ✅ **Command Execution**: ESP32 rejects all commands until PIN validated
- ✅ **Multi-Board**: Each board can have different PIN
- ✅ **App-Level**: No BLE pairing complexity
- ✅ **User Control**: Explicit PIN entry per connection

### What's NOT Protected:
- ❌ **BLE Scanning**: Any device can see board advertising
- ❌ **GATT Connection**: Any device can connect to GATT (but commands rejected)
- ❌ **PIN Storage**: Saved PINs stored in plaintext for convenience

### Optional Additional Security:
1. **MAC Address Whitelist** (ESP32 firmware):
   - Enable `MAC_FILTERING_ENABLED true`
   - Add trusted MAC addresses to `TRUSTED_ANDROID_MACS[]`
   
2. **BLE Bonding** (Complex, not recommended):
   - Requires post-GATT pairing implementation
   - Android compatibility issues

---

## ⚙️ Configuration

### ESP32 Firmware Settings:

**File**: `ESP32 Program/sketch_ble.ino`

```cpp
// Line 56-60: Password Configuration
#define BOARD_PASSWORD "654321"        // Change this for each board
#define PAIRING_REQUIRED true          // Keep enabled for PIN protection
#define BLE_PAIRING_ENABLED false      // Keep disabled (app-level PIN only)
#define MAC_FILTERING_ENABLED false    // Optional: Enable MAC whitelist
```

**To Change Board PIN:**
1. Edit `BOARD_PASSWORD "654321"` to your desired PIN
2. Upload firmware to ESP32
3. Android app will prompt for new PIN on next connection

### Android App Settings:

**File**: `app/src/main/java/earth/lastdrop/app/MainActivity.kt`

```kotlin
// Line 84: Pairing behavior
private const val SKIP_PAIRING = true  // Keep true for app-level PIN
```

**File**: `app/src/main/java/earth/lastdrop/app/BoardPreferencesManager.kt`

```kotlin
// Line 22: PIN storage key prefix
private const val KEY_PIN_PREFIX = "board_pin_"
```

---

## 🧪 Testing Procedures

### Test 1: First Connection
1. Fresh install app (no saved PINs)
2. Tap "Connect ESP32 Board"
3. Select board from list
4. Verify PIN dialog shows:
   - Board ID
   - MAC address
   - Empty PIN field
   - Unchecked "Remember" box
   - Default PIN hint
5. Enter wrong PIN (e.g., `111111`)
6. Verify red LED flash + retry dialog
7. Enter correct PIN (`654321`)
8. Verify green LED flash + connection success

### Test 2: Saved PIN
1. Connect with "Remember PIN" checked
2. Disconnect
3. Reconnect to same board
4. Verify PIN dialog auto-fills correct PIN
5. Verify "Remember" box is checked
6. Press Enter or tap Connect
7. Verify instant connection

### Test 3: Multiple Boards
1. Power on 2+ boards
2. Tap "Connect ESP32 Board"
3. Verify all boards appear in selection dialog
4. Select LASTDROP-0001, enter its PIN
5. Disconnect
6. Reconnect, select LASTDROP-0002, enter its PIN
7. Verify each board's PIN is remembered separately

### Test 4: PIN Clear
1. Connect to board with saved PIN
2. Enter wrong PIN
3. Verify saved PIN is cleared after failure
4. Reconnect
5. Verify PIN field is empty (not auto-filled)

### Test 5: Cancel Flow
1. Tap "Connect ESP32 Board"
2. Select board
3. Tap "Cancel" in PIN dialog
4. Verify:
   - No connection attempt
   - "Connect ESP32 Board" button re-enabled
   - No error messages

---

## 🐛 Debugging

### Issue: PIN Dialog Doesn't Appear
**Check:**
- Board selection working? (log: "✅ Selected: LASTDROP-0001")
- `showPinEntryDialog()` called in `onBoardSelected` callback
- Dialog not blocked by other UI elements

**Fix**: Check `MainActivity.kt` line ~3048, verify callback chain

### Issue: PIN Always Fails
**Check:**
- ESP32 Serial Monitor shows: "🔐 Processing PAIR command..."
- ESP32 password matches: `boardPassword = "654321"`
- No typos in PIN entry (numeric keyboard only)

**Fix**: Re-upload ESP32 firmware with correct `BOARD_PASSWORD`

### Issue: No Response from ESP32
**Check:**
- GATT connection established? (`esp32Connected = true`)
- BLE TX characteristic writable? (Nordic UART Service)
- ESP32 Serial Monitor shows: "✓ BLE Client Connected"

**Fix**: Verify ESP32 BLE service UUID matches Android app

### Issue: Saved PIN Not Auto-Filling
**Check:**
- PIN saved? (`boardPreferencesManager.getSavedPin(boardId)`)
- Board ID matches exactly? (case-sensitive)
- SharedPreferences not cleared?

**Fix**: Check Android logcat for "Saved PIN for board: LASTDROP-0001"

---

## 📚 Related Documentation

- **BLE_CONNECTION_TODO.md** - Implementation history and decisions
- **SECURITY.md** - Complete security architecture (to be updated)
- **ANDROID_BLE_INTEGRATION.md** - BLE protocol specification
- **BOARD_SETTINGS_GUIDE.md** - ESP32 configuration options
- **IMPLEMENTATION_GUIDE.md** - Hardware setup procedures

---

## 🚀 Future Enhancements

### Phase 2 (Optional):
- [ ] QR Code Pairing (scan board QR to auto-connect)
- [ ] Biometric Unlock (fingerprint for saved boards)
- [ ] PIN Strength Meter (visual feedback)
- [ ] Board Groups (family/friends/events)
- [ ] Remote PIN Reset (via admin command)

### Phase 3 (Advanced):
- [ ] Cloud Board Registry (discover boards by serial number)
- [ ] Encrypted PIN Storage (AndroidKeyStore)
- [ ] Multi-Factor Auth (PIN + biometric)
- [ ] Audit Log (connection history)

---

**Last Updated**: December 12, 2025  
**Next Step**: Hardware testing with physical ESP32 board
