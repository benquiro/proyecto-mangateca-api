# Research: Identity & Access

**Feature**: `002-identity-access` | **Fecha**: 2026-11-05 | **Constitución**: v4.0.0

Fuentes: documentación oficial vía Context7 (Spring Security 7.1.1, Spring Boot 4.1.0, Spring
Modulith 2.1.0) y el código de la feature 001. Las afirmaciones sobre el comportamiento exacto de
una librería que la documentación consultada no cierra se marcan **«verificar en
implementación»**.

## R1. Emisión de tokens: propia (`NimbusJwtEncoder`) vs. servidor de autorización dedicado

- **Decision**: la API emite sus propios JWT con `NimbusJwtEncoder` (RS256). Se documenta en
  ADR-006.
- **Rationale**: el Principio V pide soporte nativo (`NimbusJwtEncoder`/`JwtDecoder`). Hay un
  único emisor y un único consumidor dentro del mismo monolito. Un servidor de autorización
  separado añadiría un proceso, endpoints OAuth2 que nadie consume y gestión de clientes
  registrados. Los endpoints `/api/v1/auth/*` son los que fija la spec.
- **Alternatives considered**:
  - *Spring Authorization Server*: adecuado con varias aplicaciones cliente, SSO o federación.
    Hoy no existe ninguna de esas necesidades (YAGNI, Principio VII). **Condición de revisión**
    que ADR-006 debe registrar: una segunda aplicación consumidora, login social o MFA.
  - *Proveedor externo (Keycloak, Auth0)*: dependencia operativa y de coste no pedida.
  - *HMAC (HS256)*: obligaría a compartir el secreto de firma con cada verificador (ya
    descartado en ADR-003).

## R2. Estructura del token de acceso

- **Decision**: JWS compacto RS256 con `JwsHeader.with(SignatureAlgorithm.RS256)` y estos claims:

  | Claim | Valor |
  |-------|-------|
  | `sub` | UUID del usuario (texto) |
  | `roles` | Lista de strings: `["CUSTOMER"]` o `["ADMIN"]` |
  | `iss` | Valor de `mangateca.identity.jwt.issuer` |
  | `iat` | Instante de emisión (del `Clock`) |
  | `exp` | `iat` + 15 min (`mangateca.identity.access-token-ttl`) |

  Sin `jti`, `aud`, `kid` ni claims de correo o nombre: ninguna feature los necesita
  (Principio VII) y el token no debe llevar PII.
- **Rationale**: el `JwtAuthenticationConverter` de la feature 001 ya lee `roles` y antepone
  `ROLE_`, así que no requiere cambios. El `JwtDecoder` valida firma, `exp`/`nbf` e `iss` (R3).
- **Roles**: «cliente»/«CLIENTE» de la spec se representa siempre como `Role.CUSTOMER`
  (Constitución V); no existe un rol `CLIENTE` en código, claims ni base de datos.
- **Alternatives considered**: un claim `scope`/`scp` (convención OAuth2; la 001 ya fijó
  `roles`); `jti` para listas de revocación (el access token no se revoca: dura como máximo
  15 min, según la spec).

## R3. Propietario del par de claves y del `JwtDecoder`

- **Decision**: `identity` define, en `JwtKeyConfiguration`, un único par RSA y a partir de él
  dos beans:
  - `JwtEncoder`: `new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)))`.
  - `JwtDecoder`: `NimbusJwtDecoder.withPublicKey(publicKey).build()` con
    `JwtValidators.createDefaultWithIssuer(issuer)`.

  La cadena de la plataforma (`oauth2ResourceServer().jwt(...)`) toma el `JwtDecoder` del
  contexto por tipo. Se retira `DevJwtDecoderConfiguration` y la propiedad de Boot
  `spring.security.oauth2.resourceserver.jwt.public-key-location` del contrato de 001.
- **Rationale**: con un par efímero en dev/test (R4), encoder y decoder **tienen que compartir la
  misma instancia en memoria**. Si cada módulo creara su propio par, ningún token validaría. Si
  el par viviera en `platform`, `identity` dependería de un tipo interno de `platform`. Si viviera
  en `identity` pero el decoder siguiera en `platform`, `platform` dependería de `identity`. Con el
  diseño elegido, el único vínculo es la interfaz `JwtDecoder` de Spring Security, que no es un tipo
  de ningún módulo. Además, el validador de `iss` explícito evita depender de cómo combina Boot
  `public-key-location` con `issuer-uri`, que la documentación consultada no aclara.
- **Consecuencia**: `platform` exige que exista un bean `JwtDecoder` en el contexto. Si se
  desactivara `identity`, la aplicación no arrancaría. Es aceptable porque `identity` es la
  autoridad de tokens del sistema.
- **Alternatives considered**: mantener el decoder en `platform` y que lea la clave pública por
  propiedades (no funciona con el par efímero); usar el autoconfigurador de Boot para el decoder
  (mismo problema).

## R4. Origen de las claves por entorno (ninguna clave versionada)

- **Decision**: `JwtKeyConfiguration` resuelve el par así:
  1. Si `mangateca.identity.jwt.private-key-location` **y** `.public-key-location` están definidas,
     carga los PEM como `Resource` y los convierte con `RsaKeyConverters.pkcs8()` (privada,
     PKCS#8) y `RsaKeyConverters.x509()` (pública), API estable de `spring-security-core`.
     Comprueba que ambas forman un par (mismo módulo); si no, el arranque falla.
  2. Si no hay ninguna definida y el entorno activa `dev` o `test`
     (`environment.matchesProfiles("dev | test")`), genera un par RSA 2048 efímero con
     `KeyPairGenerator` y registra un INFO sin material de clave («par efímero de desarrollo; los
     tokens dejan de valer al reiniciar»).
  3. En cualquier otro caso (solo una definida, o ninguna fuera de dev/test), el arranque falla
     con `IllegalStateException` y un mensaje que nombra las variables que faltan.
  - **Producción**: las rutas llegan por variables de entorno y apuntan a los secretos montados:
    `MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem`,
    `MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem` y
    `MANGATECA_IDENTITY_JWT_ISSUER`.
  - **Dev**: `./mvnw spring-boot:run` (perfil `dev`) arranca sin variables; `issuer` en
    `application-dev.yaml`. Si se definen las rutas, se usan (útil para que los tokens sobrevivan a
    un reinicio).
  - **Test**: Surefire fija `spring.profiles.active=test`; `issuer` en
    `src/test/resources/application-test.yaml`. Sin claves en el árbol de tests.
- **Rationale**: cumple la Higiene Operacional (claves de firma MUST NOT versionarse; MUST
  inyectarse por entorno). Montar el PEM como secreto y pasar su ruta por variable de entorno es
  más robusto que meter el PEM entero en la variable, que es frágil con los saltos de línea y
  aparece en `/proc` y en los volcados de entorno.
- **Alternatives considered**:
  - *Pares de dev/test versionados* (versión anterior de este plan): descartado por la Higiene
    Operacional.
  - *PEM dentro de la variable de entorno*: descartado por lo anterior. Si un despliegue lo exige,
    se añade como segunda fuente sin cambiar el contrato.
  - *Generar el par efímero en cualquier perfil sin clave*: un despliegue mal configurado
    arrancaría con tokens que caducan en cada reinicio y que difieren entre instancias. Fallar
    es más seguro.
- **ADR-006 reemplaza parcialmente a ADR-003** en este punto. ADR-003 dejaba la validación a cargo
  del Resource Server configurado por propiedades de Boot (`public-key-location`) y un decoder
  efímero solo en dev (`DevJwtDecoderConfiguration`), porque la feature 001 no emitía tokens.
  ADR-006 traslada a `identity` el par de claves, el origen por entorno y el `JwtDecoder`. ADR-003
  sigue vigente en lo demás: Resource Server nativo, RS256, sin librerías JWT de terceros y el
  starter `spring-boot-starter-security-oauth2-resource-server`. ADR-003 recibe una nota
  «parcialmente reemplazado por ADR-006».

## R5. Contraseñas: bcrypt delegante y límite de 72 bytes

- **Decision**: bean `PasswordEncoder` =
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (prefijo `{bcrypt}`, coste por
  defecto). `password_hash VARCHAR(255)` deja margen para futuros algoritmos. Política:
  **8–64 caracteres y ≤72 bytes UTF-8**, validada con `@ValidPassword` (DTO) y `PasswordPolicy`
  (admin inicial).
- **Rationale**: FR-003 fija 64 caracteres, pero un carácter UTF-8 ocupa hasta 4 bytes, así que
  64 caracteres pueden llegar a 256 bytes, y bcrypt solo procesa los primeros 72. Sin la
  comprobación de bytes habría truncamiento silencioso o un error, según la versión de Spring
  Security. Rechazar con 400 antes de codificar mantiene el contrato. No es un escenario de la
  spec, así que se comprueba a mano en el quickstart (Principio VI).
- **Alternatives considered**:
  - *Prehash SHA-256 + Base64 antes de bcrypt*: elimina el límite, pero es un esquema no estándar
    que complica migrar de algoritmo.
  - *Argon2/scrypt*: el Principio V pide bcrypt por defecto; `{argon2}` sigue disponible a través
    del encoder delegante.
- **Tiempo constante** (SC-002): `AuthenticationService` calcula al arrancar un
  `DUMMY_HASH = encoder.encode(<aleatorio>)`. Si el correo no existe o la cuenta está deshabilitada
  o bloqueada, ejecuta igualmente `matches(password, DUMMY_HASH)` antes de responder 401.

## R6. Bloqueo por intentos fallidos

- **Decision**: la lógica vive en la entidad `User` (Principio II) y recibe el instante actual del
  `Clock`:
  - `registerFailedLogin(now)`: si `locked_until` venció, reinicia el contador antes de sumar;
    luego incrementa y, al llegar a 5, fija `locked_until = now + 15 min`.
  - `registerSuccessfulLogin(now)`: contador a 0 y `locked_until = null`.
  - `isLocked(now)`: `locked_until != null && locked_until > now`.
  - Constantes `MAX_FAILED_ATTEMPTS = 5` y `LOCK_DURATION = 15 min` en el dominio. La spec las fija,
    así que no se parametrizan (YAGNI).
- **Concurrencia**: el login obtiene al usuario con `SELECT … FOR UPDATE`
  (`@Lock(PESSIMISTIC_WRITE)` en `findByEmailForUpdate`) para serializar intentos simultáneos
  sobre la misma cuenta.
- **Persistir el fallo**: el login lanza `InvalidCredentialsException` después de actualizar el
  contador, en una transacción con `@Transactional(noRollbackFor = InvalidCredentialsException.class)`
  para que el contador se confirme.
- **Cuenta bloqueada o deshabilitada**: responde igual que con credenciales inválidas, con el
  `matches` ficticio de R5. Un intento sobre una cuenta bloqueada no amplía el bloqueo ni
  incrementa el contador.
- **Alternatives considered**: `UPDATE … SET failed_login_attempts = failed_login_attempts + 1`
  (atómico, pero saca la regla del dominio); `@Version` optimista (reintentos innecesarios).

## R7. Refresh tokens: formato, almacenamiento y rotación

- **Decision**:
  - Valor: 32 bytes de `SecureRandom` (256 bits) en Base64 URL sin relleno (43 caracteres). Se
    entrega una sola vez al cliente.
  - Almacenamiento: `token_hash BYTEA` = SHA-256 (`MessageDigest`) del **texto** del token
    (UTF-8), tal como llega en la cookie. SHA-256 sin sal basta porque el valor tiene 256 bits de
    entropía y no lo elige una persona.
  - Familia: cada login crea un `family_id` nuevo (UUID); cada rotación lo hereda.
  - Rotación (`refresh`), en una transacción con `SELECT … FOR UPDATE` sobre la fila hallada por
    hash:
    1. No existe → 401 `invalid-refresh-token`.
    2. `revoked_at != null` (rotado o revocado) → **reutilización**: se revocan todas las filas
       vigentes de la familia y se responde 401. Se confirma pese al 401 (`noRollbackFor`).
    3. `expires_at <= now` → 401.
    4. Usuario deshabilitado o inexistente → 401, y se revoca la familia.
    5. Válido → se inserta el token nuevo (mismo `family_id`, `expires_at = now + 7 d`) y se marca
       el anterior con `revoked_at = now` y `replaced_by = nuevo.id`.
  - Logout: busca por hash y, si existe, **revoca toda su familia** (la sesión). Siempre 204.
  - Validez: ventana deslizante; cada rotación vuelve a dar 7 días. La spec fija 7 días por token,
    no una vida máxima de sesión.
- **Rationale**: `revoked_at` + `replaced_by` distinguen «rotado» de «revocado» sin una columna de
  estado y dejan un rastro auditable. El `FOR UPDATE` serializa dos refrescos simultáneos del mismo
  token: el segundo ve `revoked_at` y se trata como reutilización, que es el comportamiento seguro
  (ver R18 para el efecto en el frontend).
- **Mantenimiento diferido**: no hay purga de filas expiradas (fuera de alcance; tarea futura).
- **Alternatives considered**: tabla `sessions` aparte (la familia no tiene atributos propios hoy);
  un JWT como refresh token (no revocable sin estado; el Principio V exige opaco); hash en
  hexadecimal `CHAR(64)` (el doble de espacio; `BYTEA` con `CHECK octet_length = 32` es más
  estricto).

## R8. Transporte del refresh token: cookie, no cuerpo

- **Decision**: el token de renovación viaja en la cookie `refresh_token`:
  `HttpOnly; Secure; SameSite=<Strict por defecto>; Path=/api/v1/auth; Max-Age=604800`.
  - `login` y `refresh` responden con `Set-Cookie` y el cuerpo `{access_token, token_type, expires_in}`.
  - `refresh` y `logout` leen la cookie; no aceptan el token en el cuerpo.
  - `logout` responde 204 con `Set-Cookie` y `Max-Age=0`, aunque la cookie falte.
- **Rationale**: la Constitución V exige cookie `HttpOnly`, `Secure` y `SameSite` explícito. Una
  cookie HttpOnly impide que un XSS robe la credencial de larga duración.
- **CSRF (mismo sitio)**: la cadena es stateless y `csrf` está desactivado, como en 001. Para los
  dos endpoints que usan la cookie, la protección viene de `SameSite=Strict`, del `Path` restringido
  y de CORS limitado a la lista de orígenes, que impide a un sitio ajeno **leer** la respuesta del
  refresh. Un `logout` por CSRF solo cierra la sesión, un efecto inocuo. Si frontend y API quedan en
  sitios distintos, ver R19.
- **Configurable**: `mangateca.identity.refresh-cookie.same-site` (`Strict` por defecto; también
  `Lax` o `None`), siempre explícito. `Secure` no es configurable.
- **Alternatives considered**: refresh en el cuerpo guardado por el SPA en `localStorage`
  (contradice la Constitución y queda expuesto a XSS); cookie sin `Path` (se enviaría a todos los
  endpoints).

## R9. Rutas públicas sin acoplar `platform` a `identity`

- **Decision**: `identity` define su propio `SecurityFilterChain` con `@Order(1)` y un
  `securityMatcher` limitado a `POST /api/v1/auth/register|login|refresh|logout` (y su preflight
  `OPTIONS`), con `permitAll()`, `csrf` desactivado, sesión `STATELESS` y el mismo `corsFilter` de
  la plataforma. La cadena de la plataforma (deny-by-default) sigue a continuación y protege
  `/api/v1/auth/me` y todo lo demás.
- **Rationale**: las cuatro rutas son exactamente las que la Constitución V (v4.0.0) permite como
  anónimas. `platform` no conoce las rutas de `identity` (Principio I).
- **Alternatives considered**: `permitAll` en `platform.SecurityConfiguration` (acopla los
  módulos); una cadena que cubra todo `/api/v1/auth/**` con `/me` autenticado dentro (duplica la
  configuración del resource server en `identity`).
- **Login sin `AuthenticationManager`**: el login es un método de servicio que verifica contraseña
  y bloqueo; no se registra `AuthenticationProvider` ni `UserDetailsService`. Boot no genera un
  usuario por defecto porque existe un bean `JwtDecoder` (R3). **Verificar en implementación** que
  no aparece el log «Using generated security password».

## R10. Manejo de errores y tipos de problema

- **Decision**: un único `@RestControllerAdvice` para toda la API, el `GlobalExceptionHandler` de
  001 (Constitución IV: «Errores MUST unificarse en un `@RestControllerAdvice`»). `identity` no
  define advice propio:
  - Las excepciones `EmailAlreadyInUseException`, `InvalidCredentialsException` e
    `InvalidRefreshTokenException` extienden `org.springframework.web.ErrorResponseException` y
    construyen su `ProblemDetail` con estado, `type` URN y `detail` constante.
    `ResponseEntityExceptionHandler`, la base del `GlobalExceptionHandler`, ya las traduce sin
    conocer su clase.
  - Cambio genérico en `GlobalExceptionHandler.handleExceptionInternal`: si el `ProblemDetail`
    trae un `type` distinto de `about:blank`, lo fija una excepción de dominio de un módulo, y se
    conservan su `type` y su `detail`. Solo se normalizan `title` e `instance`. Con
    `about:blank`, el comportamiento de 001 no cambia.
  - `/me` con un usuario inexistente o deshabilitado lanza `ErrorResponseException(UNAUTHORIZED)`
    con la cabecera `WWW-Authenticate: Bearer`. El handler produce el mismo cuerpo que el entry
    point de 401 de 001 (`about:blank`, «Se requiere autenticación válida.»).
  - El formato de correo o el nombre visible, si los rechazan el VO `Email` o la fábrica
    `User.register` tras pasar Bean Validation, se traducen en el servicio a
    `ErrorResponseException(BAD_REQUEST)` (400 genérico), nunca a 500.

  | Tipo (`type`) | Estado | Cuándo |
  |---------------|--------|--------|
  | `urn:mangateca:problem:email-already-in-use` | 409 | Registro con correo existente, incluida la carrera detectada por el índice único |
  | `urn:mangateca:problem:invalid-credentials` | 401 | Login: contraseña errónea, correo inexistente, cuenta bloqueada o deshabilitada |
  | `urn:mangateca:problem:invalid-refresh-token` | 401 | Refresh: token ausente, desconocido, expirado, malformado, rotado o revocado, o usuario deshabilitado |
  | `about:blank` | 400 | Validación de campos (formato de 001, con `errors`), incluido `role` presente |
  | `about:blank` | 401 | `/me` sin token o con token inválido (entry point de 001) |

  Cada tipo tiene un `detail` genérico y constante que no menciona la causa concreta. Los `type`
  son URN, para no inventar un dominio.
- **Rationale**: cumple FR-020 y SC-002. Con tipos documentados, el frontend puede decidir sin
  analizar textos.
- **Alternatives considered**:
  - *`about:blank` con solo `status`*: el 401 del login y el del refresh no se distinguirían, y el
    cliente necesita saber si debe pedir la contraseña de nuevo.
  - *`@RestControllerAdvice` propio de `identity`* (versión anterior de este plan): viola la
    unicidad que exige la Constitución IV.
  - *Mapear las excepciones de `identity` dentro del `GlobalExceptionHandler`*: acoplaría
    `platform` a tipos internos de `identity` (Principio I).
- **Costo aceptado**: las excepciones de la capa de aplicación dependen de `spring-web`
  (`ErrorResponseException`). Es una dependencia de framework, no de otro módulo.

## R11. Registro: normalización, unicidad y rechazo de `role`

- **Decision**:
  - `RegisterRequest` recorta `email` y `displayName` en su constructor compacto (tolera `null`
    para que Bean Validation lo reporte). Bean Validation corre sobre el objeto ya construido, así
    que `@Email` y `@Size` validan el valor recortado: un correo con espacios alrededor se
    normaliza antes de validar (caso borde de la spec).
  - `Email` (record VO): `trim()` y minúsculas con `Locale.ROOT`. El constructor compacto aplica
    una regla mínima, no vacío, ≤254 caracteres, una sola `@` con ambos lados no vacíos y sin
    espacios, que todo correo aceptado por `@Email` cumple en la práctica. Si aun así la rechaza,
    ver R10 (400, nunca 500). En el login, un correo que el VO rechaza se trata como inexistente
    (401 genérico con el hash ficticio). Se guarda normalizado, y el índice
    `UNIQUE (lower(email))` garantiza la unicidad.
  - Carrera: el servicio comprueba primero `existsByEmail`, que cubre el caso común. Para el caso
    concurrente confía en el índice único: `saveAndFlush` dentro de la transacción y
    `DataIntegrityViolationException` → `EmailAlreadyInUseException` (409). La transacción se
    revierte antes de publicar el evento.
  - `displayName`: `trim()`; 1–100 caracteres tras recortar.
  - Rechazo de `role`: `RegisterRequest` declara `@Null String role`. Jackson ignora por defecto las
    propiedades desconocidas, así que sin el campo un `"role":"ADMIN"` se descartaría en silencio.
    Con `@Null`, cualquier valor no nulo produce un 400 con `errors: [{field: "role", ...}]`.
  - La respuesta 201 devuelve `UserResponse` (`id`, `email`, `displayName`, `role`), sin
    contraseña ni hash. El registro no inicia sesión.
- **Alternatives considered**: `spring.jackson.deserialization.fail-on-unknown-properties=true`
  (cambia el contrato de toda la API); `citext` (requiere una extensión que V1 no creó).

## R12. Evento `UserRegistered`

- **Decision**:
  - `UserRegistered(UUID userId, Role role, Instant occurredAt)` es un `record` en la raíz del
    módulo, junto con el enum público `Role`. No lleva correo ni nombre, así que el evento no
    contiene PII.
  - Se publica con `ApplicationEventPublisher.publishEvent(...)` dentro del método
    `@Transactional register(...)`, después del `saveAndFlush`.
  - **Verificación** (FR-006, US1): `UserRegisteredIT` con `@ApplicationModuleTest` y `Scenario`:

    ```java
    scenario.stimulate(() -> authenticationService.register(command))
        .andWaitForEventOfType(UserRegistered.class)
        .matching(event -> event.role() == Role.CUSTOMER)
        .toArriveAndVerify(event -> assertThat(event.userId()).isNotNull());
    ```

    `stimulate` ejecuta el estímulo dentro de una transacción que se confirma (sin rollback).
  - No se verifica la tabla `event_publication`: sin consumidores en esta feature, inspeccionar el
    registro de publicaciones sería probar Spring Modulith, no `identity`. Cuando exista el primer
    `@ApplicationModuleListener` (feature futura), su módulo verificará la entrega.
- **Alternatives considered**: un listener definido solo en tests para ver la fila en
  `event_publication` (versión anterior de este plan; descartado por la razón anterior);
  `PublishedEvents`/`AssertablePublishedEvents` (válidos, pero `Scenario` además comprueba que el
  evento sobrevive al commit de la transacción).

## R13. Administrador inicial

- **Decision**: `BootstrapAdminRunner` (`ApplicationRunner`) lee
  `mangateca.identity.bootstrap-admin.email` y `.password` (record anidado en
  `IdentityProperties`; variables `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL` y `_PASSWORD`). En una
  transacción:
  1. Ambos vacíos → INFO «sin administrador inicial configurado» y fin.
  2. Solo uno definido, correo inválido o contraseña fuera de `PasswordPolicy` → WARN con el
     motivo, **sin contraseña ni correo**, y fin. El arranque continúa.
  3. `existsByRole(ADMIN)` → fin.
  4. El correo ya existe como `CUSTOMER` → WARN y fin (no se promociona la cuenta).
  5. Crea `User(role=ADMIN)` con el hash y registra un INFO con el `userId`.
  - Varias instancias a la vez: el índice único de correo impide el duplicado; la instancia que
    pierde captura `DataIntegrityViolationException` y termina sin error.
  - El record de configuración **redefine `toString()`** para enmascarar la contraseña, porque el
    `toString` generado de un record la imprimiría.
- **Alternatives considered**: migración Flyway con el administrador (prohibido por FR-018);
  `@EventListener(ApplicationReadyEvent)` (equivalente; se pidió `ApplicationRunner`); índice único
  parcial «un solo ADMIN» (impediría más administradores en la feature 010).

## R14. Logging y PII

- **Decision**: ningún log contiene contraseñas, tokens, hashes ni correos. Cuando hace falta se
  registra el `userId`: al crear el administrador y al detectar una reutilización (WARN con
  `userId` y `familyId`). Los intentos de login fallidos no se registran en esta feature.
  `LoginRequest` y `RegisterRequest` redefinen `toString()` sin la contraseña.
- **Rationale**: Higiene Operacional y SC-006.

## R15. Mapeo, nulabilidad y estilo de entidades

- **Decision**:
  - MapStruct (`componentModel = "spring"`) para `User → UserResponse`.
  - Entidades sin Lombok: constructor protegido para JPA, fábrica pública que valida invariantes,
    accesores explícitos y sin setters públicos.
  - Ids asignados por la aplicación (`UUID.randomUUID()`). `created_at` y `updated_at` los fija el
    dominio con el `Clock`.
  - `@NullMarked` en todos los paquetes.
- **Rationale**: Principios II, III y IV.

## R16. `Clock`

- **Decision**: `identity` define `@Bean Clock clock()` = `Clock.systemUTC()`; los servicios y el
  emisor de tokens lo reciben por constructor. Los tests unitarios usan `Clock.fixed(...)`.
- **Rationale**: frontera permitida por la Constitución VI; hace deterministas las pruebas de
  bloqueo, expiración y rotación.

## R17. Contrato OpenAPI

- **Decision**: `contracts/openapi.yaml` (OpenAPI 3.1) es el contrato de diseño. En implementación,
  springdoc genera el documento real desde las anotaciones (`@Operation`, `@ApiResponse`), y una
  tarea comprueba que ambos coinciden en rutas, códigos y esquemas. Los DTO usan
  `@JsonProperty("access_token")`, `"token_type"` y `"expires_in"` para el cuerpo en snake_case.
- **Alternatives considered**: generar el servidor desde el YAML con openapi-generator (dependencia
  nueva sin justificación, Principio III).

## R18. Riesgo: refresh simultáneo en varias pestañas

- **Riesgo**: todas las pestañas del SPA comparten la cookie `refresh_token`. Si dos pestañas
  refrescan a la vez con el mismo token, la primera lo rota y la segunda presenta un token ya
  rotado. El backend lo trata como **reutilización** (R7): revoca toda la familia y la persona
  pierde la sesión en todas las pestañas.
- **Decision**: el backend no añade ventana de gracia ni tolerancia a la reutilización, porque
  debilitaría SC-005. La mitigación es del frontend: un **refresh single-flight** coordinado entre
  pestañas con la **Web Locks API** (`navigator.locks.request('mangateca-refresh', …)`), de modo
  que solo una pestaña llame a `/api/v1/auth/refresh` y las demás reutilicen el access token
  resultante (por ejemplo, vía `BroadcastChannel`).
- **Planificación**: se implementa en la **feature 012** (frontend). Hasta entonces, el riesgo
  queda aceptado y documentado.
- **Alternatives considered**: ventana de gracia de unos segundos para el token anterior
  (complejidad en el backend y una ventana explotable tras un robo); no rotar (incumple la spec).

## R19. Nota de despliegue: frontend y API en dominios distintos

- **Contexto**: con `SameSite=Strict`, el navegador solo envía la cookie en peticiones del mismo
  sitio (mismo esquema y dominio registrable; el puerto no cuenta). Sirve para el desarrollo
  (`localhost:5173` → `localhost:8080`) y para despliegues bajo un mismo dominio
  (`app.mangateca.x` / `api.mangateca.x`).
- **Nota**: si en producción el frontend y la API quedan en **dominios distintos** (sitios
  distintos), la cookie necesitará `SameSite=None` (con `Secure`, que ya es obligatorio), mediante
  `MANGATECA_IDENTITY_REFRESH_COOKIE_SAME_SITE=None`. En ese caso `SameSite` deja de proteger
  contra CSRF, y **`/refresh` necesitará protección CSRF explícita**: por ejemplo, el patrón
  *double-submit* de Spring Security (`CookieCsrfTokenRepository` en la cadena pública) o exigir
  una cabecera propia que un formulario de otro sitio no puede enviar, junto con la validación del
  `Origin` contra la lista de CORS. Esto se decide y se implementa antes de ese despliegue; no
  forma parte de esta feature.
- **Logout**: un CSRF sobre `/logout` solo cierra la sesión (efecto inocuo); se le aplicaría la
  misma protección por coherencia.
