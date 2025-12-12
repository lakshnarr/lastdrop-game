# Last Drop - PowerShell Configuration
$global:ArduinoCli = "C:\Users\ADMIN\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
$global:AndroidSdkPath = "C:\Users\ADMIN\AppData\Local\Android\Sdk"
$global:JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$global:AdbPath = "$AndroidSdkPath\platform-tools\adb.exe"
$global:ESP32Board = "esp32:esp32:esp32"
$global:ESP32Port = "COM7"
$global:ESP32BaudRate = 115200
$global:ProjectRoot = "D:\PERSONAL\lakhna\DEVELOPMENT\LastDrop"
$global:ESP32Firmware = "$ProjectRoot\ESP32 Program\sketch_ble.ino"

# Set JAVA_HOME environment variable for Gradle builds
$env:JAVA_HOME = $global:JavaHome

function Test-ArduinoCli {
    if (Test-Path $ArduinoCli) {
        Write-Host "✓ Arduino CLI found" -ForegroundColor Green
        & $ArduinoCli version
        return $true
    }
    Write-Host "✗ Arduino CLI not found" -ForegroundColor Red
    return $false
}

function Test-AndroidSdk {
    if (Test-Path $AdbPath) {
        Write-Host "✓ ADB found" -ForegroundColor Green
        & $AdbPath version
        return $true
    }
    Write-Host "✗ ADB not found" -ForegroundColor Red
    return $false
}

function Test-JavaHome {
    if (Test-Path "$JavaHome\bin\java.exe") {
        Write-Host "✓ Java found" -ForegroundColor Green
        & "$JavaHome\bin\java.exe" -version
        return $true
    }
    Write-Host "✗ Java not found" -ForegroundColor Red
    return $false
}

function Test-ESP32Port {
    $port = Get-CimInstance Win32_PnPEntity | Where-Object { $_.Name -match "COM7" }
    if ($port) {
        Write-Host "✓ ESP32 port COM7 ready" -ForegroundColor Green
        return $true
    }
    Write-Host "✗ ESP32 port COM7 not found" -ForegroundColor Red
    return $false
}

Write-Host "Loading Last Drop configuration..." -ForegroundColor Cyan
$a = Test-ArduinoCli
$b = Test-AndroidSdk
$c = Test-JavaHome
$d = Test-ESP32Port
if ($a -and $b -and $c -and $d) { Write-Host "`n✅ All tools ready!" -ForegroundColor Green }
