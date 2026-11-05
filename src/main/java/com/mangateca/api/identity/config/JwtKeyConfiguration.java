package com.mangateca.api.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Resuelve el par RSA y expone el JwtEncoder y el JwtDecoder (research R3/R4). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
public class JwtKeyConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JwtKeyConfiguration.class);
    private static final String PRIVATE_VAR = "MANGATECA_IDENTITY_JWT_PRIVATE_KEY_LOCATION";
    private static final String PUBLIC_VAR = "MANGATECA_IDENTITY_JWT_PUBLIC_KEY_LOCATION";
    private static final String ISSUER_VAR = "MANGATECA_IDENTITY_JWT_ISSUER";

    private final String issuer;
    private final KeyPair keyPair;

    public JwtKeyConfiguration(IdentityProperties properties, Environment environment) {
        String configuredIssuer = properties.jwt().issuer();
        if (configuredIssuer == null) {
            throw new IllegalStateException(
                    "Falta el emisor JWT: defina " + ISSUER_VAR + " (mangateca.identity.jwt.issuer).");
        }
        this.issuer = configuredIssuer;
        this.keyPair = resolveKeyPair(properties.jwt(), environment);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static KeyPair resolveKeyPair(IdentityProperties.Jwt jwt, Environment environment) {
        Resource privateLocation = jwt.privateKeyLocation();
        Resource publicLocation = jwt.publicKeyLocation();
        if (privateLocation != null && publicLocation != null) {
            return loadKeyPair(privateLocation, publicLocation);
        }
        if (privateLocation == null && publicLocation == null
                && environment.matchesProfiles("dev | test")) {
            LOG.info("Sin claves JWT configuradas: se genera un par RSA 2048 efimero en memoria; "
                    + "los tokens caducaran al reiniciar.");
            return generateEphemeral();
        }
        List<String> missing = new ArrayList<>();
        if (privateLocation == null) {
            missing.add(PRIVATE_VAR);
        }
        if (publicLocation == null) {
            missing.add(PUBLIC_VAR);
        }
        throw new IllegalStateException("Configuracion de claves JWT incompleta: faltan "
                + String.join(" y ", missing) + ".");
    }

    private static KeyPair loadKeyPair(Resource privateLocation, Resource publicLocation) {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream in = privateLocation.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "No se pudo leer la clave privada JWT (" + PRIVATE_VAR + ").", e);
        }
        try (InputStream in = publicLocation.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "No se pudo leer la clave publica JWT (" + PUBLIC_VAR + ").", e);
        }
        if (privateKey == null || publicKey == null
                || !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException(
                    "Las claves JWT privada y publica no forman un par RSA.");
        }
        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair generateEphemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA no disponible en la JVM.", e);
        }
    }
}
