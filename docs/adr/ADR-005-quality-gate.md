# ADR-005: Puerta de calidad (compilación sin advertencias)

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

La constitución exige una puerta de calidad automática en el build, concretada en **FR-014**: el build debe fallar si la compilación produce cualquier advertencia. Hace falta convertir las advertencias del compilador en errores dentro de `./mvnw verify`, sin depender de revisión humana.

El requisito original de cobertura (FR-013, ≥ 80% en dominio y aplicación) se retiró el 2026-11-05 con la constitución v2.0.0 (testing pragmático, sin umbral obligatorio). Ver la enmienda en Consecuencias.

## Alternativas consideradas

1. **Compilador nativo (`javac -Xlint:all` con `failOnWarning`), sin dependencia extra**: cubre FR-014 dentro del build. Se adopta.
2. **Herramientas SaaS externas de análisis**: añaden un servicio de terceros, cuentas y tokens, y evalúan fuera del build local. Descartadas.
3. **JaCoCo 0.8.16 con umbral de cobertura**: era la decisión original para FR-013. Descartado el 2026-11-05: la constitución v2.0.0 ya no exige cobertura y JaCoCo nunca llegó a añadirse al `pom.xml`.

## Decisión

- **`maven-compiler-plugin`** con `<compilerArgs><arg>-Xlint:all,-processing</arg></compilerArgs>` y `<failOnWarning>true</failOnWarning>`, en las ejecuciones `default-compile` y `default-testCompile` (el mismo flag en ambas, porque la advertencia se reproduce en las dos), conservando `annotationProcessorPaths`. Es la única categoría excluida; `-Xlint:all` sigue activo para el resto y `failOnWarning` no cambia. Ver la enmienda del 2026-11-05 en Consecuencias.

### Política ante advertencias de terceros

Si una advertencia procede de una librería o procesador de anotaciones de terceros, no se suprime de forma global (no se desactiva `-Xlint:all` ni `failOnWarning`). Se aplica la supresión más acotada posible (por ejemplo, `@SuppressWarnings` sobre el elemento concreto o la exclusión de una única categoría de lint) y se documenta aquí con su justificación. Nunca se excluye en silencio.

## Compatibilidad

- **Java 25 / `--release 25`**: `-Xlint` y `failOnWarning` son opciones estándar de `javac` y `maven-compiler-plugin`; no aparecieron advertencias de `-Xlint:options` (T010).
- **Spring Boot 4.1.x**: independiente; la versión de `maven-compiler-plugin` la gestiona el parent.

## Estado de mantenimiento

- Sin dependencia nueva: `maven-compiler-plugin` lo gestiona el parent de Spring Boot 4.1.2 (licencia Apache-2.0).

## Consecuencias

- Cualquier advertencia de compilación, incluida la de código de test, rompe el build.
- **Verificado (T053, 2026-11-05)**: un raw type (`List l = new ArrayList();`) en una clase de
  `src/main` hace fallar `./mvnw compile` con `found raw type: java.util.List` y
  `warnings found and -Werror specified`. El cambio se revirtió y el build vuelve a pasar.
- **Riesgo: `-Xlint:processing` (materializado en T010)**. Con `-Xlint:all` javac emite en ambas ejecuciones, con `--release 25`:
  - `default-compile`: `No processor claimed any of these annotations: /org.springframework.boot.autoconfigure.SpringBootApplication`
  - `default-testCompile`: `No processor claimed any of these annotations: /org.springframework.boot.test.context.TestConfiguration,/org.springframework.context.annotation.Bean,/org.junit.jupiter.api.Test,/org.springframework.context.annotation.Import,/org.springframework.boot.testcontainers.service.connection.ServiceConnection,/org.springframework.boot.test.context.SpringBootTest`
  - **Justificación de la exclusión**: la advertencia es esperada y no accionable. Los procesadores configurados (Lombok, MapStruct) no reclaman anotaciones de Spring ni de JUnit/Testcontainers. Javac no admite suprimir esta advertencia por anotación en el código (`@SuppressWarnings`), y las anotaciones de Spring/JUnit son imprescindibles.
  - **Alcance**: solo se excluye la categoría `processing` (`-Xlint:all,-processing`), en las dos ejecuciones donde se reproduce. Con esa exclusión `./mvnw clean test-compile` compila sin advertencias de javac.
  - **Enmienda 2026-11-05**: se sustituye `-Xlint:all` por `-Xlint:all,-processing` en `default-compile` y `default-testCompile`. El Estado sigue en Aceptado.
  - Compatibilidad de MapStruct con `--release 25` (T010): la compilación no falló, pero aún no existen mappers, así que no está demostrada con uso real.
- **Enmienda 2026-11-05 (constitución v2.0.0)**: se retira JaCoCo y el umbral de cobertura del 80% (FR-013 retirado). La puerta de calidad queda solo en compilación sin advertencias. La exclusión `-processing` está permitida de forma explícita por la constitución v3.0.0 (§VI). El Estado sigue en Aceptado.
