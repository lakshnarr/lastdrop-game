package com.example.lastdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.view.View
import android.widget.TextView
import android.widget.Button
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast

/**
 * Generates QR codes for spectators to scan and view live.html
 * 
 * Creates a QR code containing the live.html URL with session ID
 * Example: https://lastdrop.earth/live.html?session=abc-123-xyz
 */
object LiveQRGenerator {
    
    private const val LIVE_URL_BASE = "https://lastdrop.earth/live.html"
    
    /**
     * Generate QR code bitmap for live.html URL with session
     * @param sessionId Unique game session ID
     * @param size QR code size in pixels (default 512x512)
     * @return Bitmap of QR code, or null if generation fails
     */
    fun generateQRCode(sessionId: String, size: Int = 512): Bitmap? {
        return try {
            val url = "$LIVE_URL_BASE?session=$sessionId"
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)
            
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Show QR code dialog with session info and copy button
     * @param context Android context
     * @param sessionId Unique game session ID
     * @param boardId Optional ESP32 board ID (e.g., "LASTDROP-0001")
     */
    fun showQRCodeDialog(context: Context, sessionId: String, boardId: String? = null) {
        val dialogView = View.inflate(context, android.R.layout.simple_list_item_1, null)
        
        // Create custom dialog layout
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        
        // Title
        val titleText = TextView(context).apply {
            text = "📱 Spectator View"
            textSize = 20f
            setTextColor(Color.parseColor("#1F2937"))
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(titleText)
        
        // Instructions
        val instructionsText = TextView(context).apply {
            text = "Scan this QR code with your phone to watch the game live!"
            textSize = 14f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(instructionsText)
        
        // QR Code Image
        val qrImageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                512, 512
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setPadding(20, 20, 20, 20)
        }
        
        val qrBitmap = generateQRCode(sessionId, 512)
        if (qrBitmap != null) {
            qrImageView.setImageBitmap(qrBitmap)
        } else {
            qrImageView.setImageResource(android.R.drawable.ic_dialog_alert)
        }
        layout.addView(qrImageView)
        
        // URL Text (for manual entry)
        val url = "$LIVE_URL_BASE?session=$sessionId"
        val urlText = TextView(context).apply {
            text = url
            textSize = 12f
            setTextColor(Color.parseColor("#3B82F6"))
            setPadding(20, 20, 20, 10)
            gravity = android.view.Gravity.CENTER
            setTextIsSelectable(true)
        }
        layout.addView(urlText)
        
        // Session ID
        val sessionText = TextView(context).apply {
            text = "Session: ${sessionId.substring(0, 8)}..."
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 0, 0, 10)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(sessionText)
        
        // Board ID (if available)
        if (boardId != null) {
            val boardText = TextView(context).apply {
                text = "Board: $boardId"
                textSize = 11f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, 0, 0, 20)
                gravity = android.view.Gravity.CENTER
            }
            layout.addView(boardText)
        }
        
        // Copy URL Button
        val copyButton = Button(context).apply {
            text = "📋 Copy URL"
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Live URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(copyButton)
        
        // Show dialog
        AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
            .show()
    }
    
    /**
     * Get the live.html URL for a session
     * @param sessionId Unique game session ID
     * @return Complete URL string
     */
    fun getLiveUrl(sessionId: String): String {
        return "$LIVE_URL_BASE?session=$sessionId"
    }
}
