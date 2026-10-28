# Backend base

Base para una API REST con Java 21, Spring Boot, JPA y PostgreSQL. No incluye entidades ni datos de negocio ficticios.

## Ejecutar con Docker

Crea tu `.env` a partir de la plantilla (no se versiona) y reemplaza `POSTGRES_PASSWORD` y `JWT_SECRET`:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs/v1`

Para detener el entorno sin borrar los datos de PostgreSQL:

```powershell
docker compose down
```

## Añadir un recurso de negocio

1. Crea una entidad que extienda `BaseEntity`.
2. Crea un repositorio que extienda `BaseRepository<Entidad>`.
3. Define DTOs de entrada y salida para el recurso.
4. Implementa servicio y controlador bajo `/api/v1`.

Las excepciones de negocio deben lanzar las excepciones de `exception`; `GlobalExceptionHandler` las convierte en respuestas `application/problem+json` consistentes.
