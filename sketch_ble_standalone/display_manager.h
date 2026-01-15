/*
 * Last Drop Earth - TFT Display Manager
 * Handles all display rendering and touch input
 * 
 * Uses TFT_eSPI library for ILI9488 display
 * Uses XPT2046_Touchscreen for touch input
 */

#ifndef DISPLAY_MANAGER_H
#define DISPLAY_MANAGER_H

// TFT_eSPI setup must be done before including the library
// We'll use the User_Setup_Select.h approach by defining in the main sketch
#include <TFT_eSPI.h>
#include <XPT2046_Touchscreen.h>
#include <SPI.h>
#include "display_config.h"

// ==================== GLOBAL DISPLAY OBJECTS ====================
TFT_eSPI tft = TFT_eSPI();
XPT2046_Touchscreen touch(TOUCH_CS_PIN);

// Display state
DisplayGameState displayState;
PlayerProfile profiles[MAX_PROFILES];
int profileCount = 2;  // Start with Cloudie AI + Guest
unsigned long lastFrameTime = 0;
unsigned long lastTouchTime = 0;
const unsigned long TOUCH_DEBOUNCE = 200;

// Animation state
int diceZoomFrame = 0;
int cloudieFrame = 0;
bool sdCardReady = false;

// ==================== FORWARD DECLARATIONS ====================
void initDefaultProfiles();
void drawScoreboard();
void drawCloudie();
void drawDiceResult();
void drawGameButtons();
void handlePlayerSelectTouch(int tx, int ty);
void handleProfileCreateTouch(int tx, int ty);
void handleColorSelectTouch(int tx, int ty);
void handleDiceSelectTouch(int tx, int ty);
void handleGameplayTouch(int tx, int ty);
void handleChanceCardTouch(int tx, int ty);
void rollVirtualDice();
void changeScreen(ScreenID newScreen);
void showDiceResult(int value, int value2 = 0);
void drawChanceCardScreen();
void drawChanceCardFlipFrame(int frame);
void showChanceCard(int cardIndex, ScreenID returnTo);
void updateChanceCard();
void dismissChanceCard();

// ==================== INITIALIZATION ====================

void initDisplay() {
  Serial.println("\n========================================");
  Serial.println("Initializing TFT Display (ILI9488)");
  Serial.println("========================================");
  
  // Initialize TFT (TFT_eSPI configured via User_Setup.h or build flags)
  tft.init();
  tft.setRotation(TFT_ROTATION);
  tft.fillScreen(COLOR_BLACK);
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  
  Serial.printf("  Display: %dx%d\n", tft.width(), tft.height());
  Serial.println("✓ TFT initialized");
  
  // Initialize touch
  touch.begin();
  touch.setRotation(TFT_ROTATION);
  Serial.println("✓ Touch initialized");
  
  // SD card init removed for simplicity - can add back if needed
  
  // Initialize default profiles
  initDefaultProfiles();
  
  // Initialize display state
  displayState.currentScreen = SCREEN_LOGO;
  displayState.selectedPlayers = 0;
  displayState.useSmartDice = true;
  displayState.diceCount = 1;
  displayState.lastDiceValue = 0;
  displayState.diceAnimating = false;
  displayState.cloudieY = 180;
  displayState.cloudieUp = true;
  
  // Chance card state
  displayState.showingChanceCard = false;
  displayState.chanceCardNumber = 0;
  displayState.chanceCardText = "";
  displayState.chanceCardEffect = 0;
  displayState.cardFlipFrame = 0;
  displayState.cardShowTime = 0;
  displayState.returnScreen = SCREEN_GAMEPLAY;
  
  Serial.println("✓ Display manager ready\n");
}

void initDefaultProfiles() {
  // Profile 0: Cloudie AI
  strcpy(profiles[0].nickname, "Cloudie AI");
  profiles[0].isAI = true;
  profiles[0].isGuest = false;
  profiles[0].avatarColor = COLOR_CYAN;
  profiles[0].gamesPlayed = 0;
  profiles[0].gamesWon = 0;
  
  // Profile 1: Guest
  strcpy(profiles[1].nickname, "Guest");
  profiles[1].isAI = false;
  profiles[1].isGuest = true;
  profiles[1].avatarColor = COLOR_PURPLE;
  profiles[1].gamesPlayed = 0;
  profiles[1].gamesWon = 0;
  
  profileCount = 2;
}

// ==================== TOUCH HANDLING ====================

struct TouchPoint {
  int x;
  int y;
  bool pressed;
};

TouchPoint getTouchPoint() {
  TouchPoint tp = {0, 0, false};
  
  if (touch.touched()) {
    TS_Point p = touch.getPoint();
    
    // Map touch coordinates to screen coordinates
    tp.x = map(p.x, TOUCH_MIN_X, TOUCH_MAX_X, 0, TFT_WIDTH);
    tp.y = map(p.y, TOUCH_MIN_Y, TOUCH_MAX_Y, 0, TFT_HEIGHT);
    tp.pressed = true;
    
    // Clamp to screen bounds
    tp.x = constrain(tp.x, 0, TFT_WIDTH - 1);
    tp.y = constrain(tp.y, 0, TFT_HEIGHT - 1);
  }
  
  return tp;
}

bool isTouchInRect(int tx, int ty, int x, int y, int w, int h) {
  return (tx >= x && tx < x + w && ty >= y && ty < y + h);
}

// ==================== DRAWING PRIMITIVES ====================

void drawRoundButton(int x, int y, int w, int h, const char* text, uint16_t bgColor, uint16_t textColor, bool pressed) {
  uint16_t color = pressed ? COLOR_BUTTON_PRESS : bgColor;
  
  // Draw rounded rectangle
  tft.fillRoundRect(x, y, w, h, BUTTON_RADIUS, color);
  tft.drawRoundRect(x, y, w, h, BUTTON_RADIUS, COLOR_WHITE);
  
  // Draw centered text
  int16_t textW = strlen(text) * 6 * FONT_SIZE_MEDIUM;
  int16_t textH = 8 * FONT_SIZE_MEDIUM;
  int16_t textX = x + (w - textW) / 2;
  int16_t textY = y + (h - textH) / 2;
  
  tft.setTextColor(textColor);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  tft.setCursor(textX, textY);
  tft.print(text);
}

void drawIconButton(int x, int y, int size, const char* label, uint16_t iconColor, bool selected) {
  // Draw icon background (circle)
  int radius = size / 2;
  int cx = x + radius;
  int cy = y + radius;
  
  if (selected) {
    tft.fillCircle(cx, cy, radius + 4, COLOR_ACCENT);
  }
  tft.fillCircle(cx, cy, radius, iconColor);
  tft.drawCircle(cx, cy, radius, COLOR_WHITE);
  
  // Draw label below
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_SMALL);
  int16_t labelW = strlen(label) * 6;
  tft.setCursor(x + (size - labelW) / 2, y + size + 5);
  tft.print(label);
}

void drawTitle(const char* title) {
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_LARGE);
  int16_t textW = strlen(title) * 6 * FONT_SIZE_LARGE;
  tft.setCursor((TFT_WIDTH - textW) / 2, 20);
  tft.print(title);
}

void drawSubtitle(const char* subtitle, int y) {
  tft.setTextColor(COLOR_ACCENT);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  int16_t textW = strlen(subtitle) * 6 * FONT_SIZE_MEDIUM;
  tft.setCursor((TFT_WIDTH - textW) / 2, y);
  tft.print(subtitle);
}

// ==================== LOGO / SPLASH SCREEN ====================

void drawLogoScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  
  // Draw simple water drop logo (unicolor white)
  int cx = TFT_WIDTH / 2;
  int cy = TFT_HEIGHT / 2 - 30;
  
  // Water drop shape using circles and triangle
  // Top curve
  tft.fillCircle(cx, cy - 20, 40, COLOR_WHITE);
  // Bottom point (triangle approximation with filled circles)
  for (int i = 0; i < 60; i++) {
    int radius = 40 - i * 0.6;
    if (radius > 0) {
      tft.fillCircle(cx, cy - 20 + i, radius, COLOR_WHITE);
    }
  }
  
  // Inner detail (darker blue to show depth)
  tft.fillCircle(cx - 10, cy - 25, 12, COLOR_BLUE);
  
  // Title
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_LARGE);
  const char* title = "LAST DROP";
  int16_t textW = strlen(title) * 6 * FONT_SIZE_LARGE;
  tft.setCursor((TFT_WIDTH - textW) / 2, cy + 70);
  tft.print(title);
  
  // Subtitle
  tft.setTextColor(COLOR_ACCENT);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  const char* subtitle = "E A R T H";
  textW = strlen(subtitle) * 6 * FONT_SIZE_MEDIUM;
  tft.setCursor((TFT_WIDTH - textW) / 2, cy + 100);
  tft.print(subtitle);
  
  // Tap to continue
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_SMALL);
  const char* tap = "Tap to start";
  textW = strlen(tap) * 6 * FONT_SIZE_SMALL;
  tft.setCursor((TFT_WIDTH - textW) / 2, TFT_HEIGHT - 40);
  tft.print(tap);
}

// ==================== GAME SELECTION SCREEN ====================

void drawGameSelectScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("SELECT GAME");
  
  // For now, only Last Drop Earth
  int btnW = 300;
  int btnH = 100;
  int btnX = (TFT_WIDTH - btnW) / 2;
  int btnY = (TFT_HEIGHT - btnH) / 2;
  
  // Draw game card
  tft.fillRoundRect(btnX, btnY, btnW, btnH, 15, COLOR_BUTTON);
  tft.drawRoundRect(btnX, btnY, btnW, btnH, 15, COLOR_ACCENT);
  
  // Draw mini water drop icon
  int iconX = btnX + 30;
  int iconY = btnY + btnH/2;
  tft.fillCircle(iconX, iconY - 10, 15, COLOR_BLUE);
  tft.fillCircle(iconX, iconY + 5, 12, COLOR_BLUE);
  tft.fillCircle(iconX, iconY + 15, 8, COLOR_BLUE);
  
  // Game title
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  tft.setCursor(btnX + 70, btnY + 25);
  tft.print("Last Drop Earth");
  
  tft.setTextColor(COLOR_ACCENT);
  tft.setTextSize(FONT_SIZE_SMALL);
  tft.setCursor(btnX + 70, btnY + 55);
  tft.print("Save water, save the world!");
}

// ==================== PLAYER SELECTION SCREEN ====================

void drawPlayerSelectScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("SELECT PLAYERS");
  drawSubtitle("Choose 2-4 players", 55);
  
  // Draw profile icons in a grid
  int startX = 30;
  int startY = 90;
  int iconSpacing = 100;
  
  for (int i = 0; i < profileCount && i < MAX_PROFILES; i++) {
    int col = i % 4;
    int row = i / 4;
    int x = startX + col * iconSpacing;
    int y = startY + row * (ICON_SIZE + 40);
    
    // Check if this profile is selected
    bool selected = false;
    for (int j = 0; j < displayState.selectedPlayers; j++) {
      if (displayState.selectedProfiles[j] == i) {
        selected = true;
        break;
      }
    }
    
    drawIconButton(x, y, ICON_SIZE, profiles[i].nickname, profiles[i].avatarColor, selected);
    
    // Draw AI badge
    if (profiles[i].isAI) {
      tft.setTextColor(COLOR_CYAN);
      tft.setTextSize(1);
      tft.setCursor(x + ICON_SIZE - 15, y + 5);
      tft.print("AI");
    }
  }
  
  // Create Profile button (if room for more)
  if (profileCount < MAX_PROFILES) {
    int col = profileCount % 4;
    int row = profileCount / 4;
    int x = startX + col * iconSpacing;
    int y = startY + row * (ICON_SIZE + 40);
    
    // Plus icon
    tft.drawRoundRect(x, y, ICON_SIZE, ICON_SIZE, 10, COLOR_WHITE);
    tft.drawLine(x + ICON_SIZE/2, y + 15, x + ICON_SIZE/2, y + ICON_SIZE - 15, COLOR_WHITE);
    tft.drawLine(x + 15, y + ICON_SIZE/2, x + ICON_SIZE - 15, y + ICON_SIZE/2, COLOR_WHITE);
    
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_SMALL);
    tft.setCursor(x, y + ICON_SIZE + 5);
    tft.print("Create");
  }
  
  // Player count display
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  char countStr[20];
  sprintf(countStr, "Selected: %d/4", displayState.selectedPlayers);
  tft.setCursor(30, TFT_HEIGHT - 80);
  tft.print(countStr);
  
  // Next button (enabled if 2-4 players selected)
  int btnX = TFT_WIDTH - 150;
  int btnY = TFT_HEIGHT - 70;
  bool canProceed = (displayState.selectedPlayers >= 2 && displayState.selectedPlayers <= 4);
  uint16_t btnColor = canProceed ? COLOR_SUCCESS : COLOR_BUTTON;
  drawRoundButton(btnX, btnY, 120, 50, "NEXT >", btnColor, COLOR_WHITE, false);
}

// ==================== PROFILE CREATION SCREEN ====================

void drawProfileCreateScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("CREATE PROFILE");
  
  // Simple name entry (using preset names for simplicity)
  const char* presetNames[] = {"Player 1", "Player 2", "Player 3", "Player 4", "Hero", "Star"};
  const uint16_t presetColors[] = {COLOR_RED, COLOR_GREEN, COLOR_BLUE, COLOR_YELLOW, COLOR_ORANGE, COLOR_PINK};
  
  drawSubtitle("Choose a name:", 70);
  
  int startX = 40;
  int startY = 110;
  int btnW = 130;
  int btnH = 50;
  int margin = 15;
  
  for (int i = 0; i < 6; i++) {
    int col = i % 3;
    int row = i / 3;
    int x = startX + col * (btnW + margin);
    int y = startY + row * (btnH + margin);
    
    drawRoundButton(x, y, btnW, btnH, presetNames[i], presetColors[i], COLOR_WHITE, false);
  }
  
  // Cancel button
  drawRoundButton(30, TFT_HEIGHT - 70, 100, 50, "CANCEL", COLOR_ERROR, COLOR_WHITE, false);
}

// ==================== COLOR SELECTION SCREEN ====================

void drawColorSelectScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("CHOOSE COLORS");
  
  // Color options
  const uint16_t colors[] = {COLOR_PLAYER_RED, COLOR_PLAYER_GREEN, COLOR_PLAYER_BLUE, COLOR_PLAYER_YELLOW};
  const char* colorNames[] = {"Red", "Green", "Blue", "Yellow"};
  
  int startY = 80;
  int rowHeight = 60;
  
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    int y = startY + p * rowHeight;
    
    // Player name
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_MEDIUM);
    tft.setCursor(20, y + 15);
    tft.print(profiles[displayState.selectedProfiles[p]].nickname);
    
    // Color buttons
    int colorX = 180;
    for (int c = 0; c < 4; c++) {
      bool selected = (displayState.playerColors[p] == colors[c]);
      bool taken = false;
      
      // Check if color is taken by another player
      for (int other = 0; other < p; other++) {
        if (displayState.playerColors[other] == colors[c]) {
          taken = true;
          break;
        }
      }
      
      int btnX = colorX + c * 70;
      if (taken) {
        // Dimmed color (taken)
        tft.fillCircle(btnX + 25, y + 20, 20, COLOR_BG_LIGHT);
      } else {
        tft.fillCircle(btnX + 25, y + 20, 20, colors[c]);
        if (selected) {
          tft.drawCircle(btnX + 25, y + 20, 25, COLOR_WHITE);
          tft.drawCircle(btnX + 25, y + 20, 26, COLOR_WHITE);
        }
      }
    }
  }
  
  // Next button
  bool allSelected = true;
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    if (displayState.playerColors[p] == 0) {
      allSelected = false;
      break;
    }
  }
  
  int btnX = TFT_WIDTH - 150;
  int btnY = TFT_HEIGHT - 70;
  uint16_t btnColor = allSelected ? COLOR_SUCCESS : COLOR_BUTTON;
  drawRoundButton(btnX, btnY, 120, 50, "NEXT >", btnColor, COLOR_WHITE, false);
  
  // Back button
  drawRoundButton(30, TFT_HEIGHT - 70, 100, 50, "< BACK", COLOR_BUTTON, COLOR_WHITE, false);
}

// ==================== DICE SELECTION SCREEN ====================

void drawDiceSelectScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("CHOOSE DICE");
  
  int centerY = TFT_HEIGHT / 2 - 20;
  
  // Smart Dice button
  int smartX = 50;
  bool smartSelected = displayState.useSmartDice;
  tft.fillRoundRect(smartX, centerY - 50, 170, 120, 15, smartSelected ? COLOR_ACCENT : COLOR_BUTTON);
  tft.drawRoundRect(smartX, centerY - 50, 170, 120, 15, COLOR_WHITE);
  
  // Dice icon (simple)
  tft.fillRoundRect(smartX + 60, centerY - 30, 50, 50, 8, COLOR_WHITE);
  tft.fillCircle(smartX + 75, centerY - 15, 4, COLOR_BLACK);
  tft.fillCircle(smartX + 95, centerY + 5, 4, COLOR_BLACK);
  tft.fillCircle(smartX + 75, centerY + 5, 4, COLOR_BLACK);
  
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  tft.setCursor(smartX + 30, centerY + 40);
  tft.print("Smart Dice");
  
  // Virtual Dice button
  int virtX = 260;
  bool virtSelected = !displayState.useSmartDice;
  tft.fillRoundRect(virtX, centerY - 50, 170, 120, 15, virtSelected ? COLOR_ACCENT : COLOR_BUTTON);
  tft.drawRoundRect(virtX, centerY - 50, 170, 120, 15, COLOR_WHITE);
  
  // Touch icon (hand)
  tft.fillCircle(virtX + 85, centerY - 10, 25, COLOR_WHITE);
  tft.fillRoundRect(virtX + 75, centerY + 5, 20, 30, 5, COLOR_WHITE);
  
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  tft.setCursor(virtX + 20, centerY + 40);
  tft.print("Virtual Dice");
  
  // If Smart Dice selected, show 1 or 2 dice option
  if (displayState.useSmartDice) {
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_SMALL);
    tft.setCursor(50, centerY + 90);
    tft.print("Number of dice:");
    
    // 1 dice button
    bool one = (displayState.diceCount == 1);
    drawRoundButton(200, centerY + 80, 60, 40, "1", one ? COLOR_SUCCESS : COLOR_BUTTON, COLOR_WHITE, false);
    
    // 2 dice button
    bool two = (displayState.diceCount == 2);
    drawRoundButton(280, centerY + 80, 60, 40, "2", two ? COLOR_SUCCESS : COLOR_BUTTON, COLOR_WHITE, false);
  }
  
  // Start Game button
  drawRoundButton((TFT_WIDTH - 200) / 2, TFT_HEIGHT - 70, 200, 50, "START GAME", COLOR_SUCCESS, COLOR_WHITE, false);
  
  // Back button
  drawRoundButton(30, TFT_HEIGHT - 70, 100, 50, "< BACK", COLOR_BUTTON, COLOR_WHITE, false);
}

// ==================== DICE CONNECTION SCREEN ====================

void drawDiceConnectScreen(const char* status, bool success) {
  tft.fillScreen(COLOR_BG_DARK);
  drawTitle("CONNECTING...");
  
  // Animated dots or status
  int centerY = TFT_HEIGHT / 2;
  
  // Dice icon
  tft.fillRoundRect((TFT_WIDTH - 80) / 2, centerY - 60, 80, 80, 10, COLOR_WHITE);
  
  // Status text
  tft.setTextColor(success ? COLOR_SUCCESS : COLOR_ACCENT);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  int16_t textW = strlen(status) * 6 * FONT_SIZE_MEDIUM;
  tft.setCursor((TFT_WIDTH - textW) / 2, centerY + 50);
  tft.print(status);
  
  if (!success) {
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_SMALL);
    const char* hint = "Roll your dice to wake it up!";
    textW = strlen(hint) * 6 * FONT_SIZE_SMALL;
    tft.setCursor((TFT_WIDTH - textW) / 2, centerY + 80);
    tft.print(hint);
  }
}

// ==================== GAMEPLAY SCREEN ====================

void drawGameplayScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  
  // Scoreboard area (top)
  drawScoreboard();
  
  // Cloudie animation area (center-left)
  drawCloudie();
  
  // Dice display area (center-right)
  if (displayState.diceAnimating || displayState.lastDiceValue > 0) {
    drawDiceResult();
  }
  
  // Navigation buttons (bottom)
  drawGameButtons();
}

void drawScoreboard() {
  int startX = 10;
  int startY = 10;
  int cardW = (TFT_WIDTH - 40) / displayState.selectedPlayers;
  int cardH = 70;
  
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    int x = startX + p * (cardW + 5);
    uint16_t color = displayState.playerColors[p];
    
    // Player card
    tft.fillRoundRect(x, startY, cardW - 5, cardH, 8, COLOR_BUTTON);
    tft.drawRoundRect(x, startY, cardW - 5, cardH, 8, color);
    
    // Color indicator
    tft.fillCircle(x + 20, startY + 20, 10, color);
    
    // Player name
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_SMALL);
    tft.setCursor(x + 35, startY + 15);
    // Truncate long names
    char shortName[8];
    strncpy(shortName, profiles[displayState.selectedProfiles[p]].nickname, 7);
    shortName[7] = '\0';
    tft.print(shortName);
    
    // Score (from main game state - will be linked)
    tft.setTextSize(FONT_SIZE_MEDIUM);
    tft.setCursor(x + 35, startY + 40);
    tft.print("10");  // Placeholder - link to actual score
    
    // Water drop icon
    tft.setTextColor(COLOR_BLUE);
    tft.setTextSize(FONT_SIZE_SMALL);
    tft.setCursor(x + 70, startY + 45);
    tft.print("drops");
  }
}

void drawCloudie() {
  // Floating cloud character
  int cx = 100;
  int cy = displayState.cloudieY;
  
  // Cloud body (simple circles)
  tft.fillCircle(cx, cy, 35, COLOR_WHITE);
  tft.fillCircle(cx - 30, cy + 10, 25, COLOR_WHITE);
  tft.fillCircle(cx + 30, cy + 10, 25, COLOR_WHITE);
  tft.fillCircle(cx - 15, cy - 15, 20, COLOR_WHITE);
  tft.fillCircle(cx + 15, cy - 15, 20, COLOR_WHITE);
  
  // Eyes
  tft.fillCircle(cx - 12, cy, 5, COLOR_BLACK);
  tft.fillCircle(cx + 12, cy, 5, COLOR_BLACK);
  
  // Smile
  tft.drawArc(cx, cy + 5, 15, 12, 200, 340, COLOR_BLACK, COLOR_BLACK);
  
  // Speech bubble (if message)
  // TODO: Add message display
}

void drawDiceResult() {
  int cx = 320;
  int cy = 180;
  int size = 80;
  
  if (displayState.diceAnimating) {
    // Zoom animation
    size = 40 + (diceZoomFrame * 3);
    if (size > 100) size = 100;
  }
  
  // Dice background
  tft.fillRoundRect(cx - size/2, cy - size/2, size, size, 10, COLOR_WHITE);
  tft.drawRoundRect(cx - size/2, cy - size/2, size, size, 10, COLOR_BLACK);
  
  // Dice pips
  int pip = size / 8;
  int val = displayState.lastDiceValue;
  
  // Center pip (1, 3, 5)
  if (val == 1 || val == 3 || val == 5) {
    tft.fillCircle(cx, cy, pip, COLOR_BLACK);
  }
  
  // Top-left and bottom-right (2, 3, 4, 5, 6)
  if (val >= 2) {
    tft.fillCircle(cx - size/4, cy - size/4, pip, COLOR_BLACK);
    tft.fillCircle(cx + size/4, cy + size/4, pip, COLOR_BLACK);
  }
  
  // Top-right and bottom-left (4, 5, 6)
  if (val >= 4) {
    tft.fillCircle(cx + size/4, cy - size/4, pip, COLOR_BLACK);
    tft.fillCircle(cx - size/4, cy + size/4, pip, COLOR_BLACK);
  }
  
  // Middle left and right (6)
  if (val == 6) {
    tft.fillCircle(cx - size/4, cy, pip, COLOR_BLACK);
    tft.fillCircle(cx + size/4, cy, pip, COLOR_BLACK);
  }
  
  // Second dice if using 2 dice
  if (displayState.diceCount == 2 && displayState.lastDiceValue2 > 0) {
    // Draw second dice to the right
    int cx2 = cx + size + 20;
    tft.fillRoundRect(cx2 - size/2, cy - size/2, size, size, 10, COLOR_WHITE);
    tft.drawRoundRect(cx2 - size/2, cy - size/2, size, size, 10, COLOR_BLACK);
    
    // Draw pips for second dice (simplified)
    int val2 = displayState.lastDiceValue2;
    if (val2 == 1 || val2 == 3 || val2 == 5) {
      tft.fillCircle(cx2, cy, pip, COLOR_BLACK);
    }
    if (val2 >= 2) {
      tft.fillCircle(cx2 - size/4, cy - size/4, pip, COLOR_BLACK);
      tft.fillCircle(cx2 + size/4, cy + size/4, pip, COLOR_BLACK);
    }
    if (val2 >= 4) {
      tft.fillCircle(cx2 + size/4, cy - size/4, pip, COLOR_BLACK);
      tft.fillCircle(cx2 - size/4, cy + size/4, pip, COLOR_BLACK);
    }
    if (val2 == 6) {
      tft.fillCircle(cx2 - size/4, cy, pip, COLOR_BLACK);
      tft.fillCircle(cx2 + size/4, cy, pip, COLOR_BLACK);
    }
  }
}

void drawGameButtons() {
  int btnY = TFT_HEIGHT - 60;
  
  // Undo button
  drawRoundButton(20, btnY, 100, 45, "UNDO", COLOR_ORANGE, COLOR_WHITE, false);
  
  // Current player indicator
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  tft.setCursor(TFT_WIDTH / 2 - 60, btnY + 12);
  tft.print("Player 1's turn");  // Placeholder
  
  // End Game button
  drawRoundButton(TFT_WIDTH - 120, btnY, 100, 45, "END", COLOR_ERROR, COLOR_WHITE, false);
}

// ==================== VIRTUAL DICE ====================

void drawVirtualDice(int value, bool rolling) {
  int cx = TFT_WIDTH / 2;
  int cy = TFT_HEIGHT / 2;
  int size = 120;
  
  if (rolling) {
    // Random position jitter
    cx += random(-5, 6);
    cy += random(-5, 6);
    value = random(1, 7);
  }
  
  // Dice shadow
  tft.fillRoundRect(cx - size/2 + 5, cy - size/2 + 5, size, size, 15, COLOR_BG_LIGHT);
  
  // Dice body
  tft.fillRoundRect(cx - size/2, cy - size/2, size, size, 15, COLOR_WHITE);
  tft.drawRoundRect(cx - size/2, cy - size/2, size, size, 15, COLOR_BLACK);
  
  // Draw pips
  int pip = 10;
  
  if (value == 1 || value == 3 || value == 5) {
    tft.fillCircle(cx, cy, pip, COLOR_BLACK);
  }
  if (value >= 2) {
    tft.fillCircle(cx - 25, cy - 25, pip, COLOR_BLACK);
    tft.fillCircle(cx + 25, cy + 25, pip, COLOR_BLACK);
  }
  if (value >= 4) {
    tft.fillCircle(cx + 25, cy - 25, pip, COLOR_BLACK);
    tft.fillCircle(cx - 25, cy + 25, pip, COLOR_BLACK);
  }
  if (value == 6) {
    tft.fillCircle(cx - 25, cy, pip, COLOR_BLACK);
    tft.fillCircle(cx + 25, cy, pip, COLOR_BLACK);
  }
  
  // Tap instruction
  if (!rolling) {
    tft.setTextColor(COLOR_WHITE);
    tft.setTextSize(FONT_SIZE_SMALL);
    const char* tap = "Tap dice to roll!";
    int16_t textW = strlen(tap) * 6;
    tft.setCursor((TFT_WIDTH - textW) / 2, cy + size/2 + 20);
    tft.print(tap);
  }
}

// ==================== GAME OVER SCREEN ====================

void drawGameOverScreen(int winnerId) {
  tft.fillScreen(COLOR_BG_DARK);
  
  // Winner announcement
  tft.setTextColor(COLOR_YELLOW);
  tft.setTextSize(FONT_SIZE_LARGE);
  const char* title = "WINNER!";
  int16_t textW = strlen(title) * 6 * FONT_SIZE_LARGE;
  tft.setCursor((TFT_WIDTH - textW) / 2, 60);
  tft.print(title);
  
  // Winner avatar
  int cx = TFT_WIDTH / 2;
  int cy = TFT_HEIGHT / 2 - 20;
  tft.fillCircle(cx, cy, 50, displayState.playerColors[winnerId]);
  tft.drawCircle(cx, cy, 55, COLOR_YELLOW);
  tft.drawCircle(cx, cy, 56, COLOR_YELLOW);
  
  // Winner name
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  const char* name = profiles[displayState.selectedProfiles[winnerId]].nickname;
  textW = strlen(name) * 6 * FONT_SIZE_MEDIUM;
  tft.setCursor((TFT_WIDTH - textW) / 2, cy + 70);
  tft.print(name);
  
  // Play again button
  drawRoundButton((TFT_WIDTH - 200) / 2, TFT_HEIGHT - 80, 200, 50, "PLAY AGAIN", COLOR_SUCCESS, COLOR_WHITE, false);
}

// ==================== CHANCE CARD DISPLAY ====================

void drawChanceCardScreen() {
  tft.fillScreen(COLOR_BG_DARK);
  
  // Card dimensions
  int cardW = 360;
  int cardH = 260;
  int cardX = (TFT_WIDTH - cardW) / 2;
  int cardY = (TFT_HEIGHT - cardH) / 2 - 20;
  int cornerRadius = 20;
  
  // Determine card color based on effect
  uint16_t cardBorderColor;
  uint16_t effectBgColor;
  const char* effectIcon;
  
  if (displayState.chanceCardEffect > 0) {
    cardBorderColor = COLOR_SUCCESS;
    effectBgColor = 0x0600;  // Dark green
    effectIcon = "+";
  } else if (displayState.chanceCardEffect < 0) {
    cardBorderColor = COLOR_ERROR;
    effectBgColor = 0x6000;  // Dark red
    effectIcon = "-";
  } else {
    cardBorderColor = COLOR_ACCENT;
    effectBgColor = COLOR_BG_LIGHT;
    effectIcon = "~";
  }
  
  // Draw card shadow
  tft.fillRoundRect(cardX + 6, cardY + 6, cardW, cardH, cornerRadius, COLOR_BG_LIGHT);
  
  // Draw card body
  tft.fillRoundRect(cardX, cardY, cardW, cardH, cornerRadius, COLOR_WHITE);
  
  // Draw card border (double line for effect cards)
  tft.drawRoundRect(cardX, cardY, cardW, cardH, cornerRadius, cardBorderColor);
  tft.drawRoundRect(cardX + 3, cardY + 3, cardW - 6, cardH - 6, cornerRadius - 3, cardBorderColor);
  
  // Card number badge (top left corner)
  int badgeSize = 50;
  int badgeX = cardX + 15;
  int badgeY = cardY + 15;
  tft.fillCircle(badgeX + badgeSize/2, badgeY + badgeSize/2, badgeSize/2, cardBorderColor);
  
  // Card number text
  tft.setTextColor(COLOR_WHITE);
  tft.setTextSize(FONT_SIZE_LARGE);
  char numStr[4];
  snprintf(numStr, sizeof(numStr), "%d", displayState.chanceCardNumber);
  int numX = badgeX + (badgeSize - strlen(numStr) * 6 * FONT_SIZE_LARGE) / 2;
  int numY = badgeY + (badgeSize - 8 * FONT_SIZE_LARGE) / 2;
  tft.setCursor(numX, numY);
  tft.print(numStr);
  
  // "CHANCE" title
  tft.setTextColor(COLOR_BG_DARK);
  tft.setTextSize(FONT_SIZE_LARGE);
  const char* chanceTitle = "CHANCE";
  int titleW = strlen(chanceTitle) * 6 * FONT_SIZE_LARGE;
  tft.setCursor(cardX + (cardW - titleW) / 2, cardY + 25);
  tft.print(chanceTitle);
  
  // Decorative line under title
  tft.drawFastHLine(cardX + 40, cardY + 55, cardW - 80, cardBorderColor);
  
  // Card description (word-wrapped)
  tft.setTextColor(COLOR_BG_DARK);
  tft.setTextSize(FONT_SIZE_MEDIUM);
  
  // Simple word wrap for description
  const char* desc = displayState.chanceCardText;
  int descX = cardX + 25;
  int descY = cardY + 75;
  int maxLineWidth = cardW - 50;
  int charWidth = 6 * FONT_SIZE_MEDIUM;
  int maxChars = maxLineWidth / charWidth;
  
  char line[50];
  int lineLen = 0;
  int wordStart = 0;
  int currentY = descY;
  
  for (int i = 0; desc[i] != '\0' && currentY < cardY + cardH - 100; i++) {
    if (desc[i] == ' ' || desc[i + 1] == '\0') {
      int wordLen = i - wordStart + (desc[i + 1] == '\0' ? 1 : 0);
      
      if (lineLen + wordLen >= maxChars) {
        // Print current line
        if (lineLen > 0) {
          line[lineLen] = '\0';
          tft.setCursor(descX, currentY);
          tft.print(line);
          currentY += 20;
          lineLen = 0;
        }
      }
      
      // Add word to line
      for (int j = wordStart; j <= i; j++) {
        if (desc[j] != '\0') {
          line[lineLen++] = desc[j];
        }
      }
      wordStart = i + 1;
    }
  }
  // Print last line
  if (lineLen > 0) {
    line[lineLen] = '\0';
    tft.setCursor(descX, currentY);
    tft.print(line);
  }
  
  // Effect indicator box at bottom
  int effectBoxY = cardY + cardH - 70;
  int effectBoxH = 50;
  tft.fillRoundRect(cardX + 30, effectBoxY, cardW - 60, effectBoxH, 10, effectBgColor);
  tft.drawRoundRect(cardX + 30, effectBoxY, cardW - 60, effectBoxH, 10, cardBorderColor);
  
  // Effect text
  char effectText[30];
  if (displayState.chanceCardEffect > 0) {
    snprintf(effectText, sizeof(effectText), "+%d Water Drops!", displayState.chanceCardEffect);
    tft.setTextColor(COLOR_SUCCESS);
  } else if (displayState.chanceCardEffect < 0) {
    snprintf(effectText, sizeof(effectText), "%d Water Drops", displayState.chanceCardEffect);
    tft.setTextColor(COLOR_ERROR);
  } else {
    snprintf(effectText, sizeof(effectText), "Special Effect!");
    tft.setTextColor(COLOR_ACCENT);
  }
  
  tft.setTextSize(FONT_SIZE_LARGE);
  int effectTextW = strlen(effectText) * 6 * FONT_SIZE_LARGE;
  tft.setCursor(cardX + (cardW - effectTextW) / 2, effectBoxY + (effectBoxH - 8 * FONT_SIZE_LARGE) / 2);
  tft.print(effectText);
  
  // Water drop icons based on effect
  if (displayState.chanceCardEffect != 0) {
    int dropCount = abs(displayState.chanceCardEffect);
    int dropSpacing = 30;
    int dropsWidth = dropCount * dropSpacing;
    int dropStartX = cardX + (cardW - dropsWidth) / 2;
    int dropY = cardY + cardH - 90;
    
    // Don't draw if we would overlap with text
    if (effectBoxY - dropY > 15) {
      for (int d = 0; d < dropCount && d < 5; d++) {
        int dx = dropStartX + d * dropSpacing + 15;
        uint16_t dropColor = displayState.chanceCardEffect > 0 ? COLOR_BLUE : COLOR_ERROR;
        // Simple water drop shape
        tft.fillCircle(dx, dropY, 8, dropColor);
        tft.fillTriangle(dx - 6, dropY, dx + 6, dropY, dx, dropY + 12, dropColor);
      }
    }
  }
  
  // Tap to continue hint
  tft.setTextColor(COLOR_BG_LIGHT);
  tft.setTextSize(FONT_SIZE_SMALL);
  const char* tapHint = "Tap to continue...";
  int hintW = strlen(tapHint) * 6 * FONT_SIZE_SMALL;
  tft.setCursor((TFT_WIDTH - hintW) / 2, TFT_HEIGHT - 25);
  tft.print(tapHint);
}

// Animated card reveal with flip effect
void drawChanceCardFlipFrame(int frame) {
  int totalFrames = CARD_FLIP_FRAMES;
  int cardW = 360;
  int cardH = 260;
  int cardX = (TFT_WIDTH - cardW) / 2;
  int cardY = (TFT_HEIGHT - cardH) / 2 - 20;
  
  // Calculate card width for flip animation (pseudo-3D)
  float progress = (float)frame / totalFrames;
  int currentW;
  
  if (progress < 0.5) {
    // Shrinking (showing back)
    currentW = cardW * (1.0 - progress * 2);
    
    tft.fillScreen(COLOR_BG_DARK);
    
    // Draw card back (water drop pattern)
    int cx = TFT_WIDTH / 2;
    tft.fillRoundRect(cx - currentW/2, cardY, currentW, cardH, 15, COLOR_BUTTON);
    tft.drawRoundRect(cx - currentW/2, cardY, currentW, cardH, 15, COLOR_ACCENT);
    
    // Question mark or pattern on back
    if (currentW > 40) {
      tft.setTextColor(COLOR_ACCENT);
      tft.setTextSize(5);
      tft.setCursor(cx - 15, cardY + cardH/2 - 20);
      tft.print("?");
    }
  } else {
    // Expanding (showing front)
    currentW = cardW * ((progress - 0.5) * 2);
    
    tft.fillScreen(COLOR_BG_DARK);
    
    // Draw partial front
    int cx = TFT_WIDTH / 2;
    uint16_t borderColor = displayState.chanceCardEffect > 0 ? COLOR_SUCCESS : 
                           displayState.chanceCardEffect < 0 ? COLOR_ERROR : COLOR_ACCENT;
    
    tft.fillRoundRect(cx - currentW/2, cardY, currentW, cardH, 15, COLOR_WHITE);
    tft.drawRoundRect(cx - currentW/2, cardY, currentW, cardH, 15, borderColor);
    
    // Show card number when wide enough
    if (currentW > 100) {
      tft.setTextColor(borderColor);
      tft.setTextSize(4);
      char numStr[4];
      snprintf(numStr, sizeof(numStr), "%d", displayState.chanceCardNumber);
      tft.setCursor(cx - 12, cardY + cardH/2 - 16);
      tft.print(numStr);
    }
  }
}

// Show a chance card (call this when landing on chance tile)
void showChanceCard(int cardIndex, ScreenID returnTo) {
  // Get card data from main CHANCE_CARDS array (defined in main sketch)
  extern const ChanceCard CHANCE_CARDS[20];
  
  displayState.chanceCardNumber = CHANCE_CARDS[cardIndex].number;
  displayState.chanceCardText = CHANCE_CARDS[cardIndex].description;
  displayState.chanceCardEffect = CHANCE_CARDS[cardIndex].effect;
  displayState.showingChanceCard = true;
  displayState.cardShowTime = millis();
  displayState.returnScreen = returnTo;
  displayState.cardFlipFrame = 0;
  
  Serial.printf("Showing Chance Card #%d: %s (Effect: %d)\n", 
    displayState.chanceCardNumber,
    displayState.chanceCardText,
    displayState.chanceCardEffect);
  
  // Play flip animation
  for (int frame = 0; frame <= CARD_FLIP_FRAMES; frame++) {
    drawChanceCardFlipFrame(frame);
    delay(40);  // ~25 FPS animation
  }
  
  // Draw final card
  changeScreen(SCREEN_CHANCE_CARD);
}

// Check if chance card should auto-dismiss
void updateChanceCard() {
  if (displayState.showingChanceCard && displayState.currentScreen == SCREEN_CHANCE_CARD) {
    // Auto-dismiss after CARD_DISPLAY_TIME
    if (millis() - displayState.cardShowTime > CARD_DISPLAY_TIME) {
      dismissChanceCard();
    }
  }
}

void dismissChanceCard() {
  displayState.showingChanceCard = false;
  changeScreen(displayState.returnScreen);
}

// Handle touch on chance card screen
void handleChanceCardTouch(int tx, int ty) {
  // Any touch dismisses the card
  dismissChanceCard();
}

// ==================== ANIMATION UPDATES ====================

void updateAnimations() {
  unsigned long now = millis();
  if (now - lastFrameTime < FRAME_DELAY_MS) return;
  lastFrameTime = now;
  
  // Cloudie floating animation
  if (displayState.cloudieUp) {
    displayState.cloudieY -= CLOUDIE_FLOAT_SPEED;
    if (displayState.cloudieY < 150) displayState.cloudieUp = false;
  } else {
    displayState.cloudieY += CLOUDIE_FLOAT_SPEED;
    if (displayState.cloudieY > 200) displayState.cloudieUp = true;
  }
  
  // Dice zoom animation
  if (displayState.diceAnimating) {
    diceZoomFrame++;
    if (diceZoomFrame >= DICE_ZOOM_FRAMES) {
      displayState.diceAnimating = false;
      diceZoomFrame = 0;
    }
  }
}

// ==================== SCREEN NAVIGATION ====================

void changeScreen(ScreenID newScreen) {
  displayState.currentScreen = newScreen;
  
  switch (newScreen) {
    case SCREEN_LOGO:
      drawLogoScreen();
      break;
    case SCREEN_GAME_SELECT:
      drawGameSelectScreen();
      break;
    case SCREEN_PLAYER_SELECT:
      drawPlayerSelectScreen();
      break;
    case SCREEN_PROFILE_CREATE:
      drawProfileCreateScreen();
      break;
    case SCREEN_COLOR_SELECT:
      drawColorSelectScreen();
      break;
    case SCREEN_DICE_SELECT:
      drawDiceSelectScreen();
      break;
    case SCREEN_DICE_CONNECT:
      drawDiceConnectScreen("Scanning for dice...", false);
      break;
    case SCREEN_GAMEPLAY:
      drawGameplayScreen();
      break;
    case SCREEN_CHANCE_CARD:
      drawChanceCardScreen();
      break;
    case SCREEN_GAME_OVER:
      drawGameOverScreen(0);  // Placeholder
      break;
  }
}

// ==================== TOUCH EVENT HANDLER ====================

void handleTouch(int tx, int ty) {
  unsigned long now = millis();
  if (now - lastTouchTime < TOUCH_DEBOUNCE) return;
  lastTouchTime = now;
  
  Serial.printf("Touch: %d, %d (Screen: %d)\n", tx, ty, displayState.currentScreen);
  
  switch (displayState.currentScreen) {
    case SCREEN_LOGO:
      // Any touch goes to game select
      changeScreen(SCREEN_GAME_SELECT);
      break;
      
    case SCREEN_GAME_SELECT:
      // Check if Last Drop Earth card touched
      if (isTouchInRect(tx, ty, (TFT_WIDTH - 300) / 2, (TFT_HEIGHT - 100) / 2, 300, 100)) {
        changeScreen(SCREEN_PLAYER_SELECT);
      }
      break;
      
    case SCREEN_PLAYER_SELECT:
      handlePlayerSelectTouch(tx, ty);
      break;
      
    case SCREEN_PROFILE_CREATE:
      handleProfileCreateTouch(tx, ty);
      break;
      
    case SCREEN_COLOR_SELECT:
      handleColorSelectTouch(tx, ty);
      break;
      
    case SCREEN_DICE_SELECT:
      handleDiceSelectTouch(tx, ty);
      break;
      
    case SCREEN_GAMEPLAY:
      handleGameplayTouch(tx, ty);
      break;
      
    case SCREEN_CHANCE_CARD:
      handleChanceCardTouch(tx, ty);
      break;
      
    case SCREEN_GAME_OVER:
      // Play again button
      if (isTouchInRect(tx, ty, (TFT_WIDTH - 200) / 2, TFT_HEIGHT - 80, 200, 50)) {
        changeScreen(SCREEN_GAME_SELECT);
      }
      break;
      
    default:
      break;
  }
}

void handlePlayerSelectTouch(int tx, int ty) {
  // Check profile icons
  int startX = 30;
  int startY = 90;
  int iconSpacing = 100;
  
  for (int i = 0; i < profileCount && i < MAX_PROFILES; i++) {
    int col = i % 4;
    int row = i / 4;
    int x = startX + col * iconSpacing;
    int y = startY + row * (ICON_SIZE + 40);
    
    if (isTouchInRect(tx, ty, x, y, ICON_SIZE, ICON_SIZE)) {
      // Toggle selection
      bool wasSelected = false;
      int selectedIdx = -1;
      
      for (int j = 0; j < displayState.selectedPlayers; j++) {
        if (displayState.selectedProfiles[j] == i) {
          wasSelected = true;
          selectedIdx = j;
          break;
        }
      }
      
      if (wasSelected) {
        // Deselect
        for (int j = selectedIdx; j < displayState.selectedPlayers - 1; j++) {
          displayState.selectedProfiles[j] = displayState.selectedProfiles[j + 1];
        }
        displayState.selectedPlayers--;
      } else if (displayState.selectedPlayers < MAX_PLAYERS) {
        // Select
        displayState.selectedProfiles[displayState.selectedPlayers] = i;
        displayState.selectedPlayers++;
      }
      
      drawPlayerSelectScreen();
      return;
    }
  }
  
  // Create profile button
  if (profileCount < MAX_PROFILES) {
    int col = profileCount % 4;
    int row = profileCount / 4;
    int x = startX + col * iconSpacing;
    int y = startY + row * (ICON_SIZE + 40);
    
    if (isTouchInRect(tx, ty, x, y, ICON_SIZE, ICON_SIZE)) {
      changeScreen(SCREEN_PROFILE_CREATE);
      return;
    }
  }
  
  // Next button
  if (displayState.selectedPlayers >= 2 && displayState.selectedPlayers <= 4) {
    if (isTouchInRect(tx, ty, TFT_WIDTH - 150, TFT_HEIGHT - 70, 120, 50)) {
      // Initialize player colors
      for (int p = 0; p < displayState.selectedPlayers; p++) {
        displayState.playerColors[p] = 0;  // Not selected yet
      }
      changeScreen(SCREEN_COLOR_SELECT);
    }
  }
}

void handleProfileCreateTouch(int tx, int ty) {
  const char* presetNames[] = {"Player 1", "Player 2", "Player 3", "Player 4", "Hero", "Star"};
  const uint16_t presetColors[] = {COLOR_RED, COLOR_GREEN, COLOR_BLUE, COLOR_YELLOW, COLOR_ORANGE, COLOR_PINK};
  
  int startX = 40;
  int startY = 110;
  int btnW = 130;
  int btnH = 50;
  int margin = 15;
  
  // Check preset name buttons
  for (int i = 0; i < 6; i++) {
    int col = i % 3;
    int row = i / 3;
    int x = startX + col * (btnW + margin);
    int y = startY + row * (btnH + margin);
    
    if (isTouchInRect(tx, ty, x, y, btnW, btnH)) {
      // Create new profile
      strcpy(profiles[profileCount].nickname, presetNames[i]);
      profiles[profileCount].isAI = false;
      profiles[profileCount].isGuest = false;
      profiles[profileCount].avatarColor = presetColors[i];
      profiles[profileCount].gamesPlayed = 0;
      profiles[profileCount].gamesWon = 0;
      profileCount++;
      
      changeScreen(SCREEN_PLAYER_SELECT);
      return;
    }
  }
  
  // Cancel button
  if (isTouchInRect(tx, ty, 30, TFT_HEIGHT - 70, 100, 50)) {
    changeScreen(SCREEN_PLAYER_SELECT);
  }
}

void handleColorSelectTouch(int tx, int ty) {
  const uint16_t colors[] = {COLOR_PLAYER_RED, COLOR_PLAYER_GREEN, COLOR_PLAYER_BLUE, COLOR_PLAYER_YELLOW};
  
  int startY = 80;
  int rowHeight = 60;
  int colorX = 180;
  
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    int y = startY + p * rowHeight;
    
    for (int c = 0; c < 4; c++) {
      int btnX = colorX + c * 70;
      
      if (isTouchInRect(tx, ty, btnX, y, 50, 40)) {
        // Check if color is taken
        bool taken = false;
        for (int other = 0; other < displayState.selectedPlayers; other++) {
          if (other != p && displayState.playerColors[other] == colors[c]) {
            taken = true;
            break;
          }
        }
        
        if (!taken) {
          displayState.playerColors[p] = colors[c];
          drawColorSelectScreen();
        }
        return;
      }
    }
  }
  
  // Next button
  bool allSelected = true;
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    if (displayState.playerColors[p] == 0) {
      allSelected = false;
      break;
    }
  }
  
  if (allSelected && isTouchInRect(tx, ty, TFT_WIDTH - 150, TFT_HEIGHT - 70, 120, 50)) {
    changeScreen(SCREEN_DICE_SELECT);
  }
  
  // Back button
  if (isTouchInRect(tx, ty, 30, TFT_HEIGHT - 70, 100, 50)) {
    changeScreen(SCREEN_PLAYER_SELECT);
  }
}

void handleDiceSelectTouch(int tx, int ty) {
  int centerY = TFT_HEIGHT / 2 - 20;
  
  // Smart Dice button
  if (isTouchInRect(tx, ty, 50, centerY - 50, 170, 120)) {
    displayState.useSmartDice = true;
    drawDiceSelectScreen();
    return;
  }
  
  // Virtual Dice button
  if (isTouchInRect(tx, ty, 260, centerY - 50, 170, 120)) {
    displayState.useSmartDice = false;
    drawDiceSelectScreen();
    return;
  }
  
  // Dice count buttons (if smart dice)
  if (displayState.useSmartDice) {
    if (isTouchInRect(tx, ty, 200, centerY + 80, 60, 40)) {
      displayState.diceCount = 1;
      drawDiceSelectScreen();
      return;
    }
    if (isTouchInRect(tx, ty, 280, centerY + 80, 60, 40)) {
      displayState.diceCount = 2;
      drawDiceSelectScreen();
      return;
    }
  }
  
  // Start Game button
  if (isTouchInRect(tx, ty, (TFT_WIDTH - 200) / 2, TFT_HEIGHT - 70, 200, 50)) {
    if (displayState.useSmartDice) {
      // Go to dice connection screen
      changeScreen(SCREEN_DICE_CONNECT);
      // Trigger GoDice scan (handled by main loop)
    } else {
      // Virtual dice - go directly to gameplay
      changeScreen(SCREEN_GAMEPLAY);
    }
    return;
  }
  
  // Back button
  if (isTouchInRect(tx, ty, 30, TFT_HEIGHT - 70, 100, 50)) {
    changeScreen(SCREEN_COLOR_SELECT);
  }
}

void handleGameplayTouch(int tx, int ty) {
  int btnY = TFT_HEIGHT - 60;
  
  // Undo button
  if (isTouchInRect(tx, ty, 20, btnY, 100, 45)) {
    Serial.println("UNDO pressed");
    // TODO: Link to game undo function
    return;
  }
  
  // End Game button
  if (isTouchInRect(tx, ty, TFT_WIDTH - 120, btnY, 100, 45)) {
    Serial.println("END GAME pressed");
    changeScreen(SCREEN_GAME_SELECT);
    return;
  }
  
  // Virtual dice tap (if using virtual dice)
  if (!displayState.useSmartDice) {
    int cx = TFT_WIDTH / 2;
    int cy = TFT_HEIGHT / 2;
    if (isTouchInRect(tx, ty, cx - 60, cy - 60, 120, 120)) {
      // Roll virtual dice
      rollVirtualDice();
    }
  }
}

void rollVirtualDice() {
  Serial.println("Rolling virtual dice...");
  
  // Animate rolling
  for (int i = 0; i < 15; i++) {
    int tempVal = random(1, 7);
    drawVirtualDice(tempVal, true);
    delay(50 + i * 10);  // Slowing down
  }
  
  // Final value
  displayState.lastDiceValue = random(1, 7);
  if (displayState.diceCount == 2) {
    displayState.lastDiceValue2 = random(1, 7);
  }
  
  drawVirtualDice(displayState.lastDiceValue, false);
  
  Serial.printf("Virtual dice result: %d\n", displayState.lastDiceValue);
  
  // TODO: Process dice roll in game logic
}

// ==================== MAIN DISPLAY UPDATE ====================

void updateDisplay() {
  // Check for touch
  TouchPoint tp = getTouchPoint();
  if (tp.pressed) {
    handleTouch(tp.x, tp.y);
  }
  
  // Update animations
  updateAnimations();
  
  // Check for chance card auto-dismiss
  updateChanceCard();
  
  // Redraw gameplay screen if active (for animations)
  if (displayState.currentScreen == SCREEN_GAMEPLAY) {
    static unsigned long lastRedraw = 0;
    if (millis() - lastRedraw > 100) {  // 10 FPS for gameplay updates
      drawGameplayScreen();
      lastRedraw = millis();
    }
  }
}

// ==================== DICE RESULT DISPLAY ====================

void showDiceResult(int value, int value2) {
  displayState.lastDiceValue = value;
  displayState.lastDiceValue2 = value2;
  displayState.diceAnimating = true;
  diceZoomFrame = 0;
  
  if (displayState.currentScreen == SCREEN_GAMEPLAY) {
    // Will be drawn by updateDisplay
  }
}

// ==================== HELPER TO SEND COLORS TO LEDs ====================

void sendColorsToLEDs() {
  // Convert display colors (RGB565) to LED colors (RGB888)
  // This will be called after color selection to update the main game
  Serial.println("Sending player colors to LED board:");
  
  for (int p = 0; p < displayState.selectedPlayers; p++) {
    uint16_t c565 = displayState.playerColors[p];
    
    // Convert RGB565 to RGB888
    uint8_t r = ((c565 >> 11) & 0x1F) << 3;
    uint8_t g = ((c565 >> 5) & 0x3F) << 2;
    uint8_t b = (c565 & 0x1F) << 3;
    
    uint32_t rgb888 = (r << 16) | (g << 8) | b;
    
    Serial.printf("  Player %d: 0x%06X\n", p, rgb888);
    
    // Update main game player colors
    // PLAYER_COLORS[p] = rgb888;  // Link to main sketch
  }
}

#endif // DISPLAY_MANAGER_H
