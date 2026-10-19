# Bloque 3 · La misma modificación, ahora con el token de Lisandro (ADMIN): permitida.
. "$PSScriptRoot\comun.ps1"
$lisandro = (Get-Token 'lisandro').access_token

Write-Paso 'ADMIN (lisandro): POST /admin/categorias  -> creada'
$r = Invoke-Api 'POST' '/admin/categorias' $lisandro @{ nombre = 'Prueba S10'; descripcion = 'Creada por el administrador' }
Write-Resultado $r 201, 200
$id = ($r.Cuerpo | ConvertFrom-Json).id

Write-Paso "ADMIN (lisandro): PUT /admin/categorias/$id  -> editada"
Write-Resultado (Invoke-Api 'PUT' "/admin/categorias/$id" $lisandro @{ nombre = 'Prueba S10 (editada)'; descripcion = 'Editada por el administrador' }) 200

Write-Paso "ADMIN (lisandro): DELETE /admin/categorias/$id  -> eliminada (limpieza de la prueba)"
Write-Resultado (Invoke-Api 'DELETE' "/admin/categorias/$id" $lisandro) 204, 200
