package earth.lastdrop.app.ai.cloudie

import earth.lastdrop.app.game.session.GameState
import earth.lastdrop.app.game.session.MoveContext
import kotlin.random.Random

/**
 * Pure Kotlin dialogue generator for Cloudie.
 * Returns a single line for a given event and context.
 */
class CloudieBrain(
    private val random: Random = Random.Default
) {
    fun generate(request: CloudieRequest): CloudieResponse {
        val line = when (request.eventType) {
            CloudieEventType.GAME_START -> gameStart(request.gameState)
            CloudieEventType.TURN_PROMPT -> turnPrompt(request.gameState)
            CloudieEventType.DICE_ROLL -> diceRoll(request.moveContext)
            CloudieEventType.LANDED_TILE -> landedTile(request.moveContext)
            CloudieEventType.UNDO -> undoLine(request.moveContext)
            CloudieEventType.WIN -> winLine(request.gameState)
            CloudieEventType.WARNING_TIMEOUT -> timeoutLine(request.moveContext)
            CloudieEventType.WARNING_MISPLACEMENT -> misplacementLine(request.moveContext)
            CloudieEventType.WIN_ANIMATION -> winAnimationLine(request.gameState)
        }
        return CloudieResponse(line = line)
    }

    private fun gameStart(state: GameState): String {
        val players = state.players
        return when (players.size) {
            1 -> "Solo flight! ${name(players[0])}, let's make it a splashy run."
            2 -> "Duo duel! ${name(players[0])} vs ${name(players[1])}—may the best drop win!"
            3 -> "Triple drizzle incoming. ${namesList(players)}—ready to roll?"
            else -> "Raindrop squad, assemble! ${namesList(players)}—let's storm the board."
        }
    }

    private fun turnPrompt(state: GameState): String {
        val p = state.players.getOrNull(state.currentPlayerIndex) ?: return "Your turn."
        val leaderNote = leaderNote(state, p.id) + comebackHint(state)
        val prompts = listOf(
            "${name(p)}, your cloud is charged—roll when ready!$leaderNote",
            "${name(p)} to the runway. Let's see that number!$leaderNote",
            "Heads up ${name(p)}! Time to drop some luck.$leaderNote"
        )
        return pick(prompts)
    }

    private fun diceRoll(move: MoveContext?): String {
        move ?: return "Dice time!"
        val v = move.diceValue
        return when {
            v <= 2 -> pick(listOf("Tiny drizzle: $v.", "A shy sprinkle: $v."))
            v in 3..4 -> pick(listOf("Steady shower: $v.", "Nice and even: $v."))
            else -> pick(listOf("Monsoon roll! $v.", "Thunder drop! Rolled $v."))
        }
    }

    private fun landedTile(move: MoveContext?): String {
        move ?: return "Landed!"
        val base = "${name(move.player)} landed on tile ${move.toTile}"
        return when (move.tileType) {
            earth.lastdrop.app.game.session.TileType.SAFE -> "$base — safe skies ahead."
            earth.lastdrop.app.game.session.TileType.BONUS -> "$base — jackpot puddles!"
            earth.lastdrop.app.game.session.TileType.DANGER -> "$base — watch the drought cracks!"
            earth.lastdrop.app.game.session.TileType.WATER -> "$base — secret fountain found!"
            earth.lastdrop.app.game.session.TileType.SKIP -> "$base — wind pause, skip next turn."
            earth.lastdrop.app.game.session.TileType.START -> "$base — back at base camp."
            earth.lastdrop.app.game.session.TileType.FINISH -> "$base — finish line glow!"
        }
    }

    private fun undoLine(move: MoveContext?): String {
        move ?: return "Time rewind—last move undone."
        return "Rewinding raindrop for ${name(move.player)}. Back to tile ${move.fromTile}."
    }

    private fun winLine(state: GameState): String {
        val leader = state.players.firstOrNull { it.id in state.scoreLeaderIds.ifEmpty { state.leaderIds } }
        val champ = leader ?: state.players.getOrNull(state.currentPlayerIndex)
        val who = champ?.let { name(it) } ?: "Player"
        val streakNote = when {
            state.hotStreakPlayerIds.contains(champ?.id) -> " (unstoppable streak!)"
            else -> ""
        }
        return "$who takes the crown$streakNote! Rainbows all around."
    }

    private fun timeoutLine(move: MoveContext?): String {
        return "Timer's up—I'll keep the storm moving."
    }

    private fun misplacementLine(move: MoveContext?): String {
        return "Cloudie alert: a coin is off its cloud. Fix the placement and we’ll roll on."
    }

    private fun winAnimationLine(state: GameState): String {
        val leader = state.players.firstOrNull { it.id in state.scoreLeaderIds.ifEmpty { state.leaderIds } }
        val who = leader?.let { name(it) } ?: "Our winner"
        return "Celebration time! $who is lighting up the skies."
    }

    private fun comebackHint(state: GameState): String {
        val comebackId = state.comebackPlayerIds.firstOrNull() ?: return ""
        val player = state.players.firstOrNull { it.id == comebackId } ?: return ""
        val gap = state.scoreGaps[comebackId]
        val gapText = gap?.takeIf { it > 0 }?.let { "only $it behind" } ?: "closing in"
        return " ${name(player)} is $gapText—watch this comeback."
    }

    private fun leaderNote(state: GameState, currentId: String): String {
        val scoreLeader = state.scoreLeaderIds.firstOrNull()
        val hot = state.hotStreakPlayerIds.firstOrNull()
        return when {
            scoreLeader == currentId -> " You are leading the drops."
            hot == currentId -> " You're on a streak—keep it flowing!"
            scoreLeader != null -> " ${state.players.firstOrNull { it.id == scoreLeader }?.let { name(it) } ?: "Someone"} is leading."
            hot != null -> " ${state.players.firstOrNull { it.id == hot }?.let { name(it) } ?: "Someone"} is heating up."
            else -> ""
        }
    }

    private fun pick(options: List<String>): String = options[random.nextInt(options.size)]

    private fun name(p: earth.lastdrop.app.game.session.PlayerInfo): String {
        return p.nickname?.takeIf { it.isNotBlank() } ?: p.name
    }

    private fun namesList(players: List<earth.lastdrop.app.game.session.PlayerInfo>): String {
        return players.joinToString(", ") { name(it) }
    }
}
