#!/usr/bin/env python3
"""
QR Code Generator for Last Drop ESP32 Boards

Generates QR codes containing board connection information:
- Board ID (LASTDROP-XXXX)
- MAC Address
- Password
- Nickname

Usage: python generate_qr_codes.py
Output: Creates qr_codes/ folder with PNG files for each board
"""

import qrcode
import json
import os

# Board configurations
boards = [
    {
        "boardId": "LASTDROP-0001",
        "macAddress": "AA:BB:CC:DD:EE:01",
        "password": "654321",
        "nickname": "Game Room A"
    },
    {
        "boardId": "LASTDROP-0002",
        "macAddress": "AA:BB:CC:DD:EE:02",
        "password": "123456",
        "nickname": "Tournament Table 1"
    },
    {
        "boardId": "LASTDROP-0003",
        "macAddress": "AA:BB:CC:DD:EE:03",
        "password": "789012",
        "nickname": "Cafe Board"
    },
    {
        "boardId": "LASTDROP-0004",
        "macAddress": "AA:BB:CC:DD:EE:04",
        "password": "345678",
        "nickname": "Home Setup"
    },
    {
        "boardId": "LASTDROP-0005",
        "macAddress": "AA:BB:CC:DD:EE:05",
        "password": "901234",
        "nickname": "Demo Unit"
    }
]

def generate_qr_code(board_data, output_folder):
    """Generate QR code for a board"""
    # Create JSON payload
    qr_data = json.dumps({
        "boardId": board_data["boardId"],
        "macAddress": board_data["macAddress"],
        "password": board_data["password"],
        "nickname": board_data["nickname"]
    })
    
    # Create QR code
    qr = qrcode.QRCode(
        version=1,  # Auto-size
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=10,
        border=4,
    )
    qr.add_data(qr_data)
    qr.make(fit=True)
    
    # Create image
    img = qr.make_image(fill_color="black", back_color="white")
    
    # Save to file
    filename = f"{board_data['boardId']}.png"
    filepath = os.path.join(output_folder, filename)
    img.save(filepath)
    
    print(f"✓ Generated: {filename}")
    print(f"  Nickname: {board_data['nickname']}")
    print(f"  Password: {board_data['password']}")
    print(f"  MAC: {board_data['macAddress']}")
    print(f"  QR Data: {qr_data}")
    print()
    
    return filepath

def create_info_file(boards, output_folder):
    """Create a text file with board information"""
    info_path = os.path.join(output_folder, "BOARD_INFO.txt")
    
    with open(info_path, 'w') as f:
        f.write("=" * 60 + "\n")
        f.write("LAST DROP - BOARD QR CODES\n")
        f.write("=" * 60 + "\n\n")
        
        for board in boards:
            f.write(f"Board: {board['nickname']}\n")
            f.write(f"  ID: {board['boardId']}\n")
            f.write(f"  MAC: {board['macAddress']}\n")
            f.write(f"  Password: {board['password']}\n")
            f.write(f"  QR Code: {board['boardId']}.png\n")
            f.write("-" * 60 + "\n\n")
        
        f.write("USAGE INSTRUCTIONS:\n")
        f.write("1. Print the QR code PNG files\n")
        f.write("2. Attach to corresponding ESP32 board\n")
        f.write("3. In Android app, tap 'Scan QR Code'\n")
        f.write("4. Camera will scan and auto-connect\n")
        f.write("5. No manual password entry needed!\n")
    
    print(f"✓ Created: BOARD_INFO.txt")
    print()

def main():
    # Create output folder
    output_folder = "qr_codes"
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
        print(f"Created folder: {output_folder}/\n")
    
    # Generate QR codes
    print("Generating QR codes...\n")
    for board in boards:
        generate_qr_code(board, output_folder)
    
    # Create info file
    create_info_file(boards, output_folder)
    
    print("=" * 60)
    print(f"✓ SUCCESS: Generated {len(boards)} QR codes")
    print(f"✓ Location: {os.path.abspath(output_folder)}/")
    print("=" * 60)

if __name__ == "__main__":
    main()
