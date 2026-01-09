/*
 * ESP32-S3 RMT Direct WS2812B Test
 * Use ESP32 RMT peripheral directly on GPIO16
 * Bypass NeoPixel library
 */

#include <driver/rmt.h>

#define LED_PIN 16
#define AHCT125_OE_PIN 48
#define NUM_LEDS 135

#define RMT_TX_CHANNEL RMT_CHANNEL_0

// WS2812B timing (in 12.5ns ticks, 80MHz)
#define WS2812_T0H 32   // 0 bit high time: 0.4us = 32 ticks
#define WS2812_T0L 64   // 0 bit low time: 0.85us = 64 ticks  
#define WS2812_T1H 64   // 1 bit high time: 0.8us = 64 ticks
#define WS2812_T1L 32   // 1 bit low time: 0.45us = 32 ticks

rmt_item32_t led_data_buffer[NUM_LEDS * 24];

void rmt_init() {
  rmt_config_t config;
  config.rmt_mode = RMT_MODE_TX;
  config.channel = RMT_TX_CHANNEL;
  config.gpio_num = (gpio_num_t)LED_PIN;
  config.mem_block_num = 1;
  config.tx_config.loop_en = false;
  config.tx_config.carrier_en = false;
  config.tx_config.idle_output_en = true;
  config.tx_config.idle_level = RMT_IDLE_LEVEL_LOW;
  config.clk_div = 1;  // 80MHz / 1 = 80MHz (12.5ns per tick)
  
  rmt_config(&config);
  rmt_driver_install(config.channel, 0, 0);
}

void setPixelColor(int pixel, uint8_t r, uint8_t g, uint8_t b) {
  if (pixel >= NUM_LEDS) return;
  
  int offset = pixel * 24;
  uint32_t grb = ((uint32_t)g << 16) | ((uint32_t)r << 8) | b;
  
  for(int bit = 0; bit < 24; bit++) {
    if (grb & (1 << (23 - bit))) {
      // Bit is 1
      led_data_buffer[offset + bit].level0 = 1;
      led_data_buffer[offset + bit].duration0 = WS2812_T1H;
      led_data_buffer[offset + bit].level1 = 0;
      led_data_buffer[offset + bit].duration1 = WS2812_T1L;
    } else {
      // Bit is 0
      led_data_buffer[offset + bit].level0 = 1;
      led_data_buffer[offset + bit].duration0 = WS2812_T0H;
      led_data_buffer[offset + bit].level1 = 0;
      led_data_buffer[offset + bit].duration1 = WS2812_T0L;
    }
  }
}

void clearAll() {
  for(int i = 0; i < NUM_LEDS; i++) {
    setPixelColor(i, 0, 0, 0);
  }
}

void show() {
  rmt_write_items(RMT_TX_CHANNEL, led_data_buffer, NUM_LEDS * 24, true);
}

void setup() {
  Serial.begin(115200);
  delay(2000);
  
  Serial.println("\n========================================");
  Serial.println("ESP32-S3 RMT DIRECT TEST");
  Serial.println("========================================\n");
  
  // Enable AHCT125
  pinMode(AHCT125_OE_PIN, OUTPUT);
  digitalWrite(AHCT125_OE_PIN, LOW);
  Serial.println("✓ AHCT125 enabled (GPIO48 = LOW)\n");
  
  // Initialize RMT on GPIO16
  Serial.println("Initializing RMT on GPIO16...");
  rmt_init();
  Serial.println("✓ RMT initialized\n");
  
  // Clear all LEDs
  Serial.println("Clearing all LEDs...");
  clearAll();
  show();
  delay(500);
  Serial.println("✓ All LEDs cleared\n");
  
  Serial.println("Starting LED test with FULL brightness...\n");
}

int ledNum = 0;

void loop() {
  digitalWrite(AHCT125_OE_PIN, LOW);
  
  clearAll();
  
  Serial.print("LED ");
  Serial.print(ledNum);
  Serial.println(" = FULL WHITE");
  
  setPixelColor(ledNum, 255, 255, 255);
  show();
  
  Serial.println("  Check AHCT125 pin 2 during transmission (should pulse)");
  Serial.println("  Check LED strip!\n");
  
  delay(1000);
  
  ledNum++;
  if(ledNum >= 10) {
    ledNum = 0;
    Serial.println("--- Restarting cycle ---\n");
    delay(1000);
  }
}
