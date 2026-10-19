# Funciones comunes de las pruebas de seguridad S10 (Keycloak, realm "bomerp").
# Requisitos: Keycloak en :38080 (docker compose) y el backend en :8080.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = 'Stop'

$Global:KC  = 'http://localhost:38080/realms/bomerp/protocol/openid-connect/token'
$Global:API = 'http://localhost:8080/api/v1'
# Usuarios semilla de desarrollo (keycloak/configurar_realm.py). Cambiar en producción.
$Global:CLAVE = 'JdRefri2026!'

function Get-Token([string]$usuario) {
    $r = Invoke-RestMethod -Method Post -Uri $Global:KC -ContentType 'application/x-www-form-urlencoded' `
        -Body "grant_type=password&client_id=bomerp-frontend&username=$usuario&password=$($Global:CLAVE)"
    return $r
}

# Decodifica la parte central (claims) de un JWT, sin verificar la firma: solo para inspeccionar.
function Get-Claims([string]$jwt) {
    $p = $jwt.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($p.Length % 4) { 2 { $p += '==' } 3 { $p += '=' } }
    return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}

# Llama a la API y devuelve el código HTTP y el cuerpo, también cuando es 401/403.
function Invoke-Api([string]$metodo, [string]$ruta, [string]$token, $cuerpo) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    $params = @{ Method = $metodo; Uri = "$($Global:API)$ruta"; Headers = $h; UseBasicParsing = $true }
    if ($cuerpo) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Compress))
    }
    try {
        $r = Invoke-WebRequest @params
        $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return [pscustomobject]@{ Codigo = [int]$r.StatusCode; Cuerpo = $texto }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        # PowerShell 5.1 ya leyó el cuerpo del error: queda en ErrorDetails
        $texto = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { '' }
        return [pscustomobject]@{ Codigo = [int]$resp.StatusCode; Cuerpo = $texto }
    }
}

function Write-Paso([string]$texto) { Write-Host ''; Write-Host "=== $texto ===" -ForegroundColor Cyan }

function Write-Resultado($r, [int[]]$esperado) {
    $color = if ($esperado -contains $r.Codigo) { 'Green' } else { 'Red' }
    Write-Host ("HTTP {0}  (esperado {1})" -f $r.Codigo, ($esperado -join '/')) -ForegroundColor $color
    $c = $r.Cuerpo
    if ($c.Length -gt 400) { $c = $c.Substring(0, 400) + ' ...' }
    if ($c) { Write-Host $c }
}
