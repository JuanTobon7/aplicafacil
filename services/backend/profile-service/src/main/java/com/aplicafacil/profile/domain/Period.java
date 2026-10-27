package com.aplicafacil.profile.domain;

import java.time.YearMonth;

/** Desde–hasta de un cargo o un estudio. {@code end = null}: en curso; {@code start = null}: no se sabe. */
public record Period(YearMonth start, YearMonth end) {

    public Period {
        if (start != null && end != null && end.isBefore(start)) {
            end = null;
        }
    }
}
