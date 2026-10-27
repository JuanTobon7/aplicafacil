package com.aplicafacil.jobs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Regla del título de una oferta, sin Spring ni base de datos. */
class JobCardTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Casos reales guardados el 2026-10-16
            "Desarrollador Full stack (Java/ React)Desarrollador Full stack (Java/ React)|Desarrollador Full stack (Java/ React)",
            "Desarrollador de Automatismos PLC y HMIDesarrollador de Automatismos PLC y HMI|Desarrollador de Automatismos PLC y HMI",
            "Backend Developer Backend Developer|Backend Developer",
            "Senior Data Developer, Colombia|Senior Data Developer, Colombia",
            "Desarrollador Java / Desarrollador Kotlin|Desarrollador Java / Desarrollador Kotlin"
    })
    void theTitleIsNeverItsOwnTextTwice(String raw, String expected) {
        assertThat(card(raw).title()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"'   '", "''"})
    void aBlankTitleMakesTheCardIncomplete(String raw) {
        assertThat(card(raw).isComplete()).isFalse();
    }

    private static JobCard card(String title) {
        return new JobCard("1", "https://portal.example/jobs/1", title, "Acme", null, null, null, false);
    }
}
