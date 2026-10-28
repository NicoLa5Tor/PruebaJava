# Sistema de Gestión de Préstamos Bancarios

Prueba técnica Makers (parte práctica): API REST con Spring Boot y front-end Angular. Los usuarios solicitan préstamos indicando monto y plazo, los administradores los aprueban o rechazan, y cada usuario sigue el estado de sus préstamos en tiempo real.

| Proyecto | Stack |
|---|---|
| `back/` | Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Cache, WebFlux/Reactor), PostgreSQL 17, Flyway, EhCache 3, JWT |
| `front/` | Angular 22 (standalone, signals), Reactive Forms, Bootstrap 5, Vitest |

## Cómo ejecutarlo

```powershell
# 1. Backend + PostgreSQL
Set-Location back
Copy-Item .env.example .env      # ajusta POSTGRES_PASSWORD y JWT_SECRET
docker compose up --build

# 2. Frontend (otra terminal)
Set-Location front
npm install
npm start                         # http://localhost:4200
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`. Usa `POST /api/v1/auth/login` y pega el `accessToken` en *Authorize*.
- El servidor de desarrollo de Angular redirige `/api` al backend en el puerto 8080 (`proxy.conf.json`).

### Cuentas de demostración

Las crea el seed al arrancar si `SEED_DEMO_USERS=true` (valor por defecto). La pantalla de login también las ofrece como atajos.

| Rol | Email | Contraseña | Qué puede hacer |
|---|---|---|---|
| `USER` | `usuario@test.com` | `123` | Solicitar préstamos; ver, modificar y cancelar los suyos mientras estén pendientes |
| `ADMIN` | `admin@test.com` | `123` | Ver todas las solicitudes, aprobar y rechazar |
| `SUPER_ADMIN` | `superadmin@test.com` | `123` | Todo lo de ADMIN, más administrar usuarios y roles |

`ADMIN_EMAIL` define además un super administrador real, que entra con Logto (Google/Facebook). Si se configura `SUPER_ADMIN_PASSWORD`, también puede entrar con contraseña.

## Arquitectura (hexagonal)

El backend está organizado por módulos (`user`, `auth`, `loan`, más `shared`). Cada módulo tiene tres capas y las dependencias solo apuntan hacia el dominio:

```
            ┌──────────────────────── infrastructure ────────────────────────┐
 HTTP ───▶  │ web (controllers, DTOs)          persistence (JPA adapters)    │ ───▶ PostgreSQL
            │ security (JWT filter)            cache (EhCache)               │ ───▶ EhCache
            │ events (SSE stream)              logto (OIDC adapter)          │ ───▶ Logto
            └───────────────┬──────────────────────────────▲─────────────────┘
                            │ port/in (use cases)          │ port/out (interfaces)
            ┌───────────────▼────────── application ───────┴─────────────────┐
            │ LoanApplicationService · LoanReviewService · LoanQueryService  │
            └───────────────┬────────────────────────────────────────────────┘
            ┌───────────────▼──────────── domain ────────────────────────────┐
            │ Loan (inmutable, reglas de negocio) · LoanStatus · eventos     │
            └────────────────────────────────────────────────────────────────┘
```

- **domain**: Java puro, sin Spring ni JPA. `Loan` concentra las reglas:
  - límites de monto (100 a 100.000.000) y de plazo (1 a 120 meses), con máximo dos decimales;
  - solo se revisa un préstamo `PENDING`;
  - nadie revisa su propio préstamo;
  - solo el solicitante modifica o cancela su préstamo.
- **application**: casos de uso detrás de interfaces (`port/in`). Hablan con el exterior solo mediante puertos (`port/out`). Define los límites transaccionales.
- **infrastructure**: adaptadores (controladores REST, repositorios JPA, JWT, Logto, EhCache, SSE).
- `ArchitectureTest` (ArchUnit) verifica estas reglas en cada build.

La única concesión pragmática es que los servicios de aplicación usan `@Service` y `@Transactional` de Spring. Escribir esos aspectos a mano no aportaba nada para el alcance de la prueba.

## Decisiones técnicas

| Requisito | Decisión y motivo |
|---|---|
| Transacciones en la aprobación | `LoanReviewService` hace en una sola transacción: `SELECT … FOR UPDATE` del préstamo, la transición de estado, el guardado y una fila de auditoría en `loan_reviews`. Si algo falla no se escribe nada. Dos admins que deciden a la vez quedan serializados: uno obtiene 200 y el otro 409 (hay un test de concurrencia). `@Version` protege además contra escrituras obsoletas. |
| Caché | Se usa EhCache 3 vía JCache para `loanById` y `loansByApplicant`, que son las consultas repetitivas de "¿ya aprobaron mi préstamo?". `@Cacheable`/`@CacheEvict` viven en el adaptador de persistencia, así el dominio no sabe que existe caché. El `CacheManager` es *transaction-aware*: las invalidaciones se aplican al hacer commit, de modo que nunca se cachea un valor que luego se revierte. |
| Spring WebFlux | Se usa solo donde aporta: `GET /api/v1/loans/me/events` es un stream SSE (`Flux`) alimentado por un `Sinks.Many`. Así el usuario ve el cambio de estado en vivo sin hacer polling. El evento se emite con `@TransactionalEventListener`, es decir, solo después del commit. El resto de la API sigue siendo MVC porque JPA es bloqueante y hacerla reactiva no daría ningún beneficio real. |
| Seguridad | JWT de acceso de 15 minutos en memoria, más refresh token rotativo en cookie `HttpOnly`/`Secure`/`SameSite=Strict` (solo se guarda su hash SHA-256). Las contraseñas usan BCrypt, y el login responde lo mismo y tarda lo mismo exista o no el email. La autorización por rol se declara con `@PreAuthorize` en cada endpoint. Un préstamo ajeno responde 404, para no revelar que existe. |
| Roles | `USER` solicita, `ADMIN` revisa y `SUPER_ADMIN` revisa y administra usuarios. No hay jerarquía implícita: un ADMIN no puede pedir préstamos y un USER no puede revisarlos. |
| Login | Email y contraseña, tal como pide el enunciado, conviviendo con Logto (OIDC social) que ya existía. Ambos emiten el mismo JWT local. |
| Base de datos | Flyway gestiona el esquema y Hibernate solo lo valida (`ddl-auto: validate`). Las bases creadas antes con `ddl-auto` se adoptan como V1 (`baseline-on-migrate`) y migran a V2 sin perder datos. |
| Errores | Todo error responde `application/problem+json` (RFC 9457): 400 con `errors[]` por campo, 401, 403, 404, 409 (conflicto o concurrencia), 422 (regla de negocio) y 500 sin filtrar detalles. |
| Préstamos | Nunca se borran: se cancelan (`PATCH /{id}/cancel`), como se esperaría en un banco. Para evitar abusos, un usuario puede tener como máximo 3 solicitudes pendientes. |
| Front: estado | Se usan servicios con signals, en la misma línea que el proyecto existente. `MyLoansStore` centraliza la lista, los contadores derivados (`computed`), la cancelación optimista con rollback y la aplicación de eventos SSE. Para este tamaño de aplicación NgRx agregaría mucho código repetitivo sin beneficio. |
| Front: auth | El access token vive solo en memoria, el refresh token en la cookie HttpOnly y el interceptor renueva el token ante un 401. Los guards `canMatch` por rol (`roleGuard('ADMIN','SUPER_ADMIN')`) evitan incluso descargar los módulos que el usuario no puede ver. Son solo una ayuda de UX: el backend vuelve a validar todo. |
| Front: SSE | Se usa `fetch` + `ReadableStream` en lugar de `EventSource`, porque `EventSource` no puede enviar el header `Authorization`. El cliente reconecta con backoff y renueva el token. |

## API

Todas las rutas cuelgan de `/api/v1`.

| Método | Ruta | Rol |
|---|---|---|
| POST | `/auth/login` · `/auth/logto` · `/auth/register` · `/auth/refresh` · `/auth/logout` | público |
| POST | `/loans` | USER |
| GET | `/loans/me` · `/loans/me/events` (SSE) | USER |
| PUT | `/loans/{id}` · PATCH `/loans/{id}/cancel` | USER (dueño, préstamo pendiente) |
| GET | `/loans/{id}` | dueño o revisor |
| GET | `/loans?status=&page=&size=` | ADMIN, SUPER_ADMIN |
| PATCH | `/loans/{id}/approve` · `/loans/{id}/reject` | ADMIN, SUPER_ADMIN |
| GET/PUT | `/users/me` | autenticado |
| GET/POST/PUT/DELETE | `/users`, `/users/{id}`, PUT `/users/{id}/password` | SUPER_ADMIN |

## Tests

```powershell
Set-Location back; mvn verify     # requiere Docker (Testcontainers)
Set-Location front; npm test -- --watch=false
```

- **Backend: unitarios** (JUnit 5 + Mockito, sin Spring):
  - dominio `Loan`: límites y transiciones;
  - `LoanReviewService`: orden lock → guardar → auditar → publicar, y rollback ante fallo;
  - `LoanApplicationService`;
  - `AuthenticationService`: login, refresh;
  - `UserCommandService`.
- **Backend: integración** (Testcontainers + PostgreSQL real):
  - seguridad por rol;
  - flujo completo solicitar → aprobar → consultar;
  - 409 en doble revisión y en revisiones concurrentes;
  - validaciones 400;
  - comprobación de que la caché evita la consulta a la base y se invalida al aprobar;
  - migraciones Flyway.
- **Backend: arquitectura**: ArchUnit.
- **Frontend** (Vitest):
  - `roleGuard` y redirección por rol;
  - validaciones del Reactive Form de solicitud;
  - `MyLoansStore` (contadores, inserción, cancelación optimista con rollback, eventos);
  - parser SSE.
