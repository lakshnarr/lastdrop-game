# GoDice ESP32 Upload Script
# Automated upload for GoDice library test firmware

# Load project configuration
$configPath = Join-Path $PSScriptRoot "..\config.ps1"
if (Test-Path $configPath) {
    . $configPath
    Write-Host "✓ Loaded project configuration" -ForegroundColor Green
} else {
    Write-Host "⚠️  config.ps1 not found, using defaults" -ForegroundColor Yellow
    $global:ArduinoCli = "arduino-cli"
    $global:ESP32Board = "esp32:esp32:esp32s3:CDCOnBoot=cdc"
    $global:ESP32Port = "COM10"
    $global:ESP32BaudRate = 115200
}

$SketchPath = Join-Path $PSScriptRoot "godice_test.ino"

Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║                                                        ║" -ForegroundColor Cyan
Write-Host "║        GoDice ESP32 Test Firmware Upload              ║" -ForegroundColor Cyan
Write-Host "║                                                        ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

# Verify sketch exists
if (-not (Test-Path $SketchPath)) {
    Write-Host "✗ Sketch not found: $SketchPath" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Sketch found: godice_test.ino" -ForegroundColor Green

# Verify Arduino CLI
if (-not (Get-Command $global:ArduinoCli -ErrorAction SilentlyContinue)) {
    Write-Host "✗ Arduino CLI not found" -ForegroundColor Red
    Write-Host "Please install Arduino CLI or update path in config.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "✓ Arduino CLI ready" -ForegroundColor Green

# Check ESP32 board support
Write-Host "`n📦 Checking ESP32 board support..." -ForegroundColor Cyan
$coreList = & $global:ArduinoCli core list 2>&1
if ($coreList -match "esp32:esp32") {
    Write-Host "✓ ESP32 board support installed" -ForegroundColor Green
} else {
    Write-Host "⚠️  ESP32 board support not found" -ForegroundColor Yellow
    Write-Host "Installing ESP32 core (this may take a few minutes)..." -ForegroundColor Cyan
    & $global:ArduinoCli core update-index
    & $global:ArduinoCli core install esp32:esp32
}

# Check COM port
Write-Host "`n🔌 Checking ESP32 connection..." -ForegroundColor Cyan
$portCheck = & $global:ArduinoCli board list 2>&1 | Select-String $global:ESP32Port
if ($portCheck) {
    Write-Host "✓ ESP32 detected on $global:ESP32Port" -ForegroundColor Green
} else {
    Write-Host "⚠️  ESP32 not detected on $global:ESP32Port" -ForegroundColor Yellow
    Write-Host "Available ports:" -ForegroundColor Gray
    & $global:ArduinoCli board list
    
    $continue = Read-Host "`nContinue anyway? (y/n)"
    if ($continue -ne "y") {
        Write-Host "Upload cancelled" -ForegroundColor Yellow
        exit 0
    }
}

# Compile
Write-Host "`n⚙️  Compiling firmware..." -ForegroundColor Cyan
Write-Host "Board: $global:ESP32Board" -ForegroundColor Gray
Write-Host "This may take 1-2 minutes on first compile...`n" -ForegroundColor Gray

$compileResult = & $global:ArduinoCli compile `
    --fqbn $global:ESP32Board `
    --warnings "more" `
    $SketchPath 2>&1

# Check compilation result
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n✗ Compilation FAILED" -ForegroundColor Red
    Write-Host $compileResult -ForegroundColor Red
    exit 1
}

Write-Host "✓ Compilation successful" -ForegroundColor Green

# Show memory usage
$memoryInfo = $compileResult | Select-String "Sketch uses.*bytes"
if ($memoryInfo) {
    Write-Host "`nMemory Usage:" -ForegroundColor Cyan
    $memoryInfo | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
}

# Upload
Write-Host "`n📤 Uploading to ESP32..." -ForegroundColor Cyan
Write-Host "Port: $global:ESP32Port" -ForegroundColor Gray
Write-Host "⏳ Please wait (may take 30-60 seconds)...`n" -ForegroundColor Yellow

$uploadResult = & $global:ArduinoCli upload `
    --fqbn $global:ESP32Board `
    --port $global:ESP32Port `
    $SketchPath 2>&1

# Check upload result
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n✗ Upload FAILED" -ForegroundColor Red
    Write-Host $uploadResult -ForegroundColor Red
    Write-Host "`nTroubleshooting:" -ForegroundColor Yellow
    Write-Host "1. Make sure ESP32 is connected via USB" -ForegroundColor Gray
    Write-Host "2. Try pressing BOOT button on ESP32, then upload" -ForegroundColor Gray
    Write-Host "3. Check COM port in Device Manager" -ForegroundColor Gray
    Write-Host "4. Close Arduino IDE if open" -ForegroundColor Gray
    exit 1
}

Write-Host "`n✓ Upload successful!" -ForegroundColor Green

# Offer to open serial monitor
Write-Host "`n╔════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                                                        ║" -ForegroundColor Green
Write-Host "║              ✓ Firmware Uploaded Successfully          ║" -ForegroundColor Green
Write-Host "║                                                        ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Green

$monitor = Read-Host "Open Serial Monitor? (y/n)"
if ($monitor -eq "y") {
    Write-Host "`n📡 Starting Serial Monitor..." -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to exit`n" -ForegroundColor Yellow
    
    # Wait for ESP32 to reboot
    Start-Sleep -Seconds 2
    
    # Use arduino-cli monitor
    & $global:ArduinoCli monitor `
        --port $global:ESP32Port `
        --config "baudrate=$global:ESP32BaudRate"
}

Write-Host "`n✅ All done! GoDice test firmware is ready." -ForegroundColor Green
Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "1. Power on your GoDice" -ForegroundColor Gray
Write-Host "2. Watch Serial Monitor for connection" -ForegroundColor Gray
Write-Host "3. Roll dice to test detection" -ForegroundColor Gray
Write-Host "4. Type 'h' for serial commands" -ForegroundColor Gray
