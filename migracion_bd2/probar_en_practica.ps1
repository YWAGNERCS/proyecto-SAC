# Corre TODAS las pruebas del backend contra la base de practica (sge-jd-bd2, puerto 1523), ya migrada.
# Es el ensayo de la integracion con LP2: si pasa aqui, la migracion y el codigo nuevo se pueden llevar a desarrollo.
#   .\migracion_bd2\probar_en_practica.ps1 -Usuario jdrefrigeracion   # como entra desarrollo
#   .\migracion_bd2\probar_en_practica.ps1 -Usuario jd_app            # como entraria produccion (BD2 S8)
# Nada sale de la PC: SUNAT simulado, correo a 127.0.0.1:9, esquema solo validado.
param([Parameter(Mandatory)][ValidateSet('jdrefrigeracion', 'jd_app')][string]$Usuario)
$ErrorActionPreference = 'Continue'
$raiz = (Resolve-Path "$PSScriptRoot\..").Path
$claves = @{}
Get-Content (Join-Path $raiz 'bd2\.env.bd2') | Where-Object { $_ -match '^\s*([A-Z_0-9]+)=(.*)$' } |
    ForEach-Object { $claves[$Matches[1]] = $Matches[2].Trim() }
$env:DB_URL = 'jdbc:oracle:thin:@localhost:1523/FREEPDB1'
$env:DB_USERNAME = $Usuario
$env:SPRING_DATASOURCE_PASSWORD = if ($Usuario -eq 'jd_app') { $claves['BD2_APP_PASSWORD'] } else { $claves['BD2_OWNER_PASSWORD'] }
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
# Nombres exactos de propiedad (ver BD2 S8): por JSON, no por variables con "_"
$env:SPRING_APPLICATION_JSON = '{"spring.jpa.properties.hibernate.default_schema":"JDREFRIGERACION","spring.datasource.hikari.connection-init-sql":"ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION"}'
$env:APP_FACTURACION_MODO = 'simulado'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '9'
Push-Location $raiz
try { mvn -q test 2>&1 | Out-Null } finally { Pop-Location }
$total = 0; $fallas = 0; $errores = 0; $saltadas = 0; $malas = @()
foreach ($f in Get-ChildItem (Join-Path $raiz 'target\surefire-reports') -Filter 'TEST-*.xml') {
    [xml]$x = Get-Content $f.FullName
    $s = $x.testsuite
    $total += [int]$s.tests; $fallas += [int]$s.failures; $errores += [int]$s.errors; $saltadas += [int]$s.skipped
    foreach ($c in $s.testcase) {
        if ($c.failure -or $c.error) {
            $m = if ($c.failure) { $c.failure.message } else { $c.error.message }
            $malas += "$($c.classname.Split('.')[-1]).$($c.name): $(([string]$m).Split("`n")[0])"
        }
    }
}
Write-Host "Usuario $Usuario -> pruebas: $total, fallas: $fallas, errores: $errores, omitidas: $saltadas"
$malas | ForEach-Object { Write-Host "  - $_" }
