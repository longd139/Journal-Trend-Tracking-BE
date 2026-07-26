# Kill any process on port 8080, then start the app
$pid = (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue).OwningProcess
if ($pid) { Stop-Process -Id $pid -Force; Write-Host "Killed PID $pid on port 8080" }
docker stop scitrack-api 2>$null
.\mvnw.cmd spring-boot:run
