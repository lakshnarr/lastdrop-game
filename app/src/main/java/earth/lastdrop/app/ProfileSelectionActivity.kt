package earth.lastdrop.app

import earth.lastdrop.app.ui.intro.IntroAiActivity
import earth.lastdrop.app.voice.HybridVoiceService
import earth.lastdrop.app.voice.NoOpVoiceService
import earth.lastdrop.app.voice.VoiceService
import earth.lastdrop.app.voice.VoiceSettingsManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
    private var voiceService: VoiceService? = null
    private val cloudiePrefs by lazy { getSharedPreferences("cloudie_prefs", MODE_PRIVATE) }
    private val selectedProfiles = mutableSetOf<String>() // For multiplayer
    private var isCalledFromMainActivity = false // Track where we came from
    private var isCalledFromIntroAi = false // Track if called from IntroAi
    private var latestSavedGame: SavedGame? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if called from MainActivity
        isCalledFromMainActivity = intent.getBooleanExtra("FROM_MAIN_ACTIVITY", false)
        isCalledFromIntroAi = intent.getBooleanExtra("FROM_INTRO_AI", false)
        
        // Simple layout (you'll need to create XML)
        setContentView(createLayout())
        
        database = LastDropDatabase.getInstance(this)
        savedGameDao = database.savedGameDao()
        profileManager = ProfileManager(this)
        
        // Initialize voice service
        initVoiceService()
        
        setupRecyclerView()
        updateStartButtonState()
        
        // Auto-create Cloudie AI and Guestie profiles
        lifecycleScope.launch {
            profileManager.getOrCreateAIProfile()
            profileManager.getOrCreateGuestProfile()
            // Migrate old gray profiles to vibrant colors
            profileManager.updateProfileColors()
            val profiles = profileManager.getAllProfiles().first()
            adapter.submitProfiles(profiles)
            
            // Check if this is first time (no user profiles exist)
            val hasUserProfiles = profiles.any { !it.isAI && !it.isGuest }
            
            // Welcome voice after profiles load
            delay(600)
            speakCloudieIntro(hasUserProfiles)
        }

        refreshSavedGameButton()
    }

    override fun onResume() {
        super.onResume()
        // Re-fetch in case a save was created or consumed while this screen was backgrounded
        refreshSavedGameButton()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceService?.shutdown()
    }
    
    private fun initVoiceService() {
        val voiceSettingsManager = VoiceSettingsManager(this)
        val voiceSettings = voiceSettingsManager.getSettings()
        voiceService = runCatching {
            HybridVoiceService(
                context = this,
                settings = voiceSettings,
                onReady = { },
                onError = { }
            )
        }.getOrElse {
            NoOpVoiceService(this)
        }
    }
    
    private fun speakLine(text: String) {
        runCatching { voiceService?.speak(text) }
    }
    
    /**
     * Kid-friendly AI introduction with random fresh greetings
     */
    private fun speakCloudieIntro(hasUserProfiles: Boolean) {
        val greetings = if (!hasUserProfiles) {
            // First time users - full introduction
            listOf(
                "Hey there, welcome to Last Drop Earth! An awesome IoT game made by Lakshna! I'm Cloudie, your AI friend! Let's create your player profile and start saving the planet!",
                "Woohoo! Welcome to Last Drop Earth! This cool IoT game was created by Lakshna! I'm Cloudie, your fluffy AI buddy! Let's make your profile and have some fun!",
                "Hello hello! Welcome to Last Drop Earth, an amazing IoT adventure by Lakshna! I'm Cloudie, your cloud friend! Time to create your profile and join the fun!",
                "Yay, a new friend! Welcome to Last Drop Earth! Lakshna made this awesome IoT game just for you! I'm Cloudie, your AI pal! Let's set up your profile!",
                "Hi hi hi! Welcome to Last Drop Earth! It's an IoT game created by Lakshna! I'm Cloudie, the friendliest cloud around! Let's make your profile together!"
            )
        } else {
            // Returning users - fun random greetings
            listOf(
                "You're back! Cloudie missed you! Ready to save some water drops today?",
                "Hey hey! Cloudie here! Who's ready for an awesome game?",
                "Woohoo! My favorite humans are here! Let's play Last Drop!",
                "Yippee! Game time! Cloudie is SO excited to see you!",
                "Hello friends! Cloudie's been waiting! Pick your players and let's goooo!",
                "Bouncy bouncy! You came back! Cloudie is doing a happy dance!",
                "Ta-daaaa! It's game time! Who wants to be a water hero today?",
                "Sparkle sparkle! My friends are here! Ready to roll some dice?",
                "Wheee! Another adventure awaits! Tap your profiles to play!",
                "Cloudie says HI! Let's make this the best game ever!",
                "Oh oh oh! You're here! Cloudie almost floated away with excitement!",
                "Guess who's ready to play? Cloudie! And you too, right? RIGHT?",
                "Boing boing! Welcome back, water warriors! Let's save the planet!",
                "Fluffy greetings, friends! Time to pick your team and have fun!",
                "Pssst! Hey! It's Cloudie! Wanna play a super cool game?"
            )
        }
        
        speakLine(greetings.random())
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If called from IntroAi or MainActivity, just go back
        if (isCalledFromIntroAi || isCalledFromMainActivity) {
            super.onBackPressed()
        } else {
            // Exit app when back is pressed from profile selection (first screen from Splash)
            finishAffinity()
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
            
            // Title with settings icon
            addView(LinearLayout(this@ProfileSelectionActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 32
                }
                
                // Title
                addView(TextView(this@ProfileSelectionActivity).apply {
                    text = "Select Players"
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                })
                
                // Settings icon
                addView(Button(this@ProfileSelectionActivity).apply {
                    text = "⚙️"
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(16, 16, 16, 16)
                    setOnClickListener {
                        val dialog = VoiceSettingsDialog(this@ProfileSelectionActivity, lifecycleScope)
                        dialog.show()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
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
                isEnabled = true // keep clickable so we can show validation toasts
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
        
        // Use 3 columns for better space utilization (6 profiles fit perfectly in 2 rows)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
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
            // Voice for deselection
            val name = profile.nickname.ifBlank { profile.name }
            speakLine("$name removed.")
        } else {
            if (selectedProfiles.size < 4) { // Max 4 players
                selectedProfiles.add(profile.playerId)
                
                // Voice welcome for the selected player
                val name = profile.nickname.ifBlank { profile.name }
                val welcomeLines = when {
                    profile.isAI -> listOf("Cloudie is in!", "I'm playing too!", "Cloudie joins the game!")
                    profile.isGuest -> listOf("Guest player joining!", "Welcome guest!")
                    else -> listOf("Welcome $name!", "Hey $name!", "$name is in!", "Nice to see you $name!")
                }
                speakLine(welcomeLines.random())
                
                // Show AI greeting when player joins (not for AI or guest)
                if (!profile.isGuest && !profile.isAI) {
                    showPlayerGreeting(profile)
                }
            } else {
                speakLine("Maximum 4 players!")
                Toast.makeText(this, "Maximum 4 players", Toast.LENGTH_SHORT).show()
            }
        }
        adapter.updateSelection(selectedProfiles)
        updateStartButtonState()
    }
    
    private fun updateStartButtonState() {
        // Require at least 2 players (can include AI)
        val hasMinPlayers = selectedProfiles.size >= 2

        // Keep button clickable so we can show toast when under-minimum
        btnStartGame.isEnabled = true

        // Keep label stable; append count only when valid
        btnStartGame.text = if (hasMinPlayers) {
            "🎮 Start Game (${selectedProfiles.size} players)"
        } else {
            "🎮 Start Game"
        }
    }

    private fun refreshSavedGameButton() {
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = runCatching { savedGameDao.getLatest() }.getOrNull()
            withContext(Dispatchers.Main) {
                latestSavedGame = saved
                btnLoadSavedGame.isEnabled = true // allow click to show toast if none
                btnLoadSavedGame.text = "📂 Load Saved Game"
            }
        }
    }

    private fun openSavedGames() {
        // Use cached latestSavedGame when available for immediate feedback
        val cached = latestSavedGame
        if (cached == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val hasSaved = runCatching { savedGameDao.getLatest() }.getOrNull() != null
                withContext(Dispatchers.Main) {
                    if (!hasSaved) {
                        Toast.makeText(this@ProfileSelectionActivity, "No saved games present", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(this@ProfileSelectionActivity, SavedGamesActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        } else {
            val intent = Intent(this, SavedGamesActivity::class.java)
            startActivity(intent)
        }
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
                createProfile(result)
            }
        }
    }
    
    private fun createProfile(result: CreateProfileResult) {
        lifecycleScope.launch {
            val error = profileManager.validateProfileName(result.name)
            if (error != null) {
                Toast.makeText(this@ProfileSelectionActivity, "Name: $error", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val nicknameError = profileManager.validateProfileName(result.nickname)
            if (nicknameError != null) {
                Toast.makeText(this@ProfileSelectionActivity, "Nickname: $nicknameError", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val created = profileManager.createProfile(result.name, result.nickname, result.persona)
            created.onSuccess { profile ->
                cloudiePrefs.edit().putString("cloudie_persona", result.persona).apply()
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

        options += "🎙 Voice Persona"
        actions += { showPersonaDialog(profile) }

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

    private fun showPersonaDialog(profile: PlayerProfile) {
        lifecycleScope.launch {
            val currentPersona = profile.aiPersonality.ifBlank { cloudiePrefs.getString("cloudie_persona", "cloudie") ?: "cloudie" }
            val selected = ProfileDialogs.showPersonaSelectionDialog(this@ProfileSelectionActivity, currentPersona)
            if (selected != null) {
                profileManager.updatePersona(profile.playerId, selected)
                cloudiePrefs.edit().putString("cloudie_persona", selected).apply()
                loadProfiles()
                Toast.makeText(this@ProfileSelectionActivity, "Voice persona set to $selected", Toast.LENGTH_SHORT).show()
            }
        }
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
        if (selectedProfiles.size < 2) {
            showMinPlayerWarning()
            return
        }
        
        // Show color selection dialog before starting game
        showColorSelectionDialog()
    }

    private fun showMinPlayerWarning() {
        btnStartGame.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        Toast.makeText(this@ProfileSelectionActivity, "Select at least 2 players to start the game", Toast.LENGTH_LONG).show()
        // Quick shake to make the validation obvious
        btnStartGame.animate()
            .translationX(18f)
            .setDuration(70)
            .withEndAction {
                btnStartGame.animate()
                    .translationX(0f)
                    .setDuration(90)
                    .start()
            }
            .start()
    }
    
    private fun showColorSelectionDialog() {
        // Stop any ongoing speech when user initiates color selection
        voiceService?.stop()
        
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
        val playerName = profile.nickname.ifBlank { profile.name }
        
        // AI auto-picks last available color
        if (profile.isAI) {
            val autoColor = availableColors.firstOrNull() ?: ProfileManager.GAME_COLORS[0]
            selectedColors[profile.playerId] = autoColor
            availableColors.remove(autoColor)
            
            // Show AI picked message briefly, then continue
            val colorSpoken = getColorNameSpoken(autoColor)
            speakLine("Cloudie picks $colorSpoken!")
            Toast.makeText(this, "☁️ Cloudie chose ${getColorName(autoColor)}!", Toast.LENGTH_SHORT).show()
            
            // Continue to next player after short delay
            lifecycleScope.launch {
                delay(1200)
                showColorPickerForPlayer(profiles, currentIndex + 1, selectedColors, availableColors)
            }
            return
        }
        
        // Human or Guest player - show color picker with voice prompt
        val colorNames = availableColors.map { getColorName(it) }.toTypedArray()
        val playerLabel = when {
            profile.isGuest -> "👤 ${profile.name}"
            else -> profile.nickname
        }
        
        // Voice prompt for color selection
        speakLine("$playerName, pick your color!")
        
        AlertDialog.Builder(this)
            .setTitle("$playerLabel - Choose Your Color")
            .setItems(colorNames) { _, which ->
                val chosenColor = availableColors[which]
                selectedColors[profile.playerId] = chosenColor
                availableColors.remove(chosenColor)
                
                // Announce the color choice
                val colorSpoken = getColorNameSpoken(chosenColor)
                speakLine("$playerName picks $colorSpoken!")
                
                // Move to next player after brief delay for voice
                lifecycleScope.launch {
                    delay(800)
                    showColorPickerForPlayer(profiles, currentIndex + 1, selectedColors, availableColors)
                }
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
    
    private fun getColorNameSpoken(colorHex: String): String {
        return when (colorHex) {
            "FF0000" -> "red"
            "00FF00" -> "green"
            "0000FF" -> "blue"
            "FFFF00" -> "yellow"
            else -> "a color"
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
        
        // IMPORTANT: Stop and shutdown voice before navigating to prevent dual voices
        voiceService?.shutdown()
        voiceService = null
        
        // Launch IntroAiActivity with profiles and colors
        val intent = Intent(this, IntroAiActivity::class.java).apply {
            putStringArrayListExtra("selected_profiles", ArrayList(selectedProfiles))
            putStringArrayListExtra("assigned_colors", ArrayList(orderedColors))
        }
        
        startActivity(intent)
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
        // Calculate height based on screen size for equal spacing
        // Screen height minus toolbar (~56dp) minus buttons (~200dp) divided by 2 rows
        val displayMetrics = parent.context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val availableHeight = screenHeight - (56 + 200) * displayMetrics.density.toInt()
        val tileHeight = (availableHeight / 2.2).toInt() // 2.2 to leave some padding
        
        return FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tileHeight
            ).apply {
                setMargins(8, 8, 8, 8) // Smaller margins for 3 columns
            }
            setPadding(12, 12, 12, 12)
        }
    }
    
    private fun createAddButtonView(parent: ViewGroup): View {
        // Match the profile tile height
        val displayMetrics = parent.context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val availableHeight = screenHeight - (56 + 200) * displayMetrics.density.toInt()
        val tileHeight = (availableHeight / 2.2).toInt()
        
        return Button(parent.context).apply {
            text = "+ Add Profile"
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tileHeight
            ).apply {
                setMargins(8, 8, 8, 8)
            }
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
                        val nick = profile.nickname.takeIf { it.isNotBlank() && it != profile.name }
                        val baseName = when {
                            profile.isAI -> "☁️ ${profile.name}"
                            profile.isGuest -> "👤 ${profile.name}"
                            else -> profile.name
                        }
                        text = if (nick != null) "$baseName ($nick)" else baseName
                        textSize = 18f
                        setTextColor(Color.WHITE)
                    })
                    
                    addView(TextView(context).apply {
                        val personaLabel = profile.aiPersonality.takeIf { it.isNotBlank() }?.let { "Voice: $it" }
                        text = when {
                            profile.isAI -> personaLabel ?: "AI Player"
                            profile.isGuest -> personaLabel ?: "Guest Player"
                            personaLabel != null -> personaLabel
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
