<?php
/**
 * Get Player Profile API Endpoint
 * Phase 5.3: Leaderboards
 * 
 * Retrieves detailed statistics for a specific player
 * 
 * Request: GET /api/get_player_profile.php?playerName=Alice
 * 
 * Response: {
 *   "success": true,
 *   "player": {
 *     "playerName": "Alice",
 *     "rank": 1,
 *     "eloRating": 1250,
 *     "gamesPlayed": 45,
 *     "gamesWon": 32,
 *     "winRate": 71.11,
 *     "totalScore": 1733,
 *     "avgScore": 38.51,
 *     "highestScore": 92,
 *     "lowestScore": 12,
 *     "currentStreak": 5,
 *     "bestStreak": 12,
 *     "totalPlayTime": 45678,
 *     "avgGameDuration": 1015,
 *     "lastPlayed": "2025-12-05 14:30:00",
 *     "recentGames": [...]
 *   }
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['success' => false, 'error' => 'Method not allowed']);
    exit();
}

// Database configuration
$host = 'localhost';
$dbname = 'lastdrop_db';
$username = 'lastdrop_user';
$password = 'your_secure_password';

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Database connection failed']);
    exit();
}

// Get player name from query parameter
$playerName = $_GET['playerName'] ?? null;

if (!$playerName) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Missing playerName']);
    exit();
}

try {
    // Get player stats
    $stmt = $pdo->prepare("
        SELECT * FROM player_stats WHERE playerName = :playerName
    ");
    $stmt->execute([':playerName' => $playerName]);
    $player = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$player) {
        http_response_code(404);
        echo json_encode(['success' => false, 'error' => 'Player not found']);
        exit();
    }
    
    // Get recent games from replays
    $stmt = $pdo->prepare("
        SELECT 
            id,
            sessionId,
            duration,
            totalRolls,
            winner,
            finalScores,
            createdAt,
            shareUrl
        FROM game_replays
        WHERE JSON_SEARCH(playerNames, 'one', :playerName) IS NOT NULL
        ORDER BY createdAt DESC
        LIMIT 10
    ");
    $stmt->execute([':playerName' => $playerName]);
    $recentGames = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Parse JSON fields in recent games
    foreach ($recentGames as &$game) {
        $game['finalScores'] = json_decode($game['finalScores'], true);
        $game['isWinner'] = $game['winner'] === $playerName;
    }
    
    // Format player stats
    $player['winRate'] = round((float)$player['winRate'], 2);
    $player['avgScore'] = round((float)$player['avgScore'], 2);
    $player['recentGames'] = $recentGames;
    
    echo json_encode([
        'success' => true,
        'player' => $player
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to fetch player profile: ' . $e->getMessage()]);
}
