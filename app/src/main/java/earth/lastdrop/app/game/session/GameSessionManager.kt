package earth.lastdrop.app.game.session

/**
 * Lightweight session manager (pure Kotlin) to track turn order and moves.
 */
class GameSessionManager(
    private val tiles: List<TileType>
) {
    private val positions = mutableMapOf<String, Int>() // playerId -> tile index
    private val history = ArrayDeque<MoveContext>()
    private var players: List<PlayerInfo> = emptyList()
    private var currentIndex = 0
    private var turnNumber = 0

    fun setPlayers(list: List<PlayerInfo>) {
        players = list
        positions.clear()
        list.forEach { positions[it.id] = 0 }
        currentIndex = 0
        turnNumber = 0
        history.clear()
    }

    fun setPosition(playerId: String, tileIndex: Int) {
        positions[playerId] = tileIndex.coerceAtLeast(0)
    }

    fun setCurrentPlayerIndex(index: Int) {
        if (players.isNotEmpty()) {
            currentIndex = index.coerceIn(0, players.lastIndex)
        }
    }

    fun currentState(): GameState = GameState(
        players = players,
        currentPlayerIndex = currentIndex,
        leaderIds = leaderIds(),
        turnNumber = turnNumber,
        scoreLeaderIds = emptyList(),
        hotStreakPlayerIds = emptyList(),
        trailingIds = emptyList(),
        comebackPlayerIds = emptyList(),
        scoreGaps = emptyMap()
    )

    fun moveCurrentPlayer(steps: Int): MoveContext? {
        val player = players.getOrNull(currentIndex) ?: return null
        val from = positions[player.id] ?: 0
        val rawTo = from + steps
        val clampedTo = rawTo.coerceAtMost(tiles.lastIndex)
        val tileType = tiles.getOrElse(clampedTo) { TileType.FINISH }
        val ctx = MoveContext(
            player = player,
            fromTile = from,
            toTile = clampedTo,
            diceValue = steps,
            tileType = tileType
        )
        positions[player.id] = clampedTo
        history.addLast(ctx)
        advanceTurn()
        return ctx
    }

    fun undoLastMove(): MoveContext? {
        val last = history.removeLastOrNull() ?: return null
        positions[last.player.id] = last.fromTile
        // Step turn back to the player who just moved
        currentIndex = players.indexOfFirst { it.id == last.player.id }.coerceAtLeast(0)
        turnNumber = (turnNumber - 1).coerceAtLeast(0)
        return last
    }

    private fun advanceTurn() {
        turnNumber += 1
        if (players.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % players.size
        }
    }

    private fun leaderIds(): List<String> {
        val maxPos = positions.values.maxOrNull() ?: 0
        return positions.filter { it.value == maxPos }.keys.toList()
    }
}
