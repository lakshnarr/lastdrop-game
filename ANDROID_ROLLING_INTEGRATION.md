# Android Dice Rolling Integration Guide

## Overview
This guide shows you how to send dice rolling status from your Android app to the live web board, so the dice animation starts when the physical dice is rolling and stops at the exact rolled number.

## How GoDice SDK Works

Your app uses **GoDiceSDK** which provides two key callbacks:

1. **`onDiceRoll(diceId, number)`** - Fires WHILE dice is physically rolling/tumbling
2. **`onDiceStable(diceId, number)`** - Fires ONCE when dice stops on final value

## Implementation Steps

### Step 1: Add Rolling Status Tracking

Add this to your MainActivity class variables:

```kotlin
// Add to class variables (around line 95)
private var isDiceRolling: Boolean = false
```

### Step 2: Update onDiceRoll Callback

Modify your `onDiceRoll` function (lines 604-607):

```kotlin
override fun onDiceRoll(diceId: Int, number: Int) {
    // Dice is physically rolling - send rolling status to API
    if (!isDiceRolling) {
        isDiceRolling = true
        pushRollingStatusToApi()
    }
}
```

### Step 3: Update onDiceStable Callback

Modify your `onDiceStable` function (lines 550-601) to reset rolling status:

```kotlin
override fun onDiceStable(diceId: Int, number: Int) {
    // Dice has stopped rolling
    isDiceRolling = false
    
    // Store the result
    diceResults[diceId] = number
    
    // YOUR EXISTING LOGIC HERE...
    // (process turn, update scores, etc.)
    
    // After all processing, push final state with rolling = false
    pushGameStateToApi(rolling = false)
}
```

### Step 4: Add API Push Functions

Add these two new functions to MainActivity:

```kotlin
/**
 * Send rolling status while dice is tumbling
 */
private fun pushRollingStatusToApi() {
    val url = URL("$API_BASE_URL/api/live_push.php?key=$API_KEY")
    
    mainScope.launch(Dispatchers.IO) {
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val currentPlayerName = playerNames[currentPlayer]
            
            // Simple payload with just rolling status
            val payload = """
                {
                    "players": [],
                    "lastEvent": {
                        "playerId": "p${currentPlayer + 1}",
                        "playerName": "$currentPlayerName",
                        "rolling": true
                    }
                }
            """.trimIndent()
            
            connection.outputStream.use { it.write(payload.toByteArray()) }
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Log.d("API", "Rolling status sent")
            } else {
                Log.w("API", "Rolling status failed: $responseCode")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("API", "Failed to push rolling status: ${e.message}")
        }
    }
}

/**
 * Send complete game state after dice stops
 */
private fun pushGameStateToApi(rolling: Boolean = false) {
    val url = URL("$API_BASE_URL/api/live_push.php?key=$API_KEY")
    
    mainScope.launch(Dispatchers.IO) {
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Build players array
            val playersJson = buildString {
                append("[")
                for (i in 0 until playerCount) {
                    val playerId = "p${i + 1}"
                    val playerName = playerNames[i]
                    val pos = playerPositions[playerId] ?: 1
                    val score = playerScores[playerId] ?: 10
                    val eliminated = score <= 0
                    
                    if (i > 0) append(",")
                    append("""
                        {
                            "id": "$playerId",
                            "name": "$playerName",
                            "pos": $pos,
                            "score": $score,
                            "eliminated": $eliminated
                        }
                    """.trimIndent())
                }
                append("]")
            }
            
            // Build lastEvent with all details
            val currentPlayerName = playerNames[currentPlayer]
            val currentPlayerId = "p${currentPlayer + 1}"
            val currentPos = playerPositions[currentPlayerId] ?: 1
            
            val eventJson = """
                {
                    "playerId": "$currentPlayerId",
                    "playerName": "$currentPlayerName",
                    "dice1": ${lastDice1 ?: 0},
                    ${if (lastDice2 != null) "\"dice2\": $lastDice2," else ""}
                    "avg": ${lastAvg ?: 0},
                    "tileIndex": $currentPos,
                    "tileName": "${lastTile?.name?.replace("\"", "\\\"") ?: ""}",
                    "tileType": "${lastTile?.type ?: ""}",
                    "chanceCardId": ${lastChanceCard?.number ?: "null"},
                    "chanceCardText": "${lastChanceCard?.description?.replace("\"", "\\\"") ?: ""}",
                    "rolling": false
                }
            """.trimIndent()
            
            val payload = """
                {
                    "players": $playersJson,
                    "lastEvent": $eventJson
                }
            """.trimIndent()
            
            connection.outputStream.use { it.write(payload.toByteArray()) }
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Log.d("API", "Game state pushed successfully")
            } else {
                Log.w("API", "Game state push failed: $responseCode")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("API", "Failed to push game state: ${e.message}")
        }
    }
}
```

### Step 5: Call pushGameStateToApi After Processing Turn

In your turn processing logic (inside `onDiceStable`), after you've:
1. Calculated new position
2. Applied tile effects
3. Updated scores
4. Checked for elimination

Add the call:

```kotlin
// After all game logic
pushGameStateToApi(rolling = false)
```

## How It Works

### Timeline Flow:

1. **Physical Dice Starts Rolling**
   - GoDice SDK calls: `onDiceRoll()`
   - Android sends: `{"rolling": true, "playerId": "p1", "playerName": "Laksh"}`
   - Live board: Shows continuous dice rolling animation

2. **Physical Dice Stops on Number**
   - GoDice SDK calls: `onDiceStable(number)`
   - Android processes: turn logic, updates positions, scores
   - Android sends: Complete game state with `{"rolling": false, "dice1": 5, "score": 8, ...}`
   - Live board: Stops animation, shows final dice value, moves coin

### Web Board Behavior:

- **When `rolling: true`** → Shows continuous rolling animation with message "Player is rolling..."
- **When `rolling: false`** → Shows exact dice values, updates scores, moves coins

## Testing

### 1. Test Rolling Status
```bash
curl -X POST "https://lastdrop.earth/api/live_push.php?key=ABC123" \
  -H "Content-Type: application/json" \
  -d '{
    "players": [],
    "lastEvent": {
      "playerId": "p1",
      "playerName": "Test Player",
      "rolling": true
    }
  }'
```

Check live board - should show continuous dice animation.

### 2. Test Final State
```bash
curl -X POST "https://lastdrop.earth/api/live_push.php?key=ABC123" \
  -H "Content-Type: application/json" \
  -d '{
    "players": [
      {"id": "p1", "name": "Test Player", "pos": 5, "score": 8, "eliminated": false}
    ],
    "lastEvent": {
      "playerId": "p1",
      "playerName": "Test Player",
      "dice1": 5,
      "avg": 5,
      "tileIndex": 5,
      "tileName": "Recycling Drive",
      "tileType": "BONUS",
      "chanceCardId": null,
      "chanceCardText": "",
      "rolling": false
    }
  }'
```

Check live board - dice should stop at 5, coin should move.

## Important Notes

1. **API Already Supports This** - No server changes needed, just send the `rolling` field
2. **Rolling Detection** - Web board checks `rolling === true` to trigger continuous animation
3. **Score Field** - Use `score` field (not `drops`) - web automatically maps it to water drops
4. **Error Handling** - Both functions include try-catch for network errors
5. **Async** - Both functions run on IO dispatcher, won't block UI

## Summary

**Add to Android:**
- Track `isDiceRolling` boolean
- Call `pushRollingStatusToApi()` when dice starts rolling
- Call `pushGameStateToApi(false)` when dice stops

**Web Board Will:**
- ✅ Animate dice continuously while `rolling: true`
- ✅ Stop at exact value when `rolling: false`
- ✅ Move coins after dice animation completes
- ✅ Show real-time scores and positions
