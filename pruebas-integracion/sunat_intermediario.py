"""Intermediario local SOLO para la prueba de integración: recibe en http://127.0.0.1:9081/billService y reenvía
cada pedido, sin cambiarlo, a la BETA de SUNAT (https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService).
Su dirección no contiene "beta", así que SGE-JD registra esos envíos como PRODUCCION: es la única forma de recorrer el
camino del correo tributario sin tocar la SUNAT de producción. Todo lo que pasa por aquí sigue siendo de la beta (sin
validez) y la base de datos donde queda es un contenedor temporal que se borra al terminar.
"""
import http.server
import urllib.request
import urllib.error

DESTINO = 'https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService'


class Reenviar(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        cuerpo = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        cabeceras = {k: v for k, v in self.headers.items() if k.lower() in ('content-type', 'soapaction')}
        pedido = urllib.request.Request(DESTINO, data=cuerpo, headers=cabeceras, method='POST')
        try:
            with urllib.request.urlopen(pedido, timeout=60) as r:
                estado, datos, tipo = r.status, r.read(), r.headers.get('Content-Type', 'text/xml')
        except urllib.error.HTTPError as e:
            estado, datos, tipo = e.code, e.read(), e.headers.get('Content-Type', 'text/xml')
        self.send_response(estado)
        self.send_header('Content-Type', tipo)
        self.send_header('Content-Length', str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)

    def log_message(self, formato, *args):
        print('[intermediario] ' + formato % args, flush=True)


if __name__ == '__main__':
    print('Intermediario a la beta de SUNAT en http://127.0.0.1:9081/billService', flush=True)
    http.server.ThreadingHTTPServer(('127.0.0.1', 9081), Reenviar).serve_forever()
