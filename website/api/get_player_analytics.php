<?php
/**
 * Get Player Analytics API
 * Phase 5.5: Analytics Dashboard
 * 
 * Returns detailed analytics for a specific player
 * 
 * Input (GET):
 * ?playerName=Alice
 * 
 * Output:
 * {
 *   "success": true,
 *   "playerAnalytics": {
 *     "profile": {...},
 *     "performance": {...},
 *     "trends": [...],
 *     "favoriteStrategies": {...}
 *   }
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Database configuration
$host = 'localhost';
$dbname = 'lastdrop';
$username = 'root';
$password = '';

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    
    if (!isset($_GET['playerName'])) {
        throw new Exception('Player name required');
    }
    
    $playerName = trim($_GET['playerName']);
    
    // ===== PLAYER PROFILE =====
    $stmt = $pdo->prepare("SELECT * FROM player_stats WHERE playerName = :playerName");
    $stmt->execute([':playerName' => $playerName]);
    $profile = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$profile) {
        throw new Exception('Player not found');
    }
    
    // Convert numeric fields
    $profile['gamesPlayed'] = (int)$profile['gamesPlayed'];
    $profile['gamesWon'] = (int)$profile['gamesWon'];
    $profile['totalScore'] = (int)$profile['totalScore'];
    $profile['highestScore'] = (int)$profile['highestScore'];
    $profile['lowestScore'] = (int)$profile['lowestScore'];
    $profile['avgScore'] = (float)$profile['avgScore'];
    $profile['totalPlayTime'] = (int)$profile['totalPlayTime'];
    $profile['avgGameDuration'] = (int)$profile['avgGameDuration'];
    $profile['winRate'] = (float)$profile['winRate'];
    $profile['currentStreak'] = (int)$profile['currentStreak'];
    $profile['bestStreak'] = (int)$profile['bestStreak'];
    $profile['eloRating'] = (int)$profile['eloRating'];
    $profile['rank'] = (int)$profile['rank'];
    
    // ===== PERFORMANCE OVER TIME =====
    $trends = [];
    
    // Get recent games from replays
    $stmt = $pdo->prepare("
        SELECT 
            createdAt,
            winner,
            winnerPlayerId,
            finalScores,
            duration,
            totalRolls,
            playerNames
        FROM game_replays
        WHERE JSON_SEARCH(playerNames, 'one', :playerName) IS NOT NULL
        ORDER BY createdAt DESC
        LIMIT 30
    ");
    $stmt->execute([':playerName' => $playerName]);
    $recentGames = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($recentGames as $game) {
        $playerNames = json_decode($game['playerNames'], true);
        $finalScores = json_decode($game['finalScores'], true);
        
        // Find player's score
        $playerIndex = array_search($playerName, $playerNames);
        $playerScore = $playerIndex !== false && isset($finalScores[$playerIndex]) ? $finalScores[$playerIndex] : 0;
        
        $trends[] = [
            'date' => $game['createdAt'],
            'won' => ($game['winner'] === $playerName),
            'score' => (int)$playerScore,
            'duration' => (int)$game['duration'],
            'rolls' => (int)$game['totalRolls']
        ];
    }
    
    // ===== DICE STATISTICS =====
    $diceStats = [
        'distribution' => [],
        'luckyNumber' => null,
        'unluckyNumber' => null
    ];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                diceValue,
                COUNT(*) as rollCount
            FROM analytics_events
            WHERE eventType = 'dice_roll'
              AND playerName = :playerName
              AND diceValue IS NOT NULL
            GROUP BY diceValue
            ORDER BY diceValue
        ");
        $stmt->execute([':playerName' => $playerName]);
        $diceDistribution = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        $maxRolls = 0;
        $minRolls = PHP_INT_MAX;
        
        foreach ($diceDistribution as $dice) {
            $value = (int)$dice['diceValue'];
            $count = (int)$dice['rollCount'];
            
            $diceStats['distribution'][$value] = $count;
            
            if ($count > $maxRolls) {
                $maxRolls = $count;
                $diceStats['luckyNumber'] = $value;
            }
            
            if ($count < $minRolls) {
                $minRolls = $count;
                $diceStats['unluckyNumber'] = $value;
            }
        }
    }
    
    // ===== TILE PREFERENCES =====
    $tilePreferences = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                tileId,
                COUNT(*) as visitCount
            FROM analytics_events
            WHERE eventType = 'tile_landed'
              AND playerName = :playerName
              AND tileId IS NOT NULL
            GROUP BY tileId
            ORDER BY visitCount DESC
            LIMIT 10
        ");
        $stmt->execute([':playerName' => $playerName]);
        $tilePreferences = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($tilePreferences as &$tile) {
            $tile['tileId'] = (int)$tile['tileId'];
            $tile['visitCount'] = (int)$tile['visitCount'];
        }
    }
    
    // ===== CHANCE CARD HISTORY =====
    $chanceCardHistory = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                JSON_EXTRACT(eventData, '$.cardType') as cardType,
                COUNT(*) as drawnCount
            FROM analytics_events
            WHERE eventType = 'chance_card'
              AND playerName = :playerName
            GROUP BY cardType
            ORDER BY drawnCount DESC
        ");
        $stmt->execute([':playerName' => $playerName]);
        $chanceCardHistory = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($chanceCardHistory as &$card) {
            $card['cardType'] = trim($card['cardType'], '"');
            $card['drawnCount'] = (int)$card['drawnCount'];
        }
    }
    
    // ===== WIN/LOSS PATTERNS =====
    $winLossPatterns = [
        'winsByPlayerCount' => [],
        'avgScoreWhenWinning' => 0,
        'avgScoreWhenLosing' => 0
    ];
    
    // Calculate wins by player count
    $stmt = $pdo->prepare("
        SELECT 
            playerCount,
            COUNT(*) as wins
        FROM game_replays
        WHERE winner = :playerName
        GROUP BY playerCount
        ORDER BY playerCount
    ");
    $stmt->execute([':playerName' => $playerName]);
    $winsByCount = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($winsByCount as $win) {
        $winLossPatterns['winsByPlayerCount'][(int)$win['playerCount']] = (int)$win['wins'];
    }
    
    // Calculate average scores
    $winningScores = [];
    $losingScores = [];
    
    foreach ($recentGames as $game) {
        $playerNames = json_decode($game['playerNames'], true);
        $finalScores = json_decode($game['finalScores'], true);
        $playerIndex = array_search($playerName, $playerNames);
        
        if ($playerIndex !== false && isset($finalScores[$playerIndex])) {
            $score = (int)$finalScores[$playerIndex];
            if ($game['winner'] === $playerName) {
                $winningScores[] = $score;
            } else {
                $losingScores[] = $score;
            }
        }
    }
    
    $winLossPatterns['avgScoreWhenWinning'] = count($winningScores) > 0 ? 
        round(array_sum($winningScores) / count($winningScores), 1) : 0;
    $winLossPatterns['avgScoreWhenLosing'] = count($losingScores) > 0 ? 
        round(array_sum($losingScores) / count($losingScores), 1) : 0;
    
    // ===== RECENT ACTIVITY =====
    $recentActivity = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                eventType,
                eventData,
                timestamp
            FROM analytics_events
            WHERE playerName = :playerName
            ORDER BY timestamp DESC
            LIMIT 50
        ");
        $stmt->execute([':playerName' => $playerName]);
        $activities = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($activities as $activity) {
            $recentActivity[] = [
                'type' => $activity['eventType'],
                'data' => json_decode($activity['eventData'], true),
                'timestamp' => $activity['timestamp']
            ];
        }
    }
    
    echo json_encode([
        'success' => true,
        'playerAnalytics' => [
            'profile' => $profile,
            'trends' => $trends,
            'diceStats' => $diceStats,
            'tilePreferences' => $tilePreferences,
            'chanceCardHistory' => $chanceCardHistory,
            'winLossPatterns' => $winLossPatterns,
            'recentActivity' => array_slice($recentActivity, 0, 20)
        ]
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

/**
 * Check if table exists
 */
function tableExists($pdo, $tableName) {
    try {
        $result = $pdo->query("SELECT 1 FROM $tableName LIMIT 1");
        return $result !== false;
    } catch (Exception $e) {
        return false;
    }
}
