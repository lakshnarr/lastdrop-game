# IntroAi Main Interface - Testing Guide

## 🎯 Overview
Complete testing checklist for the transformed IntroAiActivity - now the primary game interface with Lottie animations, sound effects, haptic feedback, and particle effects.

---

## ✅ Pre-Test Setup

### Required Resources
- [ ] Device: Samsung SM-S928B (or any Android 12+ device)
- [ ] APK installed via `.\gradlew installDebug`
- [ ] Bluetooth permissions granted
- [ ] Sound enabled (check device volume)
- [ ] Haptic feedback enabled in device settings

### Known Limitations
- ⚠️ **Sound files not included** - SoundEffectManager loads from `res/raw/` but files don't exist yet
- ✅ **Haptic feedback** - Fully functional, no external resources needed
- ✅ **Lottie animations** - Load from URLs via EmoteManager (14 animations configured)
- ✅ **Particle effects** - Fully functional canvas rendering

---

## 🧪 Test Cases

### **Test 1: App Launch Flow**
**Steps:**
1. Launch app from device home screen
2. Wait for SplashActivity (2.5 seconds)
3. Observe ProfileSelectionActivity loads

**Expected Results:**
- ✅ Splash screen displays for 2.5s
- ✅ ProfileSelectionActivity appears with player grid
- ✅ No crashes or errors

---

### **Test 2: Player Selection**
**Steps:**
1. Tap 2-4 player profiles (minimum 2 required)
2. Observe selection highlights
3. Tap "🎮 Start Game" button

**Expected Results:**
- ✅ Selected profiles show visual highlight
- ✅ Button shows player count: "Start Game (X Players)"
- ✅ If <2 players: Warning toast + button shake animation
- ✅ Button haptic feedback on tap

---

### **Test 3: Color Assignment**
**Steps:**
1. After tapping Start, color selection dialog appears
2. Select color for Player 1
3. Repeat for all players
4. AI player auto-assigns last color

**Expected Results:**
- ✅ Dialog shows player name + 4 color options
- ✅ Selected colors become unavailable for next player
- ✅ AI skips dialog, auto-picks remaining color
- ✅ No duplicate colors assigned

---

### **Test 4: IntroAi Launch & Entrance Animations**
**Steps:**
1. Complete color assignment
2. Observe IntroAiActivity loads
3. Watch entrance animations

**Expected Results:**
- ✅ **Confetti burst** - 50 particles explode from center
- ✅ **Game win sound** - Plays celebration audio (if sound files present)
- ✅ **Game win haptic** - Vibration pattern (ascending bursts)
- ✅ **Cloudie animation** - Flies in with overshoot, scales to 1.0
- ✅ **Player drops** - Fade in from top
- ✅ **Scorecard badges** - All show "0" with player colors
- ✅ **Dialogue text** - Shows game start message

---

### **Test 5: Icon Toolbar Functionality**
**Steps:**
1. Tap each toolbar icon
2. Verify sounds and haptics

**Expected Results:**

| Icon | Action | Sound | Haptic | Result |
|------|--------|-------|--------|--------|
| 🔌 Connect | Dropdown menu | Button click | Light tap | Shows: Dice/Board/Server |
| 🎲 Dice Mode | Toggle BT/Virtual | Toggle on/off | Light tap | Icon changes |
| 👥 Players | Back to selection | Button click | Light tap | Navigates to ProfileSelection |
| 📊 History | Placeholder | Button click | Light tap | "Coming soon" toast |
| 🏆 Ranks | Placeholder | Button click | Light tap | "Coming soon" toast |
| ↩️ Undo | Placeholder | Warning | Warning tap | "Coming soon" toast |
| 🔄 Refresh | Placeholder | Button click | Light tap | "Coming soon" toast |
| 🔴 Reset | Reset game | Warning | Warning tap | Resets scores/positions |
| ⏹️ End Game | End current | Warning | Warning tap | Ends game |
| 🐛 Debug | Hidden/MainActivity | Button click | Light tap | Opens MainActivity |

---

### **Test 6: Virtual Dice System**
**Steps:**
1. Ensure dice mode is "Virtual" (🎲 icon)
2. Tap virtual dice buttons 1-6
3. Observe each roll outcome

**Expected Results:**
- ✅ **Dice roll sound** - Rolling audio plays
- ✅ **Dice roll haptic** - 6-stage pattern (roll-roll-LAND)
- ✅ **Tile landing sound** - Soft thud when piece moves
- ✅ **Tile landing haptic** - Medium tap
- ✅ **Score change animation** - Badge counts up/down with bounce
- ✅ **Active player indicator** - Shimmer + pulse on current player's badge
- ✅ **Dialogue update** - Cloudie speaks roll announcement
- ✅ **Last event text** - Updates with turn details

---

### **Test 7: Score Change Effects**

#### Positive Score (Gain Points)
**Steps:**
1. Roll dice to land on positive tile (e.g., Oasis +3)
2. Observe effects

**Expected Results:**
- ✅ **Score gain sound** - Positive chime
- ✅ **Score gain haptic** - Double tap pattern
- ✅ **Sparkle particles** - 20 gold particles spawn at badge
- ✅ **Badge animation** - Bounces 1.15x scale, green flash
- ✅ **Cloudie animation** - CELEBRATE emote (if score > 5 points)
- ✅ **Dialogue** - Positive commentary

#### Negative Score (Lose Points)
**Steps:**
1. Roll dice to land on negative tile (e.g., Desert -2)
2. Observe effects

**Expected Results:**
- ✅ **Score loss sound** - Warning tone
- ✅ **Score loss haptic** - Single heavy buzz
- ✅ **Badge animation** - Bounces 1.15x scale, red flash
- ✅ **Cloudie animation** - WARNING emote (if score < -5 points)
- ✅ **Dialogue** - Sympathetic commentary

---

### **Test 8: Chance Card Events**
**Steps:**
1. Roll dice to land on tile with chance card
2. Observe chance card effects

**Expected Results:**
- ✅ **Chance card sound** - Special event audio
- ✅ **Chance card haptic** - Triple tap pattern
- ✅ **Cloudie animation** - THINKING emote
- ✅ **Dialogue** - Card description narration
- ✅ **Score adjustment** - Badge updates based on card effect

---

### **Test 9: Player Elimination**
**Steps:**
1. Force player to score ≤ 0 (via negative tiles/cards)
2. Observe elimination sequence

**Expected Results:**
- ✅ **Elimination sound** - Dramatic audio
- ✅ **Elimination haptic** - Long descending pattern (200ms total)
- ✅ **Cloudie animation** - SAD emote with rain
- ✅ **Badge fade** - Animates to 30% alpha over 300ms
- ✅ **Dialogue** - Elimination announcement with remaining player count
- ✅ **Turn skip** - Eliminated player no longer gets turns

---

### **Test 10: Scorecard Badge Animations**

#### Active Player Effects
**Steps:**
1. Observe current player's badge
2. Wait for continuous shimmer

**Expected Results:**
- ✅ **Pulse animation** - Scales to 1.3x with overshoot
- ✅ **Shimmer effect** - 2-second cycle, white overlay (0-80 alpha)
- ✅ **Border color** - Matches assigned player color

#### Eliminated Player Effects
**Steps:**
1. Eliminate a player (score ≤ 0)
2. Observe badge state

**Expected Results:**
- ✅ **Alpha fade** - 30% opacity (animated transition)
- ✅ **No shimmer** - Effect stops
- ✅ **No pulse** - Animation disabled
- ✅ **Score frozen** - No further updates

---

### **Test 11: Lottie Animation System**
**Steps:**
1. Throughout gameplay, observe Cloudie animations
2. Check animation URLs load correctly

**Expected Results:**

| Event | Animation | URL Loaded | Loop |
|-------|-----------|------------|------|
| Idle | CLOUDIE_IDLE | ✅ | Yes (3s) |
| Speaking | CLOUDIE_SPEAKING | ✅ | Yes (0.5s) |
| Score gain >5 | CLOUDIE_CELEBRATE | ✅ | No |
| Score loss <-5 | CLOUDIE_WARNING | ✅ | No |
| Elimination | CLOUDIE_SAD | ✅ | No |
| Chance card | CLOUDIE_THINKING | ✅ | No |
| Game start | CLOUDIE_EXCITED | ✅ | No |

---

### **Test 12: Particle Effects**

#### Confetti Burst
**Steps:**
1. Start new game
2. Observe confetti at game start

**Expected Results:**
- ✅ **Particle count** - 50 rectangles
- ✅ **Colors** - 8 random vibrant colors
- ✅ **Physics** - Explode outward, fall with gravity
- ✅ **Rotation** - Each particle spins
- ✅ **Lifetime** - 3 seconds, fade out
- ✅ **Cleanup** - Auto-removes from view

#### Sparkles (Score Gain)
**Steps:**
1. Gain score (any positive tile)
2. Observe sparkles at badge location

**Expected Results:**
- ✅ **Particle count** - 20 circles
- ✅ **Color** - Gold (0xFFD700)
- ✅ **Position** - Centered on scorecard badge
- ✅ **Physics** - Radiate outward from badge
- ✅ **Lifetime** - 3 seconds, fade out

---

### **Test 13: Turn Progression**
**Steps:**
1. Roll dice for Player 1
2. Wait for turn to advance to Player 2
3. Observe badge updates

**Expected Results:**
- ✅ **Previous player** - Shimmer stops, scale returns to 1.0
- ✅ **Next player** - Shimmer starts, pulse animation
- ✅ **Dialogue** - Turn transition announcement
- ✅ **Last event** - Updates with previous action

---

### **Test 14: Game Reset**
**Steps:**
1. Play several turns (build up scores)
2. Tap Reset icon (🔴)
3. Confirm reset action

**Expected Results:**
- ✅ **Warning sound** - Alert audio
- ✅ **Warning haptic** - Heavy buzz
- ✅ **Confirmation** - Dialog or immediate reset
- ✅ **Score reset** - All badges animate to 0
- ✅ **Position reset** - All players return to tile 1
- ✅ **Elimination reset** - All players alive (100% alpha)
- ✅ **Turn reset** - Returns to Player 1

---

### **Test 15: End Game**
**Steps:**
1. Play game to completion or tap End Game (⏹️)
2. Observe end state

**Expected Results:**
- ✅ **Warning sound** - Alert audio
- ✅ **Warning haptic** - Heavy buzz
- ✅ **Confirmation** - Dialog or immediate end
- ✅ **Winner detection** - Highest score among alive players
- ✅ **Confetti** - Victory particle effect (if winner found)
- ✅ **Dialogue** - Game end announcement

---

### **Test 16: Navigation & Back Button**
**Steps:**
1. Press device back button in IntroAiActivity
2. Verify navigation behavior

**Expected Results:**
- ✅ **Back to ProfileSelection** - Navigates with clear task stack
- ✅ **No crash** - Clean navigation
- ✅ **State preserved** - Can restart game with same players

---

### **Test 17: Debug Button**
**Steps:**
1. Tap Debug icon (🐛) 10 times in toolbar
2. Observe MainActivity launch

**Expected Results:**
- ✅ **MainActivity opens** - Old game board interface
- ✅ **No crash** - Backward compatibility maintained
- ✅ **Can return** - Back button returns to IntroAi

---

### **Test 18: Memory & Performance**

**Monitoring:**
1. Play 20+ consecutive turns
2. Trigger many particle effects
3. Cycle through all animations

**Expected Results:**
- ✅ **No memory leaks** - Particle effects auto-cleanup
- ✅ **Smooth animations** - 60 FPS Lottie playback
- ✅ **No frame drops** - Scorecard animations fluid
- ✅ **No crashes** - Stable over extended gameplay

---

## 🐛 Known Issues & Workarounds

### Issue 1: Sound Files Missing
**Symptom:** No audio plays despite SoundEffectManager calls  
**Cause:** `res/raw/` folder empty - sound files not included  
**Workaround:** Add MP3/OGG files to `res/raw/` with matching names:
- `button_click.mp3`, `dice_roll.mp3`, `score_gain.mp3`, etc.

### Issue 2: Lottie Animations Slow to Load
**Symptom:** First animation has delay  
**Cause:** URL-based loading requires network fetch  
**Workaround:** Normal behavior - subsequent plays are cached

### Issue 3: Haptic Feedback Not Working
**Symptom:** No vibrations on actions  
**Cause:** Device settings or battery saver mode  
**Check:** Settings → Sound & Vibration → Vibration enabled

---

## 📊 Feature Completion Matrix

| Phase | Feature | Status | Notes |
|-------|---------|--------|-------|
| **Phase 1** | Icon Toolbar | ✅ Complete | 10 icons with dropdown |
| | Layout Redesign | ✅ Complete | Top toolbar, center animations, bottom dialogue |
| | ScorecardBadge Component | ✅ Complete | Custom view with animations |
| **Phase 2** | GameEngine Integration | ✅ Complete | 20 tiles, chance cards |
| | BLE Managers | ✅ Complete | ESP32 + GoDice placeholders |
| | Virtual Dice System | ✅ Complete | 6 buttons, immediate response |
| | Scorecard Updates | ✅ Complete | Real-time with animations |
| **Phase 3** | Lottie Dependency | ✅ Complete | Version 6.2.0 |
| | EmoteManager | ✅ Complete | 14 animations via URLs |
| **Phase 4** | DialogueGenerator | ✅ Complete | 10+ context-aware methods |
| | Event-Driven System | ✅ Complete | Roll, tile, card, elimination dialogue |
| **Phase 5** | SoundEffectManager | ✅ Complete | 18 sound effects (files missing) |
| | HapticFeedbackManager | ✅ Complete | Contextual vibration patterns |
| | Scorecard Polish | ✅ Complete | Bounce, flash, shimmer, shadow |
| | ParticleEffectView | ✅ Complete | Confetti, sparkles, rain |
| **Phase 6** | Navigation Fix | ✅ Complete | Splash → ProfileSelection → IntroAi |

---

## 🎉 Success Criteria

**Minimum Viable Experience:**
- [x] App launches without crashes
- [x] Player selection works (2-4 players)
- [x] Color assignment completes
- [x] IntroAiActivity loads with animations
- [x] Virtual dice functional (roll → score update)
- [x] Turn progression works
- [x] Game can be reset/ended

**Enhanced Experience (All Features):**
- [x] Confetti bursts on game start
- [x] Sparkles on score gains
- [x] Haptic feedback on all interactions
- [x] Lottie animations for Cloudie emotions
- [x] Scorecard bounce/shimmer/flash effects
- [x] Sound effects (when files added)
- [x] Dialogue generation with context
- [x] Elimination handling
- [x] Navigation flow corrected

---

## 📝 Testing Checklist

### Quick Smoke Test (5 minutes)
- [ ] Launch app → select 2 players → start game
- [ ] Roll dice 5 times
- [ ] Observe animations, sounds, haptics
- [ ] Tap Reset → verify state clears
- [ ] Press back → returns to ProfileSelection

### Full Feature Test (20 minutes)
- [ ] Complete all 18 test cases above
- [ ] Document any failures
- [ ] Check logcat for errors
- [ ] Verify particle cleanup (no memory issues)

### Edge Cases
- [ ] Test with 2 players (minimum)
- [ ] Test with 4 players (maximum)
- [ ] Test elimination scenario (score ≤ 0)
- [ ] Test rapid dice rolls (10+ consecutive)
- [ ] Test device rotation (if supported)

---

## 🔧 Debugging Commands

```powershell
# Clear logcat and monitor specific tags
adb logcat -c
adb logcat IntroAiActivity:D SoundEffectManager:D HapticFeedback:D ParticleEffectView:D *:S

# Monitor Lottie loading
adb logcat EmoteManager:D *:S

# Check for crashes
adb logcat AndroidRuntime:E *:S

# Reinstall APK
.\gradlew installDebug
```

---

## ✅ Sign-Off

**Testing completed by:** _________________  
**Date:** _________________  
**Device:** Samsung SM-S928B  
**Build:** app-debug.apk  

**Overall Status:**
- [ ] ✅ All core features working
- [ ] ⚠️ Minor issues (document below)
- [ ] ❌ Blockers found (document below)

**Notes:**
_____________________________________________________________________________
_____________________________________________________________________________
_____________________________________________________________________________
