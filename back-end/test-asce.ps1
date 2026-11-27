$secret = $env:KEYCLOAK_CLIENT_SECRET
if (-not $secret) { Write-Error "Définir d'abord KEYCLOAK_CLIENT_SECRET (voir .env)"; exit 1 }
$username = "admin.asce"
$password = $env:ASCE_TEST_PASSWORD   # à définir avant de lancer, jamais écrit ici

$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/realms/asce-lc/protocol/openid-connect/token" -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=asce-lc-backend&client_secret=$secret&username=$username&password=$password"

$token = $response.access_token
Write-Host "=== TOKEN OK ===" -ForegroundColor Green

$roles = Invoke-RestMethod -Method Get -Uri "http://localhost:8081/api/v1/agents/keycloak-roles" -Headers @{ Authorization = "Bearer $token" }
Write-Host "=== ROLES KEYCLOAK ===" -ForegroundColor Cyan
$roles