# Migración BD2 → SGE-JD (integración con LP2)

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Lleva a la base real lo que el curso de BD2 probó en la base de práctica (`bd2/`). Son siete scripts que se aplican en orden con `migrar.ps1`. El ejecutor entra como SYSDBA dentro del contenedor, así que no necesita la clave de SYS. Cada script se puede repetir: lo que ya existe se informa y no se toca.

| Orden | Script | Qué deja en la base | Origen |
|---|---|---|---|
| 1 | [01_tablespace.sql](01_tablespace.sql) | `TS_SGEJD_VENTAS` (dentro del volumen) con `VENTAS`, `DETALLE_VENTA` y sus índices, movidos con `MOVE ONLINE` | BD2 S9 |
| 2 | [02_reglas_y_plsql.sql](02_reglas_y_plsql.sql) | 5 CHECK (cantidad, precio, descuento, stock, costo y margen); `LOG_ERRORES` + `SP_REGISTRAR_ERROR`; `FN_SUBTOTAL_LINEA`, `FN_TOTAL_VENTA`, `SP_REGISTRAR_VENTA` (versión 2) | BD2 S1 y S3 |
| 3 | [03_auditoria_ventas.sql](03_auditoria_ventas.sql) | `VENTA_AUDITORIA` particionada por mes (INTERVAL), `TRG_VENTA_ESTADO_BU` (anulada es definitiva; motivo y quién), `TRG_VENTA_AUDITORIA`, índices LOCAL | BD2 S2, S5 y S11 |
| 4 | [04_indices.sql](04_indices.sql) | `IX_DETALLE_VENTA_VENTA`, `IX_VENTAS_FECHA_VENTA`, `IX_VENTAS_CLIENTE_FECHA`, `IX_COBROS_REFERENCIA_NORM`, **`UX_COBRO_VOUCHER_VIGENTE`** (único) y estadísticas | BD2 S5 y S10, ADS S9 |
| 5 | [05_usuarios_roles.sql](05_usuarios_roles.sql) | Roles `ROL_SGEJD_MAESTROS`, `_OPERACION` y `_CONSULTA`; usuarios `JD_APP` y `JD_REPORTES`; el dueño sin `DB_DEVELOPER_ROLE` | BD2 S1, S7 y S8 |
| 6 | [06_auditoria_unificada.sql](06_auditoria_unificada.sql) | `POL_SGEJD_VENTAS` (9 acciones sobre 3 tablas), `POL_SGEJD_SESIONES`, `ORA_LOGON_FAILURES` | BD2 S9 |
| 7 | [07_redo.sql](07_redo.sql) | Redo de 2 × 10 MB a 3 × 200 MB, sin reiniciar | BD2 S7 y S9 |

```powershell
.\migracion_bd2\migrar.ps1 -Contenedor sge-jd-bd2 -Secretos bd2\.env.bd2       # ensayo (práctica)
.\migracion_bd2\migrar.ps1 -Contenedor SGE-JD -Secretos .env                    # desarrollo
.\migracion_bd2\migrar.ps1 -Contenedor sge-jd-oracle-prod -Secretos .env.prod   # producción (ver abajo)
```

**Claves de `JD_APP` y `JD_REPORTES`:**
- Salen del archivo de secretos (`JD_APP_PASSWORD`, `JD_REPORTES_PASSWORD`). Si no están, se generan al azar y se agregan al archivo.
- Nunca se imprimen.
- A un usuario que ya existe no se le cambia la clave.

## Cambios en el backend que acompañan a la migración

| Cambio | Archivo | Origen |
|---|---|---|
| Las 2 pruebas que anulaban por SQL sin motivo ni usuario ahora los envían (el trigger los exige) | `ComprobanteReemplazoIntegracionTest`, `VentaAnuladaSinNotaIntegracionTest` | BD2 S8 |
| `buscar` con `Specification` (solo los filtros que llegan) y `lineasVendidas` con rango fijo y `estado = REGISTRADA`, sin `(:x IS NULL OR …)` | `VentaRepository` | BD2 S4 |
| Voucher repetido que llega a la vez: el índice único lo rechaza y el usuario recibe un 409 con mensaje de negocio | `GlobalExceptionHandler` | ADS S9 |
| `CobranzaService` depende del puerto `ComprobantesDeVenta`, no de `FacturacionService` | `CobranzaService`, `CobranzaServiceTest` | ADS S10 |
| Cada conexión dice a Oracle qué operación la usa: `MODULE` = "SGE-JD ventas" y `ACTION` = "PATCH /ventas/182/anular". Se ve en `V$SESSION`, en ASH y en `VENTA_AUDITORIA.PROGRAMA` | `config/monitoreo/ModuloOracle`, `MonitoreoConfig` (+ `ModuloOracleTest`, 5 pruebas) | BD2 S14 |
| Perfil `prod` preparado para entrar como `JD_APP`: `default_schema` y `connection-init-sql` | `application-prod.properties` | BD2 S8 |
| Oracle de desarrollo con la hora de Lima | `docker-compose.yml` (`TZ: America/Lima`) | BD2 S2 |

## Cómo se probó

1. **Respaldo de desarrollo** antes de tocar nada: `C:\SGE-JD\respaldos\sgejd_dev_antes_integracion_bd2_20261110.dmp` (2952 KB).
2. **Ensayo sobre una copia exacta:** la base de práctica, restaurada con ese respaldo (237 ventas, 222 cobros, 29 tablas).
   - La migración pasó completa.
   - Una **segunda pasada** solo informó "ya existe" o "sin cambio".
3. **Todas las pruebas del backend contra la copia migrada** (`probar_en_practica.ps1`):

   | Usuario | Pruebas | Fallas | Errores |
   |---|---|---|---|
   | `JDREFRIGERACION` (como entra desarrollo) | 215 | 0 | 0 |
   | `JD_APP` (como entraría producción; con el rol temporal de pruebas de BD2 S8, revocado después) | 215 | 0 | 0 |
   | `JDREFRIGERACION`, después del cambio de interceptor a filtro (+5 pruebas nuevas) | **220** | **0** | **0** |

   Las pruebas limpian lo que crean: `VENTA_AUDITORIA` registró 21 INSERT y 21 DELETE por usuario.
4. **Índice del voucher, a propósito:** al segundo cobro vigente se le puso el voucher del primero, con otro formato (`' cd-880001 '`), y Oracle respondió `ORA-00001 … UX_COBRO_VOUCHER_VIGENTE`.
5. **Desarrollo (`SGE-JD`):**
   - La migración pasó completa.
   - Datos intactos (237 ventas, 222 cobros); 0 índices UNUSABLE; 0 objetos inválidos.
   - Se recreó el contenedor con `TZ`, con la **misma imagen** (`fbbd3023d5ab`, verificada antes). `SYSDATE` pasó de UTC (ya marcaba 11/11) a Lima (`-05:00`).
6. **Backend de desarrollo** (`mvn spring-boot:run`, `ddl-auto=update`, como el dueño sin `DB_DEVELOPER_ROLE`):
   - Arrancó en 27 a 40 s y `/actuator/health` respondió 200.
   - Hibernate no pidió ningún `ALTER` ni `CREATE`.
   - En `V$SESSION` la conexión de una petición se vio como `SGE-JD ventas / PATCH /ventas/182/anular`.

## Hallazgos de la integración

1. **Data Pump no importa entre imágenes distintas.** Para el ensayo, la base de práctica (imagen completa desde S13, zonas horarias v43) rechazó el respaldo de desarrollo (slim, v45) con **ORA-39405**. Es la otra cara del hallazgo de S13: no solo cambia el software, también deja de servir el respaldo. `compose-bd2.yml` acepta ahora `BD2_IMAGEN`, y el ensayo se hizo con la misma imagen que desarrollo.
2. **El primer marcado de conexiones dejaba una sin marca.** Con un interceptor de Spring MVC, la petición rechazada con 401 usó una conexión **antes** de llegar al interceptor: la seguridad registra en `HISTORIAL_CAMBIOS` los intentos denegados. Se cambió por un filtro de servlet con la máxima prioridad, que corre antes que Spring Security.
3. **SQL*Plus:**
   - `DBMS_OUTPUT` se habilita por contenedor: después de `ALTER SESSION SET CONTAINER` hay que repetir `SET SERVEROUTPUT ON`, o no se ve nada.
   - Un comentario `--` en la misma línea que un `SET` da `SP2-0158: unknown SET option "--"`.
4. **Las variables de sustitución se reemplazan también dentro de los comentarios**: el comentario de `05` ya no escribe el ampersand.

## Producción (4300): aplicada el 10/11/2026

1. **Respaldo:** `C:\SGE-JD\respaldos\sgejd_prod_antes_migracion_bd2_20261110.dmp` (2036 KB), con
   `respaldar_sgejd.ps1 -Contenedor sge-jd-oracle-prod`.
2. **Revisión previa:** las 29 tablas que usan los roles existen; ningún dato rompe un CHECK ni el índice del voucher.
   Producción estaba vacía (0 ventas, 0 productos: se limpió el 07/11 para la entrega). Imagen `fbbd3023d5ab`, la
   misma del ensayo; ya estaba en hora de Lima.
3. **Migración:** `migrar.ps1 -Contenedor sge-jd-oracle-prod -Secretos .env.prod` pasó completa, los 7 scripts.
   Las claves de `JD_APP` y `JD_REPORTES` se generaron en `.env.prod`.
4. **Backend:** `compose-prod.yml` pasó a `DB_USERNAME: jd_app` y `DB_PASSWORD: ${JD_APP_PASSWORD}`. Se reconstruyó la
   imagen con este código. Arrancó en 36 s con `ddl-auto=validate` y `/actuator/health` respondió 200.
5. **Verificación en Oracle:**
   - Las 10 conexiones del backend son de `JD_APP` y el dueño no tiene ninguna.
   - La auditoría unificada registró los 10 LOGON de `JD_APP` (`POL_SGEJD_SESIONES`).
   - La petición pública se ve como `SGE-JD publico / GET /publico/catalogo`.
   - 0 objetos inválidos, 0 índices UNUSABLE, redo de 3 × 200 MB.
   - El 4300 responde 200.

**Volver atrás** (si hiciera falta):
1. En `compose-prod.yml`, `DB_USERNAME: jdrefrigeracion` y `DB_PASSWORD: ${APP_USER_PASSWORD}`.
2. Restaurar el respaldo con `scripts\restaurar_sgejd.ps1`.

Quedan para después de decidir PC u hosting:
- imagen fijada por digest;
- imagen completa con RMAN, resolviendo antes las zonas horarias (hallazgo 1);
- ARCHIVELOG y RMAN programados.
