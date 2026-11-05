package com.mangateca.api.platform.security;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Unica serializacion de Problem Details fuera de MVC (filtros de seguridad y CORS).
 */
@Component
public class ProblemDetailsResponseWriter {

	private final JsonMapper jsonMapper;

	public ProblemDetailsResponseWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		// En Spring 7 ProblemDetail.type es null por defecto y se omite al serializar: se fija de forma
		// explicita para que el contrato (problem-details.schema.json) y los errores de MVC coincidan.
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("about:blank"));
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(URI.create(request.getRequestURI()));
		byte[] body = this.jsonMapper.writeValueAsBytes(problem);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
		response.flushBuffer();
	}

}
