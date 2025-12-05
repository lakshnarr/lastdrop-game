package com.example.lastdrop

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {
    
    // ==================== QUERIES ====================
    
    @Query("SELECT * FROM game_records WHERE profileId = :id ORDER BY playedAt DESC LIMIT 10")
    suspend fun getRecentGames(id: String): List<GameRecord>
    
    @Query("SELECT * FROM game_records ORDER BY playedAt DESC")
    suspend fun getAllGameRecords(): List<GameRecord>
    
    @Query("SELECT * FROM game_records WHERE profileId = :id ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLastGame(id: String): GameRecord?
    
    @Query("SELECT * FROM game_records WHERE gameId = :gameId ORDER BY placement ASC")
    suspend fun getGameResults(gameId: String): List<GameRecord>
    
    @Query("SELECT COUNT(*) FROM game_records WHERE profileId = :id")
    suspend fun getGameCount(id: String): Int
    
    // ==================== INSERT/UPDATE ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GameRecord)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<GameRecord>)
    
    @Update
    suspend fun updateRecord(record: GameRecord)
    
    @Delete
    suspend fun deleteRecord(record: GameRecord)
    
    // ==================== MAINTENANCE ====================
    
    @Query("""
        DELETE FROM game_records 
        WHERE recordId IN (
            SELECT recordId FROM game_records 
            WHERE profileId = :id 
            ORDER BY playedAt DESC 
            LIMIT -1 OFFSET 10
        )
    """)
    suspend fun pruneOldGames(id: String)
    
    @Query("DELETE FROM game_records WHERE profileId = :id")
    suspend fun deleteAllForProfile(id: String)
    
    @Query("DELETE FROM game_records WHERE gameId = :gameId")
    suspend fun deleteGameRecords(gameId: String)
    
    // ==================== ANALYTICS ====================
    
    @Query("""
        SELECT colorUsed, COUNT(*) as count 
        FROM game_records 
        WHERE profileId = :id 
        GROUP BY colorUsed 
        ORDER BY count DESC
    """)
    suspend fun getColorUsageStats(id: String): List<ColorUsage>
    
    @Query("""
        SELECT AVG(finalScore) 
        FROM game_records 
        WHERE profileId = :id AND playedAt > :since
    """)
    suspend fun getAverageScoreSince(id: String, since: Long): Float?
    
    @Query("""
        SELECT * FROM game_records 
        WHERE profileId = :id 
        AND opponentIds LIKE '%' || :opponentId || '%'
        ORDER BY playedAt DESC
    """)
    suspend fun getGamesVsOpponent(id: String, opponentId: String): List<GameRecord>
}

/**
 * Result class for color usage stats
 */
data class ColorUsage(
    val colorUsed: String,
    val count: Int
)
