package com.mangateca.api.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.platform.web.ProbeController;

/**
 * Constitución VI: autorización de controladores con {@code @WebMvcTest}. Tokens reales firmados
 * con el JwtEncoder de pruebas, para ejercitar el conversor del claim {@code roles}.
 */
@WebMvcTest(ProbeController.class)
@Import({ SecurityConfiguration.class, ProblemDetailsResponseWriter.class,
		ProblemDetailsAuthenticationEntryPoint.class, ProblemDetailsAccessDeniedHandler.class,
		TestSecurityConfiguration.class })
class ProbeSecurityWebMvcTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	@DisplayName("FR-019 / US3 escenario 1: sin token responde 401 Problem Details con WWW-Authenticate Bearer")
	void shouldReturn401WhenTokenIsMissing() {
		MvcTestResult result = this.mvc.get().uri("/api/v1/probes/admin").exchange();

		assertThat(result).hasStatus(401).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		assertThat(result).headers().hasValue(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(401);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/probes/admin");
	}

	@Test
	@DisplayName("FR-022 / US3 escenario 6: CUSTOMER recibe 403 Problem Details en ruta de ADMIN")
	void shouldReturn403WhenRoleIsCustomerOnAdminRoute() {
		MvcTestResult result = this.mvc.get()
			.uri("/api/v1/probes/admin")
			.header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER"))
			.exchange();

		assertThat(result).hasStatus(403);
		assertThat(result).headers().hasValue(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Forbidden");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/probes/admin");
	}

	@Test
	@DisplayName("FR-022 / US3 escenario 6: ADMIN recibe 200 en ruta de ADMIN")
	void shouldReturn200WhenRoleIsAdminOnAdminRoute() {
		MvcTestResult result = this.mvc.get()
			.uri("/api/v1/probes/admin")
			.header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
			.exchange();

		assertThat(result).hasStatusOk().bodyText().isEqualTo("admin");
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 7: autenticado basta en ruta sin rol")
	void shouldReturn200WhenCustomerAccessesAuthenticatedRoute() {
		MvcTestResult result = this.mvc.get()
			.uri("/api/v1/probes?size=1")
			.header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER"))
			.exchange();

		assertThat(result).hasStatusOk();
	}

	@Test
	@DisplayName("FR-019 / US3 escenario 3: POST sin token responde 401 y no 403 (CSRF desactivado)")
	void shouldReturn401WhenPostWithoutToken() {
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/probes")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"x\",\"quantity\":1}")
			.exchange();

		assertThat(result).hasStatus(401);
	}

	private String bearer(String role) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("user-1")
			.issuedAt(now)
			.expiresAt(now.plus(1, ChronoUnit.HOURS))
			.claim("roles", List.of(role))
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return "Bearer " + this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
