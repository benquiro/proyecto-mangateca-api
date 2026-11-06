package com.mangateca.api.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Evento de dominio publicado al crear una cuenta. No contiene correo ni nombre (sin PII). */
public record UserRegistered(UUID userId, Role role, Instant occurredAt) {

  public UserRegistered {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
