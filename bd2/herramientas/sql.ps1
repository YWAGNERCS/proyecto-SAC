# Ejecuta un script .sql en la base de práctica de BD2 (contenedor sge-jd-bd2) con sqlplus y muestra la salida.
#   .\bd2\herramientas\sql.ps1 bd2\S01\S01_02_plsql.sql            -> como JDREFRIGERACION (propietario)
#   .\bd2\herramientas\sql.ps1 bd2\S01\S01_01_esquema.sql -Dba     -> como SYSTEM en FREEPDB1
#   .\bd2\herramientas\sql.ps1 bd2\S01\S01_04_app.sql -Usuario JD_APP
# Las claves salen de bd2\.env.bd2 (no se imprimen ni se versionan).
param(
    [Parameter(Mandatory)][string]$Archivo,
    [switch]$Dba,
    [switch]$ClaveApp,
    [string]$Usuario = 'jdrefrigeracion',
    [string]$Contenedor = 'sge-jd-bd2'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$envFile = Join-Path $PSScriptRoot '..\.env.bd2'
$claves = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*([A-Z_0-9]+)=(.*)$' } | ForEach-Object { $claves[$Matches[1]] = $Matches[2].Trim() }

if ($Dba) { $u = 'system'; $pw = $claves['BD2_ORACLE_PASSWORD'] }
elseif ($Usuario -ieq 'jdrefrigeracion') { $u = 'jdrefrigeracion'; $pw = $claves['BD2_OWNER_PASSWORD'] }
elseif ($Usuario -ieq 'jd_app') { $u = 'jd_app'; $pw = $claves['BD2_APP_PASSWORD'] }
else { throw "Usuario sin clave en .env.bd2: $Usuario" }

$nombre = [IO.Path]::GetFileName($Archivo)
# Se copia en UTF-8 sin BOM y con saltos LF para que sqlplus lo lea igual que en Linux
$texto = (Get-Content -Raw -Encoding UTF8 $Archivo) -replace "`r`n", "`n"
$tmp = Join-Path $env:TEMP $nombre
[IO.File]::WriteAllText($tmp, $texto, (New-Object System.Text.UTF8Encoding $false))
docker cp $tmp "${Contenedor}:/tmp/$nombre" | Out-Null
Remove-Item $tmp
# -ClaveApp pasa la clave de JD_APP como &1 (para crear el usuario sin escribirla en el script)
$arg = if ($ClaveApp) { $claves['BD2_APP_PASSWORD'] } else { '' }
docker exec -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 -e PW=$pw -e APW=$arg $Contenedor bash -c "sqlplus -s -l $u/`"`$PW`"@localhost:1521/FREEPDB1 @/tmp/$nombre `"`$APW`" < /dev/null"
docker exec -u root $Contenedor rm -f "/tmp/$nombre" | Out-Null
