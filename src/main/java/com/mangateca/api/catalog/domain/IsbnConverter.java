package com.mangateca.api.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

@Converter
public class IsbnConverter implements AttributeConverter<Isbn, String> {

	@Override
	public @Nullable String convertToDatabaseColumn(@Nullable Isbn attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public @Nullable Isbn convertToEntityAttribute(@Nullable String dbData) {
		return dbData == null ? null : new Isbn(dbData);
	}

}
