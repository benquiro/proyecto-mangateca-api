# Feature Specification: Platform Foundation, Persistence Baseline & Architectural Governance

**Feature Branch**: `001-platform-foundation`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "001 - Platform Foundation, Persistence Baseline & Architectural
Governance. Walking skeleton de Mangateca API: una plataforma base que arranque, persista datos
con un esquema versionado, se proteja por defecto, responda errores de forma estándar y haga
cumplir automáticamente las reglas de arquitectura y calidad de la constitución."

## User Scenarios & Testing *(mandatory)*

**Actores**:

- **Desarrollador**: miembro del equipo de ingeniería que levanta y prueba el sistema en su
  máquina.
- **Pipeline**: proceso de construcción/CI que compila, prueba y decide si un cambio es
  aceptable.
- **Consumidor de la API**: cliente HTTP (incluido el futuro SPA) que interactúa con los
  endpoints expuestos.

### User Story 1 - Entorno local sin instalación manual (Priority: P1)

Como desarrollador, quiero iniciar la aplicación en modo desarrollo con un único comando y sin
instalar una base de datos, para empezar a trabajar de inmediato.

**Why this priority**: es la base del walking skeleton; sin un arranque local reproducible
ninguna otra historia puede demostrarse.

**Independent Test**: en una máquina con el repositorio recién clonado y sin base de datos
instalada, ejecutar el comando de arranque y comprobar que la API responde.

**Acceptance Scenarios**:

1. **Given** un repositorio recién clonado y una máquina sin base de datos instalada,
   **When** el desarrollador inicia la aplicación en modo desarrollo,
   **Then** la base de datos relacional se levanta automáticamente, se aplica la migración V1
   y la API queda disponible para recibir peticiones.
2. **Given** la aplicación ejecutándose en modo desarrollo con su base de datos levantada
   automáticamente,
   **When** el desarrollador detiene la aplicación,
   **Then** la base de datos asociada también se detiene.

---

### User Story 2 - Esquema de base de datos versionado y verificado (Priority: P1)

Como equipo, queremos que el esquema evolucione solo mediante migraciones versionadas e
inmutables, para que todos los entornos sean reproducibles.

**Why this priority**: un esquema no controlado desde el inicio genera divergencias entre
entornos que son costosas de corregir después.

**Independent Test**: arrancar contra una base vacía y verificar que V1 queda aplicada;
arrancar contra una base alterada y verificar que el arranque se aborta.

**Acceptance Scenarios**:

1. **Given** una base de datos vacía,
   **When** la aplicación arranca,
   **Then** aplica todas las migraciones pendientes (incluida V1) antes de aceptar tráfico y
   el historial de migraciones registra V1 como aplicada.
2. **Given** una base de datos cuyo esquema no coincide con el modelo esperado por la
   aplicación,
   **When** la aplicación arranca,
   **Then** el arranque falla antes de aceptar cualquier petición, con un mensaje que
   identifica la discrepancia, y el esquema no es modificado por la aplicación.
3. **Given** una base de datos con una migración aplicada que ya no existe en el repositorio,
   o cuyo contenido fue modificado tras aplicarse,
   **When** la aplicación arranca,
   **Then** el arranque falla con un mensaje claro que identifica la migración afectada.

---

### User Story 3 - Seguridad denegada por defecto (Priority: P1)

Como responsable de seguridad, quiero que toda ruta esté protegida salvo las declaradas
explícitamente como públicas.

**Why this priority**: la seguridad incorporada después de construir módulos deja huecos;
deny-by-default garantiza que una ruta olvidada quede cerrada.

**Independent Test**: enviar peticiones sin credenciales, con credenciales inválidas y con
credenciales sin permisos a rutas no públicas, y verificar 401/403; enviar peticiones sin
credenciales a las rutas públicas y verificar acceso.

**Acceptance Scenarios**:

1. **Given** una ruta no declarada como pública,
   **When** un consumidor la invoca sin credenciales (o con credenciales inválidas o expiradas),
   **Then** recibe 401 en formato Problem Details.
2. **Given** una ruta restringida a un rol concreto,
   **When** un consumidor autenticado sin ese rol la invoca,
   **Then** recibe 403 en formato Problem Details.
3. **Given** las rutas de salud, información, contrato OpenAPI y su interfaz visual,
   **When** un consumidor las invoca sin credenciales,
   **Then** obtiene acceso sin ser rechazado.
4. **Given** un entorno con orígenes CORS permitidos configurados,
   **When** llega una petición de origen cruzado (incluida la de verificación previa) desde un
   origen no autorizado,
   **Then** la petición es rechazada y la respuesta no contiene cabeceras que autoricen ese
   origen.
5. **Given** un entorno con orígenes CORS permitidos configurados,
   **When** llega una petición de origen cruzado desde un origen autorizado,
   **Then** la respuesta autoriza explícitamente ese origen (nunca con comodín).

---

### User Story 4 - Errores estandarizados (Priority: P1)

Como consumidor de la API, quiero recibir errores en un formato estándar y predecible, sin
detalles internos del servidor.

**Why this priority**: el contrato de errores es compartido por todos los módulos futuros y por
el cliente SPA; cambiarlo después rompe a los consumidores.

**Independent Test**: provocar cada categoría de error (cuerpo malformado, validación, ruta
inexistente, método no soportado, fallo inesperado) y verificar formato, código HTTP y ausencia
de información interna.

**Acceptance Scenarios**:

1. **Given** un consumidor autenticado,
   **When** envía una petición con un cuerpo malformado,
   **Then** recibe 400 en formato Problem Details sin trazas de pila, nombres de clases ni
   mensajes internos.
2. **Given** un consumidor autenticado,
   **When** envía datos que no superan la validación,
   **Then** recibe 400 en formato Problem Details con la lista de campos inválidos y un
   mensaje legible por campo.
3. **Given** un consumidor autenticado,
   **When** invoca una ruta inexistente,
   **Then** recibe 404 en formato Problem Details.
4. **Given** cualquier petición,
   **When** se produce un fallo inesperado en el servidor,
   **Then** el consumidor recibe 500 en formato Problem Details con un mensaje genérico, y el
   detalle del fallo queda registrado solo en los logs del servidor.

---

### User Story 5 - Pruebas aisladas y reproducibles (Priority: P2)

Como pipeline, quiero ejecutar las pruebas de integración contra una base de datos real, limpia
y efímera, para que los resultados no dependan del entorno local del desarrollador.

**Why this priority**: habilita que todas las demás historias se verifiquen automáticamente,
pero el sistema puede arrancar y demostrarse sin ella.

**Independent Test**: ejecutar la suite dos veces seguidas, con la base de desarrollo encendida
y con datos, y verificar resultados idénticos sin cambios en la base de desarrollo.

**Acceptance Scenarios**:

1. **Given** la base de desarrollo local en ejecución y con datos,
   **When** se ejecuta la suite de integración,
   **Then** la suite usa una instancia nueva del mismo motor de base de datos de producción,
   independiente de la base local, y la base local no sufre ningún cambio.
2. **Given** una ejecución de la suite finalizada,
   **When** se inspecciona el entorno,
   **Then** la instancia efímera usada por las pruebas ha sido descartada.

---

### User Story 6 - Límites modulares verificados automáticamente (Priority: P2)

Como equipo, queremos que la estructura del código esté preparada para módulos de negocio
independientes y que sus límites se verifiquen en cada build.

**Why this priority**: debe estar activo antes del primer módulo de negocio, pero no aporta
valor visible al consumidor por sí solo.

**Independent Test**: introducir de forma temporal un acceso indebido entre módulos y verificar
que el build falla; retirarlo y verificar que el build pasa.

**Acceptance Scenarios**:

1. **Given** una estructura modular que respeta sus límites,
   **When** se ejecuta el build,
   **Then** la verificación de arquitectura pasa.
2. **Given** un cambio en el que un módulo accede a un componente interno de otro módulo,
   **When** se ejecuta el build,
   **Then** el build falla identificando el módulo origen, el módulo destino y el tipo
   accedido.
3. **Given** un cambio que introduce un ciclo de dependencias entre módulos,
   **When** se ejecuta el build,
   **Then** el build falla identificando el ciclo.

---

### User Story 7 - Puerta de calidad (Priority: P2)

Como pipeline, quiero rechazar automáticamente los cambios que no cumplan los umbrales de
calidad de la constitución.

**Why this priority**: evita que la deuda se acumule desde el primer módulo; no bloquea la
demostración del esqueleto.

**Independent Test**: introducir código que genere una advertencia de compilación y verificar
que el build falla. (Sin umbral de cobertura: constitución v2.0.0, §VI.)

**Acceptance Scenarios**:

1. **Given** un cambio que produce al menos una advertencia de compilación,
   **When** se ejecuta el build,
   **Then** el build falla mostrando la advertencia.

---

### User Story 8 - Observabilidad y contrato (Priority: P2)

Como consumidor de la API, quiero consultar el estado del servicio y su contrato publicado.

**Why this priority**: necesaria para operar y para que el SPA descubra el contrato, pero el
contrato aún no contiene endpoints de negocio.

**Independent Test**: invocar sin credenciales el endpoint de salud y la documentación del
contrato y verificar su contenido.

**Acceptance Scenarios**:

1. **Given** la aplicación en ejecución con la base de datos disponible,
   **When** se consulta el endpoint de salud,
   **Then** informa estado "arriba" para la aplicación y para la base de datos.
2. **Given** la aplicación en ejecución con la base de datos no disponible,
   **When** se consulta el endpoint de salud,
   **Then** informa estado "abajo" con un código HTTP que indica servicio no disponible.
3. **Given** la aplicación en ejecución,
   **When** se consulta la documentación del contrato,
   **Then** se obtiene el contrato en formato OpenAPI 3 y una interfaz web navegable.

---

### User Story 9 - Decisiones tecnológicas documentadas (Priority: P3)

Como desarrollador, quiero que cada capacidad nueva agregada al proyecto base tenga un registro
de decisión arquitectónica (ADR) en `docs/adr/`, para entender por qué existe y qué
alternativas se descartaron.

**Why this priority**: no cambia el comportamiento del sistema, pero la constitución exige que
cada ADR exista antes de incorporar la capacidad que justifica.

**Independent Test**: revisar `docs/adr/` y comprobar que existen los cinco ADR con todas las
secciones obligatorias.

**Acceptance Scenarios**:

1. **Given** el repositorio al finalizar la feature,
   **When** se revisa `docs/adr/`,
   **Then** existen ADR-001 (verificación de arquitectura modular y eventos de dominio),
   ADR-002 (mapeo entre entidades y DTOs en tiempo de compilación), ADR-003 (autenticación
   stateless como servidor de recursos), ADR-004 (aprovisionamiento automático de
   infraestructura local en desarrollo) y ADR-005 (puerta de calidad: compilación sin
   advertencias).
2. **Given** cualquiera de esos ADR,
   **When** se lee,
   **Then** contiene las siete secciones obligatorias de FR-001; en particular, expone el
   problema, las alternativas descartadas (incluida la opción nativa sin dependencia extra), la
   compatibilidad con la versión de Java y del framework definidas en la constitución, y el
   estado de mantenimiento de la herramienta.

---

### Edge Cases

- Una petición sin credenciales a una ruta inexistente recibe 401, no 404: deny-by-default
  se evalúa antes que la resolución de rutas, para no revelar qué rutas existen.
- Un token con firma inválida, malformado o expirado recibe 401 en formato Problem Details,
  sin indicar cuál de las comprobaciones falló.
- Una petición con un método no soportado recibe 405, y una con un tipo de contenido no
  soportado recibe 415, ambos en formato Problem Details.
- Si la base de datos no es accesible al arrancar, el arranque falla con un mensaje claro y no
  queda una aplicación a medio iniciar aceptando tráfico.
- Si en modo desarrollo el entorno de contenedores locales no está disponible, el arranque
  falla con un mensaje que indica el requisito ausente.
- Si un entorno no define orígenes CORS permitidos, no se autoriza ningún origen cruzado
  (falla cerrado), nunca se interpreta como "todos".
- Si la configuración de orígenes CORS contiene el comodín `*`, la aplicación no arranca.
- Si se modifica una migración ya aplicada, el arranque falla por discrepancia de contenido.
- Un error de validación con varios campos inválidos los informa todos en una única respuesta.

## Requirements *(mandatory)*

### Functional Requirements

**Decisiones documentadas**

- **FR-001**: El repositorio MUST contener en `docs/adr/` los cinco ADR (ADR-001 a ADR-005)
  descritos en la User Story 9, cada uno con estas siete secciones obligatorias: (1) título,
  estado y fecha; (2) contexto y problema; (3) alternativas descartadas, incluida la opción
  nativa sin dependencia extra; (4) decisión, con el artefacto y la versión exactos;
  (5) compatibilidad con Java 25 / Spring Boot 4; (6) estado de mantenimiento; y
  (7) consecuencias.
- **FR-002**: Cada capacidad nueva MUST tener su ADR aceptado antes de incorporarse al build.

**Entorno local**

- **FR-003**: En modo desarrollo, la aplicación MUST levantar automáticamente la base de datos
  relacional al iniciar y detenerla al finalizar, sin instalación manual.
- **FR-004**: El arranque local MUST realizarse con un único comando documentado en el README.

**Esquema y persistencia**

- **FR-005**: Al arrancar, el sistema MUST aplicar las migraciones pendientes antes de aceptar
  tráfico.
- **FR-006**: MUST existir una migración inicial V1 que prepare únicamente las capacidades base
  del motor (el almacenamiento del registro de publicación de eventos de dominio y solo las
  extensiones que ese almacenamiento necesite, que en V1 son ninguna, porque usa tipos nativos
  de PostgreSQL) y MUST NOT crear tablas de negocio ni extensiones especulativas.
- **FR-007**: El sistema MUST validar que el esquema coincide con el modelo esperado y MUST
  abortar el arranque, con un mensaje que identifique la discrepancia, si no coincide, si falta
  una migración aplicada o si una migración aplicada fue alterada.
- **FR-008**: El sistema MUST NOT crear, alterar ni eliminar estructuras del esquema por su
  cuenta fuera de las migraciones versionadas.
- **FR-009**: El sistema MUST NOT mantener sesiones de base de datos abiertas durante la
  generación de respuestas HTTP fuera de los límites transaccionales.

**Pruebas**

- **FR-010**: La suite de integración MUST ejecutarse contra una instancia nueva y efímera del
  mismo motor de base de datos de producción, independiente de la base de desarrollo, que se
  descarta al terminar.
- **FR-011**: Las pruebas MUST NOT usar bases de datos en memoria ni un motor distinto al de
  producción.

**Arquitectura y calidad**

- **FR-012**: El código MUST organizarse en módulos de dominio vertical, y el build MUST fallar
  si un módulo accede a componentes internos de otro o si existe un ciclo de dependencias entre
  módulos.
- **FR-013**: *(Retirado el 2026-11-05: la constitución v2.0.0 elimina el umbral de cobertura
  obligatorio.)*
- **FR-014**: El build MUST fallar si la compilación produce cualquier advertencia.

**Errores**

- **FR-015**: Toda respuesta de error MUST usar el formato Problem Details (RFC 9457) con el
  código HTTP correspondiente: 400 (cuerpo malformado o validación), 401, 403, 404, 405, 415 y
  500 (fallo inesperado).
- **FR-016**: Los errores de validación MUST incluir la lista de campos inválidos con un
  mensaje legible por campo.
- **FR-017**: Ninguna respuesta de error MUST exponer trazas de pila, nombres de clases,
  sentencias SQL ni mensajes internos de excepción.
- **FR-018**: Los fallos inesperados MUST registrarse en los logs del servidor con su detalle,
  sin registrar contraseñas, tokens ni datos personales.

**Seguridad**

- **FR-019**: Toda ruta MUST requerir autenticación salvo: endpoint de salud, endpoint de
  información, documento del contrato OpenAPI y su interfaz visual.
- **FR-020**: Las peticiones a rutas no públicas sin credenciales válidas MUST recibir 401, y
  las peticiones autenticadas sin permisos suficientes MUST recibir 403.
- **FR-021**: El sistema MUST validar credenciales stateless de tipo token portador (bearer)
  emitidas por la futura capacidad de identidad, sin mantener sesión en el servidor.
- **FR-022**: Los orígenes CORS permitidos MUST configurarse por entorno, MUST NOT aceptar el
  comodín `*`, y en ausencia de configuración MUST NOT autorizarse ningún origen cruzado.
- **FR-023**: La configuración sensible (credenciales de base de datos, claves de
  verificación de tokens) MUST proveerse por variables de entorno y MUST NOT versionarse.

**Observabilidad y contrato**

- **FR-024**: Solo los endpoints operativos de salud e información MUST estar expuestos.
- **FR-025**: El endpoint de salud MUST informar el estado de la aplicación y de la base de
  datos, respondiendo con un código de servicio no disponible cuando alguno esté caído.
- **FR-026**: El contrato de la API MUST publicarse en formato OpenAPI 3 y MUST ser navegable
  desde una interfaz web.

### Key Entities

- **Historial de migraciones**: registro de las migraciones de esquema aplicadas (versión,
  descripción, huella de contenido, fecha); permite detectar migraciones faltantes o alteradas.
- **Registro de publicación de eventos de dominio**: almacenamiento preparado en V1 para
  registrar los eventos publicados entre módulos y su estado de procesamiento; sin uso de
  negocio en esta feature.
- **ADR**: documento de decisión (identificador, título, estado, contexto/problema,
  alternativas, decisión, consecuencias, compatibilidad, mantenimiento).
- **Respuesta de error (Problem Details)**: tipo, título, estado HTTP, detalle legible,
  instancia y, para validación, la lista de errores por campo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un desarrollador nuevo pasa de clonar el repositorio a tener la API respondiendo
  localmente con un único comando, sin instalar una base de datos, en menos de 10 minutos
  (incluida la primera descarga de imágenes).
- **SC-002**: El 100% de las violaciones introducidas intencionalmente (acceso indebido entre
  módulos, ciclo entre módulos, advertencia de compilación) hacen
  fallar el build, sin depender de revisión manual.
- **SC-003**: El 100% de las respuestas de error del catálogo de escenarios (400, 401, 403,
  404, 405, 415, 500) siguen el formato Problem Details, y el 0% contiene trazas de pila,
  nombres de clases o mensajes internos.
- **SC-004**: El 100% de las rutas no declaradas como públicas rechazan peticiones sin
  credenciales.
- **SC-005**: Dos ejecuciones consecutivas de la suite de pruebas producen el mismo resultado y
  no dejan ningún cambio en la base de desarrollo local.
- **SC-006**: El 100% de los arranques contra un esquema discrepante, con una migración faltante
  o con una migración alterada se abortan antes de atender la primera petición.
- **SC-007**: Los cinco ADR existen y cada uno cubre las siete secciones obligatorias de
  FR-001.

## Assumptions

- Las máquinas de desarrollo y el pipeline disponen de un entorno de contenedores (p. ej.
  Docker) en ejecución; es el único requisito previo además de la JDK.
- "Modo desarrollo" es un perfil de configuración explícito; los demás entornos obtienen la
  conexión a la base de datos solo por variables de entorno.
- La emisión de tokens está fuera de alcance. Para verificar 401/403, las pruebas generan
  tokens firmados con una clave exclusiva de pruebas, y la ruta restringida por rol usada en
  el escenario 403 existe solo en el contexto de pruebas, no en la API publicada.
- La clave de verificación de tokens de cada entorno se provee por configuración; el
  repositorio no contiene secretos.
- El endpoint de salud muestra a usuarios anónimos solo el estado (arriba/abajo) de la
  aplicación y de la base de datos, sin detalles como versiones, hosts o credenciales.
- El endpoint de información expone únicamente el nombre y la versión de la aplicación.
- Las dependencias base del `pom.xml` inicial se conservan; las capacidades nuevas se añaden
  solo tras su ADR.
- Esta feature no expone endpoints de negocio, así que la paginación obligatoria aplica a
  partir de los módulos de negocio.
- La detección de violaciones (SC-002) se demuestra introduciendo cambios temporales que se
  revierten después (procedimiento en `quickstart.md`); esos cambios nunca se integran.
