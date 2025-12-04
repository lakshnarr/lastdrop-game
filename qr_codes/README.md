# Last Drop - Board QR Codes

This folder contains QR codes for quick ESP32 board connection.

## Generated QR Codes

| Board ID | Nickname | Password | QR Code File |
|----------|----------|----------|--------------|
| LASTDROP-0001 | Game Room A | 654321 | LASTDROP-0001.png |
| LASTDROP-0002 | Tournament Table 1 | 123456 | LASTDROP-0002.png |
| LASTDROP-0003 | Cafe Board | 789012 | LASTDROP-0003.png |
| LASTDROP-0004 | Home Setup | 345678 | LASTDROP-0004.png |
| LASTDROP-0005 | Demo Unit | 901234 | LASTDROP-0005.png |

## How to Use

### 1. Print QR Codes
- Print each PNG file on paper or sticker
- Recommended size: 2x2 inches (5x5 cm)
- Ensure high contrast for reliable scanning

### 2. Attach to ESP32 Boards
- Place QR code sticker on each board
- Match board ID in ESP32 firmware (`BOARD_UNIQUE_ID`)
- Keep QR code visible and unobstructed

### 3. Scan in Android App
1. Open Last Drop app
2. Tap **"Scan QR Code"** button
3. Point camera at QR code
4. App automatically:
   - Detects board ID
   - Saves board with nickname
   - Connects using saved password
   - No manual entry needed!

## QR Code Format

Each QR code contains JSON data:

```json
{
  "boardId": "LASTDROP-0001",
  "macAddress": "AA:BB:CC:DD:EE:01",
  "password": "654321",
  "nickname": "Game Room A"
}
```

### Fields
- **boardId**: Unique board identifier (must match ESP32 firmware)
- **macAddress**: Bluetooth MAC address (update when deploying to real hardware)
- **password**: BLE pairing PIN (matches ESP32 `BLE_PAIRING_PIN`)
- **nickname**: Friendly display name in app

## Regenerating QR Codes

To create new QR codes or update existing ones:

```bash
# Edit board configurations in generate_qr_codes.py
python generate_qr_codes.py

# Test compatibility with Android app
python test_qr_compatibility.py
```

## Security Notes

⚠️ **Important**: These QR codes contain passwords in plaintext!

- Do not share QR code images publicly
- Use different passwords for production boards
- Consider regenerating with stronger passwords (8+ characters)
- MAC addresses shown here are examples - update with real hardware addresses

## Validation

All QR codes have been tested and validated against `QRCodeHelper.kt`:
- ✅ Board ID format: `LASTDROP-XXXX`
- ✅ MAC address format: `AA:BB:CC:DD:EE:FF`
- ✅ JSON structure valid
- ✅ All required fields present
- ✅ Compatible with ZXing scanner library

Test output: **5/5 QR codes passed validation** ✅

## Troubleshooting

### QR Code Won't Scan
- Ensure good lighting
- Hold camera steady 6-12 inches from QR code
- Grant camera permission in Android settings
- Clean camera lens

### Board Won't Connect After Scan
1. Verify board ID matches ESP32 firmware `BOARD_UNIQUE_ID`
2. Check MAC address is correct (update QR code if needed)
3. Verify password matches ESP32 `BLE_PAIRING_PIN`
4. Ensure board is powered on and advertising

### Update MAC Address
When deploying to real ESP32 hardware:
1. Upload firmware to board
2. Check Serial Monitor for actual MAC address
3. Update MAC in `generate_qr_codes.py`
4. Regenerate QR code: `python generate_qr_codes.py`
5. Retest: `python test_qr_compatibility.py`

## Technical Details

**Generator**: `generate_qr_codes.py` (Python 3)  
**Validator**: `test_qr_compatibility.py` (Python 3)  
**Dependencies**: `qrcode[pil]`, `pyzbar`  
**QR Library**: ZXing (Android)  
**Error Correction**: Level L (7% recovery)  
**Format**: PNG, 10px box size, 4-unit border
