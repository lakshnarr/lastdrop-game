Write-Host "Deploying to lastdrop.earth..." -ForegroundColor Cyan
scp live.html lastdrop:/var/www/lastdrop.earth/public/
scp demo.html lastdrop:/var/www/lastdrop.earth/public/
scp -r assets\tiles\*.svg lastdrop:/var/www/lastdrop.earth/public/assets/tiles/
ssh lastdrop "mkdir -p /var/www/lastdrop.earth/public/api"
scp api\*.php lastdrop:/var/www/lastdrop.earth/public/api/
Write-Host "Done!" -ForegroundColor Green
