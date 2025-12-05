package earth.lastdrop.app

import androidx.room.*

@Dao
interface LastDropDao {

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Insert
    suspend fun insertRoll(event: RollEventEntity): Long

    @Query("SELECT * FROM roll_events WHERE gameId = :gameId ORDER BY timestamp ASC")
    suspend fun getRollsForGame(gameId: Long): List<RollEventEntity>

    @Query("SELECT playerName, SUM(avg) as totalDrops FROM roll_events WHERE gameId = :gameId GROUP BY playerName")
    suspend fun getTotalsForGame(gameId: Long): List<PlayerTotal>

    @Query("SELECT * FROM roll_events WHERE sentToServer = 0")
    suspend fun getUnsyncedEvents(): List<RollEventEntity>

    @Query("UPDATE roll_events SET sentToServer = 1 WHERE id = :id")
    suspend fun markEventSynced(id: Long)
}

data class PlayerTotal(
    val playerName: String,
    val totalDrops: Int
)
