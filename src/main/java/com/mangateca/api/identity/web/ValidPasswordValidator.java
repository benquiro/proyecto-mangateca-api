package com.mangateca.api.identity.web;

import com.mangateca.api.identity.application.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/** Delega en {@link PasswordPolicy}. */
public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  @Override
  public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
    return PasswordPolicy.isValid(value);
  }
}
