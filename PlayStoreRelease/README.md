# Last Drop - Play Store Release Build

## Files

- **LastDrop-v1.0-release.aab** (9.7 MB) - Android App Bundle for Play Store upload
- **LastDrop-v1.0-release.apk** (10.49 MB) - Signed APK for direct installation/testing

## App Details

- **Package Name**: `earth.lastdrop.app`
- **Version Code**: 1
- **Version Name**: 1.0
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Upload to Google Play Console (Internal Testing)

### Step 1: Create App in Play Console
1. Go to https://play.google.com/console
2. Click "Create app"
3. Enter app details:
   - **App name**: Last Drop
   - **Default language**: English (US)
   - **App or game**: Game
   - **Free or paid**: Free

### Step 2: Set Up Internal Testing Track
1. In left menu, go to **Testing** → **Internal testing**
2. Click "Create new release"
3. Upload `LastDrop-v1.0-release.aab`
4. Add release notes (e.g., "Initial internal testing build")
5. Click "Save" then "Review release"

### Step 3: Add Testers
1. In Internal testing page, go to "Testers" tab
2. Create email list with tester emails
3. Copy the opt-in URL
4. Share URL with testers

### Step 4: Fill Required Store Listing
Before you can publish (even to internal testing), you need:

**Main Store Listing**:
- App name: Last Drop
- Short description: Water conservation board game with GoDice and ESP32
- Full description: (Copy from your marketing materials)
- App icon: 512x512 PNG
- Feature graphic: 1024x500 PNG
- Screenshots: At least 2 phone screenshots (16:9 or 9:16 ratio)

**App Content**:
- Privacy policy URL (if collecting user data)
- App category: Games → Board
- Content rating questionnaire
- Target audience age selection

### Step 5: Review and Publish to Internal Testing
1. Complete all required sections
2. Click "Send X changes for review"
3. Review can take a few hours to a few days
4. Once approved, testers can install via the opt-in URL

## Testing the APK Directly

You can also test the APK directly on devices:

```bash
# Install via ADB
adb install -r LastDrop-v1.0-release.apk

# Or share the APK file and enable "Install from Unknown Sources" on device
```

## Signing Information

**Keystore**: `lastdrop-release.keystore` (in project root)
- **Alias**: lastdrop
- **Store Password**: Lastdrop2025!
- **Key Password**: Lastdrop2025!

⚠️ **IMPORTANT**: Keep the keystore file safe! You'll need it for all future updates.

## Next Steps for Production Release

1. Complete internal testing
2. Add more detailed screenshots and graphics
3. Create closed testing track with more testers
4. Fix any reported bugs
5. Open testing or production release

## Build Commands

To rebuild:
```bash
# APK
./gradlew assembleRelease

# AAB (for Play Store)
./gradlew bundleRelease
```

Files will be in:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
