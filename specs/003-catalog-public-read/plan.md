# Implementation Plan: Catalog (Public Read)

**Branch**: `003-catalog-public-read` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-catalog-public-read/spec.md`

**Constitución de referencia**: v4.0.0

## Summary

Añadir el módulo Spring Modulith `catalog`, de solo lectura y acceso anónimo: listado de
productos con búsqueda, filtros, orden y paginación; detalle; series (datos y volúmenes por
separado); y listados de géneros, autores y editoriales que alimentan los menús de filtro.

Decisiones clave (detalle en [research.md](research.md)):

1. **Búsqueda con PostgreSQL nativo**: `unaccent` + `pg_trgm`. Una función envoltorio
   `IMMUTABLE` (`catalog_normalize`) permite índices GIN de trigramas sobre el título y el
   nombre de autor normalizados. Cada palabra del texto es un `LIKE '%palabra%'` sobre esa
   función; un producto coincide si **todas** las palabras aparecen en el título o en algún
   autor, o si el texto completo (sin guiones ni espacios) es exactamente su ISBN (R1, R2).
2. **Todos los filtros son subconsultas `EXISTS`** dentro de `Specification`s. Así no hay
   uniones que multipliquen filas: ni `DISTINCT` ni duplicados por autores o géneros (R3).
3. **Consulta en dos pasos**: (1) página de IDs con filtros, orden y `count`; (2) carga de esos
   productos con autores y serie mediante `@EntityGraph`, sin paginar, y reordenación según
   la página. Evita la paginación en memoria de Hibernate (R3).
4. **Paginación validada por nosotros, no por Spring**: ningún endpoint recibe `Pageable`;
   `page` y `size` se validan con Bean Validation y `size > 100` responde 400 (R4). La
   respuesta es el record `PageResponse<T>` (`content` + `page`), nunca `PageImpl` ni `PagedModel`.
5. **Orden por lista blanca** de claves de API (`title`, `price`, `publicationDate`,
   `createdAt`) con desempate estable por `id` (R5).
6. **Series en dos endpoints** (`/series/{id}` y `/series/{id}/volumes`) con 404 si la serie no
   tiene volúmenes publicados (R7).
7. **Sin API pública del módulo**: nada fuera de `catalog` lo consume todavía (Principio VII).
8. **Sin dependencias Maven nuevas y sin ADR**: `unaccent` y `pg_trgm` son extensiones
   `contrib` de PostgreSQL, no dependencias del proyecto.

## Technical Context

**Language/Version**: Java 25 (LTS), `--release 25`

**Primary Dependencies**: Spring Boot 4.1.2 (Spring Data JPA 4.1, Hibernate 7, Security 7),
Spring Modulith 2.1.2, MapStruct 1.6.3, springdoc-openapi 3.2.0. **Sin dependencias nuevas.**
Specifications con la API nativa de Spring Data JPA 4 (`Specification`, `JpaSpecificationExecutor`,
`findBy` fluido); sin generador de metamodelo (se usan nombres de atributo como cadenas).

**Storage**: PostgreSQL 18; migración `V3__create_catalog.sql` (V1 y V2 intactas). V1 **no**
crea extensiones (solo `event_publication`), así que V3 ejecuta
`CREATE EXTENSION IF NOT EXISTS unaccent` y `pg_trgm`.

**Testing**: JUnit 5 + AssertJ; `@DataJpaTest` + Testcontainers `@ServiceConnection`
(`unaccent` y `pg_trgm` reales), `@WebMvcTest` con `MockMvcTester`, `@SpringBootTest` para
seguridad, `ApplicationModules.verify()`. Mocks solo en la capa de aplicación dentro de
`@WebMvcTest` (Constitución VI).

**Target Platform**: servidor Linux / contenedor OCI.

**Project Type**: web-service (monolito modular).

**Performance Goals**: SC-002: p95 < 500 ms en listados con 10.000 productos publicados, con o
sin búsqueda y filtros. Verificación manual con `EXPLAIN ANALYZE` ([quickstart.md](quickstart.md)),
sin test automatizado.

**Constraints**: solo lectura; sin caché ni otras optimizaciones especulativas (VII); sin
limitación de tasa (fuera de alcance, confirmado en `/speckit-clarify`); moneda única CLP sin
decimales.

**Scale/Scope**: ~10.000 productos; 8 endpoints `GET`; 7 tablas; 1 módulo nuevo.

## Constitution Check

*GATE: pasar antes de Phase 0. Re-evaluado tras Phase 1. Constitución v4.0.0.*

| # | Regla | Cómo se cumple | Estado |
|---|-------|----------------|--------|
| I | Monolito modular; módulos verticales, sin capas globales | `catalog/{domain,application,web,config}` dentro del módulo; sin paquetes técnicos en raíz | ✅ |
| I | Límites verificados con Modulith | `ModularityTests` existente cubre `catalog`; ningún otro módulo importa tipos de `catalog` ni al revés | ✅ |
| I | Solo API pública o eventos entre módulos | `catalog` no expone paquete raíz público ni eventos: nadie lo consume aún (el carrito, feature 006, definirá la fachada) | ✅ |
| I | Terceros como puerto de dominio | N/A: sin integraciones externas | — |
| II | Invariantes en el dominio | `Money` (monto > 0, escala 0) e `Isbn` (formato y dígito verificador) en constructores compactos. Serie/volumen y ≥1 autor/género se protegen con CHECK y FK en V3; la fábrica de `Product` llega con la feature 004, que es la que crea productos (R14) | ✅ |
| II | Dinero en `BigDecimal`/`Money` | `Money` record sobre `BigDecimal` con escala 0; columna `numeric(12,0)` | ✅ |
| II | Identificadores de negocio como VO `record` | `Isbn` | ✅ |
| III | Java 25, Boot 4.1.x, solo APIs estables | Specifications, `findBy` fluido, `EXISTS`; ninguna API preview ni deprecada | ✅ |
| III | No eliminar dependencias base; ADR por dependencia nueva | Ninguna dependencia nueva ni eliminada; no hay ADR | ✅ |
| III | DTO, comandos, eventos y VO como `record` | Todos los DTO (incluida la respuesta paginada `PageResponse<T>`), `ProductSearchCriteria`, `Money`, `Isbn`, `PageParams`, `ProductSearchRequest` | ✅ |
| III | Lombok solo en entidades; sin `@Data` | `@Getter` únicamente en las cinco entidades | ✅ |
| IV | PostgreSQL único; Flyway versionado e inmutable | `V3__create_catalog.sql` nueva; V1 y V2 intactas | ✅ |
| IV | `ddl-auto=validate`, OSIV off | Sin cambios; `SchemaDriftIT` y el smoke test validan V3 contra las entidades | ✅ |
| IV | Sin N+1 | Listado en dos pasos con `@EntityGraph`; detalle con grafo; un test cuenta las sentencias SQL (R3) | ✅ |
| IV | `@Transactional` en aplicación, `readOnly` en consultas | `ProductQueryService` y `ClassificationQueryService` con `@Transactional(readOnly = true)` | ✅ |
| IV | Entidades no expuestas; DTO + MapStruct | `CatalogMapper` (MapStruct) entidad → record; las entidades no salen de `application` hacia JSON | ✅ |
| IV | `/api/v1`, sustantivos en plural | `/products`, `/series`, `/genres`, `/authors`, `/publishers`, `/series/{id}/volumes` | ✅ |
| IV | Errores en un único `@RestControllerAdvice` con `ProblemDetail` | Solo el `GlobalExceptionHandler` de 001, con dos ajustes aditivos (ver «Impacto en 001 y 002»); el 404 es un `ErrorResponseException` | ✅ |
| IV | Todo listado paginado con tamaño máximo | Los 6 listados: defecto 20, máximo 100, rechazo con 400 | ✅ |
| IV | JSpecify; `Optional` en resultados únicos | `@NullMarked` en cada `package-info.java`; repositorios con `Optional<T>`; colecciones nunca `null` | ✅ |
| V | Autenticación JWT nativa | Sin cambios; los endpoints de `catalog` no leen la identidad | ✅ |
| V | `SecurityFilterChain` deny-by-default | La cadena de plataforma no cambia. `catalog` añade una cadena `@Order(2)` limitada a `GET`/`HEAD`/`OPTIONS` sobre las rutas exactas; `POST`/`PUT`/`DELETE` caen en la cadena por defecto y se rechazan | ✅ |
| V | Acceso anónimo limitado a lectura de catálogo y a los 4 `POST /auth/*` | Solo `GET`/`HEAD` sobre las 8 rutas de lectura del catálogo | ✅ |
| V | Anti IDOR; identidad del token | N/A: el catálogo es idéntico para todos los roles; no hay recursos por usuario | — |
| VI | Solo tests vitales; tests obligatorios 1–4 | Ver sección Testing: invariantes de `Money`/`Isbn`, seguridad, `verify()`, y flujo feliz de los endpoints | ✅ |
| VI | Testcontainers vía `@ServiceConnection`; sin H2 | `TestcontainersConfiguration` de 001; la búsqueda depende de `unaccent`/`pg_trgm` reales | ✅ |
| VI | Mocks: repositorios, `Clock` y capa de aplicación en `@WebMvcTest`; no entidades, records ni VO | Exactamente esos | ✅ |
| VI | Cero advertencias (`-Xlint:all,-processing`) | Toolchain de 001 sin cambios | ✅ |
| VII | Clases concretas; sin interfaces especulativas | Sin interfaces propias salvo las de framework (Spring Data, MapStruct) | ✅ |
| VII | Trade-offs documentados | Tabla de trade-offs | ✅ |
| Hig. | DTO de entrada validados con `@Valid` | `PageParams` y `ProductSearchRequest` con `@Valid` | ✅ |
| Hig. | CORS explícito; Actuator `health`/`info`; sin PII en logs | Sin cambios; el catálogo no registra PII | ✅ |
| DoD | `spec.md`, `plan.md` y `tasks.md` aprobados; OpenAPI | `tasks.md` generado (`/speckit-tasks`) y analizado (`/speckit-analyze`); OpenAPI de springdoc contrastado con [contracts/openapi.yaml](contracts/openapi.yaml) | ⏳ |

**Resultado pre-Phase 0**: PASS, sin violaciones.

**Resultado post-Phase 1** (re-evaluado tras data-model y contracts): PASS. El diseño no añade
interfaces, dependencias, caché ni rutas anónimas fuera de `GET`/`HEAD` sobre el catálogo. Tras
`/speckit-analyze`: FR-019 queda asignado a la feature 004 en la spec (Assumptions), el tope de
`page` está en FR-004 y la petición de tests por escenario está en la spec (VI).

## Evaluación de trade-offs (Principio VII)

| Elemento | Problema real que resuelve hoy | Alternativa simple descartada | Costo aceptado |
|----------|--------------------------------|-------------------------------|----------------|
| Función SQL `catalog_normalize` (V3) | `unaccent` no es `IMMUTABLE`, por lo que no se puede indexar; sin ella no hay índice de trigramas sobre texto sin tildes | `unaccent()` directo en la consulta: recorrido secuencial en cada búsqueda | 1 función SQL; si cambia el diccionario de `unaccent` hay que reindexar (R2) |
| Consulta en dos pasos (IDs + carga) | Hibernate pagina en memoria si se mezclan `fetch join` de colecciones y paginación | `fetch join` + `Pageable` en una consulta: carga todo el resultado en memoria | 2 consultas más 1 `count` por listado; reordenar la lista cargada |
| `ProductSpecifications` (clase estática con predicados `EXISTS`) | Filtros combinables y opcionales sin multiplicar métodos de repositorio | Un método derivado por combinación (2⁷ posibles) o `@Query` con `:p IS NULL OR …` | Atributos por nombre (cadenas); un error se descubre en test, no al compilar |
| `ProductSearchCriteria` (record) | Aislar `application`/`domain` de los tipos HTTP (`ProductSearchRequest`) | Pasar el record web al servicio: el dominio dependería de `web` | 1 record y 1 conversión |
| `PageParams` + `ProductSearchRequest` (records con Bean Validation) | `Pageable` recortaría `size > 100` en silencio en vez de rechazarlo | `spring.data.web.pageable.max-page-size`: ajusta el valor, no devuelve 400 | 2 records; construir el `PageRequest` a mano |
| `@ValidPriceRange` (anotación de clase + validador) | `minPrice > maxPrice` debe reportar el parámetro `minPrice` | Comprobarlo en el controlador y lanzar una excepción: segunda ruta de error | 1 anotación + 1 validador (mismo patrón que `@ValidPassword`) |
| `Money` e `Isbn` + `AttributeConverter` | Invariantes en el constructor y una sola normalización de ISBN para búsqueda y almacenamiento | `BigDecimal` y `String` sueltos: invariantes repartidas | 2 records, 2 convertidores (~10 líneas cada uno) |
| `ClassificationQueryService` separado de `ProductQueryService` | Cambian por razones distintas: tarjetas con filtros vs. listados alfabéticos con `EXISTS` | Un único servicio de ~250 líneas | 1 dependencia entre dos clases concretas (volúmenes → comprobar serie visible) |
| Cadena `SecurityFilterChain` propia en `catalog` | Rutas públicas sin que `platform` conozca `/api/v1/products` | `permitAll` en `platform.SecurityConfiguration`: acopla `platform` a `catalog` | Una tercera cadena que mantener (mismo patrón que `identity`) |
| Dos endpoints de serie | Paginar los volúmenes de forma estándar y reutilizar la tarjeta de producto | Lista paginada embebida en el detalle de la serie (R7) | Dos llamadas del cliente para pintar una página de serie |
| Record `PageResponse<T>` (`content` + `PageMetadata`) en `catalog.web` | Respuesta paginada estable para los 6 listados como DTO `record` (Principio III), sin serializar `PageImpl` ni depender de la forma JSON de una clase de Spring Data | `PagedModel` de Spring Data: no es un `record` y ata el contrato a la serialización de la librería | 1 record (con un record anidado) y una fábrica `from(Page)`; vive en `catalog` hasta que otro módulo lo necesite (VII) |

## Project Structure

### Documentation (this feature)

```text
specs/003-catalog-public-read/
├── plan.md              # Este archivo
├── research.md          # Decisiones R1–R16
├── data-model.md        # V3, entidades, VO, índices
├── quickstart.md        # Validación automática y manual (EXPLAIN ANALYZE)
├── contracts/
│   └── openapi.yaml     # Contrato HTTP de los 8 endpoints
├── checklists/
└── tasks.md             # Lo genera /speckit-tasks (no se crea aquí)
```

### Source Code (repository root)

```text
src/main/java/com/mangateca/api/catalog/
├── package-info.java                  # @NullMarked @ApplicationModule(displayName = "Catalog")
├── domain/
│   ├── Product.java                   # entidad (solo lectura en 003)
│   ├── ProductType.java               # enum MANGA, LIBRO, COMIC
│   ├── PublicationStatus.java         # enum PUBLISHED, UNPUBLISHED
│   ├── Author.java  Publisher.java  Genre.java  Series.java
│   ├── Money.java  Isbn.java          # VO records con invariantes
│   ├── MoneyConverter.java  IsbnConverter.java
│   ├── ProductId.java                 # proyección de interfaz (solo id) para el paso 1
│   ├── ProductSearchCriteria.java     # record de criterios
│   ├── ProductSpecifications.java     # predicados EXISTS combinables
│   ├── ProductRepository.java         # JpaRepository + JpaSpecificationExecutor + @EntityGraph
│   ├── AuthorRepository.java  GenreRepository.java
│   ├── PublisherRepository.java  SeriesRepository.java
│   └── package-info.java
├── application/
│   ├── ProductQueryService.java       # listado (2 pasos), detalle, volúmenes
│   ├── ClassificationQueryService.java# géneros, autores, editoriales, series
│   ├── CatalogNotFoundException.java  # ErrorResponseException 404
│   └── package-info.java
├── web/
│   ├── ProductController.java         # GET /api/v1/products, /{id}
│   ├── SeriesController.java          # GET /api/v1/series, /{id}, /{id}/volumes
│   ├── ClassificationController.java  # GET /api/v1/genres, /authors, /publishers
│   ├── PageParams.java  ProductSearchRequest.java
│   ├── PageResponse.java              # record de respuesta paginada (content + page)
│   ├── ValidPriceRange.java  ValidPriceRangeValidator.java
│   ├── CatalogMapper.java             # MapStruct
│   ├── ProductSummaryResponse.java  ProductDetailResponse.java
│   ├── NamedResponse.java  SeriesResponse.java
│   └── package-info.java
└── config/
    ├── CatalogSecurityConfiguration.java  # cadena @Order(2): GET/HEAD/OPTIONS públicos
    └── package-info.java

src/main/resources/db/migration/V3__create_catalog.sql

src/test/java/com/mangateca/api/catalog/
├── domain/   MoneyTests, IsbnTests, ProductSearchIT, ClassificationRepositoryIT
├── application/ ProductQueryServiceIT, ClassificationQueryServiceIT
├── web/      ProductControllerTests, SeriesControllerTests, ClassificationControllerTests
├── support/  CatalogTestData (inserta por JDBC), CatalogFixtures (entidades en memoria)
└── CatalogSecurityIT
src/test/resources/catalog/*.sql                                       # datos de prueba
```

**Structure Decision**: un módulo vertical `catalog` con los mismos cuatro subpaquetes que
`identity`. Todo es interno: el paquete raíz solo lleva `package-info.java`, de modo que Modulith
lo trata como módulo sin API pública. No se crean fachadas ni interfaces (Principio VII).

## Testing

### Tests obligatorios (Constitución VI)

| # | Obligatorio | Test |
|---|-------------|------|
| 1 | Arquitectura y arranque | `ModularityTests` (existente, `verify()` incluye `catalog`); `ApiApplicationTests` con V3 y `ddl-auto=validate` |
| 2 | Invariantes de dominio, sin Spring ni BD | `MoneyTests` (positivo, escala 0, rechaza decimales, normaliza `1500` y `1.5E3`), `IsbnTests` (normaliza, ISBN-10 con `X`, ISBN-13, dígito verificador, longitud, caracteres) |
| 3 | Seguridad crítica | `CatalogSecurityIT`: `GET` anónimo permitido en las 8 rutas; `POST`, `PUT` y `DELETE` anónimos denegados (401); un token inválido en un `GET` público no rompe la respuesta |
| 4 | Flujo feliz de cada endpoint crítico | Cubierto por los escenarios de la spec (tabla siguiente) |

### Escenarios de aceptación de la spec

| Escenario (spec) | Test | Nivel |
|------------------|------|-------|
| US1 listado sin parámetros: 20 publicados, novedades primero, metadatos | `ProductQueryServiceIT` | `@DataJpaTest`-style + servicio |
| US1 tamaño 101 → 400 con parámetro | `ProductControllerTests` | `@WebMvcTest` |
| US1 página fuera de rango → vacía, sin error | `ProductQueryServiceIT` | servicio |
| US1 ordenamiento explícito (4 criterios) | `ProductQueryServiceIT` (parametrizado, orden real en BD) + `ProductControllerTests` (traducción clave de API → `Sort`, sentido por defecto y desempate) | servicio + `@WebMvcTest` |
| US1 tarjeta con serie / sin serie | `ProductControllerTests` (JSON de tarjeta) | `@WebMvcTest` |
| US2 sin mayúsculas ni tildes, autor, ISBN (con y sin guiones), varias palabras, parcial | `ProductSearchIT` | `@DataJpaTest` + Testcontainers |
| US2 filtros combinados (tipo + género + precio); búsqueda + filtros + orden + 2.ª página | `ProductSearchIT` | `@DataJpaTest` + Testcontainers |
| US2 parámetros inválidos (min > max, tipo inexistente, orden no permitido) | `ProductControllerTests` | `@WebMvcTest` |
| US2 sin coincidencias → página vacía | `ProductSearchIT` | `@DataJpaTest` |
| US3 detalle publicado, con serie | `ProductControllerTests` | `@WebMvcTest` |
| US3 no publicado ≡ inexistente (cuerpos idénticos); UUID mal formado → 400 | `ProductControllerTests` + `ProductQueryServiceIT` (el no publicado lanza la misma excepción) | mixto |
| US4 serie con volumen 2 oculto → 1 y 3 en orden | `ProductQueryServiceIT` | servicio |
| US4 serie inexistente o sin publicados → 404 (detalle y volúmenes) | `SeriesControllerTests` + `ProductQueryServiceIT` + `ClassificationQueryServiceIT` | mixto |
| US4 llegar a la serie desde el volumen | `ProductControllerTests` (el detalle trae `series.id`) | `@WebMvcTest` |
| US5 género sin publicados no aparece; autor con solo no publicados no aparece; orden alfabético; 4 clasificaciones | `ClassificationRepositoryIT` | `@DataJpaTest` + Testcontainers |
| US6 acceso sin token; mismo catálogo para CUSTOMER y ADMIN | `CatalogSecurityIT` | `@SpringBootTest` |
| FR-005/FR-007/FR-020 dentro de los escenarios de US2: varias palabras en cualquier orden, parcial, ISBN con y sin guiones, producto con varios autores sin duplicar, no publicados nunca visibles | `ProductSearchIT` | `@DataJpaTest` |
| Edge: paginación inválida (`size=0`, `page=-1`) e identificador de filtro mal formado (`genreId=abc`) | `ProductControllerTests` (filas extra del parametrizado de US2 «Parámetros inválidos») | `@WebMvcTest` |
| Edge (añadidos tras la revisión de la fase US2): `%`, `_` y `\` del texto se tratan como literales en el `LIKE` (FR-005, R1); texto con forma de ISBN y dígito verificador inválido → página vacía, sin error (R1) | `ProductSearchIT` | `@DataJpaTest` + Testcontainers |
| Sin N+1 (IV) | `ProductQueryServiceIT`: estadísticas de Hibernate, número de sentencias constante para páginas de 1 y de 20 | servicio |
| Restricciones de V3 (FR-015..FR-018) | Sin test propio (opcional, VI): las protege V3 y las invariantes de `Money`/`Isbn` tienen sus tests unitarios obligatorios. Excluidos también los casos borde sin escenario (términos de 1–2 caracteres, texto en blanco) | — |

### Mocks y dobles permitidos

Solo la capa de aplicación en `@WebMvcTest`. Los tests de repositorio y de servicio usan la BD
real de Testcontainers. Las entidades, `Money` e `Isbn` nunca se mockean.

### Datos de prueba

Solo en `src/test`. **Ningún dato de ejemplo en las migraciones** (los datos de desarrollo llegan
con la feature 012).

- **Tests con BD**: scripts SQL (`src/test/resources/catalog/*.sql`) y el builder
  `CatalogTestData`, que inserta con `JdbcClient`.
- **Tests `@WebMvcTest`**: el servicio mockeado devuelve entidades reales, no mocks (VI). Como en
  003 las entidades no tienen fábrica pública (R14), `CatalogFixtures` las construye en memoria:
  instancia por el constructor `protected` y asigna los campos con `ReflectionTestUtils`. Es
  código solo de test y no añade setters ni constructores a producción.

### Fuera de los tests automatizados

El criterio de rendimiento (p95 < 500 ms con 10.000 productos) se verifica a mano con
`EXPLAIN ANALYZE` ([quickstart.md](quickstart.md)).

## Impacto en 001 y 002

- **`platform.web.GlobalExceptionHandler`** (cambio aditivo, necesario para FR-008 y US3):
  1. `handleTypeMismatch`: un `MethodArgumentTypeMismatchException` (UUID de ruta mal formado)
     responde 400 con la propiedad `errors` identificando el parámetro, sin el valor recibido.
  2. En `handleMethodArgumentNotValid`, un `FieldError` que sea fallo de enlace
     (`isBindingFailure()`: `type=XYZ`, `genreId=abc`, `page=abc`) usa un texto fijo en vez del
     mensaje de conversión de Spring, que incluye el valor y el tipo Java.
  Ambos casos se prueban a través de los escenarios que los exigen (`ProductControllerTests`:
  `type=XYZ`, `genreId=abc` y UUID de ruta mal formado), sin tests propios en
  `GlobalExceptionHandlerTests` (serían opcionales, VI).
  Aclaración: la regla de no repetir el valor rechazado (FR-022 / SC-005) se aplica a `detail`,
  `errors` y demás propiedades del cuerpo; `instance` (URI de la petición, presente en todo
  `ProblemDetail`) queda fuera de la regla porque puede contener el id del path.
- **OpenAPI**: los controladores de `catalog` se anotan sin requisito de seguridad
  (`@SecurityRequirements`), porque `OpenApiConfiguration` aplica `bearerAuth` globalmente.
- **Migraciones**: V1 y V2 no se tocan.
- **`spring.data.web.pageable.*`**: no se configura (R4).
- **`FlywayMigrationIT`**: su aserción de la lista exacta de tablas se amplía con las siete tablas
  de V3.
- **`README.md`**: sección breve «Catálogo público» (rutas, paginación y extensiones
  `unaccent`/`pg_trgm` con el privilegio que requieren, R2).

## Orden de implementación (guía para `/speckit-tasks`)

1. V3 (`CREATE EXTENSION`, función, tablas, índices) + entidades + convertidores + `Money`/`Isbn`
   con sus tests unitarios; `SchemaDriftIT`/arranque en verde.
2. Repositorios y `ProductSpecifications`; `ProductSearchIT` con datos de prueba.
3. `ProductQueryService` (dos pasos, detalle, volúmenes) y `ClassificationQueryService`.
4. Ajustes a `GlobalExceptionHandler`; `PageParams`, `ProductSearchRequest`, validador.
5. DTO, `CatalogMapper`, controladores, `CatalogSecurityConfiguration`.
6. `CatalogSecurityIT`, tests web, contraste de OpenAPI y verificación manual del quickstart.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| `FluentQuery.as(ProductId.class)` podría cargar la entidad completa en vez de solo el `id` | Verificar el SQL generado en `ProductQueryServiceIT`; alternativa: `project("id")` o una consulta `select p.id` (R3) |
| `CREATE EXTENSION` exige privilegios en el entorno de despliegue | `unaccent` y `pg_trgm` son extensiones *trusted* desde PostgreSQL 13: basta ser dueño de la BD. Documentado en el quickstart |
| El planificador no usa el índice GIN si el patrón llega como parámetro | `EXPLAIN ANALYZE` en el quickstart con el SQL real; ver R1 |
| Un `page` enorme desborda `int` en `OFFSET` | `page` limitado a 1.000.000 (R13) |
| Mapear fuera de la transacción provoca `LazyInitializationException` | El grafo del paso 2 trae todo lo que el mapper toca; `ProductQueryServiceIT` ejecuta el mapper sobre los resultados |
| Orden por `title` depende de la intercalación (*collation*) de la BD | Los tests usan títulos ASCII simples; documentado en R16 |

## Complexity Tracking

Sin violaciones de la Constitución. Ninguna entrada.
