package com.mangateca.api.catalog.web;

import com.mangateca.api.catalog.application.ClassificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lectura pública de clasificaciones (géneros, autores, editoriales) con productos publicados. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Classifications")
public class ClassificationController {

	private final ClassificationQueryService service;

	private final CatalogMapper mapper;

	public ClassificationController(ClassificationQueryService service, CatalogMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping("/genres")
	@SecurityRequirements
	@Operation(operationId = "listGenres", summary = "Géneros con al menos un producto publicado, en orden alfabético",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de genres"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<NamedResponse> genres(@Valid @ModelAttribute PageParams params) {
		return PageResponse.from(this.service.listGenres(params.toPageRequest(Sort.unsorted())).map(this.mapper::toNamed));
	}

	@GetMapping("/authors")
	@SecurityRequirements
	@Operation(operationId = "listAuthors", summary = "Autores con al menos un producto publicado, en orden alfabético",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de authors"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<NamedResponse> authors(@Valid @ModelAttribute PageParams params) {
		return PageResponse.from(this.service.listAuthors(params.toPageRequest(Sort.unsorted())).map(this.mapper::toNamed));
	}

	@GetMapping("/publishers")
	@SecurityRequirements
	@Operation(operationId = "listPublishers", summary = "Editoriales con al menos un producto publicado, en orden alfabético",
			responses = {
					@ApiResponse(responseCode = "200", description = "Página de publishers"),
					@ApiResponse(responseCode = "400", description = "Parámetros inválidos",
							content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
									schema = @Schema(implementation = ProblemDetail.class))) })
	public PageResponse<NamedResponse> publishers(@Valid @ModelAttribute PageParams params) {
		return PageResponse.from(this.service.listPublishers(params.toPageRequest(Sort.unsorted())).map(this.mapper::toNamed));
	}

}
