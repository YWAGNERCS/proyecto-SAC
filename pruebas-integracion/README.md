# Prueba de integración del correo del comprobante

Herramienta para repetir, en un entorno controlado, la prueba real descrita en
[docs/PRUEBA_INTEGRACION_CORREO.md](../docs/PRUEBA_INTEGRACION_CORREO.md): el backend de verdad, con Oracle, la firma
del XML, la beta de SUNAT, el PDF y la entrega SMTP por la red. **Ningún correo sale de la PC.**

| Archivo | Qué hace |
|---|---|
| `smtp_local.py` | Servidor SMTP de prueba en `127.0.0.1:2526`: guarda cada mensaje en `buzon/NNN.eml` y nunca lo reenvía. Con un archivo `RECHAZAR` en esta carpeta responde 451 (error temporal); con `LENTO` guarda el mensaje y tarda 120 s en confirmarlo (para probar una caída en pleno envío). |

> El intermediario hacia la beta que se usó el 08/11/2026 para marcar envíos como producción se **quitó**: desde
> `SUNAT_AMBIENTE` el backend solo acepta las direcciones oficiales de SUNAT y no arranca con un intermediario. Con la
> beta, lo aceptado queda BETA y no se envía por correo (eso es lo que se comprueba aquí). El envío del correo con
> comprobantes de producción lo cubren las pruebas automáticas (`EntregaComprobanteIntegracionTest`,
> `NotificadorCorreoRealTest`).

## Pasos

1. Contenedor Oracle temporal en el puerto 1522 (con el de desarrollo apagado) y un respaldo importado; cambiar el
   correo de todos los clientes de esa copia a direcciones `@sgejd.test`.
2. Aplicar las migraciones en orden, dos veces cada una, y comparar el esquema con `scripts/prod/esquema_jdrefrigeracion.sql`.
3. `python -u smtp_local.py`.
4. Backend contra esa base, con `--spring.jpa.hibernate.ddl-auto=validate` (el perfil de desarrollo usa `update`),
   `--spring.mail.host=127.0.0.1 --spring.mail.port=2526 --spring.mail.username=facturacion@sgejd.test`
   `--spring.mail.properties.mail.smtp.auth=false --spring.mail.properties.mail.smtp.starttls.enable=false`
   y `--app.sunat.ambiente=BETA --app.sunat.url=https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService`.
5. Registrar ventas y revisar: comprobante aceptado por la beta con ambiente BETA, sin correo; reenvío manual 409.
6. Borrar el contenedor temporal.
