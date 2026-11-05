package com.mangateca.api;

import org.springframework.boot.SpringApplication;

/**
 * Arranque local con Testcontainers. No importa TestSecurityConfiguration: el perfil dev (activado por
 * el plugin de Maven) ya aporta su propio JwtDecoder efimero y ambos beans chocarian.
 */
public class TestApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
