# Arquitectura del frontend

## `core/`

Infraestructura global y de instancia única. Aquí viven configuración, acceso HTTP, interceptores, guards, servicios transversales y layout global. Ninguna funcionalidad de negocio debe depender directamente de los detalles de infraestructura fuera de esta carpeta.

## `shared/`

Código reutilizable y sin conocimiento de un dominio: componentes UI, directivas, pipes, modelos y utilidades. El componente `shared/ui/page-header` es el primer ejemplo.

## `features/`

Cualquier funcionalidad de negocio futura tendrá su propia carpeta aquí. Cada feature encapsulará sus rutas, páginas, componentes exclusivos, contratos y acceso a datos. No se crearán componentes de ejemplo hasta definir el primer dominio.

## Raíz de `app/`

`App` es únicamente el punto de arranque y shell de rutas. Mantenerlo pequeño evita que se convierta en una pantalla de negocio global.
