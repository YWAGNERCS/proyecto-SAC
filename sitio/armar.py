"""Arma sitio/docs/ para MkDocs a partir de la documentación del proyecto (carpeta docs/ del backend).

La fuente sigue siendo una sola: docs/README.md del backend (se divide en una página
por sección, como las sesiones del sitio del curso), LIMITACIONES.md y lp2-demo.md de la rama frontend.
Las páginas generadas no se versionan (sitio/.gitignore).

    python sitio/armar.py [--lp2 ruta/a/lp2-demo.md]
"""
import argparse
import re
import shutil
from pathlib import Path

AQUI = Path(__file__).resolve().parent
FUENTE = AQUI.parent / "docs"

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


def enlaces(texto):
    """Los enlaces internos del README apuntan a anclas; en el sitio, a páginas."""
    for ancla, (archivo, _) in SECCIONES.items():
        texto = texto.replace(f"](#{ancla})", f"]({archivo})")
    for ancla, archivo in SUBSECCIONES.items():
        texto = texto.replace(f"](#{ancla})", f"]({archivo}#{ancla})")
    texto = texto.replace("](LIMITACIONES.md)", "](limitaciones.md)")
    # Docsify necesita `a\|b` dentro de una tabla; MkDocs ya ignora los | del código y mostraría la barra
    texto = re.sub(r"`[^`\n]*`", lambda m: m.group(0).replace("\\|", "|"), texto)
    # "6b. …" no es un número de lista: MkDocs lo pegaría al punto anterior. Va como párrafo propio dentro de él
    return re.sub(r"^(\d+[a-z])\. ", r"\n    **\1.** ", texto, flags=re.M)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lp2", default=str(AQUI.parent.parent / "bomerp-frontend" / "docs" / "lp2-demo.md"),
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
    (destino / "limitaciones.md").write_text(enlaces((FUENTE / "LIMITACIONES.md").read_text(encoding="utf-8")), encoding="utf-8")
    shutil.copyfile(args.lp2, destino / "lp2-demo.md")
    # Diagramas BPMN (imagen para la página y archivo .bpmn para abrir en Camunda Modeler o bpmn.io)
    (destino / "diagramas").mkdir(exist_ok=True)
    for f in (FUENTE / "diagramas").glob("*"):
        if f.suffix in (".svg", ".bpmn"):
            shutil.copyfile(f, destino / "diagramas" / f.name)

    print("páginas:", ", ".join(sorted(p.name for p in destino.glob("*.md"))))


if __name__ == "__main__":
    main()
