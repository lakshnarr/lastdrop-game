package earth.lastdrop.app

import kotlin.random.Random

class LocalAIPresenter(private val emit: (String) -> Unit) {

    fun onGameStart(names: List<String>) {
        if (names.isEmpty()) return
        val line = when {
            names.size == 1 -> "Let's go ${names.first()}!"
            else -> "Welcome ${names.joinToString()} — ready to roll?"
        }
        emit(line)
    }

    fun onTurnStart(name: String) {
        val options = listOf(
            "Your turn, $name. Roll when ready!",
            "$name, step up. Show us the dice!",
            "Alright $name, let's see that throw." 
        )
        emit(options.random())
    }

    fun onTurnResult(name: String, tile: String?, delta: Int) {
        val mood = when {
            delta > 0 -> "Nice gain"
            delta < 0 -> "Tough break"
            else -> "Steady move"
        }
        val tileLabel = tile ?: "the board"
        val options = listOf(
            "$name hits $tileLabel. $mood!",
            "$name lands on $tileLabel. Keep going!",
            "$name is at $tileLabel."
        )
        emit(options.random())
    }

    fun onGameEnd(winner: String?) {
        val line = winner?.let { "${it} takes the win!" } ?: "Game over. Well played!"
        emit(line)
    }
}
