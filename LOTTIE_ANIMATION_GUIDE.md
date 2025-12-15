# Lottie Animation Download Guide

## Required Animations from LottieFiles.com

Visit [lottiefiles.com](https://lottiefiles.com) and search for the following animations. Download as **Lottie JSON** format.

---

## Cloudie Animations (7 files)

### 1. **cloudie_idle.json**
- **Search**: "cloud character idle" OR "cute cloud breathing"
- **Style**: Gentle floating/breathing animation, loop-friendly
- **Duration**: 2-4 seconds loop
- **Save as**: `app/src/main/res/raw/cloudie_idle.json`

### 2. **cloudie_speaking.json**
- **Search**: "character talking" OR "mouth animation"
- **Style**: Subtle mouth movement, loop-friendly
- **Duration**: 1-2 seconds loop
- **Save as**: `app/src/main/res/raw/cloudie_speaking.json`

### 3. **cloudie_celebrate.json**
- **Search**: "celebration jump" OR "happy dance character"
- **Style**: Joyful bouncing/dancing, can be one-shot
- **Duration**: 2-3 seconds
- **Save as**: `app/src/main/res/raw/cloudie_celebrate.json`

### 4. **cloudie_warning.json**
- **Search**: "warning shake" OR "alert character"
- **Style**: Shaking/concerned movement
- **Duration**: 1-2 seconds
- **Save as**: `app/src/main/res/raw/cloudie_warning.json`

### 5. **cloudie_sad.json**
- **Search**: "sad character" OR "crying cloud"
- **Style**: Drooping/crying animation
- **Duration**: 2-3 seconds
- **Save as**: `app/src/main/res/raw/cloudie_sad.json`

### 6. **cloudie_thinking.json**
- **Search**: "thinking character" OR "pondering animation"
- **Style**: Hand on chin or question mark above head
- **Duration**: 2-3 seconds loop
- **Save as**: `app/src/main/res/raw/cloudie_thinking.json`

### 7. **cloudie_excited.json**
- **Search**: "excited bounce" OR "energetic character"
- **Style**: Fast bouncing/vibrating with energy
- **Duration**: 1-2 seconds loop
- **Save as**: `app/src/main/res/raw/cloudie_excited.json`

---

## Drop Animations (7 files)

### 8. **drop_idle.json**
- **Search**: "water drop idle" OR "droplet wobble"
- **Style**: Subtle wobble/breathing, loop-friendly
- **Duration**: 2-3 seconds loop
- **Save as**: `app/src/main/res/raw/drop_idle.json`

### 9. **drop_rolling.json**
- **Search**: "rolling dice" OR "spinning object"
- **Style**: Fast spinning/rotating animation
- **Duration**: 1-2 seconds loop
- **Save as**: `app/src/main/res/raw/drop_rolling.json`

### 10. **drop_moving.json**
- **Search**: "hopping forward" OR "jump animation"
- **Style**: Small hop from left to right
- **Duration**: 0.5-1 second one-shot
- **Save as**: `app/src/main/res/raw/drop_moving.json`

### 11. **drop_winning.json**
- **Search**: "victory celebration" OR "confetti character"
- **Style**: Celebratory jump with sparkles/confetti
- **Duration**: 2-3 seconds
- **Save as**: `app/src/main/res/raw/drop_winning.json`

### 12. **drop_losing.json**
- **Search**: "sad droop" OR "disappointed character"
- **Style**: Deflating/drooping animation
- **Duration**: 1-2 seconds
- **Save as**: `app/src/main/res/raw/drop_losing.json`

### 13. **drop_eliminated.json**
- **Search**: "fade out" OR "disappear animation"
- **Style**: Fading/sinking downward, opacity to 0
- **Duration**: 2-3 seconds one-shot
- **Save as**: `app/src/main/res/raw/drop_eliminated.json`

### 14. **drop_revived.json**
- **Search**: "appear animation" OR "power up glow"
- **Style**: Rising from below with glow effect
- **Duration**: 2-3 seconds one-shot
- **Save as**: `app/src/main/res/raw/drop_revived.json`

---

## Download Instructions

1. **Visit**: https://lottiefiles.com
2. **Search** using keywords above
3. **Select** animation that matches style
4. **Click** "Download" button
5. **Choose** "Lottie JSON" format (NOT After Effects or GIF)
6. **Rename** file to match names above
7. **Save** to `app/src/main/res/raw/` directory

## File Naming Rules

- All lowercase
- Underscores for spaces
- `.json` extension
- No special characters

## Fallback Strategy

If you can't find perfect matches:
- Use generic animations temporarily (e.g., bouncing ball for drop_idle)
- Can replace individual files later without code changes
- System will work with ANY valid Lottie JSON

## Testing After Download

After placing files in `/res/raw/`:
1. Rebuild app: `.\gradlew installDebug`
2. Animations will auto-load in IntroAiActivity
3. Check logcat for loading errors

---

**Total Files Needed**: 14 JSON files  
**Estimated Download Time**: 30-60 minutes  
**Current Status**: Directory created, waiting for files
