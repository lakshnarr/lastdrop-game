<?php
/**
 * Live State API Endpoint
 * Returns the current game state for the 3D board visualization
 * 
 * Expected JSON structure:
 * {
 *   "players": [
 *     {"id": "P1", "name": "Player 1", "pos": 5, "eliminated": false},
 *     {"id": "P2", "name": "Player 2", "pos": 12, "eliminated": false}
 *   ],
 *   "lastEvent": {
 *     "playerId": "P1",
 *     "playerName": "Player 1",
 *     "dice1": 3,
 *     "dice2": 4,
 *     "avg": 3.5,
 *     "tileIndex": 5,
 *     "chanceCardId": "C1"
 *   },
 *   "chanceDescriptions": {
 *     "C1": "Rain bonus — gain an extra drop!",
 *     "C2": "Drought alert — move back 2 tiles."
 *   }
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

// Security check
if (!isset($_GET['key']) || $_GET['key'] !== 'ABC123') {
    http_response_code(403);
    echo json_encode(['error' => 'Invalid or missing API key']);
    exit;
}

// Get session ID from query parameter
$sessionId = isset($_GET['session']) ? trim($_GET['session']) : '';
if (empty($sessionId)) {
    http_response_code(400);
    echo json_encode(['error' => 'Session ID required']);
    exit;
}

$gameLogPath = dirname(__DIR__) . '/logs/game.log';
$gameStateDir = __DIR__ . '/../game_states/';
$gameStatePath = $gameStateDir . $sessionId . '.json';

// Chance card descriptions
$chanceDescriptions = [
    'C1' => 'Rain bonus — gain an extra drop!',
    'C2' => 'Drought alert — move back 2 tiles.',
    'C3' => 'River restoration — jump to the nearest water tile.',
    'C4' => 'Water tax — pay 2 drops to continue.',
    'C5' => 'Free drop — move forward 3 spaces.',
    'C6' => 'Pipeline leak — move back 1 tile.',
    'C7' => 'Double roll — roll again immediately.',
    'C8' => 'Conservation award — skip next turn penalty.'
];

// Initialize response
$response = [
    'players' => [],
    'lastEvent' => null,
    'chanceDescriptions' => $chanceDescriptions
];

// Check if game state file exists (maintained by Android app)
if (file_exists($gameStatePath)) {
    // Read game state from JSON file (preferred method)
    $stateContent = file_get_contents($gameStatePath);
    $state = json_decode($stateContent, true);
    
    if ($state && isset($state['players'])) {
        $response['players'] = $state['players'];
    }
    
    if ($state && isset($state['lastEvent'])) {
        $response['lastEvent'] = $state['lastEvent'];
    }
} else {
    // Fallback: reconstruct state from game.log
    if (file_exists($gameLogPath)) {
        $lines = file($gameLogPath, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        
        if ($lines && count($lines) > 0) {
            // Parse all events to build player positions
            $playerPositions = [];
            $lastEvent = null;
            
            foreach ($lines as $line) {
                $event = json_decode($line, true);
                if ($event && isset($event['player']) && isset($event['avg'])) {
                    $playerId = $event['player'];
                    $playerName = $event['player_name'] ?? $playerId;
                    $dice1 = $event['dice1'] ?? 0;
                    $dice2 = $event['dice2'] ?? 0;
                    $avg = $event['avg'];
                    
                    // Estimate position based on dice rolls (simplified)
                    // In real implementation, Android app should maintain actual positions
                    if (!isset($playerPositions[$playerId])) {
                        $playerPositions[$playerId] = [
                            'id' => $playerId,
                            'name' => $playerName,
                            'pos' => 1, // Start position
                            'eliminated' => false
                        ];
                    }
                    
                    // Move player forward (simplified - actual logic is on Android)
                    $movement = $dice1 + $dice2;
                    $playerPositions[$playerId]['pos'] = (($playerPositions[$playerId]['pos'] + $movement - 1) % 20) + 1;
                    
                    // Update last event
                    $lastEvent = [
                        'playerId' => $playerId,
                        'playerName' => $playerName,
                        'dice1' => $dice1,
                        'dice2' => $dice2,
                        'avg' => $avg,
                        'tileIndex' => $playerPositions[$playerId]['pos'],
                        'chanceCardId' => $event['chance_card'] ?? null
                    ];
                }
            }
            
            $response['players'] = array_values($playerPositions);
            $response['lastEvent'] = $lastEvent;
        }
    }
}

// If no players found, return empty state
if (empty($response['players'])) {
    $response['players'] = [];
}

echo json_encode($response, JSON_PRETTY_PRINT);
