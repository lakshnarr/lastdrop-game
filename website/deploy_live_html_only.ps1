param(
    [switch]$NoBackup
)

$Server = "lastdrop"          # SSH host alias (configured in .ssh/config)
$WebRoot = "/var/www/lastdrop.earth/public"
$RemoteTmp = "~/live_tmp"
$Sudo = "echo 'Lastdrop1!' | sudo -S"

Write-Host "Deploying live.html only..." -ForegroundColor Cyan

# Ensure temp folder on remote
ssh $Server "mkdir -p $RemoteTmp"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Upload only live.html
scp live.html ${Server}:${RemoteTmp}/
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Optional backup of current live.html
if (-not $NoBackup) {
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    ssh $Server "$Sudo cp $WebRoot/live.html $WebRoot/live_$timestamp.backup 2>/dev/null"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Move into place with correct ownership/permissions
ssh $Server "$Sudo mv $RemoteTmp/live.html $WebRoot/; $Sudo chown -R www-data:www-data $WebRoot; $Sudo chmod -R 755 $WebRoot"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Done. Visit https://lastdrop.earth/live.html" -ForegroundColor Green
