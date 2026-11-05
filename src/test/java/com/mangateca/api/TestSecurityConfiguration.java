package com.mangateca.api;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Seguridad de pruebas: par RSA 2048 efimero generado en memoria al cargar la configuracion.
 * No se versiona ninguna clave. La feature no emite tokens en produccion.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

	private final RSAKey rsaKey = generateRsaKey();

	@Bean
	JwtDecoder jwtDecoder() {
		try {
			return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Clave publica RSA invalida", ex);
		}
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	private static RSAKey generateRsaKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair pair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
				.privateKey((RSAPrivateKey) pair.getPrivate())
				.keyID(UUID.randomUUID().toString())
				.build();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("RSA no disponible", ex);
		}
	}

}
