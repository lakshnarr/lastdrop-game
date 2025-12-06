<?php
/**
 * Last Drop - Session Heartbeat API
 * 
 * Updates session activity timestamp to keep it counted as "active"
 * Server counts sessions with heartbeat in last 5 minutes as active games
 * 
 * Endpoint: GET /api/heartbeat.php?key=ABC123&session=SESSION_ID
 * 
 * Response:
 * {
 *   "success": true,
 *   "sessionId": "abc-123",
 *   "timestamp": "2025-12-05T10:30:00+00:00"
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
define('DB_HOST', '127.0.0.1');
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
    echo json_encode($data);
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
    error_log("Heartbeat: Invalid API key attempt");
    sendError('Invalid API key', 401);
}

// Get session ID
$sessionId = $_GET['session'] ?? '';
if (empty($sessionId)) {
    sendError('Session ID required', 400);
}

// Log heartbeat to file (temporary until database is configured)
$logFile = __DIR__ . '/../heartbeat.log';
$logEntry = date('Y-m-d H:i:s') . " - Session: $sessionId\n";
file_put_contents($logFile, $logEntry, FILE_APPEND);

// Return success immediately (database update will be added later)
sendResponse([
    'success' => true,
    'sessionId' => $sessionId,
    'timestamp' => date('c'),
    'message' => 'Heartbeat received (database integration pending)'
]);
