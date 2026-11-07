package com.mangateca.api.catalog.web;

import com.mangateca.api.catalog.application.ClassificationQueryService;
import com.mangateca.api.catalog.application.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lectura pública de series y de sus volúmenes publicados. */
@RestController
@RequestMapping("/api/v1/series")
@Tag(name = "Series")
public class SeriesController {

	private final ClassificationQueryService classificationService;

	private final ProductQueryService productService;

	private final CatalogMapper mapper;

	public SeriesController(ClassificationQueryService classificationService, ProductQueryService productService,
			CatalogMapper mapper) {
		this.classificationService = classificationService;
		this.productService = productService;
		this.mapper = mapper;
	}

	@GetMapping
	@SecurityRequirements
	@Operation(operationId = "listSeries", summary = "Series con al menos un volumen publicado, en orden alfabético",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de series"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<SeriesResponse> list(@Valid @ModelAttribute PageParams params) {
		return PageResponse.from(this.classificationService.listSeries(params.toPageRequest(Sort.unsorted()))
			.map(this.mapper::toSeries));
	}

	@GetMapping("/{id}")
	@SecurityRequirements
	@Operation(operationId = "getSeries", summary = "Detalle de una serie con volúmenes publicados",
			responses = {
					@ApiResponse(responseCode = "200", description = "Detalle de la serie",
							content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
									schema = @Schema(implementation = SeriesResponse.class))),
					@ApiResponse(responseCode = "400", description = "Identificador con formato inválido",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "404", description = "Serie no encontrada",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public SeriesResponse get(@PathVariable UUID id) {
		return this.mapper.toSeries(this.classificationService.seriesDetail(id));
	}

	@GetMapping("/{id}/volumes")
	@SecurityRequirements
	@Operation(operationId = "listSeriesVolumes", summary = "Volúmenes publicados de una serie, por número de volumen",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de volúmenes"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))),
					@ApiResponse(responseCode = "404", description = "Serie no encontrada",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<ProductSummaryResponse> volumes(@PathVariable UUID id,
			@Valid @ModelAttribute PageParams params) {
		return PageResponse.from(this.productService.volumes(id, params.toPageRequest(Sort.unsorted()))
			.map(this.mapper::toSummary));
	}

}
