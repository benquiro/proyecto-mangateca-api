package com.mangateca.api.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.mangateca.api.catalog.application.CatalogNotFoundException;
import com.mangateca.api.catalog.application.ProductQueryService;
import com.mangateca.api.catalog.config.CatalogSecurityConfiguration;
import com.mangateca.api.catalog.domain.Author;
import com.mangateca.api.catalog.domain.Genre;
import com.mangateca.api.catalog.domain.Isbn;
import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.ProductSearchCriteria;
import com.mangateca.api.catalog.support.CatalogFixtures;
import com.mangateca.api.identity.config.IdentitySecurityConfiguration;
import com.mangateca.api.identity.config.JwtKeyConfiguration;
import com.mangateca.api.platform.security.ProblemDetailsAccessDeniedHandler;
import com.mangateca.api.platform.security.ProblemDetailsAuthenticationEntryPoint;
import com.mangateca.api.platform.security.ProblemDetailsResponseWriter;
import com.mangateca.api.platform.security.SecurityConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@WebMvcTest(ProductController.class)
@ActiveProfiles("test")
@Import({ SecurityConfiguration.class, IdentitySecurityConfiguration.class, JwtKeyConfiguration.class,
		ProblemDetailsResponseWriter.class, ProblemDetailsAuthenticationEntryPoint.class,
		ProblemDetailsAccessDeniedHandler.class, CatalogSecurityConfiguration.class, CatalogMapperImpl.class })
class ProductControllerTests {

	private static final String PRODUCTS = "/api/v1/products";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ProductQueryService service;

	@Test
	@DisplayName("US1 Escenario 1 / FR-001: GET anónimo responde 200 con forma PageResponse")
	void shouldReturn200WithPageResponseShapeWhenAnonymous() {
		stub(List.of(CatalogFixtures.product("Uno")), 1);

		MvcTestResult result = this.mvc.get().uri(PRODUCTS).exchange();

		assertThat(result).hasStatusOk();
		JsonNode body = json(result);
		assertThat(body.path("content").isArray()).isTrue();
		assertThat(body.path("content")).hasSize(1);
		assertThat(body.path("page").path("size").asInt()).isEqualTo(20);
		assertThat(body.path("page").path("number").asInt()).isZero();
		assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(1);
		assertThat(body.path("page").path("totalPages").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("US1 Escenario 2 / FR-001 / FR-008: size=101 responde 400 problem+json con errors[field=size]")
	void shouldReturn400ProblemJsonWhenSizeExceedsMaximum() {
		MvcTestResult result = this.mvc.get().uri(PRODUCTS).param("size", "101").exchange();

		assertThat(result).hasStatus(400);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		JsonNode errors = json(result).path("errors");
		boolean hasSize = false;
		for (JsonNode error : errors) {
			hasSize |= "size".equals(error.path("field").asString());
		}
		assertThat(hasSize).as("errors contiene field=size").isTrue();
	}

	@ParameterizedTest(name = "{0}={1} -> 400 con errors[field={0}]")
	@CsvSource({ "type,XYZ,true", "genreId,abc,true", "sort,synopsis,false", "size,0,false", "page,-1,false" })
	@DisplayName("US2 Escenario 6 / FR-008 / FR-022: parametro invalido responde 400 problem+json con errors[field]")
	void shouldReturn400ProblemJsonWhenParameterIsInvalid(String param, String value, boolean mustNotEchoValue) {
		MvcTestResult result = this.mvc.get().uri(PRODUCTS).param(param, value).exchange();

		assertInvalidParameter(result, param, mustNotEchoValue ? value : null);
	}

	@Test
	@DisplayName("US2 Escenario 6 / FR-008: minPrice=9000 y maxPrice=100 responde 400 con errors[field=minPrice]")
	void shouldReturn400WhenMinPriceIsGreaterThanMaxPrice() {
		MvcTestResult result = this.mvc.get()
			.uri(PRODUCTS)
			.param("minPrice", "9000")
			.param("maxPrice", "100")
			.exchange();

		assertInvalidParameter(result, "minPrice", null);
	}

	private void assertInvalidParameter(MvcTestResult result, String field, String forbiddenInBody) {
		assertThat(result).hasStatus(400);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		boolean hasField = false;
		for (JsonNode error : json(result).path("errors")) {
			hasField |= field.equals(error.path("field").asString());
		}
		assertThat(hasField).as("errors contiene field=" + field).isTrue();
		if (forbiddenInBody != null) {
			// `instance` es la URI de la petición (presente en todo ProblemDetail); el valor no debe aparecer en el resto.
			ObjectNode body = (ObjectNode) json(result);
			body.remove("instance");
			assertThat(body.toString()).doesNotContain(forbiddenInBody);
		}
		verifyNoInteractions(this.service);
	}

	@Test
	@DisplayName("US1 Escenario 4 / FR-002: sin sort el Pageable es createdAt DESC, id DESC, page 0 y size 20")
	void shouldUseDefaultSortAndPagingWhenNoParams() {
		stub(List.of(), 0);

		this.mvc.get().uri(PRODUCTS).exchange();

		Pageable pageable = capturePageable();
		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
	}

	@Test
	@DisplayName("US1 Escenario 4 / FR-002: sort=title produce title ASC, id ASC")
	void shouldSortByTitleAscendingWhenSortIsTitle() {
		stub(List.of(), 0);

		this.mvc.get().uri(PRODUCTS).param("sort", "title").exchange();

		assertThat(capturePageable().getSort()).isEqualTo(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));
	}

	@Test
	@DisplayName("US1 Escenario 4 / FR-002: sort=price,desc produce price DESC, id DESC")
	void shouldSortByPriceDescendingWhenSortIsPriceDesc() {
		stub(List.of(), 0);

		this.mvc.get().uri(PRODUCTS).param("sort", "price,desc").exchange();

		assertThat(capturePageable().getSort()).isEqualTo(Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id")));
	}

	@Test
	@DisplayName("US1 Escenario 5 / FR-003: la tarjeta de un volumen de Dragon Ball trae serie, volumen y precio entero")
	void shouldReturnSummaryCardWithSeriesAndVolume() {
		Product product = CatalogFixtures.productInSeries("Dragon Ball 3",
				CatalogFixtures.series("Dragon Ball", "Descripcion"), 3);
		stub(List.of(product), 1);

		MvcTestResult result = this.mvc.get().uri(PRODUCTS).exchange();

		assertThat(result).hasStatusOk();
		JsonNode card = json(result).path("content").path(0);
		assertThat(card.path("id").asString()).isEqualTo(product.getId().toString());
		assertThat(card.path("title").asString()).isEqualTo("Dragon Ball 3");
		assertThat(card.path("type").asString()).isEqualTo("MANGA");
		assertThat(card.path("authors")).hasSize(1);
		assertThat(card.path("authors").path(0).path("id").asString()).isNotBlank();
		assertThat(card.path("authors").path(0).path("name").asString()).isEqualTo("Autor Uno");
		assertThat(card.path("price").isIntegralNumber()).isTrue();
		assertThat(card.path("price").asLong()).isEqualTo(9990);
		assertThat(card.path("coverUrl").asString()).isEqualTo("https://covers.example/cover.jpg");
		assertThat(card.path("series").path("id").asString()).isEqualTo(product.getSeries().getId().toString());
		assertThat(card.path("series").path("name").asString()).isEqualTo("Dragon Ball");
		assertThat(card.path("volumeNumber").asInt()).isEqualTo(3);
		for (String detailField : List.of("synopsis", "publisher", "genres", "isbn")) {
			assertThat(card.has(detailField)).as("no incluye " + detailField).isFalse();
		}
	}

	@Test
	@DisplayName("US1 Escenario 6 / FR-003: un producto sin serie tiene series y volumeNumber nulos")
	void shouldReturnNullSeriesAndVolumeWhenProductHasNoSeries() {
		stub(List.of(CatalogFixtures.product("Suelto")), 1);

		MvcTestResult result = this.mvc.get().uri(PRODUCTS).exchange();

		assertThat(result).hasStatusOk();
		JsonNode card = json(result).path("content").path(0);
		assertThat(card.path("title").asString()).isEqualTo("Suelto");
		assertThat(card.path("series").isNull() || card.path("series").isMissingNode()).isTrue();
		assertThat(card.path("volumeNumber").isNull() || card.path("volumeNumber").isMissingNode()).isTrue();
	}

	@Test
	@DisplayName("US3 Escenario 1 / FR-009, FR-012: el detalle de un volumen trae todos los campos y ninguno interno")
	void shouldReturnProductDetailWithoutInternalFields() {
		Product product = CatalogFixtures.product("Dragon Ball 3", new Isbn(CatalogFixtures.VALID_ISBN13),
				CatalogFixtures.series("Dragon Ball", "Descripcion"), 3,
				List.<Author>of(CatalogFixtures.author("Akira Toriyama"), CatalogFixtures.author("Otro")),
				List.<Genre>of(CatalogFixtures.genre("Accion"), CatalogFixtures.genre("Aventura")));
		when(this.service.detail(product.getId())).thenReturn(product);

		MvcTestResult result = this.mvc.get().uri(PRODUCTS + "/" + product.getId()).exchange();

		assertThat(result).hasStatusOk();
		JsonNode body = json(result);
		assertThat(body.path("id").asString()).isEqualTo(product.getId().toString());
		assertThat(body.path("title").asString()).isEqualTo("Dragon Ball 3");
		assertThat(body.path("type").asString()).isEqualTo("MANGA");
		assertThat(body.path("synopsis").asString()).isEqualTo("Sinopsis de prueba");
		assertThat(body.path("authors")).hasSize(2);
		assertThat(body.path("authors").path(0).path("name").asString()).isNotBlank();
		assertThat(body.path("publisher").path("name").asString()).isEqualTo("Editorial Test");
		assertThat(body.path("publisher").path("id").asString()).isNotBlank();
		assertThat(body.path("genres")).hasSize(2);
		assertThat(body.path("isbn").asString()).isEqualTo(CatalogFixtures.VALID_ISBN13);
		assertThat(body.path("language").asString()).isEqualTo("es");
		assertThat(body.path("pageCount").asInt()).isEqualTo(192);
		assertThat(body.path("publicationDate").asString()).isEqualTo("2024-01-15");
		assertThat(body.path("price").isIntegralNumber()).isTrue();
		assertThat(body.path("price").asLong()).isEqualTo(9990);
		assertThat(body.path("coverUrl").asString()).isEqualTo("https://covers.example/cover.jpg");
		assertThat(body.path("series").path("id").asString()).isEqualTo(product.getSeries().getId().toString());
		assertThat(body.path("series").path("name").asString()).isEqualTo("Dragon Ball");
		assertThat(body.path("volumeNumber").asInt()).isEqualTo(3);
		Set<String> fields = new HashSet<>();
		body.propertyNames().forEach(fields::add);
		assertThat(fields).containsExactlyInAnyOrder("id", "title", "type", "synopsis", "authors", "publisher",
				"genres", "isbn", "language", "pageCount", "publicationDate", "price", "coverUrl", "series",
				"volumeNumber");
		assertThat(fields).doesNotContain("status", "createdAt", "updatedAt");
	}

	@Test
	@DisplayName("US3 Escenario 2 / FR-010, FR-022: dos UUID inexistentes dan 404 problem+json idéntico salvo instance")
	void shouldReturnIdenticalProblemJson404ForDifferentMissingProducts() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		when(this.service.detail(any(UUID.class))).thenThrow(new CatalogNotFoundException());

		MvcTestResult a = this.mvc.get().uri(PRODUCTS + "/" + first).exchange();
		MvcTestResult b = this.mvc.get().uri(PRODUCTS + "/" + second).exchange();

		verify(this.service).detail(first);
		verify(this.service).detail(second);
		for (MvcTestResult result : List.of(a, b)) {
			assertThat(result).hasStatus(404);
			assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		}
		ObjectNode bodyA = (ObjectNode) json(a);
		ObjectNode bodyB = (ObjectNode) json(b);
		assertThat(bodyA.path("status").asInt()).isEqualTo(404);
		bodyA.remove("instance");
		bodyB.remove("instance");
		assertThat(bodyA).isEqualTo(bodyB);
		assertThat(bodyA.toString()).doesNotContain(first.toString()).doesNotContain(second.toString());
	}

	@Test
	@DisplayName("US3 Escenario 3 / FR-008, FR-022: un id que no es UUID responde 400 con errors[field=id] sin el valor")
	void shouldReturn400WithoutEchoWhenIdIsNotUuid() {
		MvcTestResult result = this.mvc.get().uri(PRODUCTS + "/no-es-un-uuid").exchange();

		assertInvalidParameter(result, "id", "no-es-un-uuid");
	}

	private void stub(List<Product> products, long total) {
		when(this.service.search(any(ProductSearchCriteria.class), any(Pageable.class)))
			.thenAnswer(invocation -> {
				Pageable pageable = invocation.getArgument(1);
				return new PageImpl<>(products, pageable, total);
			});
	}

	private Pageable capturePageable() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).search(eq(ProductSearchCriteria.NONE), captor.capture());
		return captor.getValue();
	}

	private static JsonNode json(MvcTestResult result) {
		return MAPPER.readTree(result.getResponse().getContentAsByteArray());
	}

}
