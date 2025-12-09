package earth.lastdrop.app

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Centralizes ESP32 pairing dialogs so MainActivity stays lean.
 */
object BoardPairingDialogs {
    private const val TAG = "BoardPairingDialogs"

    @Suppress("MissingPermission")
    fun showPinEntryDialog(
        activity: Activity,
        device: BluetoothDevice,
        onClearPairing: () -> Unit
    ) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 6-digit PIN"
            setText("654321")
            selectAll()
        }

        AlertDialog.Builder(activity)
            .setTitle("Pair with ${device.name ?: device.address}")
            .setMessage("Enter the PIN code for this board\n(Default: 654321)")
            .setView(input)
            .setPositiveButton("Pair") { _, _ ->
                val pin = input.text.toString()
                if (pin.length == 6 && pin.all { it.isDigit() }) {
                    try {
                        val success = device.setPin(pin.toByteArray())
                        Log.d(TAG, "PIN set result: $success")
                        if (success) {
                            activity.runOnUiThread {
                                Toast.makeText(activity, "Pairing...", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            activity.runOnUiThread {
                                Toast.makeText(activity, "Failed to set PIN", Toast.LENGTH_SHORT).show()
                            }
                            onClearPairing()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting PIN", e)
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        onClearPairing()
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
                    }
                    onClearPairing()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onClearPairing()
                activity.runOnUiThread {
                    Toast.makeText(activity, "Pairing cancelled", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("MissingPermission")
    fun showPasskeyConfirmationDialog(
        activity: Activity,
        device: BluetoothDevice,
        passkey: Int,
        onClearPairing: () -> Unit
    ) {
        val passkeyStr = String.format("%06d", passkey)

        AlertDialog.Builder(activity)
            .setTitle("Pair with ${device.name ?: device.address}")
            .setMessage("Confirm that the following passkey matches\n" +
                        "the one shown on the ESP32 Serial Monitor:\n\n$passkeyStr")
            .setPositiveButton("Match") { _, _ ->
                try {
                    val success = device.setPairingConfirmation(true)
                    Log.d(TAG, "Pairing confirmation result: $success")
                    if (success) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Pairing...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "Failed to confirm pairing", Toast.LENGTH_SHORT).show()
                        }
                        onClearPairing()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error confirming pairing", e)
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    onClearPairing()
                }
            }
            .setNegativeButton("Don't Match") { _, _ ->
                try {
                    device.setPairingConfirmation(false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error rejecting pairing", e)
                }
                onClearPairing()
                activity.runOnUiThread {
                    Toast.makeText(activity, "Pairing rejected", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }
}
