package earth.lastdrop.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlayerEntity::class, GameEntity::class, RollEventEntity::class, PlayerProfile::class, GameRecord::class, Achievement::class, RivalryRecord::class],
    version = 6,
    exportSchema = false
)
abstract class LastDropDatabase : RoomDatabase() {
    abstract fun dao(): LastDropDao
    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun gameRecordDao(): GameRecordDao
    abstract fun achievementDao(): AchievementDao
    abstract fun rivalryDao(): RivalryDao

    companion object {
        @Volatile private var INSTANCE: LastDropDatabase? = null
        
        // Migration from version 3 to 4 (adding playerCode and nickname)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add playerCode column with unique generated codes
                database.execSQL("""
                    ALTER TABLE player_profiles 
                    ADD COLUMN playerCode TEXT NOT NULL DEFAULT ''
                """)
                
                // Add nickname column (defaults to name)
                database.execSQL("""
                    ALTER TABLE player_profiles 
                    ADD COLUMN nickname TEXT NOT NULL DEFAULT ''
                """)
                
                // Generate unique codes for existing profiles
                val cursor = database.query("SELECT playerId, name FROM player_profiles")
                val updates = mutableListOf<Pair<String, String>>()
                val usedCodes = mutableSetOf<String>()
                
                while (cursor.moveToNext()) {
                    val playerId = cursor.getString(0)
                    val name = cursor.getString(1)
                    
                    // Generate unique code
                    var code: String
                    do {
                        code = generatePlayerCode()
                    } while (code in usedCodes)
                    usedCodes.add(code)
                    
                    updates.add(playerId to code)
                }
                cursor.close()
                
                // Update profiles with generated codes and set nickname = name
                updates.forEach { (playerId, code) ->
                    database.execSQL("""
                        UPDATE player_profiles 
                        SET playerCode = '$code',
                            nickname = name
                        WHERE playerId = '$playerId'
                    """)
                }
                
                // Create unique index on playerCode
                database.execSQL("CREATE UNIQUE INDEX index_player_profiles_playerCode ON player_profiles(playerCode)")
            }
            
            private fun generatePlayerCode(): String {
                val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                val numbers = "0123456789"
                val random = java.util.Random()
                return buildString {
                    repeat(3) { append(letters[random.nextInt(letters.length)]) }
                    repeat(3) { append(numbers[random.nextInt(numbers.length)]) }
                }
            }
        }
        
        // Migration from version 2 to 3 (adding GameRecord table)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE game_records (
                        recordId TEXT PRIMARY KEY NOT NULL,
                        profileId TEXT NOT NULL,
                        gameId TEXT NOT NULL,
                        playedAt INTEGER NOT NULL,
                        colorUsed TEXT NOT NULL,
                        won INTEGER NOT NULL,
                        finalScore INTEGER NOT NULL,
                        finalTile INTEGER NOT NULL,
                        placement INTEGER NOT NULL,
                        opponentIds TEXT NOT NULL,
                        opponentNames TEXT NOT NULL,
                        chanceCardsDrawn INTEGER NOT NULL,
                        droughtTileHits INTEGER NOT NULL,
                        bonusTileHits INTEGER NOT NULL,
                        waterDockHits INTEGER NOT NULL,
                        maxComebackPoints INTEGER NOT NULL,
                        gameTimeMinutes INTEGER NOT NULL,
                        totalDropsEarned INTEGER NOT NULL,
                        wasEliminated INTEGER NOT NULL,
                        eliminatedOpponents TEXT NOT NULL,
                        hadPerfectStart INTEGER NOT NULL,
                        usedUndo INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES player_profiles(playerId) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX index_game_records_profileId ON game_records(profileId)")
                database.execSQL("CREATE INDEX index_game_records_playedAt ON game_records(playedAt)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        playerId TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        avatarColor TEXT NOT NULL,
                        isGuest INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        lastPlayedAt INTEGER NOT NULL,
                        totalGames INTEGER NOT NULL DEFAULT 0,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        totalDropsEarned INTEGER NOT NULL DEFAULT 0,
                        currentWinStreak INTEGER NOT NULL DEFAULT 0,
                        bestWinStreak INTEGER NOT NULL DEFAULT 0,
                        personalBestScore INTEGER NOT NULL DEFAULT 0,
                        averageScore REAL NOT NULL DEFAULT 0.0,
                        totalPlayTimeMinutes INTEGER NOT NULL DEFAULT 0,
                        favoriteColor TEXT NOT NULL,
                        aiPersonality TEXT NOT NULL DEFAULT 'coach_carter'
                    )
                """.trimIndent())
            }
        }
        
        // Migration from version 4 to 5 (adding achievements table)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE achievements (
                        achievementId TEXT PRIMARY KEY NOT NULL,
                        playerId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        unlockedAt INTEGER NOT NULL,
                        notificationShown INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(playerId) REFERENCES player_profiles(playerId) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create index for faster queries
                database.execSQL("CREATE INDEX index_achievements_playerId ON achievements(playerId)")
                database.execSQL("CREATE INDEX index_achievements_type ON achievements(type)")
            }
        }
        
        // Migration from version 5 to 6 (adding rivalry_records table)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE rivalry_records (
                        recordId TEXT PRIMARY KEY NOT NULL,
                        player1Id TEXT NOT NULL,
                        player2Id TEXT NOT NULL,
                        player1Wins INTEGER NOT NULL DEFAULT 0,
                        player2Wins INTEGER NOT NULL DEFAULT 0,
                        totalGames INTEGER NOT NULL DEFAULT 0,
                        lastGameTimestamp INTEGER NOT NULL DEFAULT 0,
                        player1LargestMargin INTEGER NOT NULL DEFAULT 0,
                        player2LargestMargin INTEGER NOT NULL DEFAULT 0,
                        closestGame INTEGER NOT NULL DEFAULT 2147483647,
                        FOREIGN KEY(player1Id) REFERENCES player_profiles(playerId) ON DELETE CASCADE,
                        FOREIGN KEY(player2Id) REFERENCES player_profiles(playerId) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create indexes for faster queries
                database.execSQL("CREATE INDEX index_rivalry_player1 ON rivalry_records(player1Id)")
                database.execSQL("CREATE INDEX index_rivalry_player2 ON rivalry_records(player2Id)")
            }
        }

        fun getInstance(context: Context): LastDropDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LastDropDatabase::class.java,
                    "lastdrop.db"
                )
                .fallbackToDestructiveMigration() // For fresh installs, create from scratch
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build().also { INSTANCE = it }
            }
        }
    }
}
