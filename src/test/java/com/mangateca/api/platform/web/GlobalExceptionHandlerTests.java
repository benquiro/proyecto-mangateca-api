package com.mangateca.api.platform.web;

import static com.mangateca.api.ProblemDetailsAssertions.assertNoInternalInfo;
import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US4 / FR-015..FR-018: catalogo de errores de MVC en Problem Details. El slice no depende de
 * SecurityConfiguration: la autenticacion se simula con jwt() y csrf().
 */
@WebMvcTest(ProbeController.class)
@Import(GlobalExceptionHandler.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

	private static final String PROBES = "/api/v1/probes";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Test
	@DisplayName("FR-015 / US4 caso 1: JSON malformado responde 400 Problem Details")
	void shouldReturn400WhenJsonIsMalformed() {
		MvcTestResult result = this.mvc.post()
			.uri(PROBES)
			.with(jwt())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":")
			.exchange();

		assertThat(result).hasStatus(400);
		assertProblem(result, 400, PROBES);
		assertThat(body(result)).doesNotContain("\"errors\"");
	}

	@Test
	@DisplayName("FR-016 / US4 caso 2: validacion de DTO responde 400 con errors de 2 elementos {field, message}")
	void shouldReturn400WithAllFieldErrorsWhenBodyIsInvalid() {
		MvcTestResult result = this.mvc.post()
			.uri(PROBES)
			.with(jwt())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"\",\"quantity\":-1}")
			.exchange();

		assertThat(result).hasStatus(400);
		assertProblem(result, 400, PROBES);
		JsonNode errors = MAPPER.readTree(body(result)).path("errors");
		assertThat(errors.isArray()).isTrue();
		assertThat(errors.size()).isEqualTo(2);
		List<String> fields = new ArrayList<>();
		for (JsonNode item : errors) {
			assertThat(item.propertyNames()).containsExactlyInAnyOrder("field", "message");
			assertThat(item.path("message").asString()).isNotBlank();
			fields.add(item.path("field").asString());
		}
		assertThat(fields).containsExactlyInAnyOrder("name", "quantity");
		// nunca el valor rechazado
		assertThat(body(result)).doesNotContain("-1");
	}

	@Test
	@DisplayName("FR-016 / US4 caso 3: parametro invalido (HandlerMethodValidationException) responde 400 con errors que incluye size")
	void shouldReturn400WithSizeErrorWhenParameterIsInvalid() {
		MvcTestResult result = this.mvc.get().uri(PROBES).param("size", "0").with(jwt()).exchange();

		assertThat(result).hasStatus(400);
		assertProblem(result, 400, PROBES);
		JsonNode errors = MAPPER.readTree(body(result)).path("errors");
		assertThat(errors.isArray()).isTrue();
		List<String> fields = new ArrayList<>();
		for (JsonNode item : errors) {
			assertThat(item.path("message").asString()).isNotBlank();
			fields.add(item.path("field").asString());
		}
		assertThat(fields).contains("size");
		// nunca el valor rechazado: se comprueba en los mensajes parseados, no en todo el cuerpo
		for (JsonNode item : errors) {
			assertThat(item.path("message").asString()).doesNotContainPattern("(?<![0-9])0(?![0-9])");
		}
	}

	@Test
	@DisplayName("FR-017 / FR-018 / US4 caso 8: ResponseStatusException 500 responde generico y se registra en ERROR")
	void shouldReturn500AndLogErrorWhenResponseStatusExceptionIs5xx(CapturedOutput output) {
		MvcTestResult result = this.mvc.get().uri(PROBES + "/status-failure").with(jwt()).exchange();

		assertThat(result).hasStatus(500);
		assertProblem(result, 500, PROBES + "/status-failure");
		assertThat(body(result)).doesNotContain("razon interna", "SELECT");
		assertThat(output.getAll().lines()
			.anyMatch((line) -> line.contains("ResponseStatusException")
					&& (line.contains("\"level\":\"ERROR\"") || line.contains(" ERROR "))))
			.as("la ResponseStatusException 5xx se registra en nivel ERROR")
			.isTrue();
	}

	@Test
	@DisplayName("FR-015 / US4 caso 9: Accept application/xml responde 406 Problem Details en problem+json")
	void shouldReturn406ProblemJsonWhenAcceptIsXml() {
		MvcTestResult result = this.mvc.get()
			.uri(PROBES + "/json-only")
			.with(jwt())
			.accept(MediaType.APPLICATION_XML)
			.exchange();

		assertThat(result).hasStatus(406);
		assertProblem(result, 406, PROBES + "/json-only");
		JsonNode json = MAPPER.readTree(body(result));
		assertThat(json.path("status").asInt()).isEqualTo(406);
		assertThat(json.path("title").asString()).isEqualTo("Not Acceptable");
		assertThat(json.path("detail").asString()).isNotBlank();
	}

	@Test
	@DisplayName("FR-015 / US4 caso 4: ruta inexistente autenticada responde 404 Problem Details")
	void shouldReturn404WhenRouteDoesNotExist() {
		MvcTestResult result = this.mvc.get().uri("/api/v1/no-existe").with(jwt()).exchange();

		assertThat(result).hasStatus(404);
		assertProblem(result, 404, "/api/v1/no-existe");
	}

	@Test
	@DisplayName("FR-015 / US4 caso 5: metodo no soportado responde 405 Problem Details con cabecera Allow")
	void shouldReturn405WithAllowHeaderWhenMethodIsNotSupported() {
		MvcTestResult result = this.mvc.delete().uri(PROBES).with(jwt()).with(csrf()).exchange();

		assertThat(result).hasStatus(405);
		assertProblem(result, 405, PROBES);
		assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).isNotBlank().contains("GET");
	}

	@Test
	@DisplayName("FR-015 / US4 caso 6: Content-Type no soportado responde 415 Problem Details")
	void shouldReturn415WhenContentTypeIsNotSupported() {
		MvcTestResult result = this.mvc.post()
			.uri(PROBES)
			.with(jwt())
			.with(csrf())
			.contentType(MediaType.TEXT_PLAIN)
			.content("hola")
			.exchange();

		assertThat(result).hasStatus(415);
		assertProblem(result, 415, PROBES);
	}

	@Test
	@DisplayName("FR-017 / FR-018 / US4 caso 7: fallo inesperado responde 500 generico y se registra en ERROR")
	void shouldReturn500WithoutInternalInfoAndLogErrorWhenUnexpectedFailure(CapturedOutput output) {
		MvcTestResult result = this.mvc.get().uri(PROBES + "/failure").with(jwt()).exchange();

		assertThat(result).hasStatus(500);
		assertProblem(result, 500, PROBES + "/failure");
		assertThat(body(result)).doesNotContain("detalle interno", "SELECT", "IllegalStateException");
		assertThat(output.getAll()).contains("IllegalStateException");
		assertThat(output.getAll().lines()
			.anyMatch((line) -> line.contains("IllegalStateException")
					&& (line.contains("\"level\":\"ERROR\"") || line.contains(" ERROR "))))
			.as("la excepcion se registra en nivel ERROR")
			.isTrue();
	}

	@Test
	@DisplayName("FR-020 / research R10: ProblemDetail con type propio conserva type y detail y solo normaliza title e instance")
	void shouldKeepCustomTypeAndDetailWhenProblemDetailHasCustomType() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();
		URI customType = URI.create("urn:mangateca:error:test");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "detalle de dominio");
		problem.setType(customType);
		problem.setTitle("titulo de dominio");
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/probes");

		ResponseEntity<Object> response = handler.handleExceptionInternal(new ErrorResponseException(
				HttpStatus.CONFLICT, problem, null), null, new HttpHeaders(), HttpStatus.CONFLICT,
				new ServletWebRequest(request));

		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, body -> {
			assertThat(body.getType()).isEqualTo(customType);
			assertThat(body.getDetail()).isEqualTo("detalle de dominio");
			assertThat(body.getTitle()).isEqualTo("Conflict");
			assertThat(body.getInstance()).isEqualTo(URI.create("/api/v1/probes"));
		});
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * Comprueba contra problem-details.schema.json: campos requeridos, type, status del
	 * enum y ausencia de trace, stackTrace y exception.
	 */
	private static void assertProblem(MvcTestResult result, int status, String instance) {
		String body = body(result);
		String contentType = result.getResponse().getContentType();
		assertProblemDetails(status, contentType == null ? null : MediaType.parseMediaType(contentType), body,
				instance);
		JsonNode json = MAPPER.readTree(body);
		assertThat(json.propertyNames()).contains("type", "title", "status", "detail", "instance")
			.doesNotContain("trace", "stackTrace", "exception");
		assertThat(List.of(400, 401, 403, 404, 405, 406, 415, 500)).contains(json.path("status").asInt());
		assertNoInternalInfo(body);
	}

}
