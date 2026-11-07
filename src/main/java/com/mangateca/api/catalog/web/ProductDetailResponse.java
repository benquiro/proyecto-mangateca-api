package com.mangateca.api.catalog.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mangateca.api.catalog.domain.ProductType;

/** Detalle completo de un producto publicado. */
public record ProductDetailResponse(UUID id, String title, ProductType type, String synopsis,
		List<NamedResponse> authors, NamedResponse publisher, List<NamedResponse> genres, @Nullable String isbn,
		String language, int pageCount, LocalDate publicationDate, long price, String coverUrl,
		@Nullable NamedResponse series, @Nullable Integer volumeNumber) {
}
