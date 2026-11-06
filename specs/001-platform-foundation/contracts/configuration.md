# Contrato de configuración por entorno

**Feature**: `001-platform-foundation`

Ningún secreto se versiona (Constitución, Higiene Operacional). Los valores se inyectan por
variables de entorno con el relaxed binding de Spring Boot.

## Perfil `dev` (local)

Lo activa `./mvnw spring-boot:run`. No requiere variables.

| Aspecto        | Valor                                                                 |
|----------------|-----------------------------------------------------------------------|
| Base de datos  | Contenedor `postgres:18` de `compose.yaml` (ServiceConnection automática) |
| JWT            | Par RSA efímero generado en memoria al arrancar                       |
| CORS           | `http://localhost:5173`                                               |

## Entornos no-dev (obligatorias)

| Variable                                                       | Ejemplo                             | Si falta              |
|----------------------------------------------------------------|-------------------------------------|-----------------------|
| `SPRING_DATASOURCE_URL`                                        | `jdbc:postgresql://db:5432/mangateca` | Arranque falla      |
| `SPRING_DATASOURCE_USERNAME`                                   | `mangateca`                         | Arranque falla        |
| `SPRING_DATASOURCE_PASSWORD`                                   | *(secreto)*                         | Arranque falla        |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`| `file:/run/secrets/jwt-public.pem`  | Arranque falla (sin `JwtDecoder`) |

> **Sustituida (feature 002, ADR-006)**: `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`
> ya no se usa; la sustituyen las variables `MANGATECA_IDENTITY_JWT_*` (issuer, private-key-location,
> public-key-location). Ver `specs/002-identity-access/contracts/configuration.md`.

## Opcionales

| Variable                              | Formato                                   | Por defecto |
|---------------------------------------|-------------------------------------------|-------------|
| `MANGATECA_CORS_ALLOWED_ORIGINS`      | Lista separada por comas de `scheme://host[:port]` | vacía (ningún origen) |

Si `MANGATECA_CORS_ALLOWED_ORIGINS` contiene `*` o un valor mal formado, la aplicación no
arranca.

## Fijos en `application.yaml` (no configurables por entorno)

| Propiedad                                                | Valor   |
|----------------------------------------------------------|---------|
| `spring.jpa.hibernate.ddl-auto`                          | `validate` |
| `spring.jpa.open-in-view`                                | `false` |
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `false` |
| `spring.flyway.clean-disabled`                           | `true`  |
| `spring.flyway.validate-on-migrate`                      | `true`  |
| `spring.flyway.ignore-migration-patterns`                | `[]` (vacía a propósito) |
| `management.endpoints.web.exposure.include`              | `health,info` |
| `management.endpoint.health.show-details`                | `never` |
| `management.endpoint.health.show-components`             | `always` |
| `spring.web.error.include-stacktrace` / `include-message` / `include-binding-errors` | `never` |
| `logging.structured.format.console`                      | `ecs`   |

> Nota: `spring.flyway.ignore-migration-patterns` queda vacía a propósito porque por defecto Flyway ignora `*:future`; verificado con `SchemaDriftIT` caso 2.

> Nota: `server.error.*` está deprecado (level error) desde Boot 4.0.0 y se reemplaza por `spring.web.error.*`; comprobado en el spring-configuration-metadata.json de Boot 4.1.2.
