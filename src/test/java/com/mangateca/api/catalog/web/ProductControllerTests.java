package com.mangateca.api.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

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

import com.mangateca.api.catalog.application.ProductQueryService;
import com.mangateca.api.catalog.config.CatalogSecurityConfiguration;
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
			assertThat(json(result).toString()).doesNotContain(forbiddenInBody);
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
