package com.mangateca.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.application.AuthenticationService;
import com.mangateca.api.identity.application.EmailAlreadyInUseException;
import com.mangateca.api.identity.application.RefreshTokenService;
import com.mangateca.api.identity.config.IdentitySecurityConfiguration;
import com.mangateca.api.identity.config.JwtKeyConfiguration;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.User;
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
