package com.mangateca.api.identity.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Contrasena conforme a {@code PasswordPolicy} (8-64 caracteres y max. 72 bytes UTF-8). */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  String message() default "debe tener entre 8 y 64 caracteres";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
