package com.mangateca.api.identity.web;

import com.mangateca.api.identity.config.IdentityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Construye la cookie {@code refresh_token} (HttpOnly, Secure, SameSite configurable). */
@Component
public class RefreshCookieFactory {

  private static final String NAME = "refresh_token";
  private static final String PATH = "/api/v1/auth";

  private final IdentityProperties properties;

  public RefreshCookieFactory(IdentityProperties properties) {
    this.properties = properties;
  }

  public ResponseCookie create(String token) {
    return build(token, properties.refreshTokenTtl());
  }

  public ResponseCookie clear() {
    return build("", Duration.ZERO);
  }

  private ResponseCookie build(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .path(PATH)
        .httpOnly(true)
        .secure(true)
        .sameSite(properties.refreshCookie().sameSite())
        .maxAge(maxAge)
        .build();
  }
}
