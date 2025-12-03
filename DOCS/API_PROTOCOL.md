# API Communication Protocol

**Last Drop - Android ↔ Server API (live.html)**

This document describes all data variables transmitted between the Android app and the server API for live web display synchronization.

---

## API Endpoints

**Base URL**: `https://lastdrop.earth/api/`

### 1. Live State Push
- **Endpoint**: `POST /live_push.php?key=ABC123`
- **Purpose**: Push current game state from Android to server for live.html display
- **Direction**: Android → Server → live.html
- **Frequency**: After every game state change (dice roll, undo, reset)

### 2. Live State Fetch
- **Endpoint**: `GET /live_state.php`
- **Purpose**: Fetch current game state for display
- **Direction**: live.html → Server
- **Frequency**: Polling every 2 seconds

---

## Android → Server (POST to /live_push.php)

Android pushes complete game state after every turn, undo, or reset.

### Complete JSON Structure

```json
{
  "players": [
    {
      "id": "p1",
      "name": "Alice",
      "pos": 9,
      "score": 7,
      "eliminated": false,
      "color": "red"
    },
    {
      "id": "p2",
      "name": "Bob",
      "pos": 5,
      "score": 8,
      "eliminated": false,
      "color": "green"
    },
    {
      "id": "p3",
      "name": "Charlie",
      "pos": 12,
      "score": 0,
      "eliminated": true,
      "color": "blue"
    }
  ],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "dice1": 4,
    "dice2": 2,
    "avg": 3,
    "tileIndex": 9,
    "tileName": "Marsh Land",
    "tileType": "CHANCE",
    "chanceCardId": 7,
    "chanceCardText": "You cleaned a riverbank",
    "rolling": false,
    "coinPlaced": true,
    "diceColor1": "red",
    "diceColor2": "green"
  }
}
```

---

### Players Array

Each player object contains current game state for that player.

**Fields:**

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `id` | string | `"p1"`, `"p2"`, `"p3"`, `"p4"` | Player identifier |
| `name` | string | Any | Player's chosen name |
| `pos` | integer | 0-20 | Current tile position (1-based, 0 = start) |
| `score` | integer | 0-10+ | Current water drops |
| `eliminated` | boolean | `true`/`false` | Player eliminated (score ≤ 0) |
| `color` | string | `"red"`, `"green"`, `"blue"`, `"yellow"` | Player's token color |

**Example:**
```json
{
  "id": "p1",
  "name": "Alice",
  "pos": 9,
  "score": 7,
  "eliminated": false,
  "color": "red"
}
```

---

### lastEvent Object

Contains details of the most recent game action (dice roll, undo, or reset).

**Fields:**

| Field | Type | Values | Description |
|-------|------|--------|-------------|
| `playerId` | string | `"p1"` to `"p4"` | Player who performed the action |
| `playerName` | string | Any | Player's name |
| `dice1` | integer/null | 1-6 or `null` | First die value (or only die in 1-die mode) |
| `dice2` | integer/null | 1-6 or `null` | Second die value (or `null` in 1-die mode) |
| `avg` | integer | 1-6 | Average of both dice (or single die value) |
| `tileIndex` | integer | 0-20 | Final landing tile (1-based, 0 = start) |
| `tileName` | string | See Tile Names | Name of landed tile |
| `tileType` | string | See Tile Types | Type of landed tile |
| `chanceCardId` | integer/null | 1-20 or `null` | Chance card number (if landed on CHANCE tile) |
| `chanceCardText` | string | Card text or `""` | Chance card description |
| `rolling` | boolean | `true`/`false` | **Physical dice currently rolling** (GoDice sensor data) |
| `coinPlaced` | boolean | `true`/`false` | **ESP32 Hall sensor confirmed coin placement** |
| `diceColor1` | string | `"red"`, `"green"`, `"blue"`, `"yellow"` | Physical color of first die (GoDice shell) |
| `diceColor2` | string/null | Same as above or `null` | Physical color of second die (or `null` in 1-die mode) |

**Example (2-dice mode with chance card):**
```json
{
  "playerId": "p1",
  "playerName": "Alice",
  "dice1": 4,
  "dice2": 2,
  "avg": 3,
  "tileIndex": 9,
  "tileName": "Marsh Land",
  "tileType": "CHANCE",
  "chanceCardId": 7,
  "chanceCardText": "You cleaned a riverbank",
  "rolling": false,
  "coinPlaced": true,
  "diceColor1": "red",
  "diceColor2": "green"
}
```

**Example (1-die mode):**
```json
{
  "playerId": "p2",
  "playerName": "Bob",
  "dice1": 5,
  "dice2": null,
  "avg": 5,
  "tileIndex": 12,
  "tileName": "Waste Dump",
  "tileType": "DISASTER",
  "chanceCardId": null,
  "chanceCardText": "",
  "rolling": false,
  "coinPlaced": true,
  "diceColor1": "blue",
  "diceColor2": null
}
```

---

### Tile Names & Types

**Tile Names** (20 tiles, 1-based):
1. Start Point
2. Sunny Patch
3. Rain Dock
4. Leak Lane
5. Storm Zone
6. Cloud Hill
7. Oil Spill Bay
8. Riverbank Road
9. Marsh Land
10. Drought Desert
11. Clean Well
12. Waste Dump
13. Sanctuary Stop
14. Sewage Drain Street
15. Filter Plant
16. Mangrove Mile
17. Heatwave Road
18. Spring Fountain
19. Eco Garden
20. Great Reservoir

**Tile Types:**
- `START` - Starting position
- `NORMAL` - No effect
- `CHANCE` - Draw chance card
- `BONUS` - Gain drops
- `PENALTY` - Lose drops
- `DISASTER` - Major loss
- `WATER_DOCK` - Gain drops
- `SUPER_DOCK` - Major gain

---

### Special Event States

#### Rolling State (`rolling: true`)
Indicates physical dice are currently being rolled (detected by GoDice accelerometer).

```json
{
  "playerId": "p1",
  "playerName": "Alice",
  "dice1": null,
  "dice2": null,
  "avg": null,
  "rolling": true,
  "coinPlaced": false,
  ...
}
```

**live.html behavior:**
- Shows "Alice is rolling the dice..." message
- Displays animated rolling dice with player's color
- Does NOT move token yet (waiting for settled dice values)

#### Coin Placement Wait (`coinPlaced: false`)
After dice settle, Android sends values but waits for ESP32 Hall sensor confirmation.

```json
{
  "playerId": "p1",
  "playerName": "Alice",
  "dice1": 4,
  "dice2": 2,
  "avg": 3,
  "tileIndex": 9,
  "rolling": false,
  "coinPlaced": false,
  ...
}
```

**live.html behavior:**
- Shows dice result with animation
- Moves token to new position
- Waits for `coinPlaced: true` update before proceeding

#### Confirmed Placement (`coinPlaced: true`)
ESP32 Hall sensor detected physical coin on correct tile.

```json
{
  "coinPlaced": true
  // ... rest of data
}
```

**live.html behavior:**
- Triggers token landing animation
- Displays tile effect and chance card (if any)
- Game ready for next player

---

## Server → live.html (GET /live_state.php)

live.html polls the server every 2 seconds for latest game state.

**Response**: Identical JSON structure as Android POST (see above).

**Polling Logic:**
```javascript
async function fetchAndUpdate() {
  const res = await fetch('https://lastdrop.earth/api/live_state.php', 
                          { cache: "no-store" });
  const state = await res.json();
  updateUIFromState(state);
}

setInterval(fetchAndUpdate, 2000); // Poll every 2 seconds
```

---

## live.html → Android Communication

**Direction**: None (one-way communication)

live.html is a **read-only display**. It does not send any data back to Android or the server.

**Why read-only?**
- Spectators should not control the game
- Android app is the authoritative game controller
- Server is stateless (no game logic, just data relay)

---

## Data Flow Examples

### Example 1: Normal Dice Roll

**Step 1 - Player picks up dice:**
```
Android → Server:
{
  "lastEvent": {
    "rolling": true,
    "dice1": null,
    "dice2": null,
    ...
  }
}
```

**live.html shows:** "Alice is rolling both dice..." + animated dice

---

**Step 2 - Dice settle (GoDice stable event):**
```
Android → Server:
{
  "lastEvent": {
    "rolling": false,
    "dice1": 4,
    "dice2": 2,
    "avg": 3,
    "coinPlaced": false,
    ...
  }
}
```

**live.html shows:** Dice result (4 + 2 = 3), token moves to tile 9

---

**Step 3 - Player places physical coin (ESP32 Hall sensor):**
```
Android → Server:
{
  "players": [
    {"id": "p1", "pos": 9, "score": 8, ...}
  ],
  "lastEvent": {
    "coinPlaced": true,
    "tileIndex": 9,
    "tileName": "Marsh Land",
    "tileType": "CHANCE",
    "chanceCardId": 7,
    "chanceCardText": "You cleaned a riverbank",
    ...
  }
}
```

**live.html shows:** Token lands, chance card popup, score updates

---

### Example 2: Test Mode 2 (Android/Web Only)

**No ESP32 → Instant Confirmation:**
```
Android → Server:
{
  "lastEvent": {
    "rolling": false,
    "coinPlaced": true,  // Immediate (no physical board)
    "dice1": 5,
    "dice2": null,
    "avg": 5,
    ...
  }
}
```

**live.html shows:** Dice result + token movement + effects (no waiting for ESP32)

---

### Example 3: Player Elimination

```
Android → Server:
{
  "players": [
    {
      "id": "p3",
      "name": "Charlie",
      "pos": 12,
      "score": 0,
      "eliminated": true,  // Eliminated!
      "color": "blue"
    }
  ],
  "lastEvent": {
    "playerId": "p3",
    "tileIndex": 12,
    "tileName": "Waste Dump",
    "tileType": "DISASTER",
    ...
  }
}
```

**live.html shows:**
- Charlie's token fades/grays out
- "Charlie eliminated!" message
- Charlie removed from turn rotation

---

### Example 4: Game Reset

```
Android → Server:
{
  "players": [
    {"id": "p1", "pos": 0, "score": 10, "eliminated": false, ...},
    {"id": "p2", "pos": 0, "score": 10, "eliminated": false, ...},
    {"id": "p3", "pos": 0, "score": 10, "eliminated": false, ...}
  ],
  "lastEvent": {
    "playerId": "",
    "dice1": null,
    "dice2": null,
    "reset": true,  // Reset flag
    ...
  }
}
```

**live.html shows:**
- All tokens return to start
- All scores reset to 10
- "Game reset - Ready to play!" message

---

## Variable Summary Table

### Android → Server Variables

| Category | Variable | Type | Purpose |
|----------|----------|------|---------|
| **Players** | `id` | string | Player identifier |
| | `name` | string | Player name |
| | `pos` | integer | Tile position |
| | `score` | integer | Water drops |
| | `eliminated` | boolean | Elimination status |
| | `color` | string | Token color |
| **Last Event** | `playerId` | string | Active player |
| | `playerName` | string | Active player name |
| | `dice1` | integer/null | First die value |
| | `dice2` | integer/null | Second die value |
| | `avg` | integer | Averaged/single die |
| | `tileIndex` | integer | Landing tile |
| | `tileName` | string | Tile name |
| | `tileType` | string | Tile category |
| | `chanceCardId` | integer/null | Card number |
| | `chanceCardText` | string | Card description |
| | `rolling` | boolean | Dice rolling state |
| | `coinPlaced` | boolean | ESP32 confirmation |
| | `diceColor1` | string | First die shell color |
| | `diceColor2` | string/null | Second die shell color |

### Server → live.html Variables

**Identical to Android → Server** (server is passthrough)

### live.html → Android/Server Variables

**None** (live.html is read-only)

---

## Authentication & Security

### API Key
- **Parameter**: `?key=ABC123`
- **Location**: Query string on POST endpoint
- **Purpose**: Basic authentication to prevent unauthorized writes
- **Current Value**: `ABC123` (hardcoded for development)
- **Production**: Should be moved to `BuildConfig.API_KEY` via `local.properties`

### HTTPS
- **Protocol**: HTTPS (TLS encrypted)
- **Domain**: `lastdrop.earth`
- **Certificate**: Valid SSL certificate required

### CORS (Cross-Origin Resource Sharing)
- live.html fetches from different domain
- Server must include CORS headers:
  ```
  Access-Control-Allow-Origin: *
  Access-Control-Allow-Methods: GET, POST
  ```

---

## Error Handling

### Android Side

**Network Errors:**
```kotlin
try {
  // POST to server
} catch (e: Exception) {
  Log.e(TAG, "Error pushing live state", e)
  // Continue game (live display optional)
}
```

- Game continues even if API push fails
- No retry mechanism (next turn will sync)
- 3-second connection timeout

### live.html Side

**Fetch Errors:**
```javascript
try {
  const res = await fetch(LIVE_STATE_URL);
  if (!res.ok) throw new Error("HTTP " + res.status);
  const state = await res.json();
  // Update display
} catch (err) {
  console.error("Live state error:", err);
  connStatus.textContent = "OFFLINE";
  modeStatus.textContent = "DEMO";
  // Switch to demo mode automatically
}
```

**Error States:**
- **OFFLINE**: Cannot reach server (red badge)
- **DEMO**: Fallback to simulated demo data
- **ONLINE**: Successfully connected (green badge)

---

## Performance Considerations

### Polling Frequency
- **Current**: 2-second intervals
- **Rationale**: Balance between responsiveness and server load
- **Alternative**: WebSocket for real-time push (future enhancement)

### Payload Size
- **Typical**: ~500-800 bytes (JSON)
- **Maximum**: 2 KB (with all players + full event)
- **Compression**: None (small payload, negligible benefit)

### Caching
```javascript
fetch(LIVE_STATE_URL, { cache: "no-store" })
```
- Forces fresh data on every poll
- Prevents stale state display
- Essential for real-time updates

---

## Test Modes & API

### Test Mode 1 (ESP32 Board Only)
- **API Usage**: Full (Android → Server → live.html)
- **Behavior**: Same as production
- **Difference**: Virtual dice instead of GoDice
- **rolling flag**: Always `false` (simulated)
- **coinPlaced**: Waits for ESP32 Hall sensor

### Test Mode 2 (Android + Web Only)
- **API Usage**: Full (Android → Server → live.html)
- **Behavior**: Instant updates (no ESP32 wait)
- **Difference**: `coinPlaced` always `true` immediately
- **rolling flag**: Always `false` (simulated)
- **Purpose**: Test Android logic + live.html without hardware

---

## Future API Enhancements

**Potential additions:**

1. **WebSocket Support**
   - Replace polling with push notifications
   - Lower latency, reduced server load
   - Real-time dice rolling updates

2. **Player Statistics**
   - `GET /api/player_stats.php?playerId=p1`
   - Historical win/loss records
   - Average scores, favorite tiles

3. **Game History**
   - `GET /api/game_history.php?gameId=123`
   - Complete turn-by-turn replay
   - Time-series data for analysis

4. **Multi-Game Support**
   - `gameId` field in all payloads
   - Multiple concurrent games
   - Spectator room selection

5. **Chat/Reactions**
   - Spectator emoji reactions
   - Live chat overlay
   - Optional feature toggle

---

**Protocol Version:** 1.0  
**Last Updated:** December 3, 2025  
**Compatibility:** Android build 1.0, live.html v3171
