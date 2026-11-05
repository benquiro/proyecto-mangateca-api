# ADR-001: Spring Modulith para verificación modular y registro de eventos

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

El monolito modular necesita dos capacidades:

1. Verificar automáticamente, en cada build, los límites entre módulos (la API pública vive
   en la raíz del paquete del módulo y lo interno en subpaquetes; entre módulos solo se usa
   la API pública o eventos de dominio).
2. Registrar de forma persistente y transaccional los eventos de dominio, para que no se
   pierdan si un listener falla después del commit.

La feature de plataforma crea el primer módulo (`platform`) y deja lista la infraestructura.
No hay todavía tablas ni lógica de negocio.

## Alternativas consideradas

- **ArchUnit puro** (sin Modulith): obliga a escribir a mano las reglas de módulos y no
  incluye registro de eventos ni tests por módulo.
- **`ApplicationEventPublisher` nativo, sin registro** (opción nativa sin dependencia extra):
  no añade dependencias, pero los eventos se pierden si el listener falla tras el commit,
  porque no hay registro persistente. Tampoco verifica límites entre módulos.
- **`spring-modulith-starter-jpa`**: también serviría, pero JDBC evita una entidad JPA interna
  adicional y su DDL lo publica el propio proyecto, de modo que lo gestiona Flyway.

## Decisión

Se adopta Spring Modulith **2.1.2**:

- `org.springframework.modulith:spring-modulith-bom` 2.1.2, importado en
  `dependencyManagement` (`<type>pom</type>`, `<scope>import</scope>`).
- `org.springframework.modulith:spring-modulith-starter-jdbc` (scope compile), sin versión.
- `org.springframework.modulith:spring-modulith-starter-test` (scope test), sin versión.

Aporta `ApplicationModules.verify()`, `@ApplicationModuleTest` y un registro de publicación de
eventos transaccional sobre JDBC, sin depender de JPA para los eventos.

## Compatibilidad

- Spring Modulith 2.1.2 está compilado contra Spring Boot 4.1.2, la misma versión del parent
  del `pom.xml` (4.1.x). Dato tomado de la investigación del proyecto (research R1); no se
  pudo confirmar de forma independiente en el POM publicado.
- Java 25: el proyecto compila con `--release 25`; la serie 2.x de Modulith se publica para
  Spring Boot 4. La compatibilidad efectiva se comprueba con `./mvnw verify`.
- Las versiones de Modulith las gestiona el BOM; no se declaran versiones en cada dependencia.

## Estado de mantenimiento

Datos comprobados en Maven Central (`repo.maven.apache.org`) el 2026-11-05:

- **Última release estable de la serie 2.1**: 2.1.2, publicada el 2026-10-22 (cabecera
  `Last-Modified` del POM). La última versión del repositorio es 2.2.0-RC1 (pre-release);
  la 2.0.9 se publicó el mismo día.
- **Cadencia**: releases de corrección frecuentes y releases menores periódicas. 2.0.0 el
  2025-11-21, 2.1.0 el 2026-06-11, 2.1.1 el 2026-08-25 y 2.1.2 el 2026-10-22; hay mantenimiento
  paralelo de varias líneas (2.0.x y 2.1.x) y la 2.2 está en RC.
- **Licencia**: Apache License 2.0 (declarada en el POM del BOM).
- Proyecto oficial del ecosistema Spring (VMware/Broadcom), no de terceros.

## Consecuencias

- La tabla `event_publication` y sus índices los crea **Flyway en V1**, copiando el DDL v2
  oficial de Modulith para PostgreSQL. Así el esquema depende de una migración versionada y
  no de la librería (Principio IV).
- Se fija `spring.modulith.events.jdbc.schema-initialization.enabled=false` para que Modulith
  no cree ni altere la tabla por su cuenta.
- Los módulos se detectan como subpaquetes directos de `com.mangateca.api`; el primero es
  `platform`. Los módulos de negocio se crearán en sus propias features.
- Los tests de arquitectura (`ApplicationModules.verify()`) fallan el build si se violan los
  límites entre módulos.
- Se añade una dependencia de runtime (JDBC) y una de test; las dependencias base del
  `pom.xml` no se eliminan (Principio III).
- Si el DDL del registro cambia en una versión futura de Modulith, habrá que añadir una nueva
  migración Flyway; nunca editar V1.
