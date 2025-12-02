package com.example.lastdrop

import kotlin.random.Random

// What type of tile is this?
enum class TileType {
    START,
    NORMAL,
    CHANCE,
    BONUS,
    PENALTY
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

    // 20-tile sample board – adjust names/types to match your real board
    // Position is 1-based; index 0 in the list is tile 1.
    val tiles: List<Tile> = listOf(
        Tile(1,  "Start",             TileType.START),
        Tile(2,  "Clean Water",       TileType.NORMAL),
        Tile(3,  "Chance – Rainfall", TileType.CHANCE),
        Tile(4,  "Drought Zone",      TileType.PENALTY),
        Tile(5,  "Recycling Drive",   TileType.BONUS),
        Tile(6,  "Chance – Pollution",TileType.CHANCE),
        Tile(7,  "Green Belt",        TileType.NORMAL),
        Tile(8,  "Riverbank",         TileType.NORMAL),
        Tile(9,  "Chance – Awareness",TileType.CHANCE),
        Tile(10, "Factory Waste",     TileType.PENALTY),
        Tile(11, "Solar Park",        TileType.BONUS),
        Tile(12, "Chance – Community",TileType.CHANCE),
        Tile(13, "Reservoir",         TileType.NORMAL),
        Tile(14, "Flood Plain",       TileType.PENALTY),
        Tile(15, "Wetlands",          TileType.NORMAL),
        Tile(16, "Chance – Policy",   TileType.CHANCE),
        Tile(17, "Tree Line",         TileType.NORMAL),
        Tile(18, "Rainwater Harvest", TileType.BONUS),
        Tile(19, "Chance – Surprise", TileType.CHANCE),
        Tile(20, "Final Lake",        TileType.NORMAL)
    )

    // Use your existing ChanceCard(effect: Int) class
    private val chanceCards: List<ChanceCard> = listOf(
        ChanceCard(
            number = 1,
            description = "Light Rain: +2 points",
            effect = +2
        ),
        ChanceCard(
            number = 2,
            description = "Heavy Rain: +3 points",
            effect = +3
        ),
        ChanceCard(
            number = 3,
            description = "Pollution Spill: -3 points",
            effect = -3
        ),
        ChanceCard(
            number = 4,
            description = "Plant 5 Trees: +4 points",
            effect = +4
        ),
        ChanceCard(
            number = 5,
            description = "Waste Dumped in River: -4 points",
            effect = -4
        ),
        ChanceCard(
            number = 6,
            description = "Community Clean-up: +2 points",
            effect = +2
        ),
        ChanceCard(
            number = 7,
            description = "Water Leak at Home: -2 points",
            effect = -2
        ),
        ChanceCard(
            number = 8,
            description = "New Water Policy: +3 points",
            effect = +3
        ),
        ChanceCard(
            number = 9,
            description = "Careless Tap Left Open: -1 point",
            effect = -1
        ),
        ChanceCard(
            number = 10,
            description = "Innovative Water Saver: +5 points",
            effect = +5
        )
    )

    private val boardSize: Int = tiles.size

    /**
     * Process one turn:
     * - Move player
     * - Detect tile
     * - If CHANCE → pick random card and apply its effect
     * - Apply bonus/penalty tiles if required
     */
    fun processTurn(currentPosition: Int, roll: Int): TurnResult {
        val rawPosition = currentPosition + roll

        // Clamp to last tile for now (you can change to "bounce back" if you want)
        val newPosition = when {
            rawPosition < 1 -> 1
            rawPosition > boardSize -> boardSize
            else -> rawPosition
        }

        val tile = tiles[newPosition - 1] // position 1 → index 0

        var scoreChange = 0
        var card: ChanceCard? = null

        when (tile.type) {
            TileType.START -> {
                // Usually no score change on start
            }

            TileType.NORMAL -> {
                // Nothing special
            }

            TileType.BONUS -> {
                // Simple +2 bonus tile (adjust as you like)
                scoreChange += 2
            }

            TileType.PENALTY -> {
                // Simple -2 penalty tile (adjust as you like)
                scoreChange -= 2
            }

            TileType.CHANCE -> {
                // Pick one random card from the deck
                card = drawRandomChanceCard()
                scoreChange += card.effect   // <-- use existing property 'effect'
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
