package earth.lastdrop.app

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.ceil

class RivalryDetailActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rivalry_detail)

        tvTitle = findViewById(R.id.tvRivalryTitle)
        tvSummary = findViewById(R.id.tvRivalrySummary)
        tvStats = findViewById(R.id.tvRivalryStats)

        val playerId = intent.getStringExtra("playerId")
        val opponentId = intent.getStringExtra("opponentId")
        val opponentName = intent.getStringExtra("opponentName") ?: "Nemesis"

        if (playerId.isNullOrBlank() || opponentId.isNullOrBlank()) {
            Toast.makeText(this, "Missing rivalry info", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = "Rivalry with $opponentName"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LastDropDatabase.getInstance(this@RivalryDetailActivity)
            val rivalryDao = db.rivalryDao()
            val rivalryManager = RivalryManager(this@RivalryDetailActivity)
            val gameRecordDao = db.gameRecordDao()

            val summary = rivalryManager.getPlayerRivalries(playerId)
                .firstOrNull { it.opponentId == opponentId }
            val record = rivalryDao.getRivalryRecord(playerId, opponentId)

            val deltas = gameRecordDao.getGamesVsOpponent(playerId, opponentId).mapNotNull { game ->
                val opponents = parseJsonArray(game.opponentIds)
                val scores = parseJsonArray(game.opponentScores).mapNotNull { it.toIntOrNull() }
                val idx = opponents.indexOf(opponentId)
                if (idx >= 0) {
                    val oppScore = scores.getOrNull(idx)
                    if (oppScore != null) game.finalScore - oppScore else null
                } else null
            }

            val stats = computeMarginStats(deltas)

            withContext(Dispatchers.Main) {
                if (summary == null) {
                    tvSummary.text = "No rivalry data yet. Play more games together!"
                    tvStats.text = ""
                } else {
                    val wl = "W ${summary.wins} / L ${summary.losses}"
                    val total = "Games: ${summary.totalGames}"
                    val rate = "Win rate: ${summary.winRate}%"
                    val last = "Last played: ${formatAgo(summary.lastPlayed)}"

                    val marginsText = record?.let {
                        val biggestWin = if (playerId <= opponentId) it.player1LargestMargin else it.player2LargestMargin
                        val biggestLoss = if (playerId <= opponentId) it.player2LargestMargin else it.player1LargestMargin
                        val closest = if (it.closestGame == Int.MAX_VALUE) "n/a" else "±${it.closestGame}"
                        buildString {
                            append("Biggest win: +$biggestWin")
                            append("\nBiggest loss: -$biggestLoss")
                            append("\nClosest game: $closest")
                            stats?.let { s ->
                                append("\nMedian margin: ${s.median}")
                                append("\nP90 margin: ${s.p90}")
                                append("\nAvg margin: ${s.avg}")
                                append("\nBlowouts (>=10): ${s.blowouts}")
                            }
                        }
                    } ?: "Margins: n/a"

                    tvSummary.text = "$wl\n$total\n$rate"
                    tvStats.text = "$last\n$marginsText"
                }
            }
        }
    }

    private fun parseJsonArray(raw: String): List<String> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun computeMarginStats(margins: List<Int>): MarginStats? {
        if (margins.isEmpty()) return null
        val sorted = margins.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            val mid = sorted.size / 2
            ((sorted[mid - 1] + sorted[mid]) / 2.0).toInt()
        }
        val p90 = sorted[ceil(sorted.size * 0.9).toInt().coerceAtMost(sorted.lastIndex)]
        val avg = (sorted.sum().toDouble() / sorted.size).toInt()
        val blowouts = margins.count { abs(it) >= 10 }
        return MarginStats(median, p90, avg, blowouts)
    }

    private fun formatAgo(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        val diff = System.currentTimeMillis() - timestamp
        val mins = diff / 60000
        val hours = diff / 3600000
        val days = diff / 86400000
        return when {
            mins < 1 -> "Just now"
            mins < 60 -> "$mins min ago"
            hours < 24 -> "$hours hr ago"
            days < 7 -> "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }

}

private data class MarginStats(
    val median: Int,
    val p90: Int,
    val avg: Int,
    val blowouts: Int
)
