package com.mangateca.api.catalog.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Criterios de búsqueda y filtrado del catálogo. Todos son opcionales; {@link #NONE} no restringe nada.
 */
public record ProductSearchCriteria(@Nullable String text, @Nullable ProductType type, @Nullable UUID genreId,
		@Nullable UUID authorId, @Nullable UUID publisherId, @Nullable UUID seriesId, @Nullable Money minPrice,
		@Nullable Money maxPrice) {

	public static final ProductSearchCriteria NONE = new ProductSearchCriteria(null, null, null, null, null, null,
			null, null);

}
