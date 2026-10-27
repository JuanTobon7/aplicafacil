package com.aplicafacil.linkedin;

import com.aplicafacil.platform.architecture.HexagonalRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Acoplamiento entre capas y SOLID (CONSTRAINTS.md). Sin deuda: cualquier violación falla. */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "com.aplicafacil.linkedin";

    @ArchTest
    static final ArchRule domainIsPure = HexagonalRules.domainIsPure(BASE);

    @ArchTest
    static final ArchRule applicationDoesNotKnowAdapters = HexagonalRules.applicationDoesNotKnowAdapters(BASE);

    @ArchTest
    static final ArchRule applicationUsesPortsNotTechnology = HexagonalRules.applicationUsesPortsNotTechnology(BASE);

    @ArchTest
    static final ArchRule portsAreSmall = HexagonalRules.portsAreSmall(BASE);

    @ArchTest
    static final ArchRule infrastructureDoesNotKnowApi = HexagonalRules.infrastructureDoesNotKnowApi(BASE);

    @ArchTest
    static final ArchRule apiGoesThroughApplication = HexagonalRules.apiGoesThroughApplication(BASE);

    @ArchTest
    static final ArchRule noCycles = HexagonalRules.noCycles(BASE);
}
