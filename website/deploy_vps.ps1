Write-Host "Deploying to lastdrop.earth..." -ForegroundColor Cyan
Write-Host "Uploading files to home directory..." -ForegroundColor Yellow
scp live.html lastdrop:~/
scp demo.html lastdrop:~/
ssh lastdrop "mkdir -p ~/tiles"
scp assets\tiles\*.svg lastdrop:~/tiles/
ssh lastdrop "mkdir -p ~/api"
scp api\*.php lastdrop:~/api/
Write-Host "Moving files to web directory..." -ForegroundColor Yellow
ssh lastdrop "sudo mkdir -p /var/www/lastdrop.earth/public/assets/tiles /var/www/lastdrop.earth/public/api && sudo mv ~/live.html ~/demo.html /var/www/lastdrop.earth/public/ && sudo mv ~/tiles/*.svg /var/www/lastdrop.earth/public/assets/tiles/ && sudo mv ~/api/*.php /var/www/lastdrop.earth/public/api/ && sudo chown -R www-data:www-data /var/www/lastdrop.earth/public && sudo chmod -R 755 /var/www/lastdrop.earth/public"
Write-Host "Done! Visit https://lastdrop.earth/live.html" -ForegroundColor Green
