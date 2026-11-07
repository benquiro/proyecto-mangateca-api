package com.mangateca.api.catalog.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Cadena anonima de solo lectura para las ocho rutas publicas del catalogo (R8, FR-021). */
@Configuration(proxyBeanMethods = false)
public class CatalogSecurityConfiguration {

	private static final String[] PUBLIC_CATALOG_PATHS = {
		"/api/v1/products", "/api/v1/products/*", "/api/v1/series", "/api/v1/series/*",
		"/api/v1/series/*/volumes", "/api/v1/genres", "/api/v1/authors", "/api/v1/publishers"
	};

	private static final HttpMethod[] PUBLIC_CATALOG_METHODS = {HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS};

	@Bean
	@Order(2)
	SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http) {
		List<RequestMatcher> matchers = new ArrayList<>();
		for (String path : PUBLIC_CATALOG_PATHS) {
			for (HttpMethod method : PUBLIC_CATALOG_METHODS) {
				matchers.add(PathPatternRequestMatcher.withDefaults().matcher(method, path));
			}
		}
		http.securityMatcher(new OrRequestMatcher(matchers))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors(Customizer.withDefaults());
		return http.build();
	}

}
