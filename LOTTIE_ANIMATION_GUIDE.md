# Lottie Animation Setup Guide - URL Method (RECOMMENDED)

## Quick Start - Using LottieFiles URLs

**No downloads needed!** Load animations directly from LottieFiles.com URLs.

### Step 1: Find Animations on LottieFiles.com

Visit [lottiefiles.com](https://lottiefiles.com) and search for animations.

### Step 2: Get the URL

On any animation page:
1. Click the **"</> Embed"** button or **"Lottie URL"**
2. Copy the URL that looks like:
   ```
   https://lottie.host/XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX/YYYYYYYYYY.json
   ```
   OR
   ```
   https://assets5.lottiefiles.com/packages/lf20_XXXXX.json
   ```

### Step 3: Update EmoteManager.kt

Replace placeholder URLs in `EmoteManager.kt` (lines 15-30):

```kotlin
// Cloudie animations
const val CLOUDIE_IDLE = "https://lottie.host/YOUR_URL_HERE.json"
const val CLOUDIE_SPEAKING = "https://lottie.host/YOUR_URL_HERE.json"
// ... etc
```

### Step 4: Rebuild & Test

```powershell
.\gradlew installDebug
```

Animations load automatically - no file management needed!

---

## Recommended Animations

### Cloudie (Water Conservation Mascot)

| Animation | Search Terms | Style | Loop |
|-----------|-------------|-------|------|
| CLOUDIE_IDLE | "cloud character breathing", "cute cloud idle" | Gentle floating | ✅ Yes |
| CLOUDIE_SPEAKING | "character talking mouth", "speaking animation" | Mouth movement | ✅ Yes |
| CLOUDIE_CELEBRATE | "celebration jump", "happy character dance" | Joyful bouncing | ❌ No |
| CLOUDIE_WARNING | "warning shake", "alert character" | Shaking/concerned | ❌ No |
| CLOUDIE_SAD | "sad cloud", "crying character" | Drooping/crying | ❌ No |
| CLOUDIE_THINKING | "thinking character", "pondering" | Hand on chin | ✅ Yes |
| CLOUDIE_EXCITED | "excited bounce", "energetic character" | Fast bouncing | ❌ No |

### Drops (Player Tokens)

| Animation | Search Terms | Style | Loop |
|-----------|-------------|-------|------|
| DROP_IDLE | "water drop idle", "droplet wobble" | Subtle breathing | ✅ Yes |
| DROP_ROLLING | "rolling dice", "spinning object" | Fast rotation | ✅ Yes |
| DROP_MOVING | "hop animation", "jump forward" | Small hop | ❌ No |
| DROP_WINNING | "victory celebration", "confetti character" | Celebratory jump | ❌ No |
| DROP_LOSING | "sad droop", "disappointed" | Deflating | ❌ No |
| DROP_ELIMINATED | "fade out", "disappear animation" | Fading/sinking | ❌ No |
| DROP_REVIVED | "appear", "power up glow" | Rising with glow | ❌ No |

---

## Example Search & URL Copy Process

### Example: Finding CLOUDIE_IDLE

1. Go to https://lottiefiles.com
2. Search: **"cloud character idle"**
3. Select animation you like
4. Click **"</> Embed"** or **"Lottie URL"**
5. Copy URL:
   ```
   https://lottie.host/4db68bbd-31f6-4cd8-84eb-189de081159a/IGmMCqhzpt.json
   ```
6. Open `EmoteManager.kt`
7. Replace line 16:
   ```kotlin
   const val CLOUDIE_IDLE = "https://lottie.host/4db68bbd-31f6-4cd8-84eb-189de081159a/IGmMCqhzpt.json"
   ```
8. Save & rebuild

---

## Advantages of URL Method

✅ **No file management** - no downloads, no res/raw/ folders  
✅ **Easy updates** - change URL anytime  
✅ **Instant preview** - see changes immediately  
✅ **Smaller APK size** - animations loaded on demand  
✅ **Version control friendly** - just text URLs in code  

## Disadvantages

❌ **Requires internet** - won't work offline  
❌ **Slight delay** - first load takes ~500ms  
❌ **External dependency** - relies on LottieFiles hosting  

---

## Fallback to Local Files (Optional)

If you prefer offline animations:
1. Download JSON from LottieFiles
2. Place in `app/src/main/res/raw/`
3. Use `emoteManager.playLocalAnimation(view, "filename")`

---

## Testing Your URLs

After updating URLs, check logcat for:
```
D/EmoteManager: Loading animation from URL: https://lottie.host/... (loop=true)
```

If you see:
```
W/EmoteManager: Placeholder URL detected: ... - using fallback
```
You forgot to replace `YOUR_URL_HERE` placeholders!

---

## Next Steps

1. Find 14 animations on LottieFiles.com
2. Copy their URLs
3. Paste into `EmoteManager.kt` constants
4. Rebuild: `.\gradlew installDebug`
5. Animations appear automatically!

**Estimated Time**: 15-30 minutes (vs 1-2 hours downloading files)
