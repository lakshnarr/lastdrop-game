/*
 * Last Drop - LED Continuous Glow Test
 * 
 * This test program continuously glows all tile LEDs to verify wiring and LED functionality.
 * - Tile 1: LEDs 0-3 (Red, Green, Blue, Yellow)
 * - Tile 2: LEDs 6-9
 * - ...
 * - Tile 20: LEDs 127-130
 * 
 * All tile LEDs will glow continuously at full brightness.
 * 
 * Hardware:
 * - ESP32 Dev Board
 * - WS2812B LED Strip (136 LEDs total)
 * - GPIO16 → AHCT125 buffer → LED strip data
 * - GPIO48 → AHCT125 OE (active LOW, enables output)
 */

#include <Adafruit_NeoPixel.h>

// ==================== HARDWARE CONFIGURATION ====================
#define LED_PIN 16         // GPIO16 → AHCT125 data input
#define LED_OE_PIN 48      // GPIO48 → AHCT125 OE (active LOW to enable)
#define NUM_LEDS 136       // Total LEDs in perimeter strip
#define NUM_TILES 20       // Number of game tiles
#define BRIGHTNESS 255     // LED brightness (0-255) - FULL BRIGHTNESS

// LED mapping from main program: Each tile has 4 LEDs (Red, Green, Blue, Yellow)
// LED positions shifted -1 due to removed LED 0
const int TILE_LED_START[NUM_TILES] = {
  0,   // Tile 1:  LEDs 0-3   (R=0, G=1, B=2, Y=3)
  6,   // Tile 2:  LEDs 6-9
  12,  // Tile 3:  LEDs 12-15
  18,  // Tile 4:  LEDs 18-21
  24,  // Tile 5:  LEDs 24-27
  30,  // Tile 6:  LEDs 30-33
  40,  // Tile 7:  LEDs 40-43
  46,  // Tile 8:  LEDs 46-49
  52,  // Tile 9:  LEDs 52-55
  58,  // Tile 10: LEDs 58-61
  69,  // Tile 11: LEDs 69-72
  74,  // Tile 12: LEDs 74-77
  80,  // Tile 13: LEDs 80-83
  86,  // Tile 14: LEDs 86-89
  92,  // Tile 15: LEDs 92-95
  98,  // Tile 16: LEDs 98-101
  109, // Tile 17: LEDs 109-112
  115, // Tile 18: LEDs 115-118
  121, // Tile 19: LEDs 121-124
  127  // Tile 20: LEDs 127-130
};

// Define player colors
const uint32_t COLOR_RED = Adafruit_NeoPixel::Color(255, 0, 0);
const uint32_t COLOR_GREEN = Adafruit_NeoPixel::Color(0, 255, 0);
const uint32_t COLOR_BLUE = Adafruit_NeoPixel::Color(0, 0, 255);
const uint32_t COLOR_YELLOW = Adafruit_NeoPixel::Color(255, 255, 0);

// Create NeoPixel strip object
Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("===========================================");
  Serial.println("Last Drop - LED Continuous Glow Test");
  Serial.println("===========================================");
  
  // Initialize LED output enable pin (active LOW)
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, LOW);  // Enable AHCT125 output
  Serial.println("✓ AHCT125 output enabled (GPIO48 = LOW)");
  
  // Initialize NeoPixel strip
  strip.begin();
  strip.setBrightness(BRIGHTNESS);
  strip.clear();
  strip.show();
  Serial.println("✓ NeoPixel strip initialized");
  Serial.print("✓ Total LEDs: ");
  Serial.println(NUM_LEDS);
  Serial.print("✓ Brightness: ");
  Serial.print(BRIGHTNESS);
  Serial.println("/255");
  
  // Light up all tile LEDs continuously
  Serial.println("\n===========================================");
  Serial.println("Lighting all tile LEDs...");
  Serial.println("===========================================");
  
  for (int tile = 0; tile < NUM_TILES; tile++) {
    int startLED = TILE_LED_START[tile];
    
    // Each tile has 4 LEDs: Red, Green, Blue, Yellow
    strip.setPixelColor(startLED + 0, COLOR_RED);     // Player 0: Red
    strip.setPixelColor(startLED + 1, COLOR_GREEN);   // Player 1: Green
    strip.setPixelColor(startLED + 2, COLOR_BLUE);    // Player 2: Blue
    strip.setPixelColor(startLED + 3, COLOR_YELLOW);  // Player 3: Yellow
    
    Serial.print("Tile ");
    Serial.print(tile + 1);
    Serial.print(": LEDs ");
    Serial.print(startLED);
    Serial.print("-");
    Serial.print(startLED + 3);
    Serial.print(" (R=");
    Serial.print(startLED);
    Serial.print(", G=");
    Serial.print(startLED + 1);
    Serial.print(", B=");
    Serial.print(startLED + 2);
    Serial.print(", Y=");
    Serial.print(startLED + 3);
    Serial.println(")");
  }
  
  // Update LED strip to show all colors
  strip.show();
  
  Serial.println("\n===========================================");
  Serial.println("✓ All tile LEDs are now glowing!");
  Serial.println("===========================================");
  Serial.println("\nTest Status:");
  Serial.println("- All 20 tiles should show 4 colored LEDs each");
  Serial.println("- Red, Green, Blue, Yellow pattern per tile");
  Serial.println("- LEDs will glow continuously");
  Serial.println("\nVerify each tile's LEDs are working correctly.");
  Serial.println("===========================================");
}

void loop() {
  // Do nothing - LEDs stay on continuously
  delay(1000);
  
  // Optional: Print a heartbeat message every 10 seconds
  static unsigned long lastHeartbeat = 0;
  if (millis() - lastHeartbeat > 10000) {
    Serial.println("⚡ LEDs still glowing...");
    lastHeartbeat = millis();
  }
}
