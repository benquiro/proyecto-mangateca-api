---

description: "Lista de tareas de la feature 001 - Platform Foundation"
---

# Tasks: Platform Foundation, Persistence Baseline & Architectural Governance

**Input**: documentos de diseño en `/specs/001-platform-foundation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (http-surface.md,
configuration.md, problem-details.schema.json) y quickstart.md

**Tests**: solo los **vitales** (constitución v2.0.0+, §VI: arquitectura y arranque, seguridad
crítica, errores sin fugas, esquema). Sin umbral de cobertura. Escribirlos primero es
recomendable, no obligatorio. Las tareas ~~tachadas~~ se retiraron el 2026-11-05 (sin casilla:
`/speckit-implement` las omite).

**Organization**: tareas agrupadas por historia de usuario para implementarlas y probarlas de
forma independiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo (archivos distintos y sin dependencias pendientes)
- **[Story]**: historia de usuario de la tarea (US1…US9)
- Todas las rutas son relativas a la raíz del repositorio

## Convenciones para todas las tareas

- **Git** (CLAUDE.md): un commit al terminar cada tarea, en Conventional Commits con el ID,
  p. ej. `feat(platform): add CorsProperties record (T034)`. Rama `001-platform-foundation`.
  Nunca hacer push. Las tareas de **demostración temporal** (marcadas ⚠️ TEMPORAL) **no se
  commitean**: se revierten con `git checkout -- . && git clean -fd`.
- **Paquete base**: `com.mangateca.api`. Producción en `src/main/java/com/mangateca/api/`,
  tests en `src/test/java/com/mangateca/api/`.
- **Sufijos**: los tests de integración (`@SpringBootTest`) acaban en `IT`; los slice/unitarios en
  `Tests`. Todos los ejecuta Surefire con `./mvnw verify` (no se añade Failsafe). Surefire **no**
  incluye `*IT.java` por defecto: la tarea T011 lo configura de forma explícita.
- **Cliente HTTP en ITs**: los `@SpringBootTest(webEnvironment = RANDOM_PORT)` usan `RestTestClient`
  con `@AutoConfigureRestTestClient`, contra el servidor real. El módulo
  `spring-boot-resttestclient` 4.1.2 ya llega de forma transitiva vía
  `spring-boot-starter-webmvc-test`: **no** se añade ninguna dependencia. No usar `TestRestTemplate`.
- **Tests de integración**: todo `@SpringBootTest` importa
  `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`, salvo los que
  declaran su propio contenedor (indicado en la tarea).
- **Problem Details**: todo error cumple `contracts/problem-details.schema.json`: `type` =
  `about:blank`, `title` = frase HTTP estándar, `status` = código HTTP, `detail` genérico,
  `instance` = ruta de la petición, `Content-Type: application/problem+json`. Los tests
  verifican `status`, `title`, `Content-Type` y la ausencia de información interna, **no** el
  texto literal de `detail`.
- **"Sin información interna"** en un test significa que el cuerpo no contiene las claves
  `trace`, `stackTrace` ni `exception`, ni las subcadenas `Exception`, `java.`, `jakarta.`,
  `org.springframework`, `SELECT` ni el mensaje de la excepción original.
- **APIs**: solo APIs estables y no deprecadas de Spring Boot 4.1.2, Spring Framework 7, Spring
  Security 7, Jackson 3 y Spring Modulith 2.1.2. Ante cualquier duda de API, consultar Context7
  antes de escribir código.
- **Nulabilidad**: cada paquete nuevo de `src/main` lleva `package-info.java` con
  `@org.jspecify.annotations.NullMarked`.
- **DoD** de cada tarea de código: `./mvnw verify` pasa sin advertencias.

---

## Phase 1: Setup (ADRs y toolchain)

**Purpose**: la constitución (Principio III, FR-002) exige que el ADR de cada dependencia nueva
exista y esté **Aceptado** antes de tocar el `pom.xml`. Por eso los ADR se escriben primero y
cada cambio de `pom.xml` depende de su ADR.

Cada ADR usa el nombre `docs/adr/ADR-NNN-<slug>.md`, `Estado: Aceptado`, `Fecha: 2026-11-05`, y
contiene **las 7 secciones obligatorias de data-model.md §ADR**, en este orden:
1. Título, Estado (`Propuesto` | `Aceptado` | `Reemplazado por ADR-XXX`) y Fecha
2. Contexto y problema
3. Alternativas consideradas (incluida la opción nativa sin dependencia extra)
4. Decisión (artefacto y versión exactos)
5. Compatibilidad (Java 25, Spring Boot 4.1.x)
6. Estado de mantenimiento (última release, cadencia, licencia)
7. Consecuencias

El contenido de cada ADR sale de la sección `research.md` indicada. Los datos de mantenimiento
(fecha de la última release, cadencia y licencia) se comprueban en Maven Central o en el
repositorio oficial del proyecto.

- [X] T001 [P] Escribir `docs/adr/ADR-001-spring-modulith.md` (fuente: research R1, R2 y R16). Problema: verificar automáticamente los límites entre módulos y registrar de forma persistente los eventos de dominio. Alternativas: ArchUnit puro, `ApplicationEventPublisher` nativo sin registro (opción nativa) y `spring-modulith-starter-jpa`. Decisión: `spring-modulith-bom` 2.1.2 importado en `dependencyManagement`, `spring-modulith-starter-jdbc` (compile) y `spring-modulith-starter-test` (test). Compatibilidad: 2.1.2 compilado contra Spring Boot 4.1.2. Consecuencias: la tabla `event_publication` la crea Flyway en V1 y se fija `spring.modulith.events.jdbc.schema-initialization.enabled=false`.
- [X] T002 [P] Escribir `docs/adr/ADR-002-mapstruct.md` (fuente: research R13). Problema: el Principio IV exige mapeo entidad↔DTO con MapStruct, verificado en compilación. Alternativas: mapeo manual en records (opción nativa, descartada porque el Principio IV exige MapStruct) y ModelMapper (reflexión en runtime). Decisión: `org.mapstruct:mapstruct` 1.6.3 (compile), `org.mapstruct:mapstruct-processor` 1.6.3 y `org.projectlombok:lombok-mapstruct-binding` 0.2.0 en `annotationProcessorPaths`, en el orden Lombok → binding → MapStruct. Consecuencias: no se crea ningún mapper hasta que exista un DTO real. Riesgo: compatibilidad con `--release 25`, que se verifica en T010.
- [X] T003 [P] Escribir `docs/adr/ADR-003-oauth2-resource-server.md` (fuente: research R8). Problema: autenticación stateless con tokens bearer emitidos por el futuro módulo de identidad (FR-021). Alternativas: librerías JWT de terceros y filtros manuales (prohibidos por el Principio V), HMAC con secreto compartido, una clave pública de dev versionada, y el starter `spring-boot-starter-oauth2-resource-server` (deprecado en Boot 4.1.2). Decisión: `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` (versión gestionada por el parent 4.1.2). Consecuencias: fuera de dev, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION` es obligatoria (fail-fast); en dev se usa un par RSA efímero en memoria; en tests, `NimbusJwtEncoder` con una clave efímera; la cabecera del 401 es siempre `WWW-Authenticate: Bearer`, sin `error`/`error_description` (research R10); **CSRF desactivado** (research R8): la autenticación viaja solo en la cabecera `Authorization`, que un sitio ajeno no puede adjuntar, y con CSRF activo un POST sin credenciales recibiría 403 en vez de 401 (SC-004). **Deuda registrada**: el módulo identity MUST reevaluar CSRF para el endpoint de refresh, que usará cookie con `allowCredentials=true` (p. ej. `SameSite=Strict` y CSRF solo en esa ruta).
- [X] T004 [P] Escribir `docs/adr/ADR-004-docker-compose-dev.md` (fuente: research R4 y R6). Problema: arrancar en local con un único comando sin instalar PostgreSQL (US1, FR-003, FR-004). Alternativas: instalación manual de PostgreSQL, `TestApiApplication` con Testcontainers como modo dev (la base se pierde en cada reinicio) y un script `docker compose up` aparte (dos comandos). Decisión: `org.springframework.boot:spring-boot-docker-compose` con `<optional>true</optional>` y un `compose.yaml` en la raíz con `postgres:18`, `lifecycle-management` `start-and-stop`. Consecuencias: requiere Docker; se omite en tests y no se incluye en el jar reempaquetado; la imagen es idéntica a la de Testcontainers.
- [X] T005 [P] Escribir `docs/adr/ADR-005-quality-gate.md` (fuente: research R14). Problema: FR-013 (cobertura de líneas ≥ 80% en dominio y aplicación) y FR-014 (cero advertencias). Alternativas: revisión manual sin umbral (opción nativa), herramientas SaaS externas y otros plugins de cobertura. Decisión: `org.jacoco:jacoco-maven-plugin` 0.8.16 (goals `prepare-agent`, `report` y `check` en `verify`; regla `BUNDLE`, `LINE` `COVEREDRATIO` mínimo `0.80`, sobre `com/mangateca/api/**/domain/**` y `com/mangateca/api/**/application/**`) y `maven-compiler-plugin` con `-Xlint:all` y `<failOnWarning>true</failOnWarning>` en `compile` y `testCompile`. Exclusiones documentadas (supuesto de la spec): clases de arranque (`ApiApplication`) y de configuración, que quedan fuera por estar fuera de los paquetes `domain`/`application`. Consecuencias: en esta feature la regla se evalúa sobre un conjunto vacío (se demuestra en quickstart §4b). Riesgo: `-Xlint:processing`, que se verifica en T010. **Enmienda (2026-11-05)**: JaCoCo retirado (constitución v2.0.0, FR-013 retirado); ADR-005 queda solo con `-Xlint:all,-processing` + `failOnWarning`.
- [X] T006 Modificar `pom.xml`: añadir en `<dependencyManagement>` el BOM `org.springframework.modulith:spring-modulith-bom` 2.1.2 (`<type>pom</type>`, `<scope>import</scope>`, versión en la propiedad `spring-modulith.version`), y en `<dependencies>` `spring-modulith-starter-jdbc` (compile) y `spring-modulith-starter-test` (`<scope>test</scope>`), sin versión. No eliminar ninguna dependencia existente (Principio III). Depende de T001.
- [X] T007 Modificar `pom.xml`: añadir `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` sin versión. NO usar el deprecado `spring-boot-starter-oauth2-resource-server`. `spring-security-test` ya llega vía `spring-boot-starter-security-test`. Depende de T003 y T006.
- [X] T008 Modificar `pom.xml`: añadir `org.springframework.boot:spring-boot-docker-compose` con `<optional>true</optional>` y sin versión. Depende de T004 y T007.
- [X] T009 Modificar `pom.xml`: añadir las propiedades `mapstruct.version` = `1.6.3` y `lombok-mapstruct-binding.version` = `0.2.0`, la dependencia `org.mapstruct:mapstruct` (compile), y en `annotationProcessorPaths` de **ambas** ejecuciones (`default-compile` y `default-testCompile`) del `maven-compiler-plugin` los procesadores en este orden exacto: `lombok`, `lombok-mapstruct-binding`, `mapstruct-processor`. Depende de T002 y T008.
- [X] T010 Modificar `pom.xml`: en **ambas** ejecuciones del `maven-compiler-plugin` añadir `<compilerArgs><arg>-Xlint:all</arg></compilerArgs>` y `<failOnWarning>true</failOnWarning>`, conservando `annotationProcessorPaths`. Después ejecutar `./mvnw -q clean test-compile` y confirmar cero advertencias. **Si aparece** `No processor claimed any of these annotations` (`-Xlint:processing`): DETENERSE, enmendar ADR-005 justificando `-Xlint:all,-processing` y registrarlo en `plan.md` (*Riesgos* y *Complexity Tracking*). Nunca se excluye en silencio. **Si MapStruct falla** con `--release 25`: detenerse y reabrir ADR-002. Depende de T005 y T009.
- [X] T011 Modificar `pom.xml`: añadir la configuración del `maven-surefire-plugin` (versión gestionada por el parent) con `<includes><include>**/*Tests.java</include><include>**/*IT.java</include></includes>`. Sin esto, Surefire ignora los `*IT.java` y `./mvnw verify` pasaría sin ejecutar los tests de integración. No añadir Failsafe (plan, *Structure Decision*). Verificación: crear un `src/test/java/com/mangateca/api/SurefireProbeIT.java` temporal con un test que falle, comprobar que `./mvnw verify` falla por él, borrarlo y confirmar que vuelve a pasar. Depende de T010.

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: estructura del módulo, configuración base, migración V1 e infraestructura de
tests compartida por todas las historias.

**⚠️ CRITICAL**: ninguna historia empieza hasta completar esta fase.

- [X] T012 [P] Crear `src/main/java/com/mangateca/api/package-info.java` con `@NullMarked` (`org.jspecify.annotations.NullMarked`, transitiva de `spring-core` 7; no declararla en `pom.xml`, research R15).
- [X] T013 [P] Crear `src/main/java/com/mangateca/api/platform/package-info.java`, que declara el módulo Modulith `platform` con `@org.springframework.modulith.ApplicationModule(displayName = "Platform")` y `@NullMarked`. No crear paquetes de negocio vacíos (research R16, Principio VII).
- [X] T014 [P] Crear `src/main/java/com/mangateca/api/platform/web/package-info.java` con `@NullMarked` (subpaquete interno del módulo `platform`).
- [X] T015 [P] Crear `src/main/java/com/mangateca/api/platform/security/package-info.java` con `@NullMarked` (subpaquete interno del módulo `platform`).
- [X] T016 Reescribir `src/main/resources/application.yaml` con los valores fijos de `contracts/configuration.md` que son transversales: `spring.application.name: api`; `spring.jpa.hibernate.ddl-auto: validate`; `spring.jpa.open-in-view: false`; `spring.modulith.events.jdbc.schema-initialization.enabled: false`; `management.endpoints.web.exposure.include: health,info`; `spring.web.error.include-stacktrace: never`, `spring.web.error.include-message: never`, `spring.web.error.include-binding-errors: never`; `logging.structured.format.console: ecs`. Sin URL, usuario ni contraseña de base de datos: fuera de dev llegan por `SPRING_DATASOURCE_*` (FR-023). No activar `spring.mvc.problemdetails.enabled` (lo sustituye el advice de US4, research R9). **Nota (2026-11-05)**: `server.error.include-*` está deprecado (level error) desde Boot 4.0.0; se usa `spring.web.error.include-*`.
- [X] T017 [P] Crear `src/main/resources/db/migration/V1__init_schema.sql` copiando **sin alteraciones semánticas** el DDL de `org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql` del jar `spring-modulith-events-jdbc` 2.1.2 (extraerlo con `unzip -p ~/.m2/repository/org/springframework/modulith/spring-modulith-events-jdbc/2.1.2/spring-modulith-events-jdbc-2.1.2.jar org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql`). Debe crear la tabla `event_publication` con las columnas de data-model.md §1: `id UUID` no nulo (PK), `listener_id TEXT` no nulo, `event_type TEXT` no nulo, `serialized_event TEXT` no nulo, `publication_date TIMESTAMPTZ` no nulo, `completion_date TIMESTAMPTZ` nulo, `status TEXT` nulo, `completion_attempts INT` nulo y `last_resubmission_date TIMESTAMPTZ` nulo, más los índices `event_publication_serialized_event_hash_idx` (`USING hash (serialized_event)`) y `event_publication_by_completion_date_idx` (`(completion_date)`). "V1 MUST NOT crear tablas de negocio ni extensiones" (data-model.md): sin `CREATE EXTENSION`. Añadir arriba un comentario con el origen del DDL. Si el DDL real difiere del de data-model.md, gana el del jar y se corrige data-model.md.
- [X] T018 [P] Modificar `src/test/java/com/mangateca/api/TestcontainersConfiguration.java`: hacer la clase `public` (la importan tests de subpaquetes) y fijar la imagen a `DockerImageName.parse("postgres:18")` en lugar de `postgres:latest` (research R4: idéntica a `compose.yaml`). Mantener el bean `@ServiceConnection PostgreSQLContainer`.
- [X] T019 [P] Crear `src/test/java/com/mangateca/api/TestSecurityConfiguration.java`: `public` `@TestConfiguration(proxyBeanMethods = false)` que genera al cargarse un par RSA 2048 efímero (`KeyPairGenerator`) y expone los beans `JwtDecoder` (`NimbusJwtDecoder.withPublicKey(...)`) y `JwtEncoder` (`NimbusJwtEncoder` sobre un `RSAKey`/`ImmutableJWKSet` de Nimbus incluido en Spring Security). Los tests firman con el `JwtEncoder` tokens RS256 con o sin el claim `roles` (lista con `CUSTOMER` y/o `ADMIN`, contracts/http-surface.md §2) y también tokens expirados. Para los tokens "con otra clave", cada test genera su propio par RSA. No se versiona ninguna clave.
- [X] T020 Modificar `src/test/java/com/mangateca/api/ApiApplicationTests.java` para importar `TestcontainersConfiguration.class` y `TestSecurityConfiguration.class`, y `src/test/java/com/mangateca/api/TestApiApplication.java` para usar `.with(TestcontainersConfiguration.class, TestSecurityConfiguration.class)`. Añadir a `ApiApplicationTests` un test `openInViewIsDisabled()`, que comprueba que el contexto **no** contiene ningún bean de tipo `OpenEntityManagerInViewInterceptor` (FR-009, `spring.jpa.open-in-view: false`). Verificar con `./mvnw verify`. Depende de T016, T018 y T019. **Nota (2026-11-05)**: `TestApiApplication` ya no importa `TestSecurityConfiguration`, porque `spring-boot:test-run` activa el perfil `dev` y su `JwtDecoder` chocaba con el de dev (BeanDefinitionOverrideException).
- [X] T021 [P] Crear el controlador **solo de test** `src/test/java/com/mangateca/api/platform/web/ProbeController.java` (`@RestController`, `@RequestMapping("/api/v1/probes")`, **sin** `@Validated` a nivel de clase, para que la validación de parámetros lance `HandlerMethodValidationException` y no `ConstraintViolationException`), con un `record ProbeRequest(@NotBlank String name, @NotNull @Positive Integer quantity)` anidado y estos endpoints: `POST` (consume `application/json`, `@Valid @RequestBody ProbeRequest`, devuelve 200 con el cuerpo); `GET` con `@RequestParam @Min(1) @Max(100) int size` (devuelve 200); `GET /admin` con `@PreAuthorize("hasRole('ADMIN')")` (devuelve 200); `GET /failure`, que lanza `new IllegalStateException("detalle interno SELECT * FROM secreto")` para comprobar que el mensaje no se filtra. Nunca forma parte de la API publicada (supuesto de la spec).

**Checkpoint**: `./mvnw verify` pasa y las historias pueden empezar.

---

## Phase 3: User Story 1 - Entorno local sin instalación manual (Priority: P1) 🎯 MVP

**Goal**: `./mvnw spring-boot:run` levanta PostgreSQL 18 con Docker Compose, aplica V1, deja la
API respondiendo y detiene el contenedor al apagar.

**Independent Test**: con el repositorio recién clonado y sin PostgreSQL instalado, ejecutar
`./mvnw spring-boot:run` y comprobar que la API responde. Al pulsar `Ctrl+C`, el contenedor se
detiene (quickstart §1).

### Implementation for User Story 1

- [X] T022 [US1] Crear `compose.yaml` en la raíz con un único servicio `postgres`: `image: postgres:18` (idéntica a T018), `environment` con `POSTGRES_DB: mangateca`, `POSTGRES_USER: mangateca` y `POSTGRES_HOST_AUTH_METHOD: trust` (dev local: así no se versiona ninguna contraseña, FR-023), y `ports: ['5432']` (puerto de host aleatorio para no colisionar con un PostgreSQL local). La conexión la detecta automáticamente `spring-boot-docker-compose` (ServiceConnection); no definir `spring.datasource.*`. **Nota (2026-11-05)**: tras revisión, el puerto se publica solo en loopback (`'127.0.0.1::5432'`), porque `trust` en 0.0.0.0 exponía la base a la red local.
- [X] T023 [US1] Crear `src/main/resources/application-dev.yaml` con `spring.docker.compose.lifecycle-management: start-and-stop` (explícito; US1-2: al detener la app se detiene la base).
- [X] T024 [US1] Modificar `pom.xml`: en el `spring-boot-maven-plugin` añadir `<configuration><profiles><profile>dev</profile></profiles></configuration>` para que `./mvnw spring-boot:run` active el perfil `dev` (research R6). Solo afecta a `spring-boot:run`, no al jar.
- [X] T025 [US1] Añadir a `README.md` la sección "Arranque local" (FR-004): requisitos (JDK 25 y Docker en ejecución con `docker compose`; nada más), el comando único `./mvnw spring-boot:run`, qué ocurre (compose levanta `postgres:18`, Flyway aplica V1, la API escucha en `http://localhost:8080`), cómo detenerla (`Ctrl+C` detiene también el contenedor) y que, sin Docker, el arranque falla con un mensaje que indica el requisito ausente.
- [X] T026 [US1] Validar manualmente quickstart.md §1: arrancar con `./mvnw spring-boot:run`, comprobar en el log `docker compose up` y la aplicación de `1 - init schema`, que `docker ps` muestra `postgres:18` y que `curl -i localhost:8080/actuator/health` responde (401 antes de US3, 200 después). Detener con `Ctrl+C` y comprobar que `docker ps` ya no muestra el contenedor. **Tiempos** (SC-001 y plan, *Performance Goals*): anotar el `Started ApiApplication in X seconds` del log con la imagen ya descargada (objetivo < 30 s) y estimar el tiempo total desde el clon con la primera descarga de `postgres:18` (objetivo < 10 min). Si no se cumplen, registrarlo en `plan.md` (*Riesgos*). **Docker ausente** (edge case): con el daemon de Docker detenido, `./mvnw spring-boot:run` falla durante el arranque con un mensaje que indica que Docker no está disponible, y no queda ninguna aplicación aceptando tráfico. Si algo falla, corregir T022–T024. Sin cambios de archivos salvo correcciones. **Resultado (2026-11-05)**: compose y V1 OK; `Started ApiApplication in 3.28 s` (< 30 s); tiempo desde el clon estimado en ~2–4 min (imagen `postgres:18` de 672 MB, no medido en frío); el health respondió **200** y no 401, porque sin cadena propia la autoconfiguración de Boot deja `health` abierto (la cadena propia llega en US3); Docker ausente simulado con `DOCKER_HOST` apuntando a un socket inexistente (decisión del usuario, sin parar Docker Desktop): el arranque falla y no queda nada escuchando en 8080.

**Checkpoint**: US1 funciona por sí sola (MVP demostrable).

---

## Phase 4: User Story 2 - Esquema versionado y verificado (Priority: P1)

**Goal**: Flyway es el único dueño del esquema y el arranque se aborta ante cualquier
discrepancia (FR-005…FR-008, SC-006).

**Independent Test**: arrancar contra una base vacía y comprobar V1 aplicada; arrancar contra
una base alterada y comprobar que el arranque se aborta.

### Tests for User Story 2 ⚠️ (escribir primero; deben fallar)

- [X] T027 [P] [US2] Crear `src/test/java/com/mangateca/api/persistence/FlywayMigrationIT.java` (`@SpringBootTest`, imports estándar, `JdbcTemplate`). Verifica: (a) `flyway_schema_history` tiene la versión `1` con `success = true`; (b) `event_publication` existe con las 9 columnas, sus tipos y su nulabilidad de data-model.md §1 (consultando `information_schema.columns`); (c) existen los índices `event_publication_serialized_event_hash_idx` y `event_publication_by_completion_date_idx` (`pg_indexes`); (d) en el esquema `public` solo hay las tablas `flyway_schema_history` y `event_publication` (sin tablas de negocio, FR-006); (e) `pg_extension` solo contiene `plpgsql` (sin extensiones, research R3).
- [X] T028 [P] [US2] Crear `src/test/java/com/mangateca/api/persistence/SchemaDriftIT.java`. Usa su **propio** `PostgreSQLContainer` `postgres:18` (`@Container static`, no el compartido) y arranca la aplicación a mano con `new SpringApplicationBuilder(ApiApplication.class, TestSecurityConfiguration.class)`, pasando `spring.datasource.url/username/password` del contenedor y `spring.docker.compose.enabled=false`. Para cada caso, primero un arranque correcto (aplica V1, se cierra el contexto), luego se altera la base con JDBC y se comprueba con AssertJ que el segundo arranque lanza una excepción cuya cadena de causas contiene el mensaje esperado: (1) `UPDATE flyway_schema_history SET checksum = 1 WHERE version = '1'` → `checksum mismatch`; (2) `INSERT` de una fila de versión `999` en `flyway_schema_history` → mensaje que identifica la `999` como aplicada y ausente en local (*future*/*missing*), lo que valida `ignore-migration-patterns` vacío; (3) una `@Entity` solo de test sin tabla (en un paquete propio, cargada con `@EntityScan` desde una `@TestConfiguration` añadida solo a este arranque) → fallo de `ddl-auto=validate` (`missing table`); (4) una URL JDBC a un puerto cerrado → el arranque falla (edge case "base inaccesible"). Tras cada caso, comprobar que el esquema no fue modificado por la aplicación (FR-008): mismas tablas y misma fila alterada. Restaurar la base entre casos (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`).

### Implementation for User Story 2

- [X] T029 [US2] Modificar `src/main/resources/application.yaml` añadiendo `spring.flyway.validate-on-migrate: true`, `spring.flyway.clean-disabled: true` y `spring.flyway.ignore-migration-patterns` con valor **vacío** (research R5: por defecto Flyway ignora `*:future`). Confirmar con el caso (2) de T028 cuál de las dos formas YAML (`""` o `[]`) hace fallar el arranque, dejar esa y comentar por qué. No usar `baseline-on-migrate`. Ejecutar `./mvnw verify`: T027 y T028 deben pasar. Depende de T027 y T028.

**Checkpoint**: US2 verificada automáticamente (SC-006).

---

## Phase 5: User Story 3 - Seguridad denegada por defecto (Priority: P1)

**Goal**: toda ruta exige un JWT válido salvo las públicas. Los 401, los 403 y los rechazos
CORS responden en Problem Details (FR-019…FR-022, SC-004).

**Independent Test**: peticiones sin credenciales, con credenciales inválidas y con
credenciales sin rol a rutas no públicas → 401/403; peticiones sin credenciales a las rutas
públicas → acceso concedido.

### Tests for User Story 3 ⚠️ (escribir primero; deben fallar)

- [X] T030 [P] [US3] Crear `src/test/java/com/mangateca/api/platform/security/CorsPropertiesTests.java` (unitario, sin contexto, más un `ApplicationContextRunner` con `@EnableConfigurationProperties(CorsProperties.class)` para el binding). Casos: lista vacía o ausente → `allowedOrigins()` vacía (falla cerrado, nunca "todos"); `null` → lista vacía; `http://localhost:5173` y `https://app.example.com:8443` → aceptados; `*` → rechazado; `https://app.example.com/` (barra final), `https://app.example.com/path`, `app.example.com` (sin esquema) y `ftp://x` → rechazados; con `mangateca.cors.allowed-origins=*` el contexto del runner falla al arrancar (`hasFailed()`). Invariantes de data-model.md: "Sin `null`; ningún valor `*`; cada valor es `scheme://host[:port]` sin ruta ni barra final".
- [X] T031 [P] [US3] Crear `src/test/java/com/mangateca/api/platform/security/SecurityIT.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`, imports estándar, `RestTestClient` inyectado; ver Convenciones). Firmar los tokens con el `JwtEncoder` de `TestSecurityConfiguration`. Casos: (1) `GET /api/v1/probes?size=1` sin token → 401; (2) `GET /api/v1/no-existe` sin token → 401, no 404 (deny-by-default antes de resolver la ruta); (3) `POST /api/v1/probes` sin token → 401, no 403 (CSRF desactivado); (4) token `x.y.z` malformado, token firmado con otra clave y token expirado → 401 los tres, con **cuerpo y cabecera `WWW-Authenticate` idénticos entre sí**: la cabecera empieza por `Bearer` y no contiene `error_description` (no revela qué comprobación falló); (5) todo 401 es Problem Details (`Content-Type: application/problem+json`, `status` 401, `title` `Unauthorized`, `instance` = ruta) sin información interna; (6) token con `roles: [CUSTOMER]` en `GET /api/v1/probes/admin` → 403 Problem Details (`title` `Forbidden`); con `roles: [ADMIN]` → 200; (7) token válido en `GET /api/v1/probes?size=1` → 200; (8) sin token, `/actuator/health`, `/actuator/info`, `/v3/api-docs` y `/swagger-ui.html` → ni 401 ni 403; (9) `/actuator/env` sin token → 401 y con token válido → 404 (no expuesto); (10) ninguna respuesta incluye `Set-Cookie` con `JSESSIONID` (stateless); (11) con `@ExtendWith(OutputCaptureExtension.class)`, la salida capturada durante los casos (4), (6) y (7) no contiene el texto de ningún token enviado (FR-018: nunca registrar tokens).
- [X] T032 [P] [US3] Crear `src/test/java/com/mangateca/api/platform/security/CorsIT.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "mangateca.cors.allowed-origins=http://localhost:5173")`, imports estándar). Casos: (1) `GET /actuator/health` con `Origin: http://localhost:5173` → 200 con `Access-Control-Allow-Origin: http://localhost:5173` (nunca `*`), `Access-Control-Allow-Credentials: true` y `Vary` que contiene `Origin`; (2) preflight `OPTIONS /api/v1/probes` con ese origen y `Access-Control-Request-Method: POST`, **sin token** → 200, con `Access-Control-Allow-Methods` que incluye `GET, POST, PUT, PATCH, DELETE` y `Access-Control-Allow-Headers` que admite `Authorization` y `Content-Type`; (3) `GET /actuator/health` con `Origin: https://evil.example` → 403 Problem Details (`title` `Forbidden`) sin ninguna cabecera `Access-Control-Allow-*`; (4) preflight desde `https://evil.example` → 403 Problem Details sin cabeceras `Access-Control-Allow-*`. Añadir una clase `@Nested` (o un segundo IT) **sin** la propiedad, en la que cualquier `Origin` cruzado recibe 403 Problem Details (falla cerrado).
- [X] T033 [P] [US3] Crear `src/test/java/com/mangateca/api/platform/security/ProbeSecurityWebMvcTests.java`, slice de autorización. `@WebMvcTest(ProbeController.class)` + `@Import({SecurityConfiguration.class, ProblemDetailsResponseWriter.class, ProblemDetailsAuthenticationEntryPoint.class, ProblemDetailsAccessDeniedHandler.class, TestSecurityConfiguration.class})` y MockMvc. Los tokens son **reales**: firmados con el `JwtEncoder` de `TestSecurityConfiguration` y enviados en `Authorization: Bearer`, para ejercitar el `JwtAuthenticationConverter` del claim `roles`; no usar `jwt()` ni `@WithMockUser`. Casos: (1) `GET /api/v1/probes/admin` sin token → 401 Problem Details con `WWW-Authenticate: Bearer`; (2) con `roles: [CUSTOMER]` → 403 Problem Details; (3) con `roles: [ADMIN]` → 200; (4) `GET /api/v1/probes?size=1` con `roles: [CUSTOMER]` → 200 (autenticado basta); (5) `POST /api/v1/probes` sin token → 401 (CSRF desactivado). Si el slice no carga `CorsProperties`, añadir `@EnableConfigurationProperties` o importar lo necesario, sin tocar producción. **Retirado (2026-11-05)**: archivo eliminado por duplicar los casos de `SecurityIT` (T031); con la constitución v2.0.0 el slice ya no es obligatorio.

### Implementation for User Story 3

- [X] T034 [P] [US3] Crear `src/main/java/com/mangateca/api/platform/security/CorsProperties.java`: `record CorsProperties(@Nullable List<String> allowedOrigins)` (`org.jspecify.annotations.Nullable`: el binding pasa `null` si la propiedad no existe; el paquete es `@NullMarked`) con `@ConfigurationProperties("mangateca.cors")`. El accesor `allowedOrigins()` nunca devuelve `null` gracias a la normalización. En el constructor compacto: `null` → `List.of()`; copia inmutable (`List.copyOf`); rechazar con `IllegalArgumentException` (mensaje que nombra la propiedad y el valor, sin datos sensibles) cualquier `*` y cualquier valor que no sea `scheme://host[:port]` con esquema `http` o `https`, sin ruta, consulta ni barra final (validar con `java.net.URI`). Hace pasar T030.
- [X] T035 [P] [US3] Crear `src/main/java/com/mangateca/api/platform/security/ProblemDetailsResponseWriter.java`: `@Component` concreto que recibe el `JsonMapper` de Jackson 3 del contexto y expone `void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)`. Construye `ProblemDetail.forStatusAndDetail(status, detail)` con `instance` = `URI` de `request.getRequestURI()`, fija el estado, `Content-Type: application/problem+json` y UTF-8, y serializa el cuerpo. Es la única serialización Problem Details fuera de MVC (ver la evaluación de trade-offs del plan). **Nota (2026-11-05)**: en Spring 7, `ProblemDetail.type` es null por defecto y se omite al serializar; todo `ProblemDetail` del proyecto fija `type = about:blank` de forma explícita. El `Content-Type` va sin parámetro charset (coherente con MVC) y el cuerpo se escribe en bytes UTF-8 (research R10).
- [X] T036 [US3] Crear `src/main/java/com/mangateca/api/platform/security/ProblemDetailsAuthenticationEntryPoint.java` (`implements AuthenticationEntryPoint`): fija la cabecera `WWW-Authenticate` al valor literal `Bearer` y escribe con `ProblemDetailsResponseWriter` un 401 con `detail` `"Se requiere autenticación válida."`. **No** delega en `BearerTokenAuthenticationEntryPoint`, que añade `error="invalid_token"` y `error_description` con la causa (p. ej. `Jwt expired at …`) e incumple el edge case "sin indicar cuál de las comprobaciones falló" (research R10, T031 caso 4). No registrar el token ni el mensaje de la excepción con datos del token (FR-018). Depende de T035.
- [X] T037 [P] [US3] Crear `src/main/java/com/mangateca/api/platform/security/ProblemDetailsAccessDeniedHandler.java` (`implements AccessDeniedHandler`): escribe con `ProblemDetailsResponseWriter` un 403 con `detail` `"No tiene permisos para acceder a este recurso."`. Depende de T035.
- [X] T038 [US3] Crear `src/main/java/com/mangateca/api/platform/security/ProblemDetailsCorsProcessor.java`: `extends DefaultCorsProcessor`; ante un origen no autorizado responde 403 Problem Details con `detail` `"Origen no autorizado."`, `Content-Type: application/problem+json` y sin cabeceras `Access-Control-Allow-*` (research R11). MUST escribir a través del `ProblemDetailsResponseWriter` (inyectado por constructor), que es la única serialización de Problem Details fuera de MVC (plan, evaluación de trade-offs). Mecanismo: (1) sobrescribir `rejectRequest(ServerHttpResponse)` para que **no escriba nada** (anula el `403 Invalid CORS request` en texto plano); (2) sobrescribir `processRequest(@Nullable CorsConfiguration, HttpServletRequest, HttpServletResponse)` para llamar a `super` y, si devuelve `false`, escribir con el writer un 403 `"Origen no autorizado."` usando el request y el response que recibe el método. Así no hay estado en el hilo ni casts. El método devuelve el mismo booleano que `super`. Depende de T035.
- [X] T039 [US3] Crear `src/main/java/com/mangateca/api/platform/security/SecurityConfiguration.java` (`@Configuration(proxyBeanMethods = false)`, `@EnableWebSecurity`, `@EnableMethodSecurity`, `@EnableConfigurationProperties(CorsProperties.class)`). Beans: (a) `CorsFilter corsFilter(CorsProperties)` con un `UrlBasedCorsConfigurationSource` para `/**`: `allowedOrigins` = `properties.allowedOrigins()` (vacía = ningún origen), `allowCredentials=true`, métodos `GET,POST,PUT,PATCH,DELETE`, cabeceras `Authorization, Content-Type`, y `setCorsProcessor(new ProblemDetailsCorsProcessor(...))`; (b) un `FilterRegistrationBean<CorsFilter>` con `setEnabled(false)` para que el servlet container no lo registre dos veces; (c) un `JwtAuthenticationConverter` cuyo `JwtGrantedAuthoritiesConverter` lee el claim `roles` con prefijo `ROLE_` (contracts/http-surface.md §2); (d) `SecurityFilterChain`: `cors(Customizer.withDefaults())` (recoge el bean `corsFilter`); `csrf` desactivado (justificado en research R8 y ADR-003: API stateless con bearer; con CSRF activo, un POST sin credenciales devolvería 403 en lugar de 401, lo que rompe SC-004); `sessionManagement` `STATELESS`; `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` para que el *error dispatch* a `/error` llegue al `ProblemDetailsErrorController` de US4 (la petición original ya pasó la autorización, y sin esto el error se convertiría en un 401); `permitAll` solo para `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html` y `/swagger-ui/**` (FR-019), y `anyRequest().authenticated()`; `oauth2ResourceServer(o -> o.jwt(...).authenticationEntryPoint(entryPoint))`; `exceptionHandling` con `ProblemDetailsAuthenticationEntryPoint` y `ProblemDetailsAccessDeniedHandler`; sin `formLogin` ni `httpBasic`. Fuera de dev, el `JwtDecoder` lo crea Boot desde `spring.security.oauth2.resourceserver.jwt.public-key-location` (si falta, el arranque falla). Depende de T034, T036, T037 y T038.
- [X] T040 [P] [US3] Crear `src/main/java/com/mangateca/api/platform/security/DevJwtDecoderConfiguration.java` (`@Configuration(proxyBeanMethods = false)`, `@Profile("dev")`): genera al arrancar un par RSA 2048 en memoria y expone un `JwtDecoder` (`NimbusJwtDecoder.withPublicKey`). Sin claves en el repositorio; los tokens de dev se invalidan en cada reinicio (aceptable: la emisión está fuera de alcance, research R8). Registrar en INFO que se usa un decoder efímero de desarrollo, sin imprimir la clave.
- [X] T041 [US3] Modificar `src/main/resources/application-dev.yaml` añadiendo `mangateca.cors.allowed-origins: http://localhost:5173` (contracts/configuration.md, perfil dev). En el resto de entornos se configura con `MANGATECA_CORS_ALLOWED_ORIGINS` (lista separada por comas); no añadir valor por defecto en `application.yaml`. Ejecutar `./mvnw verify`: T030, T031 y T032 pasan. Validar a mano las filas CORS y 401 de quickstart §2 con `./mvnw spring-boot:run`.

**Checkpoint**: US3 verificada (SC-004).

---

## Phase 6: User Story 4 - Errores estandarizados (Priority: P1)

**Goal**: 400, 404, 405, 415 y 500 se devuelven en Problem Details desde un único
`@RestControllerAdvice`, sin detalles internos (FR-015…FR-018, SC-003).

**Independent Test**: provocar cada categoría de error y verificar el formato, el código HTTP y
la ausencia de información interna.

### Tests for User Story 4 ⚠️ (escribir primero; deben fallar)

- [X] T042 [US4] Crear `src/test/java/com/mangateca/api/platform/web/GlobalExceptionHandlerTests.java` (`@WebMvcTest(ProbeController.class)` + `@Import(GlobalExceptionHandler.class)`, MockMvc, `@ExtendWith(OutputCaptureExtension.class)`). El slice no depende de `SecurityConfiguration`: el consumidor autenticado se simula con `SecurityMockMvcRequestPostProcessors.jwt()` y `csrf()`. Casos, todos con `Content-Type: application/problem+json`, `type` `about:blank`, `title` estándar, `status` igual al HTTP, `instance` = ruta y sin información interna: (1) `POST /api/v1/probes` con JSON malformado `{"name":` → 400; (2) `POST` con `{"name":"","quantity":-1}` → 400 con `errors` de **2** elementos `{field, message}` (`name` y `quantity`), cada uno con `message` no vacío (todos los campos en una única respuesta); (3) `GET /api/v1/probes?size=0` → 400 (`HandlerMethodValidationException`) con `errors` que incluye `size`; (4) `GET /api/v1/no-existe` → 404; (5) `DELETE /api/v1/probes` → 405 con cabecera `Allow`; (6) `POST /api/v1/probes` con `Content-Type: text/plain` → 415; (7) `GET /api/v1/probes/failure` → 500, cuyo cuerpo NO contiene `detalle interno`, `SELECT` ni `IllegalStateException`, mientras que la salida capturada SÍ contiene `IllegalStateException` en nivel ERROR (FR-018). Validar al menos un cuerpo contra `contracts/problem-details.schema.json` (comprobando campos con AssertJ si no se añade un validador JSON Schema: no añadir dependencias).
- [X] T043 [US4] Crear `src/test/java/com/mangateca/api/platform/web/ErrorDispatchIT.java`, que cubre los errores **fuera de MVC** (FR-015: "Toda respuesta de error"). `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`, imports estándar y una `@TestConfiguration` anidada que registra un `FilterRegistrationBean` con un `OncePerRequestFilter` **solo de test**, mapeado a `/api/v1/probes/filter-failure` y ordenado después de la cadena de seguridad, que lanza `new IllegalStateException("fallo interno en filtro SELECT secreto")`. Casos: (1) esa ruta con un token válido → 500 con `Content-Type: application/problem+json`, `type` `about:blank`, `title` `Internal Server Error`, `instance` = la ruta original (no `/error`) y sin información interna (sin `fallo interno`, `SELECT`, `IllegalStateException`, `timestamp`, `trace` ni `path`); (2) `GET /error` directo con un token válido (sin atributos de error) → 500 Problem Details (`title` `Internal Server Error`), nunca el JSON de `BasicErrorController`. Debe fallar antes de T046.
- [X] T044 [P] [US4] Crear `src/test/java/com/mangateca/api/platform/web/ProblemDetailsErrorControllerTests.java`, slice del controlador de error. `@WebMvcTest(ProblemDetailsErrorController.class)` + MockMvc + `@ExtendWith(OutputCaptureExtension.class)`. Autenticación simulada con `SecurityMockMvcRequestPostProcessors.jwt()` (el slice no depende de `SecurityConfiguration`). Invocar `GET /error` con `requestAttr` para estos casos: (1) `jakarta.servlet.error.status_code` = `404` y `jakarta.servlet.error.request_uri` = `/api/v1/x` → 404 Problem Details con `instance` `/api/v1/x`; (2) `status_code` = `500`, `request_uri` = `/api/v1/y`, `jakarta.servlet.error.exception` = `new IllegalStateException("secreto SELECT")` y `jakarta.servlet.error.message` = `"secreto"` → 500 Problem Details sin `secreto`, `SELECT` ni `IllegalStateException` en el cuerpo, mientras que la salida capturada SÍ contiene `IllegalStateException` en ERROR (FR-018); (3) sin atributos → 500 con `instance` `/error`; (4) `status_code` = `200` (no es error) → 500. En todos: `Content-Type: application/problem+json`, `type` `about:blank` y `title` estándar. Debe fallar antes de T046. **Retirado (2026-11-05)**: archivo eliminado por duplicar `ErrorDispatchIT` (T043); con la constitución v2.0.0 el slice ya no es obligatorio.

### Implementation for User Story 4

- [X] T045 [US4] Crear `src/main/java/com/mangateca/api/platform/web/GlobalExceptionHandler.java`: `@RestControllerAdvice` que **extiende** `ResponseEntityExceptionHandler` (research R9). (a) Sobrescribir `handleExceptionInternal` (o `createProblemDetail`) para que todo `ProblemDetail` tenga `type` `about:blank`, `title` = `HttpStatus.getReasonPhrase()`, `instance` = ruta de la petición y un `detail` genérico por estado según contracts/http-surface.md §3: 400 → `"El cuerpo de la petición no es válido."`, 404 → `"Recurso no encontrado."`, 405 → `"Método no soportado para este recurso."`, 415 → `"Tipo de contenido no soportado."`; conservar las cabeceras que aporta el framework (p. ej. `Allow`). (b) Sobrescribir `handleMethodArgumentNotValid` y `handleHandlerMethodValidationException`: `detail` `"La petición contiene campos inválidos."` y la propiedad de extensión `errors` = lista no vacía de `{field, message}` (un `record FieldErrorItem(String field, String message)` anidado en `GlobalExceptionHandler`, con `message` del `MessageSource`/`getDefaultMessage`, nunca el valor rechazado). (c) `@ExceptionHandler(Exception.class)` → 500 con `detail` `"Se produjo un error interno."` y un log `ERROR` con la excepción (SLF4J), sin registrar el cuerpo, las cabeceras ni el token de la petición (FR-018). No reabrir excepciones de Spring Security (`AccessDeniedException`, `AuthenticationException`): se relanzan para que las gestione la cadena de filtros. Hace pasar T042. **Nota (2026-11-05)**: tras la revisión se amplió el contrato (`problem-details.schema.json` admite cualquier 4xx/5xx y `http-surface.md` §3 tiene la fila "Otros 4xx/5xx del framework"), porque el framework también produce 406, 413 o 503. Todo 5xx que pasa por `handleExceptionInternal` se registra en ERROR (FR-018). Los textos de 401 y 403 salen ahora de `GlobalExceptionHandler.detailFor`.
- [X] T046 [US4] Crear `src/main/java/com/mangateca/api/platform/web/ProblemDetailsErrorController.java`: `@RestController` concreto que `implements org.springframework.boot.webmvc.error.ErrorController` (comprobar el paquete en Boot 4.1 con Context7) y está mapeado a `${spring.web.error.path:/error}`. Al existir este bean, Boot no registra `BasicErrorController`. Lee `jakarta.servlet.error.status_code` (500 por defecto si falta o no es un error 4xx/5xx) y `jakarta.servlet.error.request_uri` (para `instance`; `/error` si falta), y devuelve `ResponseEntity<ProblemDetail>` con `Content-Type: application/problem+json`, `type` `about:blank`, `title` = frase HTTP estándar y el mismo `detail` genérico por estado que T045. **Nunca** usa `jakarta.servlet.error.message` ni `jakarta.servlet.error.exception` en la respuesta. Para mantener una única tabla de textos, extraer los `detail` por estado a un método estático package-private de `GlobalExceptionHandler` y reutilizarlo aquí. Si existe `jakarta.servlet.error.exception`, la registra en ERROR con SLF4J, solo en el log del servidor (FR-018), sin cabeceras, cuerpo ni token. Anotar la clase con `@io.swagger.v3.oas.annotations.Hidden` para que `/error` nunca aparezca en el contrato OpenAPI publicado (lo verifica T057). Hace pasar T044 y T043 (T043 requiere el `dispatcherTypeMatchers(ERROR).permitAll()` de T039). **Nota (2026-11-05)**: `server.error.path` está deprecado (level error) desde Boot 4.0.0; se usa `spring.web.error.path`.

**Checkpoint**: US4 verificada (SC-003 junto con US3).

---

## Phase 7: User Story 5 - Pruebas aisladas y reproducibles (Priority: P2)

**Goal**: la suite usa siempre una instancia nueva y efímera de `postgres:18`, independiente de
la base de dev (FR-010, FR-011, SC-005).

**Independent Test**: ejecutar la suite dos veces seguidas, con la base de dev encendida y con
datos, y obtener resultados idénticos sin cambios en la base de dev.

### Tests for User Story 5 ⚠️

- [X] T047 [US5] Crear `src/test/java/com/mangateca/api/persistence/TestDatabaseIsolationIT.java` (`@SpringBootTest`, imports estándar). Verifica: (1) `SHOW server_version` empieza por `18` (mismo motor y versión que producción y dev); (2) la URL JDBC del `DataSource` es la del bean `PostgreSQLContainer` de `TestcontainersConfiguration` (`getJdbcUrl()`), es decir, no la del contenedor de Compose; (3) el driver es `org.postgresql` (sin H2 ni bases en memoria, FR-011). Verificar también que en `pom.xml` no hay ninguna dependencia de H2, HSQLDB ni Derby.

### Implementation for User Story 5

- [X] T048 [US5] Validar manualmente SC-005 (sin cambios de archivos): con `./mvnw spring-boot:run` en otra terminal, insertar una fila de prueba en la base de dev (`docker compose exec postgres psql -U mangateca -d mangateca`, p. ej. una tabla temporal `smoke(id int)`) y anotar `SELECT count(*) FROM flyway_schema_history`. Ejecutar `./mvnw verify` dos veces: ambos `BUILD SUCCESS` con el mismo número de tests. La base de dev queda intacta (mismas filas) y, al terminar, `docker ps` no muestra contenedores de Testcontainers (Ryuk los descarta, US5-2). Después, borrar la tabla de prueba. **Resultado (2026-11-05, validación manual de spring-test con la app de dev corriendo en 8080)**: `flyway_schema_history` = 1 y `smoke` = 1 fila, idénticos antes y después; dos `./mvnw verify` seguidos con BUILD SUCCESS y 70 tests cada uno; sin conflicto de puertos entre 8080 y RANDOM_PORT; `docker ps` tras 15 s mostraba solo el contenedor de Compose (ningún contenedor de Testcontainers, ni ryuk); tabla `smoke` borrada y app detenida con SIGTERM. Evidencia basada en el informe del agente y en logs temporales, no versionados.

**Checkpoint**: US5 verificada.

---

## Phase 8: User Story 6 - Límites modulares verificados (Priority: P2)

**Goal**: `ApplicationModules.verify()` rompe el build ante accesos a tipos internos o ciclos
entre módulos (FR-012, SC-002).

**Independent Test**: introducir de forma temporal un acceso indebido y comprobar que el build
falla; retirarlo y comprobar que pasa.

### Tests for User Story 6 ⚠️

- [X] T049 [P] [US6] Crear `src/test/java/com/mangateca/api/ModularityTests.java` (sin contexto Spring): `ApplicationModules.of(ApiApplication.class)`; test `verifiesModularStructure()` → `modules.verify()`; test que comprueba que existe el módulo `platform`. Sin `Documenter` (YAGNI). **Nota (2026-11-05)**: se descartaron los fixtures de violación/ciclo en `src/test`, porque `ApplicationModules.of` no importa clases de test (`No classes found in packages`); SC-002 se demuestra con T051.
- ~~T050 [P] [US6] `PlatformModuleTests` con `@ApplicationModuleTest`.~~ **Retirada (2026-11-05)**: opcional según la constitución v2.0.0; el arranque del contexto ya lo cubre `ApiApplicationTests`.

### Implementation for User Story 6

- [X] T051 [US6] ⚠️ TEMPORAL, NO COMMITEAR. Ejecutar quickstart §4a: crear `src/main/java/com/mangateca/api/demo/Demo.java` que use `com.mangateca.api.platform.security.CorsProperties`, y `./mvnw verify` → `ModularityTests` falla indicando el módulo origen `demo`, el destino `platform` y el tipo accedido. Revertir con `git checkout -- . && git clean -fd` y confirmar que `./mvnw verify` vuelve a pasar. (El caso del ciclo se omite: no es vital.)

**Checkpoint**: US6 verificada.

---

## Phase 9: User Story 7 - Puerta de calidad (Priority: P2)

**Goal**: el build falla si hay cualquier advertencia de compilación (FR-014, SC-002). Sin umbral
de cobertura (FR-013 retirado, constitución v2.0.0).

**Independent Test**: introducir una advertencia de compilación; el build falla.

### Implementation for User Story 7

- ~~T052 [US7] Añadir `jacoco-maven-plugin` 0.8.16 con regla `LINE COVEREDRATIO` ≥ 0.80.~~ **Retirada (2026-11-05)**: la constitución v2.0.0 elimina el umbral de cobertura obligatorio. No se añade JaCoCo.
- [X] T053 [US7] ⚠️ TEMPORAL, NO COMMITEAR. Ejecutar quickstart §4c: añadir en una clase de `src/main` un raw type (`List l = new ArrayList();`) y `./mvnw compile` → falla mostrando la advertencia y `warnings found and -Werror specified` (US7-1). Revertir con `git checkout -- . && git clean -fd` y confirmar que `./mvnw verify` pasa. Si no falla, corregir T010. (La parte de cobertura §4b se retiró el 2026-11-05.)

**Checkpoint**: US7 verificada.

---

## Phase 10: User Story 8 - Observabilidad y contrato (Priority: P2)

**Goal**: health e info públicos y mínimos, 503 cuando la base cae, y el contrato OpenAPI 3
navegable (FR-024…FR-026).

**Independent Test**: invocar sin credenciales el endpoint de salud y la documentación del
contrato y verificar su contenido.

### Tests for User Story 8 ⚠️ (escribir primero; deben fallar)

- [X] T054 [US8] Crear `src/test/java/com/mangateca/api/actuator/ActuatorAndDocsIT.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient`, imports estándar), un solo IT con tres tests y aserciones mínimas, todos sin token: (1) `GET /actuator/health` → 200, `status` `UP`, `components.db.status` `UP` y ningún componente con la clave `details`; (2) `GET /actuator/info` → 200 con `build.name` y `build.version`, y sin las claves `java`, `os` ni `env` (requiere el `build-info` de T059); (3) `GET /v3/api-docs` → 200 con `openapi` que empieza por `3.`, `components.securitySchemes.bearerAuth` (`http`/`bearer`/`JWT`) y `paths` sin `/error`. Fusiona las antiguas T054, T055 y T057.
- ~~T055 [P] [US8] `InfoEndpointIT`.~~ **Fusionada en T054 (2026-11-05).**
- ~~T056 [P] [US8] `HealthDatabaseDownIT` con contenedor propio detenido → 503.~~ **Retirada (2026-11-05)**: test caro y no vital; US8-2 se valida a mano con quickstart §4(g) en T065.
- ~~T057 [P] [US8] `OpenApiDocsIT`.~~ **Fusionada en T054 (2026-11-05).**

### Implementation for User Story 8

- [X] T058 [US8] Modificar `src/main/resources/application.yaml` añadiendo `management.endpoint.health.show-details: never`, `management.endpoint.health.show-components: always` y la desactivación de las contribuciones de info `env`, `java`, `os` y `git` (`management.info.<x>.enabled: false`; comprobar los nombres de las propiedades en Boot 4.1 con Context7). `management.endpoints.web.exposure.include: health,info` ya está desde T016.
- [X] T059 [US8] Modificar `pom.xml`: añadir al `spring-boot-maven-plugin` la ejecución con el goal `build-info` (genera `META-INF/build-info.properties` con nombre y versión, research R12), conservando la configuración de perfiles de T024.
- [X] T060 [P] [US8] Crear `src/main/java/com/mangateca/api/platform/web/OpenApiConfiguration.java` (`@Configuration(proxyBeanMethods = false)`): bean `OpenAPI` (springdoc 3.2.0) con `info` (título `Mangateca API`, versión de la app) y `components.securitySchemes.bearerAuth` = `SecurityScheme` `type HTTP`, `scheme "bearer"`, `bearerFormat "JWT"`, más un `SecurityRequirement` global `bearerAuth`. Ejecutar `./mvnw verify`: T054 pasa.

**Checkpoint**: US8 verificada.

---

## Phase 11: User Story 9 - Decisiones tecnológicas documentadas (Priority: P3)

**Goal**: los cinco ADR existen, están aceptados y reflejan lo verificado durante la
implementación (FR-001, FR-002, SC-007).

**Independent Test**: revisar `docs/adr/` y comprobar que hay cinco ADR con todas las
secciones obligatorias.

### Implementation for User Story 9

- [X] T061 [US9] Actualizar `docs/adr/ADR-002-mapstruct.md` y `docs/adr/ADR-005-quality-gate.md` con los resultados reales: compatibilidad verificada de MapStruct 1.6.3 con `--release 25` (T010) y el mensaje de fallo de `-Xlint` obtenido en T053. Si algún ADR cambió de decisión, actualizar "Decisión" y "Consecuencias". (ADR-005 ya registra la retirada de JaCoCo, 2026-11-05.)
- [X] T062 [US9] Revisar `docs/adr/ADR-001-spring-modulith.md` … `docs/adr/ADR-005-quality-gate.md` contra la lista de data-model.md §ADR: nombre `ADR-NNN-<slug>.md`, `Estado` `Aceptado`, fecha ISO, y las 7 secciones (Título/Estado/Fecha, Contexto y problema, Alternativas con la opción nativa, Decisión con artefacto y versión exactos que coinciden con `pom.xml`, Compatibilidad Java 25 / Spring Boot 4.1.x, Mantenimiento con última release, cadencia y licencia, Consecuencias). Corregir lo que falte.

**Checkpoint**: US9 verificada (SC-007).

---

## Phase 12: Polish & Cross-Cutting Concerns

**Purpose**: documentación final, higiene y validación de punta a punta.

- [X] T063 Completar `README.md`: sección "Configuración por entorno" con la tabla de variables obligatorias y opcionales de `contracts/configuration.md` (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`, `MANGATECA_CORS_ALLOWED_ORIGINS`) y qué ocurre si faltan; sección "Pruebas" (`./mvnw verify`, requiere Docker; solo tests vitales, sin umbral de cobertura); enlace a `docs/adr/`.
- [X] T064 [P] Revisión de secretos y configuración (FR-023): buscar en el repositorio (`git grep -nE "BEGIN (RSA |)PRIVATE KEY|BEGIN PUBLIC KEY|password:|secret"`) y confirmar que no hay claves ni contraseñas versionadas; asegurar que `.gitignore` incluye `.env`, `*.pem` y `*.key`. Confirmar que ningún log de producción registra tokens, contraseñas ni PII.
- [X] T065 Validación de punta a punta según `specs/001-platform-foundation/quickstart.md` §1, §2, §3 y §5, y las filas (c), (d), (e), (f) y (g) de §4 (⚠️ los cambios de §4 son TEMPORALES: revertirlos y limpiar con `docker compose down -v`). Registrar cualquier discrepancia y corregirla en la tarea correspondiente, o actualizar quickstart.md si la guía estaba mal.
- [X] T066 Revisión con el agente `spec-reviewer` del código frente a `spec.md`, `plan.md` y `.specify/memory/constitution.md` (FR-001…FR-026, SC-001…SC-007). Corregir los hallazgos y volver a ejecutar `./mvnw verify`.
- [X] T067 Actualizar `specs/001-platform-foundation/plan.md` (*Riesgos* y *Complexity Tracking*) y, si cambió el DDL o alguna propiedad, `data-model.md` y `contracts/configuration.md`, para que reflejen las desviaciones reales encontradas (p. ej. la forma YAML de `ignore-migration-patterns`, la exclusión `-processing` o el comportamiento del entry point 401).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias. T001–T005 en paralelo; T006→T007→T008→T009→T010→T011 en
  secuencia (mismo `pom.xml`, cada uno después de su ADR).
- **Foundational (Phase 2)**: depende de T011. **Bloquea** todas las historias.
- **US1, US2, US3, US5, US7**: solo dependen de Foundational.
- **US4**: T042 (slice), T045 y T046 solo dependen de Foundational; T043 (error dispatch real)
  depende de T039 (US3), por el `permitAll` del error dispatch y los tokens reales.
- **US6**: T049 solo depende de Foundational.
- **US8**: depende de US3 (los tests asumen el `SecurityFilterChain` con las rutas públicas; sin
  él, la seguridad por defecto de Boot devuelve 401).
- **US9**: depende de T010 y T053 (registra sus resultados).
- **Polish (Phase 12)**: depende de todas las historias.

### Archivos compartidos (no paralelizar entre historias)

- `pom.xml`: T006–T011, T024 y T059.
- `GlobalExceptionHandler.java`: T045 y T046 (este último extrae los textos de `detail`).
- `src/main/resources/application.yaml`: T016, T029 y T058.
- `src/main/resources/application-dev.yaml`: T023 y T041.
- `README.md`: T025 y T063.

### User Story Dependencies

```text
Setup ─► Foundational ─┬─► US1 (P1) 🎯 MVP
                       ├─► US2 (P1)
                       ├─► US3 (P1) ─┬─► US8 (P2)
                       │             └─► US4: T043 (error dispatch real, tokens reales)
                       ├─► US4 (P1)
                       ├─► US6 (P2)
                       ├─► US5 (P2)
                       ├─► US7 (P2) ───► US9 (P3)
                       └──────────────────► Polish
```

### Within Each User Story

- Tests vitales primero (recomendado, no obligatorio).
- Records y escritores compartidos → handlers → configuración (`SecurityConfiguration`).
- Configuración YAML/pom de la historia al final, para cerrar los tests.
- Commit al terminar cada tarea (salvo las ⚠️ TEMPORALES).

### Parallel Opportunities

- Phase 1: T001–T005 (cinco ADR en archivos distintos).
- Phase 2: T012–T015, T017, T018, T019 y T021.
- Con Foundational terminado: US1, US2, US3, US4, US5 y US7 pueden avanzar en paralelo,
  respetando los archivos compartidos.
- Dentro de US3: T030, T031 y T032 (tests); T034 y T035; T036, T037 y T038 tras T035; T040.
- Dentro de US8: T054 (test) y T060.

---

## Parallel Example: User Story 3

```bash
# Tests de US3 (en paralelo, deben fallar):
Task: "CorsPropertiesTests en src/test/java/com/mangateca/api/platform/security/CorsPropertiesTests.java"
Task: "SecurityIT en src/test/java/com/mangateca/api/platform/security/SecurityIT.java"
Task: "CorsIT en src/test/java/com/mangateca/api/platform/security/CorsIT.java"

# Piezas base en paralelo:
Task: "CorsProperties en src/main/java/com/mangateca/api/platform/security/CorsProperties.java"
Task: "ProblemDetailsResponseWriter en src/main/java/com/mangateca/api/platform/security/ProblemDetailsResponseWriter.java"
Task: "DevJwtDecoderConfiguration en src/main/java/com/mangateca/api/platform/security/DevJwtDecoderConfiguration.java"

# Tras el writer, los tres handlers en paralelo:
Task: "ProblemDetailsAuthenticationEntryPoint"
Task: "ProblemDetailsAccessDeniedHandler"
Task: "ProblemDetailsCorsProcessor"
```

## Parallel Example: User Story 8

```bash
Task: "ActuatorAndDocsIT en src/test/java/com/mangateca/api/actuator/ActuatorAndDocsIT.java"
Task: "OpenApiConfiguration en src/main/java/com/mangateca/api/platform/web/OpenApiConfiguration.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: ADRs y toolchain (compila sin advertencias).
2. Phase 2: Foundational.
3. Phase 3: US1.
4. **STOP and VALIDATE**: quickstart §1 (arranque con un comando, sin PostgreSQL instalado).

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 → arranque local (MVP).
3. US2 → esquema blindado (SC-006).
4. US3 + US4 → seguridad y errores (SC-003, SC-004). Con esto el bloque P1 está completo.
5. US5, US6 y US7 → gobierno automático (SC-002, SC-005), sin umbral de cobertura.
6. US8 → observabilidad y contrato.
7. US9 → ADR actualizados con lo verificado (SC-007).
8. Polish → quickstart completo y revisión con `spec-reviewer`.

### Agentes sugeridos en `/speckit-implement`

- `spring-dev`: tareas de producción, configuración y `pom.xml`.
- `spring-test`: tareas de tests (T018–T021, T027, T028, T030, T031, T032, T042, T043, T047, T049
  y T054) y fallos de build por tests.
- `spec-reviewer`: al cerrar cada fase y en T066.

---

## Notes

- [P] = archivos distintos y sin dependencias pendientes.
- [USx] da trazabilidad de cada tarea a su historia de usuario en spec.md.
- Las tareas ⚠️ TEMPORAL demuestran SC-002 y **nunca** se commitean.
- Si una tarea descubre que un documento de diseño estaba equivocado, se corrige el documento
  (T067), no se reinterpreta la constitución.

---

## Phase 13: Convergence

- [X] T068 ⚠️ TEMPORAL, NO COMMITEAR. Demostrar el ciclo entre módulos: crear `src/main/java/com/mangateca/api/demoa/A.java` y `src/main/java/com/mangateca/api/demob/B.java` que se referencien mutuamente (públicos, en el paquete raíz de cada módulo), ejecutar `./mvnw verify` y comprobar que `ModularityTests` falla identificando el ciclo `demoa` ↔ `demob`. Revertir borrando solo esos dos directorios (no usar `git checkout -- .`: revertiría cambios ajenos como `.claude/settings.json`) y confirmar que `./mvnw verify` vuelve a pasar. Registrar el resultado en ADR-005 o `plan.md` per US6/AC3 y SC-002 (partial)
- [X] T069 Limitar `/actuator/info` a `build.name` y `build.version` (hoy incluye también `artifact`, `group` y `time`), sin exponer propiedades internas; ajustar `ActuatorAndDocsIT` para comprobar que esas claves no aparecen, y comprobar los nombres de propiedades con Context7 antes de escribir la configuración. per Assumptions (endpoint de información) y `contracts/http-surface.md` (partial)
- [X] T070 [P] Corregir las etiquetas FR de `@DisplayName` en `SecurityIT.java` (líneas 107, 121 y 130), `CorsIT.java`, `CorsPropertiesTests.java` (CORS pertenece a FR-022; 401/403 a FR-020) y `ActuatorAndDocsIT.java` (health = FR-025, solo health/info expuestos = FR-024), sin cambiar el comportamiento de los tests. per FR-020, FR-022, FR-024, FR-025 (partial)
