# S13 · Optimización y preparación para producción (SGE-JD)

Borrador técnico del informe individual (LP2, Unidad III, sesión 1). Todos los números salen de mediciones
reales del 05/11/2026 sobre SGE-JD. **Las capturas con reloj y usuario visibles las toma cada estudiante**:
al final está la lista de qué capturar y con qué comando.

Guía para levantar producción local: [PRODUCCION_LOCAL.md](PRODUCCION_LOCAL.md).

---

## 1. Lazy Loading y presupuestos

**Medición** (`npx ng build --configuration production --stats-json`):

| | Tamaño | Transferido (gzip) |
|---|---|---|
| Carga inicial (`main` + `styles`) | 346,5 kB | 90,5 kB |
| Chunks diferidos | 61 archivos (uno por pantalla: `venta-form` 19 kB, `cobranza-list` 45 kB, `inicio` 29 kB...) | se bajan al entrar a cada ruta |

Las 40 rutas de `app.routes.ts` usan `loadComponent`. Ninguna pantalla viaja en la carga inicial: la
de Cobranza, por ejemplo, solo se descarga al abrir `/cobranza` (Network → filtro JS → aparece
`chunk-…js (cobranza-list)` recién al navegar).

**Presupuestos** (`angular.json` → `production.budgets`): carga inicial con aviso en 500 kB y error en 1 MB.
Los estilos por componente tienen aviso en 6 kB y error en 8 kB; el aviso está más alto que los 4 kB de la
guía porque las hojas más grandes, Inicio y el menú, miden ~8 kB sin minificar. Compiladas quedan bajo 6 kB:
el build no da ningún aviso.

**Prueba de que el presupuesto corta un exceso real:** con el error de `initial` bajado a 300 kB, el build
falla con código 1:

```
[ERROR] bundle initial exceeded maximum budget. Budget 300.00 kB was not met by 46.53 kB with a total of 346.53 kB.
```

Luego se restauró el valor de 1 MB.

## 2. Caché del navegador

**Endpoint elegido:** `GET /api/v1/admin/tipo-cambio` (tipo de cambio SUNAT). SUNAT publica un valor por día,
y el SPA lo pide cada vez que se abre una venta o una proforma nueva.

```java
return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
        .body(tipoCambioService.obtener(fecha));
```

- Respuesta: `Cache-Control: max-age=600, private`. Va con `private` porque lleva token y ningún proxy debe
  guardarla.
- El resto de la API sigue con el `no-cache, no-store` que pone Spring Security.

**Verificación con dos fuentes:**
- **Navegador.** En 5 minutos de uso se abrieron 8 formularios de venta o proforma, y solo 1 petición al tipo
  de cambio llegó al backend: las otras 7 salieron de la caché del navegador (Network: `(disk cache)`).
- **Servidor.** El log `Tipo de cambio pedido por el navegador` aparece una sola vez en ese lapso; las
  métricas de Actuator cuentan 1 petición.

**Por qué no Categorías** (el ejemplo de la guía): en SGE-JD Lisandro las edita desde la misma pantalla. Con
5 minutos de caché, después de crear una categoría la lista seguiría mostrando la versión vieja.
Cache-Control solo va en lecturas que pueden estar un rato desactualizadas sin confundir a nadie.

## 3. Decisión sobre Redis: **descartado**, con medición

**Cómo se midió:** con Actuator (`/actuator/metrics/http.server.requests`, perfil dev) se contaron las
peticiones GET por endpoint durante 5 minutos de uso normal del SPA como asistente:
- inicio, ventas, venta nueva;
- proformas, proforma nueva;
- clientes, productos, cobranza;
- compras, compra nueva;
- agenda, órdenes, postventa.

Fueron dos vueltas, 118 peticiones en total.

| Endpoint (GET) | Veces en 5 min | Promedio (ms) |
|---|---|---|
| `/clientes` | 23 | 30 |
| `/productos` | 18 | 67 |
| `/cobranza/por-cobrar` | 9 | 191 |
| `/ordenes` | 9 | 83 |
| `/descuentos/tope` | 8 | 9 |
| `/proformas/pagina` | 6 | 142 |
| `/ventas/pagina` | 6 | 1 496 |
| `/cobranza/cobros` | 6 | 285 |
| `/categorias` | 5 | 45 |
| `/ventas` (lista completa, tablero) | 4 | 1 657 |
| `/proformas` (lista completa) | 3 | 1 254 |
| `/tipo-cambio` | 1 | ver sección 2 |

**Lectura de la medición:**
- **Los candidatos naturales a caché son baratos.** Productos suma 18 × 67 ms ≈ 1,2 s de servidor en 5
  minutos y clientes 23 × 30 ms ≈ 0,7 s. Redis ahorraría menos de 2 s cada 5 minutos para un usuario. JD tiene
  dos.
- **Lo lento no se puede cachear.** Las listas de ventas y proformas (1,2 a 1,7 s) cambian con cada venta,
  cobro o anulación, y el caché habría que invalidarlo a cada rato. Eso se arregla con la consulta: paginar,
  sin N+1 y con el `fetch_size` de Oracle, que Hibernate avisa que está en 10. Es tarea de S14, no de Redis.
- **Productos tampoco es tan estable como parece.** El stock cambia con cada venta, compra y ajuste, así que
  `@CacheEvict` se dispararía todo el día.
- **El dato externo caro ya está cacheado.** El tipo de cambio cuesta ~1,1 s contra SUNAT; el backend lo
  guarda en memoria por día y el navegador 10 minutos.
- **Cuesta infraestructura.** Redis sería un contenedor más en una laptop con 0,7 GB libres, DTOs
  `Serializable` y un punto de falla adicional.

**Qué cambiaría la decisión:**
- correr más de una instancia del backend, porque la caché en memoria no se comparte entre instancias;
- que el catálogo crezca a miles de productos y su lista pase de ~300 ms;
- más de 10 usuarios simultáneos.

## 4. Logging y Actuator por perfil

**Perfiles:**
- `application.properties` tiene lo común y `spring.profiles.default=dev`.
- `application-dev.properties`: `ddl-auto=update` y Actuator con todo expuesto.
- `application-prod.properties`: `ddl-auto=validate`, Actuator `health,info` y Swagger apagado.

**`logback-spring.xml`:**

| | dev | prod |
|---|---|---|
| `com.bomerp` (código propio) | DEBUG | INFO |
| `org.hibernate.SQL` | DEBUG (cada sentencia) | WARN |
| Spring / Hibernate | INFO | WARN |

**Comparación real:**
- **Dev:** los 5 minutos de uso escribieron **174 286 líneas**, de ellas 6 085 sentencias SQL.
- **Prod:** el contenedor escribió **39 líneas** desde que arrancó, con un flujo completo de verificación:
  crear categoría, producto y cliente, registrar una venta y emitir el comprobante B001-00000001.

**Actuator verificado:**

| | dev (8080) | prod (48088) |
|---|---|---|
| `/actuator/health` sin token | 200 con detalles (db, disco) | 200 `{"status":"UP"}` sin detalles |
| `/actuator/env` sin token | 401 | 401 |
| `/actuator/env` con token de Lucy (ASISTENTE) | 403 | — |
| `/actuator/env` con token de Lisandro (ADMIN) | 200 | **404** |
| `/actuator/heapdump`, `/metrics`, `/beans`, `/loggers` con token ADMIN | 200 (metrics) / 404 (heapdump) | **404** |

Hay dos capas:
1. **Seguridad.** Actuator, salvo `health` e `info`, exige el rol ADMIN.
2. **Exposición.** En prod esos endpoints ni existen: un token robado de ADMIN tampoco llega a ellos.

## 5. Configuración de producción

- **`application-prod.properties`:** datos de conexión, emisor de Keycloak, URL interna, secreto de la cuenta
  de servicio y CORS llegan por variables **sin valor por defecto**. Si falta una, el backend no arranca.
- **`environment.prod.ts`:** URLs de prod (48088, 48080 y redirección a 4300). No tiene secretos porque el SPA
  es un cliente público con PKCE. Lo reemplaza `fileReplacements` en `angular.json`.
- **`compose-prod.yml`:**
  - Oracle 41521, Postgres 45432, Keycloak 48080 y backend 48088, con otros contenedores y volúmenes que dev;
  - Keycloak en modo `start`, no `start-dev`;
  - secretos en `.env.prod`, ignorado por git y generado al azar por `scripts/prod/generar_env_prod.py`.
- **Esquema:** `scripts/prod/esquema_jdrefrigeracion.sql`, sacado del esquema real de dev con `DBMS_METADATA`,
  sin datos. Al arrancar, `ddl-auto=validate` lo aceptó sin errores.
- **Frontend:** nginx con el build de producción en 4300. Recargar `/ventas` devuelve 200, `index.html` va con
  `no-cache` y los `.js` con hash se cachean un año.

**De punta a punta (05/11/2026):**
1. Login de Lisandro en http://localhost:4300 contra el Keycloak de prod; el token trae
   `iss = http://localhost:48080/realms/bomerp`.
2. Usuarios y permisos lista a Lisandro y Lucy, consultando la API de administración de Keycloak por la red
   interna de Docker.
3. Se creó la venta 1 (S/ 306,80, contado) con su comprobante.
4. El bundle de prod solo contiene las URLs de prod: ni una referencia a 4200, 8080 ni 38080.

## Hallazgos

1. **La imagen Docker habría llevado las contraseñas de dev.** `application-local.properties`, ignorado por
   git pero presente en `src/main/resources`, entra al JAR con cualquier `mvn package` local.
   - Si el Dockerfile copiara un JAR compilado en la laptop, la imagen llevaría las claves.
   - Solución: el `.dockerignore` excluye ese archivo y `.env*`, y el perfil prod ya no lo importa.
   - Comprobado: el JAR de la imagen solo trae `application`, `-dev` y `-prod.properties`.
2. **El emisor del token no es el mismo visto desde dentro del contenedor.**
   - El navegador obtiene el token de `localhost:48080`, que queda como `iss`.
   - Dentro del contenedor, `localhost` es el propio backend.
   - Con solo `issuer-uri` (como en la guía), el backend no puede descargar las claves; si se usa
     `keycloak:8080`, todos los tokens se rechazan por emisor distinto.
   - Solución:
     - validar `iss` con la URL pública;
     - pedir las claves (`jwk-set-uri`) y la API de administración por `keycloak:8080`;
     - fijar en Keycloak `--hostname=http://localhost:48080`.
3. **`spring.jpa.show-sql=true` se salta logback**: escribe directo en la consola, así que ningún nivel de
   log lo apagaba en prod. Se cambió por el logger `org.hibernate.SQL`, que solo enciende dev.
4. **Siete clases usaban `System.out`/`System.err`**, también fuera del control de niveles. Una de ellas, el
   WhatsApp simulado, escribía el teléfono y el mensaje del cliente. Ahora usan SLF4J, y el WhatsApp va en
   DEBUG: no sale en prod.
5. **Hora del contenedor:** sin `TZ=America/Lima`, el resumen diario y las alertas de mantenimiento correrían
   5 horas adelantados (UTC).
6. **El correo apagaría la salud.** Con Actuator, el chequeo de SMTP marcaría `DOWN` mientras no haya cuenta de
   correo de la empresa. Se desactivó (`management.health.mail.enabled=false`) porque el correo es opcional.
7. **RAM:** dev y prod en paralelo (dos Oracle y dos Keycloak) no caben en 3,5 GB de Docker. Se verificó prod
   con dev apagado.

## Reflexión técnica

"Funciona en mi laptop" solo prueba que funciona con la configuración de mi laptop:
- con `ddl-auto=update` arreglando el esquema solo;
- con Swagger y todos los endpoints de Actuator abiertos;
- con un log que escribe 174 mil líneas en 5 minutos;
- con contraseñas leídas de un archivo local que, sin darme cuenta, también terminaba dentro del JAR.

Igual que en el caso de 1.6, el riesgo no da error: Spring no avisa que `/actuator/env` está expuesto ni que el
SQL se imprime en prod. Solo aparece al probar el sistema tal como va a correr: imagen Docker, perfil `prod`,
base nueva validada contra las entidades y un token emitido por otra URL. En SGE-JD esa prueba destapó dos
problemas que en dev no se veían: el emisor del token y el archivo de secretos en el JAR.

## Preguntas de defensa (respuestas cortas)

1. **¿`ng serve` frente a `ng build --configuration production`?**
   - `ng serve` compila en memoria, sin minificar, con source maps y con `environment.ts`.
   - El build de producción minifica, elimina código muerto, pone hash en los nombres para que la caché sea
     segura, aplica los presupuestos (y falla si se pasan) y reemplaza `environment.ts` por `environment.prod.ts`.
   - Es otro artefacto, no solo uno más rápido.
2. **¿Por qué Cache-Control no va en escrituras?** Un POST, PUT o DELETE cambia datos. Cachear su respuesta no
   evita repetir la operación y puede mostrar como hecho algo que no se volvió a ejecutar. Además, la siguiente
   lectura debe ver el cambio.
3. **¿Qué mediste para Redis?**
   - Peticiones por endpoint y su tiempo promedio en 5 minutos de uso real.
   - La cambiaría tener más de una instancia del backend, un catálogo que pase de ~300 ms o más de 10
     usuarios simultáneos.
4. **¿Por qué `/actuator/env` expuesto es un riesgo?** Muestra la configuración del entorno: URLs internas,
   usuarios y propiedades. `heapdump` vuelca la memoria, que puede contener tokens y contraseñas. Spring no lo
   marca como error porque es una decisión de configuración.
5. **¿Por qué no escribir la contraseña en `application-prod`?**
   - El repositorio se copia: laptops, CI, forks, el historial de git aunque luego se borre la línea.
   - En SGE-JD el historial público ya tuvo una contraseña de Gmail.
   - Con `${DB_PASSWORD}` sin valor por defecto, si falta la variable el sistema no arranca en vez de arrancar
     con una clave expuesta.

## Capturas que debe tomar cada estudiante (con reloj y usuario visibles)

1. DevTools → Network → filtro JS, en http://localhost:4300: entrar a Inicio y luego a Cobranza. El chunk
   `cobranza-list` aparece recién al navegar.
2. Salida de `npx ng build --configuration production` con la tabla de Initial y Lazy chunk files.
3. `angular.json` con los `budgets` y la salida del build fallando con el presupuesto bajado a 300 kB (y luego
   restaurado).
4. Network → petición `tipo-cambio` → Response Headers con `Cache-Control: max-age=600, private`. Abrir otra
   venta nueva: la fila dice `(disk cache)` y la consola del backend no imprime el log otra vez.
5. Tabla de la medición para Redis: salida de `/actuator/metrics/http.server.requests` o del script de conteo.
6. Consola de `mvn spring-boot:run` (dev) con el SQL en DEBUG frente a `docker logs sge-jd-backend-prod`.
7. `curl -i http://localhost:48088/actuator/env` → 401 y la misma petición con token de ADMIN → 404.
8. `application-prod.properties`, `environment.prod.ts`, `compose-prod.yml` y `git check-ignore -v .env.prod`.
9. Venta registrada en http://localhost:4300 con el backend de prod (`docker ps` mostrando los 5 contenedores
   `-prod`).
