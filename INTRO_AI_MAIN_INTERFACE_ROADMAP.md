# IntroAi as Main Game Interface - Implementation Roadmap

## 🎯 Vision Overview

Transform **IntroAiActivity** from a splash screen into the **primary game interface** with:
- **Lottie-animated characters** (Cloudie + 2-4 player drops) with emotion states
- **Icon-based minimal UI** for all game controls
- **Integrated scorecards** within character animations
- **Bottom-positioned** Cloudie dialogue and event text
- **Voice-synced animations** (mouth movements, bounces, emotes)

This creates a **modern, character-driven gaming experience** that feels like a living board game.

---

## 🏗️ Current Architecture Analysis

### Current Flow
```
SplashActivity (launcher)
    ↓
ProfileSelectionActivity (player selection)
    ↓
IntroAiActivity (AI intro with Start/Skip buttons)
    ↓
MainActivity (game board interface with buttons)
```

### Current IntroAiActivity Features
- ✅ Cloudie static image
- ✅ Player drop avatars in a row
- ✅ Dialogue text bubble
- ✅ Voice synthesis (ElevenLabs + TTS)
- ✅ Start Game / Skip buttons
- ❌ No game controls
- ❌ Static images (no Lottie animations)
- ❌ No scorecard integration
- ❌ No emote system

### Current MainActivity Features
- Game engine integration (roll processing, turn logic)
- ESP32 BLE communication
- GoDice BLE integration
- Virtual dice system
- Buttons: History, Ranks, Players, Settings, Undo, Reset, End Game
- Scorecard display
- Chance card display
- Live server API push

---

## 🎨 New UI Design Specification

### Layout Structure (Top to Bottom)

```
┌─────────────────────────────────────────────────┐
│  [🔌] [🎲] [👥] [📊] [🏆]    Toolbar Icons      │ Top
├─────────────────────────────────────────────────┤
│                                                  │
│        🌥️  Cloudie (Lottie Animation)          │
│             [Scorecard Badge: 42]               │ Center
│                                                  │
│   💧 P1   💧 P2   💧 P3   💧 P4                 │
│   [15]    [23]    [8]     [42]  Scorecards      │
│                                                  │
├─────────────────────────────────────────────────┤
│  Cloudie: "Little Star just rolled a 3!"        │ Bottom
│  Last: Player 2 moved to tile 7 (+5 points)     │
└─────────────────────────────────────────────────┘
```

### Icon Toolbar (Top Right)

| Icon | Function | Action |
|------|----------|--------|
| 🔌 | Connect | Opens dropdown: [🎲 Dice] [📟 Board] [🌐 Server] |
| 🎲 | Dice Mode | Toggle: Bluetooth ⇄ Virtual |
| 👥 | Players | Opens ProfileSelectionActivity |
| 📊 | History | Opens GameHistoryActivity |
| 🏆 | Ranks | Opens LeaderboardActivity |
| ↩️ | Undo | Undo last roll (5s confirmation window) |
| 🔄 | Refresh | Refresh game state |
| 🔴 | Reset | Reset game |
| ⏹️ | End Game | End current game |
| 🐛 | Debug | Navigate to MainActivity (hidden/debug mode) |

### Character Animation States

**Cloudie (Host)**
- `idle` - Floating/breathing loop
- `talking` - Mouth open/close sync with TTS
- `happy` - Eyes sparkle, bounce up
- `sad` - Cloud droops, rain drops
- `surprised` - Eyes widen, quick jump
- `thinking` - Eyes look up, tilt animation
- `cheer` - Confetti burst, big bounce
- `oops` - Sweat drop, wobble

**Player Drops**
- `idle` - Gentle bob animation
- `active` - Pulse/glow (current player turn)
- `happy` - Jump + sparkle (gained points)
- `sad` - Shrink slightly (lost points)
- `eliminated` - Fade to grayscale
- `winner` - Crown appears, victory spin

### Scorecard Integration

**Position**: Floating badge below each character
- Cloudie: Total score (top center)
- Players: Individual scores (below each drop)
- **Animation**: Numbers count up/down on change
- **Color**: Match player color border

---

## 📋 Implementation Phases

### **PHASE 1: Layout Migration** ⏱️ 8 hours | Priority: P0

#### Tasks:
- [ ] **1.1** Create icon resource files (PNG/SVG for all toolbar icons)
  - Source from Material Icons or custom design
  - Sizes: 24dp, 32dp, 48dp for different densities
  - **Deliverable**: `res/drawable/` folder with icon assets

- [ ] **1.2** Design new `activity_intro_ai.xml` layout
  - Remove Start/Skip buttons
  - Add top toolbar with icon row (HorizontalScrollView)
  - Add bottom dialogue section (2 TextViews: host speech + last event)
  - Center area for character animations
  - **Deliverable**: New layout XML file

- [ ] **1.3** Create floating scorecard badge component
  - Custom View extending TextView
  - Circular or rounded rectangle background
  - Animation support for number changes
  - **Deliverable**: `ScorecardBadge.kt` class

- [ ] **1.4** Implement Connect icon dropdown
  - PopupMenu with 3 options: Dice, Board, Server
  - Each launches existing connection logic
  - **Deliverable**: `showConnectMenu()` function

#### Code Example: Icon Toolbar
```kotlin
// IntroAiActivity.kt - onCreate()
private fun setupToolbar() {
    val toolbar = findViewById<LinearLayout>(R.id.iconToolbar)
    
    // Connect icon with dropdown
    findViewById<ImageView>(R.id.iconConnect).setOnClickListener { 
        showConnectMenu(it) 
    }
    
    // Dice mode toggle
    findViewById<ImageView>(R.id.iconDiceMode).setOnClickListener {
        toggleDiceMode()
    }
    
    // Players button
    findViewById<ImageView>(R.id.iconPlayers).setOnClickListener {
        val intent = Intent(this, ProfileSelectionActivity::class.java)
        startActivity(intent)
    }
    
    // History button
    findViewById<ImageView>(R.id.iconHistory).setOnClickListener {
        val intent = Intent(this, GameHistoryActivity::class.java)
        startActivity(intent)
    }
    
    // Ranks button
    findViewById<ImageView>(R.id.iconRanks).setOnClickListener {
        val intent = Intent(this, LeaderboardActivity::class.java)
        startActivity(intent)
    }
    
    // Undo button
    findViewById<ImageView>(R.id.iconUndo).setOnClickListener {
        showUndoConfirmation()
    }
    
    // Debug button (hidden by default)
    findViewById<ImageView>(R.id.iconDebug).apply {
        visibility = View.GONE
        setOnClickListener {
            val intent = Intent(this@IntroAiActivity, MainActivity::class.java)
            startActivity(intent)
        }
    }
}

private fun showConnectMenu(anchor: View) {
    PopupMenu(this, anchor).apply {
        menuInflater.inflate(R.menu.connect_menu, menu)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.connect_dice -> connectGoDice()
                R.id.connect_board -> connectESP32()
                R.id.connect_server -> testServerConnection()
            }
            true
        }
        show()
    }
}
```

---

### **PHASE 2: Game Logic Integration** ⏱️ 12 hours | Priority: P0

#### Tasks:
- [ ] **2.1** Move game engine from MainActivity to IntroAiActivity
  - Copy `GameEngine` initialization
  - Copy turn processing logic (`handleNewRoll()`)
  - Copy player state management
  - **Deliverable**: `gameEngine`, `currentGameProfiles`, `currentPlayer` in IntroAi

- [ ] **2.2** Move BLE communication managers
  - Copy `ESP32ConnectionManager` instance
  - Copy `GoDiceSDK` initialization and callbacks
  - Handle `onDiceStable()` in IntroAi
  - **Deliverable**: BLE fully functional in IntroAi

- [ ] **2.3** Move virtual dice system
  - Copy `rollVirtualDice()` function
  - Copy dice mode toggle logic
  - Update UI to show dice value with emote animation
  - **Deliverable**: Both Bluetooth and virtual dice work

- [ ] **2.4** Integrate scorecard updates
  - Update `ScorecardBadge` views when scores change
  - Animate number transitions (count-up effect)
  - Highlight current player's badge
  - **Deliverable**: Real-time score display

#### Code Example: Score Update Animation
```kotlin
private fun updatePlayerScore(playerId: String, newScore: Int, oldScore: Int) {
    val badge = scorecardBadges[playerId] ?: return
    
    // Animate number count-up/down
    ValueAnimator.ofInt(oldScore, newScore).apply {
        duration = 800
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { badge.text = it.animatedValue.toString() }
        start()
    }
    
    // Trigger character emote based on delta
    val delta = newScore - oldScore
    when {
        delta > 0 -> playCharacterAnimation(playerId, "happy")
        delta < 0 -> playCharacterAnimation(playerId, "sad")
        else -> playCharacterAnimation(playerId, "idle")
    }
}
```

---

### **PHASE 3: Lottie Animation System** ⏱️ 20 hours | Priority: P1

#### Tasks:
- [ ] **3.1** Set up Lottie dependency
  ```kotlin
  // app/build.gradle.kts
  implementation("com.airbnb.android:lottie:6.2.0")
  ```
  - **Deliverable**: Lottie library integrated

- [ ] **3.2** Design character animations (After Effects)
  - **Cloudie**: 8 states (idle, talking, happy, sad, surprised, thinking, cheer, oops)
  - **Player Drops**: 6 states (idle, active, happy, sad, eliminated, winner)
  - Export as Lottie JSON files via Bodymovin plugin
  - **Deliverable**: 14 animation JSON files in `res/raw/`

- [ ] **3.3** Create animation manager class
  ```kotlin
  class CharacterAnimator(val lottieView: LottieAnimationView) {
      private val animationMap = mapOf(
          "idle" to R.raw.cloudie_idle,
          "talking" to R.raw.cloudie_talking,
          "happy" to R.raw.cloudie_happy,
          // ... etc
      )
      
      fun playAnimation(state: String, loop: Boolean = false) {
          animationMap[state]?.let { resId ->
              lottieView.setAnimation(resId)
              lottieView.repeatCount = if (loop) LottieDrawable.INFINITE else 0
              lottieView.playAnimation()
          }
      }
      
      fun stopAnimation() {
          lottieView.cancelAnimation()
      }
  }
  ```
  - **Deliverable**: `CharacterAnimator.kt` class

- [ ] **3.4** Implement TTS-to-animation sync
  - **Approach**: Fake mouth movement with simple loop during TTS playback
  - Start "talking" animation when `voiceService.speak()` called
  - Switch to "idle" when audio finishes
  - **Deliverable**: Voice-synced "talking" state

#### Animation Design Guidelines
- **File Size**: Keep each JSON < 200KB (use simple paths, no gradients)
- **Frame Rate**: 30 FPS (smooth on all devices)
- **Duration**: 
  - Idle loop: 3-5 seconds
  - Talking loop: 0.5 seconds (repeat)
  - Emotes: 1-2 seconds (one-shot)
- **Colors**: Use tintable layers (programmatically set player colors)

#### Mouth Sync Strategy (Lightweight)
```kotlin
private fun speakWithAnimation(text: String) {
    // Switch to talking animation
    cloudieAnimator.playAnimation("talking", loop = true)
    
    voiceService.speak(
        text = text,
        onStart = { /* Already talking */ },
        onComplete = {
            // Return to idle after 0.5s delay
            lifecycleScope.launch {
                delay(500)
                cloudieAnimator.playAnimation("idle", loop = true)
            }
        }
    )
}
```

---

### **PHASE 4: Event-Driven Emote System** ⏱️ 8 hours | Priority: P1

#### Tasks:
- [ ] **4.1** Define emotion trigger rules
  - Map game events to character emotions
  - Create decision tree for Cloudie responses
  - **Deliverable**: `EmotionMapper.kt` singleton

- [ ] **4.2** Implement event listeners
  - Listen to turn results (score changes)
  - Listen to chance card draws
  - Listen to player eliminations
  - **Deliverable**: Event-to-emote pipeline

- [ ] **4.3** Queue system for overlapping emotes
  - Prevent animation spam (max 1 emote per 3 seconds)
  - Priority system: elimination > big score > normal
  - **Deliverable**: `EmoteQueue.kt` class

#### Emotion Mapping Example
```kotlin
object EmotionMapper {
    fun getCloudieEmotion(event: GameEvent): String {
        return when (event) {
            is TurnResult -> when {
                event.scoreDelta >= 10 -> "cheer"
                event.scoreDelta > 0 -> "happy"
                event.scoreDelta < -5 -> "oops"
                event.scoreDelta < 0 -> "sad"
                event.chanceCard != null -> "surprised"
                else -> "idle"
            }
            is PlayerEliminated -> "sad"
            is GameWon -> "cheer"
            else -> "idle"
        }
    }
    
    fun getPlayerDropEmotion(scoreDelta: Int, isEliminated: Boolean): String {
        return when {
            isEliminated -> "eliminated"
            scoreDelta > 0 -> "happy"
            scoreDelta < 0 -> "sad"
            else -> "idle"
        }
    }
}
```

---

### **PHASE 5: Advanced Polish** ⏱️ 10 hours | Priority: P2

#### Tasks:
- [ ] **5.1** Add particle effects
  - Confetti for winner
  - Sparkles for score gains
  - Rain drops for Cloudie sad state
  - **Deliverable**: Lottie overlay layers

- [ ] **5.2** Implement bounce sync with audio
  - Analyze TTS audio waveform (optional, advanced)
  - OR: Simple bounce every 0.3s while speaking
  - **Deliverable**: Rhythmic character bounce

- [ ] **5.3** Add eye blink idle animation
  - Random blinks every 3-7 seconds
  - Layered on top of current animation
  - **Deliverable**: Realistic idle behavior

- [ ] **5.4** Color tinting for player drops
  - Programmatically tint drop animations to match player colors
  - Use `ColorFilter` on Lottie layers
  - **Deliverable**: Color-matched drops

#### Particle Effect Implementation
```kotlin
private fun triggerWinnerConfetti(playerId: String) {
    // Overlay Lottie confetti animation
    val confettiView = LottieAnimationView(this).apply {
        setAnimation(R.raw.confetti_burst)
        repeatCount = 0
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    
    rootLayout.addView(confettiView)
    confettiView.playAnimation()
    
    // Remove after animation completes
    confettiView.addAnimatorListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            rootLayout.removeView(confettiView)
        }
    })
}
```

---

### **PHASE 6: AndroidManifest & Navigation Updates** ⏱️ 2 hours | Priority: P0

#### Tasks:
- [ ] **6.1** Set IntroAiActivity as main launcher (optional)
  ```xml
  <!-- AndroidManifest.xml -->
  <activity
      android:name=".ui.intro.IntroAiActivity"
      android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
  </activity>
  ```
  - OR keep SplashActivity → ProfileSelectionActivity → IntroAiActivity flow
  - **Decision**: Depends on desired UX (discuss with team)
  - **Deliverable**: Updated AndroidManifest.xml

- [ ] **6.2** Update back button behavior
  - IntroAi back → ProfileSelectionActivity (not MainActivity)
  - ProfileSelection back → Exit app
  - **Deliverable**: Custom `onBackPressed()` logic

- [ ] **6.3** Hide MainActivity from app flow (debug only)
  - Remove from navigation hierarchy
  - Only accessible via Debug icon in IntroAi
  - **Deliverable**: MainActivity becomes hidden debug screen

---

## 🔧 Technical Architecture

### Component Hierarchy
```
IntroAiActivity (Main Game Screen)
├── IconToolbar (Top)
│   ├── ConnectIconDropdown
│   ├── DiceModeToggle
│   ├── PlayersButton → ProfileSelectionActivity
│   ├── HistoryButton → GameHistoryActivity
│   ├── RanksButton → LeaderboardActivity
│   ├── UndoButton
│   ├── RefreshButton
│   ├── ResetButton
│   ├── EndGameButton
│   └── DebugButton → MainActivity (hidden)
│
├── CharacterAnimationLayer (Center)
│   ├── CloudieAnimator (Lottie)
│   │   └── ScorecardBadge (total score)
│   ├── PlayerDropAnimator[0..3] (Lottie)
│   │   └── ScorecardBadge (individual scores)
│   └── ParticleOverlay (confetti, sparkles)
│
├── DialogueSection (Bottom)
│   ├── CloudieHostText (current speech)
│   └── LastEventText (game event log)
│
└── Game Logic Layer
    ├── GameEngine (turn processing)
    ├── BLE Managers (ESP32, GoDice)
    ├── VoiceService (TTS synthesis)
    ├── EmotionMapper (event → emote)
    └── EmoteQueue (animation scheduler)
```

### Data Flow
```
1. Dice Roll (Physical/Virtual)
   ↓
2. handleNewRoll(diceValue)
   ↓
3. GameEngine.processTurn() → TurnResult
   ↓
4. Update Scorecards (animated count-up)
   ↓
5. Trigger Emotes (EmotionMapper)
   ↓
6. Speak Cloudie Line (VoiceService + talking animation)
   ↓
7. Update Last Event Text
   ↓
8. Push to ESP32 + Live Server
```

---

## 📦 Assets Checklist

### Lottie Animation Files (JSON)
- [ ] `cloudie_idle.json` (3s loop)
- [ ] `cloudie_talking.json` (0.5s loop)
- [ ] `cloudie_happy.json` (1.5s one-shot)
- [ ] `cloudie_sad.json` (1.5s one-shot)
- [ ] `cloudie_surprised.json` (1s one-shot)
- [ ] `cloudie_thinking.json` (2s one-shot)
- [ ] `cloudie_cheer.json` (2s one-shot)
- [ ] `cloudie_oops.json` (1.5s one-shot)
- [ ] `drop_idle.json` (2s loop)
- [ ] `drop_active.json` (1s loop with pulse)
- [ ] `drop_happy.json` (1s one-shot)
- [ ] `drop_sad.json` (1s one-shot)
- [ ] `drop_eliminated.json` (2s fade effect)
- [ ] `drop_winner.json` (3s victory spin)
- [ ] `confetti_burst.json` (2s overlay)
- [ ] `sparkles.json` (1s overlay)

### Icon Assets (PNG/Vector)
- [ ] `ic_connect.xml` (plug icon)
- [ ] `ic_dice_bluetooth.xml` (die with bluetooth symbol)
- [ ] `ic_dice_virtual.xml` (die with screen symbol)
- [ ] `ic_players.xml` (group of people)
- [ ] `ic_history.xml` (clock/history symbol)
- [ ] `ic_ranks.xml` (trophy/leaderboard)
- [ ] `ic_undo.xml` (curved arrow)
- [ ] `ic_refresh.xml` (circular arrows)
- [ ] `ic_reset.xml` (restart icon)
- [ ] `ic_end_game.xml` (stop/exit icon)
- [ ] `ic_debug.xml` (bug icon)

### Menu Resources
- [ ] `res/menu/connect_menu.xml` (3 items: Dice, Board, Server)

---

## 📊 Success Metrics

### Performance Targets
- **Animation FPS**: ≥ 30 FPS on mid-range devices (Snapdragon 6 Gen 1)
- **TTS Latency**: < 1s from event to voice start
- **Total APK Size Increase**: < 5 MB (from Lottie assets)
- **Memory Usage**: < 150 MB RAM (with animations loaded)

### UX Validation
- Users can start a game without seeing MainActivity
- All game controls accessible from IntroAi icons
- Scorecard updates visible during gameplay
- Emotes trigger correctly for 90% of game events
- Voice-animation sync feels natural (user survey)

---

## 🚀 Migration Strategy

### Option A: Parallel Development (Recommended)
1. Keep MainActivity functional (don't delete code)
2. Build new IntroAi interface alongside
3. Test both versions for 2 weeks
4. Gradual rollout: 10% users → 50% → 100%
5. Deprecate MainActivity after validation

### Option B: Direct Replacement (Risky)
1. Copy all game logic to IntroAi in one sprint
2. Delete MainActivity code
3. Fix bugs in IntroAi
4. Risk: Introduces instability if not tested thoroughly

**Recommendation**: Choose Option A for safer deployment.

---

## 🧪 Testing Plan

### Unit Tests
- [ ] EmotionMapper: Correct emote for each event type
- [ ] CharacterAnimator: Animation state transitions
- [ ] ScorecardBadge: Number animation accuracy
- [ ] EmoteQueue: Priority and rate limiting

### Integration Tests
- [ ] Dice roll → Score update → Emote trigger pipeline
- [ ] Voice synthesis → Animation sync timing
- [ ] BLE connection → Game state → UI update

### Manual Testing Scenarios
1. **2-player game**: Verify both drops animate correctly
2. **4-player game**: All drops visible without overlap
3. **AI player**: Cloudie drop shows special emote
4. **Chance card**: Surprised emote triggers
5. **Winner**: Confetti + cheer animation
6. **Elimination**: Drop fades to grayscale
7. **Rapid rolls**: Emote queue doesn't spam
8. **Voice disabled**: Text-only mode works
9. **Low memory device**: Graceful degradation (skip animations)
10. **Offline mode**: All icons functional except server

---

## 📝 Development Checklist

### Before Starting
- [ ] Review all existing docs (AI_GAMEMASTER_ROADMAP, AI_ANIMATION_NEXT_STEPS, AI_PLAYER_CLOUDIE)
- [ ] Backup MainActivity.kt (copy to `archived/MainActivity_legacy.kt`)
- [ ] Create feature branch: `feature/intro-ai-main-interface`
- [ ] Set up After Effects + Bodymovin plugin for Lottie export

### During Development
- [ ] Commit after each phase completion
- [ ] Update this roadmap with actual time spent vs estimated
- [ ] Document any architecture changes in comments
- [ ] Screenshot new UI at each milestone

### After Completion
- [ ] Update `copilot-instructions.md` with new architecture
- [ ] Record demo video showing new interface
- [ ] Create user guide for icon toolbar
- [ ] Publish release notes highlighting character animations

---

## 🎓 Learning Resources

### Lottie Tutorials
- [LottieFiles - Android Integration](https://lottiefiles.com/blog/working-with-lottie/how-to-add-lottie-animation-android-app)
- [Airbnb Lottie Docs](https://airbnb.io/lottie/#/android)
- [After Effects to Lottie Workflow](https://lottiefiles.com/blog/after-effects/how-to-export-lottie-animation-from-after-effects)

### Character Animation Design
- [12 Principles of Animation](https://www.creativebloq.com/advice/understand-the-12-principles-of-animation)
- [Squash and Stretch in 2D](https://www.animationmentor.com/blog/squash-and-stretch-the-12-basic-principles-of-animation/)
- [Idle Animation Best Practices](https://www.gamedeveloper.com/design/the-art-of-the-idle-animation)

### TTS Sync Techniques
- [Simple Mouth Sync Tutorial](https://www.gamedeveloper.com/audio/simple-text-to-speech-lip-sync)
- [Rhythm-based Animation](https://docs.unity3d.com/Manual/animeditor-AnimatingACharacter.html)

---

## 💡 Future Enhancements (Post-MVP)

### Phase 7: Advanced AI Personality
- Dynamic dialogue based on player history
- Contextual jokes and references
- Multi-lingual voice support

### Phase 8: Multiplayer Emotes
- Players can trigger emotes for their drops
- Emoji reactions during other players' turns
- Social sharing of funny moments

### Phase 9: Customization
- Unlock alternative Cloudie skins (thundercloud, rainbow cloud)
- Player drop costume unlocks (hats, accessories)
- Custom animation speed settings

### Phase 10: Spectator Mode Enhancements
- Pip-in-pip: Show IntroAi interface on live.html
- Live chat with emoji reactions
- Real-time betting/predictions (gamification)

---

## 📞 Support & Questions

If implementation questions arise:
1. Check existing docs: `AI_GAMEMASTER_ROADMAP.md`, `AI_ANIMATION_NEXT_STEPS.md`
2. Review current `IntroAiActivity.kt` and `MainActivity.kt` code
3. Test Lottie animations in isolation first
4. Use `Log.d("IntroAi", ...)` extensively for debugging

**Key Decision Points Requiring Approval:**
- Lottie animation style/quality (show mockups before production)
- Icon design language (Material vs custom illustrations)
- Launcher activity choice (SplashActivity vs IntroAiActivity)
- Debug mode access method (hidden icon vs shake gesture)

---

## ✅ Definition of Done

IntroAiActivity is considered complete when:
- ✅ All toolbar icons functional (except debug)
- ✅ Game can be played start-to-finish without MainActivity
- ✅ Scorecards update in real-time with animation
- ✅ Cloudie emotes trigger correctly for major events
- ✅ Player drops animate on score changes
- ✅ Voice synthesis works with "talking" animation sync
- ✅ Performance ≥ 30 FPS on mid-range devices
- ✅ No crashes in 50+ consecutive game rounds
- ✅ Code review passed with ≥ 2 approvals
- ✅ User acceptance testing: 5 users complete a game successfully

---

**Total Estimated Effort:** ~60 hours (1.5 weeks for solo dev, 1 week for team)  
**Risk Level:** Medium (major UI refactor, animation learning curve)  
**Dependencies:** After Effects skills, Lottie export workflow setup

**Status:** 📋 **ROADMAP DRAFT** - Awaiting approval to begin Phase 1
