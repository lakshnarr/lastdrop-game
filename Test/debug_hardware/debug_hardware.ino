/*
 * STEP-BY-STEP HARDWARE DEBUG
 * Tests each component individually with detailed diagnostics
 */

#include <Wire.h>

// Pin definitions
#define LED_PIN         16
#define LED_OE_PIN      47
#define SDA_PIN         13
#define SCL_PIN         14

void setup() {
  Serial.begin(115200);
  delay(3000); // Extra time to open monitor
  
  Serial.println("\n\n");
  Serial.println("========================================");
  Serial.println("  HARDWARE DEBUG - STEP BY STEP");
  Serial.println("========================================\n");
  
  // STEP 1: Basic Pin Test
  Serial.println("STEP 1: Testing basic GPIO output...");
  pinMode(LED_BUILTIN, OUTPUT);
  for (int i = 0; i < 5; i++) {
    digitalWrite(LED_BUILTIN, HIGH);
    Serial.print(".");
    delay(200);
    digitalWrite(LED_BUILTIN, LOW);
    delay(200);
  }
  Serial.println(" OK\n");
  
  // STEP 2: LED Output Enable Pin
  Serial.println("STEP 2: Testing LED OE pin (GPIO47)...");
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, HIGH);
  Serial.println("  OE set HIGH (output disabled)");
  delay(1000);
  digitalWrite(LED_OE_PIN, LOW);
  Serial.println("  OE set LOW (output enabled)");
  Serial.println("  >> Check if AHCT125 pin 3 shows signal with scope/LED tester\n");
  
  // STEP 3: LED Data Pin Test (Simple Toggle)
  Serial.println("STEP 3: Testing LED data pin (GPIO16)...");
  pinMode(LED_PIN, OUTPUT);
  Serial.println("  Toggling GPIO16 HIGH/LOW 10 times...");
  for (int i = 0; i < 10; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
    Serial.print(".");
  }
  Serial.println(" Done");
  Serial.println("  >> Measure GPIO16 with multimeter (should toggle 0-3.3V)\n");
  
  // STEP 4: I2C Bus Scan
  Serial.println("STEP 4: Scanning I2C bus...");
  Wire.begin(SDA_PIN, SCL_PIN);
  delay(100);
  
  Serial.println("  SDA=GPIO13, SCL=GPIO14");
  Serial.println("  Scanning addresses 0x01-0x7F...");
  
  int deviceCount = 0;
  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();
    
    if (error == 0) {
      Serial.printf("  ✓ Device found at 0x%02X\n", addr);
      deviceCount++;
    }
  }
  
  if (deviceCount == 0) {
    Serial.println("  ✗ NO I2C devices found!");
    Serial.println("  >> Check: MCP23017 VCC=3.3V, GND=GND, SDA/SCL wiring");
    Serial.println("  >> Try: Swap SDA/SCL pins if accidentally reversed\n");
  } else {
    Serial.printf("  ✓ Found %d device(s)\n\n", deviceCount);
  }
  
  // STEP 5: Direct GPIO Hall Sensor Test
  Serial.println("STEP 5: Testing direct ESP32 Hall sensor pins...");
  pinMode(17, INPUT_PULLUP);
  pinMode(18, INPUT_PULLUP);
  pinMode(8, INPUT_PULLUP);
  pinMode(9, INPUT_PULLUP);
  
  Serial.println("  GPIO17 (Tile 9):  " + String(digitalRead(17) ? "HIGH" : "LOW"));
  Serial.println("  GPIO18 (Tile 10): " + String(digitalRead(18) ? "HIGH" : "LOW"));
  Serial.println("  GPIO8  (Tile 11): " + String(digitalRead(8) ? "HIGH" : "LOW"));
  Serial.println("  GPIO9  (Tile 12): " + String(digitalRead(9) ? "HIGH" : "LOW"));
  Serial.println("  >> All should show HIGH (pulled up)");
  Serial.println("  >> Place magnet on Tile 9/10/11/12 and check if it goes LOW\n");
  
  Serial.println("========================================");
  Serial.println("  DIAGNOSTIC COMPLETE");
  Serial.println("========================================\n");
  Serial.println("Waiting for manual tests...\n");
}

void loop() {
  // Continuous monitoring of direct Hall sensors
  static bool lastState[4] = {HIGH, HIGH, HIGH, HIGH};
  bool currentState[4];
  
  currentState[0] = digitalRead(17); // Tile 9
  currentState[1] = digitalRead(18); // Tile 10
  currentState[2] = digitalRead(8);  // Tile 11
  currentState[3] = digitalRead(9);  // Tile 12
  
  for (int i = 0; i < 4; i++) {
    if (currentState[i] != lastState[i]) {
      Serial.printf("GPIO%d (Tile %d): %s\n", 
                    (i==0)?17:(i==1)?18:(i==2)?8:9,
                    9+i,
                    currentState[i] ? "HIGH (no magnet)" : "LOW (MAGNET!)");
      lastState[i] = currentState[i];
    }
  }
  
  delay(50);
}
