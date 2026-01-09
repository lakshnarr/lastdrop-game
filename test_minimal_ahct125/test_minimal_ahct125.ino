/*
 * Minimal AHCT125 + LED Test
 * Verify GPIO48 enables AHCT125 and GPIO16 sends data
 */

#include <Adafruit_NeoPixel.h>

#define LED_DATA_PIN 16       // GPIO16 to AHCT125 pin 2 (1A)
#define AHCT125_OE_PIN 48     // GPIO48 to AHCT125 pin 1 (1OE) - active LOW
#define NUM_LEDS 135

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, LED_DATA_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("MINIMAL AHCT125 TEST");
  Serial.println("========================================\n");
  
  // Step 1: Test GPIO48 control
  Serial.println("STEP 1: Setting up GPIO48 (AHCT125 enable)");
  pinMode(AHCT125_OE_PIN, OUTPUT);
  
  Serial.println("  Testing HIGH (disabled)...");
  digitalWrite(AHCT125_OE_PIN, HIGH);
  delay(1000);
  Serial.println("  -> Measure GPIO48 with multimeter: should be ~3.3V");
  
  Serial.println("\n  Testing LOW (enabled)...");
  digitalWrite(AHCT125_OE_PIN, LOW);
  delay(1000);
  Serial.println("  -> Measure GPIO48 with multimeter: should be ~0V");
  Serial.println("  ✓ GPIO48 control ready\n");
  
  // Step 2: Initialize NeoPixel on GPIO16
  Serial.println("STEP 2: Initializing NeoPixel on GPIO16");
  strip.begin();
  strip.setBrightness(100);  // 40% brightness
  strip.clear();
  strip.show();
  Serial.println("  ✓ NeoPixel library initialized\n");
  
  // Step 3: Keep GPIO48 LOW (enabled)
  Serial.println("STEP 3: Enabling AHCT125 (GPIO48 = LOW)");
  digitalWrite(AHCT125_OE_PIN, LOW);
  Serial.println("  ✓ AHCT125 enabled\n");
  
  Serial.println("========================================");
  Serial.println("VERIFICATION CHECKLIST:");
  Serial.println("========================================");
  Serial.println("With multimeter:");
  Serial.println("  1. AHCT125 pin 14 (VCC) = 5.0V?");
  Serial.println("  2. AHCT125 pin 7 (GND) = 0V?");
  Serial.println("  3. GPIO48 = 0V (LOW)?");
  Serial.println("");
  Serial.println("With oscilloscope/logic analyzer:");
  Serial.println("  4. GPIO16 has signal? (data)");
  Serial.println("  5. AHCT125 pin 3 (1Y) has signal?");
  Serial.println("  6. LED strip DIN has signal?");
  Serial.println("");
  Serial.println("Physical:");
  Serial.println("  7. LED strip getting 4.7V?");
  Serial.println("  8. LED strip GND connected?");
  Serial.println("  9. All solder joints good?");
  Serial.println("========================================\n");
  
  Serial.println("Starting MAXIMUM brightness test...\n");
}

int ledNum = 0;

void loop() {
  // FORCE GPIO48 LOW every loop
  digitalWrite(AHCT125_OE_PIN, LOW);
  
  // Cycle through first 10 LEDs with MAXIMUM brightness WHITE
  strip.clear();
  strip.setBrightness(255);  // FULL BRIGHTNESS
  
  Serial.print("LED ");
  Serial.print(ledNum);
  Serial.println(" = FULL WHITE (255,255,255)");
  
  strip.setPixelColor(ledNum, 255, 255, 255);  // Maximum white
  strip.show();
  
  delay(1000);
  
  ledNum++;
  if(ledNum >= 10) {
    ledNum = 0;
    Serial.println("\n--- Restarting from LED 0 ---\n");
    delay(1000);
  }
}
