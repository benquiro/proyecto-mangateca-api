# Constitución de Mangateca API

| Campo                    | Valor      |
|--------------------------|------------|
| Versión                  | 1.1.0      |
| Estado                   | Activo     |
| Fecha de ratificación    | 2026-11-05 |
| Fecha de última enmienda | 2026-11-05 |

Las palabras clave MUST, MUST NOT, REQUIRED, SHOULD, SHOULD NOT y MAY se interpretan según
RFC 2119. Cada regla incluye su justificación (*Por qué*).

## Metadatos y Jerarquía Normativa

- Esta Constitución es la ley suprema del repositorio y prevalece sobre todo `spec.md`,
  `plan.md`, `tasks.md`, ADR o código preexistente.
  *Por qué*: una única fuente de verdad elimina ambigüedad al resolver conflictos de diseño.
- Todo artefacto inferior que contradiga esta Constitución MUST considerarse inválido y
  corregirse antes de integrarse; el conflicto NO se resuelve reinterpretando la Constitución.
  *Por qué*: si los artefactos derivados pudieran imponerse, las reglas se erosionarían por
  acumulación de excepciones.
- **Naturaleza del sistema**: "Mangateca API" es el backend REST de comercio electrónico
  (catálogo, compras y administración) consumido por un cliente SPA desacoplado, construido con
  estándares profesionales de industria.

## Principios Fundamentales

### I. Monolito Modular con Límites Verificados

- El sistema MUST desplegarse como un único monolito modular. Se MUST preferir simplicidad,
  legibilidad y cohesión sobre abstracción prematura (YAGNI).
  *Por qué*: el dominio y el equipo no justifican el coste operativo de microservicios; la
  modularidad interna conserva la opción de extraerlos más adelante.
- El código MUST organizarse en módulos de dominio vertical (p. ej. `catalog`, `user`, `cart`,
  `order`, `payment`, `review`) y MUST NOT organizarse en capas técnicas horizontales globales
  (`controllers/`, `services/`, `repositories/` a nivel raíz).
  *Por qué*: la cohesión por capacidad de negocio localiza los cambios y hace visibles las
  dependencias entre módulos.
- Los límites de módulo MUST verificarse en la suite de tests mediante Spring Modulith
  (`ApplicationModules.of(...).verify()`); un fallo de verificación MUST romper el build.
  *Por qué*: una regla arquitectónica no verificada automáticamente se degrada con el tiempo.
- Un módulo MUST NOT acceder a repositorios, entidades JPA ni tipos internos de otro módulo.
  La interacción MUST ocurrir solo vía la API pública del módulo (servicios/interfaces en su
  paquete raíz o expuestos explícitamente) o mediante eventos de dominio publicados con
  `ApplicationEventPublisher`.
  *Por qué*: el acoplamiento a internals ajenos impide evolucionar un módulo sin romper otros.
- Las integraciones con terceros aún no implementadas (p. ej. pasarela de pago) MUST modelarse
  como contratos de dominio (`PaymentGateway`) con una implementación simulada desacoplada.
  *Por qué*: sustituir el simulador por la integración real no debe alterar el dominio de
  órdenes.

### II. Integridad del Dominio

- Las invariantes de negocio MUST validarse en el dominio (constructores, fábricas estáticas o
  constructores compactos de records), de modo que no pueda existir un objeto inválido.
  *Por qué*: validar solo en el borde HTTP deja al dominio expuesto a otros puntos de entrada
  (eventos, jobs, tests).
- Los valores monetarios MUST representarse con `BigDecimal` o con un Value Object inmutable
  `Money`; `double` y `float` MUST NOT usarse para dinero.
  *Por qué*: la coma flotante binaria no representa exactamente fracciones decimales y produce
  errores de redondeo en totales e impuestos.
- Los identificadores de negocio (ISBN, Email, etc.) SHOULD modelarse como Value Objects
  inmutables (`record`) con validación propia.
  *Por qué*: evita la "obsesión por primitivos" y centraliza la validación de formato.

### III. Stack Estable y Disciplina de Dependencias

- Runtime: Java 25 (LTS) con Maven Wrapper. Se SHOULD usar features estables del lenguaje
  (records, sealed types, pattern matching, switch expressions).
  *Por qué*: una LTS garantiza soporte prolongado; las features modernas reducen boilerplate.
- Frameworks base: Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Spring MVC) y
  OpenAPI 3 mediante `springdoc-openapi`.
  *Por qué*: alinea el proyecto con la generación actual soportada del ecosistema Spring.
- MUST usarse exclusivamente APIs estables y documentadas oficialmente. APIs deprecadas,
  experimentales, en incubación (`jdk.incubator.*`) o en preview (`--enable-preview`) MUST NOT
  usarse.
  *Por qué*: estas APIs pueden cambiar o desaparecer sin aviso, generando deuda de migración.
- Las dependencias base del `pom.xml` inicial MUST NOT eliminarse.
  *Por qué*: constituyen la línea base acordada del stack sobre la que se diseñan las features.
- Toda dependencia externa nueva MUST justificarse en un ADR en `docs/adr/` que evalúe:
  necesidad concreta, descarte de APIs nativas de Java/Spring, estado de mantenimiento, licencia
  y compatibilidad con Java 25 / Spring Boot 4. Las capacidades nativas del runtime MUST
  priorizarse sobre librerías de terceros.
  *Por qué*: cada dependencia amplía la superficie de ataque, el riesgo de licencia y el coste
  de actualización.
- DTOs, comandos, eventos y Value Objects MUST implementarse como `record` de Java.
  *Por qué*: los records son inmutables y tienen `equals`/`hashCode` correctos por diseño.
- Lombok MAY usarse exclusivamente en entidades JPA. `@Data` MUST NOT usarse en entidades JPA.
  *Por qué*: `@Data` genera `equals`, `hashCode` y `toString` sobre todos los campos, lo que
  rompe la identidad con proxies de Hibernate y dispara cargas lazy o recursión infinita.

### IV. Persistencia Controlada y Contrato de API

- PostgreSQL MUST ser el único motor de base de datos.
  *Por qué*: un solo motor evita divergencias de dialecto entre entornos.
- Todo cambio de esquema MUST realizarse con migraciones versionadas de Flyway. Una migración
  ya aplicada MUST NOT modificarse; las correcciones se hacen con una migración nueva.
  *Por qué*: el esquema debe ser reproducible y el checksum de Flyway detecta alteraciones.
- `spring.jpa.hibernate.ddl-auto` MUST ser `validate` y `spring.jpa.open-in-view` MUST ser
  `false`.
  *Por qué*: Hibernate no debe alterar el esquema gestionado por Flyway, y OSIV oculta consultas
  lazy fuera de la transacción.
- Las consultas N+1 MUST NOT existir; MUST resolverse con `fetch join`, `@EntityGraph` o
  proyecciones.
  *Por qué*: N+1 degrada el rendimiento de forma lineal con el volumen de datos.
- Las entidades JPA MUST NOT exponerse fuera de su módulo ni en la API HTTP. Toda entrada y
  salida HTTP MUST usar DTOs `record` mapeados con MapStruct.
  *Por qué*: desacopla el contrato público del modelo de persistencia y evita fugas de datos.
- Las rutas MUST versionarse bajo `/api/v1` y usar sustantivos en plural.
  *Por qué*: permite evolucionar el contrato sin romper al cliente SPA.
- Los errores MUST unificarse en un `@RestControllerAdvice` que retorne `ProblemDetail`
  (RFC 9457). Stack traces, mensajes de excepción internos y detalles de SQL MUST NOT exponerse.
  *Por qué*: un formato estándar simplifica el cliente y ocultar internals evita filtrar
  información útil para un atacante.
- Todo listado MUST paginarse y MUST imponer un tamaño máximo de página.
  *Por qué*: listados ilimitados permiten agotar memoria y ancho de banda.
- `@Transactional` MUST declararse en la capa de aplicación, con `readOnly = true` en
  operaciones de consulta.
  *Por qué*: los casos de uso definen la unidad de trabajo; `readOnly` habilita optimizaciones
  de Hibernate y del driver.
- Los paquetes MUST anotarse con JSpecify (`@NullMarked`, `@Nullable` donde aplique). Servicios
  y repositorios MUST NOT retornar `null` para colecciones (retornan colecciones vacías) y MUST
  usar `Optional<T>` para resultados únicos opcionales.
  *Por qué*: hace explícita la nulabilidad en el contrato y elimina `NullPointerException`
  evitables.

### V. Seguridad por Defecto

- La autenticación MUST ser stateless con JWT usando exclusivamente el soporte nativo de Spring
  Security (`oauth2-resource-server`, `NimbusJwtEncoder`/`JwtDecoder`). Librerías JWT de terceros
  y filtros de servlet manuales para autenticación MUST NOT usarse.
  *Por qué*: el soporte nativo está auditado y mantenido; las implementaciones manuales son una
  fuente habitual de vulnerabilidades.
- Los access tokens MUST tener vida corta (~15 minutos) y viajar en `Authorization: Bearer`.
  *Por qué*: limita la ventana de abuso de un token filtrado.
- Los refresh tokens MUST ser opacos, persistirse hasheados (nunca en claro) para permitir
  revocación, y transmitirse en cookies `HttpOnly`, `Secure` y con `SameSite` explícito.
  *Por qué*: el hash protege ante fuga de la base de datos y la cookie `HttpOnly` impide su
  robo vía XSS.
- Las contraseñas MUST almacenarse con un `PasswordEncoder` delegante (bcrypt por defecto).
  *Por qué*: permite migrar de algoritmo sin invalidar hashes existentes.
- La `SecurityFilterChain` MUST denegar por defecto (deny-by-default) y la autorización de
  negocio MUST reforzarse con `@PreAuthorize` a nivel de método.
  *Por qué*: un endpoint olvidado queda cerrado, no abierto.
- Los roles MUST ser `CUSTOMER` y `ADMIN`. El acceso anónimo MUST limitarse a lectura del
  catálogo y de reseñas públicas.
  *Por qué*: un modelo de roles mínimo es fácil de auditar.
- Un usuario autenticado MUST NOT leer ni modificar recursos de otro usuario (carrito, órdenes,
  favoritos). La identidad MUST obtenerse del token, nunca de parámetros del cliente.
  *Por qué*: previene vulnerabilidades IDOR/BOLA, primera categoría del OWASP API Top 10.

### VI. Calidad Verificable

- Las pruebas unitarias de dominio MUST ejecutarse sin contexto de Spring ni base de datos.
  *Por qué*: feedback en milisegundos y aislamiento de la lógica de negocio.
- Los controladores y su autorización MUST probarse con `@WebMvcTest`; las consultas de
  persistencia con `@DataJpaTest`.
  *Por qué*: los slice tests validan cada capa con un contexto mínimo y rápido.
- Los contratos de módulo MUST probarse con `@ApplicationModuleTest` y la arquitectura con
  `ApplicationModules.verify()`.
  *Por qué*: garantiza que cada módulo funciona aislado y que los límites del Principio I se
  respetan.
- Los tests que requieran base de datos MUST usar Testcontainers con PostgreSQL real vía
  `@ServiceConnection`. Bases de datos en memoria (p. ej. H2) MUST NOT usarse.
  *Por qué*: H2 difiere de PostgreSQL en dialecto, tipos y constraints, y oculta errores reales.
- Los mocks MUST restringirse a fronteras de I/O externas (pasarela simulada, `Clock`).
  Entidades, records y Value Objects MUST NOT mockearse.
  *Por qué*: mockear el dominio prueba el mock, no el comportamiento.
- La cobertura de líneas en las capas de dominio y aplicación MUST ser ≥ 80% medida con JaCoCo,
  y el build de Maven MUST fallar si no se alcanza.
  *Por qué*: un umbral automático impide que la cobertura se degrade silenciosamente.
- La compilación MUST producir cero advertencias con `-Xlint:all`, tratándolas como errores.
  *Por qué*: las advertencias ignoradas ocultan defectos reales (raw types, deprecaciones).

### VII. Pragmatismo Arquitectónico, SOLID y Control de Complejidad

- **Simplicidad deliberada (KISS y YAGNI)**: toda solución MUST ser la más simple que resuelva
  el problema actual sin comprometer las invariantes del dominio (Principio II). Diseñar para
  requisitos futuros hipotéticos que no estén explícitos en el `spec.md` activo MUST NOT
  hacerse.
  *Por qué*: el código especulativo encarece el mantenimiento y dificulta la lectura sin
  aportar valor verificable hoy.
- **Sin interfaces especulativas**: los servicios y componentes internos de un módulo MUST
  implementarse como clases concretas. Se MUST NOT extraer una interfaz (patrón
  `IUserService` → `UserServiceImpl`) salvo que se cumpla al menos una condición:
  1. Existen 2 o más implementaciones reales en el código actual.
  2. Es un puerto de salida hacia infraestructura externa o simulada (p. ej. `PaymentGateway`).
  3. Es la fachada pública del módulo, expuesta a otros módulos según Spring Modulith.

  Quedan exentas las interfaces que exige el propio framework (repositorios de Spring Data,
  mappers de MapStruct).
  *Por qué*: una interfaz con una sola implementación agrega indirección y archivos sin
  aportar polimorfismo; los mocks de I/O no la requieren (Principio VI).
- **SRP basado en cohesión, no en fragmentación**: una clase SHOULD NOT dividirse solo por su
  tamaño si toda su lógica cambia por la misma razón de negocio. Se MUST preferir una clase
  cohesiva (p. ej. 150 líneas) antes que muchas clases diminutas (p. ej. 6 de 25 líneas) que
  obliguen a saltar entre archivos para entender un único flujo.
  *Por qué*: SRP se refiere a razones de cambio, no a número de líneas; fragmentar en exceso
  dispersa el flujo y reduce la legibilidad.
- **OCP y DIP basados en evidencia**: puntos de extensión, jerarquías o inversiones de
  dependencia no triviales MUST NOT introducirse hasta que el mismo cambio o duplicación se
  haya presentado en al menos 2 escenarios reales, o cuando lo exija una frontera de módulo
  (Principio I).
  *Por qué*: una abstracción extraída de casos reales acierta en la forma; una extraída de
  casos imaginados suele equivocarse y hay que deshacerla.
- **Patrones de diseño (GoF) restringidos**: un patrón MUST NOT aplicarse por defecto y MAY
  usarse solo cuando elimine complejidad accidental evidente en el problema actual.
  *Por qué*: un patrón aplicado sin necesidad es complejidad accidental en sí mismo.
- **Construcciones nativas antes que patrones verbosos**: se MUST preferir las construcciones
  de Java 25 (`record`, `sealed interface`, `switch` con pattern matching, funciones de orden
  superior) sobre patrones clásicos equivalentes. Cuando las variantes sean cerradas y
  conocidas, se MUST usar `sealed` + pattern matching en lugar de *Visitor* o *Strategy*.
  *Por qué*: el compilador verifica la exhaustividad del `switch` sobre tipos sellados, con
  menos código y sin jerarquías auxiliares.
- **Trade-offs obligatorios en `plan.md`**: toda abstracción, patrón de diseño o división de
  clases que proponga un `plan.md` MUST documentar brevemente:
  1. **Problema real que resuelve hoy**.
  2. **Alternativa simple descartada** y por qué una clase o un método directo no basta.
  3. **Costo aceptado**: indirección agregada frente al beneficio obtenido.

  *Por qué*: obliga a hacer explícito el razonamiento y permite rechazar en revisión la
  complejidad que no se justifica.

## Higiene Operacional

- Credenciales, secretos, claves de firma y tokens MUST NOT versionarse; MUST inyectarse
  mediante variables de entorno.
  *Por qué*: un secreto en el historial de Git se considera comprometido para siempre.
- Todo DTO de entrada MUST validarse con Bean Validation (`@Valid`) antes de alcanzar la capa
  de aplicación.
  *Por qué*: rechaza datos malformados en el borde con errores consistentes.
- CORS MUST configurarse explícitamente hacia el/los origen(es) del cliente web; el wildcard
  `*` MUST NOT usarse.
  *Por qué*: con credenciales (cookies) un origen abierto expone la API a sitios maliciosos.
- Actuator MUST exponer únicamente `health` e `info`.
  *Por qué*: endpoints como `env`, `heapdump` o `configprops` filtran información sensible.
- El logging MUST usar el formato estructurado nativo de Spring Boot. Contraseñas, tokens y PII
  MUST NOT registrarse.
  *Por qué*: logs estructurados son consultables; los logs con datos sensibles son una fuga.

## Flujo de Desarrollo y Definition of Done

- Ningún cambio de código MUST integrarse sin su `spec.md`, `plan.md` y `tasks.md` aprobados
  (flujo Specification-Driven Development con Spec Kit).
  *Por qué*: la especificación previa hace que la implementación sea verificable contra un
  objetivo explícito.
- El `plan.md` de cada feature MUST incluir una verificación de cumplimiento ("Constitution
  Check") contra estos principios, incluida la evaluación de trade-offs del Principio VII;
  toda desviación MUST justificarse o eliminarse.
  *Por qué*: detecta incumplimientos en diseño, antes de que sean costosos.
- Una tarea se considera terminada (DoD) solo si: compila sin advertencias, todos los tests
  pasan (incluida `ApplicationModules.verify()`), se cumple el umbral de cobertura, la API
  afectada queda documentada en OpenAPI y no se viola ninguna regla de esta Constitución.
  *Por qué*: un criterio de cierre objetivo evita trabajo "casi terminado".

## Gobernanza

- **Precedencia**: esta Constitución prevalece sobre cualquier otra práctica, guía o artefacto
  del repositorio (ver *Metadatos y Jerarquía Normativa*).
- **Enmiendas**: toda modificación MUST realizarse en un Pull Request dedicado que justifique
  la decisión. Si la enmienda afecta la arquitectura o las dependencias, MUST acompañarse de un
  ADR formal en `docs/adr/`.
  *Por qué*: deja trazabilidad del razonamiento detrás de cada cambio normativo.
- **Versionado semántico** (MUST):
  - **MAJOR**: modificación, relajación o eliminación de principios o reglas existentes.
  - **MINOR**: incorporación de nuevos principios o reglas invariantes.
  - **PATCH**: correcciones tipográficas o aclaraciones de redacción sin impacto normativo.
- **Registro**: el encabezado MUST mantener actualizados Versión, Estado y Fecha de última
  enmienda (formato ISO `YYYY-MM-DD`).
- **Cumplimiento**: toda revisión de código (humana o mediante el agente `spec-reviewer`) MUST
  verificar el cumplimiento de esta Constitución. Cualquier complejidad adicional MUST
  justificarse explícitamente en el `plan.md` correspondiente, según el Principio VII.

**Versión**: 1.1.0 | **Estado**: Activo | **Ratificada**: 2026-11-05 | **Última enmienda**: 2026-11-05
