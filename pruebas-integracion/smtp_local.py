"""Servidor SMTP de prueba: escucha en 127.0.0.1:2526 y guarda cada mensaje en buzon/NNN.eml. Nunca reenvía nada.
Modo según archivos de control en esta carpeta:
  RECHAZAR  -> responde 451 (error temporal) al destinatario
  LENTO     -> guarda el mensaje pero tarda 120 s en confirmar (para simular una caída en pleno envío)
"""
import os
import socketserver
import time

AQUI = os.path.dirname(os.path.abspath(__file__))
BUZON = os.path.join(AQUI, 'buzon')
os.makedirs(BUZON, exist_ok=True)


class Atender(socketserver.StreamRequestHandler):
    def responder(self, texto):
        self.wfile.write((texto + '\r\n').encode('ascii'))
        self.wfile.flush()

    def handle(self):
        self.responder('220 smtp-local-sgejd')
        while True:
            linea = self.rfile.readline()
            if not linea:
                return
            cmd = linea.decode('latin-1').strip().upper()
            if cmd.startswith('EHLO') or cmd.startswith('HELO'):
                self.responder('250 smtp-local-sgejd')
            elif cmd.startswith('MAIL FROM') or cmd.startswith('RSET') or cmd.startswith('NOOP'):
                self.responder('250 OK')
            elif cmd.startswith('RCPT TO'):
                if os.path.exists(os.path.join(AQUI, 'RECHAZAR')):
                    self.responder('451 4.3.0 Servidor ocupado (prueba)')
                else:
                    self.responder('250 OK')
            elif cmd.startswith('DATA'):
                self.responder('354 Fin con <CRLF>.<CRLF>')
                datos = bytearray()
                while True:
                    l = self.rfile.readline()
                    if not l or l in (b'.\r\n', b'.\n'):
                        break
                    datos += l[1:] if l.startswith(b'..') else l
                n = len([f for f in os.listdir(BUZON) if f.endswith('.eml')]) + 1
                with open(os.path.join(BUZON, '%03d.eml' % n), 'wb') as f:
                    f.write(datos)
                if os.path.exists(os.path.join(AQUI, 'LENTO')):
                    time.sleep(120)
                self.responder('250 OK en cola %03d' % n)
            elif cmd.startswith('QUIT'):
                self.responder('221 Adios')
                return
            else:
                self.responder('502 No implementado')


class Servidor(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == '__main__':
    print('SMTP de prueba en 127.0.0.1:2526, buzón:', BUZON, flush=True)
    Servidor(('127.0.0.1', 2526), Atender).serve_forever()
