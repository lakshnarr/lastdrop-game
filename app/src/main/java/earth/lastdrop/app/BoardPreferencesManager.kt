package earth.lastdrop.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages saved board preferences including:
 * - Last connected board (auto-reconnect)
 * - Board nicknames (custom names)
 * - Saved board passwords (hashed)
 * - Board connection history
 */
class BoardPreferencesManager(context: Context) {
    
    companion object {
        private const val TAG = "BoardPreferences"
        private const val PREF_NAME = "lastdrop_boards"
        
        // Keys
        private const val KEY_LAST_BOARD_ID = "last_board_id"
        private const val KEY_LAST_BOARD_MAC = "last_board_mac"
        private const val KEY_SAVED_BOARDS = "saved_boards"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Data class for saved board information
     */
    data class SavedBoard(
        val boardId: String,
        val macAddress: String,
        val nickname: String? = null,
        val passwordHash: String? = null,
        val lastConnected: Long = 0L,
        val connectionCount: Int = 0
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("boardId", boardId)
                put("macAddress", macAddress)
                put("nickname", nickname ?: "")
                put("passwordHash", passwordHash ?: "")
                put("lastConnected", lastConnected)
                put("connectionCount", connectionCount)
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): SavedBoard {
                return SavedBoard(
                    boardId = json.getString("boardId"),
                    macAddress = json.getString("macAddress"),
                    nickname = json.optString("nickname").takeIf { it.isNotEmpty() },
                    passwordHash = json.optString("passwordHash").takeIf { it.isNotEmpty() },
                    lastConnected = json.optLong("lastConnected", 0L),
                    connectionCount = json.optInt("connectionCount", 0)
                )
            }
        }
    }
    
    /**
     * Save last connected board for auto-reconnect
     */
    fun saveLastBoard(boardId: String, macAddress: String) {
        prefs.edit().apply {
            putString(KEY_LAST_BOARD_ID, boardId)
            putString(KEY_LAST_BOARD_MAC, macAddress)
            apply()
        }
        Log.d(TAG, "Saved last board: $boardId ($macAddress)")
    }
    
    /**
     * Get last connected board
     */
    fun getLastBoard(): Pair<String, String>? {
        val boardId = prefs.getString(KEY_LAST_BOARD_ID, null)
        val macAddress = prefs.getString(KEY_LAST_BOARD_MAC, null)
        
        return if (boardId != null && macAddress != null) {
            Pair(boardId, macAddress)
        } else {
            null
        }
    }
    
    /**
     * Clear last board (e.g., when user explicitly disconnects)
     */
    fun clearLastBoard() {
        prefs.edit().apply {
            remove(KEY_LAST_BOARD_ID)
            remove(KEY_LAST_BOARD_MAC)
            apply()
        }
        Log.d(TAG, "Cleared last board")
    }
    
    /**
     * Save board to saved boards list
     */
    fun saveBoard(
        boardId: String,
        macAddress: String,
        nickname: String? = null,
        password: String? = null
    ) {
        val boards = getSavedBoards().toMutableList()
        
        // Remove existing entry if present
        boards.removeAll { it.boardId == boardId }
        
        // Create new entry
        val newBoard = SavedBoard(
            boardId = boardId,
            macAddress = macAddress,
            nickname = nickname,
            passwordHash = password?.let { hashPassword(it) },
            lastConnected = System.currentTimeMillis(),
            connectionCount = boards.find { it.boardId == boardId }?.connectionCount?.plus(1) ?: 1
        )
        
        boards.add(0, newBoard)  // Add to front (most recent)
        
        // Save to preferences
        saveBoardsList(boards)
        
        Log.d(TAG, "Saved board: $boardId with nickname: ${nickname ?: "none"}")
    }
    
    /**
     * Get all saved boards
     */
    fun getSavedBoards(): List<SavedBoard> {
        val json = prefs.getString(KEY_SAVED_BOARDS, null) ?: return emptyList()
        
        return try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i ->
                SavedBoard.fromJson(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved boards", e)
            emptyList()
        }
    }
    
    /**
     * Get saved board by ID
     */
    fun getSavedBoard(boardId: String): SavedBoard? {
        return getSavedBoards().find { it.boardId == boardId }
    }
    
    /**
     * Get saved board by MAC address
     */
    fun getSavedBoardByMac(macAddress: String): SavedBoard? {
        return getSavedBoards().find { it.macAddress == macAddress }
    }
    
    /**
     * Update board nickname
     */
    fun updateBoardNickname(boardId: String, nickname: String) {
        val boards = getSavedBoards().toMutableList()
        val index = boards.indexOfFirst { it.boardId == boardId }
        
        if (index >= 0) {
            boards[index] = boards[index].copy(nickname = nickname)
            saveBoardsList(boards)
            Log.d(TAG, "Updated nickname for $boardId: $nickname")
        }
    }
    
    /**
     * Update board connection timestamp
     */
    fun updateBoardConnection(boardId: String) {
        val boards = getSavedBoards().toMutableList()
        val index = boards.indexOfFirst { it.boardId == boardId }
        
        if (index >= 0) {
            val board = boards[index]
            boards[index] = board.copy(
                lastConnected = System.currentTimeMillis(),
                connectionCount = board.connectionCount + 1
            )
            
            // Move to front (most recent)
            boards.add(0, boards.removeAt(index))
            saveBoardsList(boards)
        }
    }
    
    /**
     * Forget a board (remove from saved list)
     */
    fun forgetBoard(boardId: String) {
        val boards = getSavedBoards().toMutableList()
        boards.removeAll { it.boardId == boardId }
        saveBoardsList(boards)
        
        // Clear last board if it's the one being forgotten
        val lastBoard = getLastBoard()
        if (lastBoard?.first == boardId) {
            clearLastBoard()
        }
        
        Log.d(TAG, "Forgot board: $boardId")
    }
    
    /**
     * Get board nickname (returns board ID if no nickname set)
     */
    fun getBoardNickname(boardId: String): String {
        return getSavedBoard(boardId)?.nickname ?: boardId
    }
    
    /**
     * Check if board has saved password
     */
    fun hasSavedPassword(boardId: String): Boolean {
        return getSavedBoard(boardId)?.passwordHash != null
    }
    
    /**
     * Verify password against saved hash
     */
    fun verifyPassword(boardId: String, password: String): Boolean {
        val savedHash = getSavedBoard(boardId)?.passwordHash ?: return false
        return hashPassword(password) == savedHash
    }
    
    /**
     * Get saved password hash (for auto-login)
     */
    fun getSavedPasswordHash(boardId: String): String? {
        return getSavedBoard(boardId)?.passwordHash
    }
    
    /**
     * Clear all saved boards
     */
    fun clearAllBoards() {
        prefs.edit().apply {
            remove(KEY_SAVED_BOARDS)
            remove(KEY_LAST_BOARD_ID)
            remove(KEY_LAST_BOARD_MAC)
            apply()
        }
        Log.d(TAG, "Cleared all saved boards")
    }
    
    /**
     * Get boards sorted by last connection time
     */
    fun getBoardsSortedByRecent(): List<SavedBoard> {
        return getSavedBoards().sortedByDescending { it.lastConnected }
    }
    
    /**
     * Get boards sorted by connection count
     */
    fun getBoardsSortedByUsage(): List<SavedBoard> {
        return getSavedBoards().sortedByDescending { it.connectionCount }
    }
    
    // ==================== Private Helpers ====================
    
    private fun saveBoardsList(boards: List<SavedBoard>) {
        val jsonArray = JSONArray()
        boards.forEach { jsonArray.put(it.toJson()) }
        
        prefs.edit().apply {
            putString(KEY_SAVED_BOARDS, jsonArray.toString())
            apply()
        }
    }
    
    /**
     * Simple password hashing (use proper crypto in production)
     */
    private fun hashPassword(password: String): String {
        // Using SHA-256 for demo - in production use proper key derivation (bcrypt, scrypt)
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing password", e)
            password.hashCode().toString()  // Fallback
        }
    }
}
