# Website Deployment Verification

Write-Host "=== Checking Last Drop Website Deployment ===" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://lastdrop.earth/website"
$pagesToCheck = @(
    "index.html",
    "host.html",
    "spectate.html",
    "leaderboard.html",
    "tournaments.html",
    "analytics.html",
    "replay.html",
    "player_analytics.html",
    "api/get_analytics.php",
    "api/list_tournaments.php",
    "api/get_leaderboard.php"
)

Write-Host "Testing accessibility of deployed pages..." -ForegroundColor Yellow
Write-Host ""

$results = @()

foreach ($page in $pagesToCheck) {
    $url = "$baseUrl/$page"
    try {
        $response = Invoke-WebRequest -Uri $url -Method Head -UseBasicParsing -TimeoutSec 5
        $status = $response.StatusCode
        $statusText = "✓"
        $color = "Green"
    } catch {
        $status = "Error"
        $statusText = "✗"
        $color = "Red"
    }
    
    $results += [PSCustomObject]@{
        Page = $page
        Status = $status
        Symbol = $statusText
    }
    
    Write-Host "$statusText $page" -ForegroundColor $color -NoNewline
    Write-Host " - Status: $status"
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
$successful = ($results | Where-Object { $_.Status -eq 200 }).Count
$total = $results.Count
Write-Host "Accessible: $successful / $total pages" -ForegroundColor $(if ($successful -eq $total) { "Green" } else { "Yellow" })

Write-Host ""
Write-Host "Website Base URL: http://lastdrop.earth/website/" -ForegroundColor Cyan
