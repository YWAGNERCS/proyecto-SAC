# S14 · Integración y estabilización (LP2) y trazabilidad (ADS)

**SGE-JD · JD Refrigeración S.A.C. · Equipo CherryTree · 07/11/2026**

Qué pide la sesión y dónde está la evidencia en el proyecto:

| Pide la guía | En SGE-JD | Sección |
|---|---|---|
| Diagnosticar totales calculados en el cliente | Diagnóstico de las 13 listas | 1 |
| Paginar un listado de alto volumen sin romper el agregado | Ventas, Proformas y Compras, paginadas desde el 05/11/2026 | 1 |
| Checklist de integración funcional (Tabla 6) | Recorrido automatizado + recorrido en vivo | 2 |
| Auditoría de al menos dos acciones críticas | `HISTORIAL_CAMBIOS` (RF-29) | 3 |
| Prueba end-to-end contra la base real | `FlujoCompletoEndToEndTest` | 4 |
| Bitácora de estabilización | 5 hallazgos reales, cada uno con su prueba | 5 |
| Matriz de trazabilidad (ADS S14) | [RTM.md](RTM.md) | 6 |

## 1. Totales del servidor frente a totales del navegador

**Pregunta de la guía:** ¿alguna pantalla suma en el navegador un total que debería venir del servidor, y ese
cálculo depende de tener la lista completa?

Se revisaron todos los `reduce(` de la SPA:

| Pantalla | ¿Paginada? | ¿De dónde salen sus totales? | Riesgo |
|---|---|---|---|
| Ventas (reporte) | Sí (20, 50 o 100 por página) | Del servidor: `GET /ventas/pagina` devuelve la página **y** `resumen` (monto, cantidad y ticket promedio por moneda) calculado sobre **todo** el filtro (`VentaService.reportePagina`) | Ninguno |
| Ventas › Pagos atrasados | No: la lista llega completa (`/ventas/pagos-atrasados`) | Navegador (`venta-list.ts`, `agregados`) | Ninguno mientras no se pagine. Si se pagina, debe pasar al servidor |
| Proformas, Compras | Sí | No muestran totales del filtro | Ninguno |
| Cobranza (por cobrar, cobros, devoluciones) | No: listas completas | Navegador | Igual que Pagos atrasados: queda registrado en la bitácora como condición |
| Formularios (Nueva venta, Nueva compra, Orden) | — | Vista previa; el servidor recalcula al guardar | Ninguno |

**Conclusión:** el caso de la guía (paginar y seguir sumando en el navegador) ya se evitó cuando se paginó Ventas el
05/11/2026: los totales del reporte vienen del servidor. Las sumas que quedan en el navegador son de listas que llegan
completas. Si algún día se paginan, deben pasar al servidor, y así quedó anotado en la bitácora (H-4).

**Verificación cruzada (no solo "se ve bien"):** `FlujoCompletoEndToEndTest` pide el reporte de ventas de un cliente
de 5 en 5, recorre **todas** las páginas, suma a mano los totales de las ventas REGISTRADAS en soles y comprueba que
la suma es igual al `montoTotal` que calculó el servidor.

## 2. Checklist de integración funcional (Tabla 6)

| Paso | Qué se verifica | Prueba automática (`FlujoCompletoEndToEndTest`) | En vivo (sistema completo levantado) |
|---|---|---|---|
| 1. Login real | Token emitido y aceptado | Token simulado con los mismos claims de Keycloak, leído con el conversor real de `SecurityConfig` | Login de Lisandro en Keycloak (PKCE) |
| 2. Crear una venta | Stock descontado y venta registrada en la misma transacción | Proforma → aceptar → venta: el material pasa de 10 a 7; la proforma queda CONVERTIDA; se emite el comprobante y se programan 4 visitas | Nueva venta con equipo y rutina |
| 3. Listado paginado | Página 1 rápida y total de páginas coherente | Se recorren todas las páginas de 5 en 5 | Ventas, 20 por página |
| 4. Reporte | `montoTotal` = suma manual de las filas | Verificación cruzada de la sección 1 | Comparar el total del reporte con la suma de las filas |
| 5. Anular | Vuelve el stock y se ve en una consulta nueva | Stock otra vez 10; visitas CANCELADO; devolución por lo cobrado; segunda anulación → 409 | Anular desde Ventas |
| 6. Sin token | 401, nada a medias | Anular sin token → 401; la asistente → 403 | Cerrar sesión y repetir |

El paso de cobro (abono parcial y total) también está en la prueba: la venta sale de "por cobrar" al llegar a saldo 0.

## 3. Auditoría

`HISTORIAL_CAMBIOS` (RF-29) registra **cada** POST, PUT, PATCH y DELETE del panel, no solo dos acciones. Lo hace un
filtro dentro de la cadena de seguridad (`AuditoriaFilter`), después de que la petición termina:

| Columna | Qué guarda |
|---|---|
| `USUARIO` | El usuario del JWT (`preferred_username`), nunca un valor fijo |
| `ROLES` | Rol y permisos del token |
| `FECHA_HORA`, `IP` | Cuándo y desde dónde |
| `MODULO`, `ACCION` | En palabras: "Registró la venta #288", "Anuló la venta #288", "Intentó anular la venta #288" |
| `RESULTADO` | EXITO, DENEGADO (401/403), RECHAZADO (409/400, con el motivo) o ERROR |

Se guarda en su propia transacción (`REQUIRES_NEW`): si la operación del usuario falla, el intento igual queda.
`FlujoCompletoEndToEndTest` comprueba que la anulación hecha por el usuario de la prueba quedó con resultado EXITO y el
intento sin permiso con DENEGADO.

Consulta para la evidencia:

```sql
SELECT fecha_hora, usuario, accion, resultado FROM historial_cambios
WHERE modulo = 'Ventas' ORDER BY id DESC FETCH FIRST 10 ROWS ONLY;
```

## 4. Prueba end-to-end real

`src/test/java/com/bomerp/FlujoCompletoEndToEndTest.java` levanta el contexto completo de Spring
(`@SpringBootTest` + `@AutoConfigureMockMvc`). La petición pasa por la cadena de seguridad, el filtro de auditoría,
los controladores, los servicios, los eventos entre módulos y Oracle, **sin ningún mock**. Recorre:

```
producto → proforma → aceptar → venta a crédito con rutina de mantenimiento → comprobante (PENDIENTE)
  → 4 visitas programadas → reporte paginado = suma manual → cobro parcial → cobro total
  → sin token 401 / asistente 403 → anulación (stock, visitas, devolución, 409) → auditoría
```

- **Datos propios:** crea sus productos (un equipo y un material con 10 unidades) y usa un cliente que no es agente
  de retención. No depende del stock que dejó otra corrida (el error frecuente de la guía).
- **No deja rastro:** corre en una transacción que se deshace al final, así que no quedan ventas en la base y no se
  envía nada a SUNAT (el envío ocurre solo al confirmar). Las filas del historial, que se guardan aparte, se borran al
  terminar, incluida la del intento sin token (hallazgo H-5). Se comprobó en Oracle después de correrla: 0 productos "E2E", la última venta sigue siendo la #287 y 0
  filas del usuario `prueba-e2e`.
- **Ejecutar** (con Oracle encendido; Keycloak no hace falta):

```powershell
mvn test "-Dtest=FlujoCompletoEndToEndTest"
```

Resultado del 07/11/2026: `Tests run: 1, Failures: 0, Errors: 0` (unos 17 s).

## 5. Bitácora de estabilización

| # | Hallazgo | Corrección | Prueba de no regresión |
|---|---|---|---|
| H-1 (06/11) | **Un rechazo de SUNAT se marcaba como válido.** El código se sacaba de los dígitos del texto del error, que trae el número de ticket: un rechazo 2800 se leía como 8001 (observado, es decir, válido). | El código se lee de `faultcode` (`soap-env:Client.2800`), no del texto (`SunatSoapClient.codigoDeFalla`). | `ClasificacionCodigosSunatTest.codigoDelFaultcode` (5 casos) y `SunatBetaTest` (rechazo 2800 real en beta). |
| H-2 (06/11) | **La beta de SUNAT respondía 401 a envíos seguidos.** Al registrar varias ventas juntas, algunos comprobantes fallaban. | Envíos en cola de un solo hilo con pausa de 1,5 s; un 401 o 5xx deja el comprobante PENDIENTE y se reintenta cada 3 minutos (hasta 20 veces). | `EnvioSunatServiceTest.sunatSinResponderQuedaPendienteYCuentaElIntento` y `elReintentoNoTomaLosQueYaSuperaronElMaximoDeIntentos`. |
| H-3 (07/11) | **Ordenar por Total mezclaba monedas.** Al ordenar ventas por Total, una de US$ 20,874 quedaba debajo de una de S/ 66,505, porque se comparaba solo el número. | Ventas y proformas comparan en soles con su propio tipo de cambio; compras y productos, con el del día (`Orden.enSoles`). | `OrdenTest.losMontosEnDolaresSeComparanEnSoles` y verificación por API: la venta #266 en dólares sale primera. |
| H-5 (07/11) | **La prueba de punta a punta dejaba rastro en el historial.** En la verificación en vivo, el Historial mostraba "anónimo · Intentó anular la venta #291, #292 y #293", ventas que no existen. Eran de la prueba: la venta se deshace al terminar, pero el intento sin token se guarda aparte (REQUIRES_NEW) con usuario "anónimo", y la limpieza solo borraba las filas del usuario `prueba-e2e`. | La limpieza borra también la fila anónima de la venta de la prueba (por su ruta exacta). Se borraron las 3 filas que habían quedado, después de comprobar que esas ventas no existían. | `FlujoCompletoEndToEndTest` vuelto a ejecutar: 0 filas nuevas en `HISTORIAL_CAMBIOS`. |
| H-4 (07/11) | **Condición de las sumas en el navegador (sección 1).** Pagos atrasados y Cobranza suman en la SPA, lo que es correcto solo porque esas listas llegan completas. | Se registra la condición: si se paginan, sus totales pasan al servidor, como en el reporte de ventas. | Verificación cruzada del reporte paginado en `FlujoCompletoEndToEndTest`. |

## 6. Trazabilidad (ADS S14)

La matriz completa, con la columna de requerimiento, está en [RTM.md](RTM.md): 35 requisitos funcionales y 9 no
funcionales, cada uno con arquitectura, base de datos, API, pantalla y prueba, más dos requisitos trazados eslabón
por eslabón (RF-11 stock y RF-14 rutina de mantenimiento) y la lista de pruebas que faltan.
