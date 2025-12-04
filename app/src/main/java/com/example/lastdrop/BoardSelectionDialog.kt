package com.example.lastdrop

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.text.InputType
import android.widget.EditText

/**
 * Dialog helper for board selection with nickname and saved board support
 */
object BoardSelectionDialog {
    
    /**
     * Show board selection dialog with nickname support
     */
    fun show(
        context: Context,
        boards: List<BluetoothDevice>,
        preferencesManager: BoardPreferencesManager,
        onBoardSelected: (BluetoothDevice) -> Unit,
        onRescan: () -> Unit,
        onManageBoards: (() -> Unit)? = null
    ) {
        if (boards.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("No Boards Found")
                .setMessage("No LASTDROP boards discovered.\n\nCheck:\n• Board is powered on\n• Bluetooth enabled\n• Close to board")
                .setPositiveButton("Rescan") { _, _ -> onRescan() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        
        if (boards.size == 1) {
            onBoardSelected(boards[0])
            return
        }
        
        // Display boards with nicknames
        val displayItems = boards.map { device ->
            val boardId = device.name ?: "Unknown"
            val nickname = preferencesManager.getBoardNickname(boardId)
            val savedBoard = preferencesManager.getSavedBoard(boardId)
            
            buildString {
                if (nickname != boardId) {
                    append("$nickname\n  $boardId")
                } else {
                    append(boardId)
                }
                
                if (savedBoard?.passwordHash != null) append(" 🔑")
                if (savedBoard != null && savedBoard.connectionCount > 0) {
                    append(" (${savedBoard.connectionCount}×)")
                }
                append("\n  ${device.address}")
            }
        }.toTypedArray()
        
        val builder = AlertDialog.Builder(context)
            .setTitle("Select Board (${boards.size})")
            .setItems(displayItems) { dialog, which ->
                onBoardSelected(boards[which])
                dialog.dismiss()
            }
            .setNeutralButton("Rescan") { _, _ -> onRescan() }
            .setNegativeButton("Cancel", null)
        
        if (onManageBoards != null) {
            builder.setPositiveButton("Manage") { _, _ -> onManageBoards() }
        }
        
        builder.show()
    }
    
    /**
     * Show board nickname dialog
     */
    fun showNicknameDialog(
        context: Context,
        currentNickname: String?,
        boardId: String,
        onNicknameSaved: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(currentNickname ?: "")
            hint = "e.g., Game Room A"
        }
        
        AlertDialog.Builder(context)
            .setTitle("Board Nickname")
            .setMessage("Name for $boardId")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val nickname = input.text.toString().trim()
                if (nickname.isNotEmpty()) onNicknameSaved(nickname)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show saved boards dialog
     */
    fun showSavedBoardsDialog(
        context: Context,
        preferencesManager: BoardPreferencesManager,
        onBoardSelected: (String, String) -> Unit,
        onForgetBoard: (String) -> Unit,
        onEditNickname: (String) -> Unit
    ) {
        val savedBoards = preferencesManager.getBoardsSortedByRecent()
        
        if (savedBoards.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("No Saved Boards")
                .setMessage("Boards saved after first connection")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val displayItems = savedBoards.map { board ->
            buildString {
                append(board.nickname ?: board.boardId)
                if (board.nickname != null) append("\n  ${board.boardId}")
                append("\n  ${board.macAddress}")
                if (board.passwordHash != null) append(" 🔑")
                append(" • ${board.connectionCount}× used")
            }
        }.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle("Saved Boards")
            .setItems(displayItems) { _, which ->
                val board = savedBoards[which]
                showBoardActionDialog(
                    context, board,
                    onConnect = { onBoardSelected(board.boardId, board.macAddress) },
                    onEditNickname = { onEditNickname(board.boardId) },
                    onForget = { onForgetBoard(board.boardId) }
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    /**
     * Show actions for saved board
     */
    private fun showBoardActionDialog(
        context: Context,
        board: BoardPreferencesManager.SavedBoard,
        onConnect: () -> Unit,
        onEditNickname: () -> Unit,
        onForget: () -> Unit
    ) {
        val name = board.nickname ?: board.boardId
        
        AlertDialog.Builder(context)
            .setTitle(name)
            .setItems(arrayOf("Connect", "Edit Nickname", "Forget Board", "Cancel")) { dialog, which ->
                when (which) {
                    0 -> onConnect()
                    1 -> onEditNickname()
                    2 -> AlertDialog.Builder(context)
                        .setTitle("Forget Board?")
                        .setMessage("Remove $name?\n\nPassword will be deleted.")
                        .setPositiveButton("Forget") { _, _ -> onForget() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    3 -> dialog.dismiss()
                }
            }
            .show()
    }
}
