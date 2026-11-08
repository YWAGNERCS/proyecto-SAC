# Prueba de integración real: migraciones y correo del comprobante (08/11/2026)

Objetivo: comprobar, fuera de las pruebas automáticas, que las migraciones funcionan sobre bases reales y que la entrega
del comprobante por correo funciona con los componentes de verdad. Entorno controlado y descartable: un contenedor
Oracle temporal (borrado al terminar), el backend real, Keycloak de desarrollo, la **beta** de SUNAT y un servidor SMTP
local que guarda los mensajes sin reenviarlos. Los correos de todos los clientes de esa copia se cambiaron a
`@sgejd.test`. Nada se envió a la SUNAT de producción ni a ningún cliente. Herramientas: `pruebas-integracion/`.

## 1. Migraciones

| Cadena | Punto de partida | Migraciones | Resultado |
|---|---|---|---|
| Con datos | Respaldo del 30/10/2026 con datos de demostración (`sgejd_antes_de_entrega_20261030_2224.dmp`) | Las 9 posteriores, en orden, **dos veces cada una** (proforma_unica, envio_sunat, compra_factura_unica, comprobante_reemplazo, quitar_usuarios, venta_anulacion, costos_precios, envio_correo, ambiente_emision) | 18 ejecuciones sin errores; la segunda vuelta no repite nada. Esquema igual al de instalación nueva salvo `USUARIOS` (ver hallazgos). Sin objetos inválidos; disparador válido; 12 compras con subtotal + IGV = total; 39 comprobantes con receptor y ambiente SIMULADO; anuladas con motivo |
| Producción | Respaldo real de producción tomado antes del correo (`sgejd_prod_antes_envio_correo_20261108_0934.dmp`) | envio_correo y ambiente_emision, dos veces cada una | Sin errores; esquema **idéntico** al de instalación nueva (`esquema_jdrefrigeracion.sql`); disparador válido |

El backend real arrancó contra la copia migrada de producción con `ddl-auto=validate` (como en producción), y la prueba
negativa confirmó que la validación está activa: al renombrar una columna, se negó a arrancar ("missing column").

## 2. Correo del comprobante, de punta a punta

| # | Escenario | Resultado real |
|---|---|---|
| A | Venta con SUNAT **beta** directa | Beta aceptó F001-20 (código 0, XML y CDR guardados); ambiente **BETA** registrado; **no** se creó correo; reenvío manual → 409; cambiar el ambiente por SQL → ORA-20031 |
| B1 | Venta con aceptación en "producción" (vía intermediario a la beta) | F001-21 aceptado, ambiente PRODUCCION, correo ENVIADO al 1.er intento. En el buzón: asunto con el número, HTML con el encabezado y el logo incrustado, PDF (`20521632268-01-F001-21.pdf`) y XML en base64; **el XML adjunto es idéntico al de Oracle** (9 490 bytes, SHA-256 `d96347f0…` en ambos); Message-ID igual al guardado |
| B2 | El servidor SMTP rechaza (451) | FALLIDO con reintento a los 2 min; al volver el servidor, el planificador lo reintentó solo y quedó ENVIADO (2 intentos en el historial) |
| B3 | **Caída real del backend** en pleno envío (el servidor ya tenía el mensaje sin confirmarlo) | Quedó EN_PROCESO; al reiniciar pasó a **INCIERTO** con su Message-ID (el mismo del mensaje del buzón) y **no se reenvió solo** |
| B4 | Reenvío manual del INCIERTO | Sin confirmar → 409; confirmando → ENVIADO, "reenvío de lisandro" |
| B5 | Automático esperando reintento + reenvío manual | El reintento quedó cancelado en la misma operación; pasada su hora, **no salió**: un solo correo |
| B6 | Evento de aceptación perdido (solicitud borrada a propósito) | Pasado el margen de 2 min, la **conciliación** del backend real lo encontró, creó el envío automático y salió |

En Ventas, "Entrega del comprobante" mostró para cada caso el ambiente, el estado del correo, el destinatario, los
intentos, los reenvíos y el detalle.

## 3. Hallazgos

- **`USUARIOS` con filas no se borra.** En el respaldo del 30/10 la tabla tenía 2 filas y `migracion_quitar_usuarios.sql`
  no la borró (su protección). No impide arrancar; si aparece en una base real, revisarla y borrarla a mano.
- **Error SMTP con texto técnico** (corregido en esta prueba): lo guardado decía "Failed messages: jakarta.mail.Send…";
  ahora se guarda solo la respuesta del servidor ("451 4.3.0 …"). Prueba en `NotificadorCorreoRealTest`.
- **ORA-00600 al renombrar `AMBIENTE_EMISION`** (error interno de Oracle 23 Free, por el disparador que la vigila): no
  afecta al uso; no renombrar esa columna sin quitar antes el disparador.
- El perfil de desarrollo usa `ddl-auto=update`; para validar un esquema hay que forzar `validate`.

## 4. Lo que no cubre

- **La SUNAT de producción** (necesita el usuario SOL y el certificado de JD) ni el **servidor de correo real** (Gmail u
  otro, con su autenticación y TLS). Primer día en producción: registrar una venta propia, con el correo de Lisandro
  como cliente, y revisar que llegue (también la carpeta de spam).
- La caída exacta entre la respuesta de SUNAT y la creación del envío (milisegundos): se simuló borrando la solicitud;
  también la cubre `EntregaComprobanteIntegracionTest`.
