package com.aplicafacil.linkedin.contract;

/** Datos básicos del perfil. Cualquier campo puede ser {@code null} si no se pudo leer. */
public record ProfileBasics(
        String firstName,
        String lastName,
        String headline,
        String photoUrl,
        String publicProfileUrl,
        String location) {
}
