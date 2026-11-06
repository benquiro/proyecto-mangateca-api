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

> Verificado a mano 2026-11-05 (T065).

```bash
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL=admin@example.com \
MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD='cambiar-esta-clave-1' \
./mvnw spring-boot:run
```

Esperado:

- Arranca con el perfil `dev`, sin variables de claves.
- Log INFO de par RSA efímero de desarrollo, sin material de clave.
- Flyway aplica V2.
- Log INFO con el `userId` del administrador creado (solo en el primer arranque; en los siguientes no se
  emite ningún log). **La contraseña no aparece en ningún log.**
- No aparece el log «Using generated security password» (research R9).

Reinicia con las mismas variables y comprueba que no se crea otro administrador (SC-007):

```bash
docker compose exec postgres psql -U mangateca -d mangateca \
  -c "select count(*) from users where role = 'ADMIN';"   # 1
```

## 3. Flujo de cliente

> Verificado a mano 2026-11-05 (T065): 3a a 3f y payload del JWT.

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

# 3d. Contraseña de 30 emojis (60 caracteres, 120 bytes > 72) → 400 errors[field=password]
#     (con 64 emojis también da 400, pero por longitud en caracteres y no por el límite de bytes)
PW=$(printf '😀%.0s' $(seq 1 30))
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

Revisa el payload del JWT (`echo $ACCESS | cut -d. -f2 | tr '_-' '/+' | base64 -d`; el JWT usa
base64url sin relleno, así que `base64 -d` puede avisar de entrada incompleta). Debe traer `sub` (UUID),
`roles` (`["CUSTOMER"]`), `iss` (el de `application-dev.yaml`), `iat` y `exp = iat + 900`.

## 4. Renovación, reutilización y cierre de sesión

> Verificado a mano 2026-11-05 (T065): 4a a 4d y hashes en base de datos.

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
docker compose exec postgres psql -U mangateca -d mangateca \
  -c "select family_id, encode(token_hash,'hex') as hash, revoked_at, replaced_by from refresh_tokens;"
```

Esperado: hashes de 64 caracteres hexadecimales; ninguna columna contiene el valor de la cookie.

## 5. Bloqueo por fuerza bruta (SC-004)

> Verificado a mano 2026-11-05 (T065): 401 x5, sexto intento correcto 401, cuerpo idéntico al de un
> correo inexistente.

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

> Verificado a mano 2026-11-05 (T065): el login del administrador devuelve `roles: ["ADMIN"]`. El 403
> para `CUSTOMER` lo cubre `AuthFlowIT` (no hay endpoint protegido por rol que probar a mano).

El administrador inicia sesión con las credenciales del paso 2 y su token lleva
`roles: ["ADMIN"]`. `AuthFlowIT` comprueba que un `CUSTOMER` recibe 403 en un recurso con
`@PreAuthorize("hasRole('ADMIN')")`.

## 7. Arquitectura y contrato

> Verificado a mano 2026-11-05 (T065): `GET /v3/api-docs` lista register (201/400/409), login
> (200/400/401), refresh (200/401), logout (204) y me (200/401). `ModularityTests` lo ejecuta `verify`.

- `ModularityTests` pasa: la raíz de `identity` solo expone `UserRegistered` y `Role`.
- `GET /v3/api-docs` lista las 5 rutas de `/api/v1/auth` con los mismos códigos de estado y
  esquemas que `contracts/openapi.yaml`.

## 8. Claves fuera de dev/test

> Verificado a mano 2026-11-05 (T065) con un par RSA generado fuera del repositorio.

```bash
./mvnw -q package -DskipTests
# Sin perfil, con la base configurada y sin claves → el arranque falla nombrando las variables que
# faltan (sin las variables de datasource falla antes, por la base de datos)
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:<puerto>/mangateca \
  SPRING_DATASOURCE_USERNAME=mangateca SPRING_DATASOURCE_PASSWORD=<clave>
java -jar target/api-0.0.1-SNAPSHOT.jar
# Con un par que no corresponde (pública de otro par) → el arranque falla:
# «Las claves JWT privada y publica no forman un par RSA.»
MANGATECA_IDENTITY_JWT_ISSUER=https://api.example.test \
MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION=file:/ruta/privada-a.pem \
MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION=file:/ruta/publica-b.pem \
java -jar target/api-0.0.1-SNAPSHOT.jar
```

El puerto de PostgreSQL es aleatorio: `docker compose port postgres 5432`. Con un par válido
(`privada-a` + `publica-a`) la aplicación arranca y `/actuator/health` responde 200.

Comprueba también que el repositorio no contiene claves:

```bash
git grep -l -e "-----BEGIN [A-Z ]*PRIVATE KEY-----"     # sin resultados (el patrón sin guiones
                                                      # coincide con texto de documentación)
```
