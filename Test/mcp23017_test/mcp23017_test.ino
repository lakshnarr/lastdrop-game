// MCP23017 I2C GPIO Expander Test for ESP32-S3 (USB serial output only)
//
// Wiring:
// MCP23017 VCC -> ESP32 3V3
// MCP23017 GND -> ESP32 GND
// MCP23017 SDA -> ESP32 GPIO 13 (+ 10kΩ pull-up to 3V3)
// MCP23017 SCL -> ESP32 GPIO 14 (+ 10kΩ pull-up to 3V3)
//
// Read results over USB serial (115200 baud). No onboard LED used.
<Wire.h>

#define SDA_PIN 13
#define SCL_PIN 14


void setup() {
  Serial.begin(115200);
  delay(1500);  // allow USB CDC to enumerate

  Serial.println("\n=== MCP23017 I2C Scanner (ESP32-S3) ===");
  Serial.println("Wiring: VCC->3V3, GND->GND, SDA->GPIO13, SCL->GPIO14 (with 10k pull-ups)");
  Serial.println("Expected addresses: 0x20 (A0/A1/A2 low) or 0x27 (all high/floating)");
  Serial.print("Build: ");
  Serial.print(__DATE__);
  Serial.print(" ");
  Serial.println(__TIME__);
  Serial.println();

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);   // 100kHz standard I2C speed

  delay(500);  // settle
}


void loop() {
  byte error, address;
  int devices = 0;
  bool mcpFound = false;
  const byte mcpAddrs[] = {0x20, 0x27};

  Serial.print("[");
  Serial.print(millis());
  Serial.println("] Scanning I2C bus...");

  for (address = 1; address < 127; address++) {
    Wire.beginTransmission(address);
    error = Wire.endTransmission();

    if (error == 0) {
      devices++;
      Serial.print("  Found device at 0x");
      if (address < 16) Serial.print("0");
      Serial.println(address, HEX);

      for (byte i = 0; i < sizeof(mcpAddrs); i++) {
        if (address == mcpAddrs[i]) {
          mcpFound = true;  // MCP23017 found at accepted address
        }
      }
    }
  }

  if (mcpFound) {
    Serial.println("✅ MCP23017 detected at 0x20");
  } else if (devices == 0) {
    Serial.println("⚠ No I2C devices found. Check wiring, pull-ups, power.");
  } else {
    Serial.print("Found ");
    Serial.print(devices);
    Serial.println(" device(s), but none at 0x20.");
  }

  Serial.println("---\n");
  Serial.flush();
  delay(3000);  // wait before next scan
}
