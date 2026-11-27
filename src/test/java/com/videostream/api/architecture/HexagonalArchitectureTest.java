package com.videostream.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Trava a arquitetura hexagonal no build: se alguém importar Spring no domínio ou o
 * adaptador web no adaptador S3, o build quebra.
 */
@AnalyzeClasses(packages = "com.videostream.api", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule dominioNaoConheceFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "software.amazon..", "jakarta..", "com.fasterxml..");

    @ArchTest
    static final ArchRule aplicacaoNaoConheceFrameworks = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "software.amazon..", "jakarta..", "com.fasterxml..");

    @ArchTest
    static final ArchRule aplicacaoNaoDependeDeAdaptadores = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule dominioNaoDependeDeAplicacaoNemDeAdaptadores = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..adapter..");

    @ArchTest
    static final ArchRule adaptadorWebNaoDependeDoAdaptadorS3 = noClasses()
            .that().resideInAPackage("..adapter.in.web..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.s3..");

    @ArchTest
    static final ArchRule adaptadorS3NaoDependeDoAdaptadorWeb = noClasses()
            .that().resideInAPackage("..adapter.out.s3..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in.web..");

    @ArchTest
    static final ArchRule portasSaoInterfaces = classes()
            .that().resideInAPackage("..port..")
            .should().beInterfaces();

    @ArchTest
    static final ArchRule adaptadoresFicamNoPacoteDeAdaptadores = classes()
            .that().haveSimpleNameEndingWith("Adapter")
            .should().resideInAPackage("..adapter..");
}
