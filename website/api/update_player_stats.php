<?php
/**
 * Update Player Stats API Endpoint
 * Phase 5.3: Leaderboards
 * 
 * Updates player statistics after a game ends
 * 
 * Request: POST
 * Body: {
 *   "playerName": "Alice",
 *   "gameResult": "win|loss",
 *   "score": 45,
 *   "gameDuration": 1234,
 *   "opponentElo": 1050
 * }
 * 
 * Response: {
 *   "success": true,
 *   "newElo": 1025,
 *   "newRank": 15
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
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

// Parse request body
$input = file_get_contents('php://input');
$data = json_decode($input, true);

if (!$data || !isset($data['playerName']) || !isset($data['gameResult']) || !isset($data['score'])) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Missing required fields']);
    exit();
}

$playerName = $data['playerName'];
$gameResult = $data['gameResult'];  // 'win' or 'loss'
$score = (int)$data['score'];
$gameDuration = isset($data['gameDuration']) ? (int)$data['gameDuration'] : 0;
$opponentElo = isset($data['opponentElo']) ? (int)$data['opponentElo'] : 1000;

try {
    // Get or create player stats
    $stmt = $pdo->prepare("
        SELECT * FROM player_stats WHERE playerName = :playerName
    ");
    $stmt->execute([':playerName' => $playerName]);
    $player = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$player) {
        // Create new player
        $stmt = $pdo->prepare("
            INSERT INTO player_stats (
                playerName, gamesPlayed, gamesWon, totalScore, highestScore, 
                lowestScore, totalPlayTime, eloRating, createdAt, updatedAt, lastPlayed
            ) VALUES (
                :playerName, 0, 0, 0, 0, 9999, 0, 1000, NOW(), NOW(), NOW()
            )
        ");
        $stmt->execute([':playerName' => $playerName]);
        
        // Fetch the newly created player
        $stmt = $pdo->prepare("SELECT * FROM player_stats WHERE playerName = :playerName");
        $stmt->execute([':playerName' => $playerName]);
        $player = $stmt->fetch(PDO::FETCH_ASSOC);
    }
    
    // Calculate new stats
    $gamesPlayed = $player['gamesPlayed'] + 1;
    $gamesWon = $gameResult === 'win' ? $player['gamesWon'] + 1 : $player['gamesWon'];
    $totalScore = $player['totalScore'] + $score;
    $highestScore = max($player['highestScore'], $score);
    $lowestScore = min($player['lowestScore'], $score);
    $avgScore = $totalScore / $gamesPlayed;
    $totalPlayTime = $player['totalPlayTime'] + $gameDuration;
    $avgGameDuration = $totalPlayTime / $gamesPlayed;
    $winRate = ($gamesWon / $gamesPlayed) * 100;
    
    // Update streak
    if ($gameResult === 'win') {
        $currentStreak = $player['currentStreak'] + 1;
        $bestStreak = max($player['bestStreak'], $currentStreak);
    } else {
        $currentStreak = 0;
        $bestStreak = $player['bestStreak'];
    }
    
    // Calculate new ELO rating (simplified K=32)
    $currentElo = $player['eloRating'];
    $expectedScore = 1 / (1 + pow(10, ($opponentElo - $currentElo) / 400));
    $actualScore = $gameResult === 'win' ? 1 : 0;
    $newElo = round($currentElo + 32 * ($actualScore - $expectedScore));
    
    // Update player stats
    $stmt = $pdo->prepare("
        UPDATE player_stats SET
            gamesPlayed = :gamesPlayed,
            gamesWon = :gamesWon,
            totalScore = :totalScore,
            highestScore = :highestScore,
            lowestScore = :lowestScore,
            avgScore = :avgScore,
            totalPlayTime = :totalPlayTime,
            avgGameDuration = :avgGameDuration,
            winRate = :winRate,
            currentStreak = :currentStreak,
            bestStreak = :bestStreak,
            eloRating = :eloRating,
            lastPlayed = NOW(),
            updatedAt = NOW()
        WHERE playerName = :playerName
    ");
    
    $stmt->execute([
        ':gamesPlayed' => $gamesPlayed,
        ':gamesWon' => $gamesWon,
        ':totalScore' => $totalScore,
        ':highestScore' => $highestScore,
        ':lowestScore' => $lowestScore,
        ':avgScore' => $avgScore,
        ':totalPlayTime' => $totalPlayTime,
        ':avgGameDuration' => $avgGameDuration,
        ':winRate' => $winRate,
        ':currentStreak' => $currentStreak,
        ':bestStreak' => $bestStreak,
        ':eloRating' => $newElo,
        ':playerName' => $playerName
    ]);
    
    // Recalculate ranks for all players
    recalculateRanks($pdo);
    
    // Get new rank
    $stmt = $pdo->prepare("SELECT rank FROM player_stats WHERE playerName = :playerName");
    $stmt->execute([':playerName' => $playerName]);
    $newRank = $stmt->fetchColumn();
    
    echo json_encode([
        'success' => true,
        'newElo' => $newElo,
        'newRank' => $newRank,
        'gamesPlayed' => $gamesPlayed,
        'winRate' => round($winRate, 2)
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to update stats: ' . $e->getMessage()]);
}

function recalculateRanks($pdo) {
    // Assign ranks based on ELO rating (highest first)
    $pdo->exec("
        SET @rank = 0;
        UPDATE player_stats
        SET rank = (@rank := @rank + 1)
        ORDER BY eloRating DESC, gamesWon DESC, winRate DESC
    ");
}
