/*
 * Last Drop - ESP32 Test Mode Firmware (Full Game Logic)
 * 
 * This firmware implements COMPLETE game logic on ESP32 for Test Mode 1
 * - 20-tile board with tile types (START, NORMAL, CHANCE, BONUS, PENALTY)
 * - 10 chance cards with random selection
 * - Score calculation and tracking
 * - Comprehensive reporting back to Android
 * - Undo functionality with state restoration
 * - Misplacement detection
 * - Full reset capabilities
 * 
 * Hardware:
 * - ESP32 Dev Board
 * - WS2812B LED Strip (80 LEDs = 4 LEDs per tile × 20 tiles)
 * - 20 Hall Effect Sensors (A3144) for coin detection
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>
#include <Wire.h>
#include <Adafruit_MCP23X17.h>
#include <Adafruit_NeoPixel.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <esp_task_wdt.h>
#include <queue>

// ==================== HARDWARE CONFIGURATION ====================
#define LED_PIN 16  // GPIO16 → AHCT125 data
#define LED_OE_PIN 47  // GPIO47 → AHCT125 OE (active LOW)
#define NUM_LEDS 137  // Total LEDs in perimeter strip
#define NUM_TILES 20
#define NUM_PLAYERS 4

// LED mapping: Each tile has 4 LEDs (R, G, B, Y), with decorative LEDs between tiles
// Tile format: [Red, Green, Blue, Yellow]
// Corner LEDs for connection status: 0, 36, 70, 104
const int TILE_LED_START[NUM_TILES] = {
  2,   // Tile 1:  LEDs 2-5   (R=2, G=3, B=4, Y=5)
  8,   // Tile 2:  LEDs 8-11
  14,  // Tile 3:  LEDs 14-17
  20,  // Tile 4:  LEDs 20-23
  26,  // Tile 5:  LEDs 26-29
  32,  // Tile 6:  LEDs 32-35
  42,  // Tile 7:  LEDs 42-45
  48,  // Tile 8:  LEDs 48-51
  54,  // Tile 9:  LEDs 54-57
  60,  // Tile 10: LEDs 60-63
  71,  // Tile 11: LEDs 71-74
  76,  // Tile 12: LEDs 76-79
  82,  // Tile 13: LEDs 82-85
  88,  // Tile 14: LEDs 88-91
  94,  // Tile 15: LEDs 94-97
  100, // Tile 16: LEDs 100-103
  111, // Tile 17: LEDs 111-114
  117, // Tile 18: LEDs 117-120
  123, // Tile 19: LEDs 123-126
  129  // Tile 20: LEDs 129-132
};

const int CORNER_LEDS[4] = {0, 36, 70, 104};  // Connection status indicators

// ==================== I2C & HALL SENSOR CONFIGURATION ====================
#define SDA_PIN 13
#define SCL_PIN 14
#define MCP_ADDR 0x27

// MCP23017 Port B tiles (PB0-PB7)
const uint8_t MCP_PORTB_TILES[8] = {1, 20, 19, 18, 16, 14, 17, 15};

// MCP23017 Port A tiles (PA0-PA7)
const uint8_t MCP_PORTA_TILES[8] = {2, 3, 4, 5, 13, 7, 8, 6};

// Direct ESP32 GPIO tiles (4 tiles not on MCP)
const uint8_t DIRECT_GPIO_PINS[4] = {17, 18, 8, 9};
const uint8_t DIRECT_GPIO_TILES[4] = {9, 10, 11, 12};

Adafruit_MCP23X17 mcp;

// ==================== BLE CONFIGURATION ====================
#define SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

// ==================== BOARD IDENTIFICATION ====================
// IMPORTANT: Change these values for each board you manufacture
#define BOARD_UNIQUE_ID "LASTDROP-0001"  // Unique ID for this board (0001, 0002, 0003, etc.)
#define BOARD_VERSION "1.0.0"             // Firmware version
#define MANUFACTURER_DATA "LASTDROP-BOARD-V1"  // Identifier for board discovery

// ==================== PAIRING PASSWORD ====================
// IMPORTANT: Set a unique 6-digit password for each board
#define BOARD_PASSWORD "654321"  // Default PIN for LASTDROP-0001
#define PAIRING_REQUIRED true     // Password protection enabled

// ==================== SECURITY CONFIGURATION ====================
#define BLE_PAIRING_ENABLED false   // TEMPORARILY DISABLED - Testing basic connection first
#define BLE_PAIRING_PIN 654321      // Default PIN: 654321
#define MAC_FILTERING_ENABLED false // Set true to enable MAC whitelist

// ==================== BUILD FLAGS ====================
// Toggle production vs. Test Mode 1 behavior
const bool PRODUCTION_MODE = false;      // Set to false to enable test behaviors
const bool TEST_MODE_1 = true;         // When PRODUCTION_MODE is false and this is true, ESP auto-confirms coin placement and returns chance card details without waiting for Hall sensors

// Android device MAC address whitelist (find via Android Bluetooth settings)
const String TRUSTED_ANDROID_MACS[] = {
  "AA:BB:CC:DD:EE:FF",  // Your phone's BLE MAC address
  "11:22:33:44:55:66"   // Second trusted device
};
const int NUM_TRUSTED_DEVICES = 2;

// ==================== GAME LOGIC CONFIGURATION ====================
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

// ==================== LED COLORS ====================
const uint32_t TILE_COLORS[NUM_TILES] = {
  0x2E8B57, 0x3CB371, 0x90EE90, 0x98FB98, 0xADFF2F,  // 0-4 Green shades
  0xFFFF00, 0xFFD700, 0xFFA500, 0xFF8C00, 0xFF6347,  // 5-9 Yellow to Orange
  0xFF4500, 0xFF0000, 0xDC143C, 0xB22222, 0x8B0000,  // 10-14 Red shades
  0x800080, 0x9932CC, 0xBA55D3, 0xDA70D6, 0xEE82EE   // 15-19 Purple shades
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
  int currentTile;      // 1-based (1 to 20)
  int score;            // Water drops
  bool alive;
  bool coinPlaced;
  uint32_t color;
  
  // Undo support
  int previousTile;
  int previousScore;
};

PlayerState players[NUM_PLAYERS];
int activePlayerCount = 2;  // Default to 2 players, updated via config command
int currentPlayer = -1;
int expectedTile = -1;
bool waitingForCoin = false;
unsigned long coinWaitStartTime = 0;
const unsigned long COIN_TIMEOUT = 60000; // 60 seconds (increased for winner animation)

// Security: Pairing state
bool isPaired = false;
unsigned long pairTimeout = 0;
const unsigned long PAIR_TIMEOUT_MS = 30000;  // 30 seconds to enter password

// Board Settings (customizable from Android)
String boardPassword = BOARD_PASSWORD;  // Current password (can be changed)
String boardNickname = BOARD_UNIQUE_ID; // Current nickname (can be changed)

// Connection status LED modes
enum ConnectionMode {
  MODE_DISCONNECTED,    // Light sea blue blink (waiting for Android)
  MODE_PAIRING,         // Purple pulse (waiting for password)
  MODE_CONNECTED,       // Initialization lap (connection established)
  MODE_READY            // Player LED blink (ready for game)
};
ConnectionMode currentConnectionMode = MODE_DISCONNECTED;
unsigned long lastConnectionLEDUpdate = 0;
const unsigned long CONNECTION_LED_INTERVAL = 500;  // 500ms blink/pulse
int connectionLEDStep = 0;

// Undo state tracking
struct UndoState {
  bool hasUndo;
  int playerId;
  int fromTile;
  int toTile;
  int scoreChange;
  int chanceCardNumber;
} lastMove;

// ==================== LED CONTROL ====================
bool blinkState = false;
unsigned long lastBlinkTime = 0;
const unsigned long BLINK_INTERVAL = 500;

// Forward declarations for LED rendering functions
void renderPlayers();
void renderBackground();
int getPlayerLED(int tile, int playerId);
void animatePlayerElimination(int playerId);
void animateWinner(int winnerId);

void updateConnectionStatusLEDs() {
  if (millis() - lastConnectionLEDUpdate < CONNECTION_LED_INTERVAL) {
    return;
  }
  lastConnectionLEDUpdate = millis();
  
  switch (currentConnectionMode) {
    case MODE_DISCONNECTED:
      // Light blue blink on corner LEDs (0, 36, 70, 104)
      if (blinkState) {
        strip.clear();
        for (int i = 0; i < 4; i++) {
          strip.setPixelColor(CORNER_LEDS[i], strip.Color(100, 200, 255));  // Light blue
        }
      } else {
        strip.clear();
      }
      blinkState = !blinkState;
      strip.show();
      break;
      
    case MODE_PAIRING:
      // Purple pulse on corner LEDs
      {
        int brightness = (connectionLEDStep < 128) ? connectionLEDStep * 2 : (255 - connectionLEDStep) * 2;
        strip.clear();
        for (int i = 0; i < 4; i++) {
          strip.setPixelColor(CORNER_LEDS[i], strip.Color(brightness, 0, brightness));  // Purple
        }
        connectionLEDStep = (connectionLEDStep + 16) % 256;
        strip.show();
      }
      break;
      
    case MODE_CONNECTED:
      // Success animation - flash corner LEDs green 3 times
      {
        if (connectionLEDStep < 6) {  // 6 steps = 3 blinks
          if (connectionLEDStep % 2 == 0) {
            strip.clear();
            for (int i = 0; i < 4; i++) {
              strip.setPixelColor(CORNER_LEDS[i], strip.Color(0, 255, 0));  // Green
            }
          } else {
            strip.clear();
          }
          connectionLEDStep++;
        } else {
          // Completed success animation, switch to READY mode
          currentConnectionMode = MODE_READY;
          connectionLEDStep = 0;
          strip.clear();
        }
        strip.show();
      }
      break;
      
    case MODE_READY:
      // Show player positions only if game is active AND coins are placed
      bool gameActive = false;
      for (int i = 0; i < NUM_PLAYERS; i++) {
        if (players[i].coinPlaced) {
          gameActive = true;
          break;
        }
      }
      
      if (gameActive) {
        // Game in progress - show actual positions
        renderPlayers();
      } else {
        // Pre-game: Show all active players on tile 1 with their configured colors
        strip.clear();
        for (int i = 0; i < activePlayerCount; i++) {
          if (players[i].alive) {
            int ledIndex = getPlayerLED(1, i);  // All players start on tile 1
            if (ledIndex >= 0) {
              strip.setPixelColor(ledIndex, players[i].color);
            }
          }
        }
        strip.show();
      }
      break;
  }
}

unsigned long lastScanTime = 0;
const unsigned long SCAN_INTERVAL = 5000;

// ==================== COMMAND QUEUE ====================
std::queue<String> commandQueue;
bool processingCommand = false;

// ==================== HEARTBEAT ====================
unsigned long lastHeartbeatTime = 0;
const unsigned long HEARTBEAT_INTERVAL = 5000;  // 5 seconds

// ==================== ACTIVITY TRACKING ====================
unsigned long lastActivityTime = 0;
const unsigned long IDLE_TIMEOUT = 300000;  // 5 minutes

// ==================== HELPER FUNCTIONS (Forward Declarations) ====================
bool isTrustedDevice(BLEAddress address);
void sendBLEResponse(const char* json);
void sendBLEResponse(String json);
void sendErrorResponse(const char* message);
void loadGameState();
void startupAnimation();
void resetIdleTimer();
void validateGameState();
void handleUndo(JsonDocument& doc);
void handleReset();
void sendStatus();
void animateMove(int fromTile, int toTile, uint32_t color, int playerId);
void setTileColor(int tile, uint32_t color);
void renderBackground();
void renderPlayers();
void saveGameState();
void sendRollResponse(int playerId, int fromTile, int toTile, const TileDefinition& tile, int scoreChange, int oldScore, int newScore, int chanceCard, const char* chanceDesc, bool alive, bool waitForCoin);
void sendUndoResponse(int playerId, int fromTile, int toTile, int score, bool alive);
void sendResetResponse();
void sendCoinPlacedResponse(int playerId, int tile, bool hallVerified = true, const char* message = "Physical coin placement confirmed");
void sendTimeoutResponse(int playerId, int tile);
const char* getTileTypeName(TileType type);

// ==================== BLE CALLBACKS ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("✓ BLE Client Connected");
      Serial.print("Connection time: ");
      Serial.println(millis());
      
      // Clear any old game state from previous session
      for (int i = 0; i < NUM_PLAYERS; i++) {
        players[i].currentTile = 1;
        players[i].score = 10;
        players[i].alive = true;
        players[i].coinPlaced = false;  // Critical: ensure no ghost coins
        players[i].color = PLAYER_COLORS[i];
      }
      lastMove.hasUndo = false;
      Serial.println("✓ Game state reset for new session");
      
      // Send ready message
      sendBLEResponse("{\"event\":\"ready\",\"message\":\"ESP32 Test Mode Ready\",\"firmware\":\"v2.0-testmode\"}");
      
      // Request game state sync from Android (in case of reconnection during active game)
      delay(500);  // Wait for Android to be ready
      sendBLEResponse("{\"event\":\"request_state\",\"message\":\"Requesting game state sync\"}");
      Serial.println("📤 Requesting game state from Android...");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("✗ BLE Client Disconnected");
      waitingForCoin = false;
      expectedTile = -1;
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();
      if (rxValue.length() > 0) {
        Serial.println("📨 Received BLE Command:");
        Serial.println(rxValue);
        handleBLECommand(rxValue.c_str());
      }
    }
};

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  Serial.println("\n========================================");
  Serial.println("Last Drop ESP32 Test Mode Firmware v2.0");
  Serial.println("Full Game Logic Implementation");
  Serial.println("========================================\n");

  // Initialize watchdog timer (30s timeout) - ESP32 Arduino Core 3.x
  esp_task_wdt_config_t wdt_config = {
    .timeout_ms = 30000,
    .idle_core_mask = 0,
    .trigger_panic = true
  };
  esp_task_wdt_init(&wdt_config);
  esp_task_wdt_add(NULL);
  Serial.println("✓ Watchdog enabled (30s timeout)");

  // Initialize LED Output Enable (AHCT125)
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, HIGH);  // Disable output during init
  Serial.println("✓ LED OE disabled during init");

  // Initialize LED strip
  strip.begin();
  strip.setBrightness(100);
  strip.show();
  
  delay(500);  // Settle time
  digitalWrite(LED_OE_PIN, LOW);  // Enable output (active LOW)
  Serial.println("✓ LED OE enabled");

  // Initialize I2C and MCP23017
  Serial.println("Initializing I2C and MCP23017...");
  Wire.begin(SDA_PIN, SCL_PIN);
  
  if (!mcp.begin_I2C(MCP_ADDR)) {
    Serial.println("✗ ERROR: MCP23017 not found at 0x27!");
    Serial.println("  Check wiring: SDA=GPIO13, SCL=GPIO14, VCC=3.3V");
  } else {
    Serial.println("✓ MCP23017 found at 0x27");
    
    // Configure MCP Port B (8 tiles with pull-ups)
    for (uint8_t i = 0; i < 8; i++) {
      mcp.pinMode(i + 8, INPUT_PULLUP);  // Port B = pins 8-15
    }
    Serial.println("  - Port B configured (8 tiles)");
    
    // Configure MCP Port A (8 tiles with pull-ups)
    for (uint8_t i = 0; i < 8; i++) {
      mcp.pinMode(i, INPUT_PULLUP);  // Port A = pins 0-7
    }
    Serial.println("  - Port A configured (8 tiles)");
  }
  
  // Initialize direct ESP32 GPIO Hall sensors (4 tiles)
  Serial.println("Initializing direct ESP32 Hall sensor pins...");
  for (uint8_t i = 0; i < 4; i++) {
    pinMode(DIRECT_GPIO_PINS[i], INPUT_PULLUP);
    Serial.printf("  - GPIO%d → Tile %d\n", DIRECT_GPIO_PINS[i], DIRECT_GPIO_TILES[i]);
  }
  Serial.println("✓ All Hall sensors ready");

  // Initialize preferences
  preferences.begin("lastdrop", false);
  
  // Load custom board settings (password and nickname)
  boardPassword = preferences.getString("password", BOARD_PASSWORD);
  boardNickname = preferences.getString("nickname", BOARD_UNIQUE_ID);
  Serial.printf("Board Password: %s\n", boardPassword.c_str());
  Serial.printf("Board Nickname: %s\n", boardNickname.c_str());
  loadGameState();

  // Initialize BLE
  initBLE();

  // Initialize game state
  initializeGameState();

  // Show startup animation
  startupAnimation();

  Serial.println("✓ System Ready - Waiting for BLE connection...\n");
}

// ==================== GAME INITIALIZATION ====================
void initializeGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    players[i].currentTile = 1;  // Start at tile 1
    players[i].score = 10;       // Starting water drops
    players[i].alive = true;
    players[i].coinPlaced = false;
    players[i].color = PLAYER_COLORS[i];
    players[i].previousTile = 1;
    players[i].previousScore = 10;
  }
  
  lastMove.hasUndo = false;
  lastMove.playerId = -1;
}

// ==================== MAC ADDRESS FILTERING ====================
bool isTrustedDevice(BLEAddress address) {
  if (!MAC_FILTERING_ENABLED) return true;
  
  String addrStr = address.toString().c_str();
  for (int i = 0; i < NUM_TRUSTED_DEVICES; i++) {
    if (addrStr.equalsIgnoreCase(TRUSTED_ANDROID_MACS[i])) {
      return true;
    }
  }
  return false;
}

// ==================== BLE INITIALIZATION ====================
void initBLE() {
  BLEDevice::init(boardNickname.c_str());  // Use custom nickname (defaults to BOARD_UNIQUE_ID)
  
  Serial.print("ESP32 MAC Address: ");
  Serial.println(BLEDevice::getAddress().toString().c_str());
  Serial.printf("Board ID: %s\n", BOARD_UNIQUE_ID);
  Serial.printf("Board Nickname: %s\n", boardNickname.c_str());
  Serial.printf("Board Version: %s\n", BOARD_VERSION);

  #if BLE_PAIRING_ENABLED
    BLESecurity *pSecurity = new BLESecurity();
    pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
    pSecurity->setCapability(ESP_IO_CAP_KBDISP);  // FIXED: Keyboard + Display allows PIN entry
    pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    
    uint32_t passkey = BLE_PAIRING_PIN;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));
    
    Serial.printf("✓ BLE Security Enabled - PIN: %d\n", BLE_PAIRING_PIN);
    Serial.println("  Android will prompt for PIN on first connection\n");
  #else
    Serial.println("⚠️  BLE Security DISABLED - Any device can connect!\n");
  #endif

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();

  // Configure advertising with manufacturer data for discovery
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  
  // Add manufacturer-specific data for board identification
  BLEAdvertisementData advData;
  advData.setName(boardNickname.c_str());  // Use custom nickname
  advData.setManufacturerData(MANUFACTURER_DATA);
  pAdvertising->setAdvertisementData(advData);
  
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  
  Serial.println("✓ BLE Service Started");
  Serial.printf("  Device Name: %s\n", boardNickname.c_str());
  Serial.printf("  Manufacturer Data: %s\n", MANUFACTURER_DATA);
  Serial.printf("  Service UUID: %s\n\n", SERVICE_UUID);
}

// ==================== BLE COMMAND HANDLER ====================
void handleBLECommand(const char* jsonStr) {
  // Queue command for sequential processing
  commandQueue.push(String(jsonStr));
}

// ==================== COMMAND QUEUE PROCESSOR ====================
void processCommandQueue() {
  if (processingCommand || commandQueue.empty()) return;
  
  processingCommand = true;
  String cmdStr = commandQueue.front();
  commandQueue.pop();
  
  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, cmdStr);

  if (error) {
    Serial.print("❌ JSON Parse Error: ");
    Serial.println(error.c_str());
    sendErrorResponse("Invalid JSON format");
    processingCommand = false;
    return;
  }

  const char* command = doc["command"];
  
  if (strcmp(command, "roll") == 0) {
    handleRoll(doc);
  } else if (strcmp(command, "undo") == 0) {
    handleUndo(doc);
  } else if (strcmp(command, "reset") == 0) {
    handleReset();
  } else if (strcmp(command, "pair") == 0) {
    handlePair(doc);
  } else if (strcmp(command, "unpair") == 0) {
    handleUnpair();
  } else if (strcmp(command, "config") == 0) {
    handleConfig(doc);
  } else if (strcmp(command, "sync_state") == 0) {
    handleSyncState(doc);
  } else if (strcmp(command, "update_settings") == 0) {
    handleUpdateSettings(doc);
  } else if (strcmp(command, "status") == 0) {
    sendStatus();
  } else {
    sendErrorResponse("Unknown command");
  }
  
  processingCommand = false;
}

// ==================== HANDLE PAIRING ====================
void handlePair(JsonDocument& doc) {
  Serial.println("\n🔐 Processing PAIR command...");
  resetIdleTimer();
  
  const char* password = doc["password"];
  
  if (!PAIRING_REQUIRED) {
    // Pairing disabled - auto-accept
    isPaired = true;
    sendPairResponse(true, "Pairing not required");
    Serial.println("✓ Pairing not required - auto-paired");
    return;
  }
  
  if (password == nullptr || strlen(password) == 0) {
    sendPairResponse(false, "Password required");
    Serial.println("✗ Password missing");
    return;
  }
  
  // Validate against custom board password
  if (strcmp(password, boardPassword.c_str()) == 0) {
    isPaired = true;
    pairTimeout = 0;
    Serial.println("✓ Password correct - device paired");
    
    // Visual feedback: Quick green flash on all LEDs
    for (int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, strip.Color(0, 255, 0));
    }
    strip.show();
    delay(500);
    strip.clear();
    strip.show();
    
    // Switch to connected mode (initialization lap)
    currentConnectionMode = MODE_CONNECTED;
    connectionLEDStep = 0;
    
    sendPairResponse(true, "Paired successfully");
  } else {
    Serial.println("✗ Incorrect password");
    
    // Visual feedback: Quick red flash on all LEDs
    for (int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, strip.Color(255, 0, 0));
    }
    strip.show();
    delay(500);
    strip.clear();
    strip.show();
    
    sendPairResponse(false, "Incorrect password");
  }
}

void handleUnpair() {
  Serial.println("\n🔓 Processing UNPAIR command...");
  resetIdleTimer();
  
  isPaired = false;
  pairTimeout = 0;
  
  // Visual feedback: Yellow flash
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(255, 255, 0));
  }
  strip.show();
  delay(500);
  strip.clear();
  strip.show();
  
  StaticJsonDocument<128> response;
  response["event"] = "unpaired";
  response["message"] = "Device unpaired";
  
  String output;
  serializeJson(response, output);
  sendBLEResponse(output);
  Serial.println("✓ Device unpaired");
}

void sendPairResponse(bool success, const char* message) {
  StaticJsonDocument<256> response;
  response["event"] = success ? "pair_success" : "pair_failed";
  response["message"] = message;
  response["boardId"] = BOARD_UNIQUE_ID;
  response["version"] = BOARD_VERSION;
  response["pairingRequired"] = PAIRING_REQUIRED;
  
  String output;
  serializeJson(response, output);
  sendBLEResponse(output);
}

// ==================== HANDLE CONFIG ====================
void handleConfig(JsonDocument& doc) {
  Serial.println("\n⚙️ Processing CONFIG command...");
  resetIdleTimer();  // Reset idle timer on activity
  
  int playerCount = doc["playerCount"];
  JsonArray colorsArray = doc["colors"];
  
  if (playerCount < 2 || playerCount > NUM_PLAYERS) {
    Serial.printf("  ⚠️ Invalid player count: %d (must be 2-4)\n", playerCount);
    sendErrorResponse("Invalid player count");
    return;
  }
  
  activePlayerCount = playerCount;
  Serial.printf("  Active Players: %d\n", activePlayerCount);
  
  // Update player colors from hex strings
  for (int i = 0; i < activePlayerCount; i++) {
    if (i < colorsArray.size()) {
      const char* colorHex = colorsArray[i];
      // Convert hex string to uint32_t (format: "RRGGBB")
      uint32_t color = (uint32_t)strtol(colorHex, NULL, 16);
      players[i].color = color;
      
      Serial.printf("  Player %d color: #%s (0x%06X)\n", i, colorHex, color);
    }
  }
  
  // Turn off LEDs for inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = 0x000000;  // Black (off)
  }
  
  // Send confirmation
  StaticJsonDocument<256> response;
  response["event"] = "config_complete";
  response["playerCount"] = activePlayerCount;
  
  String output;
  serializeJson(response, output);
  pTxCharacteristic->setValue(output.c_str());
  pTxCharacteristic->notify();
  
  Serial.println("✓ Config applied\n");
}

// ==================== HANDLE STATE SYNC ====================
void handleSyncState(JsonDocument& doc) {
  Serial.println("\n🔄 Processing STATE SYNC command...");
  resetIdleTimer();
  
  if (!doc.containsKey("gameActive") || !doc["gameActive"]) {
    Serial.println("  No active game - skipping state sync");
    return;
  }
  
  // Sync player count and colors
  int playerCount = doc["playerCount"];
  JsonArray colorsArray = doc["colors"];
  JsonArray positionsArray = doc["positions"];
  JsonArray scoresArray = doc["scores"];
  JsonArray aliveArray = doc["alive"];
  
  if (playerCount < 2 || playerCount > NUM_PLAYERS) {
    Serial.printf("  ⚠️ Invalid player count: %d\n", playerCount);
    return;
  }
  
  activePlayerCount = playerCount;
  Serial.printf("  Active Players: %d\n", activePlayerCount);
  
  // Restore each player's state
  for (int i = 0; i < activePlayerCount; i++) {
    // Update color
    if (i < colorsArray.size()) {
      const char* colorHex = colorsArray[i];
      uint32_t color = (uint32_t)strtol(colorHex, NULL, 16);
      players[i].color = color;
      Serial.printf("  Player %d color: #%s\n", i, colorHex);
    }
    
    // Update position
    if (i < positionsArray.size()) {
      players[i].currentTile = positionsArray[i];
      Serial.printf("  Player %d position: Tile %d\n", i, players[i].currentTile);
    }
    
    // Update score
    if (i < scoresArray.size()) {
      players[i].score = scoresArray[i];
      Serial.printf("  Player %d score: %d\n", i, players[i].score);
    }
    
    // Update alive status
    if (i < aliveArray.size()) {
      players[i].alive = aliveArray[i];
      players[i].coinPlaced = aliveArray[i];  // If alive, coin is placed
      Serial.printf("  Player %d alive: %s\n", i, players[i].alive ? "Yes" : "No");
    }
  }
  
  // Turn off inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = 0x000000;
  }
  
  // Render restored state immediately
  currentConnectionMode = MODE_READY;
  renderPlayers();
  
  // Send confirmation
  StaticJsonDocument<256> response;
  response["event"] = "sync_complete";
  response["message"] = "Game state restored";
  
  String output;
  serializeJson(response, output);
  pTxCharacteristic->setValue(output.c_str());
  pTxCharacteristic->notify();
  
  Serial.println("✓ Game state synchronized\n");
}

// ==================== HANDLE UPDATE SETTINGS ====================
void handleUpdateSettings(JsonDocument& doc) {
  Serial.println("\n⚙️ Processing UPDATE_SETTINGS command...");
  resetIdleTimer();
  
  // Security: Only allow settings update when paired
  if (PAIRING_REQUIRED && !isPaired) {
    Serial.println("  🔒 SECURITY: Settings update rejected - device not paired");
    sendErrorResponse("Device not paired - pairing required");
    return;
  }
  
  bool updated = false;
  
  // Update password if provided
  if (doc.containsKey("password")) {
    const char* newPassword = doc["password"];
    if (strlen(newPassword) >= 6) {  // Minimum 6 characters
      boardPassword = String(newPassword);
      preferences.putString("password", boardPassword);
      Serial.printf("  ✓ Password updated: %s\n", boardPassword.c_str());
      updated = true;
    } else {
      Serial.println("  ⚠️ Password too short (min 6 chars)");
      sendErrorResponse("Password must be at least 6 characters");
      return;
    }
  }
  
  // Update nickname if provided
  if (doc.containsKey("nickname")) {
    const char* newNickname = doc["nickname"];
    if (strlen(newNickname) > 0 && strlen(newNickname) <= 30) {
      boardNickname = String(newNickname);
      preferences.putString("nickname", boardNickname);
      Serial.printf("  ✓ Nickname updated: %s\n", boardNickname.c_str());
      
      // Update BLE device name (requires restart to take effect)
      // Note: BLE device name is set in setup(), will apply on next boot
      updated = true;
    } else {
      Serial.println("  ⚠️ Invalid nickname (1-30 chars)");
      sendErrorResponse("Nickname must be 1-30 characters");
      return;
    }
  }
  
  if (updated) {
    // Send confirmation with new settings
    StaticJsonDocument<512> response;
    response["event"] = "settings_updated";
    response["password"] = boardPassword;
    response["nickname"] = boardNickname;
    response["restartRequired"] = doc.containsKey("nickname");  // Nickname needs restart
    
    String output;
    serializeJson(response, output);
    pTxCharacteristic->setValue(output.c_str());
    pTxCharacteristic->notify();
    
    Serial.println("✓ Settings updated successfully");
    if (doc.containsKey("nickname")) {
      Serial.println("  ⚠️ Board restart required for nickname to take effect in BLE advertising");
    }
  } else {
    sendErrorResponse("No valid settings provided");
  }
  
  Serial.println();
}

// ==================== HANDLE DICE ROLL ====================
void handleRoll(JsonDocument& doc) {
  Serial.println("\n🎲 Processing Dice Roll...");
  resetIdleTimer();  // Reset idle timer on activity
  validateGameState();  // Validate before processing
  
  // Security check: Require pairing before accepting roll commands
  if (PAIRING_REQUIRED && !isPaired) {
    Serial.println("  🔒 SECURITY: Roll rejected - device not paired");
    sendErrorResponse("Device not paired - pairing required");
    return;
  }
  
  int playerId = doc["playerId"];
  int diceValue = doc["diceValue"];
  
  if (playerId < 0 || playerId >= activePlayerCount) {
    Serial.printf("  ⚠️ Invalid player ID: %d (active players: %d)\n", playerId, activePlayerCount);
    sendErrorResponse("Invalid player ID");
    return;
  }

  Serial.printf("  Player: %d\n", playerId);
  Serial.printf("  Dice Value: %d\n", diceValue);

  // Store previous state for undo
  players[playerId].previousTile = players[playerId].currentTile;
  players[playerId].previousScore = players[playerId].score;

  int currentTile = players[playerId].currentTile;
  int newTile = currentTile + diceValue;
  bool completedLap = false;
  
  // Lap detection and wrapping
  if (newTile > NUM_TILES) {
    Serial.println("  >> LAP COMPLETED! +5 BONUS POINTS");
    completedLap = true;
    newTile = newTile - NUM_TILES;  // Wrap to beginning
  }
  if (newTile < 1) newTile = 1;

  Serial.printf("  Movement: Tile %d → Tile %d\n", currentTile, newTile);

  // Get tile information
  const TileDefinition& tile = BOARD[newTile - 1];
  Serial.printf("  Tile Name: %s\n", tile.name);
  Serial.printf("  Tile Type: %s\n", getTileTypeName(tile.type));
  
  int scoreChange = 0;
  int chanceCardNumber = 0;
  const char* chanceCardDesc = "";

  switch (tile.type) {
    case TYPE_START:
      Serial.println("  Effect: Start tile (no effect)");
      break;
      
    case TYPE_NORMAL:
      Serial.println("  Effect: Normal/Safe tile (no effect)");
      break;
      
    case TYPE_BONUS:
      scoreChange = +1;
      Serial.println("  Effect: BONUS +1 point");
      break;
      
    case TYPE_PENALTY:
      // Penalty tiles: -1 or -2 based on tile
      if (newTile == 2 || newTile == 4) {
        scoreChange = -1;
        Serial.println("  Effect: PENALTY -1 point");
      } else {
        scoreChange = -2;
        Serial.println("  Effect: PENALTY -2 points");
      }
      break;
      
    case TYPE_DISASTER:
      // Disaster tiles: -2, -3, or -4 based on tile
      if (newTile == 12) {
        scoreChange = -2;
        Serial.println("  Effect: DISASTER -2 points");
      } else if (newTile == 5 || newTile == 10) {
        scoreChange = -3;
        Serial.println("  Effect: DISASTER -3 points");
      } else {  // Tile 7
        scoreChange = -4;
        Serial.println("  Effect: DISASTER -4 points");
      }
      break;
      
    case TYPE_WATER_DOCK:
      // Water Dock tiles: +1, +2, or +3 based on tile
      if (newTile == 15) {
        scoreChange = +1;
        Serial.println("  Effect: WATER DOCK +1 point");
      } else if (newTile == 11) {
        scoreChange = +2;
        Serial.println("  Effect: WATER DOCK +2 points");
      } else {  // Tile 3
        scoreChange = +3;
        Serial.println("  Effect: WATER DOCK +3 points");
      }
      break;
      
    case TYPE_SUPER_DOCK:
      scoreChange = +4;
      Serial.println("  Effect: SUPER DOCK +4 points");
      break;
      
    case TYPE_CHANCE: {
      // Draw random chance card from 20 cards
      int cardIndex = random(20);  // 0-19
      const ChanceCard& card = CHANCE_CARDS[cardIndex];
      chanceCardNumber = card.number;
      chanceCardDesc = card.description;
      scoreChange = card.effect;
      
      Serial.printf("  Effect: CHANCE CARD #%d\n", card.number);
      Serial.printf("    \"%s\"\n", card.description);
      Serial.printf("    Score change: %+d\n", card.effect);
      
      // Note: Special cards (11-14) have 0 effect but should trigger special logic
      // This is handled in Android app, ESP32 just reports the card
      break;
    }
  }
  
  // Apply lap bonus if completed
  if (completedLap) {
    scoreChange += 5;
    Serial.println("  Applied lap bonus: +5 points");
  }

  // Apply score change
  int oldScore = players[playerId].score;
  int newScore = oldScore + scoreChange;
  if (newScore < 0) newScore = 0;
  
  players[playerId].score = newScore;
  players[playerId].currentTile = newTile;
  
  Serial.printf("  Score: %d → %d (%+d)\n", oldScore, newScore, scoreChange);

  // Check if player is eliminated
  if (newScore <= 0 && players[playerId].alive) {
    players[playerId].alive = false;
    Serial.println("  ⚠️  PLAYER ELIMINATED!");
    
    // Send elimination event to Android
    StaticJsonDocument<256> eliminationEvent;
    eliminationEvent["event"] = "player_eliminated";
    eliminationEvent["playerId"] = playerId;
    
    String eliminationOutput;
    serializeJson(eliminationEvent, eliminationOutput);
    pTxCharacteristic->setValue(eliminationOutput.c_str());
    pTxCharacteristic->notify();
    
    // Trigger elimination animation
    animatePlayerElimination(playerId);
    
    // Check if there's a winner (only 1 player alive)
    int alivePlayers = 0;
    int lastAliveId = -1;
    for (int i = 0; i < activePlayerCount; i++) {
      if (players[i].alive) {
        alivePlayers++;
        lastAliveId = i;
      }
    }
    
    if (alivePlayers == 1) {
      Serial.printf("🎉 GAME OVER - Player %d WINS!\n", lastAliveId);
      
      // Send winner event to Android
      StaticJsonDocument<256> winnerEvent;
      winnerEvent["event"] = "winner_declared";
      winnerEvent["winnerId"] = lastAliveId;
      
      String winnerOutput;
      serializeJson(winnerEvent, winnerOutput);
      pTxCharacteristic->setValue(winnerOutput.c_str());
      pTxCharacteristic->notify();
      
      // Trigger winner animation
      animateWinner(lastAliveId);
    }
  }

  // Store undo information
  lastMove.hasUndo = true;
  lastMove.playerId = playerId;
  lastMove.fromTile = currentTile;
  lastMove.toTile = newTile;
  lastMove.scoreChange = scoreChange;
  lastMove.chanceCardNumber = chanceCardNumber;

  // Animate movement
  currentPlayer = playerId;
  expectedTile = newTile;
  animateMove(currentTile, newTile, PLAYER_COLORS[playerId], playerId);

  const bool skipCoinWait = (!PRODUCTION_MODE && TEST_MODE_1);

  if (skipCoinWait) {
    // Test Mode 1: Auto-confirm placement without Hall sensors
    players[playerId].coinPlaced = true;
    waitingForCoin = false;
    currentPlayer = -1;
    expectedTile = -1;
    saveGameState();

    sendRollResponse(playerId, currentTile, newTile, tile, scoreChange, oldScore, newScore,
                     chanceCardNumber, chanceCardDesc, players[playerId].alive, false);
    sendCoinPlacedResponse(playerId, newTile, false, "Test Mode 1: placement auto-confirmed");

    Serial.println("✓ Roll processed (Test Mode 1) - coin auto-confirmed\n");
  } else {
    // Production/normal behavior: wait for Hall sensor confirmation
    waitingForCoin = true;
    coinWaitStartTime = millis();
    saveGameState();

    sendRollResponse(playerId, currentTile, newTile, tile, scoreChange, oldScore, newScore,
                     chanceCardNumber, chanceCardDesc, players[playerId].alive, true);

    Serial.println("✓ Roll processed, waiting for coin placement\n");
  }
}

// ==================== SEND ROLL RESPONSE ====================
void sendRollResponse(int playerId, int fromTile, int toTile, const TileDefinition& tile,
                      int scoreChange, int oldScore, int newScore, int chanceCard, 
                      const char* chanceDesc, bool alive, bool waitForCoin) {
  StaticJsonDocument<768> doc;
  
  doc["event"] = "roll_processed";
  doc["playerId"] = playerId;
  doc["movement"]["from"] = fromTile;
  doc["movement"]["to"] = toTile;
  doc["movement"]["animated"] = true;
  
  doc["tile"]["index"] = tile.index;
  doc["tile"]["name"] = tile.name;
  doc["tile"]["type"] = getTileTypeName(tile.type);
  
  doc["score"]["old"] = oldScore;
  doc["score"]["new"] = newScore;
  doc["score"]["change"] = scoreChange;
  doc["lapBonus"] = false;  // Will be updated in handleRoll if lap completed
  
  if (tile.type == TYPE_CHANCE && chanceCard > 0) {
    doc["chanceCard"]["number"] = chanceCard;
    doc["chanceCard"]["description"] = chanceDesc;
    doc["chanceCard"]["effect"] = scoreChange;
  }
  
  doc["player"]["alive"] = alive;
  doc["player"]["eliminated"] = !alive;
  
  doc["waiting"]["forCoin"] = waitForCoin;
  doc["waiting"]["tile"] = toTile;
  doc["waiting"]["blinking"] = waitForCoin;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== HANDLE UNDO ====================
void handleUndo(JsonDocument& doc) {
  Serial.println("\n↩️  Processing Undo...");
  
  if (!lastMove.hasUndo) {
    Serial.println("❌ No move to undo");
    sendErrorResponse("No move to undo");
    return;
  }

  int playerId = lastMove.playerId;
  int fromTile = lastMove.toTile;  // Current position
  int toTile = lastMove.fromTile;  // Original position
  
  Serial.printf("  Player: %d\n", playerId);
  Serial.printf("  Reverting: Tile %d → Tile %d\n", fromTile, toTile);

  // Restore previous state
  players[playerId].currentTile = players[playerId].previousTile;
  players[playerId].score = players[playerId].previousScore;
  players[playerId].alive = (players[playerId].score > 0);
  players[playerId].coinPlaced = false;

  Serial.printf("  Score restored: %d\n", players[playerId].score);
  Serial.printf("  Status: %s\n", players[playerId].alive ? "Alive" : "Eliminated");

  // Animate reverse movement
  currentPlayer = playerId;
  expectedTile = toTile;
  animateMove(fromTile, toTile, PLAYER_COLORS[playerId], playerId);
  
  // Start waiting for coin at old position
  waitingForCoin = true;
  coinWaitStartTime = millis();
  
  // Clear undo
  lastMove.hasUndo = false;
  
  // Save state
  saveGameState();

  // Send undo response
  sendUndoResponse(playerId, fromTile, toTile, players[playerId].score, players[playerId].alive);

  Serial.println("✓ Undo complete, waiting for coin placement\n");
}

// ==================== SEND UNDO RESPONSE ====================
void sendUndoResponse(int playerId, int fromTile, int toTile, int score, bool alive) {
  StaticJsonDocument<512> doc;
  
  doc["event"] = "undo_complete";
  doc["playerId"] = playerId;
  doc["movement"]["from"] = fromTile;
  doc["movement"]["to"] = toTile;
  doc["movement"]["reversed"] = true;
  
  doc["score"]["restored"] = score;
  doc["player"]["alive"] = alive;
  
  doc["waiting"]["forCoin"] = true;
  doc["waiting"]["tile"] = toTile;
  doc["waiting"]["blinking"] = true;
  doc["waiting"]["message"] = "Place coin at original position";
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== HANDLE RESET ====================
void handleReset() {
  Serial.println("\n🔄 Processing Game Reset...");

  // Reset only active players
  for (int i = 0; i < activePlayerCount; i++) {
    players[i].currentTile = 1;
    players[i].score = 10;
    players[i].alive = true;
    players[i].coinPlaced = false;
    // Keep the configured color
    players[i].previousTile = 1;
    players[i].previousScore = 10;
    
    Serial.printf("  Player %d: Reset to Start (10 drops)\n", i);
  }
  
  // Turn off inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].currentTile = 1;
    players[i].score = 0;
    players[i].alive = false;
    players[i].coinPlaced = false;
    players[i].color = 0x000000;  // Black (off)
    players[i].previousTile = 1;
    players[i].previousScore = 0;
  }
  
  currentPlayer = -1;
  expectedTile = -1;
  waitingForCoin = false;
  lastMove.hasUndo = false;
  
  // Clear all LEDs
  strip.clear();
  strip.show();
  delay(100);
  
  // Redraw background
  renderBackground();
  
  // Save state
  saveGameState();

  // Send reset response
  sendResetResponse();

  Serial.println("✓ Game reset complete\n");
}

// ==================== SEND RESET RESPONSE ====================
void sendResetResponse() {
  StaticJsonDocument<512> doc;
  
  doc["event"] = "reset_complete";
  doc["message"] = "All players reset to start";
  
  JsonArray playersArray = doc.createNestedArray("players");
  for (int i = 0; i < NUM_PLAYERS; i++) {
    JsonObject player = playersArray.createNestedObject();
    player["id"] = i;
    player["tile"] = 1;
    player["score"] = 10;
    player["alive"] = true;
  }
  
  doc["board"]["cleared"] = true;
  doc["leds"]["background"] = true;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== SEND STATUS ====================
void sendStatus() {
  StaticJsonDocument<1024> doc;
  
  doc["event"] = "status_report";
  doc["connected"] = deviceConnected;
  doc["waitingForCoin"] = waitingForCoin;
  doc["currentPlayer"] = currentPlayer;
  doc["expectedTile"] = expectedTile;
  doc["undoAvailable"] = lastMove.hasUndo;
  
  JsonArray playersArray = doc.createNestedArray("players");
  for (int i = 0; i < NUM_PLAYERS; i++) {
    JsonObject player = playersArray.createNestedObject();
    player["id"] = i;
    player["tile"] = players[i].currentTile;
    player["score"] = players[i].score;
    player["alive"] = players[i].alive;
    player["coinPlaced"] = players[i].coinPlaced;
  }
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== COIN DETECTION ====================
bool isCoinPresent(int tile) {
  if (tile < 1 || tile > NUM_TILES) return false;
  
  // Hall sensor reads LOW when magnet is near (active LOW with pull-up)
  int readings = 0;
  
  for (int i = 0; i < 5; i++) {
    bool detected = false;
    
    // Check MCP Port B tiles (1, 20, 19, 18, 16, 14, 17, 15)
    for (uint8_t j = 0; j < 8; j++) {
      if (MCP_PORTB_TILES[j] == tile) {
        detected = (mcp.digitalRead(j + 8) == LOW);
        break;
      }
    }
    
    // Check MCP Port A tiles (2, 3, 4, 5, 13, 7, 8, 6)
    if (!detected) {
      for (uint8_t j = 0; j < 8; j++) {
        if (MCP_PORTA_TILES[j] == tile) {
          detected = (mcp.digitalRead(j) == LOW);
          break;
        }
      }
    }
    
    // Check direct ESP32 GPIO tiles (9, 10, 11, 12)
    if (!detected) {
      for (uint8_t j = 0; j < 4; j++) {
        if (DIRECT_GPIO_TILES[j] == tile) {
          detected = (digitalRead(DIRECT_GPIO_PINS[j]) == LOW);
          break;
        }
      }
    }
    
    if (detected) readings++;
    delay(2);
  }
  
  return readings >= 3;
}

void checkCoinPlacement() {
  if (!waitingForCoin || currentPlayer < 0 || expectedTile < 1) return;
  
  if (isCoinPresent(expectedTile)) {
    Serial.println("\n✓ Coin detected!");
    Serial.printf("  Player %d at Tile %d\n", currentPlayer, expectedTile);
    
    players[currentPlayer].coinPlaced = true;
    waitingForCoin = false;
    
    // Stop blinking, show solid color
    setTileColor(expectedTile, PLAYER_COLORS[currentPlayer]);
    strip.show();
    
    saveGameState();
    
    // Send coin placed confirmation
    sendCoinPlacedResponse(currentPlayer, expectedTile);
    
    currentPlayer = -1;
    expectedTile = -1;
  }
}

void sendCoinPlacedResponse(int playerId, int tile, bool hallVerified, const char* message) {
  StaticJsonDocument<384> doc;
  
  doc["event"] = "coin_placed";
  doc["playerId"] = playerId;
  doc["tile"] = tile;
  doc["verified"] = hallVerified;
  doc["hallSensor"] = hallVerified;
  doc["message"] = message;
  
  doc["player"]["score"] = players[playerId].score;
  doc["player"]["alive"] = players[playerId].alive;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

void checkCoinTimeout() {
  if (!waitingForCoin) return;
  
  if (millis() - coinWaitStartTime > COIN_TIMEOUT) {
    Serial.println("\n⏱️  Coin placement timeout!");
    Serial.printf("  Player %d, Tile %d\n", currentPlayer, expectedTile);
    
    waitingForCoin = false;
    
    sendTimeoutResponse(currentPlayer, expectedTile);
    
    renderPlayers();
    
    currentPlayer = -1;
    expectedTile = -1;
  }
}

void sendTimeoutResponse(int playerId, int tile) {
  StaticJsonDocument<256> doc;
  
  doc["event"] = "coin_timeout";
  doc["playerId"] = playerId;
  doc["tile"] = tile;
  doc["timeout"] = COIN_TIMEOUT / 1000;
  doc["message"] = "Coin placement timed out";
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== MISPLACEMENT DETECTION ====================
void scanAllTiles() {
  // In Test Mode 1 we skip misplacement scanning to avoid noisy Hall readings
  if (!PRODUCTION_MODE && TEST_MODE_1) return;

  if (waitingForCoin) return;
  
  bool foundMisplacement = false;
  StaticJsonDocument<1024> doc;
  doc["event"] = "misplacement_scan";
  JsonArray errors = doc.createNestedArray("errors");
  
  for (int tile = 1; tile <= NUM_TILES; tile++) {
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
      foundMisplacement = true;
      JsonObject error = errors.createNestedObject();
      error["tile"] = tile;
      error["tileName"] = BOARD[tile-1].name;
      error["issue"] = "unexpected_coin";
      error["message"] = "Coin found where none should be";
      
      Serial.printf("⚠️  Misplacement: Unexpected coin at Tile %d (%s)\n", 
                    tile, BOARD[tile-1].name);
      
      // Flash red warning
      setTileColor(tile, 0xFF0000);
    } else if (!coinPresent && shouldBePresent) {
      foundMisplacement = true;
      JsonObject error = errors.createNestedObject();
      error["tile"] = tile;
      error["tileName"] = BOARD[tile-1].name;
      error["playerId"] = expectedPlayer;
      error["issue"] = "missing_coin";
      error["message"] = "Expected coin not found";
      
      Serial.printf("⚠️  Misplacement: Missing coin at Tile %d (%s) for Player %d\n", 
                    tile, BOARD[tile-1].name, expectedPlayer);
      
      // Flash red warning
      setTileColor(tile, 0xFF0000);
    }
  }
  
  if (foundMisplacement) {
    strip.show();
    
    String response;
    serializeJson(doc, response);
    sendBLEResponse(response.c_str());
    
    delay(500);
    renderPlayers(); // Restore normal view
  }
}

// ==================== LED ANIMATIONS ====================
void animateMove(int fromTile, int toTile, uint32_t color, int playerId) {
  int step = (toTile > fromTile) ? 1 : -1;
  
  for (int tile = fromTile; tile != toTile + step; tile += step) {
    if (tile < 1 || tile > NUM_TILES) continue;
    
    // Clear previous position (only player's LED)
    if (tile != fromTile) {
      int prevLedIndex = getPlayerLED(tile - step, playerId);
      if (prevLedIndex >= 0) {
        strip.setPixelColor(prevLedIndex, TILE_COLORS[tile - step - 1]);
      }
    }
    
    // Light up current position (only player's LED)
    int currentLedIndex = getPlayerLED(tile, playerId);
    if (currentLedIndex >= 0) {
      strip.setPixelColor(currentLedIndex, color);
    }
    strip.show();
    
    delay(200);
  }
}

// Get LED index for specific player on specific tile
// Tile is 1-based (1-20), playerId is 0-based (0-3 = R, G, B, Y)
int getPlayerLED(int tile, int playerId) {
  if (tile < 1 || tile > NUM_TILES) return -1;
  if (playerId < 0 || playerId >= NUM_PLAYERS) return -1;
  
  // Use lookup table: TILE_LED_START[tile-1] gives first LED of that tile
  // Each tile has 4 consecutive LEDs for R, G, B, Y
  return TILE_LED_START[tile - 1] + playerId;
}

void setTileColor(int tile, uint32_t color) {
  if (tile < 1 || tile > NUM_TILES) return;
  
  // Set all 4 player LEDs for this tile to the same background color
  int startLED = TILE_LED_START[tile - 1];
  for (int i = 0; i < NUM_PLAYERS; i++) {
    strip.setPixelColor(startLED + i, color);
  }
}

void renderBackground() {
  for (int tile = 1; tile <= NUM_TILES; tile++) {
    setTileColor(tile, TILE_COLORS[tile - 1]);
  }
  strip.show();
}

void renderPlayers() {
  renderBackground();
  
  // Light up only each player's specific LED on their current tile
  for (int i = 0; i < NUM_PLAYERS; i++) {
    if (players[i].alive && players[i].coinPlaced) {
      int ledIndex = getPlayerLED(players[i].currentTile, i);
      if (ledIndex >= 0) {
        strip.setPixelColor(ledIndex, players[i].color);
      }
    }
  }
  
  strip.show();
}

// ==================== PLAYER ELIMINATION ANIMATION ====================
void animatePlayerElimination(int playerId) {
  Serial.printf("💀 Animating elimination for Player %d...\n", playerId);
  
  uint32_t playerColor = players[playerId].color;
  
  // Blink the player's LED in all 20 tiles (1 LED per tile) 3 times
  for (int blink = 0; blink < 3; blink++) {
    // Turn ON all player LEDs across board
    for (int tile = 1; tile <= NUM_TILES; tile++) {
      int ledIndex = getPlayerLED(tile, playerId);
      if (ledIndex >= 0) {
        strip.setPixelColor(ledIndex, playerColor);
      }
    }
    strip.show();
    delay(300);
    
    // Turn OFF all player LEDs
    for (int tile = 1; tile <= NUM_TILES; tile++) {
      int ledIndex = getPlayerLED(tile, playerId);
      if (ledIndex >= 0) {
        strip.setPixelColor(ledIndex, 0);  // Black (off)
      }
    }
    strip.show();
    delay(300);
  }
  
  // Final state: All player LEDs OFF permanently
  for (int tile = 1; tile <= NUM_TILES; tile++) {
    int ledIndex = getPlayerLED(tile, playerId);
    if (ledIndex >= 0) {
      strip.setPixelColor(ledIndex, 0);
    }
  }
  strip.show();
  
  Serial.println("✓ Elimination animation complete - player LEDs turned off\n");
}

// ==================== WINNER CELEBRATION ANIMATION ====================
void animateWinner(int winnerId) {
  Serial.printf("🏆 WINNER ANIMATION for Player %d!\n", winnerId);
  
  uint32_t winnerColor = players[winnerId].color;
  
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

void startupAnimation() {
  // Quick rainbow sweep
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.ColorHSV(i * 65536L / NUM_LEDS));
    strip.show();
    delay(10);
  }
  delay(200);
  
  // Clear all LEDs - connection status will handle blinking tile 1
  strip.clear();
  strip.show();
}

// ==================== HELPER FUNCTIONS ====================

void sendBLEResponse(const char* json) {
  if (deviceConnected && pTxCharacteristic != nullptr) {
    pTxCharacteristic->setValue((uint8_t*)json, strlen(json));
    pTxCharacteristic->notify();
    Serial.print("📤 Sent: ");
    Serial.println(json);
  }
}

void sendBLEResponse(String json) {
  sendBLEResponse(json.c_str());
}

void sendErrorResponse(const char* message) {
  StaticJsonDocument<256> doc;
  doc["event"] = "error";
  doc["message"] = message;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response);
}

const char* getTileTypeName(TileType type) {
  switch (type) {
    case TYPE_START:      return "START";
    case TYPE_NORMAL:     return "SAFE";
    case TYPE_CHANCE:     return "CHANCE";
    case TYPE_BONUS:      return "BONUS";
    case TYPE_PENALTY:    return "PENALTY";
    case TYPE_DISASTER:   return "DISASTER";
    case TYPE_WATER_DOCK: return "WATER_DOCK";
    case TYPE_SUPER_DOCK: return "SUPER_DOCK";
    default:              return "UNKNOWN";
  }
}

// ==================== PERSISTENCE ====================
void saveGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    String prefix = "p" + String(i) + "_";
    preferences.putInt((prefix + "tile").c_str(), players[i].currentTile);
    preferences.putInt((prefix + "score").c_str(), players[i].score);
    preferences.putBool((prefix + "alive").c_str(), players[i].alive);
    preferences.putBool((prefix + "coin").c_str(), players[i].coinPlaced);
  }
}

void loadGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    String prefix = "p" + String(i) + "_";
    players[i].currentTile = preferences.getInt((prefix + "tile").c_str(), 1);
    players[i].score = preferences.getInt((prefix + "score").c_str(), 10);
    players[i].alive = preferences.getBool((prefix + "alive").c_str(), true);
    players[i].coinPlaced = preferences.getBool((prefix + "coin").c_str(), false);
    players[i].color = PLAYER_COLORS[i];
  }
  
  // Validate loaded state
  validateGameState();
}

// ==================== STATE VALIDATION ====================
void validateGameState() {
  for (int i = 0; i < activePlayerCount; i++) {
    // Validate tile bounds
    if (players[i].currentTile < 1 || players[i].currentTile > NUM_TILES) {
      Serial.printf("⚠️  Player %d invalid tile %d - resetting to 1\n", i, players[i].currentTile);
      players[i].currentTile = 1;
    }
    
    // Validate score bounds
    if (players[i].score < 0) {
      Serial.printf("⚠️  Player %d negative score %d - setting to 0\n", i, players[i].score);
      players[i].score = 0;
    }
    if (players[i].score > 99) {
      Serial.printf("⚠️  Player %d excessive score %d - capping at 99\n", i, players[i].score);
      players[i].score = 99;
    }
  }
}

// ==================== HEARTBEAT ====================
void sendHeartbeat() {
  if (!waitingForCoin) return;
  
  StaticJsonDocument<256> doc;
  doc["event"] = "heartbeat";
  doc["waiting"]["forCoin"] = waitingForCoin;
  doc["waiting"]["playerId"] = currentPlayer;
  doc["waiting"]["tile"] = expectedTile;
  doc["waiting"]["elapsed"] = (millis() - coinWaitStartTime) / 1000;
  doc["waiting"]["remaining"] = (COIN_TIMEOUT - (millis() - coinWaitStartTime)) / 1000;
  
  String json;
  serializeJson(doc, json);
  sendBLEResponse(json.c_str());
}

// ==================== ACTIVITY MANAGEMENT ====================
void resetIdleTimer() {
  lastActivityTime = millis();
  strip.setBrightness(100);  // Full brightness
}

void checkIdleTimeout() {
  if (millis() - lastActivityTime > IDLE_TIMEOUT) {
    // Dim LEDs to 20% brightness when idle
    strip.setBrightness(20);
    strip.show();
  }
}

// ==================== MAIN LOOP ====================
void loop() {
  // Reset watchdog timer
  esp_task_wdt_reset();
  
  // BLE connection handling
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    isPaired = false;  // Reset pairing on disconnect
    pairTimeout = 0;
    currentConnectionMode = MODE_DISCONNECTED;
    connectionLEDStep = 0;
    Serial.println("[BLE] Disconnected - pairing reset");
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    if (PAIRING_REQUIRED) {
      currentConnectionMode = MODE_PAIRING;
    } else {
      currentConnectionMode = MODE_CONNECTED;
    }
    connectionLEDStep = 0;
    Serial.println("[BLE] Connected - entering pairing/connected mode");
    oldDeviceConnected = deviceConnected;
  }
  
  // Check pairing timeout
  if (PAIRING_REQUIRED && !isPaired && pairTimeout > 0) {
    if (millis() > pairTimeout) {
      Serial.println("[SECURITY] Pairing timeout - resetting");
      pairTimeout = 0;
      // Could disconnect here if desired
    }
  }
  
  // Process command queue
  processCommandQueue();
  
  // Update connection status LEDs if not waiting for coin
  if (!waitingForCoin) {
    updateConnectionStatusLEDs();
  }
  
  // Enhanced blinking animation with timeout warnings
  if (waitingForCoin && expectedTile >= 1) {
    unsigned long elapsed = millis() - coinWaitStartTime;
    unsigned long remaining = COIN_TIMEOUT - elapsed;
    unsigned long blinkInterval = BLINK_INTERVAL;
    uint32_t blinkColor = PLAYER_COLORS[currentPlayer];
    
    // Last 10 seconds: flash faster
    if (remaining < 10000 && remaining > 5000) {
      blinkInterval = 250;  // 250ms blink (was 500ms)
    }
    // Last 5 seconds: very fast flash with RED warning
    else if (remaining < 5000) {
      blinkInterval = 100;  // 100ms blink
      blinkColor = 0xFF0000;  // RED warning
    }
    
    if (millis() - lastBlinkTime > blinkInterval) {
      blinkState = !blinkState;
      lastBlinkTime = millis();
      
      if (blinkState) {
        setTileColor(expectedTile, blinkColor);
      } else {
        setTileColor(expectedTile, 0x000000);
      }
      strip.show();
    }
  }
  
  // Check for coin placement
  checkCoinPlacement();
  
  // Check for timeout
  checkCoinTimeout();
  
  // Send heartbeat every 5 seconds when waiting
  if (waitingForCoin && millis() - lastHeartbeatTime > HEARTBEAT_INTERVAL) {
    sendHeartbeat();
    lastHeartbeatTime = millis();
  }
  
  // Periodic misplacement scan
  if (millis() - lastScanTime > SCAN_INTERVAL) {
    scanAllTiles();
    lastScanTime = millis();
  }
  
  // Check idle timeout for power saving
  checkIdleTimeout();
  
  delay(10);
}
