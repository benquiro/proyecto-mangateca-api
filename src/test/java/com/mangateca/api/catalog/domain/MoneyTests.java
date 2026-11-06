package com.mangateca.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTests {

	@Test
	@DisplayName("FR-016: la moneda es CLP")
	void shouldExposeClpCurrency() {
		assertThat(Money.CURRENCY).isEqualTo("CLP");
	}

	@Test
	@DisplayName("FR-016: monto cero rechazado")
	void shouldRejectZero() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Money(BigDecimal.ZERO));
	}

	@Test
	@DisplayName("FR-016: monto negativo rechazado")
	void shouldRejectNegative() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("-1")));
	}

	@Test
	@DisplayName("FR-016: monto nulo rechazado")
	void shouldRejectNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Money(null));
	}

	@Test
	@DisplayName("FR-016: 1500 y 1.5E3 quedan con escala 0 e iguales")
	void shouldNormalizeScaleToZero() {
		Money plain = new Money(new BigDecimal("1500"));
		Money scientific = new Money(new BigDecimal("1.5E3"));

		assertThat(plain.amount().scale()).isZero();
		assertThat(scientific.amount().scale()).isZero();
		assertThat(scientific).isEqualTo(plain);
	}

	@Test
	@DisplayName("FR-016: 1500.00 se normaliza a escala 0")
	void shouldAcceptTrailingZeroDecimals() {
		Money money = new Money(new BigDecimal("1500.00"));

		assertThat(money.amount().scale()).isZero();
		assertThat(money).isEqualTo(Money.clp(1500));
	}

	@Test
	@DisplayName("FR-016: decimales significativos (10.5) rechazados")
	void shouldRejectFractionalAmount() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("10.5")));
	}

	@Test
	@DisplayName("FR-016: fabrica clp(8990)")
	void shouldCreateFromLong() {
		assertThat(Money.clp(8990).amount()).isEqualByComparingTo("8990");
		assertThat(Money.clp(8990).amount().scale()).isZero();
	}

	@Test
	@DisplayName("FR-016: la fabrica clp rechaza cero y negativos")
	void shouldRejectNonPositiveFromFactory() {
		assertThatIllegalArgumentException().isThrownBy(() -> Money.clp(0));
		assertThatIllegalArgumentException().isThrownBy(() -> Money.clp(-5));
	}

	@Test
	@DisplayName("FR-016: compareTo ordena por monto")
	void shouldCompareByAmount() {
		assertThat(Money.clp(1000)).isLessThan(Money.clp(2000));
		assertThat(Money.clp(2000)).isGreaterThan(Money.clp(1000));
		assertThat(Money.clp(1000).compareTo(Money.clp(1000))).isZero();
	}

	@Test
	@DisplayName("FR-016: igualdad por valor")
	void shouldBeEqualByValue() {
		assertThat(Money.clp(8990)).isEqualTo(new Money(new BigDecimal("8990"))).hasSameHashCodeAs(Money.clp(8990));
		assertThat(Money.clp(8990)).isNotEqualTo(Money.clp(8991));
	}

}
