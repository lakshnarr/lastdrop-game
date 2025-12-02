/*
 * Last Drop - ESP32 Physical Board Controller (BLE Version)
 * 
 * Hardware:
 * - ESP32 Dev Board
 * - WS2812B LED Strip (80 LEDs = 4 LEDs per tile × 20 tiles)
 * - 20 Hall Effect Sensors (A3144) for coin detection
 * 
 * Communication: Bluetooth BLE (not WiFi)
 * - Allows phone to maintain WiFi for internet connection
 * - No conflict with GoDice BLE connections
 * 
 * BLE Service: LASTDROP Board Control
 * UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e (Nordic UART Service compatible)
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Adafruit_NeoPixel.h>
#include <Preferences.h>
#include <ArduinoJson.h>

// ==================== HARDWARE CONFIGURATION ====================
#define LED_PIN 5
#define NUM_LEDS 80
#define LEDS_PER_TILE 4
#define NUM_TILES 20
#define NUM_PLAYERS 4

// Hall sensor pins (GPIO 12-31, adjust based on your wiring)
const int hallPins[NUM_TILES] = {
  12, 13, 14, 15, 16, 17, 18, 19, 21, 22,
  23, 25, 26, 27, 32, 33, 34, 35, 36, 39
};

// ==================== BLE CONFIGURATION ====================
#define SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"  // Android → ESP32
#define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"  // ESP32 → Android

#define DEVICE_NAME "LASTDROP-ESP32"

// ==================== LED COLORS ====================
const uint32_t TILE_COLORS[NUM_TILES] = {
  0x2E8B57, 0x3CB371, 0x90EE90, 0x98FB98, 0xADFF2F,  // Tiles 0-4 (Green shades)
  0xFFFF00, 0xFFD700, 0xFFA500, 0xFF8C00, 0xFF6347,  // Tiles 5-9 (Yellow to Orange)
  0xFF4500, 0xFF0000, 0xDC143C, 0xB22222, 0x8B0000,  // Tiles 10-14 (Red shades)
  0x800080, 0x9932CC, 0xBA55D3, 0xDA70D6, 0xEE82EE   // Tiles 15-19 (Purple shades)
};

const uint32_t PLAYER_COLORS[NUM_PLAYERS] = {
  0xFF0000,  // Player 0: Red
  0x0000FF,  // Player 1: Blue
  0x00FF00,  // Player 2: Green
  0xFFFF00   // Player 3: Yellow
};

// ==================== GLOBAL OBJECTS ====================
Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);
Preferences preferences;

BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// ==================== GAME STATE ====================
struct PlayerState {
  int currentTile;
  int waterLevel;
  bool alive;
  bool coinPlaced;
  uint32_t color;
};

PlayerState players[NUM_PLAYERS];
int currentPlayer = -1;
int expectedTile = -1;
bool waitingForCoin = false;
unsigned long coinWaitStartTime = 0;
const unsigned long COIN_TIMEOUT = 30000; // 30 seconds

// ==================== LED CONTROL ====================
bool blinkState = false;
unsigned long lastBlinkTime = 0;
const unsigned long BLINK_INTERVAL = 500;

unsigned long lastScanTime = 0;
const unsigned long SCAN_INTERVAL = 5000; // Scan all sensors every 5 seconds

// ==================== BLE CALLBACKS ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("BLE Client Connected");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("BLE Client Disconnected");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();

      if (rxValue.length() > 0) {
        Serial.println("Received BLE data:");
        Serial.println(rxValue.c_str());
        
        // Parse JSON command
        handleBLECommand(rxValue.c_str());
      }
    }
};

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  Serial.println("Last Drop ESP32 (BLE) Starting...");

  // Initialize LED strip
  strip.begin();
  strip.setBrightness(100);
  strip.show();

  // Initialize Hall sensors
  for (int i = 0; i < NUM_TILES; i++) {
    pinMode(hallPins[i], INPUT);
  }

  // Initialize preferences
  preferences.begin("lastdrop", false);
  loadGameState();

  // Initialize BLE
  initBLE();

  // Show startup animation
  startupAnimation();

  Serial.println("System Ready - Waiting for BLE connection...");
}

// ==================== BLE INITIALIZATION ====================
void initBLE() {
  // Create the BLE Device
  BLEDevice::init(DEVICE_NAME);

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create BLE Characteristic for TX (ESP32 → Android)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // Create BLE Characteristic for RX (Android → ESP32)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  
  Serial.println("BLE Service started - Device name: LASTDROP-ESP32");
}

// ==================== BLE COMMAND HANDLER ====================
void handleBLECommand(const char* jsonStr) {
  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, jsonStr);

  if (error) {
    Serial.print("JSON parse error: ");
    Serial.println(error.c_str());
    sendBLEResponse("{\"status\":\"error\",\"message\":\"Invalid JSON\"}");
    return;
  }

  const char* command = doc["command"];
  
  if (strcmp(command, "roll") == 0) {
    handleRoll(doc);
  } else if (strcmp(command, "undo") == 0) {
    handleUndo(doc);
  } else if (strcmp(command, "reset") == 0) {
    handleReset();
  } else if (strcmp(command, "status") == 0) {
    sendStatus();
  } else {
    sendBLEResponse("{\"status\":\"error\",\"message\":\"Unknown command\"}");
  }
}

// ==================== HANDLE ROLL ====================
void handleRoll(JsonDocument& doc) {
  int playerId = doc["playerId"];
  int diceValue = doc["diceValue"];
  int currentTile = doc["currentTile"];
  int targetTile = doc["expectedTile"];

  if (playerId < 0 || playerId >= NUM_PLAYERS) {
    sendBLEResponse("{\"status\":\"error\",\"message\":\"Invalid player ID\"}");
    return;
  }

  currentPlayer = playerId;
  expectedTile = targetTile;
  
  // Update player state
  players[playerId].currentTile = currentTile;
  
  // Animate movement
  animateMove(currentTile, targetTile, PLAYER_COLORS[playerId]);
  
  // Start blinking at target tile
  waitingForCoin = true;
  coinWaitStartTime = millis();
  
  // Save state
  saveGameState();
  
  Serial.printf("Roll: Player %d moved from tile %d to %d\n", playerId, currentTile, targetTile);
  
  sendBLEResponse("{\"status\":\"ok\",\"blinking\":true,\"message\":\"Waiting for coin placement\"}");
}

// ==================== HANDLE UNDO ====================
void handleUndo(JsonDocument& doc) {
  int playerId = doc["playerId"];
  int fromTile = doc["fromTile"];
  int toTile = doc["toTile"];

  if (playerId < 0 || playerId >= NUM_PLAYERS) {
    sendBLEResponse("{\"status\":\"error\",\"message\":\"Invalid player ID\"}");
    return;
  }

  currentPlayer = playerId;
  expectedTile = toTile;
  
  // Reverse animation
  animateMove(fromTile, toTile, PLAYER_COLORS[playerId]);
  
  // Update state
  players[playerId].currentTile = toTile;
  players[playerId].coinPlaced = false;
  
  // Start blinking at new position
  waitingForCoin = true;
  coinWaitStartTime = millis();
  
  saveGameState();
  
  Serial.printf("Undo: Player %d moved from tile %d back to %d\n", playerId, fromTile, toTile);
  
  sendBLEResponse("{\"status\":\"ok\",\"blinking\":true,\"message\":\"Undo complete, waiting for coin\"}");
}

// ==================== HANDLE RESET ====================
void handleReset() {
  // Clear all player states
  for (int i = 0; i < NUM_PLAYERS; i++) {
    players[i].currentTile = -1;
    players[i].waterLevel = 10;
    players[i].alive = true;
    players[i].coinPlaced = false;
    players[i].color = PLAYER_COLORS[i];
  }
  
  currentPlayer = -1;
  expectedTile = -1;
  waitingForCoin = false;
  
  // Clear all LEDs
  strip.clear();
  strip.show();
  
  // Redraw background
  renderBackground();
  
  saveGameState();
  
  Serial.println("Game reset");
  
  sendBLEResponse("{\"status\":\"ok\",\"message\":\"Game reset complete\"}");
}

// ==================== SEND STATUS ====================
void sendStatus() {
  StaticJsonDocument<1024> doc;
  doc["status"] = "ok";
  doc["connected"] = deviceConnected;
  doc["waitingForCoin"] = waitingForCoin;
  doc["currentPlayer"] = currentPlayer;
  doc["expectedTile"] = expectedTile;
  
  JsonArray playersArray = doc.createNestedArray("players");
  for (int i = 0; i < NUM_PLAYERS; i++) {
    JsonObject player = playersArray.createNestedObject();
    player["id"] = i;
    player["tile"] = players[i].currentTile;
    player["water"] = players[i].waterLevel;
    player["alive"] = players[i].alive;
    player["coinPlaced"] = players[i].coinPlaced;
  }
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== LED ANIMATION ====================
void animateMove(int fromTile, int toTile, uint32_t color) {
  int step = (toTile > fromTile) ? 1 : -1;
  
  for (int tile = fromTile; tile != toTile + step; tile += step) {
    if (tile < 0 || tile >= NUM_TILES) continue;
    
    // Clear previous position
    if (tile != fromTile) {
      setTileColor(tile - step, TILE_COLORS[tile - step]);
    }
    
    // Light up current position
    setTileColor(tile, color);
    strip.show();
    
    delay(200); // Animation speed
  }
}

// ==================== TILE RENDERING ====================
void setTileColor(int tile, uint32_t color) {
  if (tile < 0 || tile >= NUM_TILES) return;
  
  int startLED = tile * LEDS_PER_TILE;
  for (int i = 0; i < LEDS_PER_TILE; i++) {
    strip.setPixelColor(startLED + i, color);
  }
}

void renderBackground() {
  for (int tile = 0; tile < NUM_TILES; tile++) {
    setTileColor(tile, TILE_COLORS[tile]);
  }
  strip.show();
}

void renderPlayers() {
  // Start with background
  renderBackground();
  
  // Overlay players
  for (int i = 0; i < NUM_PLAYERS; i++) {
    if (players[i].alive && players[i].currentTile >= 0 && players[i].coinPlaced) {
      setTileColor(players[i].currentTile, players[i].color);
    }
  }
  
  strip.show();
}

// ==================== COIN DETECTION ====================
bool isCoinPresent(int tile) {
  if (tile < 0 || tile >= NUM_TILES) return false;
  
  // Hall sensor reads LOW when magnet is near
  // Read multiple times for debouncing
  int readings = 0;
  for (int i = 0; i < 5; i++) {
    if (digitalRead(hallPins[tile]) == LOW) {
      readings++;
    }
    delay(2);
  }
  
  return readings >= 3; // At least 3 out of 5 reads must be LOW
}

void checkCoinPlacement() {
  if (!waitingForCoin || currentPlayer < 0 || expectedTile < 0) return;
  
  if (isCoinPresent(expectedTile)) {
    // Coin detected!
    players[currentPlayer].coinPlaced = true;
    players[currentPlayer].currentTile = expectedTile;
    waitingForCoin = false;
    
    // Stop blinking, show solid color
    setTileColor(expectedTile, PLAYER_COLORS[currentPlayer]);
    strip.show();
    
    saveGameState();
    
    // Notify Android
    StaticJsonDocument<256> doc;
    doc["event"] = "coin_placed";
    doc["playerId"] = currentPlayer;
    doc["tile"] = expectedTile;
    doc["verified"] = true;
    
    String response;
    serializeJson(doc, response);
    sendBLEResponse(response.c_str());
    
    Serial.printf("Coin placed: Player %d at tile %d\n", currentPlayer, expectedTile);
    
    currentPlayer = -1;
    expectedTile = -1;
  }
}

void checkCoinTimeout() {
  if (!waitingForCoin) return;
  
  if (millis() - coinWaitStartTime > COIN_TIMEOUT) {
    // Timeout!
    Serial.println("Coin placement timeout!");
    
    waitingForCoin = false;
    
    // Notify Android
    StaticJsonDocument<256> doc;
    doc["event"] = "coin_timeout";
    doc["playerId"] = currentPlayer;
    doc["tile"] = expectedTile;
    
    String response;
    serializeJson(doc, response);
    sendBLEResponse(response.c_str());
    
    // Clear blinking
    renderPlayers();
    
    currentPlayer = -1;
    expectedTile = -1;
  }
}

// ==================== MISPLACEMENT DETECTION ====================
void scanAllTiles() {
  if (waitingForCoin) return; // Don't scan while waiting for specific placement
  
  bool foundMisplacement = false;
  StaticJsonDocument<1024> doc;
  doc["event"] = "misplacement";
  JsonArray errors = doc.createNestedArray("errors");
  
  for (int tile = 0; tile < NUM_TILES; tile++) {
    bool coinPresent = isCoinPresent(tile);
    bool shouldBePresent = false;
    int expectedPlayer = -1;
    
    // Check if any player should be on this tile
    for (int p = 0; p < NUM_PLAYERS; p++) {
      if (players[p].alive && players[p].coinPlaced && players[p].currentTile == tile) {
        shouldBePresent = true;
        expectedPlayer = p;
        break;
      }
    }
    
    if (coinPresent && !shouldBePresent) {
      // Coin where it shouldn't be
      foundMisplacement = true;
      JsonObject error = errors.createNestedObject();
      error["tile"] = tile;
      error["issue"] = "unexpected_coin";
      
      // Blink red at this tile
      setTileColor(tile, 0xFF0000);
    } else if (!coinPresent && shouldBePresent) {
      // Missing coin
      foundMisplacement = true;
      JsonObject error = errors.createNestedObject();
      error["tile"] = tile;
      error["playerId"] = expectedPlayer;
      error["issue"] = "missing_coin";
      
      // Blink red at this tile
      setTileColor(tile, 0xFF0000);
    }
  }
  
  if (foundMisplacement) {
    strip.show();
    delay(500);
    renderPlayers(); // Restore normal view
    
    // Send notification
    String response;
    serializeJson(doc, response);
    sendBLEResponse(response.c_str());
    
    Serial.println("Misplacement detected!");
  }
}

// ==================== BLE COMMUNICATION ====================
void sendBLEResponse(const char* json) {
  if (deviceConnected) {
    pTxCharacteristic->setValue((uint8_t*)json, strlen(json));
    pTxCharacteristic->notify();
    Serial.print("Sent: ");
    Serial.println(json);
  }
}

// ==================== PERSISTENCE ====================
void saveGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    String prefix = "p" + String(i) + "_";
    preferences.putInt((prefix + "tile").c_str(), players[i].currentTile);
    preferences.putInt((prefix + "water").c_str(), players[i].waterLevel);
    preferences.putBool((prefix + "alive").c_str(), players[i].alive);
    preferences.putBool((prefix + "coin").c_str(), players[i].coinPlaced);
  }
}

void loadGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    String prefix = "p" + String(i) + "_";
    players[i].currentTile = preferences.getInt((prefix + "tile").c_str(), -1);
    players[i].waterLevel = preferences.getInt((prefix + "water").c_str(), 10);
    players[i].alive = preferences.getBool((prefix + "alive").c_str(), true);
    players[i].coinPlaced = preferences.getBool((prefix + "coin").c_str(), false);
    players[i].color = PLAYER_COLORS[i];
  }
}

// ==================== STARTUP ANIMATION ====================
void startupAnimation() {
  // Rainbow sweep
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.ColorHSV(i * 65536L / NUM_LEDS));
    strip.show();
    delay(10);
  }
  delay(500);
  
  // Fade to background
  renderBackground();
  
  // Show saved player positions
  renderPlayers();
}

// ==================== MAIN LOOP ====================
void loop() {
  // Handle BLE connection status
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    Serial.println("Start advertising");
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }
  
  // Handle blinking when waiting for coin
  if (waitingForCoin && expectedTile >= 0) {
    if (millis() - lastBlinkTime > BLINK_INTERVAL) {
      blinkState = !blinkState;
      lastBlinkTime = millis();
      
      if (blinkState) {
        setTileColor(expectedTile, PLAYER_COLORS[currentPlayer]);
      } else {
        setTileColor(expectedTile, 0x000000); // Off
      }
      strip.show();
    }
  }
  
  // Check for coin placement
  checkCoinPlacement();
  
  // Check for timeout
  checkCoinTimeout();
  
  // Periodic scan for misplacements
  if (millis() - lastScanTime > SCAN_INTERVAL) {
    scanAllTiles();
    lastScanTime = millis();
  }
  
  delay(10);
}
