package com.mangateca.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.mangateca.api.catalog.support.CatalogTestData;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ClassificationRepositoryIT {

	private static final Pageable FIRST_PAGE = PageRequest.of(0, 2, Sort.by("name", "id"));

	@Autowired
	private GenreRepository genres;

	@Autowired
	private AuthorRepository authors;

	@Autowired
	private PublisherRepository publishers;

	@Autowired
	private SeriesRepository series;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private DataSource dataSource;

	private CatalogTestData data;

	private UUID basePublisher;

	@BeforeEach
	void setUp() {
		this.data = new CatalogTestData(JdbcClient.create(this.dataSource));
		this.data.clear();
		this.basePublisher = this.data.publisher("Editorial Base");
	}

	@Test
	@DisplayName("US5 Escenario 1 / FR-014: un género con productos publicados aparece y otro sin productos no")
	void shouldListGenreWithPublishedProductsAndOmitGenreWithoutProducts() {
		UUID withProducts = this.data.genre("Shonen");
		this.data.genre("Sin productos");
		this.data.product("Naruto", this.basePublisher).genres(withProducts).insert();

		Page<Genre> page = this.genres.findVisible(FIRST_PAGE);

		assertThat(page.getContent()).extracting(Genre::getName).containsExactly("Shonen");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("US5 Escenario 3 / FR-014: un autor cuyos únicos productos están UNPUBLISHED no aparece")
	void shouldOmitAuthorWhoseOnlyProductsAreUnpublished() {
		UUID hidden = this.data.author("Autor Oculto");
		UUID visible = this.data.author("Autor Visible");
		this.data.product("Borrador", this.basePublisher)
			.status(PublicationStatus.UNPUBLISHED)
			.authors(hidden)
			.insert();
		this.data.product("Publicado", this.basePublisher).authors(visible).insert();

		Page<Author> page = this.authors.findVisible(FIRST_PAGE);

		assertThat(page.getContent()).extracting(Author::getName).containsExactly("Autor Visible");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "genres", "authors", "publishers", "series" })
	@DisplayName("US5 Escenario 2 / FR-013, FR-014: primera página alfabética, totalElements correcto y sin duplicados")
	void shouldReturnFirstAlphabeticalPageWithoutDuplicates(String kind) {
		// Charlie y Bravo tienen un producto publicado; Alpha tiene dos (riesgo de duplicado
		// por join); Delta solo tiene uno no publicado; Echo no tiene ninguno.
		UUID charlie = create(kind, "Charlie");
		UUID alpha = create(kind, "Alpha");
		UUID bravo = create(kind, "Bravo");
		UUID delta = create(kind, "Delta");
		create(kind, "Echo");
		insertProduct(kind, "P-Charlie", charlie, PublicationStatus.PUBLISHED, 1);
		insertProduct(kind, "P-Alpha-1", alpha, PublicationStatus.PUBLISHED, 1);
		insertProduct(kind, "P-Alpha-2", alpha, PublicationStatus.PUBLISHED, 2);
		insertProduct(kind, "P-Bravo", bravo, PublicationStatus.PUBLISHED, 1);
		insertProduct(kind, "P-Delta", delta, PublicationStatus.UNPUBLISHED, 1);
		this.entityManager.clear();

		Page<String> page = findVisibleNames(kind);

		assertThat(page.getContent()).containsExactly("Alpha", "Bravo");
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(2);
	}

	private Page<String> findVisibleNames(String kind) {
		return switch (kind) {
			case "genres" -> this.genres.findVisible(FIRST_PAGE).map(Genre::getName);
			case "authors" -> this.authors.findVisible(FIRST_PAGE).map(Author::getName);
			case "publishers" -> this.publishers.findVisible(FIRST_PAGE).map(Publisher::getName);
			case "series" -> this.series.findVisible(FIRST_PAGE).map(Series::getName);
			default -> throw new IllegalArgumentException(kind);
		};
	}

	private UUID create(String kind, String name) {
		return switch (kind) {
			case "genres" -> this.data.genre(name);
			case "authors" -> this.data.author(name);
			case "publishers" -> this.data.publisher(name);
			case "series" -> this.data.series(name, "Descripción de " + name);
			default -> throw new IllegalArgumentException(kind);
		};
	}

	private void insertProduct(String kind, String title, UUID classification, PublicationStatus status,
			int volume) {
		UUID publisher = "publishers".equals(kind) ? classification : this.basePublisher;
		CatalogTestData.ProductBuilder builder = this.data.product(title, publisher).status(status);
		switch (kind) {
			case "genres" -> builder.genres(classification);
			case "authors" -> builder.authors(classification);
			case "series" -> builder.series(classification, volume);
			default -> {
			}
		}
		builder.insert();
	}

}
