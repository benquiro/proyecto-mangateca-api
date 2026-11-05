# Research: Identity & Access

**Feature**: `002-identity-access` | **Fecha**: 2026-11-05

Fuentes: documentación oficial vía Context7 (Spring Security 7.1.1, Spring Boot 4.1.0, Spring
Modulith 2.1.0) y el código de la feature 001. Las afirmaciones que dependen del comportamiento
exacto de una librería y que la documentación consultada no cierra se marcan **«verificar en
implementación»** y tienen un test asociado.

## R1. Emisión de tokens: propia (`NimbusJwtEncoder`) vs. servidor de autorización dedicado

- **Decision**: la API emite sus propios JWT con `NimbusJwtEncoder` (RS256). Se documenta en
  ADR-006.
- **Rationale**: el Principio V pide soporte nativo (`NimbusJwtEncoder`/`JwtDecoder`). Hay un
  único emisor y un único consumidor dentro del mismo monolito; un servidor de autorización
  separado añadiría un proceso, endpoints OAuth2 que nadie consume y gestión de clientes
  registrados. Los endpoints `/api/v1/auth/*` son los que fijó la spec.
- **Alternatives considered**:
  - *Spring Authorization Server*: correcto cuando hay varias aplicaciones cliente, SSO o
    federación. Hoy no existe ninguna (YAGNI, Principio VII). **Condición de revisión** que
    ADR-006 debe registrar: segunda aplicación consumidora, login social o MFA.
  - *Proveedor externo (Keycloak, Auth0)*: dependencia operativa y de coste no pedida.
  - *HMAC (HS256)*: obligaría a compartir el secreto de firma con cada verificador
    (ya descartado en ADR-003).

## R2. Estructura del token de acceso

- **Decision**: JWS compacto RS256 con `JwsHeader.with(SignatureAlgorithm.RS256)` y claims:

  | Claim | Valor |
  |-------|-------|
  | `sub` | UUID del usuario (texto) |
  | `roles` | Lista de strings: `["CUSTOMER"]` o `["ADMIN"]` |
  | `iss` | Valor de `mangateca.security.jwt.issuer` |
  | `iat` | Instante de emisión (del `Clock`) |
  | `exp` | `iat` + 15 min (`mangateca.identity.access-token-ttl`) |

  Sin `jti`, `aud`, `kid` ni claims de correo/nombre: ninguna feature los necesita (Principio
  VII) y el token no debe llevar PII.
- **Rationale**: el `JwtAuthenticationConverter` de la feature 001 ya lee `roles` y antepone
  `ROLE_`; no requiere cambios. El `JwtDecoder` valida firma, `exp`/`nbf` y, con el validador
  explícito de R3, `iss`.
- **Alternatives considered**: un claim `scope`/`scp` (convención OAuth2; la 001 ya fijó
  `roles`); `jti` para listas de revocación (el access token no se revoca: dura ≤15 min, spec).

## R3. Validación en la plataforma: `JwtDecoder` explícito con issuer

- **Decision**: la plataforma define su propio bean `JwtDecoder`
  (`NimbusJwtDecoder.withPublicKey(key).build()` con
  `JwtValidators.createDefaultWithIssuer(issuer)`) alimentado por un record
  `JwtVerificationProperties(RSAPublicKey publicKey, String issuer)` bajo
  `mangateca.security.jwt`. Se retira `DevJwtDecoderConfiguration` y la propiedad de Boot
  `spring.security.oauth2.resourceserver.jwt.public-key-location` del contrato de configuración
  de la 001.
- **Rationale**: la documentación consultada de Boot 4.1 no deja claro si `issuer-uri` valida
  `iss` cuando se combina con `public-key-location` (sin descubrimiento). Un decoder explícito
  elimina esa ambigüedad y hace el comportamiento comprobable con un test: token con `iss`
  ajeno → 401.
- **Alternatives considered**: mantener las propiedades de Boot (**verificar en
  implementación** si validan `iss`; si lo hacen, esta decisión puede simplificarse sin cambiar
  el comportamiento observable).
- **Acoplamiento**: `platform` e `identity` comparten únicamente la clave
  `mangateca.security.jwt.issuer` (lectura con `@Value` en identity), nunca clases.

## R4. Carga de claves RSA y entornos

- **Decision**:
  - Los PEM se cargan como `Resource` (`file:`, `classpath:`) y se convierten con
    `RsaKeyConverters.pkcs8()` (privada, PKCS#8) y `RsaKeyConverters.x509()` (pública), API
    estable de `spring-security-core`. Se usa un cargador explícito (`RsaKeys`) en vez de
    depender de que el contexto registre el conversor de `RSAPublicKey` para `@Value`/binding.
  - **Prod**: variables de entorno con **la ubicación** de los secretos montados
    (`MANGATECA_IDENTITY_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem`,
    `MANGATECA_SECURITY_JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem`,
    `MANGATECA_SECURITY_JWT_ISSUER`). Sin valores por defecto: si falta cualquiera, el
    arranque falla.
  - **Dev**: `application-dev.yaml` apunta a `file:./dev/keys/jwt-dev-*.pem` (par exclusivo de
    desarrollo, versionado, con README y cabecera «NO USAR FUERA DE DESARROLLO»).
  - **Test**: `src/test/resources/application.yaml` apunta a `classpath:keys/jwt-test-*.pem`
    (otro par distinto del de dev).
- **Rationale**: el requisito del usuario («por variables de entorno en prod») se cumple en el
  sentido operativo habitual (secret montado + variable con su ruta). Un PEM completo dentro de
  una variable de entorno es posible pero frágil (saltos de línea, límites de tamaño, aparece
  en `/proc` y en volcados de entorno).
- **Alternatives considered**:
  - *PEM inline en variable*: soportable con un `ByteArrayResource`; se descarta por lo anterior.
    Si el despliegue lo exige, se añade como segunda fuente sin cambiar el contrato.
  - *Un par efímero en dev (como hacía la 001)*: ya no sirve; encoder y decoder viven en módulos
    distintos y los tests de contexto compartido necesitan claves estables.
  - *Generar el par en `dev` y escribirlo a disco en el primer arranque*: lógica extra y
    secretos fuera del control del repositorio.
- **Seguridad**: el repositorio contiene solo claves de dev/test, claramente separadas. Un test
  comprueba que ni `application.yaml` (base) ni el perfil `prod` referencian `dev/keys` ni
  `classpath:keys`. **Nota**: ADR-003 había descartado versionar una clave pública de dev; este
  caso es distinto (hay privada y se firma de verdad) y se justifica en ADR-006.

## R5. Contraseñas: bcrypt delegante y límite de 72 bytes

- **Decision**: bean `PasswordEncoder` = `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  (prefijo `{bcrypt}`, coste por defecto). `password_hash VARCHAR(255)` admite futuros
  algoritmos. Política: **8–64 caracteres y ≤72 bytes UTF-8**, validada con `@ValidPassword`
  (DTO) y `PasswordPolicy` (admin inicial).
- **Rationale**: FR-003 fija 64 caracteres, pero un carácter UTF-8 ocupa hasta 4 bytes: 64
  caracteres pueden alcanzar 256 bytes, y bcrypt solo procesa los primeros 72. Sin la
  comprobación de bytes habría truncamiento silencioso o un error 500 según la versión de
  Spring Security. Rechazar con 400 antes de codificar mantiene la validación coherente.
  **Verificar en implementación** (test en `PasswordPolicyTests`): una contraseña de 64
  caracteres de 4 bytes se rechaza con 400 de campo `password` y nunca llega al encoder.
- **Alternatives considered**:
  - *Prehash SHA-256 + Base64 antes de bcrypt*: elimina el límite, pero añade un esquema
    no estándar que complica futuras migraciones de algoritmo.
  - *Argon2/scrypt*: el Principio V pide bcrypt por defecto; `{argon2}` seguirá siendo posible
    por el encoder delegante.
- **Tiempo constante** (SC-002): `AuthenticationService` calcula en el arranque un
  `DUMMY_HASH = encoder.encode(<aleatorio>)` y, si el correo no existe, la cuenta está
  deshabilitada o bloqueada, ejecuta igualmente `matches(password, DUMMY_HASH)` antes de
  responder 401. Así el tiempo de respuesta no distingue estos casos.

## R6. Bloqueo por intentos fallidos

- **Decision**: lógica en la entidad `User` (Principio II), con `Clock` recibido por parámetro:
  - `registerFailedLogin(now)`: si `locked_until` venció, reinicia el contador antes de sumar;
    incrementa; al llegar a 5 fija `locked_until = now + 15 min`.
  - `registerSuccessfulLogin()`: contador a 0 y `locked_until = null`.
  - `isLocked(now)`: `locked_until != null && locked_until > now`.
  - Constantes `MAX_FAILED_ATTEMPTS = 5` y `LOCK_DURATION = 15 min` en el dominio (la spec las
    fija; no se parametrizan: YAGNI).
- **Concurrencia**: el login obtiene al usuario con `SELECT … FOR UPDATE`
  (`@Lock(PESSIMISTIC_WRITE)` en `findByEmailForUpdate`) para serializar intentos simultáneos
  sobre la misma cuenta; sin esto, 5 intentos paralelos podrían contarse como menos de 5.
- **Persistir el fallo**: el método de login lanza `InvalidCredentialsException` tras
  actualizar el contador; la transacción se declara con
  `@Transactional(noRollbackFor = InvalidCredentialsException.class)` para que el contador se
  confirme.
- **Cuenta bloqueada / deshabilitada**: respuesta idéntica a credenciales inválidas (clarificaciones
  de la spec), con el `matches` ficticio de R5. Un intento sobre una cuenta bloqueada **no**
  amplía el bloqueo ni incrementa el contador.
- **Alternatives considered**: `UPDATE … SET failed_login_attempts = failed_login_attempts + 1`
  (atómico sin bloqueo de fila, pero separa la regla de bloqueo del dominio y complica el test
  unitario); `@Version` optimista (reintentos innecesarios aquí).

## R7. Refresh tokens: formato, almacenamiento y rotación

- **Decision**:
  - Valor: 32 bytes de `SecureRandom` (256 bits) codificados en Base64 URL sin relleno
    (43 caracteres). Se entrega una sola vez al cliente.
  - Almacenamiento: `token_hash BYTEA` = `MessageDigest.getInstance("SHA-256")` del **texto**
    del token (UTF-8), tal como llega en la cookie. SHA-256 sin sal es suficiente porque el
    valor tiene 256 bits de entropía (no es una contraseña elegida por una persona).
  - Familia: un login crea un `family_id` nuevo (UUID); cada rotación hereda el `family_id`.
  - Rotación (`refresh`), en una transacción con `SELECT … FOR UPDATE` sobre la fila hallada
    por hash:
    1. No existe → 401 `invalid-refresh-token`.
    2. `revoked_at != null` (rotado o revocado) → **reutilización**: se revocan todas las filas
       vigentes de la familia (`revoked_at = now`) y se responde 401. Se confirma pese al 401
       (`noRollbackFor`).
    3. `expires_at <= now` → 401.
    4. Usuario deshabilitado o inexistente → 401 (y se revoca la familia).
    5. Válido → se inserta el token nuevo (mismo `family_id`, `expires_at = now + 7 d`), se
       marca el anterior con `revoked_at = now` y `replaced_by = nuevo.id`.
  - Logout: busca por hash; si existe, **revoca toda su familia** (la sesión). Siempre 204.
  - Validez: ventana deslizante (cada rotación vuelve a dar 7 días). La spec fija 7 días por
    token, no una vida máxima de sesión.
- **Rationale**: el estado `revoked_at` + `replaced_by` permite distinguir «rotado» de
  «revocado» sin una columna de estado y deja un rastro auditable. El `FOR UPDATE` serializa
  dos refrescos simultáneos del mismo token: el segundo ve `revoked_at` y se trata como
  reutilización, que es el comportamiento seguro.
- **Limitación aceptada**: dos pestañas del SPA que refrescan a la vez con la misma cookie
  pierden la sesión. No hay ventana de gracia (YAGNI); el frontend debe serializar el refresco.
- **Mantenimiento diferido**: no hay purga de filas expiradas (fuera de alcance); se anota como
  tarea futura. Los índices por `user_id` y `family_id` mantienen acotado el coste de las
  consultas.
- **Alternatives considered**: tabla `sessions` aparte (la familia no tiene atributos propios
  hoy); JWT como refresh token (no revocable sin estado; el Principio V exige opaco);
  almacenar el hash en hexadecimal `CHAR(64)` (el doble de espacio; `BYTEA(32)` con `CHECK
  octet_length = 32` es más estricto).

## R8. Transporte del refresh token: cookie, no cuerpo

- **Decision**: el token de renovación viaja en la cookie `refresh_token`:
  `HttpOnly; Secure; SameSite=<Strict por defecto>; Path=/api/v1/auth; Max-Age=604800`.
  - `login` y `refresh` responden `Set-Cookie` y un cuerpo `{access_token, token_type, expires_in}`.
  - `refresh` y `logout` leen la cookie; no aceptan el token en el cuerpo.
  - `logout` responde `Set-Cookie` con `Max-Age=0` (borra la cookie) y 204, incluso si la
    cookie falta.
- **Rationale**: la Constitución V («Refresh tokens MUST … viajar en cookies HttpOnly, Secure y
  SameSite explícito») prevalece sobre la descripción del usuario, que incluía `refresh_token` en
  el cuerpo del login. Una cookie HttpOnly impide que un XSS robe la credencial de larga
  duración.
- **CSRF**: la cadena es stateless y `csrf` está desactivado (como en 001). Para los dos
  endpoints que usan la cookie: `SameSite=Strict`, `Path` restringido a `/api/v1/auth`, y CORS
  limitado a la lista de orígenes (001) de modo que un sitio ajeno no puede **leer** la respuesta
  del refresh. `logout` por CSRF solo cierra la sesión (efecto inocuo).
- **Configurable**: `mangateca.identity.refresh-cookie.same-site` (`Strict` por defecto, también
  `Lax`/`None`); siempre explícito. `Secure` no es configurable.
- **Alternatives considered**: refresh en el cuerpo y almacenado por el SPA (`localStorage`):
  contradice la Constitución y la expone a XSS; cookie sin `Path`: se enviaría a todos los
  endpoints.
- **Si se prefiere el cuerpo**: requiere enmendar el Principio V; no se hace sin una decisión
  explícita.

## R9. Rutas públicas sin acoplar `platform` a `identity`

- **Decision**: `identity` define su propio `SecurityFilterChain` con `@Order(1)`,
  `securityMatcher` limitado a `POST /api/v1/auth/register|login|refresh|logout` (y su preflight
  `OPTIONS`) y `permitAll()`; `csrf` desactivado, `STATELESS`, CORS con el mismo
  `corsFilter` de la plataforma. La cadena de plataforma (deny-by-default) sigue siendo la
  segunda y protege `/api/v1/auth/me` y todo lo demás.
- **Rationale**: `platform` no conoce las rutas de `identity` (límite de módulo, Principio I);
  añadir una ruta pública futura en otro módulo sigue el mismo patrón.
- **Requisito previo**: enmienda de la Constitución V (acceso anónimo). Ver plan.
- **Alternatives considered**: `permitAll` en `platform.SecurityConfiguration` (acopla módulos);
  un `SecurityFilterChain` que cubra todo `/api/v1/auth/**` con `/me` autenticado dentro de la
  misma cadena (duplica la configuración del resource server en identity).
- **Login sin `AuthenticationManager`**: el login es un método de servicio que verifica
  contraseña y bloqueo; no se registra un `AuthenticationProvider`/`UserDetailsService`. Boot
  no genera usuario por defecto porque la plataforma ya define un `JwtDecoder` propio.
  **Verificar en implementación** que en el arranque no aparece el log de «generated security
  password».

## R10. Manejo de errores y tipos de problema

- **Decision**: `IdentityExceptionHandler` (`@RestControllerAdvice(basePackageClasses = …)`)
  traduce las excepciones propias de `identity` a `ProblemDetail`. Los errores genéricos
  (validación, JSON ilegible, 500) los sigue produciendo el `GlobalExceptionHandler` de la 001.

  | Tipo (`type`) | Estado | Cuándo |
  |---------------|--------|--------|
  | `urn:mangateca:problem:email-already-in-use` | 409 | Registro con correo existente (incluye la carrera detectada por el índice único) |
  | `urn:mangateca:problem:invalid-credentials` | 401 | Login: contraseña errónea, correo inexistente, cuenta bloqueada o deshabilitada |
  | `urn:mangateca:problem:invalid-refresh-token` | 401 | Refresh: ausente, desconocido, expirado, malformado, rotado/revocado o usuario deshabilitado |
  | `about:blank` | 400 | Validación de campos (formato 001, con `errors`), incluye `role` presente |
  | `about:blank` | 401 | `/me` sin token o con token inválido (entry point de 001) |

  `detail` genérico y constante para cada tipo; nunca menciona la causa concreta. Los `type` son
  URN (sin inventar un dominio).
- **Rationale**: cumple FR-020 y SC-002; los tipos documentados permiten al frontend ramificar
  sin analizar textos.
- **Alternatives considered**: reutilizar `about:blank` con solo `status` (el 401 de login y el de
  refresh no se distinguirían para el cliente, que necesita saber si debe volver a pedir la
  contraseña).

## R11. Registro: normalización, unicidad y rechazo de `role`

- **Decision**:
  - `Email` (record VO): `trim()` y minúsculas con `Locale.ROOT`; validación de formato en el
    constructor compacto; máximo 254 caracteres. Se almacena normalizado y la unicidad se
    garantiza con el índice `UNIQUE (lower(email))` (también cubre filas escritas sin pasar por
    el VO).
  - Carrera: el servicio comprueba `existsByEmail` (camino común) y confía en el índice único
    para el caso concurrente: `saveAndFlush` dentro de la transacción y captura de
    `DataIntegrityViolationException` → `EmailAlreadyInUseException` (409). La transacción se
    revierte antes de publicar el evento.
  - `displayName`: `trim()` y 1–100 caracteres tras recortar (clarificación de la spec).
  - Rechazo de `role`: `RegisterRequest` declara `@Null String role`. Jackson ignora por defecto
    las propiedades desconocidas, así que sin el campo un `"role":"ADMIN"` se descartaría en
    silencio y la cuenta se crearía como `CUSTOMER`. Con `@Null`, enviar cualquier valor no nulo
    produce un 400 con `errors: [{field: "role", ...}]`.
  - La respuesta 201 devuelve `UserResponse` (`id`, `email`, `displayName`, `role`); no incluye
    contraseña ni hash. No inicia sesión automáticamente.
- **Alternatives considered**: `spring.jackson.deserialization.fail-on-unknown-properties=true`
  (cambia el contrato de toda la API); `citext` (requiere extensión que V1 no creó y privilegios
  extra en el despliegue).

## R12. Evento `UserRegistered` y registro de publicaciones

- **Decision**:
  - `UserRegistered(UUID userId, Role role, Instant occurredAt)` es un `record` en la raíz del
    módulo, junto con el enum `Role` (también público porque forma parte del evento). Sin
    correo ni nombre: el evento no lleva PII.
  - Se publica con `ApplicationEventPublisher.publishEvent(...)` dentro del método
    `@Transactional register(...)`, después del `saveAndFlush`.
  - Con `spring-modulith-starter-jdbc` el registro persiste una fila en `event_publication`
    **por cada listener transaccional** (`@ApplicationModuleListener` /
    `@TransactionalEventListener`). **Sin listeners no se escribe ninguna fila**; el evento se
    publica igualmente en memoria. En esta feature no hay consumidores, y crear uno solo para
    ver la fila contradice el Principio VII.
  - Verificación: `UserRegisteredIT` (`@ApplicationModuleTest`) usa `Scenario` con
    `publish`/`stimulate` y un `@ApplicationModuleListener` definido **solo en test** dentro de
    `identity`; comprueba que el evento llega con el `userId` correcto y que
    `event_publication` contiene la fila y queda completada. **Verificar en implementación** el
    comportamiento sin listener (documentado aquí como hecho de Modulith 2.x a confirmar con un
    test de una línea).
- **Alternatives considered**: listener real de «auditoría» sin necesidad actual (viola VII);
  escribir en `event_publication` a mano (rompe la abstracción del registro).

## R13. Administrador inicial

- **Decision**: `BootstrapAdminRunner` (`ApplicationRunner`) lee
  `mangateca.identity.bootstrap-admin.email` y `.password` (record anidado en
  `IdentityProperties`, variables `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL`/`_PASSWORD`).
  Algoritmo, en una transacción:
  1. Ambos vacíos → fin silencioso (log INFO «sin administrador inicial configurado»).
  2. Solo uno definido, correo inválido o contraseña fuera de `PasswordPolicy` → log WARN con el
     motivo **sin contraseña ni correo** y fin; el arranque continúa.
  3. `existsByRole(ADMIN)` → fin.
  4. Si el correo ya existe como `CUSTOMER` → WARN y fin (no se promociona una cuenta).
  5. Crear `User(role=ADMIN)` con hash; log INFO con el `userId`, nunca con la contraseña.
  - Varias instancias a la vez: el índice único de correo impide el duplicado; el perdedor
    captura `DataIntegrityViolationException` y termina sin error.
  - El record de configuración **redefine `toString()`** para enmascarar la contraseña, porque
    el `toString` generado de un record la imprimiría.
- **Rationale**: ApplicationRunner pedido por el usuario; idempotencia por consulta, no por
  marcas de migración (FR-018: nada en el repositorio ni en Flyway).
- **Alternatives considered**: migración Flyway con el admin (prohibido por FR-018);
  `@EventListener(ApplicationReadyEvent)` (equivalente, el usuario pidió `ApplicationRunner`);
  índice único parcial «un solo ADMIN» (impediría tener más administradores en la feature 010).

## R14. Logging y PII

- **Decision**: ningún log contiene contraseñas, tokens, hashes ni correos. Se registra el
  `userId` cuando hace falta (creación del admin, detección de reutilización: WARN con
  `userId` y `familyId`). Los intentos de login fallidos no se registran en esta feature
  (sin observabilidad de seguridad en el alcance). `LoginRequest`/`RegisterRequest` redefinen
  `toString()` para no imprimir la contraseña (los records generan uno que la incluye).
- **Rationale**: Higiene Operacional (PII/contraseñas/tokens MUST NOT registrarse) y SC-006.

## R15. Mapeo, nulabilidad y estilo de entidades

- **Decision**: MapStruct (`componentModel = "spring"`) para `User → UserResponse`. Las
  entidades se escriben sin Lombok: constructor protegido para JPA, constructor/fábrica pública
  que valida invariantes, accesores explícitos, sin setters públicos. Ids asignados por la
  aplicación (`UUID.randomUUID()`), sin generador de Hibernate. `created_at`/`updated_at`
  los fija el dominio con el `Clock` inyectado, no `@PrePersist`, para poder fijarlos en tests.
  Todos los paquetes con `@NullMarked`.
- **Rationale**: Principios II, III, IV y VI; Lombok queda permitido pero no aporta aquí.

## R16. `Clock`

- **Decision**: `identity` define `@Bean Clock clock()` = `Clock.systemUTC()`; los servicios y
  el emisor de tokens lo reciben por constructor. Los tests lo sustituyen con
  `Clock.fixed(...)`.
- **Rationale**: único mock permitido por la Constitución VI; hace deterministas las pruebas de
  bloqueo, expiración y rotación.

## R17. Contrato OpenAPI

- **Decision**: `contracts/openapi.yaml` (OpenAPI 3.1) como contrato de diseño. En
  implementación, springdoc genera el documento real desde las anotaciones (`@Operation`,
  `@ApiResponse`); una tarea verifica que ambos coinciden en rutas, códigos y esquemas. Los DTO
  usan `@JsonProperty("access_token")`, `"token_type"`, `"expires_in"` para el cuerpo
  snake_case que pidió el usuario, sin cambiar la convención del resto de la API.
- **Alternatives considered**: generar el servidor desde el YAML (openapi-generator): dependencia
  nueva sin justificación (Principio III).
