<?php
/**
 * Get Replay API Endpoint
 * Phase 5.2: Game Replay System
 * 
 * Retrieves a specific game replay by ID
 * 
 * Request: GET /api/get_replay.php?id=123
 * 
 * Response: {
 *   "success": true,
 *   "replay": {
 *     "id": 123,
 *     "sessionId": "unique-session-id",
 *     "boardId": "LASTDROP-0001",
 *     "playerCount": 3,
 *     "playerNames": ["Alice", "Bob", "Charlie"],
 *     "playerColors": ["#FF0000", "#00FF00", "#0000FF"],
 *     "duration": 1234,
 *     "totalRolls": 56,
 *     "winner": "Alice",
 *     "winnerPlayerId": 0,
 *     "finalScores": [45, 32, 28],
 *     "endReason": "completed",
 *     "replayData": [...],
 *     "createdAt": "2025-12-05 10:30:00",
 *     "views": 42
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

// Get replay ID from query parameter
$replayId = $_GET['id'] ?? null;

if (!$replayId) {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'Missing replay ID']);
    exit();
}

try {
    // Fetch replay data
    $stmt = $pdo->prepare("
        SELECT 
            id, sessionId, boardId, playerCount, playerNames, playerColors,
            duration, totalRolls, winner, winnerPlayerId, finalScores,
            endReason, replayData, createdAt, views, featured, shareUrl
        FROM game_replays
        WHERE id = :id
    ");
    
    $stmt->execute([':id' => $replayId]);
    $replay = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$replay) {
        http_response_code(404);
        echo json_encode(['success' => false, 'error' => 'Replay not found']);
        exit();
    }
    
    // Increment view count
    $updateStmt = $pdo->prepare("UPDATE game_replays SET views = views + 1 WHERE id = :id");
    $updateStmt->execute([':id' => $replayId]);
    
    // Parse JSON fields
    $replay['playerNames'] = json_decode($replay['playerNames'], true);
    $replay['playerColors'] = json_decode($replay['playerColors'], true);
    $replay['finalScores'] = json_decode($replay['finalScores'], true);
    $replay['replayData'] = json_decode($replay['replayData'], true);
    $replay['featured'] = (bool)$replay['featured'];
    
    echo json_encode([
        'success' => true,
        'replay' => $replay
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to fetch replay: ' . $e->getMessage()]);
}
