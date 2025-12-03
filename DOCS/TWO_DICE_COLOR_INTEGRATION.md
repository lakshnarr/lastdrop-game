# Two-Dice Color & Individual Rolling Status Integration

## Overview
Complete integration for tracking and displaying:
- Individual dice colors in 2-dice mode
- Individual rolling status for each die
- Granular animation control per die

## Android Implementation (MainActivity_COMPLETE.kt)

### 1. Individual Dice Tracking
```kotlin
// Per-die rolling status map
private val diceRollingStatus: MutableMap<Int, Boolean> = HashMap()

// Per-die color map
private val diceColorMap = HashMap<Int, String>()
```

### 2. Individual Rolling Status Tracking
```kotlin
override fun onDiceRoll(diceId: Int, number: Int) {
    // Mark THIS specific die as rolling
    diceRollingStatus[diceId] = true
    
    runOnUiThread {
        if (playWithTwoDice) {
            val rollingCount = diceRollingStatus.values.count { it }
            tvDiceStatus.text = "Dice rolling… ($rollingCount/2)"
        }
    }
    
    // Send rolling status to API
    if (!isDiceRolling) {
        isDiceRolling = true
        pushRollingStatusToApi()
    }
}

override fun onDiceStable(diceId: Int, number: Int) {
    // Mark THIS specific die as stable
    diceRollingStatus[diceId] = false
    
    // Only set global rolling to false when ALL dice are stable
    val anyDiceRolling = diceRollingStatus.values.any { it }
    if (!anyDiceRolling) {
        isDiceRolling = false
    }
    
    // Process results when both dice are stable...
}
```

### 3. Color Detection
Each physical die's color is detected via GoDice SDK:
```kotlin
override fun onDiceColor(diceId: Int, color: Int) {
    val colorName = when (color) {
        GoDiceSDK.DICE_BLACK -> "black"
        GoDiceSDK.DICE_RED -> "red"
        GoDiceSDK.DICE_GREEN -> "green"
        GoDiceSDK.DICE_BLUE -> "blue"
        GoDiceSDK.DICE_YELLOW -> "yellow"
        GoDiceSDK.DICE_ORANGE -> "orange"
        else -> "red"
    }
    
    diceColorMap[diceId] = colorName
}
```

### 4. Color Mapping (2-Dice Mode)
```kotlin
// In pushLiveStateToBoard() and pushRollingStatusToApi()
if (playWithTwoDice && diceColorMap.size >= 2) {
    // Get individual colors for each die
    val sortedDiceIds = diceColorMap.keys.sorted()
    diceColor1 = diceColorMap[sortedDiceIds[0]] ?: playerColors[playerIndex]
    diceColor2 = diceColorMap[sortedDiceIds.getOrNull(1)] ?: playerColors[playerIndex]
} else {
    // 1-die mode fallback
    diceColor1 = diceColorMap.values.firstOrNull() ?: playerColors[playerIndex]
    diceColor2 = null
}
```

### 5. API Payload
**Rolling Status (while dice are tumbling):**
```json
{
  "players": [],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "rolling": true,
    "diceColor1": "red",
    "diceColor2": "blue",
    "rollingDiceCount": 2,
    "dice1Rolling": true,
    "dice2Rolling": true
  }
}
```

**Partial Rolling (one die settled, one still rolling):**
```json
{
  "players": [],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "rolling": true,
    "diceColor1": "red",
    "diceColor2": "blue",
    "rollingDiceCount": 1,
    "dice1Rolling": false,
    "dice2Rolling": true
  }
}
```

**Final Result:**
```json
{
  "players": [...],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "dice1": 4,
    "dice2": 6,
    "avg": 5,
    "tileIndex": 8,
    "rolling": false,
    "diceColor1": "red",
    "diceColor2": "blue"
  }
}
```

## Web Implementation (live.html)

### 1. API Parsing
```javascript
const diceColor1 = e.diceColor1 || e.diceColor || null;
const diceColor2 = e.diceColor2 || null;
```

### 2. Individual Rolling Animation Control
```javascript
function showRollingDice(playerName = "Player", diceColor1 = null, diceColor2 = null, dice1Rolling = true, dice2Rolling = true) {
  // Set colors
  if (diceColor1 && diceColor2) {
    setIndividualDiceColors(diceColor1, diceColor2);
  }
  
  // Update status text
  if (dice1Rolling && dice2Rolling) {
    dicePlayerName.textContent = `${playerName} is rolling both dice...`;
  } else if (dice1Rolling) {
    dicePlayerName.textContent = `${playerName} - die 1 rolling...`;
  } else if (dice2Rolling) {
    dicePlayerName.textContent = `${playerName} - die 2 rolling...`;
  }
  
  // Animate only the rolling dice
  if (dice1Rolling !== false) {
    scoreboardDice1.style.animation = 'dice-roll 0.5s linear infinite';
  } else {
    scoreboardDice1.style.animation = 'none'; // Stop animation for settled die
  }
  
  if (dice2Rolling !== false) {
    scoreboardDice2.style.animation = 'dice-roll 0.5s linear infinite';
  } else {
    scoreboardDice2.style.animation = 'none'; // Stop animation for settled die
  }
}
```

### 3. Individual Color Application
```javascript
function setIndividualDiceColors(color1, color2 = null) {
  const colorMap = {
    'red': '#ef4444',
    'green': '#22c55e',
    'blue': '#60a5fa',
    'yellow': '#eab308',
    'orange': '#f97316',
    'black': '#1f2937'
  };
  
  const hexColor1 = colorMap[color1] || color1 || '#60a5fa';
  const hexColor2 = color2 ? (colorMap[color2] || color2) : hexColor1;
  
  // Update dice 1 dots
  const dice1Dots = scoreboardDice1.querySelectorAll('.dice-dot');
  dice1Dots.forEach(dot => {
    dot.style.backgroundColor = hexColor1;
  });
  
  // Update dice 2 dots
  const dice2Dots = scoreboardDice2.querySelectorAll('.dice-dot');
  dice2Dots.forEach(dot => {
    dot.style.backgroundColor = hexColor2;
  });
}
```

### 4. Function Signatures Updated

**showRollingDice:**
```javascript
function showRollingDice(playerName = "Player", diceColor1 = null, diceColor2 = null, dice1Rolling = true, dice2Rolling = true)
```

**showStaticDice:**
```javascript
function showStaticDice(value1, value2 = null, playerName = "Player", diceColor1 = null, diceColor2 = null)
```

**show3DDiceRoll:**
```javascript
function show3DDiceRoll(value1, value2 = null, playerName = "Player", callback = null, diceColor1 = null, diceColor2 = null)
```

## Usage Examples

### Scenario 1: Both Dice Rolling (Start of Turn)
- **Android sends**: 
  ```json
  {
    "rolling": true,
    "diceColor1": "red",
    "diceColor2": "blue",
    "dice1Rolling": true,
    "dice2Rolling": true,
    "rollingDiceCount": 2
  }
  ```
- **Web displays**: 
  - Both dice animating
  - Red dots on die 1, blue dots on die 2
  - Text: "Alice is rolling both dice..."

### Scenario 2: One Die Settled, One Still Rolling
- **Android sends**: 
  ```json
  {
    "rolling": true,
    "diceColor1": "red",
    "diceColor2": "blue",
    "dice1Rolling": false,
    "dice2Rolling": true,
    "rollingDiceCount": 1
  }
  ```
- **Web displays**: 
  - Die 1: Static (stopped at its value)
  - Die 2: Still animating
  - Text: "Alice - die 2 rolling..."

### Scenario 3: Both Dice Settled (Final Result)
- **Android sends**: 
  ```json
  {
    "rolling": false,
    "dice1": 4,
    "dice2": 6,
    "diceColor1": "red",
    "diceColor2": "blue"
  }
  ```
- **Web displays**: 
  - Both dice static showing final values
  - Colors maintained
  - Walking animation triggers

### Single Die Mode
- Android sends: `diceColor1: "red"`
- Web displays: Both dice with red dots (for visual consistency during animation)

### Two Dice Mode
- Android sends: `diceColor1: "red", diceColor2: "blue"`
- Web displays: 
  - Dice 1 with red dots
  - Dice 2 with blue dots

## Supported Colors
1. **Red** (#ef4444) - GoDiceSDK.DICE_RED
2. **Green** (#22c55e) - GoDiceSDK.DICE_GREEN
3. **Blue** (#60a5fa) - GoDiceSDK.DICE_BLUE
4. **Yellow** (#eab308) - GoDiceSDK.DICE_YELLOW
5. **Orange** (#f97316) - GoDiceSDK.DICE_ORANGE
6. **Black** (#1f2937) - GoDiceSDK.DICE_BLACK

### Testing Checklist

### Android Side
- [ ] Connect 2 physical GoDice with different colors
- [ ] Verify `onDiceColor()` callback receives correct color codes
- [ ] Verify `diceColorMap` stores both dice IDs and colors
- [ ] **Roll both dice simultaneously** - verify `diceRollingStatus` shows both as true
- [ ] **Let one die settle first** - verify status shows one true, one false
- [ ] Verify status text shows "Dice rolling… (1/2)" or "(2/2)"
- [ ] Check API payload includes `dice1Rolling` and `dice2Rolling` fields
- [ ] Verify `rollingDiceCount` is accurate (0, 1, or 2)

### Web Side
- [ ] Open https://lastdrop.earth/live.html
- [ ] Roll dice in 2-dice mode
- [ ] Verify **both dice animate** when both rolling
- [ ] Verify **one die stops** while other continues if settled at different times
- [ ] Verify colors maintained throughout (red die = red dots, blue die = blue dots)
- [ ] Check status messages: "rolling both dice..." → "die 2 rolling..." → final values
- [ ] Verify single-die mode still works with single color

### Live Integration
```bash
# Check current API state
curl -s "https://lastdrop.earth/api/live_state.php?key=ABC123" | python3 -m json.tool

# Expected output for 2-dice mode:
{
  "lastEvent": {
    "rolling": true,
    "diceColor1": "red",
    "diceColor2": "blue"
  }
}
```

## Deployment Status
✅ MainActivity_COMPLETE.kt - Updated with individual dice rolling tracking
✅ live.html - Deployed with per-die animation control
✅ API integration - Full granular status fields (dice1Rolling, dice2Rolling, rollingDiceCount)

## Technical Advantages

### 1. Accurate Visual Feedback
- Users see exactly which die is still rolling
- No confusion when one die settles before the other
- Natural physics representation

### 2. Debugging Support
- `rollingDiceCount` field helps diagnose issues
- Individual status flags enable detailed logging
- Clear state transitions in API payloads

### 3. Performance Optimization
- Only animating dice that are actually rolling
- Reduced CPU usage when one die has settled
- Smooth transition from rolling to static state

## Notes
- Backward compatible: Still supports legacy `diceColor` field
- Fallback: If only one color detected, both dice show same color
- Visual clarity: Different physical dice = different dot colors on screen
