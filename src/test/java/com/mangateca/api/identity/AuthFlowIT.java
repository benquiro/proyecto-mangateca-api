package com.mangateca.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestcontainersConfiguration;
import java.util.Base64;
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
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Autowired private RestTestClient client;

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
  }
}
