<?php
/**
 * Get Leaderboard API Endpoint
 * Phase 5.3: Leaderboards
 * 
 * Retrieves leaderboard rankings for specified period
 * 
 * Request: GET /api/get_leaderboard.php?period=daily&limit=100
 * 
 * Periods: daily, weekly, monthly, alltime
 * 
 * Response: {
 *   "success": true,
 *   "period": "daily",
 *   "leaderboard": [
 *     {
 *       "rank": 1,
 *       "playerName": "Alice",
 *       "eloRating": 1250,
 *       "gamesPlayed": 45,
 *       "gamesWon": 32,
 *       "winRate": 71.11,
 *       "avgScore": 38.5,
 *       "currentStreak": 5
 *     },
 *     ...
 *   ]
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

// Parse query parameters
$period = $_GET['period'] ?? 'alltime';
$limit = min((int)($_GET['limit'] ?? 100), 1000);

// Validate period
$validPeriods = ['daily', 'weekly', 'monthly', 'alltime'];
if (!in_array($period, $validPeriods)) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid period']);
    exit();
}

try {
    if ($period === 'alltime') {
        // Get all-time leaderboard from player_stats
        $stmt = $pdo->prepare("
            SELECT 
                rank,
                playerName,
                eloRating,
                gamesPlayed,
                gamesWon,
                winRate,
                avgScore,
                highestScore,
                currentStreak,
                bestStreak,
                lastPlayed
            FROM player_stats
            WHERE gamesPlayed > 0
            ORDER BY eloRating DESC, gamesWon DESC, winRate DESC
            LIMIT :limit
        ");
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();
        $leaderboard = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
    } else {
        // Get period-specific leaderboard from leaderboard_entries
        // Determine period dates
        $now = new DateTime();
        switch ($period) {
            case 'daily':
                $periodStart = $now->format('Y-m-d');
                $periodEnd = $periodStart;
                break;
            case 'weekly':
                $periodStart = $now->modify('monday this week')->format('Y-m-d');
                $periodEnd = $now->modify('sunday this week')->format('Y-m-d');
                break;
            case 'monthly':
                $periodStart = $now->modify('first day of this month')->format('Y-m-d');
                $periodEnd = $now->modify('last day of this month')->format('Y-m-d');
                break;
        }
        
        $stmt = $pdo->prepare("
            SELECT 
                rank,
                playerName,
                eloRating,
                gamesPlayed,
                gamesWon,
                (gamesWon / gamesPlayed * 100) as winRate,
                avgScore,
                periodStart,
                periodEnd
            FROM leaderboard_entries
            WHERE period = :period 
              AND periodStart = :periodStart
            ORDER BY rank ASC
            LIMIT :limit
        ");
        $stmt->bindValue(':period', $period);
        $stmt->bindValue(':periodStart', $periodStart);
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();
        $leaderboard = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        // If no period data exists yet, fall back to current all-time stats
        if (empty($leaderboard)) {
            $stmt = $pdo->prepare("
                SELECT 
                    rank,
                    playerName,
                    eloRating,
                    gamesPlayed,
                    gamesWon,
                    winRate,
                    avgScore,
                    highestScore,
                    currentStreak
                FROM player_stats
                WHERE gamesPlayed > 0
                ORDER BY eloRating DESC, gamesWon DESC
                LIMIT :limit
            ");
            $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
            $stmt->execute();
            $leaderboard = $stmt->fetchAll(PDO::FETCH_ASSOC);
        }
    }
    
    // Format numeric fields
    foreach ($leaderboard as &$entry) {
        if (isset($entry['winRate'])) {
            $entry['winRate'] = round((float)$entry['winRate'], 2);
        }
        if (isset($entry['avgScore'])) {
            $entry['avgScore'] = round((float)$entry['avgScore'], 2);
        }
    }
    
    echo json_encode([
        'success' => true,
        'period' => $period,
        'count' => count($leaderboard),
        'leaderboard' => $leaderboard
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to fetch leaderboard: ' . $e->getMessage()]);
}
