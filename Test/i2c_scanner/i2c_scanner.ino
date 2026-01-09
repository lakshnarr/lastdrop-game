// I2C Scanner for ESP32-S3
// Scans for all I2C devices on the bus

#include <Wire.h>

#define SDA_PIN 13
#define SCL_PIN 14

// ESP32-S3 USB CDC handling
#if ARDUINO_USB_CDC_ON_BOOT
#define HWSerial Serial0
#define USBSerial Serial
#else
#define HWSerial Serial
#define USBSerial Serial
#endif

void setup() {
  // Initialize both Serial interfaces for ESP32-S3
  USBSerial.begin(115200);
  HWSerial.begin(115200);
  
  delay(3000);  // Extra time for USB CDC enumeration
  
  USBSerial.println("\n\n=== I2C Scanner ===");
  USBSerial.println("USB Serial working!");
  HWSerial.println("\n\n=== I2C Scanner ===");
  HWSerial.println("Hardware Serial working!");
  
  USBSerial.println("Scanning I2C bus...");
  HWSerial.println("Scanning I2C bus...");
  
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);  // 100kHz
  
  byte count = 0;
  
  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();
    
    if (error == 0) {
      USBSerial.print("✓ Device found at 0x");
      HWSerial.print("✓ Device found at 0x");
      if (addr < 16) {
        USBSerial.print("0");
        HWSerial.print("0");
      }
      USBSerial.print(addr, HEX);
      HWSerial.print(addr, HEX);
      USBSerial.print(" (");
      HWSerial.print(" (");
      USBSerial.print(addr);
      HWSerial.print(addr);
      USBSerial.println(")");
      HWSerial.println(")");
      count++;
    }
  }
  
  USBSerial.print("\nScan complete. Found ");
  HWSerial.print("\nScan complete. Found ");
  USBSerial.print(count);
  HWSerial.print(count);
  USBSerial.println(" device(s).");
  HWSerial.println(" device(s).");
  
  if (count == 0) {
    USBSerial.println("\n❌ NO DEVICES FOUND!");
    HWSerial.println("\n❌ NO DEVICES FOUND!");
    USBSerial.println("Check:");
    HWSerial.println("Check:");
    USBSerial.println("  - SDA wire: GPIO13 → MCP23017 pin 13");
    HWSerial.println("  - SDA wire: GPIO13 → MCP23017 pin 13");
    USBSerial.println("  - SCL wire: GPIO14 → MCP23017 pin 12");
    HWSerial.println("  - SCL wire: GPIO14 → MCP23017 pin 12");
    USBSerial.println("  - MCP23017 VCC → 3.3V");
    HWSerial.println("  - MCP23017 VCC → 3.3V");
    USBSerial.println("  - MCP23017 GND → GND");
    HWSerial.println("  - MCP23017 GND → GND");
    USBSerial.println("  - Pull-up resistors (4.7kΩ to 3.3V on SDA/SCL)");
    HWSerial.println("  - Pull-up resistors (4.7kΩ to 3.3V on SDA/SCL)");
  }
}

void loop() {
  delay(5000);
  
  USBSerial.println("\n--- Rescanning ---");
  HWSerial.println("\n--- Rescanning ---");
  byte count = 0;
  
  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();
    
    if (error == 0) {
      USBSerial.print("0x");
      HWSerial.print("0x");
      if (addr < 16) {
        USBSerial.print("0");
        HWSerial.print("0");
      }
      USBSerial.print(addr, HEX);
      HWSerial.print(addr, HEX);
      USBSerial.print(" ");
      HWSerial.print(" ");
      count++;
    }
  }
  
  if (count > 0) {
    USBSerial.print("\nFound ");
    HWSerial.print("\nFound ");
    USBSerial.print(count);
    HWSerial.print(count);
    USBSerial.println(" device(s)");
    HWSerial.println(" device(s)");
  } else {
    USBSerial.println("No devices");
    HWSerial.println("No devices");
  }
}
