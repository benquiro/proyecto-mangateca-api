package com.mangateca.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Aserciones reutilizables para respuestas RFC 9457 Problem Details. El cuerpo se parsea
 * (no se buscan subcadenas de JSON compacto) para no depender del formato de
 * serializacion; solo las comprobaciones de "sin informacion interna" usan el cuerpo
 * crudo.
 */
public final class ProblemDetailsAssertions {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private ProblemDetailsAssertions() {
	}

	/**
	 * Comprueba Content-Type, type, title, status, detail e instance, y que no se filtre
	 * informacion interna.
	 * @param result respuesta con cuerpo String
	 * @param expectedStatus codigo HTTP esperado
	 * @param expectedInstance ruta de la peticion, sin query
	 */
	public static void assertProblemDetails(EntityExchangeResult<String> result, int expectedStatus,
			String expectedInstance) {
		assertThat(result.getStatus().value()).isEqualTo(expectedStatus);
		MediaType contentType = result.getResponseHeaders().getContentType();
		assertThat(contentType).isNotNull();
		assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();

		String body = result.getResponseBody();
		assertThat(body).isNotNull().isNotBlank();
		JsonNode json = MAPPER.readTree(body);
		assertThat(json.path("type").asString()).isEqualTo("about:blank");
		assertThat(json.path("title").asString()).isEqualTo(HttpStatus.valueOf(expectedStatus).getReasonPhrase());
		assertThat(json.path("status").isNumber()).isTrue();
		assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
		assertThat(json.path("detail").asString()).isNotBlank();
		assertThat(json.path("instance").asString()).isEqualTo(expectedInstance);

		assertNoInternalInfo(body);
	}

	/**
	 * Comprueba que el cuerpo crudo no contiene trazas, nombres de excepcion, paquetes
	 * internos ni SQL.
	 * @param body cuerpo crudo de la respuesta
	 */
	public static void assertNoInternalInfo(String body) {
		assertThat(body).doesNotContain("trace", "stackTrace", "exception", "Exception", "java.", "jakarta.",
				"org.springframework", "SELECT");
	}

}
