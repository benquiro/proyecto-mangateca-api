# Research: Platform Foundation, Persistence Baseline & Architectural Governance

**Feature**: `001-platform-foundation` | **Fecha**: 2026-11-05

Fuentes: documentación oficial vía Context7 (Spring Boot 4.1.0, Spring Modulith 2.1.0),
metadatos de Maven Central consultados el 2026-11-05 y el DDL incluido en el jar
`spring-modulith-events-jdbc-2.1.2`. Solo se eligen versiones GA (sin RC, M ni Beta), según el
Principio III.

## R1. Verificación modular y registro de eventos: Spring Modulith

- **Decision**: Spring Modulith **2.1.2** importado vía `spring-modulith-bom`, con
  `spring-modulith-starter-jdbc` (compile) y `spring-modulith-starter-test` (test).
- **Rationale**: 2.1.2 está compilado contra Spring Boot 4.1.2, la misma versión del parent
  del `pom.xml`. Aporta `ApplicationModules.verify()`, `@ApplicationModuleTest` y un registro de
  publicación de eventos transaccional sobre JDBC, sin depender de JPA para los eventos.
- **Alternatives considered**:
  - *ArchUnit puro* (sin dependencia de Modulith): obliga a escribir a mano las reglas de
    módulos y no incluye registro de eventos ni tests por módulo.
  - *Solo `ApplicationEventPublisher` nativo*: los eventos se pierden si el listener falla tras
    el commit, porque no hay registro persistente.
  - *`spring-modulith-starter-jpa`*: también serviría, pero JDBC evita una entidad JPA interna
    adicional y su DDL lo publica el propio proyecto.

## R2. Esquema `event_publication`: propiedad de Flyway

- **Decision**: V1 crea la tabla `event_publication` y sus dos índices copiando el DDL
  **v2** oficial de `schemas/v2/schema-postgresql.sql` (ver `data-model.md`).
  Se fija `spring.modulith.events.jdbc.schema-initialization.enabled=false`.
- **Rationale**: el Principio IV exige que todo cambio de esquema pase por Flyway. Si Modulith
  creara la tabla por su cuenta, el esquema dependería de la librería y no de una migración
  versionada.
- **Alternatives considered**: dejar la inicialización automática de Modulith (descartado:
  viola el Principio IV) o usar la estructura legacy v1 (descartado: Modulith 2.x usa v2 por
  defecto, con `use-legacy-structure=false`).

## R3. Extensiones de PostgreSQL en V1

- **Decision**: V1 **no** crea extensiones.
- **Rationale**: el DDL del registro usa `UUID`, `TEXT`, `TIMESTAMPTZ` e índices `hash`, todos
  nativos de PostgreSQL. `gen_random_uuid()` es nativo desde PG 13. Crear `pgcrypto` o
  `citext` "por si acaso" violaría el Principio VII (YAGNI). La primera feature que necesite
  una extensión la añade en su propia migración.
- **Alternatives considered**: `CREATE EXTENSION citext`, pensando en emails (descartado: lo
  decidirá el módulo de identidad).

## R4. Motor e imagen de base de datos

- **Decision**: imagen `postgres:18` fijada **idéntica** en `compose.yaml` y en Testcontainers.
  Se sustituye el `postgres:latest` actual de `TestcontainersConfiguration`.
- **Rationale**: con `latest`, la versión cambia sin control entre ejecuciones y entornos,
  justo lo que la US5 quiere evitar.
- **Alternatives considered**: variante `-alpine` (descartada: usa otra libc y puede diferir en
  collations respecto de la imagen de producción).

## R5. Flyway: fallo ante discrepancias

- **Decision**:
  - `spring.flyway.validate-on-migrate=true` (por defecto).
  - `spring.flyway.ignore-migration-patterns` vacío, para que una migración aplicada que no
    existe localmente (*future*) también haga fallar el arranque.
  - `spring.flyway.clean-disabled=true`.
  - `spring.jpa.hibernate.ddl-auto=validate` y `spring.jpa.open-in-view=false`.
- **Rationale**: por defecto Flyway ignora las migraciones *future* (`*:future`), lo que deja
  sin cubrir la US2-3. La comprobación de checksum detecta migraciones modificadas.
  `ddl-auto=validate` detecta entidades sin tabla o columna.
- **Alternatives considered**: `baselineOnMigrate` (descartado: oculta discrepancias).
- **Verificación**: en la implementación se confirma cómo se expresa en YAML el valor vacío de
  `ignore-migration-patterns` (`""` o `[]`) con un test que inserta una fila *future* en
  `flyway_schema_history`.

## R6. Base de datos local: Docker Compose de Spring Boot

- **Decision**: añadir `spring-boot-docker-compose` (`optional`) y un `compose.yaml` en la raíz
  con un servicio `postgres`. Se usa el `lifecycle-management` por defecto (`start-and-stop`).
  El `spring-boot-maven-plugin` activa el perfil `dev` en `spring-boot:run`, así que el único
  comando es `./mvnw spring-boot:run`.
- **Rationale**: Boot ejecuta `docker compose up` al arrancar, crea la `ServiceConnection` sin
  configurar URL ni credenciales, y ejecuta `docker compose stop` al apagar (US1-2). Por defecto
  se omite en tests y no se incluye en el jar reempaquetado.
- **Alternatives considered**:
  - *Instalación manual de PostgreSQL*: viola la US1.
  - *`TestApiApplication` con Testcontainers como modo de desarrollo*: la base se pierde en cada
    reinicio y no sirve para trabajar con datos persistentes. Se conserva solo como utilidad.
  - *Script `docker compose up` aparte*: son dos comandos y el ciclo de vida queda manual.

## R7. Base de datos de pruebas

- **Decision**: Testcontainers 2.x (ya presente) con un bean `@ServiceConnection`
  `PostgreSQLContainer` en `TestcontainersConfiguration`. El contenedor se comparte entre
  contextos cacheados y se descarta al terminar la JVM (Ryuk).
- **Rationale**: es una instancia nueva por ejecución e independiente del contenedor de Compose
  (otro nombre y puerto aleatorio), así que cumple la US5 sin colisiones.
- **Nota**: en esta feature no hay repositorios, por lo que no hay `@DataJpaTest`. Se
  incorporan con el primer módulo de negocio.

## R8. Autenticación: Resource Server con JWT

- **Decision**: añadir `spring-boot-starter-security-oauth2-resource-server` (test:
  `spring-security-test`, ya incluido en `spring-boot-starter-security-test`).
- **Rationale**: en Boot 4.1.2, `spring-boot-starter-oauth2-resource-server` está
  **deprecado** en favor de este starter, y el Principio III prohíbe APIs deprecadas.
- **Claves**:
  - Entornos no-dev: `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION` (clave
    pública RSA). Si falta, no existe un `JwtDecoder` y el arranque falla (fail-fast).
  - Perfil `dev`: un `JwtDecoder` construido con un par RSA efímero generado en memoria al
    arrancar. No hay claves en el repositorio y los tokens de dev se vuelven inválidos en cada
    reinicio, lo que es aceptable porque la emisión está fuera de alcance.
  - Tests: `TestSecurityConfiguration` genera un par RSA efímero y expone `JwtDecoder` y
    `NimbusJwtEncoder` para firmar tokens con o sin roles, y también expirados o con otra
    clave.
- **Alternatives considered**: librerías JWT de terceros o un filtro manual (prohibidos por el
  Principio V); HMAC con secreto compartido (descartado: obligaría a distribuir un secreto a
  cada verificador); clave pública de dev versionada (descartado: sin su privada no aporta
  nada y añade un archivo).
- **CSRF**: desactivado en la `SecurityFilterChain`. La credencial viaja solo en la cabecera
  `Authorization`, que el navegador no adjunta automáticamente en una petición de otro sitio, así
  que no hay superficie CSRF. Con CSRF activo, un `POST` sin credenciales recibiría 403 (fallo de
  CSRF) en lugar de 401, lo que incumple SC-004 y FR-020.
  **Deuda registrada** (ADR-003): `allowCredentials=true` (R11) prepara la cookie del refresh
  token. El módulo identity MUST reevaluar la protección CSRF de ese endpoint (p. ej.
  `SameSite=Strict` y CSRF limitado a esa ruta) antes de emitir cookies.

## R9. Errores con Problem Details (RFC 9457)

- **Decision**:
  - `GlobalExceptionHandler`, un `@RestControllerAdvice` que **extiende**
    `ResponseEntityExceptionHandler`. Cubre 400 (`HttpMessageNotReadableException`,
    `MethodArgumentNotValidException`, `HandlerMethodValidationException`), 404
    (`NoResourceFoundException`), 405 y 415, y añade un `@ExceptionHandler(Exception.class)`
    para el 500 genérico con log del detalle.
  - La validación añade la propiedad de extensión `errors: [{field, message}]`.
  - Se usa `type = about:blank` y `title` = frase HTTP estándar.
  - `spring.web.error.include-stacktrace/message/binding-errors = never`. `server.error.*` está deprecado (level error) desde Boot 4.0.0 y se reemplaza por `spring.web.error.*`; comprobado en el spring-configuration-metadata.json de Boot 4.1.2.
- **Rationale**: los handlers de Spring MVC 7 ya traducen las excepciones estándar a
  `ProblemDetail`, así que solo se personalizan la validación y el 500. Al existir un
  `ResponseEntityExceptionHandler` propio, el handler de Boot se desactiva y no hay
  duplicidad.
- **Alternatives considered**: `spring.mvc.problemdetails.enabled=true` sin advice propio
  (descartado: no permite el 500 genérico ni la extensión `errors`); URIs `type` propias por
  error (descartado por YAGNI, se podrán añadir por módulo).
- **Errores fuera de MVC** (FR-015: "Toda respuesta de error"): una excepción lanzada en un
  filtro, o cualquier `sendError` del contenedor, termina en un *error dispatch* a `/error`, que el
  advice no ve. Lo atendería `BasicErrorController`, que responde con su propio JSON
  (`timestamp`, `path`…), no con Problem Details.
  - **Decision**: `ProblemDetailsErrorController`, un `@RestController` concreto que implementa
    `ErrorController` de Boot (al existir, Boot no registra `BasicErrorController`). Responde
    Problem Details a partir de `jakarta.servlet.error.status_code` y
    `jakarta.servlet.error.request_uri`, sin usar nunca el mensaje ni la excepción en la
    respuesta. Los textos de `detail` se comparten con `GlobalExceptionHandler`. La
    `SecurityFilterChain` permite el `DispatcherType.ERROR` (la petición original ya pasó la
    autorización); sin eso, el error se convertiría en un 401.
  - **Alternatives considered**: un `ErrorAttributes` propio (descartado: sigue sin producir
    `application/problem+json` ni la forma de RFC 9457); acotar FR-015 a los errores de MVC
    (descartado: deja respuestas no estándar al cliente SPA).

## R10. 401/403 en Problem Details

- **Decision**: `ProblemDetailsAuthenticationEntryPoint` (fija la cabecera literal
  `WWW-Authenticate: Bearer` y escribe el cuerpo) y `ProblemDetailsAccessDeniedHandler`. Ambos
  escriben a través de un `ProblemDetailsResponseWriter` común que usa el `JsonMapper`
  (Jackson 3) del contexto.
- **No se delega en `BearerTokenAuthenticationEntryPoint`**: ante un token inválido añade
  `error="invalid_token", error_description="…"` con la causa (p. ej. `Jwt expired at …`), lo que
  incumple el edge case de la spec "sin indicar cuál de las comprobaciones falló". El valor fijo
  `Bearer` cumple RFC 6750 §3, donde el atributo `error` es opcional, y coincide con
  contracts/http-surface.md §2.
- **Rationale**: estos errores ocurren en la cadena de filtros, antes de llegar al
  `DispatcherServlet`, por lo que el advice no los ve.
- **Detalle**: `anyRequest().authenticated()` hace que una ruta inexistente sin credenciales
  devuelva 401 (edge case de la spec). El cuerpo del 401 es genérico y no indica si el token
  expiró o tiene una firma inválida.
- **Nota (2026-11-05)**: en Spring 7 `ProblemDetail.type` es `null` por defecto y se omite al
  serializar (antes valía `about:blank`). Por eso todo `ProblemDetail` del proyecto (el
  `ProblemDetailsResponseWriter` y los errores de MVC) fija `type = about:blank` de forma
  explícita; el `ProblemDetailJacksonMixin` usa `NON_EMPTY`, así que un type fijado sí sale.
- **Nota (2026-11-05)**: el `Content-Type` es `application/problem+json` sin el parámetro
  `charset` (coherente con MVC; RFC 8259 §11 y RFC 9457 no lo definen). El cuerpo son bytes UTF-8.

## R11. CORS

- **Decision**:
  - `CorsProperties` es un `record` `@ConfigurationProperties("mangateca.cors")` con
    `allowedOrigins` (por defecto lista vacía). Su constructor compacto rechaza `*` y los
    orígenes que no sean `scheme://host[:port]`, lo que hace fallar el arranque.
  - Un bean `CorsFilter` llamado `corsFilter` usa `ProblemDetailsCorsProcessor`, que extiende
    `DefaultCorsProcessor`. Sobrescribe `rejectRequest` para que no escriba nada y
    `processRequest` para que, si `super` devuelve `false`, escriba el 403 Problem Details con
    `ProblemDetailsResponseWriter` y los objetos servlet que ya recibe el método (sin estado en
    el hilo ni casts). `http.cors()` recoge el bean, y su registro automático como filtro de
    servlet se desactiva para que no se ejecute dos veces.
  - Configuración: `allowCredentials=true` (cookies del refresh token futuro), métodos
    `GET,POST,PUT,PATCH,DELETE` y cabeceras `Authorization, Content-Type`.
- **Rationale**: sin orígenes configurados no se autoriza ninguno (falla cerrado). El
  procesador por defecto responde `403 Invalid CORS request` en texto plano, lo que rompería
  SC-003 / FR-015.
- **Alternatives considered**: `http.cors(c -> c.configurationSource(...))` estándar
  (descartado: no permite cambiar el cuerpo del rechazo) o aceptar texto plano en CORS
  (descartado: incumple FR-015).

## R12. Actuator, OpenAPI y logging

- **Decision**:
  - **Actuator**: `management.endpoints.web.exposure.include=health,info`,
    `endpoint.health.show-components=always`, `show-details=never`. El `db` health indicator
    viene por defecto con DataSource, y DOWN devuelve 503.
  - **Info**: el goal `build-info` del `spring-boot-maven-plugin` aporta nombre y versión. Las
    contribuciones `env`, `java`, `os` y `git` quedan desactivadas.
  - **OpenAPI**: `springdoc-openapi-starter-webmvc-ui` 3.2.0 (ya presente). Rutas públicas
    `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html` y `/swagger-ui/**`. Se declara el
    esquema de seguridad `bearerAuth` en un bean `OpenAPI`.
  - **Logging**: `logging.structured.format.console=ecs` en todos los perfiles (Principio
    "Higiene Operacional").
- **Rationale**: con `show-components` sin detalles, el estado de la app y de la base es
  visible sin exponer hosts ni versiones (supuesto de la spec).

## R13. Mapeo DTO: MapStruct

- **Decision**: MapStruct **1.6.3** (`mapstruct` compile, `mapstruct-processor` en
  `annotationProcessorPaths`) junto a `lombok-mapstruct-binding` **0.2.0**. Orden de procesadores:
  Lombok, binding y MapStruct.
- **Rationale**: 1.6.3 es la última GA (la 1.7 está en Beta). El Principio IV lo exige y la
  feature lo deja integrado en el toolchain, verificado con `-Werror`. No se crea ningún
  mapper hasta que exista un DTO real (Principio VII).
- **Alternatives considered**: mapeo manual en records (descartado: el Principio IV exige
  MapStruct) o ModelMapper (descartado: usa reflexión en runtime y no detecta errores en
  compilación).
- **Riesgo**: compatibilidad de MapStruct 1.6.3 con `--release 25`. Mitigación: la build con
  `-Werror` lo detecta en la primera tarea y, si falla, se reabre ADR-002.

## R14. Puerta de calidad: JaCoCo y `-Xlint:all -Werror`

- **Decision**:
  - `jacoco-maven-plugin` **0.8.16**, con los goals `prepare-agent`, `report` y `check`
    (fase `verify`). Regla `BUNDLE`, `LINE COVEREDRATIO >= 0.80`, aplicada a
    `com/mangateca/api/**/domain/**` y `com/mangateca/api/**/application/**`.
  - `maven-compiler-plugin`: `<compilerArgs>-Xlint:all</compilerArgs>` y
    `<failOnWarning>true</failOnWarning>`, en `compile` y en `testCompile`.
- **Rationale**: el alcance lo fija la constitución (dominio y aplicación). La plataforma se
  prueba igualmente con slice tests e integración. 0.8.16 es la última GA y soporta el bytecode
  de Java 25.
- **Consecuencia conocida**: en esta feature no hay clases de dominio ni de aplicación, así que
  la regla se evalúa sobre un conjunto vacío. Su eficacia se demuestra con el procedimiento
  temporal de `quickstart.md` (US7-1), y la primera feature de negocio la ejercita de verdad.
- **Riesgo**: `-Xlint:processing` puede emitir "No processor claimed any of these annotations"
  con Lombok o MapStruct. Mitigación: verificarlo en la primera build. Si aparece, solo se
  puede excluir esa categoría (`-Xlint:all,-processing`) mediante una enmienda justificada en
  ADR-005 y en *Complexity Tracking*. Nunca en silencio.

## R15. JSpecify

- **Decision**: usar `org.jspecify` vía la dependencia transitiva de `spring-core` 7, sin
  declararla en el `pom.xml`. Cada paquete lleva un `package-info.java` con `@NullMarked`.
- **Rationale**: Spring Framework 7 publica su API anotada con JSpecify y lo trae como
  dependencia de compilación. Declararlo explícitamente añadiría una dependencia sin
  beneficio (Principio VII).

## R16. Estructura de módulos

- **Decision**: un único módulo de aplicación `platform` (`com.mangateca.api.platform`), con
  los subpaquetes internos `web` y `security`. Los módulos de negocio (`catalog`, `user`, …) no
  se crean todavía.
- **Rationale**: Modulith detecta los subpaquetes directos del paquete base. Crear paquetes de
  negocio vacíos sería diseño especulativo (Principio VII). La infraestructura transversal
  vive en su propio módulo vertical y no en capas globales.
- **Alternatives considered**: dejar la configuración en el paquete raíz (descartado: el
  paquete raíz no es un módulo y lo vería todo el sistema).
