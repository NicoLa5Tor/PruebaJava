# Despliegue en producción

Copia esta carpeta al servidor como `/opt`. Cada servicio tiene su propio `compose.yaml` y todos comparten la red Docker externa `makers`.

```
Internet ─▶ Cloudflare (TLS, WAF, Turnstile) ─▶ cloudflared ─▶ nginx ─▶ frontend (Angular SSR + BFF) ─▶ backend ─▶ database
                                                 opt/cloudflare  opt/nginx  opt/project/frontend      project/backend  project/database
```

- **Ningún contenedor publica puertos.** La única entrada es el tunnel, que abre una conexión saliente hacia Cloudflare.
- **El backend no es accesible desde internet.** El servidor SSR de Angular hace de BFF: renderiza la app y reenvía `/api/v1/**` al backend por la red interna. El navegador solo habla con un origen, así que las cookies son *first-party*.

| Carpeta | Imagen | Contenedor |
|---|---|---|
| `project/database` | `postgres:17-alpine` (datos en `volumes/postgres`) | `makers-database` |
| `project/backend` | `nicoscale/backmakers:1.0.0` | `makers-backend` |
| `project/frontend` | `nicoscale/frontmakers:1.0.2` | `makers-frontend` |
| `nginx` | `nginx:1.29-alpine` | `makers-nginx` |
| `cloudflare` | `cloudflare/cloudflared` | `makers-cloudflared` |

## Primera instalación

```bash
docker network create makers

# Variables: copia cada .env.example a .env y complétalo
cp project/database/.env.example project/database/.env      # POSTGRES_PASSWORD
cp project/backend/.env.example  project/backend/.env       # DB_PASSWORD (la misma), JWT_SECRET, ...
cp project/frontend/.env.example project/frontend/.env      # NG_ALLOWED_HOSTS=tu-dominio
cp cloudflare/.env.example       cloudflare/.env            # TUNNEL_TOKEN

# Arranque, en este orden
docker compose -f project/database/compose.yaml up -d
docker compose -f project/backend/compose.yaml  up -d
docker compose -f project/frontend/compose.yaml up -d
docker compose -f nginx/compose.yaml            up -d
docker compose -f cloudflare/compose.yaml       up -d
```

En Cloudflare Zero Trust, ve a **Networks › Tunnels › (tu tunnel) › Public hostname** y agrega tu dominio. Como servicio indica `HTTP` → `makers-nginx:80`.

Si usas el login con Logto (Google/Facebook), agrega `https://tu-dominio/callback` a los *Redirect URIs* y `https://tu-dominio/login` a los *Post sign-out redirect URIs* de la app en Logto.

## Turnstile

1. Crea un widget en Cloudflare › Turnstile para tu dominio.
2. Pon la **site key** en `project/frontend/.env` como `TURNSTILE_SITE_KEY`.
3. Pon la **secret key** en `project/backend/.env` como `TURNSTILE_SECRET_KEY`.
4. Recrea ambos contenedores (`docker compose ... up -d`).

No hay que reconstruir imágenes: el front lee la site key en tiempo de ejecución (`/app-config.json`). Mientras alguna de las dos claves esté vacía, Turnstile queda desactivado.

## Actualizar

```bash
docker compose -f project/backend/compose.yaml pull && docker compose -f project/backend/compose.yaml up -d
docker compose -f project/frontend/compose.yaml pull && docker compose -f project/frontend/compose.yaml up -d
```

Flyway aplica las migraciones nuevas al arrancar el backend.

## Probar localmente sin tunnel

```bash
docker network create makers
# levanta database, backend y frontend como arriba, luego:
docker compose -f nginx/compose.yaml -f nginx/compose.local.yaml up -d   # http://localhost:8088
```

Para esa prueba usa `NG_ALLOWED_HOSTS=localhost`.

## Protecciones

| Capa | Qué hace |
|---|---|
| Cloudflare | TLS, WAF y Turnstile en el login |
| nginx | Límite de 10 logins por minuto por IP (`CF-Connecting-IP`) y 30 req/s en el resto de la API; gzip; SSE sin buffer |
| Frontend (BFF) | Rechaza un `Host` que no esté en `NG_ALLOWED_HOSTS`; envía cabeceras de seguridad (`X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`) |
| Backend | Bloquea temporalmente (HTTP 429) tras 5 fallos por cuenta o 20 por IP en 15 minutos; verifica Turnstile; usa JWT de 15 minutos más refresh token rotativo en cookie `HttpOnly`/`Secure`/`SameSite=Strict` |
