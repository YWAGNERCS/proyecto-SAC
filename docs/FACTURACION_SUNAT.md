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
| 5 | Dar la **cuenta de detracciones** del Banco de la Nación | Va obligatoriamente en las facturas con detracción (servicios desde S/ 700) | Sin ella SUNAT rechaza esas facturas (código 3034). SGE-JD guarda la venta **por emitir**, sin comprobante ni número (no corre el plazo de 3 días), y la emite sola cuando se configure |

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
| Por emitir | Venta con detracción y la cuenta de detracciones todavía sin configurar: no tiene comprobante ni número | Configurar `CUENTA_DETRACCIONES` en `.env.prod` y reiniciar el backend: el comprobante se emite solo en unos minutos |
| Fuera de plazo: no es válido | Pasaron 3 días desde la emisión sin que SUNAT lo registrara: lo que llega tarde no tiene validez | Pulsar **Emitir comprobante de reemplazo** (número nuevo, fecha de hoy, misma venta). La campana avisa antes de que venza |

La nota de crédito de una venta anulada sale cuando SUNAT aceptó el comprobante original. Si el
original fue rechazado, no se crea nota: no había nada válido que anular.

## 5. Entrega del comprobante por correo al cliente

Cuando SUNAT acepta una factura o boleta (también con observaciones), SGE-JD se la envía al cliente por correo,
solo, con dos archivos adjuntos: el **PDF** (representación impresa) y el **XML firmado** (el comprobante con validez
tributaria, byte por byte el mismo que se envió a SUNAT y que está guardado en Oracle). Se nombran como SUNAT:
`20521632268-01-F001-125.pdf` y `.xml`. La venta no espera al correo: sale en segundo plano.

**Nunca se envía** un comprobante pendiente, rechazado, simulado, reemplazado o aceptado por la **beta** de SUNAT
(no tienen validez tributaria). Tampoco si el cliente no tiene correo registrado, o es inválido.

**Ambiente de emisión, fijo en cada comprobante.** Al aceptarlo SUNAT, SGE-JD guarda en el comprobante en qué ambiente
se aceptó (`AMBIENTE_EMISION`: PRODUCCION, BETA o SIMULADO) y ya no cambia: lo impide además el disparador
`TRG_COMPROBANTE_AMBIENTE` en Oracle, también desde SQL. El correo solo sale si ese ambiente es PRODUCCION. Así, si el
servidor se pasa de beta a producción, lo que aceptó la beta sigue sin validez y nunca se le envía al cliente. Los
aceptados antes del 08/11/2026 quedaron SIMULADO (sin XML) o NO_REGISTRADO (con XML, sin saber a qué ambiente fueron):
estos últimos tampoco se envían; si alguno fuera de producción, se verifica en SUNAT (Consulta de validez del CPE).
Cada envío a SUNAT guarda también su ambiente (`ENVIOS_SUNAT.AMBIENTE`).

### Configurar el servidor de correo

En `bomerp-backend/.env.prod` (o en `application-local.properties` en desarrollo, que no se sube a git):

```properties
MAIL_USERNAME=<cuenta que envía, p. ej. facturacion@...>
MAIL_PASSWORD=<en Gmail: una "contraseña de aplicación", no la clave normal>
# Opcionales: contacto oficial que se muestra al pie del correo (vacíos = no se muestran; no se inventan)
EMPRESA_TELEFONO=
EMPRESA_CORREO=
EMPRESA_WEB=
```

El servidor (`spring.mail.host`, Gmail por defecto, puerto 587 con STARTTLS) está en `application.properties`. Los
reintentos se ajustan con `app.correo.max-intentos` (5), `app.correo.revision-ms` (cada 60 s) y
`app.correo.minutos-en-proceso` (10). Después, reiniciar el backend.

### Qué ve Lisandro en Ventas

Cada venta muestra **Entrega del comprobante**: el estado de SUNAT y, aparte, el del correo, porque no son lo mismo.

| Estado del correo | Qué pasó | Qué hacer |
|---|---|---|
| Entregado al servidor de correo | El servidor de correo (SMTP) aceptó el mensaje. **No garantiza** que el cliente lo haya leído (puede ir a spam o rebotar después) | Nada |
| Por enviar / Enviando | En cola o saliendo | Esperar unos segundos |
| No se pudo enviar | El servidor de correo no lo aceptó (caído, clave SMTP mal, dirección rechazada) o no se pudo armar el PDF | Si dice "Se vuelve a intentar solo el …", nada: se reintenta a los 2, 10, 30 y 120 minutos (5 intentos). Si no, corregir el dato (p. ej. el correo del cliente) y pulsar **Reenviar por correo** |
| Envío por confirmar | El backend se apagó en pleno envío y no se sabe si el servidor de correo llegó a aceptarlo | No se reenvía solo, para no mandarlo dos veces. Preguntar al cliente y, si no le llegó, **Reenviar por correo** |
| No se envía | El comprobante no tiene validez (pendiente, rechazado, beta…) o el cliente no tiene correo | El motivo aparece debajo; para el correo, agregarlo en Clientes y pulsar **Enviar por correo** |

Botones: **PDF**, **XML**, **CDR** (constancia de SUNAT), **Historial de intentos** (cada intento con fecha, resultado,
mensaje del servidor y los archivos adjuntos con su huella SHA-256) y **Reenviar por correo**. El reenvío va siempre al
correo registrado del cliente (no se puede escribir otra dirección); si ya se había entregado, pide confirmarlo. No
crea otra venta ni otro comprobante, no usa otro número y no toca el stock ni la cobranza. Queda en el historial de
cambios ("Envió por correo al cliente el comprobante #…").

### Cómo funciona por dentro (para soporte)

- Cada solicitud queda en `ENVIOS_CORREO` (estado, destinatario, intentos, último error, Message-ID, próximo
  intento) y cada intento en `INTENTOS_CORREO` (solo se agregan). Migración: `migracion_envio_correo.sql`.
- Un solo envío automático por comprobante (índice único `UX_ENVIO_CORREO_AUTOMATICO`): si SUNAT avisa dos veces, sale
  un correo. Para enviar, el trabajador bloquea la fila del comprobante y toma el envío con un `UPDATE` condicional:
  nunca lo envían dos procesos a la vez. Ninguna transacción de Oracle queda abierta mientras se habla con el servidor
  de correo.
- La revisión de cada minuto retoma lo pendiente y lo fallido cuya hora llegó (también al reiniciar el backend).
- **Conciliación (evento perdido):** si SUNAT acepta y el backend se cae antes de crear la solicitud de envío, la misma
  revisión busca comprobantes aceptados en PRODUCCION en los últimos 3 días (`app.correo.conciliar-dias`), de un
  cliente con correo y sin envío automático, y lo crea (con 2 minutos de margen para el camino normal; el índice único
  impide duplicarlo). Se vuelven a comprobar el ambiente, el destinatario y el XML.
- **El XML se comprueba campo por campo** con un analizador XML seguro (sin DOCTYPE ni entidades externas), sobre una
  copia de los bytes, respetando su codificación (ISO-8859-1): que sea un Invoice UBL firmado y que el número, el tipo
  (01/03), el RUC del emisor, la fecha, la moneda y el importe total sean los del comprobante. Si algo no coincide, no se
  envía.
- **Política ante la duda:** el Message-ID se guarda antes de enviar. Si el proceso se cae entre que el servidor de
  correo acepta y que Oracle lo registra, el envío queda `EN_PROCESO`; pasados 10 minutos pasa a `INCIERTO` y no se
  reenvía solo. SMTP no permite garantizar "exactamente una vez": SGE-JD prefiere no duplicar y deja la decisión a
  Lisandro.
- API: `GET /api/v1/admin/facturacion/{id}/correo` (estado e historial), `POST /{id}/enviar?confirmar=true|false`
  (reenvío; 409 si no tiene validez, si hay un envío en curso o si ya se envió sin confirmar; 400 sin correo válido) y
  `GET /{id}/pdf`.

### Probarlo sin enviar correos reales

Las pruebas automáticas usan un servidor SMTP de prueba dentro de la misma PC o un notificador simulado
(`NotificadorCorreoRealTest`, `EntregaComprobanteIntegracionTest`): ningún correo sale. En desarrollo, lo que acepta la
beta de SUNAT nunca se envía. Para ver el correo de verdad antes de producción, configurar `MAIL_USERNAME` con una
cuenta propia y registrar una venta de prueba con un cliente cuyo correo sea el suyo, ya con SUNAT de producción.

## 6. Cuidados

- **Certificado y claves:** viven solo en `.env.prod` y en `certificados/`, que nunca van a git ni a la
  imagen Docker. Incluir ambos en el respaldo del servidor, protegido.
- **El certificado vence.** Antes de esa fecha hay que pedir uno nuevo, registrarlo en SOL y reemplazar
  el archivo. Con un certificado vencido, SUNAT rechaza los comprobantes.
- **Si alguien deja la empresa**, cambiar la clave del usuario SOL secundario y actualizar `SUNAT_CLAVE`.
- **Si SUNAT rechaza el usuario o la clave SOL**, SGE-JD detiene todos los envíos para no bloquear la cuenta y la
  campana muestra "Envíos a SUNAT detenidos". Corregir `SUNAT_USUARIO` y `SUNAT_CLAVE` en `.env.prod` y reiniciar el
  backend: los comprobantes pendientes salen solos.
- **Si un cliente cambia de empresa a persona natural** (o al revés) después de una venta cuyo comprobante SUNAT no
  recibió, ese comprobante no se reenvía: se pulsa **Emitir comprobante de reemplazo** y sale del tipo correcto.

## 7. Lo que SGE-JD todavía no hace

- **Comunicación de baja:** una venta se anula con nota de crédito, que es lo habitual. La baja de un
  comprobante por error material no está implementada.
- **Consulta del CDR de un envío cuya respuesta se perdió:** si SUNAT recibió el comprobante pero la
  respuesta no llegó, el reintento obtiene el código 1033 ("ya registrado") y queda **por confirmar**, sin
  CDR: hay que verificarlo a mano en SUNAT. La consulta automática (getStatusCdr) solo existe en producción.
- **Guías de remisión electrónicas**, retenciones y percepciones emitidas por JD.
- **Resumen diario de boletas:** no hace falta; SUNAT acepta las boletas una por una (probado en beta).
