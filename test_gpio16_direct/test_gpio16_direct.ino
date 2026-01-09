/*
 * GPIO16 Direct Test
 * Test GPIO16 output directly on ESP32 pin
 */

#define TEST_PIN 16

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("GPIO16 DIRECT OUTPUT TEST");
  Serial.println("========================================\n");
  
  pinMode(TEST_PIN, OUTPUT);
  
  Serial.println("CRITICAL: Measure GPIO16 DIRECTLY on ESP32 board");
  Serial.println("Do NOT measure at AHCT125 yet\n");
  
  Serial.println("Starting continuous HIGH/LOW toggle...\n");
}

void loop() {
  Serial.println("GPIO16 = HIGH (should be 3.3V)");
  digitalWrite(TEST_PIN, HIGH);
  delay(2000);
  
  Serial.println("GPIO16 = LOW (should be 0V)");
  digitalWrite(TEST_PIN, LOW);
  delay(2000);
  
  Serial.println("GPIO16 = HIGH");
  digitalWrite(TEST_PIN, HIGH);
  delay(2000);
  
  Serial.println("GPIO16 = LOW");
  digitalWrite(TEST_PIN, LOW);
  delay(2000);
  
  Serial.println("\n--- Fast toggle for 5 seconds ---");
  Serial.println("Multimeter should show ~1.6V average\n");
  
  for(int i = 0; i < 250; i++) {
    digitalWrite(TEST_PIN, HIGH);
    delay(10);
    digitalWrite(TEST_PIN, LOW);
    delay(10);
  }
  
  delay(1000);
}
