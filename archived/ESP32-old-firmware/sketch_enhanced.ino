#include <Arduino.h>
#include <Adafruit_NeoPixel.h>
#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include <Preferences.h>

// -----------------------------
// Wi-Fi Configuration (AP mode)
// -----------------------------
const char* WIFI_SSID     = "LASTDROP-ESP32";
const char* WIFI_PASSWORD = "lastdrop123";

WebServer server(80);
Preferences preferences;

// Phone IP for callbacks (learned on first connection)
String phoneIP = "";
const int PHONE_PORT = 8080;

// -----------------------------
// LED & Tile Configuration
// -----------------------------

#define LED_PIN    18
#define LED_COUNT  80       // 4 LEDs per tile * 20 tiles

const int TILE_COUNT = 20;
const int PLAYER_COUNT = 4;

// GPIO mapping for each tile's Hall sensor
const int tileSensorPins[TILE_COUNT] = {
  34, 35, 32, 33, 25, 26, 27, 14, 12, 13,
  2, 4, 5, 15, 16, 17, 21, 22, 23, 19
};

// Optional: Dice button for manual testing
const int dicePin = 0;

// -----------------------------
// Game State
// -----------------------------

struct PlayerState {
  int position;            // 0-19 tile index
  int water;               // 0-20 drops
  bool alive;
  String name;
  String color;            // "red", "green", "blue", "yellow"
  uint8_t r, g, b;        // RGB values
  bool skipNextPenalty;
  bool waterShield;
  bool coinPlaced;         // Has physical coin been placed?
  unsigned long lastMoveTime;
};

PlayerState players[PLAYER_COUNT];
int currentPlayer = 0;

// Expected vs actual coin positions
int expectedPositions[PLAYER_COUNT] = {0, 0, 0, 0};
int actualPositions[PLAYER_COUNT] = {-1, -1, -1, -1};  // -1 = not detected

// Animation state
bool waitingForCoinPlacement = false;
int blinkingPlayer = -1;
int blinkingTile = -1;
unsigned long lastBlinkTime = 0;
bool blinkState = false;

// Misplacement tracking
bool hasMisplacements = false;
unsigned long lastScanTime = 0;
const unsigned long SCAN_INTERVAL = 5000;  // Scan every 5 seconds

// Timeout tracking
unsigned long coinPlacementStartTime = 0;
const unsigned long COIN_TIMEOUT = 30000;  // 30 second timeout

// NeoPixel strip
Adafruit_NeoPixel strip(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);

// -----------------------------
// Tile Effects (from game rules)
// -----------------------------

const int tileWaterDelta[TILE_COUNT] = {
  0, -1, +3, -1, -3, +1, -4, 0, 0, -3,
  +2, -2, 0, -2, +1, 0, -2, +4, 0, 0
};

const char* tileNames[TILE_COUNT] = {
  "Start Point", "Sunny Patch", "Rain Dock", "Leak Lane",
  "Storm Zone", "Cloud Hill", "Oil Spill Bay", "Riverbank Road",
  "Marsh Land (Chance)", "Drought Desert", "Clean Well", "Waste Dump",
  "Sanctuary Stop (Chance)", "Sewage Drain", "Filter Plant",
  "Mangrove Mile (Chance)", "Heatwave Road", "Spring Fountain",
  "Eco Garden", "Great Reservoir"
};

bool isChanceTile(int tile) {
  return (tile == 8 || tile == 12 || tile == 15);
}

// -----------------------------
// Helper Functions
// -----------------------------

int tilePlayerLedIndex(int tileIndex, int playerIndex) {
  return tileIndex * 4 + playerIndex;
}

void clearStrip() {
  for (int i = 0; i < LED_COUNT; i++) {
    strip.setPixelColor(i, 0, 0, 0);
  }
}

void initPlayers() {
  const char* defaultNames[4] = {"Player 1", "Player 2", "Player 3", "Player 4"};
  const char* defaultColors[4] = {"red", "green", "blue", "yellow"};
  const uint8_t defaultR[4] = {200, 0, 0, 200};
  const uint8_t defaultG[4] = {0, 200, 0, 200};
  const uint8_t defaultB[4] = {0, 0, 200, 0};

  for (int p = 0; p < PLAYER_COUNT; p++) {
    players[p].position = 0;
    players[p].water = 10;
    players[p].alive = true;
    players[p].name = String(defaultNames[p]);
    players[p].color = String(defaultColors[p]);
    players[p].r = defaultR[p];
    players[p].g = defaultG[p];
    players[p].b = defaultB[p];
    players[p].skipNextPenalty = false;
    players[p].waterShield = false;
    players[p].coinPlaced = true;  // Start with coins placed
    players[p].lastMoveTime = 0;
    
    expectedPositions[p] = 0;
    actualPositions[p] = 0;
  }
}

void parseColorString(int playerIndex, String colorStr) {
  colorStr.toLowerCase();
  if (colorStr == "red") {
    players[playerIndex].r = 200; players[playerIndex].g = 0; players[playerIndex].b = 0;
  } else if (colorStr == "green") {
    players[playerIndex].r = 0; players[playerIndex].g = 200; players[playerIndex].b = 0;
  } else if (colorStr == "blue") {
    players[playerIndex].r = 0; players[playerIndex].g = 0; players[playerIndex].b = 200;
  } else if (colorStr == "yellow") {
    players[playerIndex].r = 200; players[playerIndex].g = 200; players[playerIndex].b = 0;
  }
}

// -----------------------------
// LED Rendering
// -----------------------------

void renderBoard() {
  clearStrip();

  // Background for all tiles
  for (int tile = 0; tile < TILE_COUNT; tile++) {
    uint8_t bgR = 0, bgG = 0, bgB = 0;

    // Dim blue for empty tiles
    bgB = 15;

    // Tint for water tiles
    if (tileWaterDelta[tile] != 0) {
      bgG = 40;
      bgB = 50;
    }

    // Tint for chance tiles
    if (isChanceTile(tile)) {
      bgR = 30;
      bgB = 60;
    }

    for (int p = 0; p < 4; p++) {
      int led = tilePlayerLedIndex(tile, p);
      strip.setPixelColor(led, bgR, bgG, bgB);
    }
  }

  // Overlay player tokens
  for (int p = 0; p < PLAYER_COUNT; p++) {
    int tile = players[p].position;
    if (tile < 0 || tile >= TILE_COUNT) continue;

    int led = tilePlayerLedIndex(tile, p);

    if (players[p].alive && players[p].coinPlaced) {
      strip.setPixelColor(led, players[p].r, players[p].g, players[p].b);
    } else if (!players[p].alive) {
      strip.setPixelColor(led, 40, 40, 40);  // Gray for eliminated
    }
  }

  // Blinking LED for coin placement wait
  if (waitingForCoinPlacement && blinkingPlayer >= 0 && blinkingTile >= 0) {
    unsigned long now = millis();
    if (now - lastBlinkTime > 300) {
      blinkState = !blinkState;
      lastBlinkTime = now;
    }

    int led = tilePlayerLedIndex(blinkingTile, blinkingPlayer);
    if (blinkState) {
      strip.setPixelColor(led, players[blinkingPlayer].r, 
                                players[blinkingPlayer].g, 
                                players[blinkingPlayer].b);
    } else {
      strip.setPixelColor(led, 255, 255, 255);  // White blink
    }
  }

  // Show misplacement indicators (red blink on wrong tiles)
  if (hasMisplacements) {
    for (int p = 0; p < PLAYER_COUNT; p++) {
      if (actualPositions[p] >= 0 && actualPositions[p] != expectedPositions[p]) {
        int led = tilePlayerLedIndex(expectedPositions[p], p);
        if (blinkState) {
          strip.setPixelColor(led, 255, 0, 0);  // Red blink
        }
      }
    }
  }

  strip.show();
}

// -----------------------------
// Coin Position Scanning
// -----------------------------

void scanAllTiles() {
  // Read all hall sensors
  for (int tile = 0; tile < TILE_COUNT; tile++) {
    int pin = tileSensorPins[tile];
    int val = digitalRead(pin);
    
    // LOW = coin detected (pullup resistor, sensor closes to ground)
    if (val == LOW) {
      // Coin detected on this tile - but which player?
      // We need to match against expected positions
      for (int p = 0; p < PLAYER_COUNT; p++) {
        if (expectedPositions[p] == tile) {
          actualPositions[p] = tile;
          players[p].coinPlaced = true;
        }
      }
    }
  }

  // Detect misplacements
  hasMisplacements = false;
  for (int p = 0; p < PLAYER_COUNT; p++) {
    if (actualPositions[p] != expectedPositions[p]) {
      hasMisplacements = true;
      Serial.print("Misplacement: Player ");
      Serial.print(p);
      Serial.print(" expected at tile ");
      Serial.print(expectedPositions[p]);
      Serial.print(" but at ");
      Serial.println(actualPositions[p]);
    }
  }
}

// Send misplacement alert to phone
void sendMisplacementAlert() {
  if (phoneIP.length() == 0 || !hasMisplacements) return;

  WiFiClient client;
  if (!client.connect(phoneIP.c_str(), PHONE_PORT)) {
    Serial.println("Failed to connect to phone for misplacement alert");
    return;
  }

  StaticJsonDocument<512> doc;
  JsonArray errors = doc.createNestedArray("errors");

  for (int p = 0; p < PLAYER_COUNT; p++) {
    if (actualPositions[p] != expectedPositions[p]) {
      JsonObject err = errors.createNestedObject();
      err["playerId"] = p;
      err["playerName"] = players[p].name;
      err["expectedTile"] = expectedPositions[p];
      err["actualTile"] = (actualPositions[p] >= 0) ? actualPositions[p] : JsonVariant();
      err["color"] = players[p].color;
    }
  }

  String json;
  serializeJson(doc, json);

  client.println("POST /misplacement HTTP/1.1");
  client.println("Host: " + phoneIP);
  client.println("Content-Type: application/json");
  client.print("Content-Length: ");
  client.println(json.length());
  client.println();
  client.println(json);
  client.stop();

  Serial.println("Misplacement alert sent to phone");
}

// Check if specific player's coin is placed
bool checkCoinPlaced(int playerIndex, int tileIndex) {
  int pin = tileSensorPins[tileIndex];
  int val = digitalRead(pin);
  return (val == LOW);  // LOW = coin present
}

// Send coin placed confirmation to phone
void sendCoinPlacedCallback(int playerIndex) {
  if (phoneIP.length() == 0) {
    Serial.println("No phone IP configured");
    return;
  }

  WiFiClient client;
  if (!client.connect(phoneIP.c_str(), PHONE_PORT)) {
    Serial.println("Failed to connect to phone for callback");
    return;
  }

  StaticJsonDocument<256> doc;
  doc["playerId"] = playerIndex;
  doc["playerName"] = players[playerIndex].name;
  doc["tile"] = players[playerIndex].position;
  doc["verified"] = true;
  doc["timestamp"] = millis();

  String json;
  serializeJson(doc, json);

  client.println("POST /coin-placed HTTP/1.1");
  client.println("Host: " + phoneIP);
  client.println("Content-Type: application/json");
  client.print("Content-Length: ");
  client.println(json.length());
  client.println();
  client.println(json);
  client.stop();

  Serial.print("Coin placed callback sent for player ");
  Serial.println(playerIndex);
}

// -----------------------------
// LED Animation for Movement
// -----------------------------

void animateMove(int playerIndex, int fromTile, int toTile) {
  Serial.print("Animating player ");
  Serial.print(playerIndex);
  Serial.print(" from tile ");
  Serial.print(fromTile);
  Serial.print(" to ");
  Serial.println(toTile);

  // Simple step-by-step animation
  int current = fromTile;
  while (current != toTile) {
    int next = (current + 1) % TILE_COUNT;
    
    // Temporarily show player at next position
    players[playerIndex].position = next;
    renderBoard();
    delay(200);
    
    current = next;
  }

  players[playerIndex].position = toTile;
  renderBoard();
}

// -----------------------------
// HTTP Handlers
// -----------------------------

void handleRoot() {
  server.send(200, "text/plain", 
    "Last Drop ESP32 Physical Board\n"
    "POST /roll - Receive dice roll\n"
    "POST /undo - Undo last move\n"
    "POST /reset - Reset game\n"
    "POST /config - Configure phone IP\n"
    "GET /status - Get board status");
}

void handleRoll() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }

  String body = server.arg("plain");
  Serial.println("Received roll: " + body);

  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, body);

  if (error) {
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }

  int playerId = doc["playerId"] | 0;
  String playerName = doc["playerName"] | "";
  int diceValue = doc["diceValue"] | 1;
  int currentTile = doc["currentTile"] | 0;
  int expectedTile = doc["expectedTile"] | 0;
  String color = doc["color"] | "red";

  // Update player data
  if (playerId >= 0 && playerId < PLAYER_COUNT) {
    players[playerId].name = playerName;
    players[playerId].color = color;
    parseColorString(playerId, color);
    players[playerId].coinPlaced = false;  // Waiting for new placement

    // Animate movement
    animateMove(playerId, currentTile, expectedTile);

    // Update expected position
    expectedPositions[playerId] = expectedTile;

    // Start blinking and waiting
    waitingForCoinPlacement = true;
    blinkingPlayer = playerId;
    blinkingTile = expectedTile;
    coinPlacementStartTime = millis();
    lastBlinkTime = millis();
    blinkState = true;

    // Response
    StaticJsonDocument<256> response;
    response["status"] = "ok";
    response["ledTile"] = expectedTile;
    response["blinking"] = true;
    response["message"] = "Waiting for coin placement";

    String json;
    serializeJson(response, json);
    server.send(200, "application/json", json);
  } else {
    server.send(400, "application/json", "{\"error\":\"Invalid player ID\"}");
  }
}

void handleUndo() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }

  String body = server.arg("plain");
  Serial.println("Received undo: " + body);

  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, body);

  if (error) {
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }

  int playerId = doc["playerId"] | 0;
  int fromTile = doc["fromTile"] | 0;
  int toTile = doc["toTile"] | 0;

  if (playerId >= 0 && playerId < PLAYER_COUNT) {
    // Reverse animation
    Serial.print("Undoing player ");
    Serial.print(playerId);
    Serial.print(" from ");
    Serial.print(fromTile);
    Serial.print(" to ");
    Serial.println(toTile);

    players[playerId].coinPlaced = false;
    animateMove(playerId, fromTile, toTile);

    expectedPositions[playerId] = toTile;

    // Start blinking at original position
    waitingForCoinPlacement = true;
    blinkingPlayer = playerId;
    blinkingTile = toTile;
    coinPlacementStartTime = millis();

    server.send(200, "application/json", "{\"status\":\"ok\",\"message\":\"Undo animation complete\"}");
  } else {
    server.send(400, "application/json", "{\"error\":\"Invalid player ID\"}");
  }
}

void handleReset() {
  Serial.println("Resetting game");

  initPlayers();
  waitingForCoinPlacement = false;
  blinkingPlayer = -1;
  blinkingTile = -1;
  hasMisplacements = false;
  currentPlayer = 0;

  renderBoard();

  server.send(200, "application/json", "{\"status\":\"ok\",\"message\":\"Game reset\"}");
}

void handleConfig() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"No body\"}");
    return;
  }

  String body = server.arg("plain");
  StaticJsonDocument<128> doc;
  DeserializationError error = deserializeJson(doc, body);

  if (error) {
    server.send(400, "application/json", "{\"error\":\"Invalid JSON\"}");
    return;
  }

  String ip = doc["phoneIP"] | "";
  if (ip.length() > 0) {
    phoneIP = ip;
    Serial.print("Phone IP configured: ");
    Serial.println(phoneIP);

    // Save to preferences
    preferences.begin("lastdrop", false);
    preferences.putString("phoneIP", phoneIP);
    preferences.end();

    server.send(200, "application/json", "{\"status\":\"ok\",\"phoneIP\":\"" + phoneIP + "\"}");
  } else {
    server.send(400, "application/json", "{\"error\":\"Invalid phone IP\"}");
  }
}

void handleStatus() {
  StaticJsonDocument<1024> doc;
  doc["wifi"] = "connected";
  doc["phoneIP"] = phoneIP;
  doc["waitingForCoin"] = waitingForCoinPlacement;
  doc["hasMisplacements"] = hasMisplacements;

  JsonArray playersArray = doc.createNestedArray("players");
  for (int p = 0; p < PLAYER_COUNT; p++) {
    JsonObject player = playersArray.createNestedObject();
    player["id"] = p;
    player["name"] = players[p].name;
    player["position"] = players[p].position;
    player["water"] = players[p].water;
    player["alive"] = players[p].alive;
    player["coinPlaced"] = players[p].coinPlaced;
  }

  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

// -----------------------------
// Setup & Loop
// -----------------------------

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n\nLAST DROP - ESP32 Physical Board v2.0");

  // Initialize preferences
  preferences.begin("lastdrop", false);
  phoneIP = preferences.getString("phoneIP", "");
  preferences.end();

  Serial.print("Loaded phone IP: ");
  Serial.println(phoneIP.length() > 0 ? phoneIP : "None");

  // Initialize LED strip
  strip.begin();
  strip.setBrightness(80);
  clearStrip();
  strip.show();

  // Initialize sensors
  for (int i = 0; i < TILE_COUNT; i++) {
    pinMode(tileSensorPins[i], INPUT_PULLUP);
  }

  pinMode(dicePin, INPUT_PULLUP);

  // Initialize game state
  randomSeed(esp_random());
  initPlayers();

  // Startup animation
  for (int i = 0; i < LED_COUNT; i++) {
    clearStrip();
    strip.setPixelColor(i, 60, 60, 60);
    strip.show();
    delay(5);
  }

  clearStrip();
  strip.show();
  renderBoard();

  // Start WiFi AP
  WiFi.mode(WIFI_AP);
  WiFi.softAP(WIFI_SSID, WIFI_PASSWORD);
  delay(100);

  IPAddress ip = WiFi.softAPIP();
  Serial.print("WiFi AP started. SSID: ");
  Serial.println(WIFI_SSID);
  Serial.print("Password: ");
  Serial.println(WIFI_PASSWORD);
  Serial.print("AP IP: ");
  Serial.println(ip);

  // Setup HTTP server
  server.on("/", handleRoot);
  server.on("/roll", HTTP_POST, handleRoll);
  server.on("/undo", HTTP_POST, handleUndo);
  server.on("/reset", HTTP_POST, handleReset);
  server.on("/config", HTTP_POST, handleConfig);
  server.on("/status", HTTP_GET, handleStatus);
  server.begin();

  Serial.println("HTTP server started on port 80");
  Serial.println("Ready for game!\n");
}

void loop() {
  server.handleClient();

  unsigned long now = millis();

  // Check for coin placement if waiting
  if (waitingForCoinPlacement && blinkingPlayer >= 0 && blinkingTile >= 0) {
    // Check timeout
    if (now - coinPlacementStartTime > COIN_TIMEOUT) {
      Serial.println("Coin placement timeout - skipping");
      waitingForCoinPlacement = false;
      blinkingPlayer = -1;
      blinkingTile = -1;
      // Could send timeout notification to phone here
    } else {
      // Check if coin placed
      if (checkCoinPlaced(blinkingPlayer, blinkingTile)) {
        Serial.print("Coin placed detected for player ");
        Serial.println(blinkingPlayer);

        players[blinkingPlayer].coinPlaced = true;
        actualPositions[blinkingPlayer] = blinkingTile;
        waitingForCoinPlacement = false;

        // Send callback to phone
        sendCoinPlacedCallback(blinkingPlayer);

        blinkingPlayer = -1;
        blinkingTile = -1;
      }
    }
  }

  // Periodic full scan for misplacements
  if (now - lastScanTime > SCAN_INTERVAL) {
    scanAllTiles();
    if (hasMisplacements) {
      sendMisplacementAlert();
    }
    lastScanTime = now;
  }

  // Render board (handles blinking)
  renderBoard();

  delay(50);
}
