<?php
/**
 * Get Tournament Details API
 * Phase 5.4: Tournament Mode
 * 
 * Retrieves complete tournament information including participants and matches
 * 
 * Input (GET):
 * ?tournamentId=1
 * 
 * Output:
 * {
 *   "success": true,
 *   "tournament": {...},
 *   "participants": [...],
 *   "matches": [...]
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

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    http_response_code(405);
    echo json_encode(['success' => false, 'error' => 'Method not allowed']);
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
    
    if (!isset($_GET['tournamentId'])) {
        throw new Exception('Tournament ID required');
    }
    
    $tournamentId = (int)$_GET['tournamentId'];
    
    // Get tournament details
    $stmt = $pdo->prepare("SELECT * FROM tournaments WHERE id = :tournamentId");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $tournament = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$tournament) {
        throw new Exception('Tournament not found');
    }
    
    // Parse JSON fields
    $tournament['bracketData'] = json_decode($tournament['bracketData'], true);
    $tournament['prizeDistribution'] = json_decode($tournament['prizeDistribution'], true);
    
    // Get participants
    $stmt = $pdo->prepare("
        SELECT * FROM tournament_participants 
        WHERE tournamentId = :tournamentId
        ORDER BY seedPosition ASC, eloAtEntry DESC, registeredAt ASC
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $participants = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Get matches
    $stmt = $pdo->prepare("
        SELECT 
            m.*,
            r.playerNames as replayPlayerNames,
            r.finalScores as replayScores
        FROM tournament_matches m
        LEFT JOIN game_replays r ON m.replayId = r.id
        WHERE m.tournamentId = :tournamentId
        ORDER BY m.roundNumber ASC, m.matchNumber ASC
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $matches = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Parse JSON fields in matches
    foreach ($matches as &$match) {
        $match['finalScores'] = json_decode($match['finalScores'], true);
        $match['replayPlayerNames'] = json_decode($match['replayPlayerNames'], true);
        $match['replayScores'] = json_decode($match['replayScores'], true);
    }
    
    echo json_encode([
        'success' => true,
        'tournament' => $tournament,
        'participants' => $participants,
        'matches' => $matches
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}
