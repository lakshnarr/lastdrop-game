# Player Color Selection Integration

## Overview
Players choose their token colors during setup in the Android app. The selected colors are sent to the live web board, which displays each player's token in their chosen color.

---

## Android Implementation

### 1. Player Setup Dialog (Lines 295-357)

**Custom Dialog with Name + Color Selection:**
```kotlin
private fun askPlayerName(index: Int) {
    // Create custom dialog layout
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(50, 40, 50, 20)
    }

    // Name input
    val nameLabel = TextView(this).apply {
        text = "Player ${index + 1} Name:"
        textSize = 14f
        setPadding(0, 0, 0, 8)
    }
    val editText = EditText(this).apply {
        hint = "Enter name"
        inputType = InputType.TYPE_CLASS_TEXT
        setPadding(20, 20, 20, 20)
    }

    // Color selection dropdown
    val colorLabel = TextView(this).apply {
        text = "Choose Token Color:"
        textSize = 14f
        setPadding(0, 24, 0, 8)
    }
    
    val availableColors = listOf("red", "green", "blue", "yellow")
    val colorNames = listOf("Red 🔴", "Green 🟢", "Blue 🔵", "Yellow 🟡")
    
    val colorSpinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            colorNames
        )
        // Set default color for this player (cycles through 4 colors)
        setSelection(index % availableColors.size)
    }

    // Add views to container
    container.addView(nameLabel)
    container.addView(editText)
    container.addView(colorLabel)
    container.addView(colorSpinner)

    AlertDialog.Builder(this)
        .setTitle("Player ${index + 1} Setup")
        .setView(container)
        .setPositiveButton("OK") { dialog, _ ->
            val name = editText.text.toString().trim()
            playerNames[index] = if (name.isNotEmpty()) name else "Player ${index + 1}"
            
            // Store selected color
            val selectedColorIndex = colorSpinner.selectedItemPosition
            playerColors[index] = availableColors[selectedColorIndex]
            
            Log.d(TAG, "Player ${index + 1}: ${playerNames[index]}, Color: ${playerColors[index]}")
            
            dialog.dismiss()
            askPlayerName(index + 1)
        }
        .setCancelable(false)
        .show()
}
```

### 2. Color Storage

**Player Colors List (Line 91):**
```kotlin
private val playerColors = mutableListOf("red", "green", "blue", "yellow")
```

- **Mutable list** - colors are updated during player setup
- **4 colors available**: red, green, blue, yellow
- **Default values** - overwritten when player makes selection

### 3. API Integration

**Colors sent in `pushLiveStateToBoard()` (Line 951):**
```kotlin
val playersJson = org.json.JSONArray().apply {
    playerNames.take(playerCount).forEachIndexed { index, name ->
        val pos = playerPositions[name] ?: 0
        val score = playerScores[name] ?: 0
        val color = playerColors[index]  // ← Player's chosen color
        val obj = org.json.JSONObject().apply {
            put("id", "p${index + 1}")
            put("name", name)
            put("pos", pos)
            put("score", score)
            put("eliminated", score <= 0)
            put("color", color)  // ← Sent to web board
        }
        put(obj)
    }
}
```

**Example API Payload:**
```json
{
  "players": [
    {
      "id": "p1",
      "name": "Alice",
      "pos": 5,
      "score": 8,
      "eliminated": false,
      "color": "blue"
    },
    {
      "id": "p2",
      "name": "Bob",
      "pos": 3,
      "score": 10,
      "eliminated": false,
      "color": "yellow"
    }
  ],
  "lastEvent": { ... }
}
```

---

## Web Board Implementation

### 1. Token Creation (live.html, Lines 1690-1720)

**Dynamic Color Application:**
```javascript
function ensureTokenForPlayer(player) {
    if (tokensByPlayerId[player.id]) return tokensByPlayerId[player.id];
    
    const token = document.createElement("div");
    token.className = "player-token";
    token.dataset.player = player.id;

    // Set token color from API or fallback to default
    const playerColors = {
        'P1': 'red',
        'P2': 'green',
        'P3': 'blue',
        'P4': 'yellow'
    };
    const color = player.color || playerColors[player.id] || 'red';
    
    // Apply color via SVG background
    token.style.backgroundImage = `url("/assets/pawns/pawn-${color}.svg")`;

    const label = document.createElement("div");
    label.className = "player-token-label";
    label.textContent = player.name || player.id;
    token.appendChild(label);
    
    tokensLayer.appendChild(token);
    tokensByPlayerId[player.id] = token;
    return token;
}
```

### 2. SVG Assets Required

**File Structure:**
```
/var/www/html/assets/pawns/
├── pawn-red.svg
├── pawn-green.svg
├── pawn-blue.svg
└── pawn-yellow.svg
```

**CSS (Lines 483-497):**
```css
.player-token[data-player="P1"] {
  background-image: url("/assets/pawns/pawn-red.svg");
}

.player-token[data-player="P2"] {
  background-image: url("/assets/pawns/pawn-green.svg");
}

.player-token[data-player="P3"] {
  background-image: url("/assets/pawns/pawn-blue.svg");
}

.player-token[data-player="P4"] {
  background-image: url("/assets/pawns/pawn-yellow.svg");
}
```

**Note:** CSS provides fallback styling, but JavaScript applies `player.color` dynamically, overriding CSS rules.

---

## User Flow

### Android App (Player Setup)

1. **Launch App** → Select number of players (2-4)
2. **For each player:**
   - Dialog appears: "Player X Setup"
   - Enter player name (optional, defaults to "Player X")
   - **Select color from dropdown:**
     - 🔴 Red
     - 🟢 Green
     - 🔵 Blue
     - 🟡 Yellow
   - Default selection cycles through colors (Player 1 → Red, Player 2 → Green, etc.)
3. **Tap OK** → Color stored in `playerColors[index]`
4. **Repeat** for all players
5. **Game starts** → Colors sent to API with every state update

### Web Board (Live Display)

1. **Receives API state** with `players` array
2. **For each player:**
   - Checks if token exists
   - If new player: creates token with `ensureTokenForPlayer(player)`
   - Reads `player.color` field (e.g., "blue")
   - Applies SVG: `url("/assets/pawns/pawn-blue.svg")`
3. **Token displayed** on board with chosen color
4. **Updates live** as players move

---

## Available Colors

| Color  | Emoji | SVG File             | Hex Value (Dice Dots) |
|--------|-------|----------------------|-----------------------|
| Red    | 🔴    | pawn-red.svg         | #ef4444               |
| Green  | 🟢    | pawn-green.svg       | #22c55e               |
| Blue   | 🔵    | pawn-blue.svg        | #60a5fa               |
| Yellow | 🟡    | pawn-yellow.svg      | #eab308               |

---

## Testing Checklist

### Android Side
- [ ] Launch app and select 2 players
- [ ] Verify color dropdown appears for each player
- [ ] Select different colors for each player
- [ ] Check logcat: `Player 1: Alice, Color: blue`
- [ ] Verify `playerColors` list contains selected colors
- [ ] Start game and check API payload includes `color` field

### Web Side
- [ ] Open https://lastdrop.earth/live.html
- [ ] Verify tokens appear in selected colors
- [ ] Test all 4 color combinations
- [ ] Verify color persists during movement
- [ ] Check fallback: if API sends no color, defaults work

### Integration Test
1. Android: Player 1 = "Alice", Color = Blue
2. Android: Player 2 = "Bob", Color = Yellow
3. Web: Verify Alice's token is blue pawn
4. Web: Verify Bob's token is yellow pawn
5. Android: Roll dice and move
6. Web: Verify tokens maintain colors during animation

---

## API Payload Example

**Complete Game State:**
```json
{
  "players": [
    {
      "id": "p1",
      "name": "Alice",
      "pos": 8,
      "score": 7,
      "eliminated": false,
      "color": "blue"
    },
    {
      "id": "p2",
      "name": "Bob",
      "pos": 5,
      "score": 10,
      "eliminated": false,
      "color": "yellow"
    },
    {
      "id": "p3",
      "name": "Charlie",
      "pos": 3,
      "score": 9,
      "eliminated": false,
      "color": "red"
    }
  ],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "dice1": 4,
    "dice2": 6,
    "avg": 5,
    "tileIndex": 8,
    "tileName": "Riverbank Road",
    "tileType": "neutral",
    "chanceCardId": null,
    "chanceCardText": "",
    "rolling": false,
    "diceColor1": "red",
    "diceColor2": "blue"
  }
}
```

---

## File Changes Summary

### Modified Files

**1. MainActivity_COMPLETE.kt (1339 lines)**
- Added `LinearLayout` import for custom dialog
- Enhanced `askPlayerName()` with color selection spinner
- Modified `playerColors` initialization (mutable list)
- Colors sent in `pushLiveStateToBoard()` players array

**2. live.html (2944 lines)**
- No changes required! Already supports `player.color` field
- Dynamic color application in `ensureTokenForPlayer()`
- CSS fallbacks for default player colors

### Required Assets

**SVG Files** (must exist on server):
- `/var/www/html/assets/pawns/pawn-red.svg`
- `/var/www/html/assets/pawns/pawn-green.svg`
- `/var/www/html/assets/pawns/pawn-blue.svg`
- `/var/www/html/assets/pawns/pawn-yellow.svg`

---

## Deployment Status

✅ **MainActivity_COMPLETE.kt** - Updated with color selection (1339 lines)  
✅ **live.html** - Already supports dynamic colors (no changes needed)  
⚠️ **SVG Assets** - Ensure 4 pawn SVG files exist on server  

---

## Future Enhancements

### Potential Features
1. **Color validation** - Prevent duplicate color selection
2. **More colors** - Add orange, purple, pink, cyan (requires new SVG files)
3. **Custom colors** - Color picker instead of dropdown
4. **Save preferences** - Remember player names and colors
5. **Color preview** - Show token preview in setup dialog

### Technical Considerations
- If adding more colors, update both `availableColors` list and create matching SVG files
- Color names must exactly match SVG filenames (e.g., "red" → "pawn-red.svg")
- Web board automatically adapts to any color name sent via API
