package com.example.lastdrop

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Achievement Engine - Detects and unlocks achievements based on game events
 */
class AchievementEngine(context: Context) {
    
    private val dao = LastDropDatabase.getInstance(context).achievementDao()
    private val profileDao = LastDropDatabase.getInstance(context).playerProfileDao()
    
    companion object {
        private const val TAG = "AchievementEngine"
    }
    
    /**
     * Check and unlock achievements after a game ends
     */
    suspend fun checkGameEndAchievements(
        playerId: String,
        won: Boolean,
        score: Int,
        placement: Int,
        totalPlayers: Int,
        gameResult: GameResult,
        profile: PlayerProfile
    ): List<Achievement> = withContext(Dispatchers.IO) {
        val unlockedAchievements = mutableListOf<Achievement>()
        
        try {
            // First Game
            if (profile.totalGames == 1) {
                unlockAchievement(playerId, "first_game")?.let { unlockedAchievements.add(it) }
            }
            
            // First Win
            if (won && profile.wins == 1) {
                unlockAchievement(playerId, "first_win")?.let { unlockedAchievements.add(it) }
            }
            
            // Milestone Games
            when (profile.totalGames) {
                10 -> unlockAchievement(playerId, "veteran")?.let { unlockedAchievements.add(it) }
                100 -> unlockAchievement(playerId, "century_club")?.let { unlockedAchievements.add(it) }
            }
            
            // Milestone Wins
            when (profile.wins) {
                50 -> unlockAchievement(playerId, "champion")?.let { unlockedAchievements.add(it) }
                100 -> unlockAchievement(playerId, "master")?.let { unlockedAchievements.add(it) }
            }
            
            // Win Streaks
            if (won) {
                when (profile.currentWinStreak) {
                    3 -> unlockAchievement(playerId, "hot_streak_3")?.let { unlockedAchievements.add(it) }
                    5 -> unlockAchievement(playerId, "hot_streak_5")?.let { unlockedAchievements.add(it) }
                    10 -> unlockAchievement(playerId, "hot_streak_10")?.let { unlockedAchievements.add(it) }
                }
                
                // Perfect Score
                if (score >= 20) {
                    unlockAchievement(playerId, "perfect_score")?.let { unlockedAchievements.add(it) }
                }
                
                // Photo Finish (win by exactly 1 point)
                // This would need opponent scores - implement later
                
                // Perfectionist (no undo used)
                if (!gameResult.usedUndo) {
                    unlockAchievement(playerId, "perfectionist")?.let { unlockedAchievements.add(it) }
                }
                
                // Slow and Steady (win without reaching tile 20)
                if (gameResult.finalTile < 20) {
                    unlockAchievement(playerId, "turtle_power")?.let { unlockedAchievements.add(it) }
                }
            }
            
            // Drought Survivor
            if (won && gameResult.droughtTileHits >= 5) {
                unlockAchievement(playerId, "drought_survivor")?.let { unlockedAchievements.add(it) }
            }
            
            // Bonus Master
            if (gameResult.bonusTileHits >= 5) {
                unlockAchievement(playerId, "bonus_master")?.let { unlockedAchievements.add(it) }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking achievements: ${e.message}")
        }
        
        unlockedAchievements
    }
    
    /**
     * Unlock a specific achievement (only if not already unlocked)
     */
    private suspend fun unlockAchievement(playerId: String, achievementType: String): Achievement? {
        return try {
            // Check if already unlocked
            if (dao.hasAchievement(playerId, achievementType)) {
                Log.d(TAG, "Achievement $achievementType already unlocked for $playerId")
                return null
            }
            
            val achievement = Achievement(
                playerId = playerId,
                type = achievementType,
                unlockedAt = System.currentTimeMillis(),
                notificationShown = false
            )
            
            val inserted = dao.insertAchievement(achievement)
            if (inserted > 0) {
                Log.d(TAG, "✨ Achievement unlocked: $achievementType for $playerId")
                achievement
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unlocking achievement $achievementType: ${e.message}")
            null
        }
    }
    
    /**
     * Get all unlocked achievements for a player with definitions
     */
    suspend fun getPlayerAchievementsWithDetails(playerId: String): List<Pair<Achievement, AchievementDefinition?>> = 
        withContext(Dispatchers.IO) {
            val achievements = dao.getPlayerAchievements(playerId)
            // Convert Flow to List for this operation
            val achievementList = mutableListOf<Achievement>()
            achievements.collect { achievementList.addAll(it) }
            
            achievementList.map { achievement ->
                val definition = AchievementDefinitions.getById(achievement.type)
                Pair(achievement, definition)
            }
        }
    
    /**
     * Get unshown achievements for notification
     */
    suspend fun getUnshownAchievements(playerId: String): List<Pair<Achievement, AchievementDefinition?>> =
        withContext(Dispatchers.IO) {
            val unshown = dao.getUnshownAchievements(playerId)
            unshown.map { achievement ->
                val definition = AchievementDefinitions.getById(achievement.type)
                Pair(achievement, definition)
            }
        }
    
    /**
     * Mark achievement notification as shown
     */
    suspend fun markAsShown(achievementId: String) = withContext(Dispatchers.IO) {
        dao.markNotificationShown(achievementId)
    }
    
    /**
     * Get achievement progress summary
     */
    suspend fun getAchievementSummary(playerId: String): AchievementSummary = withContext(Dispatchers.IO) {
        val unlockedCount = dao.getAchievementCount(playerId)
        val totalCount = AchievementDefinitions.ALL_ACHIEVEMENTS.size
        
        AchievementSummary(
            unlocked = unlockedCount,
            total = totalCount,
            percentage = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0
        )
    }
}

/**
 * Achievement summary data
 */
data class AchievementSummary(
    val unlocked: Int,
    val total: Int,
    val percentage: Int
)
