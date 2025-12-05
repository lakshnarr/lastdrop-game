package earth.lastdrop.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roll_events")
data class RollEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val playerName: String,  // simpler than foreign key for now
    val timestamp: Long,
    val modeTwoDice: Boolean,
    val dice1: Int?,
    val dice2: Int?,
    val avg: Int,
    val sentToServer: Boolean = false
)
