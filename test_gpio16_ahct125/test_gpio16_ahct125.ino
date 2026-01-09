/*
 * LED Test - Correct Wiring for AHCT125 Level Shifter
 * GPIO16 → AHCT125 → LED Strip DIN
 * GPIO48 → AHCT125 1OE (enable control)
 */

#include <Adafruit_NeoPixel.h>

#define LED_DATA_PIN 16       // GPIO16 to AHCT125 input (1A)
#define AHCT125_OE_PIN 48     // GPIO48 to AHCT125 1OE (active LOW)
#define NUM_LEDS 135

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, LED_DATA_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("LED TEST - AHCT125 LEVEL SHIFTER");
  Serial.println("========================================\n");
  
  // CRITICAL: Set up AHCT125 Output Enable (active LOW)
  pinMode(AHCT125_OE_PIN, OUTPUT);
  digitalWrite(AHCT125_OE_PIN, HIGH);  // Disable initially (safe)
  Serial.println("STEP 1: AHCT125 disabled (safe boot)");
  Serial.println("  GPIO48 = HIGH (output disabled)\n");
  
  // Initialize LED strip
  Serial.println("STEP 2: Initializing NeoPixel library");
  Serial.println("  Data pin: GPIO16");
  Serial.println("  LED type: WS2812B (GRB)");
  Serial.println("  LED count: 135\n");
  
  strip.begin();
  strip.setBrightness(50);  // Medium brightness for testing
  strip.clear();
  strip.show();
  
  // Wait for power to stabilize
  delay(500);
  
  // ENABLE the AHCT125 buffer
  Serial.println("STEP 3: ENABLING AHCT125 buffer");
  Serial.println("  GPIO48 = LOW (output enabled)");
  digitalWrite(AHCT125_OE_PIN, LOW);  // Enable output (active LOW)
  delay(100);
  Serial.println("  ✓ Buffer enabled!\n");
  
  Serial.println("========================================");
  Serial.println("WIRING CHECK:");
  Serial.println("========================================");
  Serial.println("Power:");
  Serial.println("  [?] 5V switch ON");
  Serial.println("  [?] LED strip getting 4.7V (measured)");
  Serial.println("  [?] ESP32 getting 5.0V (measured)");
  Serial.println("");
  Serial.println("AHCT125 Buffer:");
  Serial.println("  [✓] Pin 14 (VCC) → 5V");
  Serial.println("  [✓] Pin 7 (GND) → GND");
  Serial.println("  [✓] Pin 1 (1OE) → GPIO48 (now LOW)");
  Serial.println("  [✓] Pin 2 (1A) → GPIO16");
  Serial.println("  [✓] Pin 3 (1Y) → 330Ω → LED DIN");
  Serial.println("");
  Serial.println("Capacitors:");
  Serial.println("  [?] 0.1µF ceramic between AHCT125 VCC-GND");
  Serial.println("  [?] 1000µF near LED strip VCC-GND");
  Serial.println("");
  Serial.println("LED Strip:");
  Serial.println("  [?] +5V connected to strip VCC");
  Serial.println("  [?] GND connected to common GND");
  Serial.println("  [?] DIN connected to AHCT125 1Y output");
  Serial.println("  [?] Strip direction: DIN → DOUT (follow arrows)");
  Serial.println("========================================\n");
  
  Serial.println("Starting LED tests...\n");
}

int testPattern = 0;
unsigned long lastChange = 0;

void loop() {
  // Make sure AHCT125 stays enabled
  digitalWrite(AHCT125_OE_PIN, LOW);
  
  if(millis() - lastChange > 3000) {
    testPattern++;
    if(testPattern > 7) testPattern = 0;
    lastChange = millis();
    
    strip.clear();
    
    switch(testPattern) {
      case 0:
        Serial.println("TEST 1: LED 0 only - WHITE");
        strip.setPixelColor(0, 255, 255, 255);
        break;
        
      case 1:
        Serial.println("TEST 2: LED 0 only - RED");
        strip.setPixelColor(0, 255, 0, 0);
        break;
        
      case 2:
        Serial.println("TEST 3: LED 0 only - GREEN");
        strip.setPixelColor(0, 0, 255, 0);
        break;
        
      case 3:
        Serial.println("TEST 4: LED 0 only - BLUE");
        strip.setPixelColor(0, 0, 0, 255);
        break;
        
      case 4:
        Serial.println("TEST 5: LED 1 only - YELLOW (tests passthrough)");
        strip.setPixelColor(1, 255, 255, 0);
        break;
        
      case 5:
        Serial.println("TEST 6: First 5 LEDs - RAINBOW");
        strip.setPixelColor(0, 255, 0, 0);    // RED
        strip.setPixelColor(1, 0, 255, 0);    // GREEN
        strip.setPixelColor(2, 0, 0, 255);    // BLUE
        strip.setPixelColor(3, 255, 255, 0);  // YELLOW
        strip.setPixelColor(4, 255, 0, 255);  // PURPLE
        break;
        
      case 6:
        Serial.println("TEST 7: First 10 LEDs - WHITE");
        for(int i=0; i<10; i++) {
          strip.setPixelColor(i, 100, 100, 100);
        }
        break;
        
      case 7:
        Serial.println("TEST 8: All LEDs - DIM WHITE");
        strip.setBrightness(10);
        for(int i=0; i<NUM_LEDS; i++) {
          strip.setPixelColor(i, 255, 255, 255);
        }
        strip.setBrightness(50);  // Reset for next test
        break;
    }
    
    strip.show();
    Serial.println("  → Data sent\n");
  }
  
  delay(10);
}
