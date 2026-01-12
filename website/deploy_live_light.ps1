param(
    [switch]$NoBackup
)

$Server = "lastdrop"          # SSH host alias (configured in .ssh/config)
$WebRoot = "/var/www/lastdrop.earth/public"
$RemoteTmp = "~/live_tmp"
$Sudo = "echo 'Lastdrop1!' | sudo -S"

Write-Host "Deploying live.html (light) ..." -ForegroundColor Cyan

# Ensure temp folders on remote
ssh $Server "mkdir -p $RemoteTmp/assets/images $RemoteTmp/assets/tiles"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Upload only live.html + images + tiles (no API or other pages)
scp live.html ${Server}:${RemoteTmp}/
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
scp assets/images/* ${Server}:${RemoteTmp}/assets/images/
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
scp assets/tiles/* ${Server}:${RemoteTmp}/assets/tiles/
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Optional backup
if (-not $NoBackup) {
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    ssh $Server "$Sudo cp $WebRoot/live.html $WebRoot/live_$timestamp.backup 2>/dev/null" 
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Move into place with correct ownership/permissions
ssh $Server "$Sudo mkdir -p $WebRoot/assets/images $WebRoot/assets/tiles; $Sudo mv $RemoteTmp/live.html $WebRoot/; $Sudo mv $RemoteTmp/assets/images/* $WebRoot/assets/images/; $Sudo mv $RemoteTmp/assets/tiles/* $WebRoot/assets/tiles/; $Sudo chown -R www-data:www-data $WebRoot; $Sudo chmod -R 755 $WebRoot"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Done. Visit https://lastdrop.earth/live.html" -ForegroundColor Green
