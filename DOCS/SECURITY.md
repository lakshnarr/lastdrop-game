# Last Drop - Security Guide

## Overview

This document covers security best practices for the Last Drop hybrid board game system.

## Critical Security Measures

### 1. API Key Management

**Problem**: The API key connects to `lastdrop.earth` for live game display. Hardcoding it in source code exposes it if the code is shared publicly.

**Solution**: API keys are now loaded from `local.properties` (which is gitignored).

#### Setup Instructions

1. **Copy the template**:
   ```powershell
   Copy-Item local.properties.template local.properties
   ```

2. **Edit `local.properties`** and set your API key:
   ```properties
   LASTDROP_API_KEY=your_actual_api_key_here
   ```

3. **Get your API key** from https://lastdrop.earth/dashboard

4. **Verify it's NOT tracked**:
   ```powershell
   git status
   # local.properties should NOT appear in the list
   ```

**How it works**:
- `app/build.gradle.kts` reads `LASTDROP_API_KEY` from `local.properties`
- Creates `BuildConfig.API_KEY` at compile time
- `MainActivity.kt` uses `BuildConfig.API_KEY` instead of hardcoded value
- `.gitignore` prevents `local.properties` from being committed

**For Development**: If `local.properties` doesn't exist or key is missing, defaults to `"ABC123"` (dev/demo key with limited functionality).

**For Production**: Use environment-specific API keys:
- Development: `ABC123_DEV`
- Staging: `ABC123_STAGING`  
- Production: `ABC123_PROD`

---

### 2. Bluetooth Device Security

**Problem**: By default, the Android app will connect to ANY device advertising as:
- `GoDice` (dice)
- `LASTDROP-ESP32` (physical board)

This means someone else's device could interfere with your game.

**Solution**: MAC address whitelisting for trusted devices.

#### ESP32 Board Security

1. **Find your ESP32's MAC address**:
   - Upload `sketch_ble.ino` to your ESP32
   - Open Arduino Serial Monitor
   - Look for line: `ESP32 MAC Address: AA:BB:CC:DD:EE:FF`

2. **Add to MainActivity.kt** (lines 51-56):
   ```kotlin
   private val TRUSTED_ESP32_ADDRESSES = setOf(
       "AA:BB:CC:DD:EE:FF"  // Your ESP32's MAC address
   )
   ```

3. **Compile and test**: App will now ONLY connect to your specific ESP32

**Alternative**: Add MAC filtering to ESP32 firmware (see below)

#### GoDice Security

Currently, the app accepts any GoDice device. To restrict to specific dice:

1. **Find MAC address**: Check the bottom of your GoDice or scan with nRF Connect app

2. **Modify `handleScanResult()` in MainActivity.kt** (around line 520):
   ```kotlin
   private fun handleScanResult(result: ScanResult) {
       val device = result.device ?: return
       val address = device.address
       
       // Whitelist specific GoDice MAC addresses
       val trustedDice = setOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66")
       if (!trustedDice.contains(address)) {
           Log.d(TAG, "Rejected untrusted device: $address")
           return
       }
       
       // ... rest of existing code
   }
   ```

---

### 3. ESP32 Pairing Authentication (Advanced)

For maximum security, implement BLE pairing with PIN code on ESP32.

#### Add to `sketch_ble.ino`

**Option A: Fixed PIN (Simple)**

Add after `BLEDevice::init(DEVICE_NAME);` (around line 147):

```cpp
// Require pairing with fixed PIN
BLESecurity *pSecurity = new BLESecurity();
pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
pSecurity->setCapability(ESP_IO_CAP_OUT); // Display only
pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

// Set static PIN
uint32_t passkey = 123456; // Change this!
esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));

Serial.println("BLE Security enabled - PIN: 123456");
```

**Option B: Random PIN (More Secure)**

```cpp
// Generate random PIN on each boot
BLESecurity *pSecurity = new BLESecurity();
pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
pSecurity->setCapability(ESP_IO_CAP_OUT);

uint32_t passkey = random(100000, 999999);
esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));

Serial.print("BLE Security enabled - PIN: ");
Serial.println(passkey);
// Display this PIN on Serial Monitor when connecting
```

**Android Implementation**: User will be prompted to enter PIN when connecting. The pairing is saved for future connections.

---

### 4. Network Security

#### HTTPS API Calls

All server communication uses HTTPS (`https://lastdrop.earth`). The app's `network_security_config.xml` should enforce this:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">lastdrop.earth</domain>
    </domain-config>
</network-security-config>
```

#### Certificate Pinning (Advanced)

For production apps, consider certificate pinning to prevent MITM attacks.

---

### 5. Permission Best Practices

The app requests minimal permissions:

**Android 12+ (API 31+)**:
- `BLUETOOTH_SCAN` (find dice)
- `BLUETOOTH_CONNECT` (connect to dice)
- `INTERNET` (API calls)

**Android 11 and below**:
- `BLUETOOTH` (basic BLE)
- `BLUETOOTH_ADMIN` (scan/connect)
- `ACCESS_FINE_LOCATION` (required for BLE scanning)

**Important**: The app uses `neverForLocation` flag on `BLUETOOTH_SCAN` to clarify we don't track location:

```xml
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
```

---

## Security Checklist

Before deploying or sharing code:

- [ ] `local.properties` is gitignored
- [ ] Production API key is NOT hardcoded in source
- [ ] ESP32 MAC address whitelist is configured (optional but recommended)
- [ ] BLE pairing is enabled on ESP32 (optional for high-security scenarios)
- [ ] HTTPS is enforced for all API calls
- [ ] Debug logs don't expose sensitive data
- [ ] Permissions are minimized to only what's needed

---

## What to NEVER Commit to Git

❌ `local.properties` (contains API keys, SDK paths)  
❌ `secrets.properties` (if you create one)  
❌ Production API keys in source files  
❌ ESP32 MAC addresses (if you want to keep them private)  
❌ Signing keys or keystores  

✅ `.gitignore` already protects these files

---

## Incident Response

**If API key is exposed**:
1. Immediately revoke the key at https://lastdrop.earth/dashboard
2. Generate a new key
3. Update `local.properties` with new key
4. Rebuild app
5. If committed to Git: rewrite Git history to remove the key (complex - see Git filter-branch docs)

**If ESP32 is compromised**:
1. Power off the device
2. Update firmware with new pairing PIN
3. Update whitelist in Android app
4. Pair devices again

---

## Testing Security

### Test 1: API Key Isolation
```powershell
# Search for hardcoded API keys
Select-String -Path "app\src\**\*.kt" -Pattern "ABC123" | Where-Object { $_ -notmatch "BuildConfig" }
# Should only find the BuildConfig reference or default fallback
```

### Test 2: ESP32 Connection
Try connecting with a different BLE device named "LASTDROP-ESP32" - it should be rejected if MAC filtering is enabled.

### Test 3: Permission Scope
Check Android's Permission Manager:
- Last Drop should NOT have Location permission
- Should only have Bluetooth permission

---

## Future Enhancements

- [ ] Encrypted BLE communication (AES)
- [ ] Device registration flow with server
- [ ] User authentication for live display
- [ ] Rate limiting on API endpoints
- [ ] Audit logging for security events
