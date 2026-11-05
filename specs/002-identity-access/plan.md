# Implementation Plan: Identity & Access

**Branch**: `002-identity-access` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-identity-access/spec.md`

## Summary

Añadir el módulo Spring Modulith `identity`: registro de clientes, login, renovación con
rotación, logout, identidad actual (`/me`) y administrador inicial por entorno. La propia API
emite los access tokens (JWT RS256 con `NimbusJwtEncoder`, 15 min) y la plataforma de la
feature 001 los valida con la clave pública (`JwtDecoder`). Los refresh tokens son opacos
(256 bits, `SecureRandom`), se guardan solo como hash SHA-256 y rotan dentro de una familia
(`family_id`); reutilizar uno rotado revoca toda la familia.

Decisiones que se apartan de lo pedido o lo precisan (detalle en [research.md](research.md)):

1. **El refresh token viaja en una cookie `HttpOnly; Secure; SameSite`, no en el cuerpo del
   login** (Constitución V, que prevalece). El cuerpo trae `access_token`, `token_type` y
   `expires_in`.
2. **Sin umbral de cobertura ≥80%**: la Constitución VI lo prohíbe como requisito y ADR-005
   descartó JaCoCo. Los tests pedidos sí se planifican (ver Testing).
3. **`event_publication` solo recibe filas si hay un listener transaccional.** `identity` no tiene
   consumidores en esta feature, así que la persistencia del evento se verifica con un listener
   que solo existe en tests (R12).
4. **El rechazo del rol `ADMIN` en el registro** se modela con un campo `role` con `@Null` en el
   DTO, porque Jackson ignora por defecto las propiedades desconocidas (R11).
5. **Prerrequisito de gobierno**: la Constitución V debe enmendarse para permitir los
   endpoints públicos de `/api/v1/auth` antes de implementar (supuesto de la spec).

## Technical Context

**Language/Version**: Java 25 (LTS), `--release 25`

**Primary Dependencies**: Spring Boot 4.1.2 (Security 7), Spring Modulith 2.1.2, MapStruct
1.6.3, springdoc-openapi 3.2.0. **Sin dependencias nuevas**: `NimbusJwtEncoder`, Nimbus JOSE,
`RsaKeyConverters` y `PasswordEncoderFactories` ya llegan con `oauth2-resource-server` y
`spring-security-core`. Por eso no hay ADR de dependencia; ADR-006 documenta la decisión de
arquitectura (emisión propia).

**Storage**: PostgreSQL 18; migración `V2__create_identity.sql` (`users`, `refresh_tokens`).
V1 no creó extensiones, así que el correo es único con un índice sobre `lower(email)`, no con
`citext`.

**Testing**: JUnit 5 + AssertJ; `@WebMvcTest`, `@DataJpaTest` + Testcontainers
`@ServiceConnection`, `spring-security-test`, `@ApplicationModuleTest` + `Scenario`. `Clock`
inyectable como única frontera simulada (Constitución VI).

**Target Platform**: servidor Linux / contenedor OCI; desarrollo con Docker.

**Project Type**: web-service (monolito modular)

**Performance Goals**: login y registro dominados por bcrypt (coste por defecto 10, ~50–100 ms).
Sin objetivos de throughput.

**Constraints**: cero advertencias de compilación; sin secretos de producción en el repo; ninguna
API deprecada; contraseña nunca en logs, respuestas ni `toString()`; respuestas indistinguibles
(contenido y tiempo) entre correo inexistente y contraseña errónea.

**Scale/Scope**: 1 módulo nuevo, 1 migración, 2 tablas, 5 endpoints, 1 runner, ~25 clases de
producción, ADR-006.

## Constitution Check

*GATE: pasar antes de Phase 0. Re-evaluado tras Phase 1.*

| # | Regla | Cómo se cumple | Estado |
|---|-------|----------------|--------|
| I | Módulos verticales, sin capas globales | `identity/{web,application,domain,config}` dentro del módulo | ✅ |
| I | Límites verificados | `ModularityTests` ya existente; `identity` no importa nada de `platform` ni al revés (R14) | ✅ |
| I | Solo API pública entre módulos | Raíz de `identity`: `UserRegistered` y `Role`. Los demás módulos usan el claim `sub` | ✅ |
| II | Invariantes en el dominio; VO | `Email` (record con normalización/validación), `User` con bloqueo en métodos de dominio, `RefreshToken` con estados | ✅ |
| III | Java 25, Boot 4.1.x, APIs estables | `NimbusJwtEncoder`, `RsaKeyConverters`, `PasswordEncoderFactories`: APIs estables | ✅ |
| III | ADR por dependencia nueva | Ninguna dependencia nueva. ADR-006 igualmente (decisión de arquitectura) | ✅ |
| III | DTO/eventos/config como `record` | Todos los DTO, `UserRegistered`, `Email` y `*Properties` son records | ✅ |
| III | Lombok solo en entidades | No se usa Lombok (entidades con constructor protegido y accesores a mano) | ✅ |
| IV | Flyway inmutable, `validate`, OSIV off | V2 nueva; V1 intacta; esquema validado por `@DataJpaTest` | ✅ |
| IV | Entidades no expuestas; DTO + MapStruct | `UserMapper` (MapStruct) entidad → `UserResponse` | ✅ |
| IV | `/api/v1`, plural | `/api/v1/auth/*`: `auth` es un espacio de acciones, no una colección. Ver Complexity Tracking | ⚠️ justificado |
| IV | `ProblemDetail`; sin trazas | `IdentityExceptionHandler` acotado a los controladores de identity; tipos documentados | ✅ |
| IV | `@Transactional` en aplicación; `readOnly` en consultas | Servicios de `application` | ✅ |
| IV | JSpecify | `@NullMarked` en cada `package-info.java` nuevo | ✅ |
| V | JWT nativo, sin libs ni filtros manuales | `NimbusJwtEncoder`/`JwtDecoder`; el login es un controlador, no un filtro | ✅ |
| V | Access 15 min en `Authorization: Bearer` | `expires_in = 900` | ✅ |
| V | Refresh opaco, hasheado, revocable, cookie `HttpOnly`+`Secure`+`SameSite` explícito | Cookie `refresh_token`, `Path=/api/v1/auth`, `SameSite=Strict` configurable | ✅ |
| V | `PasswordEncoder` delegante | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` | ✅ |
| V | Deny-by-default; `@PreAuthorize` | Cadena de plataforma intacta; `identity` añade una cadena acotada a 4 rutas | ✅ |
| V | Acceso anónimo limitado a catálogo y reseñas | Registro/login/refresh/logout son públicos | ❌ **requiere enmienda previa** |
| V | Identidad desde el token | `/me` usa `sub`; ningún parámetro del cliente | ✅ |
| VI | Tests obligatorios | Seguridad crítica, flujo feliz de auth y `ApplicationModules.verify()`: ver Testing | ✅ |
| VI | Testcontainers PostgreSQL; sin H2; mocks solo en I/O | `@ServiceConnection`; solo `Clock` se fija | ✅ |
| VI | Sin umbral de cobertura; cero advertencias | No se añade JaCoCo | ✅ (ver resumen, punto 2) |
| VII | Clases concretas; trade-offs | Sin interfaces propias; ver tabla de trade-offs | ✅ |
| Hig. | Secretos por entorno; PII/contraseñas/tokens fuera de logs | R3, R4, R13, R14 | ✅ |

**Resultado pre-Phase 0**: la única violación es la fila ❌ (acceso anónimo). La spec ya la
declara como prerrequisito. **No bloquea el plan, pero sí `/speckit-implement`**: debe
mergearse antes la enmienda de la Constitución (Governance: PR dedicado; relajar una regla
`MUST` es cambio MAJOR) con el texto equivalente a «...salvo los endpoints de registro, login,
renovación y cierre de sesión». Se crea como tarea T001 en `/speckit-tasks`.

**Resultado post-Phase 1**: sin cambios; el diseño no añade violaciones.

## Evaluación de trade-offs (Principio VII)

| Elemento | Problema real que resuelve hoy | Alternativa simple descartada | Costo aceptado |
|----------|--------------------------------|-------------------------------|----------------|
| `Email` (record VO) | Normalizar (trim + minúsculas) en un solo sitio; se usa en registro, login y admin inicial | `String.toLowerCase()` repetido en cada servicio: tres copias que pueden divergir | 1 record de ~15 líneas |
| `@ValidPassword` + `PasswordPolicy` | 8–64 caracteres **y** ≤72 bytes UTF-8, igual en el DTO y en el admin inicial | `@Size(8,64)` solo: 64 caracteres multibyte superan los 72 bytes de bcrypt | 1 anotación + 1 validador + 1 clase estática |
| `AuthenticationService` y `RefreshTokenService` separados | Cambian por razones distintas: credenciales/bloqueo vs. ciclo de vida y rotación de tokens; los tests pedidos los separan | Una sola clase de ~250 líneas con ambas | 1 dependencia entre dos clases concretas |
| Cadena `SecurityFilterChain` propia de `identity` (`@Order(1)`, 4 rutas) | Permitir rutas públicas sin que `platform` conozca `/api/v1/auth` (límite de módulos) | Añadir los `permitAll` en `platform.SecurityConfiguration`: acopla `platform` a `identity` | 1 `@Configuration`; segunda cadena que mantener |
| `IdentityExceptionHandler` (`basePackageClasses`) | Tipos de problema propios (`email-already-in-use`, `invalid-credentials`, `invalid-refresh-token`) sin que `platform` los conozca | Meter los 3 tipos en `GlobalExceptionHandler`: acopla `platform` a excepciones de `identity` | 1 `@RestControllerAdvice` de ~40 líneas |
| `RefreshCookieFactory` | Construir y borrar la cookie con los mismos atributos en login, refresh y logout | Armar `ResponseCookie` en el controlador, 3 veces | 1 clase de ~30 líneas |
| `@Null String role` en `RegisterRequest` | Rechazar `role` con error de campo (spec: 400 de validación) | `fail-on-unknown-properties=true` global: cambia el comportamiento de todo el API | Un campo que en el contrato debe ir ausente |
| Dos pares de claves de desarrollo (dev, test) versionados | Firmar en dev/test sin variables; `./mvnw spring-boot:run` sigue sin configuración | Par efímero por arranque (001): el `JwtEncoder` de identity y el `JwtDecoder` de plataforma necesitan **el mismo** par, y el test de contexto lo recrea cada vez | 4 archivos PEM marcados «solo desarrollo» + un test que prohíbe usarlos fuera de dev/test |
| `dummy hash` precalculado | Igualar el tiempo de login con correo inexistente (SC-002) | No igualar: el tiempo de respuesta revelaría qué correos existen | 1 campo y 1 comparación bcrypt extra |

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
docs/adr/ADR-006-token-issuance.md             # emisión propia vs. Spring Authorization Server
dev/keys/                                      # SOLO DESARROLLO: README.md, jwt-dev-private.pem, jwt-dev-public.pem
pom.xml                                        # sin dependencias nuevas

src/main/java/com/mangateca/api/identity/      # módulo Modulith "identity"
├── package-info.java                          # @ApplicationModule + @NullMarked
├── UserRegistered.java                        # API pública: record (userId, role, occurredAt)
├── Role.java                                  # API pública: enum CUSTOMER, ADMIN
├── domain/
│   ├── package-info.java
│   ├── User.java                              # entidad JPA; bloqueo y contador en métodos de dominio
│   ├── RefreshToken.java                      # entidad JPA; rotated/revoked/expired
│   ├── Email.java                             # VO record
│   ├── UserRepository.java                    # Spring Data
│   └── RefreshTokenRepository.java
├── application/
│   ├── package-info.java
│   ├── AuthenticationService.java             # register, login, me
│   ├── RefreshTokenService.java               # issue, rotate (reuse detection), revoke
│   ├── AccessTokenIssuer.java                 # JwtEncoder → access token (sub, roles, iat, exp, iss)
│   ├── PasswordPolicy.java                    # 8–64 chars, ≤72 bytes UTF-8
│   ├── BootstrapAdminRunner.java              # ApplicationRunner idempotente
│   └── (excepciones) EmailAlreadyInUseException, InvalidCredentialsException, InvalidRefreshTokenException
├── web/
│   ├── package-info.java
│   ├── AuthController.java                    # 5 endpoints
│   ├── RegisterRequest.java, LoginRequest.java, TokenResponse.java, UserResponse.java   # records
│   ├── ValidPassword.java, ValidPasswordValidator.java
│   ├── UserMapper.java                        # MapStruct
│   ├── RefreshCookieFactory.java
│   └── IdentityExceptionHandler.java          # @RestControllerAdvice acotado
└── config/
    ├── package-info.java
    ├── IdentitySecurityConfiguration.java     # cadena pública @Order(1), PasswordEncoder, JwtEncoder, Clock
    ├── IdentityProperties.java                # record: private-key, access/refresh TTL, cookie, bootstrap-admin
    └── RsaKeys.java                           # carga PEM con RsaKeyConverters

src/main/java/com/mangateca/api/platform/security/
├── JwtVerificationProperties.java             # NUEVO: record (public-key, issuer)
├── JwtDecoderConfiguration.java               # NUEVO: JwtDecoder explícito con validador de issuer
└── DevJwtDecoderConfiguration.java            # ELIMINADO (sustituido)

src/main/resources/
├── application.yaml                           # + claves mangateca.security.jwt / mangateca.identity.*
├── application-dev.yaml                       # + rutas a dev/keys
└── db/migration/V2__create_identity.sql

src/test/resources/
├── application.yaml                           # propiedades de test (claves test, TTL)
└── keys/{jwt-test-private.pem, jwt-test-public.pem}   # SOLO TEST

src/test/java/com/mangateca/api/
├── TestSecurityConfiguration.java             # MODIFICADO: ya no define JwtEncoder/JwtDecoder
├── ModularityTests.java                       # sin cambios (cubre identity)
└── identity/
    ├── domain/
    │   ├── UserLockoutTests.java              # unitario: 5 fallos, 15 min, reinicio, expiración
    │   ├── EmailTests.java
    │   └── RefreshTokenRepositoryIT.java, UserRepositoryIT.java, IdentitySchemaIT.java   # @DataJpaTest + V2
    ├── application/
    │   ├── AuthenticationServiceTests.java    # unitario con Clock fijo y repos en memoria de test
    │   ├── RefreshTokenServiceTests.java      # rotación, reutilización, expiración
    │   ├── PasswordPolicyTests.java
    │   └── BootstrapAdminRunnerIT.java        # idempotencia, config ausente/incompleta, sin log de contraseña
    ├── web/
    │   ├── AuthControllerTests.java           # @WebMvcTest: contratos, cookie, errores
    │   └── AuthSecurityTests.java             # spring-security-test: públicas vs. protegidas, 401/403
    └── UserRegisteredIT.java                  # @ApplicationModuleTest + Scenario + listener de test
```

**Structure Decision**: un único proyecto Maven. `identity` es un módulo vertical; solo su
paquete raíz es API pública (`UserRegistered`, `Role`), y `domain/application/web/config` son
internos. `platform` y `identity` no se importan entre sí: se acuerdan por contrato de datos
(claim `roles`, claim `sub`, propiedad `mangateca.security.jwt.issuer`).

Los tests con base de datos usan el sufijo `IT`; los unitarios y de slice, `Tests`, según la
convención de la feature 001 (Surefire ejecuta ambos en `./mvnw verify`).

## Testing

La Constitución VI limita los tests obligatorios; esta feature planifica además los tests que
pidió el usuario, que cuentan como petición explícita para los opcionales.

| Petición | Clase(s) | Cubre |
|----------|----------|-------|
| Unitarios: autenticación, bloqueo, rotación | `AuthenticationServiceTests`, `UserLockoutTests`, `RefreshTokenServiceTests` | FR-006…FR-014, SC-004, SC-005 |
| `@WebMvcTest` de controladores | `AuthControllerTests` | Contrato HTTP, cookie, ProblemDetail, rechazo de `role` |
| `@DataJpaTest` + Testcontainers: repos y V2 | `UserRepositoryIT`, `RefreshTokenRepositoryIT`, `IdentitySchemaIT` | Unicidad `lower(email)`, índices, `ddl-auto=validate` |
| Seguridad con `spring-security-test` | `AuthSecurityTests` | FR-019, FR-021: rutas públicas, `/me` 401, `CUSTOMER` → 403 en recurso `ADMIN` |
| `@ApplicationModuleTest` + `Scenario` | `UserRegisteredIT` | `UserRegistered` publicado dentro de la transacción y fila en `event_publication` |
| Obligatorios (VI) | `ModularityTests` (existente), `ApiApplicationTests` (existente), `AuthFlowIT` (flujo feliz de punta a punta con `@SpringBootTest`) | Arquitectura, arranque, flujo feliz de auth |

Notas:

- `@DataJpaTest` con `@AutoConfigureTestDatabase(replace = NONE)` y `TestcontainersConfiguration`.
- La rotación concurrente (dos refresh simultáneos con el mismo token) se prueba en
  `RefreshTokenServiceTests` a nivel de lógica y se valida a mano en el quickstart.
- Los tests existentes de 001 que firmaban tokens con `TestSecurityConfiguration` pasan a usar
  el `JwtEncoder` real y las claves de test (ver «Impacto en la feature 001»).

## Impacto en la feature 001

| Elemento | Cambio |
|----------|--------|
| `DevJwtDecoderConfiguration` | Se elimina; la sustituye `JwtDecoderConfiguration` (todos los perfiles) |
| Propiedad `spring.security.oauth2.resourceserver.jwt.public-key-location` (contrato de configuración 001) | Se reemplaza por `mangateca.security.jwt.public-key-location` y `.issuer`, ambas obligatorias. Motivo: validar `iss` de forma explícita, no dependiendo de cómo Boot combine `public-key-location` con `issuer-uri` |
| `TestSecurityConfiguration` | Deja de definir `JwtDecoder`/`JwtEncoder`; ofrece un helper para acuñar tokens de prueba con el `JwtEncoder` real. El caso «firmado con otra clave» sigue usando un par efímero local |
| ADR-003 | Se anota que la alternativa «clave pública de dev versionada» queda superada por ADR-006 (ahora hay clave privada de uso real en dev/test) |
| `SecurityConfiguration.jwtAuthenticationConverter` | Sin cambios: ya mapea `roles` → `ROLE_*` |

## Orden de implementación (guía para `/speckit-tasks`)

1. **Gobierno**: enmienda de la Constitución V (prerrequisito) y ADR-006.
2. **Claves y configuración de plataforma**: `dev/keys`, claves de test, `JwtVerificationProperties`,
   `JwtDecoderConfiguration`; retirar `DevJwtDecoderConfiguration`; adaptar tests 001. Verificar
   que 001 sigue en verde antes de continuar.
3. **Persistencia**: `V2__create_identity.sql`, entidades, repositorios, `IdentitySchemaIT`.
4. **Dominio y servicios**: `Email`, `User` (bloqueo), `RefreshToken`, `PasswordPolicy`,
   `AccessTokenIssuer`, `RefreshTokenService`, `AuthenticationService` con sus tests unitarios.
5. **Web**: DTO, mapper, cookie, controlador, handler, cadena de seguridad pública.
6. **Evento**: `UserRegistered` y `UserRegisteredIT`.
7. **Administrador inicial**: runner y su test.
8. **Cierre**: OpenAPI verificado contra `contracts/openapi.yaml`, README, quickstart ejecutado.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Clave de desarrollo usada en producción | Sin valores por defecto en `application.yaml`; solo `application-dev.yaml` y los `src/test/resources` las referencian; test que falla el arranque sin perfil dev si faltan las propiedades; `dev/keys/README.md` y cabecera en cada PEM |
| Cookie `Secure` no se guarda en Safari sobre `http://localhost` | Chrome/Firefox tratan `localhost` como contexto seguro; en Safari usar HTTPS local. No se relaja `Secure` (Constitución V) |
| `SameSite=Strict` falla si el frontend y la API quedan en sitios distintos en producción | `SameSite` configurable (`Strict`/`Lax`/`None`) con valor explícito siempre; decidir al desplegar |
| Dos pestañas refrescan a la vez con el mismo token: la segunda cuenta como reutilización y cierra la sesión | Comportamiento aceptado y documentado (sin ventana de gracia: YAGNI). Se revisa si el frontend lo sufre |
| Crecimiento de `refresh_tokens` (sin purga) | Fuera de alcance; índice por `family_id` y `user_id`; tarea de purga futura anotada en research R7 |
| `Role`/`enabled` cambian pero el access token vigente conserva los antiguos (≤15 min) | Documentado como límite aceptado; el refresh relee el usuario |
| Registro de eventos sin filas por falta de listener | R12: listener solo de test; el primer consumidor real (feature futura) hereda la persistencia |
| Bcrypt y contraseñas >72 bytes | `PasswordPolicy` rechaza antes de codificar; test con 64 caracteres de 4 bytes |

## Complexity Tracking

| Desviación | Por qué | Alternativa descartada |
|------------|---------|------------------------|
| Ruta `/api/v1/auth/*` con verbos (`login`, `logout`, `refresh`) y no sustantivos en plural (Constitución IV) | Son comandos de un flujo de autenticación, no CRUD de un recurso; el usuario fijó estas rutas | `/api/v1/sessions` (REST puro): diverge del contrato pedido y del esquema común de frontends. Se propone anotar en la enmienda de la Constitución (junto a la excepción de acceso anónimo) que `/api/v1/auth` queda exento |
| Dos pares de claves RSA privadas versionados (dev y test) | Ver trade-offs. Contradice la alternativa descartada en ADR-003, que se enmienda | Generar el par en cada arranque: imposible de compartir entre el encoder y el decoder tras reiniciar y rompe `spring-boot-devtools` |
