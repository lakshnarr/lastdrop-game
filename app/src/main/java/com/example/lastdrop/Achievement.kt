package com.example.lastdrop

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Achievement entity - Tracks unlocked achievements per player
 */
@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val achievementId: String = UUID.randomUUID().toString(),
    val playerId: String, // Foreign key to PlayerProfile
    val type: String, // Achievement type identifier (e.g., "first_win", "hot_streak_5")
    val unlockedAt: Long = System.currentTimeMillis(),
    val notificationShown: Boolean = false
)

/**
 * Achievement definition - Metadata for all available achievements
 */
data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String, // Emoji or icon identifier
    val category: AchievementCategory,
    val rarity: AchievementRarity,
    val requirement: String // Human-readable requirement
)

enum class AchievementCategory {
    STARTER,    // First game, first win, etc.
    SKILL,      // High scores, comebacks
    LUCK,       // Lucky rolls, chance cards
    DEDICATION, // Games played, streaks
    FUN,        // Funny or rare events
    RIVALRY     // Head-to-head achievements
}

enum class AchievementRarity {
    COMMON,     // Most players will get
    UNCOMMON,   // Requires some effort
    RARE,       // Difficult to achieve
    EPIC,       // Very challenging
    LEGENDARY   // Extremely rare
}

/**
 * Achievement definitions - All available achievements in the game
 */
object AchievementDefinitions {
    
    val ALL_ACHIEVEMENTS = listOf(
        // ===== STARTER ACHIEVEMENTS =====
        AchievementDefinition(
            id = "first_game",
            name = "First Drop",
            description = "Play your first game",
            icon = "🎮",
            category = AchievementCategory.STARTER,
            rarity = AchievementRarity.COMMON,
            requirement = "Complete 1 game"
        ),
        AchievementDefinition(
            id = "first_win",
            name = "Winner Winner",
            description = "Win your first game",
            icon = "🏆",
            category = AchievementCategory.STARTER,
            rarity = AchievementRarity.COMMON,
            requirement = "Win 1 game"
        ),
        AchievementDefinition(
            id = "veteran",
            name = "Veteran Player",
            description = "Play 10 games",
            icon = "🎖️",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Complete 10 games"
        ),
        
        // ===== SKILL ACHIEVEMENTS =====
        AchievementDefinition(
            id = "perfect_score",
            name = "Perfect Score",
            description = "Win with 20 drops",
            icon = "💯",
            category = AchievementCategory.SKILL,
            rarity = AchievementRarity.RARE,
            requirement = "Win with maximum score"
        ),
        AchievementDefinition(
            id = "comeback_king",
            name = "Comeback King",
            description = "Win from last place",
            icon = "👑",
            category = AchievementCategory.SKILL,
            rarity = AchievementRarity.RARE,
            requirement = "Win after being in last place"
        ),
        AchievementDefinition(
            id = "domination",
            name = "Total Domination",
            description = "Win by 10+ points",
            icon = "💪",
            category = AchievementCategory.SKILL,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Win with 10+ point margin"
        ),
        
        // ===== STREAK ACHIEVEMENTS =====
        AchievementDefinition(
            id = "hot_streak_3",
            name = "On Fire",
            description = "Win 3 games in a row",
            icon = "🔥",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "3 consecutive wins"
        ),
        AchievementDefinition(
            id = "hot_streak_5",
            name = "Unstoppable",
            description = "Win 5 games in a row",
            icon = "⚡",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.RARE,
            requirement = "5 consecutive wins"
        ),
        AchievementDefinition(
            id = "hot_streak_10",
            name = "Legendary Streak",
            description = "Win 10 games in a row",
            icon = "🌟",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.LEGENDARY,
            requirement = "10 consecutive wins"
        ),
        
        // ===== LUCK ACHIEVEMENTS =====
        AchievementDefinition(
            id = "lucky_seven",
            name = "Lucky Seven",
            description = "Roll three 6s in a row (two-dice mode)",
            icon = "🎲",
            category = AchievementCategory.LUCK,
            rarity = AchievementRarity.RARE,
            requirement = "Roll 6+6 three times consecutively"
        ),
        AchievementDefinition(
            id = "drought_survivor",
            name = "Drought Survivor",
            description = "Win despite hitting 5 drought tiles",
            icon = "🏜️",
            category = AchievementCategory.LUCK,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Hit 5+ drought tiles and still win"
        ),
        AchievementDefinition(
            id = "bonus_master",
            name = "Bonus Master",
            description = "Hit 5 bonus tiles in one game",
            icon = "💎",
            category = AchievementCategory.LUCK,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Land on 5+ bonus tiles"
        ),
        
        // ===== FUN ACHIEVEMENTS =====
        AchievementDefinition(
            id = "close_call",
            name = "Photo Finish",
            description = "Win by exactly 1 point",
            icon = "📸",
            category = AchievementCategory.FUN,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Win with 1-point margin"
        ),
        AchievementDefinition(
            id = "turtle_power",
            name = "Slow and Steady",
            description = "Win with the lowest final tile position",
            icon = "🐢",
            category = AchievementCategory.FUN,
            rarity = AchievementRarity.RARE,
            requirement = "Win without reaching tile 20"
        ),
        AchievementDefinition(
            id = "perfectionist",
            name = "No Mistakes",
            description = "Win without using undo",
            icon = "✨",
            category = AchievementCategory.SKILL,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Win without undoing any moves"
        ),
        
        // ===== RIVALRY ACHIEVEMENTS =====
        AchievementDefinition(
            id = "nemesis",
            name = "Arch Nemesis",
            description = "Beat the same player 5 times",
            icon = "⚔️",
            category = AchievementCategory.RIVALRY,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Win 5 games against same opponent"
        ),
        AchievementDefinition(
            id = "grudge_match",
            name = "Grudge Match Victor",
            description = "Beat a player who has beaten you 3+ times",
            icon = "🥊",
            category = AchievementCategory.RIVALRY,
            rarity = AchievementRarity.UNCOMMON,
            requirement = "Defeat your nemesis"
        ),
        
        // ===== MILESTONE ACHIEVEMENTS =====
        AchievementDefinition(
            id = "century_club",
            name = "Century Club",
            description = "Play 100 games",
            icon = "💯",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.EPIC,
            requirement = "Complete 100 games"
        ),
        AchievementDefinition(
            id = "champion",
            name = "Champion",
            description = "Win 50 games",
            icon = "🏅",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.EPIC,
            requirement = "Win 50 games"
        ),
        AchievementDefinition(
            id = "master",
            name = "Water Master",
            description = "Win 100 games",
            icon = "💧",
            category = AchievementCategory.DEDICATION,
            rarity = AchievementRarity.LEGENDARY,
            requirement = "Win 100 games"
        )
    )
    
    fun getById(id: String): AchievementDefinition? = ALL_ACHIEVEMENTS.find { it.id == id }
    
    fun getByCategory(category: AchievementCategory): List<AchievementDefinition> =
        ALL_ACHIEVEMENTS.filter { it.category == category }
}
