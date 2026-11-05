package com.mangateca.api.platform.security;

import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestcontainersConfiguration;

/**
 * US3 / FR-020 / research R11: CORS con origen permitido y rechazo en Problem Details.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "mangateca.cors.allowed-origins=http://localhost:5173")
@AutoConfigureRestTestClient
class CorsIT {

	private static final String ALLOWED = "http://localhost:5173";

	private static final String EVIL = "https://evil.example";

	@Autowired
	private RestTestClient client;

	@Test
	@DisplayName("FR-020 / US3 CORS 1: origen permitido recibe cabeceras exactas, credenciales y Vary")
	void shouldAllowConfiguredOriginOnSimpleRequest() {
		EntityExchangeResult<String> result = this.client.get()
			.uri("/actuator/health")
			.header(HttpHeaders.ORIGIN, ALLOWED)
			.exchange()
			.returnResult(String.class);

		assertThat(result.getStatus().value()).isEqualTo(200);
		HttpHeaders headers = result.getResponseHeaders();
		assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);
		assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
		assertThat(String.join(",", headers.getOrEmpty(HttpHeaders.VARY))).contains("Origin");
	}

	@Test
	@DisplayName("FR-020 / US3 CORS 2: preflight permitido sin token anuncia métodos y cabeceras")
	void shouldAnswerPreflightWithoutToken() {
		EntityExchangeResult<String> result = preflight(ALLOWED);

		assertThat(result.getStatus().value()).isEqualTo(200);
		HttpHeaders headers = result.getResponseHeaders();
		assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED);
		assertThat(tokens(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)))
			.contains("get", "post", "put", "patch", "delete");
		assertThat(tokens(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)))
			.contains("authorization", "content-type");
	}

	@Test
	@DisplayName("FR-020 / US3 CORS 3: origen no permitido en petición simple recibe 403 Problem Details")
	void shouldRejectUnknownOriginOnSimpleRequest() {
		EntityExchangeResult<String> result = this.client.get()
			.uri("/actuator/health")
			.header(HttpHeaders.ORIGIN, EVIL)
			.exchange()
			.returnResult(String.class);

		assertRejected(result, "/actuator/health");
	}

	@Test
	@DisplayName("FR-020 / US3 CORS 4: preflight de origen no permitido recibe 403 Problem Details")
	void shouldRejectUnknownOriginOnPreflight() {
		assertRejected(preflight(EVIL), "/api/v1/probes");
	}

	@Nested
	@TestPropertySource(properties = "mangateca.cors.allowed-origins=")
	@DisplayName("Sin orígenes configurados (falla cerrado)")
	class WithoutConfiguredOrigins {

		@Autowired
		private RestTestClient nestedClient;

		@Test
		@DisplayName("FR-020 / US3 CORS 5: cualquier Origin cruzado recibe 403 Problem Details")
		void shouldRejectAnyCrossOriginWhenNoOriginIsConfigured() {
			for (String origin : List.of(EVIL, ALLOWED)) {
				EntityExchangeResult<String> result = this.nestedClient.get()
					.uri("/actuator/health")
					.header(HttpHeaders.ORIGIN, origin)
					.exchange()
					.returnResult(String.class);

				assertRejected(result, "/actuator/health");
			}
		}

		@Test
		@DisplayName("FR-020 / US3 CORS 5: el preflight sin orígenes configurados recibe 403 Problem Details")
		void shouldRejectPreflightWhenNoOriginIsConfigured() {
			EntityExchangeResult<String> result = this.nestedClient.options()
				.uri("/api/v1/probes")
				.header(HttpHeaders.ORIGIN, ALLOWED)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.exchange()
				.returnResult(String.class);

			assertRejected(result, "/api/v1/probes");
		}

	}

	private EntityExchangeResult<String> preflight(String origin) {
		return this.client.options()
			.uri("/api/v1/probes")
			.header(HttpHeaders.ORIGIN, origin)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
			.exchange()
			.returnResult(String.class);
	}

	private static List<String> tokens(String headerValue) {
		assertThat(headerValue).isNotNull();
		return Arrays.stream(headerValue.split(","))
			.map(value -> value.trim().toLowerCase(Locale.ROOT))
			.toList();
	}

	private static void assertRejected(EntityExchangeResult<String> result, String instance) {
		assertProblemDetails(result, 403, instance);
		assertThat(result.getResponseBody()).doesNotContain("Invalid CORS request");
		assertThat(result.getResponseHeaders().headerNames())
			.noneMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("access-control-allow-"));
	}

}
