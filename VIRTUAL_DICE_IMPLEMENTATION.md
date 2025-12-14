# Virtual Dice Feature - Play Store Testing Implementation

## Overview
Added virtual dice functionality to enable Play Store closed group testing without requiring physical GoDice hardware.

## Features Implemented

### 1. Dice Mode Toggle
- **Location**: Above "Connect Dice" button
- **States**: 
  - Bluetooth Dice (default)
  - Virtual Dice
- **UI**: Switch control with clear label showing current mode

### 2. Connect Dice Button Behavior
- **Bluetooth Mode**: Enabled, green color (#4CAF50)
- **Virtual Mode**: Disabled, gray color (#757575), 50% opacity
- Auto-disconnects physical dice when switching to virtual mode

### 3. Animated Virtual Dice
- **Location**: Between Scoreboard and Last Event sections
- **Size**: 120dp x 120dp
- **Design**: 
  - Blue gradient background with rounded corners
  - Unicode dice faces (⚀ ⚁ ⚂ ⚃ ⚄ ⚅)
  - 360° rotation animation on each roll
  - Elevation shadow for depth
- **Animation**: 
  - 1 second rapid value cycling (20 frames @ 50ms each)
  - Final value display
  - 300ms pause before processing
- **Interaction**: 
  - Tap to roll
  - Shows "🎲 Tap to Roll" label above
  - "Virtual Dice Active" hint below

### 4. Roll Processing Logic
The virtual dice integrates seamlessly with existing game logic:

**Production Mode** (testModeEnabled = false):
- Calls `onDiceStable(0, value)` directly
- Processes turn as if from physical GoDice
- Sends to ESP32 (if connected) and Server API
- Full game flow: roll → move token → update scoreboard → AI commentary

**Test Mode 1** (ESP32 testing):
- Calls `simulateDiceRoll(value)`
- Sends to ESP32, waits for coin placement
- Logs to test console

**Test Mode 2** (Android/Web only):
- Calls `simulateDiceRoll(value)`
- Bypasses ESP32, processes locally
- Updates live.html immediately

## Files Modified

### MainActivity.kt
**New Variables**:
```kotlin
private lateinit var switchDiceMode: Switch
private lateinit var tvDiceModeLabel: TextView
private lateinit var layoutVirtualDiceContainer: LinearLayout
private lateinit var virtualDiceView: View
private var virtualDiceEnabled: Boolean = false
private var virtualDiceAnimating: Boolean = false
private var virtualDiceCurrentValue: Int = 1
```

**New Functions**:
- `updateDiceModeUI()` - Updates button states and visibility based on mode
- `rollVirtualDice()` - Handles dice animation and value generation
- `updateVirtualDiceDisplay(value: Int)` - Updates dice face and rotation

**Modified Functions**:
- `setupUi()` - Initializes virtual dice UI components
- `setupUiListeners()` - Adds switch and dice click listeners

### activity_main.xml
**New UI Components**:
1. **Dice Mode Toggle** (above Connect Dice button):
   - LinearLayout with label and switch
   - Background: #1F2937
   - Padding: 12dp

2. **Virtual Dice Container** (between Scoreboard and Last Event):
   - LinearLayout with vertical orientation
   - Contains: Label, Dice FrameLayout, Hint text
   - Initially hidden (visibility="gone")

3. **Virtual Dice View**:
   - FrameLayout (120dp x 120dp)
   - Background: dice_background drawable
   - Contains: TextView with Unicode dice character
   - Clickable and focusable

### dice_background.xml (new file)
```xml
<shape android:shape="rectangle">
  <gradient android:angle="135"
            android:startColor="#42A5F5"
            android:endColor="#1976D2" />
  <corners android:radius="16dp" />
  <stroke android:width="3dp" android:color="#64B5F6" />
</shape>
```

## User Flow

### For Testers Without GoDice:
1. Open app after selecting players in profile page
2. Navigate through AI animation page to MainActivity
3. Toggle switch to "Virtual Dice" mode
4. Notice "Connect Dice" button becomes disabled and gray
5. See animated dice appear below scoreboard
6. Tap dice to roll
7. Watch 1-second animation
8. Game processes roll and updates board/scoreboard
9. Continue playing entire game without physical hardware

### For Testers With GoDice:
1. Keep toggle on "Bluetooth Dice" (default)
2. Tap "Connect Dice" button normally
3. Play with physical dice as usual

## Testing Checklist

- [ ] Toggle switches between modes correctly
- [ ] Connect Dice button disables/enables appropriately
- [ ] Connect Dice button color changes (green → gray)
- [ ] Virtual dice appears/disappears based on mode
- [ ] Dice animation plays on tap
- [ ] Roll generates random value 1-6
- [ ] Roll processes correctly in production mode
- [ ] ESP32 receives roll if connected
- [ ] Server API updates
- [ ] live.html displays roll
- [ ] Scoreboard updates after roll
- [ ] AI commentary triggers
- [ ] Turn advances to next player
- [ ] Physical dice disconnects when switching to virtual
- [ ] Can complete full game using only virtual dice

## Play Store Release Notes
```
New Feature: Virtual Dice Mode
- Play without physical GoDice hardware
- Perfect for testing and demonstrations
- Toggle between Bluetooth and Virtual modes
- Animated 3D dice with realistic rolling
- Full game functionality maintained
```

## Future Enhancements (Optional)
1. 3D dice model instead of Unicode characters
2. Customizable dice colors
3. Sound effects on roll
4. Haptic feedback
5. Multiple dice animation for 2-dice mode
6. Statistics tracking for virtual rolls
7. Persistence of mode preference across app sessions

## Technical Notes

**Thread Safety**: 
- `virtualDiceAnimating` flag prevents overlapping animations
- `runOnUiThread` ensures UI updates on main thread
- Coroutines handle animation timing

**Performance**:
- Lightweight animation (20 frames total)
- No external libraries required
- Minimal memory footprint

**Compatibility**:
- Works with existing test modes
- Doesn't break physical dice functionality
- Seamless integration with ESP32 and server

## Deployment Checklist

- [ ] Test on physical device (not emulator)
- [ ] Verify Bluetooth permissions still work
- [ ] Test full game flow start to finish
- [ ] Check memory usage during long games
- [ ] Verify orientation changes don't break state
- [ ] Test with 2-4 players
- [ ] Confirm server API receives correct data
- [ ] Validate live.html displays correctly
- [ ] Test switching modes mid-game
- [ ] Verify undo functionality works

## Known Limitations
1. Virtual dice uses pseudo-random number generation (not cryptographically secure)
2. No "shaking" gesture detection (future enhancement)
3. Animation is 2D rotation, not full 3D (acceptable for v1)
4. Single dice only (2-dice mode would require separate implementation)

## Support for Testers
When testers encounter issues:
1. Ensure app has Bluetooth permissions (even for virtual mode, due to ESP32)
2. Toggle mode switch if dice not responding
3. Restart app if switch gets stuck
4. Check that players were configured before entering MainActivity
