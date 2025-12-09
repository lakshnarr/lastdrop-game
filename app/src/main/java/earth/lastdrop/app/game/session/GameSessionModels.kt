package earth.lastdrop.app.game.session

import earth.lastdrop.app.TileType as EngineTileType

/**
 * Lightweight game/session data models (no Android deps) feeding Cloudie and intro screens.
 */

data class PlayerInfo(
    val id: String,
    val name: String,
    val nickname: String?,
    val colorHex: String,
    val isAI: Boolean = false,
    val isGuest: Boolean = false
)

enum class TileType {
    START,
    SAFE,
    BONUS,
    DANGER,
    WATER,
    SKIP,
    FINISH
}

data class MoveContext(
    val player: PlayerInfo,
    val fromTile: Int,
    val toTile: Int,
    val diceValue: Int,
    val tileType: TileType,
    val engineTileType: EngineTileType? = null,
    val tileName: String? = null,
    val scoreDelta: Int? = null,
    val chanceCardDescription: String? = null,
    val chanceCardEffect: Int? = null
)

data class GameState(
    val players: List<PlayerInfo>,
    val currentPlayerIndex: Int,
    val leaderIds: List<String> = emptyList(),
    val turnNumber: Int = 0,
    val scoreLeaderIds: List<String> = emptyList(),
    val hotStreakPlayerIds: List<String> = emptyList(),
    val trailingIds: List<String> = emptyList(),
    val comebackPlayerIds: List<String> = emptyList(),
    val scoreGaps: Map<String, Int> = emptyMap()
)
