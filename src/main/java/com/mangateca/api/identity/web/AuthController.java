package com.mangateca.api.identity.web;

import com.mangateca.api.identity.application.AuthenticationService;
import com.mangateca.api.identity.application.IssuedTokens;
import com.mangateca.api.identity.domain.User;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  public AuthController(
      AuthenticationService authenticationService,
      UserMapper userMapper,
      RefreshCookieFactory refreshCookieFactory) {
    this.authenticationService = authenticationService;
    this.userMapper = userMapper;
    this.refreshCookieFactory = refreshCookieFactory;
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
}
