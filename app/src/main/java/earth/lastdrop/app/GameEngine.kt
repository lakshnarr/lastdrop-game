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
        Tile(1,  "Start Point",          TileType.START),
        Tile(2,  "Sunny Patch",          TileType.PENALTY),
        Tile(3,  "Rain Dock",            TileType.WATER_DOCK),
        Tile(4,  "Leak Lane",            TileType.PENALTY),
        Tile(5,  "Storm Zone",           TileType.DISASTER),
        Tile(6,  "Cloud Hill",           TileType.BONUS),
        Tile(7,  "Oil Spill Bay",        TileType.DISASTER),
        Tile(8,  "Riverbank Road",       TileType.NORMAL),
        Tile(9,  "Marsh Land",           TileType.CHANCE),
        Tile(10, "Drought Desert",       TileType.DISASTER),
        Tile(11, "Clean Well",           TileType.WATER_DOCK),
        Tile(12, "Waste Dump",           TileType.DISASTER),
        Tile(13, "Sanctuary Stop",       TileType.CHANCE),
        Tile(14, "Sewage Drain Street",  TileType.PENALTY),
        Tile(15, "Filter Plant",         TileType.WATER_DOCK),
        Tile(16, "Mangrove Mile",        TileType.CHANCE),
        Tile(17, "Heatwave Road",        TileType.PENALTY),
        Tile(18, "Spring Fountain",      TileType.SUPER_DOCK),
        Tile(19, "Eco Garden",           TileType.NORMAL),
        Tile(20, "Great Reservoir",      TileType.NORMAL)
    )

    // All 20 chance cards from RULEBOOK.md (Elimination Mode)
    private val chanceCards: List<ChanceCard> = listOf(
        ChanceCard(number = 1, description = "You fixed a tap leak", effect = +2),
        ChanceCard(number = 2, description = "Rainwater harvested", effect = +2),
        ChanceCard(number = 3, description = "You planted two trees", effect = +1),
        ChanceCard(number = 4, description = "Cool clouds formed", effect = +1),
        ChanceCard(number = 5, description = "You cleaned a riverbank", effect = +1),
        ChanceCard(number = 6, description = "Discovered a tiny spring", effect = +3),
        ChanceCard(number = 7, description = "You saved a wetland animal", effect = +1),
        ChanceCard(number = 8, description = "You reused RO water", effect = +1),
        ChanceCard(number = 9, description = "Used bucket instead of shower", effect = +2),
        ChanceCard(number = 10, description = "Drip irrigation success", effect = +2),
        ChanceCard(number = 11, description = "Skip next penalty", effect = 0),  // Special
        ChanceCard(number = 12, description = "Move forward 2 tiles", effect = 0),  // Special
        ChanceCard(number = 13, description = "Swap positions with next player", effect = 0),  // Special
        ChanceCard(number = 14, description = "Water Shield (next damage=0)", effect = 0),  // Special
        ChanceCard(number = 15, description = "You left tap running", effect = -1),
        ChanceCard(number = 16, description = "Your bottle spilled", effect = -1),
        ChanceCard(number = 17, description = "Pipe burst nearby", effect = -3),
        ChanceCard(number = 18, description = "Heat wave dries water", effect = -2),
        ChanceCard(number = 19, description = "Sewage contamination", effect = -2),
        ChanceCard(number = 20, description = "Flood washed away water", effect = -3)
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
                scoreChange += 1  // Cloud Hill: +1
            }

            TileType.PENALTY -> {
                // Variable penalty based on tile
                scoreChange += when (tile.index) {
                    2, 4 -> -1   // Sunny Patch, Leak Lane
                    else -> -2   // Sewage Drain Street, Heatwave Road
                }
            }

            TileType.DISASTER -> {
                // Major penalties
                scoreChange += when (tile.index) {
                    12 -> -2    // Waste Dump
                    5, 10 -> -3 // Storm Zone, Drought Desert
                    else -> -4  // Oil Spill Bay
                }
            }

            TileType.WATER_DOCK -> {
                // Water collection bonuses
                scoreChange += when (tile.index) {
                    15 -> +1    // Filter Plant
                    11 -> +2    // Clean Well
                    else -> +3  // Rain Dock
                }
            }

            TileType.SUPER_DOCK -> {
                scoreChange += 4  // Spring Fountain: +4
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
