/*
 * Last Drop Earth - TFT Display Configuration
 * ILI9488 3.5" 480x320 SPI Display with XPT2046 Touch
 * 
 * Hardware Connections:
 * TFT GND   → ESP32 GND
 * TFT VCC   → ESP32 3V3
 * TFT LED   → ESP32 3V3 (always on) or GPIO for PWM dimming
 * 
 * TFT DC    → GPIO4
 * TFT CS    → GPIO5
 * TFT RST   → GPIO6
 * 
 * TOUCH_CS  → GPIO15
 * SD_CS     → GPIO10
 * 
 * Shared SPI Bus:
 * GPIO35 → TFT MOSI + TOUCH_DIN + SD_MOSI
 * GPIO36 → TFT SCK  + TOUCH_CLK + SD_SCK
 * GPIO37 → TFT MISO + TOUCH_DO  + SD_MISO
 */

#ifndef DISPLAY_CONFIG_H
#define DISPLAY_CONFIG_H

// ==================== DISPLAY HARDWARE PINS ====================
// Note: Most pins are configured in TFT_eSPI User_Setup.h
// These are for reference and touch controller
#define TFT_MOSI_PIN    35
#define TFT_MISO_PIN    37
#define TFT_SCLK_PIN    36
#define TFT_CS_PIN      5
#define TFT_DC_PIN      4
#define TFT_RST_PIN     6

#ifndef TOUCH_CS_PIN
#define TOUCH_CS_PIN    15
#endif
#define SD_CS_PIN       10

// ==================== DISPLAY SETTINGS ====================
#define TFT_WIDTH   480
#define TFT_HEIGHT  320
#define TFT_ROTATION 1  // Landscape mode

// SPI Speed (ILI9488 supports up to 40MHz, use 27MHz for stability)
#define TFT_SPI_FREQ     27000000
#define TOUCH_SPI_FREQ   2500000

// ==================== COLOR PALETTE (Kids Friendly) ====================
// Using 16-bit RGB565 format
#define COLOR_BG_DARK       0x1082  // Dark blue-gray background
#define COLOR_BG_LIGHT      0x2945  // Lighter background
#define COLOR_WHITE         0xFFFF
#define COLOR_BLACK         0x0000

// Primary colors (bright, kid-friendly)
#define COLOR_BLUE          0x34DF  // Bright sky blue
#define COLOR_GREEN         0x07E0  // Bright green
#define COLOR_RED           0xF800  // Bright red
#define COLOR_YELLOW        0xFFE0  // Bright yellow
#define COLOR_ORANGE        0xFD20  // Bright orange
#define COLOR_PURPLE        0x881F  // Purple
#define COLOR_CYAN          0x07FF  // Cyan
#define COLOR_PINK          0xFC18  // Pink

// UI Colors
#define COLOR_BUTTON        0x2945  // Button background
#define COLOR_BUTTON_PRESS  0x4A69  // Button pressed
#define COLOR_BUTTON_TEXT   0xFFFF  // Button text
#define COLOR_ACCENT        0x34DF  // Accent color (sky blue)
#define COLOR_SUCCESS       0x07E0  // Green for success
#define COLOR_ERROR         0xF800  // Red for errors
#define COLOR_WARNING       0xFD20  // Orange for warnings

// Player colors (matching LED colors)
#define COLOR_PLAYER_RED    0xF800
#define COLOR_PLAYER_GREEN  0x07E0
#define COLOR_PLAYER_BLUE   0x001F
#define COLOR_PLAYER_YELLOW 0xFFE0

// ==================== UI DIMENSIONS ====================
#define BUTTON_HEIGHT       60
#define BUTTON_RADIUS       10
#define BUTTON_MARGIN       15
#define ICON_SIZE           64
#define ICON_SIZE_SMALL     48
#define FONT_SIZE_LARGE     3
#define FONT_SIZE_MEDIUM    2
#define FONT_SIZE_SMALL     1

// Touch calibration (adjust based on your display)
#define TOUCH_MIN_X         200
#define TOUCH_MAX_X         3800
#define TOUCH_MIN_Y         200
#define TOUCH_MAX_Y         3800

// ==================== ANIMATION SETTINGS ====================
#define ANIMATION_FPS       30
#define FRAME_DELAY_MS      (1000 / ANIMATION_FPS)
#define FADE_STEPS          10
#define DICE_ZOOM_FRAMES    15
#define CLOUDIE_FLOAT_SPEED 2
#define CARD_FLIP_FRAMES    12  // Frames for card flip animation
#define CARD_DISPLAY_TIME   3000  // Show card for 3 seconds

// ==================== SCREEN IDS ====================
enum ScreenID {
  SCREEN_LOGO,
  SCREEN_GAME_SELECT,
  SCREEN_PLAYER_SELECT,
  SCREEN_PROFILE_CREATE,
  SCREEN_COLOR_SELECT,
  SCREEN_DICE_SELECT,
  SCREEN_DICE_CONNECT,
  SCREEN_GAMEPLAY,
  SCREEN_CHANCE_CARD,  // Chance card overlay
  SCREEN_GAME_OVER
};

// ==================== PLAYER PROFILE ====================
#define MAX_PROFILES        6   // Cloudie AI + Guest + 4 custom
#define MAX_NICKNAME_LEN    12
#define MAX_PLAYERS         4

struct PlayerProfile {
  char nickname[MAX_NICKNAME_LEN + 1];
  bool isAI;
  bool isGuest;
  uint16_t avatarColor;
  uint32_t gamesPlayed;
  uint32_t gamesWon;
};

// ==================== GAME STATE FOR DISPLAY ====================
struct DisplayGameState {
  ScreenID currentScreen;
  int selectedPlayers;
  int selectedProfiles[MAX_PLAYERS];
  uint16_t playerColors[MAX_PLAYERS];
  bool useSmartDice;
  int diceCount;  // 1 or 2
  int lastDiceValue;
  int lastDiceValue2;  // For 2-dice mode
  bool diceAnimating;
  int cloudieY;  // For floating animation
  bool cloudieUp;
  
  // Chance card state
  bool showingChanceCard;
  int chanceCardNumber;
  const char* chanceCardText;
  int chanceCardEffect;
  int cardFlipFrame;
  unsigned long cardShowTime;
  ScreenID returnScreen;  // Screen to return to after card
};

// ==================== CHANCE CARD DATA ====================
struct ChanceCardDisplay {
  int number;
  const char* description;
  int effect;
  uint16_t cardColor;  // Color based on effect
};

#endif // DISPLAY_CONFIG_H
