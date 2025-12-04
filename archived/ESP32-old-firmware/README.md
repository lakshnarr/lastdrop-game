# Archived ESP32 Firmware

This folder contains old ESP32 firmware versions that have been superseded by the production firmware.

## Files

### sketch_ble_testmode.ino (52,409 bytes, Dec 3, 2025)
- **Purpose**: Test mode firmware with full game logic
- **Features**: Same features as production version, identical game rules
- **Status**: Superseded by production sketch_ble.ino
- **Note**: Useful reference for testing scenarios

### sketch_enhanced.ino (19,734 bytes, Dec 2, 2025)
- **Purpose**: Early enhanced BLE implementation
- **Features**: Partial game logic, BLE foundation work
- **Status**: Development version, superseded by complete implementation
- **Note**: Historical reference for BLE development

## Active Production Firmware

**Current**: `/ESP32 Program/sketch_ble.ino` (56,508 bytes, Dec 4, 2025)

**Features**:
- Complete 20-tile board game logic
- BLE pairing with customizable password
- Remote settings (password + nickname) via NVRAM
- Player elimination and winner animations
- Multi-board support with unique board IDs
- Real-time game state synchronization with Android

**Upload This**: When programming ESP32 hardware, always use the production sketch_ble.ino from the ESP32 Program folder.

## Recovery

If you need to rollback to a previous firmware version:
1. Copy the desired .ino file from this archive
2. Upload to ESP32 using Arduino IDE
3. Note: Old versions may lack recent features (remote settings, animation events, etc.)
