package com.mangateca.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestSecurityConfiguration.TestTokens;
import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.identity.domain.RefreshToken;
import com.mangateca.api.identity.domain.RefreshTokenRepository;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class AuthFlowIT {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Autowired private RestTestClient client;

  @Autowired private TestTokens testTokens;

  @Autowired private UserRepository userRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Nested
  class Register {

    @Test
    @DisplayName("FR-001 / FR-005: registro de punta a punta responde 201 con rol CUSTOMER sin contrasena")
    void shouldRegisterCustomerEndToEnd() {
      String password = "S3cret-pass-123";
      EntityExchangeResult<String> result =
          client
              .post()
              .uri("/api/v1/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .body("{\"email\":\"flow@example.com\",\"displayName\":\"Flow\",\"password\":\"" + password + "\"}")
              .exchange()
              .returnResult(String.class);

      assertThat(result.getStatus().value()).isEqualTo(201);
      String json = result.getResponseBody();
      assertThat(json).isNotNull().doesNotContain(password);
      JsonNode node = MAPPER.readTree(json);
      assertThat(node.path("id").asString()).isNotBlank();
      assertThat(node.path("email").asString()).isEqualTo("flow@example.com");
      assertThat(node.path("displayName").asString()).isEqualTo("Flow");
      assertThat(node.path("role").asString()).isEqualTo("CUSTOMER");
      assertThat(node.has("password")).isFalse();
      assertThat(node.has("passwordHash")).isFalse();
    }
  }

  private String uniqueEmail() {
    return "u" + UUID.randomUUID() + "@example.com";
  }

  private JsonNode register(String email, String password) {
    EntityExchangeResult<String> result =
        client
            .post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"email\":\"" + email + "\",\"displayName\":\"Flow\",\"password\":\"" + password + "\"}")
            .exchange()
            .returnResult(String.class);
    assertThat(result.getStatus().value()).isEqualTo(201);
    return MAPPER.readTree(result.getResponseBody());
  }

  private EntityExchangeResult<String> login(String email, String password) {
    return client
        .post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .exchange()
        .returnResult(String.class);
  }

  private static JsonNode jwtPayload(String jwt) {
    String[] parts = jwt.split("\\.");
    assertThat(parts).hasSize(3);
    return MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
  }

  private EntityExchangeResult<String> postWithCookie(String uri, String cookieValue) {
    return client
        .post()
        .uri(uri)
        .header("Cookie", "refresh_token=" + cookieValue)
        .exchange()
        .returnResult(String.class);
  }

  private static String refreshCookieValue(EntityExchangeResult<String> result) {
    String header = result.getResponseHeaders().getFirst("Set-Cookie");
    assertThat(header).isNotNull().startsWith("refresh_token=");
    return header.substring("refresh_token=".length(), header.indexOf(';'));
  }

  @Nested
  class Login {

    @Test
    @DisplayName("Escenario 1 / FR-007 / FR-008: login valido emite JWT de 900s, cookie de refresh y acceso a ruta protegida")
    void shouldIssueTokensOnValidLogin() {
      String email = uniqueEmail();
      String password = "S3cret-pass-123";
      String userId = register(email, password).path("id").asString();

      EntityExchangeResult<String> result = login(email, password);

      assertThat(result.getStatus().value()).isEqualTo(200);
      String body = result.getResponseBody();
      assertThat(body).isNotNull();
      JsonNode node = MAPPER.readTree(body);
      assertThat(node.has("refresh_token")).isFalse();
      assertThat(body).doesNotContain("refresh_token");
      String accessToken = node.path("access_token").asString();
      assertThat(accessToken).isNotBlank();

      JsonNode payload = jwtPayload(accessToken);
      assertThat(payload.path("exp").asLong() - payload.path("iat").asLong()).isEqualTo(900L);
      assertThat(payload.path("sub").asString()).isEqualTo(userId);
      assertThat(payload.path("roles")).hasSize(1);
      assertThat(payload.path("roles").get(0).asString()).isEqualTo("CUSTOMER");

      String cookie = result.getResponseHeaders().getFirst("Set-Cookie");
      assertThat(cookie)
          .isNotNull()
          .startsWith("refresh_token=")
          .contains("Max-Age=604800")
          .contains("Path=/api/v1/auth")
          .contains("HttpOnly")
          .contains("Secure")
          .contains("SameSite=Strict");

      client
          .get()
          .uri("/api/v1/probes?size=1")
          .header("Authorization", "Bearer " + accessToken)
          .exchange()
          .expectStatus()
          .isOk();
    }

    @Test
    @DisplayName("Escenario 2 / FR-009 / SC-002: contrasena erronea y correo inexistente son indistinguibles")
    void shouldReturnIdenticalErrorForWrongPasswordAndUnknownEmail() {
      String email = uniqueEmail();
      register(email, "S3cret-pass-123");

      EntityExchangeResult<String> wrongPassword = login(email, "Wrong-pass-999");
      EntityExchangeResult<String> unknownEmail = login(uniqueEmail(), "Wrong-pass-999");

      assertThat(wrongPassword.getStatus().value()).isEqualTo(401);
      assertThat(unknownEmail.getStatus().value()).isEqualTo(wrongPassword.getStatus().value());
      ObjectNode a = (ObjectNode) MAPPER.readTree(wrongPassword.getResponseBody());
      ObjectNode b = (ObjectNode) MAPPER.readTree(unknownEmail.getResponseBody());
      assertThat(b.path("type")).isEqualTo(a.path("type"));
      assertThat(b.path("detail")).isEqualTo(a.path("detail"));
      assertThat(a.path("status").asInt()).isEqualTo(401);
      assertThat(b.path("status").asInt()).isEqualTo(401);
      for (String variable : new String[] {"instance", "timestamp", "traceId"}) {
        a.remove(variable);
        b.remove(variable);
      }
      assertThat(b).isEqualTo(a);
    }

    @Test
    @DisplayName("FR-010 / FR-011: 5 fallos persisten el contador y bloquean la cuenta aunque la contrasena sea correcta")
    void shouldPersistFailedAttemptsAndLockAccountAfterFiveFailures() {
      String email = uniqueEmail();
      String password = "S3cret-pass-123";
      UUID userId = UUID.fromString(register(email, password).path("id").asString());
      EntityExchangeResult<String> firstFailure = login(email, "Wrong-pass-999");

      for (int i = 1; i < 5; i++) {
        assertThat(login(email, "Wrong-pass-999").getStatus().value()).isEqualTo(401);
      }

      User persisted = userRepository.findById(userId).orElseThrow();
      assertThat(persisted.getFailedLoginAttempts()).isEqualTo(5);
      assertThat(persisted.getLockedUntil()).isNotNull();

      EntityExchangeResult<String> locked = login(email, password);
      assertThat(locked.getStatus().value()).isEqualTo(401);
      ObjectNode expected = (ObjectNode) MAPPER.readTree(firstFailure.getResponseBody());
      ObjectNode actual = (ObjectNode) MAPPER.readTree(locked.getResponseBody());
      for (String variable : new String[] {"instance", "timestamp", "traceId"}) {
        expected.remove(variable);
        actual.remove(variable);
      }
      assertThat(actual).isEqualTo(expected);
    }
  }

  @Nested
  class Session {

    @Test
    @DisplayName("FR-012 / FR-014: refresh rota la cookie, la anterior queda invalida y logout cierra la sesion")
    void shouldRotateRefreshTokenAndInvalidateSessionOnLogout() {
      String email = uniqueEmail();
      String password = "S3cret-pass-123";
      register(email, password);
      String first = refreshCookieValue(login(email, password));

      EntityExchangeResult<String> refreshed = postWithCookie("/api/v1/auth/refresh", first);

      assertThat(refreshed.getStatus().value()).isEqualTo(200);
      assertThat(refreshed.getResponseBody()).isNotNull().doesNotContain("refresh_token");
      assertThat(MAPPER.readTree(refreshed.getResponseBody()).path("access_token").asString()).isNotBlank();
      String second = refreshCookieValue(refreshed);
      assertThat(second).isNotEqualTo(first);
      assertThat(refreshed.getResponseHeaders().getFirst("Set-Cookie"))
          .contains("Max-Age=604800", "Path=/api/v1/auth", "HttpOnly", "Secure");

      EntityExchangeResult<String> reused = postWithCookie("/api/v1/auth/refresh", first);
      assertThat(reused.getStatus().value()).isEqualTo(401);
      assertThat(MAPPER.readTree(reused.getResponseBody()).path("type").asString())
          .isEqualTo("urn:mangateca:problem:invalid-refresh-token");

      EntityExchangeResult<String> loggedOut = postWithCookie("/api/v1/auth/logout", second);
      assertThat(loggedOut.getStatus().value()).isEqualTo(204);
      assertThat(loggedOut.getResponseHeaders().getFirst("Set-Cookie")).contains("Max-Age=0");

      EntityExchangeResult<String> afterLogout = postWithCookie("/api/v1/auth/refresh", second);
      assertThat(afterLogout.getStatus().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("FR-013: reutilizar un refresh token rotado revoca toda la familia y la revocacion se confirma")
    void shouldRevokeWholeFamilyAndCommitWhenRotatedTokenIsReused() {
      String email = uniqueEmail();
      String password = "S3cret-pass-123";
      UUID userId = UUID.fromString(register(email, password).path("id").asString());
      String original = refreshCookieValue(login(email, password));
      String rotated = refreshCookieValue(postWithCookie("/api/v1/auth/refresh", original));

      EntityExchangeResult<String> reuse = postWithCookie("/api/v1/auth/refresh", original);
      assertThat(reuse.getStatus().value()).isEqualTo(401);

      assertThat(postWithCookie("/api/v1/auth/refresh", rotated).getStatus().value()).isEqualTo(401);
      List<RefreshToken> family =
          refreshTokenRepository.findAll().stream().filter(t -> t.getUserId().equals(userId)).toList();
      assertThat(family).hasSize(2);
      assertThat(family).allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
    }
  }

  private static String accessToken(EntityExchangeResult<String> loginResult) {
    return MAPPER.readTree(loginResult.getResponseBody()).path("access_token").asString();
  }

  private EntityExchangeResult<String> getWithBearer(String uri, String token) {
    return client.get().uri(uri).header("Authorization", "Bearer " + token).exchange().returnResult(String.class);
  }

  @Nested
  class Me {

    @Test
    @DisplayName("Escenario 1 / FR-016: cada token obtiene en /me solo sus propios datos (anti IDOR)")
    void shouldReturnOnlyOwnIdentityForEachToken() {
      String password = "S3cret-pass-123";
      String emailA = uniqueEmail();
      String emailB = uniqueEmail();
      String idA = register(emailA, password).path("id").asString();
      String idB = register(emailB, password).path("id").asString();
      String tokenA = accessToken(login(emailA, password));
      String tokenB = accessToken(login(emailB, password));

      EntityExchangeResult<String> meA = getWithBearer("/api/v1/auth/me", tokenA);
      EntityExchangeResult<String> meB = getWithBearer("/api/v1/auth/me", tokenB);

      assertThat(meA.getStatus().value()).isEqualTo(200);
      assertThat(meB.getStatus().value()).isEqualTo(200);
      JsonNode a = MAPPER.readTree(meA.getResponseBody());
      JsonNode b = MAPPER.readTree(meB.getResponseBody());
      assertThat(a.path("id").asString()).isEqualTo(idA);
      assertThat(a.path("email").asString()).isEqualTo(emailA);
      assertThat(a.path("displayName").asString()).isEqualTo("Flow");
      assertThat(a.path("role").asString()).isEqualTo("CUSTOMER");
      assertThat(b.path("id").asString()).isEqualTo(idB);
      assertThat(b.path("email").asString()).isEqualTo(emailB);
      assertThat(meA.getResponseBody()).doesNotContain(idB, emailB);
      assertThat(meB.getResponseBody()).doesNotContain(idA, emailA);
      for (JsonNode node : List.of(a, b)) {
        assertThat(node.has("password")).isFalse();
        assertThat(node.has("passwordHash")).isFalse();
      }
      assertThat(meA.getResponseBody()).doesNotContainIgnoringCase("password");
    }

    @Test
    @DisplayName("Escenario 2 / FR-016: token expirado en /me responde 401 ProblemDetail")
    void shouldReturn401ProblemWhenTokenIsExpired() {
      String email = uniqueEmail();
      String userId = register(email, "S3cret-pass-123").path("id").asString();
      Instant issuedAt = Instant.parse("2020-01-01T00:00:00Z");
      String expired = testTokens.token(userId, List.of("CUSTOMER"), issuedAt, issuedAt.plusSeconds(900));

      EntityExchangeResult<String> result = getWithBearer("/api/v1/auth/me", expired);

      assertThat(result.getStatus().value()).isEqualTo(401);
      assertThat(result.getResponseHeaders().getContentType())
          .isNotNull()
          .hasToString("application/problem+json");
      JsonNode node = MAPPER.readTree(result.getResponseBody());
      assertThat(node.path("status").asInt()).isEqualTo(401);
      assertThat(node.path("type").asString()).isEqualTo("about:blank");
      assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
      assertThat(result.getResponseBody()).doesNotContain(userId);
    }

    @Test
    @DisplayName("Escenario 2 / FR-016: token valido de un usuario inexistente en /me responde 401 ProblemDetail")
    void shouldReturn401ProblemWhenTokenSubjectDoesNotExist() {
      String unknownId = UUID.randomUUID().toString();
      String token = testTokens.token(unknownId, List.of("CUSTOMER"));

      EntityExchangeResult<String> result = getWithBearer("/api/v1/auth/me", token);

      assertThat(result.getStatus().value()).isEqualTo(401);
      assertThat(result.getResponseHeaders().getContentType()).isNotNull().hasToString("application/problem+json");
      JsonNode node = MAPPER.readTree(result.getResponseBody());
      assertThat(node.path("status").asInt()).isEqualTo(401);
      assertThat(node.path("type").asString()).isEqualTo("about:blank");
      assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
      assertThat(result.getResponseBody()).doesNotContain(unknownId);
    }
  }

  @Nested
  class Security {

    @Test
    @DisplayName("FR-019 / Escenario 3: las 4 rutas POST publicas sin token nunca devuelven el 401 del entry point")
    void shouldNeverReturnPlatformEntryPoint401OnPublicAuthRoutes() {
      String[][] routes = {
        {"/api/v1/auth/register", "{\"email\":\"no-es-correo\",\"displayName\":\"\",\"password\":\"x\"}"},
        {"/api/v1/auth/login", "{\"email\":\"nadie@example.com\",\"password\":\"Wrong-pass-999\"}"},
        {"/api/v1/auth/refresh", null},
        {"/api/v1/auth/logout", null}
      };
      for (String[] route : routes) {
        var request = client.post().uri(route[0]);
        EntityExchangeResult<String> result =
            (route[1] == null
                    ? request.exchange()
                    : request.contentType(MediaType.APPLICATION_JSON).body(route[1]).exchange())
                .returnResult(String.class);

        int status = result.getStatus().value();
        assertThat(status).as(route[0]).isIn(204, 400, 401);
        assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).as(route[0]).isNull();
        if (status == 401) {
          assertThat(MAPPER.readTree(result.getResponseBody()).path("type").asString())
              .as(route[0])
              .startsWith("urn:mangateca:problem:");
        }
      }
    }

    @Test
    @DisplayName("FR-019 / FR-020: refresh sin cookie responde el 401 propio de identity, sin WWW-Authenticate")
    void shouldReturnIdentityProblemWhenRefreshHasNoCookie() {
      EntityExchangeResult<String> result =
          client.post().uri("/api/v1/auth/refresh").exchange().returnResult(String.class);

      assertThat(result.getStatus().value()).isEqualTo(401);
      assertThat(MAPPER.readTree(result.getResponseBody()).path("type").asString())
          .isEqualTo("urn:mangateca:problem:invalid-refresh-token");
      assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).isNull();
    }

    @Test
    @DisplayName("Escenario 3 / deny-by-default: /me y una ruta inexistente sin token dan el 401 del entry point")
    void shouldReturnPlatform401WhenProtectedOrUnknownRouteHasNoToken() {
      for (String uri : new String[] {"/api/v1/auth/me", "/api/v1/auth/inexistente"}) {
        EntityExchangeResult<String> result = client.get().uri(uri).exchange().returnResult(String.class);

        assertThat(result.getStatus().value()).as(uri).isEqualTo(401);
        assertThat(result.getResponseHeaders().getFirst("WWW-Authenticate")).as(uri).isEqualTo("Bearer");
        JsonNode node = MAPPER.readTree(result.getResponseBody());
        assertThat(node.path("type").asString()).as(uri).isEqualTo("about:blank");
        assertThat(node.path("status").asInt()).as(uri).isEqualTo(401);
      }
    }
  }

  @Nested
  class Roles {

    @Test
    @DisplayName("Escenario 4 / FR-021 / SC-003: CUSTOMER real recibe 403 en la ruta de ADMIN")
    void shouldReturn403WhenCustomerAccessesAdminRoute() {
      String email = uniqueEmail();
      register(email, "S3cret-pass-123");
      String token = accessToken(login(email, "S3cret-pass-123"));

      EntityExchangeResult<String> result = getWithBearer("/api/v1/probes/admin", token);

      assertThat(result.getStatus().value()).isEqualTo(403);
      assertThat(result.getResponseHeaders().getContentType())
          .isNotNull()
          .hasToString("application/problem+json");
      assertThat(MAPPER.readTree(result.getResponseBody()).path("status").asInt()).isEqualTo(403);
      assertThat(result.getResponseBody()).isNotEqualTo("admin");
    }

    @Test
    @DisplayName("FR-008 / FR-021: token con roles=[ADMIN] accede a la ruta de ADMIN (roles -> ROLE_*)")
    void shouldReturn200WhenTokenCarriesAdminRole() {
      String token = testTokens.token(UUID.randomUUID().toString(), List.of("ADMIN"));

      EntityExchangeResult<String> result = getWithBearer("/api/v1/probes/admin", token);

      assertThat(result.getStatus().value()).isEqualTo(200);
      assertThat(result.getResponseBody()).isEqualTo("admin");
    }
  }
}
