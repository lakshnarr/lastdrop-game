<?php
/**
 * Last Drop - Live Push API
 * 
 * Receives game state updates from Android app and stores them
 * Also updates active_sessions table for session tracking
 * 
 * Endpoint: POST /api/live_push.php
 * 
 * Request Body:
 * {
 *   "apiKey": "ABC123",
 *   "sessionId": "abc-1234-xyz",
 *   "boardId": "LASTDROP-0001",
 *   "players": [...],
 *   "lastEvent": {...}
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "Game state updated",
 *   "sessionId": "abc-1234-xyz"
 * }
 */

// Headers
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
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
define('STATE_FILE_DIR', __DIR__ . '/../game_states/');

// Error logging
ini_set('display_errors', 0);
ini_set('log_errors', 1);

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

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendError('Only POST requests are allowed', 405);
}

// Get JSON input
$input = file_get_contents('php://input');
$data = json_decode($input, true);

if (json_last_error() !== JSON_ERROR_NONE) {
    sendError('Invalid JSON', 400);
}

// Verify API key
$apiKey = $data['apiKey'] ?? '';
if ($apiKey !== API_KEY) {
    error_log("Invalid API key attempt in live_push: " . $apiKey);
    sendError('Invalid API key', 401);
}

// Extract required fields
$sessionId = $data['sessionId'] ?? null;
$boardId = $data['boardId'] ?? null;
$players = $data['players'] ?? [];
$lastEvent = $data['lastEvent'] ?? null;

// Validate required fields
if (empty($sessionId)) {
    sendError('Session ID is required', 400);
}

// Validate session ID format
if (!preg_match('/^[a-zA-Z0-9\-]+$/', $sessionId)) {
    sendError('Invalid session ID format', 400);
}

// Create game states directory if it doesn't exist
if (!file_exists(STATE_FILE_DIR)) {
    mkdir(STATE_FILE_DIR, 0755, true);
}

// Save game state to file (existing functionality)
$stateFile = STATE_FILE_DIR . $sessionId . '.json';
$success = file_put_contents($stateFile, $input);

if ($success === false) {
    error_log("Failed to write game state file: " . $stateFile);
    sendError('Failed to save game state', 500);
}

// Connect to database for session tracking
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
    error_log("Database connection failed in live_push: " . $e->getMessage());
    // Don't fail the request - file is saved, DB is optional
    sendResponse([
        'success' => true,
        'message' => 'Game state saved (database unavailable)',
        'sessionId' => $sessionId,
        'warning' => 'Session tracking unavailable'
    ]);
}

// Update or insert session in active_sessions table
try {
    // Check if session exists
    $stmt = $pdo->prepare("SELECT id FROM active_sessions WHERE sessionId = :sessionId");
    $stmt->execute(['sessionId' => $sessionId]);
    $exists = $stmt->fetch();
    
    $playerCount = is_array($players) ? count($players) : 0;
    $gameStateJson = json_encode($data);
    
    if ($exists) {
        // Update existing session
        $stmt = $pdo->prepare("
            UPDATE active_sessions 
            SET 
                boardId = :boardId,
                playerCount = :playerCount,
                lastUpdate = NOW(),
                status = 'active',
                hostConnected = TRUE,
                gameState = :gameState
            WHERE sessionId = :sessionId
        ");
        
        $stmt->execute([
            'sessionId' => $sessionId,
            'boardId' => $boardId,
            'playerCount' => $playerCount,
            'gameState' => $gameStateJson
        ]);
        
    } else {
        // Insert new session
        $stmt = $pdo->prepare("
            INSERT INTO active_sessions 
            (sessionId, boardId, playerCount, spectatorCount, startTime, lastUpdate, status, hostConnected, gameState)
            VALUES 
            (:sessionId, :boardId, :playerCount, 0, NOW(), NOW(), 'active', TRUE, :gameState)
        ");
        
        $stmt->execute([
            'sessionId' => $sessionId,
            'boardId' => $boardId,
            'playerCount' => $playerCount,
            'gameState' => $gameStateJson
        ]);
    }
    
    // Return success response
    sendResponse([
        'success' => true,
        'message' => 'Game state updated',
        'sessionId' => $sessionId,
        'boardId' => $boardId,
        'playerCount' => $playerCount,
        'timestamp' => date('c')
    ]);
    
} catch (PDOException $e) {
    error_log("Database update failed in live_push: " . $e->getMessage());
    
    // Still return success since file is saved
    sendResponse([
        'success' => true,
        'message' => 'Game state saved (session tracking failed)',
        'sessionId' => $sessionId,
        'warning' => 'Database update failed'
    ]);
}
