<?php
/**
 * Last Drop - Session List API
 * Returns list of active sessions for discovery/debugging
 * 
 * Query Parameters:
 * - key: API authentication key (required)
 * - boardId: Filter by specific board (optional)
 * 
 * Response Format:
 * {
 *   "sessions": [
 *     {
 *       "boardId": "LASTDROP-0001",
 *       "sessionId": "uuid-here",
 *       "playerCount": 3,
 *       "lastUpdate": 1234567890,
 *       "fileName": "LASTDROP-0001_uuid.json"
 *     }
 *   ],
 *   "totalSessions": 5
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

$boardId = $_GET['boardId'] ?? null;

// Data directory
$dataDir = __DIR__ . '/../data/sessions';
if (!is_dir($dataDir)) {
    echo json_encode(['sessions' => [], 'totalSessions' => 0]);
    exit;
}

// Get session files
$pattern = $boardId ? "$dataDir/{$boardId}_*.json" : "$dataDir/*.json";
$files = glob($pattern);

$sessions = [];
foreach ($files as $file) {
    $fileName = basename($file);
    
    // Parse filename: BOARDID_SESSIONID.json
    if (preg_match('/^(.+?)_(.+?)\.json$/', $fileName, $matches)) {
        $fileBoardId = $matches[1];
        $fileSessionId = $matches[2];
        
        // Read file to get player count
        $data = json_decode(file_get_contents($file), true);
        $playerCount = isset($data['players']) ? count($data['players']) : 0;
        
        $sessions[] = [
            'boardId' => $fileBoardId,
            'sessionId' => $fileSessionId,
            'playerCount' => $playerCount,
            'lastUpdate' => filemtime($file),
            'lastUpdateFormatted' => date('Y-m-d H:i:s', filemtime($file)),
            'fileName' => $fileName,
            'url' => "/live.html?session={$fileBoardId}_{$fileSessionId}"
        ];
    }
}

// Sort by last update (most recent first)
usort($sessions, function($a, $b) {
    return $b['lastUpdate'] - $a['lastUpdate'];
});

echo json_encode([
    'sessions' => $sessions,
    'totalSessions' => count($sessions),
    'filterBoardId' => $boardId
]);
?>
