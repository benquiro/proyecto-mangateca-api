package com.mangateca.api.platform.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador solo de test. No forma parte de la API publicada. Sin @Validated a nivel de
 * clase para que la validacion de parametros lance HandlerMethodValidationException.
 */
@RestController
@RequestMapping("/api/v1/probes")
public class ProbeController {

	public record ProbeRequest(@NotBlank String name, @NotNull @Positive Integer quantity) {
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ProbeRequest create(@Valid @RequestBody ProbeRequest request) {
		return request;
	}

	@GetMapping
	public String list(@RequestParam @Min(1) @Max(100) int size) {
		return "size=" + size;
	}

	@GetMapping("/admin")
	@PreAuthorize("hasRole('ADMIN')")
	public String admin() {
		return "admin";
	}

	@GetMapping(value = "/json-only", produces = MediaType.APPLICATION_JSON_VALUE)
	public ProbeRequest jsonOnly() {
		return new ProbeRequest("probe", 1);
	}

	@GetMapping("/status-failure")
	public String statusFailure() {
		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "razon interna SELECT");
	}

	@GetMapping("/failure")
	public String failure() {
		throw new IllegalStateException("detalle interno SELECT * FROM secreto");
	}

}
