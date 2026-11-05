package com.mangateca.api.platform.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JwtDecoder de desarrollo con un par RSA efimero en memoria. La feature no emite tokens.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevJwtDecoderConfiguration {

	private static final Logger log = LoggerFactory.getLogger(DevJwtDecoderConfiguration.class);

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
