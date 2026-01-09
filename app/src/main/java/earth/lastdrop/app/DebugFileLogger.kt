package earth.lastdrop.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple file-based logger for debugging ESP32 communication.
 * Overwrites log file on each fresh app start.
 */
object DebugFileLogger {
    private const val TAG = "DebugFileLogger"
    private const val LOG_FILE_NAME = "lastdrop_debug.log"
    
    private var logFile: File? = null
    private var initialized = false
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    /**
     * Initialize logger - call once in Application or Activity onCreate.
     * Clears the log file on each init.
     */
    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE_NAME)
            
            // Overwrite file on fresh start
            logFile?.writeText("=== LastDrop Debug Log ===\n")
            logFile?.appendText("Started: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            logFile?.appendText("Device: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})\n")
            logFile?.appendText("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            logFile?.appendText("=============================\n\n")
            
            initialized = true
            Log.d(TAG, "Logger initialized: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init logger: ${e.message}")
        }
    }
    
    /**
     * Log a debug message
     */
    fun d(tag: String, message: String) {
        log("D", tag, message)
    }
    
    /**
     * Log an info message
     */
    fun i(tag: String, message: String) {
        log("I", tag, message)
    }
    
    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) {
        log("W", tag, message)
    }
    
    /**
     * Log an error message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("E", tag, message)
        throwable?.let {
            log("E", tag, "  Exception: ${it.message}")
            it.stackTrace.take(5).forEach { frame ->
                log("E", tag, "    at $frame")
            }
        }
    }
    
    private fun log(level: String, tag: String, message: String) {
        if (!initialized) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val line = "[$timestamp] $level/$tag: $message\n"
            logFile?.appendText(line)
            
            // Also log to Logcat
            when (level) {
                "D" -> Log.d(tag, message)
                "I" -> Log.i(tag, message)
                "W" -> Log.w(tag, message)
                "E" -> Log.e(tag, message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }
    
    /**
     * Get the full log content as string
     */
    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "Log file not initialized"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
    
    /**
     * Get the log file path for sharing
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * Get the log file for sharing via Intent
     */
    fun getLogFile(): File? {
        return logFile
    }
}
