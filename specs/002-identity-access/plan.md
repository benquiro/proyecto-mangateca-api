# Implementation Plan: Identity & Access

**Branch**: `002-identity-access` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-identity-access/spec.md`

**Constitución de referencia**: v4.0.0

## Summary

Añadir el módulo Spring Modulith `identity`: registro de clientes, login, renovación con
rotación, logout, identidad actual (`/me`) y administrador inicial por entorno. La propia API
emite los access tokens (JWT RS256 con `NimbusJwtEncoder`, 15 min) y los valida con un
`JwtDecoder` construido con la misma clave pública. Los refresh tokens son opacos (256 bits,
`SecureRandom`), se guardan solo como hash SHA-256, viajan en una cookie
`HttpOnly; Secure; SameSite` y rotan dentro de una familia (`family_id`); reutilizar uno rotado
revoca toda la familia.

Decisiones clave (detalle en [research.md](research.md)):

1. **Refresh token en cookie, no en el cuerpo** (Constitución V). El cuerpo del login y del
   refresh trae `access_token`, `token_type` y `expires_in` (R8).
2. **Ninguna clave se versiona.** En los perfiles `dev` y `test`, sin clave configurada, se genera
   un par RSA efímero en memoria al arrancar. En cualquier otro perfil, la falta de clave hace
   fallar el arranque. En producción, las rutas de los PEM montados como secreto llegan por
   variables de entorno (R4). ADR-006 reemplaza parcialmente a ADR-003 en este punto.
3. **El par de claves vive en `identity`**, que define `JwtEncoder` y `JwtDecoder`. La cadena de
   la plataforma consume el `JwtDecoder` por tipo, sin depender de clases de `identity` (R3).
4. **`UserRegistered` se verifica con `@ApplicationModuleTest` + `Scenario`**
   (`andWaitForEventOfType`); no se inspecciona `event_publication` (R12).
5. **Rechazo de `role` en el registro** con un campo `@Null` en el DTO, porque Jackson ignora por
   defecto las propiedades desconocidas (R11).
6. **Roles**: el código usa `CUSTOMER` y `ADMIN`. «Cliente»/«CLIENTE» en la spec (actores,
   US1 y el enunciado original) se mapea siempre a `Role.CUSTOMER`; no existe un rol `CLIENTE`.

## Technical Context

**Language/Version**: Java 25 (LTS), `--release 25`

**Primary Dependencies**: Spring Boot 4.1.2 (Security 7), Spring Modulith 2.1.2, MapStruct
1.6.3, springdoc-openapi 3.2.0. **Sin dependencias nuevas**: `NimbusJwtEncoder`, Nimbus JOSE,
`RsaKeyConverters` y `PasswordEncoderFactories` ya llegan con `oauth2-resource-server` y
`spring-security-core`. ADR-006 documenta la decisión de arquitectura (emisión propia y origen de
las claves), no una dependencia.

**Storage**: PostgreSQL 18; migración `V2__create_identity.sql` (`users`, `refresh_tokens`).
V1 no creó extensiones, así que el correo es único con un índice sobre `lower(email)`, no con
`citext`.

**Testing**: JUnit 5 + AssertJ; `@WebMvcTest` con `MockMvcTester`, `@DataJpaTest` y `@SpringBootTest` + Testcontainers
`@ServiceConnection`, `spring-security-test`, `@ApplicationModuleTest` + `Scenario`. Mocks solo en
repositorios, `Clock` y, en `@WebMvcTest`, la capa de aplicación de `identity` (Constitución VI).
Detalle en la sección Testing.

**Target Platform**: servidor Linux / contenedor OCI; desarrollo con Docker.

**Project Type**: web-service (monolito modular)

**Performance Goals**: login y registro dominados por bcrypt (coste por defecto 10, ~50–100 ms).
Sin objetivos de throughput.

**Constraints**:

- Cero advertencias de compilación.
- Ninguna clave, secreto ni credencial en el repositorio.
- Ninguna API deprecada.
- La contraseña nunca aparece en logs, respuestas ni `toString()`.
- Correo inexistente y contraseña errónea producen respuestas indistinguibles, en contenido y en
  tiempo.

**Scale/Scope**: 1 módulo nuevo, 1 migración, 2 tablas, 5 endpoints, 1 runner, ~25 clases de
producción, ADR-006.

## Constitution Check

*GATE: pasar antes de Phase 0. Re-evaluado tras Phase 1. Constitución v4.0.0.*

| # | Regla | Cómo se cumple | Estado |
|---|-------|----------------|--------|
| I | Monolito modular; módulos verticales, sin capas globales | `identity/{domain,application,web,config}` dentro del módulo; sin paquetes técnicos en raíz | ✅ |
| I | Límites verificados con Modulith | `ModularityTests` existente cubre `identity`; `identity` no importa tipos de `platform` ni al revés | ✅ |
| I | Solo API pública o eventos entre módulos | Raíz de `identity`: `UserRegistered` y `Role`. Otros módulos usan el claim `sub`; `platform` consume `JwtDecoder` (interfaz de Spring) por tipo | ✅ |
| I | Terceros como puerto de dominio | N/A: sin integraciones externas | — |
| II | Invariantes en el dominio | `User` (bloqueo, contador, nombre), `RefreshToken` (rotado/revocado/expirado), `Email` con validación en el constructor compacto | ✅ |
| II | Dinero en `BigDecimal`/`Money` | N/A | — |
| II | Identificadores de negocio como VO `record` | `Email` | ✅ |
| III | Java 25, Boot 4.1.x, solo APIs estables | `NimbusJwtEncoder`/`NimbusJwtDecoder`, `RsaKeyConverters`, `PasswordEncoderFactories`, `Environment.matchesProfiles` | ✅ |
| III | No eliminar dependencias base; ADR por dependencia nueva | Ninguna dependencia nueva ni eliminada; ADR-006 por decisión de arquitectura | ✅ |
| III | DTO, comandos, eventos y VO como `record` | DTO, `UserRegistered`, `Email`, `IdentityProperties` | ✅ |
| III | Lombok solo en entidades; sin `@Data` | No se usa Lombok | ✅ |
| IV | PostgreSQL único; Flyway versionado e inmutable | V2 nueva; V1 intacta | ✅ |
| IV | `ddl-auto=validate`, OSIV off | Sin cambios; el smoke test de arranque valida V2 contra las entidades | ✅ |
| IV | Sin N+1 | `RefreshToken` referencia al usuario por `userId` (sin `@ManyToOne`); consultas por clave | ✅ |
| IV | `@Transactional` en aplicación, `readOnly` en consultas | `AuthenticationService`, `RefreshTokenService`; `me()` con `readOnly = true` | ✅ |
| IV | Entidades no expuestas; DTO + MapStruct | `UserMapper` (MapStruct) `User` → `UserResponse` | ✅ |
| IV | `/api/v1`, plural; excepción `/api/v1/auth/` con verbos | `register`, `login`, `refresh`, `logout`, `me` bajo `/api/v1/auth/`, dentro de la excepción acotada | ✅ |
| IV | Errores unificados en **un** `@RestControllerAdvice` con `ProblemDetail`, sin trazas | Solo el `GlobalExceptionHandler` de 001; las excepciones de `identity` extienden `ErrorResponseException` con `type` URN (research R10) | ✅ |
| IV | Listados paginados | N/A: no hay listados | — |
| IV | JSpecify; `Optional` en resultados únicos | `@NullMarked` en cada `package-info.java`; repositorios con `Optional<T>` | ✅ |
| V | JWT nativo; sin librerías ni filtros manuales | `NimbusJwtEncoder`/`JwtDecoder`; el login es un controlador, no un filtro | ✅ |
| V | Access ~15 min en `Authorization: Bearer` | `expires_in = 900` | ✅ |
| V | Refresh opaco, hasheado, revocable; cookie `HttpOnly` + `Secure` + `SameSite` explícito | SHA-256 en `BYTEA`; familia revocable; cookie `refresh_token`, `Path=/api/v1/auth`, `SameSite=Strict` por defecto | ✅ |
| V | `PasswordEncoder` delegante | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` | ✅ |
| V | Deny-by-default + `@PreAuthorize` | La cadena de plataforma no cambia; `identity` añade una cadena acotada a las 4 rutas públicas | ✅ |
| V | Roles `CUSTOMER`/`ADMIN` | `Role` enum; «CLIENTE» de la spec → `CUSTOMER` | ✅ |
| V | Acceso anónimo: catálogo, reseñas y los 4 `POST /api/v1/auth/*` | Exactamente `register`, `login`, `refresh`, `logout`; `/me` requiere token | ✅ |
| V | Anti IDOR; identidad del token | `/me` lee `sub`; ningún identificador llega del cliente | ✅ |
| VI | Solo tests vitales | Obligatorios + escenarios de la spec (sección Testing) | ✅ |
| VI | Tests obligatorios 1–4 | Arquitectura y smoke, invariantes de `User`, seguridad crítica y flujo feliz de los 5 endpoints | ✅ |
| VI | Testcontainers vía `@ServiceConnection`; sin H2 | `TestcontainersConfiguration` de 001 | ✅ |
| VI | Mocks: repositorios, `Clock` y capa de aplicación en `@WebMvcTest`; no entidades, records ni VO | Exactamente esos; `PasswordEncoder` y `JwtEncoder` reales; publicador de eventos sustituido por una lambda que captura | ✅ |
| VI | Cero advertencias (`-Xlint:all,-processing`) | Toolchain de 001 sin cambios | ✅ |
| VII | Clases concretas; sin interfaces especulativas | Ninguna interfaz propia salvo las de framework (Spring Data, MapStruct) | ✅ |
| VII | SRP por cohesión; trade-offs documentados | Tabla de trade-offs | ✅ |
| Hig. | Secretos y claves no versionados; por entorno | Par efímero en dev/test; rutas de PEM por variables en producción; arranque falla sin clave fuera de dev/test | ✅ |
| Hig. | DTO validados con `@Valid` | `RegisterRequest`, `LoginRequest` | ✅ |
| Hig. | CORS explícito; Actuator `health`/`info` | Sin cambios respecto a 001 | ✅ |
| Hig. | Sin contraseñas, tokens ni PII en logs | Solo `userId`/`familyId`; `toString()` enmascarado en DTO y propiedades (R13, R14) | ✅ |
| DoD | `spec.md`, `plan.md` y `tasks.md` aprobados; OpenAPI | `tasks.md` pendiente (`/speckit-tasks`); OpenAPI de springdoc contrastado con `contracts/openapi.yaml` | ⏳ |

**Resultado pre-Phase 0**: PASS, sin violaciones.

**Resultado post-Phase 1** (re-evaluado tras data-model y contracts): PASS. El diseño no añade
interfaces propias, dependencias ni rutas anónimas fuera de las cuatro permitidas.

## Evaluación de trade-offs (Principio VII)

| Elemento | Problema real que resuelve hoy | Alternativa simple descartada | Costo aceptado |
|----------|--------------------------------|-------------------------------|----------------|
| `Email` (record VO) | Normalizar (recorte + minúsculas) en un solo sitio; lo usan registro, login y admin inicial | `toLowerCase()` repetido en cada servicio: tres copias que pueden divergir | 1 record de ~15 líneas |
| `@ValidPassword` + `PasswordPolicy` | 8–64 caracteres **y** ≤72 bytes UTF-8, igual en el DTO y en el admin inicial | Solo `@Size(8,64)`: 64 caracteres multibyte superan los 72 bytes de bcrypt | 1 anotación, 1 validador y 1 clase estática |
| `AuthenticationService` y `RefreshTokenService` separados | Cambian por razones distintas: credenciales y bloqueo vs. ciclo de vida y rotación de tokens | Una clase de ~250 líneas con ambas responsabilidades | 1 dependencia entre dos clases concretas |
| `JwtKeyConfiguration` en `identity` (par único → `JwtEncoder` y `JwtDecoder`) | Con un par efímero, encoder y decoder deben compartir la misma instancia en memoria | Decoder en `platform` y encoder en `identity`: cada uno generaría su propio par y ningún token validaría, o `platform` tendría que depender de `identity` | `platform` deja de definir el decoder y depende de que exista un bean `JwtDecoder` |
| Cadena `SecurityFilterChain` propia de `identity` (`@Order(1)`, 4 rutas) | Rutas públicas sin que `platform` conozca `/api/v1/auth` | `permitAll` en `platform.SecurityConfiguration`: acopla `platform` a `identity` | 1 `@Configuration`; una segunda cadena que mantener |
| Excepciones de `identity` como `ErrorResponseException` + regla «conservar `type` ≠ `about:blank`» en `GlobalExceptionHandler` | Tipos de problema propios con un único advice (Constitución IV) y sin que `platform` conozca clases de `identity` | Un `@RestControllerAdvice` en `identity` (viola IV) o mapear las excepciones en `platform` (acopla los módulos) | Las excepciones de aplicación dependen de `spring-web`; ~5 líneas más en `handleExceptionInternal` |
| `RefreshCookieFactory` | Mismos atributos de cookie al emitirla (login, refresh) y al borrarla (logout) | Construir `ResponseCookie` tres veces en el controlador | 1 clase de ~30 líneas |
| `@Null String role` en `RegisterRequest` | Rechazar `role` con error de campo (spec: 400 de validación) | `fail-on-unknown-properties=true` global: cambia el contrato de toda la API | Un campo que el contrato exige ausente |
| Hash ficticio precalculado | Igualar el tiempo del login con correo inexistente (SC-002) | No igualarlo: el tiempo revelaría qué correos existen | 1 campo y 1 comparación bcrypt extra |

## Project Structure

### Documentation (this feature)

```text
specs/002-identity-access/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── openapi.yaml            # contrato HTTP de /api/v1/auth
│   └── configuration.md        # propiedades y variables de entorno
├── checklists/requirements.md
└── tasks.md                    # Phase 2 (/speckit-tasks; no lo genera este comando)
```

### Source Code (repository root)

```text
docs/adr/ADR-006-token-issuance.md             # emisión propia; reemplaza parcialmente a ADR-003 (origen de claves)
docs/adr/ADR-003-oauth2-resource-server.md     # MODIFICADO: nota «parcialmente reemplazado por ADR-006»
pom.xml                                        # Surefire: spring.profiles.active=test (sin dependencias nuevas)

src/main/java/com/mangateca/api/identity/      # módulo Modulith "identity"
├── package-info.java                          # @ApplicationModule + @NullMarked
├── UserRegistered.java                        # API pública: record (userId, role, occurredAt)
├── Role.java                                  # API pública: enum CUSTOMER, ADMIN
├── domain/
│   ├── package-info.java
│   ├── User.java                              # entidad JPA; bloqueo y contador en métodos de dominio
│   ├── RefreshToken.java                      # entidad JPA; rotado/revocado/expirado
│   ├── Email.java                             # VO record
│   ├── UserRepository.java                    # Spring Data
│   └── RefreshTokenRepository.java
├── application/
│   ├── package-info.java
│   ├── AuthenticationService.java             # register, login, me
│   ├── RefreshTokenService.java               # issue, rotate (detección de reutilización), revoke
│   ├── AccessTokenIssuer.java                 # JwtEncoder → access token (sub, roles, iat, exp, iss)
│   ├── PasswordPolicy.java                    # 8–64 caracteres, ≤72 bytes UTF-8
│   ├── BootstrapAdminRunner.java              # ApplicationRunner idempotente
│   └── EmailAlreadyInUseException, InvalidCredentialsException, InvalidRefreshTokenException   # extienden ErrorResponseException
├── web/
│   ├── package-info.java
│   ├── AuthController.java                    # 5 endpoints
│   ├── RegisterRequest, LoginRequest, TokenResponse, UserResponse   # records
│   ├── ValidPassword.java, ValidPasswordValidator.java
│   ├── UserMapper.java                        # MapStruct
│   └── RefreshCookieFactory.java
└── config/
    ├── package-info.java
    ├── IdentitySecurityConfiguration.java     # cadena pública @Order(1), PasswordEncoder, Clock
    ├── JwtKeyConfiguration.java               # par RSA (PEM o efímero dev/test) → JwtEncoder + JwtDecoder
    └── IdentityProperties.java                # record: jwt, TTL, cookie, bootstrap-admin

src/main/java/com/mangateca/api/platform/
├── security/DevJwtDecoderConfiguration.java   # ELIMINADO (lo sustituye JwtKeyConfiguration)
└── web/GlobalExceptionHandler.java            # MODIFICADO: conserva type/detail si type ≠ about:blank

src/main/resources/
├── application.yaml                           # + mangateca.identity.* (sin claves ni valores por defecto de issuer)
├── application-dev.yaml                       # + issuer de desarrollo
└── db/migration/V2__create_identity.sql

src/test/resources/
└── application-test.yaml                      # issuer de test (sin claves: par efímero)

src/test/java/com/mangateca/api/
├── TestSecurityConfiguration.java             # MODIFICADO: sin beans JwtEncoder/JwtDecoder; helper de tokens de prueba
├── ModularityTests.java                       # existente (cubre identity)
├── ApiApplicationTests.java                   # existente (smoke; valida V2 con ddl-auto=validate)
└── identity/
    ├── domain/
    │   ├── UserLockoutTests.java              # unitario sin Spring ni BD
    │   ├── UserRepositoryIT.java              # @DataJpaTest: unicidad lower(email)
    │   └── RefreshTokenRepositoryIT.java      # @DataJpaTest: revokeFamily
    ├── application/
    │   ├── AuthenticationServiceTests.java    # unitario: repos mock + Clock fijo
    │   └── RefreshTokenServiceTests.java      # unitario: repos mock + Clock fijo
    ├── web/AuthControllerTests.java           # @WebMvcTest: capa de aplicación mockeada
    ├── AuthFlowIT.java                        # @SpringBootTest + Testcontainers: flujo feliz + seguridad crítica
    ├── BootstrapAdminIT.java                  # @SpringBootTest + Testcontainers: US5
    └── UserRegisteredIT.java                  # @ApplicationModuleTest + Scenario
```

**Structure Decision**: un único proyecto Maven. `identity` es un módulo vertical; solo su
paquete raíz es API pública (`UserRegistered`, `Role`), y `domain`, `application`, `web` y `config`
son internos. `platform` e `identity` no se importan entre sí: se acuerdan por contrato (claims
`sub` y `roles`, bean `JwtDecoder` de Spring). Los tests de integración llevan el sufijo `IT` y los
unitarios y de slice el sufijo `Tests`, según la convención de 001.

## Testing

Alcance según la Constitución VI: solo los tests obligatorios y los escenarios de aceptación que
`spec.md` enumera. Los casos borde de la spec (registro concurrente, correo con espacios,
token malformado, varias instancias del admin inicial…) quedan resueltos por diseño (research R6,
R7, R11, R13) y no tienen tests propios.

### Tests obligatorios

| VI | Clase | Qué verifica |
|----|-------|--------------|
| 1. Arquitectura | `ModularityTests` (existente) | `ApplicationModules.verify()` con el módulo `identity` |
| 1. Smoke | `ApiApplicationTests` (existente) | El contexto arranca con el perfil `test`, par efímero y V2 validada (`ddl-auto=validate`) |
| 2. Invariantes de dominio | `UserLockoutTests` | 5 fallos → bloqueo 15 min; bloqueo vigente rechaza; vencido permite; login correcto reinicia el contador |
| 3. Seguridad crítica | `AuthFlowIT` | Las 4 rutas públicas responden sin token; `/me` sin token o con token expirado → 401; token `CUSTOMER` → 403 en `/api/v1/probes/admin` (controlador de test de 001); `/me` devuelve solo al usuario del `sub` (dos usuarios) |
| 4. Flujo feliz | `AuthFlowIT` | register → login → me → refresh → logout, con cookies y tokens reales |

### Escenarios de aceptación de la spec

| Escenario | Clase | Notas |
|-----------|-------|-------|
| US1 Registro exitoso (CUSTOMER, sin contraseña en la respuesta) | `AuthFlowIT` | Parte del flujo feliz |
| US1 … y se publica «usuario registrado» | `UserRegisteredIT` | `scenario.stimulate(() -> service.register(...)).andWaitForEventOfType(UserRegistered.class).matching(...).toArriveAndVerify(...)` |
| US1 Correo existente con otra capitalización | `UserRepositoryIT` (índice `lower(email)` de V2) y `AuthControllerTests` (409) | `@DataJpaTest` + Testcontainers |
| US1 Datos inválidos (5 ejemplos) | `AuthControllerTests` | Parametrizado; 400 con `errors[field]` |
| US1 Intento de elegir `ADMIN` | `AuthControllerTests` | 400 `errors[role]`; el servicio mockeado nunca se invoca |
| US2 Login exitoso (15 min / 7 días) | `AuthFlowIT` | `exp − iat = 900`, `expires_in = 900`, cookie `Max-Age=604800` |
| US2 Credenciales inválidas indistinguibles | `AuthFlowIT` | Compara estado, `type` y `detail` (correo inexistente vs. contraseña errónea) |
| US2 Bloqueo tras cinco fallos | `AuthenticationServiceTests` | `Clock` fijo: rechazo con la contraseña correcta hasta que vence el bloqueo; misma excepción |
| US2 Login exitoso reinicia el contador | `AuthenticationServiceTests` | Con 4 fallos previos |
| US2 Cuenta deshabilitada | `AuthenticationServiceTests` | Misma excepción que credenciales inválidas |
| US3 Renovación válida | `RefreshTokenServiceTests` | Token nuevo; el anterior queda rotado e inservible |
| US3 Reutilización de un token rotado | `RefreshTokenServiceTests` y `RefreshTokenRepositoryIT` | Rechazo + revocación de la familia; la consulta `revokeFamily` se prueba con `@DataJpaTest` |
| US3 Cierre de sesión | `RefreshTokenServiceTests` | Tras el logout, el token ya no renueva |
| US3 Cierre de sesión idempotente | `AuthControllerTests` (cookie ausente) y `RefreshTokenServiceTests` (desconocido, expirado) | Siempre 204 |
| US4 Consulta con token válido | `AuthFlowIT` y `AuthControllerTests` (`jwt()` de `spring-security-test`) | Sin datos sensibles |
| US4 Token expirado / sin token → 401 | `AuthFlowIT` | ProblemDetail de 001 (con la cadena deny-by-default real) |
| US4 CUSTOMER accede a recurso ADMIN → 403 | `AuthFlowIT` | Token real emitido por `identity` |
| US5 Arranque con configuración | `BootstrapAdminIT` | Crea un administrador |
| US5 Reinicio | `BootstrapAdminIT` | Segunda ejecución del runner: sigue habiendo uno |
| US5 Arranque sin configuración | `BootstrapAdminIT` | Ningún administrador |

### Mocks y dobles permitidos

| Test | Mock | Real |
|------|------|------|
| `UserLockoutTests` | — | `User`, `Instant` |
| `AuthenticationServiceTests`, `RefreshTokenServiceTests` | `UserRepository`, `RefreshTokenRepository`, `Clock.fixed` | `PasswordEncoder`, `AccessTokenIssuer` con un `JwtEncoder` sobre un par efímero, entidades. El `ApplicationEventPublisher` se sustituye por una lambda que captura eventos (no se mockea; la publicación se verifica en `UserRegisteredIT`) |
| `AuthControllerTests` | `AuthenticationService`, `RefreshTokenService` (`@MockitoBean`) | Validación, `GlobalExceptionHandler` (lo incluye el slice), e importados: `IdentitySecurityConfiguration`, `JwtKeyConfiguration` (registra `IdentityProperties`), `UserMapperImpl`, `RefreshCookieFactory` |
| `*IT` | — | Todo, con Testcontainers |

### Configuración de tests

- Perfil `test` activado para todo `./mvnw verify` con `spring.profiles.active=test` en
  `systemPropertyVariables` de Surefire. Con él, `JwtKeyConfiguration` genera el par efímero. En
  el IDE hay que activar el mismo perfil.
- Los tests de 001 que firmaban tokens con `TestSecurityConfiguration` pasan a usar el
  `JwtEncoder` de `identity`. El caso «firmado con otra clave» de `SecurityIT` sigue generando su
  propio par local.

## Impacto en la feature 001

| Elemento | Cambio |
|----------|--------|
| `DevJwtDecoderConfiguration` | Se elimina; lo sustituye `JwtKeyConfiguration` de `identity` |
| Contrato de configuración 001 (`spring.security.oauth2.resourceserver.jwt.public-key-location`) | Se sustituye por `mangateca.identity.jwt.*` ([contracts/configuration.md](contracts/configuration.md)) |
| `TestSecurityConfiguration` | Sin beans `JwtEncoder`/`JwtDecoder` (chocarían con los de `identity`); queda un helper para acuñar tokens de prueba |
| `SecurityConfiguration` (`jwtAuthenticationConverter`, cadena) | Sin cambios: ya mapea `roles` → `ROLE_*` y toma el `JwtDecoder` del contexto |
| `GlobalExceptionHandler` | Conserva `type` y `detail` de un `ProblemDetail` cuyo `type` ≠ `about:blank`; con `about:blank`, sin cambios (research R10) |
| ADR-003 | Nota de reemplazo parcial por ADR-006 (origen de claves y propietario del decoder) |

## Orden de implementación (guía para `/speckit-tasks`)

1. **ADR-006** y nota en ADR-003.
2. **Claves y decoder**: `IdentityProperties`, `JwtKeyConfiguration`, perfil `test` en Surefire;
   retirar `DevJwtDecoderConfiguration`; adaptar `TestSecurityConfiguration` y los tests de 001.
   Verificar que 001 sigue en verde antes de continuar.
3. **Persistencia**: `V2__create_identity.sql`, entidades y repositorios (lo valida el smoke test).
4. **Dominio y servicios**: `Email`, `User`, `RefreshToken`, `PasswordPolicy`, `AccessTokenIssuer`,
   `RefreshTokenService`, `AuthenticationService` y sus tests unitarios.
5. **Web**: DTO, mapper, cookie, controlador, handler, cadena pública y `AuthControllerTests`.
6. **Evento**: `UserRegistered` y `UserRegisteredIT`.
7. **Administrador inicial**: runner y `BootstrapAdminIT`.
8. **Cierre**: `AuthFlowIT`, OpenAPI contrastado con `contracts/openapi.yaml`, README y quickstart.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Arrancar en producción sin perfil y con el par efímero | El par efímero solo se genera si el perfil activo es `dev` o `test`; en cualquier otro caso, la falta de clave aborta el arranque con un mensaje explícito (R4). Validado a mano en el quickstart |
| Tokens de dev inválidos tras reiniciar (par efímero) | Aceptado, igual que en 001: basta con volver a iniciar sesión |
| Cookie `Secure` no guardada en Safari sobre `http://localhost` | Chrome y Firefox tratan `localhost` como contexto seguro; en Safari, HTTPS local. `Secure` no se relaja (Constitución V) |
| Refresh simultáneo en varias pestañas cierra la sesión | Refresh single-flight en el frontend (Web Locks API), feature 012 (R18) |
| Frontend y API en dominios distintos en producción | `SameSite=None` y protección CSRF en `/refresh` (R19) |
| Crecimiento de `refresh_tokens` (sin purga) | Fuera de alcance; índices por `family_id` y `user_id`; purga futura anotada en R7 |
| `role`/`enabled` cambian pero el access token vigente conserva los valores antiguos (≤15 min) | Límite aceptado; el refresh relee al usuario |
| Contraseñas de más de 72 bytes en bcrypt | `PasswordPolicy` rechaza antes de codificar; comprobación manual en el quickstart (no es un escenario de la spec, VI) |

## Complexity Tracking

Sin desviaciones: el Constitution Check (v4.0.0) pasa sin excepciones que justificar.
