# S14 · Decisiones arquitectónicas y trazabilidad (ADS)

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · 07/11/2026**

Contenido:

1. El hallazgo: una inconsistencia real entre el diseño documentado y el código.
2. El ADR formalizado: una decisión que otro módulo contradijo en silencio.
3. Los atributos de calidad justificados con evidencia.
4. Los requerimientos trazados de punta a punta.
5. La matriz final.

## 1. Hallazgo: el RNF-01 decía lo contrario de lo que hace el código

**Inconsistencia.** El requisito de arquitectura del README decía: *"Hoy hay 2 módulos que usan repositorios de
otros"*. El código actual ya no lo hace:

- desde el 31/10/2026, Facturación, Reportes y Rentabilidad usan fachadas de servicio;
- Ventas avisa a Facturación y Postventa con eventos;
- `ModularityTests` (Spring Modulith) verifica en cada `mvn test` que ningún módulo use el repositorio de otro ni
  forme ciclos, y pasa.

El punto 21 de la sección 5 del README sí lo marcaba como resuelto. Es decir, el mismo documento se contradecía.

**Por qué importa.** Quien evalúe la arquitectura con el RNF-01 concluiría que el monolito modular está roto, cuando
la prueba automática demuestra lo contrario. Una afirmación de diseño que no se actualiza junto con el código deja de
ser evidencia y pasa a ser ruido. Es el mismo problema que este ADR resuelve para el stock.

**Corrección (07/11/2026).** El RNF-01 ahora describe el estado real y remite a `ModularityTests` como verificación.

## 2. ADR-006 · Solo los materiales controlan stock, con una única regla en el dominio

**Estado:** aceptada (13/10/2026). Se formaliza el 07/11/2026, porque hasta ahora vivía solo como una frase del README.

### Contexto

JD Refrigeración compra los equipos (aires, cámaras frigoríficas, extractores) **por proyecto**: el cliente acepta la
proforma, JD los pide al proveedor y los instala. No los guarda en un almacén. Lo que sí guarda y cuenta son los
**materiales** (tubería de cobre, gas, cables, soportes).

Cuando se diseñó la venta (RF-11) se decidió que los equipos no descontaran stock. Pero esa decisión no quedó como
una regla única del dominio. Después, el módulo **Compras** se programó sumando stock a **todos** los productos.
Cada módulo, mirado solo, "funcionaba":

- las compras sumaban stock a los equipos;
- las ventas no se lo descontaban;
- el stock de los equipos solo crecía.

Se detectó y corrigió el 13/10/2026. Está registrado en el README, sección 5, como "Compras y stock de equipos".

### Decisión

1. **Una sola regla, en la entidad que tiene el dato** (Information Expert): `Producto.controlaStock()` devuelve
   verdadero solo para el sector `MATERIAL_SERVICIO`.
2. **Todo movimiento de stock pasa por la fachada del catálogo** y consulta esa regla:
   - `ProductoService.descontarStock` (ventas y órdenes de trabajo);
   - `ProductoService.sumarStock` (compras y anulación de ventas);
   - además la usan `AjusteInventarioService` (rechaza ajustes de equipos), `KardexService` (sin saldo para
     equipos) y la consulta de alertas `findProductosEnAlerta` (solo materiales).
3. Ningún otro módulo usa `ProductoRepository`: lo impiden ADR-002 y `ModularityTests`. Fuera del catálogo, el
   stock solo lo cambian dos caminos:
   - la fachada (`descontarStock` y `sumarStock`);
   - los ajustes de inventario, que antes de cambiarlo rechazan los productos que no controlan stock
     (`AjusteInventarioService`, `if (!producto.controlaStock())`).

**Verificable contra el código:**
- `Producto.java`: `controlaStock()` devuelve `sector == Sector.MATERIAL_SERVICIO`.
- `ProductoService.java`: `if (!p.controlaStock()) continue;` en `descontarStock` y `if (p.controlaStock())` en
  `sumarStock`.

### Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Todos los productos controlan stock | El stock de los equipos estaría casi siempre en 0 y el sistema impediría vender un equipo que todavía no se compró, que es justo el caso normal de JD. |
| Una casilla "controla stock" por producto | Más flexible, pero cada equipo nuevo dependería de que alguien marque bien la casilla. El sector ya expresa la diferencia. Queda como opción si JD empieza a almacenar equipos (ver Consecuencias). |
| Dejar la condición en cada servicio (lo que había) | Es lo que causó el error: Compras se programó sin la condición y nada lo detectó. |

### Consecuencias

- **Positivas:**
  - un cambio de regla se hace en un solo método;
  - un módulo nuevo que mueva stock lo hará por la fachada, que ya aplica la regla;
  - el kardex y las alertas vuelven a ser confiables.
- **Negativas:**
  - de los equipos no se sabe "cuántos hay en almacén";
  - si JD empieza a guardar equipos, hay que cambiar la regla, por ejemplo a la casilla por producto. Es una decisión
    del cliente que habría que confirmar con Lisandro.
- **Pruebas que la sostienen:**
  - `StockProductoTest`: equipos sin stock, todo o nada;
  - `CompraServiceTest`: la compra sube solo el stock de los materiales;
  - `AjusteInventarioServiceTest`: no se ajustan equipos;
  - `FlujoCompletoEndToEndTest`: contra Oracle, al vender, el equipo no cambia y el material pasa de 10 a 7.

## 3. Atributos de calidad: de la intención declarada a la evidencia

Los atributos se declararon como requisitos no funcionales al iniciar el proyecto (RNF-01 a RNF-05). Esta tabla
reemplaza cada intención por la evidencia que se construyó después.

| Atributo | Decisión inicial (lo declarado) | Evidencia real (lo construido y medido) | ¿Se cumple? |
|---|---|---|---|
| **Seguridad** (RNF-05) | "`/api/v1/admin/**` exige JWT; autorización por rol" | Keycloak con PKCE y JWT RS256 (S10b/S11). `pruebas-seguridad/05_matriz.ps1`: 14 de 14 casos con el código esperado, con tokens reales (401 sin token o con firma alterada, 403 a la asistente, 200/201/204 al administrador). `FlujoCompletoEndToEndTest`: 401 y 403 al anular. Toda acción queda en `HISTORIAL_CAMBIOS`, incluidos los intentos denegados. | Sí |
| **Mantenibilidad / modularidad** (RNF-01) | "Monolito modular, un módulo por paquete" | `ModularityTests` falla el build si un módulo usa el repositorio de otro o hay ciclos (verde el 07/11). Ventas no conoce a Facturación ni a Postventa: publica `VentaRegistradaEvent` y `VentaAnuladaEvent`. La rutina de mantenimiento del 07/11 se agregó sin que Ventas dependa de Postventa. 110 pruebas automáticas. | Sí (corregida la descripción, sección 1) |
| **Rendimiento** (RNF-02) | "Endpoints por debajo de 1000 ms" (sin medir) | Medición S13 con Actuator, en uso real de la SPA: `/clientes` 27 ms, `/productos` 92 ms, `/proformas/pagina` 88 ms, `/ventas/pagina` 642 ms (promedios). Paginación de 20 por página desde el 05/11. Tipo de cambio en caché (Cache-Control 10 min). **Pero** las listas completas `/ventas` (1079 ms) y `/proformas` (1426 ms) pasan de 1000 ms. | Parcial: las pantallas principales sí; las dos listas completas quedan como pendiente (consulta con N+1) |
| **Fiabilidad / integridad transaccional** (RF-11, RF-17) | "Descuento de stock atómico" | Bloqueo `SELECT ... FOR UPDATE` en orden de id (evita interbloqueos); todo o nada con 409. `StockProductoTest` y `FlujoCompletoEndToEndTest` contra Oracle: 10 → 7 al vender, vuelve a 10 al anular, y una segunda anulación da 409 sin tocar nada. | Sí |
| **Interoperabilidad** (RNF-03 y RF-10) | "API REST JSON versionada" | API `/api/v1` consumida por la SPA Angular. Integración real con SUNAT: el ambiente beta **aceptó** facturas, boletas y notas de crédito firmadas por SGE-JD (por ejemplo F001-130 y FC01-3, con CDR) y rechazó con el código correcto lo que debía rechazar (2800). Consulta de RUC/DNI con apis.net.pe. | Sí (producción espera el certificado de JD) |

## 4. Requerimientos trazados de punta a punta

Los cinco eslabones (requerimiento → arquitectura → base de datos → backend → prueba) están completos para:

- **RF-11, descuento de stock atómico.** Es el requerimiento del ADR-006.
- **RF-14, rutina de mantenimiento al vender equipos.**

Están en [RTM.md](RTM.md), sección 3. Ningún eslabón queda pendiente en estos dos.

## 5. Matriz de trazabilidad final

[RTM.md](RTM.md), con la columna de requerimiento: RF-01 a RF-35 y RNF-01 a RNF-09, cada uno con arquitectura,
base de datos, API, pantalla, prueba y estado. Los eslabones que faltan, como las pruebas automáticas de 9
requisitos, están registrados como pendientes.
