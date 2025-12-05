package com.example.lastdrop

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Rivalry Record - Tracks head-to-head statistics between two players
 */
@Entity(tableName = "rivalry_records")
data class RivalryRecord(
    @PrimaryKey val recordId: String = UUID.randomUUID().toString(),
    val player1Id: String, // Always the "lower" ID alphabetically for consistency
    val player2Id: String, // Always the "higher" ID alphabetically
    val player1Wins: Int = 0, // Wins by player1 against player2
    val player2Wins: Int = 0, // Wins by player2 against player1
    val totalGames: Int = 0, // Total games between these two players
    val lastGameTimestamp: Long = 0,
    val player1LargestMargin: Int = 0, // Biggest win margin for player1
    val player2LargestMargin: Int = 0, // Biggest win margin for player2
    val closestGame: Int = Int.MAX_VALUE // Smallest point difference in any game
)

/**
 * Rivalry summary with player details
 */
data class RivalrySummary(
    val opponentId: String,
    val opponentName: String,
    val opponentColor: String, // Avatar color
    val wins: Int,
    val losses: Int,
    val totalGames: Int,
    val winRate: Int, // Win percentage as integer (0-100)
    val lastPlayed: Long,
    val isNemesis: Boolean = false // True if this opponent has beaten you the most
)
