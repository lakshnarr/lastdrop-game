<?php
/**
 * Create Tournament API
 * Phase 5.4: Tournament Mode
 * 
 * Creates a new tournament with specified configuration
 * 
 * Input (POST JSON):
 * {
 *   "tournamentName": "Last Drop Championship",
 *   "format": "single_elimination",
 *   "maxParticipants": 8,
 *   "minParticipants": 4,
 *   "registrationDuration": 3600,
 *   "prizePool": 1000,
 *   "prizeDistribution": {"1st": 50, "2nd": 30, "3rd": 20},
 *   "entryFee": 10,
 *   "minEloRating": 800,
 *   "inviteOnly": false,
 *   "organizerName": "Admin",
 *   "description": "Annual championship tournament",
 *   "rules": "Standard Last Drop rules apply"
 * }
 * 
 * Output:
 * {
 *   "success": true,
 *   "tournamentId": 1,
 *   "message": "Tournament created successfully"
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
    
    if (!$input) {
        throw new Exception('Invalid JSON input');
    }
    
    // Validate required fields
    $requiredFields = ['tournamentName', 'format', 'maxParticipants'];
    foreach ($requiredFields as $field) {
        if (!isset($input[$field]) || empty($input[$field])) {
            throw new Exception("Missing required field: $field");
        }
    }
    
    // Validate format
    $validFormats = ['single_elimination', 'double_elimination', 'round_robin', 'swiss'];
    if (!in_array($input['format'], $validFormats)) {
        throw new Exception('Invalid tournament format');
    }
    
    // Extract parameters with defaults
    $tournamentName = $input['tournamentName'];
    $format = $input['format'];
    $maxParticipants = (int)$input['maxParticipants'];
    $minParticipants = isset($input['minParticipants']) ? (int)$input['minParticipants'] : 2;
    $prizePool = isset($input['prizePool']) ? (int)$input['prizePool'] : 0;
    $prizeDistribution = isset($input['prizeDistribution']) ? json_encode($input['prizeDistribution']) : null;
    $entryFee = isset($input['entryFee']) ? (int)$input['entryFee'] : 0;
    $minEloRating = isset($input['minEloRating']) ? (int)$input['minEloRating'] : 0;
    $inviteOnly = isset($input['inviteOnly']) ? (bool)$input['inviteOnly'] : false;
    $organizerName = isset($input['organizerName']) ? $input['organizerName'] : null;
    $description = isset($input['description']) ? $input['description'] : null;
    $rules = isset($input['rules']) ? $input['rules'] : null;
    
    // Calculate registration window
    $registrationDuration = isset($input['registrationDuration']) ? (int)$input['registrationDuration'] : 3600; // 1 hour default
    $registrationStart = new DateTime();
    $registrationEnd = (clone $registrationStart)->modify("+$registrationDuration seconds");
    
    // Calculate total rounds based on format and max participants
    $totalRounds = calculateTotalRounds($format, $maxParticipants);
    
    // Generate initial bracket structure
    $bracketData = generateBracketStructure($format, $maxParticipants);
    
    // Insert tournament
    $stmt = $pdo->prepare("
        INSERT INTO tournaments (
            tournamentName, format, maxParticipants, minParticipants,
            currentParticipants, status, bracketData, currentRound, totalRounds,
            prizePool, prizeDistribution, entryFee, minEloRating, inviteOnly,
            registrationStart, registrationEnd, organizerName, description, rules,
            createdAt, updatedAt
        ) VALUES (
            :tournamentName, :format, :maxParticipants, :minParticipants,
            0, 'registration', :bracketData, 0, :totalRounds,
            :prizePool, :prizeDistribution, :entryFee, :minEloRating, :inviteOnly,
            :registrationStart, :registrationEnd, :organizerName, :description, :rules,
            NOW(), NOW()
        )
    ");
    
    $stmt->execute([
        ':tournamentName' => $tournamentName,
        ':format' => $format,
        ':maxParticipants' => $maxParticipants,
        ':minParticipants' => $minParticipants,
        ':bracketData' => $bracketData,
        ':totalRounds' => $totalRounds,
        ':prizePool' => $prizePool,
        ':prizeDistribution' => $prizeDistribution,
        ':entryFee' => $entryFee,
        ':minEloRating' => $minEloRating,
        ':inviteOnly' => $inviteOnly ? 1 : 0,
        ':registrationStart' => $registrationStart->format('Y-m-d H:i:s'),
        ':registrationEnd' => $registrationEnd->format('Y-m-d H:i:s'),
        ':organizerName' => $organizerName,
        ':description' => $description,
        ':rules' => $rules
    ]);
    
    $tournamentId = $pdo->lastInsertId();
    
    echo json_encode([
        'success' => true,
        'tournamentId' => (int)$tournamentId,
        'registrationStart' => $registrationStart->format('Y-m-d H:i:s'),
        'registrationEnd' => $registrationEnd->format('Y-m-d H:i:s'),
        'totalRounds' => $totalRounds,
        'message' => 'Tournament created successfully'
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => $e->getMessage()
    ]);
}

/**
 * Calculate total rounds based on format and participant count
 */
function calculateTotalRounds($format, $maxParticipants) {
    switch ($format) {
        case 'single_elimination':
            return (int)ceil(log($maxParticipants, 2));
        
        case 'double_elimination':
            return (int)ceil(log($maxParticipants, 2)) * 2;
        
        case 'round_robin':
            return $maxParticipants - 1;
        
        case 'swiss':
            return (int)ceil(log($maxParticipants, 2)) + 1;
        
        default:
            return 1;
    }
}

/**
 * Generate initial bracket structure
 */
function generateBracketStructure($format, $maxParticipants) {
    $bracket = [
        'format' => $format,
        'maxParticipants' => $maxParticipants,
        'rounds' => []
    ];
    
    switch ($format) {
        case 'single_elimination':
            $totalRounds = (int)ceil(log($maxParticipants, 2));
            for ($round = 1; $round <= $totalRounds; $round++) {
                $matchesInRound = (int)pow(2, $totalRounds - $round);
                $bracket['rounds'][] = [
                    'roundNumber' => $round,
                    'roundName' => getRoundName($round, $totalRounds),
                    'matchCount' => $matchesInRound,
                    'matches' => []
                ];
            }
            break;
        
        case 'double_elimination':
            // Winners and losers brackets
            $bracket['winnersRounds'] = [];
            $bracket['losersRounds'] = [];
            $bracket['finals'] = [];
            break;
        
        case 'round_robin':
            // Everyone plays everyone
            $bracket['totalMatches'] = ($maxParticipants * ($maxParticipants - 1)) / 2;
            break;
        
        case 'swiss':
            // Pairing based on standings after each round
            $bracket['pairingAlgorithm'] = 'swiss';
            break;
    }
    
    return json_encode($bracket);
}

/**
 * Get human-readable round name
 */
function getRoundName($currentRound, $totalRounds) {
    $roundsFromEnd = $totalRounds - $currentRound;
    
    switch ($roundsFromEnd) {
        case 0:
            return 'Finals';
        case 1:
            return 'Semi-Finals';
        case 2:
            return 'Quarter-Finals';
        default:
            return "Round $currentRound";
    }
}
