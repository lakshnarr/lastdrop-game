package earth.lastdrop.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GameHistoryActivity : AppCompatActivity() {

    private lateinit var database: LastDropDatabase
    private lateinit var savedGameDao: SavedGameDao
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GameHistoryAdapter
    private lateinit var emptyState: View
    private lateinit var savedGameCard: View
    private lateinit var savedGameTitle: TextView
    private lateinit var savedGameSubtitle: TextView
    private lateinit var btnResumeSaved: Button
    private lateinit var btnDiscardSaved: Button
    private val selectedProfileId: String?
        get() = intent.getStringExtra("profileId")
    private val selectedProfileName: String?
        get() = intent.getStringExtra("profileName")
    
    private var allGames = listOf<GameRecordWithOpponents>()
    private var currentFilter = Filter.ALL
    private var latestSavedGame: SavedGame? = null

    enum class Filter {
        ALL, WINS, LOSSES, TODAY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_history)

        database = LastDropDatabase.getInstance(this)
        savedGameDao = database.savedGameDao()
        
        recyclerView = findViewById(R.id.gameList)
        emptyState = findViewById(R.id.emptyState)
        savedGameCard = findViewById(R.id.savedGameCard)
        savedGameTitle = findViewById(R.id.savedGameTitle)
        savedGameSubtitle = findViewById(R.id.savedGameSubtitle)
        btnResumeSaved = findViewById(R.id.btnResumeSavedGame)
        btnDiscardSaved = findViewById(R.id.btnDiscardSavedGame)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GameHistoryAdapter()
        recyclerView.adapter = adapter

        setupFilters()
        setupSavedGameButtons()
        loadGames()
        loadSavedGameCard()
        
        // Export CSV button
        findViewById<Button>(R.id.btnExportCSV).setOnClickListener {
            exportAllGames()
        }
    }

    private fun exportAllGames() {
        if (allGames.isEmpty()) {
            Toast.makeText(this, "No games to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val records = allGames.map { it.record }
            val playerName = allGames.firstOrNull()?.profile?.name ?: "AllPlayers"
            
            val fileUri = GameShareHelper.exportGameHistory(this@GameHistoryActivity, records, playerName)
            
            withContext(Dispatchers.Main) {
                if (fileUri != null) {
                    GameShareHelper.shareWithAttachment(
                        this@GameHistoryActivity,
                        "Last Drop game history exported (${records.size} games)",
                        fileUri
                    )
                } else {
                    Toast.makeText(this@GameHistoryActivity, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupFilters() {
        val filterAll = findViewById<Button>(R.id.filterAll)
        val filterWins = findViewById<Button>(R.id.filterWins)
        val filterLosses = findViewById<Button>(R.id.filterLosses)
        val filterToday = findViewById<Button>(R.id.filterToday)

        filterAll.setOnClickListener { applyFilter(Filter.ALL, filterAll) }
        filterWins.setOnClickListener { applyFilter(Filter.WINS, filterWins) }
        filterLosses.setOnClickListener { applyFilter(Filter.LOSSES, filterLosses) }
        filterToday.setOnClickListener { applyFilter(Filter.TODAY, filterToday) }
    }

    private fun setupSavedGameButtons() {
        btnResumeSaved.setOnClickListener {
            latestSavedGame?.let { saved ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("savedGameId", saved.savedGameId)
                startActivity(intent)
            } ?: Toast.makeText(this, "No saved game to resume", Toast.LENGTH_SHORT).show()
        }

        btnDiscardSaved.setOnClickListener {
            val toDelete = latestSavedGame ?: run {
                Toast.makeText(this, "No saved game to discard", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { savedGameDao.deleteById(toDelete.savedGameId) }
                withContext(Dispatchers.Main) {
                    latestSavedGame = null
                    bindSavedGameCard(null)
                }
            }
        }
    }

    private fun loadSavedGameCard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = runCatching { savedGameDao.getLatest() }.getOrNull()
            withContext(Dispatchers.Main) {
                latestSavedGame = saved
                bindSavedGameCard(saved)
            }
        }
    }

    private fun bindSavedGameCard(saved: SavedGame?) {
        if (saved == null) {
            savedGameCard.visibility = View.GONE
            return
        }

        val subtitle = buildString {
            append("Saved ")
            append(formatSavedGameTime(saved.savedAt))
            append(" • ")
            append(saved.playerCount)
            append(" players")
            if (saved.playWithTwoDice) append(" • two dice")
        }
        savedGameTitle.text = saved.label.ifBlank { "Resume saved game" }
        savedGameSubtitle.text = subtitle
        savedGameCard.visibility = View.VISIBLE
    }

    private fun formatSavedGameTime(timestamp: Long): String {
        if (timestamp <= 0) return "just now"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            days < 7 -> "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }

    private fun applyFilter(filter: Filter, selectedButton: Button) {
        currentFilter = filter
        
        // Update button styles
        val buttons = listOf(
            findViewById<Button>(R.id.filterAll),
            findViewById<Button>(R.id.filterWins),
            findViewById<Button>(R.id.filterLosses),
            findViewById<Button>(R.id.filterToday)
        )
        
        buttons.forEach { button ->
            if (button == selectedButton) {
                button.setBackgroundColor(Color.parseColor("#3a3a3a"))
                button.setTextColor(Color.WHITE)
            } else {
                button.setBackgroundColor(Color.parseColor("#2a2a2a"))
                button.setTextColor(Color.parseColor("#AAAAAA"))
            }
        }

        // Filter games
        val filtered = when (filter) {
            Filter.ALL -> allGames
            Filter.WINS -> allGames.filter { it.record.won }
            Filter.LOSSES -> allGames.filter { !it.record.won }
            Filter.TODAY -> {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
                allGames.filter { it.record.playedAt >= todayStart }
            }
        }

        adapter.updateGames(filtered)
        
        // Update subtitle
        val profileLabel = selectedProfileName ?: if (selectedProfileId != null) "Player history" else "All players"
        val subtitle = when (filter) {
            Filter.ALL -> "${filtered.size} games • $profileLabel"
            Filter.WINS -> "${filtered.size} wins • $profileLabel"
            Filter.LOSSES -> "${filtered.size} losses • $profileLabel"
            Filter.TODAY -> "${filtered.size} games today • $profileLabel"
        }
        findViewById<TextView>(R.id.historySubtitle).text = subtitle

        // Show/hide empty state
        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            
            // Fade in animation for RecyclerView
            val fadeIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.fade_in)
            recyclerView.startAnimation(fadeIn)
        }
    }

    private fun loadGames() {
        findViewById<View>(R.id.loadingIndicator).visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = selectedProfileId?.let { id ->
                    database.gameRecordDao().getAllGameRecordsForProfile(id)
                } ?: database.gameRecordDao().getAllGameRecords()

                val profileDao = database.playerProfileDao()
                val profileMap = profileDao.getAllProfilesList().associateBy { it.playerId }

                val gamesWithProfiles = records.mapNotNull { record ->
                    val profile = profileMap[record.profileId] ?: profileDao.getProfile(record.profileId)
                    if (profile != null) {
                        val opponentIds = try {
                            val arr = JSONArray(record.opponentIds)
                            (0 until arr.length()).mapNotNull { idx -> arr.optString(idx, null) }.filter { it.isNotBlank() }
                        } catch (e: Exception) {
                            record.opponentIds.removeSurrounding("[", "]").split(",").map { it.trim() }.filter { it.isNotBlank() }
                        }

                        val opponentProfiles = opponentIds.mapNotNull { oppId ->
                            profileMap[oppId] ?: profileDao.getProfile(oppId)
                        }

                        GameRecordWithOpponents(record, profile, opponentProfiles)
                    } else {
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    findViewById<View>(R.id.loadingIndicator).visibility = View.GONE
                    allGames = gamesWithProfiles
                    applyFilter(currentFilter, findViewById(R.id.filterAll))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<View>(R.id.loadingIndicator).visibility = View.GONE
                    Toast.makeText(this@GameHistoryActivity, "Error loading games: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Adapter
    private inner class GameHistoryAdapter : RecyclerView.Adapter<GameHistoryAdapter.ViewHolder>() {
        
        private var games = listOf<GameRecordWithOpponents>()

        fun updateGames(newGames: List<GameRecordWithOpponents>) {
            games = newGames
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val resultBadge: TextView = view.findViewById(R.id.resultBadge)
            val badgeCard: View = view.findViewById(R.id.resultBadge)
            val gameTitle: TextView = view.findViewById(R.id.gameTitle)
            val gameTime: TextView = view.findViewById(R.id.gameTime)
            val gameDuration: TextView = view.findViewById(R.id.gameDuration)
            val finalScore: TextView = view.findViewById(R.id.finalScore)
            val placement: TextView = view.findViewById(R.id.placement)
            val bonusHits: TextView = view.findViewById(R.id.bonusHits)
            val droughtHits: TextView = view.findViewById(R.id.droughtHits)
            val colorDots: LinearLayout = view.findViewById(R.id.colorDots)
            val achievementsUnlocked: TextView = view.findViewById(R.id.achievementsUnlocked)
            val btnShare: Button = view.findViewById(R.id.btnShare)
            val btnNemesis: TextView = view.findViewById(R.id.btnNemesisDetails)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_game_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val game = games[position]
            val record = game.record
            val profile = game.profile
            val opponentProfiles = game.opponents

            // Result badge
            if (record.won) {
                holder.resultBadge.text = "WIN"
                holder.badgeCard.setBackgroundColor(Color.parseColor("#4CAF50"))
            } else {
                holder.resultBadge.text = "LOSS"
                holder.badgeCard.setBackgroundColor(Color.parseColor("#F44336"))
            }

            // Game title (player count from opponents + self)
            val opponentCount = try {
                JSONArray(record.opponentIds).length()
            } catch (e: Exception) {
                try {
                    record.opponentIds.removeSurrounding("[", "]").split(",").filter { it.isNotBlank() }.size
                } catch (_: Exception) { 0 }
            }
            val playerCount = if (record.totalPlayers > 0) record.totalPlayers else opponentCount + 1
            val displayName = (profile.nickname.ifBlank { profile.name })
            holder.gameTitle.text = "$displayName • ${playerCount}-Player Game"

            // Time ago
            holder.gameTime.text = getTimeAgo(record.playedAt)

            // Duration
            val winsSummary = if (record.totalGamesAfterGame > 0) {
                " • W${record.totalWinsAfterGame}/${record.totalGamesAfterGame}"
            } else {
                ""
            }
            holder.gameDuration.text = "Duration: ${record.gameTimeMinutes} min$winsSummary"

            // Score
            holder.finalScore.text = record.finalScore.toString()

            // Placement
            val rank = if (record.rank > 0) record.rank else record.placement
            val placementEmoji = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "🎲"
            }
            val streakText = if (record.winStreakAfterGame > 1) {
                " • 🔥 ${record.winStreakAfterGame} win streak"
            } else {
                ""
            }
            holder.placement.text = "$placementEmoji ${getOrdinal(rank)} Place$streakText"

            // Nemesis hint for AI/context
            if (!record.nemesisName.isNullOrBlank()) {
                holder.achievementsUnlocked.visibility = View.VISIBLE
                holder.achievementsUnlocked.text = "👾 Nemesis: ${record.nemesisName}"
            } else {
                holder.achievementsUnlocked.visibility = View.GONE
            }

            // Bonus/Drought hits
            holder.bonusHits.text = "💧 ${record.bonusTileHits} bonuses"
            holder.droughtHits.text = "☠️ ${record.droughtTileHits} droughts"

            // Player color dots
            holder.colorDots.removeAllViews()
            addColorDot(holder.colorDots, record.colorUsed, true) // Player's color (highlighted)

            // Opponent colors from stored profiles when available
            if (opponentProfiles.isNotEmpty()) {
                opponentProfiles.forEach { opp ->
                    addColorDot(holder.colorDots, opp.avatarColor, false)
                }
            } else {
                // Fallback: maintain spacing
                repeat(opponentCount) { addColorDot(holder.colorDots, "666666", false) }
            }

            // Achievements (would need to check if any were unlocked in this game)
            holder.achievementsUnlocked.visibility = View.GONE

            // Share button
            holder.btnShare.setOnClickListener {
                GameShareHelper.shareGameResult(
                    context = holder.itemView.context,
                    playerName = profile.name,
                    placement = record.placement,
                    score = record.finalScore,
                    totalPlayers = playerCount
                )
            }

            // Nemesis details link
            if (!record.nemesisPlayerId.isNullOrBlank() && !record.nemesisName.isNullOrBlank()) {
                holder.btnNemesis.visibility = View.VISIBLE
                holder.btnNemesis.setOnClickListener {
                    val intent = Intent(this@GameHistoryActivity, RivalryDetailActivity::class.java)
                    intent.putExtra("playerId", record.profileId)
                    intent.putExtra("opponentId", record.nemesisPlayerId)
                    intent.putExtra("opponentName", record.nemesisName)
                    startActivity(intent)
                }
            } else {
                holder.btnNemesis.visibility = View.GONE
                holder.btnNemesis.setOnClickListener(null)
            }
        }

        override fun getItemCount() = games.size

        private fun addColorDot(container: LinearLayout, colorHex: String, isPlayer: Boolean) {
            val dot = View(container.context)
            val size = if (isPlayer) 28 else 20
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 6
            }
            dot.layoutParams = params
            
            val drawable = container.context.getDrawable(R.drawable.circle_background)
            dot.background = drawable
            dot.setBackgroundColor(Color.parseColor("#$colorHex"))
            
            if (isPlayer) {
                dot.elevation = 4f
            }
            
            container.addView(dot)
        }

        private fun getOrdinal(num: Int): String {
            return when (num) {
                1 -> "1st"
                2 -> "2nd"
                3 -> "3rd"
                else -> "${num}th"
            }
        }

        private fun getTimeAgo(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "$mins ${if (mins == 1L) "minute" else "minutes"} ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "$hours ${if (hours == 1L) "hour" else "hours"} ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "$days ${if (days == 1L) "day" else "days"} ago"
                }
                else -> {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    dateFormat.format(Date(timestamp))
                }
            }
        }
    }
}

data class GameRecordWithOpponents(
    val record: GameRecord,
    val profile: PlayerProfile,
    val opponents: List<PlayerProfile>
)
