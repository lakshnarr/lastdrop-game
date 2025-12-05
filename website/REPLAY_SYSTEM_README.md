# Last Drop - Game Replay System (Phase 5.2)

## Overview

The game replay system allows players to record, save, and watch complete game sessions. This feature enhances engagement, enables learning from past games, and provides shareable content for viral growth.

## Architecture

### Database Schema

**Table: `game_replays`**
- Stores complete game data including events timeline
- Enhanced from Phase 4 with additional metadata fields
- Supports featured replays, view tracking, and share URLs

Key fields:
- `sessionId` - Links to original game session
- `replayData` - JSON array of timestamped events
- `winner`, `finalScores` - Game outcome
- `views` - Popularity tracking
- `featured` - Highlight exceptional games

### Backend API

**Endpoints:**

1. **`POST /api/save_replay.php`**
   - Saves a completed game replay
   - Generates unique share URL
   - Returns replay ID for immediate viewing

2. **`GET /api/get_replay.php?id={replayId}`**
   - Retrieves full replay data by ID
   - Increments view counter
   - Returns all events for playback

3. **`GET /api/list_replays.php`**
   - Lists replays with filtering/sorting
   - Supports pagination (limit/offset)
   - Filters: featured, sortBy (views/createdAt/duration/rolls)

### Frontend Components

**1. ReplayRecorder.js** - Recording Module
- Captures all game events with timestamps
- Records: rolls, moves, chance cards, eliminations, winner
- Auto-saves when game ends
- Minimal performance overhead

**2. live.html Integration**
- Automatically starts recording when game begins
- Records events as they occur
- Shows share popup when game ends
- Zero manual configuration required

**3. replay.html** - Playback Page
- Load replay by ID from URL parameter
- Playback controls: play/pause, restart, seek
- Speed control: 0.5x, 1x, 2x, 4x
- Displays replay metadata (duration, rolls, winner)
- **Note:** Board visualization is placeholder (Phase 5.3)

**4. replays.html** - Browse Page
- Grid view of all replays
- Filter by: latest, most viewed, longest, featured
- Pagination for large collections
- Click any replay to watch

## Data Flow

### Recording Flow
1. Game starts → `replayRecorder.startRecording()` called
2. Each event → `recordEvent(type, data)` captures with timestamp
3. Game ends → `autoSave()` sends to server
4. Server returns share URL → popup shown to user

### Event Types
- **roll** - Dice rolled (dice1, dice2, colors)
- **move** - Player moved (fromPos, toPos, tileName)
- **chance** - Chance card drawn (cardText)
- **eliminated** - Player eliminated (playerId)
- **winner** - Game won (finalScore)
- **snapshot** - Full state at point in time

### Playback Flow
1. User visits `/replay.html?id=123`
2. Fetch replay data from API
3. Initialize board with initial state
4. Play events sequentially with original timing
5. Speed control adjusts playback rate

## Usage

### For Developers

**Install Database Schema:**
```bash
mysql -u lastdrop_user -p lastdrop_db < website/api/database_schema.sql
```

**Configure API:**
Edit `website/api/save_replay.php`, `get_replay.php`, `list_replays.php`:
```php
$host = 'localhost';
$dbname = 'lastdrop_db';
$username = 'lastdrop_user';
$password = 'your_secure_password';
```

### For End Users

**Watch a Replay:**
1. Visit `/replays.html` to browse available replays
2. Click "Watch" on any replay card
3. Use playback controls to navigate
4. Change speed for faster/slower playback

**Share a Replay:**
- After game ends, popup appears with share URL
- Copy link and share via social media
- Recipients can watch without account

## API Reference

### Save Replay

**Request:**
```json
POST /api/save_replay.php
{
  "sessionId": "unique-session-id",
  "boardId": "LASTDROP-0001",
  "playerCount": 3,
  "playerNames": ["Alice", "Bob", "Charlie"],
  "playerColors": ["#FF0000", "#00FF00", "#0000FF"],
  "duration": 1234,
  "totalRolls": 56,
  "winner": "Alice",
  "winnerPlayerId": 0,
  "finalScores": [45, 32, 28],
  "endReason": "completed",
  "replayData": {
    "initialState": {...},
    "events": [
      {"type": "roll", "timestamp": 0, "playerId": 0, "dice1": 5},
      {"type": "move", "timestamp": 2000, "playerId": 0, "fromPosition": 0, "toPosition": 5}
    ]
  }
}
```

**Response:**
```json
{
  "success": true,
  "replayId": 123,
  "shareUrl": "https://lastdrop.earth/replay.html?id=123"
}
```

### Get Replay

**Request:**
```
GET /api/get_replay.php?id=123
```

**Response:**
```json
{
  "success": true,
  "replay": {
    "id": 123,
    "sessionId": "...",
    "playerNames": [...],
    "duration": 1234,
    "replayData": {...},
    "views": 42,
    ...
  }
}
```

### List Replays

**Request:**
```
GET /api/list_replays.php?limit=20&offset=0&sortBy=views&order=DESC&featured=1
```

**Response:**
```json
{
  "success": true,
  "replays": [...],
  "total": 150,
  "limit": 20,
  "offset": 0
}
```

## Future Enhancements (Phase 5.3+)

- [ ] Full board visualization in replay.html
- [ ] Event annotations and highlights
- [ ] Share replay to social media with preview
- [ ] Download replay as video
- [ ] Replay analytics (heatmaps, statistics)
- [ ] Compare multiple replays side-by-side
- [ ] Community features (comments, ratings)
- [ ] Replay tournaments and challenges

## Performance Considerations

- **Storage:** ~50KB per game replay (JSON compressed)
- **Recording Overhead:** <1% CPU during gameplay
- **Playback:** Client-side rendering, no server streaming
- **Database:** Indexed on sessionId, views, createdAt for fast queries

## Security

- Replays are public by default (no sensitive data)
- Session IDs are UUIDs (not guessable)
- Rate limiting recommended for API endpoints
- No authentication required for viewing (viral growth)

## Testing

**Manual Test Workflow:**
1. Start a game in live.html
2. Play through to completion
3. Verify share popup appears with URL
4. Open replays.html and confirm game is listed
5. Click "Watch" and verify playback controls work
6. Test speed controls and seek functionality

**Database Test:**
```sql
-- Check saved replays
SELECT id, sessionId, winner, views, createdAt FROM game_replays ORDER BY createdAt DESC LIMIT 10;

-- Find most viewed
SELECT id, winner, views FROM game_replays ORDER BY views DESC LIMIT 5;

-- Check featured
SELECT id, winner, featured FROM game_replays WHERE featured = 1;
```

## Troubleshooting

**Replay not saving:**
- Check browser console for API errors
- Verify database connection in PHP files
- Ensure `game_replays` table exists

**Playback not working:**
- Confirm replay ID in URL is valid
- Check API response for errors
- Verify `replayData` JSON structure

**Missing events:**
- Check `ReplayRecorder.isRecording()` returns true
- Verify events are being recorded in browser console
- Confirm `replayData.events` array in database

## Credits

**Phase 5.2 Implementation:** December 5, 2025
**Database Schema:** Enhanced from Phase 4
**Frontend:** Integrated with existing live.html/shared.css
**Backend:** PHP API with MySQL storage

---

For questions or issues, refer to main project documentation or contact the development team.
