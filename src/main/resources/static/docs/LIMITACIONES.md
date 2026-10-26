# SGE-JD: qué hace hoy y qué todavía no

**Para:** Lisandro y Lucy · JD Refrigeración S.A.C.
**Versión del sistema:** octubre de 2026 (entrega académica de LP2)

Esta hoja dice con claridad qué partes del sistema ya funcionan de verdad y cuáles todavía son una **simulación** o necesitan una decisión de ustedes. Léanla antes de usar SGE-JD con clientes reales.

---

## 1. Lo que ya funciona de verdad

- Catálogo de productos y categorías, con precios calculados desde el costo y el margen.
- Clientes y proveedores, con búsqueda del nombre y la dirección por RUC (SUNAT) o DNI (RENIEC).
- Proformas en soles o dólares, con descuento por línea, aceptación, rechazo y conversión en venta.
- Ventas al contado y a crédito, anulación con devolución de stock.
- Compras a proveedores, que suben el stock de los materiales.
- Cobranza: abonos parciales, saldos por venta y días de atraso.
- Postventa: mantenimientos programados, vencidos, próximos y realizados.
- Kardex, alertas de stock mínimo, historiales por cliente y proveedor, exportación a Excel.
- Inicio de sesión con usuario y contraseña. Lucy (asistente) no puede crear, editar ni borrar productos ni anular ventas.

## 2. Lo que es una simulación (no tiene validez real)

| Parte | Qué pasa hoy | Qué hace falta para que sea real |
|---|---|---|
| **Facturas y boletas electrónicas** | El sistema arma la factura o boleta, le pone su número y genera el PDF, pero **no la envía a SUNAT**. Esos comprobantes **no tienen validez tributaria**: no los entreguen como factura oficial. Lo mismo pasa con las notas de crédito. | Contratar un proveedor de facturación electrónica autorizado por SUNAT (OSE, por ejemplo Nubefact) y conectar su cuenta. |
| **WhatsApp** | Al "enviar" una proforma por WhatsApp, el sistema solo lo anota internamente; **el cliente no recibe nada**. Sí funciona el enlace de WhatsApp de Postventa, que abre la conversación en su celular. | Una cuenta de WhatsApp Business API (tiene costo mensual). |
| **Correo** | El envío de proformas y facturas por correo y el resumen diario de mantenimientos **están programados pero no configurados**: hoy no sale ningún correo. | Una cuenta de correo de la empresa para enviar (por ejemplo Gmail con "contraseña de aplicación") y el correo de la oficina que recibe el resumen diario. |

## 3. Límites que conviene conocer

- **Funciona solo en esta computadora.** El sistema corre en la PC donde se instaló; desde otra PC o desde el celular todavía no se puede entrar. Si esa PC está apagada, el sistema también.
- **Para encenderlo:** doble clic en `iniciar_sgejd.bat` (tarda uno a dos minutos y abre el navegador). Para apagarlo: `detener_sgejd.bat`. No cierren las dos ventanas negras ("Backend" y "Frontend") mientras lo usan.
- **Respaldos:** la información no se copia sola a otro lugar. Hagan un respaldo con `respaldar_sgejd.bat` **al menos una vez por semana** y copien el archivo de `C:\SGE-JD\respaldos` a un USB o a la nube. Si el disco de la PC falla sin respaldo, se pierde todo.
- **Búsqueda por RUC o DNI:** el servicio gratuito que usamos no deja hacer muchas búsquedas seguidas. Si aparece "servicio ocupado", esperen unos segundos y vuelvan a intentar, o escriban los datos a mano.
- **Tipo de cambio:** se carga solo el del día de SUNAT; si no hay internet, hay que escribirlo.
- **Contraseñas:** las de `lisandro` y `lucy` las puso el equipo de desarrollo. **Al recibir el sistema, cada uno debe cambiar la suya** (nosotros les mostramos cómo). Después de 5 intentos fallidos, la cuenta espera unos minutos antes de dejar intentar de nuevo.
- **Datos de prueba:** la base tiene clientes, productos y ventas marcados "[Prueba]" que hicimos para probar. Antes de empezar se borran con `base_limpia.bat` (hace un respaldo antes de borrar).
- **El stock inicial** hay que ponerlo una vez con un conteo físico del almacén.

## 4. Decisiones que necesitamos de ustedes

1. **Topes de descuento:** hoy Lisandro puede dar hasta 30 % y Lucy hasta 10 % por línea. ¿Están bien?
2. **Costos y márgenes:** hoy Lucy ve el costo y el margen de cada producto. ¿Debe verlos?
3. **Mantenimientos:** hoy solo se programan para equipos de climatización y refrigeración. ¿Ventilación forzada también los necesita?
4. **Teléfono y correo de la empresa** que deben salir impresos en las facturas y proformas.
5. **Facturación electrónica real:** ¿con qué proveedor (OSE) quieren trabajar?
6. **Anular una venta que ya tiene pagos:** hoy el sistema la anula, pero no avisa que hubo pagos ni registra la devolución del dinero. ¿Cómo lo manejan hoy en la oficina?

---

*Equipo: Yoel Wagner Chambi Sirena, Wilson Ronald Apaza Mamani y Aron Rodrigo Magaño Quispe. Detalle técnico: sección 5 de la documentación (`/docs`).*
