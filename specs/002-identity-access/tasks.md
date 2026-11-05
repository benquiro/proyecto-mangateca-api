---
description: "Task list for 002-identity-access"
---

# Tasks: Identity & Access

**Input**: Design documents from `/specs/002-identity-access/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml),
[contracts/configuration.md](contracts/configuration.md), [quickstart.md](quickstart.md)

**Constitución**: v4.0.0

**Tests** (Principio VI): solo los obligatorios y un test por cada escenario Given-When-Then de
`spec.md`. Ya existen desde la 001 y no se regeneran: `ModularityTests`
(`ApplicationModules.verify()`) y `ApiApplicationTests` (smoke de arranque); se ejecutan en cada
checkpoint. El tiempo se controla con `Clock` (fijo o avanzado a mano en el test), nunca con
`sleep`. Mocks solo de repositorios, `Clock` y, en `@WebMvcTest`, la capa de aplicación.

**Organization**: una fase por historia de usuario, en orden de prioridad. Dentro de cada
historia, las tareas `[TEST]` van antes que las de implementación (test-first: fallan o no
compilan hasta que se implementa).

**Errores**: un único `@RestControllerAdvice`, el `GlobalExceptionHandler` de 001 (Constitución
IV). Las excepciones de `identity` extienden `ErrorResponseException` con `type` URN
(research R10).

## Format: `[ID] [P?] [Story] [Tipo] Descripción — ruta — requisito o escenario`

- **[P]**: puede ir en paralelo; no comparte archivos con otra `[P]` de la misma fase. Nunca en
  tareas que tocan `pom.xml`, `application*.yaml`, `V2__create_identity.sql` o la configuración
  del `SecurityFilterChain`.
- **[USn]**: historia de `spec.md`.
- **Tipo**: `[TEST]`, `[CODE]`, `[CONFIG]`, `[MIGRATION]` o `[DOC]`.

## Path Conventions

Proyecto Maven único. Producción en `src/main/java/com/mangateca/api/`, tests en
`src/test/java/com/mangateca/api/`. Todo paquete nuevo lleva `package-info.java` con
`@NullMarked`, creado en la primera tarea que añade una clase a ese paquete.

---

## Phase 1: Setup

**Purpose**: decisión de arquitectura documentada y activación del perfil `test`. El plan no
requiere dependencias nuevas; el único ajuste de `pom.xml` (T003) está justificado en ADR-006.

- [X] T001 [DOC] Escribir ADR-006 en `docs/adr/ADR-006-token-issuance.md` con las secciones obligatorias de la 001 (Estado `Aceptado`, Fecha, Contexto, Alternativas, Decisión, Compatibilidad, Mantenimiento, Consecuencias). Decisión:
  - Emisión propia de JWT RS256 con `NimbusJwtEncoder` y claims `sub`, `roles`, `iss`, `iat` y `exp` de 15 min.
  - Un único par RSA en `identity` alimenta `JwtEncoder` y `JwtDecoder`, con validador de `iss`.
  - Claves efímeras en memoria en los perfiles `dev`/`test` cuando no hay claves configuradas; PEM por variables de entorno (`MANGATECA_IDENTITY_JWT_*_LOCATION`) en producción; fallo de arranque en cualquier otro caso.
  - Refresh token opaco (256 bits, SHA-256 en BD) en cookie `HttpOnly; Secure; SameSite; Path=/api/v1/auth`.
  - Activación del perfil `test` desde Surefire.

  Alternativas descartadas: Spring Authorization Server, IdP externo, HS256, claves versionadas, PEM inline. Condición de revisión: segunda aplicación cliente, login social o MFA (research R1, R3, R4, R7, R8). Cubre FR-007, FR-008, FR-015, FR-021 e Higiene Operacional.
- [X] T002 [P] [DOC] Marcar ADR-003 en `docs/adr/ADR-003-oauth2-resource-server.md` con el Estado `Reemplazado parcialmente por ADR-006 (Superseded in part by ADR-006)`. El vocabulario de estados de la 001 está en castellano y se conserva el texto pedido entre paréntesis. Añadir una sección «Reemplazo parcial» que liste:
  - Lo reemplazado: el origen de la clave pública vía `spring.security.oauth2.resourceserver.jwt.public-key-location`, `DevJwtDecoderConfiguration` y la nota «esta feature no emite tokens».
  - Lo que sigue vigente: resource server nativo, RS256, starter `spring-boot-starter-security-oauth2-resource-server` y ninguna librería JWT de terceros.

  Referencia: research R4.
- [X] T003 [CONFIG] Añadir a `maven-surefire-plugin` en `pom.xml` `<systemPropertyVariables><spring.profiles.active>test</spring.profiles.active></systemPropertyVariables>`, con un comentario que cite ADR-006. No añadir dependencias (plan, Configuración de tests).
- [X] T004 [CONFIG] Crear `src/test/resources/application-test.yaml` con `mangateca.identity.jwt.issuer: mangateca-test` y nada más: sin claves (contracts/configuration.md, Perfiles).

**Checkpoint**: `./mvnw verify` en verde. Pasan todos los tests de 001 sin cambios
(`DevJwtDecoderConfiguration` solo aplica a `dev`, y `TestSecurityConfiguration` sigue aportando
las claves).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema, entidades, repositorios, claves, formato de errores, codificación de
contraseñas, `Clock` y cadena pública. La conversión del claim `roles` a
`ROLE_CUSTOMER`/`ROLE_ADMIN` **ya existe** en `JwtAuthenticationConverter` de
`src/main/java/com/mangateca/api/platform/security/SecurityConfiguration.java` (001) y no se
modifica; se verifica con un token real en T056.

**⚠️ CRITICAL**: ninguna historia empieza antes de cerrar esta fase.

- [X] T005 [MIGRATION] Crear `src/main/resources/db/migration/V2__create_identity.sql` (data-model §1 y §2), sin extensiones:
  - **`users`**:
    - `id UUID` PK
    - `email VARCHAR(254) NOT NULL`
    - `display_name VARCHAR(100) NOT NULL`
    - `password_hash VARCHAR(255) NOT NULL`
    - `role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN'))`
    - `enabled BOOLEAN NOT NULL DEFAULT TRUE`
    - `failed_login_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0)`
    - `locked_until TIMESTAMPTZ` NULL
    - `created_at TIMESTAMPTZ NOT NULL`
    - `updated_at TIMESTAMPTZ NOT NULL`
    - Índice `users_email_lower_uk`: `UNIQUE INDEX ON users (lower(email))`
  - **`refresh_tokens`**:
    - `id UUID` PK
    - `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`
    - `token_hash BYTEA NOT NULL CHECK (octet_length(token_hash) = 32)`, con `refresh_tokens_token_hash_uk UNIQUE (token_hash)`
    - `family_id UUID NOT NULL`
    - `expires_at TIMESTAMPTZ NOT NULL`
    - `revoked_at TIMESTAMPTZ` NULL
    - `replaced_by UUID REFERENCES refresh_tokens(id)` NULL
    - `created_at TIMESTAMPTZ NOT NULL`
    - Índices `refresh_tokens_family_idx (family_id)` y `refresh_tokens_user_idx (user_id)`

  Cubre FR-002 y FR-015.
- [X] T006 [P] [CODE] Crear el módulo y el enum público `Role { CUSTOMER, ADMIN }` en `src/main/java/com/mangateca/api/identity/package-info.java` (`@ApplicationModule(displayName = "Identity")` + `@NullMarked`) y `src/main/java/com/mangateca/api/identity/Role.java`. Javadoc: «cliente»/«CLIENTE» de la spec = `CUSTOMER` (FR-005, FR-021).
- [X] T007 [P] [CODE] Crear el VO `Email` en `src/main/java/com/mangateca/api/identity/domain/Email.java` y `src/main/java/com/mangateca/api/identity/domain/package-info.java`. Es un record `Email(String value)` cuyo constructor compacto aplica `trim()` y minúsculas con `Locale.ROOT` y valida la regla mínima de research R11: no vacío, ≤254 caracteres, una sola `@` con ambos lados no vacíos y sin espacios. Si no la cumple, lanza `IllegalArgumentException` (FR-002, research R11).
- [X] T008 [CODE] Crear la entidad `User` en `src/main/java/com/mangateca/api/identity/domain/User.java`, sin Lombok:
  - Mapeo 1:1 con `users` y `role` como `@Enumerated(EnumType.STRING)`.
  - Constructor protegido para JPA y fábrica `User.register(Email, String displayName, String passwordHash, Role, Clock)`. La fábrica valida el nombre (1–100 caracteres tras `trim()`; si no, `IllegalArgumentException`) y fija `enabled = true`, `failedLoginAttempts = 0`, `lockedUntil = null` y `createdAt`/`updatedAt` desde el `Clock`.
  - Método `disable(Instant now)`, necesario para el escenario «Cuenta deshabilitada».
  - Accesores sin setters públicos.

  Depende de T006 y T007 (FR-001, FR-005, FR-011, research R15).
- [X] T009 [P] [CODE] Crear la entidad `RefreshToken` en `src/main/java/com/mangateca/api/identity/domain/RefreshToken.java`. Mapeo 1:1 con `refresh_tokens`; `userId` como `UUID`, sin `@ManyToOne`; `tokenHash` como `byte[]`. Fábrica `RefreshToken.issue(UUID userId, byte[] tokenHash, UUID familyId, Instant now, Duration ttl)` con `expiresAt = now + ttl`. Depende de T007 (FR-012, FR-015).
- [X] T010 [CODE] Crear `UserRepository` (Spring Data) en `src/main/java/com/mangateca/api/identity/domain/UserRepository.java`:
  - `boolean existsByEmail(String)` con JPQL `lower(u.email) = lower(:email)`.
  - `Optional<User> findByEmailForUpdate(String)` con la misma condición y `@Lock(PESSIMISTIC_WRITE)`.
  - `boolean existsByRole(Role)`.

  Depende de T008 (data-model §3).
- [X] T011 [P] [CODE] Crear `RefreshTokenRepository` (Spring Data) en `src/main/java/com/mangateca/api/identity/domain/RefreshTokenRepository.java`:
  - `Optional<RefreshToken> findByTokenHashForUpdate(byte[])` con `@Lock(PESSIMISTIC_WRITE)`.
  - `@Modifying int revokeFamily(UUID familyId, Instant now)`: `UPDATE … SET revokedAt = :now WHERE familyId = :familyId AND revokedAt IS NULL`.

  Depende de T009 (data-model §3).
- [X] T012 [CODE] Crear el record `IdentityProperties` (`@ConfigurationProperties("mangateca.identity")`) en `src/main/java/com/mangateca/api/identity/config/IdentityProperties.java` y `src/main/java/com/mangateca/api/identity/config/package-info.java`. Campos:
  - `jwt(issuer, @Nullable Resource privateKeyLocation, @Nullable Resource publicKeyLocation)`.
  - `accessTokenTtl` (por defecto `PT15M`) y `refreshTokenTtl` (por defecto `P7D`).
  - `refreshCookie(sameSite)`: solo `Strict`|`Lax`|`None`, con `Strict` por defecto; cualquier otro valor hace fallar el arranque.
  - `bootstrapAdmin(@Nullable String email, @Nullable String password)`, con `toString()` redefinido para enmascarar la contraseña.

  Cubre contracts/configuration.md y research R13.
- [X] T013 [CODE] Crear `JwtKeyConfiguration` en `src/main/java/com/mangateca/api/identity/config/JwtKeyConfiguration.java`, anotada con `@EnableConfigurationProperties(IdentityProperties.class)` (único registro de las propiedades). Resuelve el par RSA según la tabla «Resolución de claves» de contracts/configuration.md:
  - Ambas rutas → `RsaKeyConverters.pkcs8()`/`x509()` y comprobación de que el módulo coincide.
  - Ninguna ruta con `environment.matchesProfiles("dev | test")` → `KeyPairGenerator` RSA 2048 y log INFO sin material de clave.
  - Cualquier otro caso → `IllegalStateException` que nombra las variables que faltan.

  Expone los beans `JwtEncoder` (`NimbusJwtEncoder` sobre `ImmutableJWKSet`) y `JwtDecoder` (`NimbusJwtDecoder.withPublicKey(...)` con `JwtValidators.createDefaultWithIssuer(issuer)`). Cubre FR-008 y research R3/R4.
- [X] T014 [TEST] Adaptar `src/test/java/com/mangateca/api/TestSecurityConfiguration.java` **inmediatamente después de T013**, porque sus beans `JwtDecoder`/`JwtEncoder` chocan con los de T013. Quitar esos beans y dejar un helper que acuñe tokens de prueba con el `JwtEncoder` del contexto, incluyendo siempre `iss = mangateca-test` (el decoder valida `iss`) y permitiendo fijar `iat`/`exp` y `roles` (plan, Impacto en 001).
- [X] T015 [TEST] Adaptar `src/test/java/com/mangateca/api/platform/security/SecurityIT.java` al helper de T014. El caso «firmado con otra clave» sigue generando su propio par local, y se añade el caso «`iss` ajeno → 401» (FR-008, research R3).
- [X] T016 [CODE] Eliminar `src/main/java/com/mangateca/api/platform/security/DevJwtDecoderConfiguration.java` (lo sustituye T013; con ambos, el perfil `dev` tendría dos `JwtDecoder`) (research R3).
- [X] T017 [CONFIG] Añadir `mangateca.identity.jwt.issuer: http://localhost:8080` en `src/main/resources/application-dev.yaml`, sin rutas de claves (contracts/configuration.md, Perfiles).
- [X] T018 [CONFIG] Añadir en `src/main/resources/application.yaml` los valores por defecto de `mangateca.identity`: `access-token-ttl: PT15M`, `refresh-token-ttl: P7D` y `refresh-cookie.same-site: Strict`. Sin `issuer` ni rutas de claves, para que fuera de dev/test sean obligatorias (contracts/configuration.md).
- [X] T019 [CODE] Modificar `handleExceptionInternal` en `src/main/java/com/mangateca/api/platform/web/GlobalExceptionHandler.java`: si el `ProblemDetail` trae un `type` distinto de `about:blank`, se conservan su `type` y su `detail` y solo se normalizan `title` e `instance`. Con `about:blank`, el comportamiento de 001 no cambia. Es una regla genérica, sin referencias a clases de `identity`, y el advice sigue siendo único (Constitución IV, FR-020, research R10).
- [X] T020 [CODE] Crear `IdentitySecurityConfiguration` en `src/main/java/com/mangateca/api/identity/config/IdentitySecurityConfiguration.java`. Beans:
  - `PasswordEncoder` = `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.
  - `Clock` = `Clock.systemUTC()`.
  - `SecurityFilterChain` con `@Order(1)` y `securityMatcher` exactamente para `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` y `POST /api/v1/auth/logout`, más su `OPTIONS`: `permitAll()`, `csrf` desactivado, sesión `STATELESS` y `cors(Customizer.withDefaults())`.

  `/api/v1/auth/me` queda en la cadena deny-by-default de la plataforma. Cubre FR-004, FR-019 y research R9/R16.

**Checkpoint**: `./mvnw verify` en verde.
- `ApiApplicationTests` arranca con el perfil `test` y el par efímero, y Hibernate valida V2 con `ddl-auto=validate`.
- `ModularityTests` reconoce el módulo `identity` sin ciclos.
- Los tests de 001 pasan con el helper de T014 y con el cambio de T019 (los `about:blank` de 001 no cambian).
- `SecurityIT` rechaza un token con `iss` ajeno.

---

## Phase 3: User Story 1 - Registro de cliente (Priority: P1) 🎯 MVP

**Goal**: un invitado crea una cuenta `CUSTOMER` y se publica `UserRegistered`.

**Independent Test**: `POST /api/v1/auth/register` válido → 201 sin contraseña; existe la cuenta
con rol `CUSTOMER` y llega el evento.

### Tests for User Story 1 ⚠️

- [ ] T021 [P] [US1] [TEST] Crear `UserRepositoryIT` en `src/test/java/com/mangateca/api/identity/domain/UserRepositoryIT.java` con `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)` y `@Import(TestcontainersConfiguration.class)`. Guarda `ana@example.com` con `User.register` y verifica dos cosas:
  - `existsByEmail("ANA@Example.com")` es `true`.
  - Insertar con SQL nativo (`testEntityManager.getEntityManager().createNativeQuery(...)` y `flush`) otra fila con `ANA@EXAMPLE.COM`, sin pasar por `Email`, lanza una violación de unicidad (`PersistenceException`/`ConstraintViolationException`) del índice `users_email_lower_uk` de V2.

  Cubre US1 Escenario 2 y FR-002.
- [ ] T022 [P] [US1] [TEST] Crear `AuthControllerTests` en `src/test/java/com/mangateca/api/identity/web/AuthControllerTests.java` con `@WebMvcTest(AuthController.class)` y `MockMvcTester`, importando con `@Import` `IdentitySecurityConfiguration`, `JwtKeyConfiguration` (registra `IdentityProperties`; perfil `test`), `UserMapperImpl` y `RefreshCookieFactory`, y con `@MockitoBean AuthenticationService` y `@MockitoBean RefreshTokenService`. El `GlobalExceptionHandler` lo incluye el slice. Casos:
  - Registro válido → 201 con `id`, `email`, `displayName` y `role=CUSTOMER`, sin campos `password` ni `passwordHash` (Escenario 1, FR-004, FR-005).
  - El servicio lanza `EmailAlreadyInUseException` → 409 `application/problem+json` con `type=urn:mangateca:problem:email-already-in-use` conservado por T019 (Escenario 2, FR-020).
  - `@ParameterizedTest` con los 5 ejemplos del Scenario Outline (contraseña de 7 y de 65 caracteres, correo inválido, nombre vacío, nombre de 101 caracteres) → 400 con `errors[].field` del campo afectado (Escenario 3, FR-001, FR-003).
  - `"role":"ADMIN"` → 400 con `errors[].field = role` y `verifyNoInteractions(authenticationService)` (Escenario 4, FR-005).
- [ ] T023 [P] [US1] [TEST] Crear `UserRegisteredIT` en `src/test/java/com/mangateca/api/identity/UserRegisteredIT.java` con `@ApplicationModuleTest`, `@Import(TestcontainersConfiguration.class)` y `Scenario`: `scenario.stimulate(() -> authenticationService.register(...)).andWaitForEventOfType(UserRegistered.class).matching(e -> e.role() == Role.CUSTOMER).toArriveAndVerify(e -> assertThat(e.userId()).isNotNull())`. Sin inspeccionar `event_publication` (Escenario 1, FR-006, research R12).
- [ ] T024 [US1] [TEST] Crear `AuthFlowIT` en `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` con `@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureRestTestClient` e `@Import(TestcontainersConfiguration.class)`, y añadir el `@Nested Register`: registro de punta a punta → 201 y `role=CUSTOMER` sin contraseña en el cuerpo (flujo feliz de register; FR-001, FR-005).

### Implementation for User Story 1

- [ ] T025 [P] [US1] [CODE] Crear el record público `UserRegistered(UUID userId, Role role, Instant occurredAt)`, sin correo ni nombre, en `src/main/java/com/mangateca/api/identity/UserRegistered.java` (FR-006).
- [ ] T026 [P] [US1] [CODE] Crear `PasswordPolicy` en `src/main/java/com/mangateca/api/identity/application/PasswordPolicy.java` y `src/main/java/com/mangateca/api/identity/application/package-info.java`. Es una clase final con `static boolean isValid(String)`: 8–64 caracteres (`codePointCount`) **y** ≤72 bytes UTF-8 (FR-003, research R5).
- [ ] T027 [P] [US1] [CODE] Crear `EmailAlreadyInUseException extends ErrorResponseException` en `src/main/java/com/mangateca/api/identity/application/EmailAlreadyInUseException.java`. Construye un `ProblemDetail` 409 con `type=urn:mangateca:problem:email-already-in-use` y `detail` constante «El correo ya está en uso.», sin el correo (FR-002, FR-020, research R10, R14).
- [ ] T028 [US1] [CODE] Crear `AuthenticationService` con `register(String email, String displayName, String password)` en `src/main/java/com/mangateca/api/identity/application/AuthenticationService.java`. Método `@Transactional` que:
  1. Normaliza con `Email`.
  2. Lanza `EmailAlreadyInUseException` si `existsByEmail`.
  3. Codifica con `PasswordEncoder` y crea `User.register(..., Role.CUSTOMER, clock)`.
  4. Hace `saveAndFlush` y traduce `DataIntegrityViolationException` a `EmailAlreadyInUseException`.
  5. Publica `UserRegistered` con `ApplicationEventPublisher`.
  6. Devuelve el `User`.

  Una `IllegalArgumentException` de `Email` o de `User.register` se traduce a `ErrorResponseException(BAD_REQUEST)`, nunca a 500. Depende de T025–T027 (FR-001, FR-002, FR-004, FR-005, FR-006, research R10, R11).
- [ ] T029 [P] [US1] [CODE] Crear la restricción `@ValidPassword` y su validador (delegan en `PasswordPolicy`) en `src/main/java/com/mangateca/api/identity/web/ValidPassword.java`, `src/main/java/com/mangateca/api/identity/web/ValidPasswordValidator.java` y `src/main/java/com/mangateca/api/identity/web/package-info.java` (FR-003).
- [ ] T030 [P] [US1] [CODE] Crear los records DTO `RegisterRequest` y `UserResponse` en `src/main/java/com/mangateca/api/identity/web/RegisterRequest.java` y `src/main/java/com/mangateca/api/identity/web/UserResponse.java`:
  - `RegisterRequest`:
    - El constructor compacto recorta (`strip()`) `email` y `displayName`, tolerando `null` para que Bean Validation lo reporte.
    - Campos: `email` (`@NotBlank @Email @Size(max = 254)`), `displayName` (`@NotBlank @Size(max = 100)`), `password` (`@ValidPassword`) y `role` (`@Nullable @Null`).
    - `toString()` sin la contraseña.
  - `UserResponse(UUID id, String email, String displayName, Role role)`.

  Cubre FR-001, FR-003, FR-005, FR-016 y el caso borde «correo con espacios».
- [ ] T031 [P] [US1] [CODE] Crear el mapper MapStruct `UserMapper` (`componentModel = "spring"`, `User` → `UserResponse`) en `src/main/java/com/mangateca/api/identity/web/UserMapper.java` (Constitución IV).
- [ ] T032 [US1] [CODE] Crear `AuthController` en `src/main/java/com/mangateca/api/identity/web/AuthController.java` con `@RequestMapping("/api/v1/auth")` y `POST /register` (`@Valid @RequestBody RegisterRequest`): llama a `AuthenticationService.register` y responde 201 con `UserMapper` (FR-001, FR-019).

**Checkpoint**: `./mvnw verify` en verde, con T021–T024 más Foundational, `ModularityTests` y
`ApiApplicationTests`.

---

## Phase 4: User Story 2 - Inicio de sesión (Priority: P1)

**Goal**: login con correo y contraseña; respuestas indistinguibles; bloqueo tras 5 fallos.

**Independent Test**: login de una cuenta registrada → access token de 15 min aceptado por un
recurso autenticado (por ejemplo, `GET /api/v1/probes?size=1`, controlador de test de 001) y
cookie de refresh de 7 días.

### Tests for User Story 2 ⚠️

- [ ] T033 [P] [US2] [TEST] Crear `UserLockoutTests` en `src/test/java/com/mangateca/api/identity/domain/UserLockoutTests.java`, unitario sin Spring ni BD con `User` real e instantes explícitos. Casos:
  - 5 `registerFailedLogin` consecutivos → `isLocked(now)` y `lockedUntil = now + 15 min`.
  - `isLocked` sigue siendo `true` a `now + 14m59s` y pasa a `false` a `now + 15m`.
  - Un fallo sobre una cuenta bloqueada no amplía el bloqueo.
  - Tras vencer el bloqueo, el siguiente fallo reinicia el contador a 1.
  - `registerSuccessfulLogin` con 4 fallos deja el contador en 0.

  Cubre US2 Escenarios 3 y 4, FR-010 y Principio VI.2.
- [ ] T034 [P] [US2] [TEST] Crear `AuthenticationServiceTests` en `src/test/java/com/mangateca/api/identity/application/AuthenticationServiceTests.java`, unitario sin Spring.
  - **Montaje**: mocks de `UserRepository` y `RefreshTokenRepository`; `Clock` de test avanzable a mano (subclase de `Clock` en el test, sin `sleep`); `PasswordEncoder` delegante real; `AccessTokenIssuer` real sobre un `NimbusJwtEncoder` con par efímero; `RefreshTokenService` real; publicador de eventos como lambda que captura (no mock).
  - **Casos**:
    - Contraseña errónea y correo inexistente lanzan la misma `InvalidCredentialsException` con el mismo `ProblemDetail` (Escenario 2, FR-009).
    - Tras 5 fallos, la contraseña correcta lanza `InvalidCredentialsException`; al avanzar el `Clock` 15 min, el login tiene éxito (Escenario 3, FR-010, SC-004).
    - Con 4 fallos, el login correcto deja `failedLoginAttempts = 0` (Escenario 4).
    - Una cuenta `disable(now)` con credenciales correctas lanza la misma excepción (Escenario 5, FR-011).
- [ ] T035 [US2] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/web/AuthControllerTests.java` los casos de login:
  - Login correcto → 200 con `access_token`, `token_type=Bearer` y `expires_in=900`, sin `refresh_token` en el cuerpo, y `Set-Cookie: refresh_token=…; Path=/api/v1/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict` (Escenario 1, FR-007, Constitución V).
  - El servicio lanza `InvalidCredentialsException` → 401 con `type=urn:mangateca:problem:invalid-credentials` (FR-009, FR-020).
  - Cuerpo `{}` → 400 con `errors` (`@Valid`, Higiene Operacional).
- [ ] T036 [US2] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` el `@Nested Login`:
  - Flujo feliz de login: el `exp − iat` del JWT es 900, `sub` es el UUID del usuario, `roles=["CUSTOMER"]`, la cookie trae `Max-Age=604800` y el access token obtiene 200 en `GET /api/v1/probes?size=1` (Escenario 1, FR-007, FR-008).
  - La respuesta a contraseña errónea y la respuesta a correo inexistente coinciden en `status`, `type` y `detail` (Escenario 2, FR-009, SC-002).

### Implementation for User Story 2

- [ ] T037 [US2] [CODE] Añadir a `src/main/java/com/mangateca/api/identity/domain/User.java` las constantes `MAX_FAILED_ATTEMPTS = 5` y `LOCK_DURATION = Duration.ofMinutes(15)` y los métodos `isLocked(Instant)`, `registerFailedLogin(Instant)` y `registerSuccessfulLogin(Instant)` según research R6. Cada método actualiza `updatedAt` (FR-010).
- [ ] T038 [P] [US2] [CODE] Crear `AccessTokenIssuer` en `src/main/java/com/mangateca/api/identity/application/AccessTokenIssuer.java`. Usa `JwsHeader.with(SignatureAlgorithm.RS256)` y `JwtClaimsSet` con `sub = userId`, `roles = List.of(role.name())`, `iss` de `mangateca.identity.jwt.issuer`, `iat = clock.instant()` y `exp = iat + accessTokenTtl`, y devuelve el valor del token y `expiresIn` en segundos (FR-007, FR-008, research R2).
- [ ] T039 [P] [US2] [CODE] Crear `InvalidCredentialsException extends ErrorResponseException` en `src/main/java/com/mangateca/api/identity/application/InvalidCredentialsException.java`. `ProblemDetail` 401 con `type=urn:mangateca:problem:invalid-credentials` y `detail` constante «Credenciales inválidas.» (FR-009, FR-020, research R10).
- [ ] T040 [P] [US2] [CODE] Crear `RefreshTokenService` con `issue(UUID userId, Instant now)` en `src/main/java/com/mangateca/api/identity/application/RefreshTokenService.java`. Genera 32 bytes con `SecureRandom`, los codifica en Base64 URL sin relleno, guarda `RefreshToken.issue` con el SHA-256 del texto y un `family_id` nuevo, y devuelve el valor en claro una sola vez (FR-007, FR-015, research R7).
- [ ] T041 [US2] [CODE] Añadir `login(String email, String password)` a `src/main/java/com/mangateca/api/identity/application/AuthenticationService.java` y crear el record `IssuedTokens(String accessToken, long expiresIn, String refreshToken)` en `src/main/java/com/mangateca/api/identity/application/IssuedTokens.java`.
  - Transacción `@Transactional(noRollbackFor = InvalidCredentialsException.class)` con `findByEmailForUpdate`.
  - Correo que el VO `Email` rechaza, correo inexistente, cuenta deshabilitada o bloqueada → `matches(password, DUMMY_HASH)` y `InvalidCredentialsException`.
  - Contraseña errónea → `registerFailedLogin` y la misma excepción.
  - Login correcto → `registerSuccessfulLogin`, `AccessTokenIssuer` y `RefreshTokenService.issue`.

  Sin logs de correo ni contraseña. Cubre FR-007, FR-009, FR-010 y FR-011 (research R5, R6, R11, R14).
- [ ] T042 [P] [US2] [CODE] Crear los records DTO `LoginRequest` y `TokenResponse` en `src/main/java/com/mangateca/api/identity/web/LoginRequest.java` y `src/main/java/com/mangateca/api/identity/web/TokenResponse.java`:
  - `LoginRequest(@NotBlank String email, @NotBlank String password)`, sin `@ValidPassword` y con `toString()` sin la contraseña.
  - `TokenResponse` con `@JsonProperty("access_token")`, `"token_type"` (`"Bearer"`) y `"expires_in"`.

  Cubre FR-007 y FR-009.
- [ ] T043 [P] [US2] [CODE] Crear `RefreshCookieFactory` (`@Component`) con `create(String token)` y `clear()` en `src/main/java/com/mangateca/api/identity/web/RefreshCookieFactory.java`. Ambos construyen `ResponseCookie` `refresh_token` con `Path=/api/v1/auth`, `HttpOnly`, `Secure` y `SameSite` de `IdentityProperties`; `create` usa `Max-Age = refreshTokenTtl` y `clear` usa `Max-Age=0` (FR-007, Constitución V, research R8).
- [ ] T044 [US2] [CODE] Añadir `POST /login` (`@Valid @RequestBody LoginRequest`) a `src/main/java/com/mangateca/api/identity/web/AuthController.java`: responde 200 con `TokenResponse` y la cabecera `Set-Cookie` de `RefreshCookieFactory.create` (FR-007, FR-019, Higiene Operacional).

**Checkpoint**: `./mvnw verify` en verde con T033–T036. US1 sigue en verde.

---

## Phase 5: User Story 3 - Renovación y cierre de sesión (Priority: P2)

**Goal**: rotación del refresh token con detección de reutilización y logout idempotente.

**Independent Test**: login → refresh (nueva cookie, la anterior ya no sirve) → logout → la cookie
deja de renovar.

### Tests for User Story 3 ⚠️

- [ ] T045 [P] [US3] [TEST] Crear `RefreshTokenServiceTests` en `src/test/java/com/mangateca/api/identity/application/RefreshTokenServiceTests.java`, unitario sin Spring, con mocks de `RefreshTokenRepository` y `UserRepository` y un `Clock` de test controlado. Casos:
  - Rotación válida: se devuelve un token nuevo con el mismo `familyId`, y el anterior queda con `revokedAt` y `replacedBy` (Escenario 1, FR-012).
  - Presentar el token ya rotado lanza `InvalidRefreshTokenException` e invoca `revokeFamily(familyId, now)` (Escenario 2, FR-013, SC-005).
  - Tras `revoke` (logout), renovar con ese token lanza `InvalidRefreshTokenException` (Escenario 3, FR-014).
  - Logout con token desconocido o expirado (expirado avanzando el `Clock` 7 días) termina sin excepción (Escenario 4, FR-014).
- [ ] T046 [P] [US3] [TEST] Crear `RefreshTokenRepositoryIT` en `src/test/java/com/mangateca/api/identity/domain/RefreshTokenRepositoryIT.java` con `@DataJpaTest`, `@AutoConfigureTestDatabase(replace = NONE)` y `@Import(TestcontainersConfiguration.class)`. Guarda 3 tokens de la familia A (uno ya revocado) y 1 de la familia B; `revokeFamily(A, now)` devuelve 2, revoca todos los de A y deja intacto el de B (Escenario 2 «todos los tokens de esa sesión quedan invalidados», FR-013).
- [ ] T047 [US3] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/web/AuthControllerTests.java` los casos de refresh y logout:
  - Refresh con cookie → 200 `TokenResponse` y nueva `Set-Cookie` (Escenario 1).
  - Refresh sin cookie → 401 con `type=urn:mangateca:problem:invalid-refresh-token` (FR-020).
  - Logout sin cookie → 204, `Set-Cookie` con `Max-Age=0` y sin token de acceso (Escenario 4, FR-014, FR-019).
- [ ] T048 [US3] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` el `@Nested Session` con el flujo feliz de refresh y logout: login → refresh 200 con cookie nueva → la cookie anterior da 401 → logout 204 → la cookie vigente da 401 al renovar (FR-012, FR-014).

### Implementation for User Story 3

- [ ] T049 [US3] [CODE] Añadir a `src/main/java/com/mangateca/api/identity/domain/RefreshToken.java` los métodos `isExpired(Instant)`, `isRevoked()`, `rotate(UUID newTokenId, Instant now)` (fija `revokedAt` y `replacedBy`) y `revoke(Instant now)` (solo si no estaba revocado) (FR-012, FR-013, data-model §3).
- [ ] T050 [P] [US3] [CODE] Crear `InvalidRefreshTokenException extends ErrorResponseException` en `src/main/java/com/mangateca/api/identity/application/InvalidRefreshTokenException.java`. `ProblemDetail` 401 con `type=urn:mangateca:problem:invalid-refresh-token` y `detail` constante «Token de renovación inválido.» (FR-013, FR-020, research R10).
- [ ] T051 [US3] [CODE] Añadir `rotate(String token)` y `revoke(@Nullable String token)` a `src/main/java/com/mangateca/api/identity/application/RefreshTokenService.java`.
  - `rotate`: transacción `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`. Hashea y aplica `findByTokenHashForUpdate`; luego sigue los casos de research R7:
    - Inexistente → excepción.
    - Revocado → `revokeFamily` + WARN con `userId`/`familyId` + excepción.
    - Expirado → excepción.
    - Usuario deshabilitado o inexistente → `revokeFamily` + excepción.
    - Válido → token nuevo de la familia + `rotate` del anterior + nuevo access token vía `AccessTokenIssuer`; devuelve `IssuedTokens`.
  - `revoke`: si el token existe, `revokeFamily`; nunca lanza.

  Cubre FR-012, FR-013, FR-014 y FR-015.
- [ ] T052 [US3] [CODE] Añadir a `src/main/java/com/mangateca/api/identity/web/AuthController.java` los endpoints `POST /refresh` y `POST /logout`, ambos con `@CookieValue(name = "refresh_token", required = false)`:
  - `POST /refresh`: sin cookie → `InvalidRefreshTokenException`; con cookie → 200 con `TokenResponse` y nueva cookie.
  - `POST /logout`: siempre 204 con `RefreshCookieFactory.clear()`.

  Cubre FR-012, FR-014 y FR-019.

**Checkpoint**: `./mvnw verify` en verde con T045–T048. US1 y US2 siguen en verde.

---

## Phase 6: User Story 4 - Identidad actual (Priority: P2)

**Goal**: `/api/v1/auth/me` devuelve la identidad del `sub` y los roles del token protegen
recursos.

**Independent Test**: `/me` con token válido → 200; con token expirado o sin token → 401; token
`CUSTOMER` en un recurso `ADMIN` → 403.

### Tests for User Story 4 ⚠️

- [ ] T053 [US4] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/web/AuthControllerTests.java` el caso de `/me` con `spring-security-test` (`SecurityMockMvcRequestPostProcessors.jwt()` con `subject` = UUID): 200 con el `UserResponse` del servicio mockeado y sin campos sensibles (Escenario 1, FR-016). El 401 sin token no se prueba aquí: en el slice no está la cadena deny-by-default de la plataforma, y lo cubre T055.
- [ ] T054 [US4] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` el `@Nested Me`:
  - Dos usuarios registrados: cada token obtiene en `/me` solo sus propios datos (`id`, `email`, `displayName`, `role`; sin `password`/`passwordHash`) (Escenario 1, FR-016, anti IDOR).
  - Token acuñado con el helper de T014 y `exp` en el pasado → 401 ProblemDetail (Escenario 2).
- [ ] T055 [US4] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` el `@Nested Security` (seguridad crítica):
  - Las 4 rutas `POST /api/v1/auth/{register,login,refresh,logout}` sin token nunca devuelven el 401 del entry point de la plataforma (`type=about:blank` + `WWW-Authenticate: Bearer`). Sí pueden devolver los 401 propios de `identity`; por ejemplo, `refresh` sin cookie da `type=urn:mangateca:problem:invalid-refresh-token` sin `WWW-Authenticate` (FR-019).
  - `GET /api/v1/auth/me` y `GET /api/v1/auth/inexistente` sin token → 401 del entry point (`about:blank` + `WWW-Authenticate: Bearer`) (Escenario 3, deny-by-default).
- [ ] T056 [US4] [TEST] Añadir a `src/test/java/com/mangateca/api/identity/AuthFlowIT.java` el `@Nested Roles`. Con el access token **real** de un `CUSTOMER` obtenido por login, `GET /api/v1/probes/admin` (controlador de test de 001 con `@PreAuthorize("hasRole('ADMIN')")`) → 403 (Escenario 4, FR-021, SC-003). Con un token acuñado con `roles=["ADMIN"]` → 200: verifica la conversión `roles` → `ROLE_*` (FR-008).

### Implementation for User Story 4

- [ ] T057 [US4] [CODE] Añadir `me(UUID userId)` con `@Transactional(readOnly = true)` a `src/main/java/com/mangateca/api/identity/application/AuthenticationService.java`: devuelve `Optional<User>`, vacío si el usuario no existe o está deshabilitado (FR-016).
- [ ] T058 [US4] [CODE] Añadir `GET /me` a `src/main/java/com/mangateca/api/identity/web/AuthController.java`. Recibe `@AuthenticationPrincipal Jwt jwt` y toma `UUID.fromString(jwt.getSubject())`; si encuentra al usuario responde 200 con `UserMapper`. Si no, lanza `ErrorResponseException(UNAUTHORIZED)` con `getHeaders().set(WWW_AUTHENTICATE, "Bearer")`; el `GlobalExceptionHandler` produce el mismo cuerpo que el entry point de 401 de 001 (`about:blank`, «Se requiere autenticación válida.»). La identidad sale solo del token (FR-016, FR-020, Constitución V, research R10).

**Checkpoint**: `./mvnw verify` en verde con T053–T056. US1 a US3 siguen en verde.

---

## Phase 7: User Story 5 - Primer administrador (Priority: P3)

**Goal**: administrador inicial por entorno, idempotente, sin abrir el registro a `ADMIN`.

**Independent Test**: arrancar con las variables → exactamente un `ADMIN`, también tras reiniciar.

### Tests for User Story 5 ⚠️

- [ ] T059 [US5] [TEST] Crear `BootstrapAdminIT` en `src/test/java/com/mangateca/api/identity/BootstrapAdminIT.java` con `@SpringBootTest`, `@Import(TestcontainersConfiguration.class)` y `OutputCaptureExtension`, en dos `@Nested`:
  - **Configurado**, con `properties` `mangateca.identity.bootstrap-admin.email/password`:
    - Tras el arranque, `existsByRole(ADMIN)` y la salida capturada no contiene la contraseña (Escenario 1, FR-017, FR-018, SC-006).
    - Invocar `run(...)` del `BootstrapAdminRunner` otra vez mantiene 1 `ADMIN` (Escenario 2, SC-007).
  - **Sin configuración**: 0 `ADMIN` tras el arranque (Escenario 3, FR-017).

  Limpia `users` entre contextos.

### Implementation for User Story 5

- [ ] T060 [US5] [CODE] Crear `BootstrapAdminRunner` (`ApplicationRunner`, `@Transactional`) en `src/main/java/com/mangateca/api/identity/application/BootstrapAdminRunner.java` con los pasos 1–5 de research R13:
  1. Sin configuración → INFO.
  2. Configuración incompleta o inválida según `Email`/`PasswordPolicy` → WARN sin correo ni contraseña.
  3. `existsByRole(ADMIN)` → fin.
  4. El correo ya existe como `CUSTOMER` → WARN.
  5. Crear `User.register(..., Role.ADMIN, clock)` con INFO solo del `userId`; `DataIntegrityViolationException` por concurrencia → fin sin error.

  Cubre FR-017 y FR-018.

**Checkpoint**: `./mvnw verify` en verde con T059. Todas las historias siguen en verde.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T061 [CODE] Anotar `src/main/java/com/mangateca/api/identity/web/AuthController.java` con springdoc (`@Tag`, `@Operation`, `@ApiResponse` con los códigos y `type` de problema de `contracts/openapi.yaml`, `@SecurityRequirements` vacío en las 4 rutas públicas y `bearerAuth` en `/me`) (DoD: API documentada en OpenAPI).
- [ ] T062 [DOC] Contrastar `GET /v3/api-docs` con `specs/002-identity-access/contracts/openapi.yaml` (rutas, códigos y esquemas) y corregir el contrato o las anotaciones hasta que coincidan; dejar constancia en `specs/002-identity-access/contracts/openapi.yaml` (comentario de verificación con la fecha).
- [ ] T063 [P] [DOC] Añadir una sección «Identidad y claves» a `README.md` con el par efímero en dev/test, las variables `MANGATECA_IDENTITY_JWT_*` de producción, el comando `openssl` y las variables del administrador inicial (contracts/configuration.md).
- [ ] T064 [P] [DOC] Anotar en `specs/001-platform-foundation/contracts/configuration.md` que `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION` queda sustituida por `MANGATECA_IDENTITY_JWT_*` (feature 002, ADR-006), y en `specs/001-platform-foundation/contracts/http-surface.md` que un `ProblemDetail` con `type` ≠ `about:blank` conserva su `type` y su `detail` (T019).
- [ ] T065 [DOC] Ejecutar a mano `specs/002-identity-access/quickstart.md` §2–§8 y marcar cada paso como verificado en ese archivo. Incluye:
  - §3d: contraseña de más de 72 bytes → 400.
  - §8: arranque sin perfil y sin claves → fallo; par no correspondiente → fallo; `git grep "BEGIN .*PRIVATE KEY"` sin resultados.
  - Ausencia del log «Using generated security password» (research R9).

**Checkpoint final**: `./mvnw verify` en verde (compilación sin advertencias, tests de 001 y 002,
`ModularityTests`, `ApiApplicationTests`) y quickstart verificado.

---

## Dependencies & Execution Order

### Grafo de dependencias entre fases

```text
Phase 1 Setup ──▶ Phase 2 Foundational ──▶ Phase 3 US1 (P1, MVP)
                                             │
                                             ▼
                                         Phase 4 US2 (P1)
                                             │
                          ┌──────────────────┼──────────────────┐
                          ▼                  ▼                  ▼
                   Phase 5 US3 (P2)   Phase 6 US4 (P2)   Phase 7 US5 (P3)
                          └──────────────────┴──────────────────┘
                                             ▼
                                     Phase 8 Polish
```

- **US1 → US2**: US2 añade métodos a `AuthenticationService`, `AuthController`,
  `AuthControllerTests` y `AuthFlowIT`, que crea US1. El login necesita usuarios creados con
  `register`.
- **US2 → US3**: la rotación parte de los tokens que emite el login (`RefreshTokenService.issue`,
  `IssuedTokens`, `RefreshCookieFactory`).
- **US2 → US4**: `/me` y la prueba de roles usan el access token del login.
- **US3 y US4** comparten `AuthController`, `AuthControllerTests` y `AuthFlowIT`, así que se
  ejecutan en secuencia (US3 antes que US4, por prioridad).
- **US5** no comparte archivos con US3 ni US4. Solo necesita Foundational y `PasswordPolicy` de
  US1, así que podría ir en paralelo con ellas; se deja al final por prioridad (P3).

### Dentro de cada historia

`[TEST]` → dominio → aplicación → web → checkpoint. Las tareas que tocan el mismo archivo van en
orden de ID.

### Commits y estado del build

Un commit por tarea (CLAUDE.md), en Conventional Commits con el ID, p. ej.
`feat(identity): add V2 identity migration (T005)`. Se acepta de forma explícita que los commits
de tareas `[TEST]` test-first dejen `./mvnw verify` en rojo, porque el test aún no compila o
falla. **El verde solo se exige en cada checkpoint de fase.** Fuera de eso, ninguna tarea `[CODE]`
o `[CONFIG]` debe romper lo que ya estaba en verde (de ahí el orden T013 → T014 → T016).

---

## Trazabilidad FR → tareas

| FR | Implementación | Tests |
|----|----------------|-------|
| FR-001 | T008, T028, T030, T032 | T022, T024 |
| FR-002 | T005, T007, T010, T027, T028, T030 | T021, T022 |
| FR-003 | T026, T029, T030 | T022, T065 (manual, 72 bytes) |
| FR-004 | T020, T028 | T022 |
| FR-005 | T006, T008, T028, T030 | T022, T024 |
| FR-006 | T025, T028 | T023 |
| FR-007 | T038, T040, T041, T042, T043, T044 | T035, T036 |
| FR-008 | T013, T038 | T015, T036, T056 |
| FR-009 | T039, T041, T042 | T034, T035, T036 |
| FR-010 | T037, T041 | T033, T034 |
| FR-011 | T008, T041 | T034 |
| FR-012 | T009, T049, T051, T052 | T045, T047, T048 |
| FR-013 | T011, T049, T050, T051 | T045, T046 |
| FR-014 | T051, T052 | T045, T047, T048 |
| FR-015 | T005, T009, T040, T051 | T065 (manual, hashes en BD) |
| FR-016 | T030, T057, T058 | T053, T054 |
| FR-017 | T012, T060 | T059 |
| FR-018 | T012, T060 | T059 |
| FR-019 | T020, T032, T044, T052, T058 | T047, T055 |
| FR-020 | T019, T027, T039, T050, T058 | T022, T035, T047, T055 |
| FR-021 | T006 (+ converter de 001) | T056 |

---

## Parallel Opportunities

| Fase | Tareas `[P]` | Notas |
|------|--------------|-------|
| 1 Setup | T002 | Puede ir junto a T001 (archivos distintos). T003 y T004 no son `[P]` (pom y yaml) |
| 2 Foundational | T006, T007, T009, T011 | T009 tras T007; T011 tras T009. T005, T008, T010 y T012–T020 en secuencia (migración, yaml, cadena de seguridad o dependencias) |
| 3 US1 | T021, T022, T023 · T025, T026, T027, T029, T030, T031 | T024 (crea `AuthFlowIT`), T028 y T032 en secuencia |
| 4 US2 | T033, T034 · T038, T039, T040, T042, T043 | T035 y T036 tocan archivos de US1; T037, T041 y T044 en secuencia |
| 5 US3 | T045, T046 · T050 | T047–T049, T051 y T052 en secuencia |
| 6 US4 | — | Todas tocan `AuthControllerTests`, `AuthFlowIT`, `AuthenticationService` o `AuthController` |
| 7 US5 | — | 2 tareas en secuencia |
| 8 Polish | T063, T064 | T061 → T062 → T065 en secuencia |

### Ejemplo: tests de US1 en paralelo

```text
T021 UserRepositoryIT      (domain/UserRepositoryIT.java)
T022 AuthControllerTests   (web/AuthControllerTests.java)
T023 UserRegisteredIT      (UserRegisteredIT.java)
```

---

## Implementation Strategy

### MVP (US1 + US2)

1. Phase 1 → Phase 2 (checkpoint: 001 en verde con las claves nuevas).
2. Phase 3 (US1) → **STOP and VALIDATE**: registro de punta a punta.
3. Phase 4 (US2) → **STOP and VALIDATE**: login usable contra un recurso autenticado. Registro y
   login juntos forman el MVP (la spec da P1 a ambas).

### Entrega incremental

4. US3 (sesión sin re-login cada 15 min), US4 (`/me` y roles), US5 (administrador inicial).
5. Polish: OpenAPI, README y quickstart.
