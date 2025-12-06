/*
 * Last Drop - ESP32 NO HARDWARE Version (BLE Game Logic Only)
 * 
 * This firmware implements COMPLETE game logic WITHOUT physical hardware
 * - NO LED strip required (all LED code disabled)
 * - NO Hall sensors required (auto-confirms coin placement)
 * - FULL BLE communication with Android
 * - 20-tile board with tile types (START, NORMAL, CHANCE, BONUS, PENALTY)
 * - 20 chance cards with random selection
 * - Score calculation and tracking
 * - Comprehensive reporting back to Android
 * - Undo functionality with state restoration
 * - Full reset capabilities
 * 
 * Hardware:
 * - ESP32 Dev Board ONLY (no additional components)
 * 
 * Use Case: Testing Android app without physical board
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>
// #include <Adafruit_NeoPixel.h>  // REMOVED - No LEDs
#include <Preferences.h>
#include <ArduinoJson.h>
#include <esp_task_wdt.h>
#include <queue>

// ==================== HARDWARE CONFIGURATION ====================
#define NUM_TILES 20
#define NUM_PLAYERS 4

// ==================== BLE CONFIGURATION ====================
#define SERVICE_UUID        "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_RX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define CHARACTERISTIC_UUID_TX "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

// ==================== BOARD IDENTIFICATION ====================
// IMPORTANT: Change these values for each board you manufacture
#define BOARD_UNIQUE_ID "LASTDROP-NOHW-0001"  // Unique ID for this board
#define BOARD_VERSION "2.0-NOHW"               // Firmware version
#define MANUFACTURER_DATA "LASTDROP-BOARD-NOHW"  // Identifier for board discovery

// ==================== PAIRING PASSWORD ====================
#define BOARD_PASSWORD "654321"  // Default PIN
#define PAIRING_REQUIRED true     // Password protection enabled

// ==================== SECURITY CONFIGURATION ====================
#define BLE_PAIRING_ENABLED true    // PIN pairing enabled
#define BLE_PAIRING_PIN 654321      // Default PIN: 654321
#define MAC_FILTERING_ENABLED false // Set true to enable MAC whitelist

// Android device MAC address whitelist
const String TRUSTED_ANDROID_MACS[] = {
  "AA:BB:CC:DD:EE:FF",
  "11:22:33:44:55:66"
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
  int effect;
};

// 20 Chance Cards (matching RULEBOOK.md)
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
  {11, "Skip next penalty",                        0},
  {12, "Move forward 2 tiles",                     0},
  {13, "Swap positions with next player",          0},
  {14, "Water Shield (next damage=0)",             0},
  {15, "You left tap running",                    -1},
  {16, "Your bottle spilled",                     -1},
  {17, "Pipe burst nearby",                       -3},
  {18, "Heat wave dries water",                   -2},
  {19, "Sewage contamination",                    -2},
  {20, "Flood washed away water",                 -3}
};

// ==================== GLOBAL OBJECTS ====================
Preferences preferences;

BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// ==================== GAME STATE ====================
struct PlayerState {
  int currentTile;
  int score;
  bool alive;
  bool coinPlaced;
  uint32_t color;
  int previousTile;
  int previousScore;
};

PlayerState players[NUM_PLAYERS];
int activePlayerCount = 2;
int currentPlayer = -1;
int expectedTile = -1;
bool waitingForCoin = false;
unsigned long coinWaitStartTime = 0;
const unsigned long AUTO_COIN_DELAY = 2000; // 2 seconds auto-confirm

// Security: Pairing state
bool isPaired = false;
unsigned long pairTimeout = 0;
const unsigned long PAIR_TIMEOUT_MS = 30000;

// Board Settings
String boardPassword = BOARD_PASSWORD;
String boardNickname = BOARD_UNIQUE_ID;

// Undo state tracking
struct UndoState {
  bool hasUndo;
  int playerId;
  int fromTile;
  int toTile;
  int scoreChange;
  int chanceCardNumber;
} lastMove;

// ==================== COMMAND QUEUE ====================
std::queue<String> commandQueue;
bool processingCommand = false;

// ==================== ACTIVITY TRACKING ====================
unsigned long lastActivityTime = 0;
const unsigned long IDLE_TIMEOUT = 300000;

// ==================== HELPER FUNCTIONS (Forward Declarations) ====================
void handleBLECommand(const char* jsonStr);
void handleRoll(JsonDocument& doc);
void handlePair(JsonDocument& doc);
void handleUnpair();
void handleConfig(JsonDocument& doc);
void handleUpdateSettings(JsonDocument& doc);
bool isTrustedDevice(BLEAddress address);
void sendBLEResponse(const char* json);
void sendBLEResponse(String json);
void sendErrorResponse(const char* message);
void sendPairResponse(bool success, const char* message);
void loadGameState();
void resetIdleTimer();
void validateGameState();
void handleUndo(JsonDocument& doc);
void handleReset();
void sendStatus();
void saveGameState();
void sendRollResponse(int playerId, int fromTile, int toTile, const TileDefinition& tile, int scoreChange, int oldScore, int newScore, int chanceCard, const char* chanceDesc, bool alive);
void sendUndoResponse(int playerId, int fromTile, int toTile, int score, bool alive);
void sendResetResponse();
void sendCoinPlacedResponse(int playerId, int tile);
const char* getTileTypeName(TileType type);

// ==================== BLE CALLBACKS ====================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      Serial.println("\n========== BLE CONNECTION STARTED ==========");
      Serial.print("[DEBUG] Timestamp: ");
      Serial.println(millis());
      
      deviceConnected = true;
      Serial.println("[DEBUG] Step 1/5: deviceConnected = true");
      Serial.println("✓ BLE Client Connected");
      
      Serial.println("[DEBUG] Step 2/5: Waiting for stable connection (1000ms)...");
      delay(1000);
      
      Serial.println("[DEBUG] Step 3/5: Sending ready event to Android...");
      sendBLEResponse("{\"event\":\"ready\",\"message\":\"ESP32 No-Hardware Mode Ready\",\"firmware\":\"v2.0-nohw\"}");
      Serial.println("[DEBUG] Step 4/5: Ready event sent successfully");
      
      Serial.println("[DEBUG] Step 5/5: Connection established successfully!");
      Serial.println("========================================\n");
    };

    void onDisconnect(BLEServer* pServer) {
      Serial.println("\n========== BLE DISCONNECTION ==========");
      Serial.print("[DEBUG] Timestamp: ");
      Serial.println(millis());
      
      deviceConnected = false;
      Serial.println("[DEBUG] deviceConnected = false");
      Serial.println("✗ BLE Client Disconnected");
      
      waitingForCoin = false;
      expectedTile = -1;
      Serial.println("[DEBUG] Game state cleared (waitingForCoin=false)");
      
      Serial.println("[DEBUG] Waiting 500ms before restart...");
      delay(500);
      
      Serial.println("[DEBUG] Restarting BLE advertising...");
      pServer->startAdvertising();
      Serial.println("🔄 BLE advertising restarted successfully");
      Serial.println("======================================\n");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      Serial.println("\n[DEBUG] onWrite() callback triggered");
      Serial.print("[DEBUG] Timestamp: ");
      Serial.println(millis());
      
      String rxValue = pCharacteristic->getValue().c_str();
      Serial.print("[DEBUG] Received data length: ");
      Serial.println(rxValue.length());
      
      if (rxValue.length() > 0) {
        Serial.println("📨 Received BLE Command:");
        Serial.println(rxValue);
        Serial.println("[DEBUG] Queuing command for processing...");
        handleBLECommand(rxValue.c_str());
        Serial.println("[DEBUG] Command queued successfully\n");
      } else {
        Serial.println("[WARN] Empty command received, ignoring\n");
      }
    }
};

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n\n\n========================================");
  Serial.println("Last Drop ESP32 NO-HARDWARE Mode v2.0");
  Serial.println("BLE Game Logic Only (No LEDs/Sensors)");
  Serial.println("Serial Monitor Debug Logging Enabled");
  Serial.println("========================================");
  Serial.println("[DEBUG] Setup started...");
  Serial.print("[DEBUG] Free heap: ");
  Serial.println(ESP.getFreeHeap());
  Serial.println();

  Serial.println("[DEBUG] Initializing watchdog timer...");
  esp_task_wdt_config_t wdt_config = {
    .timeout_ms = 30000,
    .idle_core_mask = 0,
    .trigger_panic = true
  };
  esp_task_wdt_init(&wdt_config);
  esp_task_wdt_add(NULL);
  Serial.println("✓ Watchdog enabled (30s timeout)");

  Serial.println("\n[DEBUG] Loading board settings from NVS...");
  preferences.begin("lastdrop", false);
  boardPassword = preferences.getString("password", BOARD_PASSWORD);
  boardNickname = preferences.getString("nickname", BOARD_UNIQUE_ID);
  Serial.printf("Board Password: %s\n", boardPassword.c_str());
  Serial.printf("Board Nickname: %s\n", boardNickname.c_str());
  
  Serial.println("\n[DEBUG] Loading game state...");
  loadGameState();

  Serial.println("\n[DEBUG] Starting BLE initialization...");
  initBLE();

  Serial.println("\n[DEBUG] Initializing game state...");
  initializeGameState();

  Serial.println("\n========================================");
  Serial.println("✓ SETUP COMPLETE - System Ready");
  Serial.println("========================================");
  Serial.println("📡 Waiting for BLE connection...");
  Serial.println("⚠️  NO HARDWARE MODE - Coin auto-confirmed after 2s");
  Serial.print("[DEBUG] Total setup time: ");
  Serial.print(millis());
  Serial.println("ms");
  Serial.print("[DEBUG] Free heap after setup: ");
  Serial.println(ESP.getFreeHeap());
  Serial.println("\n[DEBUG] Entering main loop...\n");
}

// ==================== GAME INITIALIZATION ====================
void initializeGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    players[i].currentTile = 1;
    players[i].score = 10;
    players[i].alive = true;
    players[i].coinPlaced = false;
    players[i].color = 0xFFFFFF;
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
    if (addrStr.equalsIgnoreCase(TRUSTED_ANDROID_MACS[i])) return true;
  }
  return false;
}

// ==================== BLE INITIALIZATION ====================
void initBLE() {
  Serial.println("[DEBUG] BLE Device initialization...");
  BLEDevice::init(boardNickname.c_str());
  
  Serial.print("ESP32 MAC Address: ");
  Serial.println(BLEDevice::getAddress().toString().c_str());
  Serial.printf("Board ID: %s\n", BOARD_UNIQUE_ID);
  Serial.printf("Board Version: %s\n", BOARD_VERSION);

  #if BLE_PAIRING_ENABLED
    Serial.println("[DEBUG] Configuring BLE security...");
    BLESecurity *pSecurity = new BLESecurity();
    pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
    pSecurity->setCapability(ESP_IO_CAP_OUT);
    pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
    uint32_t passkey = BLE_PAIRING_PIN;
    esp_ble_gap_set_security_param(ESP_BLE_SM_SET_STATIC_PASSKEY, &passkey, sizeof(uint32_t));
    Serial.printf("✓ BLE Security Enabled - PIN: %d\n", BLE_PAIRING_PIN);
  #endif

  Serial.println("[DEBUG] Creating BLE server...");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  Serial.println("[DEBUG] Creating Nordic UART service...");
  BLEService *pService = pServer->createService(SERVICE_UUID);

  Serial.println("[DEBUG] Creating TX characteristic (notify)...");
  pTxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());

  Serial.println("[DEBUG] Creating RX characteristic (write)...");
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  Serial.println("[DEBUG] Starting service...");
  pService->start();

  Serial.println("[DEBUG] Configuring BLE advertising...");
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  BLEAdvertisementData advData;
  advData.setName(boardNickname.c_str());
  advData.setManufacturerData(MANUFACTURER_DATA);
  pAdvertising->setAdvertisementData(advData);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMaxPreferred(0x12);
  
  Serial.println("[DEBUG] Starting BLE advertising...");
  BLEDevice::startAdvertising();
  
  Serial.println("✓ BLE Service Started\n");
  Serial.println("📡 Broadcasting as: " + boardNickname);
  Serial.println("🔐 Pairing PIN: 654321");
  Serial.println("⚠️  If connection fails:");
  Serial.println("   1. Forget/unpair device in Android Bluetooth settings");
  Serial.println("   2. Clear app data/cache");
  Serial.println("   3. Reconnect and enter PIN when prompted\n");
}

// ==================== BLE COMMAND HANDLER ====================
void handleBLECommand(const char* jsonStr) {
  Serial.println("[DEBUG] handleBLECommand() called");
  Serial.print("[DEBUG] Queue size before: ");
  Serial.println(commandQueue.size());
  commandQueue.push(String(jsonStr));
  Serial.print("[DEBUG] Queue size after: ");
  Serial.println(commandQueue.size());
}

void processCommandQueue() {
  if (processingCommand || commandQueue.empty()) return;
  
  Serial.println("[DEBUG] processCommandQueue() - Processing next command");
  processingCommand = true;
  String cmdStr = commandQueue.front();
  commandQueue.pop();
  Serial.print("[DEBUG] Commands remaining in queue: ");
  Serial.println(commandQueue.size());
  
  Serial.println("[DEBUG] Parsing JSON...");
  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, cmdStr);

  if (error) {
    Serial.print("❌ JSON Parse Error: ");
    Serial.println(error.c_str());
    sendErrorResponse("Invalid JSON format");
    processingCommand = false;
    return;
  }
  Serial.println("[DEBUG] JSON parsed successfully");

  const char* command = doc["command"];
  Serial.print("[DEBUG] Command type: ");
  Serial.println(command ? command : "NULL");
  
  if (strcmp(command, "roll") == 0) {
    Serial.println("[DEBUG] Routing to handleRoll()");
    handleRoll(doc);
  } else if (strcmp(command, "undo") == 0) {
    Serial.println("[DEBUG] Routing to handleUndo()");
    handleUndo(doc);
  } else if (strcmp(command, "reset") == 0) {
    Serial.println("[DEBUG] Routing to handleReset()");
    handleReset();
  } else if (strcmp(command, "pair") == 0) {
    Serial.println("[DEBUG] Routing to handlePair()");
    handlePair(doc);
  } else if (strcmp(command, "unpair") == 0) {
    Serial.println("[DEBUG] Routing to handleUnpair()");
    handleUnpair();
  } else if (strcmp(command, "config") == 0) {
    Serial.println("[DEBUG] Routing to handleConfig()");
    handleConfig(doc);
  } else if (strcmp(command, "update_settings") == 0) {
    Serial.println("[DEBUG] Routing to handleUpdateSettings()");
    handleUpdateSettings(doc);
  } else if (strcmp(command, "status") == 0) {
    Serial.println("[DEBUG] Routing to sendStatus()");
    sendStatus();
  } else {
    Serial.println("[ERROR] Unknown command received!");
    sendErrorResponse("Unknown command");
  }
  
  Serial.println("[DEBUG] Command processing complete\n");
  processingCommand = false;
}

// ==================== HANDLE PAIRING ====================
void handlePair(JsonDocument& doc) {
  Serial.println("\n🔐 Processing PAIR command...");
  resetIdleTimer();
  
  const char* password = doc["password"];
  
  if (!PAIRING_REQUIRED) {
    isPaired = true;
    sendPairResponse(true, "Pairing not required");
    Serial.println("✓ Pairing not required");
    return;
  }
  
  if (password == nullptr || strlen(password) == 0) {
    sendPairResponse(false, "Password required");
    return;
  }
  
  if (strcmp(password, boardPassword.c_str()) == 0) {
    isPaired = true;
    pairTimeout = 0;
    Serial.println("✓ Password correct - device paired");
    sendPairResponse(true, "Paired successfully");
  } else {
    Serial.println("✗ Incorrect password");
    sendPairResponse(false, "Incorrect password");
  }
}

void handleUnpair() {
  Serial.println("\n🔓 Processing UNPAIR command...");
  resetIdleTimer();
  isPaired = false;
  pairTimeout = 0;
  
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
  resetIdleTimer();
  
  int playerCount = doc["playerCount"];
  JsonArray colorsArray = doc["colors"];
  
  if (playerCount < 2 || playerCount > NUM_PLAYERS) {
    sendErrorResponse("Invalid player count");
    return;
  }
  
  activePlayerCount = playerCount;
  Serial.printf("  Active Players: %d\n", activePlayerCount);
  
  for (int i = 0; i < activePlayerCount; i++) {
    if (i < colorsArray.size()) {
      const char* colorHex = colorsArray[i];
      uint32_t color = (uint32_t)strtol(colorHex, NULL, 16);
      players[i].color = color;
      Serial.printf("  Player %d color: #%s\n", i, colorHex);
    }
  }
  
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
    players[i].color = 0x000000;
  }
  
  StaticJsonDocument<256> response;
  response["event"] = "config_complete";
  response["playerCount"] = activePlayerCount;
  String output;
  serializeJson(response, output);
  sendBLEResponse(output);
  Serial.println("✓ Config applied\n");
}

// ==================== HANDLE UPDATE SETTINGS ====================
void handleUpdateSettings(JsonDocument& doc) {
  Serial.println("\n⚙️ Processing UPDATE_SETTINGS command...");
  resetIdleTimer();
  
  if (PAIRING_REQUIRED && !isPaired) {
    sendErrorResponse("Device not paired");
    return;
  }
  
  bool updated = false;
  
  if (doc.containsKey("password")) {
    const char* newPassword = doc["password"];
    if (strlen(newPassword) >= 6) {
      boardPassword = String(newPassword);
      preferences.putString("password", boardPassword);
      updated = true;
    }
  }
  
  if (doc.containsKey("nickname")) {
    const char* newNickname = doc["nickname"];
    if (strlen(newNickname) > 0 && strlen(newNickname) <= 30) {
      boardNickname = String(newNickname);
      preferences.putString("nickname", boardNickname);
      updated = true;
    }
  }
  
  if (updated) {
    StaticJsonDocument<512> response;
    response["event"] = "settings_updated";
    response["password"] = boardPassword;
    response["nickname"] = boardNickname;
    response["restartRequired"] = doc.containsKey("nickname");
    String output;
    serializeJson(response, output);
    sendBLEResponse(output);
  }
}

// ==================== HANDLE DICE ROLL ====================
void handleRoll(JsonDocument& doc) {
  Serial.println("\n🎲 Processing Dice Roll...");
  Serial.print("[DEBUG] Timestamp: ");
  Serial.println(millis());
  resetIdleTimer();
  validateGameState();
  
  if (PAIRING_REQUIRED && !isPaired) {
    Serial.println("[ERROR] Device not paired - rejecting roll");
    sendErrorResponse("Device not paired");
    return;
  }
  
  int playerId = doc["playerId"];
  int diceValue = doc["diceValue"];
  
  if (playerId < 0 || playerId >= activePlayerCount) {
    sendErrorResponse("Invalid player ID");
    Serial.printf("[ERROR] Invalid player ID: %d\n", playerId);
    return;
  }

  Serial.printf("  Player: %d, Dice: %d\n", playerId, diceValue);

  players[playerId].previousTile = players[playerId].currentTile;
  players[playerId].previousScore = players[playerId].score;

  int currentTile = players[playerId].currentTile;
  int newTile = currentTile + diceValue;
  bool completedLap = false;
  
  if (newTile > NUM_TILES) {
    Serial.println("  >> LAP COMPLETED! +5 BONUS");
    completedLap = true;
    newTile = newTile - NUM_TILES;
  }
  if (newTile < 1) newTile = 1;

  const TileDefinition& tile = BOARD[newTile - 1];
  Serial.printf("  Tile %d → %d: %s\n", currentTile, newTile, tile.name);
  
  int scoreChange = 0;
  int chanceCardNumber = 0;
  const char* chanceCardDesc = "";

  switch (tile.type) {
    case TYPE_BONUS: scoreChange = +1; break;
    case TYPE_PENALTY:
      scoreChange = (newTile == 2 || newTile == 4) ? -1 : -2;
      break;
    case TYPE_DISASTER:
      if (newTile == 12) scoreChange = -2;
      else if (newTile == 5 || newTile == 10) scoreChange = -3;
      else scoreChange = -4;
      break;
    case TYPE_WATER_DOCK:
      if (newTile == 15) scoreChange = +1;
      else if (newTile == 11) scoreChange = +2;
      else scoreChange = +3;
      break;
    case TYPE_SUPER_DOCK: scoreChange = +4; break;
    case TYPE_CHANCE: {
      int cardIndex = random(20);
      const ChanceCard& card = CHANCE_CARDS[cardIndex];
      chanceCardNumber = card.number;
      chanceCardDesc = card.description;
      scoreChange = card.effect;
      Serial.printf("  Chance Card #%d: %s\n", card.number, card.description);
      break;
    }
    default: break;
  }
  
  if (completedLap) scoreChange += 5;

  int oldScore = players[playerId].score;
  int newScore = oldScore + scoreChange;
  if (newScore < 0) newScore = 0;
  
  players[playerId].score = newScore;
  players[playerId].currentTile = newTile;
  
  Serial.printf("  Score: %d → %d (%+d)\n", oldScore, newScore, scoreChange);

  if (newScore <= 0 && players[playerId].alive) {
    players[playerId].alive = false;
    Serial.println("  ⚠️  PLAYER ELIMINATED!");
    Serial.printf("[WARN] Player %d eliminated (score=0)\n", playerId);
    
    StaticJsonDocument<256> eliminationEvent;
    eliminationEvent["event"] = "player_eliminated";
    eliminationEvent["playerId"] = playerId;
    String eliminationOutput;
    serializeJson(eliminationEvent, eliminationOutput);
    sendBLEResponse(eliminationOutput);
    
    int alivePlayers = 0;
    int lastAliveId = -1;
    for (int i = 0; i < activePlayerCount; i++) {
      if (players[i].alive) {
        alivePlayers++;
        lastAliveId = i;
      }
    }
    
    if (alivePlayers == 1) {
      Serial.printf("🎉 WINNER: Player %d!\n", lastAliveId);
      StaticJsonDocument<256> winnerEvent;
      winnerEvent["event"] = "winner_declared";
      winnerEvent["winnerId"] = lastAliveId;
      String winnerOutput;
      serializeJson(winnerEvent, winnerOutput);
      sendBLEResponse(winnerOutput);
    }
  }

  lastMove.hasUndo = true;
  lastMove.playerId = playerId;
  lastMove.fromTile = currentTile;
  lastMove.toTile = newTile;
  lastMove.scoreChange = scoreChange;
  lastMove.chanceCardNumber = chanceCardNumber;

  currentPlayer = playerId;
  expectedTile = newTile;
  
  // NO HARDWARE MODE: Auto-confirm coin after 2 seconds
  waitingForCoin = true;
  coinWaitStartTime = millis();
  
  saveGameState();
  sendRollResponse(playerId, currentTile, newTile, tile, scoreChange, oldScore, newScore, 
                   chanceCardNumber, chanceCardDesc, players[playerId].alive);

  Serial.println("✓ Roll processed, will auto-confirm coin in 2s\n");
}

// ==================== SEND ROLL RESPONSE ====================
void sendRollResponse(int playerId, int fromTile, int toTile, const TileDefinition& tile,
                      int scoreChange, int oldScore, int newScore, int chanceCard, 
                      const char* chanceDesc, bool alive) {
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
  doc["lapBonus"] = false;
  
  if (tile.type == TYPE_CHANCE && chanceCard > 0) {
    doc["chanceCard"]["number"] = chanceCard;
    doc["chanceCard"]["description"] = chanceDesc;
    doc["chanceCard"]["effect"] = scoreChange;
  }
  
  doc["player"]["alive"] = alive;
  doc["player"]["eliminated"] = !alive;
  
  doc["waiting"]["forCoin"] = true;
  doc["waiting"]["tile"] = toTile;
  doc["waiting"]["blinking"] = true;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== HANDLE UNDO ====================
void handleUndo(JsonDocument& doc) {
  Serial.println("\n↩️  Processing Undo...");
  
  if (!lastMove.hasUndo) {
    sendErrorResponse("No move to undo");
    return;
  }

  int playerId = lastMove.playerId;
  int fromTile = lastMove.toTile;
  int toTile = lastMove.fromTile;
  
  players[playerId].currentTile = players[playerId].previousTile;
  players[playerId].score = players[playerId].previousScore;
  players[playerId].alive = (players[playerId].score > 0);
  players[playerId].coinPlaced = false;

  currentPlayer = playerId;
  expectedTile = toTile;
  waitingForCoin = true;
  coinWaitStartTime = millis();
  lastMove.hasUndo = false;
  
  saveGameState();
  sendUndoResponse(playerId, fromTile, toTile, players[playerId].score, players[playerId].alive);
  Serial.println("✓ Undo complete, will auto-confirm coin in 2s\n");
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
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== HANDLE RESET ====================
void handleReset() {
  Serial.println("\n🔄 Processing Game Reset...");

  for (int i = 0; i < activePlayerCount; i++) {
    players[i].currentTile = 1;
    players[i].score = 10;
    players[i].alive = true;
    players[i].coinPlaced = false;
  }
  
  for (int i = activePlayerCount; i < NUM_PLAYERS; i++) {
    players[i].alive = false;
  }
  
  currentPlayer = -1;
  expectedTile = -1;
  waitingForCoin = false;
  lastMove.hasUndo = false;
  
  saveGameState();
  sendResetResponse();
  Serial.println("✓ Game reset complete\n");
}

// ==================== SEND RESET RESPONSE ====================
void sendResetResponse() {
  StaticJsonDocument<512> doc;
  doc["event"] = "reset_complete";
  doc["message"] = "All players reset to start";
  
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
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== AUTO COIN PLACEMENT ====================
void checkAutoCoinPlacement() {
  if (!waitingForCoin || currentPlayer < 0 || expectedTile < 1) return;
  
  if (millis() - coinWaitStartTime > AUTO_COIN_DELAY) {
    Serial.println("\n✓ Auto-confirming coin placement (no hardware)");
    Serial.printf("  Player %d at Tile %d\n", currentPlayer, expectedTile);
    
    players[currentPlayer].coinPlaced = true;
    waitingForCoin = false;
    
    saveGameState();
    sendCoinPlacedResponse(currentPlayer, expectedTile);
    
    currentPlayer = -1;
    expectedTile = -1;
  }
}

void sendCoinPlacedResponse(int playerId, int tile) {
  StaticJsonDocument<384> doc;
  
  doc["event"] = "coin_placed";
  doc["playerId"] = playerId;
  doc["tile"] = tile;
  doc["verified"] = true;
  doc["hallSensor"] = false;  // No hardware
  doc["message"] = "Auto-confirmed (no hardware mode)";
  doc["player"]["score"] = players[playerId].score;
  doc["player"]["alive"] = players[playerId].alive;
  
  String response;
  serializeJson(doc, response);
  sendBLEResponse(response.c_str());
}

// ==================== HELPER FUNCTIONS ====================
void sendBLEResponse(const char* json) {
  if (deviceConnected && pTxCharacteristic != nullptr) {
    int len = strlen(json);
    pTxCharacteristic->setValue((uint8_t*)json, len);
    pTxCharacteristic->notify();
    Serial.print("📤 Sent: ");
    Serial.println(json);
  } else {
    Serial.println("[WARN] Cannot send - device not connected");
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
  }
}

void loadGameState() {
  for (int i = 0; i < NUM_PLAYERS; i++) {
    String prefix = "p" + String(i) + "_";
    players[i].currentTile = preferences.getInt((prefix + "tile").c_str(), 1);
    players[i].score = preferences.getInt((prefix + "score").c_str(), 10);
    players[i].alive = preferences.getBool((prefix + "alive").c_str(), true);
  }
  validateGameState();
}

void validateGameState() {
  for (int i = 0; i < activePlayerCount; i++) {
    if (players[i].currentTile < 1 || players[i].currentTile > NUM_TILES) {
      players[i].currentTile = 1;
    }
    if (players[i].score < 0) players[i].score = 0;
    if (players[i].score > 99) players[i].score = 99;
  }
}

void resetIdleTimer() {
  lastActivityTime = millis();
}

// ==================== MAIN LOOP ====================
void loop() {
  esp_task_wdt_reset();
  
  // BLE connection handling
  if (!deviceConnected && oldDeviceConnected) {
    Serial.println("\n[DEBUG] Connection state change detected: DISCONNECTED");
    delay(500);
    Serial.println("[DEBUG] Restarting advertising after disconnect...");
    pServer->startAdvertising();
    isPaired = false;
    Serial.println("[BLE] Disconnected - advertising restarted");
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    Serial.println("\n[DEBUG] Connection state change detected: CONNECTED");
    Serial.println("[BLE] Connected - ready for commands");
    oldDeviceConnected = deviceConnected;
  }
  
  processCommandQueue();
  
  // AUTO COIN PLACEMENT - Key feature for no-hardware mode
  checkAutoCoinPlacement();
  
  delay(10);
}
