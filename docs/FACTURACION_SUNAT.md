# Facturación electrónica directa a SUNAT: paso a producción

SGE-JD emite facturas, boletas y notas de crédito **directamente ante SUNAT** con las credenciales de
JD Refrigeración (SUNAT lo llama *SEE - Del Contribuyente*). No hay proveedor intermedio (OSE) ni
mensualidad. Hoy está probado en el **ambiente beta** de SUNAT (pruebas, sin efecto tributario). Esta
guía explica qué falta para emitir comprobantes reales.

Cómo funciona por dentro: ver *Facturación electrónica: envío directo a SUNAT* en
[README.md](README.md) (documentación técnica).

## 1. Lo que tiene que hacer JD (Lisandro y su contador)

| # | Qué | Para qué | Notas |
|---|---|---|---|
| 1 | **Confirmar con el contador** que JD puede emitir desde un sistema propio (SEE - Del Contribuyente) y desde qué fecha | Es la forma de emisión que usa SGE-JD | Desde 2017 SUNAT ya no exige homologación para este sistema; igual conviene que el contador lo confirme |
| 2 | Crear un **usuario SOL secundario** con permiso solo para comprobantes electrónicos | SGE-JD se identifica ante SUNAT con este usuario | **No usar la Clave SOL principal de Lisandro**: si alguien obtiene el usuario secundario, no puede ver ni cambiar nada más del RUC |
| 3 | Obtener el **certificado digital** y registrarlo en SOL | Firma cada comprobante; sin firma válida SUNAT lo rechaza | SUNAT entrega gratis el *Certificado Digital Tributario* (hasta el 31/12/2027), solicitándolo desde SOL. Se recibe un archivo `.pfx` o `.p12` con su contraseña |
| 4 | Decidir las **series** de SGE-JD | Un mismo número no puede emitirse dos veces | Si JD ya emite con F001 o B001 desde el portal de SUNAT u otro sistema, SGE-JD debe usar otras (por ejemplo F002 y B002). Facturas y sus notas empiezan con F; boletas y sus notas con B; 4 caracteres |
| 5 | Dar la **cuenta de detracciones** del Banco de la Nación | Va obligatoriamente en las facturas con detracción (servicios desde S/ 700) | Sin ella SUNAT rechaza esas facturas (código 3034). SGE-JD no las envía y las deja **pendientes** hasta que se configure |

## 2. Configurar el servidor

En la computadora donde corre SGE-JD en modo producción (`compose-prod.yml`, ver
[PRODUCCION_LOCAL.md](PRODUCCION_LOCAL.md)):

1. Copiar el certificado a `bomerp-backend/certificados/` (por ejemplo `jd-refrigeracion.pfx`).
   Esa carpeta está fuera de git y el contenedor la lee como solo lectura.
2. Completar en `bomerp-backend/.env.prod` (no se versiona; plantilla en `.env.prod.example`):

   ```properties
   FACTURACION_MODO=sunat
   SUNAT_URL=https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService
   SUNAT_USUARIO=<usuario SOL secundario, sin el RUC>
   SUNAT_CLAVE=<su clave>
   SUNAT_CERT_ARCHIVO=jd-refrigeracion.pfx
   SUNAT_CERT_CLAVE=<contraseña del certificado>
   CUENTA_DETRACCIONES=<cuenta del Banco de la Nación>
   SERIE_FACTURA=F001
   SERIE_BOLETA=B001
   SERIE_NOTA_FACTURA=FC01
   SERIE_NOTA_BOLETA=BC01
   ```

3. Reiniciar el backend:

   ```powershell
   docker compose -f compose-prod.yml --env-file .env.prod up -d backend
   ```

   El log debe decir `Facturación: envío directo a SUNAT (https://e-factura.sunat.gob.pe/...)`.

> **Antes de producción, conviene probar en beta en esa misma computadora:** con
> `FACTURACION_MODO=sunat`, la URL de beta (`https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService`),
> `SUNAT_USUARIO=MODDATOS` y `SUNAT_CLAVE=MODDATOS`, y el certificado de JD. Así se comprueba que el
> certificado abre y que la red llega a SUNAT, sin emitir nada real. En producción **no hay "prueba"**:
> cada comprobante enviado es real.

## 3. Primer día

- Registrar una venta real y, en **Ventas**, revisar que su comprobante diga **Aceptado por SUNAT**
  (código 0). Descargar el **CDR**: es la constancia oficial de SUNAT.
- Si sale **Rechazado por SUNAT: no es válido** (código 2000-3999), el mensaje dice qué dato corregir (por
  ejemplo, el RUC del cliente). Ese número ya quedó usado en SUNAT y no se puede volver a enviar: se corrige
  el dato en Clientes y se pulsa **Emitir comprobante de reemplazo**, que emite otro número para la misma
  venta. El rechazado queda en el historial (botón **Envíos**) con su constancia, que hay que conservar.
- Si sale **SUNAT no lo recibió: corrija y reenvíe**, SUNAT no registró ese número: se corrige el dato y
  se pulsa **Reenviar** (sale con el mismo número).
- Los comprobantes emitidos antes (datos de prueba o de antes de activar SUNAT) aparecen como
  **Simulado (sin envío a SUNAT)**: no tienen validez tributaria.

## 4. Qué significa cada estado

| Estado en Ventas | Qué pasó | Qué hacer |
|---|---|---|
| Aceptado por SUNAT | Código 0: válido | Nada. XML y CDR quedan guardados |
| Aceptado con observaciones | Código 4000 o más: válido, con una observación | Revisar la observación para las próximas ventas |
| Rechazado por SUNAT: no es válido | Código 2000-3999 (o 1032): no tiene validez y su número ya quedó usado | Corregir el dato en Clientes y pulsar **Emitir comprobante de reemplazo** (número nuevo, misma venta; no cambian el stock ni el cobro) |
| SUNAT no lo recibió: corrija y reenvíe | Excepción: SUNAT no registró el comprobante | Corregir el dato que indica el mensaje y pulsar **Reenviar** (mismo número) |
| Por confirmar en SUNAT | Código 1033: SUNAT ya tiene ese número de un envío anterior y no dice si lo aceptó | Consultarlo en SUNAT (Consulta de validez del CPE) antes de entregarlo al cliente |
| Pendiente de envío | SUNAT no respondió o falta un dato de JD | Se reintenta solo cada 3 minutos (hasta 20 veces). Si el mensaje indica un dato de JD (cuenta de detracciones), configurarlo y pulsar **Reenviar**. Debajo dice hasta qué día SUNAT lo recibe |
| Fuera de plazo: no es válido | Pasaron 3 días desde la emisión sin que SUNAT lo registrara: lo que llega tarde no tiene validez | Pulsar **Emitir comprobante de reemplazo** (número nuevo, fecha de hoy, misma venta). La campana avisa antes de que venza |

La nota de crédito de una venta anulada sale cuando SUNAT aceptó el comprobante original. Si el
original fue rechazado, no se crea nota: no había nada válido que anular.

## 5. Cuidados

- **Certificado y claves:** viven solo en `.env.prod` y en `certificados/`, que nunca van a git ni a la
  imagen Docker. Incluir ambos en el respaldo del servidor, protegido.
- **El certificado vence.** Antes de esa fecha hay que pedir uno nuevo, registrarlo en SOL y reemplazar
  el archivo. Con un certificado vencido, SUNAT rechaza los comprobantes.
- **Si alguien deja la empresa**, cambiar la clave del usuario SOL secundario y actualizar `SUNAT_CLAVE`.

## 6. Lo que SGE-JD todavía no hace

- **Comunicación de baja:** una venta se anula con nota de crédito, que es lo habitual. La baja de un
  comprobante por error material no está implementada.
- **Consulta del CDR de un envío cuya respuesta se perdió:** si SUNAT recibió el comprobante pero la
  respuesta no llegó, el reintento obtiene el código 1033 ("ya registrado") y queda **por confirmar**, sin
  CDR: hay que verificarlo a mano en SUNAT. La consulta automática (getStatusCdr) solo existe en producción.
- **Guías de remisión electrónicas**, retenciones y percepciones emitidas por JD.
- **Resumen diario de boletas:** no hace falta; SUNAT acepta las boletas una por una (probado en beta).
