# ADR-004: Infraestructura local en desarrollo con Docker Compose

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

Una persona desarrolladora debe poder arrancar la aplicación en local con un único comando y
sin instalar PostgreSQL en su máquina (US1, FR-003, FR-004). La base de datos debe persistir
entre reinicios para poder trabajar con datos reales de desarrollo, y su ciclo de vida
(arranque y parada) no debe ser manual.

## Alternativas consideradas

1. **Instalación manual de PostgreSQL**: viola la US1, ya que exige instalar y configurar el
   motor en cada máquina, con riesgo de desviación de versión respecto a la usada en pruebas.
2. **`TestApiApplication` con Testcontainers como modo de desarrollo**: la base de datos se
   pierde en cada reinicio, por lo que no sirve para trabajar con datos persistentes. Se
   conserva solo como utilidad.
3. **Script `docker compose up` aparte** (opción sin dependencia extra): son dos comandos y el
   ciclo de vida del contenedor queda en manos de la persona desarrolladora.

## Decisión

Se añade `org.springframework.boot:spring-boot-docker-compose` con `<optional>true</optional>`
y sin versión explícita (la gestiona el parent `spring-boot-starter-parent` 4.1.2). Se añade un
`compose.yaml` en la raíz del repositorio con un servicio `postgres` basado en la imagen
`postgres:18`, y se mantiene el `lifecycle-management` por defecto (`start-and-stop`).

Con ello, `./mvnw spring-boot:run` ejecuta `docker compose up` al arrancar, crea la
`ServiceConnection` sin configurar URL ni credenciales y ejecuta `docker compose stop` al
apagar. La imagen `postgres:18` es la misma que la fijada en Testcontainers (research R4).

## Compatibilidad

- **Spring Boot 4.1.x**: es un módulo oficial del propio Spring Boot, versionado y gestionado
  por el parent 4.1.2, por lo que su versión siempre coincide con la del framework. La versión
  4.1.2 está publicada en Maven Central.
- **Java 25**: la documentación oficial (System Requirements, https://docs.spring.io/spring-boot/system-requirements.html)
  indica que Spring Boot 4.1.2 requiere al menos Java 17 y es compatible hasta Java 27
  inclusive, lo que cubre Java 25. Es una afirmación sobre el framework, no específica del módulo.
- **Docker**: requiere el CLI `docker compose` (Compose v2) y un daemon Docker disponible.

## Estado de mantenimiento

Verificado el 2026-11-05 en Maven Central
(`repo1.maven.org/maven2/org/springframework/boot/spring-boot-docker-compose`):

- **Última release**: `4.1.2` es la última versión estable de la línea 4.1 (publicada el
  2026-10-22). La última versión del metadato es `4.2.0-RC2` (2026-11-05), una release candidate
  que no se usa.
- **Cadencia**: sigue la de Spring Boot, con releases de parche mensuales o bimensuales y
  versiones menores cada pocos meses. Fechas (última modificación del POM): 4.0.0 el 2025-11-20;
  4.0.5 el 2026-03-26; 4.1.0-RC1 el 2026-04-23; 4.1.0 y 4.0.7 el 2026-06-10; 4.1.1 y 4.0.8
  el 2026-08-20; 4.1.2 y 4.0.9 el 2026-10-22. La línea 4.0 sigue recibiendo parches en paralelo.
- **Licencia**: Apache License, Version 2.0 (declarada en el POM de 4.1.2).

## Consecuencias

- Requiere Docker instalado y en ejecución en la máquina de desarrollo.
- Por defecto se omite en tests y no se incluye en el jar reempaquetado, al declararse
  `optional`.
- La imagen `postgres:18` es idéntica a la de Testcontainers, lo que evita diferencias de
  versión entre desarrollo y pruebas.
- Al apagar la aplicación el contenedor se detiene pero no se elimina, así que los datos
  persisten entre reinicios.
- `TestApiApplication` con Testcontainers queda como utilidad, no como modo de desarrollo.
