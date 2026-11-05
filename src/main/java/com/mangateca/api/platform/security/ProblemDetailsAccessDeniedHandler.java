package com.mangateca.api.platform.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 403 en Problem Details para usuarios autenticados sin permisos.
 */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemDetailsResponseWriter writer;

	public ProblemDetailsAccessDeniedHandler(ProblemDetailsResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		this.writer.write(request, response, HttpStatus.FORBIDDEN,
				"No tiene permisos para acceder a este recurso.");
	}

}
