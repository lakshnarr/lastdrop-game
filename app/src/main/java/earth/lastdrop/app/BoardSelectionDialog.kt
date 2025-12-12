package earth.lastdrop.app

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

/**
 * Dialog helper for board selection with nickname and saved board support
 */
object BoardSelectionDialog {
    
    private var currentDialog: AlertDialog? = null
    private var currentAdapter: ArrayAdapter<String>? = null
    private val boardsList = mutableListOf<BluetoothDevice>()
    
    /**
     * Show live scanning dialog that updates as boards are found
     */
    fun showLiveScanDialog(
        context: Context,
        preferencesManager: BoardPreferencesManager,
        onBoardSelected: (BluetoothDevice) -> Unit,
        onCancel: () -> Unit
    ): LiveScanDialog {
        boardsList.clear()
        
        val listView = ListView(context).apply {
            setPadding(20, 20, 20, 20)
        }
        
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mutableListOf("🔍 Scanning for boards..."))
        listView.adapter = adapter
        currentAdapter = adapter
        
        val dialog = AlertDialog.Builder(context)
            .setTitle("Select LASTDROP Board")
            .setView(listView)
            .setNegativeButton("Cancel") { _, _ ->
                currentDialog = null
                currentAdapter = null
                onCancel()
            }
            .setCancelable(true)
            .create()
        
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position < boardsList.size) {
                val board = boardsList[position]
                dialog.dismiss()
                currentDialog = null
                currentAdapter = null
                onBoardSelected(board)
            }
        }
        
        dialog.show()
        currentDialog = dialog
        
        return LiveScanDialog(adapter, preferencesManager)
    }
    
    /**
     * Helper class for live scan dialog updates
     */
    class LiveScanDialog(
        private val adapter: ArrayAdapter<String>,
        private val preferencesManager: BoardPreferencesManager
    ) {
        fun addBoard(device: BluetoothDevice) {
            // Check for duplicates by MAC address
            if (boardsList.any { it.address == device.address }) {
                return  // Already in list
            }
            
            // Remove "Scanning..." message if it's the first board
            if (boardsList.isEmpty() && adapter.count > 0 && adapter.getItem(0)?.contains("Scanning") == true) {
                adapter.clear()
            }
            
            boardsList.add(device)
            
            val boardId = device.name ?: "Unknown"
            val nickname = preferencesManager.getBoardNickname(boardId)
            val savedBoard = preferencesManager.getSavedBoard(boardId)
            
            val displayText = buildString {
                if (nickname != boardId) {
                    append("$nickname\n  $boardId")
                } else {
                    append(boardId)
                }
                
                if (savedBoard?.passwordHash != null) append(" 🔑")
                append("\n  ${device.address}")
            }
            
            adapter.add(displayText)
            adapter.notifyDataSetChanged()
        }
        
        fun onScanComplete() {
            if (boardsList.isEmpty()) {
                // No boards found after scan complete
                adapter.clear()
                adapter.add("❌ No boards found")
                adapter.add("")
                adapter.add("Check:")
                adapter.add("• Board is powered on")
                adapter.add("• Bluetooth enabled")
                adapter.add("• Close to board")
            } else {
                // Update scanning message to "Scan complete"
                // Keep existing board list, just remove scanning indicator
            }
            adapter.notifyDataSetChanged()
        }
        
        fun showNoBoards() {
            adapter.clear()
            adapter.add("❌ No boards found")
            adapter.add("\nCheck:")
            adapter.add("• Board is powered on")
            adapter.add("• Bluetooth enabled")
            adapter.add("• Close to board")
            adapter.notifyDataSetChanged()
        }
        
        fun dismiss() {
            currentDialog?.dismiss()
            currentDialog = null
            currentAdapter = null
        }
    }
    
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
