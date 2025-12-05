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
    
    echo json_encode([
        'success' => true,
        'replayId' => $replayId,
        'shareUrl' => $shareUrl
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to save replay: ' . $e->getMessage()]);
}
