package com.mangateca.api.identity.application;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Refresh token inexistente, revocado, expirado o de usuario no valido; motivo no revelado. */
public class InvalidRefreshTokenException extends ErrorResponseException {

  private static final long serialVersionUID = 1L;

  private static final URI TYPE = URI.create("urn:mangateca:problem:invalid-refresh-token");
  private static final String DETAIL = "Token de renovación inválido.";

  public InvalidRefreshTokenException() {
    super(HttpStatus.UNAUTHORIZED, problemDetail(), null);
  }

  private static ProblemDetail problemDetail() {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETAIL);
    problem.setType(TYPE);
    return problem;
  }
}
