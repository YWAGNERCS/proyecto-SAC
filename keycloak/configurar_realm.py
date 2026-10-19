"""
Configura Keycloak para SGE-JD (S10b, secciones 3-5) usando la API de administración.
Equivale a los pasos de la consola: realm, roles, cliente y usuarios semilla.

Uso (con Keycloak en http://localhost:38080):
    python keycloak/configurar_realm.py
Se puede ejecutar varias veces: lo que ya existe se deja como está.
"""
import json
import urllib.error
import urllib.parse
import urllib.request

KC = "http://localhost:38080"
REALM = "bomerp"
CLIENTE = "bomerp-frontend"
CLIENTE_PRUEBAS = "sgejd-pruebas"
SPA = "http://localhost:4200"

# Roles reales del negocio (auditoría C2): Lisandro administra, Lucy registra.
ROLES = {
    "ADMIN": "Dueño (Lisandro): todo, incluido anular ventas, editar precios y eliminar",
    "ASISTENTE": "Asistente (Lucy): registra proformas, ventas, compras, clientes y cobros",
}
# Contraseña de desarrollo (cámbiela en producción). Correos .test: dominio reservado, no reales.
CLAVE = "JdRefri2026!"
USUARIOS = [
    {"username": "lisandro", "email": "lisandro@jdrefrigeracion.test",
     "firstName": "Lisandro", "lastName": "Madrid", "rol": "ADMIN"},
    {"username": "lucy", "email": "lucy@jdrefrigeracion.test",
     "firstName": "Lucy", "lastName": "Asistente", "rol": "ASISTENTE"},
]


def pedir(metodo, ruta, token=None, datos=None, form=False):
    url = KC + ruta
    cabeceras = {}
    cuerpo = None
    if form:
        cuerpo = urllib.parse.urlencode(datos).encode()
        cabeceras["Content-Type"] = "application/x-www-form-urlencoded"
    elif datos is not None:
        cuerpo = json.dumps(datos).encode()
        cabeceras["Content-Type"] = "application/json"
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=cuerpo, headers=cabeceras, method=metodo)
    try:
        with urllib.request.urlopen(req) as r:
            texto = r.read().decode()
            return r.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    _, tok = pedir("POST", "/realms/master/protocol/openid-connect/token", datos={
        "grant_type": "password", "client_id": "admin-cli",
        "username": "admin", "password": "admin123"}, form=True)
    t = tok["access_token"]

    # 3.1 Realm (con login en español, 3.2) y tiempos de token
    st, _ = pedir("GET", f"/admin/realms/{REALM}", t)
    if st == 404:
        pedir("POST", "/admin/realms", t, {"realm": REALM, "enabled": True})
        print("realm creado")
    pedir("PUT", f"/admin/realms/{REALM}", t, {
        "displayName": "SGE-JD · JD Refrigeración",
        "internationalizationEnabled": True,
        "supportedLocales": ["es", "en"],
        "defaultLocale": "es",
        # 5 min de access token (valor por defecto de Keycloak): ante un 401 la SPA lo renueva con el refresh token
        "accessTokenLifespan": 300,
        # Eventos de login, intercambio de código, refresco y cierre: auditoría en la consola (Events)
        "eventsEnabled": True,
        "eventsExpiration": 7 * 24 * 3600,
        "enabledEventTypes": ["LOGIN", "LOGIN_ERROR", "CODE_TO_TOKEN", "CODE_TO_TOKEN_ERROR",
                              "REFRESH_TOKEN", "REFRESH_TOKEN_ERROR", "LOGOUT"],
    })

    # 3.1 Roles de realm (viajan en realm_access.roles)
    for nombre, desc in ROLES.items():
        st, _ = pedir("POST", f"/admin/realms/{REALM}/roles", t, {"name": nombre, "description": desc})
        print("rol", nombre, "creado" if st == 201 else "ya existía")

    # 4. Cliente de la SPA (S11): público, solo Authorization Code y PKCE S256 obligatorio.
    #    Sin Direct access grants: la SPA nunca recibe la contraseña del usuario.
    guardar_cliente(t, {
        "clientId": CLIENTE, "name": "SGE-JD SPA", "protocol": "openid-connect",
        "publicClient": True, "standardFlowEnabled": True, "directAccessGrantsEnabled": False,
        "implicitFlowEnabled": False,
        "redirectUris": [SPA + "/auth/callback"], "webOrigins": [SPA],
        "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": SPA + "/*"},
    })

    # Cliente SOLO para los scripts de pruebas-seguridad (PowerShell no puede hacer el login con
    # redirección). Mantiene el atajo de contraseña directa: no se usa desde la SPA y no debe
    # existir en producción.
    guardar_cliente(t, {
        "clientId": CLIENTE_PRUEBAS, "name": "Scripts de prueba (solo desarrollo)", "protocol": "openid-connect",
        "publicClient": True, "standardFlowEnabled": False, "directAccessGrantsEnabled": True,
    })

    # 5. Usuarios semilla con su rol
    for u in USUARIOS:
        st, _ = pedir("POST", f"/admin/realms/{REALM}/users", t, {
            "username": u["username"], "email": u["email"], "emailVerified": True, "enabled": True,
            "firstName": u["firstName"], "lastName": u["lastName"], "requiredActions": [],
            "credentials": [{"type": "password", "value": CLAVE, "temporary": False}],
        })
        print("usuario", u["username"], "creado" if st == 201 else "ya existía")
        _, lista = pedir("GET", f"/admin/realms/{REALM}/users?exact=true&username={u['username']}", t)
        uid = lista[0]["id"]
        _, rol = pedir("GET", f"/admin/realms/{REALM}/roles/{u['rol']}", t)
        pedir("POST", f"/admin/realms/{REALM}/users/{uid}/role-mappings/realm", t,
              [{"id": rol["id"], "name": rol["name"]}])
        print("  sub =", uid, "· rol", u["rol"])


def guardar_cliente(t, datos):
    """Crea el cliente o, si ya existe, actualiza su configuración (idempotente)."""
    _, lista = pedir("GET", f"/admin/realms/{REALM}/clients?clientId={datos['clientId']}", t)
    if lista:
        actual = lista[0]
        actual.update({k: v for k, v in datos.items() if k != "attributes"})
        actual.setdefault("attributes", {}).update(datos.get("attributes", {}))
        pedir("PUT", f"/admin/realms/{REALM}/clients/{actual['id']}", t, actual)
        print("cliente", datos["clientId"], "actualizado")
    else:
        pedir("POST", f"/admin/realms/{REALM}/clients", t, datos)
        print("cliente", datos["clientId"], "creado")


if __name__ == "__main__":
    main()
