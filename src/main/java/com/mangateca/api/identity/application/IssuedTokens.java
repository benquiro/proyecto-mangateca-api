package com.mangateca.api.identity.application;

/** Resultado de un login: token de acceso, su vigencia en segundos y el refresh en claro. */
public record IssuedTokens(String accessToken, long expiresIn, String refreshToken) {}
