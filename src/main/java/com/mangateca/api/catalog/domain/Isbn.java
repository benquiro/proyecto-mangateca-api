package com.mangateca.api.catalog.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * ISBN-10 o ISBN-13 normalizado (sin guiones ni espacios, en mayúsculas) con dígito verificador válido.
 */
public record Isbn(String value) {

	private static final int ISBN10_LENGTH = 10;

	private static final int ISBN13_LENGTH = 13;

	public Isbn {
		if (value == null) {
			throw new IllegalArgumentException("El ISBN es obligatorio");
		}
		value = normalize(value);
		boolean valid = switch (value.length()) {
			case ISBN10_LENGTH -> isValidIsbn10(value);
			case ISBN13_LENGTH -> isValidIsbn13(value);
			default -> false;
		};
		if (!valid) {
			throw new IllegalArgumentException("ISBN inválido");
		}
	}

	/**
	 * Consulta sin excepciones: normaliza y devuelve el ISBN solo si es válido.
	 */
	public static Optional<Isbn> parse(String raw) {
		String normalized = normalize(raw);
		return hasValidCheckDigit(normalized) ? Optional.of(new Isbn(normalized)) : Optional.empty();
	}

	private static boolean hasValidCheckDigit(String normalized) {
		return switch (normalized.length()) {
			case ISBN10_LENGTH -> isValidIsbn10(normalized);
			case ISBN13_LENGTH -> isValidIsbn13(normalized);
			default -> false;
		};
	}

	/**
	 * Solo limpia: quita guiones y espacios y pasa a mayúsculas. No valida.
	 */
	public static String normalize(String raw) {
		return raw.replaceAll("[-\\s]", "").toUpperCase(Locale.ROOT);
	}

	private static boolean isValidIsbn10(String isbn) {
		int sum = 0;
		for (int i = 0; i < ISBN10_LENGTH; i++) {
			char c = isbn.charAt(i);
			int digit;
			if (c >= '0' && c <= '9') {
				digit = c - '0';
			}
			else if (c == 'X' && i == ISBN10_LENGTH - 1) {
				digit = 10;
			}
			else {
				return false;
			}
			sum += digit * (ISBN10_LENGTH - i);
		}
		return sum % 11 == 0;
	}

	private static boolean isValidIsbn13(String isbn) {
		int sum = 0;
		for (int i = 0; i < ISBN13_LENGTH; i++) {
			char c = isbn.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
			sum += (c - '0') * (i % 2 == 0 ? 1 : 3);
		}
		return sum % 10 == 0;
	}

}
