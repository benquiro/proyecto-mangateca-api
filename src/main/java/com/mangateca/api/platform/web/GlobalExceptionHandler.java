package com.mangateca.api.platform.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

/**
 * Traduce los errores de MVC a Problem Details (RFC 9457) con un catalogo de textos generico.
 * Nunca incluye valores rechazados, trazas ni mensajes de excepciones (FR-017, FR-018).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String INVALID_FIELDS_DETAIL = "La petición contiene campos inválidos.";

	private static final String INTERNAL_ERROR_DETAIL = "Se produjo un error interno.";

	private static final String BINDING_FAILURE_MESSAGE = "Formato de valor inválido.";

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	private static final String ERRORS_PROPERTY = "errors";

	/** Elemento de la propiedad de extension {@code errors}. */
	public record FieldErrorItem(String field, String message) {
	}

	/**
	 * Unico punto de construccion de Problem Details dentro de MVC (compartido con
	 * {@link ProblemDetailsErrorController}): type, title, detail por estado e instance. La parte
	 * de fuera de MVC (filtros de seguridad) la cubre {@code ProblemDetailsResponseWriter}, que
	 * reutiliza los textos de {@link #detailFor(HttpStatusCode)}.
	 */
	static ProblemDetail problemDetail(HttpStatus status, @Nullable String instance) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detailFor(status));
		return normalize(problem, status, instance);
	}

	/** Catalogo unico de textos {@code detail} por estado, tambien usado desde {@code platform.security}. */
	public static String detailFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "El cuerpo de la petición no es válido.";
			case 401 -> "Se requiere autenticación válida.";
			case 403 -> "No tiene permisos para acceder a este recurso.";
			case 404 -> "Recurso no encontrado.";
			case 405 -> "Método no soportado para este recurso.";
			case 415 -> "Tipo de contenido no soportado.";
			default -> status.is4xxClientError() ? "La petición no es válida." : INTERNAL_ERROR_DETAIL;
		};
	}

	private static ProblemDetail normalize(ProblemDetail problem, HttpStatusCode status, @Nullable String instance) {
		problem.setType(URI.create("about:blank"));
		HttpStatus resolved = HttpStatus.resolve(status.value());
		problem.setTitle(resolved != null ? resolved.getReasonPhrase() : null);
		if (instance != null) {
			problem.setInstance(URI.create(instance));
		}
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (statusCode.is5xxServerError()) {
			// handleUnexpected no delega aqui, asi que no hay doble registro de la misma excepcion.
			log.error("Fallo inesperado ({}) al procesar {}", ex.getClass().getName(), requestPath(request), ex);
		}
		Object effectiveBody = body;
		if (effectiveBody == null && ex instanceof ErrorResponse errorResponse) {
			effectiveBody = errorResponse.getBody();
		}
		if (effectiveBody instanceof ProblemDetail problem) {
			// Un type distinto de about:blank conserva type y detail; solo se normalizan title e instance.
			// El tipo por defecto de Spring puede ser null, "" o "about:blank"; se compara por toString().
			URI type = problem.getType();
			boolean customType = type != null && !type.toString().isEmpty()
					&& !ABOUT_BLANK.toString().equals(type.toString());
			String detail = problem.getDetail();
			normalize(problem, statusCode, requestPath(request));
			if (customType) {
				problem.setType(type);
				problem.setDetail(detail);
			}
			else {
				Object errors = problem.getProperties() == null ? null : problem.getProperties().get(ERRORS_PROPERTY);
				if (errors == null) {
					problem.setDetail(detailFor(statusCode));
				}
			}
		}
		return super.handleExceptionInternal(ex, effectiveBody, headers, statusCode, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorItem> errors = new ArrayList<>();
		for (ObjectError error : ex.getBindingResult().getAllErrors()) {
			String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
			String message = error instanceof FieldError fieldError && fieldError.isBindingFailure()
					? BINDING_FAILURE_MESSAGE : messageOf(error);
			errors.add(new FieldErrorItem(field, message));
		}
		return validationResponse(ex, headers, status, request, errors);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorItem> errors = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError fieldError : parameterErrors.getFieldErrors()) {
					errors.add(new FieldErrorItem(fieldError.getField(), messageOf(fieldError)));
				}
				for (ObjectError globalError : parameterErrors.getGlobalErrors()) {
					errors.add(new FieldErrorItem(globalError.getObjectName(), messageOf(globalError)));
				}
			}
			else {
				String name = result.getMethodParameter().getParameterName();
				String field = name != null ? name : "parameter" + result.getMethodParameter().getParameterIndex();
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					errors.add(new FieldErrorItem(field, messageOf(error)));
				}
			}
		}
		return validationResponse(ex, headers, status, request, errors);
	}

	private ResponseEntity<Object> validationResponse(Exception ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request, List<FieldErrorItem> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, INVALID_FIELDS_DETAIL);
		problem.setProperty(ERRORS_PROPERTY, errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	private static String messageOf(MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return message != null && !message.isBlank() ? message : "Valor inválido.";
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		String path = request.getRequestURI();
		log.error("Fallo inesperado ({}) al procesar {}", ex.getClass().getName(), path, ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, path));
	}

	private static @Nullable String requestPath(WebRequest request) {
		return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI()
				: null;
	}

}
