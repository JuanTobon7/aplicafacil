package com.aplicafacil.auth.api.rest;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.auth.application.UserAdministrationService;

/** Administración de cuentas. Solo ADMIN (también lo exige la cadena de seguridad). */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController {

    private final UserAdministrationService administration;

    UserAdminController(UserAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping
    UserPageResponse list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        UserAdministrationService.Page result = administration.list(page, size);
        return new UserPageResponse(result.users().stream().map(UserResponse::from).toList(),
                result.page(), result.size(), result.total());
    }

    @PatchMapping("/{id}/enabled")
    UserResponse setEnabled(@PathVariable UUID id, @Valid @RequestBody SetEnabledRequest request) {
        return UserResponse.from(administration.setEnabled(id, request.enabled()));
    }
}
