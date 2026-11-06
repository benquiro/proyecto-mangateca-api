package com.mangateca.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.identity.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserLockoutTests {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  private User newUser() {
    return User.register(
        new Email("lockout@example.com"),
        "Lockout",
        "hash-placeholder",
        Role.CUSTOMER,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void failTimes(User user, int times, Instant at) {
    for (int i = 0; i < times; i++) {
      user.registerFailedLogin(at);
    }
  }

  @Test
  @DisplayName("FR-010 / US2 Escenarios 3-4: 5 fallos consecutivos bloquean la cuenta 15 minutos")
  void shouldLockAccountForFifteenMinutesAfterFiveConsecutiveFailures() {
    User user = newUser();

    failTimes(user, User.MAX_FAILED_ATTEMPTS, NOW);

    assertThat(user.isLocked(NOW)).isTrue();
    assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(User.LOCK_DURATION));
    assertThat(User.LOCK_DURATION).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  @DisplayName("FR-010 / US2 Escenarios 3-4: el bloqueo vence exactamente a los 15 minutos")
  void shouldUnlockExactlyWhenLockDurationElapses() {
    User user = newUser();
    failTimes(user, User.MAX_FAILED_ATTEMPTS, NOW);

    assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14).plusSeconds(59)))).isTrue();
    assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
  }

  @Test
  @DisplayName("FR-010 / US2 Escenarios 3-4: un fallo sobre cuenta bloqueada no amplia el bloqueo")
  void shouldNotExtendLockWhenFailingOnLockedAccount() {
    User user = newUser();
    failTimes(user, User.MAX_FAILED_ATTEMPTS, NOW);
    Instant originalLock = user.getLockedUntil();

    user.registerFailedLogin(NOW.plus(Duration.ofMinutes(5)));

    assertThat(user.getLockedUntil()).isEqualTo(originalLock);
    assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
  }

  @Test
  @DisplayName("FR-010 / US2 Escenarios 3-4: tras vencer el bloqueo el siguiente fallo reinicia el contador a 1")
  void shouldResetCounterToOneWhenFailingAfterLockExpired() {
    User user = newUser();
    failTimes(user, User.MAX_FAILED_ATTEMPTS, NOW);
    Instant afterExpiry = NOW.plus(User.LOCK_DURATION).plusSeconds(1);

    user.registerFailedLogin(afterExpiry);

    assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    assertThat(user.isLocked(afterExpiry)).isFalse();
  }

  @Test
  @DisplayName("FR-010 / US2 Escenarios 3-4: un login exitoso reinicia el contador de fallos")
  void shouldResetCounterToZeroOnSuccessfulLogin() {
    User user = newUser();
    failTimes(user, User.MAX_FAILED_ATTEMPTS - 1, NOW);

    user.registerSuccessfulLogin(NOW.plusSeconds(1));

    assertThat(user.getFailedLoginAttempts()).isZero();
  }
}
