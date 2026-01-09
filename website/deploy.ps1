# Last Drop - VPS Deployment Script
Write-Host "=== Last Drop VPS Deployment ===" -ForegroundColor Cyan
Write-Host ""

$sshHost = "lastdrop"
$webRoot = "/var/www/lastdrop.earth/public"
$localRoot = "D:\PERSONAL\lakhna\DEVELOPMENT\LastDrop\website"

Write-Host "SSH Host: $sshHost" -ForegroundColor Yellow
Write-Host "Remote: $webRoot" -ForegroundColor Yellow
Write-Host ""

# Test connection
Write-Host "Testing SSH connection..." -ForegroundColor Cyan
$test = ssh $sshHost "echo 'Connected'" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ SSH failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Connected" -ForegroundColor Green
Write-Host ""

# Upload HTML files
Write-Host "Uploading HTML files..." -ForegroundColor Cyan
$files = @("live.html", "demo.html", "index.html")
foreach ($file in $files) {
    $local = Join-Path $localRoot $file
    if (Test-Path $local) {
        Write-Host "  → $file" -ForegroundColor Gray
        scp $local "${sshHost}:$webRoot/$file"
    }
}
Write-Host ""

# Upload API files
Write-Host "Uploading API files..." -ForegroundColor Cyan
ssh $sshHost "mkdir -p $webRoot/api"
$apiFiles = @("live_push.php", "live_state.php", "session_list.php")
foreach ($file in $apiFiles) {
    $local = Join-Path $localRoot "api\$file"
    if (Test-Path $local) {
        Write-Host "  → api/$file" -ForegroundColor Gray
        scp $local "${sshHost}:$webRoot/api/$file"
    }
}
Write-Host ""

# Upload tiles
Write-Host "Uploading tile SVG images..." -ForegroundColor Cyan
$tilesDir = Join-Path $localRoot "assets\tiles"
if (Test-Path $tilesDir) {
    ssh $sshHost "mkdir -p $webRoot/assets/tiles"
    scp -r "$tilesDir\*.svg" "${sshHost}:$webRoot/assets/tiles/"
    Write-Host "  ✓ Tiles uploaded" -ForegroundColor Green
}
Write-Host ""

# Set permissions
Write-Host "Setting permissions..." -ForegroundColor Cyan
ssh $sshHost "sudo chown -R www-data:www-data $webRoot 2>/dev/null; sudo chmod -R 755 $webRoot 2>/dev/null"
Write-Host "✓ Done" -ForegroundColor Green
Write-Host ""

Write-Host "=== Deployment Complete ===" -ForegroundColor Green
Write-Host "Site: https://lastdrop.earth/live.html" -ForegroundColor White
