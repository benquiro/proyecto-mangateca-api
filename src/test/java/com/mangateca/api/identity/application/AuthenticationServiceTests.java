package com.mangateca.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.UserRegistered;
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
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.ErrorResponseException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String EMAIL = "carrera@example.com";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private AuthenticationService service;

  @BeforeEach
  void setUp() {
    IdentityProperties properties = identityProperties();
    when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
    service =
        new AuthenticationService(
            userRepository,
            passwordEncoder,
            eventPublisher,
            CLOCK,
            new AccessTokenIssuer(jwtEncoder(), properties, CLOCK),
            new RefreshTokenService(refreshTokenRepository, properties));
    // el constructor calcula el hash ficticio; los tests parten sin interacciones previas
    clearInvocations(passwordEncoder);
  }

  private static IdentityProperties identityProperties() {
    return new IdentityProperties(
        new IdentityProperties.Jwt("mangateca-test", null, null),
        Duration.ofMinutes(15),
        Duration.ofDays(7),
        new IdentityProperties.RefreshCookie("Strict"),
        new IdentityProperties.BootstrapAdmin(null, null));
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

  @Nested
  class Login {

    private static final String PASSWORD = "S3cret-pass-123";
    private static final String WRONG_PASSWORD = "Otra-clave-999";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final List<Object> publishedEvents = new ArrayList<>();
    private AuthenticationService loginService;
    private User user;

    @BeforeEach
    void setUpLogin() {
      PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
      IdentityProperties properties = identityProperties();
      user =
          User.register(new Email(EMAIL), "Ana", encoder.encode(PASSWORD), Role.CUSTOMER, clock);
      lenient().when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
      lenient()
          .when(userRepository.findByEmailForUpdate("nadie@example.com"))
          .thenReturn(Optional.empty());
      lenient().when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      loginService =
          new AuthenticationService(
              userRepository,
              encoder,
              publishedEvents::add,
              clock,
              new AccessTokenIssuer(jwtEncoder(), properties, clock),
              new RefreshTokenService(refreshTokenRepository, properties));
    }

    @Test
    @DisplayName("US2 Escenario 2 / FR-009: contrasena erronea y correo inexistente son indistinguibles")
    void shouldThrowSameInvalidCredentialsWhenPasswordIsWrongOrEmailDoesNotExist() {
      InvalidCredentialsException wrongPassword =
          catchInvalidCredentials(() -> loginService.login(EMAIL, WRONG_PASSWORD));
      InvalidCredentialsException unknownEmail =
          catchInvalidCredentials(() -> loginService.login("nadie@example.com", PASSWORD));

      ProblemDetail a = wrongPassword.getBody();
      ProblemDetail b = unknownEmail.getBody();
      assertThat(a.getStatus()).isEqualTo(401).isEqualTo(b.getStatus());
      assertThat(a.getType()).hasToString("urn:mangateca:problem:invalid-credentials");
      assertThat(a.getType()).isEqualTo(b.getType());
      assertThat(a.getDetail()).isEqualTo("Credenciales inválidas.").isEqualTo(b.getDetail());
      assertThat(a.getTitle()).isEqualTo(b.getTitle());
      assertThat(a.getProperties()).isEqualTo(b.getProperties());
    }

    @Test
    @DisplayName("US2 Escenario 3 / FR-010 / SC-004: cinco fallos bloquean y el acceso vuelve a los 15 min")
    void shouldRejectCorrectPasswordWhileLockedAndAcceptItAfterFifteenMinutes() {
      for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
        catchInvalidCredentials(() -> loginService.login(EMAIL, WRONG_PASSWORD));
      }

      catchInvalidCredentials(() -> loginService.login(EMAIL, PASSWORD));

      clock.advance(User.LOCK_DURATION);
      IssuedTokens tokens = loginService.login(EMAIL, PASSWORD);

      assertThat(tokens).isNotNull();
      assertThat(tokens.accessToken()).isNotBlank();
      assertThat(tokens.refreshToken()).isNotBlank();
      assertThat(tokens.expiresIn()).isEqualTo(900L);
      assertThat(user.getFailedLoginAttempts()).isZero();
      assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("US2 Escenario 4: un login correcto con 4 fallos reinicia el contador")
    void shouldResetFailedAttemptsWhenLoginSucceedsAfterFourFailures() {
      for (int i = 0; i < User.MAX_FAILED_ATTEMPTS - 1; i++) {
        catchInvalidCredentials(() -> loginService.login(EMAIL, WRONG_PASSWORD));
      }
      assertThat(user.getFailedLoginAttempts()).isEqualTo(4);

      IssuedTokens tokens = loginService.login(EMAIL, PASSWORD);

      assertThat(tokens).isNotNull();
      assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("US2 Escenario 5 / FR-011: cuenta deshabilitada con credenciales correctas es rechazada")
    void shouldThrowSameInvalidCredentialsWhenAccountIsDisabled() {
      user.disable(clock.instant());

      InvalidCredentialsException disabled =
          catchInvalidCredentials(() -> loginService.login(EMAIL, PASSWORD));
      InvalidCredentialsException wrongPassword =
          catchInvalidCredentials(() -> loginService.login(EMAIL, WRONG_PASSWORD));

      assertThat(disabled.getBody().getStatus()).isEqualTo(401);
      assertThat(disabled.getBody().getType()).isEqualTo(wrongPassword.getBody().getType());
      assertThat(disabled.getBody().getDetail()).isEqualTo(wrongPassword.getBody().getDetail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-es-correo", "", "a@"})
    @DisplayName("US2 / FR-015: correo que el VO Email rechaza produce las mismas credenciales invalidas")
    void shouldThrowSameInvalidCredentialsWithoutQueryingRepositoryWhenEmailIsMalformed(
        String malformed) {
      InvalidCredentialsException wrongPassword =
          catchInvalidCredentials(() -> loginService.login(EMAIL, WRONG_PASSWORD));
      InvalidCredentialsException malformedEmail =
          catchInvalidCredentials(() -> loginService.login(malformed, PASSWORD));

      ProblemDetail a = wrongPassword.getBody();
      ProblemDetail b = malformedEmail.getBody();
      assertThat(b.getStatus()).isEqualTo(401).isEqualTo(a.getStatus());
      assertThat(b.getType()).isEqualTo(a.getType());
      assertThat(b.getTitle()).isEqualTo(a.getTitle());
      assertThat(b.getDetail()).isEqualTo(a.getDetail());
      assertThat(b.getProperties()).isEqualTo(a.getProperties());
      // solo la llamada del intento con contrasena erronea (correo valido) llega al repositorio
      verify(userRepository).findByEmailForUpdate(EMAIL);
      verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("US2 / FR-015: el refresh token se persiste solo como SHA-256 y cada login abre otra familia")
    void shouldPersistOnlySha256OfRefreshTokenAndUseDistinctFamiliesPerLogin()
        throws NoSuchAlgorithmException {
      IssuedTokens first = loginService.login(EMAIL, PASSWORD);
      IssuedTokens second = loginService.login(EMAIL, PASSWORD);

      ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenRepository, times(2)).save(captor.capture());
      RefreshToken firstSaved = captor.getAllValues().get(0);
      RefreshToken secondSaved = captor.getAllValues().get(1);

      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] expected = sha256.digest(first.refreshToken().getBytes(StandardCharsets.UTF_8));
      assertThat(firstSaved.getTokenHash())
          .hasSize(32)
          .isEqualTo(expected)
          .isNotEqualTo(first.refreshToken().getBytes(StandardCharsets.UTF_8));
      assertThat(secondSaved.getTokenHash())
          .isEqualTo(sha256.digest(second.refreshToken().getBytes(StandardCharsets.UTF_8)));
      assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
      assertThat(firstSaved.getFamilyId()).isNotEqualTo(secondSaved.getFamilyId());
    }

    private InvalidCredentialsException catchInvalidCredentials(Runnable action) {
      return org.assertj.core.api.Assertions.catchThrowableOfType(
          InvalidCredentialsException.class, action::run);
    }
  }

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: carrera por correo duplicado se traduce en 409 sin evento")
  void shouldThrowEmailAlreadyInUseWhenUniqueIndexRejectsConcurrentInsert() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode("S3cret-pass-123")).thenReturn("hash");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(integrityViolation(AuthenticationService.EMAIL_UNIQUE_CONSTRAINT));

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isInstanceOf(EmailAlreadyInUseException.class)
        .extracting(e -> ((EmailAlreadyInUseException) e).getStatusCode().value())
        .isEqualTo(409);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName(
      "US1 Escenario 2 / FR-002: otra violacion de integridad no se confunde con correo duplicado")
  void shouldRethrowDataIntegrityViolationWhenConstraintIsNotTheEmailIndex() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode("S3cret-pass-123")).thenReturn("hash");
    DataIntegrityViolationException otherConstraint = integrityViolation("users_pkey");
    DataIntegrityViolationException noHibernateCause =
        new DataIntegrityViolationException("violacion generica");
    when(userRepository.saveAndFlush(any(User.class))).thenThrow(otherConstraint, noHibernateCause);

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isSameAs(otherConstraint);
    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isSameAs(noHibernateCause);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: correo ya registrado responde 409 sin intentar guardar")
  void shouldThrowEmailAlreadyInUseWithoutSavingWhenEmailExists() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isInstanceOf(EmailAlreadyInUseException.class);
    verify(userRepository, never()).saveAndFlush(any(User.class));
    verify(eventPublisher, never()).publishEvent(any(UserRegistered.class));
  }

  @ParameterizedTest
  @MethodSource("invalidPasswords")
  @DisplayName("US1 / FR-003: contrasena invalida responde 400 sin efectos laterales")
  void shouldThrow400WithoutSideEffectsWhenPasswordViolatesPolicy(String password) {
    assertThatThrownBy(() -> service.register(EMAIL, "Ana", password))
        .isInstanceOfSatisfying(
            ErrorResponseException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(400);
              assertThat(e.getMessage()).doesNotContain(EMAIL);
              assertThat(e.getBody().getDetail()).isNull();
              if (password != null) {
                assertThat(e.getMessage()).doesNotContain(password);
              }
            });
    verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
  }

  static Stream<String> invalidPasswords() {
    return Stream.of(
        null,
        "a".repeat(7),
        "a".repeat(65),
        "\u00f1".repeat(40)); // 40 caracteres pero 80 bytes UTF-8
  }

  @Test
  @DisplayName("US1 / FR-003: contrasena valida (64 caracteres) sigue registrando y publica evento")
  void shouldRegisterAndPublishEventWhenPasswordIsValid() {
    String password = "a".repeat(64);
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(password)).thenReturn("hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

    User user = service.register(EMAIL, "Ana", password);

    assertThat(user.getEmail()).isEqualTo(EMAIL);
    verify(eventPublisher).publishEvent(any(UserRegistered.class));
  }

  private static DataIntegrityViolationException integrityViolation(String constraint) {
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "violacion", new SQLException("sql"), "insert into users", constraint);
    assertThat(cause.getConstraintName()).isEqualTo(constraint);
    return new DataIntegrityViolationException("integridad", cause);
  }
}
