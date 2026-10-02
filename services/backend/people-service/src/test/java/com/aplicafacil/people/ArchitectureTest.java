package com.aplicafacil.people;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Reglas de puertos y adaptadores: las dependencias solo apuntan hacia adentro.
 *
 * <pre>
 *  infrastructure (in) ──▶ application ◀── persistence (out)
 *                              │
 *                              ▼
 *                           domain
 * </pre>
 */
class ArchitectureTest {

    private static final String DOMAIN = "com.aplicafacil.people.domain..";
    private static final String APPLICATION = "com.aplicafacil.people.application..";
    private static final String INFRASTRUCTURE = "com.aplicafacil.people.infrastructure..";
    private static final String PERSISTENCE = "com.aplicafacil.people.persistence..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.aplicafacil.people");

    @Test
    void domainIsPureJava() {
        classes().that().resideInAPackage(DOMAIN)
                // lombok: solo deja @lombok.Generated (sin dependencia en runtime)
                .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..", "lombok..")
                .because("el dominio no conoce frameworks ni otras capas")
                .check(CLASSES);
    }

    @Test
    void applicationDoesNotKnowAdapters() {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PERSISTENCE)
                .because("application solo conoce al dominio; los adaptadores dependen de ella")
                .check(CLASSES);
    }

    @Test
    void inputAdaptersOnlyUseInputPorts() {
        noClasses().that().resideInAPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        PERSISTENCE,
                        "com.aplicafacil.people.domain.model..",
                        "com.aplicafacil.people.application.port.out..",
                        "com.aplicafacil.people.application.services..")
                .because("los adaptadores de entrada entran por application.port.in")
                .check(CLASSES);
    }

    @Test
    void outputAdaptersOnlyImplementOutputPorts() {
        noClasses().that().resideInAPackage(PERSISTENCE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        INFRASTRUCTURE,
                        "com.aplicafacil.people.application.port.in..",
                        "com.aplicafacil.people.application.services..",
                        "com.aplicafacil.people.application.dto..")
                .because("persistence solo implementa application.port.out con modelos de dominio")
                .check(CLASSES);
    }
}
