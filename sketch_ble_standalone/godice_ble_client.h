/**
 * GoDice BLE Client - ESP32 Implementation
 * 
 * Handles BLE Central mode connection to GoDice smart dice
 * Integrates with godiceapi.c for protocol handling
 * 
 * Features:
 * - Scan for nearby GoDice
 * - Connect and pair with dice
 * - Monitor connection status
 * - Parse incoming notifications (rolls, battery, color)
 * - Send commands (LED control, queries)
 */

#ifndef GODICE_BLE_CLIENT_H
#define GODICE_BLE_CLIENT_H

#include <BLEDevice.h>
#include <BLEClient.h>
#include <BLEScan.h>
#include <BLEAdvertisedDevice.h>
#include "godiceapi.h"

// ==================== BLE SERVICE/CHARACTERISTIC UUIDS ====================

// Nordic UART Service (used by GoDice)
#define GODICE_SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define GODICE_CHAR_TX_UUID        "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"  // Die → ESP32 (notifications)
#define GODICE_CHAR_RX_UUID        "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"  // ESP32 → Die (write)

// ==================== CONFIGURATION ====================

#define MAX_GODICE_CONNECTIONS 2    // Support 2 dice simultaneously
#define GODICE_SCAN_DURATION 30     // Scan duration in seconds (longer to find sleepy dice)
#define GODICE_RECONNECT_DELAY 5000 // Reconnect attempt delay (ms)

// ==================== DICE CONNECTION INFO ====================

struct GoDiceInfo {
    int slot;                        // Slot index (0-1)
    BLEClient* client;               // BLE client instance
    BLERemoteCharacteristic* txChar; // Notification characteristic
    BLERemoteCharacteristic* rxChar; // Write characteristic
    bool connected;                  // Connection status
    bool initialized;                // Init packet sent
    String address;                  // MAC address
    String name;                     // Device name
    godice_color_t shellColor;       // Shell color (from die)
    uint8_t batteryLevel;            // Battery percentage (0-100)
    bool charging;                   // Charging status
    uint8_t lastRoll;                // Last stable roll value
    bool rolling;                    // Currently rolling
    unsigned long lastSeen;          // Last notification timestamp
    void* notifyCallback;            // DiceNotifyCallbacks pointer for BLE
};

// ==================== CALLBACK INTERFACE ====================

/**
 * User-defined callbacks for dice events
 * Implement these in your sketch to handle game logic
 */
class GoDiceEventHandler {
public:
    // Called when die is connected and ready
    virtual void onDiceConnected(int diceSlot, String address, String name) = 0;
    
    // Called when die disconnects
    virtual void onDiceDisconnected(int diceSlot) = 0;
    
    // Called when die shell color is detected
    virtual void onDiceColor(int diceSlot, godice_color_t color) = 0;
    
    // Called when die starts rolling
    virtual void onDiceRolling(int diceSlot) = 0;
    
    // Called when die settles on a face
    virtual void onDiceStable(int diceSlot, uint8_t value) = 0;
    
    // Called when battery level is received
    virtual void onDiceBattery(int diceSlot, uint8_t level) = 0;
    
    // Called when charging state changes
    virtual void onDiceCharging(int diceSlot, bool charging) = 0;
};

// ==================== MAIN GODICE CLIENT CLASS ====================

class GoDiceBLEClient {
public:
    GoDiceEventHandler* eventHandler;
    GoDiceInfo dice[MAX_GODICE_CONNECTIONS];  // Public for BLE callback access
    
private:
    BLEScan* scan;
    godice_callbacks_t callbacks;
    bool scanning;
    
    // BLE callbacks
    class DiceClientCallbacks : public BLEClientCallbacks {
    public:
        GoDiceBLEClient* parent;
        int slot;
        
        void onConnect(BLEClient* client);
        void onDisconnect(BLEClient* client);
    };
    
    class DiceNotifyCallbacks {
    public:
        GoDiceBLEClient* parent;
        int slot;
        
        void onNotify(BLERemoteCharacteristic* chr, uint8_t* data, size_t length, bool isNotify);
    };
    
    // Helper methods
    int findFreeSlot();
    void setupDiceCallbacks(int slot);
    void sendInitPacket(int slot);
    
public:
    int findSlotByAddress(String address);
    GoDiceBLEClient();
    
    /**
     * Initialize BLE client (call in setup())
     */
    void begin(const char* deviceName = "LastDrop-Board");
    
    /**
     * Set event handler for callbacks
     */
    void setEventHandler(GoDiceEventHandler* handler);
    
    /**
     * Start scanning for GoDice
     * Returns immediately, found dice trigger onDiceConnected
     */
    void startScan();
    
    /**
     * Stop scanning
     */
    void stopScan();
    
    /**
     * Check if currently scanning
     */
    bool isScanning() { return scanning; }
    
    /**
     * Connect to specific die by MAC address
     */
    bool connectToDice(String address);

    /**
     * Connect using advertised device (preserves address type)
     */
    bool connectToDice(BLEAdvertisedDevice advertisedDevice);

    /**
     * Connect using explicit address type (most reliable)
     */
    bool connectToDiceWithType(String address, String name, uint8_t addrType);
    
    /**
     * Disconnect specific die
     */
    void disconnectDice(int slot);
    
    /**
     * Get number of connected dice
     */
    int getConnectedCount();
    
    /**
     * Get dice info for specific slot
     */
    GoDiceInfo* getDiceInfo(int slot);
    
    /**
     * Check connection status for slot
     */
    bool isConnected(int slot);
    
    // ==================== DICE COMMANDS ====================
    
    /**
     * Request die shell color
     */
    void requestColor(int slot);
    
    /**
     * Request battery level
     */
    void requestBattery(int slot);
    
    /**
     * Set die LEDs to static colors
     */
    void setLEDColors(int slot, uint8_t r1, uint8_t g1, uint8_t b1, 
                                 uint8_t r2, uint8_t g2, uint8_t b2);
    
    /**
     * Start LED blinking pattern
     */
    void blinkLEDs(int slot, uint8_t blinks, uint8_t onTime, uint8_t offTime,
                   uint8_t red, uint8_t green, uint8_t blue);
    
    /**
     * Turn off LEDs
     */
    void turnOffLEDs(int slot);
    
    /**
     * Update detection settings (advanced)
     */
    void updateDetectionSettings(int slot,
        uint8_t samplesCount = GODICE_SAMPLES_COUNT_DEFAULT,
        uint8_t movementCount = GODICE_MOVEMENT_COUNT_DEFAULT,
        uint8_t faceCount = GODICE_FACE_COUNT_DEFAULT,
        uint8_t minFlatDeg = GODICE_MIN_FLAT_DEG_DEFAULT,
        uint8_t maxFlatDeg = GODICE_MAX_FLAT_DEG_DEFAULT,
        uint8_t weakStable = GODICE_WEAK_STABLE_DEFAULT,
        uint8_t movementDeg = GODICE_MOVEMENT_DEG_DEFAULT,
        uint8_t rollThreshold = GODICE_ROLL_THRESHOLD_DEFAULT
    );
    
    /**
     * Call in loop() for connection monitoring
     */
    void update();
};

#endif // GODICE_BLE_CLIENT_H
