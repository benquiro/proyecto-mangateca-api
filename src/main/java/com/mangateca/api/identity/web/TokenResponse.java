package com.mangateca.api.identity.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Respuesta de login; el refresh token va solo en cookie. */
public record TokenResponse(
    @Schema(
            description = "JWT RS256 con claims `sub`, `roles`, `iss`, `iat`, `exp`.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("access_token")
        String accessToken,
    @Schema(allowableValues = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("token_type")
        String tokenType,
    @Schema(
            description = "Segundos hasta la expiracion.",
            example = "900",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("expires_in")
        long expiresIn) {

  @Override
  public String toString() {
    return "TokenResponse[]";
  }
}
