// A3144 Hall Sensor Module Test on ESP32 with INTERNAL PULL-UP
// 
// Wiring:
// 1. A3144 Pin 1 (VCC) -> ESP32 3V3
// 2. A3144 Pin 2 (GND) -> ESP32 GND  
// 3. A3144 Pin 3 (OUT) -> ESP32 GPIO26
//
// Internal pull-up resistor (~45kΩ) used - no external resistor needed

#include <Adafruit_NeoPixel.h>

const int HALL_PIN = 26;
int lastValue = -1;
int changeCount = 0;

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

  pinMode(HALL_PIN, INPUT_PULLUP);  // Internal pull-up resistor
  
  Serial.println("\n=== A3144 Hall Sensor Test (Internal Pull-Up) ===");
  Serial.println("Wiring:");
  Serial.println("  - A3144 Pin 1 -> 3V3");
  Serial.println("  - A3144 Pin 2 -> GND");
  Serial.println("  - A3144 Pin 3 -> GPIO26");
  Serial.println("  - Internal pull-up enabled (no external resistor)");
  Serial.println("\nBring magnet close and try both sides!");
  Serial.println("Watching for changes...\n");
  Serial.println("Watching for changes...\n");
  
  lastValue = digitalRead(HALL_PIN);
  Serial.print("Starting value: ");
  Serial.println(lastValue);
}

void loop() {
  int v = digitalRead(HALL_PIN);
  
  // Only print when value changes
  if (v != lastValue) {
    changeCount++;
    Serial.print(">>> CHANGE DETECTED #");
    Serial.print(changeCount);
    Serial.print(" : HALL_PIN = ");
    Serial.print(v);
    Serial.println(v == 0 ? " (MAGNET DETECTED!)" : " (no magnet)");
    lastValue = v;
  }
  
  // Also print periodic status every 2 seconds
  static unsigned long lastPrint = 0;
  if (millis() - lastPrint > 2000) {
    Serial.print("Current: ");
    Serial.print(v);
    Serial.print(" | Changes: ");
    Serial.println(changeCount);
    lastPrint = millis();
  }

  delay(50);  // Faster polling
}
