package com.example.lastdrop

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GameShareHelper {

    /**
     * Share game result as text
     */
    fun shareGameResult(
        context: Context,
        playerName: String,
        placement: Int,
        score: Int,
        totalPlayers: Int,
        achievements: List<String> = emptyList()
    ) {
        val placementText = when (placement) {
            1 -> "🥇 1st"
            2 -> "🥈 2nd"
            3 -> "🥉 3rd"
            else -> "🎲 ${placement}th"
        }
        
        val achievementText = if (achievements.isNotEmpty()) {
            "\n\n🏆 Achievements:\n${achievements.joinToString("\n") { "• $it" }}"
        } else {
            ""
        }
        
        val text = """
            💧 Last Drop Game Results 💧
            
            Player: $playerName
            Placement: $placementText Place
            Score: $score drops
            Players: $totalPlayers
            $achievementText
            
            #LastDropGame #BoardGame
        """.trimIndent()

        shareText(context, text)
    }

    /**
     * Share leaderboard position
     */
    fun shareLeaderboardRank(
        context: Context,
        playerName: String,
        rank: Int,
        wins: Int,
        totalGames: Int,
        winRate: Int
    ) {
        val rankEmoji = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "#$rank"
        }
        
        val text = """
            🏆 Last Drop Leaderboard 🏆
            
            $rankEmoji $playerName
            Wins: $wins
            Games Played: $totalGames
            Win Rate: $winRate%
            
            #LastDropGame #Leaderboard
        """.trimIndent()

        shareText(context, text)
    }

    /**
     * Share win streak
     */
    fun shareWinStreak(
        context: Context,
        playerName: String,
        streak: Int
    ) {
        val fire = "🔥".repeat(minOf(streak, 10))
        
        val text = """
            $fire
            
            $playerName is on a $streak game win streak!
            
            Can anyone stop them?
            
            #LastDropGame #WinStreak $fire
        """.trimIndent()

        shareText(context, text)
    }

    /**
     * Share profile stats summary
     */
    fun shareProfileStats(
        context: Context,
        playerName: String,
        totalGames: Int,
        wins: Int,
        winRate: Int,
        bestScore: Int,
        currentStreak: Int,
        achievementsUnlocked: Int
    ) {
        val text = """
            📊 Last Drop Stats - $playerName 📊
            
            Total Games: $totalGames
            Wins: $wins ($winRate% win rate)
            Best Score: $bestScore drops
            Current Streak: $currentStreak 🔥
            Achievements: $achievementsUnlocked/20 unlocked 🏆
            
            #LastDropGame #Stats
        """.trimIndent()

        shareText(context, text)
    }

    /**
     * Export game history as CSV
     */
    fun exportGameHistory(
        context: Context,
        games: List<GameRecord>,
        playerName: String
    ): Uri? {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val filename = "LastDrop_${playerName}_${dateFormat.format(Date())}.csv"
            
            val file = File(context.cacheDir, filename)
            FileOutputStream(file).use { output ->
                // CSV Header
                output.write("Date,Result,Placement,Score,Players,Duration,Bonuses,Droughts\n".toByteArray())
                
                // Data rows
                games.forEach { game ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(game.playedAt))
                    val result = if (game.won) "WIN" else "LOSS"
                    val players = try {
                        game.opponentIds.removeSurrounding("[", "]").split(",").size + 1
                    } catch (e: Exception) { 1 }
                    
                    val row = "$date,$result,${game.placement},${game.finalScore},$players," +
                            "${game.gameTimeMinutes},${game.bonusTileHits},${game.droughtTileHits}\n"
                    output.write(row.toByteArray())
                }
            }
            
            // Create content URI
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generic text share
     */
    private fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Last Drop Game Results")
        }
        
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    /**
     * Share with file attachment (CSV export)
     */
    fun shareWithAttachment(context: Context, text: String, fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Export Game History"))
    }
}
