package com.mangateca.api.catalog.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.mangateca.api.catalog.domain.Money;
import com.mangateca.api.catalog.domain.ProductSearchCriteria;
import com.mangateca.api.catalog.domain.ProductType;

/**
 * Parámetros de búsqueda del listado de productos. El orden admite solo una lista blanca de claves.
 */
@ParameterObject
@ValidPriceRange
public record ProductSearchRequest(
		@Min(value = 0, message = "page debe ser mayor o igual que 0")
		@Max(value = PageParams.MAX_PAGE, message = "page debe ser menor o igual que 1000000") Integer page,
		@Min(value = 1, message = "size debe ser mayor o igual que 1")
		@Max(value = PageParams.MAX_SIZE, message = "size debe ser menor o igual que 100") Integer size,
		@Pattern(regexp = "^(title|price|publicationDate|createdAt)(,(asc|desc))?$",
				message = "sort debe ser title, price, publicationDate o createdAt, opcionalmente seguido de ,asc o ,desc") String sort,
		@Size(max = 100, message = "q debe tener como máximo 100 caracteres") String q, ProductType type,
		UUID genreId, UUID authorId, UUID publisherId, UUID seriesId,
		@Positive(message = "minPrice debe ser mayor que 0") Long minPrice,
		@Positive(message = "maxPrice debe ser mayor que 0") Long maxPrice) {

	private static final String DEFAULT_SORT = "createdAt,desc";

	private static final Map<String, String> SORT_PROPERTIES = Map.of("title", "title", "price", "price",
			"publicationDate", "publicationDate", "createdAt", "createdAt");

	public ProductSearchRequest(@Nullable Integer page, @Nullable Integer size, @Nullable String sort,
			@Nullable String q, @Nullable ProductType type, @Nullable UUID genreId, @Nullable UUID authorId,
			@Nullable UUID publisherId, @Nullable UUID seriesId, @Nullable Long minPrice, @Nullable Long maxPrice) {
		PageParams params = new PageParams(page, size);
		this.page = params.page();
		this.size = params.size();
		this.sort = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
		this.q = q;
		this.type = type;
		this.genreId = genreId;
		this.authorId = authorId;
		this.publisherId = publisherId;
		this.seriesId = seriesId;
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
	}

	public ProductSearchCriteria toCriteria() {
		String text = q == null ? null : q.strip();
		return new ProductSearchCriteria(text == null || text.isEmpty() ? null : text, type, genreId, authorId,
				publisherId, seriesId, minPrice == null ? null : Money.clp(minPrice),
				maxPrice == null ? null : Money.clp(maxPrice));
	}

	public Pageable toPageable() {
		String[] parts = sort.split(",");
		String property = SORT_PROPERTIES.get(parts[0]);
		if (property == null) {
			throw new IllegalStateException("Clave de orden no permitida");
		}
		Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
		return PageRequest.of(page, size, Sort.by(direction, property, "id"));
	}

}
