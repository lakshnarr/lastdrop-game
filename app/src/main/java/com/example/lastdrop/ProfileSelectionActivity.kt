package com.example.lastdrop

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Profile Selection Screen
 * Shows 6 profile slots + 1 guest option
 * Allows creating, editing, and deleting profiles
 */
class ProfileSelectionActivity : AppCompatActivity() {
    
    private lateinit var profileManager: ProfileManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfileAdapter
    private val selectedProfiles = mutableSetOf<String>() // For multiplayer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple layout (you'll need to create XML)
        setContentView(createLayout())
        
        profileManager = ProfileManager(this)
        
        setupRecyclerView()
        loadProfiles()
    }
    
    private fun createLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            
            // Title
            addView(TextView(this@ProfileSelectionActivity).apply {
                text = "Select Players"
                textSize = 24f
                setPadding(0, 0, 0, 32)
            })
            
            // RecyclerView for profiles
            recyclerView = RecyclerView(this@ProfileSelectionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            addView(recyclerView)
            
            // Guest button
            addView(Button(this@ProfileSelectionActivity).apply {
                text = "Play as Guest"
                setOnClickListener { playAsGuest() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                }
            })
            
            // Start game button
            addView(Button(this@ProfileSelectionActivity).apply {
                text = "Start Game"
                isEnabled = false
                setOnClickListener { startGame() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
            })
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ProfileAdapter(
            onProfileClick = { profile -> toggleProfileSelection(profile) },
            onProfileLongClick = { profile -> showProfileOptions(profile) },
            onCreateClick = { showCreateProfileDialog() }
        )
        
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }
    
    private fun loadProfiles() {
        lifecycleScope.launch {
            val profiles = profileManager.getAllProfiles().first()
            adapter.submitProfiles(profiles)
        }
    }
    
    private fun showPlayerGreeting(profile: PlayerProfile) {
        lifecycleScope.launch {
            try {
                val history = profileManager.getGameHistoryForGreeting(profile.playerId)
                val timeContext = TimeContext.from(history.lastPlayedTimestamp)
                val greeting = AIGreetings.getPersonalizedGreeting(history, timeContext)
                
                // Show greeting with nickname
                AlertDialog.Builder(this@ProfileSelectionActivity)
                    .setTitle("Welcome, ${profile.nickname}!")
                    .setMessage(greeting)
                    .setPositiveButton("Ready!", null)
                    .show()
            } catch (e: Exception) {
                // First time player or error - show simple welcome
                Toast.makeText(
                    this@ProfileSelectionActivity,
                    "Welcome, ${profile.nickname}! Ready to play?",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun toggleProfileSelection(profile: PlayerProfile) {
        if (selectedProfiles.contains(profile.playerId)) {
            selectedProfiles.remove(profile.playerId)
        } else {
            if (selectedProfiles.size < 4) { // Max 4 players
                selectedProfiles.add(profile.playerId)
                
                // Show AI greeting when player joins
                if (!profile.isGuest) {
                    showPlayerGreeting(profile)
                }
            } else {
                Toast.makeText(this, "Maximum 4 players", Toast.LENGTH_SHORT).show()
            }
        }
        adapter.updateSelection(selectedProfiles)
    }
    
    private fun showCreateProfileDialog() {
        lifecycleScope.launch {
            val canAdd = profileManager.canAddProfile()
            
            if (!canAdd) {
                AlertDialog.Builder(this@ProfileSelectionActivity)
                    .setTitle("Profile Limit Reached")
                    .setMessage("Maximum ${ProfileManager.MAX_PROFILES} profiles allowed. Delete an old profile to create a new one.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            
            val result = ProfileDialogs.showCreateProfileDialog(this@ProfileSelectionActivity)
            if (result != null) {
                val (name, nickname) = result
                createProfile(name, nickname)
            }
        }
    }
    
    private fun createProfile(name: String, nickname: String) {
        lifecycleScope.launch {
            val error = profileManager.validateProfileName(name)
            if (error != null) {
                Toast.makeText(this@ProfileSelectionActivity, "Name: $error", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val nicknameError = profileManager.validateProfileName(nickname)
            if (nicknameError != null) {
                Toast.makeText(this@ProfileSelectionActivity, "Nickname: $nicknameError", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val result = profileManager.createProfile(name, nickname)
            result.onSuccess { profile ->
                Toast.makeText(this@ProfileSelectionActivity, 
                    "Profile created! Your player code: ${profile.playerCode}", 
                    Toast.LENGTH_LONG).show()
                loadProfiles()
            }.onFailure {
                Toast.makeText(this@ProfileSelectionActivity, it.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showProfileOptions(profile: PlayerProfile) {
        AlertDialog.Builder(this)
            .setTitle("${profile.name} (${profile.playerCode})")
            .setItems(arrayOf("View Stats", "Rename Profile", "Change Nickname", "Delete")) { _, which ->
                when (which) {
                    0 -> showProfileStats(profile)
                    1 -> showRenameProfileDialog(profile)
                    2 -> showChangeNicknameDialog(profile)
                    3 -> confirmDeleteProfile(profile)
                }
            }
            .show()
    }
    
    private fun showProfileStats(profile: PlayerProfile) {
        ProfileDialogs.showProfileDetailsDialog(this, profile)
    }
    
    private fun showRenameProfileDialog(profile: PlayerProfile) {
        lifecycleScope.launch {
            val newName = ProfileDialogs.showRenameProfileDialog(this@ProfileSelectionActivity, profile.name)
            if (newName != null && newName != profile.name) {
                val result = profileManager.updateProfileName(profile.playerId, newName)
                result.onSuccess {
                    Toast.makeText(this@ProfileSelectionActivity, "Profile renamed to $newName", Toast.LENGTH_SHORT).show()
                    loadProfiles()
                }.onFailure {
                    Toast.makeText(this@ProfileSelectionActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showChangeNicknameDialog(profile: PlayerProfile) {
        lifecycleScope.launch {
            val newNickname = ProfileDialogs.showUpdateNicknameDialog(this@ProfileSelectionActivity, profile.nickname)
            if (newNickname != null && newNickname != profile.nickname) {
                val result = profileManager.updateNickname(profile.playerId, newNickname)
                result.onSuccess {
                    Toast.makeText(this@ProfileSelectionActivity, "AI will now call you '$newNickname'", Toast.LENGTH_SHORT).show()
                    loadProfiles()
                }.onFailure {
                    Toast.makeText(this@ProfileSelectionActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun confirmDeleteProfile(profile: PlayerProfile) {
        AlertDialog.Builder(this)
            .setTitle("Delete Profile?")
            .setMessage("Are you sure you want to delete ${profile.name}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    profileManager.deleteProfile(profile.playerId)
                    Toast.makeText(this@ProfileSelectionActivity, "Profile deleted", Toast.LENGTH_SHORT).show()
                    loadProfiles()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun playAsGuest() {
        lifecycleScope.launch {
            val guest = profileManager.createGuestProfile()
            selectedProfiles.clear()
            selectedProfiles.add(guest.playerId)
            startGame()
        }
    }
    
    private fun startGame() {
        if (selectedProfiles.isEmpty()) {
            Toast.makeText(this, "Select at least one player", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show color selection dialog before starting game
        showColorSelectionDialog()
    }
    
    private fun showColorSelectionDialog() {
        lifecycleScope.launch {
            val profiles = selectedProfiles.mapNotNull { id ->
                profileManager.getProfile(id)
            }
            
            if (profiles.isEmpty()) return@launch
            
            // Generate greetings for all players
            val greetings = profiles.mapNotNull { profile ->
                if (profile.isGuest) {
                    "${profile.name}: Ready to play!"
                } else {
                    try {
                        val history = profileManager.getGameHistoryForGreeting(profile.playerId)
                        val timeContext = TimeContext.from(history.lastPlayedTimestamp)
                        val greeting = AIGreetings.getPersonalizedGreeting(history, timeContext)
                        "${profile.nickname}: $greeting"
                    } catch (e: Exception) {
                        "${profile.nickname}: First game - let's make it count!"
                    }
                }
            }
            
            // Create color selection UI with greetings
            val colorAssignments = profiles.mapIndexed { index, profile ->
                "${profile.name} → ${ProfileManager.COLOR_NAMES[index]}"
            }.joinToString("\n")
            
            val message = buildString {
                append("🎮 AI GAME MASTER 🎮\n\n")
                append(greetings.joinToString("\n\n"))
                append("\n\n━━━━━━━━━━━━━━\n")
                append("Color Assignments:\n")
                append(colorAssignments)
            }
            
            AlertDialog.Builder(this@ProfileSelectionActivity)
                .setTitle("Game Starting")
                .setMessage(message)
                .setPositiveButton("Let's Play!") { _, _ ->
                    // Pass profiles with assigned colors to MainActivity
                    val colorMap = profileManager.assignGameColors(profiles.size)
                    // TODO: Start MainActivity with profiles and colors
                    Toast.makeText(
                        this@ProfileSelectionActivity, 
                        "Starting game with ${profiles.size} players", 
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}

// ==================== ADAPTER ====================

class ProfileAdapter(
    private val onProfileClick: (PlayerProfile) -> Unit,
    private val onProfileLongClick: (PlayerProfile) -> Unit,
    private val onCreateClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val profiles = mutableListOf<PlayerProfile>()
    private val selectedIds = mutableSetOf<String>()
    
    companion object {
        const val TYPE_PROFILE = 0
        const val TYPE_ADD = 1
    }
    
    fun submitProfiles(newProfiles: List<PlayerProfile>) {
        profiles.clear()
        profiles.addAll(newProfiles)
        notifyDataSetChanged()
    }
    
    fun updateSelection(selected: Set<String>) {
        selectedIds.clear()
        selectedIds.addAll(selected)
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position < profiles.size) TYPE_PROFILE else TYPE_ADD
    }
    
    override fun getItemCount(): Int {
        return minOf(profiles.size + 1, ProfileManager.MAX_PROFILES + 1)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_PROFILE) {
            ProfileViewHolder(createProfileItemView(parent))
        } else {
            AddProfileViewHolder(createAddButtonView(parent))
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ProfileViewHolder -> {
                val profile = profiles[position]
                holder.bind(profile, selectedIds.contains(profile.playerId))
            }
            is AddProfileViewHolder -> holder.bind()
        }
    }
    
    private fun createProfileItemView(parent: ViewGroup): View {
        return FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
            setPadding(16, 16, 16, 16)
        }
    }
    
    private fun createAddButtonView(parent: ViewGroup): View {
        return Button(parent.context).apply {
            text = "+ Add Profile"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
        }
    }
    
    inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(profile: PlayerProfile, isSelected: Boolean) {
            (itemView as FrameLayout).apply {
                removeAllViews()
                setBackgroundColor(Color.parseColor("#${profile.avatarColor}"))
                alpha = if (isSelected) 1.0f else 0.6f
                
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    
                    addView(TextView(context).apply {
                        text = profile.name
                        textSize = 18f
                        setTextColor(Color.WHITE)
                    })
                    
                    addView(TextView(context).apply {
                        text = "${profile.wins}W ${profile.losses}L"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setPadding(0, 8, 0, 0)
                    })
                })
                
                setOnClickListener { onProfileClick(profile) }
                setOnLongClickListener { 
                    onProfileLongClick(profile)
                    true
                }
            }
        }
    }
    
    inner class AddProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            itemView.setOnClickListener { onCreateClick() }
        }
    }
}
