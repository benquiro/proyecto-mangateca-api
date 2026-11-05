package com.mangateca.api.platform.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JwtDecoder de desarrollo con un par RSA efimero en memoria. La feature no emite tokens.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevJwtDecoderConfiguration {

    @Bean
	JwtDecoder jwtDecoder() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair pair = generator.generateKeyPair();
		log.info("Perfil dev: se usa un JwtDecoder efimero con un par RSA generado en memoria; "
				+ "los tokens dejan de ser validos al reiniciar.");
		return NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
	}

}
