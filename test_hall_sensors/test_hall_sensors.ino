/*
 * Hall Sensor Test Program
 * Tests A3144 Hall sensors on ESP32 GPIOs and MCP23017
 */

#include <Wire.h>
#include <Adafruit_MCP23X17.h>

#define SDA_PIN 13
#define SCL_PIN 14
#define MCP_ADDR 0x27

// Direct ESP32 GPIO Hall sensors
const uint8_t DIRECT_GPIO_PINS[4] = {17, 18, 8, 9};
const uint8_t DIRECT_GPIO_TILES[4] = {9, 10, 11, 12};

// MCP23017 Port B tiles (PB0-PB7)
const uint8_t MCP_PORTB_TILES[8] = {1, 20, 19, 18, 16, 14, 17, 15};

// MCP23017 Port A tiles (PA0-PA7)
const uint8_t MCP_PORTA_TILES[8] = {2, 3, 4, 5, 13, 7, 8, 6};

Adafruit_MCP23X17 mcp;

// Auto-reset mechanism for latching sensors
// Strategy: Detect LOW state, report "MAGNET PRESENT" for 1 second, then auto-reset
int lastState[20];  // Store last known state for each tile
bool magnetDetected[20];  // Currently detecting magnet
unsigned long magnetDetectTime[20];  // When magnet was first detected
#define MAGNET_DETECT_WINDOW 1000  // 1 second detection window

void autoResetTile(int tileIndex) {
  // Auto-reset after detection window expires
  lastState[tileIndex] = HIGH;  // Reset to "no magnet" state
  magnetDetected[tileIndex] = false;
  magnetDetectTime[tileIndex] = 0;
}

int readTileState(int tileIndex) {
  // Read actual GPIO/MCP state for given tile (0-19)
  if (tileIndex >= 0 && tileIndex <= 3) {
    // Direct GPIO tiles (9, 10, 11, 12)
    return digitalRead(DIRECT_GPIO_PINS[tileIndex]);
  }
  else if (tileIndex >= 4 && tileIndex <= 11) {
    // MCP Port B tiles
    return mcp.digitalRead(tileIndex - 4);
  }
  else if (tileIndex >= 12 && tileIndex <= 19) {
    // MCP Port A tiles
    return mcp.digitalRead(8 + (tileIndex - 12));
  }
  return HIGH;  // Default
}

bool checkMagnetStatus(int tileIndex) {
  int currentState = readTileState(tileIndex);
  unsigned long now = millis();
  
  // HIGH -> LOW transition = magnet just placed
  if (lastState[tileIndex] == HIGH && currentState == LOW) {
    lastState[tileIndex] = LOW;
    magnetDetected[tileIndex] = true;
    magnetDetectTime[tileIndex] = now;
    return true;  // New magnet detected!
  }
  
  // Check if detection window has expired
  if (magnetDetected[tileIndex]) {
    if (now - magnetDetectTime[tileIndex] > MAGNET_DETECT_WINDOW) {
      // Auto-reset after 1 second
      autoResetTile(tileIndex);
      return false;
    }
    return true;  // Still within detection window
  }
  
  return false;
}

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("HALL SENSOR TEST PROGRAM");
  Serial.println("========================================\n");
  
  // Test 1: Direct ESP32 GPIOs
  Serial.println("TEST 1: Direct ESP32 GPIO Hall Sensors");
  Serial.println("----------------------------------------");
  
  for(int i = 0; i < 4; i++) {
    pinMode(DIRECT_GPIO_PINS[i], INPUT_PULLUP);
    Serial.printf("GPIO %d (Tile %d): Configured with internal pull-up\n", 
                  DIRECT_GPIO_PINS[i], DIRECT_GPIO_TILES[i]);
  }
  
  Serial.println("\nReading GPIO states (should be HIGH without magnet):");
  delay(1000);
  
  for(int i = 0; i < 4; i++) {
    int state = digitalRead(DIRECT_GPIO_PINS[i]);
    Serial.printf("  GPIO %d (Tile %d): %s\n", 
                  DIRECT_GPIO_PINS[i], DIRECT_GPIO_TILES[i], 
                  state ? "HIGH (3.3V)" : "LOW (0V)");
  }
  
  Serial.println("\n** Place magnet near each sensor and watch readings **\n");
  
  // Test 2: Initialize I2C and MCP23017
  Serial.println("\nTEST 2: MCP23017 I2C Communication");
  Serial.println("----------------------------------------");
  
  Wire.begin(SDA_PIN, SCL_PIN);
  Serial.printf("I2C initialized: SDA=GPIO%d, SCL=GPIO%d\n", SDA_PIN, SCL_PIN);
  
  if (!mcp.begin_I2C(MCP_ADDR, &Wire)) {
    Serial.println("✗ ERROR: MCP23017 not found at 0x27!");
    Serial.println("  Check:");
    Serial.println("  - MCP23017 VCC connected to ESP32 3.3V");
    Serial.println("  - MCP23017 GND connected to common GND");
    Serial.println("  - SDA wire: MCP → GPIO13");
    Serial.println("  - SCL wire: MCP → GPIO14");
    Serial.println("  - Address pins A0,A1,A2 configuration");
    Serial.println("\nStopping here. Fix MCP connection first.\n");
    while(1) delay(1000);
  }
  
  Serial.println("✓ MCP23017 found!\n");
  
  // Configure MCP ports with pull-ups
  Serial.println("Configuring MCP23017 ports...");
  
  // Port B (tiles 1, 20, 19, 18, 16, 14, 17, 15)
  for(int i = 0; i < 8; i++) {
    mcp.pinMode(i, INPUT_PULLUP);  // PB0-PB7
    Serial.printf("  Port B pin %d (Tile %d): INPUT_PULLUP\n", i, MCP_PORTB_TILES[i]);
  }
  
  // Port A (tiles 2, 3, 4, 5, 13, 7, 8, 6)
  for(int i = 0; i < 8; i++) {
    mcp.pinMode(i + 8, INPUT_PULLUP);  // PA0-PA7
    Serial.printf("  Port A pin %d (Tile %d): INPUT_PULLUP\n", i, MCP_PORTA_TILES[i]);
  }
  
  Serial.println("\n✓ All MCP pins configured\n");
  
  // Initialize auto-reset system
  Serial.println("Initializing auto-reset system...");
  for(int i = 0; i < 20; i++) {
    lastState[i] = HIGH;  // Assume all start HIGH
    magnetDetected[i] = false;
    magnetDetectTime[i] = 0;
  }
  
  Serial.println("========================================");
  Serial.println("AUTO-RESET MONITORING - TILE 11");
  Serial.println("========================================");
  Serial.println("- Place magnet: Reports 'MAGNET PRESENT'");
  Serial.println("- After 1 second: Auto-resets to 'NO MAGNET'");
  Serial.println("- Ready for next coin placement\n");
  
  delay(2000);
}

void loop() {
  // Monitor ONLY Tile 11 (GPIO 8) - index 2 in array
  int tileIndex = 2;  // Tile 11 = DIRECT_GPIO_TILES[2]
  
  // Check magnet status with auto-reset
  bool magnetPresent = checkMagnetStatus(tileIndex);
  
  // Clear output and display current status
  Serial.printf("\rTile 11: %s", 
                magnetPresent ? "🧲 MAGNET PRESENT " : "   NO MAGNET     ");
  
  // Show countdown if magnet detected
  if (magnetPresent && magnetDetected[tileIndex]) {
    unsigned long elapsed = millis() - magnetDetectTime[tileIndex];
    unsigned long remaining = MAGNET_DETECT_WINDOW - elapsed;
    Serial.printf("(auto-reset in %dms) ", remaining);
  }
  
  delay(100);  // Fast refresh
}
