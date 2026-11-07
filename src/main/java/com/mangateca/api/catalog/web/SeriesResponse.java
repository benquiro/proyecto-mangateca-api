package com.mangateca.api.catalog.web;

import java.util.UUID;

/** Detalle de una serie. */
public record SeriesResponse(UUID id, String name, String description) {
}
