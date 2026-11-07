package com.mangateca.api.catalog.domain;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Especificaciones de consulta del catálogo público. Sin {@code join} ni {@code distinct} en la consulta principal:
 * los filtros por colecciones usan subconsultas {@code EXISTS}.
 */
public final class ProductSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private static final Pattern ISBN_SHAPE = Pattern.compile("^([0-9]{9}[0-9X]|[0-9]{13})$");

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private ProductSpecifications() {
	}

	public static Specification<Product> published() {
		return (root, query, cb) -> cb.equal(root.get("status"), PublicationStatus.PUBLISHED);
	}

	public static Specification<Product> hasType(@Nullable ProductType type) {
		if (type == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> cb.equal(root.get("type"), type);
	}

	public static Specification<Product> hasPublisher(@Nullable UUID publisherId) {
		if (publisherId == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> cb.equal(root.get("publisher").get("id"), publisherId);
	}

	public static Specification<Product> hasSeries(@Nullable UUID seriesId) {
		if (seriesId == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> cb.equal(root.get("series").get("id"), seriesId);
	}

	public static Specification<Product> priceBetween(@Nullable Money min, @Nullable Money max) {
		return minPrice(min).and(maxPrice(max));
	}

	public static Specification<Product> hasAuthor(@Nullable UUID authorId) {
		if (authorId == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> {
			Subquery<Integer> sub = query.subquery(Integer.class);
			Root<Product> correlated = sub.correlate(root);
			var author = correlated.join("authors");
			sub.select(cb.literal(1)).where(cb.equal(author.get("id"), authorId));
			return cb.exists(sub);
		};
	}

	public static Specification<Product> hasGenre(@Nullable UUID genreId) {
		if (genreId == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> {
			Subquery<Integer> sub = query.subquery(Integer.class);
			Root<Product> correlated = sub.correlate(root);
			var genre = correlated.join("genres");
			sub.select(cb.literal(1)).where(cb.equal(genre.get("id"), genreId));
			return cb.exists(sub);
		};
	}

	/**
	 * Todas las palabras deben aparecer en el título o en el nombre de algún autor (sin tildes ni mayúsculas, parcial),
	 * o el texto completo debe ser exactamente el ISBN del producto.
	 */
	public static Specification<Product> matchesText(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return Specification.unrestricted();
		}
		String trimmed = text.strip();
		List<String> words = new ArrayList<>();
		for (String word : WHITESPACE.split(trimmed)) {
			if (!word.isEmpty()) {
				words.add(word);
			}
		}
		if (words.isEmpty()) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> {
			List<Predicate> perWord = new ArrayList<>();
			for (String word : words) {
				perWord.add(wordMatches(root, query, cb, word));
			}
			Predicate allWords = cb.and(perWord.toArray(new Predicate[0]));
			return isbnOf(trimmed).<Predicate>map(isbn -> cb.or(allWords, cb.equal(root.get("isbn"), isbn)))
				.orElse(allWords);
		};
	}

	public static Specification<Product> from(ProductSearchCriteria criteria) {
		return Specification.<Product>unrestricted()
			.and(published())
			.and(matchesText(criteria.text()))
			.and(hasType(criteria.type()))
			.and(hasGenre(criteria.genreId()))
			.and(hasAuthor(criteria.authorId()))
			.and(hasPublisher(criteria.publisherId()))
			.and(hasSeries(criteria.seriesId()))
			.and(priceBetween(criteria.minPrice(), criteria.maxPrice()));
	}

	private static Specification<Product> minPrice(@Nullable Money min) {
		if (min == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Money>get("price"), min);
	}

	private static Specification<Product> maxPrice(@Nullable Money max) {
		if (max == null) {
			return Specification.unrestricted();
		}
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.<Money>get("price"), max);
	}

	/**
	 * {@code id IN (títulos coincidentes UNION productos de autores coincidentes)}: cada rama usa su propio índice de
	 * trigramas; un {@code OR} con {@code EXISTS} correlacionado obligaría a recorrer toda la tabla.
	 */
	private static Predicate wordMatches(Root<Product> root, AbstractQuery<?> query, CriteriaBuilder cb, String word) {
		Expression<String> pattern = normalized(cb, cb.literal("%" + escapeLike(word) + "%"));

		Subquery<UUID> byTitle = query.subquery(UUID.class);
		Root<Product> titled = byTitle.from(Product.class);
		byTitle.select(titled.<UUID>get("id"))
			.where(cb.like(normalized(cb, titled.<String>get("title")), pattern, LIKE_ESCAPE));

		Subquery<UUID> byAuthor = query.subquery(UUID.class);
		Root<Product> authored = byAuthor.from(Product.class);
		var author = authored.join("authors");
		byAuthor.select(authored.<UUID>get("id"))
			.where(cb.like(normalized(cb, author.<String>get("name")), pattern, LIKE_ESCAPE));

		return root.<UUID>get("id").in(((HibernateCriteriaBuilder) cb).union(byTitle, byAuthor));
	}

	private static Expression<String> normalized(CriteriaBuilder cb, Expression<String> argument) {
		return cb.function("catalog_normalize", String.class, argument);
	}

	private static String escapeLike(String word) {
		return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * ISBN buscado si el texto, sin guiones ni espacios y en mayúsculas, tiene forma de ISBN. Un ISBN con dígito
	 * verificador inválido no puede existir en la base, así que simplemente no aporta coincidencia.
	 */
	private static Optional<Isbn> isbnOf(String text) {
		String normalized = Isbn.normalize(text);
		if (!ISBN_SHAPE.matcher(normalized).matches()) {
			return Optional.empty();
		}
		return Isbn.parse(normalized);
	}

}
