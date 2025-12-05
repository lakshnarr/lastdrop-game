package earth.lastdrop.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * GameTracker - Tracks game progress and records results to profile system
 * Integrates with ProfileManager to save game history for AI greetings
 */
class GameTracker(private val context: Context) {
    
    private val profileManager = ProfileManager(context)
    
    // Track game session data
    data class PlayerGameData(
        val profileId: String,
        val name: String,
        val nickname: String,
        val color: String,
        var isGuest: Boolean = false,
        
        // Game progress
        var position: Int = 1,
        var score: Int = 10,
        var isEliminated: Boolean = false,
        
        // Performance tracking
        var chanceCardsDrawn: MutableList<Int> = mutableListOf(),
        var droughtTileHits: Int = 0,
        var bonusTileHits: Int = 0,
        var waterDockHits: Int = 0,
        var maxComebackPoints: Int = 0,
        var lapsCompleted: Int = 0,
        var eliminatedOpponents: MutableList<String> = mutableListOf(),
        var hadPerfectStart: Boolean = true, // No disasters in first 5 turns
        var turnCount: Int = 0,
        var usedUndo: Boolean = false
    )
    
    private var gameId: String = UUID.randomUUID().toString()
    private var gameStartTime: Long = 0L
    private val players = mutableMapOf<String, PlayerGameData>()
    
    /**
     * Initialize new game with player profiles
     */
    fun startGame(playerProfiles: List<Pair<String, String>>) {
        gameId = UUID.randomUUID().toString()
        gameStartTime = System.currentTimeMillis()
        players.clear()
        
        playerProfiles.forEachIndexed { index, (profileId, color) ->
            val profile = runCatching {
                kotlinx.coroutines.runBlocking {
                    profileManager.getProfile(profileId)
                }
            }.getOrNull()
            
            if (profile != null) {
                players[profileId] = PlayerGameData(
                    profileId = profile.playerId,
                    name = profile.name,
                    nickname = profile.nickname,
                    color = color,
                    isGuest = profile.isGuest
                )
            }
        }
    }
    
    /**
     * Update player position and score after a turn
     */
    fun recordTurn(
        profileId: String,
        newPosition: Int,
        scoreChange: Int,
        tileType: TileType,
        chanceCardNumber: Int? = null
    ) {
        players[profileId]?.let { player ->
            player.turnCount++
            player.position = newPosition
            player.score += scoreChange
            
            // Track tile type hits
            when (tileType) {
                TileType.DISASTER -> {
                    player.droughtTileHits++
                    if (player.turnCount <= 5) {
                        player.hadPerfectStart = false
                    }
                }
                TileType.BONUS -> player.bonusTileHits++
                TileType.WATER_DOCK, TileType.SUPER_DOCK -> player.waterDockHits++
                else -> {}
            }
            
            // Track chance cards
            chanceCardNumber?.let {
                player.chanceCardsDrawn.add(it)
            }
            
            // Track comeback (score was negative, now positive)
            if (scoreChange > player.maxComebackPoints && player.score > 0) {
                player.maxComebackPoints = scoreChange
            }
            
            // Check elimination
            if (player.score <= 0) {
                player.isEliminated = true
            }
        }
    }
    
    /**
     * Record lap completion
     */
    fun recordLapCompletion(profileId: String) {
        players[profileId]?.lapsCompleted = (players[profileId]?.lapsCompleted ?: 0) + 1
    }
    
    /**
     * Record undo usage
     */
    fun recordUndo(profileId: String) {
        players[profileId]?.usedUndo = true
    }
    
    /**
     * Record elimination
     */
    fun recordElimination(eliminatorId: String, eliminatedId: String) {
        players[eliminatedId]?.isEliminated = true
        players[eliminatorId]?.eliminatedOpponents?.add(
            players[eliminatedId]?.name ?: "Unknown"
        )
    }
    
    /**
     * Check if game is over (someone won or all but one eliminated)
     */
    fun isGameOver(): Boolean {
        val activePlayers = players.values.count { !it.isEliminated }
        return activePlayers <= 1
    }
    
    /**
     * Record game completion to profile system
     */
    suspend fun recordGameCompletion() {
        if (players.isEmpty()) return
        
        withContext(Dispatchers.IO) {
            // Calculate placements
            val sortedPlayers = players.values.sortedWith(
                compareByDescending<PlayerGameData> { !it.isEliminated }
                    .thenByDescending { it.score }
                    .thenByDescending { it.position }
            )
            
            val gameTimeMinutes = ((System.currentTimeMillis() - gameStartTime) / 60000).toInt()
            
            // Build game results map
            val gameResults = sortedPlayers.mapIndexed { index, player ->
                player.profileId to GameResult(
                    name = player.name,
                    color = player.color,
                    score = player.score,
                    finalTile = player.position,
                    placement = index + 1,
                    dropsEarned = player.score,
                    gameTimeMinutes = gameTimeMinutes,
                    chanceCardsDrawn = player.chanceCardsDrawn.size,
                    droughtTileHits = player.droughtTileHits,
                    bonusTileHits = player.bonusTileHits,
                    waterDockHits = player.waterDockHits,
                    maxComebackPoints = player.maxComebackPoints,
                    wasEliminated = player.isEliminated,
                    eliminatedOpponents = player.eliminatedOpponents.toList(),
                    hadPerfectStart = player.hadPerfectStart,
                    usedUndo = player.usedUndo
                )
            }.toMap()
            
            // Record to profile system (excludes guest players automatically)
            profileManager.recordMultiplayerGame(gameId, gameResults)
        }
    }
    
    /**
     * Get game summary for display
     */
    fun getGameSummary(): String {
        val winner = players.values.firstOrNull { !it.isEliminated }
        val gameTimeMinutes = ((System.currentTimeMillis() - gameStartTime) / 60000).toInt()
        
        return buildString {
            append("🏆 Game Over!\n\n")
            winner?.let {
                append("Winner: ${it.nickname} (${it.name})\n")
                append("Final Score: ${it.score} drops\n")
                append("Position: Tile ${it.position}\n")
            }
            append("\nGame Duration: $gameTimeMinutes minutes\n")
            append("\nFinal Standings:\n")
            players.values.sortedByDescending { it.score }.forEach {
                val status = if (it.isEliminated) " (Eliminated)" else ""
                append("  ${it.name}: ${it.score} drops$status\n")
            }
        }
    }
}
