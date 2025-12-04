package com.example.lastdrop

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.util.*

/**
 * Manages ESP32 BLE connection with auto-reconnection, timeout, and MAC filtering
 */
class ESP32ConnectionManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onConnectionStateChanged: (Boolean) -> Unit,
    private val onLogMessage: (String) -> Unit,
    private val onCharacteristicChanged: (String) -> Unit
) {
    companion object {
        private const val TAG = "ESP32Manager"
        const val ESP32_DEVICE_NAME = "LASTDROP-ESP32"
        
        // Nordic UART Service UUIDs
        const val UART_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        const val UART_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
        const val UART_TX_CHAR_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
        
        // Connection management
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val RECONNECT_DELAY_MS = 2000L
        
        // MAC whitelist (empty = allow all)
        val TRUSTED_ESP32_ADDRESSES = setOf<String>(
            // "24:0A:C4:XX:XX:XX"  // Add your ESP32 MAC here
        )
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Connection state
    var isConnected = false
        private set
    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    
    // Auto-reconnection
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var isGameActive = false
    
    // Scan timeout
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    
    /**
     * Start scanning for ESP32 device
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }
        
        onLogMessage("🔍 Scanning for ESP32...")
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(context, "Bluetooth scanner not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val scanFilter = ScanFilter.Builder()
            .setDeviceName(ESP32_DEVICE_NAME)
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val address = device.address
                    
                    // Validate MAC address if whitelist configured
                    if (TRUSTED_ESP32_ADDRESSES.isNotEmpty() && !TRUSTED_ESP32_ADDRESSES.contains(address)) {
                        Log.w(TAG, "Rejected untrusted ESP32: $address")
                        onLogMessage("⚠️ Rejected untrusted ESP32: $address")
                        Toast.makeText(context, "Untrusted ESP32 rejected. Add MAC to whitelist.", Toast.LENGTH_LONG).show()
                        return@let
                    }
                    
                    onLogMessage("✅ Found trusted ESP32: $address")
                    stopScan()
                    connectToDevice(device)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                onLogMessage("❌ ESP32 scan failed: $errorCode")
                Toast.makeText(context, "ESP32 scan failed", Toast.LENGTH_SHORT).show()
            }
        }
        
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        
        // Start timeout timer
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (!isConnected) {
                stopScan()
                onLogMessage("⏱️ ESP32 scan timeout (10s)")
                Toast.makeText(
                    context,
                    "ESP32 not found. Check power and proximity.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Stop scanning for devices
     */
    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanJob?.cancel()
        scanCallback?.let {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            scanCallback = null
            onLogMessage("🛑 ESP32 scan stopped")
        }
    }
    
    /**
     * Connect to discovered ESP32 device
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ESP32: ${device.address}")
        
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "ESP32 connected, discovering services...")
                        isConnected = true
                        reconnectAttempts = 0
                        scanJob?.cancel()
                        isGameActive = true
                        onLogMessage("✅ ESP32 Connected")
                        onConnectionStateChanged(true)
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "ESP32 disconnected")
                        isConnected = false
                        gatt?.close()
                        this@ESP32ConnectionManager.gatt = null
                        
                        onLogMessage("❌ ESP32 Disconnected")
                        onConnectionStateChanged(false)
                        Toast.makeText(context, "ESP32 disconnected", Toast.LENGTH_SHORT).show()
                        
                        // Auto-reconnect if game is active
                        if (isGameActive && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                            reconnectAttempts++
                            onLogMessage("🔄 Attempting reconnect ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)...")
                            
                            reconnectJob?.cancel()
                            reconnectJob = scope.launch {
                                delay(RECONNECT_DELAY_MS)
                                connect()
                            }
                        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                            Toast.makeText(context, "ESP32 reconnection failed after $MAX_RECONNECT_ATTEMPTS attempts", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            
            @Suppress("DEPRECATION")
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt?.getService(UUID.fromString(UART_SERVICE_UUID))
                    rxCharacteristic = service?.getCharacteristic(UUID.fromString(UART_RX_CHAR_UUID))
                    txCharacteristic = service?.getCharacteristic(UUID.fromString(UART_TX_CHAR_UUID))
                    
                    // Enable notifications for TX characteristic
                    txCharacteristic?.let { char ->
                        gatt?.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt?.writeDescriptor(descriptor)
                    }
                    
                    onLogMessage("✅ ESP32 Services Discovered")
                    Log.d(TAG, "ESP32 services discovered and notifications enabled")
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                }
            }
            
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic?.value?.let { bytes ->
                    val message = String(bytes, Charsets.UTF_8)
                    onCharacteristicChanged(message)
                }
            }
        })
    }
    
    /**
     * Send command to ESP32
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun sendCommand(jsonCommand: String): Boolean {
        if (!isConnected || rxCharacteristic == null) {
            Log.w(TAG, "Cannot send command - not connected")
            return false
        }
        
        rxCharacteristic?.value = jsonCommand.toByteArray(Charsets.UTF_8)
        return gatt?.writeCharacteristic(rxCharacteristic) ?: false
    }
    
    /**
     * Disconnect from ESP32
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        isGameActive = false
        reconnectJob?.cancel()
        scanJob?.cancel()
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
