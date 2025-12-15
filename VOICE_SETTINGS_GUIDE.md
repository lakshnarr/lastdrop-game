# Voice Settings System

## Overview

The Last Drop app now features a comprehensive voice settings system with **ElevenLabs premium TTS** as primary voice provider and **phone TTS as fallback**. This gives Cloudie (AI game master) a high-quality, emotional UK-accented voice.

## Architecture

### Components

1. **VoiceSettings.kt** - Data model and persistence
   - `VoiceSettings` data class with all configuration
   - `VoiceSettingsManager` for SharedPreferences storage

2. **ElevenLabsService.kt** - Premium TTS API client
   - Uses OkHttp for API calls
   - Endpoint: `https://api.elevenlabs.io/v1/text-to-speech/{voiceId}`
   - Default voice: "Adam" (British male, ID: pNInz6obpgDQGcFmaJgB)
   - Validates API keys via `/user` endpoint
   - Returns MP3 audio files

3. **HybridVoiceService.kt** - Orchestrator
   - **Primary**: Tries ElevenLabs first if enabled
   - **Fallback**: Uses phone TTS if ElevenLabs fails/disabled
   - **Caching**: Stores MP3 files by text hash (avoids redundant API calls)
   - **Playback**: Uses MediaPlayer for audio files

4. **VoiceSettingsDialog.kt** - User interface
   - Settings icon (⚙️) in ProfileSelectionActivity
   - Enable/disable voice toggle
   - ElevenLabs section (API key, voice ID, test button)
   - TTS fallback section (pitch, speech rate sliders)

### Data Flow

```
User speaks → Cloudie responds → HybridVoiceService
                                        ↓
                                 ElevenLabs enabled?
                                   ↙          ↘
                              YES                NO
                                ↓                 ↓
                         Check cache         Use TTS
                              ↓                 ↓
                         Cache hit?      Play with TTS
                           ↙    ↘               
                       YES      NO              
                        ↓        ↓               
                    Play MP3   API call         
                                ↓                
                            Success?             
                             ↙    ↘              
                         YES      NO             
                          ↓        ↓              
                      Cache & Play  Fall back to TTS
```

## Settings Configuration

### Voice Settings Fields

```kotlin
data class VoiceSettings(
    val voiceEnabled: Boolean = true,           // Master toggle
    val useElevenLabs: Boolean = false,         // Enable premium TTS
    val elevenLabsApiKey: String = "",          // Your API key
    val elevenLabsVoiceId: String = "pNInz6obpgDQGcFmaJgB",  // Voice ID
    val ttsPitch: Float = 1.1f,                 // TTS pitch (0.5-2.0)
    val ttsSpeechRate: Float = 0.95f            // TTS rate (0.5-2.0)
)
```

### Accessing Settings UI

1. Launch app → Profile Selection screen
2. Tap settings icon (⚙️) in top-right corner
3. Configure voice settings:
   - Toggle voice on/off
   - Enable ElevenLabs (requires API key)
   - Enter API key and test it
   - Adjust TTS fallback pitch/rate
4. Tap "Save" to persist settings

## Getting ElevenLabs API Key

1. Go to [elevenlabs.io](https://elevenlabs.io)
2. Sign up for free account (10,000 characters/month free tier)
3. Navigate to Profile → API Keys
4. Copy your API key
5. Paste in app settings
6. Tap "Test API Key" to validate

### Available Voices

Default voice is "Adam" (British male). To use different voices:

1. Browse voices at elevenlabs.io/voice-library
2. Copy voice ID from URL or API
3. Paste in "Voice ID" field in settings

Popular voices:
- **Adam** (pNInz6obpgDQGcFmaJgB) - British male, default
- **Rachel** (21m00Tcm4TlvDq8ikWAM) - American female
- **Clyde** (2EiwWnXFnvU5JabPnv8n) - American male
- **Antoni** (ErXwobaYiN019PkySvjV) - American male, calm

## Integration Points

### MainActivity Changes

```kotlin
// Old (direct TTS)
voiceService = TextToSpeechVoiceService(this, ...)

// New (hybrid with ElevenLabs)
val voiceSettingsManager = VoiceSettingsManager(this)
val voiceSettings = voiceSettingsManager.getSettings()
voiceService = HybridVoiceService(this, voiceSettings, ...)
```

### ProfileSelectionActivity Changes

- Added settings icon button (⚙️) next to title
- Launches `VoiceSettingsDialog` on click
- Uses `lifecycleScope` for async operations

## Behavior

### When ElevenLabs Enabled

1. User triggers Cloudie speech
2. System checks `audioCache` map for cached MP3
3. **Cache hit**: Plays MP3 via MediaPlayer immediately
4. **Cache miss**: Calls ElevenLabs API
   - Success: Saves MP3 to cache, plays audio
   - Failure: Falls back to phone TTS

### When ElevenLabs Disabled

- Always uses phone TTS
- Uses configured pitch (1.1f) and speech rate (0.95f)
- Locale set to `Locale.UK` for British accent

### Voice Disabled

- `voiceEnabled = false` → Cloudie remains silent
- Game commentary shows in chat/log only

## Testing

### Test ElevenLabs Integration

1. Get API key from elevenlabs.io
2. Open settings in app
3. Enable "Use ElevenLabs"
4. Enter API key
5. Tap "Test API Key" → should show "✓ Test API Key" and toast "API key is valid!"
6. Tap "Save"
7. Start game with Cloudie enabled
8. Roll dice → Cloudie should speak with high-quality voice
9. Check logcat: `🔊 Cloudie voice ready (ElevenLabs + TTS)`

### Test Fallback Behavior

1. Enable ElevenLabs with invalid/expired API key
2. Trigger Cloudie speech
3. First utterance → ElevenLabs fails → auto-falls back to TTS
4. Subsequent utterances → uses TTS directly (no repeated API failures)

### Test Caching

1. Trigger same phrase twice (e.g., "Welcome to Last Drop!")
2. First time: API call + slight delay
3. Second time: Instant playback from cache
4. Cache location: In-memory `audioCache` map (cleared on app restart)

## API Rate Limits

**Free Tier**: 10,000 characters/month

Typical usage:
- Average phrase: 50 characters
- ≈ 200 phrases/month on free tier
- 5-10 phrases per game
- ≈ 20-40 games/month

**Paid Tier**: Starts at $5/month for 30,000 characters

## Troubleshooting

### "Invalid API key" error

- Check key copied correctly (no extra spaces)
- Verify account active at elevenlabs.io
- Check internet connection

### ElevenLabs not working, TTS plays instead

- Normal behavior if API fails
- Check logcat for errors: `Voice error: ...`
- Verify API key in settings
- Check rate limits not exceeded

### Voice sounds robotic/not emotional

- Using phone TTS instead of ElevenLabs
- Verify "Use ElevenLabs" enabled in settings
- Check API key valid
- Ensure internet connection active

### No voice at all

- Check "Voice Enabled" toggle in settings
- Verify voiceService initialized (logcat: "Cloudie voice ready")
- Check device volume and media output

## Future Enhancements

Potential improvements:
- **Voice preview** in settings (test phrase before saving)
- **Persistent cache** (save MP3s to disk, survive app restart)
- **Multiple voice IDs** (different voices for different game events)
- **Emotion control** (stability/similarity sliders exposed in UI)
- **Offline mode** (download voices for offline play)
- **Voice gender/accent selection** (preset voices by category)

## File Locations

```
app/src/main/java/earth/lastdrop/app/
├── voice/
│   ├── VoiceSettings.kt          # Data model + manager
│   ├── ElevenLabsService.kt      # API client
│   ├── VoiceService.kt           # HybridVoiceService + TextToSpeechVoiceService
├── VoiceSettingsDialog.kt        # Settings UI
├── ProfileSelectionActivity.kt   # Settings icon integration
└── MainActivity.kt               # HybridVoiceService usage
```

## Dependencies

- **OkHttp 4.12.0** - HTTP client for ElevenLabs API (already in build.gradle)
- **MediaPlayer** - Built-in Android audio playback
- **Coroutines** - Async API calls without blocking UI
- **SharedPreferences** - Settings persistence

No additional dependencies required! ✅
