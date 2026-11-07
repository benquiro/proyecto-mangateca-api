package com.mangateca.api.catalog.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.mangateca.api.catalog.domain.ProductType;
import com.mangateca.api.catalog.domain.PublicationStatus;

/**
 * Datos de prueba del catálogo insertados con JDBC (nunca en migraciones). Cada test que
 * lo use debe llamar a {@link #clear()} al empezar.
 */
public final class CatalogTestData {

	private final JdbcClient jdbc;

	public CatalogTestData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Borra todo el catálogo en orden de claves foráneas.
	 */
	public void clear() {
		for (String table : List.of("product_authors", "product_genres", "products", "series", "authors", "genres",
				"publishers")) {
			this.jdbc.sql("DELETE FROM " + table).update();
		}
	}

	public UUID publisher(String name) {
		return insertNamed("publishers", name);
	}

	public UUID genre(String name) {
		return insertNamed("genres", name);
	}

	public UUID author(String name) {
		return insertNamed("authors", name);
	}

	public UUID series(String name, String description) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("INSERT INTO series (id, name, description) VALUES (:id, :name, :description)")
			.param("id", id)
			.param("name", name)
			.param("description", description)
			.update();
		return id;
	}

	public ProductBuilder product(String title, UUID publisherId) {
		return new ProductBuilder(title, publisherId);
	}

	private UUID insertNamed(String table, String name) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("INSERT INTO " + table + " (id, name) VALUES (:id, :name)")
			.param("id", id)
			.param("name", name)
			.update();
		return id;
	}

	public final class ProductBuilder {

		private final String title;

		private final UUID publisherId;

		private ProductType type = ProductType.MANGA;

		private PublicationStatus status = PublicationStatus.PUBLISHED;

		private String synopsis = "Sinopsis de prueba";

		private long price = 9990;

		private String isbn;

		private String language = "es";

		private int pageCount = 192;

		private LocalDate publicationDate = LocalDate.of(2024, 1, 15);

		private String coverUrl = "https://covers.example/cover.jpg";

		private UUID seriesId;

		private Integer volumeNumber;

		private Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");

		private List<UUID> authorIds = List.of();

		private List<UUID> genreIds = List.of();

		private ProductBuilder(String title, UUID publisherId) {
			this.title = title;
			this.publisherId = publisherId;
		}

		public ProductBuilder type(ProductType type) {
			this.type = type;
			return this;
		}

		public ProductBuilder status(PublicationStatus status) {
			this.status = status;
			return this;
		}

		public ProductBuilder synopsis(String synopsis) {
			this.synopsis = synopsis;
			return this;
		}

		public ProductBuilder price(long price) {
			this.price = price;
			return this;
		}

		public ProductBuilder isbn(String isbn) {
			this.isbn = isbn;
			return this;
		}

		public ProductBuilder language(String language) {
			this.language = language;
			return this;
		}

		public ProductBuilder pageCount(int pageCount) {
			this.pageCount = pageCount;
			return this;
		}

		public ProductBuilder publicationDate(LocalDate publicationDate) {
			this.publicationDate = publicationDate;
			return this;
		}

		public ProductBuilder coverUrl(String coverUrl) {
			this.coverUrl = coverUrl;
			return this;
		}

		public ProductBuilder series(UUID seriesId, int volumeNumber) {
			this.seriesId = seriesId;
			this.volumeNumber = volumeNumber;
			return this;
		}

		public ProductBuilder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public ProductBuilder authors(UUID... authorIds) {
			this.authorIds = List.of(authorIds);
			return this;
		}

		public ProductBuilder genres(UUID... genreIds) {
			this.genreIds = List.of(genreIds);
			return this;
		}

		/**
		 * Inserta el producto con sus relaciones y devuelve su UUID.
		 */
		public UUID insert() {
			UUID id = UUID.randomUUID();
			CatalogTestData.this.jdbc.sql("""
					INSERT INTO products (id, type, status, title, synopsis, price, isbn, language, page_count,
					  publication_date, cover_url, publisher_id, series_id, volume_number, created_at)
					VALUES (:id, :type, :status, :title, :synopsis, :price, :isbn, :language, :pageCount,
					  :publicationDate, :coverUrl, :publisherId, :seriesId, :volumeNumber, :createdAt)
					""")
				.param("id", id)
				.param("type", this.type.name())
				.param("status", this.status.name())
				.param("title", this.title)
				.param("synopsis", this.synopsis)
				.param("price", BigDecimal.valueOf(this.price))
				.param("isbn", this.isbn, java.sql.Types.VARCHAR)
				.param("language", this.language)
				.param("pageCount", this.pageCount)
				.param("publicationDate", this.publicationDate)
				.param("coverUrl", this.coverUrl)
				.param("publisherId", this.publisherId)
				.param("seriesId", this.seriesId, java.sql.Types.OTHER)
				.param("volumeNumber", this.volumeNumber, java.sql.Types.INTEGER)
				.param("createdAt", Timestamp.from(this.createdAt))
				.update();
			for (UUID authorId : this.authorIds) {
				CatalogTestData.this.jdbc
					.sql("INSERT INTO product_authors (product_id, author_id) VALUES (:p, :a)")
					.param("p", id)
					.param("a", authorId)
					.update();
			}
			for (UUID genreId : this.genreIds) {
				CatalogTestData.this.jdbc.sql("INSERT INTO product_genres (product_id, genre_id) VALUES (:p, :g)")
					.param("p", id)
					.param("g", genreId)
					.update();
			}
			return id;
		}

	}

}
