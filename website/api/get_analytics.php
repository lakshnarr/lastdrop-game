<?php
/**
 * Get Game Analytics API
 * Phase 5.5: Analytics Dashboard
 * 
 * Returns comprehensive game statistics and trends
 * 
 * Input (GET):
 * ?period=weekly  (optional: daily, weekly, monthly, alltime)
 * ?limit=100      (optional: limit number of records)
 * 
 * Output:
 * {
 *   "success": true,
 *   "analytics": {
 *     "overview": {...},
 *     "diceDistribution": [...],
 *     "tileHeatmap": [...],
 *     "chanceCardStats": [...],
 *     "playerActivity": [...],
 *     "gameOutcomes": [...]
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
    
    // Get period filter
    $period = isset($_GET['period']) ? $_GET['period'] : 'weekly';
    $limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 1000;
    
    // Calculate date range
    $dateFilter = getDateFilter($period);
    
    // ===== OVERVIEW STATISTICS =====
    $overview = [];
    
    // Total games
    $stmt = $pdo->prepare("
        SELECT COUNT(DISTINCT sessionId) as totalGames
        FROM game_replays
        WHERE createdAt >= :dateFilter
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $overview['totalGames'] = (int)$stmt->fetch(PDO::FETCH_ASSOC)['totalGames'];
    
    // Total players (unique)
    $stmt = $pdo->prepare("
        SELECT COUNT(DISTINCT playerName) as uniquePlayers
        FROM player_stats
        WHERE lastPlayed >= :dateFilter
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $overview['uniquePlayers'] = (int)$stmt->fetch(PDO::FETCH_ASSOC)['uniquePlayers'];
    
    // Average game duration
    $stmt = $pdo->prepare("
        SELECT AVG(duration) as avgDuration, AVG(totalRolls) as avgRolls
        FROM game_replays
        WHERE createdAt >= :dateFilter AND duration > 0
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $durationData = $stmt->fetch(PDO::FETCH_ASSOC);
    $overview['avgGameDuration'] = round((float)$durationData['avgDuration'], 1);
    $overview['avgRollsPerGame'] = round((float)$durationData['avgRolls'], 1);
    
    // Most active player
    $stmt = $pdo->prepare("
        SELECT playerName, gamesPlayed
        FROM player_stats
        WHERE lastPlayed >= :dateFilter
        ORDER BY gamesPlayed DESC
        LIMIT 1
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $mostActive = $stmt->fetch(PDO::FETCH_ASSOC);
    $overview['mostActivePlayer'] = $mostActive ? $mostActive['playerName'] : null;
    $overview['mostActiveGames'] = $mostActive ? (int)$mostActive['gamesPlayed'] : 0;
    
    // ===== DICE DISTRIBUTION =====
    $diceDistribution = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT diceValue, COUNT(*) as count
            FROM analytics_events
            WHERE eventType = 'dice_roll' 
              AND timestamp >= :dateFilter
              AND diceValue IS NOT NULL
            GROUP BY diceValue
            ORDER BY diceValue
        ");
        $stmt->execute([':dateFilter' => $dateFilter]);
        $diceDistribution = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        // Calculate fairness (should be ~16.67% each for fair dice)
        $totalRolls = array_sum(array_column($diceDistribution, 'count'));
        foreach ($diceDistribution as &$dice) {
            $dice['count'] = (int)$dice['count'];
            $dice['percentage'] = $totalRolls > 0 ? round(($dice['count'] / $totalRolls) * 100, 2) : 0;
            $dice['expectedPercentage'] = 16.67;
            $dice['deviation'] = round($dice['percentage'] - 16.67, 2);
        }
    }
    
    // ===== TILE HEATMAP =====
    $tileHeatmap = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                tileId, 
                COUNT(*) as landedCount,
                COUNT(DISTINCT sessionId) as gamesWithTile
            FROM analytics_events
            WHERE eventType = 'tile_landed' 
              AND timestamp >= :dateFilter
              AND tileId IS NOT NULL
            GROUP BY tileId
            ORDER BY landedCount DESC
            LIMIT 20
        ");
        $stmt->execute([':dateFilter' => $dateFilter]);
        $tileHeatmap = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($tileHeatmap as &$tile) {
            $tile['tileId'] = (int)$tile['tileId'];
            $tile['landedCount'] = (int)$tile['landedCount'];
            $tile['gamesWithTile'] = (int)$tile['gamesWithTile'];
        }
    }
    
    // ===== CHANCE CARD STATISTICS =====
    $chanceCardStats = [];
    
    if (tableExists($pdo, 'analytics_events')) {
        $stmt = $pdo->prepare("
            SELECT 
                JSON_EXTRACT(eventData, '$.cardType') as cardType,
                COUNT(*) as drawnCount
            FROM analytics_events
            WHERE eventType = 'chance_card' 
              AND timestamp >= :dateFilter
            GROUP BY cardType
            ORDER BY drawnCount DESC
        ");
        $stmt->execute([':dateFilter' => $dateFilter]);
        $chanceCardStats = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($chanceCardStats as &$card) {
            $card['cardType'] = trim($card['cardType'], '"');
            $card['drawnCount'] = (int)$card['drawnCount'];
        }
    }
    
    // ===== PLAYER ACTIVITY TIMELINE =====
    $playerActivity = [];
    
    $stmt = $pdo->prepare("
        SELECT 
            DATE(createdAt) as date,
            COUNT(DISTINCT sessionId) as gamesPlayed,
            COUNT(DISTINCT JSON_EXTRACT(playerNames, '$[0]')) as uniquePlayers
        FROM game_replays
        WHERE createdAt >= :dateFilter
        GROUP BY DATE(createdAt)
        ORDER BY date DESC
        LIMIT 30
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $playerActivity = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($playerActivity as &$activity) {
        $activity['gamesPlayed'] = (int)$activity['gamesPlayed'];
        $activity['uniquePlayers'] = (int)$activity['uniquePlayers'];
    }
    
    // ===== GAME OUTCOMES =====
    $gameOutcomes = [];
    
    // Win distribution by player count
    $stmt = $pdo->prepare("
        SELECT 
            playerCount,
            COUNT(*) as totalGames,
            AVG(duration) as avgDuration,
            AVG(totalRolls) as avgRolls
        FROM game_replays
        WHERE createdAt >= :dateFilter
        GROUP BY playerCount
        ORDER BY playerCount
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $gameOutcomes = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($gameOutcomes as &$outcome) {
        $outcome['playerCount'] = (int)$outcome['playerCount'];
        $outcome['totalGames'] = (int)$outcome['totalGames'];
        $outcome['avgDuration'] = round((float)$outcome['avgDuration'], 1);
        $outcome['avgRolls'] = round((float)$outcome['avgRolls'], 1);
    }
    
    // ===== TOP PERFORMERS =====
    $topPerformers = [];
    
    $stmt = $pdo->prepare("
        SELECT 
            playerName,
            eloRating,
            gamesWon,
            winRate,
            currentStreak,
            highestScore
        FROM player_stats
        WHERE lastPlayed >= :dateFilter
        ORDER BY eloRating DESC
        LIMIT 10
    ");
    $stmt->execute([':dateFilter' => $dateFilter]);
    $topPerformers = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    foreach ($topPerformers as &$player) {
        $player['eloRating'] = (int)$player['eloRating'];
        $player['gamesWon'] = (int)$player['gamesWon'];
        $player['winRate'] = (float)$player['winRate'];
        $player['currentStreak'] = (int)$player['currentStreak'];
        $player['highestScore'] = (int)$player['highestScore'];
    }
    
    echo json_encode([
        'success' => true,
        'period' => $period,
        'dateFilter' => $dateFilter,
        'analytics' => [
            'overview' => $overview,
            'diceDistribution' => $diceDistribution,
            'tileHeatmap' => $tileHeatmap,
            'chanceCardStats' => $chanceCardStats,
            'playerActivity' => $playerActivity,
            'gameOutcomes' => $gameOutcomes,
            'topPerformers' => $topPerformers
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
 * Get date filter based on period
 */
function getDateFilter($period) {
    $now = new DateTime();
    
    switch ($period) {
        case 'daily':
            return $now->modify('-1 day')->format('Y-m-d H:i:s');
        case 'weekly':
            return $now->modify('-7 days')->format('Y-m-d H:i:s');
        case 'monthly':
            return $now->modify('-30 days')->format('Y-m-d H:i:s');
        case 'alltime':
            return '2020-01-01 00:00:00';
        default:
            return $now->modify('-7 days')->format('Y-m-d H:i:s');
    }
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
