# Fonctions utiles. Utilisation (dans PowerShell, depuis C:\projet-middleware-g2) :
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   . .\scripts\functions.ps1
#   $t = Get-Token patient1 patient123
#   New-Rdv $t

function Get-Token($u, $p) {
    (curl.exe -s -X POST "http://localhost:8081/realms/hopital/protocol/openid-connect/token" `
        -d "client_id=rdv-app" -d "grant_type=password" -d "username=$u" -d "password=$p" | ConvertFrom-Json).access_token
}

function New-Rdv($token) {
    $f = Join-Path $env:TEMP "rdv.json"
    '{"motif":"Consultation generale","date":"2026-10-12T09:00"}' | Out-File -Encoding ascii $f
    curl.exe -s -X POST http://localhost:8080/rdv -H "Authorization: Bearer $token" -H "Content-Type: application/json" --data-binary "@$f"
    ""
}
