# ESP32 Serial Monitor Only
# View output without uploading

$configPath = Join-Path $PSScriptRoot "..\config.ps1"
if (Test-Path $configPath) { . $configPath }

Write-Host "╔════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║            ESP32 Serial Monitor                        ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════╝`n" -ForegroundColor Cyan

Write-Host "Port: $global:ESP32Port" -ForegroundColor Gray
Write-Host "Baud: $global:ESP32BaudRate" -ForegroundColor Gray
Write-Host "Press Ctrl+C to exit`n" -ForegroundColor Yellow

# Reset ESP32 (pulse DTR)
Write-Host "Resetting ESP32..." -ForegroundColor Cyan
try {
    $port = New-Object System.IO.Ports.SerialPort $global:ESP32Port,$global:ESP32BaudRate
    $port.DtrEnable = $false
    $port.Open()
    Start-Sleep -Milliseconds 100
    $port.DtrEnable = $true
    Start-Sleep -Milliseconds 100
    $port.Close()
    Write-Host "✓ ESP32 reset" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Could not reset ESP32" -ForegroundColor Yellow
}

Start-Sleep -Seconds 1

# Start monitor
& $global:ArduinoCli monitor --port $global:ESP32Port --config "baudrate=$global:ESP32BaudRate"
