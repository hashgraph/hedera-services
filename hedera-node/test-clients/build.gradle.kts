/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.shadow-jar")
}

description = "Hedera Services Test Clients for End to End Tests (EET)"

// -----
// module-info.java is ony used to define dependencies for now.
// It is not compiled and instead the classpath is used during compilation.
// This is temporary. To compile as module, remove the below and fix the
// compile errors in HapiTestEngine.
tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.hedera.node.test.clients") } }

java { modularity.inferModulePath.set(false) }

sourceSets.main { java.exclude("module-info.java") }
// -----

tasks.test {
    // Disable these EET tests from being executed as part of the gradle "test" task.
    // We should maybe remove them from src/test into src/eet,
    // so it can be part of an eet test task instead. See issue #3412
    // (https://github.com/hashgraph/hedera-services/issues/3412).
    exclude("**/*")
}

tasks.itest { systemProperty("itests", System.getProperty("itests")) }

sourceSets {
    // Needed because "resource" directory is misnamed. See
    // https://github.com/hashgraph/hedera-services/issues/3361
    main { resources { srcDir("src/main/resource") } }
}

itestModuleInfo {
    requires("com.hedera.node.test.clients")
    requires("com.hedera.node.hapi")
    requires("org.apache.commons.lang3")
    requires("org.junit.jupiter.api")
    requires("org.testcontainers")
    requires("org.testcontainers.junit.jupiter")
    requires("org.apache.commons.lang3")
}

eetModuleInfo {
    requires("com.hedera.node.test.clients")
    requires("org.junit.jupiter.api")
    requires("org.testcontainers")
    requires("org.testcontainers.junit.jupiter")
}

tasks.itest {
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", File(project.buildDir, "network/itest"))
}

tasks.eet {
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", File(project.buildDir, "network/eet"))
}

tasks.shadowJar {
    archiveFileName.set("SuiteRunner.jar")

    manifest {
        attributes(
            "Main-Class" to "com.hedera.services.bdd.suites.SuiteRunner",
            "Multi-Release" to "true"
        )
    }
}

val yahCliJar =
    tasks.register<ShadowJar>("yahCliJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveClassifier.set("yahcli")

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.yahcli.Yahcli",
                "Multi-Release" to "true"
            )
        }
    }

val validationJar =
    tasks.register<ShadowJar>("validationJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveFileName.set("ValidationScenarios.jar")

        manifest {
            attributes(
                "Main-Class" to
                    "com.hedera.services.bdd.suites.utils.validation.ValidationScenarios",
                "Multi-Release" to "true"
            )
        }
    }

val copyValidation =
    tasks.register<Copy>("copyValidation") {
        group = "copy"
        from(validationJar)
        into(project.file("validation-scenarios"))
    }

val cleanValidation =
    tasks.register<Delete>("cleanValidation") {
        group = "build"
        delete(File(project.file("validation-scenarios"), "ValidationScenarios.jar"))
    }

val copyYahCli =
    tasks.register<Copy>("copyYahCli") {
        group = "copy"
        from(yahCliJar)
        into(project.file("yahcli"))
        rename { "yahcli.jar" }
    }

val cleanYahCli =
    tasks.register<Delete>("cleanYahCli") {
        group = "build"
        delete(File(project.file("yahcli"), "yahcli.jar"))
    }

tasks.assemble {
    dependsOn(tasks.shadowJar)
    dependsOn(copyYahCli)
}

tasks.clean {
    dependsOn(cleanYahCli)
    dependsOn(cleanValidation)
}
