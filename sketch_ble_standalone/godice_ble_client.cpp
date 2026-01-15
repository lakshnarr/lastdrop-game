/**
 * GoDice BLE Client - ESP32 Implementation
 */

#include "godice_ble_client.h"
#include <Arduino.h>
#include <map>
#include <string>

// ==================== STATIC INSTANCE FOR BLE CALLBACKS ====================
// Static pointer to access client instance from BLE lambda callbacks
static GoDiceBLEClient* g_clientInstance = nullptr;

// ==================== STATIC GODICE CALLBACKS ====================

// Global callback trampolines (godiceapi.c expects C function pointers)
static void godice_color_callback(void* userdata, int dice_id, godice_color_t color) {
    GoDiceBLEClient* client = (GoDiceBLEClient*)userdata;
    GoDiceInfo* info = client->getDiceInfo(dice_id);
    if (info && info->connected) {
        info->shellColor = color;
        // Forward to user event handler
        if (client->eventHandler) {
            client->eventHandler->onDiceColor(dice_id, color);
        }
    }
}

static void godice_stable_callback(void* userdata, int dice_id, uint8_t number) {
    GoDiceBLEClient* client = (GoDiceBLEClient*)userdata;
    GoDiceInfo* info = client->getDiceInfo(dice_id);
    if (info && info->connected) {
        info->lastRoll = number;
        info->rolling = false;
        info->lastSeen = millis();
        if (client->eventHandler) {
            client->eventHandler->onDiceStable(dice_id, number);
        }
    }
}

static void godice_roll_callback(void* userdata, int dice_id) {
    GoDiceBLEClient* client = (GoDiceBLEClient*)userdata;
    GoDiceInfo* info = client->getDiceInfo(dice_id);
    if (info && info->connected) {
        info->rolling = true;
        info->lastSeen = millis();
        if (client->eventHandler) {
            client->eventHandler->onDiceRolling(dice_id);
        }
    }
}

static void godice_battery_callback(void* userdata, int dice_id, uint8_t level) {
    GoDiceBLEClient* client = (GoDiceBLEClient*)userdata;
    GoDiceInfo* info = client->getDiceInfo(dice_id);
    if (info && info->connected) {
        info->batteryLevel = level;
        info->lastSeen = millis();
        if (client->eventHandler) {
            client->eventHandler->onDiceBattery(dice_id, level);
        }
    }
}

static void godice_charging_callback(void* userdata, int dice_id, bool charging) {
    GoDiceBLEClient* client = (GoDiceBLEClient*)userdata;
    GoDiceInfo* info = client->getDiceInfo(dice_id);
    if (info && info->connected) {
        info->charging = charging;
        info->lastSeen = millis();
        if (client->eventHandler) {
            client->eventHandler->onDiceCharging(dice_id, charging);
        }
    }
}

// ==================== BLE CLIENT CALLBACKS ====================

void GoDiceBLEClient::DiceClientCallbacks::onConnect(BLEClient* client) {
    Serial.println("\n┌─────────────────────────────────────────────────────┐");
    Serial.printf("│ CALLBACK: onConnect() - Slot %d                     │\n", slot);
    Serial.println("└─────────────────────────────────────────────────────┘");
    Serial.printf("  → BLE connection established\n");
    Serial.printf("  → Client pointer: 0x%p\n\n", client);
    
    Serial.print("  [1/6] Setting MTU to 512... ");
    client->setMTU(512);
    Serial.println("✓");
    
    Serial.print("  [2/6] Discovering services... ");
    Serial.flush();
    BLERemoteService* service = client->getService(GODICE_SERVICE_UUID);
    if (service == nullptr) {
        Serial.println("❌ FAILED");
        Serial.println("        GoDice service not found!");
        client->disconnect();
        return;
    }
    Serial.printf("✓ Found service %s\n", GODICE_SERVICE_UUID);
    
    Serial.print("  [3/6] Getting TX characteristic... ");
    Serial.flush();
    parent->dice[slot].txChar = service->getCharacteristic(GODICE_CHAR_TX_UUID);
    if (parent->dice[slot].txChar == nullptr) {
        Serial.println("❌ FAILED");
        client->disconnect();
        return;
    }
    Serial.println("✓");
    
    Serial.print("  [4/6] Getting RX characteristic... ");
    Serial.flush();
    parent->dice[slot].rxChar = service->getCharacteristic(GODICE_CHAR_RX_UUID);
    if (parent->dice[slot].rxChar == nullptr) {
        Serial.println("❌ FAILED");
        client->disconnect();
        return;
    }
    Serial.println("✓");
    
    Serial.print("  [5/6] Registering for notifications... ");
    Serial.flush();
    if (parent->dice[slot].txChar->canNotify()) {
        auto notifyCallbacks = new DiceNotifyCallbacks();
        notifyCallbacks->parent = parent;
        notifyCallbacks->slot = slot;
        
        parent->dice[slot].notifyCallback = (void*)notifyCallbacks;
        
        parent->dice[slot].txChar->registerForNotify(
            [](BLERemoteCharacteristic* chr, uint8_t* data, size_t length, bool isNotify) {
                if (g_clientInstance) {
                    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
                        if (g_clientInstance->dice[i].txChar == chr && g_clientInstance->dice[i].notifyCallback) {
                            DiceNotifyCallbacks* cb = (DiceNotifyCallbacks*)g_clientInstance->dice[i].notifyCallback;
                            cb->onNotify(chr, data, length, isNotify);
                            break;
                        }
                    }
                }
            }
        );
        Serial.println("✓");
    } else {
        Serial.println("⚠ Characteristic cannot notify");
    }
    
    parent->dice[slot].connected = true;
    Serial.println("  [6/6] ✓ Connection fully established\n");
    
    Serial.println("  Sending initialization packet...");
    parent->sendInitPacket(slot);
    
    delay(100);
    Serial.println("  Requesting die color...");
    parent->requestColor(slot);
    
    delay(100);
    Serial.println("  Requesting battery level...");
    parent->requestBattery(slot);
    
    Serial.println("\n┌─────────────────────────────────────────────────────┐");
    Serial.printf("│ ✓ GoDice slot %d READY                              │\n", slot);
    Serial.println("└─────────────────────────────────────────────────────┘\n");
    
    if (parent->eventHandler) {
        parent->eventHandler->onDiceConnected(
            slot,
            parent->dice[slot].address,
            parent->dice[slot].name
        );
    }
}

void GoDiceBLEClient::DiceClientCallbacks::onDisconnect(BLEClient* client) {
    Serial.printf("GoDice slot %d disconnected\n", slot);
    parent->dice[slot].connected = false;
    parent->dice[slot].initialized = false;
    
    if (parent->eventHandler) {
        parent->eventHandler->onDiceDisconnected(slot);
    }
}

void GoDiceBLEClient::DiceNotifyCallbacks::onNotify(
    BLERemoteCharacteristic* chr,
    uint8_t* data,
    size_t length,
    bool isNotify
) {
    // Parse incoming packet using godiceapi.c
    godice_incoming_packet(
        &parent->callbacks,
        parent,
        slot,
        6,  // D6 die (change if using other die types)
        data,
        length
    );
}

// ==================== SCAN CALLBACK ====================

// Pending connection info (stored before scan stops to avoid invalid pointers)
struct PendingConnection {
    bool valid;
    String address;
    String name;
    uint8_t addrType;
};
static PendingConnection g_pendingConn = {false, "", "", BLE_ADDR_RANDOM};

// Track seen devices to avoid duplicate prints
static std::map<std::string, unsigned long> g_seenDevices;

class GoDiceScanCallback : public BLEAdvertisedDeviceCallbacks {
public:
    GoDiceBLEClient* parent;
    
    void onResult(BLEAdvertisedDevice advertisedDevice) {
        std::string addr = advertisedDevice.getAddress().toString().c_str();
        unsigned long now = millis();
        
        // Rate limit: only print each device once per 5 seconds
        if (g_seenDevices.find(addr) != g_seenDevices.end()) {
            if (now - g_seenDevices[addr] < 5000) {
                // Check for GoDice silently
                if (advertisedDevice.haveServiceUUID() &&
                    advertisedDevice.isAdvertisingService(BLEUUID(GODICE_SERVICE_UUID))) {
                    goto found_godice;
                }
                return;
            }
        }
        g_seenDevices[addr] = now;
        
        // Debug: Show all BLE devices found
        Serial.printf("BLE Device: %s (%s) RSSI: %d addrType: %d\n",
            advertisedDevice.getName().c_str(),
            advertisedDevice.getAddress().toString().c_str(),
            advertisedDevice.getRSSI(),
            advertisedDevice.getAddressType());
        
        // Check if device has GoDice service
        if (advertisedDevice.haveServiceUUID() &&
            advertisedDevice.isAdvertisingService(BLEUUID(GODICE_SERVICE_UUID))) {
            
found_godice:
            Serial.printf("✓ Found GoDice: %s (%s) RSSI: %d addrType: %d\n",
                advertisedDevice.getName().c_str(),
                advertisedDevice.getAddress().toString().c_str(),
                advertisedDevice.getRSSI(),
                advertisedDevice.getAddressType());
            
            // Check if already connected
            String address = advertisedDevice.getAddress().toString().c_str();
            if (parent->findSlotByAddress(address) >= 0) {
                Serial.println("Already connected to this die, skipping");
                return;  // Already connected
            }
            
            // Store connection info BEFORE stopping scan (avoids dangling pointers)
            g_pendingConn.valid = true;
            g_pendingConn.address = address;
            g_pendingConn.name = advertisedDevice.getName().c_str();
            g_pendingConn.addrType = advertisedDevice.getAddressType();
            
            Serial.printf("Stored pending connection: %s (%s) addrType=%d\n",
                g_pendingConn.name.c_str(), g_pendingConn.address.c_str(), g_pendingConn.addrType);
            
            // Stop scan - will trigger connection in main loop
            parent->stopScan();
        }
    }
};

// ==================== GODICE CLIENT IMPLEMENTATION ====================

GoDiceBLEClient::GoDiceBLEClient() {
    scanning = false;
    eventHandler = nullptr;
    
    // Initialize dice slots
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        dice[i].slot = i;
        dice[i].client = nullptr;
        dice[i].txChar = nullptr;
        dice[i].rxChar = nullptr;
        dice[i].connected = false;
        dice[i].initialized = false;
        dice[i].shellColor = GODICE_BLACK;
        dice[i].batteryLevel = 0;
        dice[i].charging = false;
        dice[i].lastRoll = 0;
        dice[i].rolling = false;
        dice[i].lastSeen = 0;
    }
    
    // Setup godiceapi callbacks
    callbacks.on_dice_color = godice_color_callback;
    callbacks.on_dice_stable = godice_stable_callback;
    callbacks.on_dice_roll = godice_roll_callback;
    callbacks.on_charge_level = godice_battery_callback;
    callbacks.on_charging_state_changed = godice_charging_callback;
}

void GoDiceBLEClient::begin(const char* deviceName) {
    Serial.println("Initializing GoDice BLE Client...");
    
    // Set global instance for BLE callbacks
    g_clientInstance = this;
    
    BLEDevice::init(deviceName);
    
    // Create scan object
    scan = BLEDevice::getScan();
    scan->setInterval(100);   // Faster scanning
    scan->setWindow(99);      // Almost continuous
    scan->setActiveScan(true);
    scan->setDuplicateFilter(false);  // Show all devices including duplicates
    
    auto scanCallback = new GoDiceScanCallback();
    scanCallback->parent = this;
    scan->setAdvertisedDeviceCallbacks(scanCallback, true);  // true = want duplicates
    
    Serial.println("GoDice BLE Client ready");
}

void GoDiceBLEClient::setEventHandler(GoDiceEventHandler* handler) {
    eventHandler = handler;
}

int GoDiceBLEClient::findFreeSlot() {
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        if (!dice[i].connected) {
            return i;
        }
    }
    return -1;
}

int GoDiceBLEClient::findSlotByAddress(String address) {
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        if (dice[i].connected && dice[i].address == address) {
            return i;
        }
    }
    return -1;
}

void GoDiceBLEClient::startScan() {
    if (scanning) {
        return;
    }
    
    Serial.println("Scanning for GoDice...");
    scanning = true;
    scan->start(GODICE_SCAN_DURATION, false);
}

void GoDiceBLEClient::stopScan() {
    if (!scanning) {
        return;
    }
    
    Serial.println("Stopping scan");
    scan->stop();
    scanning = false;
}

bool GoDiceBLEClient::connectToDice(String address) {
    int slot = findFreeSlot();
    if (slot < 0) {
        Serial.println("No free slots for new dice");
        return false;
    }
    
    Serial.printf("Connecting to dice at %s in slot %d...\n", address.c_str(), slot);
    
    // Create BLE client
    dice[slot].client = BLEDevice::createClient();
    dice[slot].address = address;
    
    Serial.println("Setting callbacks...");
    // Set callbacks
    auto clientCallbacks = new DiceClientCallbacks();
    clientCallbacks->parent = this;
    clientCallbacks->slot = slot;
    dice[slot].client->setClientCallbacks(clientCallbacks);
    
    // Connect
    Serial.println("Attempting BLE connection...");
    Serial.println("(This may take 10-30 seconds...)");
    // GoDice advertises with a random static address, so try RANDOM first then PUBLIC as fallback
    Serial.printf("Using address (random): %s\n", address.c_str());
    BLEAddress bleAddressRandom(address.c_str(), BLE_ADDR_RANDOM);
    BLEAddress bleAddressPublic(address.c_str(), BLE_ADDR_PUBLIC);
    
    // Try to connect - false = no encryption required
    bool connected = dice[slot].client->connect(bleAddressRandom, false);
    if (!connected) {
        Serial.println("Random connect failed, retrying with PUBLIC address type...");
        delay(200);
        connected = dice[slot].client->connect(bleAddressPublic, false);
    }
    
    if (!connected) {
        Serial.printf("❌ Connection failed to %s\n", address.c_str());
        Serial.println("   Try:");
        Serial.println("   1. Shake die vigorously to wake it");
        Serial.println("   2. Move die closer to ESP32");
        Serial.println("   3. Press 's' to scan again");
        delete dice[slot].client;
        dice[slot].client = nullptr;
        return false;
    }
    
    Serial.println("✓ BLE connected, waiting for service discovery...");
    dice[slot].name = dice[slot].client->getPeerAddress().toString().c_str();
    return true;
}

// Overload: connect using advertised device to preserve address type
bool GoDiceBLEClient::connectToDice(BLEAdvertisedDevice advertisedDevice) {
    String address = advertisedDevice.getAddress().toString().c_str();
    String name = advertisedDevice.getName().c_str();
    Serial.printf("Connecting (adv) to %s (%s)\n", name.c_str(), address.c_str());

    int slot = findFreeSlot();
    if (slot < 0) {
        Serial.println("No free slots for new dice");
        return false;
    }

    // Create BLE client
    dice[slot].client = BLEDevice::createClient();
    dice[slot].address = address;
    dice[slot].name = name;

    Serial.println("Setting callbacks...");
    auto clientCallbacks = new DiceClientCallbacks();
    clientCallbacks->parent = this;
    clientCallbacks->slot = slot;
    dice[slot].client->setClientCallbacks(clientCallbacks);

    Serial.println("Attempting BLE connection using advertised device (preserves address type)...");
    Serial.println("(This may take 10-30 seconds...)");

    // Try connecting using advertised device (keeps address type)
    bool connected = dice[slot].client->connect(&advertisedDevice);

    if (!connected) {
        Serial.printf("❌ Connection failed to %s (adv)\n", address.c_str());
        Serial.println("   Try:");
        Serial.println("   1. Shake die vigorously to wake it");
        Serial.println("   2. Move die closer to ESP32");
        Serial.println("   3. Press 's' to scan again");
        delete dice[slot].client;
        dice[slot].client = nullptr;
        return false;
    }

    Serial.println("✓ BLE connected (adv), waiting for service discovery...");
    return true;
}

// Connect using explicit address type (most reliable method)
bool GoDiceBLEClient::connectToDiceWithType(String address, String name, uint8_t addrType) {
    Serial.println("\n╔════════════════════════════════════════════════════╗");
    Serial.println("║       CONNECTION ATTEMPT - DETAILED LOG           ║");
    Serial.println("╚════════════════════════════════════════════════════╝");
    Serial.printf("→ Target Die: %s\n", name.c_str());
    Serial.printf("→ MAC Address: %s\n", address.c_str());
    Serial.printf("→ Address Type: %d (0=PUBLIC, 1=RANDOM)\n\n", addrType);

    Serial.print("STEP 1: Finding free connection slot... ");
    int slot = findFreeSlot();
    if (slot < 0) {
        Serial.println("❌ FAILED - No slots available");
        return false;
    }
    Serial.printf("✓ Slot %d allocated\n\n", slot);

    Serial.print("STEP 2: Creating BLE client object... ");
    dice[slot].client = BLEDevice::createClient();
    if (!dice[slot].client) {
        Serial.println("❌ FAILED - createClient() returned NULL");
        return false;
    }
    Serial.printf("✓ Client created at 0x%p\n\n", dice[slot].client);
    
    dice[slot].address = address;
    dice[slot].name = name;

    Serial.print("STEP 3: Registering connection callbacks... ");
    auto clientCallbacks = new DiceClientCallbacks();
    clientCallbacks->parent = this;
    clientCallbacks->slot = slot;
    dice[slot].client->setClientCallbacks(clientCallbacks);
    Serial.println("✓ Callbacks registered\n");

    Serial.println("STEP 4: Creating BLEAddress object...");
    Serial.printf("       Input: %s, Type: %d\n", address.c_str(), addrType);
    BLEAddress bleAddr(address.c_str(), addrType);
    Serial.printf("       Output: %s, Type: %d\n", bleAddr.toString().c_str(), addrType);
    Serial.println("       ✓ BLEAddress created\n");

    Serial.println("STEP 5: Initiating BLE connection...");
    Serial.println("       This calls: client->connect(bleAddr, type, timeout)");
    Serial.println("       Timeout: 15000ms per attempt");
    Serial.println("       Max attempts: 3\n");
    
    bool connected = false;
    for (int attempt = 1; attempt <= 3 && !connected; attempt++) {
        Serial.printf("       → Attempt %d/3 starting... ", attempt);
        Serial.flush();
        
        unsigned long startTime = millis();
        connected = dice[slot].client->connect(bleAddr, addrType, 15000);
        unsigned long elapsed = millis() - startTime;
        
        if (connected) {
            Serial.printf("✓ SUCCESS in %lu ms\n", elapsed);
        } else {
            Serial.printf("❌ FAILED after %lu ms\n", elapsed);
            if (attempt < 3) {
                Serial.println("         Waiting 2 seconds before retry...");
                delay(2000);
            }
        }
    }

    if (!connected) {
        Serial.println("\n⚠ PRIMARY CONNECTION FAILED");
        Serial.println("STEP 6: Trying alternate address type...");
        
        uint8_t altType = (addrType == BLE_ADDR_RANDOM) ? BLE_ADDR_PUBLIC : BLE_ADDR_RANDOM;
        Serial.printf("       Original: %d → Alternate: %d\n", addrType, altType);
        Serial.println("       Waiting 2 seconds...");
        delay(2000);
        
        Serial.print("       Creating alternate BLEAddress... ");
        BLEAddress bleAddrAlt(address.c_str(), altType);
        Serial.printf("✓ %s (type %d)\n", bleAddrAlt.toString().c_str(), altType);
        
        Serial.print("       Calling connect()... ");
        Serial.flush();
        
        unsigned long startTime = millis();
        connected = dice[slot].client->connect(bleAddrAlt, altType, 15000);
        unsigned long elapsed = millis() - startTime;
        
        if (connected) {
            Serial.printf("✓ SUCCESS in %lu ms\n", elapsed);
        } else {
            Serial.printf("❌ FAILED after %lu ms\n", elapsed);
        }
        
        if (!connected) {
            Serial.println("\n╔════════════════════════════════════════════════════╗");
            Serial.println("║          CONNECTION FAILED (BOTH ATTEMPTS)         ║");
            Serial.println("╚════════════════════════════════════════════════════╝");
            Serial.println("Possible causes:");
            Serial.println("  1. Die went to sleep → Shake vigorously");
            Serial.println("  2. Weak signal → Move die closer to ESP32");
            Serial.println("  3. Die connected elsewhere → Disconnect other device");
            Serial.println("  4. ESP32 BLE stack bug → Known issue with NimBLE\n");
            delete dice[slot].client;
            dice[slot].client = nullptr;
            return false;
        }
    }

    Serial.println("\n╔════════════════════════════════════════════════════╗");
    Serial.println("║         CONNECTION SUCCESSFUL - WAITING FOR        ║");
    Serial.println("║            SERVICE DISCOVERY CALLBACK              ║");
    Serial.println("╚════════════════════════════════════════════════════╝\n");
    return true;
}

void GoDiceBLEClient::disconnectDice(int slot) {
    if (slot < 0 || slot >= MAX_GODICE_CONNECTIONS) {
        return;
    }
    
    if (dice[slot].connected && dice[slot].client) {
        dice[slot].client->disconnect();
    }
}

int GoDiceBLEClient::getConnectedCount() {
    int count = 0;
    for (int i = 0; i < MAX_GODICE_CONNECTIONS; i++) {
        if (dice[i].connected) {
            count++;
        }
    }
    return count;
}

GoDiceInfo* GoDiceBLEClient::getDiceInfo(int slot) {
    if (slot < 0 || slot >= MAX_GODICE_CONNECTIONS) {
        return nullptr;
    }
    return &dice[slot];
}

bool GoDiceBLEClient::isConnected(int slot) {
    if (slot < 0 || slot >= MAX_GODICE_CONNECTIONS) {
        return false;
    }
    return dice[slot].connected;
}

void GoDiceBLEClient::sendInitPacket(int slot) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_INIT_PACKET_SIZE];
    size_t written;
    
    godice_toggle_leds_t ledConfig = {
        .number_of_blinks = 3,
        .light_on_duration_10ms = 50,   // 0.5 seconds
        .light_off_duration_10ms = 50,
        .color_red = 0,
        .color_green = 255,
        .color_blue = 0,
        .blink_mode = GODICE_BLINK_PARALLEL,
        .leds = GODICE_LEDS_BOTH
    };
    
    if (godice_init_packet(buffer, sizeof(buffer), &written,
                          GODICE_SENSITIVITY_DEFAULT, &ledConfig) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
        dice[slot].initialized = true;
        Serial.printf("Sent init packet to slot %d\n", slot);
    }
}

void GoDiceBLEClient::requestColor(int slot) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_GET_COLOR_PACKET_SIZE];
    size_t written;
    
    if (godice_get_color_packet(buffer, sizeof(buffer), &written) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::requestBattery(int slot) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_GET_CHARGE_LEVEL_PACKET_SIZE];
    size_t written;
    
    if (godice_get_charge_level_packet(buffer, sizeof(buffer), &written) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::setLEDColors(int slot, uint8_t r1, uint8_t g1, uint8_t b1,
                                              uint8_t r2, uint8_t g2, uint8_t b2) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_OPEN_LEDS_PACKET_SIZE];
    size_t written;
    
    if (godice_open_leds_packet(buffer, sizeof(buffer), &written,
                                r1, g1, b1, r2, g2, b2) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::blinkLEDs(int slot, uint8_t blinks, uint8_t onTime, uint8_t offTime,
                                uint8_t red, uint8_t green, uint8_t blue) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_TOGGLE_LEDS_PACKET_SIZE];
    size_t written;
    
    godice_toggle_leds_t ledConfig = {
        .number_of_blinks = blinks,
        .light_on_duration_10ms = onTime,
        .light_off_duration_10ms = offTime,
        .color_red = red,
        .color_green = green,
        .color_blue = blue,
        .blink_mode = GODICE_BLINK_PARALLEL,
        .leds = GODICE_LEDS_BOTH
    };
    
    if (godice_toggle_leds_packet(buffer, sizeof(buffer), &written, &ledConfig) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::turnOffLEDs(int slot) {
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_CLOSE_TOGGLE_LEDS_PACKET_SIZE];
    size_t written;
    
    if (godice_close_toggle_leds_packet(buffer, sizeof(buffer), &written) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::updateDetectionSettings(int slot,
    uint8_t samplesCount, uint8_t movementCount, uint8_t faceCount,
    uint8_t minFlatDeg, uint8_t maxFlatDeg, uint8_t weakStable,
    uint8_t movementDeg, uint8_t rollThreshold)
{
    if (!isConnected(slot)) {
        return;
    }
    
    uint8_t buffer[GODICE_DETECTION_SETTINGS_UPDATE_PACKET_SIZE];
    size_t written;
    
    if (godice_detection_settings_update_packet(buffer, sizeof(buffer), &written,
            samplesCount, movementCount, faceCount, minFlatDeg, maxFlatDeg,
            weakStable, movementDeg, rollThreshold) == GODICE_OK) {
        dice[slot].rxChar->writeValue(buffer, written, false);
    }
}

void GoDiceBLEClient::update() {
    // Check for pending connection from scan callback
    if (g_pendingConn.valid) {
        g_pendingConn.valid = false;
        
        Serial.printf("Processing pending connection to %s (%s)...\n",
            g_pendingConn.name.c_str(), g_pendingConn.address.c_str());
        
        // Ensure scan is completely stopped
        if (scanning) {
            Serial.println("Stopping scan explicitly...");
            scan->stop();
            scanning = false;
        }
        
        // Longer delay after scan stop for BLE stack stability
        // The ESP32 BLE stack needs time to fully release scan resources
        Serial.println("Waiting for BLE stack to settle (1 second)...");
        delay(1000);
        
        // Connect using stored address and type
        connectToDiceWithType(g_pendingConn.address, g_pendingConn.name, g_pendingConn.addrType);
    }
}
