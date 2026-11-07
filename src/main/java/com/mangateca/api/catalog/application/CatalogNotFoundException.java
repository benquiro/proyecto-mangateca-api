package com.mangateca.api.catalog.application;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Recurso del catálogo inexistente o no visible. Es la única excepción para producto inexistente, no publicado,
 * serie inexistente o sin volúmenes publicados, de modo que las respuestas sean indistinguibles. No lleva datos del
 * recurso.
 */
public class CatalogNotFoundException extends ErrorResponseException {

	private static final long serialVersionUID = 1L;

	public CatalogNotFoundException() {
		super(HttpStatus.NOT_FOUND, notFoundProblem(), null);
	}

	private static ProblemDetail notFoundProblem() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado.");
		problem.setType(URI.create("about:blank"));
		problem.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
		return problem;
	}

}
