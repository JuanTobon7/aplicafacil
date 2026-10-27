package com.aplicafacil.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Reglas de acoplamiento y de SOLID de CONSTRAINTS.md para un servicio con paquetes {@code domain},
 * {@code application}, {@code infrastructure} y {@code api} (ADR 0001, regla 4).
 *
 * <pre>
 *   domain  ◀── application ◀── infrastructure
 *                   ▲
 *                  api
 * </pre>
 *
 * No están congeladas: el 2026-10-19 se corrigieron todas las violaciones heredadas, así que cualquier violación falla.
 */
public final class HexagonalRules {

    /** SOLID-I: un puerto con más métodos casi siempre sirve a varios clientes distintos. */
    public static final int MAX_PORT_METHODS = 8;

    private HexagonalRules() {
    }

    /** El dominio es Java puro: no conoce las otras capas ni frameworks. */
    public static ArchRule domainIsPure(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        basePackage + ".application..", basePackage + ".infrastructure..", basePackage + ".api..",
                        "org.springframework..", "jakarta.persistence..", "tools.jackson..")
                .as("domain no depende de application, infrastructure, api ni de frameworks")
                .allowEmptyShould(true);
    }

    /** Los casos de uso dependen de puertos, no de adaptadores concretos (DIP). */
    public static ArchRule applicationDoesNotKnowAdapters(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(basePackage + ".infrastructure..", basePackage + ".api..")
                .as("application no depende de infrastructure ni de api (usa puertos)")
                .allowEmptyShould(true);
    }

    /**
     * SOLID-D: application tampoco usa directamente la tecnología que hay detrás de un puerto. Si la necesita, le
     * falta un puerto.
     */
    public static ArchRule applicationUsesPortsNotTechnology(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..", "jakarta.persistence..", "org.hibernate..",
                        "org.springframework.jdbc..", "org.springframework.web.client..", "java.net.http..",
                        "org.springframework.amqp..", "com.rabbitmq..")
                .as("application no usa Spring Data, JPA, JDBC, clientes HTTP ni AMQP (usa puertos)")
                .allowEmptyShould(true);
    }

    /** SOLID-I: los puertos son chicos; uno grande se parte por cliente. */
    public static ArchRule portsAreSmall(String basePackage) {
        return classes()
                .that().resideInAPackage(basePackage + ".application.port..")
                .and().areInterfaces()
                .should(haveAtMostMethods(MAX_PORT_METHODS))
                .as("cada puerto de application.port tiene como máximo " + MAX_PORT_METHODS + " métodos")
                .allowEmptyShould(true);
    }

    /** Los adaptadores de salida no conocen los de entrada. */
    public static ArchRule infrastructureDoesNotKnowApi(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".infrastructure..")
                .should().dependOnClassesThat().resideInAPackage(basePackage + ".api..")
                .as("infrastructure no depende de api")
                .allowEmptyShould(true);
    }

    /** Los controladores llegan a la lógica por application, no directo a BD o clientes HTTP. */
    public static ArchRule apiGoesThroughApplication(String basePackage) {
        return noClasses()
                .that().resideInAPackage(basePackage + ".api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        basePackage + ".infrastructure.persistence..", basePackage + ".infrastructure.client..",
                        basePackage + ".infrastructure.messaging..")
                .as("api no depende de persistencia, clientes ni mensajería (pasa por application)")
                .allowEmptyShould(true);
    }

    /** Sin ciclos entre los paquetes de primer nivel bajo cada capa. */
    public static ArchRule noCycles(String basePackage) {
        return slices()
                .matching(basePackage + ".(*)..")
                .should().beFreeOfCycles()
                .as("sin ciclos entre capas")
                .allowEmptyShould(true);
    }

    /** Métodos abstractos propios (los {@code default} y {@code static} no obligan a quien implementa). */
    private static ArchCondition<JavaClass> haveAtMostMethods(int max) {
        return new ArchCondition<>("tener como máximo " + max + " métodos abstractos") {
            @Override
            public void check(JavaClass port, ConditionEvents events) {
                long count = port.getMethods().stream()
                        .filter(m -> m.getModifiers().contains(JavaModifier.ABSTRACT))
                        .count();
                if (count > max) {
                    events.add(SimpleConditionEvent.violated(port, port.getName() + " tiene " + count
                            + " métodos (máximo " + max + "): pártelo por cliente"));
                }
            }
        };
    }
}
