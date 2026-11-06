---
description: "Task list for 003-catalog-public-read"
---

# Tasks: Catalog (Public Read)

**Input**: Design documents from `/specs/003-catalog-public-read/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/openapi.yaml](contracts/openapi.yaml),
[quickstart.md](quickstart.md)

**Constitución**: v4.0.0

**Tests** (Principio VI): solo los obligatorios y un test por cada escenario Given-When-Then de
`spec.md`, según la tabla «Escenarios de aceptación» de `plan.md`. Los tests por escenario se
piden explícitamente en `spec.md` («User Scenarios & Testing»), como exige VI. Los
`@WebMvcTest` construyen las entidades que devuelve el servicio mockeado con `CatalogFixtures`
(T017), nunca con mocks. Ya existen desde la 001 y no se
regeneran: `ModularityTests` (`ApplicationModules.verify()`) y `ApiApplicationTests` (smoke de
arranque); se ejecutan en cada checkpoint. Mocks solo en la capa de aplicación dentro de
`@WebMvcTest`; la BD es siempre PostgreSQL 18 real de Testcontainers (`@ServiceConnection`), nunca
H2. Entidades, `Money` e `Isbn` nunca se mockean.

**Organization**: una fase por historia de usuario, en orden de prioridad (P1: US1, US2, US3, US6;
P2: US4, US5). Dentro de cada historia, las tareas `[TEST]` van antes que las de implementación
(test-first: fallan o no compilan hasta que se implementa).

**Datos de prueba**: solo en `src/test` (`CatalogTestData` con `JdbcClient` y scripts
`src/test/resources/catalog/*.sql`). **Ningún dato de ejemplo en las migraciones.**

**Sin dependencias Maven nuevas y sin ADR** (plan). No hay interfaces propias (Principio VII):
clases concretas salvo las exigidas por Spring Data y MapStruct.

## Format: `[ID] [P?] [Story] [Tipo] Descripción — ruta — requisito o escenario`

- **[P]**: puede ir en paralelo; no comparte archivos con otra `[P]` de la misma fase. Nunca en
  tareas que tocan `V3__create_catalog.sql`, `GlobalExceptionHandler.java` o la configuración
  del `SecurityFilterChain`.
- **[USn]**: historia de `spec.md`.
- **Tipo**: `[TEST]`, `[CODE]`, `[CONFIG]`, `[MIGRATION]` o `[DOC]`.
- Commit al terminar cada tarea, con Conventional Commits y el ID: `feat(catalog): … (T0xx)`.

## Path Conventions

Proyecto Maven único. Producción en `src/main/java/com/mangateca/api/catalog/` (abreviado
`catalog/`), tests en `src/test/java/com/mangateca/api/catalog/` (abreviado `test/catalog/`).
Todo paquete nuevo lleva `package-info.java` con `@NullMarked`, creado en la primera tarea que
añade una clase a ese paquete (T001 los crea todos).

---

## Phase 1: Setup

**Purpose**: esqueleto del módulo Modulith.

- [ ] T001 [CONFIG] Crear `package-info.java` con `@NullMarked` en `catalog/domain`, `catalog/application`, `catalog/web` y `catalog/config`; y en `catalog/package-info.java` añadir además `@ApplicationModule(displayName = "Catalog")` (mismo patrón que `identity/package-info.java`). El paquete raíz **no** lleva ninguna clase: el módulo no expone API pública (plan, R11). — `src/main/java/com/mangateca/api/catalog/**/package-info.java` — Principios I, IV (JSpecify)

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: esquema V3, value objects, entidades, repositorios, ajustes de plataforma y cadena de seguridad. Ninguna historia puede empezar antes.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Esquema

- [ ] T002 [MIGRATION] Crear `V3__create_catalog.sql` (V1 y V2 intactas; la última aplicada es V2) con, en este orden:
  - `CREATE EXTENSION IF NOT EXISTS unaccent;` y `CREATE EXTENSION IF NOT EXISTS pg_trgm;` (V1 no las crea).
  - Función `catalog_normalize(text) RETURNS text`, `LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT`, cuerpo `lower(public.unaccent('public.unaccent'::regdictionary, $1))` (R2).
  - Tablas `publishers (id UUID PK, name VARCHAR(200) NOT NULL)`, `genres (id UUID PK, name VARCHAR(100) NOT NULL)`, `authors (id UUID PK, name VARCHAR(200) NOT NULL)`, `series (id UUID PK, name VARCHAR(200) NOT NULL, description TEXT NOT NULL)`.
  - `products` con las columnas exactas de data-model §2: `id UUID PK`; `type VARCHAR(10) NOT NULL CHECK (type IN ('MANGA','LIBRO','COMIC'))`; `status VARCHAR(12) NOT NULL CHECK (status IN ('PUBLISHED','UNPUBLISHED'))`; `title VARCHAR(300) NOT NULL`; `synopsis TEXT NOT NULL`; `price NUMERIC(12,0) NOT NULL CHECK (price > 0)`; `isbn VARCHAR(13)` nullable con `CHECK (isbn ~ '^([0-9]{9}[0-9X]|[0-9]{13})$')`; `language VARCHAR(35) NOT NULL`; `page_count INTEGER NOT NULL CHECK (page_count > 0)`; `publication_date DATE NOT NULL`; `cover_url VARCHAR(2048) NOT NULL`; `publisher_id UUID NOT NULL REFERENCES publishers(id)`; `series_id UUID REFERENCES series(id)` nullable; `volume_number INTEGER` nullable con `CHECK (volume_number > 0)`; `created_at TIMESTAMPTZ NOT NULL`.
  - `CHECK ((series_id IS NULL) = (volume_number IS NULL))`, `CONSTRAINT products_series_volume_uk UNIQUE (series_id, volume_number)` y `CONSTRAINT products_isbn_uk UNIQUE (isbn)`. Cada unicidad es **una sola** restricción con nombre; no se crea además un `UNIQUE` de columna ni un índice aparte.
  - `product_authors (product_id UUID REFERENCES products(id) ON DELETE CASCADE, author_id UUID REFERENCES authors(id), PRIMARY KEY (product_id, author_id))` y `product_genres (product_id … ON DELETE CASCADE, genre_id UUID REFERENCES genres(id), PRIMARY KEY (product_id, genre_id))`.
  - Índices de data-model §3, exactos: `products_pub_created_idx (created_at, id)`, `products_pub_price_idx (price, id)`, `products_pub_pubdate_idx (publication_date, id)`, `products_pub_title_idx (title, id)` —los cuatro `WHERE status = 'PUBLISHED'`—, `products_pub_type_idx (type)` y `products_pub_publisher_idx (publisher_id)` también parciales; `products_title_trgm_idx GIN (catalog_normalize(title) gin_trgm_ops)`; `authors_name_trgm_idx GIN (catalog_normalize(name) gin_trgm_ops)`; `product_authors_author_idx (author_id, product_id)`; `product_genres_genre_idx (genre_id, product_id)`.
  - Sin `INSERT` de datos. — `src/main/resources/db/migration/V3__create_catalog.sql` — FR-015..FR-018, FR-020; R1, R2, R12, R14, R15
- [ ] T003 [TEST] Actualizar `FlywayMigrationIT`: la aserción «sin tablas de negocio ajenas a plataforma e identidad» (línea ~68) debe incluir las siete tablas nuevas (`products`, `authors`, `publishers`, `genres`, `series`, `product_authors`, `product_genres`) y renombrar el `@DisplayName`; añadir un test que compruebe que V3 se aplicó con éxito y que `unaccent` y `pg_trgm` existen en `pg_extension`, y que `catalog_normalize('Dragón Ball')` devuelve `dragon ball`. Crear además `CatalogConstraintsIT` (`@DataJpaTest` + Testcontainers, inserciones con `JdbcClient`): cada caso espera `DataIntegrityViolationException`: ISBN duplicado (`products_isbn_uk`); mismo `volume_number` en la misma serie (`products_series_volume_uk`); `series_id` sin `volume_number` y viceversa; `volume_number = 0`; `type = 'REVISTA'`; `status = 'BORRADOR'`; `price = 0`; `isbn` con formato inválido (`'12345'`). Contraste: dos productos sin ISBN y el mismo `volume_number` en series distintas **sí** se aceptan. — `src/test/java/com/mangateca/api/persistence/FlywayMigrationIT.java`, `src/test/java/com/mangateca/api/catalog/domain/CatalogConstraintsIT.java` — FR-005 de 001; FR-015, FR-017, FR-018; R2, R14

### Value Objects (invariantes de dominio, tests obligatorios sin Spring)

- [ ] T004 [P] [TEST] `MoneyTests` (JUnit 5 + AssertJ, sin Spring ni BD): monto cero y negativo → `IllegalArgumentException`; `null` rechazado; `new Money(new BigDecimal("1500"))` y `new BigDecimal("1.5E3")` ambos equivalen a escala 0 (`amount().scale() == 0`); `10.5` → `IllegalArgumentException`; `Money.clp(8990)`; `compareTo`; igualdad por valor; `Money.CURRENCY` es `"CLP"`. — `src/test/java/com/mangateca/api/catalog/domain/MoneyTests.java` — FR-016; Constitución II, VI.2
- [ ] T005 [P] [TEST] `IsbnTests` (sin Spring ni BD): normaliza guiones y espacios (`978-4-08-882011-8` → `9784088820118`); ISBN-10 con `X` en minúscula pasa a mayúscula; ISBN-10 y ISBN-13 válidos aceptados; dígito verificador incorrecto rechazado (ambos formatos); longitud 9, 11, 12 y 14 rechazada; caracteres no numéricos (letras en medio, `X` fuera de la última posición del ISBN-10) rechazados; `Isbn.normalize(String)` solo limpia y no valida. — `src/test/java/com/mangateca/api/catalog/domain/IsbnTests.java` — FR-017; Constitución II, VI.2
- [ ] T006 [P] [CODE] `Money` como `record Money(BigDecimal amount) implements Comparable<Money>` con constante `CURRENCY = "CLP"`. Constructor compacto: `amount` no nulo; `amount.signum() > 0`; normaliza a escala 0 con `setScale(0, RoundingMode.UNNECESSARY)` y convierte `ArithmeticException` en `IllegalArgumentException`. Fábrica `Money.clp(long)`. — `catalog/domain/Money.java` — FR-016; R6
- [ ] T007 [P] [CODE] `Isbn` como `record Isbn(String value)`. Constructor compacto: elimina guiones y espacios, pasa a mayúsculas; acepta 10 caracteres (9 dígitos + dígito o `X`) o 13 dígitos; valida el dígito verificador (ISBN-10: Σ dígito·(10−i) ≡ 0 mod 11; ISBN-13: pesos 1/3 ≡ 0 mod 10); `IllegalArgumentException` en caso contrario. Método estático `normalize(String)` que solo limpia (sin validar). — `catalog/domain/Isbn.java` — FR-017; R6

### Entidades y repositorios

- [ ] T008 [P] [CODE] Enums `ProductType` (`MANGA`, `LIBRO`, `COMIC`) y `PublicationStatus` (`PUBLISHED`, `UNPUBLISHED`). — `catalog/domain/ProductType.java`, `catalog/domain/PublicationStatus.java` — FR-015, FR-020
- [ ] T009 [P] [CODE] Entidades `Author`, `Publisher`, `Genre` y `Series` (`@Entity`, `@Table` con los nombres `authors`, `publishers`, `genres`, `series`; `@Getter` de Lombok, **sin `@Data`**; constructor `protected` sin fábrica pública; `@Id UUID id`; `name` `@Column(nullable = false, length = …)` con 200/200/100/200 como en V3; `Series.description` `@Column(nullable = false)` TEXT). Sin colecciones inversas. — `catalog/domain/Author.java`, `Publisher.java`, `Genre.java`, `Series.java` — data-model §4
- [ ] T010 [P] [CODE] `MoneyConverter` (`AttributeConverter<Money, BigDecimal>`) e `IsbnConverter` (`AttributeConverter<Isbn, String>`, `null` ↔ `null`), ambos `@Converter` **sin `autoApply`**. — `catalog/domain/MoneyConverter.java`, `catalog/domain/IsbnConverter.java` — R6
- [ ] T011 [CODE] Entidad `Product` (`@Entity`, `@Table(name = "products")`, `@Getter`, constructor `protected`): `id`; `type` y `status` con `@Enumerated(EnumType.STRING)`; `title`; `synopsis`; `price` `Money` con `@Convert(converter = MoneyConverter.class)` y `@Column(precision = 12, scale = 0)`; `isbn` `@Nullable Isbn` con `@Convert(converter = IsbnConverter.class)`; `language`; `pageCount`; `publicationDate` `LocalDate`; `coverUrl`; `volumeNumber` `@Nullable Integer`; `createdAt` `Instant`; `@ManyToOne(fetch = LAZY) publisher`; `@ManyToOne(fetch = LAZY) @Nullable series`; `@ManyToMany Set<Author> authors` y `Set<Genre> genres` con `@JoinTable` (`product_authors`/`product_genres`) y `@OrderBy("name")`. Colecciones `Set`, asociaciones `LAZY`. Los accesores de `series` y `volumeNumber` devuelven `@Nullable` (no `Optional`). — `catalog/domain/Product.java` — data-model §4; depende de T006–T010
- [ ] T012 [CODE] Repositorios: `ProductId` (proyección de interfaz con `UUID getId()`); `ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product>` con `@EntityGraph(attributePaths = {"authors", "series"}) List<Product> findAllByIdIn(Collection<UUID> ids)` y `@EntityGraph(attributePaths = {"authors", "genres", "publisher", "series"}) Optional<Product> findWithDetailsByIdAndStatus(UUID id, PublicationStatus status)`; y `AuthorRepository`, `GenreRepository`, `PublisherRepository`, `SeriesRepository` (`JpaRepository<…, UUID>`, de momento sin métodos). Ningún método retorna `null`. — `catalog/domain/ProductId.java`, `ProductRepository.java`, `AuthorRepository.java`, `GenreRepository.java`, `PublisherRepository.java`, `SeriesRepository.java` — R3; depende de T011
- [ ] T013 [CODE] `CatalogNotFoundException extends ErrorResponseException` (estado 404, `type` `about:blank`, sin mensaje con datos del recurso): una única clase para producto inexistente, producto no publicado, serie inexistente y serie sin volúmenes publicados, de modo que las respuestas sean idénticas. — `catalog/application/CatalogNotFoundException.java` — FR-010, SC-005; R9

### Plataforma y seguridad

- [ ] T014 [TEST] Ampliar `GlobalExceptionHandlerTests` (y, si hace falta, `ProbeController` con un `GET /api/v1/probes/{id}` de `@PathVariable UUID` y un parámetro enum): (a) UUID de ruta mal formado → 400 `ProblemDetail` con `errors: [{field: "id", message: "Formato de valor inválido."}]` y **sin** el valor recibido en el cuerpo; (b) fallo de enlace de un `@ModelAttribute` (valor no convertible) → 400 con el nombre del parámetro y el mismo texto fijo, sin el mensaje de conversión de Spring ni el tipo Java; (c) los casos ya existentes siguen pasando. — `src/test/java/com/mangateca/api/platform/web/GlobalExceptionHandlerTests.java`, `src/test/java/com/mangateca/api/platform/web/ProbeController.java` — FR-008, FR-022, US3 (UUID mal formado); plan «Impacto en 001 y 002»
- [ ] T015 [CODE] En `GlobalExceptionHandler`: (1) override de `handleTypeMismatch` que responde 400 con la propiedad `errors: [{field: <nombre del parámetro>, message: "Formato de valor inválido."}]` (nombre tomado de `MethodArgumentTypeMismatchException#getName()`); (2) en `handleMethodArgumentNotValid`, si `FieldError.isBindingFailure()` usar el mismo texto fijo en lugar de `getDefaultMessage()`. Cambio aditivo: los demás caminos no cambian. — `src/main/java/com/mangateca/api/platform/web/GlobalExceptionHandler.java` — FR-008, FR-017/FR-022 de 001; plan; depende de T014
- [ ] T016 [CODE] `CatalogSecurityConfiguration` en `catalog/config` con un `SecurityFilterChain` `@Order(2)`: `securityMatcher` (`OrRequestMatcher` de `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET|HEAD|OPTIONS, ruta)`, igual que `IdentitySecurityConfiguration`) solo sobre las **rutas exactas** `/api/v1/products`, `/api/v1/products/*`, `/api/v1/series`, `/api/v1/series/*`, `/api/v1/series/*/volumes`, `/api/v1/genres`, `/api/v1/authors`, `/api/v1/publishers`; `permitAll`, CSRF deshabilitado, sesión `STATELESS`, `cors(withDefaults())`, **sin** `oauth2ResourceServer` (un token inválido en estas rutas se ignora). `POST`/`PUT`/`PATCH`/`DELETE` no coinciden y caen en la cadena por defecto. — `catalog/config/CatalogSecurityConfiguration.java` — FR-021; Constitución V; R8

### Soporte de pruebas

- [ ] T017 [TEST] Soporte de datos de prueba (solo `src/test`):
  - `CatalogTestData`: builder con `JdbcClient` que inserta editoriales, géneros, autores, series y productos (con autores y géneros, estado, fecha de incorporación, serie y volumen opcionales) y un método `clear()` que borra en orden de FKs (`product_authors`, `product_genres`, `products`, `series`, `authors`, `genres`, `publishers`). Cada test arranca con `clear()` (la BD de Testcontainers es compartida entre clases).
  - `CatalogFixtures`: fábricas estáticas que construyen `Product`, `Author`, `Publisher`, `Genre` y `Series` **en memoria** para los `@WebMvcTest`: instancia con `BeanUtils.instantiateClass` (constructor `protected`) y asigna los campos con `ReflectionTestUtils.setField`, con `Money` e `Isbn` reales. Valores por defecto válidos y métodos para variar serie/volumen, ISBN nulo y varios autores/géneros. **No** añadir constructores ni setters públicos a las entidades de producción.
  — `src/test/java/com/mangateca/api/catalog/support/CatalogTestData.java`, `src/test/java/com/mangateca/api/catalog/support/CatalogFixtures.java` — R10; depende de T002 y T011

**Checkpoint**: `./mvnw verify` en verde con V3: `ModularityTests`, `ApiApplicationTests`, `SchemaDriftIT` (comprobar que no asume el conjunto de tablas), `FlywayMigrationIT`, `MoneyTests` e `IsbnTests`. `ddl-auto=validate` acepta las entidades.

---

## Phase 3: User Story 1 - Explorar el catálogo (Priority: P1) 🎯 MVP

**Goal**: listado público paginado y ordenado, con tarjeta de producto y sin N+1.

**Independent Test**: con productos publicados y no publicados cargados, consultar el listado sin credenciales y comprobar página, tamaño, orden y contenido de cada tarjeta.

### Tests for User Story 1

- [ ] T018 [P] [US1] [TEST] `ProductQueryServiceIT` (`@DataJpaTest` con `@AutoConfigureTestDatabase(replace = NONE)`, `@Import({TestcontainersConfiguration.class, ProductQueryService.class})`, `hibernate.generate_statistics=true`): (a) 25 publicados + 3 no publicados → página 0 sin parámetros trae 20 publicados ordenados por `createdAt` descendente y los metadatos (`totalElements = 25`, `totalPages = 2`); (b) 45 publicados, página 50 tamaño 20 → contenido vacío, `totalElements = 45`, sin excepción; (c) `@ParameterizedTest` de ordenamiento: `title` asc, `price` desc, `publicationDate` desc, `createdAt` asc, comparando el orden esperado con títulos ASCII; (d) empate: 5 productos con el mismo precio → orden estable e idéntico en dos consultas, con `id` como desempate en el mismo sentido que el criterio; (e) sin N+1: el número de sentencias SQL (estadísticas de Hibernate) es el mismo para una página de 1 producto y para una de 20, con autores y serie; y, en un método marcado `@Transactional(propagation = Propagation.NOT_SUPPORTED)` (sin la transacción de `@DataJpaTest`, con datos de `CatalogTestData` y `clear()` al inicio), el `CatalogMapper` se ejecuta sobre el resultado sin `LazyInitializationException`; (f) con `logging.level.org.hibernate.SQL=DEBUG` en el test y `OutputCaptureExtension`, la sentencia del paso 1 selecciona solo la columna `id` de `products` (si carga la entidad completa, aplicar la alternativa de T021). — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US1 escenarios 1, 3, 4; FR-001, FR-002, FR-004; Constitución IV (N+1)
- [ ] T019 [P] [US1] [TEST] `ProductControllerTests` (`@WebMvcTest(ProductController.class)` con `MockMvcTester`; `@Import` de `SecurityConfiguration`, `CatalogSecurityConfiguration`, `GlobalExceptionHandler`, `CatalogMapperImpl` y los beans de seguridad necesarios; `@MockitoBean ProductQueryService` que devuelve entidades de `CatalogFixtures`): (a) `size=101` → 400 `application/problem+json` con `errors[field=size]`; (b) `size=0`, `page=-1` y `page=1000001` → 400 con el campo afectado; (c) `size=abc` o `page=abc` → 400 con el campo y sin eco del valor; (d) respuesta 200 con la forma `PagedModel` (`content` y `page.size/number/totalElements/totalPages`); (e) tarjeta de un producto de la serie «Dragon Ball» volumen 3: `id`, `title`, `type`, `authors`, `price` (entero), `coverUrl`, `series.name` y `volumeNumber = 3`; (f) tarjeta de producto sin serie: `series` y `volumeNumber` nulos. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US1 escenarios 2, 5, 6; FR-001, FR-003, FR-008

### Implementation for User Story 1

- [ ] T020 [P] [US1] [CODE] `ProductSearchCriteria` (record: `@Nullable String text`, `@Nullable ProductType type`, `@Nullable UUID genreId`, `authorId`, `publisherId`, `seriesId`, `@Nullable Money minPrice`, `@Nullable Money maxPrice`; todos opcionales) y `ProductSpecifications` (clase final con métodos estáticos que devuelven `Specification<Product>`). En esta tarea: `published()` (`status = PUBLISHED`) y `from(ProductSearchCriteria)` que compone con `Specification.unrestricted()` como neutro y `.and(...)`. Los filtros concretos se añaden en US2. Atributos por nombre de cadena. — `catalog/domain/ProductSearchCriteria.java`, `catalog/domain/ProductSpecifications.java` — FR-020; R3
- [ ] T021 [US1] [CODE] `ProductQueryService` (`@Service`, `@Transactional(readOnly = true)`, clase concreta): `Page<Product> search(ProductSearchCriteria criteria, Pageable pageable)` en dos pasos: (1) `productRepository.findBy(spec, q -> q.as(ProductId.class).page(pageable))` —verificar que solo selecciona `id`; si no, `q.project("id")`—; (2) si hay IDs, `findAllByIdIn(ids)` y reordenar según la posición en la página de IDs; devolver `new PageImpl<>(ordenados, pageable, page.getTotalElements())`. Página vacía → sin segunda consulta. Nunca `null` en colecciones. — `catalog/application/ProductQueryService.java` — FR-001..FR-004; R3; depende de T012, T020
- [ ] T022 [P] [US1] [CODE] `PageParams` (record `@ParameterObject`, `Integer page`, `Integer size`; constructor compacto aplica `0` y `20` si llegan `null`; `@Min(0) @Max(1_000_000)` en `page`, `@Min(1) @Max(100)` en `size`, con mensajes en castellano; constantes públicas `MAX_SIZE = 100` y `MAX_PAGE = 1_000_000`, usadas en esas mismas anotaciones (`@Max(MAX_SIZE)`, `@Max(MAX_PAGE)`); método `PageRequest toPageRequest(Sort sort)`). Ningún controlador recibe `Pageable`. — `catalog/web/PageParams.java` — FR-001, FR-008; R4, R13
- [ ] T023 [P] [US1] [CODE] DTO records: `NamedResponse(UUID id, String name)` y `ProductSummaryResponse(UUID id, String title, ProductType type, List<NamedResponse> authors, long price, String coverUrl, @Nullable NamedResponse series, @Nullable Integer volumeNumber)`. — `catalog/web/NamedResponse.java`, `catalog/web/ProductSummaryResponse.java` — FR-003; contrato `ProductSummaryResponse`
- [ ] T024 [US1] [CODE] `CatalogMapper` (MapStruct `@Mapper(componentModel = "spring")`, interfaz): `ProductSummaryResponse toSummary(Product)` (mapea `price` con un método por defecto `Money → long`, `authors → NamedResponse` y `series → NamedResponse`) y `NamedResponse` desde `Author`. No toca `publisher` ni `genres` en la tarjeta. — `catalog/web/CatalogMapper.java` — R3 (mapear fuera de la transacción: el grafo trae autores y serie); depende de T023
- [ ] T025 [US1] [CODE] `ProductSearchRequest` (record `@ParameterObject` para el listado) con, por ahora, `page` y `size` (mismas restricciones que `PageParams`, reutilizando `@Max(PageParams.MAX_PAGE)` y `@Max(PageParams.MAX_SIZE)`, sin repetir los literales) y `sort` (`@Pattern("^(title|price|publicationDate|createdAt)(,(asc|desc))?$")`, un solo `String`, defecto `createdAt,desc`); método `Pageable toPageable()` que traduce la clave de API a la propiedad de la entidad con un mapa explícito y añade `id` **en el mismo sentido** que el criterio principal; los filtros y `q` se añaden en US2. — `catalog/web/ProductSearchRequest.java` — FR-001, FR-002, FR-008; R4, R5; depende de T022
- [ ] T026 [US1] [CODE] `ProductController` (`@RestController`, `@RequestMapping("/api/v1/products")`, `@SecurityRequirements` sin valores para que OpenAPI marque `security: []`): `GET` listado con `@Valid @ModelAttribute ProductSearchRequest` que devuelve `PagedModel<ProductSummaryResponse>` creado con `new PagedModel<>(page.map(mapper::toSummary))` (nunca `PageImpl`); `@Tag` y `@Operation` de springdoc alineados con el contrato. — `catalog/web/ProductController.java` — FR-001..FR-004; contrato `listProducts`; depende de T021, T024, T025

**Checkpoint**: T018 y T019 en verde; `GET /api/v1/products` responde sin credenciales.

---

## Phase 4: User Story 2 - Buscar y filtrar (Priority: P1)

**Goal**: búsqueda por texto (título, autor, ISBN; sin tildes ni mayúsculas, parcial, todas las palabras) y filtros combinables.

**Independent Test**: cargar productos con títulos con tilde, autores e ISBN conocidos y verificar que cada búsqueda y combinación de filtros devuelve exactamente los productos esperados.

### Tests for User Story 2

- [ ] T027 [P] [US2] [TEST] `ProductSearchIT` (`@DataJpaTest` + Testcontainers, repositorio con `ProductSpecifications.from(...)`; `unaccent` y `pg_trgm` reales): (a) «Dragón» se encuentra con `dragon`, con `DRAGON` y con `drag` (parcial); (b) autor «Akira Toriyama» con `toriyama`; (c) `dragon toriyama` exige ambas palabras y funciona en cualquier orden (`toriyama dragon`); un producto con solo una de las palabras no aparece; (d) ISBN con guiones y sin guiones devuelven el mismo producto, y el resultado contiene **exactamente** ese producto; (e) producto sin ISBN: no aparece por ISBN pero sí por título y autor; (f) texto vacío o solo espacios = sin búsqueda; (g) `%`, `_` y `\` en el texto se tratan como literales (un título con `100%` no coincide con `%`-comodín); (h) término de 1–2 caracteres (`a`) devuelve los productos que lo contienen sin error (R1); (i) filtros: tipo + género + rango de precio devuelve solo productos que cumplen las tres condiciones, y cada filtro por separado (`authorId`, `publisherId`, `seriesId`); `minPrice`/`maxPrice` inclusivos; (j) producto con varios autores y varios géneros no se duplica (ni en el contenido ni en `totalElements`) al filtrar por cualquiera de ellos o al buscar por dos de sus autores; (k) identificador de clasificación inexistente → página vacía; (l) búsqueda + filtros + orden + segunda página → la segunda página del conjunto correcto en el orden pedido; (m) sin coincidencias → página vacía con metadatos; (n) productos no publicados nunca aparecen en ningún caso anterior. — `src/test/java/com/mangateca/api/catalog/domain/ProductSearchIT.java` — US2 escenarios 1–7, Edge Cases; FR-005..FR-007
- [ ] T028 [P] [US2] [TEST] Ampliar `ProductControllerTests` con los parámetros inválidos del listado: `minPrice=9000&maxPrice=100` → 400 con `errors[field=minPrice]`; `type=XYZ` → 400 con `errors[field=type]` sin eco del valor; `sort=synopsis` y `sort=price,sideways` → 400 con `errors[field=sort]`; `genreId=abc` → 400 con `errors[field=genreId]`; `minPrice=0` y `minPrice=-5` → 400; `q` de más de 100 caracteres → 400 con `errors[field=q]`; traducción del orden, capturando el `Pageable` con `ArgumentCaptor`: sin `sort` → `createdAt DESC, id DESC`; `sort=price` → `price ASC, id ASC` (sentido por defecto ascendente, FR-002); `sort=title,desc` → `title DESC, id DESC`; `sort=publicationDate,asc` → `publicationDate ASC, id ASC`; cada filtro bien formado llega al servicio como `ProductSearchCriteria` (capturar con `ArgumentCaptor`): `q` recortado, `type`, UUID, y precios como `Money`. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US2 «Parámetros inválidos»; US1 «Ordenamiento explícito»; FR-002, FR-008, SC-006

### Implementation for User Story 2

- [ ] T029 [US2] [CODE] Completar `ProductSpecifications` con los predicados, todos `EXISTS` o comparaciones directas (sin `join` ni `distinct`): `hasType`, `hasPublisher`, `hasSeries`, `priceBetween(min, max)` (comparando `Money`, inclusivo, cualquiera de los extremos opcional), `hasAuthor` y `hasGenre` (subconsulta `EXISTS` sobre las colecciones), y `matchesText(String)`: normaliza con `trim`, divide por espacios en blanco y descarta vacíos; para cada palabra, `catalog_normalize(title) LIKE catalog_normalize('%palabra%') ESCAPE '\'` **o** `EXISTS` de un autor con `catalog_normalize(name) LIKE …`, unidas con `AND`; escapa `\`, `%` y `_` de la palabra; añade con `OR` la coincidencia exacta `isbn = Isbn.normalize(texto completo)` solo si el texto normalizado tiene forma de ISBN (`^([0-9]{9}[0-9X]|[0-9]{13})$`). Llamadas con `cb.function("catalog_normalize", String.class, …)`. Texto nulo/vacío → `Specification.unrestricted()`. `from(...)` los compone todos con `published()`. — `catalog/domain/ProductSpecifications.java` — FR-005..FR-007; R1, R3; depende de T020, T027
- [ ] T030 [US2] [CODE] `@ValidPriceRange` (anotación de clase) y `ValidPriceRangeValidator`: rechaza `minPrice > maxPrice` registrando la violación en el nodo `minPrice` (`ConstraintValidatorContext#buildConstraintViolationWithTemplate(...).addPropertyNode("minPrice")`), con mensaje en castellano; sin efecto si falta alguno de los dos. Mismo patrón que `@ValidPassword`. — `catalog/web/ValidPriceRange.java`, `catalog/web/ValidPriceRangeValidator.java` — FR-008; plan (trade-offs)
- [ ] T031 [US2] [CODE] Completar `ProductSearchRequest` con `@Size(max = 100) String q`, `ProductType type`, `UUID genreId`, `authorId`, `publisherId`, `seriesId`, `@Positive Long minPrice`, `@Positive Long maxPrice`, anotado con `@ValidPriceRange`; método `ProductSearchCriteria toCriteria()` (`q` recortado; vacío ⇒ `null`; precios ⇒ `Money.clp(...)`). Actualizar `ProductController` para pasar `toCriteria()` al servicio. — `catalog/web/ProductSearchRequest.java`, `catalog/web/ProductController.java` — FR-006, FR-008; depende de T025, T030

**Checkpoint**: T027 y T028 en verde; las búsquedas del quickstart §4c–4f responden como se espera.

---

## Phase 5: User Story 3 - Ver el detalle de un producto (Priority: P1)

**Goal**: detalle completo de un producto publicado; 404 indistinguible para no publicado e inexistente.

**Independent Test**: consultar el detalle de un producto publicado, de uno no publicado y de uno inexistente, y comparar respuestas.

### Tests for User Story 3

- [ ] T032 [P] [US3] [TEST] Ampliar `ProductQueryServiceIT`: `detail(id)` de un producto publicado con 2 autores, 2 géneros, editorial y serie devuelve todos los datos con una sola sentencia SQL; un producto `UNPUBLISHED` y un UUID inexistente lanzan **la misma** `CatalogNotFoundException` (mismo tipo, mismo estado). — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US3 escenarios 1–2; FR-009, FR-010
- [ ] T033 [P] [US3] [TEST] Ampliar `ProductControllerTests` para `GET /api/v1/products/{id}`: (a) 200 con título, tipo, sinopsis, autores, editorial, géneros, ISBN, idioma, número de páginas, fecha de publicación, precio y portada; si pertenece a una serie, `series.id`, `series.name` y `volumeNumber` (permite llegar a la serie); producto sin ISBN ni serie → `isbn`, `series` y `volumeNumber` nulos; (b) servicio lanzando `CatalogNotFoundException` → 404 `problem+json`; los cuerpos de «no publicado» y «inexistente» (misma ruta de forma, UUID distinto) son idénticos salvo `instance`; (c) `/api/v1/products/no-es-un-uuid` → 400 con `errors[field=id]` y sin el valor recibido. — `src/test/java/com/mangateca/api/catalog/web/ProductControllerTests.java` — US3 escenarios 1–3; US4 escenario 2; FR-009, FR-010, FR-012, SC-005

### Implementation for User Story 3

- [ ] T034 [US3] [CODE] `ProductQueryService.detail(UUID id)`: `findWithDetailsByIdAndStatus(id, PUBLISHED)` y `orElseThrow(CatalogNotFoundException::new)` (una sola ruta de error para ambos casos). — `catalog/application/ProductQueryService.java` — FR-010; depende de T013, T012
- [ ] T035 [P] [US3] [CODE] `ProductDetailResponse(UUID id, String title, ProductType type, String synopsis, List<NamedResponse> authors, NamedResponse publisher, List<NamedResponse> genres, @Nullable String isbn, String language, int pageCount, LocalDate publicationDate, long price, String coverUrl, @Nullable NamedResponse series, @Nullable Integer volumeNumber)` y el método `toDetail(Product)` en `CatalogMapper` (`Isbn → String` con `value()`, `Money → long`). — `catalog/web/ProductDetailResponse.java`, `catalog/web/CatalogMapper.java` — FR-009; contrato `ProductDetailResponse`
- [ ] T036 [US3] [CODE] `GET /api/v1/products/{id}` en `ProductController` (`@PathVariable UUID id`, `@Operation`, respuestas 200/400/404 documentadas). — `catalog/web/ProductController.java` — FR-009, FR-010; contrato `getProduct`; depende de T034, T035

**Checkpoint**: T032 y T033 en verde.

---

## Phase 6: User Story 6 - Acceso anónimo (Priority: P1)

**Goal**: todos los endpoints de lectura son anónimos; la escritura sigue denegada; los tres roles ven el mismo catálogo.

**Independent Test**: invocar todos los endpoints de lectura sin credenciales y con una sesión válida de cliente y de administrador.

> Los endpoints de series y clasificaciones se crean en US4/US5; `CatalogSecurityIT` se escribe aquí, pero las rutas de series, géneros, autores y editoriales solo responden 200 al terminar esas fases. Hasta entonces, los casos de esas rutas se anotan con `@Disabled("T0xx")` y se activan en T050.

### Tests for User Story 6

- [ ] T037 [US6] [TEST] `CatalogSecurityIT` (`@SpringBootTest(RANDOM_PORT)` + Testcontainers + `TestSecurityConfiguration`, `RestTestClient` como `SecurityIT`; datos con `CatalogTestData`): (a) `GET` anónimo → no 401/403 en `/api/v1/products` y `/api/v1/products/{id}` (y, activados en T050, en las otras seis rutas); (b) `POST`, `PUT` y `DELETE` anónimos sobre `/api/v1/products`, `/api/v1/products/{id}`, `/api/v1/series`, `/api/v1/genres`, `/api/v1/authors` y `/api/v1/publishers` → 401 `problem+json`; (c) `GET` con `Authorization: Bearer basura` o con un token caducado → 200 (se ignora); `HEAD` anónimo a `/api/v1/products` → 200 sin cuerpo; (d) cliente (`ROLE_CUSTOMER`) y administrador (`ROLE_ADMIN`) con token válido reciben exactamente el mismo cuerpo del listado que un invitado; (e) ninguna ruta bajo `/api/v1/` ajena a las ocho sigue permitida (p. ej. `GET /api/v1/products/x/y` y `GET /api/v1/orders` → 401 anónimo). — `src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java` — US6 escenarios 1–2; FR-021; SC-004; Constitución V, VI.3

### Implementation for User Story 6

- [ ] T038 [US6] [CODE] Dejar T037 en verde ajustando solo `CatalogSecurityConfiguration` y las anotaciones de OpenAPI. Criterios de cierre:
  1. Al arrancar hay exactamente tres `SecurityFilterChain`, en este orden: `identity` (`@Order(1)`), `catalog` (`@Order(2)`) y la de `platform` (sin `@Order`).
  2. Un preflight `OPTIONS` con `Origin: http://localhost:5173` y `Access-Control-Request-Method: GET` a `/api/v1/products` responde 200 con `Access-Control-Allow-Origin`; con un origen no permitido, el rechazo de CORS de 001.
  3. `GET /v3/api-docs` muestra `security: []` en `listProducts` y `getProduct`.
  — `catalog/config/CatalogSecurityConfiguration.java`, `catalog/web/ProductController.java` — FR-021; contrato

**Checkpoint**: `CatalogSecurityIT` en verde para las rutas de productos.

---

## Phase 7: User Story 4 - Navegar por series (Priority: P2)

**Goal**: datos de una serie y sus volúmenes publicados, paginados y ordenados por número de volumen.

**Independent Test**: cargar una serie con volúmenes 1, 2 y 3 donde el 2 no está publicado y consultar la serie.

### Tests for User Story 4

- [ ] T039 [P] [US4] [TEST] Ampliar `ProductQueryServiceIT`: serie con volúmenes 1, 2 (no publicado) y 3 → `volumes(seriesId, page)` devuelve solo 1 y 3, en ese orden y con `totalElements = 2`; paginación con `size = 1`; serie inexistente y serie sin volúmenes publicados lanzan `CatalogNotFoundException`; el orden es siempre por `volumeNumber` ascendente. — `src/test/java/com/mangateca/api/catalog/application/ProductQueryServiceIT.java` — US4 escenarios 1, 3; FR-011
- [ ] T040 [P] [US4] [TEST] `ClassificationQueryServiceIT` parte series (`@DataJpaTest` + Testcontainers + `@Import(ClassificationQueryService.class)`): `listSeries` incluye solo series con al menos un producto publicado, ordenadas por nombre; `seriesDetail(id)` devuelve nombre y descripción, y lanza `CatalogNotFoundException` para serie inexistente o sin volúmenes publicados. — `src/test/java/com/mangateca/api/catalog/application/ClassificationQueryServiceIT.java` — US4 escenario 3; US5 «series»; FR-011, FR-013, FR-014
- [ ] T041 [P] [US4] [TEST] `SeriesControllerTests` (`@WebMvcTest(SeriesController.class)`): `GET /api/v1/series/{id}` 200 con `id`, `name`, `description`; `GET /api/v1/series/{id}/volumes` 200 con la misma tarjeta que el listado de productos y forma `PagedModel`; `size=101` → 400 con `errors[field=size]`; UUID mal formado → 400 con `errors[field=id]`; `CatalogNotFoundException` del servicio → 404 idéntico para detalle y volúmenes; `GET /api/v1/series` con forma `PagedModel`. — `src/test/java/com/mangateca/api/catalog/web/SeriesControllerTests.java` — US4; FR-011, FR-013

### Implementation for User Story 4

- [ ] T042 [US4] [CODE] `SeriesRepository`: `@Query` con `EXISTS` (sobre `Product p` con `p.series = s` y `p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED`) para `Page<Series> findVisible(Pageable)` (con `countQuery` explícito) y `Optional<Series> findVisibleById(UUID id)`. — `catalog/domain/SeriesRepository.java` — FR-014; R3
- [ ] T043 [US4] [CODE] `ClassificationQueryService` (`@Service`, `@Transactional(readOnly = true)`) con `listSeries(PageParams)` (orden fijo `name ASC, id ASC`) y `seriesDetail(UUID)` (`orElseThrow(CatalogNotFoundException::new)`); y en `ProductQueryService` `volumes(UUID seriesId, Pageable)`: comprueba `findVisibleById` (si no, `CatalogNotFoundException`) y reutiliza el flujo en dos pasos con `ProductSearchCriteria(seriesId)` y orden fijo `volumeNumber ASC`. — `catalog/application/ClassificationQueryService.java`, `catalog/application/ProductQueryService.java` — FR-011, FR-013; depende de T042
- [ ] T044 [P] [US4] [CODE] `SeriesResponse(UUID id, String name, String description)` y `Series → SeriesResponse` en `CatalogMapper`. — `catalog/web/SeriesResponse.java`, `catalog/web/CatalogMapper.java` — contrato `SeriesResponse`
- [ ] T045 [US4] [CODE] `SeriesController` (`/api/v1/series`, `@SecurityRequirements` vacío): `GET` listado (`@Valid @ModelAttribute PageParams`), `GET /{id}` y `GET /{id}/volumes` (tarjeta `ProductSummaryResponse`, `PagedModel`). — `catalog/web/SeriesController.java` — FR-011, FR-012, FR-013; contrato `listSeries`, `getSeries`, `listSeriesVolumes`; depende de T043, T044

**Checkpoint**: T039–T041 en verde.

---

## Phase 8: User Story 5 - Clasificaciones para filtros (Priority: P2)

**Goal**: listados paginados y alfabéticos de géneros, autores y editoriales con al menos un producto publicado.

**Independent Test**: cargar clasificaciones con y sin productos publicados y consultar cada listado.

### Tests for User Story 5

- [ ] T046 [P] [US5] [TEST] `ClassificationRepositoryIT` (`@DataJpaTest` + Testcontainers): género con productos publicados y otro sin ninguno → el segundo no aparece; autor cuyos únicos productos están `UNPUBLISHED` no aparece; editorial sin productos publicados no aparece; un autor con varios productos publicados aparece **una sola vez**; el orden es alfabético por nombre con `id` como desempate; paginación con `size` pequeño (metadatos correctos); los cuatro listados (géneros, autores, editoriales, series) cumplen lo anterior. — `src/test/java/com/mangateca/api/catalog/domain/ClassificationRepositoryIT.java` — US5 escenarios 1–3; FR-013, FR-014
- [ ] T047 [P] [US5] [TEST] `ClassificationControllerTests` (`@WebMvcTest(ClassificationController.class)`): `GET /api/v1/genres`, `/authors` y `/publishers` devuelven `PagedModel` de `{id, name}`; `size=101` → 400 con `errors[field=size]`; `page=-1` → 400. — `src/test/java/com/mangateca/api/catalog/web/ClassificationControllerTests.java` — US5; FR-001, FR-013

### Implementation for User Story 5

- [ ] T048 [US5] [CODE] En `GenreRepository`, `AuthorRepository` y `PublisherRepository`, un `@Query` `findVisible(Pageable)` con `EXISTS` sobre `Product p` publicado (por `p.genres`, `p.authors` y `p.publisher` respectivamente) y `countQuery` explícito. — `catalog/domain/GenreRepository.java`, `AuthorRepository.java`, `PublisherRepository.java` — FR-014; R3
- [ ] T049 [US5] [CODE] En `ClassificationQueryService`: `listGenres`, `listAuthors`, `listPublishers` (orden fijo `name ASC, id ASC`, `@Transactional(readOnly = true)`); `ClassificationController` (`/api/v1`, `@SecurityRequirements` vacío) con `GET /genres`, `/authors` y `/publishers`, `@Valid @ModelAttribute PageParams`, devolviendo `PagedModel<NamedResponse>` (con `toNamed` de `CatalogMapper` para `Genre`, `Author` y `Publisher`). — `catalog/application/ClassificationQueryService.java`, `catalog/web/ClassificationController.java`, `catalog/web/CatalogMapper.java` — FR-013, FR-014; contrato `listGenres`, `listAuthors`, `listPublishers`; depende de T048

**Checkpoint**: T046 y T047 en verde.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T050 [TEST] Activar en `CatalogSecurityIT` los casos de series, géneros, autores y editoriales (quitar los `@Disabled` de T037) y comprobar que `GET` anónimo responde 200 en las **ocho** rutas y que `POST`/`PUT`/`DELETE` siguen devolviendo 401. — `src/test/java/com/mangateca/api/catalog/CatalogSecurityIT.java` — FR-021; SC-004
- [ ] T051 [TEST] Verificar de punta a punta el contrato: arrancar con perfil `dev`, contrastar `GET /v3/api-docs` con `contracts/openapi.yaml` (rutas, parámetros `page`/`size`/`sort`/filtros, códigos 200/400/404, forma `PagedModel`, `security: []` en las ocho operaciones) y anotar en el encabezado del YAML la fecha de verificación y las divergencias aceptadas, como se hizo en 002. — `specs/003-catalog-public-read/contracts/openapi.yaml` — Constitución DoD (API documentada)
- [ ] T052 [DOC] Ejecutar a mano `quickstart.md` §2 a §5 y §7 con datos insertados por `psql` (no versionados) y marcar en el documento «Verificado a mano <fecha> (T052)»; corregir pasos desfasados. — `specs/003-catalog-public-read/quickstart.md` — SC-001, SC-004..SC-006
- [ ] T053 [DOC] Verificar el rendimiento (SC-002) con el procedimiento de `quickstart.md` §6: 10.000 productos sintéticos (fuera de las migraciones), `EXPLAIN (ANALYZE, BUFFERS)` de orden, búsqueda con término de ≥ 3 y de 1–2 caracteres, filtros, y p95 de 200 peticiones < 500 ms. Si algún plan de ejecución no usa el índice esperado, corregir el índice o la consulta en una migración **nueva** (V4; V3 es inmutable una vez aplicada) y registrar el resultado en `research.md` (R1, R12). — `specs/003-catalog-public-read/quickstart.md`, `specs/003-catalog-public-read/research.md` — SC-002
- [ ] T054 [DOC] Revisión final de límites y calidad: `./mvnw verify` sin advertencias (`-Xlint:all,-processing`); `ModularityTests` verde; ninguna interfaz propia añadida; ninguna dato de ejemplo en `src/main/resources/db/migration`; actualizar `README.md` con una sección breve «Catálogo público» (rutas, paginación, extensiones `unaccent`/`pg_trgm` y el privilegio necesario, ver R2). — `README.md` — Constitución DoD; Principio VII

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup; bloquea todas las historias. Dentro de la fase: T002 → T003; T004/T005 → T006/T007 → T011 (con T008–T010) → T012; T002 y T011 → T017; T014 → T015; T013 y T016 independientes.
- **US1 (Phase 3)**, **US2 (Phase 4)**, **US3 (Phase 5)**: dependen de Foundational. US2 y US3 extienden `ProductQueryService`, `ProductController` y `CatalogMapper` de US1, por lo que se ejecutan después de US1 (no en paralelo con ella).
- **US6 (Phase 6)**: depende de US1; sus casos de series y clasificaciones se activan en T050.
- **US4 (Phase 7)** y **US5 (Phase 8)**: dependen de US1 (tarjeta, mapper, servicio de productos). Entre sí comparten `ClassificationQueryService` y `CatalogMapper` (T043/T049), así que van en orden US4 → US5.
- **Polish (Phase 9)**: depende de todas.

### Within Each User Story

- Tests antes que implementación (deben fallar o no compilar).
- Dominio → repositorios → servicio → DTO/mapper → controlador.

### Parallel Opportunities

- Foundational: T004 y T005 (tests de VO) en paralelo; T006 y T007; T008, T009 y T010.
- US1: T018 y T019 en paralelo; T020, T022 y T023 en paralelo.
- US2: T027 y T028 en paralelo.
- US3: T032 y T033 en paralelo; T035 en paralelo con T034.
- US4: T039, T040 y T041 en paralelo; US5: T046 y T047 en paralelo.

---

## Parallel Example: User Story 1

```text
# Tests de US1 (archivos distintos):
Task: "T018 ProductQueryServiceIT en test/catalog/application/ProductQueryServiceIT.java"
Task: "T019 ProductControllerTests en test/catalog/web/ProductControllerTests.java"

# Piezas independientes de US1:
Task: "T020 ProductSearchCriteria y ProductSpecifications en catalog/domain/"
Task: "T022 PageParams en catalog/web/PageParams.java"
Task: "T023 NamedResponse y ProductSummaryResponse en catalog/web/"
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3 + US6)

1. Setup y Foundational (V3, VOs, entidades, repositorios, seguridad).
2. US1: listado paginado → validar T018/T019.
3. US2: búsqueda y filtros → validar T027/T028.
4. US3: detalle y 404 indistinguible → validar T032/T033.
5. US6: `CatalogSecurityIT` para productos. **STOP y validar**: el frontend ya puede reemplazar el JSON de productos.

### Incremental Delivery

1. Foundation → US1 (MVP mínimo) → US2 → US3 → US6.
2. US4 (series) y US5 (clasificaciones, menús de filtros) en P2.
3. Polish: contrato, quickstart a mano, rendimiento con `EXPLAIN ANALYZE`, README.

---

## Notes

- [P] tasks = archivos distintos, sin dependencias entre sí.
- Cada tarea termina en un commit propio con su ID (Conventional Commits).
- Sin caché ni optimizaciones especulativas (Principio VII); sin interfaces ni fachada pública (R11).
- Todo hallazgo del checklist `checklists/api.md` que cambie la spec se resuelve con `/speckit-clarify` **antes** de T018.
