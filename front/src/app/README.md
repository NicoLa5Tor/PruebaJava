# Arquitectura del frontend

## `core/`

Infraestructura global y de instancia única. Aquí viven configuración, acceso HTTP, interceptores, guards, servicios transversales y layout global. Ninguna funcionalidad de negocio debe depender directamente de los detalles de infraestructura fuera de esta carpeta.

## `shared/`

Código reutilizable y sin conocimiento de un dominio: componentes UI, directivas, pipes, modelos y utilidades. El componente `shared/ui/page-header` es el primer ejemplo.

## `features/`

Cada funcionalidad de negocio tiene su carpeta, con sus rutas, páginas, contratos y acceso a datos. `loans/` contiene la página del solicitante (`my-loans`) y la del revisor (`loan-review`, montada bajo `/admin/loans`), que comparten `data-access/` (API, store con signals, cliente SSE y Reactive Form).

## Raíz de `app/`

`App` es únicamente el punto de arranque y shell de rutas. Mantenerlo pequeño evita que se convierta en una pantalla de negocio global.
