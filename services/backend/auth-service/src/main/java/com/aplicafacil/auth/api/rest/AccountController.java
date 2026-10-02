package com.aplicafacil.auth.api.rest;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.auth.application.AccountService;

/** Cuenta del usuario autenticado (el id sale del {@code sub} del token, nunca de la URL). */
@RestController
@RequestMapping("/me")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(accounts.getAccount(userId(jwt)));
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        accounts.changePassword(userId(jwt), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Un token de servicio (sub = client_id) no representa a un usuario. */
    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new NotAUserTokenException();
        }
    }

    static class NotAUserTokenException extends RuntimeException {
        NotAUserTokenException() {
            super("El token no pertenece a un usuario");
        }
    }
}
