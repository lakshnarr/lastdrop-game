/*
 * LED Segment Test - Find Data Break
 * Tests each perimeter segment individually
 * 
 * Segments:
 * - Bottom: LEDs 0-36 (37 LEDs, 6 tiles)
 * - Left: LEDs 37-70 (34 LEDs, 4 tiles)
 * - Top: LEDs 71-104 (34 LEDs, 6 tiles)
 * - Right: LEDs 105-136 (32 LEDs, 4 tiles)
 */

#include <Adafruit_NeoPixel.h>

#define LED_PIN    16
#define LED_OE_PIN 47
#define LED_COUNT  137
#define ONBOARD_LED_PIN 48  // ESP32-S3 onboard RGB LED (often a WS2812/NeoPixel)

Adafruit_NeoPixel strip(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel onboard(1, ONBOARD_LED_PIN, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel onboard38(1, 38, NEO_GRB + NEO_KHZ800);
Adafruit_NeoPixel onboard2(1, 2, NEO_GRB + NEO_KHZ800);

static void turnOffOnboardLEDs() {
  // Some ESP32-S3 boards have an onboard WS2812 on GPIO48, others on different pins.
  onboard.begin();
  onboard.clear();
  onboard.show();

  onboard38.begin();
  onboard38.clear();
  onboard38.show();

  onboard2.begin();
  onboard2.clear();
  onboard2.show();

  pinMode(48, OUTPUT);
  digitalWrite(48, LOW);
  pinMode(38, OUTPUT);
  digitalWrite(38, LOW);
  pinMode(2, OUTPUT);
  digitalWrite(2, LOW);
}

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  // Turn off onboard LED(s) as best as possible.
  // If it still stays ON, it's likely a power LED and cannot be controlled.
  turnOffOnboardLEDs();
  
  Serial.println("\n\n==============================");
  Serial.println("  LED SEGMENT TEST");
  Serial.println("==============================\n");
  
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, HIGH);
  
  strip.begin();
  strip.clear();
  strip.show();
  
  delay(500);
  digitalWrite(LED_OE_PIN, LOW);
  
  Serial.println("Testing segments one by one...\n");
  
  // Test bottom segment
  Serial.println("1) BOTTOM segment (LEDs 0-36):");
  Serial.println("   Lighting first 5 LEDs...");
  for(int i = 0; i <= 4; i++) {
    strip.setPixelColor(i, strip.Color(100, 0, 0));
  }
  strip.show();
  delay(3000);
  
  Serial.println("   Lighting last 5 LEDs of bottom...");
  strip.clear();
  for(int i = 32; i <= 36; i++) {
    strip.setPixelColor(i, strip.Color(0, 100, 0));
  }
  strip.show();
  delay(3000);
  
  // Test left segment
  Serial.println("\n2) LEFT segment (LEDs 37-70):");
  Serial.println("   Lighting first 5 LEDs...");
  strip.clear();
  for(int i = 37; i <= 41; i++) {
    strip.setPixelColor(i, strip.Color(0, 0, 100));
  }
  strip.show();
  delay(3000);
  
  Serial.println("   Lighting last 5 LEDs of left...");
  strip.clear();
  for(int i = 66; i <= 70; i++) {
    strip.setPixelColor(i, strip.Color(100, 100, 0));
  }
  strip.show();
  delay(3000);
  
  // Test top segment
  Serial.println("\n3) TOP segment (LEDs 71-104):");
  Serial.println("   Lighting first 5 LEDs...");
  strip.clear();
  for(int i = 71; i <= 75; i++) {
    strip.setPixelColor(i, strip.Color(100, 0, 100));
  }
  strip.show();
  delay(3000);
  
  Serial.println("   Lighting last 5 LEDs of top...");
  strip.clear();
  for(int i = 100; i <= 104; i++) {
    strip.setPixelColor(i, strip.Color(0, 100, 100));
  }
  strip.show();
  delay(3000);
  
  // Test right segment
  Serial.println("\n4) RIGHT segment (LEDs 105-136):");
  Serial.println("   Lighting first 5 LEDs...");
  strip.clear();
  for(int i = 105; i <= 109; i++) {
    strip.setPixelColor(i, strip.Color(100, 50, 0));
  }
  strip.show();
  delay(3000);
  
  Serial.println("   Lighting last 5 LEDs of right...");
  strip.clear();
  for(int i = 132; i <= 136; i++) {
    strip.setPixelColor(i, strip.Color(50, 100, 50));
  }
  strip.show();
  delay(3000);
  
  Serial.println("\n==============================");
  Serial.println("Which LEDs actually lit up?");
  Serial.println("This tells us where the break is!");
  Serial.println("==============================\n");
  
  // All segments slow chase
  Serial.println("Starting full perimeter chase...");
}

void loop() {
  // Chase around perimeter
  for(int i = 0; i < LED_COUNT; i++) {
    strip.clear();
    strip.setPixelColor(i, strip.Color(255, 0, 0));
    strip.show();
    Serial.printf("LED %d", i);
    
    // Indicate segment
    if(i >= 0 && i <= 36) Serial.println(" (BOTTOM)");
    else if(i >= 37 && i <= 70) Serial.println(" (LEFT)");
    else if(i >= 71 && i <= 104) Serial.println(" (TOP)");
    else Serial.println(" (RIGHT)");
    
    delay(100);
  }
}
