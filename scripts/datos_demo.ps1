# Deja la base de DESARROLLO (contenedor SGE-JD, 4200) como nueva, con datos de demostración:
# catálogo, clientes y proveedores de datos_demo.sql y 20 ventas por año (2024, 2025 y 2026) de datos_demo_ventas.sql.
#   datos_demo.bat              pide escribir BORRAR, respalda y carga
#   datos_demo.bat -Confirmado  sin la pregunta (el respaldo se saca igual)
# BORRA todos los datos de negocio de desarrollo. Los usuarios (Keycloak) no se tocan. Nunca apunta a producción.
param([switch]$Confirmado)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$raiz = (Resolve-Path "$PSScriptRoot\..").Path

if ((docker inspect -f '{{.State.Running}}' SGE-JD 2>$null) -ne 'true') {
    Write-Host 'Oracle de desarrollo (contenedor SGE-JD) esta apagado. Enciendalo con iniciar_sgejd.bat.' -ForegroundColor Red; exit 1
}
if (-not $Confirmado) {
    Write-Host 'Esto BORRA todos los datos de negocio de desarrollo y carga los de demostracion.' -ForegroundColor Red
    if ((Read-Host 'Escriba BORRAR para continuar') -cne 'BORRAR') { Write-Host 'Cancelado. No se cambio nada.'; exit 0 }
}

& "$PSScriptRoot\respaldar_sgejd.ps1" -Nombre ('sgejd_antes_de_datos_demo_' + (Get-Date -Format 'yyyyMMdd_HHmm'))
if ($LASTEXITCODE -ne 0) { Write-Host 'Sin respaldo no se borra nada.' -ForegroundColor Red; exit 1 }

foreach ($script in 'datos_demo.sql', 'datos_demo_ventas.sql') {
    Write-Host "== $script" -ForegroundColor Cyan
    docker cp (Join-Path $raiz $script) "SGE-JD:/tmp/$script" | Out-Null
    # Como SYSDBA (sin clave) apuntando al esquema del proyecto; UTF-8 para las tildes y la ene
    $envoltorio = Join-Path $env:TEMP "envolver_$script"
    [IO.File]::WriteAllText($envoltorio, "ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION;`n@/tmp/$script`n",
        (New-Object System.Text.UTF8Encoding $false))
    docker cp $envoltorio "SGE-JD:/tmp/envolver_$script" | Out-Null
    Remove-Item $envoltorio
    docker exec -e ORACLE_PDB_SID=FREEPDB1 -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 SGE-JD `
        sqlplus -s / as sysdba "@/tmp/envolver_$script" | Where-Object { $_ } | ForEach-Object { Write-Host "   $_" }
    $codigo = $LASTEXITCODE
    docker exec -u root SGE-JD rm -f "/tmp/$script" "/tmp/envolver_$script" | Out-Null
    if ($codigo -ne 0) {
        Write-Host "Fallo $script (Oracle deshizo lo de ese script). El respaldo quedo en C:\SGE-JD\respaldos." -ForegroundColor Red
        exit 1
    }
}
Write-Host 'Listo. Si el backend estaba encendido, recargue la pagina.' -ForegroundColor Green
