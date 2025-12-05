# Cloudie AI Player Feature

## Overview
**Cloudie** is a built-in AI player that can participate in games alongside human players.

## Features Implemented

### 1. Auto-Creation
- ✅ AI profile "Cloudie" is **automatically created** when ProfileSelectionActivity loads
- ✅ Uses fixed player code `AI0001` (never changes)
- ✅ Cyan/Sky blue color (`#00D4FF`) for cloud theme
- ✅ Cloud emoji ☁️ displayed with name

### 2. Selection System
- ✅ Appears in profile grid like regular players
- ✅ Can be selected/deselected by tapping
- ✅ Shows "AI Player" label instead of win/loss stats
- ✅ Cannot be edited or deleted (protected)
- ✅ Long-press shows message: "Cloudie is a permanent AI player"

### 3. Minimum Player Validation
- ✅ **At least 2 players required** to start game (can include AI)
- ✅ Start Game button shows dynamic message:
  - `"🎮 Start Game (2 Players)"` when >= 2 selected
  - `"Select at least 2 players to start"` when < 2 selected
- ✅ Button disabled until minimum met

### 4. Database Schema
**PlayerProfile.kt** - Added field:
```kotlin
val isAI: Boolean = false, // True for AI player (Cloudie)
```

### 5. Profile Manager
**ProfileManager.kt** - New constants and function:
```kotlin
const val AI_NAME = "Cloudie"
const val AI_PLAYER_CODE = "AI0001"

suspend fun getOrCreateAIProfile(): PlayerProfile {
    // Auto-creates or retrieves existing AI profile
}
```

## Usage Flow

1. **App Launch** → SplashActivity → MainActivity → ProfileSelectionActivity
2. **ProfileSelectionActivity.onCreate()** → Auto-creates Cloudie
3. **User sees**:
   - Regular player profiles (6 slots max)
   - ☁️ Cloudie with cyan background
   - Guest option
4. **User selects**:
   - 1 human player + Cloudie = ✅ Can start
   - Only Cloudie = ❌ Need 1 more
   - 2+ human players (with/without Cloudie) = ✅ Can start

## Game Scenarios

### 2-Player Game
- Human vs Cloudie
- Human vs Human

### 3-Player Game
- 2 Humans + Cloudie
- 3 Humans

### 4-Player Game
- 3 Humans + Cloudie
- 4 Humans

## Technical Details

### Color Assignment
- **Profile avatar**: `#00D4FF` (cyan/sky blue)
- **Game color**: Assigned from GAME_COLORS when game starts (red/green/blue/yellow)

### Database
- Stored permanently in `player_profiles` table
- `isAI = true` flag distinguishes from human players
- `isGuest = false` (permanent profile, not temporary)

### Future AI Logic
When implementing AI turn logic in MainActivity:
```kotlin
if (currentPlayer.isAI) {
    // AI decision-making logic here
    // Can use basic strategy or ML model
}
```

## Modified Files

1. **PlayerProfile.kt** - Added `isAI: Boolean` field
2. **ProfileManager.kt** - Added AI constants and `getOrCreateAIProfile()`
3. **ProfileSelectionActivity.kt**:
   - Auto-creates Cloudie in onCreate
   - Updated validation: minimum 2 players
   - AI player styling (cloud emoji, "AI Player" label)
   - Protected from editing/deletion
   - Dynamic button text with player count

## Next Steps (For AI Implementation)

1. ✅ **Turn Logic** - Detects when it's AI's turn in MainActivity
2. ✅ **Decision Algorithm** - Virtual dice generation:
   - **Single Die Mode**: Random 1-6
   - **Two Dice Mode**: Two random 1-6, averaged and rounded
3. ✅ **Animation Timing** - 1.5 second delay to simulate "thinking"
4. **Difficulty Levels** - Easy/Medium/Hard personality settings (TODO)

## AI Dice Rolling System

### Implementation Details

**Automatic Turn Detection**
```kotlin
private fun checkAndTriggerAITurn() {
    // Checks if currentGameProfiles[currentPlayer].isAI
    // If true, triggers virtual dice roll after 1.5s delay
}
```

**Virtual Dice Generation**
- **Single Die**: `Random.nextInt(1, 7)` → Direct roll
- **Two Dice**: `Random.nextInt(1, 7)` × 2 → Averaged like GoDice

**Flow Sequence**
1. Human player rolls GoDice → `onDiceStable()` called
2. `handleNewRoll()` processes turn → Advances `currentPlayer`
3. `checkAndTriggerAITurn()` checks if new player is AI
4. If AI: Delay 1.5s → Generate random roll → `handleNewRoll()`
5. AI roll sent to ESP32 board and live.html just like human rolls

**Visual Feedback**
- Status: `"☁️ Cloudie rolls: 4"` (single die)
- Status: `"☁️ Cloudie rolls: 3 + 5 = 4"` (two dice)
- Test Log: `"🤖 AI Roll: 4"` or `"🤖 AI Roll: Die 1 = 3, Die 2 = 5, Avg = 4"`

### Game Integration

**ESP32 Board**: AI rolls are sent to ESP32 exactly like human rolls
- Uses same `sendRollToESP32()` function
- LED animations show Cloudie's token moving
- Hall sensor confirmation works identically

**live.html**: AI rolls appear in real-time spectator view
- Same animation as human players
- Cloudie's token (with cyan color) moves on virtual board
- Dice roll values displayed in event log

**Server API**: AI rolls logged to database
- `lastDice1`, `lastDice2`, `lastAvg` tracked
- Stored in `roll_events` table with player name "Cloudie"
- Statistics tracked (but not counted toward win/loss records for AI)

### Modified Files (Dice Rolling)

1. **MainActivity.kt**:
   - Added `currentGameProfiles` list to store full PlayerProfile objects
   - Added `checkAndTriggerAITurn()` function for AI detection
   - Modified `handleNewRoll()` to call AI check after turn advances
   - Modified `resetLocalGame()` to trigger AI if first player
   - Virtual dice generation with 1.5s delay for realism

## Testing Checklist

### Profile Selection
- [x] Build succeeds
- [ ] Cloudie appears automatically in profile grid
- [ ] Cloud emoji ☁️ displays correctly
- [ ] Cannot edit/delete Cloudie
- [ ] Start button disabled with < 2 players
- [ ] Start button shows player count when enabled
- [ ] Can start game with 1 human + Cloudie

### Game Play
- [ ] Cloudie participates in color selection
- [ ] When Cloudie's turn arrives, auto-roll triggers after 1.5s
- [ ] Status shows "☁️ Cloudie rolls: X" (single die mode)
- [ ] Status shows "☁️ Cloudie rolls: X + Y = Z" (two dice mode)
- [ ] AI roll sent to ESP32 board (LED moves Cloudie's token)
- [ ] AI roll appears on live.html spectator view
- [ ] AI roll logged in test log with 🤖 emoji
- [ ] Game advances to next player after AI turn
- [ ] AI can win/lose like human players
- [ ] Multiple AI players work correctly (if supported)
