package com.mangateca.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangateca.fixture.cycle.CycleFixtureApplication;
import com.mangateca.fixture.violation.ViolationFixtureApplication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(ApiApplication.class);

	@Test
	@DisplayName("FR-012 / US6: la estructura modular real no tiene violaciones ni ciclos")
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	@DisplayName("FR-012 / US6: existe el modulo platform")
	void shouldDetectPlatformModule() {
		assertThat(modules.getModuleByName("platform")).isPresent();
	}

	@Test
	@DisplayName("FR-012 / US6: se genera la documentacion PlantUML de los modulos")
	void writesDocumentation() {
		assertThatCode(() -> new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml())
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("FR-012 / SC-002: verify() detecta el acceso a un tipo interno de otro modulo")
	void shouldFailVerificationWhenModuleAccessesInternalTypeOfAnother() {
		ApplicationModules fixture = ApplicationModules.of(ViolationFixtureApplication.class);

		assertThatThrownBy(fixture::verify).isInstanceOf(Violations.class)
			.hasMessageContaining("billing")
			.hasMessageContaining("orders")
			.hasMessageContaining("BillingService")
			.hasMessageContaining("OrderInternals");
	}

	@Test
	@DisplayName("FR-012 / SC-002: verify() detecta un ciclo entre dos modulos")
	void shouldFailVerificationWhenModulesFormACycle() {
		ApplicationModules fixture = ApplicationModules.of(CycleFixtureApplication.class);

		assertThatThrownBy(fixture::verify).isInstanceOf(Violations.class)
			.hasMessageContaining("Cycle")
			.hasMessageContaining("alpha")
			.hasMessageContaining("beta");
	}

}
