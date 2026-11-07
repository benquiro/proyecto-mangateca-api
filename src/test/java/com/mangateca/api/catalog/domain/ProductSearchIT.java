package com.mangateca.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
class ProductSearchIT {

	private static final String ISBN = "9784088820118";

	private static final String OTHER_ISBN = "9780306406157";

	@Autowired
	private ProductRepository repository;

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

	private Page<Product> search(ProductSearchCriteria criteria) {
		return search(criteria, PageRequest.of(0, 20, Sort.by("title")));
	}

	private Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
		this.entityManager.clear();
		return this.repository.findAll(ProductSpecifications.from(criteria), pageable);
	}

	private static ProductSearchCriteria text(String text) {
		return new ProductSearchCriteria(text, null, null, null, null, null, null, null);
	}

	@ParameterizedTest
	@ValueSource(strings = { "dragon", "DRAGON", "drag" })
	@DisplayName("US2 Escenario 1 / FR-005: «Dragón» se encuentra sin tildes, sin mayúsculas y por parcial")
	void shouldFindTitleIgnoringAccentsCaseAndPartialMatch(String term) {
		this.data.product("Dragón Ball", this.publisher).insert();
		this.data.product("Naruto", this.publisher).insert();
		this.data.product("Dragón oculto", this.publisher).status(PublicationStatus.UNPUBLISHED).insert();

		Page<Product> page = search(text(term));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dragón Ball");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("US2 Escenario 2 / FR-005: el autor «Akira Toriyama» se encuentra con «toriyama»")
	void shouldFindProductByAuthorName() {
		UUID toriyama = this.data.author("Akira Toriyama");
		this.data.product("Dr. Slump", this.publisher).authors(toriyama).insert();
		this.data.product("Naruto", this.publisher).authors(this.data.author("Masashi Kishimoto")).insert();
		this.data.product("Sand Land oculto", this.publisher)
			.authors(toriyama)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();

		Page<Product> page = search(text("toriyama"));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dr. Slump");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "dragon toriyama", "toriyama dragon" })
	@DisplayName("US2 Escenario 2 / FR-005: varias palabras exigen todas, en cualquier orden")
	void shouldRequireAllWordsInAnyOrder(String term) {
		UUID toriyama = this.data.author("Akira Toriyama");
		this.data.product("Dragón Ball", this.publisher).authors(toriyama).insert();
		this.data.product("Dragón Quest", this.publisher).authors(this.data.author("Yuji Horii")).insert();
		this.data.product("Dr. Slump", this.publisher).authors(toriyama).insert();
		this.data.product("Dragón Ball oculto", this.publisher)
			.authors(toriyama)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();

		Page<Product> page = search(text(term));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dragón Ball");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("FR-007: un producto con dos autores que coinciden aparece una sola vez y cuenta una vez")
	void shouldNotDuplicateProductWhenSeveralAuthorsMatch() {
		UUID first = this.data.author("Akira Toriyama");
		UUID second = this.data.author("Toriyama Junior");
		this.data.product("Dragón Ball", this.publisher).authors(first, second).insert();
		this.data.product("Naruto", this.publisher).insert();

		Page<Product> page = search(text("toriyama"));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dragón Ball");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "978-4-08-882011-8", "9784088820118" })
	@DisplayName("US2 Escenario 3 / FR-005: el ISBN con y sin guiones devuelve exactamente ese producto")
	void shouldFindExactlyProductByIsbnWithOrWithoutHyphens(String term) {
		this.data.product("Dragón Ball", this.publisher).isbn(ISBN).insert();
		this.data.product("Naruto", this.publisher).isbn(OTHER_ISBN).insert();
		this.data.product("Bleach", this.publisher).insert();
		this.data.product("Dragón Ball oculto", this.publisher)
			.isbn("9781234567897")
			.status(PublicationStatus.UNPUBLISHED)
			.insert();

		Page<Product> page = search(text(term));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dragón Ball");
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("US2 Escenario 3 / FR-020: un ISBN de un producto no publicado no lo hace visible")
	void shouldNotFindUnpublishedProductByIsbn() {
		this.data.product("Oculto", this.publisher).isbn(ISBN).status(PublicationStatus.UNPUBLISHED).insert();

		Page<Product> page = search(text(ISBN));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("US2 Escenario 4 / FR-006: tipo + género + precio inclusivo devuelve solo los que cumplen las tres")
	void shouldApplyTypeGenreAndInclusivePriceRange() {
		UUID action = this.data.genre("Acción");
		UUID drama = this.data.genre("Drama");
		this.data.product("En el mínimo", this.publisher).type(ProductType.MANGA).genres(action).price(5000).insert();
		this.data.product("En el máximo", this.publisher).type(ProductType.MANGA).genres(action).price(10000).insert();
		this.data.product("En medio", this.publisher)
			.type(ProductType.MANGA)
			.genres(action, drama)
			.price(7000)
			.insert();
		this.data.product("Bajo el mínimo", this.publisher).type(ProductType.MANGA).genres(action).price(4999).insert();
		this.data.product("Sobre el máximo", this.publisher)
			.type(ProductType.MANGA)
			.genres(action)
			.price(10001)
			.insert();
		this.data.product("Otro género", this.publisher).type(ProductType.MANGA).genres(drama).price(7000).insert();
		this.data.product("Otro tipo", this.publisher).type(ProductType.COMIC).genres(action).price(7000).insert();
		this.data.product("No publicado", this.publisher)
			.type(ProductType.MANGA)
			.genres(action)
			.price(7000)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();

		Page<Product> page = search(new ProductSearchCriteria(null, ProductType.MANGA, action, null, null, null,
				Money.clp(5000), Money.clp(10000)));

		assertThat(page.getContent()).extracting(Product::getTitle)
			.containsExactlyInAnyOrder("En el mínimo", "En el máximo", "En medio");
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	@DisplayName("US2 Escenario 5 / FR-007: búsqueda + filtros + price desc + página 1 es la segunda página correcta")
	void shouldReturnSecondPageOfFilteredSearchOrderedByPriceDesc() {
		UUID action = this.data.genre("Acción");
		UUID toriyama = this.data.author("Akira Toriyama");
		UUID other = this.data.author("Toriyama Junior");
		for (int i = 1; i <= 5; i++) {
			this.data.product("Dragón " + i, this.publisher)
				.authors(toriyama, other)
				.genres(action)
				.price(1000L * i)
				.insert();
		}
		this.data.product("Dragón sin género", this.publisher).authors(toriyama).price(9000).insert();
		this.data.product("Dragón oculto", this.publisher)
			.authors(toriyama)
			.genres(action)
			.price(8000)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();
		ProductSearchCriteria criteria = new ProductSearchCriteria("dragon toriyama", ProductType.MANGA, action, null,
				null, null, null, null);

		Page<Product> page = search(criteria, PageRequest.of(1, 2, Sort.by(Sort.Order.desc("price"))));

		assertThat(page.getContent()).extracting(Product::getTitle).containsExactly("Dragón 3", "Dragón 2");
		assertThat(page.getTotalElements()).isEqualTo(5);
		assertThat(page.getTotalPages()).isEqualTo(3);
	}

	@Test
	@DisplayName("US2 Escenario 7 / FR-005: texto sin coincidencias devuelve página vacía con totalElements 0")
	void shouldReturnEmptyPageWhenNothingMatches() {
		this.data.product("Dragón Ball", this.publisher).authors(this.data.author("Akira Toriyama")).insert();

		Page<Product> page = search(text("zzzxyz"));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
	}

}
