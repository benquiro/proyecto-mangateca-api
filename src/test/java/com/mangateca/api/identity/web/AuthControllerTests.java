package com.mangateca.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.application.AuthenticationService;
import com.mangateca.api.identity.application.EmailAlreadyInUseException;
import com.mangateca.api.identity.application.InvalidCredentialsException;
import com.mangateca.api.identity.application.InvalidRefreshTokenException;
import com.mangateca.api.identity.application.IssuedTokens;
import com.mangateca.api.identity.application.RefreshTokenService;
import com.mangateca.api.identity.config.IdentitySecurityConfiguration;
import com.mangateca.api.identity.config.JwtKeyConfiguration;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.User;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({
  IdentitySecurityConfiguration.class,
  JwtKeyConfiguration.class,
  UserMapperImpl.class,
  RefreshCookieFactory.class
})
class AuthControllerTests {

  private static final String REGISTER = "/api/v1/auth/register";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final String PASSWORD = "S3cret-pass-123";
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Autowired private MockMvcTester mvc;

  @MockitoBean private AuthenticationService authenticationService;

  @MockitoBean private RefreshTokenService refreshTokenService;

  @Test
  @DisplayName("Escenario 1 / FR-004 / FR-005: registro valido responde 201 sin password ni hash")
  void shouldReturn201WithCustomerRoleAndNoPasswordWhenRegistrationIsValid() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    User user =
        User.register(new Email("ana@example.com"), "Ana", "{bcrypt}hash-secreto", Role.CUSTOMER, clock);
    when(authenticationService.register("ana@example.com", "Ana", PASSWORD)).thenReturn(user);

    MvcTestResult result = post(body("ana@example.com", "Ana", PASSWORD, null));

    assertThat(result).hasStatus(201);
    String json = content(result);
    JsonNode node = MAPPER.readTree(json);
    assertThat(node.path("id").asString()).isEqualTo(user.getId().toString());
    assertThat(node.path("email").asString()).isEqualTo("ana@example.com");
    assertThat(node.path("displayName").asString()).isEqualTo("Ana");
    assertThat(node.path("role").asString()).isEqualTo("CUSTOMER");
    assertThat(node.has("password")).isFalse();
    assertThat(node.has("passwordHash")).isFalse();
    assertThat(json).doesNotContain(PASSWORD, "hash-secreto");
  }

  @Test
  @DisplayName("Escenario 2 / FR-020: correo duplicado responde 409 con type email-already-in-use")
  void shouldReturn409WhenEmailIsAlreadyInUse() {
    when(authenticationService.register(any(), any(), any())).thenThrow(new EmailAlreadyInUseException());

    MvcTestResult result = post(body("ana@example.com", "Ana", PASSWORD, null));

    assertThat(result).hasStatus(409);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    JsonNode node = MAPPER.readTree(content(result));
    assertThat(node.path("type").asString()).isEqualTo("urn:mangateca:problem:email-already-in-use");
    assertThat(node.path("status").asInt()).isEqualTo(409);
    assertThat(node.path("detail").asString()).doesNotContain("ana@example.com");
  }

  static Stream<Arguments> invalidRegistrations() {
    return Stream.of(
        Arguments.of("password de 7 caracteres", "ana@example.com", "Ana", "a".repeat(7), "password"),
        Arguments.of("password de 65 caracteres", "ana@example.com", "Ana", "a".repeat(65), "password"),
        Arguments.of("correo invalido", "no-es-un-correo", "Ana", PASSWORD, "email"),
        Arguments.of("nombre vacio", "ana@example.com", "", PASSWORD, "displayName"),
        Arguments.of("nombre de 101 caracteres", "ana@example.com", "n".repeat(101), PASSWORD, "displayName"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRegistrations")
  @DisplayName("Escenario 3 / FR-001 / FR-003: datos invalidos responden 400 con errors[].field")
  void shouldReturn400WithFieldErrorWhenRegistrationIsInvalid(
      String caso, String email, String displayName, String password, String field) {
    MvcTestResult result = post(body(email, displayName, password, null));

    assertThat(result).hasStatus(400);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(errorFields(result)).contains(field);
    verifyNoInteractions(authenticationService);
  }

  @Test
  @DisplayName("Escenario 4 / FR-005: un role en el cuerpo responde 400 y no llama al servicio")
  void shouldReturn400AndNotCallServiceWhenBodyContainsRole() {
    MvcTestResult result = post(body("ana@example.com", "Ana", PASSWORD, "ADMIN"));

    assertThat(result).hasStatus(400);
    assertThat(errorFields(result)).contains("role");
    verifyNoInteractions(authenticationService);
  }

  @Test
  @DisplayName("Escenario 1 / FR-007: login correcto responde 200 con access_token y cookie de refresh")
  void shouldReturn200WithAccessTokenAndRefreshCookieWhenLoginIsValid() {
    when(authenticationService.login("ana@example.com", PASSWORD))
        .thenReturn(new IssuedTokens("access-jwt", 900L, "refresh-opaque"));

    MvcTestResult result = login("{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}");

    assertThat(result).hasStatus(200);
    JsonNode node = MAPPER.readTree(content(result));
    assertThat(node.path("access_token").asString()).isEqualTo("access-jwt");
    assertThat(node.path("token_type").asString()).isEqualTo("Bearer");
    assertThat(node.path("expires_in").asInt()).isEqualTo(900);
    assertThat(node.has("refresh_token")).isFalse();
    assertThat(content(result)).doesNotContain("refresh-opaque");
    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
    assertThat(cookies).hasSize(1);
    String cookie = cookies.get(0);
    assertThat(cookie).startsWith("refresh_token=refresh-opaque;");
    assertThat(cookie)
        .contains("Path=/api/v1/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");
  }

  @Test
  @DisplayName("FR-009 / FR-020: credenciales invalidas responden 401 con type invalid-credentials")
  void shouldReturn401WhenCredentialsAreInvalid() {
    when(authenticationService.login(any(), any())).thenThrow(new InvalidCredentialsException());

    MvcTestResult result = login("{\"email\":\"ana@example.com\",\"password\":\"incorrecta\"}");

    assertThat(result).hasStatus(401);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    JsonNode node = MAPPER.readTree(content(result));
    assertThat(node.path("type").asString()).isEqualTo("urn:mangateca:problem:invalid-credentials");
    assertThat(node.path("status").asInt()).isEqualTo(401);
    assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
  }

  @Test
  @DisplayName("FR-009: login con cuerpo vacio responde 400 con errors y no llama al servicio")
  void shouldReturn400WithErrorsWhenLoginBodyIsEmpty() {
    MvcTestResult result = login("{}");

    assertThat(result).hasStatus(400);
    assertThat(errorFields(result)).contains("email", "password");
    verifyNoInteractions(authenticationService);
  }

  @Test
  @DisplayName("Escenario 1 / FR-012: refresh con cookie responde 200 con nuevo access_token y nueva cookie")
  void shouldReturn200WithNewAccessTokenAndRotatedCookieWhenRefreshCookieIsValid() {
    when(refreshTokenService.rotate("old-opaque"))
        .thenReturn(new IssuedTokens("new-access-jwt", 900L, "new-opaque"));

    MvcTestResult result = mvc.post().uri(REFRESH).cookie(new Cookie("refresh_token", "old-opaque")).exchange();

    assertThat(result).hasStatus(200);
    JsonNode node = MAPPER.readTree(content(result));
    assertThat(node.path("access_token").asString()).isEqualTo("new-access-jwt");
    assertThat(node.path("token_type").asString()).isEqualTo("Bearer");
    assertThat(node.path("expires_in").asInt()).isEqualTo(900);
    assertThat(node.has("refresh_token")).isFalse();
    assertThat(content(result)).doesNotContain("new-opaque");
    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
    assertThat(cookies).hasSize(1);
    assertThat(cookies.get(0)).startsWith("refresh_token=new-opaque;");
    assertThat(cookies.get(0))
        .contains("Path=/api/v1/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");
  }

  @Test
  @DisplayName("FR-020: refresh sin cookie responde 401 con type invalid-refresh-token y sin WWW-Authenticate")
  void shouldReturn401WhenRefreshCookieIsMissing() {
    MvcTestResult result = mvc.post().uri(REFRESH).exchange();

    assertThat(result).hasStatus(401);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    JsonNode node = MAPPER.readTree(content(result));
    assertThat(node.path("type").asString()).isEqualTo("urn:mangateca:problem:invalid-refresh-token");
    assertThat(node.path("status").asInt()).isEqualTo(401);
    assertThat(result.getResponse().getHeader("WWW-Authenticate")).isNull();
    assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
  }

  @Test
  @DisplayName("FR-020: refresh con token rechazado responde 401 con el mismo type y detail que sin cookie")
  void shouldReturnSameProblemWhenRefreshTokenIsRejectedAsWhenMissing() {
    when(refreshTokenService.rotate("rejected")).thenThrow(new InvalidRefreshTokenException());

    MvcTestResult missing = mvc.post().uri(REFRESH).exchange();
    MvcTestResult rejected = mvc.post().uri(REFRESH).cookie(new Cookie("refresh_token", "rejected")).exchange();

    assertThat(rejected).hasStatus(401);
    JsonNode a = MAPPER.readTree(content(missing));
    JsonNode b = MAPPER.readTree(content(rejected));
    assertThat(b.path("type")).isEqualTo(a.path("type"));
    assertThat(b.path("detail")).isEqualTo(a.path("detail"));
  }

  @Test
  @DisplayName("Escenario 4 / FR-014 / FR-019: logout sin cookie responde 204, borra la cookie y no emite token")
  void shouldReturn204AndClearCookieWhenLogoutHasNoCookie() {
    MvcTestResult result = mvc.post().uri(LOGOUT).exchange();

    assertThat(result).hasStatus(204);
    assertThat(content(result)).isEmpty();
    List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
    assertThat(cookies).hasSize(1);
    assertThat(cookies.get(0)).startsWith("refresh_token=;");
    assertThat(cookies.get(0))
        .contains("Max-Age=0", "Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=Strict");
    assertThat(result.getResponse().getHeader("Authorization")).isNull();
  }

  @Test
  @DisplayName("FR-014: logout con cookie revoca el token recibido y responde 204 borrando la cookie")
  void shouldRevokeTokenAndClearCookieWhenLogoutHasCookie() {
    MvcTestResult result = mvc.post().uri(LOGOUT).cookie(new Cookie("refresh_token", "live-opaque")).exchange();

    assertThat(result).hasStatus(204);
    verify(refreshTokenService).revoke("live-opaque");
    assertThat(result.getResponse().getHeaders("Set-Cookie").get(0)).contains("Max-Age=0");
  }

  private MvcTestResult login(String json) {
    return mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private static String content(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private MvcTestResult post(String json) {
    return mvc.post().uri(REGISTER).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private static String body(String email, String displayName, String password, String role) {
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"email\":\"").append(email).append("\",");
    sb.append("\"displayName\":\"").append(displayName).append("\",");
    sb.append("\"password\":\"").append(password).append("\"");
    if (role != null) {
      sb.append(",\"role\":\"").append(role).append("\"");
    }
    return sb.append("}").toString();
  }

  private static List<String> errorFields(MvcTestResult result) {
    List<String> fields = new ArrayList<>();
    JsonNode errors = MAPPER.readTree(content(result)).path("errors");
    errors.forEach(e -> fields.add(e.path("field").asString()));
    return fields;
  }
}
