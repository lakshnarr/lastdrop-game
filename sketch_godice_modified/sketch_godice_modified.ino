/*
 * GoDice Direct Connection - Using Modified Official BLE Library
 * 
 * This sketch uses a modified version of the official ESP32 BLE library
 * with fixes for connecting to random address BLE devices like GoDice.
 * 
 * Features:
 * - Direct ESP32-S3 to GoDice connection (no Android needed)
 * - Dice color detection (from shell)
 * - Rolling/Stable status
 * - Dice value (1-6) from accelerometer data
 * - Battery level
 * - Connection status
 * - LED control
 */

#include <BLEDevice.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>

// GoDice UUIDs (Nordic UART Service)
#define GODICE_SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define GODICE_TX_CHAR_UUID        "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // Write to dice
#define GODICE_RX_CHAR_UUID        "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // Receive from dice

// GoDice Protocol Commands
#define CMD_BATTERY     0x03  // Request battery level
#define CMD_GET_COLOR   0x17  // Request dice shell color
#define CMD_SET_LED     0x08  // Set LED color (3 bytes RGB follow)
#define CMD_PULSE_LED   0x10  // Pulse LED

// GoDice Protocol Messages (received)
#define MSG_ROLLING     0x52  // 'R' - Dice is rolling
#define MSG_STABLE      0x53  // 'S' - Dice is stable (includes value)
#define MSG_FAKE_STABLE 0x46  // 'F' - Fake stable
#define MSG_TILT_STABLE 0x54  // 'T' - Tilt stable
#define MSG_MOVE_STABLE 0x4D  // 'M' - Move stable
#define MSG_BATTERY     0x42  // 'B' - Battery response
#define MSG_COLOR       0x43  // 'C' - Color response
#define MSG_TAP         0x31  // Single tap
#define MSG_DOUBLE_TAP  0x32  // Double tap

// Dice color codes (from dice shell)
enum DiceColor {
    DICE_COLOR_BLACK = 0,
    DICE_COLOR_RED = 1,
    DICE_COLOR_GREEN = 2,
    DICE_COLOR_BLUE = 3,
    DICE_COLOR_YELLOW = 4,
    DICE_COLOR_ORANGE = 5,
    DICE_COLOR_UNKNOWN = 255
};

// GoDice XYZ vectors for D6 face detection
// These are the accelerometer readings when each face is UP (visible)
// Values are approximately ±64 on the dominant axis
// Mapping calibrated from actual GoDice Orange dice readings
const int8_t D6_VECTORS[6][3] = {
    // {  X,   Y,   Z }  // Face UP
    { -64,  0,   0 },    // Face 1 UP (X negative)
    {  0,   0,  64 },    // Face 2 UP (Z positive)
    {  0,  64,   0 },    // Face 3 UP (Y positive)
    {  0, -64,   0 },    // Face 4 UP (Y negative)
    {  0,   0, -64 },    // Face 5 UP (Z negative)
    {  64,  0,   0 }     // Face 6 UP (X positive)
};

// ==================== Global State ====================
BLEScan* pScan = nullptr;
BLEClient* pClient = nullptr;
BLERemoteCharacteristic* pTxChar = nullptr;
BLERemoteCharacteristic* pRxChar = nullptr;

bool deviceFound = false;
bool doConnect = false;
bool connected = false;

String foundDeviceName = "";
BLEAddress* foundDeviceAddress = nullptr;
uint8_t foundDeviceType = 0;

// Dice state
String diceColorName = "Unknown";
DiceColor diceColor = DICE_COLOR_UNKNOWN;
int lastDiceValue = 0;
bool isRolling = false;
int batteryLevel = -1;
unsigned long lastRollTime = 0;
unsigned long lastStableTime = 0;
unsigned long rollCount = 0;

// ==================== Helper Functions ====================

const char* getColorName(DiceColor color) {
    switch (color) {
        case DICE_COLOR_BLACK:  return "Black";
        case DICE_COLOR_RED:    return "Red";
        case DICE_COLOR_GREEN:  return "Green";
        case DICE_COLOR_BLUE:   return "Blue";
        case DICE_COLOR_YELLOW: return "Yellow";
        case DICE_COLOR_ORANGE: return "Orange";
        default:                return "Unknown";
    }
}

// Convert accelerometer XYZ to dice face (1-6)
// D6_VECTORS contains the accelerometer reading when each face is UP
int xyzToDiceFace(int8_t x, int8_t y, int8_t z) {
    int bestFace = 1;
    int bestDistance = 999999;
    
    for (int face = 0; face < 6; face++) {
        int dx = x - D6_VECTORS[face][0];
        int dy = y - D6_VECTORS[face][1];
        int dz = z - D6_VECTORS[face][2];
        int distance = dx*dx + dy*dy + dz*dz;
        
        if (distance < bestDistance) {
            bestDistance = distance;
            bestFace = face + 1;  // Faces are 1-6
        }
    }
    
    return bestFace;
}

// Send command to dice
void sendCommand(uint8_t cmd) {
    if (pTxChar && connected) {
        pTxChar->writeValue(&cmd, 1);
    }
}

void sendCommandBytes(uint8_t* data, size_t len) {
    if (pTxChar && connected) {
        pTxChar->writeValue(data, len);
    }
}

// Request battery level
void requestBattery() {
    Serial.println("📤 Requesting battery level...");
    sendCommand(CMD_BATTERY);
}

// Request dice color
void requestColor() {
    Serial.println("📤 Requesting dice color...");
    sendCommand(CMD_GET_COLOR);
}

// Set LED color
void setLedColor(uint8_t r, uint8_t g, uint8_t b) {
    uint8_t cmd[] = {CMD_SET_LED, r, g, b};
    sendCommandBytes(cmd, 4);
    Serial.printf("📤 Set LED: RGB(%d,%d,%d)\n", r, g, b);
}

// Pulse LED
void pulseLed(uint8_t r, uint8_t g, uint8_t b, uint8_t count, uint8_t onTime, uint8_t offTime) {
    uint8_t cmd[] = {CMD_PULSE_LED, count, onTime, offTime, r, g, b};
    sendCommandBytes(cmd, 7);
}

// Print status
void printStatus() {
    Serial.println("\n╔═══════════════════════════════════╗");
    Serial.println("║          GODICE STATUS            ║");
    Serial.println("╠═══════════════════════════════════╣");
    Serial.printf("║ Connected:  %-20s ║\n", connected ? "✅ YES" : "❌ NO");
    if (connected) {
        Serial.printf("║ Dice Name:  %-20s ║\n", foundDeviceName.c_str());
        Serial.printf("║ Dice Color: %-20s ║\n", diceColorName.c_str());
        Serial.printf("║ Battery:    %-20s ║\n", batteryLevel >= 0 ? (String(batteryLevel) + "%").c_str() : "Unknown");
        Serial.printf("║ Status:     %-20s ║\n", isRolling ? "🎲 Rolling..." : "⏸️ Idle");
        Serial.printf("║ Last Value: %-20d ║\n", lastDiceValue);
        Serial.printf("║ Roll Count: %-20lu ║\n", rollCount);
    }
    Serial.println("╚═══════════════════════════════════╝\n");
}

// ==================== Notification Callback ====================

static void notifyCallback(BLERemoteCharacteristic* pChar, uint8_t* pData, size_t length, bool isNotify) {
    if (length < 1) return;
    
    uint8_t msgType = pData[0];
    
    // Debug: Print raw data
    Serial.print("📥 [");
    for (size_t i = 0; i < length; i++) {
        if (pData[i] < 0x10) Serial.print("0");
        Serial.print(pData[i], HEX);
        if (i < length - 1) Serial.print(" ");
    }
    Serial.print("] ");
    
    switch (msgType) {
        case MSG_ROLLING:
            isRolling = true;
            lastRollTime = millis();
            Serial.println("🎲 ROLLING...");
            break;
            
        case MSG_STABLE:  // 0x53 'S' - format: [S][x][y][z]
            if (length >= 4) {
                isRolling = false;
                lastStableTime = millis();
                rollCount++;
                
                // Extract XYZ accelerometer data (signed bytes)
                int8_t x = (int8_t)pData[1];
                int8_t y = (int8_t)pData[2];
                int8_t z = (int8_t)pData[3];
                
                // Convert to dice face (returns face facing UP)
                lastDiceValue = xyzToDiceFace(x, y, z);
                
                Serial.printf("✅ STABLE: %d (xyz: %d,%d,%d)\n", lastDiceValue, x, y, z);
            }
            break;
            
        case MSG_FAKE_STABLE:   // 0x46 'F' - format: [F][S][x][y][z]
        case MSG_TILT_STABLE:   // 0x54 'T' - format: [T][S][x][y][z]
        case MSG_MOVE_STABLE:   // 0x4D 'M' - format: [M][S][x][y][z]
            if (length >= 5) {
                isRolling = false;
                lastStableTime = millis();
                rollCount++;
                
                // XYZ starts at offset 2 (after type + 'S')
                int8_t x = (int8_t)pData[2];
                int8_t y = (int8_t)pData[3];
                int8_t z = (int8_t)pData[4];
                
                lastDiceValue = xyzToDiceFace(x, y, z);
                
                Serial.printf("✅ STABLE: %d (xyz: %d,%d,%d)\n", lastDiceValue, x, y, z);
            }
            break;
            
        case MSG_BATTERY:
            // Response format: "Bat" + value OR just [0x42, value]
            if (length >= 4 && pData[1] == 'a' && pData[2] == 't') {
                // "Batd" format - battery is ASCII char at pos 3
                batteryLevel = pData[3];
                Serial.printf("🔋 Battery: %d%%\n", batteryLevel);
            } else if (length >= 2) {
                batteryLevel = pData[1];
                Serial.printf("🔋 Battery: %d%%\n", batteryLevel);
            }
            break;
            
        case MSG_COLOR:
            // Response format: "Col" + color_code
            if (length >= 4 && pData[1] == 'o' && pData[2] == 'l') {
                // "Col" prefix - color is at position 3
                diceColor = (DiceColor)pData[3];
                diceColorName = getColorName(diceColor);
                Serial.printf("🎨 Dice Color: %s (code=%d)\n", diceColorName.c_str(), pData[3]);
            } else if (length >= 2) {
                diceColor = (DiceColor)pData[1];
                diceColorName = getColorName(diceColor);
                Serial.printf("🎨 Dice Color: %s (code=%d)\n", diceColorName.c_str(), pData[1]);
            }
            break;
            
        case MSG_TAP:
            Serial.println("👆 Single Tap detected");
            break;
            
        case MSG_DOUBLE_TAP:
            Serial.println("👆👆 Double Tap detected");
            break;
            
        default:
            Serial.printf("❓ Unknown message: 0x%02X\n", msgType);
            break;
    }
}

// ==================== BLE Callbacks ====================

// Client callbacks
class MyClientCallback : public BLEClientCallbacks {
    void onConnect(BLEClient* pclient) override {
        Serial.println("✅ onConnect callback");
        connected = true;
    }

    void onDisconnect(BLEClient* pclient) override {
        Serial.println("❌ onDisconnect callback - DICE DISCONNECTED");
        connected = false;
        isRolling = false;
    }
};

// Scan callbacks
class MyScanCallbacks : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) override {
        String name = advertisedDevice.getName().c_str();
        
        Serial.printf("📡 Device: %s (%s) RSSI:%d Type:%d\n",
            name.length() > 0 ? name.c_str() : "(unnamed)",
            advertisedDevice.getAddress().toString().c_str(),
            advertisedDevice.getRSSI(),
            advertisedDevice.getAddressType());
        
        if (name.startsWith("GoDice_") && !deviceFound) {
            Serial.println("\n========================================");
            Serial.printf("🎲 GODICE FOUND: %s\n", name.c_str());
            Serial.printf("   Address: %s\n", advertisedDevice.getAddress().toString().c_str());
            Serial.printf("   Address Type: %d (0=PUBLIC, 1=RANDOM)\n", advertisedDevice.getAddressType());
            Serial.printf("   RSSI: %d dBm\n", advertisedDevice.getRSSI());
            Serial.println("========================================\n");
            
            deviceFound = true;
            foundDeviceName = name;
            foundDeviceAddress = new BLEAddress(advertisedDevice.getAddress());
            foundDeviceType = advertisedDevice.getAddressType();
            
            // Stop scanning
            pScan->stop();
            doConnect = true;
        }
    }
};

bool connectToDevice() {
    if (foundDeviceAddress == nullptr) {
        Serial.println("❌ No device address");
        return false;
    }
    
    Serial.println("\n========================================");
    Serial.printf("🔗 Connecting to: %s\n", foundDeviceName.c_str());
    Serial.printf("   Address: %s\n", foundDeviceAddress->toString().c_str());
    Serial.printf("   Address Type: %d\n", foundDeviceType);
    Serial.println("========================================\n");
    
    // Create client
    pClient = BLEDevice::createClient();
    pClient->setClientCallbacks(new MyClientCallback());
    Serial.println("✓ Created BLE client");
    
    // Connect with explicit address type
    // This is the key - passing foundDeviceType (which is 1 for RANDOM)
    Serial.println("⏳ Calling connect() with address type...");
    Serial.println("⏳ This may take 10-15 seconds...");
    
    bool success = pClient->connect(*foundDeviceAddress, foundDeviceType);
    
    if (!success) {
        Serial.println("❌ Connection failed - dice may be asleep or out of range");
        Serial.println("💡 Try rolling the dice to wake it up and scan again");
        return false;
    }
    
    Serial.println("✅ Connected!");
    
    // Get service
    Serial.println("🔍 Looking for Nordic UART service...");
    BLERemoteService* pService = pClient->getService(GODICE_SERVICE_UUID);
    if (pService == nullptr) {
        Serial.println("❌ Service not found");
        pClient->disconnect();
        return false;
    }
    Serial.println("✅ Found service");
    
    // Get TX characteristic
    pTxChar = pService->getCharacteristic(GODICE_TX_CHAR_UUID);
    if (pTxChar == nullptr) {
        Serial.println("❌ TX characteristic not found");
        pClient->disconnect();
        return false;
    }
    Serial.println("✅ Found TX characteristic");
    
    // Get RX characteristic and subscribe
    pRxChar = pService->getCharacteristic(GODICE_RX_CHAR_UUID);
    if (pRxChar == nullptr) {
        Serial.println("❌ RX characteristic not found");
        pClient->disconnect();
        return false;
    }
    Serial.println("✅ Found RX characteristic");
    
    if (pRxChar->canNotify()) {
        pRxChar->registerForNotify(notifyCallback);
        Serial.println("✅ Subscribed to notifications");
    }
    
    // Flash LED green to indicate successful connection
    pulseLed(0, 255, 0, 3, 15, 10);  // 3 green pulses
    
    Serial.println("\n🎉 ====== CONNECTION COMPLETE ======");
    Serial.println("Commands:");
    Serial.println("  b - Request battery level");
    Serial.println("  c - Request dice color");
    Serial.println("  r - Set LED to RED");
    Serial.println("  g - Set LED to GREEN");
    Serial.println("  o - Set LED OFF");
    Serial.println("  p - Print status");
    Serial.println("  d - Disconnect");
    Serial.println("Roll the dice to see values!");
    Serial.println("=====================================\n");
    
    // Request initial info after connection
    delay(500);
    requestColor();
    delay(200);
    requestBattery();
    
    return true;
}

void startScan() {
    Serial.println("\n🔍 Starting BLE scan for GoDice...");
    
    deviceFound = false;
    doConnect = false;
    
    if (foundDeviceAddress != nullptr) {
        delete foundDeviceAddress;
        foundDeviceAddress = nullptr;
    }
    
    pScan->setActiveScan(true);
    pScan->setInterval(100);
    pScan->setWindow(99);
    
    Serial.println("   Scanning for 30 seconds...\n");
    pScan->start(30, false);
}

void setup() {
    Serial.begin(115200);
    delay(2000);
    
    Serial.println("\n");
    Serial.println("╔═══════════════════════════════════════════════════════╗");
    Serial.println("║   GoDice Direct - ESP32-S3 to GoDice BLE Connection   ║");
    Serial.println("║          Using Modified Official BLE Library          ║");
    Serial.println("╚═══════════════════════════════════════════════════════╝");
    Serial.println();
    
    Serial.println("🔧 Initializing BLE...");
    BLEDevice::init("ESP32-GoDice");
    Serial.println("✓ BLE initialized");
    
    // Get scanner
    pScan = BLEDevice::getScan();
    pScan->setAdvertisedDeviceCallbacks(new MyScanCallbacks());
    Serial.println("✓ Scanner ready");
    
    Serial.println("\nCommands:");
    Serial.println("  s - Start scanning for GoDice");
    Serial.println("  p - Print current status");
    Serial.println("\nPress 's' to start scanning...\n");
}

void loop() {
    // Serial commands
    if (Serial.available()) {
        char c = Serial.read();
        
        switch (c) {
            case 's':
            case 'S':
                if (!connected) {
                    startScan();
                }
                break;
                
            case 'd':
            case 'D':
                if (pClient && pClient->isConnected()) {
                    Serial.println("🔌 Disconnecting...");
                    pClient->disconnect();
                }
                break;
                
            case 'b':
            case 'B':
                if (connected) requestBattery();
                break;
                
            case 'c':
            case 'C':
                if (connected) requestColor();
                break;
                
            case 'r':
            case 'R':
                if (connected) setLedColor(255, 0, 0);  // RED
                break;
                
            case 'g':
            case 'G':
                if (connected) setLedColor(0, 255, 0);  // GREEN
                break;
                
            case 'o':
            case 'O':
                if (connected) setLedColor(0, 0, 0);  // OFF
                break;
                
            case 'p':
            case 'P':
                printStatus();
                break;
        }
    }
    
    // Handle connection after scan
    if (doConnect && foundDeviceAddress != nullptr) {
        doConnect = false;
        delay(1000);
        connectToDevice();
    }
    
    // Heartbeat every 15 seconds
    static unsigned long lastBeat = 0;
    if (millis() - lastBeat > 15000) {
        lastBeat = millis();
        
        if (connected) {
            Serial.printf("💓 [CONNECTED] Color:%s Battery:%d%% LastRoll:%d Rolls:%lu\n", 
                diceColorName.c_str(), batteryLevel, lastDiceValue, rollCount);
        } else {
            Serial.println("💓 [DISCONNECTED] Press 's' to scan");
        }
    }
    
    delay(10);
}
