# Feature Specification: Catalog (Public Read)

**Feature Branch**: `003-catalog-public-read`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "003 - Catalog (Public Read): explorar, buscar, filtrar y ver el detalle de mangas, libros y cómics; navegar series y obtener clasificaciones para filtros. Solo lectura y anónimo."

## Contexto

La plataforma base (feature 001) y la identidad de usuarios (feature 002) ya existen. Esta feature permite que cualquier visitante, con o sin cuenta, explore el catálogo de mangas, libros y cómics: listarlo, buscarlo, filtrarlo y ver el detalle de cada producto. Hoy el frontend React lee el catálogo desde un JSON propio; esta feature es la fuente de datos que lo reemplazará. El catálogo es la puerta de entrada de la tienda: sin él no hay carrito ni compras. Debe poder recorrerse sin crear cuenta y responder rápido aunque crezca a miles de productos.

**Actores**:

- **Invitado**: visitante sin sesión.
- **Cliente y Administrador**: ven exactamente el mismo catálogo público que el invitado.
- **Consumidor de la API**: el frontend React.

## Clarifications

### Session 2026-11-05

- Q: ¿Se confirman los tres supuestos del brief (CLP único sin decimales, un producto en como máximo una serie, idioma solo informativo)? → A: Sí, los tres quedan como reglas definitivas.
- Q: ¿Cómo se interpreta una búsqueda de varias palabras? → A: Cada palabra debe coincidir (en título, autor o ISBN) y la coincidencia es parcial (la palabra puede ser fragmento de un término), sin distinguir mayúsculas ni tildes.
- Q: ¿Los volúmenes de una serie se paginan? → A: Sí; la constitución exige paginar todo listado. Mismo tamaño por defecto (20) y máximo (100), siempre ordenados por número de volumen ascendente.
- Q: ¿Se limita la tasa de solicitudes a los endpoints públicos del catálogo? → A: No en esta feature; queda fuera de alcance y se trataría como mejora transversal posterior.

## User Scenarios & Testing *(mandatory)*

> **Tests**: cada escenario de aceptación de esta sección MUST tener un test automatizado. Es la petición explícita que la Constitución VI exige para generar tests más allá de los obligatorios.

### User Story 1 - Explorar el catálogo (Priority: P1)

Como visitante, quiero ver el catálogo paginado y ordenado, para recorrer lo que vende la tienda.

**Why this priority**: Es la capacidad mínima del catálogo; sin listado no hay tienda navegable ni base para carrito y compras.

**Independent Test**: Con productos publicados y no publicados cargados, consultar el listado sin credenciales y comprobar página, tamaño, orden y contenido de cada tarjeta.

**Acceptance Scenarios**:

```gherkin
Scenario: Listado sin parámetros
  Given existen más de 20 productos publicados
  When un visitante consulta el listado sin parámetros
  Then recibe la primera página con 20 productos publicados
  And los productos están ordenados por fecha de incorporación al catálogo, del más reciente al más antiguo
  And la respuesta incluye los metadatos de paginación

Scenario: Tamaño de página por encima del máximo
  When un visitante consulta el listado con tamaño de página 101
  Then la solicitud es rechazada con un error de validación que indica el parámetro afectado

Scenario: Página fuera de rango
  Given existen 45 productos publicados
  When un visitante consulta la página 50 con tamaño 20
  Then recibe una página vacía con los metadatos de paginación
  And no recibe un error

Scenario Outline: Ordenamiento explícito
  When un visitante consulta el listado ordenado por <criterio> en sentido <sentido>
  Then los productos aparecen ordenados por <criterio> en sentido <sentido>

  Examples:
    | criterio                   | sentido    |
    | título                     | ascendente |
    | precio                     | descendente|
    | fecha de publicación       | descendente|
    | fecha de incorporación     | ascendente |

Scenario: Contenido de la tarjeta de producto
  Given un producto publicado que pertenece a la serie "Dragon Ball" como volumen 3
  When un visitante lo ve en el listado
  Then la tarjeta muestra identificador, título, tipo, autores, precio, portada, nombre de la serie y número de volumen

Scenario: Producto sin serie
  Given un producto publicado que no pertenece a ninguna serie
  When un visitante lo ve en el listado
  Then la tarjeta no muestra serie ni número de volumen
```

---

### User Story 2 - Buscar y filtrar (Priority: P1)

Como visitante, quiero buscar por texto y filtrar, para encontrar rápido lo que me interesa.

**Why this priority**: Con miles de productos, recorrer páginas no basta; encontrar un título, autor o ISBN en una sola búsqueda es el principal criterio de éxito.

**Independent Test**: Cargar productos con títulos con tilde, autores e ISBN conocidos y verificar que cada búsqueda y combinación de filtros devuelve exactamente los productos esperados.

**Acceptance Scenarios**:

```gherkin
Scenario: Búsqueda sin distinguir mayúsculas ni tildes
  Given existe un producto publicado titulado "Dragón"
  When un visitante busca "dragon"
  Then el producto "Dragón" aparece en los resultados

Scenario: Búsqueda por autor
  Given existe un producto publicado del autor "Akira Toriyama"
  When un visitante busca "toriyama"
  Then el producto aparece en los resultados

Scenario: Búsqueda por ISBN
  Given existe un producto publicado con un ISBN conocido
  When un visitante busca ese ISBN
  Then el resultado contiene exactamente ese producto

Scenario: Filtros combinados
  Given productos publicados de distintos tipos, géneros y precios
  When un visitante filtra por tipo MANGA, un género y un rango de precio
  Then solo recibe productos que cumplen las tres condiciones

Scenario: Búsqueda, filtros, orden y paginación combinados
  When un visitante busca un texto, aplica filtros, ordena y pide la segunda página
  Then recibe la segunda página de los productos que cumplen la búsqueda y los filtros, en el orden pedido

Scenario Outline: Parámetros inválidos
  When un visitante consulta el listado con <parámetro inválido>
  Then la solicitud es rechazada con un error de validación detallado que identifica el parámetro

  Examples:
    | parámetro inválido                    |
    | precio mínimo mayor que el máximo     |
    | tipo inexistente                      |
    | criterio de orden no permitido        |

Scenario: Sin coincidencias
  When un visitante busca un texto que no coincide con ningún producto publicado
  Then recibe una página vacía con los metadatos de paginación
```

---

### User Story 3 - Ver el detalle de un producto (Priority: P1)

Como visitante, quiero ver toda la información de un producto, para decidir si comprarlo.

**Why this priority**: Es el paso previo a toda compra y debe contener todo lo que el frontend muestra hoy.

**Independent Test**: Consultar el detalle de un producto publicado, de uno no publicado y de uno inexistente, y comparar respuestas.

**Acceptance Scenarios**:

```gherkin
Scenario: Detalle de un producto publicado
  Given un producto publicado con todos sus datos
  When un visitante consulta su detalle
  Then recibe título, tipo, sinopsis, autores, editorial, géneros, ISBN, idioma, número de páginas, fecha de publicación, precio y portada
  And si pertenece a una serie, recibe además la serie y el número de volumen

Scenario: Producto no publicado o inexistente
  Given un producto existente pero no publicado
  And un identificador que no corresponde a ningún producto
  When un visitante consulta el detalle de cada uno
  Then ambas respuestas son "no encontrado"
  And ambas respuestas son indistinguibles entre sí

Scenario: Identificador con formato inválido
  When un visitante consulta el detalle con un identificador mal formado
  Then recibe un error de validación y no un error interno
```

---

### User Story 4 - Navegar por series (Priority: P2)

Como lector de manga, quiero ver todos los volúmenes de una serie en orden, para completar mi colección.

**Why this priority**: Mejora la experiencia de coleccionistas, pero la tienda es usable sin ella.

**Independent Test**: Cargar una serie con volúmenes 1, 2 y 3 donde el 2 no está publicado y consultar la serie.

**Acceptance Scenarios**:

```gherkin
Scenario: Serie con un volumen no publicado
  Given una serie con los volúmenes 1, 2 y 3
  And el volumen 2 no está publicado
  When un visitante consulta la serie (sus datos y su lista de volúmenes, FR-011)
  Then ve su nombre y descripción
  And ve solo los volúmenes 1 y 3, en ese orden

Scenario: Llegar a la serie desde un volumen
  Given un producto publicado que es el volumen 3 de una serie
  When un visitante consulta su detalle
  Then el detalle permite identificar la serie y llegar a ella

Scenario: Serie inexistente o sin volúmenes publicados
  When un visitante consulta una serie inexistente o sin volúmenes publicados
  Then recibe "no encontrado"
```

---

### User Story 5 - Clasificaciones para filtros (Priority: P2)

Como frontend, quiero obtener las listas de géneros, autores, editoriales y series, para construir los menús de filtros.

**Why this priority**: Habilita los menús de filtro, pero el catálogo ya es explorable por texto sin ellos.

**Independent Test**: Cargar clasificaciones con y sin productos publicados y consultar cada listado.

**Acceptance Scenarios**:

```gherkin
Scenario: Listado de géneros
  Given un género con productos publicados y otro sin ninguno
  When un visitante consulta el listado de géneros
  Then el género sin productos publicados no aparece

Scenario Outline: Listados de clasificaciones
  When un visitante consulta el listado de <clasificación>
  Then recibe una página ordenada alfabéticamente por nombre
  And la respuesta incluye los metadatos de paginación

  Examples:
    | clasificación |
    | géneros       |
    | autores       |
    | editoriales   |
    | series        |

Scenario: Clasificación que solo tiene productos no publicados
  Given un autor cuyos únicos productos están no publicados
  When un visitante consulta el listado de autores
  Then ese autor no aparece
```

---

### User Story 6 - Acceso anónimo (Priority: P1)

Como invitado, quiero acceder a todo el catálogo sin iniciar sesión.

**Why this priority**: Es un requisito transversal de la feature y de la tienda.

**Independent Test**: Invocar todos los endpoints de lectura sin credenciales y con una sesión válida de cliente y de administrador.

**Acceptance Scenarios**:

```gherkin
Scenario: Acceso sin token
  Given un visitante sin token
  When consulta cualquier endpoint de lectura del catálogo
  Then recibe la respuesta del catálogo sin ser rechazado por falta de autenticación

Scenario: Mismo catálogo para todos los roles
  Given un cliente autenticado y un administrador autenticado
  When consultan el listado de productos
  Then ven exactamente los mismos productos que un invitado
```

### Edge Cases

- Búsqueda de varias palabras: todas deben coincidir; el orden de las palabras no importa.
- Búsqueda de texto vacío o solo con espacios: se trata como si no hubiera búsqueda.
- Búsqueda de un ISBN escrito con o sin guiones: encuentra el mismo producto.
- Número de página negativo o tamaño de página cero o negativo: error de validación.
- Filtro por un identificador de clasificación inexistente (pero bien formado): página vacía, no error.
- Filtro con identificador de formato inválido: error de validación.
- Parámetro repetido (`size=10&size=20`, `genreId=A&genreId=B`, `type=MANGA&type=LIBRO`, `minPrice` repetido): los parámetros son de un solo valor, de modo que se usa solo el **primero** y se ignoran los demás (no hay filtro «uno u otro»). En `q` y `sort`, que son texto, los valores repetidos se unen con coma en un solo valor: `sort=price&sort=title,desc` equivale a `price,title,desc` y se rechaza con 400 (orden no permitido); `q=a&q=b` busca el texto `a,b`.
- Parámetro desconocido (`foo=bar`): se ignora; la respuesta es la misma que sin él.
- Productos con el mismo valor en el criterio de orden: el orden entre ellos es estable y repetible entre consultas.
- Un producto sin ISBN: no aparece en búsquedas por ISBN, pero sí por título o autor.
- Un producto con varios autores: aparece al filtrar o buscar por cualquiera de ellos y no se duplica en los resultados.
- Una serie sin volúmenes publicados: no aparece en el listado de series y su detalle responde "no encontrado".

## Requirements *(mandatory)*

### Functional Requirements

**Listado y paginación**

- **FR-001**: El listado de productos MUST ser público y siempre paginado. El tamaño de página por defecto MUST ser 20 y el máximo 100; un tamaño mayor MUST rechazarse con error de validación.
- **FR-002**: El listado MUST poder ordenarse por título, precio, fecha de publicación y fecha de incorporación al catálogo (novedades), en ambos sentidos. Sin orden explícito, MUST mostrar primero las novedades. Si se indica el criterio sin sentido, el sentido es ascendente. El orden MUST ser estable entre productos con el mismo valor. La fecha de publicación es una fecha de calendario ISO-8601 sin zona horaria (`2020-01-31`); la fecha de incorporación es un instante (`TIMESTAMPTZ`, UTC) que solo se usa para ordenar y no se expone en ninguna respuesta.
- **FR-003**: Cada elemento del listado MUST mostrar lo necesario para una tarjeta de producto: identificador, título, tipo (manga, libro o cómic), autores, precio, portada y, si pertenece a una serie, su nombre y número de volumen. Los autores (y, en el detalle, los géneros) se devuelven ordenados por nombre ascendente y, a igual nombre, por id.
- **FR-004**: Una página fuera de rango MUST devolver una página vacía con los metadatos de paginación, no un error. El número de página admite como máximo 1.000.000; un valor mayor es un parámetro inválido (FR-008), no una página fuera de rango.

**Búsqueda y filtros**

- **FR-005**: La búsqueda por texto MUST encontrar productos por título, nombre de autor o ISBN, sin distinguir mayúsculas ni tildes. Con varias palabras, cada una MUST coincidir en alguno de esos campos, y la coincidencia es parcial (p. ej. "drag" encuentra "Dragón"). Una «palabra» es cada secuencia de caracteres separada por espacios en blanco; la puntuación interna se conserva literal (`spider-man` es una sola palabra; como la coincidencia es parcial por palabra, tanto `spider-man` como `spider man` encuentran «Spider-Man»). El texto admite como máximo 100 caracteres (si lo supera, 400 según FR-008), lo que acota también el número de palabras.
- **FR-006**: El listado MUST admitir filtros combinables por tipo de producto, género, autor, editorial, serie y rango de precio (mínimo y máximo, ambos opcionales e inclusivos).
- **FR-007**: Búsqueda y filtros MUST combinarse entre sí y con la paginación y el orden. Un producto MUST aparecer una sola vez en los resultados aunque coincida por varios autores o géneros.
- **FR-008**: Los parámetros inválidos (precio mínimo mayor que el máximo, tipo inexistente, orden no permitido, paginación inválida, identificadores mal formados) MUST rechazarse con error de validación detallado que identifique cada parámetro afectado.

**Detalle**

- **FR-009**: El detalle de producto MUST incluir: título, tipo, sinopsis, autores, editorial, géneros, ISBN, idioma, número de páginas, fecha de publicación, precio, portada y, si aplica, serie y número de volumen. Autores y géneros se devuelven ordenados por nombre ascendente y, a igual nombre, por id.
- **FR-010**: Un producto inexistente o no publicado MUST responder "no encontrado" con una respuesta indistinguible entre ambos casos. Nunca MUST revelarse que existe un producto oculto.

**Series**

- **FR-011**: «Consultar una serie» se atiende con dos recursos: `GET /api/v1/series/{id}` devuelve sus datos (nombre y descripción) y `GET /api/v1/series/{id}/volumes` devuelve sus volúmenes publicados, ordenados por número de volumen ascendente y paginados con el mismo tamaño por defecto y máximo que FR-001. Una serie sin volúmenes publicados no es visible: su detalle y sus volúmenes responden 404.
- **FR-012**: El detalle de un volumen MUST permitir llegar a su serie.

**Clasificaciones**

- **FR-013**: MUST existir listados públicos de géneros, autores, editoriales y series, paginados (mismo tamaño por defecto y máximo que FR-001) y ordenados alfabéticamente por nombre.
- **FR-014**: Esos listados MUST incluir solo clasificaciones con al menos un producto publicado.

**Reglas de dominio**

- **FR-015**: Todo producto MUST tener exactamente un tipo: MANGA, LIBRO o COMIC.
- **FR-016**: El precio MUST ser un monto positivo en una única moneda: pesos chilenos (CLP), sin decimales.
- **FR-017**: El ISBN, cuando existe, MUST ser válido (ISBN-10 o ISBN-13 con dígito verificador correcto) y único en el catálogo.
- **FR-018**: Un volumen de serie MUST tener un número positivo y único dentro de su serie.
- **FR-019**: Un producto MUST tener al menos un autor y al menos un género.
- **FR-020**: Solo los productos en estado publicado MUST ser visibles en la API pública. Los no publicados existen para la administración (feature 004) y MUST NOT aparecer en listados, búsquedas, detalle, series ni clasificaciones.

**Transversales**

- **FR-021**: Todos los endpoints de esta feature MUST ser de solo lectura y de acceso anónimo, dentro de lo permitido por la constitución para la lectura de catálogo. Cliente y Administrador MUST ver el mismo catálogo que el invitado.
- **FR-022**: Todos los errores MUST responder en formato ProblemDetail, sin exponer detalles internos.

### Key Entities *(include if feature involves data)*

- **Producto**: ítem vendible del catálogo. Atributos: tipo (MANGA, LIBRO, COMIC), título, sinopsis, precio (CLP), ISBN (opcional), idioma, número de páginas, fecha de publicación, portada (URL), estado (publicado o no publicado), fecha de incorporación al catálogo. Relaciones: uno o más autores, una editorial, uno o más géneros, y opcionalmente una serie con número de volumen.
- **Autor**: persona que firma productos; tiene nombre. Se relaciona con muchos productos.
- **Editorial**: empresa que publica productos; tiene nombre.
- **Género**: categoría temática; tiene nombre. Se relaciona con muchos productos.
- **Serie**: agrupación de volúmenes; tiene nombre y descripción. Cada volumen tiene un número positivo único dentro de la serie.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un visitante encuentra un producto por título, autor o ISBN en una sola búsqueda, sin combinar filtros.
- **SC-002**: Con un catálogo de 10.000 productos publicados, el 95 % de las consultas de listado (con o sin búsqueda y filtros) responde en menos de 500 ms. Condiciones de medición en quickstart §6: un solo cliente, peticiones secuenciales, caché caliente tras `ANALYZE`, entorno local de desarrollo; concurrencia e infraestructura quedan fuera.
- **SC-003**: El frontend puede reemplazar su JSON local por esta API sin perder ninguna información que muestra hoy (tarjeta y detalle de producto, series y menús de filtros). Se verifica en la feature 012 contra la lista de campos de FR-003 (tarjeta) y FR-009 (detalle).
- **SC-004**: El 100 % de las consultas públicas se resuelve sin credenciales y ningún producto no publicado es visible en ninguna respuesta.
- **SC-005**: Las respuestas "no encontrado" para un producto no publicado y para uno inexistente son idénticas en contenido y forma.
- **SC-006**: El 100 % de las solicitudes con parámetros inválidos recibe un error que identifica el parámetro y el motivo.

## Assumptions

- Moneda única CLP sin decimales; no existen otras monedas ni conversión (confirmado).
- Un producto pertenece como máximo a una serie (confirmado).
- El idioma es un dato informativo del producto, no un filtro (confirmado).
- `language` es texto libre de hasta 35 caracteres, pensado como código corto tipo BCP 47 (`es`, `ja`); es informativo y la 003 no valida su formato.
- No se aplica limitación de tasa a los endpoints públicos en esta feature. El coste máximo de una búsqueda anónima está acotado (`q` ≤ 100 caracteres, `size` ≤ 100, peor caso medido en quickstart §6.7) y el rate limiting se difiere a una feature transversal.
- Las reglas de dominio que se comprueban al crear o editar un producto, en particular FR-019 (≥ 1 autor y ≥ 1 género), se aplican en la feature 004, que es la única que escribe en el catálogo. Esta feature solo lee; la base de datos protege lo que puede expresarse con restricciones (tipo, precio, ISBN, serie y volumen).
- Los endpoints de lectura aceptan `GET` y `HEAD` (y `OPTIONS` para CORS) sin credenciales.
- La comprobación de SC-003 contra el JSON actual del frontend se hace al migrar esos datos (feature 012).
- Un producto tiene exactamente una editorial.
- La portada es una URL; la subida y el almacenamiento de imágenes quedan fuera de alcance.
- Los datos del catálogo (productos y clasificaciones) se cargan fuera de esta feature: la administración llega con la feature 004 y los datos de ejemplo con la 012. Esta feature solo lee.
- El tamaño de página por defecto (20) y máximo (100) se aplica también a los listados de clasificaciones.
- Se presume que la fecha de incorporación al catálogo es la de creación del producto en el sistema.
- La disponibilidad y el stock no forman parte de la respuesta del catálogo (feature 005).

## Out of Scope

- Crear, editar, publicar o despublicar productos y clasificaciones (feature 004 - Catalog Admin).
- Stock y disponibilidad (feature 005 - Inventory).
- Reseñas y calificaciones (feature 013).
- Datos de ejemplo para desarrollo y migración del JSON del frontend (feature 012).
- Subida y almacenamiento de imágenes.
- Precios con descuento o promociones (feature 015).
