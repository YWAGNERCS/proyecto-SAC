# BD2 · S8 · Usuarios, roles y privilegios

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · Yoel Wagner Chambi Sirena · 10/11/2026**

Trabajo autónomo de la sesión 8: rediseñar el modelo de seguridad de la base de SGE-JD con **mínimo
privilegio** y comprobar que la aplicación sigue funcionando. Hoy el backend se conecta como `JDREFRIGERACION`,
el **dueño** de las tablas: puede borrar, alterar o eliminar cualquier cosa del esquema. El rediseño lo pasa al
usuario técnico `JD_APP` (creado en S1), que trabaja solo con roles.

Se hizo en la base de práctica, restaurada a la copia limpia de desarrollo con
[restaurar_practica.ps1](../herramientas/restaurar_practica.ps1) (237 ventas, más los objetos de S1 a S3).

| Orden | Script | Qué hace |
|---|---|---|
| 1 | [S08_01_auditoria.sql](S08_01_auditoria.sql) (`-Dba`) | Privilegios de sistema, roles, privilegios de objeto y cuota, desde el diccionario de datos |
| 2 | [S08_02_roles.sql](S08_02_roles.sql) (`-Dba -ClaveApp`) | Tres roles por función, `JD_APP` reasignado, `JD_REPORTES` de solo lectura y el dueño recortado |
| 3 | [S08_03_limites.sql](S08_03_limites.sql) (`-Usuario JD_APP` y `-Usuario JD_REPORTES`) | Accesos denegados a propósito |
| 4 | [S08_pruebas_backend.ps1](S08_pruebas_backend.ps1) | Las 217 pruebas del backend, como dueño y como `JD_APP` |
| 5 | [S08_04_rol_pruebas.sql](S08_04_rol_pruebas.sql) / [S08_05_quitar_rol_pruebas.sql](S08_05_quitar_rol_pruebas.sql) | El DELETE que solo piden las pruebas: se da y se quita |

## 1. Auditoría: el punto de partida

```
USUARIO          PRIVILEGIOS DE SISTEMA DIRECTOS                                ROLES               OBJETO
JDREFRIGERACION  CREATE PROCEDURE, SEQUENCE, SESSION, TABLE, TRIGGER, VIEW      DB_DEVELOPER_ROLE   dueño de 31 tablas, 3 triggers, 2 funciones, 2 procedimientos
JD_APP           CREATE SESSION                                                 —                   EXECUTE sobre SP_REGISTRAR_VENTA y FN_TOTAL_VENTA (S1)

DB_DEVELOPER_ROLE → RESOURCE → SODA_APP   (roles anidados)
DB_DEVELOPER_ROLE: CREATE ASSERTION, CUBE, CUBE BUILD PROCESS, CUBE DIMENSION, DIMENSION, DOMAIN, JOB,
                   MINING MODEL, MLE, SESSION · DEBUG CONNECT SESSION · EXECUTE DYNAMIC MLE · FORCE TRANSACTION · ON COMMIT REFRESH
```

El problema no es `JD_APP`, que está casi vacío: es que **el backend no usa `JD_APP`**. Se conecta como el
dueño, que puede hacer `DROP TABLE ventas`, y que además recibió de la imagen de Docker un rol con 14 privilegios
que el proyecto nunca pidió (`CREATE JOB`, `FORCE TRANSACTION`, `DEBUG CONNECT SESSION`…).

## 2. Lo que la aplicación necesita de verdad

Inventario hecho sobre el código del backend (`src/main`), no sobre suposiciones:

| Hecho medido | Cómo se midió |
|---|---|
| Solo **5 servicios** borran filas: `CategoriaService`, `ProductoService`, `ClienteService`, `SedeClienteService`, `ProveedorService` | `grep` de `.delete(`, `deleteById`, `deleteBy…` y `@Modifying` |
| **Ningún** `DELETE FROM` ni SQL nativo en la aplicación | `grep -rn "DELETE FROM\|nativeQuery"` en `src/main`: vacío |
| Ninguna colección pierde líneas (no hay `.clear()` ni `remove` sobre detalles), así que `orphanRemoval` nunca borra | `grep` de `.clear()` y `removeIf` |
| Las líneas (`DETALLE_*`, `ITEMS_PROFORMA`, `MATERIALES_ORDEN`) y los registros (`HISTORIAL_CAMBIOS`, `ENVIOS_SUNAT`, `INTENTOS_CORREO`, `AJUSTES_INVENTARIO`) se insertan y no se modifican | Revisión de sus servicios |
| `VENTA_AUDITORIA` y `LOG_ERRORES` las escriben un trigger (S2) y un procedimiento (S3) con los derechos del dueño | Se ejecutan como `JDREFRIGERACION`: la aplicación no necesita privilegios sobre ellas |

| Grupo de tablas | Otorgado hoy (como dueño) | Necesario | Exceso |
|---|---|---|---|
| 5 maestras (`CATEGORIAS`, `PRODUCTOS`, `CLIENTES`, `SEDES_CLIENTE`, `PROVEEDORES`) | Todo | SELECT, INSERT, UPDATE, DELETE | DDL (ALTER, DROP, TRUNCATE) |
| 15 documentos que cambian de estado (`VENTAS`, `COMPROBANTES`, `COBROS`…) | Todo | SELECT, INSERT, UPDATE | DELETE y DDL: se anulan, no se borran |
| 9 líneas y registros | Todo | SELECT, INSERT | UPDATE, DELETE y DDL |
| `VENTA_AUDITORIA`, `LOG_ERRORES` | Todo | **Nada** | Todo: la aplicación podría borrar su propio rastro |
| Sistema | 20 privilegios | `CREATE SESSION` | 19 |

## 3. Roles por función

```sql
CREATE ROLE rol_sgejd_maestros;   -- S, I, U, D sobre las 5 tablas maestras
CREATE ROLE rol_sgejd_operacion;  -- S, I, U sobre 15 documentos; S, I sobre 9 líneas y registros;
                                  -- EXECUTE sobre SP_REGISTRAR_VENTA y FN_TOTAL_VENTA (antes directo a JD_APP)
CREATE ROLE rol_sgejd_consulta;   -- solo SELECT sobre 18 tablas de negocio (sin XML, CDR ni correos)

GRANT rol_sgejd_maestros, rol_sgejd_operacion TO jd_app;      -- primero los roles…
REVOKE EXECUTE ON jdrefrigeracion.sp_registrar_venta FROM jd_app;   -- …después se quita lo directo
REVOKE EXECUTE ON jdrefrigeracion.fn_total_venta     FROM jd_app;

CREATE USER jd_reportes …;  GRANT CREATE SESSION, rol_sgejd_consulta TO jd_reportes;

REVOKE db_developer_role FROM jdrefrigeracion;   -- el dueño se queda con sus 6 CREATE y su cuota
```

Los roles siguen las funciones del negocio de [ADS S6](../../docs/ads/S06_modelo_dominio.md):

- **maestros:** catálogo, clientes y proveedores.
- **operación:** vender, facturar, cobrar, comprar y atender.
- **consulta:** los reportes que Lisandro pueda querer sacar en Excel sin tocar nada.

El orden importa: primero se dan los roles y después se revoca lo directo, para que `JD_APP` nunca quede sin
acceso entre un paso y otro.

## 4. Accesos denegados a propósito

| Intento | `JD_APP` | `JD_REPORTES` |
|---|---|---|
| `SELECT COUNT(*) FROM ventas` | 237 ✓ | 237 ✓ |
| `DELETE FROM ventas WHERE id = -1` | **ORA-41900**: missing DELETE privilege on "JDREFRIGERACION"."VENTAS" | **ORA-41900** |
| `DELETE FROM detalle_venta WHERE id = -1` | **ORA-41900** | **ORA-41900** |
| `UPDATE productos SET stock_actual = stock_actual WHERE id = -1` | 0 filas ✓ (catálogo) | **ORA-41900**: missing UPDATE privilege |
| `SELECT … FROM venta_auditoria` / `log_errores` | **ORA-00942** (no la ve) | **ORA-00942** |
| `CREATE TABLE prueba_s8 (id NUMBER)` | **ORA-01031**: insufficient privileges | **ORA-01031** |
| `ALTER TRIGGER trg_venta_auditoria DISABLE` | **ORA-01031** | **ORA-01031** |
| `fn_total_venta(…)` (motor PL/SQL de S1) | 738.68 ✓ (por el rol) | **ORA-00904**: invalid identifier (ni la ve) |

El intento de desactivar el trigger de auditoría es el más importante. Con el modelo anterior, quien tenía la
conexión del backend podía apagar la auditoría de S2, borrar ventas y volver a encenderla sin dejar rastro. Ahora
no puede hacer ninguna de las tres cosas.

## 5. Estado final, verificado en el diccionario

```
USUARIO          ROLES (DEFAULT_ROLE = YES)                    PRIVILEGIOS DE SISTEMA   PRIVILEGIOS DE OBJETO DIRECTOS
JD_APP           ROL_SGEJD_MAESTROS, ROL_SGEJD_OPERACION       CREATE SESSION           0
JD_REPORTES      ROL_SGEJD_CONSULTA                            CREATE SESSION           0
JDREFRIGERACION  —                                              6 (CREATE …)             (dueño de su esquema)
```

```
ROL                   PRIVILEGIOS DE OBJETO
ROL_SGEJD_CONSULTA    18   (SELECT sobre 18 tablas)
ROL_SGEJD_MAESTROS    20   (4 acciones × 5 tablas)
ROL_SGEJD_OPERACION   65   (3 × 15 documentos + 2 × 9 líneas y registros + 2 EXECUTE)
ROL_SGEJD_PRUEBAS      8   (DELETE × 8 tablas; existe, pero ya no está otorgado a nadie)
```

Consultado con `DBA_ROLE_PRIVS`, `DBA_SYS_PRIVS` y `DBA_TAB_PRIVS` después de `S08_05`
(`bd2/salidas/S08_01_despues.txt`).

## 6. ¿La aplicación sigue funcionando? Las 217 pruebas del backend

Se corrieron las pruebas del backend (JUnit, incluidas las de integración con Oracle: ventas, anulación,
comprobantes, reemplazos, correo, cobros, mantenimientos y el flujo completo de punta a punta) contra la base de
práctica, con SUNAT simulado y el correo apagado. El backend se conectó como `JD_APP`, con
`hibernate.default_schema = JDREFRIGERACION` y cada conexión iniciada con
`ALTER SESSION SET CURRENT_SCHEMA = JDREFRIGERACION`.

| Corrida | Usuario | Pruebas | Fallan | Causa |
|---|---|---|---|---|
| Línea base | `JDREFRIGERACION` (dueño) | 217 | **2** | Trigger de S2 (ORA-20022, hallazgo 4) |
| Mínimo privilegio | `JD_APP` con sus 2 roles | 217 | 20 | Las mismas 2, más 18 con **ORA-41900** en `DELETE FROM intentos_correo` (17) y `DELETE FROM historial_cambios` (1) |
| Con el rol temporal de pruebas | `JD_APP` + `ROL_SGEJD_PRUEBAS` | 217 | **2** | **Idéntico a la línea base** |

Las 18 fallas de la segunda corrida **no son de la aplicación**: son de la limpieza que hacen las propias pruebas
con `JdbcTemplate`. La aplicación no tiene ningún `DELETE FROM`, y las pruebas sí, en 8 tablas: `COMPROBANTES`,
`DETALLE_VENTA`, `ENVIOS_CORREO`, `ENVIOS_SUNAT`, `HISTORIAL_CAMBIOS`, `INTENTOS_CORREO`, `PRODUCTOS` y `VENTAS`.
Con esos DELETE en un rol aparte (`ROL_SGEJD_PRUEBAS`), el resultado es idéntico al del dueño. Ese rol se revocó
al terminar (`S08_05`): existe solo para correr pruebas en la base de práctica, nunca en desarrollo ni en
producción.

## 7. Matriz de accesos

| Usuario | Rol | Maestras (5) | Documentos (15) | Líneas y registros (9) | Auditoría y errores | PL/SQL de S1 | DDL |
|---|---|---|---|---|---|---|---|
| `JD_APP` | `ROL_SGEJD_MAESTROS` | S, I, U, D | — | — | — | — | — |
| `JD_APP` | `ROL_SGEJD_OPERACION` | — | S, I, U | S, I | — | EXECUTE | — |
| `JD_REPORTES` | `ROL_SGEJD_CONSULTA` | S (4 de 5: sin sedes) | S (sin envíos de correo) | S (líneas) | — | — | — |
| `JD_APP` (solo pruebas) | `ROL_SGEJD_PRUEBAS` | D en `PRODUCTOS` | D en `VENTAS`, `COMPROBANTES`, `ENVIOS_CORREO` | D en 4 | — | — | — |
| `JDREFRIGERACION` | — (dueño) | todo | todo | todo | todo | todo | CREATE TABLE, VIEW, SEQUENCE, PROCEDURE, TRIGGER |

Evidencia de cada celda: sección 4 (ORA-41900, ORA-01031, ORA-00942) y sección 6 (217 pruebas).

## 8. Integración con ADS y LP2

| BD2 | ADS | LP2 |
|---|---|---|
| Roles por función | Funciones del negocio (S6); seguridad de mínimo privilegio (atributo 2, S1) | Sin cambios de código |
| `JD_APP` en lugar del dueño | Contenedor Oracle (C2, S2) | Para producción: `DB_USERNAME=jd_app`, `spring.jpa.properties.hibernate.default_schema` y `spring.datasource.hikari.connection-init-sql` (en `application-prod.properties`, no por variables de entorno: hallazgo 3) |
| `JD_REPORTES` | Stakeholder Lisandro (S1): reportes sin riesgo | — |
| La auditoría no la ve la aplicación | Auditabilidad (S1) | El rastro de S2 no puede borrarlo quien tiene la conexión del backend |

**Pendiente para la integración final:**

1. Aplicar `S08_02` en desarrollo y producción.
2. Cambiar el usuario del backend.
3. Mantener `ddl-auto=update` solo en desarrollo, conectado como dueño, porque `JD_APP` no puede alterar tablas.
4. Corregir las dos pruebas del hallazgo 4.

## 9. Error o hallazgo

**Hallazgo 1: Oracle 23.26 ya no responde ORA-01031 por un privilegio de objeto.** La guía espera ORA-01031 al
borrar sin permiso. Oracle respondió:

```
DELETE FROM jdrefrigeracion.ventas WHERE id = -1
ORA-41900: missing DELETE privilege on "JDREFRIGERACION"."VENTAS"
```

Cuando el usuario **ve** la tabla (tiene SELECT) pero le falta la acción, la versión 23.26 dice exactamente qué
falta y dónde. ORA-01031 quedó para los privilegios de sistema (`CREATE TABLE`, `ALTER TRIGGER`). Y si ni
siquiera la ve, sigue siendo ORA-00942. Es la segunda vez que la versión real contradice la guía; la primera
fue ORA-00054 en S3.

**Hallazgo 2: el dueño tenía un rol, no tres.** En S7, `SESSION_ROLES` mostró `DB_DEVELOPER_ROLE`, `RESOURCE` y
`SODA_APP`. `DBA_ROLE_PRIVS` mostró que solo el primero está otorgado. Los otros vienen **anidados**
(`DB_DEVELOPER_ROLE` → `RESOURCE` → `SODA_APP`), y `SESSION_ROLES` lista todos los activos, incluidos los
heredados. La primera versión del script revocaba los tres por separado, y dos de esos REVOKE habrían fallado.
Basta con revocar el de arriba.

**Hallazgo 3: la configuración no llegaba, y el error decía otra cosa.** La primera corrida como `JD_APP`
falló entera con `Schema validation: missing table [ajustes_inventario]`, y parecía un privilegio faltante. No lo
era: la variable `SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA` nunca llegó a Hibernate. Spring convierte cada
`_` en `.`, y la propiedad quedó como `hibernate.default.schema`. Con `CONNECTION_INIT_SQL` pasó lo mismo:
`connection.init.sql`, que ni siquiera se pudo asignar (`BindException`). Se resolvió pasando las dos con
`SPRING_APPLICATION_JSON`, que conserva el nombre exacto. Para producción irán en `application-prod.properties`.
En total hicieron falta cuatro corridas. Las tres primeras fallaron por configuración, y por eso la lección es
leer el `Caused by` más profundo antes de concluir que falta un privilegio.

**Hallazgo 4: el trigger de S2 atrapó a dos pruebas del backend.** Ya en la línea base, como dueño,
`ComprobanteReemplazoIntegracionTest` y `VentaAnuladaSinNotaIntegracionTest` fallaron así:

```
UPDATE ventas SET estado = 'ANULADA', fecha_anulacion = SYSDATE WHERE id = ?
ORA-20022: Para anular la venta 1104 indique el motivo (al menos 5 caracteres).
```

Esas pruebas simulan una venta anulada "a mano" con SQL directo, sin motivo ni usuario. Es justo lo que el trigger
de S2 se diseñó para impedir, porque cualquier script podría hacerlo. El trigger hizo su trabajo. Lo que hay que
corregir son las pruebas: agregarles `motivo_anulacion` y `anulado_por` a su `UPDATE`.

**Hallazgo 5: el único DELETE de más lo pedían las pruebas.** Con mínimo privilegio, todo lo que dejó de funcionar
fue la limpieza de las pruebas (sección 6). La aplicación nunca necesitó borrar documentos, líneas, registros ni
auditoría. Ese privilegio se le habría dado al usuario de producción "para que pasen las pruebas" si no se hubiera
separado en un rol propio.

## 10. Reflexión

¿Por qué conceder un privilegio "por si acaso" es un riesgo aunque la aplicación nunca lo use, y cómo cambia ese
riesgo con un rol por función?

Un privilegio que la aplicación no usa no le sirve al sistema, pero sí le sirve a quien consiga usar su conexión:
un error en una consulta, un script mal apuntado o una contraseña filtrada. En SGE-JD, el backend conectado como
dueño podía desactivar el trigger de auditoría, borrar ventas y volver a encenderlo. Ninguna línea de código lo
hacía, pero la base lo permitía. Con roles por función, el privilegio queda atado a una tarea con nombre. Se ve en
el diccionario quién lo tiene y por qué, y se quita de una vez a todos los que lo heredan. El caso de las pruebas lo
mostró: el DELETE de limpieza existe, pero vive en `ROL_SGEJD_PRUEBAS`, que solo se activa para correr pruebas
en la base de práctica, y no en el usuario que atiende a Lisandro y a Lucy todos los días.
