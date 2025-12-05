package com.example.lastdrop

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileStatsActivity : AppCompatActivity() {

    private lateinit var database: LastDropDatabase
    private lateinit var achievementEngine: AchievementEngine
    private lateinit var rivalryManager: RivalryManager
    private var playerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_stats)

        database = LastDropDatabase.getInstance(this)
        achievementEngine = AchievementEngine(this)
        rivalryManager = RivalryManager(this)

        playerId = intent.getStringExtra("PLAYER_ID")
        if (playerId == null) {
            finish()
            return
        }

        loadProfileData()
    }

    private fun loadProfileData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = database.playerProfileDao().getProfile(playerId!!)
            if (profile == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            val achievementsFlow = database.achievementDao().getPlayerAchievements(playerId!!)
            val achievements = mutableListOf<Achievement>()
            achievementsFlow.collect { achievements.addAll(it) }
            
            val rivalries = rivalryManager.getPlayerRivalries(playerId!!)

            withContext(Dispatchers.Main) {
                displayProfile(profile)
                displayAchievements(achievements)
                displayRivalries(rivalries)
            }
        }
    }

    private fun displayProfile(profile: PlayerProfile) {
        // Avatar color
        findViewById<View>(R.id.avatarColor).setBackgroundColor(Color.parseColor(profile.avatarColor))

        // Name and details
        findViewById<TextView>(R.id.profileName).text = profile.name
        findViewById<TextView>(R.id.profileNickname).text = "@${profile.nickname}"
        findViewById<TextView>(R.id.profileCode).text = "Code: ${profile.playerCode}"

        // Statistics
        findViewById<TextView>(R.id.totalGames).text = profile.totalGames.toString()
        findViewById<TextView>(R.id.totalWins).text = profile.wins.toString()
        findViewById<TextView>(R.id.totalLosses).text = profile.losses.toString()
        
        val winRate = if (profile.totalGames > 0) {
            ((profile.wins.toFloat() / profile.totalGames) * 100).toInt()
        } else {
            0
        }
        findViewById<TextView>(R.id.winRate).text = "$winRate%"
        
        findViewById<TextView>(R.id.bestScore).text = profile.personalBestScore.toString()
        findViewById<TextView>(R.id.currentStreak).text = profile.currentWinStreak.toString()
    }

    private fun displayAchievements(achievements: List<Achievement>) {
        val allAchievements = AchievementDefinitions.ALL_ACHIEVEMENTS
        val unlockedIds = achievements.map { it.achievementId }.toSet()

        val achievementProgress = findViewById<TextView>(R.id.achievementProgress)
        achievementProgress.text = "${achievements.size} / ${allAchievements.size} Unlocked"

        val recyclerView = findViewById<RecyclerView>(R.id.achievementList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = AchievementAdapter(allAchievements, unlockedIds)
    }

    private fun displayRivalries(rivalries: List<RivalrySummary>) {
        val rivalriesEmpty = findViewById<TextView>(R.id.rivalriesEmpty)
        val rivalriesCard = findViewById<View>(R.id.rivalriesCard)

        if (rivalries.isEmpty()) {
            rivalriesEmpty.visibility = View.VISIBLE
            rivalriesCard.visibility = View.GONE
        } else {
            rivalriesEmpty.visibility = View.GONE
            rivalriesCard.visibility = View.VISIBLE

            val recyclerView = findViewById<RecyclerView>(R.id.rivalryList)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = RivalryAdapter(rivalries)
        }
    }

    // Achievement Adapter
    private inner class AchievementAdapter(
        private val achievements: List<AchievementDefinition>,
        private val unlocked: Set<String>
    ) : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val emoji: TextView = view.findViewById(R.id.achievementEmoji)
            val name: TextView = view.findViewById(R.id.achievementName)
            val description: TextView = view.findViewById(R.id.achievementDescription)
            val rarity: TextView = view.findViewById(R.id.achievementRarity)
            val unlockedIcon: TextView = view.findViewById(R.id.achievementUnlocked)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_achievement, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val achievement = achievements[position]
            val isUnlocked = unlocked.contains(achievement.id)

            holder.emoji.text = achievement.icon
            holder.name.text = achievement.name
            holder.description.text = achievement.description
            holder.rarity.text = achievement.rarity.name

            if (isUnlocked) {
                holder.unlockedIcon.visibility = View.VISIBLE
                holder.itemView.alpha = 1f
            } else {
                holder.unlockedIcon.visibility = View.GONE
                holder.itemView.alpha = 0.5f
            }

            // Color by rarity
            val rarityColor = when (achievement.rarity) {
                AchievementRarity.COMMON -> "#888888"
                AchievementRarity.UNCOMMON -> "#4CAF50"
                AchievementRarity.RARE -> "#2196F3"
                AchievementRarity.EPIC -> "#9C27B0"
                AchievementRarity.LEGENDARY -> "#FFD700"
                else -> "#888888"
            }
            holder.rarity.setTextColor(Color.parseColor(rarityColor))
        }

        override fun getItemCount() = achievements.size
    }

    // Rivalry Adapter
    private inner class RivalryAdapter(
        private val rivalries: List<RivalrySummary>
    ) : RecyclerView.Adapter<RivalryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val opponentColor: View = view.findViewById(R.id.opponentColor)
            val opponentName: TextView = view.findViewById(R.id.opponentName)
            val nemesisBadge: TextView = view.findViewById(R.id.nemesisBadge)
            val rivalryRecord: TextView = view.findViewById(R.id.rivalryRecord)
            val lastPlayed: TextView = view.findViewById(R.id.lastPlayed)
            val winRate: TextView = view.findViewById(R.id.winRate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rivalry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rivalry = rivalries[position]

            holder.opponentColor.setBackgroundColor(Color.parseColor(rivalry.opponentColor))
            holder.opponentName.text = rivalry.opponentName
            
            if (rivalry.isNemesis) {
                holder.nemesisBadge.visibility = View.VISIBLE
            } else {
                holder.nemesisBadge.visibility = View.GONE
            }

            holder.rivalryRecord.text = "${rivalry.wins}W - ${rivalry.losses}L (${rivalry.totalGames} games)"
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val lastPlayedText = if (rivalry.lastPlayed > 0) {
                "Last played: ${dateFormat.format(Date(rivalry.lastPlayed))}"
            } else {
                "Last played: Never"
            }
            holder.lastPlayed.text = lastPlayedText

            holder.winRate.text = "${rivalry.winRate}%"
            
            // Color win rate by performance
            val winRateColor = when {
                rivalry.winRate >= 60 -> "#4CAF50" // Green
                rivalry.winRate >= 40 -> "#FFC107" // Yellow
                else -> "#F44336" // Red
            }
            holder.winRate.setTextColor(Color.parseColor(winRateColor))
        }

        override fun getItemCount() = rivalries.size
    }
}
