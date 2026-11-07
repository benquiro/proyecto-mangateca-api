package com.mangateca.api.catalog.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.mangateca.api.catalog.domain.Author;
import com.mangateca.api.catalog.domain.Genre;
import com.mangateca.api.catalog.domain.Isbn;
import com.mangateca.api.catalog.domain.Money;
import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.ProductType;
import com.mangateca.api.catalog.domain.PublicationStatus;
import com.mangateca.api.catalog.domain.Publisher;
import com.mangateca.api.catalog.domain.Series;

/**
 * Entidades del catálogo reales y en memoria para tests de capa web. Nunca se mockean.
 */
public final class CatalogFixtures {

	public static final String VALID_ISBN13 = "9784088820118";

	private CatalogFixtures() {
	}

	public static Publisher publisher(String name) {
		Publisher publisher = BeanUtils.instantiateClass(Publisher.class);
		ReflectionTestUtils.setField(publisher, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(publisher, "name", name);
		return publisher;
	}

	public static Genre genre(String name) {
		Genre genre = BeanUtils.instantiateClass(Genre.class);
		ReflectionTestUtils.setField(genre, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(genre, "name", name);
		return genre;
	}

	public static Author author(String name) {
		Author author = BeanUtils.instantiateClass(Author.class);
		ReflectionTestUtils.setField(author, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(author, "name", name);
		return author;
	}

	public static Series series(String name, String description) {
		Series series = BeanUtils.instantiateClass(Series.class);
		ReflectionTestUtils.setField(series, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(series, "name", name);
		ReflectionTestUtils.setField(series, "description", description);
		return series;
	}

	/**
	 * Producto publicado sin serie ni volumen, con ISBN-13 válido, un autor y un género.
	 */
	public static Product product(String title) {
		return product(title, new Isbn(VALID_ISBN13), null, null, List.of(author("Autor Uno")),
				List.of(genre("Shonen")));
	}

	/**
	 * Producto con ISBN nulo.
	 */
	public static Product productWithoutIsbn(String title) {
		return product(title, null, null, null, List.of(author("Autor Uno")), List.of(genre("Shonen")));
	}

	/**
	 * Producto que pertenece a una serie con su número de volumen.
	 */
	public static Product productInSeries(String title, Series series, int volumeNumber) {
		return product(title, null, series, volumeNumber, List.of(author("Autor Uno")), List.of(genre("Shonen")));
	}

	/**
	 * Producto con varios autores y géneros.
	 */
	public static Product productWithManyAuthorsAndGenres(String title) {
		return product(title, new Isbn(VALID_ISBN13), null, null,
				List.of(author("Autor Uno"), author("Autor Dos"), author("Autor Tres")),
				List.of(genre("Accion"), genre("Aventura")));
	}

	public static Product product(String title, Isbn isbn, Series series, Integer volumeNumber, List<Author> authors,
			List<Genre> genres) {
		Product product = BeanUtils.instantiateClass(Product.class);
		ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(product, "type", ProductType.MANGA);
		ReflectionTestUtils.setField(product, "status", PublicationStatus.PUBLISHED);
		ReflectionTestUtils.setField(product, "title", title);
		ReflectionTestUtils.setField(product, "synopsis", "Sinopsis de prueba");
		ReflectionTestUtils.setField(product, "price", Money.clp(9990));
		ReflectionTestUtils.setField(product, "isbn", isbn);
		ReflectionTestUtils.setField(product, "language", "es");
		ReflectionTestUtils.setField(product, "pageCount", 192);
		ReflectionTestUtils.setField(product, "publicationDate", LocalDate.of(2024, 1, 15));
		ReflectionTestUtils.setField(product, "coverUrl", "https://covers.example/cover.jpg");
		ReflectionTestUtils.setField(product, "volumeNumber", volumeNumber);
		ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2025-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(product, "publisher", publisher("Editorial Test"));
		ReflectionTestUtils.setField(product, "series", series);
		ReflectionTestUtils.setField(product, "authors", new LinkedHashSet<>(authors));
		ReflectionTestUtils.setField(product, "genres", (Set<Genre>) new LinkedHashSet<>(genres));
		return product;
	}

}
