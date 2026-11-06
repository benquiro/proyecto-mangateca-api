package com.mangateca.api.identity.application;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.UserRegistered;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class AuthenticationService {

  static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_lower_uk";

  private static final String DUMMY_PASSWORD = "dummy-password-for-timing-equalization";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;
  private final AccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenService refreshTokenService;
  /** Hash del mismo codificador (algoritmo y coste reales), calculado una vez al construir. */
  private final String dummyHash;

  public AuthenticationService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher eventPublisher,
      Clock clock,
      AccessTokenIssuer accessTokenIssuer,
      RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.accessTokenIssuer = accessTokenIssuer;
    this.refreshTokenService = refreshTokenService;
    this.dummyHash = Objects.requireNonNull(passwordEncoder.encode(DUMMY_PASSWORD));
  }

  /** Usuario actual; vacio si no existe o esta deshabilitado (FR-016). */
  @Transactional(readOnly = true)
  public Optional<User> me(UUID userId) {
    return userRepository.findById(userId).filter(User::isEnabled);
  }

  /**
   * Autentica con correo y contrasena. Todo fallo produce la misma excepcion y consume un
   * {@code matches} para igualar tiempos.
   */
  @Transactional(noRollbackFor = InvalidCredentialsException.class)
  public IssuedTokens login(String email, String password) {
    Optional<User> found = findUser(email);
    Instant now = clock.instant();
    if (found.isEmpty() || !found.get().isEnabled() || found.get().isLocked(now)) {
      passwordEncoder.matches(password, dummyHash);
      throw new InvalidCredentialsException();
    }
    User user = found.get();
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      user.registerFailedLogin(now);
      throw new InvalidCredentialsException();
    }
    user.registerSuccessfulLogin(now);
    AccessTokenIssuer.IssuedAccessToken access =
        accessTokenIssuer.issue(user.getId(), user.getRole());
    String refresh = refreshTokenService.issue(user.getId(), now);
    return new IssuedTokens(access.value(), access.expiresIn(), refresh);
  }

  private Optional<User> findUser(String email) {
    try {
      return userRepository.findByEmailForUpdate(new Email(email).value());
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Registra una cuenta nueva siempre con rol CUSTOMER. */
  @Transactional
  public User register(String email, String displayName, String password) {
    if (!PasswordPolicy.isValid(password)) {
      // defensa en profundidad: sin mensaje ni causa para no filtrar datos del cliente
      throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
    }
    User user;
    try {
      Email normalized = new Email(email);
      if (userRepository.existsByEmail(normalized.value())) {
        throw new EmailAlreadyInUseException();
      }
      user =
          User.register(
              normalized, displayName, passwordEncoder.encode(password), Role.CUSTOMER, clock);
    } catch (IllegalArgumentException e) {
      // sin causa ni mensaje: podrian contener datos del cliente
      throw new ErrorResponseException(HttpStatus.BAD_REQUEST);
    }
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      if (violatesEmailUniqueIndex(e)) {
        throw new EmailAlreadyInUseException();
      }
      throw e;
    }
    eventPublisher.publishEvent(new UserRegistered(user.getId(), user.getRole(), clock.instant()));
    return user;
  }

  /** Inspecciona la cadena de causas sin leer mensajes (contienen el correo). */
  private static boolean violatesEmailUniqueIndex(DataIntegrityViolationException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException cve
          && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
