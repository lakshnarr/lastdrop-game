package com.example.lastdrop

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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GameHistoryActivity : AppCompatActivity() {

    private lateinit var database: LastDropDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GameHistoryAdapter
    private lateinit var emptyState: View
    
    private var allGames = listOf<GameRecordWithProfile>()
    private var currentFilter = Filter.ALL

    enum class Filter {
        ALL, WINS, LOSSES, TODAY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_history)

        database = LastDropDatabase.getInstance(this)
        
        recyclerView = findViewById(R.id.gameList)
        emptyState = findViewById(R.id.emptyState)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = GameHistoryAdapter()
        recyclerView.adapter = adapter

        setupFilters()
        loadGames()
        
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
        val subtitle = when (filter) {
            Filter.ALL -> "${filtered.size} games • All players"
            Filter.WINS -> "${filtered.size} wins"
            Filter.LOSSES -> "${filtered.size} losses"
            Filter.TODAY -> "${filtered.size} games today"
        }
        findViewById<TextView>(R.id.historySubtitle).text = subtitle

        // Show/hide empty state
        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun loadGames() {
        lifecycleScope.launch(Dispatchers.IO) {
            val records = database.gameRecordDao().getAllGameRecords()
            val profileDao = database.playerProfileDao()
            
            val gamesWithProfiles = records.mapNotNull { record ->
                val profile = profileDao.getProfile(record.profileId)
                if (profile != null) {
                    GameRecordWithProfile(record, profile)
                } else {
                    null
                }
            }

            withContext(Dispatchers.Main) {
                allGames = gamesWithProfiles
                applyFilter(currentFilter, findViewById(R.id.filterAll))
            }
        }
    }

    // Adapter
    private inner class GameHistoryAdapter : RecyclerView.Adapter<GameHistoryAdapter.ViewHolder>() {
        
        private var games = listOf<GameRecordWithProfile>()

        fun updateGames(newGames: List<GameRecordWithProfile>) {
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
                record.opponentIds.removeSurrounding("[", "]").split(",").filter { it.isNotBlank() }.size
            } catch (e: Exception) { 0 }
            val playerCount = opponentCount + 1
            holder.gameTitle.text = "$playerCount-Player Game"

            // Time ago
            holder.gameTime.text = getTimeAgo(record.playedAt)

            // Duration
            holder.gameDuration.text = "Duration: ${record.gameTimeMinutes} min"

            // Score
            holder.finalScore.text = record.finalScore.toString()

            // Placement
            val placementEmoji = when (record.placement) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "🎲"
            }
            holder.placement.text = "$placementEmoji ${getOrdinal(record.placement)} Place"

            // Bonus/Drought hits
            holder.bonusHits.text = "💧 ${record.bonusTileHits} bonuses"
            holder.droughtHits.text = "☠️ ${record.droughtTileHits} droughts"

            // Player color dots
            holder.colorDots.removeAllViews()
            addColorDot(holder.colorDots, record.colorUsed, true) // Player's color (highlighted)
            
            // Opponent colors (parse from opponentIds and fetch profiles)
            // For now, just show placeholder dots
            val colors = listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00")
            for (i in 0 until opponentCount) {
                if (i < colors.size) {
                    addColorDot(holder.colorDots, colors[i], false)
                }
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

data class GameRecordWithProfile(
    val record: GameRecord,
    val profile: PlayerProfile
)
