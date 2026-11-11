# BD2 · S13 · Copia el archivo de zonas horarias que falta en la imagen gvenzl/oracle-free:23 (completa).
#
# Hallazgo S13: la base se creó con 23-slim, que trae timezlrg_45.dat. La imagen completa solo trae
# timezlrg_43.dat, así que al abrir con ella V$TIMEZONE_NAMES queda vacío y FREEPDB1 abre "with errors or warnings".
# Si se usara una columna TIMESTAMP WITH TIME ZONE con una región, Oracle respondería ORA-01804.
#
# El archivo se copia al ORACLE_HOME, que vive en la capa del contenedor y NO en el volumen:
# hay que repetir este script cada vez que se recree el contenedor (docker compose down / up, cambio de imagen).
#
# Uso (con sge-jd-bd2 encendido):   .\bd2\herramientas\copiar_zona_horaria.ps1
$ErrorActionPreference = 'Continue'
$archivo = 'timezlrg_45.dat'
$destino = '/opt/oracle/product/26ai/dbhomeFree/oracore/zoneinfo'
$temporal = Join-Path $PSScriptRoot "..\salidas\$archivo"
New-Item -ItemType Directory -Force (Split-Path $temporal) | Out-Null

$ya = docker exec sge-jd-bd2 bash -c "test -f $destino/$archivo && echo si"
if ($ya -eq 'si') { Write-Host "El contenedor ya tiene $archivo. No hay nada que copiar."; exit 0 }

Write-Host "Extrayendo $archivo de la imagen gvenzl/oracle-free:23-slim..."
$id = docker create --entrypoint bash gvenzl/oracle-free:23-slim
docker cp "${id}:$destino/$archivo" $temporal
docker rm $id | Out-Null
if (-not (Test-Path $temporal)) { Write-Host "No se pudo extraer el archivo (¿está descargada la imagen 23-slim?)."; exit 1 }

docker cp $temporal "sge-jd-bd2:$destino/$archivo"
docker exec -u root sge-jd-bd2 bash -c "chown oracle:oinstall $destino/$archivo && chmod 644 $destino/$archivo"
Write-Host "Copiado. Reinicie la instancia para que lo lea:  docker restart sge-jd-bd2"
