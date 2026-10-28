# Backend base

API REST de préstamos bancarios con Java 21, Spring Boot, JPA, PostgreSQL, Flyway y EhCache. Ver el README raíz para decisiones técnicas y endpoints.

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

El código sigue arquitectura hexagonal por módulo (ver `loan/` como referencia):

1. `domain/`: modelo y reglas en Java puro (sin Spring ni JPA).
2. `application/port/in`: interfaces de casos de uso; `application/port/out`: lo que el caso de uso necesita del exterior.
3. `application/service`: implementación de los casos de uso (`@Transactional` aquí).
4. `infrastructure/persistence`: entidad JPA que extiende `BaseJpaEntity`, repositorio Spring Data y adaptador que implementa el puerto.
5. `infrastructure/web`: controlador bajo `/api/v1` y DTOs con Hibernate Validator.
6. Migración Flyway nueva en `src/main/resources/db/migration`.

`ArchitectureTest` falla si una capa interna depende de una externa. Las excepciones de `shared/domain/exception` se convierten en `application/problem+json` en `GlobalExceptionHandler`.

Si tu base local se creó antes de Flyway, se adopta automáticamente como V1. Para empezar desde cero: `docker compose down -v`.
