/**
 * GoDice Direct Connection - NimBLE-Arduino Library
 * 
 * Uses standalone NimBLE-Arduino library for better BLE support
 * Connects ESP32 directly to GoDice without Android bridge
 */

// Force use of external NimBLE-Arduino library (not bundled version)
#define CONFIG_BT_NIMBLE_ROLE_CENTRAL_ONLY
#include <NimBLEDevice.h>

// Add timing check
unsigned long scanStartTime = 0;
unsigned long lastCallbackTime = 0;
int deviceCount = 0;

// GoDice Service UUID (Nordic UART Service)
static NimBLEUUID serviceUUID("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
static NimBLEUUID    charUUID_RX("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
static NimBLEUUID    charUUID_TX("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

static NimBLEAdvertisedDevice* advDevice = nullptr;
static bool doConnect = false;
static bool connected = false;
static NimBLEClient* pClient = nullptr;
static NimBLERemoteCharacteristic* pRemoteCharacteristicTX = nullptr;
static NimBLERemoteCharacteristic* pRemoteCharacteristicRX = nullptr;

// Notification callback for dice data
static void notifyCallback(NimBLERemoteCharacteristic* pChar, uint8_t* pData, size_t length, bool isNotify) {
    Serial.print("📨 Notification received: ");
    for (size_t i = 0; i < length; i++) {
        Serial.printf("%02X ", pData[i]);
    }
    Serial.println();
    
    // Parse GoDice protocol here
    // This is where you'd integrate godiceapi.c parsing
}

// Client callback
class ClientCallbacks : public NimBLEClientCallbacks {
    void onConnect(NimBLEClient* pClient) {
        Serial.println("✅ Connected to GoDice!");
        connected = true;
    }

    void onDisconnect(NimBLEClient* pClient, int reason) {
        Serial.printf("❌ Disconnected - Reason: %d\n", reason);
        connected = false;
    }
};

// Scan callback
class ScanCallbacks : public NimBLEScanCallbacks {
    void onResult(NimBLEAdvertisedDevice* advertisedDevice) {
        deviceCount++;
        lastCallbackTime = millis();
        
        // Print EVERY device found
        Serial.println("\n───────────────────────────────────────");
        Serial.printf("📡 Device #%d found (scan running %lu ms)\n", deviceCount, millis() - scanStartTime);
        Serial.printf("   Name: %s\n", advertisedDevice->getName().c_str());
        Serial.printf("   Address: %s\n", advertisedDevice->getAddress().toString().c_str());
        Serial.printf("   Type: %d (0=PUBLIC, 1=RANDOM)\n", advertisedDevice->getAddress().getType());
        Serial.printf("   RSSI: %d dBm\n", advertisedDevice->getRSSI());
        
        // Check if advertising Nordic UART Service
        if (advertisedDevice->isAdvertisingService(serviceUUID)) {
            Serial.println("   ✓ Advertising Nordic UART Service!");
        }
        
        // Check if it's a GoDice by name
        String name = String(advertisedDevice->getName().c_str());
        if (name.startsWith("GoDice_")) {
            Serial.printf("\n🎲🎲🎲 GODICE DETECTED: %s 🎲🎲🎲\n", name.c_str());
            Serial.println("   Stopping scan and connecting...");
            
            // Store device and stop scan
            advDevice = advertisedDevice;
            NimBLEDevice::getScan()->stop();
            doConnect = true;
        }
        Serial.println("───────────────────────────────────────");
    }
    
    void onScanEnd(NimBLEScanResults results) {
        Serial.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Serial.println("🏁 Scan completed!");
        Serial.printf("   Total devices found: %d\n", results.getCount());
        Serial.printf("   Scan duration: %lu ms\n", millis() - scanStartTime);
        if (lastCallbackTime > 0) {
            Serial.printf("   Last callback: %lu ms ago\n", millis() - lastCallbackTime);
        } else {
            Serial.println("   ⚠️ WARNING: No callbacks were triggered!");
        }
        if (!doConnect) {
            Serial.println("   ❌ No GoDice found");
            Serial.println("   Make sure dice is:");
            Serial.println("   - Powered on (shake to wake)");
            Serial.println("   - Within 1 meter of ESP32");
            Serial.println("   - Not connected to another device");
            Serial.println("\n   📡 Restarting scan in 5 seconds...");
        }
        Serial.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
    }
};

bool connectToGoDice() {
    if (advDevice == nullptr) {
        Serial.println("❌ No device to connect to");
        return false;
    }

    Serial.println("\n╔════════════════════════════════════════════════╗");
    Serial.println("║       CONNECTION ATTEMPT - DETAILED LOG        ║");
    Serial.println("╚════════════════════════════════════════════════╝");
    Serial.printf("→ Target Die: %s\n", advDevice->getName().c_str());
    Serial.printf("→ MAC Address: %s\n", advDevice->getAddress().toString().c_str());
    Serial.printf("→ Address Type: %d (0=PUBLIC, 1=RANDOM, 2=RANDOM_RESOLVABLE, 3=RANDOM_STATIC)\n", 
        advDevice->getAddress().getType());
    Serial.printf("→ RSSI: %d dBm\n\n", advDevice->getRSSI());
    
    // STEP 1: Create client
    Serial.print("STEP 1: Creating BLE client object... ");
    unsigned long stepStart = millis();
    pClient = NimBLEDevice::createClient();
    if (pClient == nullptr) {
        Serial.println("❌ FAILED");
        return false;
    }
    Serial.printf("✓ Client created (took %lu ms)\n", millis() - stepStart);
    Serial.printf("        Client address: %p\n", (void*)pClient);
    
    // STEP 2: Set callbacks
    Serial.print("STEP 2: Registering connection callbacks... ");
    stepStart = millis();
    pClient->setClientCallbacks(new ClientCallbacks());
    Serial.printf("✓ Callbacks registered (took %lu ms)\n", millis() - stepStart);
    
    // STEP 3: Configure connection parameters
    Serial.print("STEP 3: Configuring connection parameters... ");
    stepStart = millis();
    pClient->setConnectionParams(12, 12, 0, 51);
    pClient->setConnectTimeout(15); // 15 seconds
    Serial.printf("✓ Parameters set (took %lu ms)\n", millis() - stepStart);
    Serial.println("        Min interval: 12 * 1.25ms = 15ms");
    Serial.println("        Max interval: 12 * 1.25ms = 15ms");
    Serial.println("        Latency: 0");
    Serial.println("        Timeout: 51 * 10ms = 510ms");
    Serial.println("        Connect timeout: 15 seconds");
    
    // STEP 4: Initiate BLE connection
    Serial.println("\nSTEP 4: Initiating BLE connection...");
    Serial.println("        Using NimBLE-Arduino library v2.3.7");
    Serial.println("        Address type automatically handled from advertised device");
    stepStart = millis();
    
    if (!pClient->connect(advDevice)) {
        unsigned long elapsed = millis() - stepStart;
        Serial.printf("        ❌ FAILED after %lu ms\n", elapsed);
        Serial.println("        Error: BLE connection rejected");
        Serial.println("        This usually means:");
        Serial.println("        - Device is out of range");
        Serial.println("        - Device is already connected to another device");
        Serial.println("        - Device rejected the connection");
        NimBLEDevice::deleteClient(pClient);
        pClient = nullptr;
        return false;
    }
    
    unsigned long connectTime = millis() - stepStart;
    Serial.printf("        ✅ SUCCESS after %lu ms\n", connectTime);
    Serial.println("        BLE link established");
    
    // STEP 5: Discover services
    Serial.print("\nSTEP 5: Discovering GATT services... ");
    stepStart = millis();
    NimBLERemoteService* pRemoteService = pClient->getService(serviceUUID);
    if (pRemoteService == nullptr) {
        unsigned long elapsed = millis() - stepStart;
        Serial.printf("❌ FAILED after %lu ms\n", elapsed);
        Serial.println("        Error: Nordic UART Service not found");
        Serial.println("        UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
        pClient->disconnect();
        return false;
    }
    Serial.printf("✓ Found Nordic UART Service (took %lu ms)\n", millis() - stepStart);
    
    // STEP 6: Get TX characteristic
    Serial.print("STEP 6: Getting TX characteristic (notifications)... ");
    stepStart = millis();
    pRemoteCharacteristicTX = pRemoteService->getCharacteristic(charUUID_TX);
    if (pRemoteCharacteristicTX == nullptr) {
        unsigned long elapsed = millis() - stepStart;
        Serial.printf("❌ FAILED after %lu ms\n", elapsed);
        Serial.println("        Error: TX characteristic not found");
        Serial.println("        UUID: 6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
        pClient->disconnect();
        return false;
    }
    Serial.printf("✓ Found TX characteristic (took %lu ms)\n", millis() - stepStart);
    Serial.printf("        Properties: ");
    if (pRemoteCharacteristicTX->canNotify()) Serial.print("NOTIFY ");
    if (pRemoteCharacteristicTX->canIndicate()) Serial.print("INDICATE ");
    if (pRemoteCharacteristicTX->canRead()) Serial.print("READ ");
    Serial.println();
    
    // STEP 7: Subscribe to notifications
    Serial.print("STEP 7: Subscribing to notifications... ");
    stepStart = millis();
    if (pRemoteCharacteristicTX->canNotify()) {
        if (!pRemoteCharacteristicTX->subscribe(true, notifyCallback)) {
            unsigned long elapsed = millis() - stepStart;
            Serial.printf("❌ FAILED after %lu ms\n", elapsed);
            Serial.println("        Error: Could not enable notifications");
            pClient->disconnect();
            return false;
        }
        Serial.printf("✓ Subscribed (took %lu ms)\n", millis() - stepStart);
    } else {
        Serial.println("⚠️ SKIPPED - characteristic does not support notifications");
    }
    
    // STEP 8: Get RX characteristic
    Serial.print("STEP 8: Getting RX characteristic (write commands)... ");
    stepStart = millis();
    pRemoteCharacteristicRX = pRemoteService->getCharacteristic(charUUID_RX);
    if (pRemoteCharacteristicRX == nullptr) {
        unsigned long elapsed = millis() - stepStart;
        Serial.printf("❌ FAILED after %lu ms\n", elapsed);
        Serial.println("        Error: RX characteristic not found");
        Serial.println("        UUID: 6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
        pClient->disconnect();
        return false;
    }
    Serial.printf("✓ Found RX characteristic (took %lu ms)\n", millis() - stepStart);
    Serial.printf("        Properties: ");
    if (pRemoteCharacteristicRX->canWrite()) Serial.print("WRITE ");
    if (pRemoteCharacteristicRX->canWriteNoResponse()) Serial.print("WRITE_NO_RESP ");
    Serial.println();
    
    // STEP 9: Get MTU size
    Serial.print("STEP 9: Checking MTU size... ");
    uint16_t mtu = pClient->getMTU();
    Serial.printf("✓ MTU = %d bytes\n", mtu);
    Serial.printf("        Max payload: %d bytes\n", mtu - 3);
    
    // STEP 10: Send test command
    Serial.print("\nSTEP 10: Sending test command (Get Shell Color)... ");
    stepStart = millis();
    uint8_t getColorCmd[] = {0x17};
    if (pRemoteCharacteristicRX->writeValue(getColorCmd, sizeof(getColorCmd), false)) {
        Serial.printf("✓ Command sent (took %lu ms)\n", millis() - stepStart);
        Serial.println("         Waiting for response...");
    } else {
        Serial.printf("❌ FAILED after %lu ms\n", millis() - stepStart);
    }
    
    Serial.println("\n╔════════════════════════════════════════════════╗");
    Serial.println("║  ✅ CONNECTION SUCCESSFUL - READY TO ROLL!     ║");
    Serial.println("╚════════════════════════════════════════════════╝");
    
    return true;
}

void setup() {
    Serial.begin(115200);
    delay(2000);
    
    Serial.println("\n\n\n");
    Serial.println("╔════════════════════════════════════════════════╗");
    Serial.println("║  GoDice Direct Connection - NimBLE-Arduino    ║");
    Serial.println("╚════════════════════════════════════════════════╝");
    
    // Initialize NimBLE
    Serial.println("\n🔧 Step 1: Initializing NimBLE...");
    Serial.print("   BLE Device Init... ");
    NimBLEDevice::init("ESP32-GoDice");
    Serial.println("✓");
    
    Serial.print("   Setting TX Power to MAX... ");
    NimBLEDevice::setPower(ESP_PWR_LVL_P9); // Max power
    Serial.println("✓ (+9 dBm)");
    
    // Create scanner
    Serial.print("\n🔧 Step 2: Creating BLE scanner... ");
    NimBLEScan* pScan = NimBLEDevice::getScan();
    if (pScan == nullptr) {
        Serial.println("❌ FAILED - Scanner is NULL!");
        while(1) delay(1000);
    }
    Serial.println("✓");
    
    Serial.print("   Setting scan callbacks... ");
    pScan->setScanCallbacks(new ScanCallbacks());
    Serial.println("✓");
    
    Serial.print("   Configuring active scan... ");
    pScan->setActiveScan(true);
    Serial.println("✓");
    
    Serial.print("   Setting scan interval (100ms)... ");
    pScan->setInterval(100);
    Serial.println("✓");
    
    Serial.print("   Setting scan window (99ms)... ");
    pScan->setWindow(99);
    Serial.println("✓");
    
    Serial.println("\n✅ NimBLE initialized successfully!");
    Serial.println("   Device name: ESP32-GoDice");
    Serial.println("   TX Power: +9 dBm (max)");
    Serial.println("   Scan type: Active");
    Serial.println("   Scan interval: 100ms");
    Serial.println("   Scan window: 99ms");
    
    Serial.println("\n🔍 Starting BLE scan (10 seconds)...");
    Serial.println("   Looking for: GoDice_XXXXXX_X_v04");
    Serial.println("   Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    Serial.println("\n📡 Scan results will appear below:");
    Serial.println("════════════════════════════════════════════════\n");
    
    scanStartTime = millis();
    deviceCount = 0;
    lastCallbackTime = 0;
    
    bool scanStarted = pScan->start(10, false);
    if (!scanStarted) {
        Serial.println("❌ ERROR: Scan failed to start!");
        Serial.println("   This usually means BLE hardware issue");
    } else {
        Serial.println("✓ Scan started successfully");
        Serial.println("⏳ Waiting for devices...\n");
    }
}

void loop() {
    // Heartbeat to show loop is running
    static unsigned long lastHeartbeat = 0;
    if (millis() - lastHeartbeat > 2000 && !connected && !doConnect) {
        Serial.printf("💓 Heartbeat - scan time: %lu ms, devices: %d\n", millis() - scanStartTime, deviceCount);
        lastHeartbeat = millis();
    }
    
    // Connection requested from scan callback
    if (doConnect) {
        Serial.println("\n🔄 Attempting connection...");
        if (connectToGoDice()) {
            Serial.println("\n✅✅✅ Successfully connected to GoDice! ✅✅✅");
        } else {
            Serial.println("\n❌❌❌ Failed to connect to GoDice ❌❌❌");
            Serial.println("Restarting scan in 5 seconds...");
            delay(5000);
            advDevice = nullptr;
            doConnect = false;
            scanStartTime = millis();
            deviceCount = 0;
            lastCallbackTime = 0;
            Serial.println("🔍 Scanning for GoDice (10 seconds)...");
            NimBLEDevice::getScan()->start(10, false);
        }
        doConnect = false;
    }
    
    // Connection status
    if (connected) {
        Serial.println("💚 GoDice connected and ready 💚");
        delay(5000);
    } else {
        delay(100);
    }
}
