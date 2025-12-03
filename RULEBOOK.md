# LAST DROP – ELIMINATION MODE RULEBOOK

## Introduction

LAST DROP Elimination Mode is a **survival-based, smart-board water conservation game**. Players move around a 20-tile looping board. Each player begins with a set amount of water. Landing on penalty tiles, disaster zones, and certain chance cards makes players lose water. Landing on water docks and bonus tiles helps them gain water. 

**A player is eliminated when their water reaches zero. The last surviving player wins.**

---

## Components

### Physical Game Components

- **20-tile smart board** with Hall sensors (physical board)
- **Water bowls** (one per player: 2-4 bowls for players)
- **Water Cloud bowl** (extra bowl containing water, managed by Water Cloud player)
- **Ink fillers/Droppers** (one per player for adding/removing water drops)
- **Water coins** with magnets (magnetic tokens for player positions)
- **GoDice** or normal dice

### Electronic Components

- **ESP32 controller** with LED strip (80 LEDs = 4 per tile)
- **LEDs** for each tile (color-coded by zone)
- **Hall sensors** (20 sensors, one per tile) to detect coin placement
- **Android app** for game control and live updates
- **Web display** (live.html) for spectators

---

## Elimination Mode Gameplay Flow

1. **Water Cloud Setup**: One player acts as "Water Cloud" (banker role)
   - Water Cloud receives the Water Cloud bowl filled with water
   - Water Cloud distributes **10 drops** to each player's bowl using ink filler
   - Water Cloud manages water additions/removals throughout the game

2. **Each player starts with 10 Drops** in their personal water bowl

3. **Players roll the dice**; LED indicates destination tile

4. **Coin must be placed on blinking tile** to continue (Hall sensor verification)

5. **Software applies tile effect**: gain, lose, or draw chance card
   - **Water gain**: Water Cloud uses ink filler to add drops to player's bowl
   - **Water loss**: Player uses ink filler to remove drops and return to Water Cloud bowl

6. **Lap Bonus**: When player completes one lap (crosses Start box after Tile 20)
   - Player receives **+5 drops** from Water Cloud
   - ESP32 detects lap completion via Hall sensor
   - Water Cloud adds 5 drops to player's bowl

7. **Board loops continuously** from Tile 20 back to Tile 1

8. **Players who reach 0 Drops are eliminated** (bowl empty, coin removed from board)

9. **Last player remaining wins**

---

## 20-Tile Elimination Layout

| No. | Tile Name | Type | Effect | Description |
|-----|-----------|------|--------|-------------|
| 1 | **Start Point** | Start | 0 | Beginning of the game |
| 2 | **Sunny Patch** | Penalty | -1 | Evaporation from heat |
| 3 | **Rain Dock** | Water Dock | +3 | Rainwater collection |
| 4 | **Leak Lane** | Penalty | -1 | Water leakage |
| 5 | **Storm Zone** | Disaster | -3 | Severe weather damage |
| 6 | **Cloud Hill** | Bonus | +1 | Cloud condensation bonus |
| 7 | **Oil Spill Bay** | Disaster | -4 | Major contamination |
| 8 | **Riverbank Road** | Safe | 0 | Neutral zone |
| 9 | **Marsh Land** | Chance | Card | Draw chance card |
| 10 | **Drought Desert** | Disaster | -3 | Severe water shortage |
| 11 | **Clean Well** | Water Dock | +2 | Fresh water source |
| 12 | **Waste Dump** | Disaster | -2 | Pollution damage |
| 13 | **Sanctuary Stop** | Chance | Card | Draw chance card |
| 14 | **Sewage Drain Street** | Penalty | -2 | Wastewater contamination |
| 15 | **Filter Plant** | Water Dock | +1 | Water purification |
| 16 | **Mangrove Mile** | Chance | Card | Draw chance card |
| 17 | **Heatwave Road** | Penalty | -2 | Extreme heat evaporation |
| 18 | **Spring Fountain** | Super Dock | +4 | Major water source |
| 19 | **Eco Garden** | Safe | 0 | Neutral zone |
| 20 | **Great Reservoir** | Safe | 0 | Safe zone before loop |

### Tile Type Summary

- **Start** (1 tile): Starting position, no effect
- **Safe** (3 tiles): No water change
- **Penalty** (4 tiles): Lose 1-2 drops
- **Disaster** (4 tiles): Lose 2-4 drops (severe)
- **Water Dock** (3 tiles): Gain 1-3 drops
- **Super Dock** (1 tile): Gain 4 drops
- **Bonus** (1 tile): Gain 1 drop
- **Chance** (3 tiles): Draw random chance card

---

## Elimination Chance Cards (20 Cards)

### Positive Cards (Water Gain)

| No. | Card Description | Effect |
|-----|------------------|--------|
| 1 | You fixed a tap leak | +2 |
| 2 | Rainwater harvested | +2 |
| 3 | You planted two trees | +1 |
| 4 | Cool clouds formed | +1 |
| 5 | You cleaned a riverbank | +1 |
| 6 | Discovered a tiny spring | +3 |
| 7 | You saved a wetland animal | +1 |
| 8 | You reused RO water | +1 |
| 9 | Used bucket instead of shower | +2 |
| 10 | Drip irrigation success | +2 |

### Special Cards (Strategic)

| No. | Card Description | Effect |
|-----|------------------|--------|
| 11 | Skip next penalty | 0 (prevents next penalty tile damage) |
| 12 | Move forward 2 tiles | 0 (bonus movement) |
| 13 | Swap positions with next player | 0 (strategic positioning) |
| 14 | Water Shield | 0 (next damage = 0) |

### Negative Cards (Water Loss)

| No. | Card Description | Effect |
### Starting the Game

1. **Choose number of players** (2-4 players, one acts as Water Cloud)

2. **Water Cloud Setup**:
   - One player volunteers as Water Cloud (banker role)
   - Water Cloud fills the Water Cloud bowl with water
   - Each player receives an ink filler/dropper
   - Each player receives a personal water bowl

3. **Initial Distribution**:
   - Water Cloud uses ink filler to give **10 drops** to each player's bowl
   - Count drops carefully to ensure fairness
   - Players verify their starting water amount

4. **Each player selects a color** (red, green, blue, yellow)

5. **All players start at Tile 1** with 10 Drops

6. **Determine turn order** (highest dice roll goes first, Water Cloud can also play)

7. **Place coins on Start Point** (magnetic coins on Tile 1)-3 |

### Chance Card Summary

- **Positive Cards**: 10 cards (effects: +1 to +3)
### Taking a Turn

1. **Roll the dice** (1 or 2 dice, depending on mode)
   - Single die: Move 1-6 tiles
   - Two dice: Move average of both dice (rounded)

2. **LED animates movement** from current tile to destination

3. **Destination tile blinks** (player color)

4. **Place magnetic coin** on blinking tile

5. **Hall sensor confirms** coin placement (ESP32 monitors coin position)

6. **Tile effect applies automatically**:
   - **Penalty/Disaster (Lose drops)**:
     - Player uses ink filler to remove drops from their bowl
     - Drops returned to Water Cloud bowl
     - Water Cloud verifies correct amount removed
   - **Water Dock/Bonus (Gain drops)**:
     - Water Cloud uses ink filler to add drops to player's bowl
     - Water Cloud takes drops from Water Cloud bowl
     - Player verifies correct amount received
   - **Chance**: Draw random card, apply effect (add/remove drops accordingly)

7. **Check for lap completion**: 
   - If player crosses Start box (Tile 1) after completing Tile 20
   - ESP32 detects lap via Hall sensor
   - **Water Cloud adds +5 drops** to player's bowl from Water Cloud bowl

8. **Check for elimination**: 
### Board Looping & Lap Bonus

- After Tile 20, **players loop back to Tile 1**
- Board is **continuous** - no end zone
- **Lap Completion Bonus**:
  - When coin crosses Start box (Tile 1) after Tile 20, lap is complete
  - ESP32 Hall sensor detects lap completion
  - Android app notifies Water Cloud
  - **Water Cloud adds +5 drops** to player's bowl
  - Lap counter increments in software
- Players continue until eliminated
- Last player standing wins

1. **Roll the dice** (1 or 2 dice, depending on mode)
### Undo Function

- **Players can undo their last move** within 5 seconds
- **Undo reverses**:
  - Position back to previous tile
  - Score back to previous amount
  - Chance card effect cancelled
  - **Water drops restored physically**:
    - If drops were removed, Water Cloud adds them back
    - If drops were added, player removes them back to Water Cloud bowl
- **Coin must be placed** at original position after undo
- **Only 1 undo per turn** allowed
   - Chance: Draw random card, apply effect
7. **Check for elimination**: If drops reach 0, player is out
8. **Next player's turn**

### Board Looping

- After Tile 20, **players loop back to Tile 1**
- Board is **continuous** - no end zone
- Players continue until eliminated
- Last player standing wins

### Elimination

- **Player reaches 0 drops** → Eliminated
- **Remove coin from board**
- **Skip eliminated player's turns**
- **Last survivor wins the game**

### Undo Function
### Reset Function

- **Resets entire game** to starting state
- **Water Cloud resets all bowls**:
  - Collect all water from player bowls back to Water Cloud bowl
  - Redistribute 10 drops to each player
- All players return to Tile 1 with 10 drops
- Board LEDs cleared
- Coins placed on Start Point
- Game history preserved in database
- **Coin must be placed** at original position after undo
- **Only 1 undo per turn** allowed

### Reset Function

- **Resets entire game** to starting state
- All players return to Tile 1 with 10 drops
- Board LEDs cleared
- Game history preserved in database

---

## Winning Conditions
## Strategy Tips

### Survival Strategies

1. **Avoid Disaster Tiles** (7, 5, 10, 12) - cause heavy water loss
2. **Aim for Water Docks** (3, 11, 15) and Super Dock (18)
3. **Complete laps** - Each lap gives +5 drops (significant bonus)
4. **Use Chance strategically** - can provide big gains or special abilities
5. **Manage risk** - sometimes safe tiles are better than chance
### Tile Hotspots

- **Best Tiles**: Spring Fountain (18) +4, Rain Dock (3) +3, **Complete Lap +5**
- **Worst Tiles**: Oil Spill Bay (7) -4, Storm Zone (5) -3, Drought Desert (10) -3
- **Strategic Tiles**: Chance tiles (9, 13, 16) - high risk/reward
- **Lap Strategy**: Completing laps is crucial - +5 drops can save you from eliminations
2. **Aim for Water Docks** (3, 11, 15) and Super Dock (18)
3. **Use Chance strategically** - can provide big gains or special abilities
4. **Manage risk** - sometimes safe tiles are better than chance

### Tile Hotspots

- **Best Tiles**: Spring Fountain (18) +4, Rain Dock (3) +3
- **Worst Tiles**: Oil Spill Bay (7) -4, Storm Zone (5) -3, Drought Desert (10) -3
- **Strategic Tiles**: Chance tiles (9, 13, 16) - high risk/reward

### Special Card Usage

- **Water Shield** - Save for approaching disaster tiles
- **Skip Next Penalty** - Use before penalty-heavy zones
- **Swap Positions** - Move to better position, send opponent to danger
- **Move Forward 2** - Skip dangerous tiles ahead

---

## Technical Implementation

### Hardware Components

- **ESP32 Dev Module**: Main controller
- **WS2812B LED Strip**: 80 RGB LEDs (4 per tile)
- **Hall Effect Sensors**: 20 sensors (A3144), one per tile
- **Magnetic Tokens**: Player position markers

### Software Components

- **Android App** (Kotlin): Game controller
  - GoDice BLE integration
  - ESP32 BLE communication
  - Database (Room) for game history
  - Live API updates
- **ESP32 Firmware** (Arduino C++):
  - LED control and animations
  - Hall sensor monitoring
  - BLE communication
  - Coin placement verification
- **Web Display** (live.html):
  - Real-time game state
  - Player positions and scores
  - Spectator view

### Game Flow (Technical)

1. GoDice → Android (BLE): Dice roll detected
2. Android → ESP32 (BLE): Movement command
3. ESP32: LED animation + wait for coin
4. Hall Sensor → ESP32: Coin detected
5. ESP32 → Android (BLE): Placement confirmed
6. Android → Server (HTTP): Update live.html
7. Web Display: Show animation

---

## Test Modes

### Test Mode 1: ESP32 Board Only
- For hardware team testing
- Dummy dice generator in Android
- Full game logic on ESP32
- Comprehensive test log

### Test Mode 2: Android + Web Only
- For software team testing
- Bypasses ESP32 hardware
## Game Variants

### Speed Mode
- Start with 5 drops instead of 10
- Faster elimination, quicker games
- Lap bonus reduced to +3 drops

### Marathon Mode
- Start with 20 drops
- Longer gameplay, more strategic
- Lap bonus increased to +7 drops

### Team Mode
- 2v2 teams share water pool (one large bowl per team)
- Water Cloud distributes to team bowls
- Team with remaining water wins

### Challenge Mode
- Pre-selected chance cards
- Known tile effects only
- Pure strategy, no random cards
- No lap bonus (pure tile strategy)

### Solo Mode (Practice)
- One player vs. target (survive 5 laps)
## Educational Value

LAST DROP teaches:
- **Water conservation** awareness (physical water drops represent real scarcity)
- **Resource management** skills (managing limited water supply)
- **Strategic thinking** and planning (lap completion vs. immediate gains)
- **Environmental** responsibility (tile themes reflect real water issues)
- **Probability** and risk assessment (chance cards, dice rolls)
- **Technology integration** (smart board, sensors, BLE)
- **Teamwork** (Water Cloud role teaches fairness and responsibility)
- **Manual dexterity** (using ink fillers to measure drops accurately)

## Troubleshooting

### Game Issues

**Coin not detected**:
- Ensure magnet strength is adequate (magnetic coins)
- Check Hall sensor alignment under tile
- Verify tile blinking before placement
- ESP32 monitors coin position continuously

**LED not animating**:
- Check ESP32 connection
- Verify BLE pairing with Android app
- Restart ESP32 if needed

**Score not updating**:
- Confirm coin placement detected by Hall sensor
- Check Android app connection
- Verify game logic processing
- Water Cloud should cross-verify with app display

**Water measurement disputes**:
- Water Cloud is final arbiter
- Count drops carefully with ink filler
- Mark water bowls with measurement lines (optional)
- Verify drops before and after each turn

**Lap bonus not triggered**:
- Ensure coin fully crosses Start box (Tile 1)
- Check ESP32 Hall sensor detection
- Verify app shows lap count increment
- Water Cloud waits for app confirmation before adding drops
**Coin not detected**:
- Ensure magnet strength is adequate
- Check Hall sensor alignment
- Verify tile blinking before placement

**LED not animating**:
- Check ESP32 connection
- Verify BLE pairing
- Restart ESP32 if needed

**Score not updating**:
- Confirm coin placement detected
- Check Android app connection
- Verify game logic processing

### Technical Issues

See `IMPLEMENTATION_GUIDE.md` for hardware troubleshooting.
See `TEST_MODE_GUIDE.md` for testing procedures.

---

## Credits

**Game Design**: Last Drop Team
**Hardware**: ESP32 + WS2812B + Hall Sensors
**Software**: Android (Kotlin) + Arduino (C++) + Web (HTML/JS)
**Integration**: GoDice BLE SDK
**Theme**: Water Conservation & Environmental Awareness

---

## Version History

- **v1.0** - Initial elimination mode implementation
- **v2.0** - Added test modes and comprehensive logging
- **Current** - 20 tiles, 20 chance cards, full smart board integration

---

## License & Usage

This rulebook is part of the Last Drop smart board game project.

For implementation details, see:
- `README.md` - Project overview
- `IMPLEMENTATION_GUIDE.md` - Hardware setup
- `TEST_MODE_GUIDE.md` - Testing procedures
- `.github/copilot-instructions.md` - Developer guide
