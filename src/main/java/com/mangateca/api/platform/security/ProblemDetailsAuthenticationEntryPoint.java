package com.mangateca.api.platform.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 uniforme: no revela la causa (token ausente, expirado o firma invalida) ni registra el token.
 */
@Component
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ProblemDetailsResponseWriter writer;

	public ProblemDetailsAuthenticationEntryPoint(ProblemDetailsResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		this.writer.write(request, response, HttpStatus.UNAUTHORIZED, "Se requiere autenticación válida.");
	}

}
