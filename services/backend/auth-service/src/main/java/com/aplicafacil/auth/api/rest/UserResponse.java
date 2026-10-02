package com.aplicafacil.auth.api.rest;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import com.aplicafacil.auth.domain.User;

/** Vista pública de una cuenta: nunca expone el hash ni el estado de bloqueo interno. */
public record UserResponse(UUID id, String email, Set<String> roles, boolean enabled,
                           Instant lastLoginAt, Instant createdAt) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)),
                user.isEnabled(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
