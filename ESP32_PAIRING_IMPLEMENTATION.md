# ESP32 PIN Pairing Implementation

## Overview
Manual PIN pairing has been fully implemented for secure ESP32 board connections. The app now intercepts pairing requests and shows a custom dialog for users to enter the board's PIN code.

## Implementation Details

### 1. Pairing Infrastructure (Lines 177-223)
**File**: `MainActivity.kt`

```kotlin
// Track device being paired
private var pairingDevice: BluetoothDevice? = null

// BroadcastReceiver for pairing events
private val pairingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                // Intercept pairing request and show custom PIN dialog
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device?.address == pairingDevice?.address) {
                    showPinEntryDialog(device)
                    abortBroadcast()  // Prevent system dialog
                }
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                // Handle bonding state changes
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                
                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        // Pairing successful, proceed with connection
                        if (device?.address == pairingDevice?.address) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Board paired successfully!", Toast.LENGTH_SHORT).show()
                            }
                            proceedWithESP32Connection(device)
                            pairingDevice = null
                        }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        // Pairing failed
                        if (device?.address == pairingDevice?.address) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Pairing failed", Toast.LENGTH_SHORT).show()
                            }
                            pairingDevice = null
                        }
                    }
                }
            }
        }
    }
}
```

**Registered in onCreate()** (Lines 357-364):
```kotlin
val pairingFilter = IntentFilter().apply {
    addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
}
registerReceiver(pairingReceiver, pairingFilter)
```

### 2. PIN Entry Dialog (Lines 225-279)
Custom dialog with pre-filled default PIN (654321):

```kotlin
@SuppressLint("MissingPermission")
private fun showPinEntryDialog(device: BluetoothDevice) {
    val input = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        hint = "Enter 6-digit PIN"
        setText("654321")  // Pre-fill with default PIN
        selectAll()  // Select all text for easy override
    }
    
    AlertDialog.Builder(this)
        .setTitle("Pair with ${device.name ?: device.address}")
        .setMessage("Enter the PIN code for this board\n(Default: 654321)")
        .setView(input)
        .setPositiveButton("Pair") { _, _ ->
            val pin = input.text.toString()
            if (pin.length == 6 && pin.all { it.isDigit() }) {
                try {
                    val success = device.setPin(pin.toByteArray())
                    Log.d(TAG, "PIN set result: $success")
                    // Toast feedback and error handling
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting PIN", e)
                }
            } else {
                Toast.makeText(this, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton("Cancel") { _, _ ->
            pairingDevice = null
            Toast.makeText(this, "Pairing cancelled", Toast.LENGTH_SHORT).show()
        }
        .setCancelable(false)  // Force user to choose
        .show()
}
```

**Features**:
- Pre-fills with default PIN (654321)
- Validates PIN format (6 digits, numeric only)
- Shows device name in title
- Non-dismissible (must choose Pair or Cancel)
- Calls `device.setPin()` to authenticate
- Clears `pairingDevice` on cancel

### 3. Modified Connection Flow (Lines 2418-2449)
**File**: `MainActivity.kt`

```kotlin
@SuppressLint("MissingPermission")
private fun connectToESP32Device(device: BluetoothDevice) {
    Log.d(TAG, "Connecting to ESP32: ${device.address}")
    
    // Check bonding state before connecting
    when (device.bondState) {
        BluetoothDevice.BOND_BONDED -> {
            // Already paired, proceed with connection
            Log.d(TAG, "Device already bonded, connecting...")
            proceedWithESP32Connection(device)
        }
        BluetoothDevice.BOND_NONE -> {
            // Not paired, initiate pairing
            Log.d(TAG, "Device not bonded, initiating pairing...")
            pairingDevice = device
            runOnUiThread {
                Toast.makeText(this, "Pairing with ${device.name}...", Toast.LENGTH_SHORT).show()
            }
            device.createBond()  // Triggers ACTION_PAIRING_REQUEST
        }
        BluetoothDevice.BOND_BONDING -> {
            // Pairing in progress, wait
            Log.d(TAG, "Pairing already in progress...")
            runOnUiThread {
                Toast.makeText(this, "Pairing in progress...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun proceedWithESP32Connection(device: BluetoothDevice) {
    // Original connection logic moved here
    // Saves board preferences and initiates GATT connection
}
```

**Key Changes**:
- Checks `device.bondState` before connecting
- If not bonded, calls `device.createBond()` to trigger pairing
- If bonding in progress, shows toast and waits
- If already bonded, skips pairing and connects directly
- Separated connection logic into `proceedWithESP32Connection()`

### 4. Cleanup in onDestroy() (Lines 519-527)
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // ... existing cleanup code ...
    
    // Unregister pairing broadcast receiver
    try {
        unregisterReceiver(pairingReceiver)
    } catch (e: Exception) {
        Log.e(TAG, "Error unregistering pairing receiver", e)
    }
}
```

Prevents memory leaks by unregistering the broadcast receiver when activity is destroyed.

## ESP32 Firmware Configuration

**File**: `sketch_ble.ino` (Lines 49-54)

```cpp
#define BOARD_UNIQUE_ID "LASTDROP-0001"  // Customize per board
#define BOARD_PASSWORD "654321"
#define BLE_PAIRING_ENABLED true
#define BLE_PAIRING_PIN 654321  // Must match Android PIN entry
```

**Important Notes**:
- Default PIN is **654321** (6 digits)
- Can be customized per board by changing `BLE_PAIRING_PIN`
- Android dialog pre-fills with 654321 for convenience
- If different PIN used, user must manually enter it

## User Flow

### First-Time Connection
1. User taps "Connect Board" button
2. App scans for `LASTDROP-*` boards
3. User selects board from list (or auto-connects if only one)
4. App checks bond state → finds `BOND_NONE`
5. App calls `device.createBond()` → triggers pairing request
6. **BroadcastReceiver intercepts** → shows PIN entry dialog
7. User enters PIN (default 654321 pre-filled)
8. User taps "Pair" → app calls `device.setPin()`
9. Bonding completes → `BOND_BONDED` state
10. App automatically connects via GATT
11. Services discovered → game ready

### Subsequent Connections
1. User taps "Connect Board"
2. App selects same board (saved in preferences)
3. App checks bond state → finds `BOND_BONDED`
4. **PIN dialog skipped** → connects directly
5. Services discovered → game ready

## Security Benefits

✅ **User Control**: User explicitly approves pairing
✅ **Custom PIN**: Can set unique PIN per board (in firmware)
✅ **No Auto-Pairing**: System pairing dialog prevented
✅ **Clear Feedback**: Toast messages show pairing status
✅ **PIN Validation**: Only accepts 6-digit numeric PIN

## Testing Checklist

- [ ] Upload `sketch_ble.ino` to ESP32 board
- [ ] Verify board advertises as `LASTDROP-0001`
- [ ] Tap "Connect Board" in Android app
- [ ] Verify PIN dialog appears with "654321" pre-filled
- [ ] Tap "Pair" and verify pairing succeeds
- [ ] Verify "Board paired successfully!" toast
- [ ] Verify GATT connection completes
- [ ] Disconnect and reconnect - verify PIN dialog skipped
- [ ] Test with wrong PIN - verify pairing fails
- [ ] Test "Cancel" button - verify pairing cancelled

## Troubleshooting

### PIN Dialog Not Appearing
- Check `BLE_PAIRING_ENABLED` is `true` in ESP32 firmware
- Verify `pairingReceiver` is registered in `onCreate()`
- Check Logcat for "Device not bonded, initiating pairing..." message
- Ensure app has BLUETOOTH_CONNECT permission

### Pairing Fails
- Verify PIN entered matches ESP32 firmware (654321)
- Check Logcat for "PIN set result: false"
- Try unpairing in Android Settings > Bluetooth > Paired Devices
- Restart ESP32 board and retry

### System Dialog Still Appears
- Verify `abortBroadcast()` is called in `ACTION_PAIRING_REQUEST` handler
- Ensure `pairingDevice` matches the device address
- Check if IntentFilter has correct actions

## Files Modified

1. **MainActivity.kt** (3230 lines):
   - Added `pairingDevice` variable
   - Added `pairingReceiver` BroadcastReceiver (47 lines)
   - Added `showPinEntryDialog()` function (55 lines)
   - Modified `connectToESP32Device()` to check bond state
   - Added `proceedWithESP32Connection()` with original logic
   - Added receiver cleanup in `onDestroy()`

2. **No firmware changes needed** - ESP32 pairing already enabled

## Documentation References

- **SECURITY.md**: API key management, BLE filtering, pairing overview
- **ANDROID_BLE_INTEGRATION.md**: BLE protocol specification
- **IMPLEMENTATION_GUIDE.md**: Hardware setup and testing procedures
- **sketch_ble.ino**: Lines 49-54 (pairing configuration)

## Next Steps

1. **Test with physical ESP32 board**
2. **Document default PIN for manufacturers** (README or board labels)
3. **Consider adding board settings UI** to change PIN after pairing
4. **Test multiple boards with different PINs**
5. **Add pairing status to diagnostics screen**

---

**Status**: ✅ Complete and ready for testing
**Commit Message**: `Implement manual PIN pairing for ESP32 board connections with custom dialog`
