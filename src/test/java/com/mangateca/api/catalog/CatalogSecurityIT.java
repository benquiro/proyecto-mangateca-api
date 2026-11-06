package com.mangateca.api.catalog;

import static com.mangateca.api.ProblemDetailsAssertions.assertProblemDetails;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.mangateca.api.TestSecurityConfiguration;
import com.mangateca.api.TestSecurityConfiguration.TestTokens;
import com.mangateca.api.TestcontainersConfiguration;
import com.mangateca.api.catalog.support.CatalogTestData;

/**
 * US6 Escenario 1 / FR-021 / SC-004: el catálogo es de lectura anónima y el resto sigue
 * denegado por defecto.
 */
@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CatalogSecurityIT {

	private static final String ID = UUID.fromString("00000000-0000-0000-0000-000000000001").toString();

	private static final List<String> READ_ROUTES = List.of("/api/v1/products", "/api/v1/products/" + ID,
			"/api/v1/series", "/api/v1/series/" + ID, "/api/v1/series/" + ID + "/volumes", "/api/v1/genres",
			"/api/v1/authors", "/api/v1/publishers");

	private static final List<String> WRITE_ROUTES = List.of("/api/v1/products", "/api/v1/products/" + ID,
			"/api/v1/series", "/api/v1/genres", "/api/v1/authors", "/api/v1/publishers");

	@Autowired
	private RestTestClient client;

	@Autowired
	private TestTokens tokens;

	@Autowired
	private JdbcClient jdbc;

	@AfterEach
	void cleanCatalog() {
		new CatalogTestData(this.jdbc).clear();
	}

	@Test
	@DisplayName("FR-021 / SC-004 / US6 escenario 2: cliente y administrador reciben el mismo listado que un invitado")
	void shouldReturnSameProductListForGuestCustomerAndAdmin() {
		CatalogTestData data = new CatalogTestData(this.jdbc);
		data.clear();
		UUID publisher = data.publisher("Editorial Test");
		UUID author = data.author("Akira Toriyama");
		UUID series = data.series("Dragon Ball", "Serie");
		data.product("Dragon Ball 1", publisher).series(series, 1).authors(author).insert();
		data.product("Suelto", publisher).authors(author).insert();

		EntityExchangeResult<String> guest = exchange(HttpMethod.GET, "/api/v1/products", null);
		EntityExchangeResult<String> customer = exchange(HttpMethod.GET, "/api/v1/products",
				this.tokens.token("customer-1", List.of("CUSTOMER")));
		EntityExchangeResult<String> admin = exchange(HttpMethod.GET, "/api/v1/products",
				this.tokens.token("admin-1", List.of("ADMIN")));

		assertThat(guest.getStatus().value()).isEqualTo(200);
		assertThat(customer.getStatus().value()).isEqualTo(200);
		assertThat(admin.getStatus().value()).isEqualTo(200);
		assertThat(guest.getResponseBody()).contains("Dragon Ball 1").contains("Suelto");
		assertThat(customer.getResponseBody()).isEqualTo(guest.getResponseBody());
		assertThat(admin.getResponseBody()).isEqualTo(guest.getResponseBody());
	}

	@Test
	@DisplayName("FR-021 / US6 escenario 1: GET anónimo a las ocho rutas del catálogo no responde 401 ni 403")
	void shouldNotRequireTokenOnAnonymousGet() {
		for (String path : READ_ROUTES) {
			assertThat(exchange(HttpMethod.GET, path, null).getStatus().value()).as("GET " + path)
				.isNotIn(401, 403);
		}
	}

	@Test
	@DisplayName("FR-021 / US6 escenario 1: HEAD anónimo a las ocho rutas del catálogo no responde 401 ni 403")
	void shouldNotRequireTokenOnAnonymousHead() {
		for (String path : READ_ROUTES) {
			assertThat(exchange(HttpMethod.HEAD, path, null).getStatus().value()).as("HEAD " + path)
				.isNotIn(401, 403);
		}
	}

	@Test
	@DisplayName("FR-021 / SC-004: POST, PUT y DELETE anónimos al catálogo responden 401 Problem Details")
	void shouldReturn401ProblemDetailsOnAnonymousWrites() {
		for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
			for (String path : WRITE_ROUTES) {
				EntityExchangeResult<String> result = exchange(method, path, null);

				assertThat(result.getStatus().value()).as(method + " " + path).isEqualTo(401);
				assertThat(result.getResponseHeaders().getContentType()).as(method + " " + path)
					.isNotNull()
					.matches(type -> type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
				assertProblemDetails(result, 401, path);
			}
		}
	}

	@Test
	@DisplayName("FR-021 / research R8: GET con token basura al catálogo se ignora y no responde 401")
	void shouldIgnoreGarbageBearerTokenOnCatalogGet() {
		EntityExchangeResult<String> result = exchange(HttpMethod.GET, "/api/v1/products", "basura");

		assertThat(result.getStatus().value()).isNotEqualTo(401);
	}

	@Test
	@DisplayName("FR-021 / SC-004: rutas fuera de la lista blanca siguen respondiendo 401 anónimo")
	void shouldKeepDenyByDefaultOutsideWhitelist() {
		for (String path : List.of("/api/v1/products/" + ID + "/extra", "/api/v1/orders")) {
			assertThat(exchange(HttpMethod.GET, path, null).getStatus().value()).as("GET " + path).isEqualTo(401);
		}
	}

	private EntityExchangeResult<String> exchange(HttpMethod method, String path, String bearer) {
		RestTestClient.RequestBodySpec request = this.client.method(method).uri(path);
		if (bearer != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
		}
		return request.exchange().returnResult(String.class);
	}

}
