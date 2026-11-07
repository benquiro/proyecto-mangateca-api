# Quickstart: validar Catalog (Public Read)

**Feature**: `003-catalog-public-read` | Esta guía valida la feature de punta a punta.
Contrato HTTP: [contracts/openapi.yaml](contracts/openapi.yaml). Esquema e índices:
[data-model.md](data-model.md). Decisiones: [research.md](research.md).

## 0. Requisitos previos

- Docker en marcha (PostgreSQL 18 de `compose.yaml` y Testcontainers).
- Java 25 y el Maven Wrapper del repositorio.
- `curl`, `jq` y `psql` (o `docker compose exec postgres psql`).

## 1. Pruebas automatizadas

```bash
./mvnw verify
```

Esperado: compila sin advertencias y pasan los tests de 001 y 002 más los de `catalog`
(bajo `src/test/java/com/mangateca/api/`):

| Clase | Ubicación |
|-------|-----------|
| `MoneyTests`, `IsbnTests` | `catalog/domain/` |
| `ProductSearchIT`, `ClassificationRepositoryIT` | `catalog/domain/` |
| `ProductQueryServiceIT`, `ClassificationQueryServiceIT` | `catalog/application/` |
| `ProductControllerTests`, `SeriesControllerTests`, `ClassificationControllerTests` | `catalog/web/` |
| `CatalogSecurityIT` (completa: GET y HEAD anónimos con 200 en las ocho rutas; escrituras en 401) | `catalog/` |
| `ModularityTests`, `ApiApplicationTests` | raíz |
| `FlywayMigrationIT`, `SchemaDriftIT` | `persistence/` |

Los datos de los tests de catálogo salen de `catalog/support/CatalogTestData` y
`CatalogFixtures`. Para ejecutar una sola clase (no hay failsafe: surefire ejecuta tanto
`*Tests` como `*IT`):

```bash
./mvnw test -Dtest=ProductSearchIT -Dit.test=ProductSearchIT -Dsurefire.failIfNoSpecifiedTests=false
```

## 2. Arranque local y migración

```bash
./mvnw spring-boot:run
```

Esperado: perfil `dev`; Flyway aplica `V3__create_catalog.sql`; sin errores de
`ddl-auto=validate`. Comprobar extensión y función:

```bash
docker compose exec postgres psql -U mangateca -d mangateca \
  -c "select extname from pg_extension where extname in ('unaccent','pg_trgm');" \
  -c "select catalog_normalize('Dragón Ball');"          # dragon ball
```

## 3. Datos manuales para probar

La feature es de solo lectura y las migraciones no llevan datos de ejemplo. Para probar a mano,
insertar unas pocas filas con `psql` (editorial, géneros, autores, una serie con 3 volúmenes y
uno sin publicar; ver columnas en [data-model.md](data-model.md)), o cargar el volumen
sintético de §6, que también sirve para este recorrido. Estos datos **no se versionan**. Los
datos de ejemplo del proyecto llegan con la feature 012.

> **Advertencia: los ISBN deben tener un dígito verificador válido.** El `CHECK` de la tabla
> solo valida la forma (10 o 13 caracteres), pero el `AttributeConverter` de `isbn` rechaza un
> checksum inválido al **leer**, y entonces `GET /api/v1/products` responde 500. Usar un ISBN
> real (p. ej. `9784088820118`) o dejar `isbn` en `NULL`.

Al terminar, vaciar lo insertado (la BD local no tiene más datos de catálogo):

```bash
docker compose exec postgres psql -U mangateca -d mangateca \
  -c "TRUNCATE product_authors, product_genres, products, series, authors, genres, publishers"
```

## 4. Recorrido funcional (sin credenciales)

```bash
BASE=http://localhost:8080/api/v1

# 4a. Listado por defecto → 200, 20 como máximo, novedades primero, content + page
curl -s "$BASE/products" | jq '{n: (.content|length), page}'

# 4b. size=101 → 400 con errors[field=size]
curl -s -i "$BASE/products?size=101"

# 4c. Búsqueda sin tildes, parcial, varias palabras
curl -s "$BASE/products?q=drag"            | jq '.content[].title'
curl -s "$BASE/products?q=dragon%20toriyama" | jq '.content[].title'

# 4d. ISBN con guiones o sin ellos → el mismo producto
curl -s "$BASE/products?q=978-4-08-882011-8" | jq '.page.totalElements'

# 4e. Filtros combinados y orden
curl -s "$BASE/products?type=MANGA&minPrice=5000&maxPrice=12000&sort=price,desc" | jq '.content[].price'

# 4f. Parámetros inválidos → 400 con el campo afectado (sin eco del valor)
curl -s "$BASE/products?minPrice=9000&maxPrice=100"   # errors[field=minPrice]
curl -s "$BASE/products?type=XYZ"                      # errors[field=type]
curl -s "$BASE/products?sort=synopsis"                 # errors[field=sort]
curl -s "$BASE/products/no-es-un-uuid"                 # errors[field=id]

# 4g. No publicado vs inexistente → cuerpos idénticos salvo `instance`
curl -s "$BASE/products/<id-no-publicado>"
curl -s "$BASE/products/<uuid-inexistente>"

# 4h. Serie: datos, y volúmenes publicados en orden (el 2 oculto no aparece)
curl -s "$BASE/series" | jq '.content'
curl -s "$BASE/series/<series-id>"
curl -s "$BASE/series/<series-id>/volumes" | jq '.content[] | {title, volumeNumber}'

# 4i. Clasificaciones: solo las que tienen productos publicados, orden alfabético
curl -s "$BASE/genres" | jq '.content[].name'
curl -s "$BASE/authors?size=5" | jq '.page'
curl -s "$BASE/publishers" | jq '.content[].name'
```

## 5. Seguridad

```bash
# 5a. Escritura anónima sobre las rutas de catálogo → 401 (deny-by-default)
curl -s -o /dev/null -w '%{http_code}\n' -X POST   $BASE/products -H 'Content-Type: application/json' -d '{}'
curl -s -o /dev/null -w '%{http_code}\n' -X PUT    $BASE/products/<id>
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $BASE/products/<id>

# 5b. HEAD anónimo también es lectura → 200 sin cuerpo
curl -s -o /dev/null -w '%{http_code}\n' -I "$BASE/products"

# 5c. Un token inválido en un GET público no rompe la respuesta (se ignora) → 200
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/products" -H 'Authorization: Bearer basura'
```

## 6. Rendimiento (SC-002): verificación manual con `EXPLAIN ANALYZE`

No hay test automatizado. Objetivo: p95 < 500 ms con 10.000 productos publicados.

Condiciones de medición: un solo cliente con peticiones secuenciales (sin concurrencia), caché
caliente tras `ANALYZE` y entorno local de desarrollo (PostgreSQL de `compose.yaml`). Cuentan
las consultas de listado de `/products` con o sin `q` y filtros. La concurrencia y la
infraestructura de producción quedan fuera de SC-002.

### 6.1 Carga sintética (BD local, nunca en migraciones ni en el repositorio)

Guardar como `seed.sql` fuera del repositorio y ejecutar con
`docker compose exec -T postgres psql -U mangateca -d mangateca -v ON_ERROR_STOP=1 < seed.sql`.
Crea 20 editoriales, 15 géneros, 2.000 autores (con tildes), 500 series (las 1.500 primeras
filas son volúmenes en series de 3), 10.000 productos publicados, 3 géneros y 2 autores por
producto, deja `Dragon Ball 8` sin publicar (volumen 2 de una serie) y `Monster 3` con un ISBN
válido. Los ISBN del resto quedan en `NULL` (véase la advertencia de §3).

```sql
-- Carga sintetica para pruebas manuales (NO versionar datos en migraciones).
BEGIN;
INSERT INTO publishers (id, name)
SELECT gen_random_uuid(), 'Editorial ' || n FROM generate_series(1, 20) n;

INSERT INTO genres (id, name)
SELECT gen_random_uuid(), (ARRAY['Acción','Aventura','Comedia','Drama','Fantasía','Terror','Romance','Ciencia ficción',
  'Misterio','Deportes','Histórico','Slice of life','Shōnen','Seinen','Shōjo'])[n]
FROM generate_series(1, 15) n;

INSERT INTO authors (id, name)
SELECT gen_random_uuid(),
  (ARRAY['Akira','Eiichiro','Naoki','Rumiko','Hirohiko','José','María','Sebastián','Ángel','Ñandú'])[1 + n % 10]
  || ' ' || (ARRAY['Toriyama','Oda','Urasawa','Takahashi','Araki','Pérez','González','Müller','Núñez','Dragón'])[1 + (n / 10) % 10]
  || ' ' || n
FROM generate_series(1, 2000) n;

INSERT INTO series (id, name, description)
SELECT gen_random_uuid(), 'Serie ' || n, 'Descripción de la serie ' || n FROM generate_series(1, 500) n;

-- 10.000 productos publicados; los 1.500 primeros pertenecen a series (3 volumenes por serie)
INSERT INTO products (id, type, status, title, synopsis, price, isbn, language, page_count, publication_date,
                      cover_url, publisher_id, series_id, volume_number, created_at)
SELECT gen_random_uuid(),
  (ARRAY['MANGA','LIBRO','COMIC'])[1 + n % 3],
  'PUBLISHED',
  (ARRAY['Dragon Ball','Dragón de Jade','One Piece','Monster','Naruto','Épica del Sur','Cien años','Batman'])[1 + n % 8] || ' ' || n,
  'Sinopsis ' || n,
  1000 + (n * 37) % 20000,
  NULL,
  'es', 100 + n % 300,
  DATE '2000-01-01' + (n % 9000),
  'https://example.com/cover/' || n || '.jpg',
  (SELECT id FROM publishers ORDER BY id OFFSET (n % 20) LIMIT 1),
  CASE WHEN n <= 1500 THEN (SELECT id FROM series ORDER BY id OFFSET ((n - 1) / 3) LIMIT 1) END,
  CASE WHEN n <= 1500 THEN 1 + (n - 1) % 3 END,
  now() - (n || ' minutes')::interval
FROM generate_series(1, 10000) n;

-- un producto no publicado y uno sin serie con ISBN real
UPDATE products SET status = 'UNPUBLISHED' WHERE title = 'Dragon Ball 8';
UPDATE products SET isbn = '9784088820118' WHERE title = 'Monster 3';

INSERT INTO product_authors (product_id, author_id)
SELECT p.id, a.id FROM (SELECT id, row_number() OVER (ORDER BY id) rn FROM products) p
JOIN (SELECT id, row_number() OVER (ORDER BY id) rn FROM authors) a
  ON a.rn IN (1 + p.rn % 2000, 1 + (p.rn * 7) % 2000);

INSERT INTO product_genres (product_id, genre_id)
SELECT p.id, g.id FROM (SELECT id, row_number() OVER (ORDER BY id) rn FROM products) p
JOIN (SELECT id, row_number() OVER (ORDER BY id) rn FROM genres) g
  ON g.rn IN (1 + p.rn % 15, 1 + (p.rn * 4) % 15, 1 + (p.rn * 11) % 15);
COMMIT;
ANALYZE products, authors, product_authors, product_genres;
```

### 6.2 Orden y paginación

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM products WHERE status = 'PUBLISHED'
ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0;
```

Esperado: `Index Only Scan Backward` sobre `products_pub_created_idx`, sin `Sort`. Repetir con
`ORDER BY price, id` (`products_pub_price_idx`) y `ORDER BY title, id` (`products_pub_title_idx`).

### 6.3 Búsqueda de un término de 3 o más caracteres

Hibernate genera, por cada palabra de `q`, `id IN (títulos UNION productos de autores)`; el
`OR ... EXISTS` correlacionado de una versión anterior obligaba a un `Seq Scan` sobre `products`
y ya no se usa. Para ver la SQL real, activar `log_statement = 'all'` en la BD
(`ALTER SYSTEM SET log_statement = 'all'; SELECT pg_reload_conf();`, y `ALTER SYSTEM RESET
log_statement` al terminar) y leer `docker compose logs postgres`.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p1.id FROM products p1
WHERE p1.status = 'PUBLISHED'
  AND p1.id IN (
    SELECT p2.id FROM products p2
    WHERE catalog_normalize(p2.title) LIKE catalog_normalize('%epica%') ESCAPE '\'
    UNION
    SELECT p3.id FROM products p3
    JOIN product_authors pa ON p3.id = pa.product_id
    JOIN authors a ON a.id = pa.author_id
    WHERE catalog_normalize(a.name) LIKE catalog_normalize('%epica%') ESCAPE '\')
ORDER BY p1.created_at DESC, p1.id DESC LIMIT 20 OFFSET 0;
```

Esperado: `Bitmap Index Scan` sobre `products_title_trgm_idx` y sobre `authors_name_trgm_idx`.
Repetir con el patrón como parámetro para confirmar que el índice se sigue usando con plan
genérico:

```sql
SET plan_cache_mode = force_generic_plan;
PREPARE s(text) AS
SELECT p1.id FROM products p1
WHERE p1.status = 'PUBLISHED'
  AND p1.id IN (
    SELECT p2.id FROM products p2
    WHERE catalog_normalize(p2.title) LIKE catalog_normalize($1) ESCAPE '\'
    UNION
    SELECT p3.id FROM products p3
    JOIN product_authors pa ON p3.id = pa.product_id
    JOIN authors a ON a.id = pa.author_id
    WHERE catalog_normalize(a.name) LIKE catalog_normalize($1) ESCAPE '\')
ORDER BY p1.created_at DESC, p1.id DESC LIMIT 20 OFFSET 0;
EXPLAIN (ANALYZE, BUFFERS) EXECUTE s('%epica%');
EXPLAIN (ANALYZE, BUFFERS) EXECUTE s('%drag%');
DEALLOCATE s;
RESET plan_cache_mode;
```

### 6.4 Término corto (1-2 caracteres)

Repetir 6.3 con `'%a%'`. Esperado: recorrido de `products` y `authors` sin índice de
trigramas (R1); el tiempo total debe seguir muy por debajo de 500 ms con 10.000 filas.

### 6.5 Filtros

`type`, `publisher_id`, `author_id`/`genre_id` (`EXISTS`) y rango de `price`: comprobar que cada
uno usa su índice (`products_pub_*`, `product_authors_author_idx`, `product_genres_*`).

El índice concreto depende de la selectividad, no solo de la columna filtrada:

- `author_id` (2.000 autores, ~10 productos por autor) es muy selectivo: el planificador parte de
  `product_authors_author_idx (author_id, product_id)` y busca los productos.
- `genre_id` (15 géneros, ~1.650 productos por género) es poco selectivo. En la página que ordena
  por novedades, el planificador recorre `products_pub_created_idx` y, por cada producto, sonda
  `product_genres_pkey (product_id, genre_id)` con ambas columnas (el `EXISTS` correlacionado
  aporta `product_id`); para 20 filas basta con unos 300 sondeos. Es lo esperado, no un desvío.
- `product_genres_genre_idx (genre_id, product_id)` se usa cuando el planificador parte del
  género: el `count` de la paginación (`Hash Join` con `Bitmap Index Scan` en
  `product_genres_genre_idx`) y las consultas con otro filtro o un `OFFSET` profundo. Por tanto no
  es redundante. Sin el pkey, el plan de novedades sondearía `product_genres_genre_idx`.
- Los listados `findVisible` de géneros y autores no usan estos índices: recorren las tablas
  puente completas (`Seq Scan` + `Hash Join`, ~6 ms con 10.000 productos).

### 6.6 Extremo a extremo (p95)

Con la aplicación en marcha, 220 peticiones por caso y percentil 95 con `sort`/`awk`:

```bash
B=http://localhost:8080/api/v1
p95() { for i in $(seq 1 220); do curl -s -o /dev/null -w '%{time_total}\n' "$1"; done \
  | sort -n | awk '{a[NR]=$1} END{printf "p50=%.0fms p95=%.0fms max=%.0fms\n", a[int(NR*.5)]*1000, a[int(NR*.95)]*1000, a[NR]*1000}'; }
p95 "$B/products"
p95 "$B/products?q=drag"
p95 "$B/products?q=dragon%20ball%20jose%20perez"
```

Aceptación: p95 < 500 ms. Si algún paso no usa el índice esperado, se corrige el índice o la
consulta antes de dar la feature por terminada; no se añade caché (Principio VII).

### 6.7 Resultados: Verificado a mano 2026-11-07 (T064)

PostgreSQL 18 local (`compose.yaml`), 10.000 productos publicados, `ANALYZE` hecho, app en
perfil `dev`. Tiempos de `EXPLAIN` = `Execution Time`.

| Caso | Plan / índices observados | Ejecución |
|------|---------------------------|-----------|
| Orden por defecto | `Index Only Scan Backward` en `products_pub_created_idx`, sin `Sort` | 0,03 ms |
| Orden por `price` / `title` | `products_pub_price_idx` / `products_pub_title_idx` | 0,01 ms |
| Búsqueda `%drag%` (literal) | `Bitmap Index Scan` en `products_title_trgm_idx` y `authors_name_trgm_idx` | 9,8 ms |
| Búsqueda `%epica%` (literal) y su `count` | ambos índices de trigramas | 3,1 ms / 2,8 ms |
| Búsqueda con plan genérico (`$1`) | ambos índices de trigramas (`%epica%` y `%drag%`) | 3,8 ms / 8,8 ms |
| Término corto `%a%` | sin trigramas (`Seq Scan` en `products` y `authors`), esperado | 20,5 ms |
| Filtro `type` / `publisher_id` | `products_pub_created_idx` | 0,03 / 0,07 ms |
| Filtro `author_id` | `product_authors_author_idx` | 0,09 ms |
| Filtro `genre_id` | `product_genres_pkey` en el listado por novedades (el `count` usa `product_genres_genre_idx`); véase §6.5 | 0,27 ms |
| Rango de `price` | `products_pub_price_idx` | 0,01 ms |

| Caso extremo a extremo (220 peticiones) | p95 | máx. |
|------------------------------------------|-----|------|
| `/products` (sin `q`) | 9 ms | 15 ms |
| `q=drag` | 19 ms | 32 ms |
| `q=epica` | 13 ms | 14 ms |
| `q=a` (término corto) | 38 ms | 40 ms |
| `q=dragon toriyama` (2 palabras) | 27 ms | 29 ms |
| `q=dragon ball jose perez` (4 palabras) | 37 ms | 40 ms |
| `q=dragon ball jose perez manga` (5 palabras) | 9 ms | 11 ms |
| `type=MANGA&minPrice=5000&maxPrice=12000&sort=price,desc` | 4 ms | 6 ms |

Los recorridos de §2 a §5 y §7 también se ejecutaron a mano con estos datos (respuestas, códigos 400/404/401 y
cuerpos como los descritos arriba).

Todos muy por debajo de 500 ms (SC-002). Con datos de otra carga el reparto de autores cambia,
así que los totales no son reproducibles al detalle; sí lo son los planes.

## 7. Contrato OpenAPI

Con la aplicación en marcha, contrastar `GET /v3/api-docs` con
[contracts/openapi.yaml](contracts/openapi.yaml): rutas, parámetros (`page`, `size`, `sort`,
filtros), códigos de respuesta, esquemas de página (`content` + `page`), `security: []` en las 8 operaciones y
los textos de los ejemplos. Las divergencias aceptadas están listadas al inicio del contrato.
