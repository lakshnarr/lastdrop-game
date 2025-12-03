# Last Drop - Security Setup Script
# Run this after cloning the repository

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Last Drop - Security Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if local.properties already exists
if (Test-Path "local.properties") {
    Write-Host "✓ local.properties already exists" -ForegroundColor Green
    $overwrite = Read-Host "Do you want to reset it? (y/N)"
    if ($overwrite -ne "y") {
        Write-Host "Keeping existing local.properties" -ForegroundColor Yellow
        exit 0
    }
}

# Copy template
if (Test-Path "local.properties.template") {
    Copy-Item "local.properties.template" "local.properties"
    Write-Host "✓ Created local.properties from template" -ForegroundColor Green
} else {
    Write-Host "✗ local.properties.template not found!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Configuration Required" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Prompt for API key
Write-Host "1. API Key Setup" -ForegroundColor Yellow
Write-Host "   Get your API key from: https://lastdrop.earth/dashboard"
$apiKey = Read-Host "   Enter your API key (or press Enter for default 'ABC123')"

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = "ABC123"
    Write-Host "   Using default development key: ABC123" -ForegroundColor Gray
}

# Update local.properties
$content = Get-Content "local.properties"
$content = $content -replace "LASTDROP_API_KEY=.*", "LASTDROP_API_KEY=$apiKey"
Set-Content "local.properties" $content

Write-Host "   ✓ API key configured" -ForegroundColor Green
Write-Host ""

# ESP32 MAC address (optional)
Write-Host "2. ESP32 Security (Optional)" -ForegroundColor Yellow
Write-Host "   To restrict connections to YOUR specific ESP32 board:"
Write-Host "   - Upload sketch_ble.ino to your ESP32"
Write-Host "   - Open Serial Monitor and copy the MAC address"
Write-Host "   - Enter it here"
Write-Host ""
$esp32Mac = Read-Host "   ESP32 MAC address (or press Enter to skip)"

if (![string]::IsNullOrWhiteSpace($esp32Mac)) {
    $content = Get-Content "local.properties"
    $content = $content -replace "#LASTDROP_ESP32_MAC=", "LASTDROP_ESP32_MAC=$esp32Mac"
    Set-Content "local.properties" $content
    Write-Host "   ✓ ESP32 MAC filtering enabled" -ForegroundColor Green
} else {
    Write-Host "   ⚠ ESP32 MAC filtering disabled - any LASTDROP-ESP32 can connect" -ForegroundColor Yellow
}

Write-Host ""

# Verify gitignore
Write-Host "3. Security Check" -ForegroundColor Yellow
$gitStatus = git status --porcelain 2>$null | Select-String "local.properties"

if ($gitStatus) {
    Write-Host "   ✗ WARNING: local.properties is tracked by Git!" -ForegroundColor Red
    Write-Host "   This is a security risk. Check your .gitignore file." -ForegroundColor Red
} else {
    Write-Host "   ✓ local.properties is properly gitignored" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Setup Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Build the app:     .\gradlew assembleDebug"
Write-Host "2. Install on device: .\gradlew installDebug"
Write-Host "3. Upload ESP32 firmware (sketch_ble.ino)"
Write-Host ""
Write-Host "For more details, see SECURITY.md" -ForegroundColor Gray
Write-Host ""
