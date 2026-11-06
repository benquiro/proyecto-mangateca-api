package com.mangateca.api.identity.application;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Credenciales invalidas; el detalle es constante para no distinguir la causa (T039). */
public class InvalidCredentialsException extends ErrorResponseException {

  private static final long serialVersionUID = 1L;

  private static final URI TYPE = URI.create("urn:mangateca:problem:invalid-credentials");
  private static final String DETAIL = "Credenciales inválidas.";

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, problemDetail(), null);
  }

  private static ProblemDetail problemDetail() {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETAIL);
    problem.setType(TYPE);
    return problem;
  }
}
