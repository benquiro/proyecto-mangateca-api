package com.mangateca.api.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.mangateca.api.catalog.application.CatalogNotFoundException;
import com.mangateca.api.catalog.application.ClassificationQueryService;
import com.mangateca.api.catalog.application.ProductQueryService;
import com.mangateca.api.catalog.config.CatalogSecurityConfiguration;
import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.Series;
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

@WebMvcTest(SeriesController.class)
@ActiveProfiles("test")
@Import({ SecurityConfiguration.class, IdentitySecurityConfiguration.class, JwtKeyConfiguration.class,
		ProblemDetailsResponseWriter.class, ProblemDetailsAuthenticationEntryPoint.class,
		ProblemDetailsAccessDeniedHandler.class, CatalogSecurityConfiguration.class, CatalogMapperImpl.class })
class SeriesControllerTests {

	private static final String SERIES = "/api/v1/series";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ProductQueryService productService;

	@MockitoBean
	private ClassificationQueryService classificationService;

	@Test
	@DisplayName("US4 Escenario 1 / FR-011: GET /series/{id} responde 200 con id, name y description")
	void shouldReturnSeriesDetailWithIdNameAndDescription() {
		Series series = CatalogFixtures.series("Dragon Ball", "Historia de Goku");
		when(this.classificationService.seriesDetail(series.getId())).thenReturn(series);

		MvcTestResult result = this.mvc.get().uri(SERIES + "/" + series.getId()).exchange();

		assertThat(result).hasStatusOk();
		JsonNode body = json(result);
		assertThat(body.path("id").asString()).isEqualTo(series.getId().toString());
		assertThat(body.path("name").asString()).isEqualTo("Dragon Ball");
		assertThat(body.path("description").asString()).isEqualTo("Historia de Goku");
		assertThat(body.has("volumes")).isFalse();
	}

	@Test
	@DisplayName("US4 Escenario 1 / FR-011: GET /series/{id}/volumes responde 200 PageResponse en el orden del servicio")
	void shouldReturnVolumesPageInServiceOrder() {
		Series series = CatalogFixtures.series("Dragon Ball", "Historia de Goku");
		Product first = CatalogFixtures.productInSeries("Dragon Ball 1", series, 1);
		Product third = CatalogFixtures.productInSeries("Dragon Ball 3", series, 3);
		when(this.productService.volumes(any(UUID.class), any(Pageable.class))).thenAnswer(invocation -> {
			Pageable pageable = invocation.getArgument(1);
			return new PageImpl<>(List.of(first, third), pageable, 2);
		});

		MvcTestResult result = this.mvc.get().uri(SERIES + "/" + series.getId() + "/volumes").exchange();

		assertThat(result).hasStatusOk();
		JsonNode body = json(result);
		assertThat(body.path("content")).hasSize(2);
		assertThat(body.path("content").path(0).path("title").asString()).isEqualTo("Dragon Ball 1");
		assertThat(body.path("content").path(0).path("volumeNumber").asInt()).isEqualTo(1);
		assertThat(body.path("content").path(1).path("title").asString()).isEqualTo("Dragon Ball 3");
		assertThat(body.path("page").path("size").asInt()).isEqualTo(20);
		assertThat(body.path("page").path("totalElements").asLong()).isEqualTo(2);
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-011, FR-022: serie inexistente da el mismo 404 problem+json en detalle y volúmenes")
	void shouldReturnIdentical404ForDetailAndVolumes() {
		UUID id = UUID.randomUUID();
		when(this.classificationService.seriesDetail(any(UUID.class))).thenThrow(new CatalogNotFoundException());
		when(this.productService.volumes(any(UUID.class), any(Pageable.class)))
			.thenThrow(new CatalogNotFoundException());

		MvcTestResult detail = this.mvc.get().uri(SERIES + "/" + id).exchange();
		MvcTestResult volumes = this.mvc.get().uri(SERIES + "/" + id + "/volumes").exchange();

		for (MvcTestResult result : List.of(detail, volumes)) {
			assertThat(result).hasStatus(404);
			assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		}
		ObjectNode bodyDetail = (ObjectNode) json(detail);
		ObjectNode bodyVolumes = (ObjectNode) json(volumes);
		bodyDetail.remove("instance");
		bodyVolumes.remove("instance");
		assertThat(bodyDetail).isEqualTo(bodyVolumes);
		assertThat(bodyDetail.get("title").asString()).isEqualTo("Not Found");
		assertThat(bodyDetail.get("detail").asString()).isEqualTo("Recurso no encontrado.");
		assertThat(bodyDetail.toString()).doesNotContain(id.toString());
	}

	@Test
	@DisplayName("US4 / FR-008: size=101 en volúmenes responde 400 problem+json con errors[field=size]")
	void shouldReturn400ProblemJsonWhenVolumesSizeExceedsMaximum() {
		MvcTestResult result = this.mvc.get()
			.uri(SERIES + "/" + UUID.randomUUID() + "/volumes")
			.param("size", "101")
			.exchange();

		assertInvalidParameter(result, "size", null);
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-008, FR-022: un id de serie que no es UUID responde 400 sin el valor")
	void shouldReturn400WithoutEchoWhenSeriesIdIsNotUuid() {
		assertInvalidParameter(this.mvc.get().uri(SERIES + "/no-uuid").exchange(), "id", "no-uuid");
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-008, FR-022: un id de serie no UUID en volúmenes responde 400 sin el valor")
	void shouldReturn400WithoutEchoWhenVolumesSeriesIdIsNotUuid() {
		assertInvalidParameter(this.mvc.get().uri(SERIES + "/no-uuid/volumes").exchange(), "id", "no-uuid");
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
			// `instance` es la URI de la petición; el valor no debe aparecer en el resto.
			ObjectNode body = (ObjectNode) json(result);
			body.remove("instance");
			assertThat(body.toString()).doesNotContain(forbiddenInBody);
		}
		verifyNoInteractions(this.productService, this.classificationService);
	}

	private static JsonNode json(MvcTestResult result) {
		return MAPPER.readTree(result.getResponse().getContentAsByteArray());
	}

}
