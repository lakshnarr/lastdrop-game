/*
 * Last Drop - Board Hardware Test
 * Tests: 137 LEDs (0-136) + 20 Hall Sensor Tiles
 * 
 * Hardware:
 * - ESP32-S3-N16R8
 * - 74AHCT125 level shifter for LED data
 * - MCP23017 I2C expander (0x27) for 16 tiles
 * - 4 direct ESP32 GPIOs for remaining 4 tiles
 * - Hall sensors A3144 (open-collector, pulled up internally)
 */

#include <Wire.h>
#include <Adafruit_NeoPixel.h>
#include <Adafruit_MCP23X17.h>

// ========== LED Configuration ==========
#define LED_PIN         16      // ESP32 GPIO16 → AHCT125 pin 2 (1A)
#define LED_OE_PIN      47      // ESP32 GPIO47 → AHCT125 pin 1 (1OE, active LOW)
#define LED_COUNT       137     // Total LEDs (0-136)
Adafruit_NeoPixel strip(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);

// ========== I2C Configuration ==========
#define SDA_PIN         13
#define SCL_PIN         14
#define MCP_ADDR        0x27
Adafruit_MCP23X17 mcp;

// ========== Hall Sensor Tile Mapping ==========
// MCP23017 Port B (tiles via expander)
const uint8_t MCP_PORTB_TILES[8] = {1, 20, 19, 18, 16, 14, 17, 15};
// MCP23017 Port A
const uint8_t MCP_PORTA_TILES[8] = {2, 3, 4, 5, 13, 7, 8, 6};
// Direct ESP32 GPIOs
const uint8_t DIRECT_GPIO_PINS[4] = {17, 18, 8, 9};
const uint8_t DIRECT_GPIO_TILES[4] = {9, 10, 11, 12};

// Tile state tracking (true = magnet detected)
bool tileState[21] = {false}; // Index 0 unused, 1-20 for tiles

// ========== Setup ==========
void setup() {
  Serial.begin(115200);
  delay(2000); // Wait for Serial Monitor
  Serial.println("\n\n====================================");
  Serial.println("  Last Drop - Board Hardware Test");
  Serial.println("====================================\n");

  // ========== LED Initialization ==========
  Serial.println("Initializing LED system...");
  
  // AHCT125 Output Enable (active LOW, so HIGH = disabled during init)
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, HIGH); // Disable output initially
  Serial.println("  - LED Output Disabled (OE HIGH)");
  
  strip.begin();
  strip.clear();
  strip.show();
  Serial.println("  - NeoPixel initialized (cleared)");
  
  delay(500); // Settle time
  digitalWrite(LED_OE_PIN, LOW); // Enable output (active LOW)
  Serial.println("  - LED Output Enabled (OE LOW)");
  Serial.println("✓ LED system ready\n");

  // ========== I2C + MCP23017 Initialization ==========
  Serial.println("Initializing I2C and MCP23017...");
  Wire.begin(SDA_PIN, SCL_PIN);
  
  if (!mcp.begin_I2C(MCP_ADDR)) {
    Serial.println("✗ ERROR: MCP23017 not found at 0x27!");
    Serial.println("  Check wiring: SDA=GPIO13, SCL=GPIO14, VCC=3.3V");
    while (1) delay(100); // Halt
  }
  Serial.println("  - MCP23017 found at 0x27");

  // Configure MCP Port B (tiles 1, 20, 19, 18, 16, 14, 17, 15)
  for (uint8_t i = 0; i < 8; i++) {
    mcp.pinMode(i + 8, INPUT_PULLUP); // Port B = pins 8-15
  }
  Serial.println("  - Port B configured (8 tiles, pull-ups enabled)");

  // Configure MCP Port A (tiles 2, 3, 4, 5, 13, 7, 8, 6)
  for (uint8_t i = 0; i < 8; i++) {
    mcp.pinMode(i, INPUT_PULLUP); // Port A = pins 0-7
  }
  Serial.println("  - Port A configured (8 tiles, pull-ups enabled)");
  Serial.println("✓ MCP23017 ready\n");

  // ========== Direct ESP32 GPIO Initialization ==========
  Serial.println("Initializing direct ESP32 Hall sensor pins...");
  for (uint8_t i = 0; i < 4; i++) {
    pinMode(DIRECT_GPIO_PINS[i], INPUT_PULLUP);
    Serial.printf("  - GPIO%d → Tile %d (pull-up enabled)\n", DIRECT_GPIO_PINS[i], DIRECT_GPIO_TILES[i]);
  }
  Serial.println("✓ Direct GPIOs ready\n");

  // ========== LED Test Sequence ==========
  Serial.println("====================================");
  Serial.println("  LED TEST (0-136)");
  Serial.println("====================================");
  testLEDs();

  // ========== Hall Sensor Monitoring Start ==========
  Serial.println("\n====================================");
  Serial.println("  HALL SENSOR MONITORING (20 tiles)");
  Serial.println("====================================");
  Serial.println("Bring magnet near any tile...\n");
}

// ========== Main Loop ==========
void loop() {
  checkHallSensors();
  delay(50); // Poll every 50ms
}

// ========== LED Test Function ==========
void testLEDs() {
  Serial.println("Test 1: Chase red dot (0→136)...");
  for (int i = 0; i < LED_COUNT; i++) {
    strip.clear();
    strip.setPixelColor(i, strip.Color(255, 0, 0)); // Red
    strip.show();
    delay(20);
  }
  strip.clear();
  strip.show();
  Serial.println("✓ Chase complete\n");

  Serial.println("Test 2: Fill all white...");
  for (int i = 0; i < LED_COUNT; i++) {
    strip.setPixelColor(i, strip.Color(50, 50, 50)); // Dim white
  }
  strip.show();
  delay(1000);
  strip.clear();
  strip.show();
  Serial.println("✓ Fill complete\n");

  Serial.println("Test 3: Rainbow cycle...");
  for (int j = 0; j < 256; j++) {
    for (int i = 0; i < LED_COUNT; i++) {
      strip.setPixelColor(i, wheel((i + j) & 255));
    }
    strip.show();
    delay(5);
  }
  strip.clear();
  strip.show();
  Serial.println("✓ Rainbow complete\n");

  Serial.println("✓ All LED tests passed!\n");
}

// Rainbow color wheel (0-255 → RGB)
uint32_t wheel(byte pos) {
  if (pos < 85) {
    return strip.Color(pos * 3, 255 - pos * 3, 0);
  } else if (pos < 170) {
    pos -= 85;
    return strip.Color(255 - pos * 3, 0, pos * 3);
  } else {
    pos -= 170;
    return strip.Color(0, pos * 3, 255 - pos * 3);
  }
}

// ========== Hall Sensor Monitoring ==========
void checkHallSensors() {
  // Check MCP Port B (tiles 1, 20, 19, 18, 16, 14, 17, 15)
  for (uint8_t i = 0; i < 8; i++) {
    uint8_t tileNum = MCP_PORTB_TILES[i];
    bool currentState = (mcp.digitalRead(i + 8) == LOW); // Active LOW (magnet pulls down)
    
    if (currentState != tileState[tileNum]) {
      tileState[tileNum] = currentState;
      if (currentState) {
        Serial.printf("🧲 MAGNET DETECTED → Tile %d (MCP PB%d)\n", tileNum, i);
      } else {
        Serial.printf("❌ MAGNET REMOVED → Tile %d (MCP PB%d)\n", tileNum, i);
      }
    }
  }

  // Check MCP Port A (tiles 2, 3, 4, 5, 13, 7, 8, 6)
  for (uint8_t i = 0; i < 8; i++) {
    uint8_t tileNum = MCP_PORTA_TILES[i];
    bool currentState = (mcp.digitalRead(i) == LOW);
    
    if (currentState != tileState[tileNum]) {
      tileState[tileNum] = currentState;
      if (currentState) {
        Serial.printf("🧲 MAGNET DETECTED → Tile %d (MCP PA%d)\n", tileNum, i);
      } else {
        Serial.printf("❌ MAGNET REMOVED → Tile %d (MCP PA%d)\n", tileNum, i);
      }
    }
  }

  // Check direct ESP32 GPIOs (tiles 9, 10, 11, 12)
  for (uint8_t i = 0; i < 4; i++) {
    uint8_t tileNum = DIRECT_GPIO_TILES[i];
    bool currentState = (digitalRead(DIRECT_GPIO_PINS[i]) == LOW);
    
    if (currentState != tileState[tileNum]) {
      tileState[tileNum] = currentState;
      if (currentState) {
        Serial.printf("🧲 MAGNET DETECTED → Tile %d (GPIO%d)\n", tileNum, DIRECT_GPIO_PINS[i]);
      } else {
        Serial.printf("❌ MAGNET REMOVED → Tile %d (GPIO%d)\n", tileNum, DIRECT_GPIO_PINS[i]);
      }
    }
  }
}
