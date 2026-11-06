# ADR-003: Autenticación stateless con OAuth2 Resource Server (JWT)

- **Estado**: Reemplazado parcialmente por ADR-006 (Superseded in part by ADR-006)
- **Fecha**: 2026-11-05

## Reemplazo parcial

ADR-006 (emisión propia de tokens) reemplaza parcialmente esta decisión desde la feature 002.

**Reemplazado por ADR-006**:

- El origen de la clave pública mediante `spring.security.oauth2.resourceserver.jwt.public-key-location`
  (y su variable `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`). Ahora el par de
  claves y el `JwtDecoder` pertenecen a `identity` y se configuran con `MANGATECA_IDENTITY_JWT_*`.
- `DevJwtDecoderConfiguration` (decoder efímero solo en `dev`). Se retira: `identity` genera el par
  efímero en `dev` y `test`.
- La nota «esta feature no emite tokens»: a partir de la feature 002 la API emite sus propios JWT.

**Sigue vigente**:

- Resource Server nativo de Spring Security.
- Firma RS256.
- El starter `spring-boot-starter-security-oauth2-resource-server`.
- Ninguna librería JWT de terceros.

El resto del texto se conserva como registro histórico de la feature 001.

## Contexto y problema

La API MUST autenticar cada petición de forma stateless con tokens bearer (JWT) que emitirá el
futuro módulo `identity` (FR-021). Esta feature NO emite tokens: solo los valida. La
configuración del Resource Server (clave pública, issuer o JWK) se parametriza por entorno, sin
valores fijos en el repositorio. El Principio V de la constitución exige usar exclusivamente el
soporte nativo de Spring Security.

## Alternativas consideradas

- **Librerías JWT de terceros o un filtro de servlet manual**: descartadas; el Principio V las
  prohíbe (las implementaciones manuales son una fuente habitual de vulnerabilidades).
- **HMAC con secreto compartido**: descartada; obligaría a distribuir un secreto a cada
  verificador, que además podría firmar tokens.
- **Clave pública de dev versionada en el repositorio**: descartada; sin su clave privada no
  aporta nada y añade un archivo con material criptográfico al repositorio.
- **`spring-boot-starter-oauth2-resource-server`**: descartada; está deprecado en Spring Boot
  4.1.2 en favor del starter nuevo, y el Principio III prohíbe APIs deprecadas.
- **Opción nativa sin dependencia extra**: no existe; el soporte de Resource Server y
  `JwtDecoder` requiere un starter de Spring Security.

## Decisión

Añadir `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`, sin
versión explícita: la gestiona el parent `spring-boot-starter-parent` 4.1.2. Para pruebas,
`spring-security-test` ya llega vía `spring-boot-starter-security-test`.

## Compatibilidad

- Java 25: el starter se resuelve con el parent de Spring Boot 4.1.2, que es la línea que la
  constitución fija para Java 25. Se confirmará con la compilación del proyecto (T007).
- Spring Boot 4.1.x / Spring Security 7: es el starter que Boot 4.1.2 recomienda frente al
  deprecado `spring-boot-starter-oauth2-resource-server`.

## Estado de mantenimiento

Verificado el 2026-11-05 en Maven Central (`repo1.maven.org/maven2/org/springframework/boot/`):

- **Última release estable**: 4.1.2, publicada el 2026-10-22 (cabecera `Last-Modified` del POM).
  Existen además preliminares de la línea 4.2 (4.2.0-M1, M2, RC1, RC2), que no se usan.
- **Cadencia**: sigue la de Spring Boot, con releases de parche cada dos meses
  aproximadamente: 4.1.0 (2026-06-10), 4.1.1 (2026-08-20) y 4.1.2 (2026-10-22). La línea 4.0
  llegó a 4.0.9 (2026-10-22).
- **Licencia**: Apache License, Version 2.0 (declarada en el POM del artefacto 4.1.2).
- **Deprecación del starter antiguo**: el POM de `spring-boot-starter-oauth2-resource-server`
  4.1.2 declara en su descripción "deprecated in favor of
  spring-boot-starter-security-oauth2-resource-server".
- Dato no verificado: la nota oficial de release de Boot que anuncia la deprecación (no se
  consultó Context7 ni las release notes; la deprecación sí consta en el POM citado).

## Consecuencias

- **Clave pública obligatoria fuera de dev** (histórico, ver Reemplazo parcial): `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`
  (clave pública RSA, p. ej. `file:/run/secrets/jwt-public.pem`). Si falta no existe un
  `JwtDecoder` y el arranque falla (fail-fast).
- **Perfil `dev`** (histórico, ver Reemplazo parcial): `JwtDecoder` construido con un par RSA efímero generado en memoria al
  arrancar. No hay claves en el repositorio y los tokens de dev quedan inválidos en cada
  reinicio; es aceptable porque la emisión está fuera de alcance.
- **Tests**: `TestSecurityConfiguration` genera un par RSA efímero y expone `JwtDecoder` y
  `NimbusJwtEncoder` para firmar tokens con o sin roles, expirados o con otra clave.
- **401 uniforme**: la cabecera es siempre el valor literal `WWW-Authenticate: Bearer`, sin
  `error` ni `error_description`, para no revelar qué comprobación falló (p. ej. expiración o
  firma). Cumple RFC 6750 §3, donde `error` es opcional. No se delega en
  `BearerTokenAuthenticationEntryPoint`, que añadiría la causa.
- **CSRF desactivado** en la `SecurityFilterChain`: la credencial viaja solo en la cabecera
  `Authorization`, que el navegador no adjunta automáticamente en una petición de otro sitio,
  así que no hay superficie CSRF. Con CSRF activo, un `POST` sin credenciales recibiría 403 en
  lugar de 401, lo que incumple SC-004 y FR-020.
- **Deuda registrada**: `allowCredentials=true` (CORS) prepara la cookie del refresh token. El
  módulo `identity` MUST reevaluar la protección CSRF del endpoint de refresh antes de emitir
  cookies (p. ej. `SameSite=Strict` y CSRF activo solo en esa ruta). Ver ADR-006, Consecuencias.
