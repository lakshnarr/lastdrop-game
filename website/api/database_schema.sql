-- Last Drop Database Schema
-- Phase 4: Session Management and Active Games Tracking
-- Created: December 5, 2025

-- Drop existing tables if they exist (for clean reinstall)
DROP TABLE IF EXISTS tournament_matches;
DROP TABLE IF EXISTS tournament_participants;
DROP TABLE IF EXISTS tournaments;
DROP TABLE IF EXISTS player_stats;
DROP TABLE IF EXISTS leaderboard_entries;
DROP TABLE IF EXISTS game_replays;
DROP TABLE IF EXISTS active_sessions;

-- ============================================
-- Table: active_sessions
-- Purpose: Track all active game sessions for spectate mode
-- ============================================

CREATE TABLE active_sessions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    sessionId VARCHAR(64) UNIQUE NOT NULL,
    boardId VARCHAR(32) DEFAULT NULL,
    playerCount INT DEFAULT 0,
    spectatorCount INT DEFAULT 0,
    startTime DATETIME NOT NULL,
    lastUpdate DATETIME NOT NULL,
    status ENUM('waiting', 'active', 'ended') DEFAULT 'waiting',
    hostConnected BOOLEAN DEFAULT FALSE,
    gameState JSON DEFAULT NULL,
    
    -- Indexes for fast queries
    INDEX idx_session (sessionId),
    INDEX idx_board (boardId),
    INDEX idx_lastupdate (lastUpdate),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: game_replays (Phase 5.2: Game Replay System)
-- Purpose: Store completed game data for replay viewing
-- ============================================

CREATE TABLE game_replays (
    id INT PRIMARY KEY AUTO_INCREMENT,
    sessionId VARCHAR(64) NOT NULL,
    boardId VARCHAR(32) DEFAULT NULL,
    
    -- Game metadata
    playerCount INT DEFAULT 0,
    playerNames JSON DEFAULT NULL,
    playerColors JSON DEFAULT NULL,
    duration INT DEFAULT 0,
    totalRolls INT DEFAULT 0,
    
    -- Game outcome
    winner VARCHAR(64) DEFAULT NULL,
    winnerPlayerId INT DEFAULT NULL,
    finalScores JSON DEFAULT NULL,
    endReason ENUM('completed', 'abandoned', 'timeout') DEFAULT 'completed',
    
    -- Replay data (array of game events with timestamps)
    replayData JSON DEFAULT NULL,
    
    -- Metadata
    createdAt DATETIME NOT NULL,
    views INT DEFAULT 0,
    featured BOOLEAN DEFAULT FALSE,
    shareUrl VARCHAR(255) DEFAULT NULL,
    
    -- Indexes
    INDEX idx_session (sessionId),
    INDEX idx_board (boardId),
    INDEX idx_created (createdAt),
    INDEX idx_views (views),
    INDEX idx_featured (featured),
    INDEX idx_winner (winnerPlayerId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: player_stats (Phase 5.3: Leaderboards)
-- Purpose: Track aggregate player statistics across all games
-- ============================================

CREATE TABLE player_stats (
    id INT PRIMARY KEY AUTO_INCREMENT,
    playerName VARCHAR(100) UNIQUE NOT NULL,
    
    -- Game statistics
    gamesPlayed INT DEFAULT 0,
    gamesWon INT DEFAULT 0,
    totalScore INT DEFAULT 0,
    highestScore INT DEFAULT 0,
    lowestScore INT DEFAULT 0,
    avgScore DECIMAL(10,2) DEFAULT 0,
    
    -- Time statistics
    totalPlayTime INT DEFAULT 0,  -- seconds
    avgGameDuration INT DEFAULT 0,  -- seconds
    
    -- Win statistics
    winRate DECIMAL(5,2) DEFAULT 0,  -- percentage
    currentStreak INT DEFAULT 0,  -- consecutive wins
    bestStreak INT DEFAULT 0,  -- best streak ever
    
    -- Ranking
    eloRating INT DEFAULT 1000,  -- ELO rating system
    rank INT DEFAULT NULL,  -- overall rank position
    
    -- Activity tracking
    lastPlayed DATETIME DEFAULT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    
    -- Indexes
    INDEX idx_player_name (playerName),
    INDEX idx_elo (eloRating),
    INDEX idx_games_won (gamesWon),
    INDEX idx_win_rate (winRate),
    INDEX idx_rank (rank),
    INDEX idx_last_played (lastPlayed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: leaderboard_entries (Phase 5.3: Leaderboards)
-- Purpose: Track daily/weekly/monthly leaderboard snapshots
-- ============================================

CREATE TABLE leaderboard_entries (
    id INT PRIMARY KEY AUTO_INCREMENT,
    playerName VARCHAR(100) NOT NULL,
    
    -- Period tracking
    period ENUM('daily', 'weekly', 'monthly', 'alltime') NOT NULL,
    periodStart DATE NOT NULL,
    periodEnd DATE NOT NULL,
    
    -- Period statistics
    gamesPlayed INT DEFAULT 0,
    gamesWon INT DEFAULT 0,
    totalScore INT DEFAULT 0,
    avgScore DECIMAL(10,2) DEFAULT 0,
    eloRating INT DEFAULT 1000,
    
    -- Ranking
    rank INT NOT NULL,
    
    -- Metadata
    createdAt DATETIME NOT NULL,
    
    -- Unique constraint: one entry per player per period
    UNIQUE KEY unique_player_period (playerName, period, periodStart),
    
    -- Indexes
    INDEX idx_period (period, periodStart),
    INDEX idx_rank (rank),
    INDEX idx_player (playerName),
    INDEX idx_elo (eloRating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: tournaments (Phase 5.4: Tournament Mode)
-- Purpose: Manage tournament events and brackets
-- ============================================

CREATE TABLE tournaments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    tournamentName VARCHAR(200) NOT NULL,
    
    -- Tournament configuration
    format ENUM('single_elimination', 'double_elimination', 'round_robin', 'swiss') NOT NULL DEFAULT 'single_elimination',
    maxParticipants INT NOT NULL DEFAULT 8,
    minParticipants INT DEFAULT 2,
    currentParticipants INT DEFAULT 0,
    
    -- Status tracking
    status ENUM('registration', 'ready', 'in_progress', 'completed', 'cancelled') DEFAULT 'registration',
    
    -- Bracket configuration
    bracketData JSON DEFAULT NULL,  -- Dynamic bracket structure
    currentRound INT DEFAULT 0,
    totalRounds INT DEFAULT NULL,
    
    -- Prize pool
    prizePool INT DEFAULT 0,
    prizeDistribution JSON DEFAULT NULL,  -- {"1st": 50, "2nd": 30, "3rd": 20}
    
    -- Entry requirements
    entryFee INT DEFAULT 0,
    minEloRating INT DEFAULT 0,
    inviteOnly BOOLEAN DEFAULT FALSE,
    
    -- Timing
    registrationStart DATETIME NOT NULL,
    registrationEnd DATETIME NOT NULL,
    startTime DATETIME DEFAULT NULL,
    endTime DATETIME DEFAULT NULL,
    
    -- Organizer
    organizerName VARCHAR(100) DEFAULT NULL,
    description TEXT DEFAULT NULL,
    rules TEXT DEFAULT NULL,
    
    -- Metadata
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    
    -- Indexes
    INDEX idx_status (status),
    INDEX idx_format (format),
    INDEX idx_reg_end (registrationEnd),
    INDEX idx_start_time (startTime),
    INDEX idx_created (createdAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: tournament_participants (Phase 5.4: Tournament Mode)
-- Purpose: Track players registered for tournaments
-- ============================================

CREATE TABLE tournament_participants (
    id INT PRIMARY KEY AUTO_INCREMENT,
    tournamentId INT NOT NULL,
    playerName VARCHAR(100) NOT NULL,
    
    -- Seeding
    seedPosition INT DEFAULT NULL,
    eloAtEntry INT DEFAULT 1000,
    
    -- Status
    status ENUM('registered', 'checked_in', 'active', 'eliminated', 'withdrawn') DEFAULT 'registered',
    currentRound INT DEFAULT 0,
    
    -- Performance
    matchesPlayed INT DEFAULT 0,
    matchesWon INT DEFAULT 0,
    matchesLost INT DEFAULT 0,
    totalScore INT DEFAULT 0,
    
    -- Placement
    finalPosition INT DEFAULT NULL,
    prizeWon INT DEFAULT 0,
    
    -- Metadata
    registeredAt DATETIME NOT NULL,
    checkedInAt DATETIME DEFAULT NULL,
    eliminatedAt DATETIME DEFAULT NULL,
    
    -- Unique constraint
    UNIQUE KEY unique_tournament_player (tournamentId, playerName),
    
    -- Foreign key
    FOREIGN KEY (tournamentId) REFERENCES tournaments(id) ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_tournament (tournamentId),
    INDEX idx_player (playerName),
    INDEX idx_status (status),
    INDEX idx_seed (seedPosition),
    INDEX idx_position (finalPosition)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Table: tournament_matches (Phase 5.4: Tournament Mode)
-- Purpose: Track individual tournament matches
-- ============================================

CREATE TABLE tournament_matches (
    id INT PRIMARY KEY AUTO_INCREMENT,
    tournamentId INT NOT NULL,
    
    -- Match identification
    roundNumber INT NOT NULL,
    matchNumber INT NOT NULL,
    bracketPosition VARCHAR(50) DEFAULT NULL,  -- e.g., "Winners-R1-M1", "Finals"
    
    -- Participants (support 2-4 players)
    player1Name VARCHAR(100) DEFAULT NULL,
    player2Name VARCHAR(100) DEFAULT NULL,
    player3Name VARCHAR(100) DEFAULT NULL,
    player4Name VARCHAR(100) DEFAULT NULL,
    
    -- Results
    winnerName VARCHAR(100) DEFAULT NULL,
    finalScores JSON DEFAULT NULL,  -- {"player1": 25, "player2": 18, ...}
    
    -- Session link
    sessionId VARCHAR(64) DEFAULT NULL,
    replayId INT DEFAULT NULL,
    
    -- Status
    status ENUM('pending', 'ready', 'in_progress', 'completed', 'forfeit') DEFAULT 'pending',
    
    -- Timing
    scheduledTime DATETIME DEFAULT NULL,
    startTime DATETIME DEFAULT NULL,
    endTime DATETIME DEFAULT NULL,
    
    -- Next match routing (for elimination brackets)
    winnerNextMatchId INT DEFAULT NULL,
    loserNextMatchId INT DEFAULT NULL,
    
    -- Metadata
    createdAt DATETIME NOT NULL,
    
    -- Foreign keys
    FOREIGN KEY (tournamentId) REFERENCES tournaments(id) ON DELETE CASCADE,
    FOREIGN KEY (replayId) REFERENCES game_replays(id) ON DELETE SET NULL,
    
    -- Indexes
    INDEX idx_tournament (tournamentId),
    INDEX idx_round (tournamentId, roundNumber),
    INDEX idx_status (status),
    INDEX idx_players (player1Name, player2Name),
    INDEX idx_session (sessionId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Sample Data for Testing
-- ============================================

INSERT INTO active_sessions (
    sessionId, 
    boardId, 
    playerCount, 
    spectatorCount, 
    startTime, 
    lastUpdate, 
    status, 
    hostConnected,
    gameState
) VALUES 
(
    'a1b2c3d4-demo-0001',
    'LASTDROP-0001',
    3,
    5,
    DATE_SUB(NOW(), INTERVAL 12 MINUTE),
    NOW(),
    'active',
    TRUE,
    '{"currentPlayer": 0, "players": [{"name": "Alice", "score": 12}, {"name": "Bob", "score": 8}, {"name": "Charlie", "score": 15}]}'
),
(
    'e5f6g7h8-demo-0002',
    'LASTDROP-0002',
    2,
    2,
    DATE_SUB(NOW(), INTERVAL 5 MINUTE),
    NOW(),
    'active',
    TRUE,
    '{"currentPlayer": 1, "players": [{"name": "David", "score": 10}, {"name": "Eve", "score": 14}]}'
),
(
    'i9j0k1l2-demo-0003',
    'LASTDROP-0003',
    4,
    8,
    DATE_SUB(NOW(), INTERVAL 23 MINUTE),
    NOW(),
    'active',
    TRUE,
    '{"currentPlayer": 2, "players": [{"name": "Frank", "score": 20}, {"name": "Grace", "score": 18}, {"name": "Heidi", "score": 16}, {"name": "Ivan", "score": 22}]}'
);

-- ============================================
-- Cleanup Procedure
-- Purpose: Remove inactive sessions (not updated in 5 minutes)
-- Run via cron job every minute
-- ============================================

DELIMITER //

CREATE PROCEDURE cleanup_inactive_sessions()
BEGIN
    -- Move ended sessions to replays (future feature)
    -- For now, just delete them
    DELETE FROM active_sessions 
    WHERE lastUpdate < DATE_SUB(NOW(), INTERVAL 5 MINUTE);
    
    -- Log cleanup action
    SELECT CONCAT('Cleaned up ', ROW_COUNT(), ' inactive sessions') AS result;
END //

DELIMITER ;

-- ============================================
-- Utility Views
-- ============================================

-- View: Active games summary
CREATE VIEW active_games_summary AS
SELECT 
    COUNT(*) AS totalGames,
    SUM(playerCount) AS totalPlayers,
    SUM(spectatorCount) AS totalSpectators,
    AVG(TIMESTAMPDIFF(MINUTE, startTime, NOW())) AS avgGameDuration
FROM active_sessions
WHERE status = 'active' 
  AND lastUpdate > DATE_SUB(NOW(), INTERVAL 5 MINUTE);

-- View: Recent sessions
CREATE VIEW recent_sessions AS
SELECT 
    sessionId,
    boardId,
    playerCount,
    spectatorCount,
    startTime,
    TIMESTAMPDIFF(SECOND, startTime, NOW()) AS duration,
    status,
    hostConnected
FROM active_sessions
WHERE lastUpdate > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY startTime DESC;

-- ============================================
-- Grant Permissions (adjust username as needed)
-- ============================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON lastdrop.active_sessions TO 'lastdrop_user'@'localhost';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON lastdrop.game_replays TO 'lastdrop_user'@'localhost';
-- GRANT EXECUTE ON PROCEDURE lastdrop.cleanup_inactive_sessions TO 'lastdrop_user'@'localhost';
-- FLUSH PRIVILEGES;

-- ============================================
-- Installation Instructions
-- ============================================

/*
To install this schema:

1. Connect to MySQL:
   mysql -u root -p

2. Create database (if not exists):
   CREATE DATABASE IF NOT EXISTS lastdrop;
   USE lastdrop;

3. Run this schema file:
   SOURCE /path/to/database_schema.sql;

4. Verify installation:
   SHOW TABLES;
   SELECT * FROM active_sessions;

5. Set up cron job for cleanup (add to crontab -e):
   * * * * * mysql -u root -p'password' lastdrop -e "CALL cleanup_inactive_sessions();"

6. Update PHP database credentials in:
   - api/active_games.php
   - api/session_info.php
   - api/live_push.php
*/
