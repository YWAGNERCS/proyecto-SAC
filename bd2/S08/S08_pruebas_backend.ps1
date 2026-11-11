# BD2 · S8 · Corre las pruebas de integración del backend que usan Oracle (@Tag("oracle"): ventas, anulación,
# comprobantes, reemplazos, correo, cobros, mantenimiento…) contra la base de PRÁCTICA, con el usuario indicado.
#   .\bd2\S08\S08_pruebas_backend.ps1 -Usuario jdrefrigeracion     # línea base: el dueño de las tablas
#   .\bd2\S08\S08_pruebas_backend.ps1 -Usuario jd_app              # el usuario técnico, con sus roles
# Nada sale de la PC: SUNAT simulado, correo a 127.0.0.1:9, esquema solo validado.
# Con JD_APP, Hibernate nombra las tablas con el esquema dueño (hibernate.default_schema = JDREFRIGERACION).
param([Parameter(Mandatory)][ValidateSet('jdrefrigeracion', 'jd_app')][string]$Usuario)
$ErrorActionPreference = 'Continue'   # Maven y Mockito escriben avisos en stderr: con 'Stop', PowerShell 5.1 se corta
$raiz = (Resolve-Path "$PSScriptRoot\..\..").Path
$claves = @{}
Get-Content (Join-Path $raiz 'bd2\.env.bd2') | Where-Object { $_ -match '^\s*([A-Z_0-9]+)=(.*)$' } |
    ForEach-Object { $claves[$Matches[1]] = $Matches[2].Trim() }

$env:DB_URL = 'jdbc:oracle:thin:@localhost:1523/FREEPDB1'
$env:DB_USERNAME = $Usuario
$env:SPRING_DATASOURCE_PASSWORD = if ($Usuario -eq 'jd_app') { $claves['BD2_APP_PASSWORD'] } else { $claves['BD2_OWNER_PASSWORD'] }
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
# OJO: SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA NO sirve. En las claves de un mapa, Spring convierte cada "_"
# en "." y queda "hibernate.default.schema", que Hibernate no conoce (primera corrida: "missing table").
# SPRING_APPLICATION_JSON pasa el nombre exacto de la propiedad.
# Y para el SQL que no pasa por Hibernate (JdbcTemplate de las pruebas): cada conexión del pool nace apuntando al
# esquema dueño. Segunda corrida: sin esto, "ORA-00942: La tabla o vista JD_APP.CLIENTES no existe". Tercera:
# SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL tampoco sirve (queda "connection.init.sql"): va en el mismo JSON.
$env:SPRING_APPLICATION_JSON = if ($Usuario -eq 'jd_app') {
    '{"spring.jpa.properties.hibernate.default_schema":"JDREFRIGERACION","spring.datasource.hikari.connection-init-sql":"ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION"}'
} else { '{}' }
Remove-Item Env:SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL -ErrorAction SilentlyContinue
$env:APP_FACTURACION_MODO = 'simulado'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '9'

Push-Location $raiz
try {
    mvn -q test "-Dgroups=oracle" 2>&1 | Out-Null
    $codigo = $LASTEXITCODE
} finally { Pop-Location }

$informes = Get-ChildItem (Join-Path $raiz 'target\surefire-reports') -Filter 'TEST-*.xml'
$filas = foreach ($f in $informes) {
    [xml]$x = Get-Content $f.FullName
    $s = $x.testsuite
    if ([int]$s.tests -eq 0) { continue }
    $fallos = @($s.testcase | Where-Object { $_.failure -or $_.error })
    [pscustomobject]@{
        Clase   = ($s.name -split '\.')[-1]
        Pruebas = [int]$s.tests
        Fallan  = [int]$s.failures + [int]$s.errors
        Detalle = ($fallos | ForEach-Object {
                $m = if ($_.error) { $_.error.message } else { $_.failure.message }
                "$($_.name): " + ($m -replace '\s+', ' ').Substring(0, [Math]::Min(220, ($m -replace '\s+', ' ').Length))
            }) -join ' || '
    }
}
"Usuario: $Usuario   (mvn terminó con código $codigo)"
$filas | Sort-Object Clase | Format-Table Clase, Pruebas, Fallan -AutoSize | Out-String -Width 200
"TOTAL: $(($filas | Measure-Object Pruebas -Sum).Sum) pruebas, $(($filas | Measure-Object Fallan -Sum).Sum) fallan"
$filas | Where-Object Fallan -gt 0 | ForEach-Object { "--- $($_.Clase): $($_.Detalle)" }
