<?php
/**
 * Check Session Status API
 * Returns whether a host app has connected to the session
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');

// API key validation
$apiKey = isset($_GET['key']) ? $_GET['key'] : '';
if ($apiKey !== 'ABC123') {
    http_response_code(403);
    echo json_encode(['error' => 'Invalid API key']);
    exit;
}

// Get session ID
$sessionId = isset($_GET['session']) ? trim($_GET['session']) : '';
if (empty($sessionId)) {
    http_response_code(400);
    echo json_encode(['error' => 'Session ID required']);
    exit;
}

// Database connection
$host = '127.0.0.1';
$dbname = 'lastdrop';
$username = 'lastdrop_user';
$password = 'Lastdrop1!db';

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    
    // Check if session exists and is connected
    $stmt = $pdo->prepare("
        SELECT 
            sessionId,
            hostConnected,
            status,
            lastUpdate,
            TIMESTAMPDIFF(SECOND, lastUpdate, NOW()) as secondsSinceUpdate
        FROM active_sessions 
        WHERE sessionId = :sessionId
        LIMIT 1
    ");
    
    $stmt->execute(['sessionId' => $sessionId]);
    $session = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if ($session) {
        // Session exists - check if it's recently active (within last 10 seconds)
        $isActive = ($session['secondsSinceUpdate'] < 10);
        $isConnected = ($session['hostConnected'] == 1 && $isActive);
        
        echo json_encode([
            'success' => true,
            'connected' => $isConnected,
            'hostConnected' => (int)$session['hostConnected'],
            'status' => $session['status'],
            'lastUpdate' => $session['lastUpdate'],
            'secondsSinceUpdate' => (int)$session['secondsSinceUpdate'],
            'isActive' => $isActive
        ]);
    } else {
        // Session doesn't exist yet
        echo json_encode([
            'success' => true,
            'connected' => false,
            'hostConnected' => 0,
            'status' => 'waiting',
            'message' => 'Session not found - waiting for app to connect'
        ]);
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'error' => 'Database error',
        'message' => $e->getMessage()
    ]);
}
?>
