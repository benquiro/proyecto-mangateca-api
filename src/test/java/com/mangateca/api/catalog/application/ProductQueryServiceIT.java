package com.mangateca.api.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.ProductSearchCriteria;
import com.mangateca.api.catalog.domain.PublicationStatus;
import com.mangateca.api.catalog.support.CatalogTestData;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, ProductQueryService.class })
class ProductQueryServiceIT {

	private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

	private static final long EXPECTED_STATEMENTS_PER_SEARCH = 3;

	private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	@Autowired
	private ProductQueryService service;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private DataSource dataSource;

	private CatalogTestData data;

	private UUID publisher;

	@BeforeEach
	void setUp() {
		this.data = new CatalogTestData(JdbcClient.create(this.dataSource));
		this.data.clear();
		this.publisher = this.data.publisher("Editorial Test");
	}

	@Test
	@DisplayName("US1 Escenario 1 / FR-001, FR-002: primera página con 20 publicados, más recientes primero")
	void shouldReturnFirstPageOfPublishedProductsNewestFirst() {
		for (int i = 0; i < 25; i++) {
			this.data.product("Publicado " + i, this.publisher).createdAt(BASE.plusSeconds(i)).insert();
		}
		for (int i = 0; i < 3; i++) {
			this.data.product("Borrador " + i, this.publisher)
				.status(PublicationStatus.UNPUBLISHED)
				.createdAt(BASE.plusSeconds(100 + i))
				.insert();
		}
		this.entityManager.clear();

		Page<Product> page = this.service.search(ProductSearchCriteria.NONE, PageRequest.of(0, 20, DEFAULT_SORT));

		assertThat(page.getContent()).hasSize(20);
		assertThat(page.getTotalElements()).isEqualTo(25);
		assertThat(page.getTotalPages()).isEqualTo(2);
		assertThat(page.getContent()).extracting(Product::getStatus).containsOnly(PublicationStatus.PUBLISHED);
		assertThat(page.getContent()).extracting(Product::getTitle)
			.containsExactlyElementsOf(IntStream.rangeClosed(5, 24)
				.boxed()
				.sorted(Comparator.reverseOrder())
				.map(i -> "Publicado " + i)
				.toList());
	}

	@Test
	@DisplayName("US1 Escenario 1 / FR-004: el número de sentencias SQL no crece con el tamaño de página (sin N+1)")
	void shouldIssueSameNumberOfStatementsRegardlessOfPageSize() {
		UUID series = this.data.series("Serie Test", "Descripción");
		UUID authorA = this.data.author("Autor A");
		UUID authorB = this.data.author("Autor B");
		for (int i = 0; i < 25; i++) {
			this.data.product("Producto " + i, this.publisher)
				.createdAt(BASE.plusSeconds(i))
				.series(series, i + 1)
				.authors(authorA, authorB)
				.insert();
		}
		Statistics statistics = this.entityManager.unwrap(Session.class).getSessionFactory().getStatistics();

		long singleItemStatements = statementsForSearch(statistics, PageRequest.of(0, 1, DEFAULT_SORT), 1);
		long fullPageStatements = statementsForSearch(statistics, PageRequest.of(0, 20, DEFAULT_SORT), 20);

		assertThat(fullPageStatements).isEqualTo(singleItemStatements);
		// Exactamente ids de la página + count + carga con fetch de relaciones. Una paginación en memoria
		// (fetch join + límite sobre colección) emitiría menos sentencias y no respetaría el límite en BD.
		assertThat(fullPageStatements).isEqualTo(EXPECTED_STATEMENTS_PER_SEARCH);
	}

	private long statementsForSearch(Statistics statistics, Pageable pageable, int expectedSize) {
		this.entityManager.flush();
		this.entityManager.clear();
		statistics.clear();

		Page<Product> page = this.service.search(ProductSearchCriteria.NONE, pageable);

		assertThat(page.getContent()).hasSize(expectedSize);
		for (Product product : page.getContent()) {
			assertThat(Hibernate.isInitialized(product.getAuthors())).isTrue();
			assertThat(product.getAuthors()).hasSize(2);
			assertThat(Hibernate.isInitialized(product.getSeries())).isTrue();
			assertThat(product.getSeries()).isNotNull();
		}
		return statistics.getPrepareStatementCount();
	}

	@Test
	@DisplayName("US1 Escenario 3 / FR-002: una página fuera de rango devuelve contenido vacío y el total real")
	void shouldReturnEmptyContentWhenPageIsBeyondLastPage() {
		for (int i = 0; i < 45; i++) {
			this.data.product("Producto " + i, this.publisher).createdAt(BASE.plusSeconds(i)).insert();
		}
		this.entityManager.clear();

		Page<Product> page = this.service.search(ProductSearchCriteria.NONE, PageRequest.of(50, 20, DEFAULT_SORT));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(45);
	}

	static Stream<Arguments> sortCases() {
		return Stream.of(Arguments.of("title", Sort.Direction.ASC), Arguments.of("price", Sort.Direction.DESC),
				Arguments.of("publicationDate", Sort.Direction.DESC), Arguments.of("createdAt", Sort.Direction.ASC));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("sortCases")
	@DisplayName("US1 Escenario 4 / FR-004: el orden pedido se respeta y los empates se resuelven por id")
	void shouldSortByRequestedPropertyWithIdAsTieBreaker(String property, Sort.Direction direction) {
		// Seis productos en tres grupos de dos con el mismo valor en cualquier propiedad ordenable.
		List<UUID> ids = new ArrayList<>();
		List<String> titles = List.of("Alfa", "Alfa", "Beta", "Beta", "Gamma", "Gamma");
		for (int i = 0; i < titles.size(); i++) {
			int group = i / 2;
			ids.add(this.data.product(titles.get(i), this.publisher)
				.price(1000L * (group + 1))
				.publicationDate(LocalDate.of(2020 + group, 1, 1))
				.createdAt(BASE.plusSeconds(group))
				.insert());
		}
		this.entityManager.clear();
		Sort sort = Sort.by(new Sort.Order(direction, property), new Sort.Order(direction, "id"));

		Page<Product> page = this.service.search(ProductSearchCriteria.NONE, PageRequest.of(0, 20, sort));

		Comparator<Product> byProperty = propertyComparator(property);
		// Los UUID se ordenan en PostgreSQL byte a byte, equivalente al orden de su texto hexadecimal.
		Comparator<Product> byId = Comparator.comparing(p -> p.getId().toString());
		Comparator<Product> expected = direction.isAscending() ? byProperty.thenComparing(byId)
				: byProperty.reversed().thenComparing(byId.reversed());
		List<Product> actual = page.getContent();
		assertThat(actual).hasSize(ids.size());
		assertThat(actual).extracting(Product::getId).containsExactlyElementsOf(actual.stream().sorted(expected).map(Product::getId).toList());
		// Repetible: una segunda ejecución devuelve exactamente la misma secuencia.
		this.entityManager.clear();
		assertThat(this.service.search(ProductSearchCriteria.NONE, PageRequest.of(0, 20, sort)).getContent())
			.extracting(Product::getId)
			.containsExactlyElementsOf(actual.stream().map(Product::getId).toList());
	}

	private static Comparator<Product> propertyComparator(String property) {
		return switch (property) {
			case "title" -> Comparator.comparing(Product::getTitle);
			case "price" -> Comparator.comparing(Product::getPrice);
			case "publicationDate" -> Comparator.comparing(Product::getPublicationDate);
			case "createdAt" -> Comparator.comparing(Product::getCreatedAt);
			default -> throw new IllegalArgumentException(property);
		};
	}

	@Test
	@DisplayName("US3 Escenario 1 / FR-009, FR-004: detalle completo en una sola sentencia SQL y sin cargas perezosas")
	void shouldReturnFullDetailWithSingleStatement() {
		UUID series = this.data.series("Serie Test", "Descripción");
		UUID authorA = this.data.author("Autor A");
		UUID authorB = this.data.author("Autor B");
		UUID genreA = this.data.genre("Accion");
		UUID genreB = this.data.genre("Aventura");
		UUID id = this.data.product("Detalle", this.publisher)
			.series(series, 4)
			.authors(authorA, authorB)
			.genres(genreA, genreB)
			.insert();
		this.entityManager.flush();
		this.entityManager.clear();
		Statistics statistics = this.entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
		statistics.clear();

		Product product = this.service.detail(id);
		long statements = statistics.getPrepareStatementCount();
		this.entityManager.clear();

		assertThat(statements).isEqualTo(1);
		assertThat(product.getId()).isEqualTo(id);
		assertThat(product.getTitle()).isEqualTo("Detalle");
		assertThat(product.getAuthors()).extracting(a -> a.getName()).containsExactlyInAnyOrder("Autor A", "Autor B");
		assertThat(product.getGenres()).extracting(g -> g.getName()).containsExactlyInAnyOrder("Accion", "Aventura");
		assertThat(product.getPublisher().getName()).isEqualTo("Editorial Test");
		assertThat(product.getSeries().getName()).isEqualTo("Serie Test");
		assertThat(product.getVolumeNumber()).isEqualTo(4);
	}

	@Test
	@DisplayName("US3 Escenario 2 / FR-010: no publicado e inexistente lanzan la misma CatalogNotFoundException")
	void shouldThrowIndistinguishableNotFoundForUnpublishedAndMissingProduct() {
		UUID unpublished = this.data.product("Borrador", this.publisher).status(PublicationStatus.UNPUBLISHED).insert();
		this.entityManager.clear();

		Throwable forUnpublished = catchThrowable(() -> this.service.detail(unpublished));
		Throwable forMissing = catchThrowable(() -> this.service.detail(UUID.randomUUID()));

		assertThat(forUnpublished).isInstanceOf(CatalogNotFoundException.class);
		assertThat(forMissing).isInstanceOf(CatalogNotFoundException.class);
		CatalogNotFoundException a = (CatalogNotFoundException) forUnpublished;
		CatalogNotFoundException b = (CatalogNotFoundException) forMissing;
		assertThat(a.getStatusCode()).isEqualTo(b.getStatusCode());
		assertThat(a.getStatusCode().value()).isEqualTo(404);
		assertThat(a.getMessage()).isEqualTo(b.getMessage());
		assertThat(a.getBody().getType()).isEqualTo(b.getBody().getType());
		assertThat(a.getBody().getTitle()).isEqualTo(b.getBody().getTitle());
		assertThat(a.getBody().getDetail()).isEqualTo(b.getBody().getDetail());
		assertThat(a.getBody().getDetail()).doesNotContain(unpublished.toString());
	}

	static Stream<Sort> volumesPageableSorts() {
		return Stream.of(Sort.unsorted(), Sort.by(Sort.Direction.DESC, "volumeNumber"),
				Sort.by(Sort.Direction.DESC, "title"), Sort.by("volumeNumber"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("volumesPageableSorts")
	@DisplayName("US4 Escenario 1 / FR-011, FR-020 (T045): solo publicados [1, 3], por número ascendente, ignora el Sort del Pageable")
	void shouldReturnOnlyPublishedVolumesOrderedByNumberRegardlessOfPageableSort(Sort sort) {
		UUID series = this.data.series("Serie Test", "Descripción");
		this.data.product("Volumen 3", this.publisher).series(series, 3).insert();
		this.data.product("Volumen 2", this.publisher)
			.series(series, 2)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();
		this.data.product("Volumen 1", this.publisher).series(series, 1).insert();
		this.entityManager.clear();

		Page<Product> page = this.service.volumes(series, PageRequest.of(0, 20, sort));

		assertThat(page.getContent()).extracting(Product::getVolumeNumber).containsExactly(1, 3);
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-011: serie inexistente lanza CatalogNotFoundException")
	void shouldThrowNotFoundWhenSeriesDoesNotExist() {
		Throwable thrown = catchThrowable(
				() -> this.service.volumes(UUID.randomUUID(), PageRequest.of(0, 20, Sort.by("volumeNumber"))));

		assertThat(thrown).isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-011, FR-020: serie sin volúmenes publicados lanza CatalogNotFoundException")
	void shouldThrowNotFoundWhenSeriesHasNoPublishedVolumes() {
		UUID series = this.data.series("Serie Vacía", "Descripción");
		this.data.product("Borrador", this.publisher).series(series, 1).status(PublicationStatus.UNPUBLISHED).insert();
		this.entityManager.clear();

		Throwable thrown = catchThrowable(() -> this.service.volumes(series, PageRequest.of(0, 20, Sort.by("volumeNumber"))));

		assertThat(thrown).isInstanceOf(CatalogNotFoundException.class);
	}

}
