package com.mangateca.api.platform.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * Rechaza los origenes no autorizados con 403 Problem Details en lugar del texto plano por defecto.
 */
class ProblemDetailsCorsProcessor extends DefaultCorsProcessor {

	private final ProblemDetailsResponseWriter writer;

	ProblemDetailsCorsProcessor(ProblemDetailsResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		boolean allowed = super.processRequest(config, request, response);
		if (!allowed) {
			this.writer.write(request, response, HttpStatus.FORBIDDEN, "Origen no autorizado.");
		}
		return allowed;
	}

	@Override
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		// Intencionadamente vacio: el cuerpo lo escribe processRequest con Problem Details.
	}

}
