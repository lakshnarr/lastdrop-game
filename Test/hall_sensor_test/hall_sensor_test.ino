// A3144 Hall Sensor Module Test on ESP32 with EXTERNAL PULL-UP
// 
// ISOLATED 4-TILE TEST (Tiles 9-12 ONLY)
// All other tiles disconnected for focused testing
// 
// Wiring (for each of the 4 sensors):
// 1. A3144 Pin 1 (VCC) -> ESP32 3V3
// 2. A3144 Pin 2 (GND) -> ESP32 GND  
// 3. A3144 Pin 3 (OUT) -> ESP32 GPIO (17, 18, 8, 9)
// 4. 10kΩ resistor between A3144 Pin 3 (OUT) and 3V3 (EXTERNAL pull-up)
//
// Pin Mapping (4 tiles only):
// - GPIO 17 -> Tile 9
// - GPIO 18 -> Tile 10
// - GPIO 8  -> Tile 11
// - GPIO 9  -> Tile 12
//
// IMPORTANT: Internal pull-ups DISABLED - using external 10kΩ resistors

#include <Adafruit_NeoPixel.h>

const int HALL_PINS[4] = {17, 18, 8, 9};
const int TILE_NUMBERS[4] = {9, 10, 11, 12};
int lastValues[4] = {-1, -1, -1, -1};
int changeCounts[4] = {0, 0, 0, 0};

// Try multiple configurations for RGB LED
Adafruit_NeoPixel led48(1, 48, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel led38(1, 38, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel led2(1, 2, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Aggressively turn off RGB LED on all possible pins with all configurations
  led48.begin();
  led48.clear();
  led48.show();
  
  led38.begin();
  led38.clear();
  led38.show();
  
  led2.begin();
  led2.clear();
  led2.show();
  
  // Also set pins to OUTPUT LOW
  pinMode(48, OUTPUT);
  digitalWrite(48, LOW);
  pinMode(38, OUTPUT);
  digitalWrite(38, LOW);
  pinMode(2, OUTPUT);
  digitalWrite(2, LOW);

  // Initialize all 4 Hall sensor pins with NO internal pull-up (using external 10kΩ resistors)
  for (int i = 0; i < 4; i++) {
    pinMode(HALL_PINS[i], INPUT);  // NO PULLUP - external resistor used
    lastValues[i] = digitalRead(HALL_PINS[i]);
  }
  
  Serial.println("\n=== A3144 Hall Sensor Test (EXTERNAL 10kΩ Pull-Up) ===");
  Serial.println("ISOLATED TEST: 4 Tiles Only (9, 10, 11, 12)");
  Serial.println("All other tiles disconnected");
  Serial.println("\nWiring for each sensor:");
  Serial.println("  - A3144 Pin 1 (VCC) -> 3V3");
  Serial.println("  - A3144 Pin 2 (GND) -> GND");
  Serial.println("  - A3144 Pin 3 (OUT) -> GPIO + 10kΩ resistor to 3V3");
  Serial.println("\nPin Mapping (4 tiles ONLY):");
  Serial.println("  - GPIO 17 -> Tile 9");
  Serial.println("  - GPIO 18 -> Tile 10");
  Serial.println("  - GPIO 8  -> Tile 11");
  Serial.println("  - GPIO 9  -> Tile 12");
  Serial.println("\nInternal pull-ups DISABLED - using external resistors");
  Serial.println("Bring magnet close to any sensor and try both sides!");
  Serial.println("Watching for changes...\n");
  
  for (int i = 0; i < 4; i++) {
    Serial.print("GPIO ");
    Serial.print(HALL_PINS[i]);
    Serial.print(" (Tile ");
    Serial.print(TILE_NUMBERS[i]);
    Serial.print(") starting value: ");
    Serial.println(lastValues[i]);
  }
  Serial.println();
}

void loop() {
  // Read all 4 Hall sensors
  for (int i = 0; i < 4; i++) {
    int v = digitalRead(HALL_PINS[i]);
    
    // Only print when value changes
    if (v != lastValues[i]) {
      changeCounts[i]++;
      Serial.print(">>> TILE ");
      Serial.print(TILE_NUMBERS[i]);
      Serial.print(" (GPIO ");
      Serial.print(HALL_PINS[i]);
      Serial.print(") CHANGE #");
      Serial.print(changeCounts[i]);
      Serial.print(" : ");
      Serial.print(v);
      Serial.println(v == 0 ? " (MAGNET DETECTED!)" : " (no magnet)");
      lastValues[i] = v;
    }
  }
  
  // Print periodic status every 3 seconds
  static unsigned long lastPrint = 0;
  if (millis() - lastPrint > 3000) {
    Serial.println("--- Status ---");
    for (int i = 0; i < 4; i++) {
      Serial.print("Tile ");
      Serial.print(TILE_NUMBERS[i]);
      Serial.print(" (GPIO ");
      Serial.print(HALL_PINS[i]);
      Serial.print("): ");
      Serial.print(lastValues[i]);
      Serial.print(" | Changes: ");
      Serial.println(changeCounts[i]);
    }
    Serial.println();
    lastPrint = millis();
  }

  delay(50);  // Poll every 50ms
}
