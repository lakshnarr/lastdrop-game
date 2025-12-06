package earth.lastdrop.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProfileDao {
    
    // ==================== QUERIES ====================
    
    @Query("SELECT * FROM player_profiles ORDER BY CASE WHEN isAI = 1 THEN 0 WHEN isGuest = 1 THEN 1 ELSE 2 END, lastPlayedAt DESC")
    fun getAllProfiles(): Flow<List<PlayerProfile>>
    
    @Query("SELECT * FROM player_profiles ORDER BY CASE WHEN isAI = 1 THEN 0 WHEN isGuest = 1 THEN 1 ELSE 2 END, lastPlayedAt DESC")
    suspend fun getAllProfilesList(): List<PlayerProfile>
    
    @Query("SELECT * FROM player_profiles WHERE playerId = :id")
    suspend fun getProfile(id: String): PlayerProfile?
    
    @Query("SELECT * FROM player_profiles WHERE playerCode = :code")
    suspend fun getProfileByCode(code: String): PlayerProfile?
    
    @Query("SELECT COUNT(*) FROM player_profiles WHERE isGuest = 0 AND isAI = 0")
    suspend fun getProfileCount(): Int
    
    @Query("SELECT * FROM player_profiles WHERE isGuest = 0 ORDER BY wins DESC LIMIT 1")
    suspend fun getTopPlayer(): PlayerProfile?
    
    @Query("""
        SELECT playerId, playerCode, name, nickname, avatarColor, isGuest, wins, totalGames, lastPlayedAt 
        FROM player_profiles 
        WHERE isGuest = 0 
        ORDER BY lastPlayedAt DESC
    """)
    suspend fun getProfileSummaries(): List<ProfileSummary>
    
    // ==================== INSERT/UPDATE ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: PlayerProfile)
    
    @Update
    suspend fun updateProfile(profile: PlayerProfile)
    
    @Query("UPDATE player_profiles SET name = :newName WHERE playerId = :id")
    suspend fun updateProfileName(id: String, newName: String)
    
    @Query("UPDATE player_profiles SET nickname = :newNickname WHERE playerId = :id")
    suspend fun updateNickname(id: String, newNickname: String)
    
    @Delete
    suspend fun deleteProfile(profile: PlayerProfile)
    
    @Query("DELETE FROM player_profiles WHERE playerId = :id")
    suspend fun deleteProfileById(id: String)
    
    // ==================== STATS UPDATES ====================
    
    @Query("""
        UPDATE player_profiles 
        SET wins = wins + 1,
            totalGames = totalGames + 1,
            currentWinStreak = currentWinStreak + 1,
            bestWinStreak = CASE 
                WHEN currentWinStreak + 1 > bestWinStreak 
                THEN currentWinStreak + 1 
                ELSE bestWinStreak 
            END,
            lastPlayedAt = :timestamp
        WHERE playerId = :id
    """)
    suspend fun recordWin(id: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("""
        UPDATE player_profiles 
        SET losses = losses + 1,
            totalGames = totalGames + 1,
            currentWinStreak = 0,
            lastPlayedAt = :timestamp
        WHERE playerId = :id
    """)
    suspend fun recordLoss(id: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("""
        UPDATE player_profiles 
        SET totalDropsEarned = totalDropsEarned + :drops,
            personalBestScore = CASE 
                WHEN :score > personalBestScore THEN :score 
                ELSE personalBestScore 
            END
        WHERE playerId = :id
    """)
    suspend fun updateGameStats(id: String, drops: Int, score: Int)
    
    @Query("""
        UPDATE player_profiles 
        SET totalPlayTimeMinutes = totalPlayTimeMinutes + :minutes
        WHERE playerId = :id
    """)
    suspend fun addPlayTime(id: String, minutes: Int)
    
    @Query("""
        UPDATE player_profiles 
        SET averageScore = (
            SELECT AVG(score) 
            FROM (
                SELECT totalDropsEarned as score FROM player_profiles WHERE playerId = :id
            )
        )
        WHERE playerId = :id
    """)
    suspend fun recalculateAverageScore(id: String)
    
    // ==================== GUEST PROFILE ====================
    
    @Query("SELECT * FROM player_profiles WHERE isGuest = 1 LIMIT 1")
    suspend fun getGuestProfile(): PlayerProfile?
    
    @Query("DELETE FROM player_profiles WHERE isGuest = 1")
    suspend fun deleteGuestProfile()
    
    // ==================== UTILITY ====================
    
    @Query("SELECT * FROM player_profiles WHERE isGuest = 0 ORDER BY lastPlayedAt ASC LIMIT 1")
    suspend fun getOldestProfile(): PlayerProfile?
}
