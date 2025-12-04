# ESP32 Board Settings Remote Configuration

## Overview

You can now **change the ESP32 board's pairing password and nickname** directly from the Android app without re-uploading firmware!

## Features

### 1. Change Pairing Password
- Update the 6-digit pairing PIN remotely
- New password is saved to ESP32 NVRAM (persists after restart)
- Requires re-pairing after password change

### 2. Change Board Nickname
- Set a friendly name like "Game Room A", "Tournament Table 3"
- Nickname is displayed in BLE advertising
- Requires board restart for nickname to appear in Bluetooth scan

## Architecture

### ESP32 Side
**Storage**: Uses ESP32 `Preferences` library (NVRAM/flash storage)
- `boardPassword`: Stored in flash, default from `BOARD_PASSWORD` constant
- `boardNickname`: Stored in flash, default from `BOARD_UNIQUE_ID` constant

**Load on Boot**:
```cpp
preferences.begin("lastdrop", false);
boardPassword = preferences.getString("password", BOARD_PASSWORD);
boardNickname = preferences.getString("nickname", BOARD_UNIQUE_ID);
```

**BLE Command**: `update_settings`
```json
{
  "command": "update_settings",
  "password": "123456",      // Optional: new 6+ char password
  "nickname": "Game Room A"  // Optional: new 1-30 char nickname
}
```

**Response Event**: `settings_updated`
```json
{
  "event": "settings_updated",
  "password": "123456",
  "nickname": "Game Room A",
  "restartRequired": true  // true if nickname changed
}
```

**Security**:
- Settings update requires pairing (PAIRING_REQUIRED check)
- Only paired Android devices can change settings
- Password minimum 6 characters
- Nickname 1-30 characters

### Android Side

**Dialog**: `BoardSettingsDialog.kt`
- Input fields for nickname and password
- Validation (6+ chars for password, 1-30 for nickname)
- Shows current values
- Leave empty to keep current value

**Integration**: `MainActivity.kt`
- `showBoardSettings()`: Opens settings dialog
- `sendBoardSettingsUpdate()`: Sends `update_settings` command to ESP32
- `handleESP32Event("settings_updated")`: Processes confirmation
- Auto-saves updated settings to `BoardPreferencesManager`

**Storage**: `BoardPreferencesManager`
- Saves nickname and password hash locally
- Used for QR code generation
- Enables auto-reconnect with saved credentials

## Usage

### Option 1: Via Code (Manual Call)
Currently, you need to manually call the function from your Android app:

```kotlin
// In MainActivity or anywhere with access to showBoardSettings()
showBoardSettings()
```

### Option 2: Add Button to UI (Recommended)
Add this to your `activity_main.xml`:

```xml
<Button
    android:id="@+id/btnBoardSettings"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Board Settings"
    android:enabled="false"
    android:visibility="visible" />
```

Then in `MainActivity.onCreate()`:
```kotlin
findViewById<Button>(R.id.btnBoardSettings).setOnClickListener {
    showBoardSettings()
}

// Enable button when ESP32 connects
esp32Connected = true
findViewById<Button>(R.id.btnBoardSettings).isEnabled = true
```

### Option 3: Long-Press Existing Button
Add long-press handler to ESP32 connect button:

```kotlin
btnConnectESP32.setOnLongClickListener {
    if (esp32Connected) {
        showBoardSettings()
        true
    } else {
        false
    }
}
```

## Step-by-Step Procedure

### Changing Password
1. Connect to ESP32 board from Android app
2. Call `showBoardSettings()` (via button or menu)
3. Enter new password (6+ characters)
4. Tap "Update"
5. Board saves password to flash
6. Next time you connect, use new password

### Changing Nickname
1. Connect to ESP32 board from Android app
2. Call `showBoardSettings()` 
3. Enter new nickname (e.g., "Game Room A")
4. Tap "Update"
5. Board saves nickname to flash
6. **Restart ESP32** (power cycle)
7. Board now advertises with new nickname

## Example Workflow

### Setup Multiple Boards for Tournament

**Board 1:**
```
Current: LASTDROP-0001, Password: 654321
Update to: "Tournament Table 1", Password: 111111
```

**Board 2:**
```
Current: LASTDROP-0002, Password: 654321
Update to: "Tournament Table 2", Password: 222222
```

**Steps:**
1. Upload firmware to Board 1 (default ID: LASTDROP-0001)
2. Connect Android app to Board 1
3. Open Board Settings
4. Set nickname: "Tournament Table 1"
5. Set password: "111111"
6. Restart Board 1
7. Repeat for Board 2 with different nickname/password

**Result:**
- Each board has unique password
- Easy identification by nickname in Bluetooth scan
- QR codes can be regenerated with new settings

## QR Code Integration

After changing settings, regenerate QR code:

```python
# Update in generate_qr_codes.py
{
    "boardId": "LASTDROP-0001",
    "macAddress": "AA:BB:CC:DD:EE:01",
    "password": "111111",  # New password
    "nickname": "Tournament Table 1"  # New nickname
}
```

Or use `BoardSettingsDialog.generateUpdateCommand()` to get JSON for QR.

## Technical Details

### ESP32 NVRAM Storage
- Namespace: `lastdrop`
- Keys: `password`, `nickname`
- Storage type: String (NVS - Non-Volatile Storage)
- Persistence: Survives power cycle, firmware update keeps values

### Password Hashing
**Android Side**: SHA-256 hash stored in `BoardPreferencesManager`
**ESP32 Side**: Plaintext (secure pairing happens over BLE encryption)

### BLE Device Name Update
Nickname change updates BLE advertising name via:
```cpp
BLEDevice::init(boardNickname.c_str());
advData.setName(boardNickname.c_str());
```
**Note**: Requires restart to re-initialize BLE stack.

## Security Considerations

### Current Implementation
- Password length: 6+ characters (configurable)
- Storage: Plaintext in ESP32 flash (encrypted by BLE pairing)
- Transmission: Encrypted over BLE connection
- Access control: Requires pairing before settings update

### Production Recommendations
1. **Increase minimum password length** to 8+ characters
2. **Hash passwords** on ESP32 side (SHA-256)
3. **Add rate limiting** to prevent brute force
4. **Add settings lock** (require old password to change)
5. **Add audit logging** of settings changes

## Troubleshooting

### "Device not paired - pairing required"
**Solution**: Pair with ESP32 first using current password

### "Password too short (min 6 chars)"
**Solution**: Enter at least 6 characters

### Nickname change not showing in Bluetooth scan
**Solution**: Restart ESP32 board (power cycle)

### Settings not persisting after ESP32 restart
**Check**: 
- Serial Monitor shows loaded values on boot
- Preferences namespace correct (`lastdrop`)
- Flash not corrupted (re-upload firmware)

## Code Files

**ESP32 Firmware:**
- `sketch_ble.ino` lines 193-195: Global variables
- `sketch_ble.ino` lines 396-398: Load settings
- `sketch_ble.ino` lines 702-771: `handleUpdateSettings()` function
- `sketch_ble.ino` lines 446-448: BLE name initialization
- `sketch_ble.ino` lines 579-581: Password validation

**Android App:**
- `BoardSettingsDialog.kt` (174 lines): Settings UI
- `MainActivity.kt` lines 2126-2151: Event handler
- `MainActivity.kt` lines 1884-1933: Functions

## Future Enhancements

1. **Settings Menu**: Dedicated activity for all board settings
2. **Batch Update**: Change settings for multiple boards
3. **Password Strength Meter**: Visual feedback for password security
4. **Nickname Templates**: Pre-defined names ("Table 1", "Table 2", etc.)
5. **Settings Backup**: Export/import board configurations
6. **Remote Restart**: Trigger ESP32 restart from Android after nickname change
