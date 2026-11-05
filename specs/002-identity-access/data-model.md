# Data Model: Identity & Access

**Feature**: `002-identity-access` | **Fecha**: 2026-11-05

Migración `V2__create_identity.sql` (V1 no se toca). Hibernate corre con `ddl-auto=validate`,
así que las entidades deben coincidir con este esquema. Decisiones en
[research.md](research.md) (R5–R7, R11, R15).

## 1. Tabla `users`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK; asignado por la aplicación |
| `email` | `VARCHAR(254)` | no | Normalizado (recorte + minúsculas) |
| `display_name` | `VARCHAR(100)` | no | 1–100 caracteres tras recortar |
| `password_hash` | `VARCHAR(255)` | no | Salida del encoder delegante (`{bcrypt}…`) |
| `role` | `VARCHAR(20)` | no | `CHECK (role IN ('CUSTOMER','ADMIN'))` |
| `enabled` | `BOOLEAN` | no | `DEFAULT TRUE` |
| `failed_login_attempts` | `INTEGER` | no | `DEFAULT 0`, `CHECK (failed_login_attempts >= 0)` |
| `locked_until` | `TIMESTAMPTZ` | sí | `NULL` = sin bloqueo |
| `created_at` | `TIMESTAMPTZ` | no | Del `Clock` |
| `updated_at` | `TIMESTAMPTZ` | no | Del `Clock`; se actualiza en cada cambio |

Índices:

- `PRIMARY KEY (id)`
- `users_email_lower_uk`: `UNIQUE INDEX ON users (lower(email))` — unicidad sin distinguir
  capitalización. Sustituye a `citext` porque V1 no creó extensiones.
- Sin índice por `role`: `existsByRole(ADMIN)` solo se ejecuta al arrancar.

Consulta de login/registro por correo: `lower(email) = :normalized` (usa el índice).

## 2. Tabla `refresh_tokens`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `user_id` | `UUID` | no | FK → `users(id)` `ON DELETE CASCADE` |
| `token_hash` | `BYTEA` | no | SHA-256 del token; `CHECK (octet_length(token_hash) = 32)`; único |
| `family_id` | `UUID` | no | Identifica la sesión; compartido por toda la cadena de rotación |
| `expires_at` | `TIMESTAMPTZ` | no | `created_at` + 7 días |
| `revoked_at` | `TIMESTAMPTZ` | sí | No nulo = rotado o revocado |
| `replaced_by` | `UUID` | sí | FK → `refresh_tokens(id)`; solo si fue rotado |
| `created_at` | `TIMESTAMPTZ` | no | Del `Clock` |

Índices:

- `PRIMARY KEY (id)`
- `refresh_tokens_token_hash_uk`: `UNIQUE (token_hash)` — búsqueda en refresh/logout.
- `refresh_tokens_family_idx`: `(family_id)` — revocar toda la familia.
- `refresh_tokens_user_idx`: `(user_id)` — `ON DELETE CASCADE` y futura revocación por usuario.

El token en claro no se guarda en ninguna columna.

## 3. Entidades JPA (módulo `identity`, paquete interno `domain`)

### `User`

| Atributo | Notas |
|----------|-------|
| `id`, `email`, `displayName`, `passwordHash`, `role`, `enabled`, `failedLoginAttempts`, `lockedUntil`, `createdAt`, `updatedAt` | Mapeo 1:1 con `users`; `role` con `@Enumerated(STRING)` |

Invariantes y comportamiento (sin setters públicos):

- Fábrica `User.register(Email, displayName, passwordHash, Role, Clock)`: valida nombre
  (1–100 tras recortar) y fija contadores a 0 y `enabled = true`.
- `isLocked(Instant now)`, `registerFailedLogin(Instant now)`, `registerSuccessfulLogin(Instant now)`
  implementan R6.
- `isEnabled()`.

Estados de la cuenta:

```text
ACTIVA ──(5 fallos consecutivos)──▶ BLOQUEADA ──(pasan 15 min)──▶ ACTIVA (contador a 0 en el
   ▲                                    │                           siguiente intento)
   └──────(login correcto: contador 0)──┘ (solo si no está bloqueada)
DESHABILITADA (enabled=false): no inicia sesión ni renueva; sin transición en esta feature.
```

### `RefreshToken`

| Atributo | Notas |
|----------|-------|
| `id`, `userId`, `tokenHash` (`byte[]`), `familyId`, `expiresAt`, `revokedAt`, `replacedBy`, `createdAt` | `userId` como UUID (sin `@ManyToOne`: no se navega a `User`, evita N+1) |

Comportamiento:

- `isExpired(now)`, `isRevoked()`.
- `rotate(newTokenId, now)`: marca `revokedAt = now` y `replacedBy = newTokenId`.
- `revoke(now)`: marca `revokedAt = now` si aún no estaba revocado.

Estados del token:

```text
VIGENTE ──refresh──▶ ROTADO (revoked_at + replaced_by)
   │                    │
   ├──logout / reutilización de un ROTADO──▶ REVOCADO (toda la familia)
   └──pasa expires_at──▶ EXPIRADO
Reutilizar un ROTADO o REVOCADO → se revoca la familia completa y se responde 401.
```

### Repositorios (Spring Data, internos)

| Método | Uso |
|--------|-----|
| `UserRepository.existsByEmail(String)` | Registro: comprobación previa (JPQL con `lower(email)`) |
| `UserRepository.findByEmailForUpdate(String)` | Login: `@Lock(PESSIMISTIC_WRITE)` |
| `UserRepository.findById` | `/me`, refresh |
| `UserRepository.existsByRole(Role)` | Administrador inicial |
| `RefreshTokenRepository.findByTokenHashForUpdate(byte[])` | Refresh/logout: `@Lock(PESSIMISTIC_WRITE)` |
| `RefreshTokenRepository.revokeFamily(UUID familyId, Instant now)` | `@Modifying` `UPDATE … WHERE family_id=:f AND revoked_at IS NULL` |

Los métodos que devuelven un único resultado opcional usan `Optional<T>` (Principio IV).

## 4. Objetos de valor y DTO (records)

| Tipo | Campos y validación |
|------|---------------------|
| `Email` (VO, `domain`) | `String value`; `trim` + minúsculas (`Locale.ROOT`); formato válido; ≤254 |
| `RegisterRequest` (`web`) | `email` (`@NotBlank @Email @Size(max=254)`), `displayName` (`@NotBlank @Size(max=100)`, recortado), `password` (`@ValidPassword`), `role` (`@Null`) |
| `LoginRequest` | `email` (`@NotBlank`), `password` (`@NotBlank`) — el login **no** aplica `@ValidPassword`: una contraseña antigua o inválida debe producir el 401 genérico, no un 400 que revele la política |
| `TokenResponse` | `access_token`, `token_type` (`"Bearer"`), `expires_in` (segundos, `900`) |
| `UserResponse` | `id`, `email`, `displayName`, `role` |
| `UserRegistered` (público) | `UUID userId`, `Role role`, `Instant occurredAt` |
| `Role` (público) | `CUSTOMER`, `ADMIN` |

`toString()` de `RegisterRequest` y `LoginRequest` omite la contraseña.

## 5. Estructuras no persistidas

### Claims del access token

`sub` (UUID), `roles` (lista de strings), `iss`, `iat`, `exp`. Ver research R2.

### Cookie de refresh

`refresh_token=<43 caracteres Base64url>; Path=/api/v1/auth; Max-Age=604800; HttpOnly; Secure;
SameSite=<Strict|Lax|None>`. Para borrar: mismo nombre y `Path`, `Max-Age=0`.

### Propiedades (`IdentityProperties`, prefijo `mangateca.identity`)

Ver [contracts/configuration.md](contracts/configuration.md).

## 6. Compatibilidad con V1

`event_publication` (V1) recibe una fila por listener transaccional de `UserRegistered` (R12);
no se modifica. La FK `refresh_tokens.user_id → users.id` es el único vínculo entre tablas; las
demás features referenciarán `users.id` desde sus propios módulos solo por UUID (sin FK
cruzada de entidades JPA, Principio I).
