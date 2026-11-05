package com.mangateca.fixture.violation;

/**
 * Raiz de una aplicacion modular de fixture con una violacion intencionada: {@code billing} accede a
 * un tipo interno de {@code orders}. Existe solo en src/test y fuera de com.mangateca.api para que
 * ModularityTests sobre el codigo real no la escanee.
 */
public final class ViolationFixtureApplication {

	private ViolationFixtureApplication() {
	}

}
