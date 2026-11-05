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
| `management.endpoints.web.exposure.include`              | `health,info` |
| `management.endpoint.health.show-details`                | `never` |
| `management.endpoint.health.show-components`             | `always` |
| `server.error.include-stacktrace` / `include-message`    | `never` |
| `logging.structured.format.console`                      | `ecs`   |
