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
#include <esp_task_wdt.h>

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

// ==================== SECURITY CONFIGURATION ====================
// BLE Pairing PIN (change this for your device!)
// Set to 0 to disable pairing (less secure, but easier setup)
#define BLE_PAIRING_ENABLED true
#define BLE_PAIRING_PIN 123456

// Trusted Android device addresses (optional - whitelist specific phones)
// Leave empty {} to accept connections from any device
// Example: {"AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"}
const char* TRUSTED_ANDROID_ADDRESSES[] = {};
const int TRUSTED_ANDROID_COUNT = 0;

// ==================== TEST MODE CONFIGURATION ====================
// Set to true to enable Test Mode 1 (full game logic on ESP32)
// Set to false for production mode (Android controls game logic)
#define TEST_MODE_ENABLED false

// ==================== GAME LOGIC (for Test Mode 1) ====================
#if TEST_MODE_ENABLED
enum TileType {
  TYPE_START = 0,
  TYPE_NORMAL = 1,
  TYPE_CHANCE = 2,
  TYPE_BONUS = 3,
  TYPE_PENALTY = 4,
  TYPE_DISASTER = 5,
  TYPE_WATER_DOCK = 6,
  TYPE_SUPER_DOCK = 7
};

struct TileDefinition {
  int index;           // 1-based (1 to 20)
  const char* name;
  TileType type;
};

// 20-Tile Board Definition (matching RULEBOOK.md)
const TileDefinition BOARD[NUM_TILES] = {
  {1,  "Start Point",          TYPE_START},
  {2,  "Sunny Patch",          TYPE_PENALTY},
  {3,  "Rain Dock",            TYPE_WATER_DOCK},
  {4,  "Leak Lane",            TYPE_PENALTY},
  {5,  "Storm Zone",           TYPE_DISASTER},
  {6,  "Cloud Hill",           TYPE_BONUS},
  {7,  "Oil Spill Bay",        TYPE_DISASTER},
  {8,  "Riverbank Road",       TYPE_NORMAL},
  {9,  "Marsh Land",           TYPE_CHANCE},
  {10, "Drought Desert",       TYPE_DISASTER},
  {11, "Clean Well",           TYPE_WATER_DOCK},
  {12, "Waste Dump",           TYPE_DISASTER},
  {13, "Sanctuary Stop",       TYPE_CHANCE},
  {14, "Sewage Drain Street",  TYPE_PENALTY},
  {15, "Filter Plant",         TYPE_WATER_DOCK},
  {16, "Mangrove Mile",        TYPE_CHANCE},
  {17, "Heatwave Road",        TYPE_PENALTY},
  {18, "Spring Fountain",      TYPE_SUPER_DOCK},
  {19, "Eco Garden",           TYPE_NORMAL},
  {20, "Great Reservoir",      TYPE_NORMAL}
};

struct ChanceCard {
  int number;
  const char* description;
  int effect;  // Positive or negative score change
};

// 20 Chance Cards (matching RULEBOOK.md Elimination Mode)
const ChanceCard CHANCE_CARDS[20] = {
  {1,  "You fixed a tap leak",                    +2},
  {2,  "Rainwater harvested",                     +2},
  {3,  "You planted two trees",                   +1},
  {4,  "Cool clouds formed",                      +1},
  {5,  "You cleaned a riverbank",                 +1},
  {6,  "Discovered a tiny spring",                +3},
  {7,  "You saved a wetland animal",              +1},
  {8,  "You reused RO water",                     +1},
  {9,  "Used bucket instead of shower",           +2},
  {10, "Drip irrigation success",                 +2},
  {11, "Skip next penalty",                        0},  // Special
  {12, "Move forward 2 tiles",                     0},  // Special
  {13, "Swap positions with next player",          0},  // Special
  {14, "Water Shield (next damage=0)",             0},  // Special
  {15, "You left tap running",                    -1},
  {16, "Your bottle spilled",                     -1},
  {17, "Pipe burst nearby",                       -3},
  {18, "Heat wave dries water",                   -2},
  {19, "Sewage contamination",                    -2},
  {20, "Flood washed away water",                 -3}
};
#endif

// ==================== PLAYER COLORS ====================
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
int activePlayerCount = NUM_PLAYERS;  // Number of players in current game (2-4)
int currentPlayer = -1;
int expectedTile = -1;
bool waitingForCoin = false;
unsigned long coinWaitStartTime = 0;
const unsigned long COIN_TIMEOUT = 60000; // 60 seconds (allows time for winner animation)

// Undo state (for Test Mode)
struct {
  bool hasUndo;
  int playerId;
  int fromTile;
  int toTile;
  int scoreChange;
  int chanceCardNumber;
} lastMove = {false, -1, 0, 0, 0, 0};

// ==================== LED CONTROL ====================
bool blinkState = false;
unsigned long lastBlinkTime = 0;
const unsigned long BLINK_INTERVAL = 500; // Normal blink (calm)
const unsigned long BLINK_INTERVAL_WARNING = 200; // Fast blink (10s remaining)
const unsigned long BLINK_INTERVAL_URGENT = 100; // Very fast blink (5s remaining)
const unsigned long WARNING_THRESHOLD = 10000; // 10 seconds
const unsigned long URGENT_THRESHOLD = 5000; // 5 seconds

unsigned long lastScanTime = 0;
const unsigned long SCAN_INTERVAL = 5000; // Scan all sensors every 5 seconds

unsigned long lastHeartbeatTime = 0;
const unsigned long HEARTBEAT_INTERVAL = 5000; // Send heartbeat every 5 seconds

// ==================== FUNCTION DECLARATIONS ====================
void handleBLECommand(const char* jsonStr);
void animatePlayerElimination(int playerId);
void animateWinner(int winnerId);

// ==================== BLE CALLBACKS ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      // Get connected client address
      std::string clientAddress = pServer->getConnId() >= 0 ? "connected" : "unknown";
      
      // Optional: Check if client is in whitelist
      if (TRUSTED_ANDROID_COUNT > 0) {
        // Note: BLE Server API doesn't expose peer address directly
        // For production, implement custom authentication via first message
        Serial.println("WARNING: Address filtering not fully implemented - verify via app");
      }
      
      deviceConnected = true;
      Serial.println("BLE Client Connected");
      Serial.print("Connection established at: ");
      Serial.println(millis());
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("BLE Client Disconnected");
      
      // Reset game state on disconnect (security measure)
      waitingForCoin = false;
      expectedTile = -1;
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue();

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

  // Initialize Watchdog Timer (30 seconds timeout)
  esp_task_wdt_config_t wdt_config = {
    .timeout_ms = 30000,
    .idle_core_mask = 0,
    .trigger_panic = true
  };
  esp_task_wdt_init(&wdt_config);
  esp_task_wdt_add(NULL); // Add current task to WDT watch
  Serial.println("Watchdog timer initialized (30s timeout)");

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

  // Security Configuration (optional pairing with PIN)
  if (BLE_PAIRING_ENABLED) {
    BLESecurity *pSecurity = new BLESecurity();
    pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
    pSecurity->setCapability(ESP_IO_CAP_OUT); // Display only
    pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    
    uint32_t passkey = BLE_PAIRING_PIN;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));
    
    Serial.print("BLE Security enabled - Pairing PIN: ");
    Serial.println(BLE_PAIRING_PIN);
  } else {
    Serial.println("BLE Security disabled - Open connection mode");
  }

  // Print MAC address for whitelisting
  Serial.print("ESP32 MAC Address: ");
  Serial.println(BLEDevice::getAddress().toString().c_str());

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
  } else if (strcmp(command, "eliminate") == 0) {
    // Trigger elimination animation
    int playerId = doc["playerId"];
    if (playerId >= 0 && playerId < NUM_PLAYERS) {
      animatePlayerElimination(playerId);
      sendBLEResponse("{\"status\":\"ok\",\"message\":\"Elimination animation complete\"}");
    } else {
      sendBLEResponse("{\"status\":\"error\",\"message\":\"Invalid player ID\"}");
    }
  } else if (strcmp(command, "winner") == 0) {
    // Trigger winner animation
    int winnerId = doc["winnerId"];
    if (winnerId >= 0 && winnerId < NUM_PLAYERS) {
      animateWinner(winnerId);
      sendBLEResponse("{\"status\":\"ok\",\"message\":\"Winner animation complete\"}");
    } else {
      sendBLEResponse("{\"status\":\"error\",\"message\":\"Invalid winner ID\"}");
    }
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

void sendHeartbeat() {
  if (!deviceConnected) return;
  
  StaticJsonDocument<256> doc;
  doc["event"] = "heartbeat";
  doc["waitingForCoin"] = waitingForCoin;
  
  if (waitingForCoin) {
    unsigned long timeElapsed = millis() - coinWaitStartTime;
    unsigned long timeRemaining = (timeElapsed < COIN_TIMEOUT) ? (COIN_TIMEOUT - timeElapsed) : 0;
    doc["timeRemaining"] = timeRemaining / 1000; // Send in seconds
    doc["expectedTile"] = expectedTile + 1; // Convert to 1-indexed
    doc["currentPlayer"] = currentPlayer;
  }
  
  doc["uptime"] = millis() / 1000; // Seconds since boot
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
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

// ==================== ELIMINATION ANIMATION ====================
void animatePlayerElimination(int playerId) {
  Serial.printf("💀 Animating elimination for Player %d...\n", playerId);
  
  uint32_t playerColor = PLAYER_COLORS[playerId];
  
  // Blink the player's LED in all 20 tiles (1 LED per tile) 3 times
  for (int blink = 0; blink < 3; blink++) {
    // Turn ON all player LEDs across board
    for (int tile = 0; tile < NUM_TILES; tile++) {
      int ledIndex = tile * LEDS_PER_TILE + playerId;  // Each player has their own LED slot (0-3)
      strip.setPixelColor(ledIndex, playerColor);
    }
    strip.show();
    delay(300);
    
    // Turn OFF all player LEDs
    for (int tile = 0; tile < NUM_TILES; tile++) {
      int ledIndex = tile * LEDS_PER_TILE + playerId;
      strip.setPixelColor(ledIndex, 0);  // Black (off)
    }
    strip.show();
    delay(300);
  }
  
  // Final state: All player LEDs OFF permanently
  for (int tile = 0; tile < NUM_TILES; tile++) {
    int ledIndex = tile * LEDS_PER_TILE + playerId;
    strip.setPixelColor(ledIndex, 0);
  }
  strip.show();
  
  Serial.println("✓ Elimination animation complete - player LEDs turned off\n");
}

// ==================== WINNER CELEBRATION ANIMATION ====================
void animateWinner(int winnerId) {
  Serial.printf("🏆 WINNER ANIMATION for Player %d!\n", winnerId);
  
  uint32_t winnerColor = PLAYER_COLORS[winnerId];
  
  // Phase 1: Flash winner color across entire board (3 times)
  for (int flash = 0; flash < 3; flash++) {
    // All LEDs in winner color
    for (int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, winnerColor);
    }
    strip.show();
    delay(400);
    
    // All LEDs off
    for (int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, 0);
    }
    strip.show();
    delay(200);
  }
  
  // Phase 2: Disco effect with winner color + white strobes
  for (int disco = 0; disco < 20; disco++) {
    for (int i = 0; i < NUM_LEDS; i++) {
      // Alternate between winner color and white with some randomness
      if (random(100) > 50) {
        strip.setPixelColor(i, winnerColor);
      } else {
        strip.setPixelColor(i, 0xFFFFFF);  // White
      }
    }
    strip.show();
    delay(100);
  }
  
  // Phase 3: Chase pattern with winner color
  for (int chase = 0; chase < 3; chase++) {
    for (int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, winnerColor);
      if (i > 0) strip.setPixelColor(i - 1, 0);
      strip.show();
      delay(20);
    }
  }
  
  // Phase 4: Final celebration - all tiles pulsing in winner color
  for (int pulse = 0; pulse < 5; pulse++) {
    // Fade in
    for (int brightness = 0; brightness <= 255; brightness += 5) {
      uint32_t fadeColor = strip.Color(
        (uint8_t)((winnerColor >> 16) * brightness / 255),
        (uint8_t)(((winnerColor >> 8) & 0xFF) * brightness / 255),
        (uint8_t)((winnerColor & 0xFF) * brightness / 255)
      );
      for (int i = 0; i < NUM_LEDS; i++) {
        strip.setPixelColor(i, fadeColor);
      }
      strip.show();
      delay(10);
    }
    
    // Fade out
    for (int brightness = 255; brightness >= 0; brightness -= 5) {
      uint32_t fadeColor = strip.Color(
        (uint8_t)((winnerColor >> 16) * brightness / 255),
        (uint8_t)(((winnerColor >> 8) & 0xFF) * brightness / 255),
        (uint8_t)((winnerColor & 0xFF) * brightness / 255)
      );
      for (int i = 0; i < NUM_LEDS; i++) {
        strip.setPixelColor(i, fadeColor);
      }
      strip.show();
      delay(10);
    }
  }
  
  // Final state: Winner color solidly lit for 3 seconds
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, winnerColor);
  }
  strip.show();
  delay(3000);
  
  // Return to normal background
  renderBackground();
  
  Serial.println("✓ Winner celebration complete!\n");
}

// ==================== MAIN LOOP ====================
void loop() {
  // Reset watchdog timer
  esp_task_wdt_reset();
  
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
    unsigned long timeElapsed = millis() - coinWaitStartTime;
    unsigned long timeRemaining = COIN_TIMEOUT - timeElapsed;
    
    // Determine blink interval based on time remaining
    unsigned long currentBlinkInterval = BLINK_INTERVAL;
    uint32_t blinkColor = PLAYER_COLORS[currentPlayer];
    
    if (timeRemaining <= URGENT_THRESHOLD) {
      currentBlinkInterval = BLINK_INTERVAL_URGENT; // Very fast (100ms)
      blinkColor = 0xFF0000; // Red for urgency
    } else if (timeRemaining <= WARNING_THRESHOLD) {
      currentBlinkInterval = BLINK_INTERVAL_WARNING; // Fast (200ms)
    }
    // else use normal interval (500ms) with player color
    
    if (millis() - lastBlinkTime > currentBlinkInterval) {
      blinkState = !blinkState;
      lastBlinkTime = millis();
      
      if (blinkState) {
        setTileColor(expectedTile, blinkColor);
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
  
  // Send periodic heartbeat
  if (millis() - lastHeartbeatTime > HEARTBEAT_INTERVAL) {
    sendHeartbeat();
    lastHeartbeatTime = millis();
  }
  
  delay(10);
}
