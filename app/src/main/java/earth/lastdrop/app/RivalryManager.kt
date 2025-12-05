package earth.lastdrop.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rivalry Manager - Tracks head-to-head statistics between players
 */
class RivalryManager(context: Context) {
    
    private val rivalryDao = LastDropDatabase.getInstance(context).rivalryDao()
    private val profileDao = LastDropDatabase.getInstance(context).playerProfileDao()
    
    companion object {
        private const val TAG = "RivalryManager"
    }
    
    /**
     * Record game result for rivalry tracking
     */
    suspend fun recordGameResult(
        winnerId: String,
        loserId: String,
        winnerScore: Int,
        loserScore: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val margin = winnerScore - loserScore
            
            // Get existing record or create new one
            val existing = rivalryDao.getRivalryRecord(winnerId, loserId)
            
            if (existing != null) {
                // Update existing record
                val updatedRecord = if (existing.player1Id == winnerId) {
                    existing.copy(
                        player1Wins = existing.player1Wins + 1,
                        totalGames = existing.totalGames + 1,
                        lastGameTimestamp = System.currentTimeMillis(),
                        player1LargestMargin = maxOf(existing.player1LargestMargin, margin),
                        closestGame = minOf(existing.closestGame, margin)
                    )
                } else {
                    existing.copy(
                        player2Wins = existing.player2Wins + 1,
                        totalGames = existing.totalGames + 1,
                        lastGameTimestamp = System.currentTimeMillis(),
                        player2LargestMargin = maxOf(existing.player2LargestMargin, margin),
                        closestGame = minOf(existing.closestGame, margin)
                    )
                }
                rivalryDao.updateRivalry(updatedRecord)
            } else {
                // Create new record (ensure consistent ordering)
                val newRecord = if (winnerId < loserId) {
                    RivalryRecord(
                        player1Id = winnerId,
                        player2Id = loserId,
                        player1Wins = 1,
                        player2Wins = 0,
                        totalGames = 1,
                        lastGameTimestamp = System.currentTimeMillis(),
                        player1LargestMargin = margin,
                        player2LargestMargin = 0,
                        closestGame = margin
                    )
                } else {
                    RivalryRecord(
                        player1Id = loserId,
                        player2Id = winnerId,
                        player1Wins = 0,
                        player2Wins = 1,
                        totalGames = 1,
                        lastGameTimestamp = System.currentTimeMillis(),
                        player1LargestMargin = 0,
                        player2LargestMargin = margin,
                        closestGame = margin
                    )
                }
                rivalryDao.insertRivalry(newRecord)
            }
            
            Log.d(TAG, "Recorded rivalry result: $winnerId beat $loserId")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording rivalry: ${e.message}")
        }
    }
    
    /**
     * Get all rivalries for a player with opponent details
     */
    suspend fun getPlayerRivalries(playerId: String): List<RivalrySummary> = withContext(Dispatchers.IO) {
        try {
            val records = rivalryDao.getAllRivalries(playerId)
            val biggestRival = rivalryDao.getBiggestRival(playerId)
            
            records.mapNotNull { record ->
                // Determine which player is the opponent
                val (opponentId, wins, losses) = if (record.player1Id == playerId) {
                    Triple(record.player2Id, record.player1Wins, record.player2Wins)
                } else {
                    Triple(record.player1Id, record.player2Wins, record.player1Wins)
                }
                
                // Get opponent profile
                val opponent = profileDao.getProfile(opponentId)
                
                opponent?.let {
                    val winRate = if (record.totalGames > 0) {
                        ((wins.toFloat() / record.totalGames.toFloat()) * 100).toInt()
                    } else 0
                    
                    val isNemesis = biggestRival?.let { rival ->
                        (rival.player1Id == opponentId || rival.player2Id == opponentId)
                    } ?: false
                    
                    RivalrySummary(
                        opponentId = opponentId,
                        opponentName = opponent.name,
                        opponentColor = opponent.avatarColor,
                        wins = wins,
                        losses = losses,
                        totalGames = record.totalGames,
                        winRate = winRate,
                        lastPlayed = record.lastGameTimestamp,
                        isNemesis = isNemesis
                    )
                }
            }.sortedByDescending { it.totalGames }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting rivalries: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get nemesis (player who has beaten you the most)
     */
    suspend fun getNemesis(playerId: String): RivalrySummary? = withContext(Dispatchers.IO) {
        try {
            val rivalries = getPlayerRivalries(playerId)
            rivalries.firstOrNull { it.isNemesis }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nemesis: ${e.message}")
            null
        }
    }
}
