package com.mangateca.api.catalog.web;

import java.util.UUID;

/** Referencia con identificador y nombre. */
public record NamedResponse(UUID id, String name) {
}
