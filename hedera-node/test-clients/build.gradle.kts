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

tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.hedera.node.app.testclient") } }

tasks.test {
    // Disable these EET tests from being executed as part of the gradle "test" task.
    // We should maybe remove them from src/test into src/eet,
    // so it can be part of an eet test task instead. See issue #3412
    // (https://github.com/hashgraph/hedera-services/issues/3412).
    exclude("**/*")
}

configurations { evaluationDependsOn(":hedera-node:node-app-hapi-fees") }

sourceSets {
    // Needed because "resource" directory is misnamed. See
    // https://github.com/hashgraph/hedera-services/issues/3361
    main { resources { srcDir("src/main/resource") } }
}

dependencies {
    javaModuleDependencies {
        api(project(":hedera-node:node-app-hapi-fees"))
        api(project(":hedera-node:node-app-hapi-utils"))
        api(gav("com.fasterxml.jackson.annotation"))
        api(gav("com.google.common"))
        api(gav("com.google.protobuf"))
        api(project(":hedera-node:node-hapi"))
        api(gav("com.swirlds.common"))
        api(gav("headlong"))
        api(gav("info.picocli"))
        api(gav("java.annotation"))
        api(gav("net.i2p.crypto.eddsa"))
        api(gav("org.apache.commons.io"))
        api(gav("org.apache.logging.log4j"))
        api(gav("org.checkerframework.checker.qual"))
        api(gav("org.junit.jupiter.api"))
        api(gav("org.testcontainers"))
        api(gav("org.yaml.snakeyaml"))
        api(gav("tuweni.bytes"))

        implementation(project(":hedera-node:node-app-service-evm"))
        implementation(gav("com.fasterxml.jackson.core"))
        implementation(gav("com.fasterxml.jackson.databind"))
        implementation(gav("com.github.docker.java.api"))
        implementation(gav("com.github.spotbugs.annotations"))
        implementation(gav("grpc.netty"))
        implementation(gav("io.grpc"))
        implementation(gav("io.netty.handler"))
        implementation(gav("org.apache.commons.lang3"))
        implementation(gav("org.apache.logging.log4j.core"))
        implementation(gav("org.bouncycastle.provider"))
        implementation(gav("org.hyperledger.besu.crypto"))
        implementation(gav("org.hyperledger.besu.datatypes"))
        implementation(gav("org.hyperledger.besu.evm"))
        implementation(gav("org.json"))
        implementation(gav("org.opentest4j"))
        implementation(gav("tuweni.units"))

        itestImplementation(project(path))
        itestImplementation(gav("org.testcontainers"))
        itestImplementation(gav("org.testcontainers.junit.jupiter"))
        itestImplementation(project(":hedera-node:node-hapi"))
        itestImplementation(gav("org.junit.jupiter.api"))

        eetImplementation(project(path))
        eetImplementation(gav("org.junit.jupiter.api"))
        eetImplementation(gav("org.testcontainers"))
        eetImplementation(gav("org.testcontainers.junit.jupiter"))
    }
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
    dependsOn(project(":hedera-node:node-app-hapi-fees").tasks.jar)

    mergeServiceFiles()

    archiveFileName.set("SuiteRunner.jar")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    fileMode = 664
    dirMode = 775

    manifest {
        attributes(
            "Main-Class" to "com.hedera.services.bdd.suites.SuiteRunner",
            "Multi-Release" to "true"
        )
    }
}

val yahCliJar =
    tasks.register<ShadowJar>("yahCliJar") {
        dependsOn(project(":hedera-node:node-app-hapi-fees").tasks.jar)

        group = "shadow"
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations["runtimeClasspath"])
        mergeServiceFiles()

        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveClassifier.set("yahcli")
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
        fileMode = 664
        dirMode = 775

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.yahcli.Yahcli",
                "Multi-Release" to "true"
            )
        }
    }

val validationJar =
    tasks.register<ShadowJar>("validationJar") {
        dependsOn(project(":hedera-node:node-app-hapi-fees").tasks.jar)

        group = "shadow"
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations["runtimeClasspath"])
        mergeServiceFiles()

        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveFileName.set("ValidationScenarios.jar")
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
        fileMode = 664
        dirMode = 775

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
