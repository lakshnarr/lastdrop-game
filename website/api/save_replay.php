<?php
/**
 * Save Replay API Endpoint
 * Phase 5.2: Game Replay System
 * 
 * Saves a completed game replay to the database
 * 
 * Request: POST
 * Body: {
 *   "sessionId": "unique-session-id",
 *   "boardId": "LASTDROP-0001",
 *   "playerCount": 3,
 *   "playerNames": ["Alice", "Bob", "Charlie"],
 *   "playerColors": ["#FF0000", "#00FF00", "#0000FF"],
 *   "duration": 1234,
 *   "totalRolls": 56,
 *   "winner": "Alice",
 *   "winnerPlayerId": 0,
 *   "finalScores": [45, 32, 28],
 *   "endReason": "completed",
 *   "replayData": [...array of game events...]
 * }
 * 
 * Response: {
 *   "success": true,
 *   "replayId": 123,
 *   "shareUrl": "https://lastdrop.earth/replay.html?id=123"
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

if (!$data) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Invalid JSON']);
    exit();
}

// Validate required fields
$required = ['sessionId', 'playerCount', 'playerNames', 'playerColors', 'duration', 'replayData'];
foreach ($required as $field) {
    if (!isset($data[$field])) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => "Missing required field: $field"]);
        exit();
    }
}

// Insert replay into database
try {
    $stmt = $pdo->prepare("
        INSERT INTO game_replays (
            sessionId, boardId, playerCount, playerNames, playerColors,
            duration, totalRolls, winner, winnerPlayerId, finalScores,
            endReason, replayData, createdAt
        ) VALUES (
            :sessionId, :boardId, :playerCount, :playerNames, :playerColors,
            :duration, :totalRolls, :winner, :winnerPlayerId, :finalScores,
            :endReason, :replayData, NOW()
        )
    ");
    
    $stmt->execute([
        ':sessionId' => $data['sessionId'],
        ':boardId' => $data['boardId'] ?? null,
        ':playerCount' => $data['playerCount'],
        ':playerNames' => json_encode($data['playerNames']),
        ':playerColors' => json_encode($data['playerColors']),
        ':duration' => $data['duration'],
        ':totalRolls' => $data['totalRolls'] ?? 0,
        ':winner' => $data['winner'] ?? null,
        ':winnerPlayerId' => $data['winnerPlayerId'] ?? null,
        ':finalScores' => isset($data['finalScores']) ? json_encode($data['finalScores']) : null,
        ':endReason' => $data['endReason'] ?? 'completed',
        ':replayData' => json_encode($data['replayData'])
    ]);
    
    $replayId = $pdo->lastInsertId();
    $shareUrl = "https://lastdrop.earth/replay.html?id=$replayId";
    
    // Update share URL in database
    $updateStmt = $pdo->prepare("UPDATE game_replays SET shareUrl = :shareUrl WHERE id = :id");
    $updateStmt->execute([':shareUrl' => $shareUrl, ':id' => $replayId]);
    
    // Update player stats for leaderboard (Phase 5.3)
    updatePlayerStats($pdo, $data);
    
    echo json_encode([
        'success' => true,
        'replayId' => $replayId,
        'shareUrl' => $shareUrl
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to save replay: ' . $e->getMessage()]);
}

/**
 * Update player statistics for leaderboard
 */
function updatePlayerStats($pdo, $gameData) {
    $playerNames = $gameData['playerNames'];
    $finalScores = $gameData['finalScores'];
    $winner = $gameData['winner'];
    $duration = $gameData['duration'];
    
    // Calculate average opponent ELO for each player
    $avgOpponentElo = 1000;  // Default for first games
    
    try {
        $stmt = $pdo->prepare("SELECT AVG(eloRating) as avgElo FROM player_stats WHERE playerName IN (" . 
            str_repeat('?,', count($playerNames) - 1) . "?)");
        $stmt->execute($playerNames);
        $result = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($result && $result['avgElo']) {
            $avgOpponentElo = (int)$result['avgElo'];
        }
    } catch (PDOException $e) {
        // Ignore errors, use default
    }
    
    // Update stats for each player
    foreach ($playerNames as $idx => $playerName) {
        $score = $finalScores[$idx] ?? 0;
        $gameResult = ($playerName === $winner) ? 'win' : 'loss';
        
        try {
            // Call update_player_stats logic inline
            $stmt = $pdo->prepare("SELECT * FROM player_stats WHERE playerName = :playerName");
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
            $totalPlayTime = $player['totalPlayTime'] + $duration;
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
            
            // Calculate new ELO
            $currentElo = $player['eloRating'];
            $expectedScore = 1 / (1 + pow(10, ($avgOpponentElo - $currentElo) / 400));
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
            
        } catch (PDOException $e) {
            // Log error but don't fail the replay save
            error_log("Failed to update stats for $playerName: " . $e->getMessage());
        }
    }
    
    // Recalculate ranks
    try {
        $pdo->exec("
            SET @rank = 0;
            UPDATE player_stats
            SET rank = (@rank := @rank + 1)
            ORDER BY eloRating DESC, gamesWon DESC, winRate DESC
        ");
    } catch (PDOException $e) {
        error_log("Failed to recalculate ranks: " . $e->getMessage());
    }
}

