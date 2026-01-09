/*
 * Raw GPIO Toggle Test
 * Bypasses NeoPixel library to test basic GPIO16 -> AHCT125 -> LED path
 */

#define LED_DATA_PIN 16       // GPIO16 to AHCT125 pin 2 (1A)
#define AHCT125_OE_PIN 48     // GPIO48 to AHCT125 pin 1 (1OE)

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("RAW GPIO TOGGLE TEST");
  Serial.println("========================================\n");
  
  // Setup GPIO48 (AHCT125 enable)
  pinMode(AHCT125_OE_PIN, OUTPUT);
  digitalWrite(AHCT125_OE_PIN, LOW);  // Enable (active LOW)
  Serial.println("GPIO48 = LOW (AHCT125 enabled)");
  Serial.println("  -> Measure GPIO48: should be 0V\n");
  
  // Setup GPIO16 (data)
  pinMode(LED_DATA_PIN, OUTPUT);
  digitalWrite(LED_DATA_PIN, LOW);
  Serial.println("GPIO16 = LOW initially");
  Serial.println("  -> Measure GPIO16: should be 0V\n");
  
  delay(2000);
  
  Serial.println("========================================");
  Serial.println("MEASUREMENT POINTS:");
  Serial.println("========================================");
  Serial.println("1. ESP32 GPIO16 pin");
  Serial.println("2. AHCT125 pin 2 (1A input)");
  Serial.println("3. AHCT125 pin 3 (1Y output)");
  Serial.println("4. After 330Ω resistor");
  Serial.println("5. LED strip DIN pin");
  Serial.println("========================================\n");
  
  Serial.println("Starting GPIO16 toggle test...");
  Serial.println("Measure each point above with multimeter\n");
}

void loop() {
  // Test 1: Slow toggle (easy to measure with multimeter)
  Serial.println("--- SLOW TOGGLE (1 second) ---");
  
  Serial.println("GPIO16 = HIGH");
  digitalWrite(LED_DATA_PIN, HIGH);
  Serial.println("  Measure now: GPIO16, AHCT125 pin 2, AHCT125 pin 3");
  Serial.println("  Expected: GPIO16=3.3V, pin 2=3.3V, pin 3=5V\n");
  delay(1000);
  
  Serial.println("GPIO16 = LOW");
  digitalWrite(LED_DATA_PIN, LOW);
  Serial.println("  Measure now: GPIO16, AHCT125 pin 2, AHCT125 pin 3");
  Serial.println("  Expected: GPIO16=0V, pin 2=0V, pin 3=0V\n");
  delay(1000);
  
  Serial.println("GPIO16 = HIGH");
  digitalWrite(LED_DATA_PIN, HIGH);
  delay(1000);
  
  Serial.println("GPIO16 = LOW");
  digitalWrite(LED_DATA_PIN, LOW);
  delay(1000);
  
  Serial.println("GPIO16 = HIGH");
  digitalWrite(LED_DATA_PIN, HIGH);
  delay(1000);
  
  Serial.println("GPIO16 = LOW");
  digitalWrite(LED_DATA_PIN, LOW);
  delay(1000);
  
  // Test 2: Fast toggle (should see ~2.5V average on multimeter)
  Serial.println("\n--- FAST TOGGLE (10ms) ---");
  Serial.println("GPIO16 toggling rapidly...");
  Serial.println("  Multimeter should show ~2.5V (average) on AHCT125 pin 3\n");
  
  for(int i = 0; i < 100; i++) {
    digitalWrite(LED_DATA_PIN, HIGH);
    delay(10);
    digitalWrite(LED_DATA_PIN, LOW);
    delay(10);
  }
  
  Serial.println("\n--- PAUSING ---\n");
  digitalWrite(LED_DATA_PIN, LOW);
  delay(3000);
  
  // Test 3: Check if AHCT125 enable works
  Serial.println("--- TESTING AHCT125 ENABLE ---");
  digitalWrite(LED_DATA_PIN, HIGH);
  
  Serial.println("GPIO48 = HIGH (AHCT125 DISABLED)");
  Serial.println("GPIO16 = HIGH (3.3V)");
  digitalWrite(AHCT125_OE_PIN, HIGH);
  Serial.println("  AHCT125 pin 3 should be 0V (disabled)\n");
  delay(3000);
  
  Serial.println("GPIO48 = LOW (AHCT125 ENABLED)");
  Serial.println("GPIO16 = HIGH (3.3V)");
  digitalWrite(AHCT125_OE_PIN, LOW);
  Serial.println("  AHCT125 pin 3 should be 5V (enabled)\n");
  delay(3000);
  
  digitalWrite(LED_DATA_PIN, LOW);
  delay(2000);
}
