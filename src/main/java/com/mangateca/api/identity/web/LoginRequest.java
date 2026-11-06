package com.mangateca.api.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** Cuerpo de login; toString omite credenciales. */
public record LoginRequest(
    @Schema(example = "ana@example.com") @NotBlank @Nullable String email,
    @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) @NotBlank @Nullable
        String password) {

  @Override
  public String toString() {
    return "LoginRequest[]";
  }
}
