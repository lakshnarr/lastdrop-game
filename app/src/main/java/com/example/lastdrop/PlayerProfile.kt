package com.example.lastdrop

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Player Profile - Stores persistent player data across games
 * Maximum 6 profiles + 1 guest profile allowed
 */
@Entity(tableName = "player_profiles")
data class PlayerProfile(
    @PrimaryKey val playerId: String = UUID.randomUUID().toString(),
    val playerCode: String, // Unique 6-char code (never changes) - must be provided
    val name: String, // Profile display name (can be changed)
    val nickname: String = name, // How they want to be called by AI (can be changed)
    val avatarColor: String, // Hex color code (e.g., "FF0000" for red)
    val isGuest: Boolean = false, // True for temporary guest profile
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    
    // Game Statistics
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val totalDropsEarned: Int = 0,
    
    // Streaks
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    
    // Performance Stats
    val personalBestScore: Int = 0,
    val averageScore: Float = 0f,
    val totalPlayTimeMinutes: Int = 0,
    
    // Preferences
    val favoriteColor: String = avatarColor, // Preferred game piece color
    val aiPersonality: String = "coach_carter" // Selected AI voice personality
)

/**
 * Lightweight version for profile selection UI
 */
data class ProfileSummary(
    val playerId: String,
    val playerCode: String,
    val name: String,
    val nickname: String,
    val avatarColor: String,
    val isGuest: Boolean,
    val wins: Int,
    val totalGames: Int,
    val lastPlayedAt: Long
) {
    val winRate: Float
        get() = if (totalGames > 0) (wins.toFloat() / totalGames) * 100 else 0f
}

/**
 * Generate unique 6-character player code (e.g., "A3B7C2")
 * Format: 3 uppercase letters + 3 numbers for easy readability
 */
fun generatePlayerCode(): String {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val numbers = "0123456789"
    val random = java.util.Random()
    
    return buildString {
        repeat(3) { append(letters[random.nextInt(letters.length)]) }
        repeat(3) { append(numbers[random.nextInt(numbers.length)]) }
    }
}
