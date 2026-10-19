# Bloque 2 · Catálogo (módulo no transaccional): Lucy (ASISTENTE) consulta, pero no puede modificar.
. "$PSScriptRoot\comun.ps1"
$lucy = (Get-Token 'lucy').access_token

Write-Paso 'Sin token: GET /admin/categorias'
Write-Resultado (Invoke-Api 'GET' '/admin/categorias' $null) 401

Write-Paso 'ASISTENTE (lucy): GET /admin/productos  -> consulta permitida'
$r = Invoke-Api 'GET' '/admin/productos' $lucy
Write-Host ("HTTP {0}  (esperado 200)" -f $r.Codigo) -ForegroundColor Green
$productos = $r.Cuerpo | ConvertFrom-Json
Write-Host ("{0} productos. Primeros 3:" -f $productos.Count)
$productos | Select-Object -First 3 id, nombre, sector | Format-Table -AutoSize | Out-Host

Write-Paso 'ASISTENTE (lucy): POST /admin/categorias  -> modificación denegada'
Write-Resultado (Invoke-Api 'POST' '/admin/categorias' $lucy @{ nombre = 'Prueba S10'; descripcion = 'Intento de la asistente' }) 403

Write-Paso 'ASISTENTE (lucy): DELETE /admin/productos/1  -> eliminación denegada'
Write-Resultado (Invoke-Api 'DELETE' '/admin/productos/1' $lucy) 403
