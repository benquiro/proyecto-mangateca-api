package com.mangateca.api.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Solicitud de registro. {@code role} existe solo para rechazarlo si el cliente lo envia. */
public record RegisterRequest(
    @NotBlank @Email @Size(max = 254) @Nullable String email,
    @NotBlank @Size(max = 100) @Nullable String displayName,
    @ValidPassword @Nullable String password,
    @Nullable @Null String role) {

  public RegisterRequest {
    email = email == null ? null : email.strip();
    displayName = displayName == null ? null : displayName.strip();
  }

  @Override
  public String toString() {
    return "RegisterRequest[]";
  }
}
