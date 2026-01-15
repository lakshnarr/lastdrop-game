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

    // 20-tile board matching RULEBOOK.md (Updated layout)
    // Position is 1-based; index 0 in the list is tile 1.
    val tiles: List<Tile> = listOf(
        Tile(1,  "LAUNCH PAD",           TileType.START),        // +10 start, +5 pass
        Tile(2,  "NATURE GUARDIAN",      TileType.BONUS),        // +1 & Immunity (SHIELD)
        Tile(3,  "POLLUTING FACTORY",    TileType.PENALTY),      // -2 (LOSS)
        Tile(4,  "FLOWER GARDEN",        TileType.WATER_DOCK),   // +1 (ECO SAVE)
        Tile(5,  "TREE CUTTING",         TileType.DISASTER),     // -3 (GREAT CRISIS)
        Tile(6,  "MARSH SWAMP",          TileType.CHANCE),       // Card (MYSTERY)
        Tile(7,  "RECYCLED WATER",       TileType.WATER_DOCK),   // +3 (MIGHTY SAVE)
        Tile(8,  "WASTED WATER",         TileType.PENALTY),      // -1 (LOSS)
        Tile(9,  "RIVER ROBBER",         TileType.DISASTER),     // -5 (GREAT CRISIS)
        Tile(10, "LILLY POND",           TileType.WATER_DOCK),   // +1 (ECO SAVE)
        Tile(11, "SANCTUARY COVE",       TileType.CHANCE),       // Card (MYSTERY)
        Tile(12, "SHRINKING LAKE",       TileType.DISASTER),     // -4 (GREAT CRISIS)
        Tile(13, "CRYSTAL GLACIER",      TileType.WATER_DOCK),   // +2 (ECO SAVE)
        Tile(14, "DRY CITY",             TileType.PENALTY),      // -2 (LOSS)
        Tile(15, "RAIN HARVEST",         TileType.WATER_DOCK),   // +2 (ECO SAVE)
        Tile(16, "MANGROVE TRAIL",       TileType.CHANCE),       // Card (MYSTERY)
        Tile(17, "WASTED WELL",          TileType.PENALTY),      // -2 (LOSS)
        Tile(18, "EVERGREEN FOREST",     TileType.SUPER_DOCK),   // +4 (MIGHTY SAVE)
        Tile(19, "PLANT GROWER",         TileType.BONUS),        // +1 (SHIELD)
        Tile(20, "DIRTY WATER LANE",     TileType.PENALTY)       // -2 (LOSS)
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
     * - Detect lap completion (NO BONUS - handled by IntroAiActivity with LAP_BONUS constant)
     * - Detect tile
     * - Apply tile effects (variable based on tile type)
     * - If CHANCE → pick random card and apply its effect
     */
    fun processTurn(currentPosition: Int, roll: Int): TurnResult {
        val rawPosition = currentPosition + roll
        var lapBonus = 0

        // Detect lap completion (wrapping past tile 20)
        // NOTE: Lap bonus now handled in IntroAiActivity using LAP_BONUS constant
        val newPosition = when {
            rawPosition < 1 -> 1
            rawPosition > boardSize -> {
                lapBonus = 0  // No hardcoded lap bonus - IntroAiActivity handles this
                rawPosition - boardSize  // Wrap around
            }
            else -> rawPosition
        }

        val tile = tiles[newPosition - 1] // position 1 → index 0

        var scoreChange = lapBonus
        var card: ChanceCard? = null

        when (tile.type) {
            TileType.START -> {
                // LAUNCH PAD: +10 on game start (handled by IntroAiActivity), +5 on lap pass
                scoreChange += 5  // Lap bonus when passing
            }

            TileType.NORMAL -> {
                // Safe tiles - no effect (none in new layout)
            }

            TileType.BONUS -> {
                // SHIELD tiles grant +1 and immunity
                scoreChange += when (tile.index) {
                    2 -> +1   // Nature Guardian: +1 & Immunity
                    19 -> +1  // Plant Grower: +1 & Immunity
                    else -> +1
                }
            }

            TileType.PENALTY -> {
                // LOSS tiles
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
                // GREAT CRISIS tiles (severe penalties)
                scoreChange += when (tile.index) {
                    5 -> -3   // Tree Cutting: -3
                    9 -> -5   // River Robber: -5
                    12 -> -4  // Shrinking Lake: -4
                    else -> -3
                }
            }

            TileType.WATER_DOCK -> {
                // ECO SAVE & MIGHTY SAVE tiles
                scoreChange += when (tile.index) {
                    4 -> +1   // Flower Garden: +1 (ECO SAVE)
                    7 -> +3   // Recycled Water: +3 (MIGHTY SAVE)
                    10 -> +1  // Lilly Pond: +1 (ECO SAVE)
                    13 -> +2  // Crystal Glacier: +2 (ECO SAVE)
                    15 -> +2  // Rain Harvest: +2 (ECO SAVE)
                    else -> +2
                }
            }

            TileType.SUPER_DOCK -> {
                // MIGHTY SAVE
                scoreChange += when (tile.index) {
                    18 -> +4  // Evergreen Forest: +4 (MIGHTY SAVE)
                    else -> +4
                }
            }

            TileType.CHANCE -> {
                // MYSTERY tiles - CHANCE cards (tiles 6, 11, 16)
                // Card selection is done via dice roll in ChanceCardSelectionDialog
                // Score change will be applied separately after card selection
                card = null
                scoreChange += 0  // No effect until card is selected
            }
        }

        return TurnResult(
            newPosition = newPosition,
            scoreChange = scoreChange,
            tile = tile,
            chanceCard = card
        )
    }
    
    /**
     * Get 6 random chance cards for the selection dialog
     * Distribution: 2 from Tier A (1-10), 2 from Tier B (11-14), 2 from Tier C (15-20)
     */
    fun getSixRandomChanceCards(): List<ChanceCard> {
        val tierA = chanceCards.filter { it.number in 1..10 }.shuffled().take(2)
        val tierB = chanceCards.filter { it.number in 11..14 }.shuffled().take(2)
        val tierC = chanceCards.filter { it.number in 15..20 }.shuffled().take(2)
        return (tierA + tierB + tierC).shuffled()
    }
    
    /**
     * Get a specific chance card by number
     */
    fun getChanceCard(number: Int): ChanceCard? {
        return chanceCards.find { it.number == number }
    }
    
    /**
     * Get all chance cards (for reference)
     */
    fun getAllChanceCards(): List<ChanceCard> = chanceCards.toList()

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
