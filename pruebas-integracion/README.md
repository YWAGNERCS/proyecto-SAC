# Prueba de integración del correo del comprobante

Herramientas para repetir, en un entorno controlado, la prueba real de punta a punta descrita en
[docs/PRUEBA_INTEGRACION_CORREO.md](../docs/PRUEBA_INTEGRACION_CORREO.md): el backend de verdad, con Oracle, la firma
del XML, la beta de SUNAT, el PDF y la entrega SMTP por la red. **Ningún correo sale de la PC.**

| Archivo | Qué hace |
|---|---|
| `smtp_local.py` | Servidor SMTP de prueba en `127.0.0.1:2526`: guarda cada mensaje en `buzon/NNN.eml` y nunca lo reenvía. Con un archivo `RECHAZAR` en esta carpeta responde 451 (error temporal); con `LENTO` guarda el mensaje y tarda 120 s en confirmarlo (para probar una caída en pleno envío). |
| `sunat_intermediario.py` | Recibe en `http://127.0.0.1:9081/billService` y reenvía cada pedido, sin cambiarlo, a la **beta** de SUNAT. Como su dirección no dice "beta", SGE-JD registra esos envíos como PRODUCCION: es la única forma de recorrer el camino del correo tributario sin tocar la SUNAT de producción. |

> **Solo contra una base descartable.** Lo que pasa por el intermediario queda marcado PRODUCCION aunque lo aceptó la
> beta (sin validez tributaria). Usarlo únicamente con un contenedor Oracle temporal que se borre al terminar, nunca con
> la base de desarrollo ni con la de producción. Antes de empezar, cambiar el correo de todos los clientes de esa copia a
> direcciones `@sgejd.test`.

## Pasos

1. Contenedor Oracle temporal en el puerto 1522 (con el de desarrollo apagado) y un respaldo importado.
2. Aplicar las migraciones en orden, dos veces cada una, y comparar el esquema con `scripts/prod/esquema_jdrefrigeracion.sql`.
3. `python -u smtp_local.py` y, para el camino del correo, `python -u sunat_intermediario.py`.
4. Backend contra esa base, con `--spring.jpa.hibernate.ddl-auto=validate` (el perfil de desarrollo usa `update`),
   `--spring.mail.host=127.0.0.1 --spring.mail.port=2526 --spring.mail.username=facturacion@sgejd.test`
   `--spring.mail.properties.mail.smtp.auth=false --spring.mail.properties.mail.smtp.starttls.enable=false`
   y `--app.sunat.url=` la beta (fase A) o `http://127.0.0.1:9081/billService` (fase B).
5. Registrar ventas, revisar `buzon/` (adjuntos, Message-ID, huella del XML contra Oracle) y la pantalla de Ventas.
6. Borrar el contenedor temporal.
