package com.example.lastdrop

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Achievement operations
 */
@Dao
interface AchievementDao {
    
    /**
     * Get all achievements for a player
     */
    @Query("SELECT * FROM achievements WHERE playerId = :playerId ORDER BY unlockedAt DESC")
    fun getPlayerAchievements(playerId: String): Flow<List<Achievement>>
    
    /**
     * Get unlocked achievement count for a player
     */
    @Query("SELECT COUNT(*) FROM achievements WHERE playerId = :playerId")
    suspend fun getAchievementCount(playerId: String): Int
    
    /**
     * Check if player has specific achievement
     */
    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE playerId = :playerId AND type = :achievementType)")
    suspend fun hasAchievement(playerId: String, achievementType: String): Boolean
    
    /**
     * Get recent unlocked achievements (not yet shown)
     */
    @Query("SELECT * FROM achievements WHERE playerId = :playerId AND notificationShown = 0 ORDER BY unlockedAt DESC")
    suspend fun getUnshownAchievements(playerId: String): List<Achievement>
    
    /**
     * Mark achievement notification as shown
     */
    @Query("UPDATE achievements SET notificationShown = 1 WHERE achievementId = :achievementId")
    suspend fun markNotificationShown(achievementId: String)
    
    /**
     * Unlock new achievement
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: Achievement): Long
    
    /**
     * Get achievements by type
     */
    @Query("SELECT * FROM achievements WHERE type = :type ORDER BY unlockedAt DESC")
    suspend fun getAchievementsByType(type: String): List<Achievement>
    
    /**
     * Delete all achievements for a player (for testing)
     */
    @Query("DELETE FROM achievements WHERE playerId = :playerId")
    suspend fun deletePlayerAchievements(playerId: String)
}
