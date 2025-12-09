package earth.lastdrop.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import org.sample.godicesdklib.GoDiceSDK
import java.util.LinkedList
import java.util.Queue
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

/**
 * Handles GoDice scanning/connection lifecycle with callbacks back to the activity.
 */
class DiceConnectionController(
    private val context: android.content.Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val playWithTwoDice: () -> Boolean,
    private val onStatus: (String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onDiceConnected: (Int, String?) -> Unit,
    private val onDiceDisconnected: () -> Unit
) {
    companion object {
        private const val TAG = "DiceConnectionController"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private val dices = HashMap<String, Dice>()
    private val diceIds = LinkedList<String>()

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        if (isScanning) {
            onStatus("Already scanning…")
            return
        }

        onStatus(if (playWithTwoDice()) "Scanning for 2 BLDice…" else "Scanning for BLDice…")

        val filters = LinkedList<ScanFilter>()
        filters.add(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(Dice.serviceUUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val controller = this
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                super.onBatchScanResults(results)
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                onStatus("Scan failed: $errorCode")
                isScanning = false
            }
        }

        isScanning = true
        scanner.startScan(filters, scanSettings, scanCallback)

        // auto-stop scanning after 15s
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                onStatus("Scan timeout. No BLDice found.")
            }
        }, 15_000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanCallback?.let { scanner.stopScan(it) }
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    fun disconnectAll(onCleared: () -> Unit = {}) {
        try {
            stopScan()
        } catch (_: Exception) { }

        dices.values.forEach { dice ->
            try {
                dice.gatt?.disconnect()
                dice.gatt?.close()
            } catch (_: Exception) { }
        }

        dices.clear()
        diceIds.clear()
        onDiceDisconnected()
        onCleared()
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return

        val name = device.name
        Log.d(TAG, "Scan result: addr=${device.address}, name=$name, rssi=${result.rssi}")

        if (name == null) return
        if (!name.contains("GoDice")) return

        if (dices.containsKey(device.address)) return

        // Assign diceId
        var diceId = diceIds.indexOf(device.address)
        if (diceId < 0) {
            diceId = diceIds.size
            diceIds.add(device.address)
        }

        val dice = Dice(diceId, device)
        dices[device.address] = dice

        // LED blink + queries
        dice.scheduleWrite(GoDiceSDK.openLedsPacket(0xff0000, 0x00ff00))
        Timer().schedule(object : TimerTask() {
            override fun run() {
                dice.scheduleWrite(GoDiceSDK.closeToggleLedsPacket())
            }
        }, 3000)

        dice.scheduleWrite(GoDiceSDK.getColorPacket())
        dice.scheduleWrite(GoDiceSDK.getChargeLevelPacket())

        onStatus("Connecting to ${device.name}…")

        // Stop scanning after we have enough dice:
        val neededDice = if (playWithTwoDice()) 2 else 1
        if (dices.size >= neededDice) {
            stopScan()
        }

        dice.gatt = device.connectGatt(context, true, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt?,
                status: Int,
                newState: Int
            ) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onLog("✅ BLDice connected (id=$diceId)")
                    onDiceConnected(diceId, dice.getDieName())
                    dice.onConnected()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onDiceDisconnected()
                    onStatus("Dice disconnected")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                dice.onServicesDiscovered()
            }

            @Deprecated("Use onCharacteristicChanged(gatt, characteristic, value) instead")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                dice.onEvent()
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                dice.nextWrite()
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                dice.nextWrite()
            }
        })
    }
}