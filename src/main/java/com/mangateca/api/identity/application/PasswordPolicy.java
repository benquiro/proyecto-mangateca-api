package com.mangateca.api.identity.application;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Politica de contrasenas: 8 a 64 caracteres y como maximo 72 bytes UTF-8 (limite de bcrypt). */
public final class PasswordPolicy {

  private static final int MIN_CODE_POINTS = 8;
  private static final int MAX_CODE_POINTS = 64;
  private static final int MAX_BYTES = 72;

  private PasswordPolicy() {
  }

  public static boolean isValid(@Nullable String password) {
    if (password == null) {
      return false;
    }
    int codePoints = password.codePointCount(0, password.length());
    return codePoints >= MIN_CODE_POINTS
        && codePoints <= MAX_CODE_POINTS
        && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }
}
