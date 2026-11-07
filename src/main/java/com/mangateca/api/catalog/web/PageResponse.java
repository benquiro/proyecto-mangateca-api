package com.mangateca.api.catalog.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Respuesta paginada común a todos los listados del catálogo.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

	public PageResponse {
		content = List.copyOf(content);
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), new PageMetadata(page.getSize(), page.getNumber(),
				page.getTotalElements(), page.getTotalPages()));
	}

	public record PageMetadata(int size, int number, long totalElements, int totalPages) {
	}

}
