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

Here are **curated Lottie animations** with direct URLs ready to use:

### Cloudie (Water Conservation Mascot)

```kotlin
// Copy these into EmoteManager.kt

// 1. CLOUDIE_IDLE - Gentle floating cloud
const val CLOUDIE_IDLE = "https://lottie.host/4db68bbd-31f6-4cd8-84eb-189de081159a/IGmMCqhzpt.lottie"
// Alternative: https://assets5.lottiefiles.com/packages/lf20_nqoewpqn.json

// 2. CLOUDIE_SPEAKING - Talking/mouth animation
const val CLOUDIE_SPEAKING = "https://assets2.lottiefiles.com/packages/lf20_khtt8ghn.json"
// Alternative: https://lottie.host/embed/d5e3f8d0-9c8e-4a1e-8a8e-5d6f7e8f9a0b/animation.json

// 3. CLOUDIE_CELEBRATE - Happy celebration
const val CLOUDIE_CELEBRATE = "https://assets9.lottiefiles.com/packages/lf20_u4yrau.json"
// Alternative: https://assets5.lottiefiles.com/packages/lf20_5njp3vgg.json (confetti celebration)

// 4. CLOUDIE_WARNING - Alert/warning shake
const val CLOUDIE_WARNING = "https://assets4.lottiefiles.com/packages/lf20_zrqthn6o.json"
// Alternative: https://assets5.lottiefiles.com/packages/lf20_w51pcehl.json (exclamation mark)

// 5. CLOUDIE_SAD - Crying/sad expression
const val CLOUDIE_SAD = "https://assets10.lottiefiles.com/packages/lf20_kyu7xb1v.json"
// Alternative: https://assets2.lottiefiles.com/private_files/lf30_bn5winlb.json

// 6. CLOUDIE_THINKING - Pondering with thought bubble
const val CLOUDIE_THINKING = "https://assets4.lottiefiles.com/packages/lf20_touohxv0.json"
// Alternative: https://assets9.lottiefiles.com/packages/lf20_myejiggj.json

// 7. CLOUDIE_EXCITED - Energetic bouncing
const val CLOUDIE_EXCITED = "https://assets3.lottiefiles.com/packages/lf20_pwohahvd.json"
// Alternative: https://assets5.lottiefiles.com/packages/lf20_ztasojjb.json (star sparkles)
```

### Drops (Player Tokens)

```kotlin
// 8. DROP_IDLE - Water drop wobbling
const val DROP_IDLE = "https://assets8.lottiefiles.com/packages/lf20_wqypcrse.json"
// Alternative: https://assets6.lottiefiles.com/packages/lf20_nqoewpqn.json

// 9. DROP_ROLLING - Spinning/rolling dice
const val DROP_ROLLING = "https://assets4.lottiefiles.com/packages/lf20_5tl1xxnz.json"
// Alternative: https://assets9.lottiefiles.com/packages/lf20_0bqhg9km.json (spinning coin)

// 10. DROP_MOVING - Small hop/jump forward
const val DROP_MOVING = "https://assets1.lottiefiles.com/packages/lf20_j3UXNf.json"
// Alternative: https://assets7.lottiefiles.com/packages/lf20_yfsxcomf.json

// 11. DROP_WINNING - Victory with confetti
const val DROP_WINNING = "https://assets5.lottiefiles.com/packages/lf20_obhph3sh.json"
// Alternative: https://assets9.lottiefiles.com/packages/lf20_xlkxtmul.json (trophy celebration)

// 12. DROP_LOSING - Sad deflating
const val DROP_LOSING = "https://assets10.lottiefiles.com/packages/lf20_9wpyhdzo.json"
// Alternative: https://assets1.lottiefiles.com/packages/lf20_ztasojjb.json

// 13. DROP_ELIMINATED - Fade out/disappear
const val DROP_ELIMINATED = "https://assets2.lottiefiles.com/packages/lf20_svy4ivkc.json"
// Alternative: https://assets8.lottiefiles.com/packages/lf20_dmw9aoaq.json (poof smoke)

// 14. DROP_REVIVED - Power up glow/appear
const val DROP_REVIVED = "https://assets4.lottiefiles.com/packages/lf20_tll0j84z.json"
// Alternative: https://assets6.lottiefiles.com/packages/lf20_myejiggj.json (sparkle glow)
```

---

## Quick Copy-Paste Setup

**Option 1: Use all primary URLs** (fastest)
```kotlin
const val CLOUDIE_IDLE = "https://lottie.host/4db68bbd-31f6-4cd8-84eb-189de081159a/IGmMCqhzpt.lottie"
const val CLOUDIE_SPEAKING = "https://assets2.lottiefiles.com/packages/lf20_khtt8ghn.json"
const val CLOUDIE_CELEBRATE = "https://assets9.lottiefiles.com/packages/lf20_u4yrau.json"
const val CLOUDIE_WARNING = "https://assets4.lottiefiles.com/packages/lf20_zrqthn6o.json"
const val CLOUDIE_SAD = "https://assets10.lottiefiles.com/packages/lf20_kyu7xb1v.json"
const val CLOUDIE_THINKING = "https://assets4.lottiefiles.com/packages/lf20_touohxv0.json"
const val CLOUDIE_EXCITED = "https://assets3.lottiefiles.com/packages/lf20_pwohahvd.json"

const val DROP_IDLE = "https://assets8.lottiefiles.com/packages/lf20_wqypcrse.json"
const val DROP_ROLLING = "https://assets4.lottiefiles.com/packages/lf20_5tl1xxnz.json"
const val DROP_MOVING = "https://assets1.lottiefiles.com/packages/lf20_j3UXNf.json"
const val DROP_WINNING = "https://assets5.lottiefiles.com/packages/lf20_obhph3sh.json"
const val DROP_LOSING = "https://assets10.lottiefiles.com/packages/lf20_9wpyhdzo.json"
const val DROP_ELIMINATED = "https://assets2.lottiefiles.com/packages/lf20_svy4ivkc.json"
const val DROP_REVIVED = "https://assets4.lottiefiles.com/packages/lf20_tll0j84z.json"
```

**Option 2: Preview before using**

Visit these pages to see animations in action:
- Search: https://lottiefiles.com/search?q=cloud
- Search: https://lottiefiles.com/search?q=water+drop
- Search: https://lottiefiles.com/search?q=celebration
- Search: https://lottiefiles.com/search?q=thinking

---

## Alternative Sources (If URLs Break)

**Free Lottie Packs:**
- https://lottiefiles.com/featured - Featured animations
- https://lottiefiles.com/free - Free marketplace
- https://iconscout.com/lottie-animations - IconScout Lottie

**Custom Animated Characters:**
- Search: "water mascot"
- Search: "game character idle"
- Search: "emoji reactions"

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
