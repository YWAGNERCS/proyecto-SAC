# S11 · El cliente de la SPA (bomerp-frontend) solo acepta Authorization Code con PKCE.
. "$PSScriptRoot\comun.ps1"
$KC = 'http://localhost:38080/realms/bomerp/protocol/openid-connect'
$retorno = [uri]::EscapeDataString('http://localhost:4200/auth/callback')

Write-Paso '1. Atajo de S10b (password grant) contra el cliente de la SPA -> rechazado'
try {
    Invoke-RestMethod -Method Post -Uri "$KC/token" -ContentType 'application/x-www-form-urlencoded' `
        -Body "grant_type=password&client_id=bomerp-frontend&username=lucy&password=$($Global:CLAVE)" | Out-Null
    Write-Host 'ERROR: el cliente aceptó la contraseña directa' -ForegroundColor Red
} catch {
    Write-Host ("HTTP {0}  (esperado 400)" -f [int]$_.Exception.Response.StatusCode) -ForegroundColor Green
    Write-Host $_.ErrorDetails.Message
}

Write-Paso '2. Pedir un código SIN code_challenge -> Keycloak lo rechaza (PKCE obligatorio)'
$req = [System.Net.HttpWebRequest]::Create("$KC/auth?client_id=bomerp-frontend&response_type=code&scope=openid&redirect_uri=$retorno")
$req.AllowAutoRedirect = $false
$resp = $req.GetResponse()
Write-Host ("HTTP {0} -> redirige a:" -f [int]$resp.StatusCode) -ForegroundColor Green
Write-Host ([uri]::UnescapeDataString($resp.Headers['Location']))
$resp.Close()

Write-Paso '3. Con PKCE (code_challenge S256) -> Keycloak muestra su pantalla de login'
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$verifier = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::ASCII.GetBytes($verifier))
$challenge = [Convert]::ToBase64String($hash).TrimEnd('=').Replace('+', '-').Replace('/', '_')
Write-Host "code_verifier  (queda en la SPA): $verifier"
Write-Host "code_challenge (viaja a Keycloak): $challenge"
$r = Invoke-WebRequest -UseBasicParsing -Uri ("$KC/auth?client_id=bomerp-frontend&response_type=code&scope=openid" +
    "&redirect_uri=$retorno&code_challenge=$challenge&code_challenge_method=S256&state=prueba")
$titulo = [regex]::Match($r.Content, '<title>(.*?)</title>').Groups[1].Value
Write-Host ("HTTP {0} · página: {1}" -f [int]$r.StatusCode, [System.Net.WebUtility]::HtmlDecode($titulo)) -ForegroundColor Green
