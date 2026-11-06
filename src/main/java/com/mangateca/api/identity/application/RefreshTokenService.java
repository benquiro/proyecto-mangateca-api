package com.mangateca.api.identity.application;

import com.mangateca.api.identity.config.IdentityProperties;
import com.mangateca.api.identity.domain.RefreshToken;
import com.mangateca.api.identity.domain.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emision de refresh tokens opacos; solo se persiste su SHA-256 (T040). */
@Service
public class RefreshTokenService {

  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository refreshTokenRepository;
  private final IdentityProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository, IdentityProperties properties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.properties = properties;
  }

  /** Crea una familia nueva y devuelve el valor en claro, que no se vuelve a poder obtener. */
  @Transactional
  public String issue(UUID userId, Instant now) {
    byte[] random = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(random);
    String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    refreshTokenRepository.save(
        RefreshToken.issue(
            userId, sha256(plain), UUID.randomUUID(), now, properties.refreshTokenTtl()));
    return plain;
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}
