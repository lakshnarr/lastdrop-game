<?php
/**
 * Start Tournament API
 * Phase 5.4: Tournament Mode
 * 
 * Starts a tournament by seeding players and generating matches
 * 
 * Input (POST JSON):
 * {
 *   "tournamentId": 1
 * }
 * 
 * Output:
 * {
 *   "success": true,
 *   "message": "Tournament started",
 *   "matchesCreated": 4
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
    
    if (!$input || !isset($input['tournamentId'])) {
        throw new Exception('Tournament ID required');
    }
    
    $tournamentId = (int)$input['tournamentId'];
    
    // Get tournament
    $stmt = $pdo->prepare("SELECT * FROM tournaments WHERE id = :tournamentId");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $tournament = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$tournament) {
        throw new Exception('Tournament not found');
    }
    
    // Check status
    if ($tournament['status'] !== 'registration' && $tournament['status'] !== 'ready') {
        throw new Exception('Tournament cannot be started in current status');
    }
    
    // Check participant count
    if ($tournament['currentParticipants'] < $tournament['minParticipants']) {
        throw new Exception("Not enough participants. Minimum: {$tournament['minParticipants']}, Current: {$tournament['currentParticipants']}");
    }
    
    // Get participants
    $stmt = $pdo->prepare("
        SELECT * FROM tournament_participants 
        WHERE tournamentId = :tournamentId 
        ORDER BY eloAtEntry DESC, registeredAt ASC
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    $participants = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Seed participants
    foreach ($participants as $index => $participant) {
        $stmt = $pdo->prepare("
            UPDATE tournament_participants 
            SET seedPosition = :seed, status = 'active'
            WHERE id = :id
        ");
        $stmt->execute([
            ':seed' => $index + 1,
            ':id' => $participant['id']
        ]);
    }
    
    // Generate first round matches based on format
    $matchesCreated = 0;
    
    switch ($tournament['format']) {
        case 'single_elimination':
            $matchesCreated = generateSingleEliminationMatches($pdo, $tournamentId, $participants);
            break;
        
        case 'double_elimination':
            $matchesCreated = generateDoubleEliminationMatches($pdo, $tournamentId, $participants);
            break;
        
        case 'round_robin':
            $matchesCreated = generateRoundRobinMatches($pdo, $tournamentId, $participants);
            break;
        
        case 'swiss':
            $matchesCreated = generateSwissMatches($pdo, $tournamentId, $participants, 1);
            break;
    }
    
    // Update tournament status
    $stmt = $pdo->prepare("
        UPDATE tournaments 
        SET status = 'in_progress',
            currentRound = 1,
            startTime = NOW(),
            updatedAt = NOW()
        WHERE id = :tournamentId
    ");
    $stmt->execute([':tournamentId' => $tournamentId]);
    
    echo json_encode([
        'success' => true,
        'message' => 'Tournament started successfully',
        'matchesCreated' => $matchesCreated,
        'participants' => count($participants)
    ]);
    
} catch (Exception $e) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

/**
 * Generate single elimination bracket matches
 */
function generateSingleEliminationMatches($pdo, $tournamentId, $participants) {
    $count = count($participants);
    $matchesCreated = 0;
    
    // Calculate first round matches (pair adjacent seeds)
    $matchNumber = 1;
    for ($i = 0; $i < $count; $i += 2) {
        if (isset($participants[$i + 1])) {
            // Two players
            $stmt = $pdo->prepare("
                INSERT INTO tournament_matches (
                    tournamentId, roundNumber, matchNumber, bracketPosition,
                    player1Name, player2Name, status, createdAt
                ) VALUES (
                    :tournamentId, 1, :matchNumber, :bracketPosition,
                    :player1, :player2, 'ready', NOW()
                )
            ");
            
            $stmt->execute([
                ':tournamentId' => $tournamentId,
                ':matchNumber' => $matchNumber,
                ':bracketPosition' => "R1-M$matchNumber",
                ':player1' => $participants[$i]['playerName'],
                ':player2' => $participants[$i + 1]['playerName']
            ]);
            
            $matchesCreated++;
            $matchNumber++;
        } else {
            // Bye - player advances automatically
            $participants[$i]['hasbye'] = true;
        }
    }
    
    return $matchesCreated;
}

/**
 * Generate double elimination bracket matches
 */
function generateDoubleEliminationMatches($pdo, $tournamentId, $participants) {
    // Start with winners bracket (same as single elimination)
    return generateSingleEliminationMatches($pdo, $tournamentId, $participants);
}

/**
 * Generate round robin matches
 */
function generateRoundRobinMatches($pdo, $tournamentId, $participants) {
    $count = count($participants);
    $matchesCreated = 0;
    $roundNumber = 1;
    $matchNumber = 1;
    
    // Generate all pairings
    for ($i = 0; $i < $count; $i++) {
        for ($j = $i + 1; $j < $count; $j++) {
            $stmt = $pdo->prepare("
                INSERT INTO tournament_matches (
                    tournamentId, roundNumber, matchNumber, bracketPosition,
                    player1Name, player2Name, status, createdAt
                ) VALUES (
                    :tournamentId, :round, :matchNumber, :bracketPosition,
                    :player1, :player2, 'pending', NOW()
                )
            ");
            
            $stmt->execute([
                ':tournamentId' => $tournamentId,
                ':round' => $roundNumber,
                ':matchNumber' => $matchNumber,
                ':bracketPosition' => "RR-M$matchNumber",
                ':player1' => $participants[$i]['playerName'],
                ':player2' => $participants[$j]['playerName']
            ]);
            
            $matchesCreated++;
            $matchNumber++;
        }
    }
    
    // Mark first batch as ready
    $stmt = $pdo->prepare("
        UPDATE tournament_matches 
        SET status = 'ready'
        WHERE tournamentId = :tournamentId 
        LIMIT " . min($count / 2, $matchesCreated)
    );
    $stmt->execute([':tournamentId' => $tournamentId]);
    
    return $matchesCreated;
}

/**
 * Generate Swiss system pairings for a round
 */
function generateSwissMatches($pdo, $tournamentId, $participants, $round) {
    $matchesCreated = 0;
    $matchNumber = 1;
    
    // For round 1, pair by seed (similar to single elimination)
    // For later rounds, pair by standings
    if ($round === 1) {
        $count = count($participants);
        for ($i = 0; $i < $count; $i += 2) {
            if (isset($participants[$i + 1])) {
                $stmt = $pdo->prepare("
                    INSERT INTO tournament_matches (
                        tournamentId, roundNumber, matchNumber, bracketPosition,
                        player1Name, player2Name, status, createdAt
                    ) VALUES (
                        :tournamentId, :round, :matchNumber, :bracketPosition,
                        :player1, :player2, 'ready', NOW()
                    )
                ");
                
                $stmt->execute([
                    ':tournamentId' => $tournamentId,
                    ':round' => $round,
                    ':matchNumber' => $matchNumber,
                    ':bracketPosition' => "Swiss-R$round-M$matchNumber",
                    ':player1' => $participants[$i]['playerName'],
                    ':player2' => $participants[$i + 1]['playerName']
                ]);
                
                $matchesCreated++;
                $matchNumber++;
            }
        }
    }
    
    return $matchesCreated;
}
