package earth.lastdrop.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject

/**
 * QR Code integration for instant board connection
 * 
 * QR Code Format (JSON):
 * {
 *   "boardId": "LASTDROP-0001",
 *   "macAddress": "AA:BB:CC:DD:EE:01",
 *   "password": "654321",
 *   "nickname": "Game Room A"
 * }
 */
object QRCodeHelper {
    
    private const val TAG = "QRCodeHelper"
    const val CAMERA_PERMISSION_REQUEST = 1001
    
    /**
     * Data class for board QR code data
     */
    data class BoardQRData(
        val boardId: String,
        val macAddress: String,
        val password: String? = null,
        val nickname: String? = null
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("boardId", boardId)
                put("macAddress", macAddress)
                password?.let { put("password", it) }
                nickname?.let { put("nickname", it) }
            }.toString()
        }
        
        companion object {
            fun fromJson(json: String): BoardQRData? {
                return try {
                    val obj = JSONObject(json)
                    BoardQRData(
                        boardId = obj.getString("boardId"),
                        macAddress = obj.getString("macAddress"),
                        password = obj.optString("password").takeIf { it.isNotEmpty() },
                        nickname = obj.optString("nickname").takeIf { it.isNotEmpty() }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing QR data", e)
                    null
                }
            }
        }
    }
    
    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request camera permission
     */
    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }
    
    /**
     * Start QR code scanner
     */
    fun startScanner(activity: Activity) {
        val integrator = IntentIntegrator(activity)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan Board QR Code")
        integrator.setCameraId(0)  // Use rear camera
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(false)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }
    
    /**
     * Generate QR code data string for a board
     */
    fun generateQRData(
        boardId: String,
        macAddress: String,
        password: String? = null,
        nickname: String? = null
    ): String {
        return BoardQRData(boardId, macAddress, password, nickname).toJson()
    }
    
    /**
     * Parse scanned QR code result
     */
    fun parseQRResult(contents: String?): BoardQRData? {
        if (contents.isNullOrEmpty()) return null
        return BoardQRData.fromJson(contents)
    }
    
    /**
     * Validate QR code data
     */
    fun isValidBoardQR(data: BoardQRData?): Boolean {
        if (data == null) return false
        
        // Validate board ID format (LASTDROP-xxxx)
        if (!data.boardId.startsWith("LASTDROP-")) return false
        
        // Validate MAC address format (AA:BB:CC:DD:EE:FF)
        val macPattern = Regex("^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$")
        if (!macPattern.matches(data.macAddress)) return false
        
        return true
    }
    
    /**
     * Generate printable QR code instructions
     */
    fun generateQRInstructions(boardId: String): String {
        return """
            Board QR Code Setup Instructions
            
            1. Visit: https://www.qr-code-generator.com/
            2. Select "Text" type
            3. Paste this JSON:
            
            {
              "boardId": "$boardId",
              "macAddress": "YOUR_BOARD_MAC_ADDRESS",
              "password": "YOUR_BOARD_PASSWORD",
              "nickname": "Optional Board Name"
            }
            
            4. Generate QR code
            5. Download and print
            6. Laminate and attach to board case
            
            Players can scan to connect instantly!
        """.trimIndent()
    }
}
