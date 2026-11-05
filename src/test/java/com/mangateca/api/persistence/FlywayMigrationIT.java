package com.mangateca.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestcontainersConfiguration;

@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest
class FlywayMigrationIT {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("FR-005 / US2: V1 aplicada con exito en flyway_schema_history")
	void shouldHaveAppliedV1Successfully() {
		Boolean success = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	@DisplayName("FR-006 / data-model 1: event_publication tiene las 9 columnas con tipo y nulabilidad")
	void shouldCreateEventPublicationWithExpectedColumns() {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT column_name, data_type, is_nullable
				FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'event_publication'
				ORDER BY ordinal_position
				""");

		assertThat(rows).extracting(r -> r.get("column_name") + "|" + r.get("data_type") + "|" + r.get("is_nullable"))
			.containsExactly(
					"id|uuid|NO",
					"listener_id|text|NO",
					"event_type|text|NO",
					"serialized_event|text|NO",
					"publication_date|timestamp with time zone|NO",
					"completion_date|timestamp with time zone|YES",
					"status|text|YES",
					"completion_attempts|integer|YES",
					"last_resubmission_date|timestamp with time zone|YES");
	}

	@Test
	@DisplayName("FR-006 / data-model 1: existen los indices de event_publication")
	void shouldCreateEventPublicationIndexes() {
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'event_publication'",
				String.class);

		assertThat(indexes).contains("event_publication_serialized_event_hash_idx",
				"event_publication_by_completion_date_idx");
	}

	@Test
	@DisplayName("FR-006: sin tablas de negocio en el esquema public")
	void shouldNotCreateBusinessTables() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "event_publication");
	}

	@Test
	@DisplayName("FR-006 / R3: solo la extension plpgsql")
	void shouldNotCreateExtensions() {
		List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

		assertThat(extensions).containsExactly("plpgsql");
	}

}
