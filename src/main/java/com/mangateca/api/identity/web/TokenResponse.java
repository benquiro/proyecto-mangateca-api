package com.mangateca.api.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Respuesta de login; el refresh token va solo en cookie. */
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn) {

  @Override
  public String toString() {
    return "TokenResponse[]";
  }
}
