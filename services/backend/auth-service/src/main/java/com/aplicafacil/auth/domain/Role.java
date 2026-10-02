package com.aplicafacil.auth.domain;

/**
 * Roles de un usuario. Viajan en el claim {@code roles} del access token y
 * los servicios los ven como {@code ROLE_USER} / {@code ROLE_ADMIN}.
 */
public enum Role {
    USER,
    ADMIN
}
