package com.mangateca.api.catalog.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Parámetros de búsqueda del listado de productos. El orden admite solo una lista blanca de claves.
 */
@ParameterObject
public record ProductSearchRequest(
		@Min(value = 0, message = "page debe ser mayor o igual que 0")
		@Max(value = PageParams.MAX_PAGE, message = "page debe ser menor o igual que 1000000") Integer page,
		@Min(value = 1, message = "size debe ser mayor o igual que 1")
		@Max(value = PageParams.MAX_SIZE, message = "size debe ser menor o igual que 100") Integer size,
		@Pattern(regexp = "^(title|price|publicationDate|createdAt)(,(asc|desc))?$",
				message = "sort debe ser title, price, publicationDate o createdAt, opcionalmente seguido de ,asc o ,desc") String sort) {

	private static final String DEFAULT_SORT = "createdAt,desc";

	private static final Map<String, String> SORT_PROPERTIES = Map.of("title", "title", "price", "price",
			"publicationDate", "publicationDate", "createdAt", "createdAt");

	public ProductSearchRequest(@Nullable Integer page, @Nullable Integer size, @Nullable String sort) {
		PageParams params = new PageParams(page, size);
		this.page = params.page();
		this.size = params.size();
		this.sort = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
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
