package com.mangateca.api.identity.application;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** El correo ya pertenece a otra cuenta. El detalle es constante y no incluye el correo. */
public class EmailAlreadyInUseException extends ErrorResponseException {

  private static final long serialVersionUID = 1L;

  private static final URI TYPE = URI.create("urn:mangateca:problem:email-already-in-use");
  private static final String DETAIL = "El correo ya está en uso.";

  public EmailAlreadyInUseException() {
    super(HttpStatus.CONFLICT, problemDetail(), null);
  }

  private static ProblemDetail problemDetail() {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, DETAIL);
    problem.setType(TYPE);
    return problem;
  }
}
