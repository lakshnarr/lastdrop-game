<?php
/**
 * Last Drop - Live Push API Endpoint
 * Receives game state updates from Android app
 * Stores session data separately by boardId + sessionId
 * 
 * POST Body Format:
 * {
 *   "players": [...],
 *   "lastEvent": {...},
 *   "boardId": "LASTDROP-0001",
 *   "sessionId": "uuid-here",
 *   "timestamp": 1234567890
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Validate API key
$apiKey = $_GET['key'] ?? '';
$validKey = 'ABC123';  // TODO: Move to environment variable

if ($apiKey !== $validKey) {
    http_response_code(401);
    echo json_encode(['error' => 'Invalid API key']);
    exit;
}

// Validate request method
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['error' => 'Method not allowed']);
    exit;
}

// Read POST body
$body = file_get_contents('php://input');
$data = json_decode($body, true);

if (!$data) {
    http_response_code(400);
    echo json_encode(['error' => 'Invalid JSON']);
    exit;
}

// Extract session metadata
$boardId = $data['boardId'] ?? 'unknown';
$sessionId = $data['sessionId'] ?? '';
$timestamp = $data['timestamp'] ?? time();

// Validate required fields
if (!isset($data['players']) || !is_array($data['players'])) {
    http_response_code(400);
    echo json_encode(['error' => 'Missing players array']);
    exit;
}

// Data directory for storing sessions
$dataDir = __DIR__ . '/../data/sessions';
if (!is_dir($dataDir)) {
    mkdir($dataDir, 0755, true);
}

// Determine session file name
if ($sessionId) {
    $sessionFile = "$dataDir/{$boardId}_{$sessionId}.json";
} else {
    // No session ID - use timestamp-based filename
    $sessionFile = "$dataDir/{$boardId}_" . time() . ".json";
}

// Write session data
$written = file_put_contents($sessionFile, json_encode($data, JSON_PRETTY_PRINT));

if ($written === false) {
    http_response_code(500);
    echo json_encode(['error' => 'Failed to write session data']);
    exit;
}

// Success response
echo json_encode([
    'success' => true,
    'boardId' => $boardId,
    'sessionId' => $sessionId,
    'sessionFile' => basename($sessionFile),
    'bytesWritten' => $written,
    'timestamp' => $timestamp
]);

// Optional: Clean up old sessions (older than 24 hours)
$cleanupThreshold = time() - (24 * 60 * 60);
$allFiles = glob("$dataDir/*.json");
foreach ($allFiles as $file) {
    if (filemtime($file) < $cleanupThreshold) {
        unlink($file);
    }
}
?>
