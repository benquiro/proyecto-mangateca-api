package com.mangateca.api.identity.config;

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/** Propiedades {@code mangateca.identity.*} (contracts/configuration.md). */
@ConfigurationProperties("mangateca.identity")
public record IdentityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue RefreshCookie refreshCookie,
        @DefaultValue BootstrapAdmin bootstrapAdmin) {

    public IdentityProperties {
        requirePositive(accessTokenTtl, "access-token-ttl");
        requirePositive(refreshTokenTtl, "refresh-token-ttl");
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("mangateca.identity." + name + " debe ser positivo");
        }
    }

    /** Emisor y ubicaciones PEM del par RSA. */
    public record Jwt(
            @Nullable String issuer,
            @Nullable Resource privateKeyLocation,
            @Nullable Resource publicKeyLocation) {

        public Jwt {
            issuer = issuer == null || issuer.isBlank() ? null : issuer;
        }
    }

    /** Atributo SameSite de la cookie de refresh: solo Strict, Lax o None. */
    public record RefreshCookie(@DefaultValue("Strict") String sameSite) {

        private static final Set<String> ALLOWED = Set.of("Strict", "Lax", "None");

        public RefreshCookie {
            if (!ALLOWED.contains(sameSite)) {
                throw new IllegalArgumentException(
                        "mangateca.identity.refresh-cookie.same-site debe ser Strict, Lax o None");
            }
        }
    }

    /** Administrador inicial opcional. */
    public record BootstrapAdmin(@Nullable String email, @Nullable String password) {

        @Override
        public String toString() {
            return "BootstrapAdmin[email=" + (email == null ? "null" : "****")
                    + ", password=" + (password == null ? "null" : "****") + "]";
        }
    }
}
