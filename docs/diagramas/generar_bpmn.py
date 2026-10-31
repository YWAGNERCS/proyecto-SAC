"""
Genera los diagramas BPMN de SGE-JD (AS-IS y TO-BE) en dos formatos con el mismo trazado:
  - <nombre>.bpmn : BPMN 2.0 estándar, se abre y edita en Camunda Modeler o https://demo.bpmn.io
  - <nombre>.svg  : imagen para la documentación (sitio MkDocs)

Uso:  python docs/diagramas/generar_bpmn.py      (desde bomerp-backend)
Los procesos se describen abajo, en PROCESOS: carriles, nodos (columna y carril) y flujos.
"""
from pathlib import Path
from xml.sax.saxutils import escape

AQUI = Path(__file__).resolve().parent

# ── Medidas del dibujo ─────────────────────────────────────────
COL = 172          # ancho de cada columna
X0 = 150           # donde empieza la primera columna (antes: nombre del pool y de los carriles)
CARRIL = 165       # alto de cada carril
TAREA_W, TAREA_H = 144, 100
EVENTO = 36
COMPUERTA = 50

# ── Procesos ───────────────────────────────────────────────────
# Nodo: (id, tipo, texto, carril, columna). Tipos: inicio, fin, tarea, manual, sistema, compuerta.
# Flujo: (origen, destino, etiqueta)
PROCESOS = [
    {
        "archivo": "asis_venta_servicio",
        "titulo": "AS-IS · Venta, instalación y cobro (BORRADOR: supuestos [S1]–[S6] por validar con Lisandro)",
        "carriles": [("cli", "Cliente"), ("ofi", "Oficina (Lucy)"), ("due", "Dueño (Lisandro)"), ("tec", "Técnico")],
        "nodos": [
            ("i", "inicio", "Necesita un equipo o servicio", "cli", 0),
            ("t1", "manual", "Pide una cotización", "cli", 1),
            ("t2", "manual", "Arma la proforma en Word o Excel [S1]", "ofi", 2),
            ("t3", "manual", "Revisa precios y descuento [S2]", "due", 3),
            ("t4", "manual", "Envía la proforma por correo o WhatsApp", "ofi", 4),
            ("t5", "manual", "Revisa la proforma", "cli", 5),
            ("g1", "compuerta", "¿Acepta?", "cli", 6),
            ("f0", "fin", "Sin venta", "cli", 7),
            ("t6", "manual", "Consulta el stock en el almacén [S3]", "ofi", 7),
            ("t7", "manual", "Emite la factura en el portal de SUNAT [S4]", "ofi", 8),
            ("g2", "compuerta", "¿Requiere instalación?", "ofi", 9),
            ("t8", "manual", "Asigna técnico y fecha de memoria o en una agenda [S5]", "due", 10),
            ("t9", "manual", "Instala el equipo", "tec", 11),
            ("g3", "compuerta", "", "ofi", 12),
            ("g4", "compuerta", "¿Es a crédito?", "ofi", 13),
            ("t10", "manual", "Anota la deuda en un cuaderno o Excel [S6]", "ofi", 14),
            ("t11", "manual", "Paga", "cli", 15),
            ("t12", "manual", "Tacha la deuda como pagada [S6]", "ofi", 16),
            ("f1", "fin", "Venta cerrada", "ofi", 17),
        ],
        "flujos": [
            ("i", "t1", ""), ("t1", "t2", ""), ("t2", "t3", ""), ("t3", "t4", ""), ("t4", "t5", ""), ("t5", "g1", ""),
            ("g1", "f0", "No"), ("g1", "t6", "Sí"), ("t6", "t7", ""), ("t7", "g2", ""),
            ("g2", "t8", "Sí"), ("g2", "g3", "No"), ("t8", "t9", ""), ("t9", "g3", ""), ("g3", "g4", ""),
            ("g4", "t10", "Sí"), ("g4", "f1", "No"), ("t10", "t11", ""), ("t11", "t12", ""), ("t12", "f1", ""),
        ],
    },
    {
        "archivo": "tobe_venta_servicio",
        "titulo": "TO-BE · Venta, instalación y cobro con SGE-JD",
        "carriles": [("cli", "Cliente"), ("asi", "Asistente (Lucy)"), ("tec", "Técnico"), ("sis", "SGE-JD")],
        "nodos": [
            ("i", "inicio", "Necesita un equipo o servicio", "cli", 0),
            ("t1", "manual", "Pide una cotización", "cli", 1),
            ("t2", "tarea", "Registra la proforma (cliente por RUC/DNI, productos, descuento)", "asi", 2),
            ("s1", "sistema", "Calcula precios e IGV, aplica el tope de descuento, genera el PDF y lo envía por correo", "sis", 3),
            ("t3", "manual", "Revisa la proforma", "cli", 4),
            ("g1", "compuerta", "¿Acepta?", "cli", 5),
            ("f0", "fin", "Proforma rechazada o vencida", "cli", 6),
            ("t4", "tarea", "Convierte la proforma en venta (contado o crédito)", "asi", 6),
            ("s2", "sistema", "Valida y descuenta stock, emite el comprobante, calcula detracción o retención y programa mantenimientos", "sis", 7),
            ("g2", "compuerta", "¿Requiere instalación?", "asi", 8),
            ("t5", "tarea", "Programa la orden de trabajo (técnico, fecha y hora)", "asi", 9),
            ("s3", "sistema", "Verifica que el técnico no tenga otra visita a esa hora (agenda)", "sis", 10),
            ("t6", "tarea", "Instala y cierra la orden (materiales usados, quién recibió)", "tec", 11),
            ("g3", "compuerta", "", "asi", 12),
            ("g4", "compuerta", "¿Es a crédito?", "asi", 13),
            ("s4", "sistema", "Muestra el saldo, los días de atraso y el recordatorio por WhatsApp", "sis", 14),
            ("t7", "manual", "Paga (transferencia, depósito, Yape…)", "cli", 15),
            ("t8", "tarea", "Registra el cobro con su N° de operación", "asi", 16),
            ("s5", "sistema", "Rechaza un voucher repetido, actualiza el saldo y al cubrirlo marca la venta PAGADA", "sis", 17),
            ("f1", "fin", "Venta cobrada", "asi", 18),
        ],
        "flujos": [
            ("i", "t1", ""), ("t1", "t2", ""), ("t2", "s1", ""), ("s1", "t3", ""), ("t3", "g1", ""),
            ("g1", "f0", "No"), ("g1", "t4", "Sí"), ("t4", "s2", ""), ("s2", "g2", ""),
            ("g2", "t5", "Sí"), ("g2", "g3", "No"), ("t5", "s3", ""), ("s3", "t6", ""), ("t6", "g3", ""), ("g3", "g4", ""),
            ("g4", "s4", "Sí"), ("g4", "f1", "No"), ("s4", "t7", ""), ("t7", "t8", ""), ("t8", "s5", ""), ("s5", "f1", ""),
        ],
    },
    {
        "archivo": "asis_compra_inventario",
        "titulo": "AS-IS · Compra, pago al proveedor e inventario (BORRADOR: supuestos [S7]–[S9] por validar)",
        "carriles": [("ofi", "Oficina (Lucy)"), ("due", "Dueño (Lisandro)"), ("pro", "Proveedor")],
        "nodos": [
            ("i", "inicio", "Falta material al ir a usarlo [S7]", "ofi", 0),
            ("t1", "manual", "Pide el material al proveedor", "due", 1),
            ("t2", "manual", "Entrega la mercadería y la factura", "pro", 2),
            ("t3", "manual", "Archiva la factura y anota lo que llegó [S8]", "ofi", 3),
            ("g1", "compuerta", "¿Es a crédito?", "ofi", 4),
            ("t4", "manual", "Recuerda la fecha de pago [S9]", "due", 5),
            ("t5", "manual", "Paga al proveedor", "due", 6),
            ("g2", "compuerta", "", "ofi", 7),
            ("f1", "fin", "Material en almacén", "ofi", 8),
        ],
        "flujos": [
            ("i", "t1", ""), ("t1", "t2", ""), ("t2", "t3", ""), ("t3", "g1", ""),
            ("g1", "t4", "Sí"), ("g1", "g2", "No"), ("t4", "t5", ""), ("t5", "g2", ""), ("g2", "f1", ""),
        ],
    },
    {
        "archivo": "tobe_compra_inventario",
        "titulo": "TO-BE · Compra, cuentas por pagar e inventario con SGE-JD",
        "carriles": [("sis", "SGE-JD"), ("asi", "Asistente (Lucy)"), ("adm", "Administrador (Lisandro)"), ("pro", "Proveedor")],
        "nodos": [
            ("i", "inicio", "Alerta diaria de stock mínimo", "sis", 0),
            ("t1", "tarea", "Revisa los materiales bajo el mínimo", "asi", 1),
            ("t2", "manual", "Pide el material al proveedor", "adm", 2),
            ("t3", "manual", "Entrega la mercadería y la factura", "pro", 3),
            ("t4", "tarea", "Registra la compra (N° de factura, contado o crédito)", "asi", 4),
            ("s1", "sistema", "Rechaza una factura repetida y sube el stock de los materiales", "sis", 5),
            ("g1", "compuerta", "¿Es a crédito?", "asi", 6),
            ("s2", "sistema", "La deja en Cuentas por pagar con su vencimiento", "sis", 7),
            ("t5", "manual", "Paga al proveedor", "adm", 8),
            ("t6", "tarea", "Registra el pago con su N° de operación", "asi", 9),
            ("s3", "sistema", "Actualiza el saldo y al cubrirlo marca la compra PAGADA", "sis", 10),
            ("g2", "compuerta", "", "asi", 11),
            ("t7", "tarea", "Cuenta el almacén y registra el conteo físico o la merma", "adm", 12),
            ("s4", "sistema", "Ajusta el stock, lo muestra en el kardex y lo guarda en el historial", "sis", 13),
            ("f1", "fin", "Inventario cuadrado", "sis", 14),
        ],
        "flujos": [
            ("i", "t1", ""), ("t1", "t2", ""), ("t2", "t3", ""), ("t3", "t4", ""), ("t4", "s1", ""), ("s1", "g1", ""),
            ("g1", "s2", "Sí"), ("g1", "g2", "No"), ("s2", "t5", ""), ("t5", "t6", ""), ("t6", "s3", ""), ("s3", "g2", ""),
            ("g2", "t7", ""), ("t7", "s4", ""), ("s4", "f1", ""),
        ],
    },
]


# ── Geometría ──────────────────────────────────────────────────

def medidas(tipo):
    if tipo in ("inicio", "fin"):
        return EVENTO, EVENTO
    if tipo == "compuerta":
        return COMPUERTA, COMPUERTA
    return TAREA_W, TAREA_H


def caja(nodo, carriles):
    """(x, y, w, h) del nodo: centrado en su columna y su carril."""
    _, tipo, _, carril, col = nodo
    w, h = medidas(tipo)
    fila = [c[0] for c in carriles].index(carril)
    cx = X0 + col * COL + COL / 2
    cy = fila * CARRIL + CARRIL / 2
    return cx - w / 2, cy - h / 2, w, h


def ruta(o, d, carriles):
    """Puntos del flujo, en ángulos rectos. Mismo carril y columnas no vecinas: pasa por arriba de los nodos."""
    ox, oy, ow, oh = caja(o, carriles)
    dx, dy, dw, dh = caja(d, carriles)
    ocy, dcy = oy + oh / 2, dy + dh / 2
    if o[3] == d[3]:
        if d[4] - o[4] <= 1:
            return [(ox + ow, ocy), (dx, dcy)]
        fila = [c[0] for c in carriles].index(o[3])
        arriba = fila * CARRIL + 16
        return [(ox + ow / 2, oy), (ox + ow / 2, arriba), (dx + dw / 2, arriba), (dx + dw / 2, dy)]
    # Otro carril: si es la columna de al lado se baja/sube por el medio; si no, sale por arriba o abajo
    if d[4] == o[4]:
        return [(ox + ow / 2, oy + oh if dcy > ocy else oy), (dx + dw / 2, dy if dcy > ocy else dy + dh)]
    sale_y = oy + oh if dcy > ocy else oy
    return [(ox + ow / 2, sale_y), (ox + ow / 2, dcy), (dx, dcy)]


# ── BPMN 2.0 (XML) ─────────────────────────────────────────────

ETIQUETA = {"inicio": "startEvent", "fin": "endEvent", "tarea": "userTask", "manual": "manualTask",
            "sistema": "serviceTask", "compuerta": "exclusiveGateway"}


def bpmn(p):
    pid = "Proceso_" + p["archivo"]
    nodos = {n[0]: n for n in p["nodos"]}
    entrantes = {n: [] for n in nodos}
    salientes = {n: [] for n in nodos}
    for i, (o, d, _) in enumerate(p["flujos"]):
        salientes[o].append(f"Flujo_{i}")
        entrantes[d].append(f"Flujo_{i}")

    alto = len(p["carriles"]) * CARRIL
    ancho = X0 + (max(n[4] for n in p["nodos"]) + 1) * COL
    x = []
    x.append('<?xml version="1.0" encoding="UTF-8"?>')
    x.append('<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" '
             'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" '
             'xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Def_' + p["archivo"] + '" '
             'targetNamespace="https://ywagnercs.github.io/proyecto-SAC/bpmn" exporter="SGE-JD generar_bpmn.py" exporterVersion="1">')
    x.append(f'  <bpmn:collaboration id="Colab_{p["archivo"]}">')
    x.append(f'    <bpmn:participant id="Pool_{p["archivo"]}" name="{escape(p["titulo"])}" processRef="{pid}" />')
    x.append('  </bpmn:collaboration>')
    x.append(f'  <bpmn:process id="{pid}" isExecutable="false">')
    x.append('    <bpmn:laneSet id="Carriles">')
    for cid, nombre in p["carriles"]:
        x.append(f'      <bpmn:lane id="Carril_{cid}" name="{escape(nombre)}">')
        for n in p["nodos"]:
            if n[3] == cid:
                x.append(f'        <bpmn:flowNodeRef>{n[0]}</bpmn:flowNodeRef>')
        x.append('      </bpmn:lane>')
    x.append('    </bpmn:laneSet>')
    for n in p["nodos"]:
        etiqueta = ETIQUETA[n[1]]
        x.append(f'    <bpmn:{etiqueta} id="{n[0]}" name="{escape(n[2])}">')
        for f in entrantes[n[0]]:
            x.append(f'      <bpmn:incoming>{f}</bpmn:incoming>')
        for f in salientes[n[0]]:
            x.append(f'      <bpmn:outgoing>{f}</bpmn:outgoing>')
        x.append(f'    </bpmn:{etiqueta}>')
    for i, (o, d, etiqueta) in enumerate(p["flujos"]):
        nombre = f' name="{escape(etiqueta)}"' if etiqueta else ""
        x.append(f'    <bpmn:sequenceFlow id="Flujo_{i}"{nombre} sourceRef="{o}" targetRef="{d}" />')
    x.append('  </bpmn:process>')

    x.append(f'  <bpmndi:BPMNDiagram id="Diagrama_{p["archivo"]}">')
    x.append(f'    <bpmndi:BPMNPlane id="Plano_{p["archivo"]}" bpmnElement="Colab_{p["archivo"]}">')
    x.append(f'      <bpmndi:BPMNShape id="Pool_{p["archivo"]}_di" bpmnElement="Pool_{p["archivo"]}" isHorizontal="true">')
    x.append(f'        <dc:Bounds x="0" y="0" width="{ancho:.0f}" height="{alto}" />')
    x.append('      </bpmndi:BPMNShape>')
    for fila, (cid, _) in enumerate(p["carriles"]):
        x.append(f'      <bpmndi:BPMNShape id="Carril_{cid}_di" bpmnElement="Carril_{cid}" isHorizontal="true">')
        x.append(f'        <dc:Bounds x="30" y="{fila * CARRIL}" width="{ancho - 30:.0f}" height="{CARRIL}" />')
        x.append('      </bpmndi:BPMNShape>')
    for n in p["nodos"]:
        bx, by, bw, bh = caja(n, p["carriles"])
        marcador = ' isMarkerVisible="true"' if n[1] == "compuerta" else ""
        x.append(f'      <bpmndi:BPMNShape id="{n[0]}_di" bpmnElement="{n[0]}"{marcador}>')
        x.append(f'        <dc:Bounds x="{bx:.0f}" y="{by:.0f}" width="{bw}" height="{bh}" />')
        if n[1] in ("inicio", "fin", "compuerta") and n[2]:
            x.append(f'        <bpmndi:BPMNLabel><dc:Bounds x="{bx - 40:.0f}" y="{by + bh + 4:.0f}" width="{bw + 80}" height="28" /></bpmndi:BPMNLabel>')
        x.append('      </bpmndi:BPMNShape>')
    for i, (o, d, _) in enumerate(p["flujos"]):
        x.append(f'      <bpmndi:BPMNEdge id="Flujo_{i}_di" bpmnElement="Flujo_{i}">')
        for px, py in ruta(nodos[o], nodos[d], p["carriles"]):
            x.append(f'        <di:waypoint x="{px:.0f}" y="{py:.0f}" />')
        x.append('      </bpmndi:BPMNEdge>')
    x.append('    </bpmndi:BPMNPlane>')
    x.append('  </bpmndi:BPMNDiagram>')
    x.append('</bpmn:definitions>')
    return "\n".join(x) + "\n"


# ── SVG ────────────────────────────────────────────────────────

def lineas(texto, ancho_max):
    palabras, salida, actual = texto.split(), [], ""
    for p in palabras:
        if len(actual) + len(p) + 1 > ancho_max and actual:
            salida.append(actual)
            actual = p
        else:
            actual = (actual + " " + p).strip()
    if actual:
        salida.append(actual)
    return salida


def texto_centrado(cx, cy, texto, ancho_max, tam=11.5, peso="400"):
    filas = lineas(texto, ancho_max)
    alto = 14
    y0 = cy - (len(filas) - 1) * alto / 2
    partes = [f'<text x="{cx:.0f}" y="{y0:.0f}" text-anchor="middle" dominant-baseline="middle" font-size="{tam}" font-weight="{peso}">']
    for i, f in enumerate(filas):
        partes.append(f'<tspan x="{cx:.0f}" y="{y0 + i * alto:.0f}">{escape(f)}</tspan>')
    partes.append('</text>')
    return "".join(partes)


COLORES = {"tarea": ("#E0F3F9", "#0078B7"), "manual": ("#FFFFFF", "#5C6E7E"), "sistema": ("#102A43", "#102A43")}


def svg(p):
    alto = len(p["carriles"]) * CARRIL
    ancho = X0 + (max(n[4] for n in p["nodos"]) + 1) * COL
    nodos = {n[0]: n for n in p["nodos"]}
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-10 -46 {ancho + 20:.0f} {alto + 60}" width="{ancho + 20:.0f}" '
         f'font-family="Inter, Segoe UI, Arial, sans-serif" fill="#081B2C">',
         '<defs><marker id="flecha" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto">'
         '<path d="M0,0 L10,5 L0,10 z" fill="#33475B"/></marker></defs>',
         f'<rect x="-10" y="-46" width="{ancho + 20:.0f}" height="{alto + 60}" fill="#FFFFFF"/>',
         f'<text x="0" y="-20" font-size="16" font-weight="600">{escape(p["titulo"])}</text>',
         f'<rect x="0" y="0" width="{ancho:.0f}" height="{alto}" fill="none" stroke="#33475B" stroke-width="1.5"/>']
    for fila, (_, nombre) in enumerate(p["carriles"]):
        y = fila * CARRIL
        fondo = "#F6FBFD" if fila % 2 == 0 else "#FFFFFF"
        s.append(f'<rect x="30" y="{y}" width="{ancho - 30:.0f}" height="{CARRIL}" fill="{fondo}" stroke="#B5DCEA"/>')
        s.append(f'<text transform="translate(19 {y + CARRIL / 2:.0f}) rotate(-90)" text-anchor="middle" font-size="12" font-weight="600">{escape(nombre)}</text>')
    s.append(f'<line x1="30" y1="0" x2="30" y2="{alto}" stroke="#33475B" stroke-width="1.5"/>')

    for o, d, etiqueta in p["flujos"]:
        pts = ruta(nodos[o], nodos[d], p["carriles"])
        s.append('<polyline fill="none" stroke="#33475B" stroke-width="1.4" marker-end="url(#flecha)" points="'
                 + " ".join(f"{a:.0f},{b:.0f}" for a, b in pts) + '"/>')
        if etiqueta:
            (ax, ay), (bx, by) = pts[0], pts[1]
            lx, ly = (ax + 8, ay - 6) if ay == by else (ax + 6, (ay + by) / 2)
            s.append(f'<text x="{lx:.0f}" y="{ly:.0f}" font-size="11" font-weight="600" fill="#0078B7">{escape(etiqueta)}</text>')

    for n in p["nodos"]:
        bx, by, bw, bh = caja(n, p["carriles"])
        cx, cy = bx + bw / 2, by + bh / 2
        tipo = n[1]
        if tipo in ("inicio", "fin"):
            grosor = 1.5 if tipo == "inicio" else 4
            relleno = "#ECFDF5" if tipo == "inicio" else "#FEF2F2"
            s.append(f'<circle cx="{cx:.0f}" cy="{cy:.0f}" r="{EVENTO / 2}" fill="{relleno}" stroke="#081B2C" stroke-width="{grosor}"/>')
            s.append(texto_centrado(cx, by + bh + 22, n[2], 22, 10.5))
        elif tipo == "compuerta":
            m = COMPUERTA / 2
            s.append(f'<polygon points="{cx:.0f},{cy - m:.0f} {cx + m:.0f},{cy:.0f} {cx:.0f},{cy + m:.0f} {cx - m:.0f},{cy:.0f}" '
                     f'fill="#FFFBEB" stroke="#B45309" stroke-width="1.5"/>')
            s.append(f'<text x="{cx:.0f}" y="{cy + 1:.0f}" text-anchor="middle" dominant-baseline="middle" font-size="18" fill="#B45309">×</text>')
            if n[2]:
                # Arriba a la izquierda: arriba al centro pasan los flujos que saltan columnas
                filas = lineas(n[2], 16)
                for i, f in enumerate(filas):
                    s.append(f'<text x="{bx - 4:.0f}" y="{by - 4 - (len(filas) - 1 - i) * 13:.0f}" text-anchor="end" '
                             f'font-size="10.5" font-weight="600">{escape(f)}</text>')
        else:
            fondo, borde = COLORES[tipo]
            color_texto = "#FFFFFF" if tipo == "sistema" else "#081B2C"
            s.append(f'<rect x="{bx:.0f}" y="{by:.0f}" width="{bw}" height="{bh}" rx="10" fill="{fondo}" stroke="{borde}" stroke-width="1.4"/>')
            icono = {"tarea": "👤", "manual": "✋", "sistema": "⚙"}[tipo]
            s.append(f'<text x="{bx + 7:.0f}" y="{by + 14:.0f}" font-size="10" fill="{color_texto}">{icono}</text>')
            s.append(f'<g fill="{color_texto}">' + texto_centrado(cx, cy + 5, n[2], 23, 10.5) + '</g>')

    leyenda_y = alto + 6
    s.append(f'<text x="34" y="{leyenda_y + 4}" font-size="10" fill="#5C6E7E">✋ tarea manual · 👤 tarea en SGE-JD · ⚙ lo hace el sistema solo · '
             f'× decisión · generado por docs/diagramas/generar_bpmn.py</text>')
    s.append('</svg>')
    return "\n".join(s) + "\n"


def main():
    for p in PROCESOS:
        (AQUI / f'{p["archivo"]}.bpmn').write_text(bpmn(p), encoding="utf-8")
        (AQUI / f'{p["archivo"]}.svg').write_text(svg(p), encoding="utf-8")
        print("generado", p["archivo"])


if __name__ == "__main__":
    main()
