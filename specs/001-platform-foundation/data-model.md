# Data Model: Platform Foundation

**Feature**: `001-platform-foundation` | **Fecha**: 2026-11-05

Esta feature no crea entidades de negocio. Solo define las estructuras de soporte de la
plataforma.

## 1. Tablas creadas por la migración V1 (`V1__init_schema.sql`)

### `event_publication`

Registro de publicación de eventos de dominio de Spring Modulith 2.x (estructura v2). El DDL es
el de `org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql` del jar
`spring-modulith-events-jdbc` 2.1.2 y MUST copiarse sin alteraciones semánticas.

| Columna                  | Tipo          | Nulo | Descripción                                         |
|--------------------------|---------------|------|-----------------------------------------------------|
| `id`                     | `UUID`        | no   | PK de la publicación                                |
| `listener_id`            | `TEXT`        | no   | Identificador del listener destinatario             |
| `event_type`             | `TEXT`        | no   | Nombre completo del tipo de evento                  |
| `serialized_event`       | `TEXT`        | no   | Evento serializado (JSON)                           |
| `publication_date`       | `TIMESTAMPTZ` | no   | Momento de publicación                              |
| `completion_date`        | `TIMESTAMPTZ` | sí   | Momento de procesamiento exitoso                    |
| `status`                 | `TEXT`        | sí   | `PUBLISHED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RESUBMITTED` |
| `completion_attempts`    | `INT`         | sí   | Intentos de invocación del listener                 |
| `last_resubmission_date` | `TIMESTAMPTZ` | sí   | Último reenvío                                      |

Índices:

- `event_publication_serialized_event_hash_idx`: `USING hash (serialized_event)`
- `event_publication_by_completion_date_idx`: `(completion_date)`

**Ciclo de vida** (gestionado por Modulith):

```text
PUBLISHED → PROCESSING → COMPLETED
                ↘ FAILED → RESUBMITTED → PROCESSING …
```

Con `completion-mode=update` (valor por defecto) se conservan las filas completadas.
Elegir otra política de retención queda para una feature posterior.

**Reglas**: V1 MUST NOT crear tablas de negocio ni extensiones (research R3). Una vez aplicada,
V1 es inmutable.

## 2. Tabla gestionada por Flyway

### `flyway_schema_history`

La crea y mantiene Flyway. No se modela ni se modifica a mano. Guarda versión, descripción,
checksum, fecha y resultado de cada migración. La validación al arrancar (research R5) la
compara con `src/main/resources/db/migration`:

| Situación                                         | Resultado                      |
|---------------------------------------------------|--------------------------------|
| Checksum distinto de una migración aplicada       | Arranque abortado              |
| Migración aplicada ausente localmente (missing/future) | Arranque abortado         |
| Migración local pendiente                         | Se aplica antes de aceptar tráfico |

## 3. Estructuras no persistidas

### Problem Detail (respuesta de error)

Esquema formal en [contracts/problem-details.schema.json](contracts/problem-details.schema.json).

| Campo      | Regla                                                                      |
|------------|----------------------------------------------------------------------------|
| `type`     | `about:blank` en esta feature                                              |
| `title`    | Frase HTTP estándar del código (p. ej. `Bad Request`)                      |
| `status`   | Igual al código HTTP de la respuesta                                       |
| `detail`   | Texto legible y genérico, sin nombres de clases, SQL ni trazas             |
| `instance` | Ruta de la petición                                                        |
| `errors`   | Solo en validación: lista no vacía de `{ "field": string, "message": string }` |

### `CorsProperties` (record de configuración, prefijo `mangateca.cors`)

| Campo            | Tipo           | Por defecto | Invariantes (constructor compacto)                              |
|------------------|----------------|-------------|-----------------------------------------------------------------|
| `allowedOrigins` | `List<String>` | `[]`        | Sin `null`; ningún valor `*`; cada valor es `scheme://host[:port]` sin ruta ni barra final |

Si se viola una invariante, el binding falla y la aplicación no arranca.

### ADR (documento en `docs/adr/`)

Nombre de archivo: `ADR-NNN-<slug>.md`. Secciones obligatorias:

1. Título, Estado (`Propuesto` | `Aceptado` | `Reemplazado por ADR-XXX`) y Fecha
2. Contexto y problema
3. Alternativas consideradas (incluida la opción nativa sin dependencia extra)
4. Decisión (artefacto y versión exactos)
5. Compatibilidad (Java 25, Spring Boot 4.1.x)
6. Estado de mantenimiento (última release, cadencia, licencia)
7. Consecuencias

| ADR     | Capacidad                                  | Artefactos                                                                 |
|---------|--------------------------------------------|----------------------------------------------------------------------------|
| ADR-001 | Arquitectura modular y eventos de dominio  | `spring-modulith-bom` 2.1.2, `spring-modulith-starter-jdbc`, `spring-modulith-starter-test` |
| ADR-002 | Mapeo entidad↔DTO en compilación           | `mapstruct` / `mapstruct-processor` 1.6.3, `lombok-mapstruct-binding` 0.2.0 |
| ADR-003 | Autenticación stateless (resource server)  | `spring-boot-starter-security-oauth2-resource-server`                      |
| ADR-004 | Infraestructura local en desarrollo        | `spring-boot-docker-compose` (optional), `compose.yaml`                    |
| ADR-005 | Puerta de calidad                          | `-Xlint:all,-processing` + `failOnWarning` (sin JaCoCo)                    |
