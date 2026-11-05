package com.mangateca.api.identity.domain;

import com.mangateca.api.identity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Cuenta de usuario. Las invariantes se validan en la fabrica {@link #register}. */
@Entity
@Table(name = "users")
public class User {

  private static final int MAX_DISPLAY_NAME = 100;

  @Id
  private UUID id;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private @Nullable Instant lockedUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {
    // requerido por JPA; los campos los asigna el proveedor
  }

  private User(
      UUID id, Email email, String displayName, String passwordHash, Role role, Instant now) {
    this.id = id;
    this.email = email.value();
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static User register(
      Email email, String displayName, String passwordHash, Role role, Clock clock) {
    if (email == null || passwordHash == null || passwordHash.isBlank()
        || role == null || clock == null || displayName == null) {
      throw new IllegalArgumentException("User data must not be null or blank");
    }
    String name = displayName.trim();
    if (name.isEmpty() || name.length() > MAX_DISPLAY_NAME) {
      throw new IllegalArgumentException("Display name must have 1 to 100 characters");
    }
    return new User(UUID.randomUUID(), email, name, passwordHash, role, clock.instant());
  }

  public void disable(Instant now) {
    this.enabled = false;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public @Nullable Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
