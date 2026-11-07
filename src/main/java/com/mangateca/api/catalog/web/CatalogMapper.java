package com.mangateca.api.catalog.web;

import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;

import com.mangateca.api.catalog.domain.Author;
import com.mangateca.api.catalog.domain.Genre;
import com.mangateca.api.catalog.domain.Isbn;
import com.mangateca.api.catalog.domain.Money;
import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.Publisher;
import com.mangateca.api.catalog.domain.Series;

/**
 * Mapeo de entidades del catálogo a DTOs de respuesta. Solo toca atributos cargados por el paso 2 del listado
 * (autores y serie) además de los campos simples.
 */
@Mapper(componentModel = "spring")
public interface CatalogMapper {

	ProductSummaryResponse toSummary(Product product);

	ProductDetailResponse toDetail(Product product);

	NamedResponse toNamed(Author author);

	NamedResponse toNamed(Publisher publisher);

	NamedResponse toNamed(Genre genre);

	@Nullable NamedResponse toNamed(@Nullable Series series);

	SeriesResponse toSeries(Series series);

	default @Nullable String toString(@Nullable Isbn isbn) {
		return isbn == null ? null : isbn.value();
	}

	default long toLong(Money money) {
		return money.amount().longValueExact();
	}

}
