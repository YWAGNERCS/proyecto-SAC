# Bloque 4 · Perfil leído directo del JWT (sin consultar ninguna base de datos).
. "$PSScriptRoot\comun.ps1"

foreach ($u in 'lucy', 'lisandro') {
    Write-Paso "GET /api/v1/auth/perfil  (token de $u)"
    $r = Invoke-Api 'GET' '/auth/perfil' (Get-Token $u).access_token
    Write-Host ("HTTP {0}" -f $r.Codigo) -ForegroundColor Green
    $r.Cuerpo | ConvertFrom-Json | Format-List | Out-Host
}

Write-Paso 'GET /api/v1/auth/perfil  sin token'
Write-Resultado (Invoke-Api 'GET' '/auth/perfil' $null) 401
