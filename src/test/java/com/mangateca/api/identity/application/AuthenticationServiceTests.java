package com.mangateca.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangateca.api.identity.UserRegistered;
import com.mangateca.api.identity.domain.User;
import com.mangateca.api.identity.domain.UserRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.ErrorResponseException;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private static final String EMAIL = "carrera@example.com";

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AuthenticationService service;

  @BeforeEach
  void setUp() {
    service = new AuthenticationService(userRepository, passwordEncoder, eventPublisher, CLOCK);
  }

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: carrera por correo duplicado se traduce en 409 sin evento")
  void shouldThrowEmailAlreadyInUseWhenUniqueIndexRejectsConcurrentInsert() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode("S3cret-pass-123")).thenReturn("hash");
    when(userRepository.saveAndFlush(any(User.class)))
        .thenThrow(integrityViolation(AuthenticationService.EMAIL_UNIQUE_CONSTRAINT));

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isInstanceOf(EmailAlreadyInUseException.class)
        .extracting(e -> ((EmailAlreadyInUseException) e).getStatusCode().value())
        .isEqualTo(409);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName(
      "US1 Escenario 2 / FR-002: otra violacion de integridad no se confunde con correo duplicado")
  void shouldRethrowDataIntegrityViolationWhenConstraintIsNotTheEmailIndex() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode("S3cret-pass-123")).thenReturn("hash");
    DataIntegrityViolationException otherConstraint = integrityViolation("users_pkey");
    DataIntegrityViolationException noHibernateCause =
        new DataIntegrityViolationException("violacion generica");
    when(userRepository.saveAndFlush(any(User.class))).thenThrow(otherConstraint, noHibernateCause);

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isSameAs(otherConstraint);
    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isSameAs(noHibernateCause);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("US1 Escenario 2 / FR-002: correo ya registrado responde 409 sin intentar guardar")
  void shouldThrowEmailAlreadyInUseWithoutSavingWhenEmailExists() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(() -> service.register(EMAIL, "Ana", "S3cret-pass-123"))
        .isInstanceOf(EmailAlreadyInUseException.class);
    verify(userRepository, never()).saveAndFlush(any(User.class));
    verify(eventPublisher, never()).publishEvent(any(UserRegistered.class));
  }

  @ParameterizedTest
  @MethodSource("invalidPasswords")
  @DisplayName("US1 / FR-003: contrasena invalida responde 400 sin efectos laterales")
  void shouldThrow400WithoutSideEffectsWhenPasswordViolatesPolicy(String password) {
    assertThatThrownBy(() -> service.register(EMAIL, "Ana", password))
        .isInstanceOfSatisfying(
            ErrorResponseException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(400);
              assertThat(e.getMessage()).doesNotContain(EMAIL);
              assertThat(e.getBody().getDetail()).isNull();
              if (password != null) {
                assertThat(e.getMessage()).doesNotContain(password);
              }
            });
    verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
  }

  static Stream<String> invalidPasswords() {
    return Stream.of(
        null,
        "a".repeat(7),
        "a".repeat(65),
        "\u00f1".repeat(40)); // 40 caracteres pero 80 bytes UTF-8
  }

  @Test
  @DisplayName("US1 / FR-003: contrasena valida (64 caracteres) sigue registrando y publica evento")
  void shouldRegisterAndPublishEventWhenPasswordIsValid() {
    String password = "a".repeat(64);
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
    when(passwordEncoder.encode(password)).thenReturn("hash");
    when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

    User user = service.register(EMAIL, "Ana", password);

    assertThat(user.getEmail()).isEqualTo(EMAIL);
    verify(eventPublisher).publishEvent(any(UserRegistered.class));
  }

  private static DataIntegrityViolationException integrityViolation(String constraint) {
    ConstraintViolationException cause =
        new ConstraintViolationException(
            "violacion", new SQLException("sql"), "insert into users", constraint);
    assertThat(cause.getConstraintName()).isEqualTo(constraint);
    return new DataIntegrityViolationException("integridad", cause);
  }
}
