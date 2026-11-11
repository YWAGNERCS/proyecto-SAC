# BD2 · S3 · Paso 5: la excepción ORA-30006 (fila ocupada) necesita DOS sesiones a la vez.
#   .\bd2\S03\S03_05_bloqueo.ps1
# Sesión A (en segundo plano): bloquea el producto con un UPDATE sin confirmar y espera 20 s.
# Sesión B: intenta vender ese producto; SP_REGISTRAR_VENTA espera 10 s (FOR UPDATE WAIT 10) y responde ORA-20017.
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path "$PSScriptRoot\..\..").Path
$sql  = Join-Path $raiz 'bd2\herramientas\sql.ps1'
$a_sql = Join-Path $PSScriptRoot 'S03_05a_sesion_bloquea.sql'
$b_sql = Join-Path $PSScriptRoot 'S03_05b_sesion_vende.sql'

$job = Start-Job -ScriptBlock { param($s, $f) & $s $f } -ArgumentList $sql, $a_sql
Start-Sleep -Seconds 5            # que la sesión A tome el bloqueo primero
& $sql $b_sql
Wait-Job $job | Out-Null
Receive-Job $job
Remove-Job $job
