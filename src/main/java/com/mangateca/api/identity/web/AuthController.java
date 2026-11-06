package com.mangateca.api.identity.web;

import com.mangateca.api.identity.application.AuthenticationService;
import com.mangateca.api.identity.application.InvalidRefreshTokenException;
import com.mangateca.api.identity.application.IssuedTokens;
import com.mangateca.api.identity.application.RefreshTokenService;
import com.mangateca.api.identity.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos de autenticación. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registro, sesión, renovación e identidad actual")
public class AuthController {

  private static final String REFRESH_COOKIE_DESCRIPTION =
      "Token de renovación. Su ausencia produce el mismo 401 que un token inválido.";

  private final AuthenticationService authenticationService;
  private final UserMapper userMapper;
  private final RefreshCookieFactory refreshCookieFactory;
  private final RefreshTokenService refreshTokenService;

  public AuthController(
      AuthenticationService authenticationService,
      UserMapper userMapper,
      RefreshCookieFactory refreshCookieFactory,
      RefreshTokenService refreshTokenService) {
    this.authenticationService = authenticationService;
    this.userMapper = userMapper;
    this.refreshCookieFactory = refreshCookieFactory;
    this.refreshTokenService = refreshTokenService;
  }

  @Operation(
      summary = "Identidad actual",
      security = @SecurityRequirement(name = "bearerAuth"),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Datos de la cuenta autenticada.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Sin token, expirado o inválido (WWW-Authenticate: Bearer).",
            headers =
                @Header(name = HttpHeaders.WWW_AUTHENTICATE, schema = @Schema(type = "string")),
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
      })
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return currentUserId(jwt)
        .flatMap(authenticationService::me)
        .map(userMapper::toResponse)
        .orElseThrow(AuthController::unauthorized);
  }

  private static Optional<UUID> currentUserId(@Nullable Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(jwt.getSubject()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static ErrorResponseException unauthorized() {
    ErrorResponseException ex = new ErrorResponseException(HttpStatus.UNAUTHORIZED);
    ex.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    return ex;
  }

  @SecurityRequirements
  @Operation(
      summary = "Registrar un cliente (rol CUSTOMER)",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Cuenta creada. No inicia sesión.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description =
                "Validación fallida (incluye enviar `role` o una contraseña fuera de política).",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "El correo ya está en uso (tipo email-already-in-use).",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                        {"type":"urn:mangateca:problem:email-already-in-use","title":"Conflict","status":409,"detail":"El correo ya está en uso.","instance":"/api/v1/auth/register"}""")))
      })
  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    User user =
        authenticationService.register(
            Objects.requireNonNull(request.email()),
            Objects.requireNonNull(request.displayName()),
            Objects.requireNonNull(request.password()));
    return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponse(user));
  }

  @SecurityRequirements
  @Operation(
      summary = "Iniciar sesión",
      description =
          "Credenciales incorrectas, correo inexistente, cuenta bloqueada o deshabilitada "
              + "producen la misma respuesta. El token de renovación llega solo en la cookie "
              + "refresh_token.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Sesión iniciada. El token de renovación llega en Set-Cookie.",
            headers =
                @Header(
                    name = HttpHeaders.SET_COOKIE,
                    description =
                        "refresh_token=<43 caracteres base64url>; Path=/api/v1/auth;"
                            + " Max-Age=604800; HttpOnly; Secure; SameSite=Strict",
                    schema = @Schema(type = "string")),
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Cuerpo inválido: `email` o `password` vacíos o ausentes.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Credenciales inválidas (tipo invalid-credentials).",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                        {"type":"urn:mangateca:problem:invalid-credentials","title":"Unauthorized","status":401,"detail":"Credenciales inválidas.","instance":"/api/v1/auth/login"}""")))
      })
  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    IssuedTokens tokens =
        authenticationService.login(
            Objects.requireNonNull(request.email()), Objects.requireNonNull(request.password()));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
        .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn()));
  }

  @SecurityRequirements
  @Operation(
      summary = "Renovar la sesión (rotación del token de renovación)",
      description =
          "Lee el token de la cookie refresh_token. Cada uso lo invalida y entrega uno nuevo en "
              + "la misma familia. Reutilizar un token ya rotado revoca toda la familia.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Nuevo token de acceso; nueva cookie de renovación en Set-Cookie.",
            headers =
                @Header(
                    name = HttpHeaders.SET_COOKIE,
                    description =
                        "refresh_token=<43 caracteres base64url>; Path=/api/v1/auth;"
                            + " Max-Age=604800; HttpOnly; Secure; SameSite=Strict",
                    schema = @Schema(type = "string")),
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description =
                "Token ausente, desconocido, expirado, reutilizado o usuario deshabilitado "
                    + "(tipo invalid-refresh-token).",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                        {"type":"urn:mangateca:problem:invalid-refresh-token","title":"Unauthorized","status":401,"detail":"Token de renovación inválido.","instance":"/api/v1/auth/refresh"}""")))
      })
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @Parameter(description = REFRESH_COOKIE_DESCRIPTION)
          @CookieValue(name = "refresh_token", required = false)
          @Nullable String refreshToken) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException();
    }
    IssuedTokens tokens = refreshTokenService.rotate(refreshToken);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
        .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn()));
  }

  @SecurityRequirements
  @Operation(
      summary = "Cerrar sesión",
      description =
          "Público e idempotente: responde 204 con cookie ausente, desconocida o expirada. "
              + "Revoca toda la familia del token recibido y borra la cookie (Max-Age=0).",
      responses = {
        @ApiResponse(
            responseCode = "204",
            description = "Sesión cerrada (o no existía). La cookie se borra con Max-Age=0.",
            headers =
                @Header(
                    name = HttpHeaders.SET_COOKIE,
                    description = "Borrado de la cookie refresh_token (Max-Age=0).",
                    schema = @Schema(type = "string")))
      })
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @Parameter(
              description = "Token de renovación. Su ausencia o invalidez también produce 204.")
          @CookieValue(name = "refresh_token", required = false)
          @Nullable String refreshToken) {
    refreshTokenService.revoke(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
        .build();
  }
}
