package com.mangateca.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestcontainersConfiguration;
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
}
