package earth.lastdrop.app

/**
 * Routes live state payloads to the API.
 */
class LiveStatePusher(private val apiManager: ApiManager) {

    fun pushResetState(
        playerNames: List<String>,
        playerColors: List<String>,
        playerCount: Int
    ) {
        apiManager.pushResetState(
            playerNames = playerNames,
            playerColors = playerColors,
            playerCount = playerCount
        )
    }

    fun pushRollingStatus(snapshot: RollingStateSnapshot) {
        apiManager.pushRollingStatus(
            playerNames = snapshot.playerNames,
            playerColors = snapshot.playerColors,
            playerPositions = snapshot.playerPositions,
            playerScores = snapshot.playerScores,
            playerCount = snapshot.playerCount,
            currentPlayer = snapshot.currentPlayer,
            playWithTwoDice = snapshot.playWithTwoDice,
            diceColorMap = snapshot.diceColorMap,
            diceRollingStatus = snapshot.diceRollingStatus,
            lastDice1 = snapshot.lastDice1,
            lastDice2 = snapshot.lastDice2,
            lastAvg = snapshot.lastAvg
        )
    }

    fun pushLiveState(snapshot: LiveStateSnapshot) {
        apiManager.pushLiveState(
            playerNames = snapshot.playerNames,
            playerColors = snapshot.playerColors,
            playerPositions = snapshot.playerPositions,
            playerScores = snapshot.playerScores,
            playerCount = snapshot.playerCount,
            currentPlayer = snapshot.currentPlayer,
            playWithTwoDice = snapshot.playWithTwoDice,
            diceColorMap = snapshot.diceColorMap,
            lastDice1 = snapshot.lastDice1,
            lastDice2 = snapshot.lastDice2,
            lastAvg = snapshot.lastAvg,
            lastTileName = snapshot.lastTileName,
            lastTileType = snapshot.lastTileType,
            lastChanceCardNumber = snapshot.lastChanceCardNumber,
            lastChanceCardText = snapshot.lastChanceCardText,
            rolling = snapshot.rolling,
            eventType = snapshot.eventType,
            eventMessage = snapshot.eventMessage
        )
    }
}

data class RollingStateSnapshot(
    val playerNames: List<String>,
    val playerColors: List<String>,
    val playerPositions: Map<String, Int>,
    val playerScores: Map<String, Int>,
    val playerCount: Int,
    val currentPlayer: Int,
    val playWithTwoDice: Boolean,
    val diceColorMap: Map<Int, String>,
    val diceRollingStatus: Map<Int, Boolean>,
    val lastDice1: Int?,
    val lastDice2: Int?,
    val lastAvg: Int?
)

data class LiveStateSnapshot(
    val playerNames: List<String>,
    val playerColors: List<String>,
    val playerPositions: Map<String, Int>,
    val playerScores: Map<String, Int>,
    val playerCount: Int,
    val currentPlayer: Int,
    val playWithTwoDice: Boolean,
    val diceColorMap: Map<Int, String>,
    val lastDice1: Int?,
    val lastDice2: Int?,
    val lastAvg: Int?,
    val lastTileName: String?,
    val lastTileType: String?,
    val lastChanceCardNumber: Int?,
    val lastChanceCardText: String?,
    val rolling: Boolean = false,
    val eventType: String? = null,
    val eventMessage: String? = null
)
