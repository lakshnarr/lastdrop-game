#!/usr/bin/env python3
"""
QR Code Compatibility Test

Tests that generated QR codes match the format expected by 
QRCodeHelper.kt in the Android app.

Expected JSON format:
{
  "boardId": "LASTDROP-XXXX",
  "macAddress": "AA:BB:CC:DD:EE:FF",
  "password": "123456",
  "nickname": "Board Name"
}

Validation rules (from QRCodeHelper.kt):
1. boardId must start with "LASTDROP-"
2. macAddress must match pattern: XX:XX:XX:XX:XX:XX (hex)
3. password is optional but must be string if present
4. nickname is optional but must be string if present
"""

import json
import re
from PIL import Image
try:
    from pyzbar.pyzbar import decode
except ImportError:
    print("Installing pyzbar for QR code reading...")
    import subprocess
    subprocess.run(["python", "-m", "pip", "install", "pyzbar", "--quiet"])
    from pyzbar.pyzbar import decode

# Validation patterns (copied from QRCodeHelper.kt)
BOARD_ID_PREFIX = "LASTDROP-"
MAC_ADDRESS_PATTERN = re.compile(r"^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")

def validate_board_qr(data):
    """
    Validate QR data according to QRCodeHelper.kt rules
    Returns: (is_valid, error_message)
    """
    # Check required fields
    if "boardId" not in data:
        return False, "Missing required field: boardId"
    if "macAddress" not in data:
        return False, "Missing required field: macAddress"
    
    # Validate boardId prefix
    board_id = data["boardId"]
    if not board_id.startswith(BOARD_ID_PREFIX):
        return False, f"boardId must start with '{BOARD_ID_PREFIX}'"
    
    # Validate MAC address format
    mac_address = data["macAddress"]
    if not MAC_ADDRESS_PATTERN.match(mac_address):
        return False, f"Invalid MAC address format: {mac_address}"
    
    # Validate optional fields are strings
    if "password" in data and not isinstance(data["password"], str):
        return False, "password must be a string"
    
    if "nickname" in data and not isinstance(data["nickname"], str):
        return False, "nickname must be a string"
    
    return True, "Valid"

def test_qr_code(qr_path):
    """Test a single QR code file"""
    print(f"\n{'='*60}")
    print(f"Testing: {qr_path}")
    print(f"{'='*60}")
    
    try:
        # Read QR code
        img = Image.open(qr_path)
        decoded = decode(img)
        
        if not decoded:
            print("❌ FAIL: Could not decode QR code")
            return False
        
        # Get QR data
        qr_data_str = decoded[0].data.decode('utf-8')
        print(f"Raw QR Data: {qr_data_str}")
        
        # Parse JSON
        try:
            qr_data = json.loads(qr_data_str)
        except json.JSONDecodeError as e:
            print(f"❌ FAIL: Invalid JSON - {e}")
            return False
        
        print(f"\nParsed JSON:")
        print(json.dumps(qr_data, indent=2))
        
        # Validate against QRCodeHelper rules
        is_valid, message = validate_board_qr(qr_data)
        
        if is_valid:
            print(f"\n✅ PASS: {message}")
            print(f"\nBoard Details:")
            print(f"  ID: {qr_data['boardId']}")
            print(f"  MAC: {qr_data['macAddress']}")
            print(f"  Password: {qr_data.get('password', 'None')}")
            print(f"  Nickname: {qr_data.get('nickname', 'None')}")
            return True
        else:
            print(f"\n❌ FAIL: {message}")
            return False
            
    except Exception as e:
        print(f"❌ FAIL: Exception - {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    import os
    
    qr_folder = "qr_codes"
    
    # Find all QR code files
    qr_files = [
        os.path.join(qr_folder, f)
        for f in os.listdir(qr_folder)
        if f.endswith('.png')
    ]
    
    if not qr_files:
        print(f"❌ No QR code files found in {qr_folder}/")
        return
    
    print("=" * 60)
    print("QR CODE COMPATIBILITY TEST")
    print(f"Testing {len(qr_files)} QR codes against QRCodeHelper.kt validation")
    print("=" * 60)
    
    results = []
    for qr_file in sorted(qr_files):
        result = test_qr_code(qr_file)
        results.append((qr_file, result))
    
    # Summary
    print(f"\n\n{'='*60}")
    print("TEST SUMMARY")
    print(f"{'='*60}")
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for qr_file, result in results:
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{status}: {os.path.basename(qr_file)}")
    
    print(f"\n{passed}/{total} tests passed")
    
    if passed == total:
        print("\n🎉 SUCCESS: All QR codes are compatible with Android app!")
        print("\nNext steps:")
        print("1. Print the QR codes from qr_codes/ folder")
        print("2. Attach to ESP32 boards")
        print("3. Test scanning in Android app")
        return 0
    else:
        print("\n❌ FAILURE: Some QR codes failed validation")
        return 1

if __name__ == "__main__":
    exit(main())
