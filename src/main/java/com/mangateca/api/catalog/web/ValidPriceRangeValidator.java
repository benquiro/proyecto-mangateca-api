package com.mangateca.api.catalog.web;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/** Valida el rango de precios y registra la violación en el nodo {@code minPrice}. */
public class ValidPriceRangeValidator implements ConstraintValidator<ValidPriceRange, ProductSearchRequest> {

	@Override
	public boolean isValid(@Nullable ProductSearchRequest value, ConstraintValidatorContext context) {
		if (value == null || value.minPrice() == null || value.maxPrice() == null
				|| value.minPrice() <= value.maxPrice()) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			.addPropertyNode("minPrice")
			.addConstraintViolation();
		return false;
	}

}
