# ESP32 WiFi Logging Setup Guide

## Overview
The ESP32 firmware now includes **WiFi connectivity** in addition to Bluetooth, enabling remote logging to `lastdrop.earth` server for real-time debugging and monitoring.

## Features
✅ **Dual Connectivity**: BLE for game communication + WiFi for logging  
✅ **Remote Logging**: Automatic upload of debug logs to server  
✅ **Session Tracking**: Unique session IDs for each power cycle  
✅ **Smart Buffering**: Logs buffer locally and upload every 10 seconds or when 50 entries collected  
✅ **Auto-Reconnect**: Handles WiFi disconnections gracefully  
✅ **Web Viewer**: Real-time log viewer at `https://lastdrop.earth/esp32_logs.html`

## Firmware Configuration

### 1. WiFi Credentials
Edit `sketch_ble_no_hardware.ino` lines 35-37:

```cpp
#define WIFI_ENABLED true                    // Enable WiFi logging
#define WIFI_SSID "YOUR_WIFI_SSID"          // Change to your WiFi network name
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"  // Change to your WiFi password
```

### 2. Upload Firmware
1. Connect ESP32 via USB
2. Open Arduino IDE
3. Upload `sketch_ble_no_hardware.ino`
4. Open Serial Monitor (115200 baud)

### 3. Verify WiFi Connection
In Serial Monitor, you should see:

```
[WiFi] Connecting to YourNetworkName.........
✓ WiFi Connected!
[WiFi] IP Address: 192.168.1.xxx
[WiFi] Signal Strength: -45 dBm
[WiFi] Session ID: AABBCCDDEEFF-123456
```

## Server Setup

### 1. Upload PHP Script
Upload `website/api/esp32_log.php` to your server:

```bash
scp website/api/esp32_log.php lastdrop:/var/www/lastdrop.earth/public/api/
```

### 2. Create Log Directory
On your server:

```bash
ssh lastdrop
sudo mkdir -p /var/www/lastdrop.earth/logs/esp32
sudo chown www-data:www-data /var/www/lastdrop.earth/logs/esp32
sudo chmod 755 /var/www/lastdrop.earth/logs/esp32
```

### 3. Upload Log Viewer
```bash
scp website/esp32_logs.html lastdrop:/var/www/lastdrop.earth/public/
```

## Viewing Logs

### Method 1: Web Interface
Visit: **https://lastdrop.earth/esp32_logs.html**

Features:
- Select board from dropdown
- Filter by log level (INFO, WARN, ERROR, DEBUG)
- Auto-refresh every 5 seconds
- Statistics dashboard

### Method 2: Direct File Access
SSH to server:

```bash
ssh lastdrop
tail -f /var/www/lastdrop.earth/logs/esp32/LASTDROP-NOHW-0001_2025-12-06.log
```

### Method 3: API Endpoint
Check if logs are being received:

```bash
curl https://lastdrop.earth/api/esp32_log.php \
  -X POST \
  -d "boardId=LASTDROP-NOHW-0001&sessionId=test&firmware=2.0&logData=test"
```

## Log Format

### Log Entry Structure
Each log entry follows this format:

```
timestamp|level|boardId|sessionId|message
```

Example:
```
12345|INFO|LASTDROP-NOHW-0001|AABBCC-12345|BLE client connected - starting handshake
12350|WARN|LASTDROP-NOHW-0001|AABBCC-12345|BLE client disconnected
12355|ERROR|LASTDROP-NOHW-0001|AABBCC-12345|Roll rejected - device not paired
```

### Log Levels
- **INFO**: Normal operations (connections, commands, game events)
- **WARN**: Warnings (disconnections, timeouts)
- **ERROR**: Errors (invalid commands, pairing failures)
- **DEBUG**: Detailed debugging (command routing, JSON parsing)

## Logged Events

### Connection Events
- WiFi connection/disconnection
- BLE pairing attempts
- Device connection/disconnection
- Advertising restarts

### Game Events
- Dice rolls with player and value
- Player eliminations
- Winner declarations
- Auto-coin confirmations
- Undo operations
- Game resets

### Error Events
- Invalid commands
- Pairing failures
- JSON parse errors
- Invalid player IDs

## Performance Impact

### Memory Usage
- WiFi adds ~50KB to RAM usage
- Log buffer: ~2KB (50 entries × 40 bytes avg)
- Total overhead: ~52KB

### Upload Frequency
- Every 10 seconds OR
- When 50 log entries collected
- Only when WiFi connected

### BLE Impact
**No impact!** WiFi runs on separate core:
- Core 0: WiFi + HTTP uploads
- Core 1: BLE + game logic
- Both run independently

## Troubleshooting

### WiFi Won't Connect
1. Check SSID/password in firmware
2. Verify 2.4GHz network (ESP32 doesn't support 5GHz)
3. Check Serial Monitor for error messages
4. Ensure router allows new devices

### Logs Not Appearing on Server
1. Verify PHP script is uploaded to `/api/esp32_log.php`
2. Check log directory permissions (755, owned by www-data)
3. Test API endpoint with curl
4. Check server error logs: `tail -f /var/log/nginx/error.log`

### Connection Keeps Dropping
1. Check WiFi signal strength in Serial Monitor (should be > -70 dBm)
2. Move ESP32 closer to router
3. Check for 2.4GHz interference
4. Verify router stability

### Logs Upload Slowly
- Normal! Uploads happen every 10 seconds
- Force immediate upload by generating 50+ log entries
- Check `LOG_UPLOAD_INTERVAL` in firmware (line 38)

## Disabling WiFi (BLE Only Mode)

If you want to run BLE-only without WiFi:

```cpp
#define WIFI_ENABLED false  // Line 35 in firmware
```

Re-upload firmware. ESP32 will skip WiFi initialization entirely.

## Security Considerations

### HTTPS Certificate
Current setup uses `client.setInsecure()` which skips SSL verification. For production:

```cpp
// Add your server's SSL certificate
const char* rootCACertificate = "-----BEGIN CERTIFICATE-----\n...";
client.setCACert(rootCACertificate);
```

### API Authentication
Currently open endpoint. To add authentication, modify `esp32_log.php`:

```php
$apiKey = $_POST['apiKey'] ?? '';
if ($apiKey !== 'YOUR_SECRET_KEY') {
    http_response_code(401);
    exit;
}
```

Then in ESP32 firmware:

```cpp
String postData = "apiKey=YOUR_SECRET_KEY&boardId=" + ...
```

## Log Retention

### Automatic Rotation
- Logs rotate when file size exceeds 10 MB
- Old logs kept up to 100 files per board
- Oldest logs auto-deleted

### Manual Cleanup
```bash
# Delete logs older than 30 days
find /var/www/lastdrop.earth/logs/esp32 -name "*.log" -mtime +30 -delete
```

## Advanced: Custom Log Messages

Add your own logging anywhere in the code:

```cpp
addLogEntry("INFO", "My custom message here");
addLogEntry("ERROR", "Something went wrong: " + errorDetails);
addLogEntry("DEBUG", "Variable value: " + String(myVariable));
```

## Support

If you encounter issues:

1. Check Serial Monitor output (115200 baud)
2. Visit web viewer for uploaded logs
3. Verify WiFi credentials
4. Check server logs
5. Test API endpoint manually

---

**Remote logging is now active!** Your ESP32 boards will automatically upload debug logs to the server every 10 seconds. Monitor them at: https://lastdrop.earth/esp32_logs.html 🎯
