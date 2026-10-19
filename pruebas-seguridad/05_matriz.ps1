# Bloque 5 · Matriz de accesos del catálogo y de las reglas del negocio, con tokens reales de Keycloak.
. "$PSScriptRoot\comun.ps1"
$lucy = (Get-Token 'lucy').access_token
$lisandro = (Get-Token 'lisandro').access_token
$cat = @{ nombre = 'Prueba matriz S10'; descripcion = 'Temporal' }
$filas = New-Object System.Collections.ArrayList

function Fila($caso, $quien, $metodo, $ruta, $token, $esperado, $cuerpo) {
    $r = Invoke-Api $metodo $ruta $token $cuerpo
    [void]$filas.Add([pscustomobject]@{
        '#' = $caso; Usuario = $quien; 'Petición' = "$metodo $ruta"; Esp = $esperado; Obt = $r.Codigo
        OK = $(if ($esperado -split '/' -contains "$($r.Codigo)") { 'si' } else { 'NO' })
    })
    return $r
}

Fila 1 'sin token'          GET    '/admin/categorias' $null     '401' | Out-Null
Fila 2 'sin token'          POST   '/admin/categorias' $null     '401' $cat | Out-Null
Fila 3 'lucy'   GET    '/admin/categorias' $lucy     '200' | Out-Null
Fila 4 'lucy'   POST   '/admin/categorias' $lucy     '403' $cat | Out-Null
$creada = Fila 5 'lisandro' POST '/admin/categorias' $lisandro '201' $cat
$id = ($creada.Cuerpo | ConvertFrom-Json).id
Fila 6 'lucy'   PUT    "/admin/categorias/$id" $lucy     '403' $cat | Out-Null
Fila 7 'lucy'   DELETE "/admin/categorias/$id" $lucy     '403' | Out-Null
Fila 8 'lisandro'   PUT    "/admin/categorias/$id" $lisandro '200' $cat | Out-Null
Fila 9 'lisandro'   DELETE "/admin/categorias/$id" $lisandro '204' | Out-Null
Fila 10 'alterado'    GET    '/admin/categorias' ($lucy.Substring(0, $lucy.Length - 2) + 'xx') '401' | Out-Null
Fila 11 'lucy'  PATCH  '/admin/ventas/999999/anular' $lucy     '403' | Out-Null
Fila 12 'lisandro'  PATCH  '/admin/ventas/999999/anular' $lisandro '404' | Out-Null
Fila 13 'lucy'  GET    '/admin/ventas' $lucy     '200' | Out-Null
Fila 14 'sin token'         GET    '/publico/catalogo' $null '200' | Out-Null

Write-Paso 'Matriz de accesos · lucy = ASISTENTE · lisandro = ADMIN'
$filas | Format-Table -AutoSize | Out-Host
Write-Host ("{0} de {1} casos con el código esperado" -f ($filas | Where-Object OK -eq 'si').Count, $filas.Count) -ForegroundColor Green
