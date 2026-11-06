package com.mangateca.api.identity.application;

import com.mangateca.api.identity.config.IdentityProperties;
import com.mangateca.api.identity.domain.RefreshToken;
import com.mangateca.api.identity.domain.RefreshTokenRepository;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emision, rotacion y revocacion de refresh tokens opacos; solo se persiste su SHA-256. */
@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository refreshTokenRepository;
  private final IdentityProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();
  private final UserRepository userRepository;
  private final AccessTokenIssuer accessTokenIssuer;
  private final Clock clock;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      AccessTokenIssuer accessTokenIssuer,
      IdentityProperties properties,
      Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.accessTokenIssuer = accessTokenIssuer;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Rota el refresh token: el bloqueo pesimista sobre la fila serializa peticiones concurrentes con
   * el mismo token, de modo que solo una lo ve vigente. Las revocaciones de familia deben
   * confirmarse aunque se lance la excepcion, de ahi noRollbackFor.
   */
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public IssuedTokens rotate(String token) {
    Instant now = clock.instant();
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHashForUpdate(sha256(token))
            .orElseThrow(InvalidRefreshTokenException::new);
    if (current.isRevoked()) {
      log.warn(
          "Reutilizacion de refresh token revocado; userId={} familyId={}",
          current.getUserId(),
          current.getFamilyId());
      refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
      throw new InvalidRefreshTokenException();
    }
    if (current.isExpired(now)) {
      throw new InvalidRefreshTokenException();
    }
    User user = userRepository.findById(current.getUserId()).orElse(null);
    if (user == null || !user.isEnabled()) {
      refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
      throw new InvalidRefreshTokenException();
    }
    String plain = newPlainToken();
    RefreshToken next =
        RefreshToken.issue(
            user.getId(), sha256(plain), current.getFamilyId(), now, properties.refreshTokenTtl());
    refreshTokenRepository.save(next);
    current.rotate(next.getId(), now);
    AccessTokenIssuer.IssuedAccessToken access =
        accessTokenIssuer.issue(user.getId(), user.getRole());
    return new IssuedTokens(access.value(), access.expiresIn(), plain);
  }

  /** Cierra la sesion revocando toda la familia; es idempotente y nunca lanza por el token. */
  @Transactional
  public void revoke(@Nullable String token) {
    if (token == null || token.isEmpty()) {
      return;
    }
    refreshTokenRepository
        .findByTokenHashForUpdate(sha256(token))
        .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), clock.instant()));
  }

  /** Crea una familia nueva y devuelve el valor en claro, que no se vuelve a poder obtener. */
  @Transactional
  public String issue(UUID userId, Instant now) {
    String plain = newPlainToken();
    refreshTokenRepository.save(
        RefreshToken.issue(
            userId, sha256(plain), UUID.randomUUID(), now, properties.refreshTokenTtl()));
    return plain;
  }

  private String newPlainToken() {
    byte[] random = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(random);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}
