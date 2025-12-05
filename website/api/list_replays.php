<?php
/**
 * List Replays API Endpoint
 * Phase 5.2: Game Replay System
 * 
 * Retrieves a list of game replays with filtering and pagination
 * 
 * Request: GET /api/list_replays.php?limit=10&offset=0&featured=1&sortBy=views
 * 
 * Query Parameters:
 * - limit: Number of results to return (default: 20, max: 100)
 * - offset: Number of results to skip (default: 0)
 * - featured: Only show featured replays (0 or 1)
 * - sortBy: Sort field (views, createdAt, duration, totalRolls)
 * - order: Sort order (ASC or DESC, default: DESC)
 * 
 * Response: {
 *   "success": true,
 *   "replays": [...],
 *   "total": 123,
 *   "limit": 10,
 *   "offset": 0
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
$limit = min((int)($_GET['limit'] ?? 20), 100);
$offset = max((int)($_GET['offset'] ?? 0), 0);
$featured = isset($_GET['featured']) ? (bool)$_GET['featured'] : null;
$sortBy = $_GET['sortBy'] ?? 'createdAt';
$order = strtoupper($_GET['order'] ?? 'DESC');

// Validate sortBy and order
$allowedSortFields = ['views', 'createdAt', 'duration', 'totalRolls'];
if (!in_array($sortBy, $allowedSortFields)) {
    $sortBy = 'createdAt';
}
if ($order !== 'ASC' && $order !== 'DESC') {
    $order = 'DESC';
}

try {
    // Build WHERE clause
    $where = [];
    $params = [];
    
    if ($featured !== null) {
        $where[] = 'featured = :featured';
        $params[':featured'] = $featured ? 1 : 0;
    }
    
    $whereClause = count($where) > 0 ? 'WHERE ' . implode(' AND ', $where) : '';
    
    // Get total count
    $countStmt = $pdo->prepare("SELECT COUNT(*) as total FROM game_replays $whereClause");
    $countStmt->execute($params);
    $total = $countStmt->fetch(PDO::FETCH_ASSOC)['total'];
    
    // Get replays (excluding full replayData for list view)
    $stmt = $pdo->prepare("
        SELECT 
            id, sessionId, boardId, playerCount, playerNames, playerColors,
            duration, totalRolls, winner, winnerPlayerId, finalScores,
            endReason, createdAt, views, featured, shareUrl
        FROM game_replays
        $whereClause
        ORDER BY $sortBy $order
        LIMIT :limit OFFSET :offset
    ");
    
    foreach ($params as $key => $value) {
        $stmt->bindValue($key, $value);
    }
    $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
    $stmt->bindValue(':offset', $offset, PDO::PARAM_INT);
    
    $stmt->execute();
    $replays = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Parse JSON fields
    foreach ($replays as &$replay) {
        $replay['playerNames'] = json_decode($replay['playerNames'], true);
        $replay['playerColors'] = json_decode($replay['playerColors'], true);
        $replay['finalScores'] = json_decode($replay['finalScores'], true);
        $replay['featured'] = (bool)$replay['featured'];
    }
    
    echo json_encode([
        'success' => true,
        'replays' => $replays,
        'total' => (int)$total,
        'limit' => $limit,
        'offset' => $offset
    ]);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'error' => 'Failed to fetch replays: ' . $e->getMessage()]);
}
