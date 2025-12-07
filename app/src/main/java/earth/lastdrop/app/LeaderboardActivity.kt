package earth.lastdrop.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var database: LastDropDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LeaderboardAdapter
    private lateinit var emptyState: View
    
    private var allProfiles = listOf<PlayerProfile>()
    private var currentSort = SortType.WINS

    enum class SortType {
        WINS, WIN_RATE, BEST_SCORE, STREAK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        database = LastDropDatabase.getInstance(this)
        
        recyclerView = findViewById(R.id.leaderboardList)
        emptyState = findViewById(R.id.emptyState)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LeaderboardAdapter()
        recyclerView.adapter = adapter

        setupSortButtons()
        loadProfiles()
    }

    private fun setupSortButtons() {
        val sortWins = findViewById<Button>(R.id.sortWins)
        val sortWinRate = findViewById<Button>(R.id.sortWinRate)
        val sortBestScore = findViewById<Button>(R.id.sortBestScore)
        val sortStreak = findViewById<Button>(R.id.sortStreak)

        sortWins.setOnClickListener { applySorting(SortType.WINS, sortWins) }
        sortWinRate.setOnClickListener { applySorting(SortType.WIN_RATE, sortWinRate) }
        sortBestScore.setOnClickListener { applySorting(SortType.BEST_SCORE, sortBestScore) }
        sortStreak.setOnClickListener { applySorting(SortType.STREAK, sortStreak) }
    }

    private fun applySorting(sortType: SortType, selectedButton: Button) {
        currentSort = sortType
        
        // Update button styles
        val buttons = listOf(
            findViewById<Button>(R.id.sortWins),
            findViewById<Button>(R.id.sortWinRate),
            findViewById<Button>(R.id.sortBestScore),
            findViewById<Button>(R.id.sortStreak)
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

        // Sort profiles
        val sorted = when (sortType) {
            SortType.WINS -> allProfiles.sortedByDescending { it.wins }
            SortType.WIN_RATE -> allProfiles.sortedByDescending { 
                if (it.totalGames > 0) it.wins.toFloat() / it.totalGames else 0f
            }
            SortType.BEST_SCORE -> allProfiles.sortedByDescending { it.personalBestScore }
            SortType.STREAK -> allProfiles.sortedByDescending { it.currentWinStreak }
        }

        adapter.updateProfiles(sorted, sortType)
    }

    private fun loadProfiles() {
        findViewById<View>(R.id.loadingIndicator).visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val profiles = database.playerProfileDao().getProfileSummaries()
                    .mapNotNull { summary -> database.playerProfileDao().getProfile(summary.playerId) }
                    .filter { !it.isGuest } // Exclude guest profiles

                withContext(Dispatchers.Main) {
                    findViewById<View>(R.id.loadingIndicator).visibility = View.GONE
                    allProfiles = profiles
                    
                    if (profiles.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        
                        // Fade in animation for RecyclerView
                        val fadeIn = android.view.animation.AnimationUtils.loadAnimation(this@LeaderboardActivity, R.anim.fade_in)
                        recyclerView.startAnimation(fadeIn)
                        
                        applySorting(currentSort, findViewById(R.id.sortWins))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<View>(R.id.loadingIndicator).visibility = View.GONE
                    Toast.makeText(this@LeaderboardActivity, "Error loading leaderboard: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Adapter
    private inner class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {
        
        private var profiles = listOf<PlayerProfile>()
        private var sortType = SortType.WINS

        fun updateProfiles(newProfiles: List<PlayerProfile>, sort: SortType) {
            profiles = newProfiles
            sortType = sort
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rank: TextView = view.findViewById(R.id.rank)
            val playerColor: View = view.findViewById(R.id.playerColor)
            val playerName: TextView = view.findViewById(R.id.playerName)
            val wins: TextView = view.findViewById(R.id.wins)
            val games: TextView = view.findViewById(R.id.games)
            val statValue: TextView = view.findViewById(R.id.statValue)
            val statLabel: TextView = view.findViewById(R.id.statLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_leaderboard, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = profiles[position]
            val rankNum = position + 1

            // Rank with medal for top 3
            holder.rank.text = when (rankNum) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "$rankNum"
            }
            holder.rank.setTextColor(when (rankNum) {
                1 -> Color.parseColor("#FFD700") // Gold
                2 -> Color.parseColor("#C0C0C0") // Silver
                3 -> Color.parseColor("#CD7F32") // Bronze
                else -> Color.parseColor("#FFFFFF")
            })

            // Player color with defensive parsing (stored colors omit leading '#')
            val colorHex = if (profile.avatarColor.startsWith("#")) profile.avatarColor else "#${profile.avatarColor}"
            val parsedColor = runCatching { Color.parseColor(colorHex) }.getOrDefault(Color.parseColor("#9E9E9E"))
            holder.playerColor.setBackgroundColor(parsedColor)

            // Player name
            holder.playerName.text = profile.name

            // Wins and games
            holder.wins.text = "${profile.wins} wins"
            holder.games.text = "${profile.totalGames} games"

            // Stat value based on sort type
            when (sortType) {
                SortType.WINS -> {
                    holder.statValue.text = profile.wins.toString()
                    holder.statLabel.text = "Wins"
                }
                SortType.WIN_RATE -> {
                    val winRate = if (profile.totalGames > 0) {
                        ((profile.wins.toFloat() / profile.totalGames) * 100).toInt()
                    } else 0
                    holder.statValue.text = "$winRate%"
                    holder.statLabel.text = "Win Rate"
                }
                SortType.BEST_SCORE -> {
                    holder.statValue.text = profile.personalBestScore.toString()
                    holder.statLabel.text = "Best Score"
                }
                SortType.STREAK -> {
                    holder.statValue.text = profile.currentWinStreak.toString()
                    holder.statLabel.text = "Streak"
                    
                    // Fire emoji if streak > 3
                    if (profile.currentWinStreak >= 3) {
                        holder.statValue.text = "${profile.currentWinStreak} 🔥"
                    }
                }
            }
        }

        override fun getItemCount() = profiles.size
    }
}
