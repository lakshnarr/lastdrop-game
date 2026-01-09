package earth.lastdrop.app

import kotlin.random.Random

// What type of tile is this?
enum class TileType {
    START,
    NORMAL,      // Safe tiles (no effect)
    CHANCE,
    BONUS,       // Small bonus tiles
    PENALTY,     // Small penalty tiles
    DISASTER,    // Major penalty tiles
    WATER_DOCK,  // Water collection tiles
    SUPER_DOCK   // Major bonus tile
}

// One tile on the board
data class Tile(
    val index: Int,          // 1-based board position
    val name: String,
    val type: TileType
)

// NOTE: We DO NOT declare ChanceCard here.
// We use the existing ChanceCard data class in your project,
// which already has: number, description, effect.

// Result of one turn after a roll
data class TurnResult(
    val newPosition: Int,
    val scoreChange: Int,
    val tile: Tile,
    val chanceCard: ChanceCard?
)

/**
 * GameEngine
 * ----------
 * - Knows all tiles on the board
 * - Knows all chance cards
 * - Given (currentPosition, diceRoll) returns:
 *      newPosition, scoreChange, tile landed, optional chanceCard
 */
class GameEngine {

    // 20-tile board matching RULEBOOK.md
    // Position is 1-based; index 0 in the list is tile 1.
    val tiles: List<Tile> = listOf(
        Tile(1,  "Launch Pad",           TileType.START),
        Tile(2,  "Nature Guardian",      TileType.BONUS),
        Tile(3,  "Polluting Factory",    TileType.PENALTY),
        Tile(4,  "Flower Garden",        TileType.BONUS),
        Tile(5,  "Tree Cutting",         TileType.DISASTER),
        Tile(6,  "Marsh Swamp",          TileType.CHANCE),
        Tile(7,  "Recycled Water",       TileType.WATER_DOCK),
        Tile(8,  "Wasted Water",         TileType.PENALTY),
        Tile(9,  "River Robber",         TileType.DISASTER),
        Tile(10, "Lilly Pond",           TileType.BONUS),
        Tile(11, "Sanctuary Cove",       TileType.CHANCE),
        Tile(12, "Shrinking Lake",       TileType.DISASTER),
        Tile(13, "Crystal Glacier",      TileType.BONUS),
        Tile(14, "Dry City",             TileType.PENALTY),
        Tile(15, "Rain Harvest",         TileType.BONUS),
        Tile(16, "Mangrove Trail",       TileType.CHANCE),
        Tile(17, "Wasted Well",          TileType.PENALTY),
        Tile(18, "Evergreen Forest",     TileType.WATER_DOCK),
        Tile(19, "Plant Grower",         TileType.BONUS),
        Tile(20, "Dirty Water Lane",     TileType.PENALTY)
    )

    // All 20 chance cards from RULEBOOK.md (Elimination Mode)
    private val chanceCards: List<ChanceCard> = listOf(
        ChanceCard(number = 1, description = "Fixed tap leak", effect = +2),
        ChanceCard(number = 2, description = "Rain harvested", effect = +2),
        ChanceCard(number = 3, description = "Planted trees", effect = +1),
        ChanceCard(number = 4, description = "Clouds formed", effect = +1),
        ChanceCard(number = 5, description = "Preserved riverbank", effect = +2),
        ChanceCard(number = 6, description = "Cleaned well", effect = +2),
        ChanceCard(number = 7, description = "Saved plant", effect = +1),
        ChanceCard(number = 8, description = "Recycled water", effect = +1),
        ChanceCard(number = 9, description = "Bucket bath", effect = +2),
        ChanceCard(number = 10, description = "Drip irrigation", effect = +2),
        ChanceCard(number = 11, description = "Skip penalty", effect = 0),  // Special: Immunity
        ChanceCard(number = 12, description = "Move forward 2", effect = 0),  // Special: Move 2 tiles
        ChanceCard(number = 13, description = "Swap with next", effect = 0),  // Special: Next player plays twice
        ChanceCard(number = 14, description = "Water Shield", effect = 0),  // Special: Immunity
        ChanceCard(number = 15, description = "Left tap running", effect = -1),
        ChanceCard(number = 16, description = "Bottle spilled", effect = -1),
        ChanceCard(number = 17, description = "Pipe burst", effect = -3),
        ChanceCard(number = 18, description = "Climate dries water", effect = -2),
        ChanceCard(number = 19, description = "Sewage contamination", effect = -2),
        ChanceCard(number = 20, description = "Wasted papers", effect = -3)
    )

    private val boardSize: Int = tiles.size

    /**
     * Process one turn:
     * - Move player
     * - Detect lap completion (+5 bonus)
     * - Detect tile
     * - Apply tile effects (variable based on tile type)
     * - If CHANCE → pick random card and apply its effect
     */
    fun processTurn(currentPosition: Int, roll: Int): TurnResult {
        val rawPosition = currentPosition + roll
        var lapBonus = 0

        // Detect lap completion (wrapping past tile 20)
        val newPosition = when {
            rawPosition < 1 -> 1
            rawPosition > boardSize -> {
                lapBonus = 5  // Lap bonus!
                rawPosition - boardSize  // Wrap around
            }
            else -> rawPosition
        }

        val tile = tiles[newPosition - 1] // position 1 → index 0

        var scoreChange = lapBonus
        var card: ChanceCard? = null

        when (tile.type) {
            TileType.START -> {
                // No score change on start
            }

            TileType.NORMAL -> {
                // Safe tiles - no effect
            }

            TileType.BONUS -> {
                // Variable bonus based on tile
                scoreChange += when (tile.index) {
                    2 -> +1   // Nature Guardian: +1 & Immunity
                    4, 10 -> +1   // Flower Garden, Lilly Pond: +1
                    13, 15 -> +2  // Crystal Glacier, Rain Harvest: +2
                    19 -> +1  // Plant Grower: +1
                    else -> +1
                }
            }

            TileType.PENALTY -> {
                // Variable penalty based on tile
                scoreChange += when (tile.index) {
                    3 -> -2   // Polluting Factory: -2
                    8 -> -1   // Wasted Water: -1
                    14 -> -2  // Dry City: -2
                    17 -> -2  // Wasted Well: -2
                    20 -> -2  // Dirty Water Lane: -2
                    else -> -2
                }
            }

            TileType.DISASTER -> {
                // Major penalties (Great Crisis)
                scoreChange += when (tile.index) {
                    5 -> -3   // Tree Cutting: -3
                    9 -> -5   // River Robber: -5
                    12 -> -4  // Shrinking Lake: -4
                    else -> -3
                }
            }

            TileType.WATER_DOCK -> {
                // Mighty Save bonuses
                scoreChange += when (tile.index) {
                    7 -> +3   // Recycled Water: +3
                    18 -> +4  // Evergreen Forest: +4
                    else -> +3
                }
            }

            TileType.SUPER_DOCK -> {
                scoreChange += 4  // Unused in new layout
            }

            TileType.CHANCE -> {
                // Pick one random card from the deck
                card = drawRandomChanceCard()
                scoreChange += card.effect
            }
        }

        return TurnResult(
            newPosition = newPosition,
            scoreChange = scoreChange,
            tile = tile,
            chanceCard = card
        )
    }

    private fun drawRandomChanceCard(): ChanceCard {
        if (chanceCards.isEmpty()) {
            // Fallback safety – should not happen
            return ChanceCard(
                number = 0,
                description = "No card (deck empty)",
                effect = 0
            )
        }
        val index = Random.nextInt(chanceCards.size)
        return chanceCards[index]
    }
}
