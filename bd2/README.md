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

Después se vuelven a correr los scripts de las sesiones, en orden.

## Sesiones

| Sesión | Tema | Trabajo | Estado |
|---|---|---|---|
| S1 | PL/SQL aplicado al negocio | [S01](S01/README.md): `SP_REGISTRAR_VENTA`, `FN_TOTAL_VENTA` (237/237 iguales a Java), CHECK de stock, cantidad y descuento, usuario `JD_APP` | ✅ 10/11/2026 |
| S2 | Triggers DML y auditoría | [S02](S02/README.md): `VENTA_AUDITORIA` sin FK, `TRG_VENTA_ESTADO_BU` (una anulada es definitiva; motivo y quién obligatorios) y `TRG_VENTA_AUDITORIA`; hallazgo: Oracle en UTC | ✅ 10/11/2026 |
| S3 | Excepciones y robustez | [S03](S03/README.md): `LOG_ERRORES` con transacción autónoma, `NO_DATA_FOUND`, `PRAGMA EXCEPTION_INIT` (-54, -12899) y `RAISE_APPLICATION_ERROR`; prueba con dos sesiones; hallazgos: el registro de la guía se pierde con el rollback, y Oracle 23 da ORA-00054 y no ORA-30006 | ✅ 10/11/2026 |
| S4 | Optimización de consultas | [S04](S04/README.md): consulta de Rentabilidad (5 tablas) con 30 000 ventas cargadas; A/B/C con `EXPLAIN PLAN` y plan real del cursor; hallazgos: estimación 380 veces menor con estadísticas viejas, y `EXPLAIN PLAN` no ve las variables | ✅ 10/11/2026 |
| S5 | Índices | | ⏳ |
| S6 | Evaluación de la Unidad I | Sustentación con lo acumulado de S1 a S5 | ⏳ |
| S7 | Arquitectura Oracle e instancia | | ⏳ |
| S8 | Usuarios, roles y privilegios | | ⏳ |
| S9 | Almacenamiento, seguridad y auditoría | | ⏳ |
| S10 | Optimización del rendimiento | | ⏳ |
| S11 | Particionamiento | | ⏳ |
| S12 | Evaluación de la Unidad II | | ⏳ |
| S13 | Backup y recovery con RMAN | | ⏳ |
| S14 | Monitoreo y diagnóstico | | ⏳ |
| S15–S16 | Evaluación de la Unidad III | | ⏳ |

## Integración con LP2 (al final)

Lo que se lleva a la base real cuando esté probado:

| Origen | Qué | Estado |
|---|---|---|
| S1 | CHECK de `DETALLE_VENTA` y `PRODUCTOS`; `FN_SUBTOTAL_LINEA`, `FN_TOTAL_VENTA`, `SP_REGISTRAR_VENTA`; usuario `JD_APP` | Probado en práctica; falta la migración para desarrollo y producción |
| S2 | `VENTA_AUDITORIA`, `TRG_VENTA_ESTADO_BU`, `TRG_VENTA_AUDITORIA` | Probado en práctica; falta verificar `VentaService.anular()` con el backend conectado a esta base |
| S2 | `TZ: America/Lima` en `docker-compose.yml` (contenedor `SGE-JD` de desarrollo); producción ya lo tiene | Pendiente: requiere recrear el contenedor de desarrollo |
| S3 | `LOG_ERRORES`, `SP_REGISTRAR_ERROR`; `SP_REGISTRAR_VENTA` y `FN_TOTAL_VENTA` versión 2 | Probado en práctica (regresión de S1 y S2 en verde) |
| S4 | Reescribir `VentaRepository.lineasVendidas` y `buscar` sin `(:x IS NULL OR …)` y con `estado = 'REGISTRADA'` | Probado en práctica (mismo resultado, estimación 1 % frente a casi el triple); falta el cambio en Java |
| S4 | Ventas insertadas por PL/SQL quedan "por emitir" y el backend les emite comprobante en hasta 3 minutos | Comportamiento a decidir al integrar `SP_REGISTRAR_VENTA` |
