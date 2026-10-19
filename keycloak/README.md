# Keycloak · identidad de SGE-JD (S10b)

Keycloak emite los tokens (JWT RS256) que el backend valida. Corre aparte del backend, con su propio Postgres.

| Pieza | Valor (desarrollo) |
|---|---|
| Consola | http://localhost:38080 · usuario y contraseña en `docker-compose.yml` (`KC_BOOTSTRAP_ADMIN_*`) |
| Realm | `bomerp` (login en español) |
| Roles | `ADMIN` (Lisandro) · `ASISTENTE` (Lucy) |
| Cliente de la SPA | `bomerp-frontend`: público, solo *Standard flow* (Authorization Code) con **PKCE S256 obligatorio**; *Direct access grants* desactivado. Retorno: `http://localhost:4200/auth/callback` |
| Cliente de pruebas | `sgejd-pruebas`: solo para `pruebas-seguridad/*.ps1` (PowerShell no puede hacer el login con redirección). Usa contraseña directa: **no crearlo en producción** |
| Usuarios | `lisandro` · `lucy` — contraseña de desarrollo en `configurar_realm.py` (cambiarla en producción) |
| Access token | 5 minutos; ante un 401 la SPA lo renueva con el refresh token |
| Eventos | Activados (login, canje de código, refresco, logout): consola → *Events* |

## Levantarlo

```powershell
cd C:\SGE-JD\bomerp-backend
docker compose up -d keycloak-db keycloak
```

La primera vez importa `realm-bomerp.json` (realm, roles, cliente y usuarios). Si el realm no aparece, créelo con:

```powershell
python keycloak/configurar_realm.py
```

## Probar

```powershell
cd bomerp-backend\pruebas-seguridad
.\05_matriz.ps1     # 14 casos: 401 / 403 / 200 / 201 / 204
```

## Exportar de nuevo (si cambia la configuración)

```powershell
docker exec sge-jd-keycloak /opt/keycloak/bin/kc.sh export --file /tmp/realm-bomerp.json --realm bomerp --users same_file
docker cp sge-jd-keycloak:/tmp/realm-bomerp.json keycloak/realm-bomerp.json
```

> El archivo exportado incluye los hashes de las contraseñas de desarrollo. No usar estas cuentas en producción.
