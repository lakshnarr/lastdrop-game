# Last Drop - Multi-Board API Documentation

## Overview

The Last Drop API supports multiple physical boards with session isolation. Each game session is uniquely identified by:
- **Board ID**: Hardware identifier (e.g., `LASTDROP-0001`, `LASTDROP-0002`)
- **Session ID**: Unique UUID generated per game (e.g., `a1b2c3d4-e5f6-7890-abcd-ef1234567890`)

## Endpoints

### 1. Live Push (POST)
**URL**: `https://lastdrop.earth/api/live_push.php?key=ABC123`

**Purpose**: Android app pushes game state updates

**Request Body**:
```json
{
  "players": [
    {
      "id": "p1",
      "name": "Alice",
      "pos": 5,
      "score": 8,
      "eliminated": false,
      "color": "#FF0000"
    }
  ],
  "lastEvent": {
    "playerId": "p1",
    "playerName": "Alice",
    "dice1": 3,
    "dice2": 4,
    "avg": 4,
    "tileIndex": 5,
    "tileName": "Tile 5",
    "tileType": "normal",
    "rolling": false,
    "coinPlaced": true,
    "diceColor1": "#FF0000"
  },
  "boardId": "LASTDROP-0001",
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1701619200000
}
```

**Response**:
```json
{
  "success": true,
  "boardId": "LASTDROP-0001",
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionFile": "LASTDROP-0001_a1b2c3d4-e5f6-7890-abcd-ef1234567890.json",
  "bytesWritten": 1234,
  "timestamp": 1701619200000
}
```

**Storage**: Data stored in `data/sessions/{boardId}_{sessionId}.json`

---

### 2. Live State (GET)
**URL**: `https://lastdrop.earth/api/live_state.php?key=ABC123[&boardId=LASTDROP-0001][&sessionId=uuid]`

**Purpose**: Web display polls for current game state

**Query Parameters**:
- `key` (required): API authentication key
- `boardId` (optional): Filter to specific board
- `sessionId` (optional): Filter to specific session

**Behavior**:
- If `boardId` + `sessionId` specified: Returns that exact session
- If only `boardId` specified: Returns latest session for that board
- If neither specified: Returns most recent session from any board

**Response**: Same format as Live Push request body

**Example URLs**:
```
# Latest from any board
/api/live_state.php?key=ABC123

# Latest from specific board
/api/live_state.php?key=ABC123&boardId=LASTDROP-0001

# Specific session
/api/live_state.php?key=ABC123&boardId=LASTDROP-0001&sessionId=a1b2c3d4...
```

---

### 3. Session List (GET)
**URL**: `https://lastdrop.earth/api/session_list.php?key=ABC123[&boardId=LASTDROP-0001]`

**Purpose**: List all active sessions for discovery/debugging

**Query Parameters**:
- `key` (required): API authentication key
- `boardId` (optional): Filter to specific board

**Response**:
```json
{
  "sessions": [
    {
      "boardId": "LASTDROP-0001",
      "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "playerCount": 3,
      "lastUpdate": 1701619200,
      "lastUpdateFormatted": "2024-12-03 14:30:00",
      "fileName": "LASTDROP-0001_a1b2c3d4-e5f6-7890-abcd-ef1234567890.json",
      "url": "/live.html?session=LASTDROP-0001_a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  ],
  "totalSessions": 1,
  "filterBoardId": null
}
```

---

## Web Display URLs

### Direct Session Link
```
https://lastdrop.earth/live.html?session=LASTDROP-0001_a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

### Board-Specific (Latest Session)
```
https://lastdrop.earth/live.html?board=LASTDROP-0001
```

### Any Board (Latest)
```
https://lastdrop.earth/live.html
```

---

## Data Flow

```
┌─────────────┐                    ┌─────────────┐
│   Android   │ ──── POST ────────▶│   Server    │
│     App     │   live_push.php    │ (PHP + FS)  │
└─────────────┘                    └─────────────┘
                                           │
                                           │ Stores
                                           ▼
                                   ┌──────────────────┐
                                   │ data/sessions/   │
                                   │ BOARD_SESSION.json│
                                   └──────────────────┘
                                           │
                                           │ Reads
                                           ▼
┌─────────────┐                    ┌─────────────┐
│  live.html  │ ◀─── GET ──────────│   Server    │
│ (Web Viewer)│   live_state.php   │             │
└─────────────┘                    └─────────────┘
```

---

## Session Isolation

Each game session is completely isolated:

1. **Multiple boards simultaneously**: Board A and Board B can run independent games
2. **Session history**: Old sessions preserved (24hr auto-cleanup)
3. **Spectator targeting**: Web viewers can watch specific board or session
4. **No crosstalk**: Updates to Board A never affect Board B display

---

## File Storage Structure

```
/var/www/lastdrop.earth/
├── api/
│   ├── live_push.php       # Receives updates from Android
│   ├── live_state.php      # Serves data to web viewers
│   └── session_list.php    # Lists active sessions
├── data/
│   └── sessions/
│       ├── LASTDROP-0001_uuid1.json
│       ├── LASTDROP-0001_uuid2.json  # Different game on same board
│       ├── LASTDROP-0002_uuid3.json  # Different board
│       └── ...
└── live.html               # Web viewer
```

---

## Session Cleanup

- **Automatic**: Sessions older than 24 hours deleted on next push
- **Manual**: Delete files from `data/sessions/` directory
- **Retention**: Modify cleanup threshold in `live_push.php` line 88

---

## Security Considerations

1. **API Key**: Change `ABC123` to secure random key
2. **Environment Variables**: Move API key to `.env` file
3. **Rate Limiting**: Add IP-based throttling for production
4. **HTTPS**: Enforce SSL for all API endpoints
5. **Input Validation**: Sanitize boardId/sessionId parameters

---

## Testing

### Test Session Creation
```bash
curl -X POST "https://lastdrop.earth/api/live_push.php?key=ABC123" \
  -H "Content-Type: application/json" \
  -d '{
    "players": [{"id":"p1","name":"Test","pos":1,"score":10,"eliminated":false,"color":"#FF0000"}],
    "lastEvent": null,
    "boardId": "LASTDROP-TEST",
    "sessionId": "test-session-123",
    "timestamp": 1701619200000
  }'
```

### Retrieve Session
```bash
curl "https://lastdrop.earth/api/live_state.php?key=ABC123&boardId=LASTDROP-TEST&sessionId=test-session-123"
```

### List All Sessions
```bash
curl "https://lastdrop.earth/api/session_list.php?key=ABC123"
```

---

## Migration from Single-Board

**Old URL** (still works, gets latest session):
```
https://lastdrop.earth/live.html
```

**New URL** (multi-board aware):
```
https://lastdrop.earth/live.html?board=LASTDROP-0001
```

**Backward Compatibility**: Yes - existing API calls without boardId/sessionId will use latest session from any board.
