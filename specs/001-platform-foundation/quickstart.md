# Quickstart y validación: Platform Foundation

**Feature**: `001-platform-foundation`

Guía para demostrar de punta a punta que la feature cumple la spec. Los detalles de contrato
están en [contracts/](contracts/) y el modelo en [data-model.md](data-model.md).

## Requisitos previos

- JDK 25
- Docker en ejecución (Docker Desktop, OrbStack, Colima…) con `docker compose`
- Nada más: no se instala PostgreSQL ni Maven (se usa `./mvnw`)

## 1. Arranque local (US1, US2-1, SC-001)

```bash
git clone <repo> && cd proyecto-mangateca-backend
./mvnw spring-boot:run
```

Esperado:

- En el log aparece `docker compose up` y luego Flyway aplica `1 - init schema`.
- `docker ps` muestra el contenedor `postgres:18` del proyecto.
- `curl -s localhost:8080/actuator/health` devuelve `{"status":"UP","components":{"db":{"status":"UP"},...}}`.

Al detener con `Ctrl+C`, `docker ps` ya no muestra el contenedor (US1-2).

## 2. Seguridad, errores y contrato (US3, US4, US8)

Con la aplicación en marcha:

| Comando | Esperado |
|---------|----------|
| `curl -i localhost:8080/actuator/info` | 200, con nombre y versión |
| `curl -i localhost:8080/v3/api-docs` | 200, JSON con `"openapi":"3.` |
| Abrir `http://localhost:8080/swagger-ui.html` | Interfaz navegable |
| `curl -i localhost:8080/api/v1/cualquier-cosa` | 401, `application/problem+json`, `WWW-Authenticate: Bearer` |
| `curl -i -H 'Authorization: Bearer x.y.z' localhost:8080/api/v1/x` | 401 Problem Details |
| `curl -i -H 'Origin: https://evil.example' localhost:8080/actuator/health` | 403 Problem Details, sin `Access-Control-Allow-Origin` |
| `curl -i -X OPTIONS -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' localhost:8080/actuator/health` | 200 con `Access-Control-Allow-Origin: http://localhost:5173` |

Los casos 400, 403 por rol, 404, 405, 415 y 500 necesitan endpoints y tokens que solo existen
en los tests. Se validan en el paso 3.

## 3. Suite completa y puerta de calidad (US5, US6, US7)

```bash
./mvnw verify
```

Esperado: `BUILD SUCCESS`. Esto incluye `ApplicationModules.verify()`, los tests de errores,
seguridad, CORS, migraciones y health, el informe en `target/site/jacoco/index.html` y la
regla de cobertura evaluada. Si se ejecuta dos veces con la base de dev encendida, ambas
pasan y la base de dev no cambia (SC-005).

## 4. Demostración de fallos (SC-002, SC-006)

Cada cambio es **temporal**: se aplica, se observa el resultado y se revierte con
`git checkout -- .` / `git clean -fd`. **Nunca se commitea.**

| # | Cambio temporal | Comando | Esperado |
|---|-----------------|---------|----------|
| a | Crear `com.mangateca.api.demo.Demo` que use `com.mangateca.api.platform.security.CorsProperties` (tipo interno) | `./mvnw verify` | Falla `ModularityTests`, indicando `demo` → `platform` y el tipo |
| b | Crear `com.mangateca.api.demo.domain.Untested` con lógica sin tests | `./mvnw verify` | Falla `jacoco:check`: `lines covered ratio is 0.00, but expected minimum is 0.80` |
| c | Añadir en una clase de `main` un uso de API deprecada o un raw type (`List l = new ArrayList();`) | `./mvnw compile` | Falla la compilación: `warnings found and -Werror specified` |
| d | Con la app detenida: `docker compose up -d` y luego `UPDATE flyway_schema_history SET checksum = 1 WHERE version = '1';` | `./mvnw spring-boot:run` | El arranque se aborta con `Validate failed: Migration checksum mismatch` |
| e | Igual que (d), pero insertar una fila de versión `999` en `flyway_schema_history` | `./mvnw spring-boot:run` | El arranque se aborta, indicando una migración aplicada que no está en local |
| f | `MANGATECA_CORS_ALLOWED_ORIGINS='*' ./mvnw spring-boot:run` | — | El arranque se aborta con un error de binding de `mangateca.cors` |

Tras (d) y (e), limpiar la base de dev con `docker compose down -v`.

## 5. Revisión documental (US9, SC-007)

```bash
ls docs/adr/
```

Esperado: `ADR-001-…` a `ADR-005-…`. Cada uno tiene las secciones de
[data-model.md §ADR](data-model.md).
