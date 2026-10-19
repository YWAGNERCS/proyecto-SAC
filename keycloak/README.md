# Keycloak · identidad de SGE-JD (S10b)

Keycloak emite los tokens (JWT RS256) que el backend valida. Corre aparte del backend, con su propio Postgres.

| Pieza | Valor (desarrollo) |
|---|---|
| Consola | http://localhost:38080 · `admin` / `admin123` |
| Realm | `bomerp` (login en español) |
| Roles | `ADMIN` (Lisandro) · `ASISTENTE` (Lucy) |
| Cliente | `bomerp-frontend` (público; *Direct access grants* activo hasta que S11 pase el login a PKCE) |
| Usuarios | `lisandro` · `lucy` — contraseña `JdRefri2026!` (cambiar en producción) |
| Access token | 15 minutos; la SPA lo renueva con el refresh token |

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
