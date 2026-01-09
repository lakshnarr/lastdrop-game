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
#define LED_OE_PIN 48  // GPIO48 → AHCT125 OE (active LOW)
#define NUM_LEDS 136  // Total LEDs in perimeter strip (0-135, LED 0 removed due to loose connection)
#define NUM_TILES 20
#define NUM_PLAYERS 4

// LED mapping: Each tile has 4 LEDs (R, G, B, Y), with decorative LEDs between tiles
// Tile format: [Red, Green, Blue, Yellow]
// Corner LEDs for connection status: 0, 34, 68, 102
// NOTE: DIN wire soldered between LED 0 DOUT and LED 1 DIN, all positions shifted -1
const int TILE_LED_START[NUM_TILES] = {
  0,   // Tile 1:  LEDs 0-3   (R=0, G=1, B=2, Y=3)
  6,   // Tile 2:  LEDs 6-9
  12,  // Tile 3:  LEDs 12-15
  18,  // Tile 4:  LEDs 18-21
  24,  // Tile 5:  LEDs 24-27
  30,  // Tile 6:  LEDs 30-33
  40,  // Tile 7:  LEDs 40-43
  46,  // Tile 8:  LEDs 46-49
  52,  // Tile 9:  LEDs 52-55
  58,  // Tile 10: LEDs 58-61
  69,  // Tile 11: LEDs 69-72
  74,  // Tile 12: LEDs 74-77
  80,  // Tile 13: LEDs 80-83
  86,  // Tile 14: LEDs 86-89
  92,  // Tile 15: LEDs 92-95
  98,  // Tile 16: LEDs 98-101
  109, // Tile 17: LEDs 109-112
  115, // Tile 18: LEDs 115-118
  121, // Tile 19: LEDs 121-124
  127  // Tile 20: LEDs 127-130
};

const int CORNER_LEDS[4] = {0, 33, 69, 101};  // Connection status indicators

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

// ==================== HALL SENSOR OPERATIONAL FLAGS ====================
bool HALL_SENSOR_OPERATIONAL = false;   // Set to true to use real Hall sensors, false to use timer delay
const unsigned long TURN_DELAY_MS = 5000;   // 5 seconds default turn delay when Hall sensors disabled (configurable via BLE)
unsigned long currentTurnDelayMs = TURN_DELAY_MS;  // Runtime configurable delay

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
  {1,  "Launch Pad",           TYPE_START},
  {2,  "Nature Guardian",      TYPE_BONUS},
  {3,  "Polluting Factory",    TYPE_PENALTY},
  {4,  "Flower Garden",        TYPE_BONUS},
  {5,  "Tree Cutting",         TYPE_DISASTER},
  {6,  "Marsh Swamp",          TYPE_CHANCE},
  {7,  "Recycled Water",       TYPE_WATER_DOCK},
  {8,  "Wasted Water",         TYPE_PENALTY},
  {9,  "River Robber",         TYPE_DISASTER},
  {10, "Lilly Pond",           TYPE_BONUS},
  {11, "Sanctuary Cove",       TYPE_CHANCE},
  {12, "Shrinking Lake",       TYPE_DISASTER},
  {13, "Crystal Glacier",      TYPE_BONUS},
  {14, "Dry City",             TYPE_PENALTY},
  {15, "Rain Harvest",         TYPE_BONUS},
  {16, "Mangrove Trail",       TYPE_CHANCE},
  {17, "Wasted Well",          TYPE_PENALTY},
  {18, "Evergreen Forest",     TYPE_WATER_DOCK},
  {19, "Plant Grower",         TYPE_BONUS},
  {20, "Dirty Water Lane",     TYPE_PENALTY}
};

struct ChanceCard {
  int number;
  const char* description;
  int effect;  // Positive or negative score change
};

// 20 Chance Cards (matching RULEBOOK.md Elimination Mode)
const ChanceCard CHANCE_CARDS[20] = {
  {1,  "Fixed tap leak",                      +2},
  {2,  "Rain harvested",                      +2},
  {3,  "Planted trees",                       +1},
  {4,  "Clouds formed",                       +1},
  {5,  "Preserved riverbank",                 +2},
  {6,  "Cleaned well",                        +2},
  {7,  "Saved plant",                         +1},
  {8,  "Recycled water",                      +1},
  {9,  "Bucket bath",                         +2},
  {10, "Drip irrigation",                     +2},
  {11, "Skip penalty",                         0},  // Special: Immunity
  {12, "Move forward 2",                       0},  // Special: Move 2 tiles
  {13, "Swap with next",                       0},  // Special: Next player plays twice
  {14, "Water Shield",                         0},  // Special: Immunity
  {15, "Left tap running",                    -1},
  {16, "Bottle spilled",                      -1},
  {17, "Pipe burst",                          -3},
  {18, "Climate dries water",                 -2},
  {19, "Sewage contamination",                -2},
  {20, "Wasted papers",                       -3}
};

// ==================== LED COLORS ====================
// Colors matching the new tile scheme:
// Mixed (tile 1), Teal (tiles 2, 19), Orange (tiles 3, 8, 14, 17, 20), 
// Light Green (tiles 4, 10, 13, 15), Red (tiles 5, 9, 12), 
// Purple (tiles 6, 11, 16), Dark Green (tiles 7, 18)
const uint32_t TILE_COLORS[NUM_TILES] = {
  0x40E0D0, // 1: Launch Pad (Mixed - using Teal/Turquoise)
  0x008080, // 2: Nature Guardian (Teal)
  0xFF8C00, // 3: Polluting Factory (Orange)
  0x90EE90, // 4: Flower Garden (Light Green)
  0xFF0000, // 5: Tree Cutting (Red)
  0x800080, // 6: Marsh Swamp (Purple)
  0x228B22, // 7: Recycled Water (Dark Green)
  0xFFA500, // 8: Wasted Water (Orange)
  0xDC143C, // 9: River Robber (Red)
  0x98FB98, // 10: Lilly Pond (Light Green)
  0x9932CC, // 11: Sanctuary Cove (Purple)
  0xB22222, // 12: Shrinking Lake (Red)
  0xADFF2F, // 13: Crystal Glacier (Light Green)
  0xFF6347, // 14: Dry City (Orange)
  0x7FFF00, // 15: Rain Harvest (Light Green)
  0xBA55D3, // 16: Mangrove Trail (Purple)
  0xFF4500, // 17: Wasted Well (Orange)
  0x2E8B57, // 18: Evergreen Forest (Dark Green)
  0x20B2AA, // 19: Plant Grower (Teal)
  0xFF7F50  // 20: Dirty Water Lane (Orange)
};

// Player colors (will be initialized in setup() with strip.Color())
uint32_t PLAYER_COLORS[NUM_PLAYERS];

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
bool blinkState = false;  // For coin waiting animation
bool connectionBlinkState = false;  // For connection status animation
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
      if (connectionBlinkState) {
        strip.clear();
        for (int i = 0; i < 4; i++) {
          strip.setPixelColor(CORNER_LEDS[i], strip.Color(100, 200, 255));  // Light blue
        }
      } else {
        strip.clear();
      }
      connectionBlinkState = !connectionBlinkState;
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
void handleVictory(JsonDocument& doc);
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
  
  // Initialize default player colors using NeoPixel Color() function
  PLAYER_COLORS[0] = strip.Color(255, 0, 0);    // Player 0: Red
  PLAYER_COLORS[1] = strip.Color(0, 0, 255);    // Player 1: Blue
  PLAYER_COLORS[2] = strip.Color(0, 255, 0);    // Player 2: Green
  PLAYER_COLORS[3] = strip.Color(255, 255, 0);  // Player 3: Yellow
  Serial.println("✓ Player colors initialized");
  
  delay(500);  // Settle time
  digitalWrite(LED_OE_PIN, LOW);  // Enable output (active LOW)
  Serial.println("✓ LED OE enabled");

  // Initialize I2C and MCP23017
  Serial.println("======================================");
  Serial.println("Initializing I2C and MCP23017...");
  Serial.printf("  I2C SDA: GPIO%d\n", SDA_PIN);
  Serial.printf("  I2C SCL: GPIO%d\n", SCL_PIN);
  Serial.printf("  MCP Address: 0x%02X\n", MCP_ADDR);
  Serial.println("======================================");
  
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);  // 100kHz for stability
  delay(100);
  
  // I2C bus scan
  Serial.println("Scanning I2C bus...");
  int devicesFound = 0;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    uint8_t error = Wire.endTransmission();
    if (error == 0) {
      Serial.printf("  Found device at 0x%02X\n", addr);
      devicesFound++;
    }
  }
  Serial.printf("I2C scan complete: %d device(s) found\n\n", devicesFound);
  
  if (!mcp.begin_I2C(MCP_ADDR)) {
    Serial.println("✗ ERROR: MCP23017 not found at 0x27!");
    Serial.println("  Check wiring:");
    Serial.println("    - VCC → 3.3V");
    Serial.println("    - GND → Common GND");
    Serial.println("    - SDA → GPIO13");
    Serial.println("    - SCL → GPIO14");
    Serial.println("    - A0/A1/A2 → All HIGH (for 0x27)");
    Serial.println("  Continuing without Hall sensor support...");
    HALL_SENSOR_OPERATIONAL = false;
  } else {
    Serial.println("✓ MCP23017 detected at 0x27");
    
    // Configure MCP Port B (8 tiles) - pins PB0-PB7 (MCP pins 8-15)
    Serial.println("\nConfiguring Port B (8 Hall sensors):");
    for (uint8_t i = 0; i < 8; i++) {
      mcp.pinMode(i + 8, INPUT_PULLUP);  // Port B = pins 8-15
      Serial.printf("  PB%d → Tile %d (INPUT_PULLUP)\n", i, MCP_PORTB_TILES[i]);
    }
    Serial.println("  Port B: All 8 pins configured as INPUT_PULLUP");
    
    // Configure MCP Port A (8 tiles) - pins PA0-PA7 (MCP pins 0-7)
    Serial.println("\nConfiguring Port A (8 Hall sensors):");
    for (uint8_t i = 0; i < 8; i++) {
      mcp.pinMode(i, INPUT_PULLUP);  // Port A = pins 0-7
      Serial.printf("  PA%d → Tile %d (INPUT_PULLUP)\n", i, MCP_PORTA_TILES[i]);
    }
    Serial.println("  Port A: All 8 pins configured as INPUT_PULLUP");
    
    Serial.println("\n✓ MCP23017 configuration complete");
    Serial.println("  A3144 Hall sensors: Active-LOW (output pulls LOW when magnet present)");
    Serial.println("  MCP internal pull-ups: ENABLED (pulls pins HIGH when no magnet)");
  }
  
  // Initialize direct ESP32 GPIO Hall sensors (4 tiles)
  Serial.println("\n======================================");
  Serial.println("Initializing Direct GPIO Hall Sensors");
  Serial.println("======================================");
  for (uint8_t i = 0; i < 4; i++) {
    pinMode(DIRECT_GPIO_PINS[i], INPUT_PULLUP);
    int initialState = digitalRead(DIRECT_GPIO_PINS[i]);
    Serial.printf("  GPIO%d → Tile %d (INPUT_PULLUP) | Initial: %s\n", 
                  DIRECT_GPIO_PINS[i], DIRECT_GPIO_TILES[i], 
                  initialState ? "HIGH" : "LOW");
  }
  
  Serial.println("\n✓ All Hall sensors initialized");
  Serial.printf("\nHall Sensor Mode: %s\n", 
                HALL_SENSOR_OPERATIONAL ? "ENABLED (waiting for coin)" : "DISABLED (timer delay)");
  if (!HALL_SENSOR_OPERATIONAL) {
    Serial.printf("  Turn Delay: %lu seconds\n", currentTurnDelayMs / 1000);
  }
  Serial.println();

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
  } else if (strcmp(command, "victory") == 0) {
    handleVictory(doc);
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
      uint32_t hexValue = (uint32_t)strtol(colorHex, NULL, 16);
      
      // Extract R, G, B components
      uint8_t r = (hexValue >> 16) & 0xFF;
      uint8_t g = (hexValue >> 8) & 0xFF;
      uint8_t b = hexValue & 0xFF;
      
      // Use NeoPixel's Color() function for proper GRB format
      uint32_t color = strip.Color(r, g, b);
      players[i].color = color;
      
      Serial.printf("  Player %d color: #%s → R=%d G=%d B=%d → 0x%08X\n", 
                    i, colorHex, r, g, b, color);
      
      // Test: Light up player's LED immediately to verify color
      int testLed = getPlayerLED(1, i);  // Show on tile 1
      if (testLed >= 0) {
        strip.setPixelColor(testLed, color);
        strip.show();
        delay(300);  // Brief flash to verify
        Serial.printf("  → Test LED %d lit with configured color\n", testLed);
      }
    }
  }
  
  // Check for Hall sensor mode configuration
  if (doc.containsKey("hallSensorMode")) {
    HALL_SENSOR_OPERATIONAL = doc["hallSensorMode"];
    Serial.printf("  Hall Sensor Mode: %s\n", 
                  HALL_SENSOR_OPERATIONAL ? "ENABLED" : "DISABLED");
  }
  
  // Check for turn delay configuration (in seconds)
  if (doc.containsKey("turnDelaySeconds")) {
    int delaySeconds = doc["turnDelaySeconds"];
    if (delaySeconds >= 1 && delaySeconds <= 300) {  // 1s to 5 minutes
      currentTurnDelayMs = delaySeconds * 1000;
      Serial.printf("  Turn Delay: %d seconds\n", delaySeconds);
    }
  }
  
  // Turn off LEDs for inactive players
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = strip.Color(0, 0, 0);  // Black (off)
  }
  
  // Send confirmation
  StaticJsonDocument<384> response;
  response["event"] = "config_complete";
  response["playerCount"] = activePlayerCount;
  response["hallSensorMode"] = HALL_SENSOR_OPERATIONAL;
  response["turnDelaySeconds"] = currentTurnDelayMs / 1000;
  
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
      // ECO SAVE (+1), SHIELD (+1), or bonus based on tile
      if (newTile == 2) {
        scoreChange = +1;
        Serial.println("  Effect: SHIELD +1 point (Nature Guardian, +Immunity)");
      } else if (newTile == 4 || newTile == 10) {
        scoreChange = +1;
        Serial.println("  Effect: ECO SAVE +1 point");
      } else if (newTile == 13 || newTile == 15) {
        scoreChange = +2;
        Serial.println("  Effect: ECO SAVE +2 points");
      } else if (newTile == 19) {
        scoreChange = +1;
        Serial.println("  Effect: SHIELD +1 point (Plant Grower)");
      } else {
        scoreChange = +1;
        Serial.println("  Effect: BONUS +1 point");
      }
      break;
      
    case TYPE_PENALTY:
      // LOSS tiles: -1 or -2 based on tile
      if (newTile == 8) {
        scoreChange = -1;
        Serial.println("  Effect: LOSS -1 point (Wasted Water)");
      } else {
        scoreChange = -2;
        Serial.println("  Effect: LOSS -2 points");
      }
      break;
      
    case TYPE_DISASTER:
      // GREAT CRISIS tiles: -3, -4, or -5 based on tile
      if (newTile == 5) {
        scoreChange = -3;
        Serial.println("  Effect: GREAT CRISIS -3 points (Tree Cutting)");
      } else if (newTile == 9) {
        scoreChange = -5;
        Serial.println("  Effect: GREAT CRISIS -5 points (River Robber)");
      } else if (newTile == 12) {
        scoreChange = -4;
        Serial.println("  Effect: GREAT CRISIS -4 points (Shrinking Lake)");
      } else {
        scoreChange = -3;
        Serial.println("  Effect: GREAT CRISIS -3 points");
      }
      break;
      
    case TYPE_WATER_DOCK:
      // MIGHTY SAVE tiles: +3 or +4 based on tile
      if (newTile == 7) {
        scoreChange = +3;
        Serial.println("  Effect: MIGHTY SAVE +3 points (Recycled Water)");
      } else if (newTile == 18) {
        scoreChange = +4;
        Serial.println("  Effect: MIGHTY SAVE +4 points (Evergreen Forest)");
      } else {
        scoreChange = +3;
        Serial.println("  Effect: MIGHTY SAVE +3 points");
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
  animateMove(currentTile, newTile, players[playerId].color, playerId);
  
  // Restore all player LEDs after animation
  renderPlayers();

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
  } else if (HALL_SENSOR_OPERATIONAL) {
    // Hall Sensor Mode: Wait for physical coin detection
    Serial.println("\n⏳ HALL SENSOR MODE - Waiting for coin placement...");
    Serial.printf("  Player %d must place coin on Tile %d\n", playerId, newTile);
    Serial.println("  Hall sensor will detect magnet automatically");
    
    waitingForCoin = true;
    coinWaitStartTime = millis();
    saveGameState();

    sendRollResponse(playerId, currentTile, newTile, tile, scoreChange, oldScore, newScore,
                     chanceCardNumber, chanceCardDesc, players[playerId].alive, true);

    Serial.println("✓ Roll processed, waiting for Hall sensor confirmation\n");
  } else {
    // Timer Delay Mode: Auto-confirm after delay
    Serial.println("\n⏱️  TIMER DELAY MODE - No Hall sensor wait");
    Serial.printf("  Turn delay: %lu seconds\n", currentTurnDelayMs / 1000);
    Serial.printf("  ESP will auto-confirm coin after %lu ms\n", currentTurnDelayMs);
    
    waitingForCoin = true;  // Still set flag for timing logic
    coinWaitStartTime = millis();
    saveGameState();

    sendRollResponse(playerId, currentTile, newTile, tile, scoreChange, oldScore, newScore,
                     chanceCardNumber, chanceCardDesc, players[playerId].alive, false);

    Serial.println("✓ Roll processed, timer started\n");
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
  animateMove(fromTile, toTile, players[playerId].color, playerId);
  
  // Restore all player LEDs after animation
  renderPlayers();
  
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

// ==================== HANDLE VICTORY ====================
void handleVictory(JsonDocument& doc) {
  Serial.println("\n🏆 Processing Victory Command...");
  resetIdleTimer();
  
  int winnerId = doc["winnerId"] | 0;
  const char* winnerColor = doc["winnerColor"];
  const char* winnerName = doc["winnerName"];
  
  Serial.printf("  Winner ID: %d\n", winnerId);
  Serial.printf("  Winner Color: %s\n", winnerColor ? winnerColor : "default");
  Serial.printf("  Winner Name: %s\n", winnerName ? winnerName : "unknown");
  
  // Update winner color if provided
  if (winnerColor != nullptr && strlen(winnerColor) >= 6) {
    long colorValue = strtol(winnerColor, NULL, 16);
    uint8_t r = (colorValue >> 16) & 0xFF;
    uint8_t g = (colorValue >> 8) & 0xFF;
    uint8_t b = colorValue & 0xFF;
    players[winnerId].color = strip.Color(r, g, b);
    Serial.printf("  Updated winner color to: 0x%06X\n", players[winnerId].color);
  }
  
  // Trigger the winner animation
  animateWinner(winnerId);
  
  // Send victory acknowledgment
  StaticJsonDocument<256> response;
  response["event"] = "victory_complete";
  response["winnerId"] = winnerId;
  response["winnerName"] = winnerName ? winnerName : "Player";
  response["success"] = true;
  
  String output;
  serializeJson(response, output);
  pTxCharacteristic->setValue(output.c_str());
  pTxCharacteristic->notify();
  
  Serial.println("✓ Victory animation complete\n");
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
  if (tile < 1 || tile > NUM_TILES) {
    Serial.printf("[Hall] Invalid tile: %d\n", tile);
    return false;
  }
  
  // Hall sensor reads LOW when magnet is near (active LOW with pull-up)
  int readings = 0;
  String sensorType = "";
  int sensorPin = -1;
  
  for (int i = 0; i < 5; i++) {
    bool detected = false;
    
    // Check MCP Port B tiles (1, 20, 19, 18, 16, 14, 17, 15)
    for (uint8_t j = 0; j < 8; j++) {
      if (MCP_PORTB_TILES[j] == tile) {
        bool state = (mcp.digitalRead(j + 8) == LOW);
        detected = state;
        sensorType = "MCP_PORTB";
        sensorPin = j;
        if (i == 0) {  // Log first reading only
          Serial.printf("[Hall] Tile %d → MCP PB%d: %s\n", tile, j, state ? "LOW (magnet)" : "HIGH (no magnet)");
        }
        break;
      }
    }
    
    // Check MCP Port A tiles (2, 3, 4, 5, 13, 7, 8, 6)
    if (!detected && sensorType == "") {
      for (uint8_t j = 0; j < 8; j++) {
        if (MCP_PORTA_TILES[j] == tile) {
          bool state = (mcp.digitalRead(j) == LOW);
          detected = state;
          sensorType = "MCP_PORTA";
          sensorPin = j;
          if (i == 0) {  // Log first reading only
            Serial.printf("[Hall] Tile %d → MCP PA%d: %s\n", tile, j, state ? "LOW (magnet)" : "HIGH (no magnet)");
          }
          break;
        }
      }
    }
    
    // Check direct ESP32 GPIO tiles (9, 10, 11, 12)
    if (!detected && sensorType == "") {
      for (uint8_t j = 0; j < 4; j++) {
        if (DIRECT_GPIO_TILES[j] == tile) {
          bool state = (digitalRead(DIRECT_GPIO_PINS[j]) == LOW);
          detected = state;
          sensorType = "ESP32_GPIO";
          sensorPin = DIRECT_GPIO_PINS[j];
          if (i == 0) {  // Log first reading only
            Serial.printf("[Hall] Tile %d → GPIO%d: %s\n", tile, DIRECT_GPIO_PINS[j], state ? "LOW (magnet)" : "HIGH (no magnet)");
          }
          break;
        }
      }
    }
    
    if (detected) readings++;
    delay(2);
  }
  
  bool coinPresent = (readings >= 3);
  Serial.printf("[Hall] Tile %d result: %d/5 readings LOW → %s\n", 
                tile, readings, coinPresent ? "COIN DETECTED" : "NO COIN");
  
  return coinPresent;
}

void checkCoinPlacement() {
  if (!waitingForCoin || currentPlayer < 0 || expectedTile < 1) return;
  
  unsigned long elapsed = millis() - coinWaitStartTime;
  
  if (HALL_SENSOR_OPERATIONAL) {
    // Hall Sensor Mode: Check for physical coin
    if (isCoinPresent(expectedTile)) {
      Serial.println("\n🧲 COIN DETECTED VIA HALL SENSOR!");
      Serial.printf("  Player %d at Tile %d\n", currentPlayer, expectedTile);
      Serial.printf("  Detection time: %lu ms\n", elapsed);
      
      players[currentPlayer].coinPlaced = true;
      waitingForCoin = false;
      
      // Stop blinking, show solid color
      setTileColor(expectedTile, PLAYER_COLORS[currentPlayer]);
      strip.show();
      
      saveGameState();
      
      // Send coin placed confirmation
      sendCoinPlacedResponse(currentPlayer, expectedTile, true, "Hall sensor detected magnet");
      
      currentPlayer = -1;
      expectedTile = -1;
    }
  } else {
    // Timer Delay Mode: Auto-confirm after delay
    if (elapsed >= currentTurnDelayMs) {
      Serial.println("\n⏱️  TURN DELAY COMPLETE - AUTO-CONFIRMING COIN!");
      Serial.printf("  Player %d at Tile %d\n", currentPlayer, expectedTile);
      Serial.printf("  Delay duration: %lu ms (%lu seconds)\n", 
                    currentTurnDelayMs, currentTurnDelayMs / 1000);
      
      players[currentPlayer].coinPlaced = true;
      waitingForCoin = false;
      
      // Stop blinking, show solid color
      setTileColor(expectedTile, PLAYER_COLORS[currentPlayer]);
      strip.show();
      
      saveGameState();
      
      // Send coin placed confirmation
      sendCoinPlacedResponse(currentPlayer, expectedTile, false, 
                             "Timer delay complete - coin auto-confirmed");
      
      currentPlayer = -1;
      expectedTile = -1;
    }
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
  // Always animate forward (clockwise) on circular board: 1→2→...→20→1→2...
  int currentTile = fromTile;
  
  while (currentTile != toTile) {
    // Clear previous position (turn off player's LED)
    int prevLedIndex = getPlayerLED(currentTile, playerId);
    if (prevLedIndex >= 0) {
      strip.setPixelColor(prevLedIndex, 0);  // Turn off (black)
    }
    
    // Move forward (with wraparound: 20→1)
    currentTile++;
    if (currentTile > NUM_TILES) {
      currentTile = 1;  // Wrap from tile 20 to tile 1
    }
    
    // Light up new position (only player's LED)
    int currentLedIndex = getPlayerLED(currentTile, playerId);
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
  // Turn OFF all LEDs (blank board)
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, 0);  // Black (off)
  }
  strip.show();
}

void renderPlayers() {
  renderBackground();  // Clear all LEDs first
  
  // Light up ALL active/alive players' LEDs on their current tiles
  for (int i = 0; i < activePlayerCount; i++) {
    if (players[i].alive) {
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
