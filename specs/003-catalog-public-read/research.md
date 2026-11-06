# Research: Catalog (Public Read)

**Feature**: `003-catalog-public-read` | **Fecha**: 2026-11-05 | **Constitución**: v4.0.0

Fuentes: documentación de Spring Data JPA 4.1 vía Context7 (API de `Specification`,
`JpaSpecificationExecutor.findBy` fluido, `Specification.unrestricted()`), el código de las
features 001 y 002 y el comportamiento documentado de PostgreSQL y Hibernate. Lo que la
documentación consultada no cierra se marca **«verificar en implementación»**.

## R1. Estrategia de búsqueda: `unaccent` + `pg_trgm` con `LIKE`

- **Decision**: cada palabra del texto se compara con `LIKE '%palabra%'` contra
  `catalog_normalize(columna)`, donde `catalog_normalize(t) = lower(unaccent(t))` (R2). Hay
  índices GIN de trigramas (`gin_trgm_ops`) sobre `catalog_normalize(products.title)` y
  `catalog_normalize(authors.name)`.
  - El texto se recorta, se divide por espacios en blanco y se descartan las palabras vacías.
    Texto vacío o solo espacios = sin búsqueda.
  - Un producto coincide si **todas** las palabras coinciden, cada una en el título o en el
    nombre de algún autor (`EXISTS` sobre `product_authors` + `authors`).
  - **O** si el texto completo, sin guiones ni espacios y en mayúsculas, es exactamente el ISBN
    del producto (`products.isbn = :texto`). Solo se evalúa si tiene forma de ISBN (9 dígitos +
    dígito o `X`, o 13 dígitos); no se valida el dígito verificador al buscar: un ISBN mal
    tecleado simplemente no coincide.
  - Los caracteres `%`, `_` y `\` del texto se escapan (`LIKE … ESCAPE '\'`).
  - La función se aplica también al patrón, de modo que ambos lados se normalizan igual.
- **Rationale**: es la única opción que da coincidencia **parcial** (`drag` → `Dragón`), sin
  tildes ni mayúsculas, **con índice**, y con semántica «todas las palabras» sin construir
  expresiones especiales. Es SQL estándar y se compone con `CriteriaBuilder` (`cb.function`,
  `cb.like`) sin código nativo. Para 10.000 productos sobra.
- **Términos de menos de 3 caracteres**: un índice de trigramas no puede ayudar con ellos (un
  trigrama necesita 3 caracteres), así que PostgreSQL recorre el índice completo o la tabla.
  - **No se rechazan ni se ignoran**: los Edge Cases de la spec piden que cada palabra coincida
    y la búsqueda sea parcial, y rechazarlos daría 400 a una búsqueda legítima («x»).
  - Se evalúan igualmente con `LIKE`. Si la consulta tiene al menos un término de 3 o más
    caracteres, el índice reduce los candidatos y los términos cortos solo se comprueban sobre
    ese subconjunto. Si **todos** los términos son cortos, la consulta recorre los productos
    publicados: con 10.000 filas es del orden de decenas de milisegundos y queda dentro de
    SC-002. Se mide en el quickstart (paso 5.3).
  - No se añade ningún mecanismo extra (índice de prefijo, tabla auxiliar): sería una
    optimización especulativa (Principio VII).
- **Alternatives considered**:
  - *Full-text search (`tsvector` + `tsquery`)*: búsqueda por **lexemas**, no por fragmentos.
    `drag:*` admite prefijos, pero no fragmentos intermedios (`ragon`), exige elegir un
    diccionario de idioma (los títulos mezclan español, japonés romanizado e inglés, y el
    *stemming* altera nombres propios), necesita una columna o índice `tsvector` mantenido y
    devuelve coincidencias que el usuario no escribió. Es la mejor opción para texto largo con
    relevancia; aquí se busca en títulos y nombres cortos.
  - *`LIKE`/`ILIKE` sin índice*: cumple la semántica, pero recorre la tabla en cada búsqueda y
    `ILIKE` no ignora tildes. Con 10.000 filas funcionaría; falla el criterio de crecer «a miles
    de productos» sin degradarse.
  - *`ILIKE` + índice de trigramas sin `unaccent`*: no encuentra `dragon` en `Dragón`.
  - *`similarity()` / operador `%` de `pg_trgm`*: difusa y ordenada por similitud; no garantiza
    que cada palabra aparezca, contradice «todas deben coincidir».
- **Verificar en implementación**: que Hibernate 7 genere `catalog_normalize(p.title) like
  catalog_normalize(?) escape '\'` y que PostgreSQL use `Bitmap Index Scan` sobre el índice GIN
  con el patrón como parámetro (quickstart, paso 5).

## R2. Función envoltorio inmutable de `unaccent`

- **Decision**: V3 crea las extensiones con `CREATE EXTENSION IF NOT EXISTS` (V1 no las crea) y
  una función SQL `catalog_normalize(text)`, `IMMUTABLE`, `PARALLEL SAFE`, `STRICT`, que llama a
  `public.unaccent('public.unaccent'::regdictionary, $1)` y aplica `lower`. Todo con nombres
  calificados por esquema.
- **Rationale**: PostgreSQL solo indexa expresiones `IMMUTABLE`, y `unaccent(text)` está
  declarada `STABLE` porque depende del diccionario. Declarar el envoltorio `IMMUTABLE` es el
  patrón habitual y seguro mientras el diccionario no cambie. Pasar el diccionario como
  `regdictionary` evita depender del `search_path` de la sesión.
- **Alternatives considered**:
  - *Columnas generadas con el texto normalizado*: duplican datos y exigen mantener la
    normalización en dos sitios; la función + índice funcional basta.
  - *Normalizar en Java antes de guardar*: la administración (feature 004) tendría que
    recordar hacerlo; no cubre búsquedas sobre datos cargados por SQL.
- **Costos y riesgos**: si se actualiza el archivo de reglas de `unaccent`, los índices
  quedan obsoletos y hay que reindexar (`REINDEX`). `CREATE EXTENSION` requiere ser dueño de la
  base de datos: `unaccent` y `pg_trgm` son extensiones *trusted* desde PostgreSQL 13, por lo
  que no se necesita superusuario. La imagen `postgres:18` de Testcontainers y de
  `compose.yaml` incluye `contrib`.

## R3. Consultas: `Specification`, `EXISTS` y carga en dos pasos

- **Decision**:
  1. **Filtros como `EXISTS`**: `ProductSpecifications` expone un predicado por filtro. Los de
     autor, género y búsqueda por autor son subconsultas `EXISTS`; tipo, editorial, serie,
     precio y estado son comparaciones directas sobre `products`. No hay `join` en la consulta
     principal, así que un producto con varios autores o géneros no se duplica y no hace falta
     `DISTINCT` (FR-007). `Specification.unrestricted()` es el predicado neutro para filtros
     ausentes. Solo se usan las APIs nativas de Spring Data JPA 4.
  2. **Paso 1 — página de IDs**: `repository.findBy(spec, q -> q.as(ProductId.class)
     .page(pageable))`. `ProductId` es una proyección de interfaz con `getId()`. Spring Data
     emite una consulta con orden y `OFFSET/LIMIT` y otra `count`. **Verificar en
     implementación** que la sentencia seleccione solo `id`; si cargara la entidad, usar
     `q.project("id")`. Cualquiera de las dos mantiene el paso 1 sin colecciones.
  3. **Paso 2 — carga**: `ProductRepository.findAllByIdIn(ids)` con
     `@EntityGraph(attributePaths = {"authors", "series"})`, sin paginar. Hibernate hace un
     `left join fetch` y devuelve una fila por (producto, autor); como se carga una lista
     acotada (≤ 100 IDs), no hay paginación que se pueda aplicar en memoria. El servicio
     reordena el resultado según el orden de la página de IDs (índice por `id`) y construye
     `new PageImpl<>(ordenados, pageable, page.getTotalElements())`.
  4. **Detalle**: `findWithDetailsByIdAndStatus(id, PUBLISHED)` con `@EntityGraph` sobre autores,
     géneros, editorial y serie. Dos colecciones `Set` en el mismo `fetch join` generan un
     producto cartesiano (autores × géneros), de pocas filas por producto; se acepta frente a
     una segunda consulta. Las colecciones son `Set`, no `List`, para evitar
     `MultipleBagFetchException`.
- **Trade-off (Principio VII)**:
  - *Problema real que resuelve*: Hibernate, ante `fetch join` de una colección y
    `setFirstResult/setMaxResults`, emite `HHH90003004` y **pagina en memoria**: trae todas las
    filas del catálogo y recorta en la JVM. Con 10.000 productos rompería SC-002.
  - *Alternativa simple descartada*: `@EntityGraph` directamente sobre
    `findAll(spec, pageable)`. Es una sola llamada, pero activa precisamente esa paginación en
    memoria.
  - *Alternativa descartada 2*: dejar autores y serie `LAZY` sin grafo. Produce N+1 (1 consulta
    de página + hasta 20 de autores + hasta 20 de series) y la Constitución IV lo prohíbe.
  - *Costo aceptado*: tres sentencias por listado (IDs, `count`, carga) en lugar de una, y una
    reordenación O(n) sobre ≤ 100 elementos. `Specification` con `count` evita `DISTINCT` gracias
    a `EXISTS`.
- **Alternatives considered**: `@BatchSize` sobre las colecciones (menos explícito, N+1
  mitigado, no eliminado); `@Query` con proyección a DTO y `group_concat` (rompe la
  separación entidad → DTO con MapStruct).
- **Test**: `ProductQueryServiceIT` activa `hibernate.generate_statistics` y comprueba que el
  número de sentencias es el mismo para una página de 1 y de 20 productos (sin N+1).

## R4. Paginación: validación propia y `PagedModel`

- **Decision**:
  - Ningún método de controlador recibe `Pageable`. El argument resolver de Spring Data **ajusta
    en silencio** `size` a `max-page-size` en lugar de rechazarlo, lo que contradice FR-001.
  - `PageParams` (record, `@ParameterObject` para springdoc) enlaza `page` (`@Min(0)`,
    `@Max(1_000_000)`, defecto 0) y `size` (`@Min(1)`, `@Max(100)`, defecto 20). Los valores
    por defecto se aplican en el constructor compacto (los campos son `Integer` que llegan
    `null` si se omiten). Los límites viven en un único sitio: las constantes
    `PageParams.MAX_SIZE = 100` y `PageParams.MAX_PAGE = 1_000_000`, que `ProductSearchRequest`
    reutiliza en sus propias anotaciones `@Max` (son constantes de compilación).
  - El controlador construye `PageRequest.of(page, size, sort)`.
  - La respuesta es `PagedModel<T>` (`org.springframework.data.web.PagedModel`) creado
    explícitamente con `new PagedModel<>(page.map(mapper::…))`: JSON `{ "content": […],
    "page": { "size", "number", "totalElements", "totalPages" } }`. **No se serializa
    `PageImpl`** y no se toca `@EnableSpringDataWebSupport`.
- **Rationale**: crear el `PagedModel` a mano es una línea, es explícito y no cambia la
  serialización de ningún otro módulo. Un record `PageResponse<T>` propio sería equivalente
  pero extra. **Verificar en implementación** la firma del constructor en Spring Data 4.1.
- **Alternatives considered**: `@EnableSpringDataWebSupport(pageSerializationMode =
  VIA_DTO)` (global: afecta a futuras features y a springdoc de forma implícita);
  `spring.data.web.pageable.max-page-size` (descartada por lo ya dicho).
- Las violaciones llegan como `HandlerMethodValidationException` o `MethodArgumentNotValidException`;
  el `GlobalExceptionHandler` ya las traduce a `errors: [{field, message}]`.

## R5. Orden de productos: lista blanca y desempate estable

- **Decision**: `ProductSearchRequest.sort` es un único `String` (`clave[,asc|desc]`) validado
  con `@Pattern("^(title|price|publicationDate|createdAt)(,(asc|desc))?$")`. Un mapa explícito
  `clave de API → propiedad de la entidad` produce el `Sort`. Hoy los nombres coinciden, pero el
  mapa evita acoplar el contrato a los atributos internos. Por defecto: `createdAt,desc`. Una
  clave sin sentido (`sort=price`) es ascendente (FR-002).
  Cualquier otra clave → 400 con `field = sort`.
- **Desempate**: se añade siempre `id` **en el mismo sentido** que el criterio principal. Un
  orden total hace estable la paginación (Edge Cases) y, al coincidir los sentidos, un índice
  `(columna, id)` sirve tanto en ascendente como en descendente (R12).
- **Por qué un único `String` y no `List<String>`**: Spring convierte `sort=price,desc` en una
  lista de dos elementos al enlazar `List<String>`. Se admite un solo criterio, que es lo que
  piden la spec y el contrato; varios criterios serían diseño especulativo.
- **Alternatives considered**: `Sort` de Spring Data con `Pageable` (acepta cualquier propiedad:
  expondría atributos internos y permitiría `sort=synopsis`).
- **Clasificaciones**: orden fijo `name ASC, id ASC`. **Volúmenes**: fijo `volumeNumber ASC`
  (único dentro de la serie, no necesita desempate).

## R6. `Money` e `Isbn`: persistencia y búsqueda

- **Decision**:
  - `Money` es `record Money(BigDecimal amount)` con `Comparable<Money>`; su constructor
    compacto exige `amount != null`, `signum() > 0` y normaliza a escala 0 con
    `setScale(0, RoundingMode.UNNECESSARY)` (un decimal real, p. ej. `10.5`, falla con
    `IllegalArgumentException`; `1.5E3` se normaliza a `1500`). La moneda CLP es la constante
    `Money.CURRENCY`; no es un campo (una sola moneda, VII). Fábrica `Money.clp(long)`.
  - `Isbn` es `record Isbn(String value)`: el constructor compacto quita guiones y espacios,
    pasa a mayúsculas, exige 10 caracteres (9 dígitos + dígito o `X`) o 13 dígitos y valida el
    dígito verificador (ISBN-10: Σ dígito·(10−i) ≡ 0 mod 11; ISBN-13: Σ dígito·(1|3) ≡ 0 mod 10).
    Un método estático `normalize(String)` aplica solo la limpieza, para la búsqueda.
  - Ambos se persisten con `AttributeConverter` explícito (`@Convert` en el campo, sin
    `autoApply`): `Money` ↔ `numeric(12,0)`, `Isbn` ↔ `varchar(13)`. Sin `autoApply` para no
    afectar a otros `BigDecimal`/`String` del proyecto.
  - Los filtros `minPrice`/`maxPrice` son `Long` con `@Positive` y se comparan con
    `Money.clp(...)`. Un mínimo de 0 es inútil (todos los precios son positivos) y se rechaza
    con 400 en lugar de relajar la invariante de `Money`.
- **Alternatives considered**: `@Embeddable record Money` (Hibernate 7 lo soporta, pero la
  columna única y la comparación en `Specification` son más simples con un convertidor);
  almacenar `BigDecimal` en la entidad y exponer `Money` por accesor (mueve la invariante fuera
  del constructor en cada carga).
- **Limitación aceptada**: el ISBN-10 y el ISBN-13 de la misma edición son cadenas distintas;
  la unicidad compara cadenas normalizadas, no equivalencias. Canonicalizar a ISBN-13 es una
  decisión de la administración (feature 004) y no se anticipa aquí.

## R7. Series: `/series/{id}` y `/series/{id}/volumes` por separado

- **Decision**: la serie se expone como recurso propio (datos) y sus volúmenes como subrecurso
  paginado que devuelve la **misma tarjeta** (`ProductSummaryResponse`) del listado. Una serie
  es visible solo si tiene al menos un producto publicado: sin volúmenes publicados, el detalle
  **y** la lista de volúmenes responden 404 (spec, Edge Cases y US4).
- **Rationale (Principio VII)**:
  1. FR-011 exige paginar los volúmenes con el mismo tamaño por defecto y máximo. Una lista
     anidada en el detalle obligaría a parámetros de paginación ambiguos en la URL del detalle
     (`?page=` ¿de qué?) y a una respuesta que mezcla un objeto con una página.
  2. El frontend necesita los datos de la serie (nombre, descripción) sin cargar volúmenes, p.
     ej. el enlace desde el detalle de un producto; con el subrecurso solo pide lo que pinta.
  3. Reutiliza sin cambios la tarjeta, el mapper y el flujo en dos pasos de R3.
- **Alternativa simple descartada**: `GET /series/{id}` con `volumes: PagedModel<…>`
  embebido. Un único recorrido de red, pero rompe 1) y 2). **Costo aceptado**: dos llamadas
  para pintar una página de serie.
- **Nota sobre la spec**: US4 habla de «consultar la serie» y ver nombre, descripción y
  volúmenes. Esos datos se reparten entre los dos endpoints; el escenario se prueba con ambos.

## R8. Seguridad: cadena propia de `catalog`

- **Decision**: `catalog.config.CatalogSecurityConfiguration` define un `SecurityFilterChain`
  `@Order(2)` con `securityMatcher` solo para `GET`, `HEAD` y `OPTIONS` sobre **rutas exactas**:
  `/api/v1/products`, `/api/v1/products/*`, `/api/v1/series`, `/api/v1/series/*`,
  `/api/v1/series/*/volumes`, `/api/v1/genres`, `/api/v1/authors`, `/api/v1/publishers`;
  `permitAll`, sin sesión, CSRF deshabilitado y CORS por defecto. Es el patrón de
  `IdentitySecurityConfiguration` (`@Order(1)`).
- `HEAD` se incluye porque Spring MVC lo atiende con el mismo `@GetMapping` y es lectura: sin él,
  un `HEAD` anónimo a un recurso público respondería 401 (Assumptions de la spec).
- Cualquier otro método (`POST`, `PUT`, `PATCH`, `DELETE`) sobre esas rutas no coincide con el
  `securityMatcher`, cae en la cadena por defecto de `platform` (`anyRequest().authenticated()`)
  y responde 401 sin token. Las rutas exactas (no `/products/**`) evitan abrir sin querer
  futuros `GET` administrativos de la feature 004.
- **El token se ignora en estas rutas**: la cadena no incluye `oauth2ResourceServer`, así que
  un `Bearer` caducado o inválido no produce 401 en un recurso público, y Cliente y
  Administrador ven lo mismo que el invitado (US6).
- **Alternatives considered**: `permitAll` en `platform.SecurityConfiguration` (acopla
  `platform` a las rutas de `catalog`); un `@PreAuthorize("permitAll()")` por método (no
  cambia la autorización de la cadena).
- Constitución V: el acceso anónimo está limitado a lectura de catálogo y a los 4 `POST` de
  `/auth`. No hay enmienda.

## R9. Errores: validación, 404 indistinguible

- **Validación**: `PageParams` y `ProductSearchRequest` se enlazan como `@ModelAttribute` de
  record con `@Valid`. Un fallo de Bean Validation (`size=101`, `sort=foo`, `minPrice>maxPrice`)
  llega como `MethodArgumentNotValidException` y el handler de 001 lo vuelve
  `errors: [{field, message}]`. `@ValidPriceRange` (anotación de clase) registra el nodo
  `minPrice` para que `field` sea el parámetro, no un nombre sintético.
- **Fallos de enlace (tipo)**: `type=XYZ`, `genreId=abc`, `minPrice=abc`, `page=abc` fallan
  al construir el record, no en Bean Validation. El mensaje por defecto de Spring incluye el
  valor y el tipo Java. **Cambio en `GlobalExceptionHandler`**: si `FieldError.isBindingFailure()`
  el mensaje es fijo («Formato de valor inválido.») y el campo es el nombre del parámetro.
- **UUID de ruta mal formado** (`/products/abc`): `MethodArgumentTypeMismatchException`. El
  handler actual lo traduce a 400 genérico sin `errors`. **Cambio**: override de
  `handleTypeMismatch` que añade `errors: [{field: <nombre>, message: "Formato de valor
  inválido."}]`. US3 y FR-008 lo exigen.
- **404 indistinguible**: una sola `CatalogNotFoundException extends ErrorResponseException`
  (404) para producto inexistente, producto no publicado, serie inexistente y serie sin
  volúmenes publicados. `GlobalExceptionHandler` normaliza `title`, `detail` y `instance`, y el
  cuerpo solo difiere en el `instance` (la propia ruta pedida). El servicio consulta con
  `status = PUBLISHED` en la misma sentencia, así que ni el tiempo ni el tipo de excepción
  distinguen los dos casos (FR-010, SC-005).
- **Página fuera de rango**: no es error (FR-004); el `count` devuelve el total real y el
  contenido vacío.

## R10. Datos y estrategia de prueba

- **Decision**: ningún dato en las migraciones (la feature 012 aporta los de ejemplo). Los tests
  cargan datos con `@Sql` (`src/test/resources/catalog/*.sql`) o con el builder
  `CatalogTestData` (`JdbcClient`) en `src/test`. Se inserta por SQL, no mediante entidades,
  porque la feature 003 no tiene fábricas de `Product` (R14) y así los tests no dependen de la
  escritura que construirá la 004.
- **Entidades en `@WebMvcTest`**: el servicio mockeado debe devolver entidades reales (VI prohíbe
  mockearlas), pero no tienen fábrica pública. `CatalogFixtures` (solo `src/test`) las crea por el
  constructor `protected` con `BeanUtils.instantiateClass` y asigna los campos con
  `ReflectionTestUtils.setField`. **Alternativa descartada**: añadir constructores o setters
  públicos a producción solo para tests (abre una vía de escritura que esquiva las invariantes
  que la 004 pondrá en la fábrica); **otra descartada**: probar los controladores con
  `@SpringBootTest` y BD real (más lento y mezcla web con persistencia).
- **Carga perezosa fuera de la transacción**: `@DataJpaTest` envuelve cada test en una transacción,
  de modo que la carga perezosa funcionaría siempre y ocultaría un grafo incompleto. El test que lo
  comprueba se marca `@Transactional(propagation = Propagation.NOT_SUPPORTED)` y limpia con
  `CatalogTestData.clear()`.
- **SQL del paso 1**: las estadísticas de Hibernate cuentan sentencias, no muestran columnas. Para
  comprobar que el paso 1 selecciona solo `id` se activa `logging.level.org.hibernate.SQL=DEBUG`
  en ese test y se inspecciona la salida con `OutputCaptureExtension`.
- Búsqueda, filtros combinados, clasificaciones y conteo de sentencias usan `@DataJpaTest` o
  `@SpringBootTest` con Testcontainers PostgreSQL vía `@ServiceConnection`, porque dependen de
  `unaccent` y `pg_trgm` reales.
- `@DataJpaTest` por defecto sustituye la BD y ejecuta Flyway; se usa
  `@AutoConfigureTestDatabase(replace = NONE)` como en los tests de 002.
- **Verificar en implementación**: el aislamiento entre tests (`TestDatabaseIsolationIT` de 001)
  también limpia las tablas nuevas, o el `@Sql` de cada test las vacía al inicio.

## R11. Frontera de módulo

- **Decision**: `catalog` no define fachada, interfaz ni evento público. El paquete raíz solo
  tiene `package-info.java` con `@ApplicationModule(displayName = "Catalog")`. El resto de
  paquetes (`domain`, `application`, `web`, `config`) son internos por convención de Modulith.
- **Rationale**: el carrito (006) será el primer consumidor y definirá entonces qué necesita
  (probablemente precio y estado de un producto por ID). Anticiparlo sería una interfaz
  especulativa (Principio VII, punto 1: solo se extrae una interfaz si es fachada **ya
  consumida** por otro módulo).
- `catalog` no depende de `identity` ni de `platform` por tipos; solo usa el
  `GlobalExceptionHandler` por mecanismo de Spring MVC.

## R12. Índices

- **Decision** (todos en V3; detalle en [data-model.md](data-model.md)):
  - Índices **parciales** `WHERE status = 'PUBLISHED'` sobre `(created_at, id)`, `(price, id)`,
    `(publication_date, id)` y `(title, id)`: toda consulta pública filtra por publicado, así
    que el índice es más pequeño y sirve directamente a la ordenación (FR-002).
  - Parciales sobre `type`, `publisher_id` (filtros FR-006).
  - Tablas intermedias: la PK `(product_id, author_id)` sirve al paso 2 y a la búsqueda por
    producto; un índice `(author_id, product_id)` (y el equivalente para géneros) sirve al
    filtro por autor/género y a los `EXISTS` de clasificaciones.
  - Volúmenes por serie: la restricción `UNIQUE (series_id, volume_number)` ya es un índice
    B-tree que sirve el orden por número de volumen.
  - GIN de trigramas sobre `catalog_normalize(title)` y `catalog_normalize(authors.name)`.
  - ISBN: la restricción `UNIQUE (isbn)` sirve la coincidencia exacta.
- **No se crean** índices para ordenar clasificaciones por nombre (tablas pequeñas; se medirá
  si crecen) ni compuestos por combinación de filtros (especulativo).

## R13. Límite superior de `page`

- **Decision**: `page` ≤ 1.000.000 (`@Max`). Con `size` ≤ 100 el desplazamiento máximo es
  10⁸, dentro de `int`; sin tope, `page × size` desbordaría el `setFirstResult(int)` de JPA y
  produciría un 500. Una página mayor se rechaza con 400 en lugar de devolver una página vacía.
  FR-004 recoge ahora este tope de forma explícita: un valor mayor es un parámetro inválido
  (FR-008), no una página fuera de rango.

## R14. Límites de la integridad solo con SQL

- La restricción «≥ 1 autor y ≥ 1 género» no se expresa con un `CHECK` de PostgreSQL (necesita
  mirar otra tabla); un *trigger* diferido sería complejidad sin evidencia (VII). Se garantiza
  en la fábrica de `Product` que añade la feature 004, que es la única que crea productos (la
  spec lo recoge en Assumptions). Las restricciones que sí están en V3 tienen test propio
  (`CatalogConstraintsIT`). En
  003 el módulo es de solo lectura: las entidades llevan constructor `protected` sin fábrica
  pública. Los CHECK de tipo, estado, precio, ISBN y coherencia serie/volumen y las `UNIQUE`
  sí están en V3.
- Un producto sin autores no aparecería en la búsqueda por autor, pero sí en el listado; no es
  una situación alcanzable por la API.

## R15. Valores de columnas obligatorias

- **Supuesto**: la spec solo declara opcionales el ISBN y la serie (con su volumen). Idioma,
  número de páginas, fecha de publicación, sinopsis y portada son `NOT NULL`. Si la
  administración (004) necesitara permitir nulos, se relaja con una migración nueva (V4).
  El orden por `publicationDate` es total gracias a ello.

## R16. Intercalación del orden por título

- El orden por `title` usa la intercalación de la base de datos (`ORDER BY title`). No se ordena
  por `catalog_normalize(title)`: costaría un índice funcional más y la spec no pide orden
  insensible a tildes. Consecuencia: «Álbum» puede ordenarse distinto según la *collation* del
  servidor. Los tests usan títulos ASCII; si el frontend lo exige, es una mejora posterior.
