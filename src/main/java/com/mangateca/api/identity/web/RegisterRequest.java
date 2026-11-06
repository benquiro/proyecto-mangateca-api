package com.mangateca.api.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Solicitud de registro. {@code role} existe solo para rechazarlo si el cliente lo envia. */
public record RegisterRequest(
    @Schema(
            description = "Se recorta y se compara sin distinguir capitalizacion.",
            example = "ana@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        @Nullable String email,
    @Schema(description = "Longitud valida tras recortar espacios.", example = "Ana")
        @NotBlank
        @Size(max = 100)
        @Nullable String displayName,
    @Schema(
            description = "8-64 caracteres y como maximo 72 bytes en UTF-8.",
            format = "password",
            minLength = 8,
            maxLength = 64,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidPassword
        @Nullable String password,
    @Schema(
            description = "Debe estar ausente o ser null. Cualquier valor produce 400.",
            nullable = true)
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
