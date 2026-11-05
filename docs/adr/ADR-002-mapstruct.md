# ADR-002: Mapeo entidad↔DTO en compilación con MapStruct

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

El Principio IV de la constitución exige que la API exponga DTOs (records) y nunca entidades
JPA, con el mapeo entidad↔DTO realizado mediante MapStruct y verificado en compilación. La
plataforma debe dejar ese mecanismo integrado en el toolchain antes de que existan módulos
funcionales, de modo que los errores de mapeo se detecten al compilar y no en ejecución.

## Alternativas consideradas

- **Mapeo manual en records** (opción nativa, sin dependencia extra): descartada porque el
  Principio IV exige MapStruct y el código manual repetitivo es propenso a omisiones de campos.
- **ModelMapper**: descartada porque resuelve el mapeo por reflexión en runtime y no detecta
  errores en compilación.

## Decisión

Se adopta MapStruct con estos artefactos exactos:

- `org.mapstruct:mapstruct` 1.6.3 (dependencia en ámbito compile).
- `org.mapstruct:mapstruct-processor` 1.6.3 en `annotationProcessorPaths`.
- `org.projectlombok:lombok-mapstruct-binding` 0.2.0 en `annotationProcessorPaths`.

Orden de procesadores en `annotationProcessorPaths`: Lombok, `lombok-mapstruct-binding`,
MapStruct. Las versiones se declaran en propiedades de `pom.xml`: `mapstruct.version` y
`lombok-mapstruct-binding.version`.

## Compatibilidad

- **Java 25**: MapStruct 1.6.3 se publicó antes de Java 25, por lo que su compatibilidad con
  `--release 25` no está documentada por el proyecto y se verifica empíricamente en T010.
- **Spring Boot 4.1.x**: MapStruct es un procesador de anotaciones independiente del framework;
  no depende de Spring Boot.
- `lombok-mapstruct-binding` es necesario para que Lombok y MapStruct convivan en la misma
  compilación.

## Estado de mantenimiento

Verificado en Maven Central el 2026-11-05:

- MapStruct 1.6.3: publicada el 2024-11-09; es la última versión estable (GA) de la serie 1.x.
  Existen 1.7.0.Beta1 y 1.7.0.Beta2 (esta última del 2026-06-27), ambas pre-release y no
  utilizables según el Principio III.
- `lombok-mapstruct-binding` 0.2.0: publicada el 2020-12-04; es la última versión disponible.
  El artefacto es estable y de alcance mínimo, pero sin releases desde entonces.
- Licencia: Apache License 2.0 (declarada en el POM de MapStruct 1.6.3).
- Cadencia de releases de MapStruct: versiones menores/parche con intervalos de varios meses
  (1.6.0 a 1.6.3 en 2024). No se verificó la actividad del repositorio ni la política formal
  de soporte.

## Consecuencias

- No se crea ningún mapper hasta que exista un DTO real (Principio VII).
- Los errores de mapeo se detectan en compilación, junto con `-Xlint:all,-processing` y
  `failOnWarning` (ver ADR-005).
- Se añade un procesador más a la compilación, con orden de procesadores obligatorio.
- **Riesgo**: compatibilidad de MapStruct 1.6.3 con `--release 25`. Se verifica en T010 con la
  build con `failOnWarning` (ver ADR-005); si falla, se reabre este ADR.
