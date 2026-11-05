package com.mangateca.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

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

}
