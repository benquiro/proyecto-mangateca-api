package com.mangateca.api.catalog.domain;

import org.springframework.data.jpa.domain.Specification;

/**
 * Especificaciones de consulta del catálogo público.
 */
public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<Product> published() {
		return (root, query, cb) -> cb.equal(root.get("status"), PublicationStatus.PUBLISHED);
	}

	public static Specification<Product> from(ProductSearchCriteria criteria) {
		return Specification.<Product>unrestricted().and(published());
	}

}
