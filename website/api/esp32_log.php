<?php
/**
 * ESP32 Remote Logging Endpoint
 * Receives and stores ESP32 board logs
 * 
 * POST Parameters:
 * - boardId: Board unique identifier (e.g., LASTDROP-NOHW-0001)
 * - sessionId: Unique session identifier
 * - firmware: Firmware version
 * - logData: Log entries (timestamp|level|boardId|sessionId|message format)
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// Handle preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// Log directory configuration
$logDir = __DIR__ . '/../logs/esp32/';
$maxLogFileSize = 10 * 1024 * 1024; // 10 MB per log file
$maxLogFiles = 100; // Keep max 100 log files per board

// Create log directory if it doesn't exist
if (!is_dir($logDir)) {
    mkdir($logDir, 0755, true);
}

// Validate request method
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode([
        'success' => false,
        'error' => 'Method not allowed. Use POST.'
    ]);
    exit;
}

// Get POST data
$boardId = $_POST['boardId'] ?? '';
$sessionId = $_POST['sessionId'] ?? '';
$firmware = $_POST['firmware'] ?? 'unknown';
$logData = $_POST['logData'] ?? '';

// Validate required fields
if (empty($boardId) || empty($sessionId) || empty($logData)) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'error' => 'Missing required fields: boardId, sessionId, or logData'
    ]);
    exit;
}

// Sanitize board ID for filename
$safeBoardId = preg_replace('/[^a-zA-Z0-9-_]/', '', $boardId);
$safeSessionId = preg_replace('/[^a-zA-Z0-9-_]/', '', $sessionId);

// Get current date for log organization
$dateStr = date('Y-m-d');
$timestampStr = date('Y-m-d H:i:s');

// Log file path
$logFilePath = $logDir . $safeBoardId . '_' . $dateStr . '.log';

try {
    // Prepare log entry with metadata header
    $logEntry = "=== LOG UPLOAD: $timestampStr ===\n";
    $logEntry .= "Board: $boardId | Session: $sessionId | Firmware: $firmware\n";
    $logEntry .= str_repeat('-', 80) . "\n";
    $logEntry .= $logData;
    $logEntry .= "\n" . str_repeat('=', 80) . "\n\n";
    
    // Check if log file exists and its size
    if (file_exists($logFilePath) && filesize($logFilePath) > $maxLogFileSize) {
        // Rotate log file
        $rotatedPath = $logDir . $safeBoardId . '_' . $dateStr . '_' . time() . '.log';
        rename($logFilePath, $rotatedPath);
        
        // Clean up old log files if too many
        $boardLogFiles = glob($logDir . $safeBoardId . '_*.log');
        if (count($boardLogFiles) > $maxLogFiles) {
            // Sort by modification time
            usort($boardLogFiles, function($a, $b) {
                return filemtime($a) - filemtime($b);
            });
            
            // Delete oldest files
            $filesToDelete = array_slice($boardLogFiles, 0, count($boardLogFiles) - $maxLogFiles);
            foreach ($filesToDelete as $file) {
                unlink($file);
            }
        }
    }
    
    // Append log entry
    $bytesWritten = file_put_contents($logFilePath, $logEntry, FILE_APPEND | LOCK_EX);
    
    if ($bytesWritten === false) {
        throw new Exception('Failed to write log file');
    }
    
    // Also maintain a "latest" log for quick viewing
    $latestLogPath = $logDir . 'latest_' . $safeBoardId . '.log';
    file_put_contents($latestLogPath, $logEntry);
    
    // Parse log entries to extract statistics
    $logLines = explode("\n", trim($logData));
    $logStats = [
        'INFO' => 0,
        'WARN' => 0,
        'ERROR' => 0,
        'DEBUG' => 0
    ];
    
    foreach ($logLines as $line) {
        if (empty($line)) continue;
        $parts = explode('|', $line);
        if (count($parts) >= 2) {
            $level = $parts[1];
            if (isset($logStats[$level])) {
                $logStats[$level]++;
            }
        }
    }
    
    // Success response
    http_response_code(200);
    echo json_encode([
        'success' => true,
        'message' => 'Logs received successfully',
        'boardId' => $boardId,
        'sessionId' => $sessionId,
        'firmware' => $firmware,
        'bytesWritten' => $bytesWritten,
        'logEntriesReceived' => count($logLines),
        'stats' => $logStats,
        'logFile' => basename($logFilePath),
        'timestamp' => $timestampStr
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Server error: ' . $e->getMessage()
    ]);
}
