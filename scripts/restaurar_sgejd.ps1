# Vuelve la base de SGE-JD al estado de un respaldo hecho con respaldar_sgejd.ps1 (Oracle Data Pump, impdp).
#   restaurar_sgejd.bat C:\SGE-JD\respaldos\sgejd_20261025_1146.dmp
#   -Esquema JD_VERIF   restaura en otro esquema (para revisar un respaldo sin tocar JDREFRIGERACION)
# Sobre JDREFRIGERACION reemplaza las tablas por las del archivo, así que antes saca un respaldo de lo actual.
param(
    [Parameter(Mandatory = $true)][string]$Archivo,
    [string]$Esquema = 'JDREFRIGERACION'
)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$Esquema = $Esquema.ToUpper()
$real = $Esquema -eq 'JDREFRIGERACION'

if (-not (Test-Path $Archivo)) { Write-Host "✖ No existe $Archivo" -ForegroundColor Red; exit 1 }
if ((docker inspect -f '{{.State.Running}}' SGE-JD 2>$null) -ne 'true') {
    Write-Host '✖ Oracle (contenedor SGE-JD) está apagado. Enciéndalo con iniciar_sgejd.bat.' -ForegroundColor Red; exit 1
}
if ($real) {
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
        Write-Host '✖ El backend está encendido. Apáguelo primero: detener_sgejd.bat -DejarBaseDatos' -ForegroundColor Red; exit 1
    }
    Write-Host "Esto REEMPLAZA los datos actuales por los de $(Split-Path $Archivo -Leaf)." -ForegroundColor Red
    if ((Read-Host 'Escriba RESTAURAR para continuar') -cne 'RESTAURAR') { Write-Host 'Cancelado. No se cambió nada.'; exit 0 }
    & "$PSScriptRoot\respaldar_sgejd.ps1" -Nombre ('sgejd_antes_de_restaurar_' + (Get-Date -Format 'yyyyMMdd_HHmm'))
    if ($LASTEXITCODE -ne 0) { Write-Host '✖ Sin respaldo de lo actual no se restaura.' -ForegroundColor Red; exit 1 }
}

# DATA_PUMP_DIR de la PDB es la única subcarpeta de dpdump (lleva el GUID de FREEPDB1)
$dir = "$(docker exec SGE-JD bash -c 'ls -d /opt/oracle/admin/FREE/dpdump/*/ | head -1')".Trim()
$nombre = 'restaurar_' + (Get-Date -Format 'yyyyMMdd_HHmmss')
docker cp $Archivo "SGE-JD:$dir$nombre.dmp" | Out-Null
docker exec -u root SGE-JD chown oracle:oinstall "$dir$nombre.dmp" | Out-Null

$opciones = if ($real) { 'table_exists_action=replace' } else { "remap_schema=JDREFRIGERACION:$Esquema" }
$par = Join-Path $env:TEMP "$nombre.par"
[IO.File]::WriteAllText($par, "userid=`"/ as sysdba`"`ndirectory=DATA_PUMP_DIR`ndumpfile=$nombre.dmp`nlogfile=$nombre.log`nschemas=JDREFRIGERACION`n$opciones`n")
docker cp $par "SGE-JD:/tmp/$nombre.par" | Out-Null
Remove-Item $par
Write-Host "Restaurando en $Esquema ..." -ForegroundColor Cyan
docker exec -e ORACLE_PDB_SID=FREEPDB1 SGE-JD impdp "parfile=/tmp/$nombre.par" *> $null
$codigo = $LASTEXITCODE
docker exec -u root SGE-JD rm -f "/tmp/$nombre.par" "$dir$nombre.dmp" | Out-Null

# impdp termina con 5 cuando hubo avisos (por ejemplo, el usuario ya existía); el resto se revisa en el registro
docker exec SGE-JD tail -3 "$dir$nombre.log"
if ($codigo -ne 0 -and $codigo -ne 5) { Write-Host "✖ impdp terminó con código $codigo (registro: $dir$nombre.log)" -ForegroundColor Red; exit 1 }
Write-Host "✔ Restaurado en $Esquema." -ForegroundColor Green
