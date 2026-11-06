package com.mangateca.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.ConnectException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.mangateca.api.ApiApplication;
import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.drift.DriftEntityConfiguration;

@Testcontainers
class SchemaDriftIT {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

	@BeforeEach
	void startFromCleanDatabaseWithV1Applied() throws SQLException {
		resetSchema();
		start(POSTGRES.getJdbcUrl(), List.of()).close();
	}

	@AfterEach
	void restoreDatabase() throws SQLException {
		resetSchema();
	}

	@Test
	@DisplayName("FR-007 / US2: checksum alterado aborta el arranque")
	void shouldFailStartupWhenChecksumDiffers() throws SQLException {
		execute("UPDATE flyway_schema_history SET checksum = 1 WHERE version = '1'");
		Snapshot before = snapshot();

		assertThatThrownBy(() -> startAndClose(POSTGRES.getJdbcUrl(), List.of()))
			.hasStackTraceContaining("Migration checksum mismatch for migration version 1");

		assertThat(snapshot()).isEqualTo(before);
		assertThat(before.history()).anyMatch(row -> row.get("checksum") instanceof Number n && n.intValue() == 1);
	}

	@Test
	@DisplayName("FR-007 / US2: migracion aplicada ausente en local (future) aborta el arranque")
	void shouldFailStartupWhenAppliedMigrationIsMissingLocally() throws SQLException {
		execute("""
				INSERT INTO flyway_schema_history
				  (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
				SELECT COALESCE(MAX(installed_rank), 0) + 1, '999', 'future', 'SQL', 'V999__future.sql', 123,
				  'test', 0, true
				FROM flyway_schema_history
				""");
		Snapshot before = snapshot();

		assertThatThrownBy(() -> startAndClose(POSTGRES.getJdbcUrl(), List.of()))
			.hasStackTraceContaining("not resolved locally: 999");

		assertThat(snapshot()).isEqualTo(before);
		assertThat(before.history()).anyMatch(row -> "999".equals(row.get("version")));
	}

	@Test
	@DisplayName("FR-007 / US2: entidad sin tabla aborta el arranque (ddl-auto=validate)")
	void shouldFailStartupWhenEntityHasNoTable() throws SQLException {
		Snapshot before = snapshot();

		assertThatThrownBy(() -> startAndClose(POSTGRES.getJdbcUrl(), List.of(DriftEntityConfiguration.class)))
			.hasStackTraceContaining("missing table");

		Snapshot after = snapshot();
		assertThat(after).isEqualTo(before);
		assertThat(after.tables()).doesNotContain("drift_missing_table");
	}

	@Test
	@DisplayName("FR-005 / edge case: base inaccesible aborta el arranque")
	void shouldFailStartupWhenDatabaseIsUnreachable() throws SQLException {
		Snapshot before = snapshot();
		String closedPortUrl = "jdbc:postgresql://localhost:1/postgres";

		// SC-006 (trazabilidad): Flyway y JPA fallan durante el refresh del contexto, antes de que
		// arranquen los conectores web; por eso basta con WebApplicationType.NONE.
		Throwable thrown = catchThrowable(() -> startAndClose(closedPortUrl, List.of()));

		// Exigimos causa de conectividad recorriendo la cadena de causas por tipo (no por mensaje):
		// es robusta ante cambios de texto/idioma del driver y de Hikari, y rechaza fallos ajenos.
		assertThat(thrown).isNotNull();
		assertThat(causeChain(thrown))
			.anyMatch(t -> t instanceof ConnectException || t instanceof PSQLException);

		// FR-008: comprobacion trivial, la app nunca toca esta base (apunta al puerto cerrado);
		// el snapshot se toma sobre la base del contenedor y solo confirma que sigue intacta.
		assertThat(snapshot()).isEqualTo(before);
	}

	private void startAndClose(String jdbcUrl, List<Class<?>> extraSources) {
		try (ConfigurableApplicationContext context = start(jdbcUrl, extraSources)) {
			assertThat(context.isActive()).isTrue();
		}
	}

	private static List<Throwable> causeChain(Throwable throwable) {
		List<Throwable> chain = new ArrayList<>();
		for (Throwable t = throwable; t != null && !chain.contains(t); t = t.getCause()) {
			chain.add(t);
		}
		return chain;
	}

	private ConfigurableApplicationContext start(String jdbcUrl, List<Class<?>> extraSources) {
		List<Class<?>> sources = new ArrayList<>(List.of(ApiApplication.class, TestSecurityConfiguration.class));
		sources.addAll(extraSources);
		return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
			.web(WebApplicationType.NONE)
			.properties("spring.datasource.url=" + jdbcUrl,
					"spring.datasource.username=" + POSTGRES.getUsername(),
					"spring.datasource.password=" + POSTGRES.getPassword(),
					"spring.datasource.hikari.connection-timeout=1000",
					"spring.docker.compose.enabled=false")
			.run();
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static void execute(String sql) throws SQLException {
		try (Connection connection = connect(); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static void resetSchema() throws SQLException {
		execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
	}

	private static Snapshot snapshot() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
		JdbcClient jdbc = JdbcClient.create(dataSource);
		List<String> tables = jdbc.sql(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")
			.query(String.class)
			.list();
		List<Map<String, Object>> columns = jdbc.sql("""
				SELECT table_name, column_name, data_type, is_nullable
				FROM information_schema.columns WHERE table_schema = 'public'
				ORDER BY table_name, ordinal_position
				""").query().listOfRows();
		List<Map<String, Object>> history = jdbc.sql(
				"SELECT installed_rank, version, checksum, success FROM flyway_schema_history ORDER BY installed_rank")
			.query()
			.listOfRows();
		List<Map<String, Object>> indexes = jdbc.sql(
				"SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY indexname")
			.query()
			.listOfRows();
		return new Snapshot(tables, columns, history, indexes);
	}

	private record Snapshot(List<String> tables, List<Map<String, Object>> columns,
			List<Map<String, Object>> history, List<Map<String, Object>> indexes) {
	}

}
