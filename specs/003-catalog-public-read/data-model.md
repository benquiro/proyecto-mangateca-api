# Data Model: Catalog (Public Read)

**Feature**: `003-catalog-public-read` | **Fecha**: 2026-11-05

Migración `V3__create_catalog.sql` (la última aplicada es V2; V1 y V2 no se tocan). Hibernate
corre con `ddl-auto=validate`, así que las entidades deben coincidir con este esquema.
Decisiones en [research.md](research.md) (R1–R3, R6, R12, R14, R15). Todos los identificadores
son `UUID` asignados por la aplicación (esta feature no inserta).

## 1. Extensiones y función (V3)

| Objeto | Definición |
|--------|------------|
| `unaccent` | `CREATE EXTENSION IF NOT EXISTS unaccent` (V1 no la crea) |
| `pg_trgm` | `CREATE EXTENSION IF NOT EXISTS pg_trgm` |
| `catalog_normalize(text) → text` | SQL, `IMMUTABLE`, `PARALLEL SAFE`, `STRICT`: `lower(public.unaccent('public.unaccent'::regdictionary, $1))`. Envoltorio indexable (R2) |

## 2. Tablas

### `publishers`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `name` | `VARCHAR(200)` | no | Editorial |

### `genres`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `name` | `VARCHAR(100)` | no | Género |

### `authors`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `name` | `VARCHAR(200)` | no | Nombre; puede repetirse entre autores distintos (homónimos) |

### `series`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `name` | `VARCHAR(200)` | no | Nombre de la serie |
| `description` | `TEXT` | no | Descripción |

### `products`

| Columna | Tipo | Nulo | Regla |
|---------|------|------|-------|
| `id` | `UUID` | no | PK |
| `type` | `VARCHAR(10)` | no | `CHECK (type IN ('MANGA','LIBRO','COMIC'))` |
| `status` | `VARCHAR(12)` | no | `CHECK (status IN ('PUBLISHED','UNPUBLISHED'))` |
| `title` | `VARCHAR(300)` | no | Título |
| `synopsis` | `TEXT` | no | Sinopsis |
| `price` | `NUMERIC(12,0)` | no | `CHECK (price > 0)`; CLP sin decimales |
| `isbn` | `VARCHAR(13)` | sí | `CHECK (isbn ~ '^([0-9]{9}[0-9X]\|[0-9]{13})$')`; `UNIQUE`. El dígito verificador lo valida el VO `Isbn`, no la BD |
| `language` | `VARCHAR(35)` | no | Texto libre de hasta 35 caracteres, pensado como código corto tipo BCP 47 (p. ej. `es`, `ja`). No se valida el formato en la 003 (solo lectura) y no es filtro |
| `page_count` | `INTEGER` | no | `CHECK (page_count > 0)` |
| `publication_date` | `DATE` | no | Fecha de publicación: fecha de calendario ISO-8601 sin zona horaria |
| `cover_url` | `VARCHAR(2048)` | no | URL de la portada |
| `publisher_id` | `UUID` | no | `FK → publishers(id)` |
| `series_id` | `UUID` | sí | `FK → series(id)` |
| `volume_number` | `INTEGER` | sí | `CHECK (volume_number > 0)` |
| `created_at` | `TIMESTAMPTZ` | no | Fecha de incorporación al catálogo («novedades»): un instante (UTC). Solo se usa para ordenar; no se expone en la API |

Restricciones adicionales:

- `CHECK ((series_id IS NULL) = (volume_number IS NULL))`: serie y número van juntos o
  ausentes.
- `UNIQUE (series_id, volume_number)`: número único dentro de la serie (las filas con
  `series_id` nulo no entran en la comparación).
- `UNIQUE (isbn)`: un ISBN por producto; PostgreSQL permite varios `NULL`.

### `product_authors`

| Columna | Tipo | Regla |
|---------|------|-------|
| `product_id` | `UUID` | `FK → products(id) ON DELETE CASCADE` |
| `author_id` | `UUID` | `FK → authors(id)` |
| | | `PRIMARY KEY (product_id, author_id)` |

### `product_genres`

| Columna | Tipo | Regla |
|---------|------|-------|
| `product_id` | `UUID` | `FK → products(id) ON DELETE CASCADE` |
| `genre_id` | `UUID` | `FK → genres(id)` |
| | | `PRIMARY KEY (product_id, genre_id)` |

> «≥ 1 autor y ≥ 1 género» no se puede expresar con un `CHECK` entre tablas; lo garantiza la
> fábrica de `Product` de la feature 004 (R14).

## 3. Índices

| Índice | Tabla / definición | Sirve a |
|--------|--------------------|---------|
| `products_pub_created_idx` | `(created_at, id)` `WHERE status = 'PUBLISHED'` | Orden por novedades (defecto) en ambos sentidos |
| `products_pub_price_idx` | `(price, id)` `WHERE status = 'PUBLISHED'` | Orden y rango de precio |
| `products_pub_pubdate_idx` | `(publication_date, id)` `WHERE status = 'PUBLISHED'` | Orden por fecha de publicación |
| `products_pub_title_idx` | `(title, id)` `WHERE status = 'PUBLISHED'` | Orden por título |
| `products_pub_type_idx` | `(type)` `WHERE status = 'PUBLISHED'` | Filtro por tipo |
| `products_pub_publisher_idx` | `(publisher_id)` `WHERE status = 'PUBLISHED'` | Filtro por editorial y `EXISTS` de editoriales |
| `products_series_volume_uk` | `UNIQUE (series_id, volume_number)` | Volúmenes de una serie por número; `EXISTS` de series |
| `products_isbn_uk` | `UNIQUE (isbn)` | Búsqueda exacta por ISBN |
| `products_title_trgm_idx` | `GIN (catalog_normalize(title) gin_trgm_ops)` | Búsqueda parcial por título |
| `authors_name_trgm_idx` | `GIN (catalog_normalize(name) gin_trgm_ops)` | Búsqueda parcial por autor |
| `product_authors_author_idx` | `(author_id, product_id)` | Filtro por autor; `EXISTS` de autores |
| `product_genres_genre_idx` | `(genre_id, product_id)` | Filtro por género; `EXISTS` de géneros |

El orden usa `id` como desempate en el **mismo sentido** que el criterio principal (R5), de
modo que cada índice `(columna, id)` sirve ambos sentidos con un recorrido hacia delante o
hacia atrás. Los índices GIN solo ayudan con términos de 3 o más caracteres (R1).

## 4. Entidades JPA (`catalog.domain`)

Todas con `@Entity`, `@Getter` de Lombok (sin `@Data`) y constructor `protected` sin fábrica
pública: en 003 solo se leen (R14). Las colecciones son `Set` (nunca `List`) y las
asociaciones `LAZY`.

| Entidad | Tabla | Atributos | Relaciones |
|---------|-------|-----------|------------|
| `Product` | `products` | `id`, `type` (`ProductType`, `@Enumerated(STRING)`), `status` (`PublicationStatus`), `title`, `synopsis`, `price` (`Money`), `isbn` (`@Nullable Isbn`), `language`, `pageCount`, `publicationDate`, `coverUrl`, `volumeNumber` (`@Nullable Integer`), `createdAt` | `@ManyToOne(LAZY) publisher`; `@ManyToOne(LAZY) @Nullable series`; `@ManyToMany authors` (`@JoinTable product_authors`, `@OrderBy("name, id")`); `@ManyToMany genres` (`@JoinTable product_genres`, `@OrderBy("name, id")`) |
| `Author` | `authors` | `id`, `name` | — (sin lado inverso) |
| `Publisher` | `publishers` | `id`, `name` | — |
| `Genre` | `genres` | `id`, `name` | — |
| `Series` | `series` | `id`, `name`, `description` | — |

Las asociaciones son unidireccionales desde `Product`; las consultas de clasificaciones con
productos publicados recorren `Product` mediante `EXISTS`, así que no hacen falta colecciones
inversas.

Convertidores: `MoneyConverter` (`Money` ↔ `BigDecimal`), `IsbnConverter` (`Isbn` ↔ `String`,
`null` ↔ `null`), ambos aplicados con `@Convert` explícito.

## 5. Value Objects

### `Money` (record)

- Componente: `BigDecimal amount`. Constante `CURRENCY = "CLP"`.
- Constructor compacto: `amount` no nulo; `signum() > 0`; se normaliza a escala 0 con
  `setScale(0, RoundingMode.UNNECESSARY)` y un decimal real (`10.5`) lanza
  `IllegalArgumentException`.
- `Money.clp(long)`; `Comparable<Money>`; igualdad por valor.

### `Isbn` (record)

- Componente: `String value` (normalizado).
- Constructor compacto: elimina guiones y espacios, mayúsculas; acepta 10 caracteres (9
  dígitos + dígito o `X`) o 13 dígitos; valida el dígito verificador (mod 11 / mod 10);
  `IllegalArgumentException` en caso contrario.
- `Isbn.normalize(String)`: solo limpieza, sin validar (para comparar texto de búsqueda).

## 6. Enumeraciones

| Enum | Valores |
|------|---------|
| `ProductType` | `MANGA`, `LIBRO`, `COMIC` |
| `PublicationStatus` | `PUBLISHED`, `UNPUBLISHED` |

Transiciones de estado: no las hay en 003 (solo lectura). La administración (004) publica y
despublica.

## 7. Reglas de visibilidad (FR-014, FR-020)

- **Producto visible** = `status = 'PUBLISHED'`.
- **Autor, género, editorial visible** = existe al menos un producto visible que lo
  referencia (`EXISTS`).
- **Serie visible** = existe al menos un producto visible con ese `series_id`.
- Una serie, autor, género o editorial sin productos visibles **no se lista** y, en el caso de
  la serie, su detalle y volúmenes responden 404.

## 8. Consultas del módulo (resumen)

| Caso de uso | Consulta | Sentencias |
|-------------|----------|------------|
| Listado de productos | Paso 1: IDs con `Specification` + orden + `LIMIT/OFFSET`; `count`. Paso 2: `findAllByIdIn` con grafo `authors`, `series` | 3 |
| Volúmenes de serie | Existe serie visible (`EXISTS`); mismo flujo de 3 con `seriesId` y orden `volume_number` | 4 |
| Detalle de producto | Una sentencia con grafo `authors`, `genres`, `publisher`, `series` y `status = PUBLISHED` | 1 |
| Listado de géneros / autores / editoriales / series | `@Query` con `EXISTS`, `ORDER BY name, id`; `count` | 2 |
| Detalle de serie | Una sentencia con `EXISTS` | 1 |
