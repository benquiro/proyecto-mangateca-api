package com.mangateca.api.identity.application;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.UserRegistered;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.time.Clock;
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

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public AuthenticationService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
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
