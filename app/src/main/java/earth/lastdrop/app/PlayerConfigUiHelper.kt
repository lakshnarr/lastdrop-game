package earth.lastdrop.app

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Legacy player configuration dialogs (player count and per-player setup).
 */
object PlayerConfigUiHelper {

    fun showPlayerCountDialog(
        activity: Activity,
        defaultIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val options = arrayOf("2 players", "3 players", "4 players")
        var chosenIndex = defaultIndex

        AlertDialog.Builder(activity)
            .setTitle("How many players?")
            .setSingleChoiceItems(options, chosenIndex) { _, which ->
                chosenIndex = which
            }
            .setPositiveButton("OK") { dialog, _ ->
                onSelected(chosenIndex + 2) // map 0->2,1->3,2->4
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    fun showPlayerSetupDialog(
        activity: Activity,
        playerIndex: Int,
        availableColorNames: List<String>,
        availableColors: List<String>,
        onConfigured: (name: String, color: String) -> Unit
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val nameLabel = TextView(activity).apply {
            text = "Player ${playerIndex + 1} Name:"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }

        val nameInput = EditText(activity).apply {
            hint = "Enter name"
            setPadding(20, 20, 20, 20)
        }

        val colorLabel = TextView(activity).apply {
            text = "Choose Token Color:"
            textSize = 14f
            setPadding(0, 24, 0, 8)
        }

        val colorSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                availableColorNames
            )
            setSelection(0)
        }

        container.addView(nameLabel)
        container.addView(nameInput)
        container.addView(colorLabel)
        container.addView(colorSpinner)

        AlertDialog.Builder(activity)
            .setTitle("Player ${playerIndex + 1} Setup")
            .setView(container)
            .setPositiveButton("OK") { dialog, _ ->
                val name = nameInput.text.toString().trim()
                val selectedColorIndex = colorSpinner.selectedItemPosition
                val chosenColor = availableColors[selectedColorIndex]
                onConfigured(name, chosenColor)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
}
