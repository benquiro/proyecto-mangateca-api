package com.mangateca.api.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US8 / FR-024..FR-026: health e info públicos y mínimos, y contrato OpenAPI con esquema bearer.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ActuatorAndDocsIT {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private RestTestClient client;

	@Test
	@DisplayName("FR-024: health público con db UP y sin detalles")
	void healthIsPublicAndMinimal() {
		JsonNode body = get("/actuator/health");

		assertThat(body.path("status").asString()).isEqualTo("UP");
		assertThat(body.path("components").path("db").path("status").asString()).isEqualTo("UP");
		body.path("components").forEach(component -> assertThat(component.has("details")).isFalse());
	}

	@Test
	@DisplayName("FR-025: info público con build name/version y sin java, os ni env")
	void infoExposesOnlyBuild() {
		JsonNode body = get("/actuator/info");

		assertThat(body.path("build").path("name").asString()).isNotBlank();
		assertThat(body.path("build").path("version").asString()).isNotBlank();
		assertThat(body.has("java")).isFalse();
		assertThat(body.has("os")).isFalse();
		assertThat(body.has("env")).isFalse();
	}

	@Test
	@DisplayName("FR-026: /v3/api-docs es OpenAPI 3 con bearerAuth y sin /error")
	void apiDocsDeclareBearerAuth() {
		JsonNode body = get("/v3/api-docs");

		assertThat(body.path("openapi").asString()).startsWith("3.");
		JsonNode scheme = body.path("components").path("securitySchemes").path("bearerAuth");
		assertThat(scheme.path("type").asString()).isEqualTo("http");
		assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
		assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");
		assertThat(body.path("paths").has("/error")).isFalse();
	}

	private JsonNode get(String path) {
		byte[] raw = this.client.get().uri(path).exchange().expectStatus().isOk().returnResult(byte[].class)
			.getResponseBody();
		assertThat(raw).isNotNull();
		return MAPPER.readTree(raw);
	}

}
