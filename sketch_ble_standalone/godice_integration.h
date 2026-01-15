/*
 * GoDice Integration for Last Drop Standalone Mode
 * 
 * This file contains all GoDice BLE client functions for standalone operation.
 * Include this file AFTER all global variables are declared in main sketch.
 */

#ifndef GODICE_INTEGRATION_H
#define GODICE_INTEGRATION_H

// Standalone mode turn tracking
int standaloneCurrentPlayer = 0;  // Track whose turn it is (0 to activePlayerCount-1)

// ==================== Helper Functions ====================

const char* getDiceColorName(uint8_t colorCode) {
    switch (colorCode) {
        case 0: return "Black";
        case 1: return "Red";
        case 2: return "Green";
        case 3: return "Blue";
        case 4: return "Yellow";
        case 5: return "Orange";
        default: return "Unknown";
    }
}

// Convert accelerometer XYZ to dice face (1-6)
int goDiceXyzToFace(int8_t x, int8_t y, int8_t z) {
    int bestFace = 1;
    int bestDistance = 999999;
    
    for (int face = 0; face < 6; face++) {
        int dx = x - GODICE_D6_VECTORS[face][0];
        int dy = y - GODICE_D6_VECTORS[face][1];
        int dz = z - GODICE_D6_VECTORS[face][2];
        int distance = dx*dx + dy*dy + dz*dz;
        
        if (distance < bestDistance) {
            bestDistance = distance;
            bestFace = face + 1;
        }
    }
    
    return bestFace;
}

// Send command to GoDice
void sendGoDiceCommand(uint8_t cmd) {
    if (pGoDiceTxChar && goDiceConnected) {
        pGoDiceTxChar->writeValue(&cmd, 1);
    }
}

void sendGoDiceCommandBytes(uint8_t* data, size_t len) {
    if (pGoDiceTxChar && goDiceConnected) {
        pGoDiceTxChar->writeValue(data, len);
    }
}

// Set GoDice LED color
void setGoDiceLED(uint8_t r, uint8_t g, uint8_t b) {
    uint8_t cmd[] = {GODICE_CMD_SET_LED, r, g, b};
    sendGoDiceCommandBytes(cmd, 4);
}

// Pulse GoDice LED
void pulseGoDiceLED(uint8_t r, uint8_t g, uint8_t b, uint8_t count, uint8_t onTime, uint8_t offTime) {
    uint8_t cmd[] = {GODICE_CMD_PULSE_LED, count, onTime, offTime, r, g, b};
    sendGoDiceCommandBytes(cmd, 7);
}

// ==================== GoDice Notification Callback ====================

static void goDiceNotifyCallback(BLERemoteCharacteristic* pChar, uint8_t* pData, size_t length, bool isNotify) {
    if (length < 1) return;
    
    uint8_t msgType = pData[0];
    
    // Debug logging
    Serial.print("📥 GoDice [");
    for (size_t i = 0; i < length; i++) {
        if (pData[i] < 0x10) Serial.print("0");
        Serial.print(pData[i], HEX);
        if (i < length - 1) Serial.print(" ");
    }
    Serial.print("] ");
    
    switch (msgType) {
        case GODICE_MSG_ROLLING:
            isDiceRolling = true;
            lastRollTime = millis();
            Serial.println("🎲 ROLLING...");
            break;
            
        case GODICE_MSG_STABLE: {  // 0x53 'S' - format: [S][x][y][z]
            if (length >= 4) {
                isDiceRolling = false;
                rollCount++;
                
                int8_t x = (int8_t)pData[1];
                int8_t y = (int8_t)pData[2];
                int8_t z = (int8_t)pData[3];
                
                lastDiceValue = goDiceXyzToFace(x, y, z);
                Serial.printf("✅ STABLE: %d (xyz: %d,%d,%d)\n", lastDiceValue, x, y, z);
                
                // Process dice roll in game logic
                processDiceRoll(lastDiceValue);
            }
            break;
        }
            
        case GODICE_MSG_FAKE_STABLE:  // 0x46 'F' - format: [F][S][x][y][z]
        case GODICE_MSG_TILT_STABLE:  // 0x54 'T' - format: [T][S][x][y][z]
        case GODICE_MSG_MOVE_STABLE: { // 0x4D 'M' - format: [M][S][x][y][z]
            if (length >= 5) {
                isDiceRolling = false;
                rollCount++;
                
                int8_t x = (int8_t)pData[2];  // XYZ at offset 2,3,4
                int8_t y = (int8_t)pData[3];
                int8_t z = (int8_t)pData[4];
                
                lastDiceValue = goDiceXyzToFace(x, y, z);
                Serial.printf("✅ STABLE: %d (xyz: %d,%d,%d)\n", lastDiceValue, x, y, z);
                
                // Process dice roll in game logic
                processDiceRoll(lastDiceValue);
            }
            break;
        }
            
        case GODICE_MSG_BATTERY:
            if (length >= 4 && pData[1] == 'a' && pData[2] == 't') {
                // "Bat" + level
                diceBattery = pData[3];
                Serial.printf("🔋 Battery: %d%%\n", diceBattery);
            } else if (length >= 2) {
                diceBattery = pData[1];
                Serial.printf("🔋 Battery: %d%%\n", diceBattery);
            }
            break;
            
        case GODICE_MSG_COLOR:
            if (length >= 4 && pData[1] == 'o' && pData[2] == 'l') {
                // "Col" + color code
                uint8_t colorCode = pData[3];
                diceColorName = getDiceColorName(colorCode);
                Serial.printf("🎨 Dice Color: %s (code=%d)\n", diceColorName.c_str(), colorCode);
            } else if (length >= 2) {
                uint8_t colorCode = pData[1];
                diceColorName = getDiceColorName(colorCode);
                Serial.printf("🎨 Dice Color: %s (code=%d)\n", diceColorName.c_str(), colorCode);
            }
            break;
            
        default:
            Serial.printf("❓ Unknown: 0x%02X\n", msgType);
            break;
    }
}

// ==================== GoDice Client Callbacks ====================

class GoDiceClientCallbacks : public BLEClientCallbacks {
    void onConnect(BLEClient* pclient) override {
        Serial.println("✅ GoDice onConnect callback");
        goDiceConnected = true;
        
        // Connection success - green animation on dice itself
        pulseGoDiceLED(0, 255, 0, 3, 15, 10);  // 3 green pulses
    }

    void onDisconnect(BLEClient* pclient) override {
        Serial.println("❌ GoDice onDisconnect callback");
        goDiceConnected = false;
        isDiceRolling = false;
        lastDiceValue = 0;
        
        // Disconnected - return to MODE_DISCONNECTED (blue corner LEDs)
        currentConnectionMode = MODE_DISCONNECTED;
    }
};

// ==================== GoDice Scan Callbacks ====================

bool goDiceFoundFlag = false;
BLEAddress* goDiceFoundAddress = nullptr;
uint8_t goDiceFoundType = 0;

class GoDiceScanCallbacks : public BLEAdvertisedDeviceCallbacks {
    void onResult(BLEAdvertisedDevice advertisedDevice) override {
        String name = advertisedDevice.getName().c_str();
        
        if (name.startsWith("GoDice_") && !goDiceFoundFlag) {
            Serial.println("\n========================================");
            Serial.printf("🎲 GODICE FOUND: %s\n", name.c_str());
            Serial.printf("   Address: %s\n", advertisedDevice.getAddress().toString().c_str());
            Serial.printf("   Type: %d (0=PUBLIC, 1=RANDOM)\n", advertisedDevice.getAddressType());
            Serial.printf("   RSSI: %d dBm\n", advertisedDevice.getRSSI());
            Serial.println("========================================\n");
            
            goDiceFoundFlag = true;
            goDiceName = name;
            goDiceFoundAddress = new BLEAddress(advertisedDevice.getAddress());
            goDiceFoundType = advertisedDevice.getAddressType();
            
            pGoDiceScan->stop();
        }
    }
};

// ==================== GoDice Connection ====================

// Simple dice roll processor for standalone mode
void processDiceRoll(int diceValue) {
  Serial.printf("\n🎲 Processing dice roll: %d\n", diceValue);
  
  // Update display with dice result
  showDiceResult(diceValue);
  
  // Get current player for this turn
  int playerId = standaloneCurrentPlayer;
  
  // Check if player is alive
  if (!players[playerId].alive) {
    // Skip to next alive player
    for (int i = 0; i < activePlayerCount; i++) {
      standaloneCurrentPlayer = (standaloneCurrentPlayer + 1) % activePlayerCount;
      if (players[standaloneCurrentPlayer].alive) {
        playerId = standaloneCurrentPlayer;
        break;
      }
    }
  }
  
  Serial.printf("  Current player: %d (%s)\n", playerId, 
                profiles[displayState.selectedProfiles[playerId]].nickname);
  
  // Store previous state for undo
  players[playerId].previousTile = players[playerId].currentTile;
  players[playerId].previousScore = players[playerId].score;
  
  int currentTile = players[playerId].currentTile;
  int newTile = currentTile + diceValue;
  
  // Lap wrapping
  if (newTile > NUM_TILES) {
    newTile = newTile - NUM_TILES;
    Serial.println("  >> LAP COMPLETED!");
  }
  if (newTile < 1) newTile = 1;
  
  Serial.printf("  Movement: Tile %d → Tile %d\n", currentTile, newTile);
  
  // Get tile information and calculate score
  const TileDefinition& tile = BOARD[newTile - 1];
  int scoreChange = 0;
  
  // Variables for chance card
  int chanceCardIndex = -1;
  
  switch (tile.type) {
    case TYPE_BONUS:
      scoreChange = +1;
      break;
    case TYPE_PENALTY:
      scoreChange = -1;
      break;
    case TYPE_DISASTER:
      scoreChange = -3;
      break;
    case TYPE_WATER_DOCK:
      scoreChange = +3;
      break;
    case TYPE_SUPER_DOCK:
      scoreChange = +4;
      break;
    case TYPE_CHANCE:
      // Select a random chance card (0-19)
      chanceCardIndex = random(0, 20);
      scoreChange = CHANCE_CARDS[chanceCardIndex].effect;
      Serial.printf("  🎴 Chance Card #%d: %s (Effect: %+d)\n", 
        CHANCE_CARDS[chanceCardIndex].number,
        CHANCE_CARDS[chanceCardIndex].description,
        scoreChange);
      break;
    default:
      scoreChange = 0;
      break;
  }
  
  // Apply score change
  int oldScore = players[playerId].score;
  int newScore = oldScore + scoreChange;
  if (newScore < 0) newScore = 0;
  
  players[playerId].score = newScore;
  players[playerId].currentTile = newTile;
  
  Serial.printf("  Score: %d → %d (%+d)\n", oldScore, newScore, scoreChange);
  Serial.printf("  Tile: %s\n", tile.name);
  
  // Show chance card on display if one was drawn
  if (chanceCardIndex >= 0) {
    showChanceCard(chanceCardIndex, SCREEN_GAMEPLAY);
  }
  
  // Check elimination
  if (newScore <= 0 && players[playerId].alive) {
    players[playerId].alive = false;
    Serial.println("  ⚠️ PLAYER ELIMINATED!");
    animatePlayerElimination(playerId);
  }
  
  // Animate LED movement
  animateMove(currentTile, newTile, players[playerId].color, playerId);
  
  // Start coin wait
  waitingForCoin = true;
  expectedTile = newTile;
  currentPlayer = playerId;
  coinWaitStartTime = millis();
  
  // Move to next player after coin placement
  standaloneCurrentPlayer = (standaloneCurrentPlayer + 1) % activePlayerCount;
  
  // Check for winner (only one player left)
  int alivePlayers = 0;
  int winnerId = -1;
  for (int i = 0; i < activePlayerCount; i++) {
    if (players[i].alive) {
      alivePlayers++;
      winnerId = i;
    }
  }
  
  if (alivePlayers == 1 && activePlayerCount > 1) {
    Serial.printf("\n🏆 WINNER: Player %d!\n", winnerId);
    animateWinner(winnerId);
    changeScreen(SCREEN_GAME_OVER);
  }
  
  Serial.println("✅ Dice roll processed\n");
}

// ==================== GoDice Connection ====================

bool connectToGoDice() {
    if (goDiceFoundAddress == nullptr) {
        Serial.println("❌ No GoDice address");
        return false;
    }
    
    Serial.println("\n========================================");
    Serial.printf("🔗 Connecting to: %s\n", goDiceName.c_str());
    Serial.printf("   Address: %s\n", goDiceFoundAddress->toString().c_str());
    Serial.printf("   Type: %d\n", goDiceFoundType);
    Serial.println("========================================\n");
    
    pGoDiceClient = BLEDevice::createClient();
    pGoDiceClient->setClientCallbacks(new GoDiceClientCallbacks());
    Serial.println("✓ Created BLE client");
    
    Serial.println("⏳ Connecting (this may take 10-15 seconds)...");
    bool success = pGoDiceClient->connect(*goDiceFoundAddress, goDiceFoundType);
    
    if (!success) {
        Serial.println("❌ Connection failed - dice may be asleep");
        Serial.println("💡 Try rolling the dice to wake it");
        return false;
    }
    
    Serial.println("✅ Connected!");
    
    // Get Nordic UART service
    BLERemoteService* pService = pGoDiceClient->getService(GODICE_SERVICE_UUID);
    if (pService == nullptr) {
        Serial.println("❌ Nordic UART service not found");
        pGoDiceClient->disconnect();
        return false;
    }
    Serial.println("✅ Found service");
    
    // Get TX characteristic (write to dice)
    pGoDiceTxChar = pService->getCharacteristic(GODICE_TX_CHAR_UUID);
    if (pGoDiceTxChar == nullptr) {
        Serial.println("❌ TX characteristic not found");
        pGoDiceClient->disconnect();
        return false;
    }
    Serial.println("✅ Found TX characteristic");
    
    // Get RX characteristic (receive from dice)
    pGoDiceRxChar = pService->getCharacteristic(GODICE_RX_CHAR_UUID);
    if (pGoDiceRxChar == nullptr) {
        Serial.println("❌ RX characteristic not found");
        pGoDiceClient->disconnect();
        return false;
    }
    Serial.println("✅ Found RX characteristic");
    
    // Subscribe to notifications
    if (pGoDiceRxChar->canNotify()) {
        pGoDiceRxChar->registerForNotify(goDiceNotifyCallback);
        Serial.println("✅ Subscribed to notifications");
    }
    
    // Show connection success animation on board
    pulseGoDiceLED(0, 255, 0, 3, 15, 10);  // 3 green pulses on dice
    
    Serial.println("\n🎉 ====== GODICE CONNECTION COMPLETE ======");
    Serial.println("Roll the dice to play!");
    Serial.println("===========================================\n");
    
    // Request initial info
    delay(500);
    sendGoDiceCommand(GODICE_CMD_GET_COLOR);
    delay(200);
    sendGoDiceCommand(GODICE_CMD_BATTERY);
    
    return true;
}

// ==================== GoDice Scan ====================

void startGoDiceScan() {
    Serial.println("\n🔍 Starting BLE scan for GoDice...");
    Serial.println("   Roll your dice to wake it up!");
    Serial.println("   Scanning for 30 seconds...\n");
    
    goDiceScanning = true;
    goDiceFoundFlag = false;
    
    if (goDiceFoundAddress != nullptr) {
        delete goDiceFoundAddress;
        goDiceFoundAddress = nullptr;
    }
    
    pGoDiceScan->setActiveScan(true);
    pGoDiceScan->setInterval(100);
    pGoDiceScan->setWindow(99);
    
    pGoDiceScan->start(30, false);  // 30 second scan, non-blocking
}

// ==================== GoDice Loop Handler ====================

void handleGoDiceConnection() {
    // Handle connection after scan completes
    if (goDiceFoundFlag && goDiceFoundAddress != nullptr && !goDiceConnected) {
        goDiceFoundFlag = false;
        goDiceScanning = false;
        
        delay(1000);  // Wait for scan to fully stop
        
        if (connectToGoDice()) {
            // Connection successful
            currentConnectionMode = MODE_READY;
            Serial.println("✅ GoDice connected - mode set to READY");
        } else {
            // Connection failed - keep waiting
            currentConnectionMode = MODE_DISCONNECTED;
            Serial.println("❌ Connection failed - will retry");
            
            // Retry scan after 5 seconds
            delay(5000);
            startGoDiceScan();
        }
    }
}

#endif // GODICE_INTEGRATION_H
