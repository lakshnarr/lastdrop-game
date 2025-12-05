<?php
/**
 * Last Drop - Session Cleanup Script
 * 
 * Removes inactive sessions from the database
 * Sessions are considered inactive if not updated in last 5 minutes
 * 
 * Run via cron job every minute:
 * * * * * * php /path/to/cleanup_sessions.php >> /var/log/lastdrop_cleanup.log 2>&1
 * 
 * Or via web (with security token):
 * curl "https://lastdrop.earth/api/cleanup_sessions.php?token=CLEANUP_TOKEN_12345"
 */

// Configuration
define('DB_HOST', 'localhost');
define('DB_NAME', 'lastdrop');
define('DB_USER', 'lastdrop_user');
define('DB_PASS', 'your_password_here'); // Change in production
define('CLEANUP_TOKEN', 'CLEANUP_TOKEN_12345'); // Change in production
define('INACTIVE_THRESHOLD_MINUTES', 5);

// Headers (if called via web)
header('Content-Type: application/json');

// Error logging
ini_set('display_errors', 0);
ini_set('log_errors', 1);

/**
 * Log message to console/log file
 */
function logMessage($message) {
    $timestamp = date('Y-m-d H:i:s');
    echo "[$timestamp] $message\n";
    error_log("[$timestamp] Cleanup: $message");
}

/**
 * Send JSON response (if called via web)
 */
function sendResponse($data, $httpCode = 200) {
    http_response_code($httpCode);
    echo json_encode($data, JSON_PRETTY_PRINT);
    exit;
}

// Check if called via web
$isWebRequest = isset($_SERVER['REQUEST_METHOD']);

if ($isWebRequest) {
    // Verify security token
    $token = $_GET['token'] ?? '';
    if ($token !== CLEANUP_TOKEN) {
        sendResponse([
            'success' => false,
            'error' => 'Invalid security token'
        ], 401);
    }
}

logMessage("Starting session cleanup...");

// Connect to database
try {
    $pdo = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        ]
    );
    
    logMessage("Database connected successfully");
    
} catch (PDOException $e) {
    $errorMsg = "Database connection failed: " . $e->getMessage();
    logMessage($errorMsg);
    
    if ($isWebRequest) {
        sendResponse([
            'success' => false,
            'error' => $errorMsg
        ], 500);
    }
    exit(1);
}

// Get inactive sessions before deletion (for logging)
try {
    $stmt = $pdo->prepare("
        SELECT sessionId, boardId, playerCount, lastUpdate
        FROM active_sessions
        WHERE lastUpdate < DATE_SUB(NOW(), INTERVAL :threshold MINUTE)
    ");
    
    $stmt->execute(['threshold' => INACTIVE_THRESHOLD_MINUTES]);
    $inactiveSessions = $stmt->fetchAll();
    
    $inactiveCount = count($inactiveSessions);
    
    if ($inactiveCount > 0) {
        logMessage("Found $inactiveCount inactive session(s):");
        foreach ($inactiveSessions as $session) {
            logMessage("  - Session: {$session['sessionId']}, Board: {$session['boardId']}, Last update: {$session['lastUpdate']}");
        }
    } else {
        logMessage("No inactive sessions found");
    }
    
} catch (PDOException $e) {
    logMessage("Error fetching inactive sessions: " . $e->getMessage());
}

// Delete inactive sessions
try {
    $stmt = $pdo->prepare("
        DELETE FROM active_sessions
        WHERE lastUpdate < DATE_SUB(NOW(), INTERVAL :threshold MINUTE)
    ");
    
    $stmt->execute(['threshold' => INACTIVE_THRESHOLD_MINUTES]);
    $deletedCount = $stmt->rowCount();
    
    logMessage("Deleted $deletedCount inactive session(s)");
    
    // Get current active sessions count
    $stmt2 = $pdo->query("SELECT COUNT(*) as count FROM active_sessions WHERE status = 'active'");
    $result = $stmt2->fetch();
    $activeCount = $result['count'];
    
    logMessage("Current active sessions: $activeCount");
    logMessage("Cleanup completed successfully");
    
    // Return response if web request
    if ($isWebRequest) {
        sendResponse([
            'success' => true,
            'deletedSessions' => $deletedCount,
            'activeSessions' => $activeCount,
            'threshold' => INACTIVE_THRESHOLD_MINUTES . ' minutes',
            'timestamp' => date('c')
        ]);
    }
    
} catch (PDOException $e) {
    $errorMsg = "Error deleting inactive sessions: " . $e->getMessage();
    logMessage($errorMsg);
    
    if ($isWebRequest) {
        sendResponse([
            'success' => false,
            'error' => $errorMsg
        ], 500);
    }
    exit(1);
}

// Cleanup old game state files (optional)
// Remove .json files that haven't been modified in 1 hour
$stateFileDir = __DIR__ . '/../game_states/';
if (is_dir($stateFileDir)) {
    $oneHourAgo = time() - 3600;
    $files = glob($stateFileDir . '*.json');
    $deletedFiles = 0;
    
    foreach ($files as $file) {
        if (filemtime($file) < $oneHourAgo) {
            if (unlink($file)) {
                $deletedFiles++;
            }
        }
    }
    
    if ($deletedFiles > 0) {
        logMessage("Deleted $deletedFiles old game state file(s)");
    }
}

logMessage("Cleanup script finished");
exit(0);
