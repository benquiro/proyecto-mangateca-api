package com.mangateca.api.identity.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** ADELANTO DE FASE 4 (T040): firma minima para compilar T022. */
@Service
public class RefreshTokenService {

  public String issue(UUID userId, Instant now) {
    throw new UnsupportedOperationException("T040");
  }
}
