package com.mangateca.api.identity.application;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.config.IdentityProperties;
import com.mangateca.api.identity.domain.Email;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Crea el primer administrador a partir de la configuracion (research R13). Idempotente y nunca
 * hace fallar el arranque; no registra correo, contrasena ni hash.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdminRunner.class);
  private static final String DISPLAY_NAME = "Administrator";

  private final IdentityProperties properties;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public BootstrapAdminRunner(
      IdentityProperties properties,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    IdentityProperties.BootstrapAdmin config = properties.bootstrapAdmin();
    String rawEmail = blankToNull(config.email());
    String password = blankToNull(config.password());
    if (rawEmail == null && password == null) {
      LOG.info("Bootstrap administrator not configured; skipping");
      return;
    }
    if (rawEmail == null || password == null) {
      LOG.warn("Bootstrap administrator configuration is incomplete; skipping");
      return;
    }
    Email email;
    try {
      email = new Email(rawEmail);
    } catch (IllegalArgumentException e) {
      LOG.warn("Bootstrap administrator email is invalid; skipping");
      return;
    }
    if (!PasswordPolicy.isValid(password)) {
      LOG.warn("Bootstrap administrator password does not meet the password policy; skipping");
      return;
    }
    if (userRepository.existsByRole(Role.ADMIN)) {
      LOG.debug("An administrator already exists; nothing to do");
      return;
    }
    if (userRepository.existsByEmail(email.value())) {
      LOG.warn("Bootstrap administrator email belongs to an existing non-admin account; skipping");
      return;
    }
    User admin =
        User.register(email, DISPLAY_NAME, passwordEncoder.encode(password), Role.ADMIN, clock);
    try {
      userRepository.saveAndFlush(admin);
    } catch (DataIntegrityViolationException e) {
      // Otra instancia gano la carrera. La transaccion queda marcada rollback-only: se marca
      // tambien de forma local para que el commit sea un rollback silencioso y no un
      // UnexpectedRollbackException que tumbe el arranque.
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      LOG.info("Bootstrap administrator was created concurrently by another instance; skipping");
      return;
    }
    LOG.info("Bootstrap administrator created with id {}", admin.getId());
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
