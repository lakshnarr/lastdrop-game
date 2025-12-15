# Sound Effects Integration Roadmap

## Overview

Enhance Last Drop with **dynamic sound effects** using ElevenLabs Sound Effects API. Generate realistic sounds from text descriptions to create immersive game atmosphere.

## Current State

✅ **Voice System Complete**
- `HybridVoiceService` handles Cloudie speech (ElevenLabs TTS + fallback)
- MP3 caching prevents redundant API calls
- Settings UI for voice configuration

❌ **Sound Effects**
- No sound effects implementation
- Only voice audio exists

## Vision

Create immersive audio experience with:
- **Tile landing sounds** (splash, clank, whoosh)
- **Chance card effects** (magic chime, thunder, coins jingling)
- **Game events** (dice roll, victory fanfare, elimination sound)
- **Ambient atmosphere** (crowd murmur, wind, water dripping)

## ElevenLabs Sound Effects API

### Endpoint
```
POST https://api.elevenlabs.io/v1/sound-generation
Headers:
  xi-api-key: YOUR_API_KEY
  Content-Type: application/json

Body:
{
  "text": "dice rolling on wooden table",
  "duration_seconds": 2.5,
  "prompt_influence": 0.5
}

Response: audio/mpeg (MP3 file)
```

### Parameters
- **text**: Description of sound (e.g., "water splashing")
- **duration_seconds**: Length (0.5 - 22 seconds)
- **prompt_influence**: How closely to match prompt (0.0 - 1.0)

### Cost
- **Free tier**: 10,000 characters/month (shared with voice TTS)
- **Paid tier**: $5/month = 30K characters
- Sound effect descriptions typically 10-50 characters each

## Architecture Plan

### Phase 1: Service Layer (Week 1)

**1.1 Create `SoundEffectsService.kt`**
```kotlin
class SoundEffectsService(private val apiKey: String) {
    suspend fun generateSound(
        description: String,
        duration: Float = 1.5f,
        promptInfluence: Float = 0.5f
    ): File?
    
    fun validateApiKey(): Boolean
}
```

**1.2 Extend `VoiceSettings.kt`**
```kotlin
data class VoiceSettings(
    // Existing fields...
    val soundEffectsEnabled: Boolean = true,
    val soundEffectsVolume: Float = 0.7f,
    val useDynamicSounds: Boolean = false // false = use presets
)
```

**1.3 Create `SoundEffectsManager.kt`**
```kotlin
class SoundEffectsManager(
    context: Context,
    settings: VoiceSettings
) {
    private val soundCache = mutableMapOf<String, File>()
    private val mediaPlayer = MediaPlayer()
    
    suspend fun playEffect(description: String, volume: Float = 0.7f)
    fun preloadEffects(descriptions: List<String>)
    fun clearCache()
}
```

### Phase 2: Sound Effect Catalog (Week 1)

**2.1 Define Sound Categories**

Create `SoundEffectCatalog.kt`:
```kotlin
object SoundEffectCatalog {
    // Tile landing sounds
    const val WATER_SPLASH = "water splashing into pool"
    const val METAL_CLANG = "metal coin dropping on steel"
    const val WOOD_THUD = "wooden token landing on board"
    const val MAGIC_CHIME = "magical bell chime"
    
    // Chance cards
    const val CARD_FLIP = "card being flipped on table"
    const val THUNDER = "distant thunder rumble"
    const val COINS_JINGLE = "gold coins jingling in pouch"
    const val CROWD_CHEER = "small crowd cheering"
    const val CROWD_GROAN = "disappointed crowd groan"
    
    // Dice sounds
    const val DICE_ROLL = "dice rolling on wooden table"
    const val DICE_SETTLE = "dice landing and settling"
    
    // Game events
    const val VICTORY_FANFARE = "short victory trumpet fanfare"
    const val PLAYER_ELIMINATED = "sad trombone sound"
    const val GAME_START = "starting bell ring"
    const val UNDO_WHOOSH = "quick whoosh rewind sound"
    
    // Ambient atmosphere
    const val CROWD_MURMUR = "distant crowd murmuring quietly"
    const val WIND_SOFT = "gentle wind blowing"
    const val WATER_DRIP = "water dripping in cave"
}
```

**2.2 Tile-Specific Sounds**

Map each tile type to sound:
```kotlin
fun getTileLandingSound(tile: Tile): String = when (tile.type) {
    TileType.WATERHOLE -> SoundEffectCatalog.WATER_SPLASH
    TileType.MIRAGE -> SoundEffectCatalog.MAGIC_CHIME
    TileType.QUICKSAND -> SoundEffectCatalog.WOOD_THUD
    TileType.OASIS -> SoundEffectCatalog.WATER_SPLASH
    TileType.CHANCE -> SoundEffectCatalog.CARD_FLIP
    else -> SoundEffectCatalog.WOOD_THUD
}
```

### Phase 3: UI Integration (Week 2)

**3.1 Extend `VoiceSettingsDialog.kt`**

Add sound effects section:
```kotlin
// Sound Effects Section (in dialog)
- Enable Sound Effects toggle
- Volume slider (0-100%)
- "Use Dynamic Sounds" toggle
  * ON: Generate from ElevenLabs API
  * OFF: Use preset library (faster, offline-capable)
- "Preload All Sounds" button
- Cache size display
```

**3.2 Settings UI Layout**
```
🎙️ Voice Settings
├─ Enable Voice [toggle]
├─ ElevenLabs (Premium Voice)
│  └─ [existing voice settings]
├─ Phone TTS Fallback
│  └─ [existing TTS settings]
└─ 🔊 Sound Effects (NEW)
   ├─ Enable Sound Effects [toggle]
   ├─ Volume [slider: 0-100%]
   ├─ Use Dynamic Sounds [toggle]
   │  └─ "Generates unique sounds via API (uses quota)"
   ├─ Preload All Sounds [button]
   │  └─ Shows: "24/24 effects cached (12.3 MB)"
   └─ Clear Cache [button]
```

### Phase 4: Game Integration (Week 2-3)

**4.1 MainActivity Integration Points**

```kotlin
class MainActivity {
    private lateinit var soundEffectsManager: SoundEffectsManager
    
    // Initialize in onCreate
    soundEffectsManager = SoundEffectsManager(this, voiceSettings)
    
    // Preload common sounds
    lifecycleScope.launch {
        soundEffectsManager.preloadEffects(listOf(
            SoundEffectCatalog.DICE_ROLL,
            SoundEffectCatalog.WATER_SPLASH,
            SoundEffectCatalog.CARD_FLIP
        ))
    }
}
```

**4.2 Dice Roll Integration**

```kotlin
// In onDiceRoll callback (while rolling)
lifecycleScope.launch {
    soundEffectsManager.playEffect(SoundEffectCatalog.DICE_ROLL)
}

// In onDiceStable callback (when settled)
lifecycleScope.launch {
    soundEffectsManager.playEffect(SoundEffectCatalog.DICE_SETTLE)
}
```

**4.3 Tile Landing Integration**

```kotlin
// In processTurn() after ESP32 confirms coin placement
val turnResult = gameEngine.processTurn(player.position, diceValue)
lifecycleScope.launch {
    val sound = getTileLandingSound(turnResult.tile)
    soundEffectsManager.playEffect(sound)
}
```

**4.4 Chance Card Integration**

```kotlin
// When chance card applied
when (chanceCard.effect) {
    ChanceEffect.GAIN_POINTS -> {
        soundEffectsManager.playEffect(SoundEffectCatalog.COINS_JINGLE)
    }
    ChanceEffect.LOSE_POINTS -> {
        soundEffectsManager.playEffect(SoundEffectCatalog.THUNDER)
    }
    ChanceEffect.SKIP_TURN -> {
        soundEffectsManager.playEffect(SoundEffectCatalog.CROWD_GROAN)
    }
    ChanceEffect.ADVANCE -> {
        soundEffectsManager.playEffect(SoundEffectCatalog.MAGIC_CHIME)
    }
}
```

**4.5 Game Event Integration**

```kotlin
// Game start
soundEffectsManager.playEffect(SoundEffectCatalog.GAME_START)

// Player eliminated
soundEffectsManager.playEffect(SoundEffectCatalog.PLAYER_ELIMINATED)

// Victory
soundEffectsManager.playEffect(SoundEffectCatalog.VICTORY_FANFARE)

// Undo action
soundEffectsManager.playEffect(SoundEffectCatalog.UNDO_WHOOSH)
```

### Phase 5: Advanced Features (Week 3-4)

**5.1 Dynamic Sound Generation**

Allow custom sounds based on game context:
```kotlin
// Dynamic descriptions
val dynamicSound = when {
    player.score > 50 -> "triumphant brass fanfare"
    player.score < 10 -> "sad lonely violin"
    else -> "neutral drum beat"
}
soundEffectsManager.playEffect(dynamicSound)
```

**5.2 Ambient Soundscapes**

Background atmosphere during gameplay:
```kotlin
// Start ambient loop
soundEffectsManager.startAmbient(
    sound = SoundEffectCatalog.CROWD_MURMUR,
    volume = 0.3f,
    loop = true
)

// Stop when game ends
soundEffectsManager.stopAmbient()
```

**5.3 Sound Mixing**

Play multiple sounds simultaneously:
```kotlin
// Example: Dice roll + Cloudie speech
lifecycleScope.launch {
    launch { soundEffectsManager.playEffect(SoundEffectCatalog.DICE_ROLL) }
    launch { voiceService.speak("Rolling the dice!") }
}
```

**5.4 Preset Sound Library**

Include local MP3s for offline play:
```
app/src/main/res/raw/
├── dice_roll.mp3
├── water_splash.mp3
├── card_flip.mp3
├── victory_fanfare.mp3
└── ...
```

Toggle between:
- **Dynamic (ElevenLabs)**: Unique sounds, uses API quota
- **Preset (Local)**: Fast, offline, consistent, no API cost

## Implementation Priority

### Must-Have (MVP)
1. ✅ Basic sound effects service
2. ✅ Tile landing sounds (5-6 types)
3. ✅ Dice roll sounds
4. ✅ Victory/elimination sounds
5. ✅ Settings UI (enable/disable, volume)

### Nice-to-Have (V2)
6. ⭐ Chance card sounds (10+ types)
7. ⭐ Ambient soundscapes
8. ⭐ Dynamic generation based on context
9. ⭐ Preset library for offline mode

### Future Enhancements (V3+)
10. 🔮 Custom sound uploads
11. 🔮 Sound effect mixer (layer multiple sounds)
12. 🔮 3D positional audio (if VR/AR mode added)
13. 🔮 Community sound library

## API Usage Estimation

### Typical Game Session
```
Game start: 1 sound
Dice rolls: 20-30 sounds per game (3-5 per player)
Tile landings: 20-30 sounds
Chance cards: 5-10 sounds
Game end: 1 sound

Total: ~50-70 sound effects per game
```

### Character Usage
```
Average description: 30 characters
50 sounds × 30 chars = 1,500 characters per game
10,000 chars/month ÷ 1,500 = ~6-7 games/month (free tier)
```

### With Caching
```
First game: 50 unique sounds = 1,500 chars
Subsequent games: 0 chars (all cached)
Real usage: ~1,500-3,000 chars/month with caching
```

**Recommendation**: Use caching + preset library for common sounds. Reserve ElevenLabs for dynamic/contextual sounds.

## Testing Plan

### Unit Tests
- `SoundEffectsService.generateSound()` returns valid MP3
- `SoundEffectsManager.playEffect()` plays cached sound
- Cache management (size limits, LRU eviction)

### Integration Tests
1. Generate all catalog sounds → verify quality
2. Play sound during dice roll → verify timing
3. Simultaneous sound + voice → verify no audio conflict
4. Test with/without internet → verify fallback to presets

### User Acceptance Tests
1. Enable sound effects in settings
2. Roll dice → hear dice sound
3. Land on waterhole → hear splash
4. Draw chance card → hear card flip
5. Win game → hear victory fanfare
6. Disable sounds → verify silence

## File Structure After Implementation

```
app/src/main/java/earth/lastdrop/app/
├── voice/
│   ├── VoiceSettings.kt              # Extended with sound settings
│   ├── ElevenLabsService.kt          # Existing voice service
│   ├── SoundEffectsService.kt        # NEW: Sound generation API
│   ├── SoundEffectsManager.kt        # NEW: Sound playback + cache
│   └── SoundEffectCatalog.kt         # NEW: Predefined sounds
├── VoiceSettingsDialog.kt            # Extended with sound UI
├── MainActivity.kt                   # Integrated sound triggers
└── ...

app/src/main/res/raw/                 # NEW: Preset sound library
├── dice_roll.mp3
├── water_splash.mp3
├── victory_fanfare.mp3
└── ...
```

## API Key Configuration

Update ElevenLabs API key permissions:

```
✓ Text to Speech: Access        (for voice)
✓ Sound Effects: Access          (NEW - for sounds)
✓ User: Read                     (for validation)

All others: No Access
```

## Cost Optimization Strategies

1. **Aggressive Caching**: Store all generated sounds permanently
2. **Preset Library**: Include 20-30 common sounds as local files
3. **Smart Generation**: Only use API for rare/dynamic sounds
4. **Batch Preloading**: Generate all sounds during first setup
5. **Offline Mode**: Fall back to presets when quota exceeded

## Timeline

### Week 1: Foundation
- Create `SoundEffectsService.kt`
- Create `SoundEffectsManager.kt`
- Define `SoundEffectCatalog.kt`
- Test API integration

### Week 2: Integration
- Extend `VoiceSettingsDialog.kt`
- Integrate dice roll sounds
- Integrate tile landing sounds
- Basic testing

### Week 3: Enhancement
- Add chance card sounds
- Implement sound mixing
- Create preset library
- Performance optimization

### Week 4: Polish
- User testing
- Bug fixes
- Documentation
- Release

## Success Metrics

- ✅ 95%+ sound generation success rate
- ✅ <500ms latency for cached sounds
- ✅ <2s latency for API-generated sounds
- ✅ <50MB total cache size
- ✅ Zero audio conflicts with voice
- ✅ Positive user feedback on immersion

## Future Considerations

- **Spatial Audio**: Pan sounds based on player position
- **Haptic Feedback**: Vibration patterns matching sounds
- **Music Tracks**: Background music during gameplay
- **User Uploads**: Custom sound effects from community
- **Sound Themes**: Different sound packs (medieval, sci-fi, etc.)

## Next Steps

1. **Update ElevenLabs API key** → Enable "Sound Effects: Access"
2. **Create `SoundEffectsService.kt`** → Implement sound generation
3. **Test single sound** → Verify API works
4. **Extend settings UI** → Add sound effects controls
5. **Integrate dice sounds** → First user-facing feature
6. **Iterate based on feedback** → Expand sound library

---

**Let's make Last Drop sound as good as it looks!** 🎵
