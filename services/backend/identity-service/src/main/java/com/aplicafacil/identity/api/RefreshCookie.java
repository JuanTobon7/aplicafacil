package com.aplicafacil.identity.api;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import com.aplicafacil.identity.application.services.RefreshTokenService.Issued;

/**
 * Cookie {@code af_rt}: HttpOnly, Secure, SameSite=Strict y limitada a {@code /api/auth} (la ruta que ve el
 * navegador a través del gateway). Los navegadores aceptan Secure en http://127.0.0.1 y http://localhost.
 */
final class RefreshCookie {

    static final String NAME = "af_rt";
    private static final String PATH = "/api/auth";

    private RefreshCookie() {
    }

    static ResponseEntity.BodyBuilder set(ResponseEntity.BodyBuilder response, Issued token) {
        Duration maxAge = Duration.between(Instant.now(), token.expiresAt());
        return response.header(HttpHeaders.SET_COOKIE, base(token.value()).maxAge(maxAge).build().toString());
    }

    static ResponseEntity.HeadersBuilder<?> clear(ResponseEntity.HeadersBuilder<?> response) {
        return response.header(HttpHeaders.SET_COOKIE, base("").maxAge(0).build().toString());
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}
