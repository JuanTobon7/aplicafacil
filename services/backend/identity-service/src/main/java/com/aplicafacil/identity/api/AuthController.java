package com.aplicafacil.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.services.AccountService;
import com.aplicafacil.identity.application.services.AccountService.AccountSummary;
import com.aplicafacil.identity.application.services.LoginCodeService;
import com.aplicafacil.identity.application.services.RefreshTokenService;
import com.aplicafacil.identity.application.services.RefreshTokenService.Issued;
import com.aplicafacil.identity.domain.AppUser;
import com.aplicafacil.identity.infrastructure.security.TokenService;
import com.aplicafacil.identity.infrastructure.security.TokenService.IssuedToken;

/**
 * Sesión en la plataforma. El access token va en el cuerpo (el front lo guarda solo en memoria) y el
 * refresh token en la cookie {@code af_rt}. Ningún token viaja en la URL.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final AccountService accounts;
    private final LoginCodeService loginCodes;
    private final RefreshTokenService refreshTokens;
    private final TokenService tokens;
    private final boolean oauthEnabled;

    AuthController(AccountService accounts, LoginCodeService loginCodes, RefreshTokenService refreshTokens,
                   TokenService tokens, @Value("${app.linkedin.oauth.enabled:false}") boolean oauthEnabled) {
        this.accounts = accounts;
        this.loginCodes = loginCodes;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.oauthEnabled = oauthEnabled;
    }

    /** El front oculta el botón "Iniciar sesión con LinkedIn" (OAuth) mientras esté desactivado. */
    @GetMapping("/config")
    AuthConfig config() {
        return new AuthConfig(oauthEnabled);
    }

    @GetMapping("/accounts")
    List<AccountSummary> accounts() {
        return accounts.listAccounts();
    }

    /** Termina la conexión con LinkedIn: canjea el código de un solo uso recibido por SSE. */
    @PostMapping("/session")
    ResponseEntity<SessionResponse> exchange(@Valid @RequestBody ExchangeRequest request) {
        UUID userId = loginCodes.consume(request.attemptId(), request.loginCode());
        accounts.markLogin(userId);
        return start(userId);
    }

    /**
     * "Continuar como…". Si la cookie ya es de ese usuario, solo se rota; si no, se valida su sesión de
     * LinkedIn guardada en este equipo.
     */
    @PostMapping("/accounts/{userId}/session")
    ResponseEntity<SessionResponse> continueAs(@PathVariable UUID userId,
                                               @CookieValue(name = RefreshCookie.NAME, required = false) String cookie) {
        if (cookie != null && ownedBy(cookie, userId)) {
            Issued rotated = refreshTokens.rotate(cookie);
            accounts.markLogin(userId);
            return respond(userId, rotated);
        }
        refreshTokens.revoke(cookie);
        return start(accounts.continueAs(userId));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessionResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String cookie) {
        Issued rotated = refreshTokens.rotate(cookie);
        return respond(rotated.userId(), rotated);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String cookie) {
        refreshTokens.revoke(cookie);
        return RefreshCookie.clear(ResponseEntity.noContent()).build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(accounts.get(UUID.fromString(jwt.getSubject())));
    }

    /** "Eliminar mi cuenta y mis datos": usuario, perfil, sesión y postulaciones. */
    @DeleteMapping("/me")
    ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
        accounts.deleteAccount(UUID.fromString(jwt.getSubject()));
        return RefreshCookie.clear(ResponseEntity.noContent()).build();
    }

    private boolean ownedBy(String cookie, UUID userId) {
        try {
            return refreshTokens.ownerOf(cookie).equals(userId);
        } catch (IdentityException e) {
            return false;
        }
    }

    private ResponseEntity<SessionResponse> start(UUID userId) {
        return respond(userId, refreshTokens.issue(userId));
    }

    private ResponseEntity<SessionResponse> respond(UUID userId, Issued refresh) {
        IssuedToken access = tokens.issueAccessToken(userId);
        UserResponse user = UserResponse.from(accounts.get(userId));
        return RefreshCookie.set(ResponseEntity.ok(), refresh)
                .body(new SessionResponse(access.value(), access.expiresAt(), user));
    }

    record AuthConfig(boolean oauthEnabled) {
    }

    record ExchangeRequest(@NotNull UUID attemptId, @NotBlank String loginCode) {
    }

    record SessionResponse(String accessToken, Instant expiresAt, UserResponse user) {
    }

    record UserResponse(UUID id, String firstName, String lastName, String headline, String photoUrl,
                        String publicProfileUrl, String location, String email, boolean emailVerified,
                        Instant lastLoginAt) {

        static UserResponse from(AppUser u) {
            AppUser.Basics b = u.basics();
            return new UserResponse(u.id(), b.firstName(), b.lastName(), b.headline(), b.photoUrl(),
                    b.publicProfileUrl(), b.location(), u.links().email(), u.links().emailVerified(),
                    u.activity().lastLoginAt());
        }
    }
}
