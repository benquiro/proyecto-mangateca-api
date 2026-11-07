package com.mangateca.api.catalog.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;

/**
 * Parámetros de paginación. Un tamaño fuera de rango se rechaza con 400; nunca se recorta.
 */
@ParameterObject
public record PageParams(
		@Min(value = 0, message = "page debe ser mayor o igual que 0")
		@Max(value = MAX_PAGE, message = "page debe ser menor o igual que 1000000") Integer page,
		@Min(value = 1, message = "size debe ser mayor o igual que 1")
		@Max(value = MAX_SIZE, message = "size debe ser menor o igual que 100") Integer size) {

	public static final int MAX_SIZE = 100;

	public static final int MAX_PAGE = 1_000_000;

	private static final int DEFAULT_SIZE = 20;

	public PageParams(@Nullable Integer page, @Nullable Integer size) {
		this.page = page == null ? 0 : page;
		this.size = size == null ? DEFAULT_SIZE : size;
	}

	public PageRequest toPageRequest() {
		return PageRequest.of(page, size);
	}

}
