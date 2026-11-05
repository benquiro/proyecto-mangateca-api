# Quickstart: validar Identity & Access

**Feature**: `002-identity-access` | Esta guía valida la feature de punta a punta.
Contrato HTTP: [contracts/openapi.yaml](contracts/openapi.yaml). Configuración:
[contracts/configuration.md](contracts/configuration.md). Esquema: [data-model.md](data-model.md).

## 0. Requisitos previos

- Docker en marcha (PostgreSQL 18 de `compose.yaml` y Testcontainers).
- Java 25 y el Maven Wrapper del repositorio.
- La enmienda de la Constitución V (acceso anónimo a `/api/v1/auth`) mergeada.
- `curl` y `jq`.

## 1. Pruebas automatizadas

```bash
./mvnw verify
```

Esperado: compila sin advertencias; pasan los tests de 001 y los de `identity`
(unitarios, `@WebMvcTest`, `@DataJpaTest`, seguridad, `@ApplicationModuleTest`) y
`ModularityTests`.

## 2. Arranque local con administrador inicial

```bash
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD='cambiar-esta-clave-1' \
./mvnw spring-boot:run
```

Esperado: arranque con el perfil `dev` (claves de `dev/keys`), Flyway aplica V2, y un log INFO
con el `userId` del administrador creado. **La contraseña no aparece en ningún log.**

Reiniciar con las mismas variables → no se crea otro administrador (SC-007):

```bash
docker compose exec postgres psql -U <usuario> -d <base> \
  -c "select count(*) from users where role = 'ADMIN';"   # 1
```

## 3. Flujo de cliente (HTTPS o `localhost`; el jar de cookies conserva la cookie `Secure`)

```bash
BASE=http://localhost:8080/api/v1/auth

# 3a. Registro → 201, sin contraseña en la respuesta
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"Ana@Example.com","displayName":"Ana","password":"contrasena-segura-1"}'

# 3b. Mismo correo con otra capitalización → 409 (email-already-in-use)
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"ANA@example.com","displayName":"Otra","password":"contrasena-segura-1"}'

# 3c. Intento de elegir ADMIN → 400 con errors[field=role]; no se crea la cuenta
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"mal@example.com","displayName":"Mal","password":"contrasena-segura-1","role":"ADMIN"}'

# 3d. Login → 200 con access_token/token_type/expires_in (900) y Set-Cookie refresh_token
curl -s -i -c jar.txt -X POST $BASE/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"contrasena-segura-1"}'
ACCESS=$(curl -s -c jar.txt -X POST $BASE/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"contrasena-segura-1"}' | jq -r .access_token)

# 3e. Identidad actual → 200 (id, email, displayName, role=CUSTOMER)
curl -s $BASE/me -H "Authorization: Bearer $ACCESS"
# Sin token → 401 application/problem+json
curl -s -i $BASE/me
```

Comprobar en el JWT (`jwt decode` o `cut -d. -f2 | base64 -d`): claims `sub` (UUID), `roles`
(`["CUSTOMER"]`), `iss`, `iat`, `exp` = `iat` + 900.

## 4. Renovación, reutilización y cierre de sesión

```bash
cp jar.txt old.txt
# 4a. Renovar → 200, cookie nueva; el token anterior queda invalidado
curl -s -b jar.txt -c jar.txt -X POST $BASE/refresh
# 4b. Reutilizar el token anterior (old.txt) → 401 invalid-refresh-token
curl -s -i -b old.txt -X POST $BASE/refresh
# 4c. Tras la reutilización TODA la familia está revocada: la cookie nueva también falla → 401
curl -s -i -b jar.txt -X POST $BASE/refresh
# 4d. Logout idempotente → 204 aunque no haya cookie
curl -s -i -X POST $BASE/logout
```

Comprobar en base de datos que no hay tokens en claro:

```bash
docker compose exec postgres psql -U <usuario> -d <base> \
  -c "select family_id, encode(token_hash,'hex') as hash, revoked_at, replaced_by from refresh_tokens;"
```

Esperado: hashes de 64 caracteres hex; ninguna columna con el valor de la cookie.

## 5. Bloqueo por fuerza bruta (SC-004)

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/login \
    -H 'Content-Type: application/json' -d '{"email":"ana@example.com","password":"incorrecta"}'
done                                   # 401 ×5
# Sexto intento con la contraseña CORRECTA → 401 idéntico (cuenta bloqueada 15 min)
curl -s -i -X POST $BASE/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"contrasena-segura-1"}'
```

Comparar el cuerpo de este 401 con el de un correo inexistente: mismo `type`, `status` y
`detail` (SC-002).

## 6. Roles (US4, FR-021)

El administrador puede iniciar sesión con las credenciales del paso 2 y su token lleva
`roles: ["ADMIN"]`. El test `AuthSecurityTests` comprueba que un `CUSTOMER` recibe 403 en un
recurso protegido con `@PreAuthorize("hasRole('ADMIN')")`.

## 7. Arquitectura y contrato

- `ModularityTests` pasa: `identity` solo expone `UserRegistered` y `Role` en su raíz.
- `GET /v3/api-docs` lista las 5 rutas de `/api/v1/auth` con los mismos estados y esquemas que
  `contracts/openapi.yaml`.

## 8. Comprobaciones negativas de configuración

- Arrancar sin `MANGATECA_SECURITY_JWT_ISSUER` (perfil distinto de `dev`) → falla el arranque.
- Arrancar con una clave privada que no corresponde a la pública → falla el arranque.
- `grep -R "BEGIN PRIVATE KEY" --include='*' . | grep -v -e dev/keys -e src/test/resources/keys`
  → sin resultados (ninguna clave fuera de las rutas de dev/test).
