package earth.lastdrop.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey val savedGameId: String = UUID.randomUUID().toString(),
    val gameId: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val label: String = "",
    val playerCount: Int,
    val currentPlayer: Int,
    val playWithTwoDice: Boolean,
    val playerNames: String,            // JSON array of player names
    val playerColors: String,           // JSON array of player colors
    val currentGameProfileIds: String,  // JSON array of profile IDs aligned with players
    val playerPositions: String,        // JSON object map name -> position
    val playerScores: String,           // JSON object map name -> score
    val lastDice1: Int? = null,
    val lastDice2: Int? = null,
    val lastAvg: Int? = null,
    val lastTileName: String? = null,
    val lastTileType: String? = null,
    val lastChanceCardNumber: Int? = null,
    val lastChanceCardText: String? = null,
    val waitingForCoin: Boolean = false,
    val testModeEnabled: Boolean = false,
    val testModeType: Int = 0
)
