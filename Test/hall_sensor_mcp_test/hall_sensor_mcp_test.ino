#include <Wire.h>

#if ARDUINO_USB_CDC_ON_BOOT
  #define USBSerial Serial
#else
  #define USBSerial Serial
#endif

#define SDA_PIN 13
#define SCL_PIN 14

// Try common MCP23017 addresses
uint8_t mcpAddrs[] = {0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27};

// MCP23017 registers (BANK=0)
#define IODIRA  0x00
#define IODIRB  0x01
#define IOCON   0x0A
#define GPPUA   0x0C
#define GPPUB   0x0D
#define GPIOA   0x12
#define GPIOB   0x13

uint8_t MCP = 0x00;

bool i2cPresent(uint8_t addr){
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void writeReg(uint8_t addr, uint8_t reg, uint8_t val){
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.write(val);
  Wire.endTransmission(true);
}

uint8_t readReg(uint8_t addr, uint8_t reg){
  Wire.beginTransmission(addr);
  Wire.write(reg);
  Wire.endTransmission(false);       // repeated start
  Wire.requestFrom(addr, (uint8_t)1);
  if (Wire.available()) return Wire.read();
  return 0xFF;
}

void setup() {
  USBSerial.begin(115200);
  delay(1500);

  USBSerial.println("\n=== MCP23017 FULL DIAGNOSTIC ===");

  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  delay(100);

  // Find MCP address
  USBSerial.println("Scanning I2C for MCP23017...");
  for (auto a : mcpAddrs){
    if (i2cPresent(a)){
      MCP = a;
      USBSerial.print("✅ Device ACK at 0x");
      USBSerial.println(MCP, HEX);
    }
  }
  if (MCP == 0x00){
    USBSerial.println("❌ No MCP23017 found at 0x20..0x27");
    USBSerial.println("Check: SDA/SCL pins, 3.3V, common GND, pull-ups on SDA/SCL.");
    while(1) delay(1000);
  }

  USBSerial.print("Using MCP address: 0x");
  USBSerial.println(MCP, HEX);

  // Make stable
  writeReg(MCP, IOCON, 0x20); // SEQOP=1
  delay(5);

  // Set all pins as inputs
  writeReg(MCP, IODIRA, 0xFF);
  writeReg(MCP, IODIRB, 0xFF);
  delay(5);

  // Enable pull-ups on ALL pins (so floating pins read HIGH)
  writeReg(MCP, GPPUA, 0xFF);
  writeReg(MCP, GPPUB, 0xFF);
  delay(5);

  // Read back config
  uint8_t iocon = readReg(MCP, IOCON);
  uint8_t ia = readReg(MCP, IODIRA);
  uint8_t ib = readReg(MCP, IODIRB);
  uint8_t pua = readReg(MCP, GPPUA);
  uint8_t pub = readReg(MCP, GPPUB);

  USBSerial.println("\nRegisters readback:");
  USBSerial.print("IOCON  = 0x"); USBSerial.println(iocon, HEX);
  USBSerial.print("IODIRA = 0x"); USBSerial.println(ia, HEX);
  USBSerial.print("IODIRB = 0x"); USBSerial.println(ib, HEX);
  USBSerial.print("GPPUA  = 0x"); USBSerial.println(pua, HEX);
  USBSerial.print("GPPUB  = 0x"); USBSerial.println(pub, HEX);

  USBSerial.println("\nNow printing GPIOA/GPIOB every 500ms.");
  USBSerial.println("With pull-ups ON:");
  USBSerial.println("- If nothing pulls pins low: expect GPIOA=0xFF and GPIOB=0xFF");
  USBSerial.println("- If sensors pull low: corresponding bits go 0\n");
}

void loop() {
  uint8_t ga = readReg(MCP, GPIOA);
  uint8_t gb = readReg(MCP, GPIOB);

  USBSerial.print("GPIOA=0x");
  if (ga < 16) USBSerial.print("0");
  USBSerial.print(ga, HEX);

  USBSerial.print("  GPIOB=0x");
  if (gb < 16) USBSerial.print("0");
  USBSerial.print(gb, HEX);

  // Check Port A for magnet detection
  if (ga != 0xFF) {
    USBSerial.print("  🧲 MAGNET DETECTED on Port A: ");
    for (int i = 0; i < 8; i++) {
      if (!(ga & (1 << i))) {
        USBSerial.print("PA");
        USBSerial.print(i);
        USBSerial.print(" ");
      }
    }
  }

  // Check Port B for magnet detection
  if (gb != 0xFF) {
    USBSerial.print("  🧲 MAGNET DETECTED on Port B: ");
    const char* tiles[] = {"Tile1", "Tile20", "Tile19", "Tile18", "Tile16", "Tile14", "Tile17", "Tile15"};
    for (int i = 0; i < 8; i++) {
      if (!(gb & (1 << i))) {
        USBSerial.print(tiles[i]);
        USBSerial.print(" (PB");
        USBSerial.print(i);
        USBSerial.print(") ");
      }
    }
  }

  USBSerial.println();
  delay(500);
}
