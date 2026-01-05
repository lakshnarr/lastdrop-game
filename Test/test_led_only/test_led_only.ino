/*
 * MINIMAL LED TEST
 * Tests ONLY the LED strip - nothing else
 * If this doesn't work, there's a power or wiring issue
 */

#include <Adafruit_NeoPixel.h>

#define LED_PIN    16   // GPIO16 → AHCT125
#define LED_OE_PIN 47   // GPIO47 → AHCT125 OE (active LOW)
#define LED_COUNT  137
#define ONBOARD_LED 48  // ESP32-S3 onboard RGB LED

Adafruit_NeoPixel strip(LED_COUNT, LED_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  // Turn off onboard LED
  pinMode(ONBOARD_LED, OUTPUT);
  digitalWrite(ONBOARD_LED, LOW);
  
  Serial.println("\n\n=================================");
  Serial.println("  MINIMAL LED TEST");
  Serial.println("=================================\n");
  
  // Setup OE pin
  pinMode(LED_OE_PIN, OUTPUT);
  digitalWrite(LED_OE_PIN, HIGH); // Disable first
  Serial.println("Step 1: OE pin set HIGH (disabled)");
  delay(1000);
  
  // Init LEDs
  strip.begin();
  strip.clear();
  strip.show();
  Serial.println("Step 2: NeoPixel initialized");
  delay(500);
  
  // Enable output
  digitalWrite(LED_OE_PIN, LOW); // Enable (active LOW)
  Serial.println("Step 3: OE pin set LOW (enabled)");
  Serial.println("\nTrying to light up first 10 LEDs RED...\n");
  delay(500);
  
  // Try ALL 137 LEDs with dim white
  Serial.println("Setting all 137 LEDs to dim white...");
  for (int i = 0; i < LED_COUNT; i++) {
    strip.setPixelColor(i, strip.Color(10, 10, 10)); // Very dim white
  }
  strip.show();
  Serial.println("Done! How many LEDs are lit?");
  Serial.println("\nIf not all LEDs light:");
  Serial.println("- Some LEDs may be damaged");
  Serial.println("- Voltage drop along strip (needs power injection)");
  Serial.println("- Data line broken somewhere in the strip");
  
  delay(3000);
  
  // Now try a chase to see where it stops
  Serial.println("\nStarting slow chase...");
  for (int i = 0; i < LED_COUNT; i++) {
    strip.clear();
    strip.setPixelColor(i, strip.Color(255, 0, 0)); // Bright red
    strip.show();
    Serial.printf("LED %d\n", i);
    delay(50);
  }
  Serial.println("Chase complete!");
}

void loop() {
  // Blink first LED to confirm code is running
  static bool state = false;
  static unsigned long lastBlink = 0;
  
  if (millis() - lastBlink > 500) {
    lastBlink = millis();
    state = !state;
    
    if (state) {
      strip.setPixelColor(0, strip.Color(255, 0, 0));
      Serial.println("LED 0 ON");
    } else {
      strip.setPixelColor(0, strip.Color(0, 0, 0));
      Serial.println("LED 0 OFF");
    }
    strip.show();
  }
}
