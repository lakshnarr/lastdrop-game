# Sound Effects Generation Guide

## Method 1: Using Python Script (Recommended)

### Setup
```powershell
# Install required library
pip install requests

# Edit the script
cd scripts
notepad generate_sound_effects.py

# Replace API_KEY with your actual key
API_KEY = "sk_your_actual_key_here"
```

### Run
```powershell
python generate_sound_effects.py
```

The script will:
1. Generate all 24 sound effects
2. Save MP3 files to `app/src/main/res/raw/`
3. Show progress and file sizes
4. Total time: ~2-3 minutes
5. Total size: ~3-5 MB

---

## Method 2: Using ElevenLabs Web Interface (Manual)

### Step 1: Go to Sound Effects Page
1. Open [elevenlabs.io](https://elevenlabs.io)
2. Login to your account
3. Navigate to **Sound Effects** (in left sidebar)

### Step 2: Generate Each Sound
For each sound in the list below:

1. **Paste description** into text box
2. **Set duration** (seconds)
3. Click **Generate**
4. Wait ~5-10 seconds
5. Click **Download** → saves as `.mp3`
6. **Rename file** to match the name below
7. Move to `app/src/main/res/raw/` folder

### Sound Effects List (24 Total)

#### Dice Sounds (2)
```
dice_roll.mp3 (1.5s)
"wooden dice rolling tumbling on a wooden table"

dice_settle.mp3 (0.8s)
"dice landing and settling with a soft clack"
```

#### Tile Landing Sounds (4)
```
water_splash.mp3 (1.2s)
"water splashing into a pool with gentle ripples"

metal_clang.mp3 (1.0s)
"metal coin dropping and clanging on steel surface"

wood_thud.mp3 (0.8s)
"wooden token landing on board with soft thud"

magic_chime.mp3 (1.5s)
"magical crystal bell chime with shimmer"
```

#### Chance Card Sounds (6)
```
card_flip.mp3 (0.6s)
"playing card being flipped quickly on table"

thunder_rumble.mp3 (2.0s)
"distant thunder rumbling in the sky"

coins_jingle.mp3 (1.5s)
"gold coins jingling and clinking in a leather pouch"

crowd_cheer.mp3 (2.0s)
"small crowd cheering and applauding happily"

crowd_groan.mp3 (1.8s)
"disappointed crowd groaning and sighing"

magic_sparkle.mp3 (1.2s)
"magical sparkles twinkling with fairy dust"
```

#### Game Events (6)
```
victory_fanfare.mp3 (3.0s)
"triumphant brass fanfare with drums, short celebration"

player_eliminated.mp3 (2.0s)
"sad trombone descending notes, comedic failure sound"

game_start.mp3 (1.5s)
"starting bell ring, clear and bright"

undo_whoosh.mp3 (0.8s)
"quick whoosh rewind sound effect"

button_click.mp3 (0.3s)
"soft button click, interface sound"

error_buzz.mp3 (0.5s)
"error buzzer sound, negative feedback"
```

#### Ambient Sounds (3)
```
crowd_murmur.mp3 (5.0s)
"distant crowd murmuring quietly in background"

wind_soft.mp3 (4.0s)
"gentle wind blowing softly through desert"

oasis_ambience.mp3 (5.0s)
"peaceful oasis with gentle water trickling and birds"
```

#### Special Effects (3)
```
quicksand_sink.mp3 (2.0s)
"sinking into quicksand with squelching sounds"

mirage_shimmer.mp3 (1.5s)
"heat mirage shimmering with warbling effect"

sandstorm_gust.mp3 (2.5s)
"sand and wind gusting in desert storm"
```

### Step 3: Organize Files
```
app/src/main/res/raw/
├── dice_roll.mp3
├── dice_settle.mp3
├── water_splash.mp3
├── metal_clang.mp3
├── wood_thud.mp3
├── magic_chime.mp3
├── card_flip.mp3
├── thunder_rumble.mp3
├── coins_jingle.mp3
├── crowd_cheer.mp3
├── crowd_groan.mp3
├── magic_sparkle.mp3
├── victory_fanfare.mp3
├── player_eliminated.mp3
├── game_start.mp3
├── undo_whoosh.mp3
├── button_click.mp3
├── error_buzz.mp3
├── crowd_murmur.mp3
├── wind_soft.mp3
├── oasis_ambience.mp3
├── quicksand_sink.mp3
├── mirage_shimmer.mp3
└── sandstorm_gust.mp3
```

---

## Method 3: Using ElevenLabs API Directly (cURL)

### Single Sound Example
```bash
curl -X POST "https://api.elevenlabs.io/v1/sound-generation" \
  -H "xi-api-key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "wooden dice rolling on a table",
    "duration_seconds": 1.5,
    "prompt_influence": 0.5
  }' \
  --output dice_roll.mp3
```

### PowerShell Version
```powershell
$apiKey = "YOUR_API_KEY"
$headers = @{
    "xi-api-key" = $apiKey
    "Content-Type" = "application/json"
}
$body = @{
    text = "wooden dice rolling on a table"
    duration_seconds = 1.5
    prompt_influence = 0.5
} | ConvertTo-Json

Invoke-RestMethod -Uri "https://api.elevenlabs.io/v1/sound-generation" `
    -Method Post `
    -Headers $headers `
    -Body $body `
    -OutFile "dice_roll.mp3"
```

---

## Verification

After generating all sounds:

### Test Playback
```powershell
# Windows - play MP3
Start-Process "app\src\main\res\raw\dice_roll.mp3"

# Or use VLC/media player
vlc app\src\main\res\raw\dice_roll.mp3
```

### Check File Sizes
```powershell
Get-ChildItem app\src\main\res\raw\*.mp3 | 
    Measure-Object -Property Length -Sum | 
    Select-Object Count, @{Name="TotalMB";Expression={$_.Sum/1MB}}
```

Expected:
- Count: 24 files
- Total: 3-5 MB
- Individual: 50-300 KB each

---

## Tips for Best Quality

### Text Descriptions
- **Be specific**: "wooden dice" not just "dice"
- **Add context**: "rolling on table" gives better sound
- **Use adjectives**: "gentle splash", "loud clang"
- **Avoid ambiguity**: "metal coin" not just "coin"

### Duration
- **Short sounds**: 0.5-1.5s (clicks, thuds)
- **Medium sounds**: 1.5-2.5s (splashes, chimes)
- **Long sounds**: 3-5s (fanfares, ambient)

### Prompt Influence
- **0.3**: More creative, varied results
- **0.5**: Balanced (recommended)
- **0.8**: Closely match description

### If Sound Isn't Right
- Regenerate 2-3 times (API gives variations)
- Adjust description wording
- Try different duration
- Add more descriptive words

---

## Cost

**Using Free Tier (10K chars/month)**:
- 24 sounds × ~40 chars = ~1,000 characters
- Cost: $0
- Leaves 9,000 chars for voice TTS

**If you regenerate sounds**:
- Each regeneration = another ~40 chars
- Budget: Can regenerate each sound ~4-5 times

---

## Next Steps After Generation

1. ✅ Verify all 24 MP3 files exist in `app/src/main/res/raw/`
2. ✅ Test playback quality
3. ✅ Create `SoundEffectsPlayer.kt` class
4. ✅ Integrate into MainActivity
5. ✅ Build APK (sounds packaged automatically)

Sound files will be automatically included in the APK and accessible via `R.raw.dice_roll` etc.
