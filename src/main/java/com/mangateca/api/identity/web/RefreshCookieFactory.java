package com.mangateca.api.identity.web;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** ADELANTO DE FASE 4 (T043): firma minima para compilar T022. */
@Component
public class RefreshCookieFactory {

  public ResponseCookie create(String token) {
    throw new UnsupportedOperationException("T043");
  }

  public ResponseCookie clear() {
    throw new UnsupportedOperationException("T043");
  }
}
