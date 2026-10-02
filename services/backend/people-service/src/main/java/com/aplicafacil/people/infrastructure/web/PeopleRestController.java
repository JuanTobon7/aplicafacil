package com.aplicafacil.people.infrastructure.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.PeopleDtoRegister;
import com.aplicafacil.people.application.port.in.PeopleService;

/**
 * Adaptador de entrada REST. El gateway expone estas rutas bajo {@code /api/people}.
 *
 * <p>Solo traduce el JWT al {@code userId} del usuario autenticado; quién puede
 * tocar qué persona lo decide application.
 */
@RestController
@RequestMapping("/people")
class PeopleRestController {

    private final PeopleService peopleService;

    PeopleRestController(PeopleService peopleService) {
        this.peopleService = peopleService;
    }

    /** Persona del usuario autenticado. */
    @GetMapping("/me")
    PeopleDto me(Authentication auth) {
        return peopleService.getPeopleByUserId(userId(auth));
    }

    @GetMapping("/{peopleId}")
    PeopleDto get(@PathVariable UUID peopleId, Authentication auth) {
        return peopleService.getPeople(peopleId, userId(auth));
    }

    /** Registra la persona del usuario autenticado. */
    @PostMapping
    ResponseEntity<PeopleDto> register(@Valid @RequestBody PeopleDtoRegister body, Authentication auth) {
        PeopleDto created = peopleService.registerPeople(userId(auth), body);
        return ResponseEntity.created(URI.create("/people/" + created.getId())).body(created);
    }

    @PutMapping("/{peopleId}")
    PeopleDto update(@PathVariable UUID peopleId, @Valid @RequestBody PeopleDtoRegister body, Authentication auth) {
        return peopleService.updatePeople(peopleId, body, userId(auth));
    }

    @DeleteMapping("/{peopleId}")
    ResponseEntity<Void> delete(@PathVariable UUID peopleId, Authentication auth) {
        peopleService.deletePeople(peopleId, userId(auth));
        return ResponseEntity.noContent().build();
    }

    /** {@code sub} del token de un usuario (UUID). Un token de servicio no tiene persona. */
    private static UUID userId(Authentication auth) {
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("El token no corresponde a un usuario");
        }
    }
}
