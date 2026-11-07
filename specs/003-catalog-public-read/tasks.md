---
description: "Task list for 003-catalog-public-read"
---

# Tasks: Catalog (Public Read)

**Input**: Design documents from `/specs/003-catalog-public-read/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml),
[quickstart.md](quickstart.md)

**Constitución**: v4.0.0

## Reglas de esta lista

- **Tests (Principio VI)**: solo los obligatorios (invariantes de `Money` e `Isbn` sin Spring;
  seguridad: `GET` anónimo permitido, `POST`/`PUT`/`DELETE` anónimos denegados) y **un test por
  cada escenario Given-When-Then** de `spec.md`, que la spec pide explícitamente («User Scenarios
  & Testing»). No hay tests de CRUD trivial, mappers, DTO ni getters, ni objetivo de cobertura.
- `ApplicationModules.verify()` **ya existe** (`src/test/java/com/mangateca/api/ModularityTests.java`,
  feature 001) y cubre el módulo `catalog` sin cambios; no se genera tarea. Lo mismo el smoke test
  `ApiApplicationTests`. Ambos corren en cada checkpoint.
- **Test-first**: dentro de cada historia, las tareas `[TEST]` van antes que las de
  implementación (deben fallar o no compilar hasta implementarse).
- **BD real**: búsqueda y filtros combinados con `@DataJpaTest` + Testcontainers
  (`@AutoConfigureTestDatabase(replace = NONE)`, `@Import(TestcontainersConfiguration.class)`,
  `unaccent`/`pg_trgm` reales). Nunca H2.
- **Mocks** solo de la capa de aplicación en `@WebMvcTest`; el servicio mockeado devuelve
  entidades reales construidas con `CatalogFixtures` (T023). Entidades, records y VO nunca se mockean.
- **Datos de prueba** solo en `src/test` (`CatalogTestData`, T022). **Ningún `INSERT` en las
  migraciones.**
- **US6 (Acceso anónimo)** no tiene fase propia, como se pidió (fases US1–US5). Sus escenarios
  se cubren así: la cadena y el test obligatorio de seguridad en Foundational (T020, T021); US6
  Escenario 2 dentro de la fase US1 (T026, etiquetada `[US6]` porque necesita el listado); US6
  Escenario 1 con respuesta real en las ocho rutas en Polish (T062, necesita todos los endpoints).
- **`@WebMvcTest` del catálogo**: mismo patrón de `@Import` que
  `src/test/java/com/mangateca/api/identity/web/AuthControllerTests.java` (`SecurityConfiguration`,
  `IdentitySecurityConfiguration`, `JwtKeyConfiguration`, `ProblemDetailsResponseWriter`,
  `ProblemDetailsAuthenticationEntryPoint`, `ProblemDetailsAccessDeniedHandler`,
  `@ActiveProfiles("test")`) más `CatalogSecurityConfiguration` y `CatalogMapperImpl`. El
  `GlobalExceptionHandler` lo carga el propio slice (`@RestControllerAdvice`).
- **Fuera de alcance** (spec): administración, stock, reseñas, datos de ejemplo, imágenes,
  promociones, caché y rate limiting. Sin dependencias Maven nuevas y sin cambios en
  `application*.yaml`.

## Format: `- [ ] [ID] [P?] [USn?] [Tipo] Descripción — ruta(s) — requisito / escenario`

- **[P]**: no comparte archivos con otra `[P]` de la misma fase y puede ir en paralelo con ellas
  **una vez cumplidas las dependencias que indica** («depende de …» en la tarea y en
  «Dependencies & Execution Order»). Los grupos que pueden arrancar a la vez están en la tabla
  «Parallel Opportunities», separados por «·». **Nunca** en tareas que tocan `pom.xml`,
  `application*.yaml`, `V3__create_catalog.sql` o `CatalogSecurityConfiguration` (el
  `SecurityFilterChain`).
- **[USn]**: historia de `spec.md`.
- **Tipo**: `[TEST]`, `[CODE]`, `[CONFIG]`, `[MIGRATION]` o `[DOC]`.
- Rutas: producción bajo `src/main/java/com/mangateca/api/catalog/`, tests bajo
  `src/test/java/com/mangateca/api/catalog/`.
- Commit por tarea: `feat(catalog): … (T0xx)` (Conventional Commits, sin trailers).

---

## Phase 1: Setup

**Purpose**: lo que el plan exija antes de empezar.

> **Sin tareas.** El plan no añade dependencias ni requiere ADR («Sin dependencias Maven nuevas
> y sin ADR»: `unaccent` y `pg_trgm` son extensiones `contrib` de PostgreSQL). La estructura del
> proyecto ya existe desde la feature 001.

**Checkpoint**: nada que verificar; `./mvnw verify` sigue como en `main`.

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: esquema V3, VO, entidades, repositorios, respuesta paginada, validación de
paginación, lista blanca de orden y cadena de seguridad del catálogo.

**⚠️ CRITICAL**: ninguna historia empieza antes de cerrar esta fase.

### Paquetes del módulo

- [X] T001 [P] [CONFIG] Crear `package-info.java` con `@NullMarked` (JSpecify) en el paquete raíz del módulo, que además lleva `@ApplicationModule(displayName = "Catalog")` (mismo patrón que `identity/package-info.java`), y en `domain` y `application`. El paquete raíz no lleva ninguna clase (sin API pública, R11). — `src/main/java/com/mangateca/api/catalog/package-info.java`, `src/main/java/com/mangateca/api/catalog/domain/package-info.java`, `src/main/java/com/mangateca/api/catalog/application/package-info.java` — Principio I; IV (JSpecify)
- [X] T002 [P] [CONFIG] Crear `package-info.java` con `@NullMarked` en `web` y `config`. — `src/main/java/com/mangateca/api/catalog/web/package-info.java`, `src/main/java/com/mangateca/api/catalog/config/package-info.java` — Principio IV (JSpecify)

### Esquema

- [X] T003 [MIGRATION] Crear `V3__create_catalog.sql` (V1 y V2 intactas), en este orden:
  1. `CREATE EXTENSION IF NOT EXISTS unaccent;` y `CREATE EXTENSION IF NOT EXISTS pg_trgm;`.
  2. Función `catalog_normalize(text) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT`, cuerpo `SELECT lower(public.unaccent('public.unaccent'::regdictionary, $1))` (R2).
  3. Tablas: `publishers (id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL)`; `genres (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL)`; `authors (id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL)`; `series (id UUID PRIMARY KEY, name VARCHAR(200) NOT NULL, description TEXT NOT NULL)`.
  4. `products`: `id UUID PRIMARY KEY`; `type VARCHAR(10) NOT NULL CHECK (type IN ('MANGA','LIBRO','COMIC'))`; `status VARCHAR(12) NOT NULL CHECK (status IN ('PUBLISHED','UNPUBLISHED'))`; `title VARCHAR(300) NOT NULL`; `synopsis TEXT NOT NULL`; `price NUMERIC(12,0) NOT NULL CHECK (price > 0)`; `isbn VARCHAR(13)` nulo con `CHECK (isbn ~ '^([0-9]{9}[0-9X]|[0-9]{13})$')`; `language VARCHAR(35) NOT NULL`; `page_count INTEGER NOT NULL CHECK (page_count > 0)`; `publication_date DATE NOT NULL`; `cover_url VARCHAR(2048) NOT NULL`; `publisher_id UUID NOT NULL REFERENCES publishers(id)`; `series_id UUID REFERENCES series(id)` nulo; `volume_number INTEGER CHECK (volume_number > 0)` nulo; `created_at TIMESTAMPTZ NOT NULL`.
  5. Restricciones de `products`: `CHECK ((series_id IS NULL) = (volume_number IS NULL))`; `CONSTRAINT products_series_volume_uk UNIQUE (series_id, volume_number)`; `CONSTRAINT products_isbn_uk UNIQUE (isbn)`. Una sola restricción con nombre por unicidad (sin `UNIQUE` de columna ni índice duplicado).
  6. `product_authors (product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE, author_id UUID NOT NULL REFERENCES authors(id), PRIMARY KEY (product_id, author_id))` y `product_genres (product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE, genre_id UUID NOT NULL REFERENCES genres(id), PRIMARY KEY (product_id, genre_id))`.
  7. Índices (data-model §3): `products_pub_created_idx (created_at, id)`, `products_pub_price_idx (price, id)`, `products_pub_pubdate_idx (publication_date, id)`, `products_pub_title_idx (title, id)`, `products_pub_type_idx (type)`, `products_pub_publisher_idx (publisher_id)`, todos `WHERE status = 'PUBLISHED'`; `products_title_trgm_idx USING GIN (catalog_normalize(title) gin_trgm_ops)`; `authors_name_trgm_idx USING GIN (catalog_normalize(name) gin_trgm_ops)`; `product_authors_author_idx (author_id, product_id)`; `product_genres_genre_idx (genre_id, product_id)`.
  8. Sin `INSERT`.
  — `src/main/resources/db/migration/V3__create_catalog.sql` — FR-005 (búsqueda indexable), FR-015..FR-018, FR-020; R1, R2, R12
- [X] T004 [TEST] Actualizar `FlywayMigrationIT` para que sus dos aserciones exactas sigan en verde con V3 (ajuste necesario, no un test nuevo): (1) `shouldNotCreateBusinessTables` (línea ~73): la lista de tablas incluye las siete de V3 (`products`, `authors`, `publishers`, `genres`, `series`, `product_authors`, `product_genres`), y su `@DisplayName` se ajusta; (2) `shouldNotCreateExtensions` (línea ~82, hoy `containsExactly("plpgsql")`): pasa a `containsExactlyInAnyOrder("plpgsql", "unaccent", "pg_trgm")`, con método y `@DisplayName` renombrados («solo plpgsql y las extensiones de V3»). — `src/test/java/com/mangateca/api/persistence/FlywayMigrationIT.java` — plan «Impacto en 001 y 002»; depende de T003

### Value Objects (tests obligatorios VI.2, sin Spring ni BD)

- [X] T005 [P] [TEST] `MoneyTests` (JUnit 5 + AssertJ): `0` y negativo → `IllegalArgumentException`; `null` rechazado; `new BigDecimal("1500")` y `new BigDecimal("1.5E3")` quedan con `scale() == 0` e iguales entre sí; `10.5` → `IllegalArgumentException`; `Money.clp(8990)`; `compareTo`; igualdad por valor; `Money.CURRENCY == "CLP"`. — `src/test/java/com/mangateca/api/catalog/domain/MoneyTests.java` — FR-016; Constitución II, VI.2
- [X] T006 [P] [TEST] `IsbnTests`: normaliza guiones y espacios (`978-4-08-882011-8` → `9784088820118`); `x` final de ISBN-10 pasa a `X`; ISBN-10 e ISBN-13 válidos aceptados; dígito verificador incorrecto rechazado en ambos formatos; longitudes 9, 11, 12 y 14 rechazadas; letras en medio y `X` fuera de la última posición rechazadas; `Isbn.normalize(String)` solo limpia y no valida. — `src/test/java/com/mangateca/api/catalog/domain/IsbnTests.java` — FR-017; Constitución II, VI.2
- [X] T007 [P] [CODE] `record Money(BigDecimal amount) implements Comparable<Money>`, constante `CURRENCY = "CLP"`. Constructor compacto: `amount` no nulo; `amount.signum() > 0`; `setScale(0, RoundingMode.UNNECESSARY)` y `ArithmeticException` → `IllegalArgumentException`. Fábrica `Money.clp(long)`. — `src/main/java/com/mangateca/api/catalog/domain/Money.java` — FR-016; R6
- [X] T008 [P] [CODE] `record Isbn(String value)`. Constructor compacto: quita guiones y espacios, mayúsculas; exige 10 caracteres (9 dígitos + dígito o `X`) o 13 dígitos; dígito verificador ISBN-10 (Σ dígito·(10−i) ≡ 0 mod 11) e ISBN-13 (pesos 1/3, ≡ 0 mod 10); si no, `IllegalArgumentException`. Estático `normalize(String)`: solo limpieza. — `src/main/java/com/mangateca/api/catalog/domain/Isbn.java` — FR-017; R6

### Entidades JPA (`@Getter` de Lombok, sin `@Data`; constructor `protected`; sin fábrica pública, R14)

- [X] T009 [P] [CODE] Enums `ProductType` (`MANGA`, `LIBRO`, `COMIC`) y `PublicationStatus` (`PUBLISHED`, `UNPUBLISHED`). — `src/main/java/com/mangateca/api/catalog/domain/ProductType.java`, `src/main/java/com/mangateca/api/catalog/domain/PublicationStatus.java` — FR-015, FR-020
- [X] T010 [P] [CODE] Entidades `Author` (`authors`, `name` `length = 200`), `Publisher` (`publishers`, `name` `length = 200`) y `Genre` (`genres`, `name` `length = 100`): `@Id UUID id`, `name` `@Column(nullable = false)`; sin colecciones inversas. — `src/main/java/com/mangateca/api/catalog/domain/Author.java`, `src/main/java/com/mangateca/api/catalog/domain/Publisher.java`, `src/main/java/com/mangateca/api/catalog/domain/Genre.java` — data-model §4
- [X] T011 [P] [CODE] Entidad `Series` (`series`): `@Id UUID id`, `name` `@Column(nullable = false, length = 200)`, `description` `@Column(nullable = false, columnDefinition = "text")`; sin colección de volúmenes. — `src/main/java/com/mangateca/api/catalog/domain/Series.java` — FR-011; data-model §4
- [X] T012 [CODE] `MoneyConverter` (`AttributeConverter<Money, BigDecimal>`) e `IsbnConverter` (`AttributeConverter<Isbn, String>`, `null` ↔ `null`), ambos `@Converter` **sin** `autoApply`. — `src/main/java/com/mangateca/api/catalog/domain/MoneyConverter.java`, `src/main/java/com/mangateca/api/catalog/domain/IsbnConverter.java` — R6; depende de T007, T008
- [X] T013 [CODE] Entidad `Product` (`products`): `id`; `type` y `status` con `@Enumerated(EnumType.STRING)`; `title`; `synopsis`; `price` `Money` con `@Convert(converter = MoneyConverter.class)` y `@Column(precision = 12, scale = 0)`; `@Nullable Isbn isbn` con `@Convert(converter = IsbnConverter.class)`; `language`; `pageCount`; `LocalDate publicationDate`; `coverUrl`; `@Nullable Integer volumeNumber`; `Instant createdAt`; `@ManyToOne(fetch = LAZY, optional = false) publisher`; `@ManyToOne(fetch = LAZY) @Nullable series`; `@ManyToMany Set<Author> authors` (`@JoinTable product_authors`) y `Set<Genre> genres` (`@JoinTable product_genres`), ambas `@OrderBy("name")`. Colecciones `Set`, nunca `null`. — `src/main/java/com/mangateca/api/catalog/domain/Product.java` — FR-015..FR-018, FR-020; data-model §4; depende de T009–T012

### Repositorios

- [X] T014 [CODE] `ProductId` (proyección de interfaz, `UUID getId()`) y `ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product>` con `@EntityGraph(attributePaths = {"authors", "series"}) List<Product> findAllByIdIn(Collection<UUID> ids)` (paso 2 del listado). — `src/main/java/com/mangateca/api/catalog/domain/ProductId.java`, `src/main/java/com/mangateca/api/catalog/domain/ProductRepository.java` — Constitución IV (sin N+1); R3; depende de T013
- [X] T015 [P] [CODE] `AuthorRepository`, `GenreRepository` y `PublisherRepository` (`JpaRepository<…, UUID>`, sin métodos todavía; US5 añade `findVisible`). — `src/main/java/com/mangateca/api/catalog/domain/AuthorRepository.java`, `src/main/java/com/mangateca/api/catalog/domain/GenreRepository.java`, `src/main/java/com/mangateca/api/catalog/domain/PublisherRepository.java` — data-model §8; depende de T010
- [X] T016 [P] [CODE] `SeriesRepository` (`JpaRepository<Series, UUID>`, sin métodos todavía; US4/US5 añaden las consultas visibles). — `src/main/java/com/mangateca/api/catalog/domain/SeriesRepository.java` — data-model §8; depende de T011

### Contrato de paginación y orden (web)

- [X] T017 [P] [CODE] Record de respuesta paginada `PageResponse<T>(List<T> content, PageMetadata page)` con record anidado `PageMetadata(int size, int number, long totalElements, int totalPages)` y fábrica `static <T> PageResponse<T> from(Page<T> page)`. JSON idéntico al contrato (`content` + `page.size/number/totalElements/totalPages`); `PageImpl` nunca se serializa. Usado por los seis listados. Sustituye a `PagedModel` (Principio III: DTO como `record`); trade-off en `plan.md` y R4. — `src/main/java/com/mangateca/api/catalog/web/PageResponse.java` — FR-001, FR-004, FR-011, FR-013; contrato `components.schemas.*Page`
- [X] T018 [P] [CODE] `PageParams` (record `@ParameterObject`, `Integer page`, `Integer size`): constructor compacto con defecto `0` y `20` si llegan `null`; constantes `MAX_SIZE = 100` y `MAX_PAGE = 1_000_000`; `@Min(0) @Max(MAX_PAGE)` en `page`, `@Min(1) @Max(MAX_SIZE)` en `size` (mensajes en castellano); `PageRequest toPageRequest(Sort sort)`. Ningún controlador recibe `Pageable`; un `size > 100` se **rechaza** con 400, no se recorta. — `src/main/java/com/mangateca/api/catalog/web/PageParams.java` — FR-001, FR-004, FR-008, FR-011, FR-013; R4, R13
- [X] T019 [CODE] Lista blanca de orden en `ProductSearchRequest` (record `@ParameterObject`; en esta fase solo `page`, `size` y `sort`): `page`/`size` con las mismas anotaciones que `PageParams` usando `@Max(PageParams.MAX_PAGE)` / `@Max(PageParams.MAX_SIZE)`; `sort` `String` con `@Pattern(regexp = "^(title|price|publicationDate|createdAt)(,(asc|desc))?$")`, defecto `createdAt,desc`, sin sentido ⇒ `asc`; `Pageable toPageable()` traduce la clave de API a la propiedad de la entidad con un `Map` explícito y añade `id` como desempate **en el mismo sentido** que el criterio. US2 añade `q` y filtros. — `src/main/java/com/mangateca/api/catalog/web/ProductSearchRequest.java` — FR-002, FR-008; R5; depende de T018

### Seguridad (test obligatorio VI.3, test-first)

- [X] T020 [P] [TEST] `CatalogSecurityIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestcontainersConfiguration` + `TestSecurityConfiguration`, cliente como en `SecurityIT`): (a) `GET` y `HEAD` anónimos a las ocho rutas (`/api/v1/products`, `/api/v1/products/{uuid}`, `/api/v1/series`, `/api/v1/series/{uuid}`, `/api/v1/series/{uuid}/volumes`, `/api/v1/genres`, `/api/v1/authors`, `/api/v1/publishers`) **no** responden 401 ni 403 (sin controladores aún responden 404; en T062 se exige 200); (b) `POST`, `PUT` y `DELETE` anónimos a `/api/v1/products`, `/api/v1/products/{uuid}`, `/api/v1/series`, `/api/v1/genres`, `/api/v1/authors` y `/api/v1/publishers` → 401 `application/problem+json`; (c) `GET` con `Authorization: Bearer basura` a `/api/v1/products` no responde 401; (d) rutas fuera de la lista blanca (`GET /api/v1/products/{uuid}/extra`, `GET /api/v1/orders`) → 401 anónimo. — `src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java` — FR-021; US6 Escenario 1 (acceso); SC-004; Constitución V, VI.3
- [X] T021 [CONFIG] `CatalogSecurityConfiguration`: `SecurityFilterChain` `@Order(2)` con `securityMatcher` = `OrRequestMatcher` de `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET | HEAD | OPTIONS, ruta)` sobre las **rutas exactas** `/api/v1/products`, `/api/v1/products/*`, `/api/v1/series`, `/api/v1/series/*`, `/api/v1/series/*/volumes`, `/api/v1/genres`, `/api/v1/authors`, `/api/v1/publishers`; `authorizeHttpRequests(anyRequest().permitAll())`, CSRF deshabilitado, sesión `STATELESS`, `cors(withDefaults())`, **sin** `oauth2ResourceServer` (el token se ignora). Mismo patrón que `IdentitySecurityConfiguration`; la cadena de `platform` no cambia y sigue deny-by-default para el resto de métodos y rutas. — `src/main/java/com/mangateca/api/catalog/config/CatalogSecurityConfiguration.java` — FR-021; Constitución V; R8; depende de T002, T020

### Soporte de pruebas (solo `src/test`)

- [X] T022 [P] [TEST] `CatalogTestData`: builder con `JdbcClient` que inserta editoriales, géneros, autores, series y productos (autores y géneros, estado, `created_at`, precio, ISBN, serie y volumen opcionales) y devuelve los UUID generados; `clear()` borra en orden de FK (`product_authors`, `product_genres`, `products`, `series`, `authors`, `genres`, `publishers`). Cada test que lo use llama a `clear()` al empezar. — `src/test/java/com/mangateca/api/catalog/support/CatalogTestData.java` — R10; depende de T003
- [X] T023 [P] [TEST] `CatalogFixtures`: fábricas estáticas que construyen `Product`, `Author`, `Publisher`, `Genre` y `Series` en memoria para `@WebMvcTest` (`BeanUtils.instantiateClass` sobre el constructor `protected` + `ReflectionTestUtils.setField`), con `Money` e `Isbn` reales y variantes: con/sin serie y volumen, ISBN nulo, varios autores y géneros. Sin constructores ni setters nuevos en producción. — `src/test/java/com/mangateca/api/catalog/support/CatalogFixtures.java` — R10; Constitución VI (no mockear entidades); depende de T013

**Checkpoint**: `./mvnw verify` en verde y sin advertencias: `ModularityTests` (incluye `catalog`),
`ApiApplicationTests` y `SchemaDriftIT` con V3 y `ddl-auto=validate`, `FlywayMigrationIT`
actualizado, `MoneyTests`, `IsbnTests` y `CatalogSecurityIT` (T020).

---

## Phase 3: User Story 1 - Explorar el catálogo (Priority: P1) 🎯 MVP

**Goal**: listado público paginado y ordenado, con la tarjeta de producto y sin N+1.

**Independent Test**: con productos publicados y no publicados, consultar `GET /api/v1/products`
sin credenciales y comprobar página, tamaño, orden y contenido de cada tarjeta.

### Tests for User Story 1

- [X] T024 [P] [US1] [TEST] `ProductQueryServiceIT` (`@DataJpaTest` + Testcontainers, `@Import({TestcontainersConfiguration.class, ProductQueryService.class})`, datos con `CatalogTestData`): **Escenario 1**: 25 publicados + 3 no publicados, página 0 sin parámetros → 20 publicados ordenados por `createdAt` descendente, `totalElements = 25`, `totalPages = 2`, y el número de sentencias SQL (estadísticas de Hibernate) es el mismo para una página de 1 y de 20 productos con autores y serie (Constitución IV; el plan apoya en él su Constitution Check); **Escenario 3**: 45 publicados, página 50 tamaño 20 → contenido vacío, `totalElements = 45`, sin excepción; **Escenario 4** (`@ParameterizedTest`): `title` asc, `price` desc, `publicationDate` desc y `createdAt` asc producen el orden esperado (títulos ASCII), con empates resueltos por `id` de forma repetible. — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US1 Escenarios 1, 3, 4; FR-001, FR-002, FR-004, FR-020
- [X] T025 [P] [US1] [TEST] `ProductControllerTests` (`@WebMvcTest(ProductController.class)` + `MockMvcTester`; `@Import` según el patrón de «Reglas de esta lista»; `@MockitoBean ProductQueryService` con entidades de `CatalogFixtures`): **Escenario 1** (web): 200 con forma `PageResponse` (`content`, `page.size/number/totalElements/totalPages`); **Escenario 2**: `size=101` → 400 `application/problem+json` con `errors[field=size]`; **Escenario 4** (web): el `Pageable` capturado con `ArgumentCaptor` es `createdAt DESC, id DESC` sin `sort`, `title ASC, id ASC` con `sort=title` y `price DESC, id DESC` con `sort=price,desc`; **Escenario 5**: producto de la serie «Dragon Ball», volumen 3 → tarjeta con `id`, `title`, `type`, `authors`, `price` entero, `coverUrl`, `series.name = "Dragon Ball"` y `volumeNumber = 3`; **Escenario 6**: producto sin serie → `series` y `volumeNumber` nulos. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US1 Escenarios 1, 2, 4, 5, 6; FR-001..FR-003, FR-008
- [X] T026 [P] [US6] [TEST] Ampliar `CatalogSecurityIT` con **US6 Escenario 2**: con datos de `CatalogTestData`, un cliente (`ROLE_CUSTOMER`) y un administrador (`ROLE_ADMIN`) con token válido reciben en `GET /api/v1/products` exactamente el mismo cuerpo que un invitado. — `src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java` — US6 Escenario 2; FR-021; SC-004

### Implementation for User Story 1

- [X] T027 [P] [US1] [CODE] `ProductSearchCriteria` (record, todos `@Nullable`: `String text`, `ProductType type`, `UUID genreId`, `UUID authorId`, `UUID publisherId`, `UUID seriesId`, `Money minPrice`, `Money maxPrice`; constante `NONE`) y `ProductSpecifications` (clase `final`, métodos estáticos): en esta tarea `published()` (`status = PUBLISHED`) y `from(ProductSearchCriteria)`, que parte de `Specification.unrestricted()` y compone con `.and(...)`. — `src/main/java/com/mangateca/api/catalog/domain/ProductSearchCriteria.java`, `src/main/java/com/mangateca/api/catalog/domain/ProductSpecifications.java` — FR-020; R3
- [X] T028 [P] [US1] [CODE] DTO records `NamedResponse(UUID id, String name)` y `ProductSummaryResponse(UUID id, String title, ProductType type, List<NamedResponse> authors, long price, String coverUrl, @Nullable NamedResponse series, @Nullable Integer volumeNumber)`. — `src/main/java/com/mangateca/api/catalog/web/NamedResponse.java`, `src/main/java/com/mangateca/api/catalog/web/ProductSummaryResponse.java` — FR-003; contrato `ProductSummaryResponse`
- [X] T029 [US1] [CODE] `CatalogMapper` (MapStruct, `@Mapper(componentModel = "spring")`): `ProductSummaryResponse toSummary(Product)`, `NamedResponse toNamed(Author)`, `NamedResponse toNamed(Series)` y método por defecto `Money → long`. No toca `publisher` ni `genres`. — `src/main/java/com/mangateca/api/catalog/web/CatalogMapper.java` — FR-003; depende de T028
- [X] T030 [US1] [CODE] `ProductQueryService` (`@Service`, `@Transactional(readOnly = true)`): `Page<Product> search(ProductSearchCriteria, Pageable)` en dos pasos: (1) `productRepository.findBy(ProductSpecifications.from(criteria), q -> q.as(ProductId.class).page(pageable))` —si el SQL carga la entidad completa, `q.project("id")`—; (2) si hay IDs, `findAllByIdIn(ids)` reordenado según la página de IDs y `new PageImpl<>(ordenados, pageable, total)`; sin IDs, sin segunda consulta. — `src/main/java/com/mangateca/api/catalog/application/ProductQueryService.java` — FR-001, FR-002, FR-004; R3; depende de T014, T027
- [X] T031 [US1] [CODE] `ProductController` (`@RestController`, `@RequestMapping("/api/v1/products")`, `@SecurityRequirements` vacío, `@Tag`/`@Operation` según el contrato): `GET` con `@Valid @ModelAttribute ProductSearchRequest` → `PageResponse.from(service.search(ProductSearchCriteria.NONE, request.toPageable()).map(mapper::toSummary))`. — `src/main/java/com/mangateca/api/catalog/web/ProductController.java` — FR-001..FR-004, FR-021; contrato `listProducts`; depende de T017, T019, T029, T030

**Checkpoint**: `./mvnw verify` en verde con T024, T025 y T026; `GET /api/v1/products` responde
200 sin credenciales.

---

## Phase 4: User Story 2 - Buscar y filtrar (Priority: P1)

**Goal**: búsqueda por título, autor o ISBN (sin mayúsculas ni tildes, parcial, todas las
palabras) y filtros combinables con orden y paginación.

**Independent Test**: con títulos con tilde, autores e ISBN conocidos, cada búsqueda y
combinación de filtros devuelve exactamente los productos esperados.

### Tests for User Story 2

- [X] T032 [P] [US2] [TEST] `ProductSearchIT` (`@DataJpaTest` + Testcontainers; `ProductRepository.findAll(ProductSpecifications.from(criteria), pageable)`; datos con `CatalogTestData`): **Escenario 1**: «Dragón» se encuentra con `dragon`, `DRAGON` y `drag` (parcial, FR-005); **Escenario 2**: autor «Akira Toriyama» con `toriyama`; varias palabras: `dragon toriyama` y `toriyama dragon` encuentran el producto y uno que solo tiene una de las dos palabras no aparece (FR-005); **Escenario 3**: el ISBN con guiones y sin guiones devuelve **exactamente** ese producto; **Escenario 4**: tipo `MANGA` + un género + rango de precio (extremos inclusivos) → solo productos que cumplen las tres condiciones; **Escenario 5**: búsqueda + filtros + `price desc` + página 1 → la segunda página del conjunto correcto en ese orden; **Escenario 7**: texto sin coincidencias → página vacía con `totalElements = 0`. En todos los casos un producto no publicado nunca aparece (FR-020) y un producto con dos autores que coinciden aparece una sola vez, también en `totalElements` (FR-007). — `src/test/java/com/mangateca/api/catalog/domain/ProductSearchIT.java` — US2 Escenarios 1, 2, 3, 4, 5, 7; FR-005..FR-007, FR-020
- [X] T033 [P] [US2] [TEST] Ampliar `ProductControllerTests` con **Escenario 6** (`@ParameterizedTest`): `minPrice=9000&maxPrice=100` → 400 con `errors[field=minPrice]`; `type=XYZ` → 400 con `errors[field=type]` y sin el valor recibido en el cuerpo; `sort=synopsis` → 400 con `errors[field=sort]`; y, como filas extra del mismo parametrizado (paginación inválida e identificador mal formado, FR-008 y Edge Cases): `size=0` → `errors[field=size]`, `page=-1` → `errors[field=page]`, `genreId=abc` → `errors[field=genreId]` sin el valor recibido. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US2 Escenario 6; Edge Cases (paginación inválida, ID mal formado); FR-008, FR-022; SC-006

### Implementation for User Story 2

- [X] T034 [US2] [CODE] Completar `ProductSpecifications` (sin `join` ni `distinct`): `hasType`, `hasPublisher`, `hasSeries` y `priceBetween(min, max)` (comparación directa, inclusiva, extremos opcionales); `hasAuthor` y `hasGenre` con subconsulta `EXISTS`; `matchesText(String)`: `trim`, división por espacios, sin vacíos; por palabra, `catalog_normalize(title) LIKE catalog_normalize('%palabra%') ESCAPE '\'` **o** `EXISTS` de autor con `catalog_normalize(name) LIKE …`, palabras unidas con `AND` (escapar `\`, `%`, `_`); con `OR`, `isbn = Isbn.normalize(texto)` solo si el texto normalizado cumple `^([0-9]{9}[0-9X]|[0-9]{13})$`. Usar `cb.function("catalog_normalize", String.class, …)`. Texto nulo o en blanco ⇒ `Specification.unrestricted()`. `from(...)` compone todos con `published()`. — `src/main/java/com/mangateca/api/catalog/domain/ProductSpecifications.java` — FR-005..FR-007; R1, R3; depende de T032
- [X] T035 [P] [US2] [CODE] `@ValidPriceRange` (anotación de clase) y `ValidPriceRangeValidator`: si ambos existen y `minPrice > maxPrice`, registra la violación en el nodo `minPrice` (`buildConstraintViolationWithTemplate(...).addPropertyNode("minPrice")`), mensaje en castellano. Mismo patrón que `@ValidPassword`. — `src/main/java/com/mangateca/api/catalog/web/ValidPriceRange.java`, `src/main/java/com/mangateca/api/catalog/web/ValidPriceRangeValidator.java` — FR-008
- [X] T036 [US2] [CODE] Completar `ProductSearchRequest` con `@Size(max = 100) String q`, `ProductType type`, `UUID genreId`, `authorId`, `publisherId`, `seriesId`, `@Positive Long minPrice`, `@Positive Long maxPrice`, anotado `@ValidPriceRange`, y `ProductSearchCriteria toCriteria()` (`q` recortado, en blanco ⇒ `null`; precios ⇒ `Money.clp`); `ProductController` pasa `request.toCriteria()` en lugar de `ProductSearchCriteria.NONE`. — `src/main/java/com/mangateca/api/catalog/web/ProductSearchRequest.java`, `src/main/java/com/mangateca/api/catalog/web/ProductController.java` — FR-006..FR-008; depende de T034, T035
- [X] T037 [US2] [CODE] En `GlobalExceptionHandler.handleMethodArgumentNotValid`, si `FieldError.isBindingFailure()` (p. ej. `type=XYZ`, `genreId=abc`) el mensaje es el texto fijo «Formato de valor inválido.» en lugar de `getDefaultMessage()` (no se expone el valor ni el tipo Java). Cambio aditivo; el resto de caminos no cambia. — `src/main/java/com/mangateca/api/platform/web/GlobalExceptionHandler.java` — FR-008, FR-022; plan «Impacto en 001 y 002»

**Checkpoint**: `./mvnw verify` en verde con T032 y T033 (además de los anteriores).

---

## Phase 5: User Story 3 - Ver el detalle de un producto (Priority: P1)

**Goal**: detalle completo de un producto publicado; 404 indistinguible para no publicado e
inexistente; 400 para un identificador mal formado.

**Independent Test**: consultar el detalle de un producto publicado, de uno no publicado y de uno
inexistente, y comparar las respuestas.

### Tests for User Story 3

- [X] T038 [P] [US3] [TEST] Ampliar `ProductQueryServiceIT`: **Escenario 1**: `detail(id)` de un producto publicado con 2 autores, 2 géneros, editorial y serie devuelve todos sus datos en una sola sentencia SQL; **Escenario 2**: un producto `UNPUBLISHED` y un UUID inexistente lanzan la misma `CatalogNotFoundException` (mismo tipo y estado 404). — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US3 Escenarios 1, 2; FR-009, FR-010
- [X] T039 [P] [US3] [TEST] Ampliar `ProductControllerTests` para `GET /api/v1/products/{id}`: **Escenario 1**: 200 con `title`, `type`, `synopsis`, `authors`, `publisher`, `genres`, `isbn`, `language`, `pageCount`, `publicationDate`, `price`, `coverUrl` y, si es volumen, `series` y `volumeNumber`; **Escenario 2**: con el servicio lanzando `CatalogNotFoundException` para dos UUID distintos, ambas respuestas son 404 `problem+json` con cuerpos idénticos salvo `instance`; **Escenario 3**: `/api/v1/products/no-es-un-uuid` → 400 con `errors[field=id]`, sin el valor recibido y no 500. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US3 Escenarios 1, 2, 3; FR-008..FR-010, FR-022; SC-005

### Implementation for User Story 3

- [X] T040 [P] [US3] [CODE] `CatalogNotFoundException extends ErrorResponseException` (404, `type` `about:blank`, sin datos del recurso en el mensaje): única excepción para producto inexistente, producto no publicado, serie inexistente y serie sin volúmenes publicados. — `src/main/java/com/mangateca/api/catalog/application/CatalogNotFoundException.java` — FR-010; SC-005; R9
- [X] T041 [US3] [CODE] `ProductRepository`: `@EntityGraph(attributePaths = {"authors", "genres", "publisher", "series"}) Optional<Product> findWithDetailsByIdAndStatus(UUID id, PublicationStatus status)`; `ProductQueryService.detail(UUID id)`: esa consulta con `PUBLISHED` y `orElseThrow(CatalogNotFoundException::new)` (una sola ruta para ambos casos). — `src/main/java/com/mangateca/api/catalog/domain/ProductRepository.java`, `src/main/java/com/mangateca/api/catalog/application/ProductQueryService.java` — FR-009, FR-010, FR-020; depende de T040
- [X] T042 [P] [US3] [CODE] `ProductDetailResponse(UUID id, String title, ProductType type, String synopsis, List<NamedResponse> authors, NamedResponse publisher, List<NamedResponse> genres, @Nullable String isbn, String language, int pageCount, LocalDate publicationDate, long price, String coverUrl, @Nullable NamedResponse series, @Nullable Integer volumeNumber)` y `toDetail(Product)` en `CatalogMapper` (con `toNamed(Publisher)`, `toNamed(Genre)` e `Isbn → String`). — `src/main/java/com/mangateca/api/catalog/web/ProductDetailResponse.java`, `src/main/java/com/mangateca/api/catalog/web/CatalogMapper.java` — FR-009, FR-012; contrato `ProductDetailResponse`
- [X] T043 [US3] [CODE] En `GlobalExceptionHandler`, override de `handleTypeMismatch`: `MethodArgumentTypeMismatchException` → 400 `ProblemDetail` con `errors: [{field: ex.getName(), message: "Formato de valor inválido."}]`, sin el valor recibido. Cambio aditivo. — `src/main/java/com/mangateca/api/platform/web/GlobalExceptionHandler.java` — FR-008, FR-022; US3 Escenario 3
- [X] T044 [US3] [CODE] `GET /api/v1/products/{id}` en `ProductController` (`@PathVariable UUID id`, `@Operation` con respuestas 200/400/404). — `src/main/java/com/mangateca/api/catalog/web/ProductController.java` — FR-009, FR-010; contrato `getProduct`; depende de T041, T042, T043

**Checkpoint**: `./mvnw verify` en verde con T038 y T039.

---

## Phase 6: User Story 4 - Navegar por series (Priority: P2)

**Goal**: datos de la serie (`/series/{id}`) y sus volúmenes publicados paginados por número de
volumen (`/series/{id}/volumes`); 404 si no existe o no tiene volúmenes publicados.

**Independent Test**: serie con volúmenes 1, 2 y 3, con el 2 no publicado: la serie muestra
nombre y descripción, y sus volúmenes son solo 1 y 3, en ese orden.

### Tests for User Story 4

- [X] T045 [P] [US4] [TEST] Ampliar `ProductQueryServiceIT`: **Escenario 1**: `volumes(seriesId, pageable)` de una serie con volúmenes 1, 2 (no publicado) y 3 → solo 1 y 3, en ese orden, `totalElements = 2`; **Escenario 3**: serie inexistente y serie sin volúmenes publicados → `CatalogNotFoundException`. — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US4 Escenarios 1, 3; FR-011, FR-020
- [X] T046 [P] [US4] [TEST] `ClassificationQueryServiceIT` (`@DataJpaTest` + Testcontainers, `@Import(ClassificationQueryService.class)`): **Escenario 1**: `seriesDetail(id)` devuelve nombre y descripción; **Escenario 3**: serie inexistente y serie sin volúmenes publicados → `CatalogNotFoundException`. — `src/test/java/com/mangateca/api/catalog/application/ClassificationQueryServiceIT.java` — US4 Escenarios 1, 3; FR-011, FR-014
- [X] T047 [P] [US4] [TEST] `SeriesControllerTests` (`@WebMvcTest(SeriesController.class)`, mismo `@Import` que T025, servicios mockeados): **Escenario 1**: `GET /api/v1/series/{id}` → 200 con `id`, `name`, `description`; `GET /api/v1/series/{id}/volumes` → 200 `PageResponse` de tarjetas en el orden del servicio; **Escenario 3**: `CatalogNotFoundException` → 404 idéntico en detalle y volúmenes. — `src/test/java/com/mangateca/api/catalog/web/SeriesControllerTests.java` — US4 Escenarios 1, 3; FR-011
- [X] T048 [P] [US4] [TEST] Ampliar `ProductControllerTests` con **Escenario 2**: el detalle del volumen 3 de una serie trae `series.id` (el que acepta `GET /api/v1/series/{id}`), `series.name` y `volumeNumber = 3`. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US4 Escenario 2; FR-012

### Implementation for User Story 4

- [X] T049 [US4] [CODE] `SeriesRepository`: `@Query` `Optional<Series> findVisibleById(UUID id)` con `EXISTS (select 1 from Product p where p.series = s and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)`. — `src/main/java/com/mangateca/api/catalog/domain/SeriesRepository.java` — FR-011, FR-014; data-model §7
- [X] T050 [US4] [CODE] `ProductQueryService.volumes(UUID seriesId, Pageable)`: si `seriesRepository.findVisibleById` está vacío → `CatalogNotFoundException`; si no, reutiliza el flujo en dos pasos con criterio `seriesId` y orden fijo `volumeNumber ASC`. — `src/main/java/com/mangateca/api/catalog/application/ProductQueryService.java` — FR-011; depende de T049
- [X] T051 [US4] [CODE] `ClassificationQueryService` (`@Service`, `@Transactional(readOnly = true)`) con `Series seriesDetail(UUID id)` (`findVisibleById(...).orElseThrow(CatalogNotFoundException::new)`). — `src/main/java/com/mangateca/api/catalog/application/ClassificationQueryService.java` — FR-011, FR-014; depende de T049
- [X] T052 [P] [US4] [CODE] `SeriesResponse(UUID id, String name, String description)` y `toSeries(Series)` en `CatalogMapper`. — `src/main/java/com/mangateca/api/catalog/web/SeriesResponse.java`, `src/main/java/com/mangateca/api/catalog/web/CatalogMapper.java` — FR-011; contrato `SeriesResponse`
- [X] T053 [US4] [CODE] `SeriesController` (`@RequestMapping("/api/v1/series")`, `@SecurityRequirements` vacío): `GET /{id}` y `GET /{id}/volumes` (`@Valid @ModelAttribute PageParams`, `PageResponse<ProductSummaryResponse>`). — `src/main/java/com/mangateca/api/catalog/web/SeriesController.java` — FR-011, FR-012; contrato `getSeries`, `listSeriesVolumes`; depende de T050, T051, T052

**Checkpoint**: `./mvnw verify` en verde con T045–T048.

---

## Phase 7: User Story 5 - Clasificaciones para filtros (Priority: P2)

**Goal**: listados paginados y alfabéticos de géneros, autores, editoriales y series que tengan al
menos un producto publicado.

**Independent Test**: con clasificaciones con y sin productos publicados, cada listado devuelve
solo las visibles, en orden alfabético y con metadatos de paginación.

### Tests for User Story 5

- [X] T054 [P] [US5] [TEST] `ClassificationRepositoryIT` (`@DataJpaTest` + Testcontainers, datos con `CatalogTestData`): **Escenario 1**: un género con productos publicados aparece y otro sin ninguno no; **Escenario 3**: un autor cuyos únicos productos están `UNPUBLISHED` no aparece; **Escenario 2** (`@ParameterizedTest` sobre géneros, autores, editoriales y series): `findVisible(PageRequest.of(0, 2, Sort.by("name", "id")))` devuelve la primera página en orden alfabético con `totalElements` correcto y cada clasificación una sola vez. — `src/test/java/com/mangateca/api/catalog/domain/ClassificationRepositoryIT.java` — US5 Escenarios 1, 2, 3; FR-013, FR-014
- [X] T055 [P] [US5] [TEST] `ClassificationControllerTests` (`@WebMvcTest(ClassificationController.class)`): **Escenario 2** (web) para `GET /api/v1/genres`, `/api/v1/authors` y `/api/v1/publishers`: 200 con `PageResponse` de `{id, name}` y metadatos de paginación. — `src/test/java/com/mangateca/api/catalog/web/ClassificationControllerTests.java` — US5 Escenario 2; FR-013
- [X] T056 [P] [US5] [TEST] Ampliar `SeriesControllerTests` con **Escenario 2** (web) para `GET /api/v1/series`: 200 con `PageResponse` de series y metadatos. — `src/test/java/com/mangateca/api/catalog/web/SeriesControllerTests.java` — US5 Escenario 2; FR-013

### Implementation for User Story 5

- [X] T057 [US5] [CODE] `Page<…> findVisible(Pageable)` con `@Query` `EXISTS` sobre `Product p` publicado (`member of p.genres`, `member of p.authors`, `p.publisher = x`) y `countQuery` explícito. — `src/main/java/com/mangateca/api/catalog/domain/GenreRepository.java`, `src/main/java/com/mangateca/api/catalog/domain/AuthorRepository.java`, `src/main/java/com/mangateca/api/catalog/domain/PublisherRepository.java` — FR-013, FR-014; depende de T054
- [X] T058 [US5] [CODE] `SeriesRepository.findVisible(Pageable)` con `@Query` `EXISTS` (mismo predicado que `findVisibleById`) y `countQuery` explícito. — `src/main/java/com/mangateca/api/catalog/domain/SeriesRepository.java` — FR-013, FR-014; depende de T054
- [X] T059 [US5] [CODE] En `ClassificationQueryService`: `listGenres`, `listAuthors`, `listPublishers` y `listSeries(Pageable)` con orden fijo `name ASC, id ASC` (recibe `Pageable`, no `PageParams`: la capa de aplicación no depende de `web`; el controlador convierte con `PageParams.toPageRequest`). — `src/main/java/com/mangateca/api/catalog/application/ClassificationQueryService.java` — FR-013, FR-014; depende de T057, T058
- [X] T060 [US5] [CODE] `ClassificationController` (`@RequestMapping("/api/v1")`, `@SecurityRequirements` vacío): `GET /genres`, `/authors`, `/publishers` con `@Valid @ModelAttribute PageParams` → `PageResponse<NamedResponse>` vía `CatalogMapper.toNamed`. — `src/main/java/com/mangateca/api/catalog/web/ClassificationController.java` — FR-013; contrato `listGenres`, `listAuthors`, `listPublishers`; depende de T059
- [X] T061 [US5] [CODE] `GET /api/v1/series` en `SeriesController` (`@Valid @ModelAttribute PageParams` → `PageResponse<SeriesResponse>`). — `src/main/java/com/mangateca/api/catalog/web/SeriesController.java` — FR-013; contrato `listSeries`; depende de T059

**Checkpoint**: `./mvnw verify` en verde con T054–T056.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T062 [TEST] Completar `CatalogSecurityIT` (**US6 Escenario 1**): con datos de `CatalogTestData`, `GET` anónimo responde 200 con contenido en las ocho rutas (sustituye la aserción «no 401/403» de T020) y `HEAD` anónimo a `/api/v1/products` responde 200 sin cuerpo; los casos de escritura de T020 siguen en 401. — `src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java` — US6 Escenario 1; FR-021; SC-004
- [ ] T063 [DOC] Contrastar `GET /v3/api-docs` (perfil `dev`) con el contrato: ocho rutas, `page`/`size`/`sort`/filtros, 200/400/404, forma `content` + `page`, `security: []` en las ocho operaciones; anotar en el encabezado del YAML la fecha de verificación y las divergencias aceptadas. — `specs/003-catalog-public-read/contracts/openapi.yaml` — Constitución DoD (OpenAPI)
  - Pendiente anotado (revisión Fase 6): el 200 de `listProducts` y `listSeriesVolumes` no declara el schema de `PageResponse` en `@ApiResponse`; comprobar qué infiere springdoc y, si no coincide con `ProductSummaryPage`, declararlo en ambos a la vez.
- [ ] T064 [DOC] Ejecutar a mano `quickstart.md` §2–§5 y §7 con datos insertados por `psql` (no versionados) y §6 (SC-002: 10.000 productos sintéticos, `EXPLAIN (ANALYZE, BUFFERS)`, p95 < 500 ms); marcar «Verificado a mano <fecha> (T064)». Si un plan no usa el índice esperado, la corrección va en una migración nueva (V4), nunca en V3. — `specs/003-catalog-public-read/quickstart.md` — SC-001, SC-002, SC-004..SC-006
- [ ] T065 [DOC] Sección «Catálogo público» en el README: las ocho rutas, paginación (20 por defecto, máximo 100, 400 si se supera) y extensiones `unaccent`/`pg_trgm` con el privilegio necesario (dueño de la BD, R2). — `README.md` — plan «Impacto en 001 y 002»

**Checkpoint final**: `./mvnw verify` en verde y sin advertencias (`-Xlint:all,-processing`):
`ModularityTests`, `ApiApplicationTests`, `SchemaDriftIT`, `FlywayMigrationIT`, `MoneyTests`,
`IsbnTests`, `CatalogSecurityIT` completo y todos los tests de escenario de US1–US5. Ningún
`INSERT` en `src/main/resources/db/migration`; ninguna interfaz propia salvo Spring Data y MapStruct.

---

## Dependencies & Execution Order

### Grafo de dependencias entre fases

```text
Phase 1 Setup (vacía)
        │
        ▼
Phase 2 Foundational ──────────────────────────────────────────┐
        │                                                      │
        ▼                                                      │
Phase 3 US1 Explorar (P1, MVP)                                 │
        │                                                      │
        ├──────────────► Phase 4 US2 Buscar y filtrar (P1)     │
        │                       │                              │
        │                       ▼                              │
        ├──────────────► Phase 5 US3 Detalle (P1) ◄────────────┘ (CatalogNotFoundException nace aquí)
        │                       │
        │                       ▼
        └──────────────► Phase 6 US4 Series (P2)  (usa hasSeries de US2 y la excepción de US3)
                                │
                                ▼
                         Phase 7 US5 Clasificaciones (P2)  (amplía ClassificationQueryService,
                                │                           SeriesRepository y SeriesController de US4)
                                ▼
                         Phase 8 Polish
```

- **Foundational**: T003 → T004, T022; T005 → T007; T006 → T008; T007 + T008 → T012; T009–T012 → T013 → T014, T023; T010 → T015; T011 → T016; T018 → T019; T002 + T020 → T021.
- **US1** depende de Foundational. **US2** y **US3** amplían `ProductSpecifications`, `ProductController`, `ProductQueryService`, `CatalogMapper` y `GlobalExceptionHandler`; se ejecutan después de US1 y en ese orden (comparten archivos).
- **US4** necesita `hasSeries` (T034, US2) y `CatalogNotFoundException` (T040, US3).
- **US5** amplía `ClassificationQueryService`, `SeriesRepository` y `SeriesController` creados en US4.
- **Polish** depende de todas: T062 necesita las ocho rutas.

### Dentro de cada historia

Tests → dominio/repositorio → servicio → DTO/mapper → controlador.

---

## Parallel Opportunities (por fase)

| Fase | Tareas `[P]` (sin archivos compartidos entre sí) |
|------|---------------------------------------------------|
| 1 Setup | — |
| 2 Foundational | T001, T002 · T005, T006 · T007, T008 · T009, T010, T011 · T015, T016 · T017, T018 · T020 · T022, T023 |
| 3 US1 | T024, T025, T026 · T027, T028 |
| 4 US2 | T032, T033 · T035 |
| 5 US3 | T038, T039 · T040, T042 |
| 6 US4 | T045, T046, T047, T048 · T052 |
| 7 US5 | T054, T055, T056 |
| 8 Polish | — |

Sin `[P]` por regla: T003 (migración), T021 (`SecurityFilterChain`). Sin `[P]` por depender de
una tarea pendiente de la misma fase o compartir archivo: el resto.

### Parallel Example: User Story 1

```text
Task: "T024 ProductQueryServiceIT en src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java"
Task: "T025 ProductControllerTests en src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java"
Task: "T026 CatalogSecurityIT (US6 Escenario 2) en src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java"

Task: "T027 ProductSearchCriteria y ProductSpecifications en src/main/java/com/mangateca/api/catalog/domain/"
Task: "T028 NamedResponse y ProductSummaryResponse en src/main/java/com/mangateca/api/catalog/web/"
```

---

## Trazabilidad FR → tareas

| FR | Tareas |
|----|--------|
| FR-001 Listado público paginado (20 / máx. 100, 400) | T017, T018, T019, T024, T025, T030, T031 |
| FR-002 Orden por lista blanca, defecto novedades, estable | T019, T024, T025, T030 |
| FR-003 Tarjeta de producto | T025, T028, T029 |
| FR-004 Página fuera de rango vacía; `page` ≤ 1.000.000 | T017, T018, T024, T030 |
| FR-005 Búsqueda título/autor/ISBN, sin tildes, parcial, todas las palabras | T003, T032, T034 |
| FR-006 Filtros combinables | T032, T034, T036 |
| FR-007 Combinación con orden y paginación; sin duplicados | T032, T034, T036 |
| FR-008 Parámetros inválidos → 400 con el parámetro | T018, T019, T025, T033, T035, T036, T037, T039, T043 |
| FR-009 Contenido del detalle | T038, T039, T041, T042, T044 |
| FR-010 404 indistinguible | T038, T039, T040, T041, T044 |
| FR-011 Serie y volúmenes publicados paginados | T011, T045, T046, T047, T049, T050, T051, T052, T053 |
| FR-012 Del volumen a la serie | T042, T048, T053 |
| FR-013 Listados de clasificaciones paginados y alfabéticos | T017, T018, T054, T055, T056, T057, T058, T059, T060, T061 |
| FR-014 Solo clasificaciones con productos publicados | T046, T049, T051, T054, T057, T058, T059 |
| FR-015 Tipo MANGA/LIBRO/COMIC | T003, T009, T013 |
| FR-016 Precio positivo CLP sin decimales | T003, T005, T007, T012, T013 |
| FR-017 ISBN válido y único | T003, T006, T008, T012, T013 |
| FR-018 Volumen positivo y único en la serie | T003, T013 |
| FR-019 ≥ 1 autor y ≥ 1 género | — (lo garantiza la fábrica de `Product` de la feature 004; spec «Assumptions», R14) |
| FR-020 Solo publicados visibles | T003, T009, T024, T027, T032, T038, T041, T045 |
| FR-021 Solo lectura y anónimo; mismo catálogo por rol | T020, T021, T026, T031, T062 |
| FR-022 Errores `ProblemDetail` sin detalles internos | T033, T037, T039, T043 |

## Trazabilidad escenarios → tests

| Historia | Escenario | Test |
|----------|-----------|------|
| US1 | 1 Listado sin parámetros | T024, T025 |
| US1 | 2 Tamaño 101 | T025 |
| US1 | 3 Página fuera de rango | T024 |
| US1 | 4 Ordenamiento explícito | T024, T025 |
| US1 | 5 Tarjeta con serie | T025 |
| US1 | 6 Producto sin serie | T025 |
| US2 | 1 Sin mayúsculas ni tildes | T032 |
| US2 | 2 Por autor | T032 |
| US2 | 3 Por ISBN | T032 |
| US2 | 4 Filtros combinados | T032 |
| US2 | 5 Búsqueda + filtros + orden + página | T032 |
| US2 | 6 Parámetros inválidos | T033 |
| US2 | 7 Sin coincidencias | T032 |
| US3 | 1 Detalle publicado | T038, T039 |
| US3 | 2 No publicado ≡ inexistente | T038, T039 |
| US3 | 3 Identificador mal formado | T039 |
| US4 | 1 Serie con volumen no publicado | T045, T046, T047 |
| US4 | 2 De volumen a serie | T048 |
| US4 | 3 Serie inexistente o sin publicados | T045, T046, T047 |
| US5 | 1 Género sin publicados | T054 |
| US5 | 2 Listados de clasificaciones | T054, T055, T056 |
| US5 | 3 Autor solo con no publicados | T054 |
| US6 | 1 Acceso sin token | T020, T062 |
| US6 | 2 Mismo catálogo para todos los roles | T026 |

---

## Implementation Strategy

### MVP (US1)

1. Foundational completo → checkpoint.
2. US1 → **STOP y validar**: `GET /api/v1/products` paginado, ordenado y anónimo.

### Entrega incremental

1. US2 (búsqueda y filtros) → US3 (detalle): con ellas el frontend puede reemplazar el JSON de
   productos.
2. US4 (series) → US5 (menús de filtro).
3. Polish: seguridad en las ocho rutas, contrato, quickstart y
   rendimiento, README.

## Notes

- Un commit por tarea con su ID.
- Sin caché, rate limiting ni optimizaciones especulativas (Principio VII); sin fachada pública (R11).
- Excluidos por ser tests opcionales (VI): `CatalogConstraintsIT` (CHECK/UNIQUE de V3),
  `GlobalExceptionHandlerTests` de los dos ajustes (cubiertos por T033 y T039) y los casos borde
  sin escenario Given-When-Then (términos de 1–2 caracteres, texto en blanco). El escape de
  `%`/`_`/`\` y el ISBN con dígito verificador inválido sí se cubren en `ProductSearchIT`
  (añadidos tras la revisión de la fase US2).
