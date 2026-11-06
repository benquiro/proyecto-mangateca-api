package com.mangateca.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.config.IdentityProperties;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.RefreshToken;
import com.mangateca.api.identity.domain.RefreshTokenRepository;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String PLAIN_TOKEN = "token-en-claro-de-prueba";

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;

  private final MutableClock clock = new MutableClock(START);
  private final UUID familyId = UUID.randomUUID();
  private RefreshTokenService service;
  private User user;
  private RefreshToken stored;

  @BeforeEach
  void setUp() {
    IdentityProperties properties =
        new IdentityProperties(
            new IdentityProperties.Jwt("mangateca-test", null, null),
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            new IdentityProperties.RefreshCookie("Strict"),
            new IdentityProperties.BootstrapAdmin(null, null));
    user = User.register(new Email("ana@example.com"), "Ana", "hash", Role.CUSTOMER, clock);
    stored =
        RefreshToken.issue(user.getId(), sha256(PLAIN_TOKEN), familyId, START, Duration.ofDays(7));
    lenient()
        .when(refreshTokenRepository.findByTokenHashForUpdate(sha256(PLAIN_TOKEN)))
        .thenReturn(Optional.of(stored));
    lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    lenient().when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    // la revocacion de familia del repositorio real tambien marca el token almacenado
    lenient()
        .when(refreshTokenRepository.revokeFamily(eq(familyId), any(Instant.class)))
        .thenAnswer(
            i -> {
              stored.revoke(i.getArgument(1));
              return 1;
            });
    service =
        new RefreshTokenService(
            refreshTokenRepository,
            userRepository,
            new AccessTokenIssuer(jwtEncoder(), properties, clock),
            properties,
            clock);
  }

  @Test
  @DisplayName("US3 Escenario 1 / FR-012: la rotacion valida emite token nuevo de la misma familia y revoca el anterior")
  void shouldIssueNewTokenInSameFamilyAndRevokePreviousWhenRotatingValidToken() {
    clock.advance(Duration.ofHours(1));

    IssuedTokens tokens = service.rotate(PLAIN_TOKEN);

    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.expiresIn()).isEqualTo(900L);
    assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(PLAIN_TOKEN);
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());
    RefreshToken created = captor.getValue();
    assertThat(created.getFamilyId()).isEqualTo(familyId);
    assertThat(created.getUserId()).isEqualTo(user.getId());
    assertThat(created.getTokenHash()).isEqualTo(sha256(tokens.refreshToken()));
    assertThat(created.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(7)));
    assertThat(stored.getRevokedAt()).isEqualTo(clock.instant());
    assertThat(stored.getReplacedBy()).isEqualTo(created.getId());
  }

  @Test
  @DisplayName("US3 Escenario 2 / FR-013 / SC-005: presentar un token ya rotado revoca la familia y responde invalido")
  void shouldRevokeFamilyAndThrowWhenAlreadyRotatedTokenIsReused() {
    service.rotate(PLAIN_TOKEN);
    clock.advance(Duration.ofMinutes(5));

    assertThatThrownBy(() -> service.rotate(PLAIN_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository).revokeFamily(familyId, clock.instant());
  }

  @Test
  @DisplayName("US3 Escenario 3 / FR-014: tras el logout el token ya no sirve para renovar")
  void shouldThrowWhenRotatingTokenAfterLogout() {
    service.revoke(PLAIN_TOKEN);

    assertThatThrownBy(() -> service.rotate(PLAIN_TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  @DisplayName("US3 Escenario 4 / FR-014: logout con token desconocido o ausente no lanza")
  void shouldNotThrowWhenRevokingUnknownOrNullToken() {
    assertThatCode(() -> service.revoke("token-desconocido")).doesNotThrowAnyException();
    assertThatCode(() -> service.revoke(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("US3 Escenario 4 / FR-014: logout con token expirado (7 dias despues) no lanza")
  void shouldNotThrowWhenRevokingExpiredToken() {
    clock.advance(Duration.ofDays(7));

    assertThatCode(() -> service.revoke(PLAIN_TOKEN)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("US3 / FR-013 / R10: token expirado o inexistente produce la misma excepcion generica")
  void shouldThrowSameInvalidRefreshTokenWhenExpiredOrUnknown() {
    clock.advance(Duration.ofDays(7));
    InvalidRefreshTokenException expired =
        catchInvalid(() -> service.rotate(PLAIN_TOKEN));
    InvalidRefreshTokenException unknown =
        catchInvalid(() -> service.rotate("token-desconocido"));

    assertThat(expired.getBody().getStatus()).isEqualTo(401);
    assertThat(expired.getBody().getType()).hasToString("urn:mangateca:problem:invalid-refresh-token");
    assertThat(unknown.getBody().getType()).isEqualTo(expired.getBody().getType());
    assertThat(unknown.getBody().getDetail()).isEqualTo(expired.getBody().getDetail());
  }

  @Test
  @DisplayName("US3 / FR-013 / R7 paso 4: usuario inexistente revoca la familia y responde el mismo invalido generico")
  void shouldRevokeFamilyAndThrowWhenUserDoesNotExist() {
    when(userRepository.findById(user.getId())).thenReturn(Optional.empty());
    InvalidRefreshTokenException unknown = catchInvalid(() -> service.rotate("token-desconocido"));

    InvalidRefreshTokenException thrown = catchInvalid(() -> service.rotate(PLAIN_TOKEN));

    assertThat(thrown).isNotNull();
    assertThat(thrown.getBody().getType()).isEqualTo(unknown.getBody().getType());
    assertThat(thrown.getBody().getDetail()).isEqualTo(unknown.getBody().getDetail());
    verify(refreshTokenRepository).revokeFamily(familyId, clock.instant());
  }

  @Test
  @DisplayName("US3 / FR-013 / R7 paso 4: usuario deshabilitado revoca la familia y responde el mismo invalido generico")
  void shouldRevokeFamilyAndThrowWhenUserIsDisabled() {
    InvalidRefreshTokenException unknown = catchInvalid(() -> service.rotate("token-desconocido"));
    user.disable(clock.instant());

    InvalidRefreshTokenException thrown = catchInvalid(() -> service.rotate(PLAIN_TOKEN));

    assertThat(thrown).isNotNull();
    assertThat(thrown.getBody().getType()).isEqualTo(unknown.getBody().getType());
    assertThat(thrown.getBody().getDetail()).isEqualTo(unknown.getBody().getDetail());
    verify(refreshTokenRepository).revokeFamily(familyId, clock.instant());
  }

  private static InvalidRefreshTokenException catchInvalid(Runnable action) {
    return org.assertj.core.api.Assertions.catchThrowableOfType(
        InvalidRefreshTokenException.class, action::run);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static NimbusJwtEncoder jwtEncoder() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      RSAKey jwk =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .build();
      return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Reloj controlable a mano: sin sleep ni hora del sistema. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
