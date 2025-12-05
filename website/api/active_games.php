<?php
/**
 * Last Drop - Active Games API
 * 
 * Returns list of all active game sessions for spectate mode
 * 
 * Endpoint: GET /api/active_games.php?key=ABC123
 * 
 * Response:
 * {
 *   "success": true,
 *   "count": 3,
 *   "totalGames": 3,
 *   "totalPlayers": 9,
 *   "totalSpectators": 15,
 *   "games": [...]
 * }
 */

// Headers
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Configuration
define('API_KEY', 'ABC123');
define('DB_HOST', 'localhost');
define('DB_NAME', 'lastdrop');
define('DB_USER', 'lastdrop_user');
define('DB_PASS', 'your_password_here'); // Change in production

// Error logging
ini_set('display_errors', 0);
ini_set('log_errors', 1);
error_log("Active Games API called at " . date('Y-m-d H:i:s'));

/**
 * Send JSON response and exit
 */
function sendResponse($data, $httpCode = 200) {
    http_response_code($httpCode);
    echo json_encode($data, JSON_PRETTY_PRINT);
    exit;
}

/**
 * Send error response
 */
function sendError($message, $httpCode = 400) {
    sendResponse([
        'success' => false,
        'error' => $message
    ], $httpCode);
}

// Verify API key
$apiKey = $_GET['key'] ?? '';
if ($apiKey !== API_KEY) {
    error_log("Invalid API key attempt: " . $apiKey);
    sendError('Invalid API key', 401);
}

// Connect to database
try {
    $pdo = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        ]
    );
} catch (PDOException $e) {
    error_log("Database connection failed: " . $e->getMessage());
    sendError('Database connection failed', 500);
}

// Get active games (updated in last 5 minutes)
try {
    $stmt = $pdo->prepare("
        SELECT 
            sessionId,
            boardId,
            playerCount,
            spectatorCount,
            startTime,
            TIMESTAMPDIFF(SECOND, startTime, NOW()) AS duration,
            status,
            hostConnected,
            lastUpdate
        FROM active_sessions
        WHERE lastUpdate > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
          AND status IN ('waiting', 'active')
        ORDER BY startTime DESC
    ");
    
    $stmt->execute();
    $games = $stmt->fetchAll();
    
    // Calculate totals
    $totalGames = count($games);
    $totalPlayers = 0;
    $totalSpectators = 0;
    
    foreach ($games as &$game) {
        $totalPlayers += (int)$game['playerCount'];
        $totalSpectators += (int)$game['spectatorCount'];
        
        // Format duration as MM:SS
        $minutes = floor($game['duration'] / 60);
        $seconds = $game['duration'] % 60;
        $game['duration'] = sprintf('%02d:%02d', $minutes, $seconds);
        
        // Convert boolean
        $game['hostConnected'] = (bool)$game['hostConnected'];
        
        // Remove unnecessary fields
        unset($game['lastUpdate']);
    }
    
    // Return response
    sendResponse([
        'success' => true,
        'count' => $totalGames,
        'totalGames' => $totalGames,
        'totalPlayers' => $totalPlayers,
        'totalSpectators' => $totalSpectators,
        'games' => $games,
        'timestamp' => date('c')
    ]);
    
} catch (PDOException $e) {
    error_log("Query failed: " . $e->getMessage());
    sendError('Failed to fetch games', 500);
}
