package com.mangateca.api.identity.web;

import com.mangateca.api.identity.application.AuthenticationService;
import com.mangateca.api.identity.application.InvalidRefreshTokenException;
import com.mangateca.api.identity.application.IssuedTokens;
import com.mangateca.api.identity.application.RefreshTokenService;
import com.mangateca.api.identity.domain.User;
import jakarta.validation.Valid;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints publicos de autenticacion. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

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

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    User user =
        authenticationService.register(
            Objects.requireNonNull(request.email()),
            Objects.requireNonNull(request.displayName()),
            Objects.requireNonNull(request.password()));
    return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponse(user));
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    IssuedTokens tokens =
        authenticationService.login(
            Objects.requireNonNull(request.email()), Objects.requireNonNull(request.password()));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
        .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = "refresh_token", required = false) @Nullable String refreshToken) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException();
    }
    IssuedTokens tokens = refreshTokenService.rotate(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.refreshToken()).toString())
        .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "refresh_token", required = false) @Nullable String refreshToken) {
    refreshTokenService.revoke(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
        .build();
  }
}
