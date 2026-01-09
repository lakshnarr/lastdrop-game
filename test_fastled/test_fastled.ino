/*
 * FastLED Test for ESP32-S3
 * Using FastLED library which handles ESP32-S3 GPIO better
 */

#define FASTLED_ESP32_I2S
#include <FastLED.h>

#define LED_PIN 16
#define AHCT125_OE_PIN 48
#define NUM_LEDS 135

CRGB leds[NUM_LEDS];

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("FASTLED TEST - ESP32-S3");
  Serial.println("========================================\n");
  
  // Enable AHCT125
  pinMode(AHCT125_OE_PIN, OUTPUT);
  digitalWrite(AHCT125_OE_PIN, LOW);
  Serial.println("✓ AHCT125 enabled (GPIO48 = LOW)\n");
  
  // Initialize FastLED
  Serial.println("Initializing FastLED on GPIO16...");
  FastLED.addLeds<WS2812B, LED_PIN, GRB>(leds, NUM_LEDS);
  FastLED.setBrightness(255);  // Full brightness
  FastLED.clear();
  FastLED.show();
  Serial.println("✓ FastLED initialized\n");
  
  delay(500);
  
  Serial.println("Starting LED test...\n");
}

int ledNum = 0;

void loop() {
  digitalWrite(AHCT125_OE_PIN, LOW);
  
  FastLED.clear();
  
  Serial.print("LED ");
  Serial.print(ledNum);
  Serial.println(" = FULL WHITE");
  
  leds[ledNum] = CRGB::White;
  FastLED.show();
  
  delay(1000);
  
  ledNum++;
  if(ledNum >= 10) {
    ledNum = 0;
    Serial.println("\n--- Restarting ---\n");
    delay(1000);
  }
}
