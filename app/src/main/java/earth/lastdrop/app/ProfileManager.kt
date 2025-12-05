package earth.lastdrop.app

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Game result data for recording multiplayer games
 */
data class GameResult(
    val name: String,
    val color: String,
    val score: Int,
    val finalTile: Int,
    val placement: Int,
    val dropsEarned: Int,
    val gameTimeMinutes: Int,
    val chanceCardsDrawn: Int = 0,
    val droughtTileHits: Int = 0,
    val bonusTileHits: Int = 0,
    val waterDockHits: Int = 0,
    val maxComebackPoints: Int = 0,
    val wasEliminated: Boolean = false,
    val eliminatedOpponents: List<String> = emptyList(),
    val hadPerfectStart: Boolean = false,
    val usedUndo: Boolean = false
)

/**
 * ProfileManager - Handles player profile logic
 * Enforces 6 profile + 1 guest limit
 */
class ProfileManager(context: Context) {
    
    private val dao = LastDropDatabase.getInstance(context).playerProfileDao()
    private val gameRecordDao = LastDropDatabase.getInstance(context).gameRecordDao()
    
    companion object {
        const val MAX_PROFILES = 4  // Maximum custom player profiles (not including Cloudie/Guestie)
        const val GUEST_NAME = "Guestie"
        const val AI_NAME = "Cloudie"
        const val AI_PLAYER_CODE = "AI0001" // Fixed code for AI player
        const val GUEST_PLAYER_CODE = "GUEST001" // Fixed code for guest player
        
        // Profile tile colors (for UI display, not game colors)
        val PROFILE_TILE_COLORS = listOf(
            "9C27B0", // Purple
            "FF6F00", // Orange
            "E91E63", // Pink
            "00ACC1"  // Teal
        )
        
        // 4 game colors matching ESP32 LED and live.html
        val GAME_COLORS = listOf(
            "FF0000", // Red
            "00FF00", // Green
            "0000FF", // Blue
            "FFFF00"  // Yellow
        )
        
        val COLOR_NAMES = listOf("Red", "Green", "Blue", "Yellow")
    }
    
    // ==================== PROFILE CRUD ====================
    
    suspend fun createProfile(name: String, nickname: String = name): Result<PlayerProfile> {
        return try {
            val count = dao.getProfileCount()
            
            // Check if limit reached
            if (count >= MAX_PROFILES) {
                return Result.failure(Exception("Maximum $MAX_PROFILES profiles allowed"))
            }
            
            // Assign color based on profile count (cycles through 4 colors)
            val tileColor = PROFILE_TILE_COLORS[count % PROFILE_TILE_COLORS.size]
            
            val profile = PlayerProfile(
                playerCode = generatePlayerCode(),
                name = name.trim(),
                nickname = nickname.trim(),
                avatarColor = tileColor, // Use assigned tile color
                isGuest = false
            )
            
            dao.insertProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getOrCreateGuestProfile(): PlayerProfile {
        // Check if guest profile already exists
        val existingGuest = dao.getProfileByCode(GUEST_PLAYER_CODE)
        if (existingGuest != null) {
            return existingGuest
        }
        
        // Create guest profile with user emoji
        val guestProfile = PlayerProfile(
            playerCode = GUEST_PLAYER_CODE,
            name = GUEST_NAME,
            nickname = GUEST_NAME,
            avatarColor = "9E9E9E", // Gray for temporary guest
            isGuest = true
        )
        
        dao.insertProfile(guestProfile)
        return guestProfile
    }
    
    suspend fun getOrCreateAIProfile(): PlayerProfile {
        // Check if AI profile already exists
        val existingAI = dao.getProfileByCode(AI_PLAYER_CODE)
        if (existingAI != null) {
            return existingAI
        }
        
        // Create AI profile with cloud emoji
        val aiProfile = PlayerProfile(
            playerCode = AI_PLAYER_CODE,
            name = AI_NAME,
            nickname = AI_NAME,
            avatarColor = "00D4FF", // Cyan/Sky blue for cloud theme
            isGuest = false,
            isAI = true
        )
        
        dao.insertProfile(aiProfile)
        return aiProfile
    }
    
    suspend fun updateProfile(profile: PlayerProfile): Result<Unit> {
        return try {
            dao.updateProfile(profile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateProfileName(profileId: String, newName: String): Result<Unit> {
        return try {
            val validationError = validateProfileName(newName)
            if (validationError != null) {
                return Result.failure(Exception(validationError))
            }
            dao.updateProfileName(profileId, newName.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateNickname(profileId: String, newNickname: String): Result<Unit> {
        return try {
            val validationError = validateProfileName(newNickname)
            if (validationError != null) {
                return Result.failure(Exception(validationError))
            }
            dao.updateNickname(profileId, newNickname.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteProfile(profileId: String): Result<Unit> {
        return try {
            val profile = dao.getProfile(profileId)
            if (profile?.isGuest == true) {
                return Result.failure(Exception("Cannot delete guest profile this way"))
            }
            dao.deleteProfileById(profileId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update existing profiles with vibrant tile colors
     * Migrates old gray profiles to colorful ones
     */
    suspend fun updateProfileColors() {
        val allProfiles = dao.getAllProfilesList()
        val regularProfiles = allProfiles.filter { !it.isAI && !it.isGuest }
        
        regularProfiles.forEachIndexed { index, profile ->
            // Only update if profile has old gray color
            if (profile.avatarColor == "808080" || profile.avatarColor == "9E9E9E") {
                val newColor = PROFILE_TILE_COLORS[index % PROFILE_TILE_COLORS.size]
                val updatedProfile = profile.copy(avatarColor = newColor)
                dao.updateProfile(updatedProfile)
            }
        }
    }
    
    suspend fun deleteOldestProfileIfNeeded(): PlayerProfile? {
        val count = dao.getProfileCount()
        return if (count >= MAX_PROFILES) {
            val oldest = dao.getOldestProfile()
            oldest?.let {
                dao.deleteProfile(it)
                it
            }
        } else null
    }
    
    // ==================== QUERIES ====================
    
    fun getAllProfiles(): Flow<List<PlayerProfile>> = dao.getAllProfiles()
    
    suspend fun getProfile(id: String): PlayerProfile? = dao.getProfile(id)
    
    suspend fun getProfileByCode(code: String): PlayerProfile? = dao.getProfileByCode(code)
    
    suspend fun getProfileSummaries(): List<ProfileSummary> = dao.getProfileSummaries()
    
    suspend fun getTopPlayer(): PlayerProfile? = dao.getTopPlayer()
    
    suspend fun canAddProfile(): Boolean = dao.getProfileCount() < MAX_PROFILES
    
    // ==================== GAME STATS ====================
    
    /**
     * Single player result recording (legacy method)
     */
    suspend fun recordGameResult(
        playerId: String, 
        won: Boolean, 
        score: Int, 
        dropsEarned: Int,
        playTimeMinutes: Int
    ) {
        if (won) {
            dao.recordWin(playerId)
        } else {
            dao.recordLoss(playerId)
        }
        
        dao.updateGameStats(playerId, dropsEarned, score)
        dao.addPlayTime(playerId, playTimeMinutes)
        dao.recalculateAverageScore(playerId)
    }
    
    /**
     * Multi-player game result recording with AI history
     * @param gameId Unique game identifier
     * @param playerResults Map of profileId to game result
     */
    suspend fun recordMultiplayerGame(
        gameId: String,
        playerResults: Map<String, GameResult>
    ) {
        // Create game records for AI history
        val records = playerResults.map { (profileId, result) ->
            val opponentData = playerResults.filterKeys { it != profileId }
            val opponentIdsList = opponentData.keys.toList()
            val opponentNamesList = opponentData.values.map { it.name }
            
            GameRecord(
                profileId = profileId,
                gameId = gameId,
                playedAt = System.currentTimeMillis(),
                colorUsed = result.color,
                won = result.placement == 1,
                finalScore = result.score,
                finalTile = result.finalTile,
                placement = result.placement,
                opponentIds = opponentIdsList.joinToString(","),
                opponentNames = opponentNamesList.joinToString(","),
                chanceCardsDrawn = "[]", // TODO: Track actual chance cards
                droughtTileHits = result.droughtTileHits,
                bonusTileHits = result.bonusTileHits,
                waterDockHits = result.waterDockHits,
                maxComebackPoints = result.maxComebackPoints,
                gameTimeMinutes = result.gameTimeMinutes,
                totalDropsEarned = result.dropsEarned,
                wasEliminated = result.wasEliminated,
                eliminatedOpponents = result.eliminatedOpponents.joinToString(","),
                hadPerfectStart = result.hadPerfectStart,
                usedUndo = result.usedUndo
            )
        }
        
        // Insert all game records
        gameRecordDao.insertRecords(records)
        
        // Update profile stats for each player
        playerResults.forEach { (profileId, result) ->
            // Don't record stats for guest profiles
            val profile = dao.getProfile(profileId)
            if (profile?.isGuest == false) {
                recordGameResult(
                    playerId = profileId,
                    won = result.placement == 1,
                    score = result.score,
                    dropsEarned = result.dropsEarned,
                    playTimeMinutes = result.gameTimeMinutes
                )
            }
        }
        
        // Prune old game records (keep only last 10 per profile)
        playerResults.keys.forEach { profileId ->
            gameRecordDao.pruneOldGames(profileId)
        }
    }
    
    /**
     * Load game history for AI greeting generation
     */
    suspend fun getGameHistoryForGreeting(profileId: String): GameHistory {
        val profile = dao.getProfile(profileId) ?: throw Exception("Profile not found")
        val recentGames = gameRecordDao.getRecentGames(profileId)
        val lastGame = gameRecordDao.getLastGame(profileId)
        
        // Calculate color preference
        val colorUsage = gameRecordDao.getColorUsageStats(profileId)
        val colorPreference: Map<String, Int> = colorUsage.associate { it.colorUsed to it.count }
        
        // Calculate rivalries
        val rivalries = mutableMapOf<String, RivalryStats>()
        val opponentIds = recentGames.flatMap { it.opponentIds.split(",") }
            .filter { it.isNotBlank() }
            .toSet()
        
        opponentIds.forEach { opponentId ->
            val vsGames = gameRecordDao.getGamesVsOpponent(profileId, opponentId)
            if (vsGames.isNotEmpty()) {
                val wins = vsGames.count { it.won }
                val lastVsGame = vsGames.first()
                val opponentName = lastVsGame.opponentNames.split(",").first()
                val timeContext = TimeContext.from(lastVsGame.playedAt)
                
                rivalries[opponentId] = RivalryStats(
                    opponentId = opponentId,
                    opponentName = opponentName,
                    gamesPlayed = vsGames.size,
                    wins = wins,
                    losses = vsGames.size - wins,
                    lastGameResult = if (lastVsGame.won) "won" else "lost",
                    lastGameAgo = timeContext.friendlyText
                )
            }
        }
        
        // Calculate current form
        val last5Games = recentGames.take(5)
        val recentWins = last5Games.count { it.won }
        val currentForm = when {
            last5Games.size < 3 -> "neutral"
            recentWins >= 4 -> "hot_streak"
            recentWins <= 1 -> "cold_streak"
            else -> "neutral"
        }
        
        // Time since last played
        val timeContext = TimeContext.from(lastGame?.playedAt)
        
        return GameHistory(
            profileId = profile.playerId,
            profileName = profile.name,
            recentGames = recentGames,
            colorPreference = colorPreference,
            rivalries = rivalries,
            lastPlayedTimestamp = lastGame?.playedAt,
            lastPlayedAgo = timeContext.friendlyText,
            currentForm = currentForm
        )
    }
    
    // ==================== VALIDATION ====================
    
    fun validateProfileName(name: String): String? {
        return when {
            name.isBlank() -> "Name cannot be empty"
            name.length < 2 -> "Name must be at least 2 characters"
            name.length > 20 -> "Name must be less than 20 characters"
            name.contains(Regex("[^a-zA-Z0-9 ]")) -> "Name can only contain letters, numbers, and spaces"
            else -> null // Valid
        }
    }
    
    fun getAvailableGameColors(usedColors: Set<String> = emptySet()): List<String> {
        // At game start, return available colors from the 4 game colors
        return GAME_COLORS.filter { it !in usedColors }
    }
    
    fun assignGameColors(playerCount: Int): Map<Int, String> {
        // Assign colors to player slots (0-3)
        return (0 until minOf(playerCount, 4)).associateWith { GAME_COLORS[it] }
    }
    
    fun getColorName(hexColor: String): String {
        val index = GAME_COLORS.indexOf(hexColor)
        return if (index >= 0) COLOR_NAMES[index] else "Unknown"
    }
}
