package com.mangateca.api;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Seguridad de pruebas: no define JwtEncoder ni JwtDecoder (los aporta identity con un par RSA
 * efimero en el perfil test). Expone {@link TestTokens} para acunar tokens de prueba firmados
 * con el JwtEncoder del contexto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

	@Bean
	TestTokens testTokens(JwtEncoder jwtEncoder) {
		return new TestTokens(jwtEncoder);
	}

	/**
	 * Acuna tokens de prueba con {@code iss = mangateca-test} (el decoder valida el emisor).
	 */
	public static final class TestTokens {

		public static final String ISSUER = "mangateca-test";

		private final JwtEncoder encoder;

		TestTokens(JwtEncoder encoder) {
			this.encoder = encoder;
		}

		/** Token valido de 1 hora para el sujeto y roles dados. */
		public String token(String subject, List<String> roles) {
			Instant now = Instant.now();
			return token(subject, roles, now, now.plusSeconds(3600));
		}

		/** Token con {@code iat} y {@code exp} fijados explicitamente. */
		public String token(String subject, List<String> roles, Instant issuedAt, Instant expiresAt) {
			return encode(this.encoder, ISSUER, subject, roles, issuedAt, expiresAt);
		}

		/** Token con emisor arbitrario, firmado con el encoder del contexto. */
		public String tokenWithIssuer(String issuer, String subject, List<String> roles, Instant issuedAt,
				Instant expiresAt) {
			return encode(this.encoder, issuer, subject, roles, issuedAt, expiresAt);
		}

		/** Acuna con un encoder ajeno (p. ej. otra clave) usando el emisor de pruebas. */
		public static String encode(JwtEncoder encoder, String issuer, String subject, List<String> roles,
				Instant issuedAt, Instant expiresAt) {
			JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(subject)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("roles", roles)
				.build();
			JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
			return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		}

	}

}
