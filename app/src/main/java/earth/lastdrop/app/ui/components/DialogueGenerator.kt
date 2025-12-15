package com.example.lastdrop.ui.components

import earth.lastdrop.app.ChanceCard
import earth.lastdrop.app.Tile
import earth.lastdrop.app.PlayerProfile

/**
 * DialogueGenerator - Creates contextual Cloudie dialogue based on game events
 * 
 * Generates personalized, event-driven responses for:
 * - Dice rolls
 * - Tile landings
 * - Chance cards
 * - Score changes
 * - Player eliminations
 * - Game start/end
 */
class DialogueGenerator {

    companion object {
        private val encouragements = listOf(
            "Keep going!",
            "Nice roll!",
            "You've got this!",
            "Stay strong!",
            "Keep the flow going!"
        )

        private val warnings = listOf(
            "Careful now...",
            "Watch your drops!",
            "This could be risky!",
            "Be mindful!",
            "Think wisely!"
        )

        private val celebrations = listOf(
            "Amazing!",
            "Excellent work!",
            "Fantastic!",
            "Well done!",
            "Brilliant move!"
        )

        private val sympathies = listOf(
            "Don't worry, you'll bounce back!",
            "Tough break...",
            "It happens to everyone!",
            "Keep your head up!",
            "Next roll will be better!"
        )
    }

    /**
     * Generate dialogue for game start
     */
    fun generateGameStart(playerCount: Int, firstPlayerName: String): String {
        return when (playerCount) {
            2 -> "Two brave water warriors! $firstPlayerName, you're up first. Let's begin our journey!"
            3 -> "Three champions join the quest! $firstPlayerName leads the way. Roll when ready!"
            4 -> "A full party of four! $firstPlayerName takes the first turn. Let the adventure begin!"
            else -> "Welcome, brave souls! $firstPlayerName goes first. Roll the dice!"
        }
    }

    /**
     * Generate dialogue for dice roll announcement
     */
    fun generateRollAnnouncement(playerName: String, diceValue: Int): String {
        return when (diceValue) {
            1 -> "$playerName rolled a 1. Small step forward!"
            2 -> "$playerName rolled a 2. Steady progress!"
            3 -> "$playerName rolled a 3. Moving along nicely!"
            4 -> "$playerName rolled a 4. Great roll!"
            5 -> "$playerName rolled a 5. Excellent distance!"
            6 -> "$playerName rolled a 6! Maximum movement!"
            else -> "$playerName rolled $diceValue."
        }
    }

    /**
     * Generate dialogue for landing on a tile
     */
    fun generateTileLanding(
        playerName: String,
        tile: Tile,
        scoreChange: Int,
        newScore: Int
    ): String {
        val scoreText = when {
            scoreChange > 0 -> "gained $scoreChange drops! (Total: $newScore)"
            scoreChange < 0 -> "lost ${-scoreChange} drops! (Total: $newScore)"
            else -> "stays at $newScore drops."
        }

        return when {
            tile.name.contains("Oasis", ignoreCase = true) -> 
                "$playerName found an ${tile.name}! ${celebrations.random()} You $scoreText"
            
            tile.name.contains("Desert", ignoreCase = true) || tile.name.contains("Drought", ignoreCase = true) -> 
                "$playerName entered ${tile.name}. ${sympathies.random()} You $scoreText"
            
            tile.name.contains("River", ignoreCase = true) || tile.name.contains("Spring", ignoreCase = true) -> 
                "$playerName reached ${tile.name}! ${encouragements.random()} You $scoreText"
            
            tile.name.contains("Storm", ignoreCase = true) -> 
                "$playerName encountered ${tile.name}! ${warnings.random()} You $scoreText"
            
            else -> "$playerName landed on ${tile.name}. You $scoreText"
        }
    }

    /**
     * Generate dialogue for drawing a chance card
     */
    fun generateChanceCard(
        playerName: String,
        card: ChanceCard
    ): String {
        val cardEffect = when {
            card.effect > 0 -> "Luck is on your side!"
            card.effect < 0 -> "Sometimes fortune tests us!"
            else -> "An interesting turn of events!"
        }

        return "$playerName drew: '${card.description}' $cardEffect"
    }

    /**
     * Generate dialogue for player elimination
     */
    fun generateElimination(playerName: String, remainingPlayers: Int): String {
        return when (remainingPlayers) {
            1 -> "$playerName has been eliminated! Only one warrior remains to claim victory!"
            2 -> "$playerName is out of drops and eliminated. Two competitors left in the race!"
            3 -> "$playerName's journey ends here. Three adventurers continue forward!"
            else -> "$playerName has been eliminated from the quest. ${sympathies.random()}"
        }
    }

    /**
     * Generate dialogue for game end
     */
    fun generateGameEnd(winnerName: String, finalScore: Int): String {
        return when {
            finalScore >= 50 -> "$winnerName wins with $finalScore drops! ${celebrations.random()} A legendary performance!"
            finalScore >= 30 -> "$winnerName claims victory with $finalScore drops! ${celebrations.random()} Well earned!"
            finalScore >= 15 -> "$winnerName wins with $finalScore drops! ${encouragements.random()} A hard-fought battle!"
            else -> "$winnerName survives to win with $finalScore drops! Every drop counts!"
        }
    }

    /**
     * Generate random idle chatter
     */
    fun generateIdleChatter(): String {
        val chatter = listOf(
            "Every drop matters in this adventure!",
            "Water is life. Conserve wisely!",
            "The journey tests your wisdom.",
            "Stay hydrated, stay focused!",
            "Fortune favors the prepared!",
            "Each decision shapes your destiny.",
            "Remember: waste not, want not!",
            "The oasis awaits the persistent.",
            "Patience and strategy win the day!",
            "Your next roll could change everything!"
        )
        return chatter.random()
    }

    /**
     * Generate turn transition dialogue
     */
    fun generateTurnTransition(
        nextPlayerName: String,
        currentPlayerScore: Int,
        isLeading: Boolean
    ): String {
        val statusText = when {
            isLeading -> "leading with $currentPlayerScore drops"
            currentPlayerScore > 0 -> "with $currentPlayerScore drops"
            else -> "seeking their first drops"
        }
        
        return "$nextPlayerName's turn! Currently $statusText. ${encouragements.random()}"
    }

    /**
     * Generate contextual response based on score threshold
     */
    fun generateScoreThresholdDialogue(
        playerName: String,
        score: Int
    ): String {
        return when {
            score <= 0 -> "$playerName, you need drops urgently! ${warnings.random()}"
            score in 1..5 -> "$playerName is running low. Every drop counts now!"
            score in 6..15 -> "$playerName has a modest reserve. ${encouragements.random()}"
            score in 16..30 -> "$playerName is doing well! ${celebrations.random()}"
            score > 30 -> "$playerName has abundant drops! ${celebrations.random()} Stay the course!"
            else -> "$playerName continues the journey."
        }
    }

    /**
     * Generate combo dialogue (multiple positive events in a row)
     */
    fun generateComboDialogue(comboCount: Int, playerName: String): String {
        return when (comboCount) {
            2 -> "$playerName is on a roll! Two good moves in a row!"
            3 -> "$playerName is unstoppable! Three consecutive wins! ${celebrations.random()}"
            4 -> "$playerName is on fire! Four amazing rolls! ${celebrations.random()}"
            else -> "$playerName's streak continues! $comboCount in a row! Incredible!"
        }
    }

    /**
     * Generate comeback dialogue (player recovering from low score)
     */
    fun generateComebackDialogue(playerName: String, scoreGain: Int): String {
        return when {
            scoreGain >= 10 -> "$playerName makes an incredible comeback with +$scoreGain drops! ${celebrations.random()}"
            scoreGain >= 5 -> "$playerName is climbing back with +$scoreGain drops! ${encouragements.random()}"
            else -> "$playerName recovers $scoreGain drops. ${encouragements.random()}"
        }
    }
}
