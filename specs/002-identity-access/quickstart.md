# Quickstart: validar Identity & Access

**Feature**: `002-identity-access` | Esta guía valida la feature de punta a punta.
Contrato HTTP: [contracts/openapi.yaml](contracts/openapi.yaml). Configuración:
[contracts/configuration.md](contracts/configuration.md). Esquema: [data-model.md](data-model.md).

## 0. Requisitos previos

- Docker en marcha (PostgreSQL 18 de `compose.yaml` y Testcontainers).
- Java 25 y el Maven Wrapper del repositorio.
- `curl` y `jq`.

## 1. Pruebas automatizadas

```bash
./mvnw verify
```

Esperado: compila sin advertencias. Surefire activa el perfil `test` (par RSA efímero) y pasan los
tests de 001 y los de `identity`: `ModularityTests`, `ApiApplicationTests`, `UserLockoutTests`,
`AuthenticationServiceTests`, `RefreshTokenServiceTests`, `AuthControllerTests`, `AuthFlowIT`,
`BootstrapAdminIT` y `UserRegisteredIT` (ver la sección Testing de [plan.md](plan.md)).

## 2. Arranque local con administrador inicial

```bash
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD='cambiar-esta-clave-1' \
./mvnw spring-boot:run
```

Esperado:

- Arranca con el perfil `dev`, sin variables de claves.
- Log INFO de par RSA efímero de desarrollo, sin material de clave.
- Flyway aplica V2.
- Log INFO con el `userId` del administrador creado. **La contraseña no aparece en ningún log.**

Reinicia con las mismas variables y comprueba que no se crea otro administrador (SC-007):

```bash
docker compose exec postgres psql -U <usuario> -d <base> \
  -c "select count(*) from users where role = 'ADMIN';"   # 1
```

## 3. Flujo de cliente

`localhost` cuenta como contexto seguro, así que `curl` conserva la cookie `Secure` en el archivo
de cookies.

```bash
BASE=http://localhost:8080/api/v1/auth

# 3a. Registro → 201, sin contraseña en la respuesta; role = CUSTOMER
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"Ana@Example.com","displayName":"Ana","password":"contrasena-segura-1"}'

# 3b. Mismo correo con otra capitalización → 409 (email-already-in-use)
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"ANA@example.com","displayName":"Otra","password":"contrasena-segura-1"}'

# 3c. Intento de elegir ADMIN → 400 con errors[field=role]; no se crea la cuenta
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d '{"email":"mal@example.com","displayName":"Mal","password":"contrasena-segura-1","role":"ADMIN"}'

# 3d. Contraseña de 64 caracteres de 4 bytes (256 bytes > 72) → 400 errors[field=password]
PW=$(printf '😀%.0s' $(seq 1 64))
curl -s -i -X POST $BASE/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"emoji@example.com\",\"displayName\":\"Emoji\",\"password\":\"$PW\"}"

# 3e. Login → 200 con access_token, token_type y expires_in (900) + Set-Cookie refresh_token
ACCESS=$(curl -s -c jar.txt -X POST $BASE/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"contrasena-segura-1"}' | jq -r .access_token)
grep refresh_token jar.txt            # cookie HttpOnly presente; el cuerpo no trae refresh_token

# 3f. Identidad actual → 200 (id, email, displayName, role=CUSTOMER)
curl -s $BASE/me -H "Authorization: Bearer $ACCESS"
# Sin token → 401 application/problem+json
curl -s -i $BASE/me
```

Revisa el payload del JWT (`echo $ACCESS | cut -d. -f2 | base64 -d`). Debe traer `sub` (UUID),
`roles` (`["CUSTOMER"]`), `iss` (el de `application-dev.yaml`), `iat` y `exp = iat + 900`.

## 4. Renovación, reutilización y cierre de sesión

```bash
cp jar.txt old.txt
# 4a. Renovar → 200 y cookie nueva; el token anterior queda invalidado
curl -s -b jar.txt -c jar.txt -X POST $BASE/refresh
# 4b. Reutilizar el token anterior (old.txt) → 401 invalid-refresh-token
curl -s -i -b old.txt -X POST $BASE/refresh
# 4c. Tras la reutilización TODA la familia está revocada: la cookie nueva también falla → 401
curl -s -i -b jar.txt -X POST $BASE/refresh
# 4d. Logout idempotente → 204 aunque no haya cookie
curl -s -i -X POST $BASE/logout
```

Comprueba en la base de datos que no hay tokens en claro:

```bash
docker compose exec postgres psql -U <usuario> -d <base> \
  -c "select family_id, encode(token_hash,'hex') as hash, revoked_at, replaced_by from refresh_tokens;"
```

Esperado: hashes de 64 caracteres hexadecimales; ninguna columna contiene el valor de la cookie.

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

Compara el cuerpo de este 401 con el de un correo inexistente: deben coincidir `type`, `status` y
`detail` (SC-002).

## 6. Roles (US4, FR-021)

El administrador inicia sesión con las credenciales del paso 2 y su token lleva
`roles: ["ADMIN"]`. `AuthFlowIT` comprueba que un `CUSTOMER` recibe 403 en un recurso con
`@PreAuthorize("hasRole('ADMIN')")`.

## 7. Arquitectura y contrato

- `ModularityTests` pasa: la raíz de `identity` solo expone `UserRegistered` y `Role`.
- `GET /v3/api-docs` lista las 5 rutas de `/api/v1/auth` con los mismos códigos de estado y
  esquemas que `contracts/openapi.yaml`.

## 8. Claves fuera de dev/test

```bash
./mvnw -q package -DskipTests
# Sin perfil y sin claves → el arranque falla nombrando las variables que faltan
java -jar target/api-0.0.1-SNAPSHOT.jar
# Con un par que no corresponde (pública de otro par) → el arranque falla
```

Comprueba también que el repositorio no contiene claves:

```bash
git grep -l "BEGIN .*PRIVATE KEY"     # sin resultados
```
