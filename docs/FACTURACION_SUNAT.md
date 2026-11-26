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
   SUNAT_AMBIENTE=PRODUCCION
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

   El log debe decir `Facturación: envío directo a SUNAT, ambiente PRODUCCION (https://e-factura.sunat.gob.pe/...)`.

   **`SUNAT_AMBIENTE` es obligatorio** con `FACTURACION_MODO=sunat` (PRODUCCION o BETA, sin valor por defecto) y
   `SUNAT_URL` tiene que ser **exactamente la dirección oficial** de ese ambiente:

   | SUNAT_AMBIENTE | SUNAT_URL |
   |---|---|
   | `PRODUCCION` | `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService` |
   | `BETA` | `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService` |

   Si falta el ambiente, si no corresponde a la dirección o si la dirección no es la oficial (otro servidor, un
   intermediario o proxy, `http`, otro puerto, parámetros), **el backend no arranca** y el log dice exactamente qué
   corregir. Antes el ambiente se deducía buscando "beta" en la dirección, y cualquier otra dirección quedaba como
   producción: comprobantes de pruebas habrían salido por correo a los clientes como válidos. Ese ambiente es el que
   queda fijo en cada comprobante aceptado (sección 5).

> **Antes de producción, conviene probar en beta en esa misma computadora:** con
> `FACTURACION_MODO=sunat`, `SUNAT_AMBIENTE=BETA`, la URL de beta (`https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService`),
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
  un correo. Y ninguno automático si el comprobante ya tiene cualquier envío (p. ej. Lisandro lo mandó a mano): ni el
  evento tardío ni la conciliación lo duplican.
- **Reenvío manual y reintento automático:** al pedir un reenvío, en la misma transacción se cancela el reintento
  automático que estuviera programado ("Reintento cancelado: … pidió un reenvío manual"). Y un reintento automático nunca
  sale si otro envío del mismo comprobante ya se entregó o quedó por confirmar. Para enviar, el trabajador bloquea la fila del comprobante y toma el envío con un `UPDATE` condicional:
  nunca lo envían dos procesos a la vez. Ninguna transacción de Oracle queda abierta mientras se habla con el servidor
  de correo.
- La revisión de cada minuto retoma lo pendiente y lo fallido cuya hora llegó (también al reiniciar el backend).
- **Conciliación (evento perdido):** si SUNAT acepta y el backend se cae antes de crear la solicitud de envío, la misma
  revisión busca comprobantes aceptados en PRODUCCION en los últimos 3 días (`app.correo.conciliar-dias`), de un
  cliente con correo y sin ningún envío por correo (ni automático ni manual), y lo crea (con 2 minutos de margen para el camino normal; el índice único
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

**Prueba de integración real del 08/11/2026** (migraciones sobre respaldos reales y correo de punta a punta con el
backend de verdad, la beta de SUNAT y un servidor SMTP local): resultados en
[PRUEBA_INTEGRACION_CORREO.md](PRUEBA_INTEGRACION_CORREO.md); herramientas en `pruebas-integracion/`.

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


## 8. Factura con detraccion y alcance de la entrega (26/11/2026)

La reunion del 23/11/2026 excluye de Clientes la opcion de retencion del 3 % y su
verificacion automatica. No se integra el padron ni se calcula retencion en ventas
nuevas. La API ignora esa marca en fichas nuevas y conserva el valor historico al
editar los datos de contacto. Los tipos, importes, PDF y cobros historicos no se borran.
Cobranza ofrece retencion solo para documentos que ya la tienen registrada.

### Antes de desplegar

Aplicar primero `scripts/prod/migracion_reglas_tributarias.sql` y despues
`scripts/prod/migracion_factura_detraccion_credito.sql` como propietario
JDREFRIGERACION. Ambas son aditivas y repetibles. Las nuevas bases reciben todas
las columnas desde `scripts/prod/esquema_jdrefrigeracion.sql`. No reescriben importes
ni asignan una cuenta bancaria actual a comprobantes historicos.

### Configuracion del servicio y de la empresa

En Catalogo, cada servicio indica su clasificacion: mantenimiento y reparacion de
bienes muebles (020, 12 %), otros servicios empresariales (022, 12 %), demas servicios gravados (037, 12 %), construccion
(030, 4 %), arrendamiento (019, 10 %) o no sujeto. Una factura sobre el umbral con un
servicio sin clasificar pide completar el catalogo. La operacion debe tener una sola
clasificacion; no se mezclan operaciones independientes de distinta clasificacion.
Las boletas de consumidor final no reciben la detraccion automatica.

Las cuentas oficiales se configuran en el servidor, en `app.empresa.*` / variables
de entorno. No son campos editables de Clientes. La factura modelo ya se recibio;
su cuenta de detracciones se configuro localmente. Las demas cuentas y los datos
definitivos de produccion siguen pendientes de confirmar.

### PDF, XML y cobranza

- La factura muestra contado o credito, plazo, fecha de vencimiento y el importe de
  la cuota unica. El neto del credito descuenta la detraccion del total.
- El bloque SPOT muestra tipo de operacion 1001, codigo del servicio, porcentaje,
  importe en soles, cuenta del Banco de la Nacion y medio 003 (transferencia de fondos),
  segun la factura modelo E001-1901 recibida. `app.tributos.medio-pago-detraccion`
  permite configurar 001 (deposito) o 003 (transferencia) desde el servidor.
  Cada comprobante conserva el medio con el que se emitio; los historicos sin dato
  conservan la presentacion anterior 001. La clasificacion 022 se elige en Catalogo;
  no se asigna automaticamente a todos los servicios por aparecer en el ejemplo.
- El codigo y los importes se guardan en la venta. La cuenta y el medio se guardan
  en el comprobante al emitirlo; cambios posteriores de configuracion no los alteran.
- Para nuevas ventas, JD sigue el redondeo a soles enteros de su factura modelo:
  S/ 849.60 x 12 % = S/ 101.952, deposito S/ 102.00, neto a JD S/ 747.60.
  Los importes se presentan con dos decimales. No se recalculan ventas historicas.
  En dolares primero se calcula y redondea el deposito en PEN; su equivalente en
  USD se determina con el cambio guardado y se redondea a centavos para el saldo.
  El XML declara SPOT en PEN; la cuota y el saldo de cobranza siguen en USD.
- Cobranza separa el pago directo del cliente de la constancia de detraccion. El
  saldo completo no queda pagado solo por acreditar una de estas dos partes.
- No se inventa un tipo de cambio. Se usa el guardado en la operacion. La equivalencia
  mostrada corresponde a ese cambio; el tratamiento de diferencias cambiarias de
  constancias reales se debe contrastar con Lucy.
- Los documentos de prueba se identifican expresamente como tales. Las pruebas
  automaticas generan un certificado temporal y no envian a SUNAT ni a correos.

Fuentes: [Anexo de datos de factura al credito](https://www.sunat.gob.pe/legislacion/superin/2020/anexo1-193-2020.pdf),
[ejemplo oficial de factura con detraccion](https://cpe.sunat.gob.pe/sites/default/files/inline-files/factura%20con%20detraccion_0.pdf),
[preguntas frecuentes SPOT y redondeo](https://orientacion.sunat.gob.pe/preguntas-frecuentes-detracciones).

La factura modelo E001-1901 del 25/11/2026 ya fue recibida y se usa para las pruebas
de codigo 022, medio 003, redondeo y saldo. La cuenta recibida se configura localmente
en el archivo ignorado, nunca en la plantilla del repositorio. Las otras cuentas
siguen pendientes. No se importa esa factura como venta ni se replica su serie E001.
La conformidad de Lucy y la aceptacion real de SUNAT siguen pendientes.

## 9. Nota de crédito descargable (26/11/2026)

En **Comercial → Ventas**, abra el detalle de una venta anulada. En la línea de su
nota de crédito aparece **PDF nota de crédito**, junto a XML, CDR e historial de
envíos. La descarga usa la sesión del usuario y funciona para ADMIN y ASISTENTE.
El PDF identifica serie, número, fecha, motivo 01 (anulación total), cliente del
comprobante original, comprobante referenciado, líneas, base, IGV y total.

Anular una venta restaura el stock controlado, cancela las visitas pendientes y
excluye la venta de cobranza. Si ya se pagó, el dinero queda pendiente de devolución
en **Cobranza → Devoluciones**; descargar la nota no registra una devolución.
La emisión es idempotente: una nota por comprobante. Un comprobante rechazado no
se anula con nota; uno pendiente conserva la nota pendiente hasta aceptar el original.
El rechazo de una cotización por el cliente tampoco genera una nota de crédito.

La nota guarda el ambiente al aceptarla. Pendientes, rechazadas, BETA, SIMULADO y
notas antiguas sin ambiente acreditado muestran un aviso de falta de validez
tributaria acreditada. No se deduce el ambiente de la configuración actual.
Su aceptación real se comprueba con XML/CDR e historial, no solo con la descarga.
Esta entrega cubre anulación total, no notas parciales por descuentos o devoluciones.

Antes de iniciar esta versión, aplicar también
`scripts/prod/migracion_reunion_notas_recordatorios.sql`. Es repetible, crea la tabla
de recordatorios y añade el ambiente de las notas sin reescribir documentos históricos.

Referencia: [SUNAT: nota de crédito electrónica](https://cpe.sunat.gob.pe/tipos_de_comprobantes/nota_de_credito).
