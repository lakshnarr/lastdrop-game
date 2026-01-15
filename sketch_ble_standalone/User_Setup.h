// User_Setup.h for Last Drop Earth ESP32-S3 + ILI9488 Display
// Copy this file to your TFT_eSPI library folder, replacing the existing User_Setup.h
// OR include before TFT_eSPI.h

#define USER_SETUP_LOADED

// ==================== DISPLAY DRIVER ====================
#define ILI9488_DRIVER

// ==================== ESP32-S3 SPI PINS ====================
#define TFT_MISO  37
#define TFT_MOSI  35
#define TFT_SCLK  36
#define TFT_CS    5
#define TFT_DC    4
#define TFT_RST   6

// ==================== TOUCH (XPT2046) ====================
#define TOUCH_CS  15

// ==================== DISPLAY SETTINGS ====================
#define TFT_WIDTH  320
#define TFT_HEIGHT 480

// Color order - ILI9488 uses BGR
#define TFT_RGB_ORDER TFT_BGR

// ==================== SPI FREQUENCY ====================
#define SPI_FREQUENCY       27000000  // 27MHz for stability
#define SPI_READ_FREQUENCY  20000000
#define SPI_TOUCH_FREQUENCY  2500000

// ==================== FONTS ====================
#define LOAD_GLCD   // Font 1: Original Adafruit 8 pixel font
#define LOAD_FONT2  // Font 2: Small 16 pixel high font
#define LOAD_FONT4  // Font 4: Medium 26 pixel high font
#define LOAD_FONT6  // Font 6: Large 48 pixel high font (numbers only)
#define LOAD_FONT7  // Font 7: 7 segment 48 pixel font (numbers only)
#define LOAD_FONT8  // Font 8: Large 75 pixel font (numbers only)
#define LOAD_GFXFF  // FreeFonts

#define SMOOTH_FONT

// ==================== MISC ====================
// #define TFT_BL     -1  // LED backlight pin (not using PWM control)
// #define TFT_BACKLIGHT_ON HIGH

// For ESP32-S3 with PSRAM
// #define USE_HSPI_PORT

// Transaction support for SD card sharing
#define SUPPORT_TRANSACTIONS
