package earth.lastdrop.app.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import earth.lastdrop.app.R

/**
 * DialogHelper - Centralized dialog management for consistent UI/UX
 * Reduces MainActivity bloat and ensures uniform styling
 */
object DialogHelper {
    
    fun showInputDialog(
        context: Context,
        title: String,
        message: String,
        hint: String,
        prefill: String = "",
        onConfirm: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            this.hint = hint
            setText(prefill)
            setSelectAllOnFocus(true)
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input)
        }
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Confirm") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    onConfirm(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun showTwoInputDialog(
        context: Context,
        title: String,
        message: String,
        hint1: String,
        hint2: String,
        prefill1: String = "",
        prefill2: String = "",
        onConfirm: (String, String) -> Unit
    ) {
        val input1 = EditText(context).apply {
            this.hint = hint1
            setText(prefill1)
        }
        
        val input2 = EditText(context).apply {
            this.hint = hint2
            setText(prefill2)
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(input1)
            addView(input2)
        }
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(container)
            .setPositiveButton("Confirm") { _, _ ->
                val text1 = input1.text.toString().trim()
                val text2 = input2.text.toString().trim()
                if (text1.isNotEmpty() && text2.isNotEmpty()) {
                    onConfirm(text1, text2)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "Confirm",
        negativeText: String = "Cancel",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton(negativeText, null)
            .show()
    }
    
    fun showInfoDialog(
        context: Context,
        title: String,
        message: String,
        buttonText: String = "OK"
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText, null)
            .show()
    }
    
    fun showErrorDialog(
        context: Context,
        title: String = "Error",
        message: String
    ) {
        AlertDialog.Builder(context)
            .setTitle("⚠️ $title")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    fun showGameOverDialog(
        context: Context,
        winnerName: String,
        summary: String,
        onNewGame: () -> Unit,
        onViewStats: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("🏆 Game Over!")
            .setMessage("Winner: $winnerName\n\n$summary")
            .setPositiveButton("New Game") { _, _ -> onNewGame() }
            .setNeutralButton("View Stats") { _, _ -> onViewStats() }
            .setCancelable(false)
            .show()
    }
    
    fun showChanceCardDialog(
        context: Context,
        cardNumber: Int,
        cardText: String,
        scoreChange: Int,
        onDismiss: () -> Unit
    ) {
        val effect = if (scoreChange > 0) "+$scoreChange drops" else "$scoreChange drops"
        val emoji = if (scoreChange > 0) "💧" else "⚠️"
        
        AlertDialog.Builder(context)
            .setTitle("$emoji Chance Card #$cardNumber")
            .setMessage("$cardText\n\nEffect: $effect")
            .setPositiveButton("Continue") { _, _ -> onDismiss() }
            .setCancelable(false)
            .show()
    }
    
    fun showPlayerSelectionDialog(
        context: Context,
        playerNames: List<String>,
        currentIndex: Int,
        onPlayerSelected: (Int) -> Unit
    ) {
        val items = playerNames.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle("Select Player")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                onPlayerSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun showColorSelectionDialog(
        context: Context,
        colors: List<String>,
        colorNames: List<String>,
        onColorSelected: (String, String) -> Unit
    ) {
        val items = colorNames.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle("Select Color")
            .setItems(items) { _, which ->
                onColorSelected(colors[which], colorNames[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun showWaitingDialog(
        context: Context,
        title: String,
        message: String
    ): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .create()
            .apply { show() }
    }
    
    fun showListDialog(
        context: Context,
        title: String,
        items: List<String>,
        onItemSelected: (Int, String) -> Unit
    ) {
        val itemsArray = items.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(itemsArray) { _, which ->
                onItemSelected(which, items[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
