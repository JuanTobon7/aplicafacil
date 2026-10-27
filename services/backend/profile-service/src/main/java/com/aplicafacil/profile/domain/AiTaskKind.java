package com.aplicafacil.profile.domain;

/** Para qué se le pidió algo a la IA. */
public enum AiTaskKind {
    /** Descomponer el texto de un CV en secciones del perfil. */
    CV_EXTRACTION,
    /** Elegir la búsqueda de empleo que mejor encaja con el perfil. */
    SEARCH_SUGGESTION,
    /** Lo mismo y, al terminar, lanzar esas búsquedas en los portales ({@code profile.search-plan.ready}). */
    SEARCH_PLAN
}
