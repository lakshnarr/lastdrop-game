<?php
/**
 * Last Drop - Session Info API
 * 
 * Returns details about a specific game session
 * 
 * Endpoint: GET /api/session_info.php?key=ABC123&session=abc-1234
 * 
 * Response:
 * {
 *   "success": true,
 *   "session": {
 *     "sessionId": "abc-1234-xyz",
 *     "boardId": "LASTDROP-0001",
 *     "hostConnected": true,
 *     "playerCount": 3,
 *     "spectatorCount": 5,
 *     "duration": "12:34",
 *     "status": "active",
 *     "gameState": {...}
 *   }
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

// Get session ID
$sessionId = $_GET['session'] ?? '';
if (empty($sessionId)) {
    sendError('Session ID is required', 400);
}

// Validate session ID format (basic validation)
if (!preg_match('/^[a-zA-Z0-9\-]+$/', $sessionId)) {
    sendError('Invalid session ID format', 400);
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

// Fetch session details
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
            gameState
        FROM active_sessions
        WHERE sessionId = :sessionId
        LIMIT 1
    ");
    
    $stmt->execute(['sessionId' => $sessionId]);
    $session = $stmt->fetch();
    
    // Check if session exists
    if (!$session) {
        sendError('Session not found', 404);
    }
    
    // Check if session is still active (updated in last 5 minutes)
    $stmt2 = $pdo->prepare("
        SELECT lastUpdate 
        FROM active_sessions 
        WHERE sessionId = :sessionId
    ");
    $stmt2->execute(['sessionId' => $sessionId]);
    $updateInfo = $stmt2->fetch();
    
    $lastUpdateTime = strtotime($updateInfo['lastUpdate']);
    $fiveMinutesAgo = time() - (5 * 60);
    
    if ($lastUpdateTime < $fiveMinutesAgo) {
        $session['status'] = 'inactive';
        $session['warning'] = 'Session has not been updated in over 5 minutes';
    }
    
    // Format duration as MM:SS
    $minutes = floor($session['duration'] / 60);
    $seconds = $session['duration'] % 60;
    $session['duration'] = sprintf('%02d:%02d', $minutes, $seconds);
    
    // Convert boolean
    $session['hostConnected'] = (bool)$session['hostConnected'];
    
    // Parse game state JSON
    if (!empty($session['gameState'])) {
        $session['gameState'] = json_decode($session['gameState'], true);
    } else {
        $session['gameState'] = null;
    }
    
    // Increment spectator count (optional feature)
    // Uncomment to track spectators in real-time
    /*
    $stmt3 = $pdo->prepare("
        UPDATE active_sessions 
        SET spectatorCount = spectatorCount + 1 
        WHERE sessionId = :sessionId
    ");
    $stmt3->execute(['sessionId' => $sessionId]);
    */
    
    // Return response
    sendResponse([
        'success' => true,
        'session' => $session,
        'timestamp' => date('c')
    ]);
    
} catch (PDOException $e) {
    error_log("Query failed: " . $e->getMessage());
    sendError('Failed to fetch session info', 500);
}
