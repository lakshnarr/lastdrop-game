/*
 * ESP32 LED Brightness Test
 * Tests specific LED numbers (33, 34, 35, 36) with adjustable brightness
 * 
 * Upload this sketch, open Serial Monitor at 115200 baud
 * Commands:
 *   b<value>  - Set brightness (0-255), e.g., "b128" for 50% brightness
 *   r<value>  - Set red value (0-255), e.g., "r255"
 *   g<value>  - Set green value (0-255), e.g., "g255"
 *   bl<value> - Set blue value (0-255), e.g., "bl255"
 *   on        - Turn on test LEDs with current settings
 *   off       - Turn off test LEDs
 *   test      - Run color cycle test
 *   info      - Show current settings
 */

#include <Adafruit_NeoPixel.h>

// LED strip configuration
#define LED_PIN 18        // Data pin connected to LED strip (GPIO 18)
#define NUM_LEDS 108      // Total number of LEDs
#define LED_TYPE NEO_GRB  // LED type (GRB for WS2812B)

// Test LED numbers (0-indexed)
const int testLEDs[] = {33, 34, 35, 36};
const int numTestLEDs = 4;

// Global settings (LOW VALUES FOR SAFETY)
uint8_t brightness = 20;      // Default brightness (SAFE: 20/255 = 8%)
uint8_t maxBrightness = 50;   // Maximum allowed brightness for safety
uint8_t redValue = 255;       // Default red component
uint8_t greenValue = 255;     // Default green component
uint8_t blueValue = 255;      // Default blue component

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_LEDS, LED_PIN, LED_TYPE + NEO_KHZ800);

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=================================");
  Serial.println("ESP32 LED Brightness Test");
  Serial.println("=================================");
  Serial.println("Testing LEDs: 33, 34, 35, 36");
  Serial.println();
  
  // Initialize LED strip
  strip.begin();
  strip.setBrightness(brightness);
  
  // Force ALL LEDs OFF multiple times to ensure clear state
  Serial.println("Clearing all LEDs (multiple passes)...");
  for(int pass = 0; pass < 3; pass++) {
    for(int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, 0, 0, 0);
    }
    strip.show();
    delay(200);
  }
  Serial.println("All LEDs cleared!");
  
  Serial.println();
  Serial.println("*** HARDWARE CHECK ***");
  Serial.print("LED Data Pin: GPIO ");
  Serial.println(LED_PIN);
  Serial.print("Total LEDs: ");
  Serial.println(NUM_LEDS);
  Serial.println("**********************");
  Serial.println();
  
  Serial.println("Commands:");
  Serial.println("  b<value>  - Set brightness (0-255)");
  Serial.println("  r<value>  - Set red (0-255)");
  Serial.println("  g<value>  - Set green (0-255)");
  Serial.println("  bl<value> - Set blue (0-255)");
  Serial.println("  on        - Turn on test LEDs");
  Serial.println("  off       - Turn off test LEDs");
  Serial.println("  test      - Run color cycle test");
  Serial.println("  sweep     - Sweep through LEDs 30-40");
  Serial.println("  info      - Show current settings");
  Serial.println();
  
  showInfo();
  
  // Pattern test across all LEDs
  Serial.println("Lighting pattern: RED -> GREEN -> VIOLET -> YELLOW");
  Serial.println("Pattern repeats every 4 LEDs (0-107)");
  Serial.println("Brightness: 20");
  Serial.println();
  
  delay(1000);
  
  // Set brightness to 20
  strip.setBrightness(20);
  
  // Apply 4-color pattern to all 108 LEDs
  for(int i = 0; i < NUM_LEDS; i++) {
    int patternIndex = i % 4;  // 0, 1, 2, 3, 0, 1, 2, 3...
    
    switch(patternIndex) {
      case 0:  // 1st LED = RED
        strip.setPixelColor(i, strip.Color(255, 0, 0));
        break;
      case 1:  // 2nd LED = GREEN
        strip.setPixelColor(i, strip.Color(0, 255, 0));
        break;
      case 2:  // 3rd LED = VIOLET
        strip.setPixelColor(i, strip.Color(128, 0, 255));
        break;
      case 3:  // 4th LED = YELLOW
        strip.setPixelColor(i, strip.Color(255, 255, 0));
        break;
    }
  }
  
  strip.show();
  
  Serial.println("Pattern applied to all 108 LEDs (continuous glow)");
  Serial.println("Type 'off' to turn off, 'sweep' to test sequence");
}

void loop() {
  if (Serial.available() > 0) {
    String command = Serial.readStringUntil('\n');
    command.trim();
    command.toLowerCase();
    
    processCommand(command);
  }
}

void processCommand(String cmd) {
  if (cmd.startsWith("b")) {
    // Set brightness
    int value = cmd.substring(1).toInt();
    if (value >= 0 && value <= maxBrightness) {
      brightness = value;
      strip.setBrightness(brightness);
      Serial.print("Brightness set to: ");
      Serial.print(brightness);
      Serial.print(" (Max: ");
      Serial.print(maxBrightness);
      Serial.println(" for safety)");
    } else {
      Serial.print("Error: Brightness must be 0-");
      Serial.print(maxBrightness);
      Serial.println(" (safety limited)");
    }
  }
  else if (cmd.startsWith("bl")) {
    // Set blue (must check "bl" before "b")
    int value = cmd.substring(2).toInt();
    if (value >= 0 && value <= 255) {
      blueValue = value;
      Serial.print("Blue set to: ");
      Serial.println(blueValue);
    } else {
      Serial.println("Error: Blue must be 0-255");
    }
  }
  else if (cmd.startsWith("r")) {
    // Set red
    int value = cmd.substring(1).toInt();
    if (value >= 0 && value <= 255) {
      redValue = value;
      Serial.print("Red set to: ");
      Serial.println(redValue);
    } else {
      Serial.println("Error: Red must be 0-255");
    }
  }
  else if (cmd.startsWith("g")) {
    // Set green
    int value = cmd.substring(1).toInt();
    if (value >= 0 && value <= 255) {
      greenValue = value;
      Serial.print("Green set to: ");
      Serial.println(greenValue);
    } else {
      Serial.println("Error: Green must be 0-255");
    }
  }
  else if (cmd == "on") {
    // Turn on test LEDs
    turnOnTestLEDs();
  }
  else if (cmd == "off") {
    // Turn off test LEDs
    turnOffTestLEDs();
  }
  else if (cmd == "test") {
    // Run color cycle test
    runColorTest();
  }
  else if (cmd == "sweep") {
    // Run LED sweep test
    runSweepTest();
  }
  else if (cmd == "all") {
    // Turn on ALL LEDs for testing
    Serial.println("Turning on ALL 80 LEDs at brightness 20...");
    strip.setBrightness(20);
    for(int i = 0; i < NUM_LEDS; i++) {
      strip.setPixelColor(i, strip.Color(255, 255, 255));
    }
    strip.show();
    Serial.println("All LEDs ON");
  }
  else if (cmd == "info") {
    // Show current settings
    showInfo();
  }
  else {
    Serial.println("Unknown command. Type 'info' for help.");
  }
}

void turnOnTestLEDs() {
  Serial.println("Turning ON test LEDs...");
  uint32_t color = strip.Color(redValue, greenValue, blueValue);
  
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], color);
  }
  strip.show();
  
  Serial.print("LEDs 33-36 set to RGB(");
  Serial.print(redValue);
  Serial.print(", ");
  Serial.print(greenValue);
  Serial.print(", ");
  Serial.print(blueValue);
  Serial.print(") at brightness ");
  Serial.println(brightness);
}

void turnOffTestLEDs() {
  Serial.println("Turning OFF test LEDs...");
  
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], 0);
  }
  strip.show();
  
  Serial.println("LEDs 33-36 turned off");
}

void runColorTest() {
  Serial.println("Running color cycle test on LEDs 33-36...");
  Serial.println("(Each color displays for 2 seconds)");
  
  uint8_t originalBrightness = brightness;
  strip.setBrightness(originalBrightness);
  
  // Red
  Serial.println("  -> Red");
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], strip.Color(255, 0, 0));
  }
  strip.show();
  delay(2000);
  
  // Green
  Serial.println("  -> Green");
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], strip.Color(0, 255, 0));
  }
  strip.show();
  delay(2000);
  
  // Blue
  Serial.println("  -> Blue");
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], strip.Color(0, 0, 255));
  }
  strip.show();
  delay(2000);
  
  // White
  Serial.println("  -> White");
  for (int i = 0; i < numTestLEDs; i++) {
    strip.setPixelColor(testLEDs[i], strip.Color(255, 255, 255));
  }
  strip.show();
  delay(2000);
  
  // Brightness fade test
  Serial.println("  -> Brightness fade (0-255-0)");
  for (int b = 0; b <= 255; b += 5) {
    strip.setBrightness(b);
    for (int i = 0; i < numTestLEDs; i++) {
      strip.setPixelColor(testLEDs[i], strip.Color(255, 255, 255));
    }
    strip.show();
    delay(20);
  }
  delay(500);
  for (int b = 255; b >= 0; b -= 5) {
    strip.setBrightness(b);
    for (int i = 0; i < numTestLEDs; i++) {
      strip.setPixelColor(testLEDs[i], strip.Color(255, 255, 255));
    }
    strip.show();
    delay(20);
  }
  
  // Restore original brightness
  strip.setBrightness(originalBrightness);
  
  // Turn off
  turnOffTestLEDs();
  Serial.println("Color test complete!");
}

void runSweepTest() {
  Serial.println("LED Sweep Test: LEDs 1-108");
  Serial.println("Brightness: 20");
  Serial.println();
  
  // Clear all first
  Serial.println("Clearing all LEDs...");
  for(int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, 0);
  }
  strip.show();
  delay(500);
  Serial.println("All LEDs cleared.");
  Serial.println();
  
  strip.setBrightness(20); // Set to 20 as requested
  
  for (int i = 1; i <= 108; i++) {
    Serial.print("LED #");
    Serial.print(i);
    Serial.print(" ON");
    
    // Turn on ONLY this LED
    strip.setPixelColor(i, strip.Color(255, 255, 255));
    strip.show();
    delay(1000);  // 1 second ON
    
    Serial.print(" -> OFF");
    // Turn it back off
    strip.setPixelColor(i, 0);
    strip.show();
    Serial.println();
    delay(1000);  // 1 second OFF
  }
  
  strip.setBrightness(brightness);
  Serial.println();
  Serial.println("Sweep complete! All 108 LEDs tested.");
}

void showInfo() {
  Serial.println("\n--- Current Settings ---");
  Serial.print("Brightness: ");
  Serial.print(brightness);
  Serial.print(" (");
  Serial.print((brightness * 100) / 255);
  Serial.println("%)");
  Serial.print("Color: RGB(");
  Serial.print(redValue);
  Serial.print(", ");
  Serial.print(greenValue);
  Serial.print(", ");
  Serial.print(blueValue);
  Serial.println(")");
  Serial.print("Test LEDs: ");
  for (int i = 0; i < numTestLEDs; i++) {
    Serial.print(testLEDs[i]);
    if (i < numTestLEDs - 1) Serial.print(", ");
  }
  Serial.println();
  Serial.println("------------------------\n");
}
