package com.mangateca.api.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * FR-020 / data-model CorsProperties: invariantes y binding de {@code mangateca.cors}.
 */
class CorsPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(CorsPropertiesConfiguration.class);

	@Test
	@DisplayName("FR-020 / CorsProperties: lista vacía da lista vacía (falla cerrado)")
	void shouldKeepEmptyListWhenAllowedOriginsIsEmpty() {
		assertThat(new CorsProperties(List.of()).allowedOrigins()).isEmpty();
	}

	@Test
	@DisplayName("FR-020 / CorsProperties: null da lista vacía, nunca null")
	void shouldNormalizeNullToEmptyList() {
		assertThat(new CorsProperties(null).allowedOrigins()).isNotNull().isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://localhost:5173", "https://app.example.com:8443", "https://app.example.com" })
	@DisplayName("FR-020 / CorsProperties: acepta scheme://host[:port]")
	void shouldAcceptWellFormedOrigins(String origin) {
		assertThat(new CorsProperties(List.of(origin)).allowedOrigins()).containsExactly(origin);
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "https://app.example.com/", "https://app.example.com/path", "app.example.com",
			"ftp://x" })
	@DisplayName("FR-020 / CorsProperties: rechaza comodín, barra final, ruta, sin esquema y esquema no http(s)")
	void shouldRejectInvalidOrigins(String origin) {
		assertThatIllegalArgumentException().isThrownBy(() -> new CorsProperties(List.of(origin)))
			.withMessageContaining(origin);
	}

	@Test
	@DisplayName("FR-020 / CorsProperties: un valor inválido entre válidos invalida toda la lista")
	void shouldRejectListContainingOneInvalidOrigin() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CorsProperties(List.of("http://localhost:5173", "*")));
	}

	@Test
	@DisplayName("FR-020 / CorsProperties: la lista expuesta es inmutable y es una copia")
	void shouldExposeImmutableCopy() {
		List<String> source = new ArrayList<>(List.of("http://localhost:5173"));

		CorsProperties properties = new CorsProperties(source);
		source.add("https://other.example");

		assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173");
		assertThatThrownBy(() -> properties.allowedOrigins().add("https://x.example"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("FR-020 / binding: sin propiedad el contexto arranca con lista vacía")
	void shouldBindEmptyListWhenPropertyIsAbsent() {
		this.runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(CorsProperties.class).allowedOrigins()).isEmpty();
		});
	}

	@Test
	@DisplayName("FR-020 / binding: lista separada por comas se enlaza en orden")
	void shouldBindCommaSeparatedOrigins() {
		this.runner.withPropertyValues("mangateca.cors.allowed-origins=http://localhost:5173,https://app.example.com:8443")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(CorsProperties.class).allowedOrigins())
					.containsExactly("http://localhost:5173", "https://app.example.com:8443");
			});
	}

	@Test
	@DisplayName("FR-020 / binding: con '*' el contexto falla al arrancar")
	void shouldFailContextWhenAllowedOriginsIsWildcard() {
		this.runner.withPropertyValues("mangateca.cors.allowed-origins=*")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("FR-020 / binding: con un origen mal formado el contexto falla al arrancar")
	void shouldFailContextWhenAllowedOriginIsMalformed() {
		this.runner.withPropertyValues("mangateca.cors.allowed-origins=https://app.example.com/")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CorsProperties.class)
	static class CorsPropertiesConfiguration {

	}

}
