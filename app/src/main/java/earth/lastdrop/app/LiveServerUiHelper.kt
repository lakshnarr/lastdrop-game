package earth.lastdrop.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import earth.lastdrop.app.CaptureActivityPortrait
import earth.lastdrop.app.QRCodeHelper
import earth.lastdrop.app.QRCodeHelper.BoardQRData

/**
 * Dialogs and helpers for connecting to the live server and scanning QR codes.
 */
object LiveServerUiHelper {

    fun showConnectDialog(
        activity: Activity,
        onScanRequested: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("🌍 Connect to Large Screen")
            .setMessage(
                "To experience the game on a large screen:\n\n" +
                    "1. Visit https://lastdrop.earth/ on your computer or TV\n\n" +
                    "2. Click 'Join a Live Game'\n\n" +
                    "3. A QR code will appear\n\n" +
                    "4. Click 'Scan QR Code' below to connect\n\n" +
                    "Your game will be displayed live on the big screen!"
            )
            .setPositiveButton("Scan QR Code") { _, _ -> onScanRequested() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun launchQrScanner(activity: Activity) {
        val integrator = IntentIntegrator(activity)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan QR code from lastdrop.earth")
        integrator.setOrientationLocked(true)
        integrator.setBeepEnabled(true)
        integrator.setCaptureActivity(CaptureActivityPortrait::class.java)
        integrator.initiateScan()
    }

    fun extractSessionIdFromUrl(url: String): String? {
        return runCatching { Uri.parse(url).getQueryParameter("session") }.getOrNull()
    }

    fun checkCameraPermissionAndScan(
        activity: Activity,
        requestCode: Int
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchQrScanner(activity)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.CAMERA),
                requestCode
            )
        }
    }

    fun handleCameraPermissionResult(
        activity: Activity,
        requestCode: Int,
        expectedRequestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit
    ): Boolean {
        if (requestCode != expectedRequestCode) return false

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            Toast.makeText(
                activity,
                "Camera permission is required to scan QR codes",
                Toast.LENGTH_LONG
            ).show()
        }
        return true
    }

    fun handleQrActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onSessionId: (String) -> Unit,
        onBoardQr: (BoardQRData) -> Unit,
        onInvalid: () -> Unit,
        onCancel: () -> Unit
    ): Boolean {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result == null) return false

        val contents = result.contents
        if (contents != null) {
            val sessionId = extractSessionIdFromUrl(contents)
            if (sessionId != null) {
                onSessionId(sessionId)
                return true
            }

            val qrData = QRCodeHelper.parseQRResult(contents)
            return if (qrData != null && QRCodeHelper.isValidBoardQR(qrData)) {
                onBoardQr(qrData)
                true
            } else {
                onInvalid()
                true
            }
        } else {
            onCancel()
            return true
        }
    }
}
