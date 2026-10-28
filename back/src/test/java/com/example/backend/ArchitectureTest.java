package com.example.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces the hexagonal dependency rule: everything points inwards, towards the domain. */
@AnalyzeClasses(packages = "com.example.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "reactor..",
                    "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "jakarta.persistence..", "jakarta.servlet..",
                    "org.springframework.web..", "org.springframework.data..", "org.springframework.security..");

    @ArchTest
    static final ArchRule adaptersAreReachedOnlyThroughPorts = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Adapter");

    @ArchTest
    static final ArchRule controllersUseCasesNotRepositories = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..");
}
