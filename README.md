# proyecto-mangateca-api

## Arranque local

### Requisitos

- JDK 25
- Docker en ejecución, con `docker compose`

No hace falta nada más: no se instala PostgreSQL ni Maven (se usa el wrapper `./mvnw`).

### Comando

```bash
./mvnw spring-boot:run
```

### Qué ocurre

1. Docker Compose levanta el contenedor `postgres:18` definido en `compose.yaml`.
2. Flyway aplica la migración V1 sobre la base de datos.
3. La API escucha en `http://localhost:8080`.

### Detener la aplicación

Pulsa `Ctrl+C`. Al apagarse la aplicación también se detiene el contenedor de PostgreSQL
(no se elimina, así que los datos persisten entre reinicios).

### Sin Docker

Si Docker no está en ejecución o no tiene `docker compose`, el arranque falla con un mensaje
que indica el requisito ausente.

## Configuración por entorno

En local (perfil `dev`, activado por `./mvnw spring-boot:run`) no hace falta ninguna variable:
la base es el contenedor de `compose.yaml`, el JWT usa un par RSA efímero en memoria y CORS
admite `http://localhost:5173`. Ningún secreto se versiona.

Fuera de dev, la configuración llega por variables de entorno:

| Variable | Obligatoria | Ejemplo | Si falta o es inválida |
|----------|-------------|---------|------------------------|
| `SPRING_DATASOURCE_URL` | Sí | `jdbc:postgresql://db:5432/mangateca` | El arranque falla |
| `SPRING_DATASOURCE_USERNAME` | Sí | `mangateca` | El arranque falla |
| `SPRING_DATASOURCE_PASSWORD` | Sí | *(secreto)* | El arranque falla |
| `MANGATECA_CORS_ALLOWED_ORIGINS` | No | `https://app.example.com,https://admin.example.com` | Vacía: ningún origen cruzado permitido. Con `*` o un valor que no sea `scheme://host[:port]`, la aplicación no arranca |

## Identidad y claves

En `dev` y `test`, si no se define ninguna ruta de clave, la aplicación genera un par RSA 2048
efímero en memoria: los tokens dejan de ser válidos al reiniciar. Fuera de esos perfiles las
claves son obligatorias y, si faltan, el arranque falla. La variable
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION` ya no se usa (ADR-006).

| Variable | Obligatoria | Ejemplo | Notas |
|----------|-------------|---------|-------|
| `MANGATECA_IDENTITY_JWT_ISSUER` | Sí (fuera de dev/test) | `https://<host-publico-de-la-api>` | Se escribe en `iss` y lo exige el decodificador |
| `MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION` | Sí (fuera de dev/test) | `file:/run/secrets/jwt-private.pem` | PEM PKCS#8 |
| `MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION` | Sí (fuera de dev/test) | `file:/run/secrets/jwt-public.pem` | PEM X.509; debe formar par con la privada |

Generar el par (fuera del repositorio; guárdalo en el gestor de secretos del despliegue):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

`genpkey` produce la clave privada en PKCS#8 (`BEGIN PRIVATE KEY`) y `-pubout` la pública en
X.509 (`BEGIN PUBLIC KEY`).

### Administrador inicial (opcional)

| Variable | Ejemplo |
|----------|---------|
| `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_EMAIL` | `<email-del-administrador>` |
| `MANGATECA_IDENTITY_BOOTSTRAP_ADMIN_PASSWORD` | `<contraseña-secreta>` (8–64 caracteres, máx. 72 bytes) |

Si faltan o son inválidas, la aplicación arranca igualmente sin crear el administrador y emite
un aviso sin datos sensibles. Nunca versiones valores reales.

## Catálogo público

Lectura anónima (sin token) de productos publicados. Cada `GET` acepta también `HEAD`; cualquier
otro método responde 401.

| Ruta | Descripción |
|------|-------------|
| `GET /api/v1/products` | Listado con búsqueda (`q`), filtros (`type`, `genreId`, `authorId`, `publisherId`, `seriesId`, `minPrice`, `maxPrice`) y `sort` |
| `GET /api/v1/products/{id}` | Detalle de un producto publicado |
| `GET /api/v1/series` | Series con al menos un volumen publicado |
| `GET /api/v1/series/{id}` | Datos de una serie |
| `GET /api/v1/series/{id}/volumes` | Volúmenes publicados de una serie, por número de volumen |
| `GET /api/v1/genres` | Géneros con productos publicados |
| `GET /api/v1/authors` | Autores con productos publicados |
| `GET /api/v1/publishers` | Editoriales con productos publicados |

Todos los listados son paginados: `page` desde 0 y `size` de 20 por defecto, con máximo 100 (un
valor mayor responde 400, no se ajusta en silencio). La respuesta lleva `content` y `page`. Un
producto no publicado y uno inexistente responden el mismo 404.

### Extensiones de PostgreSQL

La migración V3 ejecuta `CREATE EXTENSION IF NOT EXISTS unaccent` y `pg_trgm` (búsqueda sin
tildes y parcial). Ambas son extensiones de confianza (*trusted*) desde PostgreSQL 13, así que
las puede crear el dueño de la base de datos; si el usuario de la aplicación no lo es, un
superusuario debe crearlas antes del primer arranque. En local (`postgres:18` de
`compose.yaml`) no hace falta nada.

## Pruebas

```bash
./mvnw verify
```

Requiere Docker: la suite levanta una instancia efímera de `postgres:18` con Testcontainers,
independiente de la base de desarrollo. Solo se escriben tests vitales (arquitectura y
arranque, seguridad crítica, errores sin fugas y esquema); no hay umbral de cobertura. El build
falla ante cualquier advertencia de compilación.

## Decisiones de arquitectura

Los ADR de las dependencias y herramientas adoptadas están en [`docs/adr/`](docs/adr/).
