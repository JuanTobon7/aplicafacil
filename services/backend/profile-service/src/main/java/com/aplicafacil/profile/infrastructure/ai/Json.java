package com.aplicafacil.profile.infrastructure.ai;

import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aplicafacil.profile.domain.Period;

import tools.jackson.databind.JsonNode;

/** Lectura tolerante de los campos que escribe un modelo: "null" como texto, años sueltos, enums en minúsculas. */
final class Json {

    /** 2021-03, 2021/3, 03/2021 o 2021 (mes 01). */
    private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})[-/](\\d{1,2})$");
    private static final Pattern MONTH_YEAR = Pattern.compile("^(\\d{1,2})[-/](\\d{4})$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");

    private Json() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Period period(JsonNode node) {
        return new Period(month(node, "startDate"), month(node, "endDate"));
    }

    static YearMonth month(JsonNode node, String field) {
        String text = text(node, field);
        return text == null ? null : parseMonth(text);
    }

    private static YearMonth parseMonth(String text) {
        Matcher m = YEAR_MONTH.matcher(text);
        if (m.matches()) {
            return of(m.group(1), m.group(2));
        }
        m = MONTH_YEAR.matcher(text);
        if (m.matches()) {
            return of(m.group(2), m.group(1));
        }
        m = YEAR.matcher(text);
        return m.matches() ? of(m.group(1), "1") : null;
    }

    private static YearMonth of(String year, String month) {
        int y = Integer.parseInt(year);
        int mo = Integer.parseInt(month);
        return mo >= 1 && mo <= 12 && y >= 1950 && y <= 2100 ? YearMonth.of(y, mo) : null;
    }
}
