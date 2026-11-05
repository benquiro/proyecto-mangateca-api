package com.mangateca.api.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Contrato OpenAPI 3 publicado (FR-026): esquema bearer JWT aplicado globalmente.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI mangatecaOpenApi(@Value("${info.app.version:${project.version:0.0.1-SNAPSHOT}}") String version) {
		return new OpenAPI()
			.info(new Info().title("Mangateca API").version(version))
			.components(new Components().addSecuritySchemes(BEARER_AUTH,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
