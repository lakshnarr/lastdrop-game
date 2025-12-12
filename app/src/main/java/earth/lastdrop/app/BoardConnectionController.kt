package earth.lastdrop.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
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
        gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "ESP32 connected, requesting MTU...")
                        onLog("✅ ESP32 Connected")
                        onConnected(device)
                        // Request larger MTU for longer JSON messages (default is 23 bytes)
                        gatt?.requestMtu(512)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "ESP32 disconnected")
                        onLog("❌ ESP32 Disconnected")
                        val prev = connectedDevice
                        cleanup()
                        onDisconnected(prev)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "MTU changed to $mtu bytes")
                    onLog("✅ MTU: $mtu bytes")
                    // Now discover services after MTU is set
                    gatt?.discoverServices()
                } else {
                    Log.e(TAG, "MTU request failed, using default")
                    gatt?.discoverServices()
                }
            }

            @Suppress("DEPRECATION")
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = gatt.getService(serviceUuid)
                    txCharacteristic = service?.getCharacteristic(txUuid)
                    rxCharacteristic = service?.getCharacteristic(rxUuid)

                    txCharacteristic?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        val descriptor = char.getDescriptor(cccdUuid)
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }

                    onLog("✅ ESP32 Services Discovered")
                    onServicesReady()
                } else {
                    Log.e(TAG, "Service discovery failed: $status")
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
                characteristic?.value?.let { data ->
                    val message = String(data, Charsets.UTF_8)
                    onMessage(message)
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun send(json: String) {
        val rx = rxCharacteristic ?: return
        rx.value = json.toByteArray(Charsets.UTF_8)
        gatt?.writeCharacteristic(rx)
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
