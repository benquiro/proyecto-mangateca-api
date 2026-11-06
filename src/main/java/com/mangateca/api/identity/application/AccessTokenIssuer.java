package com.mangateca.api.identity.application;

import com.mangateca.api.identity.Role;
import com.mangateca.api.identity.config.IdentityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Emite tokens de acceso JWT firmados con RS256 (T038). */
@Component
public class AccessTokenIssuer {

  /** Token de acceso firmado y su vigencia en segundos. */
  public record IssuedAccessToken(String value, long expiresIn) {}

  private final JwtEncoder jwtEncoder;
  private final IdentityProperties properties;
  private final Clock clock;

  public AccessTokenIssuer(JwtEncoder jwtEncoder, IdentityProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  public IssuedAccessToken issue(UUID userId, Role role) {
    Instant issuedAt = clock.instant();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("roles", List.of(role.name()))
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.accessTokenTtl()));
    String issuer = properties.jwt().issuer();
    if (issuer != null) {
      claims.issuer(issuer);
    }
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    String token =
        jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
  }
}
