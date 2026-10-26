# Respaldo completo del esquema JDREFRIGERACION con Oracle Data Pump (expdp).
# Uso: doble clic en respaldar_sgejd.bat. Deja el archivo en C:\SGE-JD\respaldos (fuera de los repositorios).
# Restaurar (reemplaza las tablas por las del respaldo): ver "Respaldo y restauración" en /docs.
param(
    [string]$Carpeta = (Join-Path (Resolve-Path "$PSScriptRoot\..\..").Path 'respaldos'),
    [string]$Nombre = ('sgejd_' + (Get-Date -Format 'yyyyMMdd_HHmm'))
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$dentro = '/opt/oracle/admin/FREE/dpdump'

if ((docker inspect -f '{{.State.Running}}' SGE-JD 2>$null) -ne 'true') {
    Write-Host '✖ Oracle (contenedor SGE-JD) está apagado. Enciéndalo con iniciar_sgejd.bat.' -ForegroundColor Red; exit 1
}
New-Item -ItemType Directory -Force $Carpeta | Out-Null
Write-Host "Respaldando JDREFRIGERACION como $Nombre.dmp ..." -ForegroundColor Cyan

# Los parámetros van en un parfile para no pelear con las comillas de "/ as sysdba".
# ORACLE_PDB_SID hace que esa conexión entre directo a FREEPDB1.
$par = Join-Path $env:TEMP "$Nombre.par"
[IO.File]::WriteAllText($par, "userid=`"/ as sysdba`"`nschemas=JDREFRIGERACION`ndirectory=DATA_PUMP_DIR`ndumpfile=$Nombre.dmp`nlogfile=$Nombre.log`n")
docker cp $par "SGE-JD:/tmp/$Nombre.par" | Out-Null
Remove-Item $par
docker exec -e ORACLE_PDB_SID=FREEPDB1 SGE-JD expdp "parfile=/tmp/$Nombre.par" *> $null
$codigo = $LASTEXITCODE
docker exec -u root SGE-JD rm -f "/tmp/$Nombre.par" | Out-Null
# DATA_PUMP_DIR de la PDB es una subcarpeta con el GUID de FREEPDB1
$ruta = "$(docker exec SGE-JD bash -c "ls $dentro/*/$Nombre.dmp 2>/dev/null")".Trim()
if ($codigo -ne 0 -or -not $ruta) {
    Write-Host '✖ expdp no terminó bien. Registro:' -ForegroundColor Red
    docker exec SGE-JD bash -c "cat $dentro/*/$Nombre.log"; exit 1
}
docker cp "SGE-JD:$ruta" (Join-Path $Carpeta "$Nombre.dmp") | Out-Null
docker cp "SGE-JD:$($ruta -replace '\.dmp$', '.log')" (Join-Path $Carpeta "$Nombre.log") | Out-Null
docker exec SGE-JD bash -c "rm -f $dentro/*/$Nombre.dmp" | Out-Null   # la copia ya está en $Carpeta
$kb = [int]((Get-Item (Join-Path $Carpeta "$Nombre.dmp")).Length / 1KB)
Write-Host "✔ Respaldo listo: $(Join-Path $Carpeta "$Nombre.dmp") ($kb KB)" -ForegroundColor Green
