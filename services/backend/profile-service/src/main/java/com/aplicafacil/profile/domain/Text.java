package com.aplicafacil.profile.domain;

import java.util.regex.Pattern;

/** Limpieza común de los textos que llegan de LinkedIn, de un CV o de la IA. */
final class Text {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern INLINE_SPACES = Pattern.compile("[ \\t]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private Text() {
    }

    /** Sin espacios de más; vacío = {@code null}; recortado a {@code max}. */
    static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return cut(SPACES.matcher(value.strip()).replaceAll(" "), max);
    }

    /** Como {@link #clean} pero conserva los saltos de línea (descripciones). */
    static String multiline(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = INLINE_SPACES.matcher(value.strip()).replaceAll(" ");
        return cut(BLANK_LINES.matcher(compact).replaceAll("\n\n"), max);
    }

    static String required(String value, int max, String field) {
        String clean = clean(value, max);
        if (clean == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return clean;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).strip();
    }
}
