package com.mangateca.api.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monto en pesos chilenos (CLP): positivo y sin decimales.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

	public static final String CURRENCY = "CLP";

	public Money {
		if (amount == null) {
			throw new IllegalArgumentException("El monto es obligatorio");
		}
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("El monto debe ser positivo");
		}
		try {
			amount = amount.setScale(0, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("El monto en CLP no admite decimales", ex);
		}
	}

	public static Money clp(long amount) {
		return new Money(BigDecimal.valueOf(amount));
	}

	@Override
	public int compareTo(Money other) {
		return amount.compareTo(other.amount);
	}

}
