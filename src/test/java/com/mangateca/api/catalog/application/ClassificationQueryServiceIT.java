package com.mangateca.api.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.UUID;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.catalog.domain.PublicationStatus;
import com.mangateca.api.catalog.domain.Series;
import com.mangateca.api.catalog.support.CatalogTestData;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, ClassificationQueryService.class })
class ClassificationQueryServiceIT {

	@Autowired
	private ClassificationQueryService service;

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
	@DisplayName("US4 Escenario 1 / FR-011, FR-014: seriesDetail devuelve nombre y descripción")
	void shouldReturnNameAndDescriptionOfSeriesWithPublishedVolume() {
		UUID id = this.data.series("Dragon Ball", "Historia de Goku");
		this.data.product("Dragon Ball 1", this.publisher).series(id, 1).insert();
		this.data.product("Dragon Ball 2", this.publisher)
			.series(id, 2)
			.status(PublicationStatus.UNPUBLISHED)
			.insert();
		this.entityManager.clear();

		Series series = this.service.seriesDetail(id);

		assertThat(series.getId()).isEqualTo(id);
		assertThat(series.getName()).isEqualTo("Dragon Ball");
		assertThat(series.getDescription()).isEqualTo("Historia de Goku");
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-011: serie inexistente lanza CatalogNotFoundException")
	void shouldThrowNotFoundWhenSeriesDoesNotExist() {
		Throwable thrown = catchThrowable(() -> this.service.seriesDetail(UUID.randomUUID()));

		assertThat(thrown).isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	@DisplayName("US4 Escenario 3 / FR-011, FR-014: serie sin volúmenes publicados lanza CatalogNotFoundException")
	void shouldThrowNotFoundWhenSeriesHasNoPublishedVolumes() {
		UUID id = this.data.series("Serie Vacía", "Descripción");
		this.data.product("Borrador", this.publisher).series(id, 1).status(PublicationStatus.UNPUBLISHED).insert();
		this.entityManager.clear();

		Throwable thrown = catchThrowable(() -> this.service.seriesDetail(id));

		assertThat(thrown).isInstanceOf(CatalogNotFoundException.class);
	}

}
