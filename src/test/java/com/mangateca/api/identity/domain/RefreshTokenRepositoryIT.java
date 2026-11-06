package com.mangateca.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.identity.Role;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RefreshTokenRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration TTL = Duration.ofDays(7);

  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TestEntityManager testEntityManager;

  @Test
  @DisplayName("US3 Escenario 2 / FR-013: revokeFamily revoca solo los tokens vigentes de la familia")
  void shouldRevokeOnlyActiveTokensOfFamilyAndLeaveOtherFamilyIntact() {
    User user =
        userRepository.saveAndFlush(
            User.register(new Email("ana@example.com"), "Ana", "hash", Role.CUSTOMER, CLOCK));
    UUID familyA = UUID.randomUUID();
    UUID familyB = UUID.randomUUID();
    Instant earlier = NOW.minus(Duration.ofHours(1));
    RefreshToken alreadyRevoked = token(user, familyA);
    alreadyRevoked.revoke(earlier);
    RefreshToken activeA1 = token(user, familyA);
    RefreshToken activeA2 = token(user, familyA);
    RefreshToken otherFamily = token(user, familyB);
    refreshTokenRepository.saveAllAndFlush(
        List.of(alreadyRevoked, activeA1, activeA2, otherFamily));
    testEntityManager.clear();

    int revoked = refreshTokenRepository.revokeFamily(familyA, NOW);
    testEntityManager.clear();

    assertThat(revoked).isEqualTo(2);
    assertThat(reload(activeA1).getRevokedAt()).isEqualTo(NOW);
    assertThat(reload(activeA2).getRevokedAt()).isEqualTo(NOW);
    assertThat(reload(alreadyRevoked).getRevokedAt()).isEqualTo(earlier);
    assertThat(reload(otherFamily).getRevokedAt()).isNull();
  }

  private RefreshToken reload(RefreshToken token) {
    return refreshTokenRepository.findById(token.getId()).orElseThrow();
  }

  private static RefreshToken token(User user, UUID familyId) {
    byte[] hash = new byte[32];
    new SecureRandom().nextBytes(hash);
    return RefreshToken.issue(user.getId(), hash, familyId, NOW, TTL);
  }
}
