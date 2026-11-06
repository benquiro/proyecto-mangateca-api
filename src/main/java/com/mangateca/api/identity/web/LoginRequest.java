package com.mangateca.api.identity.web;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** Cuerpo de login; toString omite credenciales. */
public record LoginRequest(@NotBlank @Nullable String email, @NotBlank @Nullable String password) {

  @Override
  public String toString() {
    return "LoginRequest[]";
  }
}
