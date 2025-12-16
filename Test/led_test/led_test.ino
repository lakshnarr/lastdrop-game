/*
 * Simple LED Strip Test - 4 LEDs ONLY
 * This will light up only 4 LEDs to test hardware
 */

#include <Adafruit_NeoPixel.h>

#define LED_PIN 18       // GPIO 18
#define NUM_LEDS 4       // Testing with ONLY 4 LEDs

Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

void setup() {  Serial.begin(115200);
  Serial.println("\n=== LED Strip Hardware Test ===");
  Serial.println("LED Pin: GPIO 18");
  Serial.println("Total LEDs: 4 (testing)");
  
  strip.begin();
  strip.setBrightness(100);  // Full brightness for testing
  strip.show();              // Initialize all pixels to 'off'
  
  Serial.println("LED strip initialized");
  Serial.println("Testing 4 LEDs only...");
  delay(1000);
}

void loop() {
  // Test 1: All WHITE (tests power and data)
  Serial.println("\nTest 1: All LEDs WHITE");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(255, 255, 255));
  }
  strip.show();
  delay(2000);
  
  // Test 2: All RED
  Serial.println("Test 2: All LEDs RED");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(255, 0, 0));
  }
  strip.show();
  delay(2000);
  
  // Test 3: All GREEN
  Serial.println("Test 3: All LEDs GREEN");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(0, 255, 0));
  }
  strip.show();
  delay(2000);
  
  // Test 4: All BLUE
  Serial.println("Test 4: All LEDs BLUE");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(0, 0, 255));
  }
  strip.show();
  delay(2000);
  
  // Test 5: Running dot (tests data transmission)
  Serial.println("Test 5: Running dot");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.clear();
    strip.setPixelColor(i, strip.Color(255, 255, 255));
    strip.show();
    delay(50);
  }
  
  // Test 6: Rainbow (tests all colors)
  Serial.println("Test 6: Rainbow");
  for(int i=0; i<NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.ColorHSV(i * 65536L / NUM_LEDS));
  }
  strip.show();
  delay(3000);
  
  // Clear and repeat
  Serial.println("Clearing...");
  strip.clear();
  strip.show();
  delay(1000);
}
