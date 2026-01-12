package earth.lastdrop.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Encapsulates ESP32 GATT connection and message flow.
 */
class BoardConnectionController(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val serviceUuid: UUID,
    private val txUuid: UUID,
    private val rxUuid: UUID,
    private val cccdUuid: UUID,
    private val onLog: (String) -> Unit,
    private val onConnected: (BluetoothDevice) -> Unit,
    private val onDisconnected: (BluetoothDevice?) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onServicesReady: () -> Unit
) {
    companion object {
        private const val TAG = "BoardConnectionController"
    }

    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingNotifyJob: Job? = null

    val isConnected: Boolean
        get() = gatt != null

    val connectedDevice: BluetoothDevice?
        get() = gatt?.device

    val currentGatt: BluetoothGatt?
        get() = gatt

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        onLog("📡 Connecting to ${device.name}...")
        DebugFileLogger.i(TAG, "=== GATT Connection Attempt ===")
        DebugFileLogger.i(TAG, "  Device Name: ${device.name}")
        DebugFileLogger.i(TAG, "  Device MAC: ${device.address}")
        DebugFileLogger.i(TAG, "  Bond State: ${device.bondState} (NONE=10, BONDING=11, BONDED=12)")
        DebugFileLogger.i(TAG, "  Device Type: ${device.type} (CLASSIC=1, LE=2, DUAL=3)")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            DebugFileLogger.d(TAG, "Using connectGatt with TRANSPORT_LE")
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            DebugFileLogger.d(TAG, "Using connectGatt legacy (no transport param)")
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            DebugFileLogger.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            // Log GATT errors
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = when (status) {
                    4 -> "GATT_ERROR_CONNECTION_TIMEOUT (status=4) - Device didn't respond. Try: Enable Location, Disable Battery Optimization, Clear Bluetooth cache"
                    133 -> "GATT_ERROR (status=133) - Generic error, often BLE stack issue. Try: Toggle Bluetooth OFF/ON, Restart phone"
                    8 -> "GATT_CONN_TIMEOUT (status=8) - Connection timeout"
                    19 -> "GATT_CONN_TERMINATE_PEER_USER (status=19) - Device disconnected by remote side"
                    22 -> "GATT_CONN_LMP_TIMEOUT (status=22) - Link supervision timeout"
                    else -> "GATT error (status=$status)"
                }
                DebugFileLogger.e(TAG, "❌ Connection failed: $errorMsg")
                onLog("❌ $errorMsg")
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "ESP32 connected, requesting MTU...")
                        DebugFileLogger.i(TAG, "STATE_CONNECTED - requesting MTU 512")
                        onLog("✅ ESP32 Connected")
                        gatt?.device?.let { onConnected(it) }
                        // Request larger MTU for longer JSON messages (default is 23 bytes)
                        gatt?.requestMtu(512)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "ESP32 disconnected")
                    DebugFileLogger.i(TAG, "STATE_DISCONNECTED (status=$status)")
                    onLog("❌ ESP32 Disconnected")
                    val prev = connectedDevice
                    cleanup()
                    onDisconnected(prev)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            DebugFileLogger.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed to $mtu bytes")
                DebugFileLogger.i(TAG, "MTU success: $mtu bytes - discovering services")
                onLog("✅ MTU: $mtu bytes")
                // Now discover services after MTU is set
                gatt?.discoverServices()
            } else {
                Log.e(TAG, "MTU request failed, using default")
                DebugFileLogger.w(TAG, "MTU request failed, discovering services anyway")
                gatt?.discoverServices()
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            DebugFileLogger.d(TAG, "onServicesDiscovered: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(serviceUuid)
                DebugFileLogger.d(TAG, "Got service: ${service != null}")
                txCharacteristic = service?.getCharacteristic(txUuid)
                rxCharacteristic = service?.getCharacteristic(rxUuid)
                DebugFileLogger.d(TAG, "txChar: ${txCharacteristic != null}, rxChar: ${rxCharacteristic != null}")

                txCharacteristic?.let { char ->
                    gatt.setCharacteristicNotification(char, true)
                    val descriptor = char.getDescriptor(cccdUuid)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val writeResult = gatt.writeDescriptor(descriptor)
                    DebugFileLogger.d(TAG, "writeDescriptor result: $writeResult - waiting for onDescriptorWrite")
                }

                onLog("✅ ESP32 Services Discovered")
                // DON'T call onServicesReady here - wait for onDescriptorWrite
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                DebugFileLogger.e(TAG, "Service discovery failed: status=$status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            DebugFileLogger.d(TAG, "onDescriptorWrite: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DebugFileLogger.i(TAG, "Descriptor write success - NOW calling onServicesReady")
                onServicesReady()
            } else {
                DebugFileLogger.e(TAG, "Descriptor write failed: $status")
                // Still try to proceed
                onServicesReady()
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                val message = String(data, Charsets.UTF_8)
                DebugFileLogger.d(TAG, "onCharacteristicChanged: ← $message")
                onMessage(message)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun send(json: String) {
        val rx = rxCharacteristic ?: run {
            DebugFileLogger.w(TAG, "send() called but rxCharacteristic is null!")
            return
        }
        DebugFileLogger.d(TAG, "send() → $json")
        rx.value = json.toByteArray(Charsets.UTF_8)
        val result = gatt?.writeCharacteristic(rx)
        DebugFileLogger.d(TAG, "writeCharacteristic result: $result")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        cleanup()
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        pendingNotifyJob?.cancel()
        pendingNotifyJob = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        txCharacteristic = null
        rxCharacteristic = null
    }
}
