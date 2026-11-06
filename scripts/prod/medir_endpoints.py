"""
Medición para la decisión sobre Redis (S13): cuántas veces se llamó cada endpoint GET y cuánto tardó,
según Actuator (/actuator/metrics/http.server.requests, solo en el perfil dev y con token de ADMIN).

    python scripts/prod/medir_endpoints.py --guardar antes.json     (al empezar)
    ... usar el sistema unos minutos ...
    python scripts/prod/medir_endpoints.py --desde antes.json       (al terminar: solo lo ocurrido entre medio)

La clave de Lisandro se lee de .env y no se muestra.
"""
import argparse
import json
import pathlib
import urllib.parse
import urllib.request

BACKEND = "http://127.0.0.1:8080"
KEYCLOAK = "http://localhost:38080/realms/bomerp/protocol/openid-connect/token"
ENV = pathlib.Path(__file__).resolve().parents[2] / ".env"


def token():
    vals = dict(l.split("=", 1) for l in ENV.read_text(encoding="utf-8").splitlines() if "=" in l and not l.startswith("#"))
    datos = urllib.parse.urlencode({"grant_type": "password", "client_id": "sgejd-pruebas", "username": "lisandro",
                                    "password": vals["SGEJD_CLAVE_LISANDRO"].strip()}).encode()
    return json.load(urllib.request.urlopen(KEYCLOAK, datos))["access_token"]


def metricas(tk):
    def pedir(q=""):
        r = urllib.request.Request(BACKEND + "/actuator/metrics/http.server.requests" + q, headers={"Authorization": "Bearer " + tk})
        return json.load(urllib.request.urlopen(r))
    uris = next(t for t in pedir()["availableTags"] if t["tag"] == "uri")["values"]
    res = {}
    for u in uris:
        if not u.startswith("/api"):
            continue
        m = {s["statistic"]: s["value"] for s in pedir("?tag=" + urllib.parse.quote("uri:" + u) + "&tag=method:GET")["measurements"]}
        if m.get("COUNT"):
            res[u] = {"veces": int(m["COUNT"]), "total_s": m["TOTAL_TIME"]}
    return res


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--guardar")
    a.add_argument("--desde")
    args = a.parse_args()
    ahora = metricas(token())
    if args.guardar:
        pathlib.Path(args.guardar).write_text(json.dumps(ahora), encoding="utf-8")
        print(f"Línea base guardada ({sum(v['veces'] for v in ahora.values())} peticiones hasta ahora).")
        return
    antes = json.loads(pathlib.Path(args.desde).read_text(encoding="utf-8")) if args.desde else {}
    filas = []
    for u, v in ahora.items():
        b = antes.get(u, {"veces": 0, "total_s": 0})
        n = v["veces"] - b["veces"]
        if n > 0:
            filas.append((u.replace("/api/v1/admin", ""), n, (v["total_s"] - b["total_s"]) / n * 1000))
    filas.sort(key=lambda f: -f[1])
    print("Peticiones GET al backend (perfil dev) — Actuator /actuator/metrics/http.server.requests\n")
    print(f"{'Endpoint':34} {'Veces':>6} {'Promedio ms':>12} {'Total ms':>9}")
    print("-" * 64)
    for u, n, p in filas:
        print(f"{u:34} {n:6} {p:12.0f} {n * p:9.0f}")
    print("-" * 64)
    print(f"{'Total':34} {sum(f[1] for f in filas):6}")


if __name__ == "__main__":
    main()
