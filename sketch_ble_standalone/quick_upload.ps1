# Quick ESP32 Upload - No Prompts
# For rapid development iterations

$configPath = Join-Path $PSScriptRoot "..\config.ps1"
if (Test-Path $configPath) { . $configPath }

$SketchPath = Join-Path $PSScriptRoot "godice_test.ino"

Write-Host "⚡ Quick Upload: godice_test.ino" -ForegroundColor Yellow

# Compile
Write-Host "⚙️  Compiling..." -ForegroundColor Cyan
& $global:ArduinoCli compile --fqbn $global:ESP32Board $SketchPath 2>&1 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Compilation failed" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Compiled" -ForegroundColor Green

# Upload
Write-Host "📤 Uploading..." -ForegroundColor Cyan
& $global:ArduinoCli upload --fqbn $global:ESP32Board --port $global:ESP32Port $SketchPath 2>&1 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Upload failed" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Upload complete!" -ForegroundColor Green

# Auto-open monitor
Start-Sleep -Seconds 2
Write-Host "📡 Serial Monitor (Ctrl+C to exit)" -ForegroundColor Cyan
& $global:ArduinoCli monitor --port $global:ESP32Port --config "baudrate=$global:ESP32BaudRate"
