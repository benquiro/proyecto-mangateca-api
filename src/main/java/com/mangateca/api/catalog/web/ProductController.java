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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.mangateca.api.catalog.application.ProductQueryService;

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
			.from(this.service.search(request.toCriteria(), request.toPageable()).map(this.mapper::toSummary));
	}

	@GetMapping("/{id}")
	@SecurityRequirements
	@Operation(operationId = "getProduct", summary = "Detalle de un producto publicado",
			responses = {
					@ApiResponse(responseCode = "200", description = "Detalle del producto"),
					@ApiResponse(responseCode = "400", description = "Identificador con formato inválido",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "404", description = "Producto no encontrado",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public ProductDetailResponse get(@PathVariable UUID id) {
		return this.mapper.toDetail(this.service.detail(id));
	}

}
