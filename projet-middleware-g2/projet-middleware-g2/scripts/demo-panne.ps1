# Demonstration obligatoire : panne du consommateur Notification.
# Lancer :  powershell -ExecutionPolicy Bypass -File scripts\demo-panne.ps1
. "$PSScriptRoot\functions.ps1"
$t = Get-Token patient1 patient123

Write-Host "1/4  Arret du consommateur Notification" -ForegroundColor Yellow
docker stop notification-consumer

Write-Host "2/4  Creation de 15 rendez-vous pendant la panne" -ForegroundColor Yellow
1..15 | ForEach-Object { New-Rdv $t }

Write-Host "3/4  Ouvrez http://localhost:15672 (Queues and Streams) : notification.queue doit afficher Ready = 15" -ForegroundColor Green
Read-Host "     Faites la capture, puis appuyez sur Entree pour relancer le consommateur"

Write-Host "4/4  Relance du consommateur : la file se vide en ~15 secondes" -ForegroundColor Yellow
docker start notification-consumer
Start-Sleep -Seconds 20
docker logs notification-consumer --tail 10
