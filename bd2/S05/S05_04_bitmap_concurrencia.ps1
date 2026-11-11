# BD2 · S5 · Paso 4: ¿qué le hace el índice Bitmap a dos ventas guardadas al mismo tiempo?
#   .\bd2\S05\S05_04_bitmap_concurrencia.ps1
# Corre la misma prueba dos veces: con el índice BITMAP sobre VENTA_AUDITORIA(ACCION) y con un B-Tree en su
# lugar. En cada una, la sesión A inserta una fila de auditoría y tarda 15 s en confirmar; la B inserta otra
# con la misma ACCION 4 s después. Al final deja el índice que se decidió conservar (ver README).
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path "$PSScriptRoot\..\..").Path
$sql  = Join-Path $raiz 'bd2\herramientas\sql.ps1'
$tmp  = Join-Path $raiz 'bd2\salidas'
New-Item -ItemType Directory -Force $tmp | Out-Null

function Indice([string]$ddl) {
    $f = Join-Path $tmp 'S05_04_indice.sql'
    "set feedback off`nDROP INDEX IF EXISTS bx_venta_auditoria_accion;`nDROP INDEX IF EXISTS ix_venta_auditoria_accion;`n$ddl`nexit" |
        Set-Content -Encoding utf8 $f
    & $sql $f | Out-Null
}
function Probar([string]$titulo) {
    Write-Host "===== $titulo =====" -ForegroundColor Cyan
    $job = Start-Job -ScriptBlock { param($s, $f) & $s $f } -ArgumentList $sql, (Join-Path $PSScriptRoot 'S05_04a_sesion_a.sql')
    Start-Sleep -Seconds 4
    & $sql (Join-Path $PSScriptRoot 'S05_04b_sesion_b.sql')
    Wait-Job $job | Out-Null; Receive-Job $job; Remove-Job $job
}

Indice 'CREATE BITMAP INDEX bx_venta_auditoria_accion ON venta_auditoria (accion);'
Probar 'Con índice BITMAP'
Indice 'CREATE INDEX ix_venta_auditoria_accion ON venta_auditoria (accion);'
Probar 'Con índice B-Tree'
