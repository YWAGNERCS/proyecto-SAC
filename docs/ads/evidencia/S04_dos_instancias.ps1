# ADS · S4 · Evidencia de escalabilidad horizontal: dos instancias del backend sobre la misma Oracle.
#   cd C:\SGE-JD\bomerp-backend; mvn -q -DskipTests package
#   .\docs\ads\evidencia\S04_dos_instancias.ps1
#
# Usa la base de PRÁCTICA de BD2 (sge-jd-bd2, puerto 1523; debe estar encendida) para no tocar desarrollo.
# Nada sale de la PC: SUNAT en modo simulado, el correo apunta a 127.0.0.1:9 (nadie escucha) y el esquema
# solo se valida (ddl-auto=validate). Las variables de entorno tienen prioridad sobre application-local.properties.
# OJO: la tarea emitirVentasPorEmitir emite comprobantes (simulados) para las ventas sin comprobante de la base
# de práctica (la carga de BD2 S4). Para volver al estado anterior, restaurar la base (bd2/README.md).
param([int]$Segundos = 150)
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$jar  = Join-Path $raiz 'target\bomerp-backend-0.0.1-SNAPSHOT.jar'
$logs = Join-Path $raiz 'bd2\salidas'
New-Item -ItemType Directory -Force $logs | Out-Null
$claves = @{}
Get-Content (Join-Path $raiz 'bd2\.env.bd2') | Where-Object { $_ -match '^\s*([A-Z_0-9]+)=(.*)$' } |
    ForEach-Object { $claves[$Matches[1]] = $Matches[2].Trim() }

$env:DB_URL = 'jdbc:oracle:thin:@localhost:1523/FREEPDB1'
$env:DB_USERNAME = 'jdrefrigeracion'
$env:SPRING_DATASOURCE_PASSWORD = $claves['BD2_OWNER_PASSWORD']
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:APP_FACTURACION_MODO = 'simulado'
$env:SPRING_MAIL_HOST = '127.0.0.1'
$env:SPRING_MAIL_PORT = '9'
$env:APP_CORREO_REVISION_INICIAL_MS = '86400000'      # sin reintentos de correo durante la prueba
$env:APP_FACTURACION_REINTENTO_INICIAL_MS = '20000'   # el reintento a SUNAT (simulado) corre a los 20 s

$procesos = foreach ($puerto in 8081, 8082) {
    Start-Process java -ArgumentList '-Xmx512m', '-jar', "`"$jar`"", "--server.port=$puerto" `
        -RedirectStandardOutput (Join-Path $logs "S04_backend_$puerto.log") `
        -RedirectStandardError (Join-Path $logs "S04_backend_$puerto.err") -PassThru -WindowStyle Hidden
}
Write-Host "Instancias iniciadas (PID $($procesos.Id -join ', ')). Esperando que arranquen..."
try {
    $t = 0
    do { Start-Sleep 5; $t += 5
         $listas = @(8081, 8082 | Where-Object { try { (Invoke-WebRequest -UseBasicParsing "http://localhost:$_/actuator/health" -TimeoutSec 3).StatusCode -eq 200 } catch { $false } })
    } until ($listas.Count -eq 2 -or $t -ge 180)
    foreach ($puerto in 8081, 8082) {
        try { $r = Invoke-WebRequest -UseBasicParsing "http://localhost:$puerto/actuator/health" -TimeoutSec 5; "GET :$puerto/actuator/health -> $($r.StatusCode) $($r.Content)" }
        catch { "GET :$puerto/actuator/health -> $($_.Exception.Message)" }
        try { Invoke-WebRequest -UseBasicParsing "http://localhost:$puerto/api/v1/admin/ventas?size=1" -TimeoutSec 5 | Out-Null; "GET :$puerto/api/v1/admin/ventas -> 200 (sin token: no debería)" }
        catch { "GET :$puerto/api/v1/admin/ventas sin token -> $([int]$_.Exception.Response.StatusCode) (cada instancia exige el JWT por su cuenta)" }
    }
    Write-Host "Las dos responden. Se dejan correr para ver las tareas programadas..."
    Start-Sleep ([Math]::Max(0, $Segundos - $t))
} finally {
    $procesos | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    Write-Host 'Instancias detenidas.'
}
foreach ($puerto in 8081, 8082) {
    Write-Host "----- Tareas en la instancia $puerto -----"
    Select-String -Path (Join-Path $logs "S04_backend_$puerto.log") -Pattern 'Started BomerpApplication|Proformas vencidas|Reintento de env|SUNAT|Tomcat started' |
        ForEach-Object { $_.Line -replace '^\S+\s+', '' }
}
