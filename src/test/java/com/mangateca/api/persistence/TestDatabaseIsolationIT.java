package com.mangateca.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestcontainersConfiguration;

@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest
class TestDatabaseIsolationIT {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PostgreSQLContainer container;

	@Test
	@DisplayName("FR-010 / US5: el servidor es PostgreSQL 18")
	void shouldRunAgainstPostgres18() {
		String version = jdbc.queryForObject("SHOW server_version", String.class);

		assertThat(version).startsWith("18");
	}

	@Test
	@DisplayName("FR-010 / US5-1: el DataSource apunta al contenedor de Testcontainers")
	void shouldUseTestcontainersJdbcUrl() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			String url = connection.getMetaData().getURL();
			assertThat(url).isEqualTo(container.getJdbcUrl());
			assertThat(url).doesNotContain(":5432/");
		}
	}

	@Test
	@DisplayName("FR-011 / US5: el driver es org.postgresql")
	void shouldUsePostgresDriver() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDriverName()).containsIgnoringCase("postgresql");
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
			assertThat(connection.unwrap(org.postgresql.PGConnection.class)).isNotNull();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "org.h2.Driver", "org.hsqldb.jdbc.JDBCDriver", "org.apache.derby.jdbc.EmbeddedDriver" })
	@DisplayName("FR-011 / US5: el classpath no contiene drivers de BD embebidas (H2, HSQLDB, Derby)")
	void shouldNotHaveEmbeddedDatabaseDriversOnClasspath(String driverClass) {
		assertThatExceptionOfType(ClassNotFoundException.class)
			.isThrownBy(() -> Class.forName(driverClass, false, getClass().getClassLoader()));
	}

}
