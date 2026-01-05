// ESP32-S3 Simple Serial Test via USB (COM10)

#define LED_PIN 48  // Built-in RGB LED

void setup() {
  pinMode(LED_PIN, OUTPUT);
  
  // Simple Serial on USB
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n\n\n");
  Serial.println("================================");
  Serial.println("ESP32-S3 UART Test - SUCCESS!");
  Serial.println("TX=GPIO43, RX=GPIO44");
  Serial.println("================================");
  Serial.print("Chip: ");
  Serial.println(ESP.getChipModel());
  Serial.print("Cores: ");
  Serial.println(ESP.getChipCores());
  Serial.print("CPU Freq: ");
  Serial.print(ESP.getCpuFreqMHz());
  Serial.println(" MHz");
  Serial.println("================================");
  Serial.println("LED should blink every second");
  Serial.println("Serial counter will increment");
  Serial.println("================================\n");
}

int counter = 0;

void loop() {
  counter++;
  
  digitalWrite(LED_PIN, HIGH);
  Serial.print("Counter: ");
  Serial.print(counter);
  Serial.println(" - LED ON");
  Serial.flush();
  delay(500);
  
  digitalWrite(LED_PIN, LOW);
  Serial.print("Counter: ");
  Serial.print(counter);
  Serial.println(" - LED OFF");
  Serial.flush();
  delay(500);
}
