package com.example.engine;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.example.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainMustNotKnowMessaging =
            noClasses().that().resideInAPackage("..task.domain..")
                    .should().dependOnClassesThat().resideInAPackage("..messaging..");

    @ArchTest
    static final ArchRule onlyMessagingAndConfigUseAmqp =
            noClasses().that().resideOutsideOfPackages("..messaging..", "..config..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.amqp..");

    @ArchTest
    static final ArchRule noFieldInjection =
            noFields().should().beAnnotatedWith(Autowired.class)
                    .because("constructor injection makes dependencies explicit and testable");
}
