# Contrato de configuración por entorno

**Feature**: `002-identity-access` | Complementa y, en la parte JWT, **sustituye** a
[../../001-platform-foundation/contracts/configuration.md](../../001-platform-foundation/contracts/configuration.md).

Ningún secreto de producción se versiona (Constitución, Higiene Operacional).

## Propiedades

| Propiedad | Variable de entorno | Tipo | Por defecto | Notas |
|-----------|---------------------|------|-------------|-------|
| `mangateca.security.jwt.issuer` | `MANGATECA_SECURITY_JWT_ISSUER` | texto | — (obligatoria) | Lo escribe `identity` en `iss` y lo exige el `JwtDecoder` |
| `mangateca.security.jwt.public-key-location` | `MANGATECA_SECURITY_JWT_PUBLIC_KEY_LOCATION` | `Resource` (PEM X.509) | — (obligatoria) | Sustituye a `spring.security.oauth2.resourceserver.jwt.public-key-location` |
| `mangateca.identity.private-key-location` | `MANGATECA_IDENTITY_PRIVATE_KEY_LOCATION` | `Resource` (PEM PKCS#8) | — (obligatoria) | Solo la usa el emisor de tokens |
| `mangateca.identity.access-token-ttl` | `MANGATECA_IDENTITY_ACCESS_TOKEN_TTL` | `Duration` | `PT15M` | Constitución V: ~15 min |
| `mangateca.identity.refresh-token-ttl` | `MANGATECA_IDENTITY_REFRESH_TOKEN_TTL` | `Duration` | `P7D` | Spec: 7 días |
| `mangateca.identity.refresh-cookie.same-site` | `MANGATECA_IDENTITY_REFRESH_COOKIE_SAME_SITE` | `Strict`\|`Lax`\|`None` | `Strict` | Siempre explícito; valor distinto de los tres → no arranca |
| `mangateca.identity.bootstrap-admin.email` | `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL` | texto | vacío | Opcional |
| `mangateca.identity.bootstrap-admin.password` | `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD` | texto | vacío | Opcional; 8–64 caracteres y ≤72 bytes; nunca se registra |

Reglas de arranque:

- Falta cualquiera de las tres obligatorias → la aplicación no arranca.
- La clave privada y la pública deben formar un par (el arranque verifica que el módulo y el
  exponente coinciden) → si no, no arranca.
- `bootstrap-admin` incompleto o inválido → la aplicación **arranca** sin crear administrador y
  emite un WARN sin datos sensibles.

## Perfiles

| Perfil | Claves | Origen |
|--------|--------|--------|
| `dev` | `file:./dev/keys/jwt-dev-private.pem` y `jwt-dev-public.pem` | `application-dev.yaml`; par **exclusivo de desarrollo**, versionado |
| tests | `classpath:keys/jwt-test-private.pem` y `jwt-test-public.pem` | `src/test/resources/application.yaml`; par distinto del de dev |
| cualquier otro (prod) | Variables de entorno; normalmente `file:/run/secrets/…` | Sin valores por defecto ni referencias a `dev/keys` |

`issuer` en dev/test: `http://localhost:8080` / `mangateca-test` (valores no secretos).

## Generar un par RSA 2048 (dev, test y también como plantilla para prod)

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

`genpkey` produce PKCS#8 (`BEGIN PRIVATE KEY`), el formato que espera `RsaKeyConverters.pkcs8()`.
En producción el par se genera fuera del repositorio y se monta como secreto; la rotación de
claves queda fuera de alcance de esta feature.

## Fijos (no configurables por entorno)

| Aspecto | Valor |
|---------|-------|
| Algoritmo de firma | RS256 |
| Bloqueo | 5 fallos consecutivos → 15 minutos |
| Cookie | nombre `refresh_token`, `Path=/api/v1/auth`, `HttpOnly`, `Secure` |
| Rutas públicas | `POST /api/v1/auth/{register,login,refresh,logout}` |
