<?php
/**
 * Last Drop - Live State API Endpoint
 * Returns current game state for session-specific or latest game
 * 
 * Query Parameters:
 * - key: API authentication key (required)
 * - boardId: Specific board identifier (optional)
 * - sessionId: Specific session UUID (optional)
 * 
 * Response Format:
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
header('Access-Control-Allow-Methods: GET');

// Validate API key
$apiKey = $_GET['key'] ?? '';
$validKey = 'ABC123';  // TODO: Move to environment variable

if ($apiKey !== $validKey) {
    http_response_code(401);
    echo json_encode(['error' => 'Invalid API key']);
    exit;
}

// Get session parameters
$boardId = $_GET['boardId'] ?? null;
$sessionId = $_GET['sessionId'] ?? null;

// Data directory for storing sessions
$dataDir = __DIR__ . '/../data/sessions';
if (!is_dir($dataDir)) {
    mkdir($dataDir, 0755, true);
}

// Determine which session file to read
$sessionFile = null;

if ($sessionId && $boardId) {
    // Specific session requested
    $sessionFile = "$dataDir/{$boardId}_{$sessionId}.json";
} elseif ($boardId) {
    // Latest session for specific board
    $pattern = "$dataDir/{$boardId}_*.json";
    $files = glob($pattern);
    if (!empty($files)) {
        // Sort by modification time, get most recent
        usort($files, function($a, $b) {
            return filemtime($b) - filemtime($a);
        });
        $sessionFile = $files[0];
    }
} else {
    // No board specified - return most recent session from any board
    $pattern = "$dataDir/*.json";
    $files = glob($pattern);
    if (!empty($files)) {
        usort($files, function($a, $b) {
            return filemtime($b) - filemtime($a);
        });
        $sessionFile = $files[0];
    }
}

// Read and return session data
if ($sessionFile && file_exists($sessionFile)) {
    $data = file_get_contents($sessionFile);
    $state = json_decode($data, true);
    
    // Add metadata if not present
    if (!isset($state['timestamp'])) {
        $state['timestamp'] = filemtime($sessionFile);
    }
    
    echo json_encode($state);
} else {
    // No data found - return empty state
    http_response_code(404);
    echo json_encode([
        'error' => 'No game data found',
        'players' => [],
        'lastEvent' => null,
        'boardId' => $boardId ?? 'unknown',
        'sessionId' => $sessionId ?? '',
        'timestamp' => time()
    ]);
}
?>
