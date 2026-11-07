package com.mangateca.api.catalog.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rango de precios coherente: {@code minPrice} no puede superar a {@code maxPrice}. */
@Documented
@Constraint(validatedBy = ValidPriceRangeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPriceRange {

	String message() default "minPrice no puede ser mayor que maxPrice";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
