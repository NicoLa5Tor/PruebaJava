# Workspace full-stack

Los proyectos son independientes:

- `back/`: API Spring Boot, PostgreSQL y Docker Compose.
- `front/`: aplicación Angular standalone.

## Backend

```powershell
Set-Location back
docker compose up --build
```

Swagger: `http://localhost:8080/swagger-ui/index.html`.

## Frontend

En otra terminal:

```powershell
Set-Location front
npm start
```

El servidor de desarrollo usa `proxy.conf.json` para enviar `/api` y `/actuator` al backend en el puerto `8080`, sin fijar una URL del backend dentro de los componentes.
