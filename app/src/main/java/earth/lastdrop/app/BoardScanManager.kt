package earth.lastdrop.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages ESP32 board scanning with multi-board support.
 * Scans for all LASTDROP-* boards and allows user selection.
 */
class BoardScanManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val scope: CoroutineScope,
    private val onBoardFound: (List<BluetoothDevice>) -> Unit,
    private val onBoardSelected: (BluetoothDevice) -> Unit,
    private val onLogMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "BoardScanManager"
        private const val SCAN_TIMEOUT_MS = 60000L  // 60 seconds
        private const val SCAN_DEBOUNCE_MS = 3000L  // Minimum gap between scan requests
        const val BOARD_PREFIX = "LASTDROP-"
        
        // MAC address whitelist (optional security)
        var TRUSTED_ADDRESSES = setOf<String>()
    }
    
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    private val discoveredBoards = mutableListOf<BluetoothDevice>()
    private var isScanning = false
    private var lastScanStartedAt = 0L
    
    /**
     * Start scanning for all LASTDROP-* boards
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        val now = System.currentTimeMillis()
        val sinceLastScan = now - lastScanStartedAt

        if (sinceLastScan in 0 until SCAN_DEBOUNCE_MS) {
            val remaining = (SCAN_DEBOUNCE_MS - sinceLastScan) / 1000
            Log.d(TAG, "Scan request debounced (${remaining}s remaining)")
            onLogMessage("⏳ Please wait ${remaining + 1}s before scanning again")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Scan already in progress")
            return
        }
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(context, "Bluetooth scanner not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        discoveredBoards.clear()
        isScanning = true
        lastScanStartedAt = now
        
        Log.d(TAG, "Starting board scan (looking for $BOARD_PREFIX*)")
        onLogMessage("🔍 Scanning for LASTDROP boards...")
        
        // Scan for ALL BLE devices (we'll filter by prefix)
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val deviceName = device.name ?: return@let
                    val address = device.address
                    
                    // Filter by LASTDROP-* prefix
                    if (!deviceName.startsWith(BOARD_PREFIX)) {
                        return@let  // Not a LASTDROP board
                    }
                    
                    // Check MAC whitelist if configured
                    if (TRUSTED_ADDRESSES.isNotEmpty() && !TRUSTED_ADDRESSES.contains(address)) {
                        Log.w(TAG, "Rejected untrusted board: $deviceName ($address)")
                        onLogMessage("⚠️ Rejected untrusted: $deviceName")
                        return@let
                    }
                    
                    // Avoid duplicates
                    if (discoveredBoards.any { it.address == address }) {
                        return@let
                    }
                    
                    // Add to discovered list
                    discoveredBoards.add(device)
                    Log.d(TAG, "Found board: $deviceName ($address)")
                    onLogMessage("✅ Found: $deviceName")
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                onLogMessage("❌ Scan failed: $errorCode")
                Toast.makeText(context, "Scan failed", Toast.LENGTH_SHORT).show()
                stopScan()
            }
        }
        
        scanner.startScan(null, scanSettings, scanCallback)  // No filter = scan all devices
        
        // Timeout timer
        scanJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (isScanning) {
                stopScan()
                
                when {
                    discoveredBoards.isEmpty() -> {
                        onLogMessage("⏱️ No boards found (60s timeout)")
                        Toast.makeText(
                            context,
                            "No LASTDROP boards found. Check power and proximity.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    discoveredBoards.size == 1 -> {
                        // Auto-connect to single board
                        val board = discoveredBoards[0]
                        onLogMessage("🎯 Auto-connecting to ${board.name}")
                        onBoardSelected(board)
                    }
                    else -> {
                        // Multiple boards - show selection dialog
                        onLogMessage("🎲 Found ${discoveredBoards.size} boards")
                        onBoardFound(discoveredBoards.toList())
                    }
                }
            }
        }
    }
    
    /**
     * Stop scanning
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        
        scanJob?.cancel()
        scanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            scanCallback = null
        }
        
        isScanning = false
        Log.d(TAG, "Scan stopped")
        onLogMessage("🛑 Scan stopped")
    }
    
    /**
     * Get list of discovered boards
     */
    fun getDiscoveredBoards(): List<BluetoothDevice> {
        return discoveredBoards.toList()
    }
    
    /**
     * Check if currently scanning
     */
    fun isScanning(): Boolean {
        return isScanning
    }
    
    /**
     * Clear discovered boards and restart scan
     */
    fun rescan() {
        stopScan()
        discoveredBoards.clear()
        startScan()
    }
}
