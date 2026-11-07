package com.mangateca.api.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

	@Override
	public BigDecimal convertToDatabaseColumn(Money attribute) {
		return attribute.amount();
	}

	@Override
	public Money convertToEntityAttribute(BigDecimal dbData) {
		return new Money(dbData);
	}

}
