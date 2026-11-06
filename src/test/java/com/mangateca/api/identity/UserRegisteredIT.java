package com.mangateca.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.identity.application.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class UserRegisteredIT {

  @Autowired
  private AuthenticationService authenticationService;

  @Test
  @DisplayName("US1 Escenario 1 / FR-006: registrar un usuario publica UserRegistered con rol CUSTOMER")
  void shouldPublishUserRegisteredWhenUserRegisters(Scenario scenario) {
    scenario.stimulate(() -> authenticationService.register("nuevo@example.com", "Nuevo", "S3cret-pass-123"))
      .andWaitForEventOfType(UserRegistered.class)
      .matching(event -> event.role() == Role.CUSTOMER)
      .toArriveAndVerify(event -> {
        assertThat(event.userId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
      });
  }

}
