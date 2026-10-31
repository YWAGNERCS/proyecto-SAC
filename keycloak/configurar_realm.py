"""
Configura Keycloak para SGE-JD (S10b, secciones 3-5) usando la API de administración.
Equivale a los pasos de la consola: realm, roles, cliente y usuarios semilla.

Uso (con Keycloak en http://localhost:38080):
    python keycloak/configurar_realm.py
Se puede ejecutar varias veces: lo que ya existe se deja como está.
"""
import json
import os
import pathlib
import urllib.error
import urllib.parse
import urllib.request

# 127.0.0.1 y no localhost: en Windows "localhost" prueba IPv6 primero y cada llamada tarda ~2 s
# (el token de admin dura 60 s y el script no alcanzaba a terminar)
KC = "http://127.0.0.1:38080"
REALM = "bomerp"
CLIENTE = "bomerp-frontend"
CLIENTE_PRUEBAS = "sgejd-pruebas"
CLIENTE_BACKEND = "sgejd-backend"
SPA = "http://localhost:4200"

# Roles reales del negocio (auditoría C2): Lisandro administra, Lucy registra.
ROLES = {
    "ADMIN": "Dueño (Lisandro): todo, incluido anular ventas, editar precios y eliminar",
    "ASISTENTE": "Asistente (Lucy): registra proformas, ventas, compras, clientes y cobros",
}
# Permisos delegables (Permiso.java): el dueño se los da a un asistente desde
# Configuración › Usuarios y permisos sin hacerlo ADMIN. ADMIN ya los tiene todos.
PERMISOS = {
    "PERM_ANULAR_VENTAS": "Puede anular ventas",
    "PERM_ANULAR_COBROS": "Puede anular cobros mal registrados",
    "PERM_DEVOLUCIONES": "Puede registrar devoluciones de dinero",
    "PERM_CATALOGO": "Puede crear, editar y eliminar productos y categorías",
    "PERM_ELIMINAR": "Puede eliminar clientes, proveedores y sedes",
    "PERM_SERVICIOS": "Puede gestionar contratos, cancelar órdenes y dar de baja equipos",
    "PERM_DESCUENTO_AMPLIADO": "Puede aplicar el tope de descuento del administrador",
}
# Lo mínimo que necesita la cuenta de servicio del backend (cliente realm-management)
ROLES_BACKEND = ["view-users", "manage-users", "view-realm", "view-events"]
# Correos .test: dominio reservado, no reales. Las contraseñas NO están aquí: salen de .env
# (variables SGEJD_CLAVE_<USUARIO> y KC_ADMIN_PASSWORD; plantilla en .env.example).
USUARIOS = [
    {"username": "lisandro", "email": "lisandro@jdrefrigeracion.test",
     "firstName": "Lisandro", "lastName": "Madrid", "rol": "ADMIN"},
    {"username": "lucy", "email": "lucy@jdrefrigeracion.test",
     "firstName": "Lucy", "lastName": "Asistente", "rol": "ASISTENTE"},
]


def secreto(nombre):
    """Lee un secreto de la variable de entorno o, si no está, de bomerp-backend/.env."""
    if os.environ.get(nombre):
        return os.environ[nombre]
    env = pathlib.Path(__file__).resolve().parent.parent / ".env"
    if env.exists():
        for linea in env.read_text(encoding="utf-8").splitlines():
            clave, _, valor = linea.partition("=")
            if clave.strip() == nombre and valor.strip():
                return valor.strip()
    raise SystemExit(f"Falta {nombre}: póngalo en .env (ver .env.example)")


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
        "username": "admin", "password": secreto("KC_ADMIN_PASSWORD")}, form=True)
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
        # Auditoría A9: tras 5 claves erradas el usuario espera 1 min (crece hasta 15); nunca se bloquea para siempre
        "bruteForceProtected": True, "permanentLockout": False, "failureFactor": 5,
        "waitIncrementSeconds": 60, "maxFailureWaitSeconds": 900, "maxDeltaTimeSeconds": 43200,
        "quickLoginCheckMilliSeconds": 1000, "minimumQuickLoginWaitSeconds": 60,
        "passwordPolicy": "length(10) and notUsername and notEmail",
    })

    # 3.1 Roles de realm (viajan en realm_access.roles)
    for nombre, desc in {**ROLES, **PERMISOS}.items():
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

    # Cuenta de servicio del backend (confidencial, solo client_credentials): lee usuarios, cambia
    # sus permisos PERM_* y lee los eventos de sesión. El secreto sale de .env (KC_BACKEND_SECRET).
    guardar_cliente(t, {
        "clientId": CLIENTE_BACKEND, "name": "Backend SGE-JD (cuenta de servicio)", "protocol": "openid-connect",
        "publicClient": False, "serviceAccountsEnabled": True, "standardFlowEnabled": False,
        "directAccessGrantsEnabled": False, "implicitFlowEnabled": False,
        "clientAuthenticatorType": "client-secret", "secret": secreto("KC_BACKEND_SECRET"),
    })
    _, lista = pedir("GET", f"/admin/realms/{REALM}/clients?clientId={CLIENTE_BACKEND}", t)
    _, cuenta = pedir("GET", f"/admin/realms/{REALM}/clients/{lista[0]['id']}/service-account-user", t)
    _, gestion = pedir("GET", f"/admin/realms/{REALM}/clients?clientId=realm-management", t)
    gid = gestion[0]["id"]
    roles = []
    for nombre in ROLES_BACKEND:
        _, rol = pedir("GET", f"/admin/realms/{REALM}/clients/{gid}/roles/{nombre}", t)
        roles.append({"id": rol["id"], "name": rol["name"]})
    pedir("POST", f"/admin/realms/{REALM}/users/{cuenta['id']}/role-mappings/clients/{gid}", t, roles)
    print("  cuenta de servicio con", ", ".join(ROLES_BACKEND))

    # 5. Usuarios semilla con su rol
    for u in USUARIOS:
        st, _ = pedir("POST", f"/admin/realms/{REALM}/users", t, {
            "username": u["username"], "email": u["email"], "emailVerified": True, "enabled": True,
            "firstName": u["firstName"], "lastName": u["lastName"], "requiredActions": [],
        })
        print("usuario", u["username"], "creado" if st == 201 else "ya existía")
        _, lista = pedir("GET", f"/admin/realms/{REALM}/users?exact=true&username={u['username']}", t)
        uid = lista[0]["id"]
        # La clave de .env manda: se vuelve a poner en cada ejecución (la política la valida)
        st, error = pedir("PUT", f"/admin/realms/{REALM}/users/{uid}/reset-password", t, {
            "type": "password", "value": secreto("SGEJD_CLAVE_" + u["username"].upper()), "temporary": False})
        if st != 204:
            raise SystemExit(f"Keycloak rechazó la clave de {u['username']}: {error}")
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
