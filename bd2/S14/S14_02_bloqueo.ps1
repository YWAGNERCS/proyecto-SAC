# BD2 · S14 · Paso 2: bloqueo real entre dos sesiones del backend (JD_APP) y su diagnóstico desde una tercera.
#   .\bd2\S14\S14_02_bloqueo.ps1 -Venta 182            -> B espera sin límite (UPDATE simple)
#   .\bd2\S14\S14_02_bloqueo.ps1 -Venta 194 -Backend   -> B usa FOR UPDATE WAIT 10, como VentaRepository.bloquear()
param([int]$Venta = 182, [int]$Retiene = 45, [switch]$Backend)
$ErrorActionPreference = 'Continue'
$sql = Join-Path $PSScriptRoot '..\herramientas\sql.ps1'
$dir = $PSScriptRoot
$a = Start-Job { param($s, $d, $v, $r) $f = Join-Path $d 'S14_02a_cobro_lento.sql'; $t = (Get-Content -Raw -Encoding UTF8 $f) -replace '&1', $v -replace '&2', $r; $tmp = Join-Path $env:TEMP 'S14_a.sql'; [IO.File]::WriteAllText($tmp, $t); & $s $tmp -Usuario jd_app } -ArgumentList $sql, $dir, $Venta, $Retiene
Start-Sleep 3
$archivoB = if ($Backend) { 'S14_02c_espera_backend.sql' } else { 'S14_02b_anulacion.sql' }
$b = Start-Job { param($s, $d, $v, $n) $f = Join-Path $d $n; $t = (Get-Content -Raw -Encoding UTF8 $f) -replace '&1', $v; $tmp = Join-Path $env:TEMP 'S14_b.sql'; [IO.File]::WriteAllText($tmp, $t); & $s $tmp -Usuario jd_app } -ArgumentList $sql, $dir, $Venta, $archivoB
Start-Sleep 6
Write-Host '########## SESION C: MONITOREO DURANTE EL BLOQUEO ##########'
& $sql (Join-Path $dir 'S14_02m_monitor.sql') -Dba
Wait-Job $a, $b | Out-Null
Write-Host '########## SESION A (cobranza) ##########'; Receive-Job $a
Write-Host "########## SESION B ($archivoB) ##########"; Receive-Job $b
Remove-Job $a, $b
