<?php
/**
 * List Tournaments API
 * Phase 5.4: Tournament Mode
 * 
 * Lists all tournaments with optional status filter
 * 
 * Input (GET):
 * ?status=registration  (optional: registration, in_progress, completed)
 * 
 * Output:
 * {
 *   "success": true,
 *   "tournaments": [...]
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Database configuration
$host = 'localhost';
$dbname = 'lastdrop';
$username = 'root';
$password = '';

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname;charset=utf8mb4", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    
    // Build query based on filters
    $whereClause = '';
    $params = [];
    
    if (isset($_GET['status']) && !empty($_GET['status'])) {
        $validStatuses = ['registration', 'ready', 'in_progress', 'completed', 'cancelled'];
        if (in_array($_GET['status'], $validStatuses)) {
            $whereClause = 'WHERE status = :status';
            $params[':status'] = $_GET['status'];
        }
    }
    
    // Get tournaments
    $stmt = $pdo->prepare("
        SELECT 
            t.*,
            COUNT(DISTINCT tp.id) as participantCount
        FROM tournaments t
        LEFT JOIN tournament_participants tp ON t.id = tp.tournamentId
        $whereClause
        GROUP BY t.id
        ORDER BY 
            CASE t.status
                WHEN 'registration' THEN 1
                WHEN 'ready' THEN 2
                WHEN 'in_progress' THEN 3
                WHEN 'completed' THEN 4
                WHEN 'cancelled' THEN 5
            END,
            t.registrationStart DESC
    ");
    
    $stmt->execute($params);
    $tournaments = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Parse JSON fields and add computed properties
    foreach ($tournaments as &$tournament) {
        $tournament['bracketData'] = json_decode($tournament['bracketData'], true);
        $tournament['prizeDistribution'] = json_decode($tournament['prizeDistribution'], true);
        
        // Add registration status
        $now = new DateTime();
        $regEnd = new DateTime($tournament['registrationEnd']);
        $tournament['registrationOpen'] = ($tournament['status'] === 'registration' && $now <= $regEnd);
        
        // Add progress percentage
        if ($tournament['totalRounds'] > 0) {
            $tournament['progressPercent'] = round(($tournament['currentRound'] / $tournament['totalRounds']) * 100);
        } else {
            $tournament['progressPercent'] = 0;
        }
        
        // Add participant count
        $tournament['participantCount'] = (int)$tournament['participantCount'];
        $tournament['spotsRemaining'] = $tournament['maxParticipants'] - $tournament['currentParticipants'];
    }
    
    echo json_encode([
        'success' => true,
        'tournaments' => $tournaments,
        'count' => count($tournaments)
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}
