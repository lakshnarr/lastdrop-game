# Last Drop - PowerShell Configuration
$global:ArduinoCli = "C:\Users\ADMIN\AppData\Local\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe"
$global:AndroidSdkPath = "C:\Users\ADMIN\AppData\Local\Android\Sdk"
$global:JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$global:AdbPath = "$AndroidSdkPath\platform-tools\adb.exe"
$global:ESP32Board = "esp32:esp32:esp32s3:CDCOnBoot=cdc"
$global:ESP32Port = "COM11"
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
    $portName = $global:ESP32Port
    $port = Get-CimInstance Win32_PnPEntity | Where-Object { $_.Name -match [regex]::Escape($portName) }
    if ($port) {
        Write-Host "✓ ESP32 port $portName ready" -ForegroundColor Green
        return $true
    }
    Write-Host "✗ ESP32 port $portName not found" -ForegroundColor Red
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
        Write-Host "⌨️  Type commands and press Enter to send to ESP32" -ForegroundColor Cyan
        Write-Host "Press Ctrl+C to stop monitoring`n" -ForegroundColor Yellow
        
        while ($true) {
            try {
                # Read from serial port
                if ($port.BytesToRead -gt 0) {
                    $line = $port.ReadLine()
                    Write-Host $line
                }
                
                # Check for keyboard input (non-blocking)
                if ([Console]::KeyAvailable) {
                    $key = [Console]::ReadKey($true)
                    
                    # Send character to ESP32
                    $port.Write($key.KeyChar.ToString())
                    
                    # Echo to console
                    Write-Host -NoNewline $key.KeyChar -ForegroundColor Green
                    
                    # If Enter pressed, send newline
                    if ($key.Key -eq 'Enter') {
                        Write-Host ""
                    }
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

function Get-ESPToolPath {
    # Prefer the newest esptool.exe bundled with the installed ESP32 Arduino core
    $toolRoot = "C:\Users\ADMIN\AppData\Local\Arduino15\packages\esp32\tools\esptool_py"
    if (-not (Test-Path $toolRoot)) {
        return $null
    }

    $candidates = Get-ChildItem $toolRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object {
            $exe = Join-Path $_.FullName "esptool.exe"
            if (Test-Path $exe) { $exe }
        }

    return $candidates | Select-Object -First 1
}

function Erase-ESP32Flash {
    param([string]$Port = $global:ESP32Port)

    $esptool = Get-ESPToolPath
    if (-not $esptool) {
        Write-Host "✗ esptool.exe not found under Arduino15 ESP32 tools" -ForegroundColor Red
        return $false
    }

    Write-Host "🗑️  Full flash erase (this takes ~10-60s)..." -ForegroundColor Yellow
    Write-Host "Tool: $esptool" -ForegroundColor Gray
    Write-Host "Port: $Port" -ForegroundColor Gray

    & $esptool --chip esp32s3 --port $Port erase_flash
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Flash erased successfully" -ForegroundColor Green
        Start-Sleep -Seconds 2
        return $true
    }

    Write-Host "✗ Flash erase failed" -ForegroundColor Red
    return $false
}

function Upload-ESP32Firmware {
    param(
        [string]$Sketch = $global:ESP32Firmware,
        [switch]$SkipErase
    )
    
    Write-Host "🔧 Compiling and uploading ESP32 firmware..." -ForegroundColor Cyan
    Write-Host "Sketch: $Sketch" -ForegroundColor Gray
    Write-Host "Board: $global:ESP32Board" -ForegroundColor Gray
    Write-Host "Port: $global:ESP32Port`n" -ForegroundColor Gray
    
    # Erase flash first (unless -SkipErase is specified)
    if (-not $SkipErase) {
        Erase-ESP32Flash | Out-Null
    }
    
    & $global:ArduinoCli compile --fqbn $global:ESP32Board $Sketch
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Compilation failed" -ForegroundColor Red
        return $false
    }
    
    Write-Host "`n📤 Uploading to ESP32..." -ForegroundColor Cyan
    & $global:ArduinoCli upload -p $global:ESP32Port --fqbn $global:ESP32Board $Sketch
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

# ============================================================================
# VPS DEPLOYMENT CONFIGURATION
# ============================================================================

$global:VPS = @{
    Host = "142.171.191.171"
    Domain = "lastdrop.earth"
    User = "lastdrop"
    Password = "Lastdrop1!"
    WebRoot = "/var/www/lastdrop.earth/public"
    Port = 22
    PscpPath = "C:\Program Files\PuTTY\pscp.exe"
    PlinkPath = "C:\Program Files\PuTTY\plink.exe"
}

$global:LocalPaths = @{
    Website = "$ProjectRoot\website"
    LiveHtml = "$ProjectRoot\website\live.html"
    HostHtml = "$ProjectRoot\website\host.html"
    Assets = "$ProjectRoot\website\assets"
    ChanceCards = "$ProjectRoot\website\assets\chance"
}

function Test-VPSTools {
    $pscp = Test-Path $global:VPS.PscpPath
    $plink = Test-Path $global:VPS.PlinkPath
    if ($pscp -and $plink) {
        Write-Host "✓ PuTTY tools found (pscp, plink)" -ForegroundColor Green
        return $true
    }
    Write-Host "✗ PuTTY tools not found. Install with: winget install PuTTY.PuTTY" -ForegroundColor Red
    return $false
}

function Upload-ToVPS {
    param(
        [Parameter(Mandatory=$true)]
        [string]$LocalPath,
        [string]$RemotePath = $global:VPS.WebRoot
    )
    
    if (-not (Test-Path $LocalPath)) {
        Write-Host "✗ Local path not found: $LocalPath" -ForegroundColor Red
        return $false
    }
    
    $isDir = (Get-Item $LocalPath).PSIsContainer
    $fileName = Split-Path $LocalPath -Leaf
    $destFull = "$($global:VPS.User)@$($global:VPS.Host):$RemotePath"
    
    Write-Host "`n📤 Uploading: $fileName" -ForegroundColor Yellow
    Write-Host "   → $destFull" -ForegroundColor Gray
    
    $args = @("-batch", "-pw", $global:VPS.Password)
    if ($isDir) { $args += "-r" }
    $args += $LocalPath, $destFull
    
    & $global:VPS.PscpPath @args 2>&1 | ForEach-Object { Write-Host "   $_" }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Success!" -ForegroundColor Green
        return $true
    } else {
        Write-Host "   ❌ Failed" -ForegroundColor Red
        return $false
    }
}

function Deploy-Live {
    Write-Host "`n🚀 Deploying live.html to VPS..." -ForegroundColor Magenta
    Upload-ToVPS -LocalPath $global:LocalPaths.LiveHtml -RemotePath "$($global:VPS.WebRoot)/live.html"
}

function Deploy-Host {
    Write-Host "`n🚀 Deploying host.html to VPS..." -ForegroundColor Magenta
    Upload-ToVPS -LocalPath $global:LocalPaths.HostHtml -RemotePath "$($global:VPS.WebRoot)/host.html"
}

function Deploy-Assets {
    Write-Host "`n🚀 Deploying assets folder to VPS..." -ForegroundColor Magenta
    Upload-ToVPS -LocalPath $global:LocalPaths.Assets -RemotePath "$($global:VPS.WebRoot)/assets/"
}

function Deploy-ChanceCards {
    Write-Host "`n🚀 Deploying chance cards to VPS..." -ForegroundColor Magenta
    
    # Upload to /tmp first, then sudo copy (due to www-data ownership)
    $chanceDir = $global:LocalPaths.ChanceCards
    $files = Get-ChildItem "$chanceDir\*.png" -ErrorAction SilentlyContinue
    
    foreach ($file in $files) {
        Write-Host "   📤 $($file.Name)..." -ForegroundColor Gray
        & $global:VPS.PscpPath -batch -pw $global:VPS.Password $file.FullName "$($global:VPS.User)@$($global:VPS.Host):/tmp/$($file.Name)" 2>&1 | Out-Null
        & $global:VPS.PlinkPath -batch -pw $global:VPS.Password "$($global:VPS.User)@$($global:VPS.Host)" "echo '$($global:VPS.Password)' | sudo -S cp /tmp/$($file.Name) $($global:VPS.WebRoot)/assets/chance/$($file.Name) 2>/dev/null" 2>&1 | Out-Null
    }
    Write-Host "   ✅ Chance cards deployed!" -ForegroundColor Green
}

function Deploy-All {
    Write-Host "`n🚀 FULL DEPLOYMENT TO VPS" -ForegroundColor Magenta
    Write-Host "================================" -ForegroundColor Magenta
    
    Deploy-Live
    Deploy-Host
    
    # Upload other HTML files
    $htmlFiles = @("index.html", "intro.html")
    foreach ($file in $htmlFiles) {
        $localFile = Join-Path $global:LocalPaths.Website $file
        if (Test-Path $localFile) {
            Upload-ToVPS -LocalPath $localFile -RemotePath "$($global:VPS.WebRoot)/$file"
        }
    }
    
    Deploy-ChanceCards
    
    Write-Host "`n✅ Full deployment complete!" -ForegroundColor Green
}

function VPS-Run {
    param([Parameter(Mandatory=$true)][string]$Command)
    Write-Host "🖥️  Running on VPS: $Command" -ForegroundColor Cyan
    & $global:VPS.PlinkPath -batch -pw $global:VPS.Password "$($global:VPS.User)@$($global:VPS.Host)" $Command
}

function Show-DeployHelp {
    Write-Host @"

📦 LastDrop VPS Deployment Commands
====================================

Quick Deploy:
  Deploy-Live          Upload live.html
  Deploy-Host          Upload host.html
  Deploy-Assets        Upload assets folder
  Deploy-ChanceCards   Upload chance card images
  Deploy-All           Upload everything

Custom Upload:
  Upload-ToVPS -LocalPath "C:\file.png" -RemotePath "/var/www/lastdrop.earth/public/"

Run Command on VPS:
  VPS-Run "ls -la /var/www/lastdrop.earth/public/"

VPS: $($global:VPS.User)@$($global:VPS.Host)

"@ -ForegroundColor Cyan
}

# Show VPS status
if (Test-VPSTools) {
    Write-Host "VPS: $($global:VPS.User)@$($global:VPS.Host) → $($global:VPS.WebRoot)" -ForegroundColor DarkGray
    Write-Host "Type 'Show-DeployHelp' for deployment commands" -ForegroundColor DarkGray
}
