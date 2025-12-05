# Last Drop - Testing Guide

## Overview
This guide provides comprehensive testing procedures for all new features added in Option A (Enhanced Gameplay Features) and Option C (Polish & Testing).

## Build & Install

```powershell
# Clean build
.\gradlew clean assembleDebug

# Install on device
.\gradlew installDebug
```

## Test 1: Profile System

### 1.1 Profile Creation
- [ ] Launch app, tap "👤 Player Profiles"
- [ ] Tap "Create New Profile" button
- [ ] Enter name, nickname, select avatar color
- [ ] Verify profile appears in list

### 1.2 Profile Selection
- [ ] Create 2-4 profiles
- [ ] Start new game
- [ ] Tap player name slots to assign profiles
- [ ] Verify colors match selected profiles
- [ ] Start game, verify stats are tracked

### 1.3 Guest Mode
- [ ] Leave a player slot unassigned
- [ ] Verify system creates "Guest X" profile
- [ ] Play game with guest player
- [ ] Verify guest stats are NOT saved to leaderboard

## Test 2: Game History

### 2.1 History Recording
- [ ] Play complete game (reach tile 20)
- [ ] Tap "📜 Game History" from main menu
- [ ] Verify game appears in history
- [ ] Check game card shows: date, players, winner, final score

### 2.2 Filters
- [ ] Tap "Wins" filter → Only shows games you won
- [ ] Tap "Losses" filter → Only shows games you lost
- [ ] Tap "Today" filter → Only shows games played today
- [ ] Tap "All" filter → Shows all games

### 2.3 Share & Export
- [ ] Tap "📤 Share Result" on any game card
- [ ] Verify Android share sheet opens
- [ ] Share to any app (Messages, Email, etc.)
- [ ] Tap "📊 Export CSV" button in header
- [ ] Verify CSV file downloads with all game data
- [ ] Open CSV in Excel/Sheets, verify format

## Test 3: Leaderboard

### 3.1 Rankings
- [ ] Tap "🏆 Leaderboard" from main menu
- [ ] Verify all profiles appear (excluding guests)
- [ ] Check top 3 have medal badges (🥇🥈🥉)
- [ ] Verify default sort is by wins

### 3.2 Sort Options
- [ ] Tap "Wins" → Verify sorted by total wins
- [ ] Tap "Win Rate" → Verify sorted by win percentage
- [ ] Tap "Best Score" → Verify sorted by personal best
- [ ] Tap "Streak" → Verify sorted by current win streak

### 3.3 Profile Navigation
- [ ] Tap any profile card in leaderboard
- [ ] Verify navigates to Profile Stats screen
- [ ] Check all stats load correctly

## Test 4: Profile Stats

### 4.1 Statistics Display
- [ ] Open any profile from leaderboard
- [ ] Verify displays: Total Games, Wins, Losses, Win Rate
- [ ] Check Personal Best, Current Streak, Longest Streak
- [ ] Verify avatar color matches profile

### 4.2 Achievements
- [ ] Scroll to "Achievements" section
- [ ] Verify shows all 20 achievements
- [ ] Unlocked achievements: Gold background, colored emoji
- [ ] Locked achievements: Gray background, black emoji

### 4.3 Rivalries
- [ ] Scroll to "Rivalries" section
- [ ] If no rivalries: Shows "No rivalries yet"
- [ ] If rivalries exist:
  - [ ] Verify shows opponent name, head-to-head record
  - [ ] Check win percentage calculation
  - [ ] Verify rivalry emoji (🔥 for close matches, etc.)

### 4.4 Share Functions
- [ ] Tap "Share Stats" → Verify shares profile summary
- [ ] Tap "Share Win Streak" → Verify shares streak stat
- [ ] Tap on leaderboard rank, share → Verify shares position

## Test 5: Loading States & Error Handling

### 5.1 Loading Indicators
- [ ] Open Game History → Verify loading spinner shows before content
- [ ] Open Leaderboard → Verify loading spinner shows
- [ ] Open Profile Stats → Verify loading spinner shows
- [ ] Verify all spinners disappear when content loads

### 5.2 Error Handling
- [ ] Enable airplane mode
- [ ] Try opening Game History → Verify error toast appears
- [ ] Try opening Leaderboard → Verify error toast appears
- [ ] Disable airplane mode, retry → Verify works

### 5.3 Empty States
- [ ] Fresh install with no games played
- [ ] Open Game History → Verify shows "No games yet"
- [ ] Open Leaderboard → Verify shows "No profiles yet"

## Test 6: Animations & Polish

### 6.1 Activity Transitions
- [ ] Navigate MainActivity → Game History → Verify fade-in animation
- [ ] Navigate MainActivity → Leaderboard → Verify fade-in animation
- [ ] Navigate Leaderboard → Profile Stats → Verify fade-in animation
- [ ] Use back button → Verify smooth fade-out

### 6.2 Undo Countdown
- [ ] Play game, make a move
- [ ] Tap "Undo Last Roll"
- [ ] Verify button text shows "Confirm (5 s)", "Confirm (4 s)", etc.
- [ ] Verify yellow progress bar animates from 5 → 0
- [ ] Wait 5 seconds → Verify timer expires, progress bar hides
- [ ] Try undo again, tap "Confirm" → Verify progress bar hides immediately

### 6.3 Achievement Unlock Animation
- [ ] Trigger achievement (e.g., win first game for "first_win")
- [ ] Verify Toast notification appears with emoji + description
- [ ] Verify "Last Event" text shows "🏆 Achievement Name unlocked!"
- [ ] Verify pulse animation plays on achievement text

### 6.4 Content Fade-In
- [ ] Open Game History → Verify game cards fade in smoothly
- [ ] Open Leaderboard → Verify profile cards fade in smoothly
- [ ] Open Profile Stats → Verify scroll content fades in

## Test 7: Complete Game Flow

### 7.1 Full Game with Profiles
1. [ ] Create 4 new profiles (different colors)
2. [ ] Start new game, assign all 4 profiles
3. [ ] Play complete game to tile 20
4. [ ] Verify winner gets achievement notification
5. [ ] Check Game History shows correct winner
6. [ ] Check Leaderboard updates all 4 profiles
7. [ ] Verify winner's stats updated (wins +1, streak +1)
8. [ ] Verify losers' stats updated (losses +1, streak reset)

### 7.2 Consecutive Games
1. [ ] Play 3 games in a row with same profiles
2. [ ] Win all 3 with same player
3. [ ] Check "hot_streak_3" achievement unlocks
4. [ ] Verify leaderboard shows 3-game win streak
5. [ ] Play 4th game, lose → Verify streak resets

## Test 8: Achievement Triggers

Test each achievement unlocks correctly:

### 8.1 Win-Based Achievements
- [ ] **first_win**: Win your first game
- [ ] **hot_streak_3**: Win 3 consecutive games
- [ ] **hot_streak_5**: Win 5 consecutive games
- [ ] **hot_streak_10**: Win 10 consecutive games
- [ ] **century_club**: Score 100+ in one game

### 8.2 Tile Achievements
- [ ] **oasis_master**: Land on Oasis (Tile 5) 5 times across all games
- [ ] **market_regular**: Land on Market (Tile 8) 5 times
- [ ] **well_visitor**: Land on Well (Tile 12) 5 times
- [ ] **caravan_fan**: Land on Caravan (Tile 15) 5 times

### 8.3 Gameplay Achievements
- [ ] **lucky_roller**: Roll 6+6 (twelve) in two-dice mode
- [ ] **perfect_score**: Win game without landing on any penalty tiles
- [ ] **comeback_king**: Win game after being 20+ points behind
- [ ] **survivor**: Win game while all other players eliminated (score <0)
- [ ] **explorer**: Land on all 20 tiles at least once (across all games)

### 8.4 Social Achievements
- [ ] **social_player**: Play games with 5+ different opponents
- [ ] **nemesis**: Face same opponent 10+ times
- [ ] **friendly_rivalry**: Maintain 5+ different rivalries
- [ ] **champion**: Reach 50+ total wins
- [ ] **legend**: Reach 100+ total wins
- [ ] **undefeated**: Win 20 consecutive games

## Test 9: Database Integrity

### 9.1 Data Persistence
- [ ] Play game, close app completely
- [ ] Reopen app → Verify game history persists
- [ ] Check leaderboard → Verify stats persist
- [ ] Check profile → Verify achievements persist

### 9.2 Concurrent Games
- [ ] Start game with Player A & B
- [ ] Mid-game: Reset and start new game with Player C & D
- [ ] Verify both games record correctly in history
- [ ] Verify all 4 players have stats updated

## Test 10: Edge Cases

### 10.1 Single Player
- [ ] Start game with only 1 player
- [ ] Play to completion
- [ ] Verify stats record correctly
- [ ] Verify achievements unlock

### 10.2 All Guests
- [ ] Start game without selecting any profiles
- [ ] Play complete game
- [ ] Verify game records in history
- [ ] Verify no guest profiles appear in leaderboard

### 10.3 Profile Deletion (if implemented)
- [ ] Create profile, play games
- [ ] Delete profile
- [ ] Verify games still in history (marked as "Deleted Player")
- [ ] Verify leaderboard updates

## Test 11: Performance

### 11.1 Large Dataset
- [ ] Simulate 50+ games in database
- [ ] Open Game History → Verify loads quickly (<2s)
- [ ] Test filters → Verify instant filtering
- [ ] Scroll through history → Verify smooth scrolling

### 11.2 CSV Export
- [ ] Export 50+ games to CSV
- [ ] Verify file size reasonable (<5MB)
- [ ] Open in Excel → Verify all data present
- [ ] Check formulas/calculations work

## Test 12: UI/UX Polish

### 12.1 Dark Mode Consistency
- [ ] All screens use consistent dark theme (#1a1a1a, #2a2a2a, etc.)
- [ ] Text contrast readable (white text on dark backgrounds)
- [ ] Buttons have clear press states

### 12.2 Button Press Feedback
- [ ] All buttons respond visually when tapped
- [ ] Filters highlight when selected
- [ ] Sort options show active state

### 12.3 Text & Icons
- [ ] All emojis render correctly
- [ ] No truncated text in cards
- [ ] Dates format correctly (e.g., "Dec 15, 2024 3:45 PM")

## Known Issues to Watch For

1. **Database Migration**: If app crashes on upgrade, check Room migration from v5→v6
2. **FileProvider**: CSV export might fail if FileProvider not configured in manifest
3. **Undo Timer**: If timer doesn't cancel properly, check coroutine cancellation
4. **Achievement Duplicates**: Verify achievements don't unlock multiple times for same event
5. **Rivalry Calculation**: Head-to-head wins must count only direct matchups, not all games

## Reporting Issues

When filing bugs, include:
- Device model & Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output (filter by "LastDrop" tag)
- Screenshots/videos if UI-related

## Success Criteria

All tests marked ✅ = Ready for release!

Minimum requirements:
- ✅ Profile system works (create, select, save stats)
- ✅ Game history records all games correctly
- ✅ Leaderboard displays accurate rankings
- ✅ All 4 sort options work
- ✅ Share/Export functions work
- ✅ Loading states prevent blank screens
- ✅ Error handling prevents crashes
- ✅ Animations smooth and performant
- ✅ At least 10 achievements unlock correctly
- ✅ Database persists between app restarts
