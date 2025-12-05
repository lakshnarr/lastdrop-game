<?php
/**
 * Register for Tournament API
 * Phase 5.4: Tournament Mode
 * 
 * Registers a player for a tournament
 * 
 * Input (POST JSON):
 * {
 *   "tournamentId": 1,
 *   "playerName": "Alice"
 * }
 * 
 * Output:
 * {
 *   "success": true,
 *   "message": "Successfully registered for tournament"
 * }
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
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
    
    // Get JSON input
    $input = json_decode(file_get_contents('php://input'), true);
    
    if (!$input || !isset($input['tournamentId']) || !isset($input['playerName'])) {
        throw new Exception('Missing required fields');
    }
    
    $tournamentId = (int)$input['tournamentId'];
    $playerName = trim($input['playerName']);
    
    if (empty($playerName)) {
        throw new Exception('Player name cannot be empty');
    }
    
    // Get tournament details
    $stmt = $pdo->prepare("
        SELECT * FROM tournaments 
        WHERE id = :tournamentId
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $tournament = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$tournament) {
        throw new Exception('Tournament not found');
    }
    
    // Check tournament status
    if ($tournament['status'] !== 'registration') {
        throw new Exception('Tournament registration is closed');
    }
    
    // Check registration deadline
    $now = new DateTime();
    $regEnd = new DateTime($tournament['registrationEnd']);
    if ($now > $regEnd) {
        throw new Exception('Registration deadline has passed');
    }
    
    // Check if tournament is full
    if ($tournament['currentParticipants'] >= $tournament['maxParticipants']) {
        throw new Exception('Tournament is full');
    }
    
    // Get player stats for ELO check
    $stmt = $pdo->prepare("SELECT eloRating FROM player_stats WHERE playerName = :playerName");
    $stmt->execute([':playerName' => $playerName]);
    $playerStats = $stmt->fetch(PDO::FETCH_ASSOC);
    
    $eloAtEntry = $playerStats ? (int)$playerStats['eloRating'] : 1000;
    
    // Check ELO requirement
    if ($tournament['minEloRating'] > 0 && $eloAtEntry < $tournament['minEloRating']) {
        throw new Exception("ELO rating too low. Minimum: {$tournament['minEloRating']}, Your ELO: $eloAtEntry");
    }
    
    // Check if already registered
    $stmt = $pdo->prepare("
        SELECT id FROM tournament_participants 
        WHERE tournamentId = :tournamentId AND playerName = :playerName
    ");
    $stmt->execute([
        ':tournamentId' => $tournamentId,
        ':playerName' => $playerName
    ]);
    
    if ($stmt->fetch()) {
        throw new Exception('Player already registered for this tournament');
    }
    
    // Insert participant
    $stmt = $pdo->prepare("
        INSERT INTO tournament_participants (
            tournamentId, playerName, eloAtEntry, status, registeredAt
        ) VALUES (
            :tournamentId, :playerName, :eloAtEntry, 'registered', NOW()
        )
    ");
    
    $stmt->execute([
        ':tournamentId' => $tournamentId,
        ':playerName' => $playerName,
        ':eloAtEntry' => $eloAtEntry
    ]);
    
    // Update tournament participant count
    $stmt = $pdo->prepare("
        UPDATE tournaments 
        SET currentParticipants = currentParticipants + 1,
            updatedAt = NOW()
        WHERE id = :tournamentId
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    
    // Get updated participant count
    $stmt = $pdo->prepare("SELECT currentParticipants, maxParticipants FROM tournaments WHERE id = :tournamentId");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $updated = $stmt->fetch(PDO::FETCH_ASSOC);
    
    echo json_encode([
        'success' => true,
        'message' => 'Successfully registered for tournament',
        'currentParticipants' => (int)$updated['currentParticipants'],
        'maxParticipants' => (int)$updated['maxParticipants']
    ]);
    
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}
