package com.mangateca.api.identity.config;

import java.time.Clock;
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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Codificador de contrasenas, reloj y cadena con los cuatro endpoints anonimos de auth (R9/R16). */
@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

    private static final String[] PUBLIC_AUTH_PATHS = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Order(1)
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http) {
        List<RequestMatcher> matchers = new ArrayList<>();
        for (String path : PUBLIC_AUTH_PATHS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path));
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, path));
        }
        http.securityMatcher(new OrRequestMatcher(matchers))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults());
        return http.build();
    }

}
