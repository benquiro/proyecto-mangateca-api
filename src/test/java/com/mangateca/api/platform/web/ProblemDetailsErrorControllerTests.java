package com.mangateca.api.platform.web;

import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * US4 / FR-015: el endpoint de error del contenedor responde Problem Details a partir de los
 * atributos jakarta.servlet.error.*, sin usar nunca el mensaje ni la excepcion en la respuesta.
 */
@WebMvcTest(ProblemDetailsErrorController.class)
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsErrorControllerTests {

	private static final String STATUS = "jakarta.servlet.error.status_code";

	private static final String URI = "jakarta.servlet.error.request_uri";

	private static final String EXCEPTION = "jakarta.servlet.error.exception";

	private static final String MESSAGE = "jakarta.servlet.error.message";

	@Autowired
	private MockMvcTester mvc;

	@Test
	@DisplayName("FR-015 / US4 caso 1: status_code 404 y request_uri responden 404 Problem Details con esa instance")
	void shouldReturn404ProblemDetailsWhenErrorStatusIs404() throws Exception {
		MvcTestResult result = this.mvc.get()
			.uri("/error")
			.with(jwt())
			.requestAttr(STATUS, 404)
			.requestAttr(URI, "/api/v1/x")
			.exchange();

		assertThat(result).hasStatus(404);
		assertProblem(result, 404, "/api/v1/x");
	}

	@Test
	@DisplayName("FR-017 / FR-018 / US4 caso 2: excepcion del contenedor responde 500 sin filtrar internos y se registra en ERROR")
	void shouldReturn500WithoutInternalInfoAndLogErrorWhenExceptionAttributeIsPresent(CapturedOutput output)
			throws Exception {
		MvcTestResult result = this.mvc.get()
			.uri("/error")
			.with(jwt())
			.requestAttr(STATUS, 500)
			.requestAttr(URI, "/api/v1/y")
			.requestAttr(EXCEPTION, new IllegalStateException("secreto SELECT"))
			.requestAttr(MESSAGE, "secreto")
			.exchange();

		assertThat(result).hasStatus(500);
		assertProblem(result, 500, "/api/v1/y");
		assertThat(body(result)).doesNotContain("secreto", "SELECT", "IllegalStateException");
		assertThat(output.getAll()).contains("IllegalStateException");
		assertThat(output.getAll().lines()
			.anyMatch((line) -> line.contains("IllegalStateException")
					&& (line.contains("\"level\":\"ERROR\"") || line.contains(" ERROR "))))
			.as("la excepcion se registra en nivel ERROR")
			.isTrue();
	}

	@Test
	@DisplayName("FR-015 / US4 caso 3: sin atributos de error responde 500 con instance /error")
	void shouldReturn500WithErrorInstanceWhenNoErrorAttributes() throws Exception {
		MvcTestResult result = this.mvc.get().uri("/error").with(jwt()).exchange();

		assertThat(result).hasStatus(500);
		assertProblem(result, 500, "/error");
	}

	@Test
	@DisplayName("FR-015 / US4 caso 4: status_code 200 (no es un error) responde 500")
	void shouldReturn500WhenStatusCodeIsNotAnError() throws Exception {
		MvcTestResult result = this.mvc.get()
			.uri("/error")
			.with(jwt())
			.requestAttr(STATUS, 200)
			.requestAttr(URI, "/api/v1/z")
			.exchange();

		assertThat(result).hasStatus(500);
		assertProblem(result, 500, "/api/v1/z");
	}

	@Test
	@DisplayName("FR-015 / US4 caso 5: Accept text/html no provoca 406, responde 500 application/problem+json")
	void shouldReturnProblemJsonWhenAcceptIsTextHtml() throws Exception {
		MvcTestResult result = this.mvc.get()
			.uri("/error")
			.with(jwt())
			.accept(MediaType.TEXT_HTML)
			.requestAttr(STATUS, 500)
			.requestAttr(URI, "/api/v1/h")
			.exchange();

		assertThat(result).hasStatus(500);
		assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
		assertProblem(result, 500, "/api/v1/h");
	}

	private static String body(MvcTestResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static void assertProblem(MvcTestResult result, int status, String instance) throws Exception {
		String contentType = result.getResponse().getContentType();
		assertProblemDetails(status, contentType == null ? null : MediaType.parseMediaType(contentType),
				body(result), instance);
	}

}
