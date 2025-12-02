# Android App Integration Guide for Live Board

## ✅ Server API is Ready!

The server now has two endpoints for real-time game updates:

### 1. **GET** `/api/live_state.php?key=ABC123`
Returns the current game state for the web visualization.

**Response:**
```json
{
  "players": [
    {"id": "P1", "name": "Player 1", "pos": 5, "drops": 10, "eliminated": false},
    {"id": "P2", "name": "Player 2", "pos": 12, "drops": 8, "eliminated": false}
  ],
  "lastEvent": {
    "playerId": "P1",
    "playerName": "Player 1",
    "dice1": 3,
    "dice2": 4,
    "avg": 3.5,
    "tileIndex": 5,
    "chanceCardId": "C1"
  }
}
```

### 2. **POST** `/api/live_push.php?key=ABC123`
Android app calls this to update game state after each dice roll.

**Request Body (JSON):**
```json
{
  "players": [
    {"id": "P1", "name": "Player 1", "pos": 5, "drops": 10, "eliminated": false, "color": "red"},
    {"id": "P2", "name": "Player 2", "pos": 12, "drops": 8, "eliminated": false, "color": "green"}
  ],
  "lastEvent": {
    "playerId": "P1",
    "playerName": "Player 1",
    "dice1": 3,
    "dice2": 4,
    "avg": 3.5,
    "tileIndex": 5,
    "chanceCardId": "C1"
  }
}
```

**Player Colors:**
- Available: `"red"`, `"green"`, `"blue"`, `"yellow"`
- If not specified, defaults to: P1=red, P2=green, P3=blue, P4=yellow
- Only active players will show tokens on the board
- Each player gets a unique colored chess soldier pawn image

**Pawn Images:**
- `/assets/pawns/pawn-red.svg` - Red gradient chess soldier
- `/assets/pawns/pawn-green.svg` - Green gradient chess soldier
- `/assets/pawns/pawn-blue.svg` - Blue gradient chess soldier
- `/assets/pawns/pawn-yellow.svg` - Yellow gradient chess soldier

**Response:**
```json
{
  "ok": true,
  "timestamp": "2025-11-30T20:28:11+00:00",
  "playersReceived": 2
}
```

## Android Code Example

Add this to your Android app (Kotlin):

```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray

class LiveBoardSync {
    private val client = OkHttpClient()
    private val apiUrl = "https://lastdrop.earth/api/live_push.php?key=ABC123"
    
    fun pushGameState(
        players: List<Player>,
        lastEvent: GameEvent?
    ) {
        // Build JSON payload
        val playersArray = JSONArray()
        for (player in players) {
            val playerObj = JSONObject()
            playerObj.put("id", player.id)
            playerObj.put("name", player.name)
            playerObj.put("pos", player.position)
            playerObj.put("drops", player.drops)
            playerObj.put("eliminated", player.isEliminated)
            if (player.color != null) {
                playerObj.put("color", player.color)
            }
            playersArray.put(playerObj)
        }
        
        val payload = JSONObject()
        payload.put("players", playersArray)
        
        if (lastEvent != null) {
            val eventObj = JSONObject()
            eventObj.put("playerId", lastEvent.playerId)
            eventObj.put("playerName", lastEvent.playerName)
            eventObj.put("dice1", lastEvent.dice1)
            eventObj.put("dice2", lastEvent.dice2)
            eventObj.put("avg", (lastEvent.dice1 + lastEvent.dice2) / 2.0)
            eventObj.put("tileIndex", lastEvent.tileIndex)
            eventObj.put("chanceCardId", lastEvent.chanceCardId ?: JSONObject.NULL)
            payload.put("lastEvent", eventObj)
        }
        
        // Send POST request
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = payload.toString().toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LiveBoard", "Failed to push state: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.d("LiveBoard", "State pushed successfully")
                    } else {
                        Log.e("LiveBoard", "Server error: ${response.code}")
                    }
                }
            }
        })
    }
}

// Data classes
data class Player(
    val id: String,
    val name: String,
    val position: Int,
    val drops: Int,
    val isEliminated: Boolean,
    val color: String? = null  // Optional: "red", "green", "blue", or "yellow"
)

data class GameEvent(
    val playerId: String,
    val playerName: String,
    val dice1: Int,
    val dice2: Int,
    val tileIndex: Int,
    val chanceCardId: String?
)
```

## When to Call the API

Call `pushGameState()` after:
1. ✅ ESP32 sends dice roll data
2. ✅ Player position is calculated
3. ✅ Any tile effect is applied
4. ✅ Chance card is drawn (if applicable)

## Testing

Test the API manually:
```bash
curl -X POST "https://lastdrop.earth/api/live_push.php?key=ABC123" \
  -H "Content-Type: application/json" \
  -d '{
    "players": [
      {"id": "P1", "name": "Player 1", "pos": 3, "eliminated": false}
    ],
    "lastEvent": {
      "playerId": "P1",
      "playerName": "Player 1",
      "dice1": 2,
      "dice2": 1,
      "avg": 1.5,
      "tileIndex": 3,
      "chanceCardId": null
    }
  }'
```

Then check the live board: https://lastdrop.earth/live

## Important Notes

- The web page polls `/api/live_state.php` every 2 seconds
- Updates appear within 2 seconds of Android pushing data
- Make sure `demoMode` is set to `false` on the live page
- Connection status will show "ONLINE" when API is working
- Mode will show "LIVE" when demo is off
