# Vuelve la base de PRÁCTICA (sge-jd-bd2) a la copia limpia de desarrollo del 10/11/2026 y recrea los objetos de
# las sesiones de BD2 que se pidan (por defecto S1 a S3: PL/SQL, auditoría y registro de errores).
#   .\bd2\herramientas\restaurar_practica.ps1                 # S1-S3
#   .\bd2\herramientas\restaurar_practica.ps1 -Hasta 2        # solo S1-S2
# BORRA el volumen de la base de práctica (no toca desarrollo ni producción).
param([ValidateRange(0, 3)][int]$Hasta = 3, [switch]$SoloObjetos)
$ErrorActionPreference = 'Continue'   # docker escribe su avance en stderr: con 'Stop', PowerShell 5.1 lo toma como error
$raiz = (Resolve-Path "$PSScriptRoot\..\..").Path
$compose = @('-f', (Join-Path $raiz 'bd2\compose-bd2.yml'), '--env-file', (Join-Path $raiz 'bd2\.env.bd2'))
$sql = Join-Path $PSScriptRoot 'sql.ps1'
$dump = 'C:\SGE-JD\respaldos\sgejd_base_bd2_20261110.dmp'

if (-not $SoloObjetos) {
Write-Host '1) Borrando la base de práctica y creándola de nuevo...' -ForegroundColor Cyan
docker compose @compose down -v 2>&1 | Out-Null
docker compose @compose up -d 2>&1 | Out-Null
$t = 0
do { Start-Sleep 10; $t += 10 } until (((docker logs sge-jd-bd2 2>&1) -match 'DATABASE IS READY TO USE') -or $t -ge 600)
if ($t -ge 600) { throw 'Oracle no arrancó en 10 minutos' }

Write-Host '2) Importando la copia de desarrollo...' -ForegroundColor Cyan
docker cp $dump 'sge-jd-bd2:/tmp/base.dmp' | Out-Null
docker cp (Join-Path $PSScriptRoot 'importar_base.sh') 'sge-jd-bd2:/tmp/importar_base.sh' | Out-Null
docker exec -u root sge-jd-bd2 bash -c "chown oracle:oinstall /tmp/base.dmp /tmp/importar_base.sh; sed -i 's/\r$//' /tmp/importar_base.sh"
docker exec sge-jd-bd2 bash /tmp/importar_base.sh
}

$pasos = @(
    @{ s = 1; f = 'S01\S01_01_esquema.sql'; a = @{ Dba = $true; ClaveApp = $true } },
    @{ s = 1; f = 'S01\S01_02_tablas.sql'; a = @{} },
    @{ s = 1; f = 'S01\S01_03_plsql.sql'; a = @{} },
    @{ s = 2; f = 'S02\S02_01_auditoria.sql'; a = @{} },
    @{ s = 2; f = 'S02\S02_02_triggers.sql'; a = @{} },
    @{ s = 3; f = 'S03\S03_01_log_errores.sql'; a = @{} },
    @{ s = 3; f = 'S03\S03_03_plsql.sql'; a = @{} }
)
foreach ($p in $pasos | Where-Object { $_.s -le $Hasta }) {
    Write-Host "3) $($p.f)" -ForegroundColor Cyan
    $opciones = $p.a
    $salida = & $sql (Join-Path $raiz "bd2\$($p.f)") @opciones 2>&1
    $errores = $salida | Select-String -Pattern 'ORA-|PLS-|SP2-' | Where-Object { $_ -notmatch 'ORA-01920|ORA-00955|ORA-02275|ORA-02261' }
    if ($errores) { $errores | ForEach-Object { Write-Host "   $_" -ForegroundColor Yellow } }
}
Write-Host 'Base de práctica restaurada.' -ForegroundColor Green
