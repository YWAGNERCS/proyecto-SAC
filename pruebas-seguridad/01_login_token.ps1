# Bloque 1 · Login real y token: Lucy (ASISTENTE) pide su token a Keycloak.
. "$PSScriptRoot\comun.ps1"

Write-Paso 'POST /realms/bomerp/protocol/openid-connect/token  (usuario: lucy)'
$login = Get-Token 'lucy'
Write-Host ("token_type = {0} · expires_in = {1} s" -f $login.token_type, $login.expires_in)
Write-Host 'access_token (inicio):' -ForegroundColor Yellow
Write-Host ($login.access_token.Substring(0, 160) + '...')
Write-Host ("longitud: {0} caracteres · 3 partes: header.payload.firma" -f $login.access_token.Length)

Write-Paso 'Header del JWT'
$h = $login.access_token.Split('.')[0].Replace('-', '+').Replace('_', '/')
switch ($h.Length % 4) { 2 { $h += '==' } 3 { $h += '=' } }
Write-Host ([System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($h)))

Write-Paso 'Claims que usa el backend'
$c = Get-Claims $login.access_token
[pscustomobject]@{
    iss                = $c.iss
    sub                = $c.sub
    preferred_username = $c.preferred_username
    email              = $c.email
    'realm_access.roles' = ($c.realm_access.roles -join ', ')
    exp                = [DateTimeOffset]::FromUnixTimeSeconds($c.exp).LocalDateTime
} | Format-List | Out-Host

Write-Paso 'Claves públicas del realm (JWKS) con las que el backend verifica la firma RS256'
$jwks = Invoke-RestMethod 'http://localhost:38080/realms/bomerp/protocol/openid-connect/certs'
$jwks.keys | Select-Object kid, kty, alg, use | Format-Table -AutoSize | Out-Host
