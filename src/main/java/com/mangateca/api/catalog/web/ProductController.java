package com.mangateca.api.catalog.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mangateca.api.catalog.application.ProductQueryService;
import com.mangateca.api.catalog.domain.ProductSearchCriteria;

/** Lectura pública del catálogo de productos. */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

	private final ProductQueryService service;

	private final CatalogMapper mapper;

	public ProductController(ProductQueryService service, CatalogMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping
	@SecurityRequirements
	@Operation(operationId = "listProducts", summary = "Listar, buscar y filtrar productos publicados",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de productos"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<ProductSummaryResponse> list(@Valid @ModelAttribute ProductSearchRequest request) {
		return PageResponse
			.from(this.service.search(ProductSearchCriteria.NONE, request.toPageable()).map(this.mapper::toSummary));
	}

}
