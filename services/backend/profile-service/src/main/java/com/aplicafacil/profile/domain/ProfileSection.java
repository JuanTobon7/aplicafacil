package com.aplicafacil.profile.domain;

/**
 * Secciones del perfil. Es la unidad de "editado a mano": si el usuario edita una sección, ni "Sincronizar de nuevo"
 * ni importar un CV la sobrescriben.
 */
public enum ProfileSection {
    HEADLINE,
    SUMMARY,
    EXPERIENCE,
    EDUCATION,
    SKILLS,
    LANGUAGES,
    CERTIFICATIONS,
    /** El tipo de perfil laboral ({@link WorkProfile}): editado a mano, la IA ya no lo cambia. */
    WORK_PROFILE
}
