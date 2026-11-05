# ADR-006: Emisión propia de tokens (JWT RS256) y origen de las claves por entorno

- **Estado**: Aceptado
- **Fecha**: 2026-11-05

## Contexto y problema

La feature 002 (`identity`) añade registro, login, renovación y cierre de sesión (FR-007, FR-008,
FR-015, FR-021). Hay que decidir quién emite los tokens, cómo se reparte el par de claves entre el
emisor y el validador, de dónde salen las claves en cada entorno y cómo viaja el token de
renovación. ADR-003 resolvió solo la validación, bajo la premisa de que la feature 001 no emitía
tokens: leía la clave pública de `spring.security.oauth2.resourceserver.jwt.public-key-location`
y usaba un decoder efímero en `dev` (`DevJwtDecoderConfiguration`). Con un par efímero, encoder
y decoder deben compartir la misma instancia en memoria, así que esa premisa deja de servir. La
Higiene Operacional prohíbe versionar claves de firma y exige inyectarlas por entorno.

## Alternativas consideradas

- **Spring Authorization Server**: adecuado con varias aplicaciones cliente, SSO o federación.
  Hoy no existe ninguna de esas necesidades; añadiría un proceso, endpoints OAuth2 que nadie
  consume y gestión de clientes registrados (Principio VII). Descartado, con condición de revisión
  (ver Consecuencias).
- **Proveedor de identidad externo (Keycloak, Auth0)**: dependencia operativa y de coste no pedida.
  Descartado.
- **HMAC (HS256)**: obliga a compartir el secreto de firma con cada verificador, que además podría
  firmar tokens. Ya descartado en ADR-003. Descartado.
- **Pares de claves de dev/test versionados en el repositorio**: incumple la Higiene Operacional.
  Descartado.
- **PEM completo dentro de la variable de entorno**: frágil con los saltos de línea y visible en
  `/proc` y en volcados de entorno. Descartado; si un despliegue lo exigiera, se añade como segunda
  fuente sin cambiar el contrato.
- **Generar el par efímero en cualquier perfil sin clave**: un despliegue mal configurado
  arrancaría con tokens que caducan en cada reinicio y difieren entre instancias. Descartado: es más
  seguro fallar.
- **Refresh token como JWT o en el cuerpo de la respuesta**: no es revocable sin estado y el
  Principio V exige un token opaco; en el cuerpo quedaría expuesto a XSS. Descartado.

## Decisión

1. **Emisión propia** del token de acceso: JWT RS256 firmado con `NimbusJwtEncoder`, con los
   claims `sub` (UUID del usuario), `roles`, `iss`, `iat` y `exp` (15 minutos). Se entrega en el
   cuerpo de la respuesta de login como `access_token`, `token_type` y `expires_in`.
2. **Un único par RSA en `identity`** (`JwtKeyConfiguration`) alimenta el `JwtEncoder` y el
   `JwtDecoder`. El decoder se construye con `NimbusJwtDecoder.withPublicKey(...)` y
   `JwtValidators.createDefaultWithIssuer(issuer)`, de modo que valida firma, `exp`/`nbf` e `iss`.
   La cadena de la plataforma toma el `JwtDecoder` del contexto por tipo. Se retiran
   `DevJwtDecoderConfiguration` y la propiedad `spring.security.oauth2.resourceserver.jwt.public-key-location`.
3. **Origen de las claves**:
   - Con `mangateca.identity.jwt.private-key-location` y `mangateca.identity.jwt.public-key-location`
     definidas, se cargan los PEM (PKCS#8 la privada, X.509 la pública) y se comprueba que forman
     un par. En producción las rutas llegan por `MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION` y
     `MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION`, apuntando a secretos montados
     (p. ej. `file:/run/secrets/jwt-private.pem`), junto con `MANGATECA_IDENTITY_JWT_ISSUER`.
   - Sin ninguna ruta y con perfil `dev` o `test`, se genera un par RSA 2048 efímero en memoria y se
     registra un INFO sin material de clave. Los tokens dejan de valer al reiniciar.
   - En cualquier otro caso (solo una ruta definida, un par que no coincide, o ninguna ruta fuera de
     `dev`/`test`) el arranque falla con un mensaje que nombra las variables que faltan.
   - Ninguna clave se versiona.
4. **Refresh token opaco**: 256 bits de `SecureRandom`, guardado solo como hash SHA-256 (FR-015).
   Se rota en cada renovación: el token anterior queda invalidado y reutilizar uno ya rotado revoca
   toda su familia (`family_id`).
5. **Transporte del refresh token**: cookie `refresh_token` con `HttpOnly; Secure; SameSite`
   explícito (`Strict` por defecto, configurable) y `Path=/api/v1/auth`. El logout la borra con
   `Max-Age=0`.
6. **Perfil `test`**: Surefire lo activa con `spring.profiles.active=test` en
   `systemPropertyVariables` (tarea T003). `issuer` de test va en `src/test/resources/application-test.yaml`,
   sin claves.

No se añade ninguna dependencia: `NimbusJwtEncoder`, `NimbusJwtDecoder` y `RsaKeyConverters` ya
llegan con el starter de ADR-003. El único ajuste de `pom.xml` es el de Surefire (punto 6).

## Compatibilidad

- **Java 25**: `KeyPairGenerator` RSA, `MessageDigest` SHA-256 y `SecureRandom` son API estándar del
  JDK.
- **Spring Boot 4.1.x / Spring Security 7**: `NimbusJwtEncoder`, `NimbusJwtDecoder`,
  `JwtValidators` y `RsaKeyConverters` pertenecen al soporte nativo que exige el Principio V y
  vienen en `spring-boot-starter-security-oauth2-resource-server` (ADR-003), gestionado por el
  parent 4.1.2. Se confirmará con la compilación de la feature.
- **Feature 001**: el `JwtAuthenticationConverter` ya lee el claim `roles` y antepone `ROLE_`, así
  que no cambia.

## Estado de mantenimiento

No se introduce ningún artefacto nuevo: el mantenimiento de las clases Nimbus de Spring Security
sigue el de Spring Boot/Spring Security ya verificado en ADR-003 (release estable 4.1.2,
2026-10-22, licencia Apache 2.0). Dato no verificado aquí: el estado individual de la librería
Nimbus JOSE + JWT, que llega de forma transitiva con el starter.

## Consecuencias

- **`platform` exige un bean `JwtDecoder`**: si se desactivara `identity`, la aplicación no
  arrancaría. Es aceptable porque `identity` es la autoridad de tokens del sistema.
- **Fallo de arranque** en un despliegue sin claves o con claves inconsistentes (fail-fast).
- **Tokens de dev/test efímeros**: no sobreviven a un reinicio salvo que se definan las rutas.
- **El access token no se revoca**: dura como máximo 15 minutos. La revocación aplica al refresh
  token y a su familia.
- **Rotación de claves**: fuera del alcance de esta feature.
- **Deuda de ADR-003 resuelta en parte**: el endpoint de refresh usa cookie con `SameSite=Strict` por
  defecto y CSRF desactivado en la cadena stateless; con `SameSite=None` (frontend y API en sitios
  distintos) hará falta CSRF en `/refresh` (research R19).
- **Condición de revisión**: reabrir esta decisión si aparece una segunda aplicación consumidora, login
  social o MFA, supuestos en los que Spring Authorization Server o un IdP externo pasarían a ser
  razonables.
