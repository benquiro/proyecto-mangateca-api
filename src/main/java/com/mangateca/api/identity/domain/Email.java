package com.mangateca.api.identity.domain;

import java.util.Locale;

/** Correo electronico normalizado (recortado y en minusculas) con validacion minima (research R11). */
public record Email(String value) {

  private static final int MAX_LENGTH = 254;

  public Email {
    if (value == null) {
      throw new IllegalArgumentException("Email must not be null");
    }
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Email has an invalid length");
    }
    int at = value.indexOf('@');
    if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
      throw new IllegalArgumentException("Email has an invalid format");
    }
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) {
        throw new IllegalArgumentException("Email has an invalid format");
      }
    }
  }

  @Override
  public String toString() {
    return "Email[***]";
  }
}
