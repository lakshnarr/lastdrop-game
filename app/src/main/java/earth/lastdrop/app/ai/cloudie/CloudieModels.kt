package earth.lastdrop.app.ai.cloudie

import earth.lastdrop.app.game.session.GameState
import earth.lastdrop.app.game.session.MoveContext

/**
 * Data contracts for Cloudie brain/voice. Pure Kotlin, no Android deps.
 */

enum class CloudieEventType {
    GAME_START,
    TURN_PROMPT,
    DICE_ROLL,
    LANDED_TILE,
    UNDO,
    WIN,
    WARNING_TIMEOUT,
    WARNING_MISPLACEMENT,
    WIN_ANIMATION
}

data class CloudieRequest(
    val eventType: CloudieEventType,
    val gameState: GameState,
    val moveContext: MoveContext? = null
)

data class CloudieResponse(
    val line: String,
    val metadata: Map<String, String> = emptyMap()
)
