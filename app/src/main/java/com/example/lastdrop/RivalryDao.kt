package com.example.lastdrop

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO for Rivalry tracking
 */
@Dao
interface RivalryDao {
    
    /**
     * Get rivalry record between two players (order-independent)
     */
    @Query("""
        SELECT * FROM rivalry_records 
        WHERE (player1Id = :playerId1 AND player2Id = :playerId2) 
           OR (player1Id = :playerId2 AND player2Id = :playerId1)
    """)
    suspend fun getRivalryRecord(playerId1: String, playerId2: String): RivalryRecord?
    
    /**
     * Get all rivalry records for a player
     */
    @Query("""
        SELECT * FROM rivalry_records 
        WHERE player1Id = :playerId OR player2Id = :playerId
        ORDER BY totalGames DESC
    """)
    suspend fun getAllRivalries(playerId: String): List<RivalryRecord>
    
    /**
     * Insert new rivalry record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRivalry(record: RivalryRecord)
    
    /**
     * Update existing rivalry record
     */
    @Update
    suspend fun updateRivalry(record: RivalryRecord)
    
    /**
     * Get player's biggest rival (most losses against)
     */
    @Query("""
        SELECT * FROM rivalry_records 
        WHERE player1Id = :playerId OR player2Id = :playerId
        ORDER BY 
            CASE 
                WHEN player1Id = :playerId THEN player2Wins 
                ELSE player1Wins 
            END DESC
        LIMIT 1
    """)
    suspend fun getBiggestRival(playerId: String): RivalryRecord?
    
    /**
     * Delete all rivalry records for a player (for testing)
     */
    @Query("DELETE FROM rivalry_records WHERE player1Id = :playerId OR player2Id = :playerId")
    suspend fun deletePlayerRivalries(playerId: String)
}
