package earth.lastdrop.app

import android.app.AlertDialog
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile Selection Screen
 * Shows 6 profile slots + 1 guest option
 * Allows creating, editing, and deleting profiles
 */
class ProfileSelectionActivity : AppCompatActivity() {
    
    private lateinit var profileManager: ProfileManager
    private lateinit var database: LastDropDatabase
    private lateinit var savedGameDao: SavedGameDao
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfileAdapter
    private lateinit var btnStartGame: Button
    private lateinit var btnLoadSavedGame: Button
    private val selectedProfiles = mutableSetOf<String>() // For multiplayer
    private var isCalledFromMainActivity = false // Track where we came from
    private var latestSavedGame: SavedGame? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if called from MainActivity
        isCalledFromMainActivity = intent.getBooleanExtra("FROM_MAIN_ACTIVITY", false)
        
        // Simple layout (you'll need to create XML)
        setContentView(createLayout())
        
        database = LastDropDatabase.getInstance(this)
        savedGameDao = database.savedGameDao()
        profileManager = ProfileManager(this)
        
        setupRecyclerView()
        
        // Auto-create Cloudie AI and Guestie profiles
        lifecycleScope.launch {
            profileManager.getOrCreateAIProfile()
            profileManager.getOrCreateGuestProfile()
            // Migrate old gray profiles to vibrant colors
            profileManager.updateProfileColors()
            loadProfiles()
        }

        refreshSavedGameButton()
    }

    override fun onResume() {
        super.onResume()
        // Re-fetch in case a save was created or consumed while this screen was backgrounded
        refreshSavedGameButton()
    }
    
    override fun onBackPressed() {
        // If this is initial launch (not called from MainActivity), exit app instead of going to MainActivity
        if (!isCalledFromMainActivity) {
            // Exit the app completely
            finishAffinity()
        } else {
            // Called from MainActivity - just go back
            super.onBackPressed()
        }
    }
    
    private fun createLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#0b1020")) // Dark theme to match MainActivity
            
            // Back button - only show if called from MainActivity
            if (isCalledFromMainActivity) {
                addView(Button(this@ProfileSelectionActivity).apply {
                    text = "⬅️ Back to Main Menu"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#6200EE"))
                    setOnClickListener { finish() }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 16
                    }
                })
            }
            
            // Title
            addView(TextView(this@ProfileSelectionActivity).apply {
                text = "Select Players"
                textSize = 24f
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setTextColor(Color.WHITE)
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
            
            // Start game button
            btnStartGame = Button(this@ProfileSelectionActivity).apply {
                text = "🎮 Start Game"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 18f
                isEnabled = false
                setOnClickListener { startGame() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    bottomMargin = 12
                }
            }
            addView(btnStartGame)

            // Load saved game button
            btnLoadSavedGame = Button(this@ProfileSelectionActivity).apply {
                text = "📂 Load Saved Game"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2962FF"))
                textSize = 16f
                isEnabled = true
                visibility = View.VISIBLE
                setOnClickListener { openSavedGames() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                    bottomMargin = 100 // Extra padding for navigation bar
                }
            }
            addView(btnLoadSavedGame)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ProfileAdapter(
            onProfileClick = { profile -> toggleProfileSelection(profile) },
            onProfileLongClick = { profile -> showProfileOptions(profile) },
            onProfileMenu = { profile -> showProfileMenu(profile) },
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
                
                // Show AI greeting when player joins (not for AI or guest)
                if (!profile.isGuest && !profile.isAI) {
                    showPlayerGreeting(profile)
                }
            } else {
                Toast.makeText(this, "Maximum 4 players", Toast.LENGTH_SHORT).show()
            }
        }
        adapter.updateSelection(selectedProfiles)
        updateStartButtonState()
    }
    
    private fun updateStartButtonState() {
        // Require at least 2 players (can include AI)
        val hasMinPlayers = selectedProfiles.size >= 2
        btnStartGame.isEnabled = hasMinPlayers
        
        // Update button text with hint
        btnStartGame.text = if (hasMinPlayers) {
            "🎮 Start Game (${selectedProfiles.size} Players)"
        } else {
            "Select at least 2 players to start"
        }
    }

    private fun refreshSavedGameButton() {
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = runCatching { savedGameDao.getLatest() }.getOrNull()
            withContext(Dispatchers.Main) {
                latestSavedGame = saved
                btnLoadSavedGame.isEnabled = saved != null
                btnLoadSavedGame.text = "📂 Load Saved Game"
            }
        }
    }

    private fun openSavedGames() {
        val intent = Intent(this, SavedGamesActivity::class.java)
        startActivity(intent)
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
    
    private fun showProfileMenu(profile: PlayerProfile) {
        // Three-dot menu with edit options
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options += "✏️ Rename"
        actions += { showRenameProfileDialog(profile) }

        options += "🎯 Change Nickname"
        actions += { showChangeNicknameDialog(profile) }

        if (!profile.isAI && !profile.isGuest) {
            options += "🗑️ Delete Profile"
            actions += { confirmDeleteProfile(profile) }
        }

        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(options.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .show()
    }
    
    private fun showEditProfileDialog(profile: PlayerProfile) {
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
    
    private fun showProfileOptions(profile: PlayerProfile) {
        if (profile.isAI || profile.isGuest) {
            AlertDialog.Builder(this)
                .setTitle("${profile.name} (${profile.playerCode})")
                .setItems(arrayOf("Rename", "Change Nickname")) { _, which ->
                    when (which) {
                        0 -> showRenameProfileDialog(profile)
                        1 -> showChangeNicknameDialog(profile)
                    }
                }
                .show()
            return
        }
        
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
        val intent = Intent(this, ProfileStatsActivity::class.java)
        intent.putExtra("PLAYER_ID", profile.playerId)
        startActivity(intent)
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
            
            // Sort profiles: Regular players → Guest → AI
            val sortedProfiles = profiles.sortedWith(compareBy(
                { if (it.isAI) 2 else if (it.isGuest) 1 else 0 },
                { it.name }
            ))
            
            // Track selected colors
            val selectedColors = mutableMapOf<String, String>() // profileId → color
            val availableColors = ProfileManager.GAME_COLORS.toMutableList()
            
            // Start interactive color selection
            showColorPickerForPlayer(sortedProfiles, 0, selectedColors, availableColors)
        }
    }
    
    private fun showColorPickerForPlayer(
        profiles: List<PlayerProfile>,
        currentIndex: Int,
        selectedColors: MutableMap<String, String>,
        availableColors: MutableList<String>
    ) {
        if (currentIndex >= profiles.size) {
            // All players have colors, start game
            startGameWithColors(profiles, selectedColors)
            return
        }
        
        val profile = profiles[currentIndex]
        
        // AI auto-picks last available color
        if (profile.isAI) {
            val autoColor = availableColors.firstOrNull() ?: ProfileManager.GAME_COLORS[0]
            selectedColors[profile.playerId] = autoColor
            availableColors.remove(autoColor)
            
            // Show AI picked message briefly, then continue
            Toast.makeText(this, "☁️ Cloudie chose ${getColorName(autoColor)}!", Toast.LENGTH_SHORT).show()
            
            // Continue to next player after short delay
            lifecycleScope.launch {
                delay(800)
                showColorPickerForPlayer(profiles, currentIndex + 1, selectedColors, availableColors)
            }
            return
        }
        
        // Human or Guest player - show color picker
        val colorNames = availableColors.map { getColorName(it) }.toTypedArray()
        val playerLabel = when {
            profile.isGuest -> "👤 ${profile.name}"
            else -> profile.nickname
        }
        
        AlertDialog.Builder(this)
            .setTitle("$playerLabel - Choose Your Color")
            .setItems(colorNames) { _, which ->
                val chosenColor = availableColors[which]
                selectedColors[profile.playerId] = chosenColor
                availableColors.remove(chosenColor)
                
                // Move to next player
                showColorPickerForPlayer(profiles, currentIndex + 1, selectedColors, availableColors)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun getColorName(colorHex: String): String {
        return when (colorHex) {
            "FF0000" -> "🔴 Red"
            "00FF00" -> "🟢 Green"
            "0000FF" -> "🔵 Blue"
            "FFFF00" -> "🟡 Yellow"
            else -> "Color"
        }
    }
    
    private fun startGameWithColors(
        profiles: List<PlayerProfile>,
        selectedColors: Map<String, String>
    ) {
        // Build final color array in original selection order
        val orderedColors = selectedProfiles.map { profileId ->
            selectedColors[profileId] ?: ProfileManager.GAME_COLORS[0]
        }
        
        val resultIntent = Intent().apply {
            putStringArrayListExtra("selected_profiles", ArrayList(selectedProfiles))
            putStringArrayListExtra("assigned_colors", ArrayList(orderedColors))
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

// ==================== ADAPTER ====================

class ProfileAdapter(
    private val onProfileClick: (PlayerProfile) -> Unit,
    private val onProfileLongClick: (PlayerProfile) -> Unit,
    private val onProfileMenu: (PlayerProfile) -> Unit,
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
        // Show all profiles + add button (if not at limit)
        // We allow: Cloudie + Guestie + 4 custom profiles = 6 total profiles + 1 add button = 7 slots
        val regularProfileCount = profiles.count { !it.isAI && !it.isGuest }
        val canAddMore = regularProfileCount < ProfileManager.MAX_PROFILES
        return if (canAddMore) profiles.size + 1 else profiles.size
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
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            ).apply {
                setMargins(12, 12, 12, 12) // Add spacing around tiles
            }
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
                
                // Selection visual feedback
                if (isSelected) {
                    alpha = 1.0f // Full opacity
                    elevation = 16f // Lifted effect
                    // Set vibrant background color
                    setBackgroundColor(Color.parseColor("#${profile.avatarColor}"))
                    // Add green glow using outline
                    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                    clipToOutline = false
                } else {
                    alpha = 0.5f // Semi-transparent (dull)
                    elevation = 4f // Flat
                    // Set dull background color
                    setBackgroundColor(Color.parseColor("#${profile.avatarColor}"))
                    outlineProvider = null
                }
                
                // Add three-dot menu button in top-right corner (only for non-AI/Guest profiles)
                if (!profile.isAI && !profile.isGuest) {
                    addView(Button(context).apply {
                        text = "⋮"
                        textSize = 24f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            80,
                            80
                        ).apply {
                            gravity = android.view.Gravity.TOP or android.view.Gravity.END
                        }
                        setOnClickListener { 
                            onProfileMenu(profile)
                        }
                    })
                }
                
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    
                    addView(TextView(context).apply {
                        text = when {
                            profile.isAI -> "☁️ ${profile.name}"
                            profile.isGuest -> "👤 ${profile.name}"
                            else -> profile.name
                        }
                        textSize = 18f
                        setTextColor(Color.WHITE)
                    })
                    
                    addView(TextView(context).apply {
                        text = when {
                            profile.isAI -> "AI Player"
                            profile.isGuest -> "Guest Player"
                            else -> "${profile.wins}W ${profile.losses}L"
                        }
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
