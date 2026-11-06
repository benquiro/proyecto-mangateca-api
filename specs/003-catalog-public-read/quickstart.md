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

Esperado: compila sin advertencias y pasan los tests de 001 y 002 más los de `catalog`:
`MoneyTests`, `IsbnTests`, `ProductSearchIT`, `ClassificationRepositoryIT`,
`ProductQueryServiceIT`, `ProductControllerTests`, `SeriesControllerTests`,
`ClassificationControllerTests`, `CatalogSecurityIT` y `ModularityTests` (ver «Testing» en
[plan.md](plan.md)).

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
uno sin publicar; ver columnas en [data-model.md](data-model.md)). Estos datos **no se
versionan**. Los datos de ejemplo del proyecto llegan con la feature 012.

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

# 5b. Un token inválido en un GET público no rompe la respuesta (se ignora) → 200
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/products" -H 'Authorization: Bearer basura'
```

## 6. Rendimiento (SC-002): verificación manual con `EXPLAIN ANALYZE`

No hay test automatizado. Objetivo: p95 < 500 ms con 10.000 productos publicados.

1. **Cargar volumen sintético** en una base de pruebas (no en las migraciones ni en el
   repositorio): insertar unos 10.000 productos publicados con `generate_series`, con títulos y
   autores variados (incluyendo tildes), 3 géneros y 1 editorial cada uno, y ejecutar
   `ANALYZE products, authors, product_authors, product_genres;`.
2. **Orden y paginación** (listado por defecto; repetir con precio y título):

   ```sql
   EXPLAIN (ANALYZE, BUFFERS)
   SELECT id FROM products WHERE status = 'PUBLISHED'
   ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0;
   ```

   Esperado: `Index Scan` sobre `products_pub_created_idx`, sin `Sort`.
3. **Búsqueda de un término de 3 o más caracteres**:

   ```sql
   EXPLAIN (ANALYZE, BUFFERS)
   SELECT id FROM products p
   WHERE catalog_normalize(p.title) LIKE catalog_normalize('%drag%')
      OR EXISTS (SELECT 1 FROM product_authors pa JOIN authors a ON a.id = pa.author_id
                 WHERE pa.product_id = p.id
                   AND catalog_normalize(a.name) LIKE catalog_normalize('%drag%'))
   ORDER BY created_at DESC LIMIT 20;
   ```

   Esperado: `Bitmap Index Scan` sobre `products_title_trgm_idx` y
   `authors_name_trgm_idx`. Repetir con el patrón como parámetro (`PREPARE`/`EXECUTE`, también
   con `SET plan_cache_mode = force_generic_plan`) para confirmar que el índice se sigue usando.
4. **Término corto (1–2 caracteres)**: repetir con `'%a%'`. Esperado: recorrido de los
   productos publicados, sin índice de trigramas; el tiempo total debe seguir muy por debajo
   de 500 ms con 10.000 filas (R1).
5. **Filtros**: `type`, `publisher_id`, `author_id`/`genre_id` (`EXISTS`) y rango de `price`:
   comprobar que cada uno usa su índice (`products_pub_*`, `product_authors_author_idx`,
   `product_genres_genre_idx`).
6. **Extremo a extremo**: con el servidor en marcha, medir el listado con y sin búsqueda
   (p. ej. 200 peticiones con `curl -w '%{time_total}'` y calcular el percentil 95).
   Aceptación: p95 < 500 ms.

Si algún paso no usa el índice esperado, se corrige el índice o la consulta antes de dar la
feature por terminada; no se añade caché (Principio VII).

## 7. Contrato OpenAPI

Con la aplicación en marcha, contrastar `GET /v3/api-docs` con
[contracts/openapi.yaml](contracts/openapi.yaml): rutas, parámetros (`page`, `size`, `sort`,
filtros), códigos de respuesta, esquemas de `PagedModel`, `security: []` en las 8 operaciones y
los textos de los ejemplos. Las divergencias aceptadas están listadas al inicio del contrato.
