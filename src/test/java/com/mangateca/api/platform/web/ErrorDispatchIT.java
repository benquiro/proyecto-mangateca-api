package com.mangateca.api.platform.web;

import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestcontainersConfiguration;

/**
 * US4 / FR-015: errores fuera de MVC (error dispatch a /error), contra el servidor real.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class,
		ErrorDispatchIT.FailingFilterConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ErrorDispatchIT {

	private static final String FILTER_FAILURE = "/api/v1/probes/filter-failure";

	@Autowired
	private RestTestClient client;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	@DisplayName("FR-015 / US4 caso 1: excepcion en un filtro responde 500 Problem Details con la ruta original")
	void shouldReturn500ProblemDetailsWhenFilterThrows() {
		EntityExchangeResult<String> result = get(FILTER_FAILURE);

		assertProblemDetails(result, 500, FILTER_FAILURE);
		assertThat(result.getResponseBody()).doesNotContain("fallo interno", "SELECT", "IllegalStateException",
				"timestamp", "trace", "path", "secreto");
	}

	@Test
	@DisplayName("FR-015 / US4 caso 2: GET /error directo responde 500 Problem Details, no el JSON de BasicErrorController")
	void shouldReturn500ProblemDetailsWhenErrorEndpointIsCalledDirectly() {
		EntityExchangeResult<String> result = get("/error");

		assertProblemDetails(result, 500, "/error");
	}

	private EntityExchangeResult<String> get(String path) {
		return this.client.get()
			.uri(path)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
			.exchange()
			.returnResult(String.class);
	}

	private String token() {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("user-1")
			.issuedAt(now)
			.expiresAt(now.plus(1, ChronoUnit.HOURS))
			.claims(map -> map.putAll(Map.of("roles", List.of("CUSTOMER"))))
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingFilterConfiguration {

		@Bean
		FilterRegistrationBean<FailingFilter> failingFilterRegistration() {
			FilterRegistrationBean<FailingFilter> registration = new FilterRegistrationBean<>(new FailingFilter());
			registration.addUrlPatterns(FILTER_FAILURE);
			// Despues de la cadena de seguridad (orden -100 por defecto).
			registration.setOrder(0);
			return registration;
		}

	}

	static class FailingFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			throw new IllegalStateException("fallo interno en filtro SELECT secreto");
		}

	}

}
