package earth.lastdrop.app

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Game Record - Stores individual game performance for AI personalization
 * Each player gets their own record per game
 * Only last 10 games per profile are kept
 */
@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey val recordId: String = UUID.randomUUID().toString(),
    val profileId: String, // Foreign key to player_profiles
    val gameId: String, // Links all players in same game
    val playedAt: Long = System.currentTimeMillis(),
    
    // Player's color this game
    val colorUsed: String, // Hex color (FF0000, 00FF00, 0000FF, FFFF00)
    
    // Game outcome
    val won: Boolean,
    val finalScore: Int,
    val finalTile: Int, // Tile position at game end
    val placement: Int, // 1st, 2nd, 3rd, or 4th place
    
    // Opponents (JSON array of profile IDs)
    val opponentIds: String = "[]", // ["uuid1", "uuid2"]
    val opponentNames: String = "[]", // ["Sarah", "Mike"] for quick AI access
    
    // Performance metrics for AI commentary
    val chanceCardsDrawn: String = "[]", // JSON: [3, 7, 12, 15]
    val droughtTileHits: Int = 0, // Times landed on disaster tiles
    val bonusTileHits: Int = 0, // Times landed on bonus tiles
    val waterDockHits: Int = 0, // Times landed on water docks
    val maxComebackPoints: Int = 0, // Biggest score recovery (e.g., +8)
    
    // Game duration
    val gameTimeMinutes: Int = 0,
    val totalDropsEarned: Int = 0,
    
    // Special events (for AI highlights)
    val wasEliminated: Boolean = false, // Ran out of drops
    val eliminatedOpponents: String = "[]", // Who this player eliminated
    val hadPerfectStart: Boolean = false, // No disasters in first 5 turns
    val usedUndo: Boolean = false
)

/**
 * AI-friendly summary of recent games
 */
data class GameHistory(
    val profileId: String,
    val profileName: String,
    val recentGames: List<GameRecord>,
    val colorPreference: Map<String, Int>, // Color → usage count
    val rivalries: Map<String, RivalryStats>,
    val lastPlayedTimestamp: Long?, // Timestamp of last game (null if never played)
    val lastPlayedAgo: String, // "2 hours ago", "yesterday", "3 days ago"
    val currentForm: String // "hot_streak", "cold_streak", "neutral"
)

/**
 * Head-to-head stats for AI rivalry commentary
 */
data class RivalryStats(
    val opponentId: String,
    val opponentName: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val lastGameResult: String, // "won", "lost"
    val lastGameAgo: String // "yesterday", "3 days ago"
) {
    val winRate: Float
        get() = if (gamesPlayed > 0) (wins.toFloat() / gamesPlayed) * 100 else 0f
        
    val isNemesis: Boolean
        get() = losses > wins && gamesPlayed >= 3
}

/**
 * Time-based greeting context for AI
 */
data class TimeContext(
    val millisAgo: Long,
    val friendlyText: String, // "just now", "5 minutes ago", "yesterday"
    val greetingType: String // "welcome_back", "long_time", "returning_today"
) {
    companion object {
        fun from(lastPlayedAt: Long?): TimeContext {
            if (lastPlayedAt == null) {
                return TimeContext(
                    millisAgo = Long.MAX_VALUE,
                    friendlyText = "first time",
                    greetingType = "first_timer"
                )
            }
            
            val now = System.currentTimeMillis()
            val diff = now - lastPlayedAt
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            
            val (text, type) = when {
                minutes < 1 -> "just now" to "instant_return"
                minutes < 60 -> "$minutes minutes ago" to "same_session"
                hours < 2 -> "an hour ago" to "returning_today"
                hours < 24 -> "$hours hours ago" to "returning_today"
                days == 1L -> "yesterday" to "welcome_back"
                days < 7 -> "$days days ago" to "welcome_back"
                days < 30 -> "${days / 7} weeks ago" to "long_time"
                days < 365 -> "${days / 30} months ago" to "long_time"
                else -> "${days / 365} years ago" to "legendary_return"
            }
            
            return TimeContext(diff, text, type)
        }
    }
}

/**
 * AI greeting templates based on player history
 */
object AIGreetings {
    
    fun getPersonalizedGreeting(history: GameHistory, timeContext: TimeContext): String {
        val name = history.profileName
        val lastGame = history.recentGames.firstOrNull()
        
        return when (timeContext.greetingType) {
            "first_timer" -> "Welcome to your first game, $name! Let's make it memorable!"
            
            "instant_return" -> {
                if (lastGame?.won == true) {
                    "Back for more victory, $name? That last win was just ${timeContext.friendlyText}!"
                } else {
                    "Ready for redemption, $name? You just played ${timeContext.friendlyText}!"
                }
            }
            
            "same_session" -> {
                val streak = history.recentGames.take(3).count { it.won }
                when {
                    streak >= 2 -> "You're on fire today, $name! $streak wins in the last hour!"
                    else -> "Back again, $name! Played ${timeContext.friendlyText}."
                }
            }
            
            "returning_today" -> {
                "Hey $name! Played ${timeContext.friendlyText}. Ready for another round?"
            }
            
            "welcome_back" -> {
                val wins = history.recentGames.take(5).count { it.won }
                val total = history.recentGames.take(5).size
                "Welcome back, $name! Last played ${timeContext.friendlyText}. " +
                "You won $wins out of $total recent games."
            }
            
            "long_time" -> {
                "Long time no see, $name! Last game was ${timeContext.friendlyText}. " +
                "Let's see if you still got it!"
            }
            
            "legendary_return" -> {
                "WOW! $name returns after ${timeContext.friendlyText}! " +
                "This is legendary! Welcome back!"
            }
            
            else -> "Hey $name! Ready to play?"
        }
    }
    
    fun getRivalryGreeting(player1: GameHistory, player2: GameHistory): String? {
        val player2Id = player2.profileId
        val rivalry = player1.rivalries[player2Id] ?: return null
        
        if (rivalry.gamesPlayed < 2) return null // Need at least 2 games for rivalry
        
        return when {
            rivalry.isNemesis -> {
                "${player1.profileName} vs ${rivalry.opponentName}... " +
                "Your nemesis! They've beaten you ${rivalry.losses} times!"
            }
            
            rivalry.wins > rivalry.losses + 2 -> {
                "${player1.profileName} dominates ${rivalry.opponentName}! " +
                "${rivalry.wins}-${rivalry.losses} record!"
            }
            
            rivalry.wins == rivalry.losses -> {
                "${player1.profileName} vs ${rivalry.opponentName}... " +
                "Perfectly tied at ${rivalry.wins}-${rivalry.losses}! Who breaks it?"
            }
            
            rivalry.lastGameResult == "won" -> {
                "${player1.profileName} won last time against ${rivalry.opponentName} " +
                "${rivalry.lastGameAgo}. Can you repeat?"
            }
            
            else -> {
                "${player1.profileName} vs ${rivalry.opponentName}... " +
                "You lost ${rivalry.lastGameAgo}. Time for revenge!"
            }
        }
    }
    
    fun getColorCommentary(history: GameHistory, assignedColor: String): String? {
        val colorCounts = history.colorPreference
        val mostUsed = colorCounts.maxByOrNull { it.value }?.key
        
        if (mostUsed == null || colorCounts.isEmpty()) return null
        
        val colorName = when (assignedColor) {
            "FF0000" -> "Red"
            "00FF00" -> "Green"
            "0000FF" -> "Blue"
            "FFFF00" -> "Yellow"
            else -> "that color"
        }
        
        val mostUsedName = when (mostUsed) {
            "FF0000" -> "Red"
            "00FF00" -> "Green"
            "0000FF" -> "Blue"
            "FFFF00" -> "Yellow"
            else -> "that color"
        }
        
        val usageCount = colorCounts[mostUsed] ?: 0
        val totalGames = colorCounts.values.sum()
        
        return when {
            assignedColor == mostUsed && usageCount >= 3 -> {
                "$colorName again? You've used it in $usageCount out of $totalGames games. It's your lucky color!"
            }
            
            assignedColor != mostUsed && usageCount >= 3 -> {
                "Trying $colorName today? Usually you go with $mostUsedName!"
            }
            
            else -> null
        }
    }
    
    fun getFormGreeting(history: GameHistory): String? {
        val recent3 = history.recentGames.take(3)
        if (recent3.size < 2) return null
        
        val allWins = recent3.all { it.won }
        val allLosses = recent3.all { !it.won }
        val streak = recent3.takeWhile { it.won }.size
        
        return when {
            allWins && recent3.size == 3 -> {
                "${history.profileName} is UNSTOPPABLE! 3-game win streak!"
            }
            
            allLosses && recent3.size == 3 -> {
                "${history.profileName} has lost 3 in a row... but every champion has bad days!"
            }
            
            streak >= 2 -> {
                "${history.profileName} won the last $streak games. On fire!"
            }
            
            else -> null
        }
    }
}
