package com.mangateca.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.identity.application.BootstrapAdminRunner;
import com.mangateca.api.identity.domain.RefreshTokenRepository;
import com.mangateca.api.identity.domain.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminIT {

  private static final String ADMIN_EMAIL = "admin-inicial@example.com";
  private static final String ADMIN_PASSWORD = "Bootstrap-Pwd-7731";

  @Nested
  @SpringBootTest(properties = {
      "mangateca.identity.bootstrap-admin.email=" + ADMIN_EMAIL,
      "mangateca.identity.bootstrap-admin.password=" + ADMIN_PASSWORD
  })
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Configured {

    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private BootstrapAdminRunner runner;

    @AfterAll
    void cleanUsers() {
      refreshTokens.deleteAll();
      users.deleteAll();
    }

    @Test
    @DisplayName("Escenario 1 / FR-017 / FR-018 / SC-006: crea el ADMIN al arrancar sin registrar la contrasena")
    void shouldCreateAdminOnStartupWithoutLoggingPassword(CapturedOutput output) {
      assertThat(users.existsByRole(Role.ADMIN)).isTrue();
      String captured = output.getAll();
      assertThat(captured).contains("Bootstrap administrator created");
      assertThat(captured).doesNotContain(ADMIN_PASSWORD).doesNotContain("{bcrypt}");
    }

    @Test
    @DisplayName("Escenario 2 / SC-007: ejecutar el runner de nuevo mantiene exactamente un ADMIN")
    void shouldKeepSingleAdminWhenRunnerExecutesAgain() {
      runner.run(new DefaultApplicationArguments());

      assertThat(users.findAll()).filteredOn(user -> user.getRole() == Role.ADMIN).hasSize(1);
    }
  }

  @Nested
  @SpringBootTest
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class NotConfigured {

    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    @AfterAll
    void cleanUsers() {
      refreshTokens.deleteAll();
      users.deleteAll();
    }

    @Test
    @DisplayName("Escenario 3 / FR-017: sin configuracion no se crea ningun ADMIN")
    void shouldNotCreateAdminWhenNotConfigured() {
      assertThat(users.existsByRole(Role.ADMIN)).isFalse();
    }
  }
}
