package com.mangateca.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.identity.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

@ExtendWith(OutputCaptureExtension.class)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryIT {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: existsByEmail ignora la capitalizacion")
  void shouldFindExistingEmailRegardlessOfCase() {
    userRepository.saveAndFlush(
        User.register(new Email("ana@example.com"), "Ana", "hash-1", Role.CUSTOMER, CLOCK));

    assertThat(userRepository.existsByEmail("ANA@Example.com")).isTrue();
    assertThat(userRepository.existsByEmail("otra@example.com")).isFalse();
  }

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: el indice users_email_lower_uk rechaza un correo duplicado en otra capitalizacion")
  void shouldRejectCaseInsensitiveDuplicateEmailAtDatabaseLevel(CapturedOutput output) {
    userRepository.saveAndFlush(
        User.register(new Email("ana@example.com"), "Ana", "hash-1", Role.CUSTOMER, CLOCK));
    EntityManager entityManager = testEntityManager.getEntityManager();

    assertThatThrownBy(() -> {
      entityManager.createNativeQuery("""
          INSERT INTO users (id, email, display_name, password_hash, role, created_at, updated_at)
          VALUES (:id, 'ANA@EXAMPLE.COM', 'Ana bis', 'hash-2', 'CUSTOMER', now(), now())
          """)
          .setParameter("id", UUID.randomUUID())
          .executeUpdate();
      entityManager.flush();
    }).isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("users_email_lower_uk");
    // regresion del logger org.hibernate.orm.jdbc.error: no debe filtrar el correo duplicado
    assertThat(output.getAll())
        .doesNotContain("already exists")
        .doesNotContain("Detail: Key")
        .doesNotContainIgnoringCase("ana@example.com");
  }
}
