# Last Drop - PowerShell Configuration
$global:ArduinoCli = "C:\Users\ADMIN\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
$global:AndroidSdkPath = "C:\Users\ADMIN\AppData\Local\Android\Sdk"
$global:JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$global:AdbPath = "$AndroidSdkPath\platform-tools\adb.exe"
$global:ESP32Board = "esp32:esp32:esp32"
$global:ESP32Port = "COM7"
$global:ESP32BaudRate = 115200
$global:ProjectRoot = "D:\PERSONAL\lakhna\DEVELOPMENT\LastDrop"
$global:ESP32Firmware = "$ProjectRoot\sketch_ble\sketch_ble.ino"

# Set JAVA_HOME environment variable for Gradle builds
$env:JAVA_HOME = $global:JavaHome

# Ensure Android SDK environment + adb are available in the current PowerShell session
$env:ANDROID_SDK_ROOT = $global:AndroidSdkPath
$env:ANDROID_HOME = $global:AndroidSdkPath
$platformTools = "$($global:AndroidSdkPath)\platform-tools"
if (Test-Path $platformTools) {
    if ($env:Path -notlike "*$platformTools*") {
        $env:Path = "$platformTools;$env:Path"
    }
}

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

function Start-ESP32Monitor {
    Write-Host "📡 Starting ESP32 Serial Monitor..." -ForegroundColor Cyan
    $port = New-Object System.IO.Ports.SerialPort $global:ESP32Port,$global:ESP32BaudRate,None,8,One
    try {
        $port.Open()
        $port.DiscardInBuffer()
        $port.DiscardOutBuffer()
        Write-Host "✓ Connected to $global:ESP32Port @ $global:ESP32BaudRate baud" -ForegroundColor Green
        Write-Host "Press Ctrl+C to stop monitoring`n" -ForegroundColor Yellow
        
        while ($true) {
            try {
                if ($port.BytesToRead -gt 0) {
                    $line = $port.ReadLine()
                    Write-Host $line
                }
            } catch { }
            Start-Sleep -Milliseconds 10
        }
    } catch {
        Write-Host "✗ Error: $_" -ForegroundColor Red
        Write-Host "Make sure COM port is not in use by another program (Arduino IDE, etc.)" -ForegroundColor Yellow
    } finally {
        if ($port.IsOpen) {
            $port.Close()
            Write-Host "`n🛑 Serial monitor closed" -ForegroundColor Yellow
        }
    }
}

function Upload-ESP32Firmware {
    Write-Host "🔧 Compiling and uploading ESP32 firmware..." -ForegroundColor Cyan
    Write-Host "Sketch: $global:ESP32Firmware" -ForegroundColor Gray
    Write-Host "Board: $global:ESP32Board" -ForegroundColor Gray
    Write-Host "Port: $global:ESP32Port`n" -ForegroundColor Gray
    
    & $global:ArduinoCli compile --fqbn $global:ESP32Board $global:ESP32Firmware
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Compilation failed" -ForegroundColor Red
        return $false
    }
    
    Write-Host "`n📤 Uploading to ESP32..." -ForegroundColor Cyan
    & $global:ArduinoCli upload -p $global:ESP32Port --fqbn $global:ESP32Board $global:ESP32Firmware
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Upload successful!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "✗ Upload failed" -ForegroundColor Red
        return $false
    }
}

Write-Host "Loading Last Drop configuration..." -ForegroundColor Cyan
$a = Test-ArduinoCli
$b = Test-AndroidSdk
$c = Test-JavaHome
$d = Test-ESP32Port
if ($a -and $b -and $c -and $d) { Write-Host "`n✅ All tools ready!" -ForegroundColor Green }
