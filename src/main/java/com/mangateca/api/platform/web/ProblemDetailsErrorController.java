package com.mangateca.api.platform.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de error del contenedor: responde Problem Details para los errores que ocurren
 * fuera de MVC (p. ej. excepciones en filtros). Sustituye a {@code BasicErrorController}.
 * Nunca usa el mensaje ni la excepcion del contenedor en la respuesta (FR-017).
 */
@Hidden
@RestController
public class ProblemDetailsErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorController.class);

	private static final String STATUS_ATTRIBUTE = "jakarta.servlet.error.status_code";

	private static final String URI_ATTRIBUTE = "jakarta.servlet.error.request_uri";

	private static final String EXCEPTION_ATTRIBUTE = "jakarta.servlet.error.exception";

	@RequestMapping("${spring.web.error.path:/error}")
	public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		HttpStatus status = resolveStatus(request.getAttribute(STATUS_ATTRIBUTE));
		Object uriAttribute = request.getAttribute(URI_ATTRIBUTE);
		String instance = uriAttribute instanceof String uri && !uri.isBlank() ? uri : "/error";
		if (request.getAttribute(EXCEPTION_ATTRIBUTE) instanceof Throwable failure) {
			log.error("Fallo no controlado ({}) en el contenedor al procesar {}", failure.getClass().getName(),
					instance, failure);
		}
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(GlobalExceptionHandler.problemDetail(status, instance));
	}

	private static HttpStatus resolveStatus(@Nullable Object attribute) {
		if (attribute instanceof Integer code) {
			HttpStatus status = HttpStatus.resolve(code);
			if (status != null && (status.is4xxClientError() || status.is5xxServerError())) {
				return status;
			}
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
