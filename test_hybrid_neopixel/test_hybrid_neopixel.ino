/*
 * Hybrid GPIO16 + NeoPixel Test
 * First verify AHCT125 with manual toggle, then try NeoPixel library
 */

#include <Adafruit_NeoPixel.h>

#define LED_DATA_PIN 16
#define AHCT125_OE_PIN 48
#define NUM_LEDS 135

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, LED_DATA_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("HYBRID GPIO16 + NEOPIXEL TEST");
  Serial.println("========================================\n");
  
  // Step 1: Manual GPIO test
  Serial.println("STEP 1: Manual GPIO16 test (10 seconds)");
  pinMode(AHCT125_OE_PIN, OUTPUT);
  digitalWrite(AHCT125_OE_PIN, LOW);  // Enable AHCT125
  
  pinMode(LED_DATA_PIN, OUTPUT);
  
  Serial.println("  Toggling GPIO16 manually...");
  Serial.println("  Check pin 2 and pin 3 of AHCT125\n");
  
  for(int i = 0; i < 10; i++) {
    digitalWrite(LED_DATA_PIN, HIGH);
    delay(500);
    digitalWrite(LED_DATA_PIN, LOW);
    delay(500);
  }
  
  Serial.println("  ✓ Manual toggle complete\n");
  
  // Step 2: Initialize NeoPixel
  Serial.println("STEP 2: Initializing NeoPixel library");
  Serial.println("  GPIO16 will be reconfigured by NeoPixel library...\n");
  
  strip.begin();
  strip.setBrightness(255);  // Full brightness
  strip.clear();
  strip.show();
  
  delay(500);
  
  Serial.println("  ✓ NeoPixel initialized");
  Serial.println("  Check pin 2 and pin 3 now - should see data signal\n");
  
  Serial.println("STEP 3: Sending LED data");
  Serial.println("  Lighting LED 0 with full white...\n");
  
  strip.setPixelColor(0, 255, 255, 255);
  strip.show();
  
  delay(2000);
  
  Serial.println("Starting LED cycle...\n");
}

int ledNum = 0;

void loop() {
  digitalWrite(AHCT125_OE_PIN, LOW);  // Keep enabled
  
  strip.clear();
  Serial.print("LED ");
  Serial.print(ledNum);
  Serial.println(" = WHITE");
  
  strip.setPixelColor(ledNum, 255, 255, 255);
  strip.show();
  
  // After show(), manually check pin 2 voltage
  Serial.println("  Check AHCT125 pin 2 now (should fluctuate during transmission)");
  
  delay(1000);
  
  ledNum++;
  if(ledNum >= 10) {
    ledNum = 0;
    Serial.println("\n--- Cycle restart ---\n");
  }
}
