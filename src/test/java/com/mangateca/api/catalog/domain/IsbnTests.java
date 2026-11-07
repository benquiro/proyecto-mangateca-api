package com.mangateca.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsbnTests {

	@Test
	@DisplayName("FR-017: normaliza guiones y espacios")
	void shouldStripHyphensAndSpaces() {
		assertThat(new Isbn("978-4-08-882011-8").value()).isEqualTo("9784088820118");
		assertThat(new Isbn(" 978 4088820118 ").value()).isEqualTo("9784088820118");
	}

	@Test
	@DisplayName("FR-017: x final de ISBN-10 pasa a X")
	void shouldUppercaseTrailingX() {
		assertThat(new Isbn("0-8044-2957-x").value()).isEqualTo("080442957X");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0306406152", "080442957X", "9784088820118", "9780306406157" })
	@DisplayName("FR-017: ISBN-10 e ISBN-13 validos aceptados")
	void shouldAcceptValidIsbns(String raw) {
		assertThat(new Isbn(raw).value()).isEqualTo(raw);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0306406153", "080442957Y", "9784088820119", "9780306406158" })
	@DisplayName("FR-017: digito verificador incorrecto rechazado en ISBN-10 e ISBN-13")
	void shouldRejectWrongCheckDigit(String raw) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Isbn(raw));
	}

	@ParameterizedTest
	@ValueSource(strings = { "030640615", "03064061522", "978030640615", "97803064061571" })
	@DisplayName("FR-017: longitudes 9, 11, 12 y 14 rechazadas")
	void shouldRejectInvalidLengths(String raw) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Isbn(raw));
	}

	@ParameterizedTest
	@ValueSource(strings = { "03064A6152", "978030A406157", "08044295X7", "X306406152" })
	@DisplayName("FR-017: letras en medio y X fuera de la ultima posicion rechazadas")
	void shouldRejectLettersAndMisplacedX(String raw) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Isbn(raw));
	}

	@Test
	@DisplayName("FR-017: normalize solo limpia y no valida")
	void shouldOnlyCleanWhenNormalizing() {
		assertThat(Isbn.normalize("12-34 x")).isEqualTo("1234X");
		assertThat(Isbn.normalize("abc-def")).isEqualTo("ABCDEF");
	}

}
