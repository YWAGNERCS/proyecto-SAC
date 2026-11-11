# Migracion BD2 -> base real: aplica en orden los 7 scripts de esta carpeta sobre un contenedor Oracle.
# Entra como SYSDBA dentro del contenedor (autenticacion del sistema operativo): no necesita la clave de SYS.
#
#   .\migracion_bd2\migrar.ps1 -Contenedor sge-jd-bd2 -Secretos bd2\.env.bd2     # ensayo en la base de practica
#   .\migracion_bd2\migrar.ps1 -Contenedor SGE-JD     -Secretos .env              # desarrollo (4200)
#   .\migracion_bd2\migrar.ps1 -Contenedor sge-jd-oracle-prod -Secretos .env.prod # produccion (4300): solo con respaldo previo
#
# Antes: respaldo con scripts\respaldar_sgejd.ps1 y backend detenido (el paso 1 mueve tablas).
# Las claves de JD_APP y JD_REPORTES salen del archivo de secretos (JD_APP_PASSWORD, JD_REPORTES_PASSWORD).
# Si no estan, se generan al azar y se agregan al archivo. Nunca se imprimen.
# Cada script se puede repetir. Si uno falla, se detiene ahi y muestra su salida.
param(
    [Parameter(Mandatory)][string]$Contenedor,
    [Parameter(Mandatory)][string]$Secretos,
    [int]$Desde = 1,
    [int]$Hasta = 7
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$dir = $PSScriptRoot
$raiz = (Resolve-Path "$dir\..").Path
$archivoSecretos = if ([IO.Path]::IsPathRooted($Secretos)) { $Secretos } else { Join-Path $raiz $Secretos }
if (-not (Test-Path $archivoSecretos)) { Write-Host "No existe $archivoSecretos" -ForegroundColor Red; exit 1 }
if ((docker inspect -f '{{.State.Running}}' $Contenedor 2>$null) -ne 'true') {
    Write-Host "El contenedor $Contenedor no esta encendido." -ForegroundColor Red; exit 1
}

function Leer-Secretos {
    $c = @{}
    Get-Content -Encoding UTF8 $archivoSecretos | Where-Object { $_ -match '^\s*([A-Z_0-9]+)=(.*)$' } |
        ForEach-Object { $c[$Matches[1]] = $Matches[2].Trim() }
    return $c
}
function Clave-Nueva {
    $bytes = New-Object byte[] 18
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    # Oracle: empieza con letra, sin comillas ni @; solo letras y digitos
    return 'J' + (([Convert]::ToBase64String($bytes)) -replace '[^A-Za-z0-9]', 'x')
}
$claves = Leer-Secretos
foreach ($k in 'JD_APP_PASSWORD', 'JD_REPORTES_PASSWORD') {
    if (-not $claves[$k]) {
        $alterna = if ($k -eq 'JD_APP_PASSWORD') { $claves['BD2_APP_PASSWORD'] } else { $claves['BD2_APP_PASSWORD'] }
        if ($alterna) { $claves[$k] = $alterna }
        else {
            Add-Content -Encoding UTF8 $archivoSecretos "$k=$(Clave-Nueva)"
            Write-Host "  $k no existia: se genero y se guardo en $Secretos" -ForegroundColor Yellow
            $claves = Leer-Secretos
        }
    }
}

$scripts = Get-ChildItem $dir -Filter '*.sql' | Sort-Object Name |
    Where-Object { [int]($_.Name.Substring(0, 2)) -ge $Desde -and [int]($_.Name.Substring(0, 2)) -le $Hasta }
Write-Host "Migracion BD2 sobre $Contenedor ($($scripts.Count) scripts)" -ForegroundColor Cyan
foreach ($s in $scripts) {
    Write-Host "== $($s.Name)" -ForegroundColor Cyan
    $texto = (Get-Content -Raw -Encoding UTF8 $s.FullName) -replace "`r`n", "`n"
    $tmp = Join-Path $env:TEMP $s.Name
    [IO.File]::WriteAllText($tmp, $texto, (New-Object System.Text.UTF8Encoding $false))
    docker cp $tmp "${Contenedor}:/tmp/$($s.Name)" | Out-Null
    Remove-Item $tmp
    $salida = docker exec -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 -e A1=$($claves['JD_APP_PASSWORD']) -e A2=$($claves['JD_REPORTES_PASSWORD']) $Contenedor `
        bash -c "sqlplus -s -l / as sysdba @/tmp/$($s.Name) `"`$A1`" `"`$A2`" < /dev/null" 2>&1
    $codigo = $LASTEXITCODE
    docker exec -u root $Contenedor rm -f "/tmp/$($s.Name)" | Out-Null
    $salida | Where-Object { $_ -ne '' } | ForEach-Object { Write-Host "   $_" }
    $malo = $salida | Where-Object { $_ -match 'ORA-|SP2-|PLS-|compilation errors|\bINVALID\b' }
    if ($codigo -ne 0 -or $malo) {
        Write-Host "FALLO en $($s.Name) (codigo $codigo). La migracion se detiene aqui." -ForegroundColor Red
        exit 1
    }
}
Write-Host "Migracion BD2 completa en $Contenedor." -ForegroundColor Green
