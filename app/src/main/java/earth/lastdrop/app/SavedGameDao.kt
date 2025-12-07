package earth.lastdrop.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(savedGame: SavedGame)

    @Query("SELECT * FROM saved_games ORDER BY savedAt DESC LIMIT 1")
    suspend fun getLatest(): SavedGame?

    @Query("SELECT * FROM saved_games ORDER BY savedAt DESC")
    suspend fun getAll(): List<SavedGame>

    @Query("SELECT * FROM saved_games WHERE savedGameId = :id LIMIT 1")
    suspend fun getById(id: String): SavedGame?

    @Query("DELETE FROM saved_games WHERE savedGameId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_games")
    suspend fun clearAll()
}
