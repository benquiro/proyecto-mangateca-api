# Contrato de configuración por entorno

**Feature**: `002-identity-access` | Complementa a
[../../001-platform-foundation/contracts/configuration.md](../../001-platform-foundation/contracts/configuration.md)
y, en la parte JWT, **la sustituye**: la propiedad
`spring.security.oauth2.resourceserver.jwt.public-key-location` deja de usarse.

Ninguna clave, secreto ni credencial se versiona (Higiene Operacional). Decisiones en
[../research.md](../research.md) R3 y R4.

## Propiedades

| Propiedad | Variable de entorno | Tipo | Por defecto | Notas |
|-----------|---------------------|------|-------------|-------|
| `mangateca.identity.jwt.issuer` | `MANGATECA_IDENTITY_JWT_ISSUER` | texto | — (obligatoria fuera de dev/test) | Se escribe en `iss` y lo exige el `JwtDecoder` |
| `mangateca.identity.jwt.private-key-location` | `MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION` | `Resource` (PEM PKCS#8) | — | Ver «Resolución de claves» |
| `mangateca.identity.jwt.public-key-location` | `MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION` | `Resource` (PEM X.509) | — | Ver «Resolución de claves» |
| `mangateca.identity.access-token-ttl` | `MANGATECA_IDENTITY_ACCESS_TOKEN_TTL` | `Duration` | `PT15M` | Constitución V: ~15 min |
| `mangateca.identity.refresh-token-ttl` | `MANGATECA_IDENTITY_REFRESH_TOKEN_TTL` | `Duration` | `P7D` | Spec: 7 días |
| `mangateca.identity.refresh-cookie.same-site` | `MANGATECA_IDENTITY_REFRESH_COOKIE_SAME_SITE` | `Strict`\|`Lax`\|`None` | `Strict` | Siempre explícito; con `None` hace falta CSRF en `/refresh` (R19) |
| `mangateca.identity.bootstrap-admin.email` | `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL` | texto | vacío | Opcional |
| `mangateca.identity.bootstrap-admin.password` | `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD` | texto | vacío | Opcional; 8–64 caracteres y ≤72 bytes; nunca se registra |

## Resolución de claves

| Situación | Perfil `dev` o `test` | Cualquier otro perfil |
|-----------|-----------------------|-----------------------|
| Ambas rutas definidas y forman un par | Se usan | Se usan |
| Ambas rutas definidas pero no forman un par | **Arranque falla** | **Arranque falla** |
| Solo una ruta definida | **Arranque falla** | **Arranque falla** |
| Ninguna ruta definida | Par RSA 2048 **efímero en memoria**; los tokens caducan al reiniciar | **Arranque falla** |

En producción, las rutas apuntan a los PEM montados como secreto:

```bash
MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem
MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem
MANGATECA_IDENTITY_JWT_ISSUER=https://<host-publico-de-la-api>
```

`bootstrap-admin` incompleto o inválido no impide arrancar: la aplicación arranca sin crear el
administrador y emite un WARN sin datos sensibles.

## Perfiles

| Perfil | Activación | Claves | `issuer` |
|--------|------------|--------|----------|
| `dev` | `./mvnw spring-boot:run` (configuración de 001) | Efímeras, salvo que se definan las rutas | `application-dev.yaml` |
| `test` | Surefire: `spring.profiles.active=test` (`systemPropertyVariables`) | Efímeras | `src/test/resources/application-test.yaml` |
| otro (prod) | Entorno de despliegue | Variables de entorno (obligatorias) | Variable de entorno (obligatoria) |

## Generar el par para producción (fuera del repositorio)

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

`genpkey` produce PKCS#8 (`BEGIN PRIVATE KEY`), el formato que espera `RsaKeyConverters.pkcs8()`.
Los archivos se guardan en el gestor de secretos del despliegue, nunca en el repositorio. La
rotación de claves queda fuera del alcance de esta feature.

## Fijos (no configurables por entorno)

| Aspecto | Valor |
|---------|-------|
| Algoritmo de firma | RS256 |
| Bloqueo | 5 fallos consecutivos → 15 minutos |
| Cookie | nombre `refresh_token`, `Path=/api/v1/auth`, `HttpOnly`, `Secure` |
| Rutas anónimas | `POST /api/v1/auth/{register,login,refresh,logout}` (Constitución V, v4.0.0) |
