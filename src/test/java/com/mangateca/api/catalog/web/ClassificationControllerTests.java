package com.mangateca.api.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.mangateca.api.catalog.application.ClassificationQueryService;
import com.mangateca.api.catalog.config.CatalogSecurityConfiguration;
import com.mangateca.api.catalog.domain.Author;
import com.mangateca.api.catalog.domain.Genre;
import com.mangateca.api.catalog.domain.Publisher;
import com.mangateca.api.catalog.support.CatalogFixtures;
import com.mangateca.api.identity.config.IdentitySecurityConfiguration;
import com.mangateca.api.identity.config.JwtKeyConfiguration;
import com.mangateca.api.platform.security.ProblemDetailsAccessDeniedHandler;
import com.mangateca.api.platform.security.ProblemDetailsAuthenticationEntryPoint;
import com.mangateca.api.platform.security.ProblemDetailsResponseWriter;
import com.mangateca.api.platform.security.SecurityConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(ClassificationController.class)
@ActiveProfiles("test")
@Import({ SecurityConfiguration.class, IdentitySecurityConfiguration.class, JwtKeyConfiguration.class,
		ProblemDetailsResponseWriter.class, ProblemDetailsAuthenticationEntryPoint.class,
		ProblemDetailsAccessDeniedHandler.class, CatalogSecurityConfiguration.class, CatalogMapperImpl.class })
class ClassificationControllerTests {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ClassificationQueryService service;

	@ParameterizedTest
	@ValueSource(strings = { "genres", "authors", "publishers" })
	@DisplayName("US5 Escenario 2 / FR-013: GET /{genres,authors,publishers} responde 200 PageResponse de {id, name}")
	void shouldReturnPageOfNamedClassifications(String path) {
		List<?> entities = stub(path);

		MvcTestResult result = this.mvc.get().uri("/api/v1/" + path).exchange();

		assertThat(result).hasStatusOk();
		JsonNode body = MAPPER.readTree(result.getResponse().getContentAsByteArray());
		assertThat(body.path("content")).hasSize(2);
		assertThat(body.path("content").path(0).propertyNames()).containsExactlyInAnyOrder("id", "name");
		assertThat(body.path("content").path(0).path("name").asString()).isEqualTo("Alpha");
		assertThat(body.path("content").path(1).path("name").asString()).isEqualTo("Bravo");
		assertThat(body.path("content").path(0).path("id").asString()).isNotBlank();
		assertThat(body.path("page").path("size").asInt()).isEqualTo(20);
		assertThat(body.path("page").path("number").asInt()).isZero();
		assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(entities.size());
		assertThat(body.path("page").path("totalPages").asInt()).isEqualTo(1);
	}

	private List<?> stub(String path) {
		switch (path) {
			case "genres" -> {
				List<Genre> items = List.of(CatalogFixtures.genre("Alpha"), CatalogFixtures.genre("Bravo"));
				when(this.service.listGenres(any(Pageable.class)))
					.thenAnswer(i -> new PageImpl<>(items, i.<Pageable>getArgument(0), items.size()));
				return items;
			}
			case "authors" -> {
				List<Author> items = List.of(CatalogFixtures.author("Alpha"), CatalogFixtures.author("Bravo"));
				when(this.service.listAuthors(any(Pageable.class)))
					.thenAnswer(i -> new PageImpl<>(items, i.<Pageable>getArgument(0), items.size()));
				return items;
			}
			default -> {
				List<Publisher> items = List.of(CatalogFixtures.publisher("Alpha"),
						CatalogFixtures.publisher("Bravo"));
				when(this.service.listPublishers(any(Pageable.class)))
					.thenAnswer(i -> new PageImpl<>(items, i.<Pageable>getArgument(0), items.size()));
				return items;
			}
		}
	}

}
