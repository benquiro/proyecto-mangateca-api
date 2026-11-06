package com.mangateca.api.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Refresh token persistido solo como hash SHA-256; el valor en claro nunca se guarda. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  private static final int HASH_LENGTH = 32;

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false)
  private byte[] tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private @Nullable Instant revokedAt;

  @Column(name = "replaced_by")
  private @Nullable UUID replacedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected RefreshToken() {
    // requerido por JPA; los campos los asigna el proveedor
  }

  private RefreshToken(
      UUID userId, byte[] tokenHash, UUID familyId, Instant now, Duration ttl) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash.clone();
    this.familyId = familyId;
    this.createdAt = now;
    this.expiresAt = now.plus(ttl);
  }

  public static RefreshToken issue(
      UUID userId, byte[] tokenHash, UUID familyId, Instant now, Duration ttl) {
    if (userId == null || tokenHash == null || familyId == null || now == null || ttl == null) {
      throw new IllegalArgumentException("Refresh token data must not be null");
    }
    if (tokenHash.length != HASH_LENGTH) {
      throw new IllegalArgumentException("Token hash must have 32 bytes");
    }
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("TTL must be positive");
    }
    return new RefreshToken(userId, tokenHash, familyId, now, ttl);
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public void rotate(UUID newTokenId, Instant now) {
    this.revokedAt = now;
    this.replacedBy = newTokenId;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      this.revokedAt = now;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public byte[] getTokenHash() {
    return tokenHash.clone();
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public @Nullable Instant getRevokedAt() {
    return revokedAt;
  }

  public @Nullable UUID getReplacedBy() {
    return replacedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
