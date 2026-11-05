# Constitución de Mangateca API

Palabras clave MUST, MUST NOT, SHOULD, SHOULD NOT, MAY según RFC 2119.

## Jerarquía Normativa

- Esta Constitución prevalece sobre `spec.md`, `plan.md`, `tasks.md`, ADR y código existente.
- Todo artefacto que la contradiga MUST considerarse inválido y corregirse antes de integrarse;
  el conflicto MUST NOT resolverse reinterpretando la Constitución.
- Sistema: backend REST de e-commerce consumido por un cliente SPA desacoplado.

## Core Principles

### I. Monolito Modular con Límites Verificados

- MUST desplegarse como un único monolito modular.
- El código MUST organizarse en módulos de dominio vertical; capas técnicas horizontales globales
  (`controllers/`, `services/`, `repositories/` en raíz) MUST NOT existir.
- Los límites MUST verificarse con Spring Modulith (`ApplicationModules.of(...).verify()`); un
  fallo MUST romper el build.
- Un módulo MUST NOT acceder a repositorios, entidades JPA ni tipos internos de otro. La
  interacción MUST ocurrir solo vía la API pública del módulo (paquete raíz o expuesta
  explícitamente) o eventos de dominio vía `ApplicationEventPublisher`.
- Integraciones de terceros no implementadas MUST modelarse como puerto de dominio
  (p. ej. `PaymentGateway`) con implementación simulada desacoplada.

### II. Integridad del Dominio

- Las invariantes MUST validarse en el dominio (constructores, fábricas estáticas, constructores
  compactos de records); un objeto inválido MUST NOT poder existir.
- Dinero MUST representarse con `BigDecimal` o un VO inmutable `Money`; `double`/`float` MUST NOT.
- Identificadores de negocio (ISBN, Email, etc.) SHOULD ser VO `record` con validación propia.

### III. Stack Estable y Disciplina de Dependencias

- Runtime: Java 25 LTS con Maven Wrapper. Base: Spring Boot 4.1.x (Spring Framework 7,
  Security 7, MVC) y OpenAPI 3 vía `springdoc-openapi`.
- Solo APIs estables y documentadas. APIs deprecadas, experimentales, `jdk.incubator.*` o preview
  (`--enable-preview`) MUST NOT usarse.
- Las dependencias base del `pom.xml` inicial MUST NOT eliminarse.
- Toda dependencia externa nueva MUST justificarse en un ADR (`docs/adr/`) evaluando: necesidad,
  descarte de APIs nativas Java/Spring, mantenimiento, licencia y compatibilidad Java 25/Boot 4.
  Las capacidades nativas MUST priorizarse.
- DTOs, comandos, eventos y VOs MUST ser `record`.
- Lombok MAY usarse solo en entidades JPA; `@Data` MUST NOT usarse en ellas.

### IV. Persistencia y Contrato de API

- **Persistencia**:
  - PostgreSQL MUST ser el único motor.
  - Cambios de esquema MUST hacerse con migraciones Flyway versionadas; una migración aplicada
    MUST NOT modificarse (se corrige con una nueva).
  - `spring.jpa.hibernate.ddl-auto=validate` y `spring.jpa.open-in-view=false` MUST.
  - Consultas N+1 MUST NOT existir; resolver con `fetch join`, `@EntityGraph` o proyecciones.
  - `@Transactional` MUST declararse en la capa de aplicación, con `readOnly = true` en consultas.
- **Contrato HTTP**:
  - Entidades JPA MUST NOT exponerse fuera de su módulo ni en HTTP; entrada/salida MUST usar DTOs
    `record` mapeados con MapStruct.
  - Rutas MUST versionarse bajo `/api/v1` con sustantivos en plural.
  - Errores MUST unificarse en un `@RestControllerAdvice` que retorne `ProblemDetail` (RFC 9457);
    stack traces, mensajes internos y detalles SQL MUST NOT exponerse.
  - Todo listado MUST paginarse con tamaño máximo de página.
- **Nulabilidad**: paquetes MUST anotarse con JSpecify (`@NullMarked`, `@Nullable`). Servicios y
  repositorios MUST NOT retornar `null` para colecciones y MUST usar `Optional<T>` para
  resultados únicos opcionales.

### V. Seguridad por Defecto

- Autenticación MUST ser stateless con JWT usando solo soporte nativo de Spring Security
  (`oauth2-resource-server`, `NimbusJwtEncoder`/`JwtDecoder`). Librerías JWT de terceros y
  filtros servlet manuales de autenticación MUST NOT usarse.
- Access tokens MUST durar ~15 min y viajar en `Authorization: Bearer`.
- Refresh tokens MUST ser opacos, persistirse hasheados (nunca en claro, revocables) y viajar en
  cookies `HttpOnly`, `Secure` y `SameSite` explícito.
- Contraseñas MUST almacenarse con `PasswordEncoder` delegante (bcrypt por defecto).
- `SecurityFilterChain` MUST ser deny-by-default; autorización de negocio MUST reforzarse con
  `@PreAuthorize` a nivel de método.
- Roles MUST ser `CUSTOMER` y `ADMIN`. Acceso anónimo MUST limitarse a lectura de catálogo y
  reseñas públicas.
- Un usuario MUST NOT leer ni modificar recursos de otro (anti IDOR/BOLA); la identidad MUST
  obtenerse del token, nunca de parámetros del cliente.

### VI. Calidad Verificable (Testing Pragmático)

- **Alcance**: se MUST testear solo lo vital; cobertura exhaustiva MUST NOT exigirse. No existe
  umbral de cobertura obligatorio (JaCoCo u otro).
- **Tests obligatorios** (MUST):
  1. Arquitectura: `ApplicationModules.verify()` y un smoke test de arranque del contexto.
  2. Invariantes de dominio con lógica de negocio real (dinero, stock, totales, estados de
     orden), como tests unitarios sin Spring ni BD.
  3. Seguridad crítica: deny-by-default, roles y propiedad de recursos (anti IDOR/BOLA).
  4. Flujo feliz de cada endpoint crítico de negocio (auth, carrito, checkout/orden).
- **Tests opcionales** (MAY): CRUD trivial, getters/mappers, DTOs, validaciones simples, casos
  borde no críticos y `@ApplicationModuleTest` por módulo. `tasks.md` SHOULD NOT generarlos
  salvo petición explícita en `spec.md`.
- **Herramientas** (cuando se escriba el test): web/autorización con `@WebMvcTest`, consultas con
  `@DataJpaTest`. Tests con BD MUST usar Testcontainers PostgreSQL vía `@ServiceConnection`; BD en
  memoria (H2, etc.) MUST NOT.
- Mocks MUST restringirse a fronteras de I/O externas (pasarela simulada, `Clock`); entidades,
  records y VOs MUST NOT mockearse.
- Compilación MUST tener cero advertencias con `-Xlint:all`, tratadas como errores.

### VII. Pragmatismo Arquitectónico (KISS / YAGNI)

- **Simplicidad**: toda solución MUST ser la más simple que resuelva el problema actual sin
  romper invariantes (II). Diseñar para requisitos no explícitos en el `spec.md` activo MUST NOT.
- **Cero interfaces especulativas**: servicios y componentes internos MUST ser clases concretas.
  Una interfaz (p. ej. `IUserService` → `UserServiceImpl`) MUST NOT extraerse salvo que sea:
  1. Fachada pública del módulo expuesta a otros módulos (Spring Modulith).
  2. Puerto de salida a infraestructura externa o simulada (p. ej. `PaymentGateway`).
  3. Contrato con ≥ 2 implementaciones reales existentes en el código.
  Exentas: interfaces exigidas por el framework (Spring Data, MapStruct).
- **SRP por cohesión**: una clase SHOULD NOT dividirse por tamaño si su lógica cambia por una
  misma razón de negocio; se MUST preferir una clase cohesiva sobre fragmentos diminutos.
- **OCP/DIP por evidencia**: puntos de extensión, jerarquías o inversiones de dependencia no
  triviales MUST NOT introducirse hasta que el cambio o duplicación se repita en ≥ 2 escenarios
  reales, o lo exija una frontera de módulo (I).
- **Patrones GoF**: MUST NOT aplicarse por defecto; MAY usarse solo si eliminan complejidad
  accidental evidente del problema actual.
- **Nativo antes que patrón**: patrones especulativos MUST NOT usarse cuando `record`,
  `sealed interface`, `switch` con pattern matching o funciones de orden superior de Java 25
  resuelvan el problema. Variantes cerradas y conocidas MUST modelarse con `sealed` + pattern
  matching, no con *Visitor* ni *Strategy*.
- **Trade-off obligatorio**: toda abstracción, patrón o división de clases en `plan.md` MUST
  documentar: (1) problema real que resuelve hoy; (2) alternativa simple descartada y por qué no
  basta; (3) costo de indirección aceptado frente al beneficio.

## Higiene Operacional

- Secretos, credenciales, claves de firma y tokens MUST NOT versionarse; MUST inyectarse por
  variables de entorno.
- Todo DTO de entrada MUST validarse con Bean Validation (`@Valid`) antes de la capa de
  aplicación.
- CORS MUST configurarse explícitamente hacia los orígenes del cliente; `*` MUST NOT usarse.
- Actuator MUST exponer solo `health` e `info`.
- Logging MUST usar el formato estructurado nativo de Spring Boot; contraseñas, tokens y PII
  MUST NOT registrarse.

## Flujo de Desarrollo y Definition of Done

- Ningún cambio de código MUST integrarse sin `spec.md`, `plan.md` y `tasks.md` aprobados
  (Spec Kit / SDD).
- Cada `plan.md` MUST incluir un "Constitution Check" contra estos principios, incluidos los
  trade-offs de VII; toda desviación MUST justificarse o eliminarse.
- DoD: compila sin advertencias, la app arranca, los tests obligatorios de VI pasan (incl.
  `ApplicationModules.verify()`), API afectada documentada en OpenAPI y ninguna regla violada.

## Governance

- **Precedencia**: ver *Jerarquía Normativa*.
- **Enmiendas**: MUST realizarse en un PR dedicado que justifique la decisión; si afectan
  arquitectura o dependencias, MUST acompañarse de un ADR en `docs/adr/`.
- **Versionado semántico** (MUST): MAJOR = modificar, relajar o eliminar reglas; MINOR = añadir
  principios o reglas; PATCH = redacción o aclaraciones sin impacto normativo.
- **Registro**: la línea de versión MUST mantener Versión, Estado y fechas en ISO `YYYY-MM-DD`.
- **Cumplimiento**: toda revisión de código (humana o agente `spec-reviewer`) MUST verificar esta
  Constitución; toda complejidad adicional MUST justificarse en `plan.md` según VII.

**Versión**: 2.0.0 | **Estado**: Activo | **Ratificada**: 2026-11-05 | **Última enmienda**: 2026-11-05
