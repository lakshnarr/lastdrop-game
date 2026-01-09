/*
 * LED 0 Data Passthrough Test
 * Tests if LED 0 works and passes data to subsequent LEDs
 * 
 * This will help verify if your DIN->DOUT connection is working
 */

#include <Adafruit_NeoPixel.h>

#define LED_PIN 18        // Data pin (GPIO 18)
#define NUM_LEDS 135      // Total LEDs in your strip

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n========================================");
  Serial.println("LED 0 Data Passthrough Test");
  Serial.println("========================================");
  Serial.println("GPIO 18 -> LED Strip");
  Serial.println();
  
  strip.begin();
  strip.setBrightness(50);  // Low brightness for safety
  strip.clear();
  strip.show();
  
  Serial.println("Starting test sequence...");
  Serial.println();
}

void loop() {
  // Test 1: Light ONLY LED 0
  Serial.println("TEST 1: LED 0 only (RED)");
  Serial.println("If LED 0 works, you should see a RED light");
  strip.clear();
  strip.setPixelColor(0, strip.Color(255, 0, 0));  // RED
  strip.show();
  delay(3000);
  
  // Test 2: Light ONLY LED 1
  Serial.println("\nTEST 2: LED 1 only (GREEN)");
  Serial.println("If data passes through LED 0, you should see GREEN on LED 1");
  strip.clear();
  strip.setPixelColor(1, strip.Color(0, 255, 0));  // GREEN
  strip.show();
  delay(3000);
  
  // Test 3: Light BOTH LED 0 and LED 1
  Serial.println("\nTEST 3: LED 0 (RED) + LED 1 (GREEN)");
  Serial.println("Both should light up if data passthrough works");
  strip.clear();
  strip.setPixelColor(0, strip.Color(255, 0, 0));  // RED
  strip.setPixelColor(1, strip.Color(0, 255, 0));  // GREEN
  strip.show();
  delay(3000);
  
  // Test 4: Light first 5 LEDs in different colors
  Serial.println("\nTEST 4: First 5 LEDs (Rainbow)");
  Serial.println("LED 0=RED, 1=GREEN, 2=BLUE, 3=YELLOW, 4=PURPLE");
  strip.clear();
  strip.setPixelColor(0, strip.Color(255, 0, 0));    // RED
  strip.setPixelColor(1, strip.Color(0, 255, 0));    // GREEN
  strip.setPixelColor(2, strip.Color(0, 0, 255));    // BLUE
  strip.setPixelColor(3, strip.Color(255, 255, 0));  // YELLOW
  strip.setPixelColor(4, strip.Color(255, 0, 255));  // PURPLE
  strip.show();
  delay(5000);
  
  // Test 5: Sequential sweep
  Serial.println("\nTEST 5: Sequential sweep (first 10 LEDs)");
  strip.clear();
  for(int i = 0; i < 10; i++) {
    Serial.print("Lighting LED ");
    Serial.println(i);
    strip.setPixelColor(i, strip.Color(100, 100, 255));  // Light blue
    strip.show();
    delay(500);
  }
  delay(2000);
  
  // Clear and wait
  Serial.println("\nClearing all LEDs...");
  strip.clear();
  strip.show();
  delay(3000);
  
  Serial.println("\n========================================");
  Serial.println("Test sequence complete. Restarting...\n");
  delay(2000);
}
