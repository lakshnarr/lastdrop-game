# LED Animation System - Last Drop ESP32

## Overview

This document describes the LED animation system for player elimination and winner celebration events on the ESP32 board.

## Hardware Architecture

- **Total LEDs**: 80 WS2812B addressable LEDs
- **Board Structure**: 20 tiles × 4 LEDs per tile
- **Player LED Allocation**: Each player "owns" one LED slot (0-3) in every tile's 4-LED group
  - Player 0 (Red): LED index = tile × 4 + 0
  - Player 1 (Blue): LED index = tile × 4 + 1
  - Player 2 (Green): LED index = tile × 4 + 2
  - Player 3 (Yellow): LED index = tile × 4 + 3

This architecture allows all players to be displayed simultaneously across the entire 20-tile board.

## Elimination Animation

### Trigger Condition
When a player's water drops reach **0 or below**, the elimination animation executes automatically.

### Animation Sequence
1. **3× Blink Pattern**:
   - Turn ON the player's LED across all 20 tiles (their color)
   - Hold for 300ms
   - Turn OFF all player LEDs
   - Hold for 300ms
   - Repeat 3 times total

2. **Final State**: All LEDs for eliminated player remain **OFF permanently**

### Code Location
`animatePlayerElimination(int playerId)` - Lines ~970-1000 in sketch_ble_testmode.ino

### Example Serial Output
```
💀 Animating elimination for Player 2...
  ⚠️  PLAYER ELIMINATED!
✓ Elimination animation complete - player LEDs turned off
```

### Integration Point
Called from `handleRoll()` at line ~530 after score update:
```cpp
if (newScore <= 0 && players[playerId].alive) {
  players[playerId].alive = false;
  Serial.println("  ⚠️  PLAYER ELIMINATED!");
  animatePlayerElimination(playerId);
  
  // Check for winner...
}
```

## Winner Animation

### Trigger Condition
When **only 1 player remains alive** (all others eliminated), the winner celebration executes.

### Animation Sequence

#### Phase 1: Flash (3 cycles)
- Turn all 80 LEDs to winner's color
- Hold for 400ms
- Turn all LEDs OFF
- Hold for 200ms
- Repeat 3 times

#### Phase 2: Disco Strobe (20 cycles)
- Randomly alternate each LED between winner color and white
- 100ms per cycle
- Creates chaotic celebration effect

#### Phase 3: Chase Pattern (3 full sweeps)
- Single LED "chases" across all 80 LEDs in winner color
- 20ms delay between LEDs
- Leaves trail of OFF LEDs behind
- 3 complete board sweeps

#### Phase 4: Pulsing Fade (5 repetitions)
- Fade winner color from 0 → 255 brightness (10ms steps)
- Fade from 255 → 0 brightness (10ms steps)
- Smooth breathing effect
- 5 complete pulse cycles

#### Final State
- All 80 LEDs solid winner color for **3 seconds**
- Return to normal background (blue with tile types)

### Code Location
`animateWinner(int winnerId)` - Lines ~1002-1110 in sketch_ble_testmode.ino

### Example Serial Output
```
🏆 WINNER ANIMATION for Player 0!
🎉 GAME OVER - Player 0 WINS!
✓ Winner celebration complete!
```

### Integration Point
Called from `handleRoll()` immediately after elimination animation, if winner detected:
```cpp
int alivePlayers = 0;
int lastAliveId = -1;
for (int i = 0; i < activePlayerCount; i++) {
  if (players[i].alive) {
    alivePlayers++;
    lastAliveId = i;
  }
}

if (alivePlayers == 1) {
  Serial.printf("🎉 GAME OVER - Player %d WINS!\n", lastAliveId);
  animateWinner(lastAliveId);
}
```

## Technical Details

### Color Extraction from uint32_t
Both animations use bit-shifting to extract RGB components for fading effects:
```cpp
uint8_t red   = (winnerColor >> 16) & 0xFF;
uint8_t green = (winnerColor >> 8) & 0xFF;
uint8_t blue  = winnerColor & 0xFF;
```

### Brightness Scaling
Winner animation includes brightness control for pulsing:
```cpp
uint32_t fadeColor = strip.Color(
  (uint8_t)((winnerColor >> 16) * brightness / 255),
  (uint8_t)(((winnerColor >> 8) & 0xFF) * brightness / 255),
  (uint8_t)((winnerColor & 0xFF) * brightness / 255)
);
```

### Performance Impact
- Elimination animation: ~1.8 seconds total (300ms × 6 states)
- Winner animation: ~20-25 seconds total (all phases combined)
- Both animations **block** game logic until complete (intentional for dramatic effect)
- Normal LED updates (renderBackground, renderPlayers) resume after animations

## Testing Procedure

### Test Elimination Animation
1. Enable Test Mode 1 in Android app
2. Configure 3+ players
3. Tap "Simulate Dice Roll" to move player to DISASTER tiles
4. Repeat until player's score reaches 0
5. Observe 3× red blink across board
6. Verify player LEDs remain OFF afterward

### Test Winner Animation
1. Enable Test Mode 1
2. Configure exactly 2 players
3. Eliminate one player (score → 0)
4. Observe full winner celebration sequence:
   - 3× flash
   - Disco strobe
   - Chase pattern
   - Pulsing fade
   - 3-second solid color hold

### Serial Monitor Validation
Watch for these messages:
```
  ⚠️  PLAYER ELIMINATED!
💀 Animating elimination for Player X...
✓ Elimination animation complete - player LEDs turned off

🎉 GAME OVER - Player Y WINS!
🏆 WINNER ANIMATION for Player Y!
✓ Winner celebration complete!
```

## Edge Cases

### Multiple Simultaneous Eliminations
If a chance card eliminates multiple players in one turn, only the current player's elimination animation triggers. Winner detection still works correctly.

### 2-Player Game Instant Winner
When 2 players start and one is eliminated immediately:
- Elimination animation plays first
- Winner animation follows immediately
- Total animation time: ~22-27 seconds

### Animation Interruption
Animations are NOT interruptible. During execution:
- BLE commands still queue but don't execute
- Hall sensor scanning paused
- Coin placement detection delayed
- Consider timeout handling in Android app for long animations

## Future Enhancements

Potential improvements (not implemented):
- Make animation speed configurable (faster for testing)
- Allow skip button via BLE command
- Add sound effects via piezo buzzer
- Partial elimination animations (score low but not 0)
- Multi-winner tie scenarios
- Custom animation patterns per player color

## Related Documentation

- `BLE_PROTOCOL.md` - Android ↔ ESP32 communication
- `TEST_MODE_GUIDE.md` - Testing without physical dice
- `IMPLEMENTATION_GUIDE.md` - Hardware setup and wiring
- `RULEBOOK.md` - Game rules and tile effects
