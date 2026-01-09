// Ultra-simple boot test - blinks LED and prints to Serial
// If this doesn't work, there's a hardware/USB issue

void setup() {
  Serial.begin(115200);
  
  // Wait for Serial (but don't hang forever)
  for(int i = 0; i < 20 && !Serial; i++) {
    delay(100);
  }
  
  Serial.println();
  Serial.println();
  Serial.println("==================");
  Serial.println("BOOT TEST STARTED");
  Serial.println("==================");
  Serial.print("Chip: ESP32-S3");
  Serial.println();
  Serial.print("Free Heap: ");
  Serial.println(ESP.getFreeHeap());
  Serial.println();
  Serial.println("If you see this, Serial is working!");
  Serial.println("Now blinking onboard LED...");
  
  // Try multiple possible LED pins
  pinMode(48, OUTPUT);
  pinMode(38, OUTPUT);
  pinMode(2, OUTPUT);
}

void loop() {
  static unsigned long lastPrint = 0;
  static int count = 0;
  
  // Blink all possible LED pins
  digitalWrite(48, HIGH);
  digitalWrite(38, HIGH);
  digitalWrite(2, HIGH);
  delay(500);
  
  digitalWrite(48, LOW);
  digitalWrite(38, LOW);
  digitalWrite(2, LOW);
  delay(500);
  
  // Print every 2 seconds
  if (millis() - lastPrint > 2000) {
    count++;
    Serial.print("[");
    Serial.print(count);
    Serial.print("] Alive at ");
    Serial.print(millis());
    Serial.println("ms");
    lastPrint = millis();
  }
}
