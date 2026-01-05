// Hall Sensor Test via MCP23017 I2C GPIO Expander
// ESP32-S3 N16R8 + MCP23017 + A3144 Hall Sensor
//
// Wiring:
// MCP23017 VCC -> ESP32 3V3
// MCP23017 GND -> ESP32 GND
// MCP23017 SDA -> ESP32 GPIO 13
// MCP23017 SCL -> ESP32 GPIO 14
//
// Hall Sensor A3144:
// VCC -> 5V (Samsung powerbank USB-C)
// GND -> Powerbank GND (shared with ESP32 GND)
// OUT -> MCP23017 PB3 (Pin 3 of Port B)
//
// NOTE: A3144 is open-drain output, needs pull-up resistor (10kΩ to 3.3V)
// Connect 10kΩ resistor between MCP23017 PB3 and 3.3V if not using internal pull-up

#include <Wire.h>

#define SDA_PIN 13
#define SCL_PIN 14
#define MCP23017_ADDRESS 0x27  // Address with A0/A1/A2 = HIGH or floating

// MCP23017 Register Addresses
#define MCP_IODIRB   0x01  // I/O Direction Register for Port B (1=input, 0=output)
#define MCP_GPPUB    0x0D  // Pull-up Resistor Register for Port B (1=enabled)
#define MCP_GPIOB    0x13  // GPIO Register for Port B (read pin states)

#define HALL_SENSOR_BIT 3  // PB3 = bit 3 of Port B

bool lastState = HIGH;
unsigned long lastChangeTime = 0;
int detectionCount = 0;

void setup() {
  Serial.begin(115200);
  delay(1500);  // allow USB CDC to enumerate

  Serial.println("\n=== Hall Sensor Test via MCP23017 ===");
  Serial.println("Hardware Setup:");
  Serial.println("  MCP23017: VCC->3V3, GND->GND, SDA->GPIO13, SCL->GPIO14");
  Serial.println("  Hall A3144: VCC->5V, GND->GND, OUT->MCP PB3");
  Serial.println("  Test: Bring magnet near Hall sensor to trigger");
  Serial.print("Build: ");
  Serial.print(__DATE__);
  Serial.print(" ");
  Serial.println(__TIME__);
  Serial.println();

  // Initialize I2C
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);  // 100kHz standard I2C
  delay(100);

  // Check if MCP23017 is present
  if (!checkMCP23017()) {
    Serial.println("❌ ERROR: MCP23017 not found at 0x27!");
    Serial.println("   Check wiring and I2C pull-up resistors (10kΩ to 3.3V on SDA/SCL)");
    while (1) {
      delay(1000);
    }
  }

  Serial.println("✅ MCP23017 detected at 0x27");

  // Configure MCP23017 Port B
  // Set PB3 as input
  writeRegister(MCP_IODIRB, 0xFF);  // All Port B pins as inputs
  delay(10);
  
  // Enable internal pull-up on PB3 (Hall sensor has open-drain output)
  writeRegister(MCP_GPPUB, (1 << HALL_SENSOR_BIT));  // Enable pull-up on PB3
  delay(10);

  Serial.println("✅ MCP23017 configured:");
  Serial.println("   - Port B all inputs");
  Serial.println("   - PB3 internal pull-up enabled");
  Serial.println("\n🧲 Monitoring Hall sensor on PB3...");
  Serial.println("   (Normal state: HIGH, Magnet detected: LOW)\n");

  // Read initial state
  lastState = readHallSensor();
  Serial.print("Initial state: ");
  Serial.println(lastState ? "HIGH (no magnet)" : "LOW (magnet present)");
  Serial.println("---\n");
}

void loop() {
  bool currentState = readHallSensor();

  // Detect state change
  if (currentState != lastState) {
    unsigned long now = millis();
    unsigned long duration = now - lastChangeTime;
    
    detectionCount++;
    
    Serial.print("[");
    Serial.print(now);
    Serial.print("ms] State changed: ");
    Serial.print(lastState ? "HIGH" : "LOW");
    Serial.print(" -> ");
    Serial.print(currentState ? "HIGH" : "LOW");
    
    if (currentState == LOW) {
      Serial.println("  🧲 MAGNET DETECTED!");
    } else {
      Serial.print("  ✓ Magnet removed");
      Serial.print(" (duration: ");
      Serial.print(duration);
      Serial.print("ms)");
      Serial.println();
    }
    
    Serial.print("  [Event #");
    Serial.print(detectionCount);
    Serial.println("]");
    Serial.flush();  // Force immediate send
    
    lastState = currentState;
    lastChangeTime = now;
  }

  // Status update every 2 seconds
  static unsigned long lastStatus = 0;
  if (millis() - lastStatus > 2000) {
    Serial.print("[");
    Serial.print(millis());
    Serial.print("ms] Current state: ");
    Serial.println(currentState ? "HIGH (no magnet)" : "LOW (magnet present)");
    Serial.flush();
    lastStatus = millis();
  }

  delay(10);  // Poll at 100Hz for real-time response
}

// Read Hall sensor state from MCP23017 PB3
bool readHallSensor() {
  byte portB = readRegister(MCP_GPIOB);
  return (portB & (1 << HALL_SENSOR_BIT)) != 0;
}

// Check if MCP23017 is present on I2C bus
bool checkMCP23017() {
  Wire.beginTransmission(MCP23017_ADDRESS);
  byte error = Wire.endTransmission();
  return (error == 0);
}

// Write to MCP23017 register
void writeRegister(byte reg, byte value) {
  Wire.beginTransmission(MCP23017_ADDRESS);
  Wire.write(reg);
  Wire.write(value);
  Wire.endTransmission();
}

// Read from MCP23017 register
byte readRegister(byte reg) {
  Wire.beginTransmission(MCP23017_ADDRESS);
  Wire.write(reg);
  Wire.endTransmission();
  
  Wire.requestFrom(MCP23017_ADDRESS, 1);
  if (Wire.available()) {
    return Wire.read();
  }
  return 0xFF;  // Error value
}
