"""Arma sitio/docs/ para MkDocs a partir de la documentación del proyecto.

La fuente sigue siendo una sola y no se copia a mano:
  * docs/README.md del backend se divide en una página por sección (como las sesiones del sitio del curso);
  * LIMITACIONES.md, RTM.md, FACTURACION_SUNAT.md y las guías de LP2 (S13, S14, producción local) de docs/;
  * lp2-demo.md de la rama frontend;
  * ADS: docs/ads/ (S1 a S14) y docs/S14_ADS_decisiones_trazabilidad.md;
  * BD2: bd2/README.md y bd2/Sxx/README.md, y migracion_bd2/README.md (integración con LP2).
Los enlaces relativos se reescriben: a la página del sitio si el destino es una página, y a GitHub si es otra cosa
(scripts .sql/.ps1/.rman, evidencias). Así mkdocs build --strict no encuentra enlaces rotos.
Las páginas generadas no se versionan (sitio/.gitignore).

    python sitio/armar.py [--lp2 ruta/a/lp2-demo.md]
"""
import argparse
import os
import re
import shutil
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent                      # bomerp-backend
FUENTE = RAIZ / "docs"
GITHUB = "https://github.com/YWAGNERCS/proyecto-SAC/blob/master"
GITHUB_ARBOL = "https://github.com/YWAGNERCS/proyecto-SAC/tree/master"

# Ancla de cada sección del README → página del sitio y su título
SECCIONES = {
    "doc-funcional": ("funcional.md", "1. Documentación funcional"),
    "doc-tecnica": ("tecnica.md", "2. Documentación técnica"),
    "manual-usuario": ("manual.md", "3. Manual de usuario"),
    "estado-trazabilidad": ("estado.md", "4. Estado y trazabilidad del proyecto"),
    "por-verificar": ("por-verificar.md", "5. Por verificar"),
}
# Subtítulos enlazados desde otra sección: en el sitio quedan en otra página
SUBSECCIONES = {
    "relaciones-muchos-a-muchos": "tecnica.md",
    "sectores-del-producto": "tecnica.md",
}


def paginas_sueltas():
    """Archivo del repositorio → página del sitio (sin contar el README dividido ni lp2-demo.md)."""
    mapa = {
        FUENTE / "LIMITACIONES.md": "limitaciones.md",
        FUENTE / "RTM.md": "rtm.md",
        FUENTE / "FACTURACION_SUNAT.md": "facturacion-sunat.md",
        FUENTE / "S13_optimizacion_produccion.md": "lp2/S13_optimizacion_produccion.md",
        FUENTE / "S14_integracion_estabilizacion.md": "lp2/S14_integracion_estabilizacion.md",
        FUENTE / "PRODUCCION_LOCAL.md": "lp2/produccion-local.md",
        RAIZ / "migracion_bd2" / "README.md": "lp2/integracion-bd2.md",
        FUENTE / "ads" / "README.md": "ads/index.md",
        FUENTE / "S14_ADS_decisiones_trazabilidad.md": "ads/S14_ADS_decisiones_trazabilidad.md",
        RAIZ / "bd2" / "README.md": "bd2/index.md",
    }
    for f in sorted((FUENTE / "ads").glob("S*.md")):
        mapa[f] = f"ads/{f.name}"
    for f in sorted((RAIZ / "bd2").glob("S*/README.md")):
        mapa[f] = f"bd2/{f.parent.name}.md"
    return {k.resolve(): v for k, v in mapa.items()}


PAGINAS = paginas_sueltas()
# Para los enlaces que salen del README dividido: lo mismo, por nombre de archivo
GUIAS_README = {p.relative_to(FUENTE).as_posix(): sitio for p, sitio in PAGINAS.items()
                if p.is_relative_to(FUENTE.resolve())}

ENLACE = re.compile(r"(!?\[[^\]\n]*\])\(([^)\s]+)\)")


def reescribir(texto, origen: Path, pagina: str):
    """Enlaces relativos de `origen` (archivo del repo) para la página `pagina` del sitio."""
    carpeta_pagina = os.path.dirname(pagina)

    def cambiar(m):
        etiqueta, destino = m.group(1), m.group(2)
        if re.match(r"^[a-z]+:", destino) or destino.startswith("#"):
            return m.group(0)
        ruta, _, ancla = destino.partition("#")
        objetivo = (origen.parent / ruta).resolve()
        sufijo = f"#{ancla}" if ancla else ""
        def relativo(pagina_sitio):
            return os.path.relpath(pagina_sitio, carpeta_pagina or ".").replace(os.sep, "/")

        if objetivo == (FUENTE / "README.md").resolve():
            if not ancla:
                return f"{etiqueta}({relativo('index.md')})"
            if ancla in SECCIONES:
                return f"{etiqueta}({relativo(SECCIONES[ancla][0])})"
            if ancla in SUBSECCIONES:
                return f"{etiqueta}({relativo(SUBSECCIONES[ancla])}#{ancla})"
            return f"{etiqueta}({GITHUB}/docs/README.md#{ancla})"
        if objetivo in PAGINAS:
            return f"{etiqueta}({relativo(PAGINAS[objetivo])}{sufijo})"
        if objetivo.parent == (FUENTE / "diagramas").resolve():
            return f"{etiqueta}({relativo('diagramas/' + objetivo.name)})"   # se copian al sitio
        try:
            en_repo = objetivo.relative_to(RAIZ.resolve()).as_posix()
        except ValueError:
            return m.group(0)   # fuera del repositorio: se deja como está
        base = GITHUB_ARBOL if objetivo.is_dir() else GITHUB
        return f"{etiqueta}({base}/{en_repo}{sufijo})"

    return ENLACE.sub(cambiar, texto)


def enlaces(texto):
    """Los enlaces internos del README apuntan a anclas; en el sitio, a páginas."""
    for ancla, (archivo, _) in SECCIONES.items():
        texto = texto.replace(f"](#{ancla})", f"]({archivo})")
    for ancla, archivo in SUBSECCIONES.items():
        texto = texto.replace(f"](#{ancla})", f"]({archivo}#{ancla})")
    # Guías de docs/ que ahora son páginas del sitio; el resto se abre en GitHub
    for guia, pagina in sorted(GUIAS_README.items(), key=lambda g: -len(g[0])):
        texto = texto.replace(f"]({guia})", f"]({pagina})").replace(f"]({guia}#", f"]({pagina}#")
    texto = texto.replace("](PRUEBA_INTEGRACION_CORREO.md)", f"]({GITHUB}/docs/PRUEBA_INTEGRACION_CORREO.md)")
    # Docsify necesita `a\|b` dentro de una tabla; MkDocs ya ignora los | del código y mostraría la barra
    texto = re.sub(r"`[^`\n]*`", lambda m: m.group(0).replace("\\|", "|"), texto)
    # "6b. …" no es un número de lista: MkDocs lo pegaría al punto anterior. Va como párrafo propio dentro de él
    return re.sub(r"^(\d+[a-z])\. ", r"\n    **\1.** ", texto, flags=re.M)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lp2", default=str(RAIZ.parent / "bomerp-frontend" / "docs" / "lp2-demo.md"),
                        help="lp2-demo.md de la rama frontend")
    args = parser.parse_args()

    destino = AQUI / "docs"
    shutil.rmtree(destino, ignore_errors=True)
    destino.mkdir()

    readme = (FUENTE / "README.md").read_text(encoding="utf-8")
    partes = re.split(r'^<a id="([^"]+)"></a>\s*$', readme, flags=re.M)

    # Antes de la primera sección: título, datos de la revisión e índice (el índice lo reemplaza el menú)
    encabezado = "\n".join(l for l in partes[0].splitlines()
                           if l.startswith(">") and "Publicado en" not in l)

    for ancla, cuerpo in zip(partes[1::2], partes[2::2]):
        archivo, titulo = SECCIONES[ancla]
        lineas = re.sub(r"\n---\s*$", "", cuerpo.strip()).splitlines()
        assert lineas[0].startswith("## "), f"la sección {ancla} no empieza con su título"
        # El título lo pone la página; los subtítulos suben un nivel (### → ##)
        cuerpo = "\n".join(re.sub(r"^#(#{2,5}) ", r"\1 ", l) for l in lineas[1:]).strip()
        (destino / archivo).write_text(enlaces(f"# {titulo}\n\n{cuerpo}\n"), encoding="utf-8")

    inicio = (AQUI / "inicio.md").read_text(encoding="utf-8")
    (destino / "index.md").write_text(inicio.replace("{{ENCABEZADO}}", enlaces(encabezado)), encoding="utf-8")
    shutil.copyfile(args.lp2, destino / "lp2-demo.md")

    # Páginas sueltas (ADS, BD2, LP2 y guías), con sus enlaces reescritos
    for origen, pagina in PAGINAS.items():
        salida = destino / pagina
        salida.parent.mkdir(parents=True, exist_ok=True)
        texto = origen.read_text(encoding="utf-8-sig")
        salida.write_text(reescribir(texto, origen, pagina), encoding="utf-8")

    # Diagramas BPMN (imagen para la página y archivo .bpmn para abrir en Camunda Modeler o bpmn.io)
    (destino / "diagramas").mkdir(exist_ok=True)
    for f in (FUENTE / "diagramas").glob("*"):
        if f.suffix in (".svg", ".bpmn"):
            shutil.copyfile(f, destino / "diagramas" / f.name)

    print(len(list(destino.rglob("*.md"))), "páginas:",
          ", ".join(sorted(p.relative_to(destino).as_posix() for p in destino.rglob("*.md"))))


if __name__ == "__main__":
    main()
