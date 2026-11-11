# BD2 · Base de Datos II sobre SGE-JD

Trabajos autónomos de BD2 (Ciclo 4, 2026-2) sobre la base Oracle real de SGE-JD: el esquema
`JDREFRIGERACION` que usa el backend de LP2. Guías del curso: <https://262ciclo4.github.io/bomerp/bd2/>.

## Base de práctica

Lo que puede romper la base, como privilegios, tablespaces, particiones, ARCHIVELOG/RMAN o bloqueos, se prueba
primero en un contenedor aparte: **`sge-jd-bd2`**, puerto **1523**. Es una copia de los datos de desarrollo y no
toca el 4200 (contenedor `SGE-JD`) ni el 4300 (`sge-jd-oracle-prod`). Lo que funcione se integra después a los
scripts del backend, junto con LP2.

La PC aguanta **un solo Oracle a la vez**: antes de encender este, apagar el de desarrollo
(`scripts\detener_sgejd.bat`) o el de producción.

```powershell
cd C:\SGE-JD\bomerp-backend
# Encender / apagar
docker compose -f bd2/compose-bd2.yml --env-file bd2/.env.bd2 up -d
docker compose -f bd2/compose-bd2.yml --env-file bd2/.env.bd2 stop
# Ejecutar un script (como JDREFRIGERACION; -Dba para SYSTEM)
.\bd2\herramientas\sql.ps1 bd2\S01\S01_04_pruebas.sql
```

Las claves están en `bd2/.env.bd2`, que no se sube a git: `BD2_ORACLE_PASSWORD` (SYS/SYSTEM),
`BD2_OWNER_PASSWORD` (JDREFRIGERACION) y `BD2_APP_PASSWORD` (JD_APP). Para conectarse desde SQL Developer o
DBeaver: host `localhost`, puerto `1523`, servicio `FREEPDB1`.

**Volver a la copia de datos original.** Se borra el volumen y se importa otra vez el respaldo del 10/11/2026:

```powershell
docker compose -f bd2/compose-bd2.yml --env-file bd2/.env.bd2 down -v
docker compose -f bd2/compose-bd2.yml --env-file bd2/.env.bd2 up -d    # esperar "DATABASE IS READY TO USE"
docker cp C:\SGE-JD\respaldos\sgejd_base_bd2_20261110.dmp sge-jd-bd2:/tmp/base.dmp
docker cp bd2\herramientas\importar_base.sh sge-jd-bd2:/tmp/importar_base.sh
docker exec -u root sge-jd-bd2 bash -c "chown oracle:oinstall /tmp/base.dmp /tmp/importar_base.sh; sed -i 's/\r$//' /tmp/importar_base.sh"
docker exec sge-jd-bd2 bash /tmp/importar_base.sh
```

Desde S13 el contenedor usa la imagen completa (con RMAN). El volumen actual se creó con la slim. Por eso, cada vez que se recrea el contenedor sobre ese volumen, hay que correr `.\bd2\herramientas\copiar_zona_horaria.ps1` y luego `docker restart sge-jd-bd2` (ver [S13](S13/README.md), hallazgo 2). Si el volumen se crea de cero, revisar con `S13_01_zona_horaria.sql`. El script no hace nada si el archivo ya está.

Después se vuelven a correr los scripts de las sesiones, en orden. Más simple: `.\bd2\herramientas\restaurar_practica.ps1` hace todo eso y recrea los objetos de S1 a S3 (`-SoloObjetos` para no restaurar).

## Sesiones

| Sesión | Tema | Trabajo | Estado |
|---|---|---|---|
| S1 | PL/SQL aplicado al negocio | [S01](S01/README.md): `SP_REGISTRAR_VENTA`, `FN_TOTAL_VENTA` (237/237 iguales a Java), CHECK de stock, cantidad y descuento, usuario `JD_APP` | ✅ 10/11/2026 |
| S2 | Triggers DML y auditoría | [S02](S02/README.md): `VENTA_AUDITORIA` sin FK, `TRG_VENTA_ESTADO_BU` (una anulada es definitiva; motivo y quién obligatorios) y `TRG_VENTA_AUDITORIA`; hallazgo: Oracle en UTC | ✅ 10/11/2026 |
| S3 | Excepciones y robustez | [S03](S03/README.md): `LOG_ERRORES` con transacción autónoma, `NO_DATA_FOUND`, `PRAGMA EXCEPTION_INIT` (-54, -12899) y `RAISE_APPLICATION_ERROR`; prueba con dos sesiones; hallazgos: el registro de la guía se pierde con el rollback, y Oracle 23 da ORA-00054 y no ORA-30006 | ✅ 10/11/2026 |
| S4 | Optimización de consultas | [S04](S04/README.md): consulta de Rentabilidad (5 tablas) con 30 000 ventas cargadas; A/B/C con `EXPLAIN PLAN` y plan real del cursor; hallazgos: estimación 380 veces menor con estadísticas viejas, y `EXPLAIN PLAN` no ve las variables | ✅ 10/11/2026 |
| S5 | Índices | [S05](S05/README.md): selectividad de 7 columnas y 8 predicados; B-Tree en `DETALLE_VENTA(VENTA_ID)` y `VENTAS(FECHA_VENTA)`, Function-Based para el voucher de cobros, Bitmap probado y reemplazado; hallazgo: el Bitmap bloqueó 11,4 s una venta simultánea | ✅ 10/11/2026 |
| S6 | Evaluación de la Unidad I | Sustentación con lo acumulado de S1 a S5 (sin trabajo autónomo) | — |
| S7 | Arquitectura Oracle e instancia | [S07](S07/README.md): SGA 1,5 GB (ASMM), PGA 512 MB, 80 procesos de fondo, `FREE`/`FREEPDB1`, ORA-00942 como usuario de la aplicación; hallazgos: redo de 2 × 10 MB provocó "Checkpoint not complete" en la carga de S4, la imagen "23" es en realidad 26ai (23.26.2), y `JDREFRIGERACION` tiene 29 privilegios | ✅ 10/11/2026 |
| S8 | Usuarios, roles y privilegios | [S08](S08/README.md): roles `ROL_SGEJD_MAESTROS`, `_OPERACION` y `_CONSULTA` derivados del código; `JD_APP` sin privilegios directos; `JD_REPORTES` de solo lectura; el dueño sin `DB_DEVELOPER_ROLE`; 217 pruebas del backend como `JD_APP` con el mismo resultado que como dueño; hallazgos: ORA-41900 en lugar de ORA-01031, y el DELETE de más solo lo piden las pruebas | ✅ 10/11/2026 |
| S9 | Almacenamiento, seguridad y auditoría | [S09](S09/README.md): `TS_SGEJD_VENTAS` (ORA-01950, índices UNUSABLE y ORA-01502 hasta el REBUILD), redo 3 × 200 MB, ARCHIVELOG documentado, auditoría unificada con evidencia real (denegado 2004, intento de NOAUDIT por `ORA$MANDATORY`, clave incorrecta 1017); hallazgos: la sintaxis de la guía auditaba toda la base, el archivado de fábrica está fuera del volumen, y EM Express no existe en 26ai slim | ✅ 10/11/2026 |
| S10 | Optimización del rendimiento | [S10](S10/README.md): carga en orden de fecha; estadísticas viejas (258 frente a 27 200) corregidas con `cascade`; el índice de fecha ahora sí se usa (factor de agrupamiento de 28 974 a 730, costo 175 frente a 251); AWR funciona pero se licencia aparte; diagnóstico con `V$SQL` y `DISPLAY_CURSOR`; índice compuesto `(cliente_id, fecha_venta)` medido y creado (28 → 12, y cubre la FK) | ✅ 10/11/2026 |
| S11 | Particionamiento | [S11](S11/README.md): RANGE mensual con INTERVAL para ventas (sin DROP: retención legal) y auditoría (con DROP); pruning 1 de 36 particiones (costo 10) frente a `TRUNC`/`TO_CHAR` (235, todas); Hash/List/Composite medidos; `DROP PARTITION` 290 ms frente a `DELETE` 1 170 ms; hallazgo: el DROP simple deja la PK global UNUSABLE (ORA-01502), se resuelve con `UPDATE GLOBAL INDEXES` (240 ms) | ✅ 10/11/2026 |
| S12 | Evaluación de la Unidad II | Sustentación con lo acumulado de S7 a S11 (sin trabajo autónomo) | — |
| S13 | Backup y recovery con RMAN | [S13](S13/README.md): imagen completa (la slim no trae RMAN); ARCHIVELOG con la FRA dentro del volumen; nivel 0 de ~2,4 GB en 28 s y nivel 1 de ~720 KB en 12 s; incidente real (`UPDATE` sin `WHERE` en `PRODUCTOS`) y PITR al segundo anterior: el cambio legítimo sobrevivió, RTO 33 s; Data Pump de 27 MB; estrategia y reflexión sobre GitLab 2017; hallazgos: la imagen completa trae zonas horarias v43 y la base espera v45 (riesgo de ORA-01804), y el TAG de `PLUS ARCHIVELOG` no llega a los datafiles | ✅ 10/11/2026 |
| S14 | Monitoreo y diagnóstico | [S14](S14/README.md): sesiones con `V$SESSION` (un SYS que es proceso de fondo); bloqueo real cobranza contra anulación diagnosticado con `DBA_BLOCKERS`/`DBA_WAITERS`, `V$SQL` y `V$LOCK`, resuelto sin `KILL` (sobrevivió la anulación); con la espera del backend, ORA-00054 a los 10 s; 0 inválidos del proyecto y un sinónimo de Oracle sin destino (recompilar: ORA-00942); hit ratio 96,79 % (39 % frío, 100 % caliente) mientras la espera dominante era `enq: TX` (52,1 s); hallazgos: EM Express desoportado desde 23ai, y el Performance Hub en HTML depende del CDN de Oracle | ✅ 10/11/2026 |
| S15–S16 | Evaluación de la Unidad III | | ⏳ |

## Integración con LP2

**Aplicada en desarrollo y en producción el 10/11/2026** con [migracion_bd2/](../migracion_bd2/README.md). Antes se
ensayó sobre una copia exacta de desarrollo, con 220 pruebas del backend en verde (como el dueño y como `JD_APP`).
El backend de producción ya entra como `JD_APP`.

| Origen | Qué | Estado |
|---|---|---|
| S1, S3 | CHECK de `DETALLE_VENTA` y `PRODUCTOS`; `LOG_ERRORES`, `SP_REGISTRAR_ERROR`; `FN_SUBTOTAL_LINEA`, `FN_TOTAL_VENTA`, `SP_REGISTRAR_VENTA` v2 | ✅ Desarrollo (`02`) |
| S2, S11 | `VENTA_AUDITORIA` particionada por mes, `TRG_VENTA_ESTADO_BU`, `TRG_VENTA_AUDITORIA` | ✅ Desarrollo (`03`) |
| S2 | `TZ: America/Lima` en `docker-compose.yml` | ✅ Contenedor recreado con la misma imagen; `SYSDATE` en `-05:00` |
| S4 | `VentaRepository.buscar` (`Specification`) y `lineasVendidas` (rango fijo, `estado = REGISTRADA`) | ✅ Código |
| S4 | Ventas insertadas por `SP_REGISTRAR_VENTA` quedan por emitir y el backend les emite comprobante | Decidido: se deja así (comentario en `02`) |
| S5, S10 | `IX_DETALLE_VENTA_VENTA`, `IX_VENTAS_FECHA_VENTA`, `IX_COBROS_REFERENCIA_NORM`, `IX_VENTA_AUDITORIA_ACCION`, `IX_VENTAS_CLIENTE_FECHA` | ✅ Desarrollo (`03`, `04`) |
| ADS S9 | `UX_COBRO_VOUCHER_VIGENTE` + mensaje 409 en `GlobalExceptionHandler` | ✅ Desarrollo y código; probado con un duplicado a propósito |
| S7, S9 | Redo de 2 × 10 MB a 3 × 200 MB | ✅ Desarrollo (`07`) |
| S8 | Roles, `JD_APP`, `JD_REPORTES`, dueño sin `DB_DEVELOPER_ROLE` | ✅ Desarrollo (`05`); el backend de desarrollo sigue entrando como dueño (`ddl-auto=update`) |
| S8 | `application-prod.properties` con `default_schema` y `connection-init-sql`; `compose-prod.yml` con `DB_USERNAME: jd_app` | ✅ Producción |
| S8 | Las 2 pruebas que anulaban sin motivo ni usuario | ✅ Código |
| S9 | `TS_SGEJD_VENTAS` (`MOVE ONLINE`), `POL_SGEJD_VENTAS`, `POL_SGEJD_SESIONES`, `ORA_LOGON_FAILURES` | ✅ Desarrollo (`01`, `06`) |
| ADS S10 | `CobranzaService` con el puerto `ComprobantesDeVenta` | ✅ Código (`ModularityTests` en verde) |
| S14 | `MODULE`/`ACTION` por operación en las conexiones del backend | ✅ Código (`config/monitoreo`, 5 pruebas); verificado en `V$SESSION` de desarrollo |
| — | **Producción (4300):** las 7 migraciones, la imagen nueva del backend y `JD_APP` | ✅ 10/11/2026, con respaldo previo; backend como `JD_APP`, health 200 |
| S7, S13 | Imagen fijada por digest; imagen completa con RMAN (antes, zonas horarias v45: ORA-39405 con Data Pump); ARCHIVELOG y RMAN programados | ⏳ Espera la decisión PC u hosting |
| ADS S10, S11, S14 | `Cobro.abonar()` (ADR-007), `VentaAnuladaEvent` con 8 campos (ADR-008), Circuit Breaker en `SunatDirectoClient` | ✅ Código, 11/11/2026 (228 pruebas en verde, como dueño y como `JD_APP`); en producción, falta reconstruir el backend |
| S14 | Consulta diaria de objetos inválidos y esperas por bloqueo | ⏳ Junto con el respaldo automático (carpeta pendiente de Lisandro) |
