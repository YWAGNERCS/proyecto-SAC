# SGE-JD en modo producción, en la misma laptop (S13)

Guía para levantar el sistema completo **como correría en producción**: backend empaquetado en una imagen
Docker con el perfil `prod`, su propio Oracle y su propio Keycloak (otros contenedores, volúmenes y puertos que
desarrollo) y el frontend compilado con `ng build --configuration production` servido por nginx.

## Puertos

| Servicio | Desarrollo | Producción local |
|---|---|---|
| Oracle | 1522 (`SGE-JD`) | 41521 (`sge-jd-oracle-prod`) |
| Postgres de Keycloak | 35432 | 45432 |
| Keycloak | 38080 | 48080 (modo `start`, no `start-dev`) |
| Backend | 8080 (`mvn spring-boot:run`, perfil `dev`) | 48088 (contenedor, perfil `prod`) |
| Frontend | 4200 (`ng serve`) | 4300 (nginx con el build de producción) |

Todos los puertos escuchan solo en `127.0.0.1`.

> **Memoria.** Los dos entornos no chocan en puertos, pero cada uno lleva un Oracle (~1,5 GB) y un Keycloak
> (~0,5 GB). En la laptop de desarrollo (7,4 GB de RAM, 3,5 GB para Docker) no caben los dos a la vez: se apaga
> uno para encender el otro (`docker stop`, no se pierde nada).

## Archivos

| Archivo | Para qué |
|---|---|
| `src/main/resources/application-prod.properties` | Perfil `prod`: todo por variables de entorno, sin valores por defecto; `ddl-auto=validate`; Swagger apagado; Actuator solo `health,info` |
| `src/main/resources/application-dev.properties` | Perfil `dev` (el de siempre, activo por defecto) |
| `src/main/resources/logback-spring.xml` | `DEBUG` + SQL en dev, `INFO` sin SQL en prod |
| `Dockerfile`, `.dockerignore` | Imagen del backend (compila con Maven, la final solo lleva JRE + JAR, usuario sin privilegios, hora de Lima) |
| `compose-prod.yml` | Oracle, Keycloak, su Postgres y el backend de prod |
| `.env.prod.example` | Plantilla de secretos de prod (el `.env.prod` real no se versiona) |
| `scripts/prod/generar_env_prod.py` | Crea `.env.prod` con secretos aleatorios, sin mostrarlos |
| `scripts/prod/esquema_jdrefrigeracion.sql` | Esquema vacío (29 tablas, índices y claves foráneas), sacado del esquema real con `DBMS_METADATA`; al 07/11/2026 incluye `migracion_comprobante_reemplazo.sql` (comprobante de reemplazo, receptor fijo y `ENVIOS_SUNAT`) |
| `keycloak/configurar_realm.py --prod` | El mismo script de dev contra el Keycloak de prod (SPA en 4300, sin el cliente de pruebas) |
| `bomerp-frontend/src/environments/environment.prod.ts` | URLs de prod del SPA (reemplaza `environment.ts` en el build) |
| `bomerp-frontend/Dockerfile`, `nginx.conf`, `compose-prod.yml` | El build servido por nginx en 4300 |

## Pasos (PowerShell, desde `bomerp-backend`)

**1. Apagar desarrollo** (si está encendido) y crear los secretos de prod (una sola vez):

```powershell
docker stop sge-jd-keycloak sge-jd-keycloak-db SGE-JD
```

```powershell
python scripts/prod/generar_env_prod.py
```

**2. Encender la infraestructura de prod** (la primera vez Oracle tarda unos minutos en crear la base;
esperar a que `docker ps` diga `healthy`):

```powershell
docker compose -f compose-prod.yml --env-file .env.prod up -d oracle keycloak-db keycloak
```

**3. Crear el esquema** (solo la primera vez; sin contraseña: entra como `sysdba` dentro del contenedor):

```powershell
docker cp scripts/prod/esquema_jdrefrigeracion.sql sge-jd-oracle-prod:/tmp/esquema.sql
```

```powershell
"alter session set current_schema=JDREFRIGERACION;`n@/tmp/esquema.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 sge-jd-oracle-prod sqlplus -s / as sysdba
```

Debe decir 26 veces `Table created.`, 10 `Index created.` y 44 `Table altered.` (comprobado el 07/11/2026 en un esquema vacío: mismas columnas, índices y restricciones que la base de desarrollo, que desde la auditoría B7 tampoco tiene la tabla `USUARIOS`).

**Base de producción ya creada antes del 07/11/2026:** en lugar del paso 3, aplicar una vez `migracion_comprobante_reemplazo.sql`, `migracion_venta_anulacion.sql`, `migracion_costos_precios.sql` y `migracion_envio_correo.sql` (se pueden ejecutar varias veces) antes de encender el backend nuevo; si no, `ddl-auto=validate` no lo deja arrancar. Se aplican igual que esta, cambiando el nombre del archivo:

```powershell
docker cp migracion_comprobante_reemplazo.sql sge-jd-oracle-prod:/tmp/m.sql
```

```powershell
"alter session set current_schema=JDREFRIGERACION;`n@/tmp/m.sql" | docker exec -i -e ORACLE_PDB_SID=FREEPDB1 sge-jd-oracle-prod sqlplus -s / as sysdba
```

**4. Crear el realm, los roles, los clientes y los usuarios** (las claves de Lisandro y Lucy quedan en `.env.prod`):

```powershell
python keycloak/configurar_realm.py --prod
```

**5. Construir y encender el backend:**

```powershell
docker compose -f compose-prod.yml --env-file .env.prod up -d --build backend
```

```powershell
docker logs -f sge-jd-backend-prod
```

Debe terminar en `Started BomerpApplication`. Si el esquema no coincide con las entidades, no arranca y dice
qué tabla o columna falta (`ddl-auto=validate`): regenerar el script del paso 3.

**6. Construir y servir el frontend** (desde `bomerp-frontend`):

```powershell
npm run build:prod
```

Compila y verifica que la Content-Security-Policy de nginx permita los dos scripts en línea de `index.html`
(por su huella sha256). Si alguno cambió, termina con error y muestra la línea `script-src` correcta para
`cabeceras-seguridad.conf`; sin ese cambio, el navegador los bloquearía y la página quedaría sin estilos.

```powershell
docker compose -f compose-prod.yml up -d --build
```

Abrir http://localhost:4300 e iniciar sesión con `lisandro` o `lucy` (clave en `.env.prod`).

## Verificación

```powershell
curl http://localhost:48088/actuator/health
```

```powershell
curl -i http://localhost:48088/actuator/env
```

| Prueba | Resultado esperado (verificado el 05/11/2026) |
|---|---|
| `/actuator/health` sin token | 200 `{"status":"UP"}` (sin detalles) |
| `/actuator/env`, `/heapdump`, `/metrics`, `/beans` sin token | 401 |
| Los mismos con token de ADMIN | **404**: en prod no existen |
| `/v3/api-docs`, `/swagger-ui/index.html` | 404 |
| `http://localhost:4300/ventas` (recargar en una ruta del SPA) | 200 (nginx devuelve `index.html`) |
| Login en 4300 → token con `iss` = `http://localhost:48080/realms/bomerp` | Aceptado por el backend |
| Configuración › Usuarios y permisos | Lista los usuarios (API de administración de Keycloak por la red interna) |
| Registrar una venta | Venta y comprobante B001-00000001 emitidos |
| `curl -I http://localhost:4300/` (y un `.js`, un 404) | Cabeceras de seguridad en todas las respuestas: `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`; `Server: nginx` sin versión (07/11/2026) |
| Login, Ventas, Proformas, Cobranza, Postventa, Compras, Productos, Clientes, Órdenes, Historial y cerrar sesión | Sin ningún aviso de la CSP en la consola (07/11/2026) |
| `fetch('https://example.com')` desde la consola de 4300 | Bloqueado por `connect-src` (07/11/2026) |

**Cabeceras de seguridad (auditoría M11):** están en `bomerp-frontend/cabeceras-seguridad.conf`. Al pasar a un
dominio real, cambiar ahí `http://localhost:48088` y `http://localhost:48080` (igual que en `environment.prod.ts`).

## Volver a desarrollo

```powershell
docker compose -f compose-prod.yml --env-file .env.prod stop
```

```powershell
docker compose -f ../bomerp-frontend/compose-prod.yml stop
```

```powershell
scripts\iniciar_sgejd.bat
```

Para empezar prod desde cero (borra su base y su Keycloak, no los de dev):
`docker compose -f compose-prod.yml --env-file .env.prod down -v`.
