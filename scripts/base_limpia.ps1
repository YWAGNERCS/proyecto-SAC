# Limpia la base de SGE-JD de forma segura: ensayo → confirmación escrita → respaldo → limpieza.
#   base_limpia.bat            modo PRUEBA: borra solo lo marcado "[Prueba]" y lo que cuelga de ello
#   base_limpia.bat -Entrega   modo ENTREGA: borra todos los movimientos y deja la base para el cliente
#   -SoloEnsayo                muestra cuánto borraría y termina sin cambiar nada
# Detalle de cada modo: comentarios de base_limpia.sql.
param([switch]$Entrega, [switch]$SoloEnsayo)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$modo = if ($Entrega) { 'ENTREGA' } else { 'PRUEBA' }

function Ejecutar([string]$fin) {
    docker cp "$PSScriptRoot\base_limpia.sql" SGE-JD:/tmp/base_limpia.sql | Out-Null
    # ORACLE_PDB_SID hace que "/ as sysdba" entre directo a FREEPDB1
    docker exec -e ORACLE_PDB_SID=FREEPDB1 SGE-JD sqlplus -s / as sysdba "@/tmp/base_limpia.sql" $modo $fin |
        Where-Object { $_ } | ForEach-Object { Write-Host $_ }
    return $LASTEXITCODE
}

if ((docker inspect -f '{{.State.Running}}' SGE-JD 2>$null) -ne 'true') {
    Write-Host '✖ Oracle (contenedor SGE-JD) está apagado. Enciéndalo con iniciar_sgejd.bat.' -ForegroundColor Red; exit 1
}

Write-Host "=== Ensayo del modo $modo (no cambia nada) ===" -ForegroundColor Yellow
if ((Ejecutar 'ROLLBACK') -ne 0) { Write-Host '✖ El ensayo falló; no se tocó nada.' -ForegroundColor Red; exit 1 }
if ($SoloEnsayo) { exit 0 }

Write-Host ''
Write-Host 'Esto BORRA esas filas. Antes se saca un respaldo en C:\SGE-JD\respaldos.' -ForegroundColor Red
if ($Entrega) { Write-Host 'Modo ENTREGA: se pierden TODAS las proformas, ventas, cobros y compras.' -ForegroundColor Red }
if ((Read-Host 'Escriba BORRAR para continuar') -cne 'BORRAR') { Write-Host 'Cancelado. No se cambió nada.'; exit 0 }

& "$PSScriptRoot\respaldar_sgejd.ps1" -Nombre ("sgejd_antes_de_limpiar_" + (Get-Date -Format 'yyyyMMdd_HHmm'))
if ($LASTEXITCODE -ne 0) { Write-Host '✖ Sin respaldo no se limpia. No se cambió nada.' -ForegroundColor Red; exit 1 }

Write-Host "=== Limpiando (modo $modo) ===" -ForegroundColor Yellow
if ((Ejecutar 'COMMIT') -ne 0) { Write-Host '✖ Falló y Oracle deshizo todo (ROLLBACK). El respaldo quedó guardado.' -ForegroundColor Red; exit 1 }
Write-Host '✔ Listo. Si el backend estaba encendido, recargue la página.' -ForegroundColor Green
