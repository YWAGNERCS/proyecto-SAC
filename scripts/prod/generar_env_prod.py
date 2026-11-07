"""
Crea bomerp-backend/.env.prod con secretos aleatorios para el entorno de producción local (S13).
No muestra ningún valor en pantalla y no sobrescribe un .env.prod existente.
Las claves de Lisandro y Lucy quedan en .env.prod: ábralo para verlas (no se versiona).

    python scripts/prod/generar_env_prod.py
"""
import pathlib
import secrets
import string

DESTINO = pathlib.Path(__file__).resolve().parents[2] / ".env.prod"
PLANTILLA = DESTINO.with_name(".env.prod.example")
# Solo letras y dígitos: Oracle y la URL JDBC no se llevan bien con símbolos
ALFABETO = string.ascii_letters + string.digits


def clave(largo=20):
    while True:
        c = "".join(secrets.choice(ALFABETO) for _ in range(largo))
        # Oracle exige empezar con letra; la política de Keycloak, 10+ caracteres
        if c[0].isalpha() and any(x.isdigit() for x in c) and any(x.isupper() for x in c):
            return c


def main():
    if DESTINO.exists():
        raise SystemExit(f"{DESTINO.name} ya existe: no se sobrescribe (bórrelo si quiere generar otro).")
    lineas = []
    for linea in PLANTILLA.read_text(encoding="utf-8").splitlines():
        nombre, igual, valor = linea.partition("=")
        # Opcionales o de configuración (no son secretos a inventar): se dejan vacíos
        opcional = nombre.startswith(("MAIL_", "CORREO_", "DECOLECTA_", "FACTURACION_", "SUNAT_", "CUENTA_", "SERIE_"))
        if igual and not linea.startswith("#") and not valor and not opcional:
            linea = f"{nombre}={clave(32 if nombre == 'KC_BACKEND_SECRET' else 20)}"
        lineas.append(linea)
    DESTINO.write_text("\n".join(lineas) + "\n", encoding="utf-8")
    print(f"{DESTINO.name} creado con secretos nuevos (no se muestran).")


if __name__ == "__main__":
    main()
