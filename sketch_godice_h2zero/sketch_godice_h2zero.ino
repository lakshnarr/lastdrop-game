/*
 * GoDice Direct Connection - Using h2zero/NimBLE-Arduino Library
 * 
 * This sketch uses the external NimBLE-Arduino library from h2zero
 * which has better support for connecting to random address BLE devices.
 * 
 * Key differences from bundled ESP32 BLE:
 * 1. Uses NimBLEDevice instead of BLEDevice
 * 2. Proper address type handling with getType()
 * 3. Direct connection using NimBLEAdvertisedDevice pointer
 */

#include <NimBLEDevice.h>

// GoDice UUIDs
#define GODICE_SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"  // Nordic UART
#define GODICE_TX_CHAR_UUID        "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // Write (TX from our perspective)
#define GODICE_RX_CHAR_UUID        "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // Notify (RX from our perspective)

// State machine
enum State {
    STATE_IDLE,
    STATE_SCANNING,
    STATE_CONNECTING,
    STATE_CONNECTED,
    STATE_ERROR
};

State currentState = STATE_IDLE;
NimBLEScan* pScan = nullptr;
NimBLEClient* pClient = nullptr;
NimBLERemoteCharacteristic* pTxChar = nullptr;
NimBLERemoteCharacteristic* pRxChar = nullptr;

// Store found device info
NimBLEAdvertisedDevice* foundDevice = nullptr;
String foundDeviceName = "";
bool doConnect = false;
bool deviceConnected = false;

// Connection parameters
const int SCAN_TIME = 30;        // seconds
const int CONNECT_TIMEOUT = 30;  // seconds

// Callback to receive dice data
void notifyCallback(NimBLERemoteCharacteristic* pChar, uint8_t* pData, size_t length, bool isNotify) {
    Serial.print("📥 Dice data received (");
    Serial.print(length);
    Serial.print(" bytes): ");
    
    // Print hex
    for (size_t i = 0; i < length; i++) {
        if (pData[i] < 0x10) Serial.print("0");
        Serial.print(pData[i], HEX);
        Serial.print(" ");
    }
    Serial.println();
    
    // Parse GoDice protocol
    if (length >= 2) {
        uint8_t msgType = pData[0];
        switch (msgType) {
            case 0x52: // 'R' - Roll result
                if (length >= 2) {
                    int diceValue = pData[1];
                    Serial.printf("🎲 DICE ROLLED: %d\n", diceValue);
                }
                break;
            case 0x53: // 'S' - Stable
                if (length >= 2) {
                    int diceValue = pData[1];
                    Serial.printf("✅ DICE STABLE: %d\n", diceValue);
                }
                break;
            case 0x42: // 'B' - Battery
                if (length >= 2) {
                    int battery = pData[1];
                    Serial.printf("🔋 Battery: %d%%\n", battery);
                }
                break;
            default:
                Serial.printf("Unknown message type: 0x%02X\n", msgType);
        }
    }
}

// Client callbacks
class ClientCallbacks : public NimBLEClientCallbacks {
    void onConnect(NimBLEClient* pClient) override {
        Serial.println("✅ Client connected callback");
        deviceConnected = true;
    }
    
    void onDisconnect(NimBLEClient* pClient, int reason) override {
        Serial.printf("❌ Client disconnected, reason: %d\n", reason);
        deviceConnected = false;
        currentState = STATE_IDLE;
        
        // Clean up
        if (foundDevice) {
            delete foundDevice;
            foundDevice = nullptr;
        }
    }
    
    bool onConnParamsUpdateRequest(NimBLEClient* pClient, const ble_gap_upd_params* params) override {
        Serial.println("📊 Connection parameters update requested");
        return true;  // Accept all parameters
    }
};

// Scan callbacks
class ScanCallbacks : public NimBLEScanCallbacks {
    void onResult(const NimBLEAdvertisedDevice* advertisedDevice) override {
        String name = advertisedDevice->getName().c_str();
        
        // Debug all devices
        Serial.printf("📡 Found: %s (%s) RSSI:%d Type:%d\n",
            name.c_str(),
            advertisedDevice->getAddress().toString().c_str(),
            advertisedDevice->getRSSI(),
            advertisedDevice->getAddress().getType());
        
        // Check if it's a GoDice
        if (name.startsWith("GoDice_")) {
            Serial.println("========================================");
            Serial.printf("🎲 GODICE FOUND: %s\n", name.c_str());
            Serial.printf("   Address: %s\n", advertisedDevice->getAddress().toString().c_str());
            Serial.printf("   Type: %d (0=PUBLIC, 1=RANDOM)\n", advertisedDevice->getAddress().getType());
            Serial.printf("   RSSI: %d dBm\n", advertisedDevice->getRSSI());
            Serial.println("========================================");
            
            // Store device for connection (make a copy!)
            if (foundDevice == nullptr) {
                foundDevice = new NimBLEAdvertisedDevice(*advertisedDevice);
                foundDeviceName = name;
                doConnect = true;
                
                // Stop scanning
                NimBLEDevice::getScan()->stop();
            }
        }
    }
    
    void onScanEnd(const NimBLEScanResults& results, int reason) override {
        Serial.printf("🔍 Scan ended. Found %d devices. Reason: %d\n", results.getCount(), reason);
    }
};

bool connectToDevice() {
    if (foundDevice == nullptr) {
        Serial.println("❌ No device to connect to");
        return false;
    }
    
    currentState = STATE_CONNECTING;
    Serial.println("\n========================================");
    Serial.printf("🔗 Connecting to: %s\n", foundDeviceName.c_str());
    Serial.printf("   Address: %s\n", foundDevice->getAddress().toString().c_str());
    Serial.printf("   Address Type: %d\n", foundDevice->getAddress().getType());
    Serial.println("========================================\n");
    
    // Delete old client and create fresh one for each connection attempt
    if (pClient != nullptr) {
        NimBLEDevice::deleteClient(pClient);
        pClient = nullptr;
    }
    
    // Create client with the peer address already set
    pClient = NimBLEDevice::createClient(foundDevice->getAddress());
    pClient->setClientCallbacks(new ClientCallbacks(), false);
    pClient->setConnectTimeout(30);  // 30 seconds
    Serial.println("✓ Created BLE client with peer address");
    
    // Use more relaxed connection parameters
    // min_int, max_int, latency, timeout (in units of 1.25ms, 1.25ms, intervals, 10ms)
    pClient->setConnectionParams(24, 48, 0, 600);  // 30-60ms interval, 6s timeout
    
    Serial.println("⏳ Attempting connection...");
    Serial.println("   Using connect(true, false, true) - delete attrs, sync, exchange MTU");
    
    // Try different connection approaches
    for (int attempt = 1; attempt <= 5; attempt++) {
        Serial.printf("\n--- Attempt %d/5 ---\n", attempt);
        
        unsigned long startTime = millis();
        bool connected = false;
        
        // Use the connect() overload that takes just flags
        // This uses the peer address already set in createClient()
        // Parameters: deleteAttributes, asyncConnect, exchangeMTU
        connected = pClient->connect(true, false, true);
        
        unsigned long elapsed = millis() - startTime;
        Serial.printf("   Attempt took %lu ms\n", elapsed);
        
        if (connected && pClient->isConnected()) {
            Serial.println("✅ Connection successful!");
            break;
        }
        
        int lastErr = pClient->getLastError();
        Serial.printf("   Connected=%d, isConnected=%d, Error: %d\n", connected, pClient->isConnected(), lastErr);
        
        // Error code meanings:
        // 13 = BLE_HS_ENOTSYNCED or timeout
        // 14 = BLE_HS_EALREADY
        // etc
        
        if (attempt < 5) {
            Serial.println("   Waiting 3 seconds before retry...");
            delay(3000);
        }
    }
    
    unsigned long elapsed = 0;
    
    if (!pClient->isConnected()) {
        Serial.println("❌ All connection attempts failed");
        currentState = STATE_ERROR;
        return false;
    }
    
    Serial.println("✅ Connected successfully!");
    Serial.printf("   MTU: %d\n", pClient->getMTU());
    
    // Discover services
    Serial.println("🔍 Discovering services...");
    NimBLERemoteService* pService = pClient->getService(GODICE_SERVICE_UUID);
    
    if (pService == nullptr) {
        Serial.println("❌ Nordic UART service not found");
        pClient->disconnect();
        currentState = STATE_ERROR;
        return false;
    }
    Serial.println("✅ Found Nordic UART service");
    
    // Get TX characteristic (for writing commands)
    pTxChar = pService->getCharacteristic(GODICE_TX_CHAR_UUID);
    if (pTxChar == nullptr) {
        Serial.println("❌ TX characteristic not found");
        pClient->disconnect();
        currentState = STATE_ERROR;
        return false;
    }
    Serial.println("✅ Found TX characteristic");
    
    // Get RX characteristic (for notifications)
    pRxChar = pService->getCharacteristic(GODICE_RX_CHAR_UUID);
    if (pRxChar == nullptr) {
        Serial.println("❌ RX characteristic not found");
        pClient->disconnect();
        currentState = STATE_ERROR;
        return false;
    }
    Serial.println("✅ Found RX characteristic");
    
    // Subscribe to notifications
    if (pRxChar->canNotify()) {
        Serial.println("📬 Subscribing to notifications...");
        if (pRxChar->subscribe(true, notifyCallback)) {
            Serial.println("✅ Subscribed to dice notifications!");
        } else {
            Serial.println("⚠️ Failed to subscribe to notifications");
        }
    }
    
    currentState = STATE_CONNECTED;
    deviceConnected = true;
    
    Serial.println("\n🎉 ====== CONNECTION COMPLETE ======");
    Serial.println("Roll the dice to see values!");
    Serial.println("Type 'b' to request battery level");
    Serial.println("Type 'c' to request color");
    Serial.println("=====================================\n");
    
    return true;
}

void startScan() {
    Serial.println("\n🔍 Starting BLE scan for GoDice...");
    Serial.println("   Looking for devices named 'GoDice_*'");
    
    currentState = STATE_SCANNING;
    doConnect = false;
    
    if (foundDevice) {
        delete foundDevice;
        foundDevice = nullptr;
    }
    
    pScan = NimBLEDevice::getScan();
    pScan->setScanCallbacks(new ScanCallbacks(), false);
    pScan->setActiveScan(true);
    pScan->setInterval(100);
    pScan->setWindow(99);
    pScan->setMaxResults(0);  // Don't store results, use callback only
    
    Serial.printf("   Scan duration: %d seconds\n\n", SCAN_TIME);
    pScan->start(SCAN_TIME, false);  // false = don't continue after timeout
}

void sendCommand(uint8_t* cmd, size_t len) {
    if (pTxChar != nullptr && deviceConnected) {
        pTxChar->writeValue(cmd, len, false);
        Serial.printf("📤 Sent command (%d bytes)\n", len);
    } else {
        Serial.println("❌ Not connected, cannot send command");
    }
}

void requestBattery() {
    uint8_t cmd[] = {0x42};  // 'B' for battery
    sendCommand(cmd, 1);
}

void requestColor() {
    uint8_t cmd[] = {0x43};  // 'C' for color
    sendCommand(cmd, 1);
}

void setup() {
    Serial.begin(115200);
    delay(2000);
    
    Serial.println("\n");
    Serial.println("╔═══════════════════════════════════════════════════╗");
    Serial.println("║     GoDice Direct Connection (h2zero NimBLE)      ║");
    Serial.println("║            ESP32-S3 BLE Client                    ║");
    Serial.println("╚═══════════════════════════════════════════════════╝");
    Serial.println();
    
    // Initialize NimBLE
    Serial.println("🔧 Initializing NimBLE...");
    NimBLEDevice::init("ESP32-GoDice");
    
    // Power settings
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);  // Max power
    Serial.println("✓ BLE initialized with max power");
    
    // Use PUBLIC address type for our device (this works for scanning)
    NimBLEDevice::setOwnAddrType(BLE_OWN_ADDR_PUBLIC);
    Serial.println("✓ Own address type set to PUBLIC");
    
    Serial.println("\nPress Enter to start scanning...\n");
}

void loop() {
    // Check for serial commands
    if (Serial.available()) {
        char c = Serial.read();
        
        switch (c) {
            case '\n':
            case '\r':
                if (currentState == STATE_IDLE || currentState == STATE_ERROR) {
                    startScan();
                }
                break;
                
            case 's':
            case 'S':
                startScan();
                break;
                
            case 'b':
            case 'B':
                requestBattery();
                break;
                
            case 'c':
            case 'C':
                requestColor();
                break;
                
            case 'd':
            case 'D':
                if (pClient && pClient->isConnected()) {
                    Serial.println("Disconnecting...");
                    pClient->disconnect();
                }
                break;
        }
    }
    
    // Handle connection after scan finds device
    if (doConnect && foundDevice != nullptr) {
        doConnect = false;
        delay(500);  // Small delay after scan stops
        connectToDevice();
    }
    
    // Status heartbeat
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 10000) {
        lastHeartbeat = millis();
        
        Serial.print("💓 [");
        switch (currentState) {
            case STATE_IDLE: Serial.print("IDLE"); break;
            case STATE_SCANNING: Serial.print("SCANNING"); break;
            case STATE_CONNECTING: Serial.print("CONNECTING"); break;
            case STATE_CONNECTED: Serial.print("CONNECTED"); break;
            case STATE_ERROR: Serial.print("ERROR"); break;
        }
        Serial.println("]");
        
        if (currentState == STATE_IDLE || currentState == STATE_ERROR) {
            Serial.println("   Press Enter or 's' to scan");
        }
    }
    
    delay(10);
}
