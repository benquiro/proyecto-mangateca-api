package com.mangateca.api.catalog.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mangateca.api.catalog.domain.ProductType;

/** Tarjeta de producto del listado. */
public record ProductSummaryResponse(UUID id, String title, ProductType type, List<NamedResponse> authors, long price,
		String coverUrl, @Nullable NamedResponse series, @Nullable Integer volumeNumber) {
}
