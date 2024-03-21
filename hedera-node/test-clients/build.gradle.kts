/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

sourceSets {
    // Needed because "resource" directory is misnamed. See
    // https://github.com/hashgraph/hedera-services/issues/3361
    main { resources { srcDir("src/main/resource") } }

    create("rcdiff")
    create("yahcli")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.compileJava { options.compilerArgs.add("-Xlint:-exports,-lossy-conversions") }

tasks.named<JavaCompile>("compileYahcliJava") {
    options.compilerArgs.add("-Xlint:-lossy-conversions")
}

mainModuleInfo { runtimeOnly("org.junit.platform.launcher") }

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

// The following tasks run the 'HapiTestEngine' tests (residing in src/main/java).
// IntelliJ picks up this task when running tests through in the IDE.

// Runs all tests
tasks.register<Test>("hapiTest") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform()

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs all tests that are not part of other test tasks
tasks.register<Test>("hapiTestMisc") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform {
        excludeTags(
            "CRYPTO",
            "TOKEN",
            "SMART_CONTRACT",
            "TIME_CONSUMING",
            "RESTART",
            "ND_RECONNECT"
        )
    }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs all tests of CryptoService
tasks.register<Test>("hapiTestCrypto") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("CRYPTO", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs all tests of TokenService
tasks.register<Test>("hapiTestToken") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("TOKEN", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs all tests of SmartContractService
tasks.register<Test>("hapiTestSmartContract") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("SMART_CONTRACT", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs a handful of test-suites that are extremely time-consuming (10+ minutes)
tasks.register<Test>("hapiTestTimeConsuming") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("TIME_CONSUMING", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs a handful of test-suites that are extremely time-consuming (10+ minutes)
tasks.register<Test>("hapiTestRestart") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("RESTART", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

tasks.register<Test>("hapiTestNDReconnect") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform { includeTags("ND_RECONNECT", "RECORD_STREAM_VALIDATION") }

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

tasks.test {
    // Disable these EET tests from being executed as part of the gradle "test" task.
    // We should maybe remove them from src/test into src/eet,
    // so it can be part of an eet test task instead. See issue #3412
    // (https://github.com/hashgraph/hedera-services/issues/3412).
    exclude("**/*")
}

tasks.itest {
    systemProperty("itests", System.getProperty("itests"))
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", layout.buildDirectory.dir("network/itest").get().asFile)
}

tasks.eet {
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", layout.buildDirectory.dir("network/itest").get().asFile)
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
        from(sourceSets["yahcli"].output)
        archiveClassifier.set("yahcli")
        configurations = listOf(project.configurations.getByName("yahcliRuntimeClasspath"))

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.yahcli.Yahcli",
                "Multi-Release" to "true"
            )
        }
    }

val rcdiffJar =
    tasks.register<ShadowJar>("rcdiffJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))
        from(sourceSets["rcdiff"].output)
        destinationDirectory.set(project.file("rcdiff"))
        archiveFileName.set("rcdiff.jar")
        configurations = listOf(project.configurations.getByName("rcdiffRuntimeClasspath"))

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.rcdiff.RcDiff",
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
        group = "copy"
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
        group = "copy"
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
