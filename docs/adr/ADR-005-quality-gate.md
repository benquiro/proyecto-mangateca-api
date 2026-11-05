# ADR-005: Puerta de calidad (JaCoCo y compilación sin advertencias)

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

La constitución exige una puerta de calidad automática en el build. Dos requisitos funcionales la concretan:

- **FR-013**: el build debe fallar si la cobertura de líneas de las capas de dominio y aplicación es inferior al 80%.
- **FR-014**: el build debe fallar si la compilación produce cualquier advertencia.

Hace falta un mecanismo que mida la cobertura en el propio `./mvnw verify` y que convierta las advertencias del compilador en errores, sin depender de revisión humana.

## Alternativas consideradas

1. **Revisión manual sin umbral (opción nativa, sin dependencia extra)**: no hay herramienta; el revisor decide. Descartada: no es automática, no es reproducible y no hace fallar el build, por lo que no cumple FR-013 ni FR-014. Para las advertencias, el compilador nativo (`javac -Xlint:all` con `failOnWarning`) sí cubre FR-014 sin dependencias; se adopta.
2. **Herramientas SaaS externas** (servicios de cobertura y análisis en la nube): añaden dependencia de un servicio de terceros, cuentas y tokens, y el umbral se evalúa fuera del build local. Descartadas: el fallo debe ocurrir en `./mvnw verify`, sin red ni credenciales.
3. **Otros plugins de cobertura para Maven**: no se evaluaron en detalle; se descartan por no aportar ventajas frente a JaCoCo para este alcance y por ser JaCoCo el estándar de facto para JVM con integración directa en el ciclo de vida de Maven.

## Decisión

- **`org.jacoco:jacoco-maven-plugin` 0.8.16**, con la versión declarada en una propiedad de `pom.xml` (se incorpora en T052). Goals `prepare-agent`, `report` y `check`, enlazados a la fase `verify`.
  - Regla `check`: elemento `BUNDLE`, contador `LINE`, valor `COVEREDRATIO`, mínimo `0.80`.
  - Alcance (`includes`): `com/mangateca/api/**/domain/**` y `com/mangateca/api/**/application/**`.
- **`maven-compiler-plugin`** con `<compilerArgs><arg>-Xlint:all,-processing</arg></compilerArgs>` y `<failOnWarning>true</failOnWarning>`, en las ejecuciones `default-compile` y `default-testCompile` (el mismo flag en ambas, porque la advertencia se reproduce en las dos), conservando `annotationProcessorPaths`. Es la única categoría excluida; `-Xlint:all` sigue activo para el resto y `failOnWarning` no cambia. Ver la enmienda del 2026-11-05 en Consecuencias.

### Exclusiones documentadas

Se miden solo dominio y aplicación, según la constitución. Quedan fuera de la regla, sin necesidad de lista de exclusión explícita, las clases de arranque (`ApiApplication`) y de configuración, por estar fuera de los paquetes `domain` y `application`. La plataforma (web, seguridad) se verifica con slice tests e integración, no con este umbral.

### Política ante advertencias de terceros

Si una advertencia procede de una librería o procesador de anotaciones de terceros, no se suprime de forma global (no se desactiva `-Xlint:all` ni `failOnWarning`). Se aplica la supresión más acotada posible (por ejemplo, `@SuppressWarnings` sobre el elemento concreto o la exclusión de una única categoría de lint) y se documenta aquí con su justificación. Nunca se excluye en silencio.

## Compatibilidad

- **Java 25**: 0.8.16 es la última versión estable publicada en Maven Central (`maven-metadata.xml`: `latest` y `release` = 0.8.16, última actualización 2026-10-26, verificado el 2026-11-05) y, según research R14, soporta el bytecode de Java 25 (versión de clase 69). El soporte concreto de bytecode 69 no se verificó contra las notas de versión del proyecto; se confirma en la primera ejecución de `./mvnw verify`.
- **Spring Boot 4.1.x**: JaCoCo es un plugin de build y un agente de JVM; no depende de Spring Boot. `-Xlint` y `failOnWarning` son opciones estándar de `maven-compiler-plugin` y `javac`.
- **`--release 25`**: con `--release 25` no aparecieron advertencias de `-Xlint:options` (T010).

## Estado de mantenimiento

- **Última release**: 0.8.16, publicada en Maven Central el 2026-10-26 (fecha del POM).
- **Cadencia**: releases previas 0.8.13 (2025-04-02), 0.8.14 (2025-10-11) y 0.8.15 (2026-06-04), es decir, varias releases por año.
- **Licencia**: Eclipse Public License 2.0 (EPL-2.0), según la cabecera del POM publicado.
- No se ha verificado el estado del repositorio oficial del proyecto (actividad de commits, incidencias abiertas).

## Consecuencias

- En esta feature no existen clases de dominio ni de aplicación, por lo que la regla `check` se evalúa sobre un conjunto vacío. Su eficacia se demuestra con el procedimiento temporal de `quickstart.md` §4b (clase `com.mangateca.api.demo.domain.Untested` sin tests, que debe hacer fallar `jacoco:check` con `lines covered ratio is 0.00, but expected minimum is 0.80`). La primera feature de negocio la ejercita de verdad.
- Cualquier advertencia de compilación, incluida la de código de test, rompe el build.
- El informe de cobertura se genera en `target/site/jacoco/index.html`.
- **Riesgo: `-Xlint:processing` (materializado en T010)**. Con `-Xlint:all` javac emite en ambas ejecuciones, con `--release 25`:
  - `default-compile`: `No processor claimed any of these annotations: /org.springframework.boot.autoconfigure.SpringBootApplication`
  - `default-testCompile`: `No processor claimed any of these annotations: /org.springframework.boot.test.context.TestConfiguration,/org.springframework.context.annotation.Bean,/org.junit.jupiter.api.Test,/org.springframework.context.annotation.Import,/org.springframework.boot.testcontainers.service.connection.ServiceConnection,/org.springframework.boot.test.context.SpringBootTest`
  - **Justificación de la exclusión**: la advertencia es esperada y no accionable. Los procesadores configurados (Lombok, MapStruct) no reclaman anotaciones de Spring ni de JUnit/Testcontainers. Javac no admite suprimir esta advertencia por anotación en el código (`@SuppressWarnings`), y las anotaciones de Spring/JUnit son imprescindibles.
  - **Alcance**: solo se excluye la categoría `processing` (`-Xlint:all,-processing`), en las dos ejecuciones donde se reproduce. Con esa exclusión `./mvnw clean test-compile` compila sin advertencias de javac.
  - **Enmienda 2026-11-05**: se sustituye `-Xlint:all` por `-Xlint:all,-processing` en `default-compile` y `default-testCompile`. El Estado sigue en Aceptado.
  - Compatibilidad de MapStruct con `--release 25` (T010): la compilación no falló, pero aún no existen mappers, así que no está demostrada con uso real.
