package com.mangateca.api.platform.security;

import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestSecurityConfiguration.TestTokens;
import com.mangateca.api.TestcontainersConfiguration;

/**
 * US3 / SC-004: seguridad denegada por defecto, contra el servidor real.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
class SecurityIT {

	private static final String PROBES = "/api/v1/probes?size=1";

	@Autowired
	private RestTestClient client;

	@Autowired
	private TestTokens tokens;

	@Test
	@DisplayName("FR-019 / US3 escenario 1: GET sin token a ruta protegida responde 401")
	void shouldReturn401WhenTokenIsMissing() {
		EntityExchangeResult<String> result = get(PROBES, null);

		assertThat(result.getStatus().value()).isEqualTo(401);
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 2: ruta inexistente sin token responde 401 y no 404")
	void shouldReturn401InsteadOf404WhenRouteDoesNotExist() {
		EntityExchangeResult<String> result = get("/api/v1/no-existe", null);

		assertThat(result.getStatus().value()).isEqualTo(401);
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 3: POST sin token responde 401 y no 403 (CSRF desactivado)")
	void shouldReturn401NotForbiddenWhenPostWithoutToken() {
		EntityExchangeResult<String> result = this.client.post()
			.uri("/api/v1/probes")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\":\"x\",\"quantity\":1}")
			.exchange()
			.returnResult(String.class);

		assertThat(result.getStatus().value()).isEqualTo(401);
	}

	@Test
	@DisplayName("FR-020 / US3 escenario 4: token malformado, con otra clave y expirado dan 401 idéntico")
	void shouldReturnIdentical401ForMalformedForeignKeyAndExpiredTokens() throws JOSEException {
		EntityExchangeResult<String> malformed = get(PROBES, "x.y.z");
		EntityExchangeResult<String> foreignKey = get(PROBES, foreignKeyToken());
		EntityExchangeResult<String> expired = get(PROBES, token(List.of("CUSTOMER"), -2, -1));

		for (EntityExchangeResult<String> result : List.of(malformed, foreignKey, expired)) {
			assertThat(result.getStatus().value()).isEqualTo(401);
			String authenticate = result.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
			assertThat(authenticate).startsWith("Bearer").doesNotContain("error_description");
		}
		assertThat(foreignKey.getResponseBody()).isEqualTo(malformed.getResponseBody());
		assertThat(expired.getResponseBody()).isEqualTo(malformed.getResponseBody());
		assertThat(foreignKey.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
			.isEqualTo(malformed.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
		assertThat(expired.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
			.isEqualTo(malformed.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
	}

	@Test
	@DisplayName("FR-008 / research R3: token firmado con la clave correcta pero iss ajeno responde 401")
	void shouldReturn401WhenIssuerIsForeign() {
		EntityExchangeResult<String> foreignIssuer = get(PROBES, foreignIssuerToken());
		EntityExchangeResult<String> malformed = get(PROBES, "x.y.z");

		assertThat(foreignIssuer.getStatus().value()).isEqualTo(401);
		assertThat(foreignIssuer.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
			.startsWith("Bearer")
			.doesNotContain("error_description")
			.isEqualTo(malformed.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
		assertProblemDetails(foreignIssuer, 401, "/api/v1/probes");
	}

	@Test
	@DisplayName("FR-020 / US3 escenario 5: todo 401 es Problem Details sin información interna")
	void shouldReturnProblemDetailsWithoutInternalInfoOn401() throws JOSEException {
		for (String path : List.of(PROBES, "/api/v1/no-existe")) {
			for (String token : Arrays.asList(null, "x.y.z", foreignKeyToken(),
					token(List.of("CUSTOMER"), -2, -1))) {
				EntityExchangeResult<String> result = get(path, token);

				assertThat(result.getStatus().value()).isEqualTo(401);
				assertProblemDetails(result, 401, path.split("\\?")[0]);
			}
		}
	}

	@Test
	@DisplayName("FR-020 / US3 escenario 6: CUSTOMER recibe 403 Problem Details en ruta de ADMIN")
	void shouldReturn403ProblemDetailsWhenRoleIsInsufficient() {
		EntityExchangeResult<String> result = get("/api/v1/probes/admin", token(List.of("CUSTOMER"), 0, 1));

		assertThat(result.getStatus().value()).isEqualTo(403);
		assertProblemDetails(result, 403, "/api/v1/probes/admin");
	}

	@Test
	@DisplayName("FR-020 / US3 escenario 6: ADMIN recibe 200 en ruta de ADMIN")
	void shouldReturn200WhenRoleIsAdmin() {
		EntityExchangeResult<String> result = get("/api/v1/probes/admin", token(List.of("ADMIN"), 0, 1));

		assertThat(result.getStatus().value()).isEqualTo(200);
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 7: token válido accede a ruta autenticada")
	void shouldReturn200WhenTokenIsValid() {
		EntityExchangeResult<String> result = get(PROBES, token(List.of("CUSTOMER"), 0, 1));

		assertThat(result.getStatus().value()).isEqualTo(200);
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 8: rutas públicas sin token no dan 401 ni 403")
	void shouldNotRequireTokenOnPublicRoutes() {
		for (String path : List.of("/actuator/health", "/actuator/info", "/v3/api-docs", "/swagger-ui.html")) {
			EntityExchangeResult<String> result = get(path, null);

			assertThat(result.getStatus().value()).as(path).isNotIn(401, 403);
		}
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 9: /actuator/env sin token es 401 y con token es 404")
	void shouldProtectAndNotExposeOtherActuatorEndpoints() {
		assertThat(get("/actuator/env", null).getStatus().value()).isEqualTo(401);
		assertThat(get("/actuator/env", token(List.of("ADMIN"), 0, 1)).getStatus().value()).isEqualTo(404);
	}

	@Test
	@DisplayName("FR-019 / US3: ninguna respuesta crea sesión (sin JSESSIONID)")
	void shouldNotSetSessionCookie() {
		List<EntityExchangeResult<String>> results = List.of(get(PROBES, null), get(PROBES, "x.y.z"),
				get(PROBES, token(List.of("CUSTOMER"), 0, 1)), get("/actuator/health", null),
				get("/api/v1/probes/admin", token(List.of("CUSTOMER"), 0, 1)));

		for (EntityExchangeResult<String> result : results) {
			List<String> cookies = result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
			assertThat(cookies).noneMatch(cookie -> cookie.contains("JSESSIONID"));
		}
	}

	@Test
	@DisplayName("FR-018 / US3 escenario 11: la salida nunca contiene los tokens recibidos")
	void shouldNeverLogTokens(CapturedOutput output) throws JOSEException {
		List<String> tokens = List.of("x.y.z", foreignKeyToken(), token(List.of("CUSTOMER"), -2, -1),
				token(List.of("CUSTOMER"), 0, 1), token(List.of("ADMIN"), 0, 1));

		get(PROBES, tokens.get(0));
		get(PROBES, tokens.get(1));
		get(PROBES, tokens.get(2));
		get("/api/v1/probes/admin", tokens.get(3));
		get("/api/v1/probes/admin", tokens.get(4));
		get(PROBES, tokens.get(3));

		for (String token : tokens) {
			assertThat(output.getAll()).doesNotContain(token);
			if (token.length() > 50) {
				assertThat(output.getAll()).doesNotContain(token.substring(token.lastIndexOf('.') + 1));
			}
		}
	}

	private EntityExchangeResult<String> get(String path, String bearer) {
		RestTestClient.RequestHeadersSpec<?> request = this.client.get().uri(path);
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		return request.exchange().returnResult(String.class);
	}

	private String token(List<String> roles, long issuedHoursFromNow, long expiresHoursFromNow) {
		Instant now = Instant.now();
		return this.tokens.token("user-1", roles, now.plus(issuedHoursFromNow, ChronoUnit.HOURS),
				now.plus(expiresHoursFromNow, ChronoUnit.HOURS));
	}

	private String foreignIssuerToken() {
		Instant now = Instant.now();
		return this.tokens.tokenWithIssuer("https://otro-emisor.example", "user-1", List.of("ADMIN"), now,
				now.plus(1, ChronoUnit.HOURS));
	}

	private String foreignKeyToken() throws JOSEException {
		RSAKey other = new RSAKeyGenerator(2048).keyID("otra-clave").generate();
		JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(other)));
		Instant now = Instant.now();
		return TestTokens.encode(foreign, TestTokens.ISSUER, "user-1", List.of("ADMIN"), now,
				now.plus(1, ChronoUnit.HOURS));
	}

}
