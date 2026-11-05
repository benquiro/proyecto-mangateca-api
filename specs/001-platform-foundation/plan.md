# Implementation Plan: Platform Foundation, Persistence Baseline & Architectural Governance

**Branch**: `001-platform-foundation` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-platform-foundation/spec.md`

## Summary

Construir el walking skeleton de Mangateca API sobre el proyecto Spring Boot 4.1.2 existente.
La feature incluye:

- **Base de datos**: PostgreSQL 18, levantado por Docker Compose en dev y por Testcontainers
  en tests, con un esquema propiedad de Flyway. V1 crea solo `event_publication` de Spring
  Modulith. Hibernate queda en `validate` y OSIV desactivado.
- **Errores**: Problem Details uniformes desde el `@RestControllerAdvice` y desde los handlers
  de seguridad y CORS.
- **Seguridad**: deny-by-default como OAuth2 Resource Server JWT.
- **Operación**: Actuator limitado a `health`/`info` y OpenAPI público.
- **Gobierno automático**: `ApplicationModules.verify()`, JaCoCo al 80% en dominio/aplicación
  y `-Xlint:all` con `failOnWarning`.

Cada dependencia nueva entra precedida de su ADR (ADR-001 a ADR-005). Las decisiones técnicas
y sus fuentes están en [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (LTS), `--release 25`

**Primary Dependencies**:

- Ya presentes: Spring Boot 4.1.2 (webmvc, data-jpa, security, validation, actuator, flyway),
  springdoc-openapi 3.2.0 y Lombok.
- Nuevas, cada una con su ADR:
  - Spring Modulith 2.1.2 (`starter-jdbc`, `starter-test`)
  - `spring-boot-starter-security-oauth2-resource-server`
  - `spring-boot-docker-compose`
  - MapStruct 1.6.3 + `lombok-mapstruct-binding` 0.2.0
  - `jacoco-maven-plugin` 0.8.16

**Storage**: PostgreSQL 18 (`postgres:18`), Flyway (`db/migration/V1__init_schema.sql`)

**Testing**:

- JUnit 5 y AssertJ.
- Slices: `@WebMvcTest` para errores (`GlobalExceptionHandlerTests`) y para la autorización de
  controladores (`ProbeSecurityWebMvcTests`, Constitución §VI).
- Integración: `@SpringBootTest` (RANDOM_PORT) + Testcontainers `@ServiceConnection`, con
  `RestTestClient` (`@AutoConfigureRestTestClient`) del módulo `spring-boot-resttestclient`, que
  ya llega de forma transitiva vía `spring-boot-starter-webmvc-test` (sin dependencias nuevas).
- Surefire ejecuta `**/*Tests.java` y `**/*IT.java` (`<includes>` explícito: por defecto ignora
  `*IT`).
- Módulos: `@ApplicationModuleTest`.
- Arquitectura: `ApplicationModules.verify()`.
- JWT de prueba firmados con `NimbusJwtEncoder` y un par RSA efímero.

**Target Platform**: servidor Linux / contenedor OCI. En desarrollo, macOS/Linux/Windows con
Docker.

**Project Type**: web-service (API REST, monolito modular)

**Performance Goals**: el arranque local con la imagen ya descargada debe tardar menos de
30 s. No hay objetivos de throughput, porque no hay endpoints de negocio.

**Constraints**:

- Cero advertencias de compilación.
- Ningún secreto en el repositorio.
- Ninguna API deprecada, experimental o en preview.
- La base de dev y la de tests nunca se comparten.

**Scale/Scope**: 1 módulo (`platform`), 1 migración, ~10 clases de producción y 5 ADR.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Regla constitucional | Cómo se cumple | Estado |
|---|----------------------|----------------|--------|
| I | Módulos verticales, sin capas globales | Módulo `platform` con subpaquetes internos `web`/`security`; sin paquetes `controllers/` ni `services/` raíz | ✅ |
| I | Límites verificados con Modulith | `ModularityTests` con `ApplicationModules.of(ApiApplication.class).verify()` | ✅ |
| I | Integraciones vía contratos | N/A en esta feature (no hay pasarelas) | — |
| II | Dinero en `BigDecimal`/`Money`; VOs | N/A (no hay dominio) | — |
| III | Java 25, Boot 4.1.x, APIs estables | Boot 4.1.2; solo versiones GA; se usa el starter no deprecado `security-oauth2-resource-server` (R8) | ✅ |
| III | No eliminar dependencias base | Solo se añaden; se modifica `TestcontainersConfiguration` (imagen fija), sin quitar dependencias | ✅ |
| III | ADR por dependencia nueva | ADR-001…005 se escriben **antes** de tocar el `pom.xml` (orden de tareas) | ✅ |
| III | Records para DTO/VO/config | `CorsProperties` es un `record`; no hay DTOs de negocio | ✅ |
| III | Lombok solo en entidades; sin `@Data` | No hay entidades en esta feature, así que no se usa Lombok | ✅ |
| IV | PostgreSQL único; Flyway inmutable | V1 en Flyway; Modulith no inicializa esquema (R2) | ✅ |
| IV | `ddl-auto=validate`, OSIV off | `application.yaml` (ver contracts/configuration.md) | ✅ |
| IV | Entidades no expuestas; DTO + MapStruct | MapStruct integrado en el toolchain; no hay endpoints de negocio | ✅ |
| IV | `/api/v1`, plural, paginación | N/A hasta los módulos de negocio; las rutas de prueba usan `/api/v1/...` | — |
| IV | `ProblemDetail` vía `@RestControllerAdvice`, sin trazas | `GlobalExceptionHandler` + handlers de seguridad y CORS (R9–R11) | ✅ |
| IV | `@Transactional`; JSpecify | No hay servicios; `@NullMarked` en cada `package-info.java` (R15) | ✅ |
| V | JWT nativo (`oauth2-resource-server`, Nimbus) | Resource server nativo; `NimbusJwtEncoder` solo en tests (R8) | ✅ |
| V | Deny-by-default + `@PreAuthorize` | `anyRequest().authenticated()`; `@EnableMethodSecurity` activo; el 403 se prueba con una ruta de test que usa `@PreAuthorize` | ✅ |
| V | Roles `CUSTOMER`/`ADMIN`; aislamiento por identidad | Convención del claim `roles` (contracts/http-surface.md §2) | ✅ |
| V | Refresh tokens / bcrypt | Fuera de alcance (módulo identity) | — |
| VI | Testcontainers real, sin H2 | `postgres:18` con `@ServiceConnection` | ✅ |
| VI | Mocks solo en I/O | No hay mocks; los JWT son reales firmados con una clave efímera | ✅ |
| VI | JaCoCo ≥ 80% dominio/aplicación; `-Xlint:all` | R14 (incluye la limitación del conjunto vacío y el riesgo de `processing`) | ✅ |
| VII | Simplicidad, sin interfaces especulativas | Todas las clases son concretas; las únicas interfaces implementadas son contratos del framework (`AuthenticationEntryPoint`, `AccessDeniedHandler`, `ErrorController`) | ✅ |
| VII | Trade-offs documentados | Ver la sección "Evaluación de trade-offs" | ✅ |
| Hig. | Secretos por entorno; CORS sin `*`; Actuator `health`/`info`; logs ECS | contracts/configuration.md | ✅ |
| DoD | Spec, plan y tasks aprobados | La spec está validada; tasks queda para `/speckit-tasks` | ✅ |

**Resultado pre-Phase 0**: PASS, sin violaciones.
**Resultado post-Phase 1** (re-evaluado tras data-model y contracts): PASS. El diseño no
introduce interfaces propias, entidades ni dependencias fuera de las listadas.

## Evaluación de trade-offs (Principio VII)

| Elemento | Problema real que resuelve hoy | Alternativa simple descartada | Costo aceptado |
|----------|--------------------------------|-------------------------------|----------------|
| `ProblemDetailsResponseWriter` (clase concreta compartida) | El entry point 401, el handler 403 y el rechazo CORS escriben Problem Details fuera de MVC | Duplicar la serialización en 3 clases: tres copias que pueden divergir en `Content-Type` o campos | 1 clase de ~30 líneas |
| `ProblemDetailsCorsProcessor` + bean `corsFilter` | El rechazo CORS por defecto responde en texto plano e incumple FR-015 | `http.cors(c -> c.configurationSource(..))`, que no deja cambiar el cuerpo del rechazo | 1 subclase con 2 métodos sobrescritos (`rejectRequest` vacío y `processRequest`), más desactivar el auto-registro del filtro |
| `ProblemDetailsErrorController` | Los errores fuera de MVC (excepción en un filtro, `sendError`) salen en el JSON de `BasicErrorController` e incumplen FR-015 | Acotar FR-015 a MVC, o un `ErrorAttributes` propio que no produce `application/problem+json` | 1 controlador de ~30 líneas y el `permitAll` del `DispatcherType.ERROR` |
| `CorsProperties` (record con validación) | Hacer fallar el arranque ante `*` u orígenes mal formados (edge cases de la spec) | `@Value` + `split(",")` sin validación: el comodín pasaría en silencio | 1 record |
| `DevJwtDecoderConfiguration` (`@Profile("dev")`) | El arranque con un único comando necesita un `JwtDecoder` sin claves versionadas | Una clave pública de dev versionada (sin su privada no sirve y añade un archivo) | ~15 líneas; los tokens de dev se invalidan al reiniciar |
| Extender `ResponseEntityExceptionHandler` | Traducir las 6 excepciones MVC estándar a PD sin reescribirlas | Un advice escrito desde cero, que reimplementaría mapeos ya existentes | Herencia de una clase del framework |

## Project Structure

### Documentation (this feature)

```text
specs/001-platform-foundation/
├── plan.md              # Este archivo
├── research.md          # Phase 0: decisiones R1–R16
├── data-model.md        # Phase 1: event_publication, Problem Detail, CorsProperties, ADR
├── quickstart.md        # Phase 1: guía de validación de punta a punta
├── contracts/
│   ├── problem-details.schema.json
│   ├── http-surface.md
│   └── configuration.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks; no lo genera este comando)
```

### Source Code (repository root)

```text
compose.yaml                                   # servicio postgres:18 (dev)
docs/adr/
├── ADR-001-spring-modulith.md
├── ADR-002-mapstruct.md
├── ADR-003-oauth2-resource-server.md
├── ADR-004-docker-compose-dev.md
└── ADR-005-quality-gate.md
pom.xml                                        # + deps, BOM Modulith, JaCoCo, -Xlint, build-info, perfil dev en spring-boot:run
README.md                                      # sección "Arranque local"

src/main/java/com/mangateca/api/
├── ApiApplication.java
└── platform/                                  # módulo Modulith "platform"
    ├── package-info.java                      # @ApplicationModule + @NullMarked
    ├── web/
    │   ├── package-info.java
    │   ├── GlobalExceptionHandler.java        # @RestControllerAdvice, extiende ResponseEntityExceptionHandler
    │   ├── ProblemDetailsErrorController.java # /error → Problem Details (errores fuera de MVC)
    │   └── OpenApiConfiguration.java          # bean OpenAPI con esquema bearerAuth
    └── security/
        ├── package-info.java
        ├── SecurityConfiguration.java         # SecurityFilterChain deny-by-default, @EnableMethodSecurity, roles
        ├── ProblemDetailsResponseWriter.java
        ├── ProblemDetailsAuthenticationEntryPoint.java
        ├── ProblemDetailsAccessDeniedHandler.java
        ├── CorsProperties.java                # record @ConfigurationProperties("mangateca.cors")
        ├── ProblemDetailsCorsProcessor.java
        └── DevJwtDecoderConfiguration.java    # @Profile("dev")

src/main/resources/
├── application.yaml
├── application-dev.yaml
└── db/migration/V1__init_schema.sql

src/test/java/com/mangateca/api/
├── ApiApplicationTests.java                   # contextLoads (existente, + imports)
├── TestApiApplication.java                    # existente, + TestSecurityConfiguration
├── TestcontainersConfiguration.java           # existente; imagen fijada a postgres:18
├── TestSecurityConfiguration.java             # par RSA efímero: JwtDecoder + JwtEncoder
├── ModularityTests.java                       # ApplicationModules.verify() + documentación
├── persistence/
│   ├── FlywayMigrationIT.java                 # V1 aplicada; event_publication existe; sin tablas de negocio
│   └── SchemaDriftIT.java                     # checksum alterado / migración future / entidad sin tabla → fallo de arranque
├── actuator/
│   ├── HealthEndpointIT.java                  # público; app + db UP; sin detalles
│   └── HealthDatabaseDownIT.java              # contenedor propio detenido → 503 DOWN
├── openapi/OpenApiDocsIT.java                 # /v3/api-docs y swagger-ui públicos
└── platform/
    ├── PlatformModuleTests.java               # @ApplicationModuleTest
    ├── web/
    │   ├── GlobalExceptionHandlerTests.java   # @WebMvcTest: 400/400+errors/404/405/415/500
    │   ├── ErrorDispatchIT.java               # excepción en filtro → /error → 500 Problem Details
    │   └── ProbeController.java               # controlador de prueba (solo en test)
    └── security/
        ├── ProbeSecurityWebMvcTests.java      # @WebMvcTest + SecurityConfiguration: 401/403/200 por rol
        ├── SecurityIT.java                    # 401 (sin/inválido/expirado/otra clave), 403 por rol, rutas públicas
        ├── CorsIT.java                        # origen permitido / no permitido / preflight
        └── CorsPropertiesTests.java           # unitario: rechaza *, mal formados; vacío = ninguno
```

**Structure Decision**: un único proyecto Maven (monolito modular). Las clases de
infraestructura transversal viven en el módulo vertical `platform`. Los subpaquetes `web` y
`security` son internos para Modulith (solo el paquete raíz del módulo es API pública), de modo
que los futuros módulos de negocio no pueden depender de ellos. Los tests de integración
llevan el sufijo `IT` y los de slice o unitarios el sufijo `Tests`. Todos se ejecutan con
Surefire en `./mvnw verify` (no se añade Failsafe: Principio VII).

## Orden de implementación (guía para `/speckit-tasks`)

1. **ADRs** (ADR-001…005). Se escriben antes de modificar el `pom.xml`.
2. **`pom.xml`**: dependencias, BOM, procesadores, `-Xlint:all` + `failOnWarning`, JaCoCo,
   `build-info` y perfil dev. Verificación: `./mvnw compile` sin advertencias.
3. **Persistencia**: `compose.yaml`, `application*.yaml`, V1, imagen de Testcontainers y tests de
   migración y drift.
4. **Errores**: `GlobalExceptionHandler` + `GlobalExceptionHandlerTests`.
5. **Seguridad**: `SecurityConfiguration`, handlers PD, CORS, decoder dev y tests.
6. **Observabilidad y OpenAPI**: configuración + tests.
7. **Arquitectura**: `ModularityTests`, `PlatformModuleTests`; README y quickstart verificados.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| `-Xlint:processing` con Lombok/MapStruct produce advertencias | Se verifica en el paso 2; si ocurre, se enmienda ADR-005 y se registra aquí (R14) |
| MapStruct 1.6.3 con `--release 25` | Se detecta en el paso 2; si falla, se reabre ADR-002 |
| Expresar en YAML un `ignore-migration-patterns` vacío | Lo cubre el test de migración *future* en `SchemaDriftIT` (R5) |
| Regla JaCoCo vacía en esta feature | Se demuestra con quickstart §4b y se ejercita en la primera feature de negocio |

## Complexity Tracking

Sin violaciones constitucionales que justificar.
