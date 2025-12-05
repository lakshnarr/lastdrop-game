# Last Drop API Documentation

## Overview

Backend API endpoints for Last Drop game system. Handles game state management, session tracking, and spectator features.

---

## 🔐 Authentication

All endpoints require an API key for security.

**API Key**: `ABC123` (change in production)

### Methods:
- **Query Parameter**: `?key=ABC123`
- **Request Body**: `{"apiKey": "ABC123", ...}`

---

## 📡 Endpoints

### 1. Active Games List

**Endpoint**: `GET /api/active_games.php`

**Purpose**: Returns list of all currently active game sessions

**Parameters**:
- `key` (required) - API key

**Response**:
```json
{
  "success": true,
  "count": 3,
  "totalGames": 3,
  "totalPlayers": 9,
  "totalSpectators": 15,
  "games": [
    {
      "sessionId": "a1b2c3d4-demo-0001",
      "boardId": "LASTDROP-0001",
      "playerCount": 3,
      "spectatorCount": 5,
      "duration": "12:34",
      "status": "active",
      "hostConnected": true
    }
  ],
  "timestamp": "2025-12-05T14:45:00+00:00"
}
```

**Example**:
```bash
curl "https://lastdrop.earth/api/active_games.php?key=ABC123"
```

---

### 2. Session Info

**Endpoint**: `GET /api/session_info.php`

**Purpose**: Get detailed information about a specific session

**Parameters**:
- `key` (required) - API key
- `session` (required) - Session ID

**Response**:
```json
{
  "success": true,
  "session": {
    "sessionId": "a1b2c3d4-demo-0001",
    "boardId": "LASTDROP-0001",
    "playerCount": 3,
    "spectatorCount": 5,
    "duration": "12:34",
    "status": "active",
    "hostConnected": true,
    "gameState": {
      "currentPlayer": 0,
      "players": [
        {"name": "Alice", "score": 12},
        {"name": "Bob", "score": 8}
      ]
    }
  },
  "timestamp": "2025-12-05T14:45:00+00:00"
}
```

**Example**:
```bash
curl "https://lastdrop.earth/api/session_info.php?key=ABC123&session=a1b2c3d4"
```

---

### 3. Live Push

**Endpoint**: `POST /api/live_push.php`

**Purpose**: Update game state from Android app

**Request Body**:
```json
{
  "apiKey": "ABC123",
  "sessionId": "a1b2c3d4-demo-0001",
  "boardId": "LASTDROP-0001",
  "players": [
    {
      "name": "Alice",
      "color": "#FF0000",
      "score": 12,
      "position": 5,
      "alive": true
    }
  ],
  "lastEvent": {
    "type": "dice_roll",
    "playerId": 0,
    "dice1": 3,
    "dice2": 4,
    "avg": 4
  }
}
```

**Response**:
```json
{
  "success": true,
  "message": "Game state updated",
  "sessionId": "a1b2c3d4-demo-0001",
  "boardId": "LASTDROP-0001",
  "playerCount": 3,
  "timestamp": "2025-12-05T14:45:00+00:00"
}
```

**Example**:
```bash
curl -X POST https://lastdrop.earth/api/live_push.php \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"ABC123","sessionId":"test-123","boardId":"LASTDROP-0001","players":[]}'
```

---

### 4. Session Cleanup

**Endpoint**: `GET /api/cleanup_sessions.php`

**Purpose**: Remove inactive sessions (automated via cron)

**Parameters**:
- `token` (required) - Security token

**Response**:
```json
{
  "success": true,
  "deletedSessions": 2,
  "activeSessions": 3,
  "threshold": "5 minutes",
  "timestamp": "2025-12-05T14:45:00+00:00"
}
```

**Cron Setup**:
```bash
# Run every minute
* * * * * php /var/www/lastdrop.earth/public/api/cleanup_sessions.php >> /var/log/lastdrop_cleanup.log 2>&1

# Or via curl
* * * * * curl -s "https://lastdrop.earth/api/cleanup_sessions.php?token=CLEANUP_TOKEN_12345" >> /dev/null 2>&1
```

---

## 🗄️ Database Setup

### Installation

1. **Connect to MySQL**:
```bash
mysql -u root -p
```

2. **Create database**:
```sql
CREATE DATABASE IF NOT EXISTS lastdrop;
USE lastdrop;
```

3. **Run schema**:
```bash
mysql -u root -p lastdrop < database_schema.sql
```

4. **Verify**:
```sql
SHOW TABLES;
SELECT * FROM active_sessions;
```

### Configuration

Update database credentials in all PHP files:
```php
define('DB_HOST', 'localhost');
define('DB_NAME', 'lastdrop');
define('DB_USER', 'lastdrop_user');
define('DB_PASS', 'your_secure_password');
```

---

## 🔧 Production Setup

### 1. Security

**Change default credentials**:
- API Key: Change `ABC123` to random string
- Database password: Use strong password
- Cleanup token: Change `CLEANUP_TOKEN_12345`

**Update all files**:
- `active_games.php` - Line 28
- `session_info.php` - Line 28
- `live_push.php` - Line 36
- `cleanup_sessions.php` - Line 20

### 2. File Permissions

```bash
# Set ownership
sudo chown -R www-data:www-data /var/www/lastdrop.earth/public/api/

# Set permissions
chmod 755 /var/www/lastdrop.earth/public/api/*.php
chmod 775 /var/www/lastdrop.earth/public/game_states/
```

### 3. Error Logging

```bash
# Create log file
sudo touch /var/log/lastdrop_api.log
sudo chown www-data:www-data /var/log/lastdrop_api.log

# Update php.ini or .htaccess
error_log = /var/log/lastdrop_api.log
```

### 4. Cron Job

```bash
# Edit crontab
crontab -e

# Add cleanup job
* * * * * php /var/www/lastdrop.earth/public/api/cleanup_sessions.php >> /var/log/lastdrop_cleanup.log 2>&1
```

---

## 🧪 Testing

### Test Active Games
```bash
curl "http://localhost/api/active_games.php?key=ABC123" | jq
```

### Test Session Info
```bash
curl "http://localhost/api/session_info.php?key=ABC123&session=a1b2c3d4-demo-0001" | jq
```

### Test Live Push
```bash
curl -X POST http://localhost/api/live_push.php \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "ABC123",
    "sessionId": "test-session-123",
    "boardId": "LASTDROP-TEST",
    "players": [
      {"name": "TestPlayer", "score": 10}
    ]
  }' | jq
```

### Test Cleanup
```bash
curl "http://localhost/api/cleanup_sessions.php?token=CLEANUP_TOKEN_12345" | jq
```

---

## 📊 Monitoring

### Check Active Sessions
```sql
SELECT COUNT(*) as active_games 
FROM active_sessions 
WHERE status = 'active' 
  AND lastUpdate > DATE_SUB(NOW(), INTERVAL 5 MINUTE);
```

### Check Inactive Sessions
```sql
SELECT sessionId, boardId, lastUpdate 
FROM active_sessions 
WHERE lastUpdate < DATE_SUB(NOW(), INTERVAL 5 MINUTE);
```

### View Recent Activity
```sql
SELECT * FROM active_sessions 
ORDER BY lastUpdate DESC 
LIMIT 10;
```

---

## 🐛 Troubleshooting

### Database Connection Fails
- Check MySQL service: `sudo systemctl status mysql`
- Verify credentials in PHP files
- Check user permissions: `GRANT ALL ON lastdrop.* TO 'lastdrop_user'@'localhost';`

### Sessions Not Appearing
- Check `live_push.php` is being called by Android app
- Verify database writes: `SELECT * FROM active_sessions;`
- Check error logs: `tail -f /var/log/lastdrop_api.log`

### Cleanup Not Running
- Verify cron job: `crontab -l`
- Check cron logs: `grep CRON /var/log/syslog`
- Test manually: `php cleanup_sessions.php`

---

## 📝 Notes

- Sessions inactive for >5 minutes are automatically removed
- Game state files stored in `../game_states/` directory
- API responses cached for 5 seconds (can be adjusted)
- All timestamps in ISO 8601 format (UTC)

---

**Last Updated**: December 5, 2025
**Version**: 1.0
**Maintainer**: Last Drop Team
