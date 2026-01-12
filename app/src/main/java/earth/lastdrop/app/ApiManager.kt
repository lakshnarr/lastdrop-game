package earth.lastdrop.app

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles all HTTP API communication with lastdrop.earth server
 * Extracts networking logic from MainActivity to keep it manageable
 */
class ApiManager(
    private val apiBaseUrl: String,
    private val apiKey: String,
    private var sessionId: String
) {
    companion object {
        private const val TAG = "ApiManager"
        private const val DEFAULT_TIMEOUT_MS = 3000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    /**
     * Update session ID (e.g., when connecting to scanned QR code session)
     */
    fun setSessionId(newSessionId: String) {
        Log.d(TAG, "Session ID updated from $sessionId to $newSessionId")
        sessionId = newSessionId
    }

    /**
     * Start periodic heartbeat to keep session alive on server
     * Server counts active games based on recent heartbeat activity
     */
    fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    // Send heartbeat every 30 seconds
                    delay(30_000)
                    sendHeartbeat()
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error", e)
                }
            }
        }
        Log.d(TAG, "Heartbeat started for session: $sessionId")
    }
    
    /**
     * Send immediate heartbeat (used when connecting to live server)
     */
    fun sendImmediateHeartbeat() {
        scope.launch {
            sendHeartbeat()
        }
    }

    /**
     * Stop heartbeat when game ends
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped for session: $sessionId")
    }

    /**
     * Send heartbeat ping to server
     */
    private suspend fun sendHeartbeat() {
        try {
            val url = URL("$apiBaseUrl/heartbeat.php?key=$apiKey&session=$sessionId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_TIMEOUT_MS
                readTimeout = DEFAULT_TIMEOUT_MS
            }
            val code = conn.responseCode
            if (code == 200) {
                Log.d(TAG, "Heartbeat sent successfully")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
        }
    }

    /**
     * Simple ping to check server connectivity
     */
    fun pingServer() {
        scope.launch {
            try {
                val urlString = "$apiBaseUrl/ping.php?key=$apiKey"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }

                Log.d(TAG, "Ping response code: $code, body: $body")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pinging server", e)
            }
        }
    }

    /**
     * Push complete reset state to server (all players back to tile 1, score 10)
     */
    fun pushResetState(
        playerNames: List<String>,
        playerColors: List<String>,
        playerCount: Int
    ) {
        scope.launch {
            try {
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                // Build players array with reset values
                val playersJson = JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val color = playerColors[index]
                        val obj = JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", 1)  // Start position is tile 1
                            put("score", 10)
                            put("eliminated", false)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                // Clear last event (no dice roll yet)
                val lastEventJson = JSONObject().apply {
                    put("playerId", "")
                    put("playerName", "")
                    put("dice1", JSONObject.NULL)
                    put("dice2", JSONObject.NULL)
                    put("avg", JSONObject.NULL)
                    put("tileIndex", 1)  // Start position is tile 1
                    put("tileName", "")
                    put("tileType", "")
                    put("chanceCardId", JSONObject.NULL)
                    put("chanceCardText", "")
                    put("rolling", false)
                    put("reset", true) // Flag indicating this is a reset
                }

                val root = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")  // Optional board ID
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Reset state pushed, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing reset state", e)
            }
        }
    }

    /**
     * Send a dice roll event to cloud for historical tracking
     * Returns true if successful
     */
    fun sendRollToCloud(
        playerName: String,
        modeTwoDice: Boolean,
        dice1: Int?,
        dice2: Int?,
        avg: Int
    ): Boolean {
        return try {
            val encodedPlayer = URLEncoder.encode(playerName, "UTF-8")
            val base = "$apiBaseUrl/register_drop.php"
            val sb = StringBuilder()
            sb.append("$base?key=$apiKey")
            sb.append("&player=$encodedPlayer")
            sb.append("&mode=" + if (modeTwoDice) "2" else "1")
            sb.append("&avg=$avg")
            if (dice1 != null) sb.append("&dice1=$dice1")
            if (dice2 != null) sb.append("&dice2=$dice2")

            val url = URL(sb.toString())
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_TIMEOUT_MS
                readTimeout = DEFAULT_TIMEOUT_MS
            }
            val code = conn.responseCode
            conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error sending roll to cloud", e)
            false
        }
    }

    /**
     * Push rolling status while dice is tumbling (before final value known)
     */
    fun pushRollingStatus(
        playerNames: List<String>,
        playerColors: List<String>,
        playerPositions: Map<String, Int>,
        playerScores: Map<String, Int>,
        playerCount: Int,
        currentPlayer: Int,
        playWithTwoDice: Boolean,
        diceColorMap: Map<Int, String>,
        diceRollingStatus: Map<Int, Boolean>,
        lastDice1: Int?,
        lastDice2: Int?,
        lastAvg: Int?
    ) {
        scope.launch {
            try {
                val playerIndex = currentPlayer.coerceIn(0, playerCount - 1)
                val playerName = playerNames[playerIndex]
                val playerId = "p${playerIndex + 1}"

                // Get dice colors (for 2-dice mode, get both individual colors)
                val diceColor1: String
                val diceColor2: String?

                if (playWithTwoDice && diceColorMap.size >= 2) {
                    val sortedDiceIds = diceColorMap.keys.sorted()
                    diceColor1 = diceColorMap[sortedDiceIds[0]] ?: playerColors[playerIndex]
                    diceColor2 = diceColorMap[sortedDiceIds.getOrNull(1)] ?: playerColors[playerIndex]
                } else {
                    diceColor1 = diceColorMap.values.firstOrNull() ?: playerColors[playerIndex]
                    diceColor2 = null
                }

                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                // Check which specific dice are rolling
                val rollingDice = diceRollingStatus.filter { it.value }.keys.sorted()

                // Build players array with CURRENT positions (before dice lands)
                val playersJson = JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 1
                        val score = playerScores[name] ?: 10
                        val color = playerColors[index]
                        val obj = JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                val payload = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("players", playersJson)
                    put("lastEvent", JSONObject().apply {
                        put("playerId", playerId)
                        put("playerName", playerName)
                        put("rolling", true)
                        put("diceColor1", diceColor1)
                        if (diceColor2 != null) {
                            put("diceColor2", diceColor2)
                        }
                        // Send current dice values (may be partial in 2-dice mode)
                        if (lastDice1 != null) {
                            put("dice1", lastDice1)
                        } else {
                            put("dice1", JSONObject.NULL)
                        }
                        if (lastDice2 != null) {
                            put("dice2", lastDice2)
                        } else {
                            put("dice2", JSONObject.NULL)
                        }
                        if (lastAvg != null) {
                            put("avg", lastAvg)
                        } else {
                            put("avg", JSONObject.NULL)
                        }
                        // Include which dice are rolling (useful for debugging)
                        put("rollingDiceCount", rollingDice.size)
                        if (playWithTwoDice) {
                            put("dice1Rolling", diceRollingStatus[rollingDice.getOrNull(0)] ?: false)
                            put("dice2Rolling", diceRollingStatus[rollingDice.getOrNull(1)] ?: false)
                        }
                    })
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Rolling status sent, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing rolling status", e)
            }
        }
    }

    /**
     * Push complete game state after a turn completes
     */
    fun pushLiveState(
        playerNames: List<String>,
        playerColors: List<String>,
        playerPositions: Map<String, Int>,
        playerScores: Map<String, Int>,
        playerCount: Int,
        currentPlayer: Int,
        playWithTwoDice: Boolean,
        diceColorMap: Map<Int, String>,
        lastDice1: Int?,
        lastDice2: Int?,
        lastAvg: Int?,
        lastTileName: String?,
        lastTileType: String?,
        lastChanceCardNumber: Int?,
        lastChanceCardText: String?,
        rolling: Boolean = false,
        eventType: String? = null,
        eventMessage: String? = null,
        playerSkipPenalty: Map<String, Boolean> = emptyMap(),
        playerWaterShield: Map<String, Boolean> = emptyMap()
    ) {
        scope.launch {
            try {
                val lastAvgLocal = lastAvg ?: return@launch  // nothing to send yet

                // Player who just played (currentPlayer has already advanced)
                val playerIndex = (currentPlayer - 1 + playerCount) % playerCount
                val playerName = playerNames[playerIndex]
                val playerId = "p${playerIndex + 1}"

                // Get dice colors (for 2-dice mode, get both individual colors)
                val diceColor1: String
                val diceColor2: String?

                if (playWithTwoDice && diceColorMap.size >= 2) {
                    // 2-dice mode: get individual colors for each die
                    val sortedDiceIds = diceColorMap.keys.sorted()
                    diceColor1 = diceColorMap[sortedDiceIds[0]] ?: playerColors[playerIndex]
                    diceColor2 = diceColorMap[sortedDiceIds.getOrNull(1)] ?: playerColors[playerIndex]
                } else {
                    // 1-die mode or fallback: use first available color
                    diceColor1 = diceColorMap.values.firstOrNull() ?: playerColors[playerIndex]
                    diceColor2 = null
                }

                // 1) Build players array
                val playersJson = JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 0
                        val score = playerScores[name] ?: 0
                        val color = playerColors[index]
                        val skipPenalty = playerSkipPenalty[name] ?: false
                        val waterShield = playerWaterShield[name] ?: false
                        val obj = JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                            put("skipPenalty", skipPenalty)
                            put("waterShield", waterShield)
                        }
                        put(obj)
                    }
                }

                // 2) Build lastEvent object
                val lastEventJson = JSONObject().apply {
                    put("playerId", playerId)
                    put("playerName", playerName)
                    put("dice1", lastDice1)
                    put("dice2", lastDice2)
                    put("avg", lastAvgLocal)
                    put("tileIndex", playerPositions[playerName] ?: 0)
                    put("tileName", lastTileName ?: "")
                    put("tileType", lastTileType ?: "")
                    put("chanceCardId", lastChanceCardNumber ?: JSONObject.NULL)
                    put("chanceCardText", lastChanceCardText ?: "")
                    put("rolling", rolling)
                    put("diceColor1", diceColor1)
                    if (diceColor2 != null) {
                        put("diceColor2", diceColor2)
                    }
                        if (eventType != null) put("eventType", eventType) else put("eventType", JSONObject.NULL)
                        if (eventMessage != null) put("eventMessage", eventMessage) else put("eventMessage", JSONObject.NULL)
                }

                // 3) Wrap into root JSON
                val root = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                // 4) POST to live_push.php
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Live push response code: $code")
                conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing live state", e)
            }
        }
    }

    /**
     * Push current game state on initial server connection (syncs live.html immediately)
     * Unlike pushResetState, this sends ACTUAL positions and scores
     */
    fun pushCurrentState(
        playerNames: List<String>,
        playerColors: List<String>,
        playerPositions: Map<String, Int>,
        playerScores: Map<String, Int>,
        playerCount: Int,
        currentPlayer: Int
    ) {
        scope.launch {
            try {
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                // Build players array with ACTUAL current values
                val playersJson = JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 1
                        val score = playerScores[name] ?: 10
                        val color = playerColors.getOrElse(index) { "FF0000" }
                        val obj = JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                // Create lastEvent that signals initial sync (no dice roll yet)
                val currentPlayerName = playerNames.getOrElse(currentPlayer) { "" }
                val lastEventJson = JSONObject().apply {
                    put("playerId", "p${currentPlayer + 1}")
                    put("playerName", currentPlayerName)
                    put("dice1", JSONObject.NULL)
                    put("dice2", JSONObject.NULL)
                    put("avg", JSONObject.NULL)
                    put("tileIndex", playerPositions[currentPlayerName] ?: 1)
                    put("tileName", "")
                    put("tileType", "")
                    put("chanceCardId", JSONObject.NULL)
                    put("chanceCardText", "")
                    put("rolling", false)
                    put("initialSync", true) // Flag indicating this is an initial sync
                }

                val root = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Current state pushed (initial sync), response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing current state", e)
            }
        }
    }

    /**
     * Push undo state to server (after undoing a move)
     */
    fun pushUndoState(
        playerNames: List<String>,
        playerColors: List<String>,
        playerPositions: Map<String, Int>,
        playerScores: Map<String, Int>,
        playerCount: Int
    ) {
        scope.launch {
            try {
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                // Build players array with current (post-undo) values
                val playersJson = JSONArray().apply {
                    playerNames.take(playerCount).forEachIndexed { index, name ->
                        val pos = playerPositions[name] ?: 1
                        val score = playerScores[name] ?: 10
                        val color = playerColors[index]
                        val obj = JSONObject().apply {
                            put("id", "p${index + 1}")
                            put("name", name)
                            put("pos", pos)
                            put("score", score)
                            put("eliminated", score <= 0)
                            put("color", color)
                        }
                        put(obj)
                    }
                }

                // Clear last event for undo
                val lastEventJson = JSONObject().apply {
                    put("playerId", "")
                    put("playerName", "")
                    put("dice1", JSONObject.NULL)
                    put("dice2", JSONObject.NULL)
                    put("avg", JSONObject.NULL)
                    put("tileIndex", JSONObject.NULL)
                    put("tileName", "")
                    put("tileType", "")
                    put("chanceCardId", JSONObject.NULL)
                    put("chanceCardText", "")
                    put("rolling", false)
                    put("undo", true) // Flag indicating this is an undo
                }

                val root = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("players", playersJson)
                    put("lastEvent", lastEventJson)
                }

                conn.outputStream.use { os ->
                    os.write(root.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Undo state pushed, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing undo state", e)
            }
        }
    }
    
    /**
     * Push chance card selection state for live.html display
     * Shows the 6 available cards and waits for dice roll selection
     */
    fun pushChanceSelection(
        playerName: String,
        cardNumbers: List<Int>,
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        scope.launch {
            try {
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                val payload = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("chanceSelection", JSONObject().apply {
                        put("active", true)
                        put("playerName", playerName)
                        put("cardNumbers", JSONArray(cardNumbers))
                    })
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Chance selection pushed, response code: $code")
                conn.disconnect()
                
                withContext(Dispatchers.Main) {
                    if (code in 200..299) {
                        onSuccess()
                    } else {
                        onError(Exception("Server returned code $code"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing chance selection", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    
    /**
     * Clear chance selection state (after card is selected)
     */
    fun clearChanceSelection() {
        scope.launch {
            try {
                val url = URL("$apiBaseUrl/live_push.php?key=$apiKey&session=$sessionId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = DEFAULT_TIMEOUT_MS
                    readTimeout = DEFAULT_TIMEOUT_MS
                }

                val payload = JSONObject().apply {
                    put("apiKey", apiKey)
                    put("sessionId", sessionId)
                    put("boardId", "ANDROID-APP")
                    put("chanceSelection", JSONObject().apply {
                        put("active", false)
                    })
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d(TAG, "Chance selection cleared, response code: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing chance selection", e)
            }
        }
    }

    /**
     * Cleanup coroutine scope when done
     */
    fun cleanup() {
        stopHeartbeat()
        scope.cancel()
    }
}
