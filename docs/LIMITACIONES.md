# SGE-JD: qué hace hoy y qué todavía no

**Para:** Lisandro y Lucy · JD Refrigeración S.A.C.
**Versión del sistema:** 31 de octubre de 2026 (entrega académica de LP2)

Esta hoja dice con claridad qué partes del sistema ya funcionan de verdad y cuáles todavía son una **simulación** o necesitan una decisión de ustedes. Léanla antes de usar SGE-JD con clientes reales.

---

## 1. Lo que ya funciona de verdad

- Catálogo de productos y categorías, con precios calculados desde el costo y el margen.
- Clientes y proveedores, con búsqueda del nombre y la dirección por RUC (SUNAT) o DNI (RENIEC).
- Proformas en soles o dólares, con descuento por línea, aceptación, rechazo y conversión en venta.
- Ventas al contado y a crédito, anulación con devolución de stock. Si la venta anulada ya tenía pagos, queda como devolución pendiente en Cobranza › Devoluciones.
- Detracción (12 % en servicios) y retención del IGV (3 % si el cliente es agente de retención), con su registro en Cobranza.
- Compras a proveedores, que suben el stock de los materiales, al contado o a crédito.
- **Cuentas por pagar:** lo que JD le debe a cada proveedor, lo vencido y lo que vence en 7 días, con pagos parciales.
- Cobranza: abonos parciales, saldos por venta, días de atraso y recordatorio por WhatsApp. Un cobro mal registrado se anula con su motivo.
- Servicios: órdenes de trabajo con técnico, hora y materiales; **agenda semanal de técnicos** que no deja cruzar dos visitas; sedes de cada cliente, equipos instalados con garantía y contratos de mantenimiento.
- Postventa: mantenimientos programados, vencidos, próximos y realizados.
- **Ajustes de inventario:** conteo físico, mermas, material dañado o perdido y stock inicial, que salen en el kardex.
- **Rentabilidad:** cuánto se gana por venta, cliente, producto, categoría y técnico.
- Kardex, alertas de stock mínimo, historiales por cliente y proveedor, exportación a Excel.
- Inicio de sesión con usuario y contraseña. Lucy (asistente) registra y consulta; las operaciones delicadas (anular ventas o cobros, editar el catálogo, eliminar, ajustar inventario, ver la rentabilidad…) son de Lisandro, que puede **darle a Lucy los permisos que quiera** desde Configuración › Usuarios y permisos.
- **Historial de cambios:** quién hizo qué y cuándo (incluidos los intentos sin permiso) y quién inició sesión. Solo lo ve Lisandro; nadie lo puede borrar desde el sistema.
- Tema claro u oscuro y letra grande, a gusto de cada uno.

## 2. Lo que es una simulación (no tiene validez real)

| Parte | Qué pasa hoy | Qué hace falta para que sea real |
|---|---|---|
| **Facturas y boletas electrónicas** | El sistema arma la factura o boleta, le pone su número y genera el PDF, pero **no la envía a SUNAT**. Esos comprobantes **no tienen validez tributaria**: no los entreguen como factura oficial. Lo mismo pasa con las notas de crédito. | Contratar un proveedor de facturación electrónica autorizado por SUNAT (OSE, por ejemplo Nubefact) y conectar su cuenta. |
| **WhatsApp** | Al "enviar" una proforma por WhatsApp, el sistema solo lo anota internamente; **el cliente no recibe nada**. Sí funciona el enlace de WhatsApp de Postventa, que abre la conversación en su celular. | Una cuenta de WhatsApp Business API (tiene costo mensual). |
| **Correo** | El envío de proformas y facturas por correo y el resumen diario de mantenimientos **funcionan en la laptop de desarrollo** con una cuenta Gmail de prueba (la de Aron); en la computadora de la empresa todavía no sale ningún correo. | Una cuenta de correo de la empresa para enviar (por ejemplo Gmail con "contraseña de aplicación") y el correo de la oficina que recibe el resumen diario. |

## 3. Límites que conviene conocer

- **Funciona solo en esta computadora.** El sistema corre en la PC donde se instaló; desde otra PC o desde el celular todavía no se puede entrar. Si esa PC está apagada, el sistema también.
- **Para encenderlo:** doble clic en `iniciar_sgejd.bat` (tarda uno a dos minutos y abre el navegador). Para apagarlo: `detener_sgejd.bat`. No cierren las dos ventanas negras ("Backend" y "Frontend") mientras lo usan.
- **Respaldos:** el sistema puede respaldarse solo todos los días. Se activa una vez con `programar_respaldo.bat`, indicando una carpeta **fuera de la PC** (un USB o la nube) para la copia; **todavía no está activado** porque falta elegir esa carpeta (punto 6 de abajo). Mientras tanto, hagan un respaldo con `respaldar_sgejd.bat` al menos una vez por semana y copien el archivo de `C:\SGE-JD\respaldos` a un USB o a la nube. Si el disco de la PC falla sin respaldo, se pierde todo.
- **Búsqueda por RUC o DNI:** el servicio gratuito que usamos no deja hacer muchas búsquedas seguidas. Si aparece "servicio ocupado", esperen unos segundos y vuelvan a intentar, o escriban los datos a mano.
- **Tipo de cambio:** se carga solo el del día de SUNAT; si no hay internet, hay que escribirlo.
- **Contraseñas:** las de `lisandro` y `lucy` las puso el equipo de desarrollo. **Al recibir el sistema, cada uno debe cambiar la suya** (nosotros les mostramos cómo). Después de 5 intentos fallidos, la cuenta espera unos minutos antes de dejar intentar de nuevo.
- **La base empieza vacía (30/10/2026):** se borró todo lo que usamos para probar: clientes, proveedores, productos, categorías, proformas, ventas, compras, cobros y mantenimientos. Las facturas y boletas empiezan en el número 1. Si algún dato de prueba les sirve, está guardado en el respaldo `sgejd_antes_de_entrega_20261030_2224.dmp`.
- **El stock inicial** hay que ponerlo una vez con un conteo físico del almacén: Inventario › Ajustes de inventario › "+ Conteo físico", material por material.
- **Rentabilidad de ventas antiguas:** desde el 31/10/2026 cada venta guarda el costo del momento. En las anteriores se usó el costo que tenía el producto ese día, así que su ganancia es aproximada.
- **Compras anteriores:** las compras registradas antes del 31/10/2026 quedaron como pagadas al contado. Si alguna se debe todavía, avísennos para pasarla a crédito.
- **Memoria de la PC:** con 8 GB de RAM el sistema funciona justo. Si se pone lento o no abre, cierren Chrome y otros programas y vuelvan a ejecutar `iniciar_sgejd.bat`.

## 4. Decisiones que necesitamos de ustedes

1. **Topes de descuento:** hoy Lisandro puede dar hasta 30 % y Lucy hasta 10 % por línea. ¿Están bien?
2. **Costos y márgenes:** el reporte de Rentabilidad ya es solo de Lisandro (o de quien él autorice), pero en la lista de productos Lucy todavía ve el costo y el margen de cada uno. ¿Debe verlos?
3. **Mantenimientos:** hoy solo se programan para equipos de climatización y refrigeración. ¿Ventilación forzada también los necesita?
4. **Teléfono y correo de la empresa** que deben salir impresos en las facturas y proformas.
5. **Facturación electrónica real:** ¿con qué proveedor (OSE) quieren trabajar?
6. **Respaldo automático:** ¿en qué carpeta fuera de la PC se guarda la copia diaria (un USB, el OneDrive o Google Drive de la empresa)?
7. **Servidor:** para entrar desde otra PC o desde el celular hay que instalar el sistema en un servidor en internet (tiene costo mensual). ¿Lo quieren?
8. **Correo de la empresa:** ¿con qué cuenta salen las proformas y facturas, y a qué correo de la oficina llega el resumen diario de mantenimientos?
9. **Cómo trabajan hoy:** para la documentación del proyecto necesitamos confirmar cómo hacen hoy las cotizaciones, el control de stock, la facturación, la agenda de los técnicos y las deudas (son 9 preguntas cortas; se las llevamos impresas).

## 5. Antes de empezar a usarlo

1. ~~Borrar los datos de prueba~~ ✅ hecho: la base está vacía.
2. Crear las categorías y los productos (Inventario › Productos), y luego cargar el stock real con Inventario › Ajustes de inventario › "+ Conteo físico", material por material.
3. Registrar sus clientes y proveedores (con el RUC o DNI se completan solos).
4. Cada uno cambia su contraseña: Configuración › "Cambiar mi contraseña".
5. Activar el respaldo automático en la carpeta que elijan (punto 6).
6. Configurar el correo de la empresa (punto 8).

---

*Equipo: Yoel Wagner Chambi Sirena, Wilson Ronald Apaza Mamani y Aron Rodrigo Magaño Quispe. Detalle técnico: sección 5 de la documentación, en https://ywagnercs.github.io/proyecto-SAC/.*
