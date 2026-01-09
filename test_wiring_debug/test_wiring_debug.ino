/*
 * LED Strip Wiring Debug Tool
 * Helps diagnose why LEDs are not lighting up
 */

#include <Adafruit_NeoPixel.h>

#define LED_PIN 18
#define NUM_LEDS 135

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, NEO_GRB + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n\n========================================");
  Serial.println("LED STRIP WIRING DIAGNOSTIC");
  Serial.println("========================================\n");
  
  // Check 1: GPIO Configuration
  Serial.println("CHECK 1: GPIO Configuration");
  Serial.print("  Data Pin: GPIO ");
  Serial.println(LED_PIN);
  pinMode(LED_PIN, OUTPUT);
  Serial.println("  ✓ GPIO set as OUTPUT\n");
  
  // Check 2: Manual GPIO Toggle (watch with multimeter/scope)
  Serial.println("CHECK 2: GPIO Signal Test");
  Serial.println("  Toggling GPIO 18 HIGH/LOW rapidly...");
  Serial.println("  ** Use multimeter/oscilloscope on GPIO 18 **");
  Serial.println("  ** You should see voltage switching **\n");
  
  for(int i = 0; i < 20; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
  Serial.println("  GPIO toggle complete\n");
  
  // Check 3: Initialize NeoPixel library
  Serial.println("CHECK 3: NeoPixel Library Init");
  strip.begin();
  Serial.println("  ✓ NeoPixel initialized");
  strip.setBrightness(255);  // FULL brightness for testing
  Serial.println("  ✓ Brightness set to MAX (255)\n");
  
  // Check 4: Clear all LEDs
  Serial.println("CHECK 4: Clearing LED buffer");
  strip.clear();
  strip.show();
  Serial.println("  ✓ Clear command sent\n");
  
  Serial.println("========================================");
  Serial.println("HARDWARE CHECKLIST:");
  Serial.println("========================================");
  Serial.println("1. POWER SUPPLY:");
  Serial.println("   [ ] 5V connected to LED strip VCC/+5V");
  Serial.println("   [ ] Current capacity: 135 LEDs = ~8A max");
  Serial.println("   [ ] Check voltage with multimeter: 4.8-5.2V");
  Serial.println("");
  Serial.println("2. GROUND:");
  Serial.println("   [ ] ESP32 GND connected to LED strip GND");
  Serial.println("   [ ] Power supply GND connected to LED strip GND");
  Serial.println("   [ ] All grounds must be common!");
  Serial.println("");
  Serial.println("3. DATA LINE:");
  Serial.println("   [ ] ESP32 GPIO 18 connected to LED strip DIN");
  Serial.println("   [ ] No resistor needed for short distances");
  Serial.println("   [ ] Wire length < 1 meter is best");
  Serial.println("");
  Serial.println("4. LED STRIP:");
  Serial.println("   [ ] Type: WS2812B (GRB) - check your strip!");
  Serial.println("   [ ] Direction: DIN -> DOUT (arrows on strip)");
  Serial.println("   [ ] First LED not damaged/burned");
  Serial.println("");
  Serial.println("5. ESP32:");
  Serial.println("   [ ] Powered on (USB or external)");
  Serial.println("   [ ] Serial monitor shows this message");
  Serial.println("   [ ] Blue LED on ESP32 board blinking");
  Serial.println("");
  Serial.println("========================================\n");
  
  Serial.println("Starting LED test patterns...\n");
}

int testMode = 0;
unsigned long lastChange = 0;

void loop() {
  if(millis() - lastChange > 5000) {
    testMode++;
    if(testMode > 5) testMode = 0;
    lastChange = millis();
    
    strip.clear();
    
    switch(testMode) {
      case 0:
        Serial.println("TEST 1: First LED only - WHITE (MAX brightness)");
        strip.setPixelColor(0, 255, 255, 255);
        break;
        
      case 1:
        Serial.println("TEST 2: First LED only - RED");
        strip.setPixelColor(0, 255, 0, 0);
        break;
        
      case 2:
        Serial.println("TEST 3: First LED only - GREEN");
        strip.setPixelColor(0, 0, 255, 0);
        break;
        
      case 3:
        Serial.println("TEST 4: First LED only - BLUE");
        strip.setPixelColor(0, 0, 0, 255);
        break;
        
      case 4:
        Serial.println("TEST 5: First 5 LEDs - WHITE");
        for(int i=0; i<5; i++) {
          strip.setPixelColor(i, 255, 255, 255);
        }
        break;
        
      case 5:
        Serial.println("TEST 6: All LEDs - DIM WHITE");
        strip.setBrightness(20);
        for(int i=0; i<NUM_LEDS; i++) {
          strip.setPixelColor(i, 255, 255, 255);
        }
        break;
    }
    
    strip.show();
    Serial.println("  -> Data sent to strip");
    Serial.println("  -> Check LED strip now!\n");
  }
}
