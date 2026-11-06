package com.mangateca.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 10)
	private ProductType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 12)
	private PublicationStatus status;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "synopsis", nullable = false, columnDefinition = "text")
	private String synopsis;

	@Convert(converter = MoneyConverter.class)
	@Column(name = "price", nullable = false, precision = 12, scale = 0)
	private Money price;

	@Convert(converter = IsbnConverter.class)
	@Column(name = "isbn", length = 13)
	private @Nullable Isbn isbn;

	@Column(name = "language", nullable = false, length = 35)
	private String language;

	@Column(name = "page_count", nullable = false)
	private int pageCount;

	@Column(name = "publication_date", nullable = false)
	private LocalDate publicationDate;

	@Column(name = "cover_url", nullable = false, length = 2048)
	private String coverUrl;

	@Column(name = "volume_number")
	private @Nullable Integer volumeNumber;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "publisher_id", nullable = false)
	private Publisher publisher;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "series_id")
	private @Nullable Series series;

	@ManyToMany
	@JoinTable(name = "product_authors", joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "author_id"))
	@OrderBy("name")
	private Set<Author> authors = new LinkedHashSet<>();

	@ManyToMany
	@JoinTable(name = "product_genres", joinColumns = @JoinColumn(name = "product_id"),
			inverseJoinColumns = @JoinColumn(name = "genre_id"))
	@OrderBy("name")
	private Set<Genre> genres = new LinkedHashSet<>();

}
